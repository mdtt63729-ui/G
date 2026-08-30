#include <jni.h>
#include <git2.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <dirent.h>
#include <string>
#include <sys/stat.h>
#include <vector>

namespace {

struct FileEntry {
    std::string absolute;
    std::string relative;
    uint64_t size;
};

struct ProgressContext {
    JNIEnv* env;
    jobject callback;
    jmethodID onProgress;
    jmethodID onStage;
    jmethodID isCancelled;
    uint64_t totalBytes;
    uint64_t indexedBytes;
    size_t totalFiles;
    size_t indexedFiles;
    uint64_t lastLogicalBytes;
};

static std::string gitError(const char* fallback) {
    const git_error* error = git_error_last();
    if (error && error->message && error->message[0] != '\0') {
        return std::string(error->message);
    }
    return fallback;
}

static bool isExcluded(const std::string& relative) {
    static const char* excluded[] = {
        ".git", ".gradle", "build", ".idea", ".cxx", "captures", ".kotlin",
        "node_modules", "dist", "out", "target", "coverage", ".cache",
        ".pytest_cache", "__pycache__", ".venv", "venv", "Pods", "DerivedData"
    };
    for (const char* name : excluded) {
        const std::string prefix(name);
        if (relative == prefix || relative.rfind(prefix + "/", 0) == 0) return true;
    }
    return false;
}

static bool collectFiles(const std::string& root, const std::string& rel, std::vector<FileEntry>& out) {
    const std::string current = rel.empty() ? root : root + "/" + rel;
    DIR* dir = opendir(current.c_str());
    if (!dir) return false;

    dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        const char* name = entry->d_name;
        if (std::strcmp(name, ".") == 0 || std::strcmp(name, "..") == 0) continue;

        std::string childRel = rel.empty() ? name : rel + "/" + name;
        if (isExcluded(childRel)) continue;

        std::string childAbs = root + "/" + childRel;
        struct stat st{};
        if (lstat(childAbs.c_str(), &st) != 0) continue;

        // Do not follow symlinks from the ZIP/project into arbitrary filesystem locations.
        if (S_ISLNK(st.st_mode)) continue;

        if (S_ISDIR(st.st_mode)) {
            if (!collectFiles(root, childRel, out)) {
                closedir(dir);
                return false;
            }
        } else if (S_ISREG(st.st_mode)) {
            out.push_back(FileEntry{childAbs, childRel, static_cast<uint64_t>(st.st_size)});
        }
    }
    closedir(dir);
    return true;
}

// Forward declaration — cancelled() is defined later but used by pushTransferProgressCallback.
static bool cancelled(JNIEnv* env, jobject callback, jmethodID method);

static void stage(JNIEnv* env, jobject callback, jmethodID method, const char* name) {
    if (!env || !callback || !method) return;
    jstring value = env->NewStringUTF(name);
    env->CallVoidMethod(callback, method, value);
    env->DeleteLocalRef(value);
}

static void progress(JNIEnv* env, jobject callback, jmethodID method,
                     uint64_t uploaded, uint64_t total, size_t filesDone,
                     size_t filesTotal, const std::string& current) {
    if (!env || !callback || !method) return;
    jstring currentFile = env->NewStringUTF(current.c_str());
    env->CallVoidMethod(callback, method,
                        static_cast<jlong>(uploaded),
                        static_cast<jlong>(total),
                        static_cast<jint>(filesDone),
                        static_cast<jint>(filesTotal),
                        currentFile);
    env->DeleteLocalRef(currentFile);
}

static int credentialsCallback(git_cred** out, const char* /*url*/, const char* usernameFromUrl,
                               unsigned int allowedTypes, void* payload) {
    const char* token = static_cast<const char*>(payload);
    if (!token || token[0] == '\0') return GIT_EAUTH;

    if ((allowedTypes & GIT_CREDENTIAL_USERPASS_PLAINTEXT) == 0) {
        return GIT_PASSTHROUGH;
    }

    const char* username = (usernameFromUrl && usernameFromUrl[0]) ? usernameFromUrl : "x-access-token";
    return git_cred_userpass_plaintext_new(out, username, token);
}

static int pushTransferProgressCallback(unsigned int current, unsigned int total,
                                         size_t bytes, void* payload) {
    auto* context = static_cast<ProgressContext*>(payload);
    if (!context || !context->env || !context->callback) return 0;
    if (cancelled(context->env, context->callback, context->isCancelled)) return -1;

    // libgit2 reports the real network byte count here. The UI's main byte meter
    // represents source/project bytes, so use a monotonic logical progress range
    // for the final network stage and expose the real sent-byte count in the
    // status text. This avoids the old incorrect 0..100 source-byte calculation.
    const double ratio = total > 0
        ? std::clamp(static_cast<double>(current) / static_cast<double>(total), 0.0, 1.0)
        : 0.0;
    uint64_t logicalBytes = static_cast<uint64_t>(context->totalBytes * ratio);
    logicalBytes = std::max(logicalBytes, context->lastLogicalBytes);
    logicalBytes = std::min(logicalBytes, context->totalBytes);
    context->lastLogicalBytes = logicalBytes;

    std::string status = "Pushing Git objects... " + std::to_string(bytes) + " B sent";
    progress(context->env, context->callback, context->onProgress,
             logicalBytes, context->totalBytes, context->totalFiles, context->totalFiles,
             status);
    return 0;
}

static int updateTipsCallback(const char* refname, const git_oid* /*a*/, const git_oid* /*b*/, void* payload) {
    auto* context = static_cast<ProgressContext*>(payload);
    if (context && context->env && context->callback) {
        progress(context->env, context->callback, context->onProgress,
                 context->totalBytes, context->totalBytes,
                 context->totalFiles, context->totalFiles,
                 refname ? refname : "Updating branch...");
    }
    return 0;
}


static bool copyProjectTree(const std::filesystem::path& source, const std::filesystem::path& destination) {
    std::error_code ec;
    std::filesystem::create_directories(destination, ec);
    if (ec) return false;
    for (const auto& entry : std::filesystem::recursive_directory_iterator(source, std::filesystem::directory_options::skip_permission_denied, ec)) {
        if (ec) return false;
        const auto rel = std::filesystem::relative(entry.path(), source, ec);
        if (ec) return false;
        if (rel == ".git" || rel.string().rfind(".git/", 0) == 0) {
            // NDK's libc++ does not have disable_recursion_pending(); just skip.
            (void)entry;
            continue;
        }
        const auto target = destination / rel;
        if (entry.is_directory(ec)) {
            std::filesystem::create_directories(target, ec);
            if (ec) return false;
        } else if (entry.is_regular_file(ec)) {
            std::filesystem::create_directories(target.parent_path(), ec);
            if (ec) return false;
            std::filesystem::copy_file(entry.path(), target, std::filesystem::copy_options::overwrite_existing, ec);
            if (ec) return false;
        }
    }
    return true;
}


static void removeExcludedIndexPaths(git_index* index) {
    static const char* excluded[] = {
        ".git", ".gradle", "build", ".idea", ".cxx", "captures", ".kotlin",
        "node_modules", "dist", "out", "target", "coverage", ".cache",
        ".pytest_cache", "__pycache__", ".venv", "venv", "Pods", "DerivedData"
    };
    for (const char* path : excluded) {
        git_index_remove_directory(index, path, 0);
    }
}

static bool clearWorkingTreeExceptGit(const std::filesystem::path& root) {
    std::error_code ec;
    for (const auto& entry : std::filesystem::directory_iterator(root, std::filesystem::directory_options::skip_permission_denied, ec)) {
        if (ec) return false;
        if (entry.path().filename() == ".git") continue;
        std::filesystem::remove_all(entry.path(), ec);
        if (ec) return false;
    }
    return true;
}

static bool checkNoJavaException(JNIEnv* env) {
    return env && !env->ExceptionCheck();
}

static bool cancelled(JNIEnv* env, jobject callback, jmethodID method) {
    if (!env || !callback || !method) return false;
    const jboolean value = env->CallBooleanMethod(callback, method);
    return value == JNI_TRUE || env->ExceptionCheck();
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_gitofy_data_git_GitNativeManager_nativePushDirectoryToGithub(
        JNIEnv* env, jobject /*thiz*/, jstring repoUrl, jstring token,
        jstring directoryPath, jstring branch, jstring commitMessage,
        jstring userName, jstring userEmail, jobject callback) {
    if (!env || !repoUrl || !token || !directoryPath || !branch || !commitMessage) {
        return env->NewStringUTF("ERROR:Invalid native push arguments");
    }

    const char* urlChars = env->GetStringUTFChars(repoUrl, nullptr);
    const char* tokenChars = env->GetStringUTFChars(token, nullptr);
    const char* dirChars = env->GetStringUTFChars(directoryPath, nullptr);
    const char* branchChars = env->GetStringUTFChars(branch, nullptr);
    const char* messageChars = env->GetStringUTFChars(commitMessage, nullptr);
    const char* nameChars = userName ? env->GetStringUTFChars(userName, nullptr) : nullptr;
    const char* emailChars = userEmail ? env->GetStringUTFChars(userEmail, nullptr) : nullptr;

    jclass callbackClass = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID onProgress = callbackClass ? env->GetMethodID(callbackClass, "onProgress", "(JJIILjava/lang/String;)V") : nullptr;
    jmethodID onStage = callbackClass ? env->GetMethodID(callbackClass, "onStage", "(Ljava/lang/String;)V") : nullptr;
    jmethodID isCancelled = callbackClass ? env->GetMethodID(callbackClass, "isCancelled", "()Z") : nullptr;

    ProgressContext context{env, callback, onProgress, onStage, isCancelled, 0, 0, 0, 0, 0};
    std::string result;

    git_libgit2_init();
    git_repository* repository = nullptr;
    git_index* index = nullptr;
    git_signature* signature = nullptr;
    git_tree* tree = nullptr;
    git_remote* remote = nullptr;

    do {
        std::vector<FileEntry> files;
        if (!collectFiles(dirChars, "", files)) {
            result = "ERROR:Unable to enumerate project files";
            break;
        }
        std::sort(files.begin(), files.end(), [](const FileEntry& a, const FileEntry& b) {
            return a.relative < b.relative;
        });
        stage(env, callback, onStage, "Initializing native Git repository");
        if (git_repository_init(&repository, dirChars, 0) != 0) {
            result = "ERROR:" + gitError("git_repository_init failed");
            break;
        }

        if (git_repository_index(&index, repository) != 0) {
            result = "ERROR:" + gitError("git_repository_index failed");
            break;
        }

        // Respect repository .gitignore rules when deciding what enters the index.
        // Progress still counts skipped source files as processed so the UI
        // denominator remains stable and never jumps backwards.
        std::vector<FileEntry> indexFiles;
        indexFiles.reserve(files.size());
        for (const auto& file : files) {
            int ignored = 0;
            if (git_ignore_path_is_ignored(&ignored, repository, file.relative.c_str()) != 0) {
                result = "ERROR:Failed to evaluate .gitignore for " + file.relative + ": " +
                         gitError("gitignore check failed");
                break;
            }
            if (!ignored) indexFiles.push_back(file);
        }
        if (!result.empty()) break;

        context.totalFiles = files.size();
        for (const auto& file : files) context.totalBytes += file.size;

        stage(env, callback, onStage, "Hashing files in native Git core");
        if (git_index_add_all(index, nullptr, GIT_INDEX_ADD_DEFAULT, nullptr, nullptr) != 0) {
            result = "ERROR:Failed to add project files: " + gitError("index add all failed");
            break;
        }
        removeExcludedIndexPaths(index);
        size_t indexPosition = 0;
        for (const auto& file : files) {
            if (cancelled(env, callback, isCancelled)) {
                result = "ERROR:Operation cancelled";
                break;
            }
            const bool shouldIndex = indexPosition < indexFiles.size() &&
                                     indexFiles[indexPosition].relative == file.relative;
            if (!shouldIndex) {
                context.indexedFiles++;
                context.indexedBytes += file.size;
                progress(env, callback, onProgress, context.indexedBytes, context.totalBytes,
                         context.indexedFiles, context.totalFiles,
                         "Ignored by .gitignore: " + file.relative);
                if (!checkNoJavaException(env)) {
                    result = "ERROR:Progress callback failed";
                    break;
                }
                continue;
            }
            context.indexedFiles++;
            context.indexedBytes += file.size;
            ++indexPosition;
            progress(env, callback, onProgress, context.indexedBytes, context.totalBytes,
                     context.indexedFiles, context.totalFiles, file.relative);
            if (!checkNoJavaException(env)) {
                result = "ERROR:Progress callback failed";
                break;
            }
        }
        if (!result.empty()) break;

        if (git_index_write(index) != 0) {
            result = "ERROR:" + gitError("Failed to write Git index");
            break;
        }
        git_oid treeOid{};
        if (git_index_write_tree(&treeOid, index) != 0 || git_tree_lookup(&tree, repository, &treeOid) != 0) {
            result = "ERROR:" + gitError("Failed to write Git tree");
            break;
        }

        const char* name = (nameChars && nameChars[0]) ? nameChars : "GITOFY";
        const char* email = (emailChars && emailChars[0]) ? emailChars : "gitofy@users.noreply.github.com";
        if (git_signature_now(&signature, name, email) != 0) {
            result = "ERROR:" + gitError("Failed to create Git signature");
            break;
        }

        // The create-repository flow starts from an empty GitHub repository, so
        // there is no parent. The ref is created locally and pushed exactly once.
        const std::string ref = std::string("refs/heads/") + branchChars;
        git_oid commitOid{};
        if (git_commit_create(&commitOid, repository, ref.c_str(), signature, signature,
                              nullptr, messageChars, tree, 0, nullptr) != 0) {
            result = "ERROR:" + gitError("Failed to create commit");
            break;
        }

        stage(env, callback, onStage, "Pushing one Git pack to GitHub");
        if (git_remote_create(&remote, repository, "origin", urlChars) != 0) {
            result = "ERROR:" + gitError("Failed to create origin remote");
            break;
        }

        git_push_options pushOptions;
        git_push_options_init(&pushOptions, GIT_PUSH_OPTIONS_VERSION);
        pushOptions.pb_parallelism = 2; // Bound native CPU use on phones; avoid UI contention.
        pushOptions.callbacks.credentials = credentialsCallback;
        pushOptions.callbacks.push_transfer_progress = pushTransferProgressCallback;
        pushOptions.callbacks.update_tips = updateTipsCallback;
        pushOptions.callbacks.payload = &context;

        const std::string refspec = ref + ":" + ref;
        git_strarray refspecs{};
        char* refspecPtr = const_cast<char*>(refspec.c_str());
        refspecs.strings = &refspecPtr;
        refspecs.count = 1;

        if (git_remote_push(remote, &refspecs, &pushOptions) != 0) {
            result = "ERROR:" + gitError("GitHub push failed");
            break;
        }

        char oidText[GIT_OID_HEXSZ + 1]{};
        git_oid_tostr(oidText, sizeof(oidText), &commitOid);
        progress(env, callback, onProgress, context.totalBytes, context.totalBytes,
                 context.totalFiles, context.totalFiles, "Completed");
        result = std::string("OK:") + oidText;
    } while (false);

    if (remote) git_remote_free(remote);
    if (signature) git_signature_free(signature);
    if (tree) git_tree_free(tree);
    if (index) git_index_free(index);
    if (repository) git_repository_free(repository);
    git_libgit2_shutdown();

    if (nameChars) env->ReleaseStringUTFChars(userName, nameChars);
    if (emailChars) env->ReleaseStringUTFChars(userEmail, emailChars);
    env->ReleaseStringUTFChars(commitMessage, messageChars);
    env->ReleaseStringUTFChars(branch, branchChars);
    env->ReleaseStringUTFChars(directoryPath, dirChars);
    env->ReleaseStringUTFChars(token, tokenChars);
    env->ReleaseStringUTFChars(repoUrl, urlChars);

    return env->NewStringUTF(result.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_gitofy_data_git_GitNativeManager_nativeSyncDirectoryToGithub(
        JNIEnv* env, jobject /*thiz*/, jstring repoUrl, jstring token,
        jstring sourceDirectory, jstring branch, jstring commitMessage,
        jstring userName, jstring userEmail, jstring workDirectory, jobject callback) {
    if (!env || !repoUrl || !token || !sourceDirectory || !branch || !commitMessage || !workDirectory) {
        return env->NewStringUTF("ERROR:Invalid native sync arguments");
    }
    const char* urlChars = env->GetStringUTFChars(repoUrl, nullptr);
    const char* tokenChars = env->GetStringUTFChars(token, nullptr);
    const char* sourceChars = env->GetStringUTFChars(sourceDirectory, nullptr);
    const char* branchChars = env->GetStringUTFChars(branch, nullptr);
    const char* messageChars = env->GetStringUTFChars(commitMessage, nullptr);
    const char* nameChars = userName ? env->GetStringUTFChars(userName, nullptr) : nullptr;
    const char* emailChars = userEmail ? env->GetStringUTFChars(userEmail, nullptr) : nullptr;
    const char* workChars = env->GetStringUTFChars(workDirectory, nullptr);

    jclass callbackClass = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID onProgress = callbackClass ? env->GetMethodID(callbackClass, "onProgress", "(JJIILjava/lang/String;)V") : nullptr;
    jmethodID onStage = callbackClass ? env->GetMethodID(callbackClass, "onStage", "(Ljava/lang/String;)V") : nullptr;
    jmethodID isCancelled = callbackClass ? env->GetMethodID(callbackClass, "isCancelled", "()Z") : nullptr;
    ProgressContext context{env, callback, onProgress, onStage, isCancelled, 0, 0, 0, 0, 0};
    std::string result;
    git_libgit2_init();
    git_repository* repository = nullptr;
    git_index* index = nullptr;
    git_signature* signature = nullptr;
    git_tree* tree = nullptr;
    git_remote* remote = nullptr;
    git_commit* parent = nullptr;
    git_reference* headRef = nullptr;

    do {
        std::vector<FileEntry> files;
        if (!collectFiles(sourceChars, "", files)) { result = "ERROR:Unable to enumerate source project"; break; }
        context.totalFiles = files.size();
        for (const auto& f : files) context.totalBytes += f.size;
        stage(env, callback, onStage, "Cloning current GitHub repository");
        if (cancelled(env, callback, isCancelled)) { result = "ERROR:Operation cancelled"; break; }

        std::filesystem::path workPath(workChars);
        std::error_code ec;
        std::filesystem::remove_all(workPath, ec);
        if (ec || !std::filesystem::create_directories(workPath, ec)) { result = "ERROR:Unable to prepare native sync directory"; break; }

        git_clone_options cloneOptions;
        if (git_clone_options_init(&cloneOptions, GIT_CLONE_OPTIONS_VERSION) != 0) { result = "ERROR:Failed to initialize clone options"; break; }
        git_fetch_options_init(&cloneOptions.fetch_opts, GIT_FETCH_OPTIONS_VERSION);
        cloneOptions.fetch_opts.callbacks.credentials = credentialsCallback;
        cloneOptions.fetch_opts.callbacks.payload = const_cast<char*>(tokenChars);
        cloneOptions.checkout_branch = branchChars;
        if (git_clone(&repository, urlChars, workChars, &cloneOptions) != 0) {
            result = "ERROR:" + gitError("GitHub clone failed"); break;
        }
        if (cancelled(env, callback, isCancelled)) { result = "ERROR:Operation cancelled"; break; }

        stage(env, callback, onStage, "Replacing repository working tree");
        if (!clearWorkingTreeExceptGit(workPath) || !copyProjectTree(sourceChars, workPath)) {
            result = "ERROR:Failed to synchronize project files"; break;
        }
        if (git_repository_index(&index, repository) != 0) { result = "ERROR:" + gitError("Failed to open Git index"); break; }
        if (git_index_clear(index) != 0) { result = "ERROR:" + gitError("Failed to reset Git index"); break; }
        stage(env, callback, onStage, "Indexing synchronized files");
        if (git_index_add_all(index, nullptr, GIT_INDEX_ADD_DEFAULT, nullptr, nullptr) != 0) {
            result = "ERROR:" + gitError("Failed to index synchronized files"); break;
        }
        removeExcludedIndexPaths(index);
        for (const auto& f : files) {
            context.indexedFiles++;
            context.indexedBytes += f.size;
            progress(env, callback, onProgress, context.indexedBytes, context.totalBytes, context.indexedFiles, context.totalFiles, f.relative);
            if (cancelled(env, callback, isCancelled)) { result = "ERROR:Operation cancelled"; break; }
        }
        if (!result.empty()) break;
        if (git_index_write(index) != 0) { result = "ERROR:" + gitError("Failed to write Git index"); break; }
        git_oid syncTreeOid{};
        if (git_index_write_tree(&syncTreeOid, index) != 0 || git_tree_lookup(&tree, repository, &syncTreeOid) != 0) { result = "ERROR:" + gitError("Failed to write synchronized tree"); break; }
        if (git_repository_head_unborn(repository)) { result = "ERROR:Cloned repository has no HEAD"; break; }
        if (git_repository_head(&headRef, repository) != 0) { result = "ERROR:" + gitError("Failed to read repository HEAD"); break; }
        const git_oid* headOid = git_reference_target(headRef);
        if (!headOid || git_commit_lookup(&parent, repository, headOid) != 0) { result = "ERROR:" + gitError("Failed to read repository HEAD commit"); break; }
        if (git_oid_equal(git_commit_tree_id(parent), git_tree_id(tree))) {
            progress(env, callback, onProgress, context.totalBytes, context.totalBytes, context.totalFiles, context.totalFiles, "No changes");
            result = "OK:NO_CHANGES";
            break;
        }

        const char* name = (nameChars && nameChars[0]) ? nameChars : "GITOFY";
        const char* email = (emailChars && emailChars[0]) ? emailChars : "gitofy@users.noreply.github.com";
        if (git_signature_now(&signature, name, email) != 0) { result = "ERROR:" + gitError("Failed to create Git signature"); break; }
        git_oid commitOid{};
        const git_commit* parentCommit = parent;
        if (git_commit_create(&commitOid, repository, "HEAD", signature, signature, nullptr, messageChars, tree, 1, &parentCommit) != 0) {
            result = "ERROR:" + gitError("Failed to create update commit"); break;
        }

        stage(env, callback, onStage, "Pushing one update commit to GitHub");
        if (git_remote_lookup(&remote, repository, "origin") != 0) { result = "ERROR:" + gitError("Failed to open origin remote"); break; }
        git_push_options pushOptions;
        git_push_options_init(&pushOptions, GIT_PUSH_OPTIONS_VERSION);
        pushOptions.pb_parallelism = 2;
        pushOptions.callbacks.credentials = credentialsCallback;
        pushOptions.callbacks.push_transfer_progress = pushTransferProgressCallback;
        pushOptions.callbacks.update_tips = updateTipsCallback;
        pushOptions.callbacks.payload = &context;
        const std::string ref = std::string("refs/heads/") + branchChars;
        const std::string refspec = ref + ":" + ref;
        git_strarray refspecs{};
        char* refspecPtr = const_cast<char*>(refspec.c_str());
        refspecs.strings = &refspecPtr; refspecs.count = 1;
        if (git_remote_push(remote, &refspecs, &pushOptions) != 0) { result = "ERROR:" + gitError("GitHub update push failed"); break; }
        char oidText[GIT_OID_HEXSZ + 1]{};
        git_oid_tostr(oidText, sizeof(oidText), &commitOid);
        progress(env, callback, onProgress, context.totalBytes, context.totalBytes, context.totalFiles, context.totalFiles, "Completed");
        result = std::string("OK:") + oidText;
    } while (false);

    if (parent) git_commit_free(parent);
    if (headRef) git_reference_free(headRef);
    if (remote) git_remote_free(remote);
    if (signature) git_signature_free(signature);
    if (tree) git_tree_free(tree);
    if (index) git_index_free(index);
    if (repository) git_repository_free(repository);
    git_libgit2_shutdown();
    if (nameChars) env->ReleaseStringUTFChars(userName, nameChars);
    if (emailChars) env->ReleaseStringUTFChars(userEmail, emailChars);
    env->ReleaseStringUTFChars(workDirectory, workChars);
    env->ReleaseStringUTFChars(commitMessage, messageChars);
    env->ReleaseStringUTFChars(branch, branchChars);
    env->ReleaseStringUTFChars(sourceDirectory, sourceChars);
    env->ReleaseStringUTFChars(token, tokenChars);
    env->ReleaseStringUTFChars(repoUrl, urlChars);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gitofy_data_git_GitNativeManager_nativeVersion(JNIEnv* env, jobject /*thiz*/) {
    int major = 0, minor = 0, rev = 0;
    git_libgit2_version(&major, &minor, &rev);
    std::string version = std::to_string(major) + "." + std::to_string(minor) + "." + std::to_string(rev);
    return env->NewStringUTF(version.c_str());
}
