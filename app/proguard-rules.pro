# Keep Hilt generated classes
-keep class **_HiltModules { *; }
-keep class **_HiltComponents$* { *; }
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.lifecycle.HiltViewModel class *

# JGit — heavily reflection-based; -dontwarn alone silences build warnings
# but does not stop R8 from stripping/renaming classes JGit looks up by name
# at runtime, which throws (ClassNotFoundException / NoSuchMethodError) the
# first time a repo operation runs on a release build.
-dontwarn org.slf4j.**
-keep class org.eclipse.jgit.** { *; }
-keep interface org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# WorkManager workers instantiated via HiltWorkerFactory reflection
# (ZipExtractionWorker, WorkflowSyncWorker, GitPushWorker,
# RepositoryUpdateWorker, ArtifactDownloadWorker). Without this, R8 can strip
# the assisted-inject constructor these are created through.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep,allowobfuscation,allowshrinking @dagger.assisted.AssistedFactory interface *
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.internal.builders.* { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking interface retrofit2.Callback
-keep,allowobfuscation,allowshrinking class * extends retrofit2.Retrofit

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Serialization
-keepattributes *Annotation*
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.gitofy.**$$serializer { *; }
-keepclassmembers class com.gitofy.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Data classes
-keep class com.gitofy.data.remote.dto.** { *; }

# Keep model classes
-keep class com.gitofy.domain.model.** { *; }

# JNI entry points must retain their class/method names for native symbol lookup.
-keep class com.gitofy.core.security.NativeSecurity { *; }
