package com.gitofy.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §5 — Extended settings repository.
 *
 * Extends the original theme/background-sync settings with every category
 * required by the PRD: appearance (amoled, accent, density, animations, font),
 * editor, agent, build, notifications, privacy, advanced.
 *
 * Uses the SAME DataStore instance as the original so existing settings
 * (themeMode, dynamicColor, backgroundSync) remain and are migrated seamlessly.
 */
private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gitofy_app_settings"
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class UiDensity { COMPACT, COMFORTABLE, SPACIOUS }
enum class AnimationLevel { FULL, REDUCED, OFF }
enum class FontSize { SMALL, DEFAULT, LARGE }
// PRD §18 lists Default/Modern/Rounded/Serif/Mono/System. Modern and Rounded
// require bundling actual custom font files (e.g. Inter, Google Sans
// Rounded) — that can't be safely added in an environment without network
// access to fetch and license-check real font assets, so this only adds the
// built-in-family options. System is kept distinct from Default so it maps
// to the platform's generic sans-serif rather than the app's own default.
enum class FontFamilyOption { DEFAULT, SERIF, MONOSPACE, SYSTEM }
enum class SyncFrequency { FIFTEEN_MINUTES, THIRTY_MINUTES, HOUR, MANUAL }

data class AppSettings(
    // Original settings (kept for backward compatibility)
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val dynamicColor: Boolean = false,
    val backgroundSync: Boolean = true,
    // PRD §6 — Appearance
    val amoledMode: Boolean = false,
    val accentColorHex: String = "#0B72B9",
    val uiDensity: UiDensity = UiDensity.COMFORTABLE,
    val animationLevel: AnimationLevel = AnimationLevel.FULL,
    val fontSize: FontSize = FontSize.DEFAULT,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val hapticFeedback: Boolean = true,
    val syncFrequency: SyncFrequency = SyncFrequency.HOUR,
    val restoreLastLocation: Boolean = true,
    // PRD §19 — Models
    val defaultProviderId: String = "gemini",
    val defaultModelId: String = "",
    val modelTemperature: Float = 0.7f,
    val modelTopP: Float = 1.0f,
    val modelMaxOutputTokens: Int = 4000,
    val modelContextWindow: Int = 128_000,
    // PRD §20 — AI & Agent
    val agentMode: Boolean = false,
    val autoToolExecution: Boolean = false,
    val confirmDangerousActions: Boolean = true,
    val maxAgentIterations: Int = 10,
    val autoErrorFixing: Boolean = true,
    val autoBuildRetry: Boolean = false,
    val aiResponseStyle: String = "concise",
    // PRD §21 — Editor
    val editorFontSize: Int = 14,
    val editorLineNumbers: Boolean = true,
    val editorWordWrap: Boolean = false,
    val editorSyntaxHighlighting: Boolean = true,
    val editorBracketMatching: Boolean = true,
    val editorAutoIndent: Boolean = true,
    val editorAutoSave: Boolean = true,
    val editorHighlightCurrentLine: Boolean = true,
    val editorTabSize: Int = 4,
    val editorUseSpaces: Boolean = true,
    val editorMinimap: Boolean = false,
    // PRD §22 — Workspace & Project
    val openLastProject: Boolean = true,
    val workspaceAutoSave: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val restoreWorkspaceLayout: Boolean = true,
    // PRD §23 — Git & GitHub
    val gitDefaultBranch: String = "main",
    val gitConfirmDestructive: Boolean = true,
    val gitAutoPush: Boolean = false,
    val gitUseRepositoryDefaultBranch: Boolean = true,
    val gitIncludeForks: Boolean = true,
    val gitFetchOnOpen: Boolean = false,
    val gitAiCreateBranch: Boolean = true,
    val gitAiConfirmCommit: Boolean = true,
    val gitAiCreatePullRequest: Boolean = true,
    val gitConfirmMerge: Boolean = true,
    val gitAiActionsEnabled: Boolean = true,
    val gitReleaseOperationsEnabled: Boolean = true,
    val gitNotificationsEnabled: Boolean = true,
    val gitParticipatingNotifications: Boolean = true,
    // PRD §24 — Build & Run
    val autoBuild: Boolean = false,
    val buildVariant: String = "debug",
    val buildNotifications: Boolean = true,
    // PRD §25 — Notifications
    val notifyBuildCompleted: Boolean = true,
    val notifyBuildFailed: Boolean = true,
    val notifyAITaskCompleted: Boolean = true,
    val notifyAITaskFailed: Boolean = true,
    val notifyGitCompleted: Boolean = true,
    val notifyGitFailed: Boolean = true,
    val notifyAppErrors: Boolean = true,
    val notifyDownloads: Boolean = true,
    // PRD §26 — Privacy & Security
    val analyticsEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = false,
    // PRD §27 — Advanced
    val debugMode: Boolean = false,
    val experimentalFeatures: Boolean = false
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        // Original
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        // Appearance
        val AMOLED = booleanPreferencesKey("amoled_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        // One-time migration guard: installs updating from a version that
        // shipped "#5849E8" (Signal Indigo) as the hardcoded default need a
        // single automatic upgrade to the new "#0B72B9" (Sky Blue) default —
        // see migrateLegacyAccentDefault() below.
        val ACCENT_MIGRATED_TO_SKY = booleanPreferencesKey("accent_migrated_to_sky_v1")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val RESTORE_LAST_LOCATION = booleanPreferencesKey("restore_last_location")
        // Models
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val MODEL_TEMPERATURE = floatPreferencesKey("model_temperature")
        val MODEL_TOP_P = floatPreferencesKey("model_top_p")
        val MODEL_MAX_TOKENS = intPreferencesKey("model_max_tokens")
        val MODEL_CONTEXT = intPreferencesKey("model_context")
        // Agent
        val AGENT_MODE = booleanPreferencesKey("agent_mode")
        val AUTO_TOOL_EXEC = booleanPreferencesKey("auto_tool_exec")
        val CONFIRM_DANGEROUS = booleanPreferencesKey("confirm_dangerous")
        val MAX_AGENT_ITER = intPreferencesKey("max_agent_iter")
        val AUTO_ERROR_FIX = booleanPreferencesKey("auto_error_fix")
        val AUTO_BUILD_RETRY_AGENT = booleanPreferencesKey("auto_build_retry_agent")
        val AI_RESPONSE_STYLE = stringPreferencesKey("ai_response_style")
        // Editor
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val EDITOR_SYNTAX = booleanPreferencesKey("editor_syntax")
        val EDITOR_BRACKET = booleanPreferencesKey("editor_bracket")
        val EDITOR_AUTO_INDENT = booleanPreferencesKey("editor_auto_indent")
        val EDITOR_AUTO_SAVE = booleanPreferencesKey("editor_auto_save")
        val EDITOR_HIGHLIGHT_LINE = booleanPreferencesKey("editor_highlight_line")
        val EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val EDITOR_USE_SPACES = booleanPreferencesKey("editor_use_spaces")
        val EDITOR_MINIMAP = booleanPreferencesKey("editor_minimap")
        // Workspace
        val OPEN_LAST_PROJECT = booleanPreferencesKey("open_last_project")
        val WORKSPACE_AUTO_SAVE = booleanPreferencesKey("workspace_auto_save")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val RESTORE_LAYOUT = booleanPreferencesKey("restore_layout")
        // Git
        val GIT_DEFAULT_BRANCH = stringPreferencesKey("git_default_branch")
        val GIT_CONFIRM_DESTRUCTIVE = booleanPreferencesKey("git_confirm_destructive")
        val GIT_AUTO_PUSH = booleanPreferencesKey("git_auto_push")
        val GIT_USE_REPO_DEFAULT_BRANCH = booleanPreferencesKey("git_use_repo_default_branch")
        val GIT_INCLUDE_FORKS = booleanPreferencesKey("git_include_forks")
        val GIT_FETCH_ON_OPEN = booleanPreferencesKey("git_fetch_on_open")
        val GIT_AI_CREATE_BRANCH = booleanPreferencesKey("git_ai_create_branch")
        val GIT_AI_CONFIRM_COMMIT = booleanPreferencesKey("git_ai_confirm_commit")
        val GIT_AI_CREATE_PR = booleanPreferencesKey("git_ai_create_pr")
        val GIT_CONFIRM_MERGE = booleanPreferencesKey("git_confirm_merge")
        val GIT_AI_ACTIONS = booleanPreferencesKey("git_ai_actions")
        val GIT_RELEASE_OPERATIONS = booleanPreferencesKey("git_release_operations")
        val GIT_NOTIFICATIONS = booleanPreferencesKey("git_notifications")
        val GIT_PARTICIPATING_NOTIFICATIONS = booleanPreferencesKey("git_participating_notifications")
        // Build
        val AUTO_BUILD = booleanPreferencesKey("auto_build")
        val BUILD_VARIANT = stringPreferencesKey("build_variant")
        val AUTO_BUILD_RETRY = booleanPreferencesKey("auto_build_retry")
        val BUILD_NOTIFICATIONS = booleanPreferencesKey("build_notifications")
        // Notifications
        val NOTIFY_BUILD_DONE = booleanPreferencesKey("notify_build_done")
        val NOTIFY_BUILD_FAIL = booleanPreferencesKey("notify_build_fail")
        val NOTIFY_AI_DONE = booleanPreferencesKey("notify_ai_done")
        val NOTIFY_AI_FAIL = booleanPreferencesKey("notify_ai_fail")
        val NOTIFY_GIT_DONE = booleanPreferencesKey("notify_git_done")
        val NOTIFY_GIT_FAIL = booleanPreferencesKey("notify_git_fail")
        val NOTIFY_ERRORS = booleanPreferencesKey("notify_errors")
        val NOTIFY_DOWNLOADS = booleanPreferencesKey("notify_downloads")
        // Privacy
        val ANALYTICS = booleanPreferencesKey("analytics")
        val CRASH_REPORTING = booleanPreferencesKey("crash_reporting")
        // Advanced
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val EXPERIMENTAL = booleanPreferencesKey("experimental")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            backgroundSync = prefs[Keys.BACKGROUND_SYNC] ?: true,
            amoledMode = prefs[Keys.AMOLED] ?: false,
            accentColorHex = prefs[Keys.ACCENT_COLOR] ?: "#0B72B9",
            uiDensity = prefs[Keys.UI_DENSITY]
                ?.let { runCatching { UiDensity.valueOf(it) }.getOrNull() }
                ?: UiDensity.COMFORTABLE,
            animationLevel = prefs[Keys.ANIMATION_LEVEL]
                ?.let { runCatching { AnimationLevel.valueOf(it) }.getOrNull() }
                ?: AnimationLevel.FULL,
            fontSize = prefs[Keys.FONT_SIZE]
                ?.let { runCatching { FontSize.valueOf(it) }.getOrNull() }
                ?: FontSize.DEFAULT,
            fontFamily = prefs[Keys.FONT_FAMILY]
                ?.let { runCatching { FontFamilyOption.valueOf(it) }.getOrNull() }
                ?: FontFamilyOption.DEFAULT,
            hapticFeedback = prefs[Keys.HAPTICS] ?: true,
            syncFrequency = prefs[Keys.SYNC_FREQUENCY]
                ?.let { runCatching { SyncFrequency.valueOf(it) }.getOrNull() }
                ?: SyncFrequency.HOUR,
            restoreLastLocation = prefs[Keys.RESTORE_LAST_LOCATION] ?: true,
            defaultProviderId = prefs[Keys.DEFAULT_PROVIDER] ?: "gemini",
            defaultModelId = prefs[Keys.DEFAULT_MODEL] ?: "",
            modelTemperature = prefs[Keys.MODEL_TEMPERATURE] ?: 0.7f,
            modelTopP = prefs[Keys.MODEL_TOP_P] ?: 1.0f,
            modelMaxOutputTokens = prefs[Keys.MODEL_MAX_TOKENS] ?: 4000,
            modelContextWindow = prefs[Keys.MODEL_CONTEXT] ?: 128_000,
            agentMode = prefs[Keys.AGENT_MODE] ?: false,
            autoToolExecution = prefs[Keys.AUTO_TOOL_EXEC] ?: false,
            confirmDangerousActions = prefs[Keys.CONFIRM_DANGEROUS] ?: true,
            maxAgentIterations = prefs[Keys.MAX_AGENT_ITER] ?: 10,
            autoErrorFixing = prefs[Keys.AUTO_ERROR_FIX] ?: true,
            autoBuildRetry = prefs[Keys.AUTO_BUILD_RETRY_AGENT] ?: false,
            aiResponseStyle = prefs[Keys.AI_RESPONSE_STYLE] ?: "concise",
            editorFontSize = prefs[Keys.EDITOR_FONT_SIZE] ?: 14,
            editorLineNumbers = prefs[Keys.EDITOR_LINE_NUMBERS] ?: true,
            editorWordWrap = prefs[Keys.EDITOR_WORD_WRAP] ?: false,
            editorSyntaxHighlighting = prefs[Keys.EDITOR_SYNTAX] ?: true,
            editorBracketMatching = prefs[Keys.EDITOR_BRACKET] ?: true,
            editorAutoIndent = prefs[Keys.EDITOR_AUTO_INDENT] ?: true,
            editorAutoSave = prefs[Keys.EDITOR_AUTO_SAVE] ?: true,
            editorHighlightCurrentLine = prefs[Keys.EDITOR_HIGHLIGHT_LINE] ?: true,
            editorTabSize = prefs[Keys.EDITOR_TAB_SIZE] ?: 4,
            editorUseSpaces = prefs[Keys.EDITOR_USE_SPACES] ?: true,
            editorMinimap = prefs[Keys.EDITOR_MINIMAP] ?: false,
            openLastProject = prefs[Keys.OPEN_LAST_PROJECT] ?: true,
            workspaceAutoSave = prefs[Keys.WORKSPACE_AUTO_SAVE] ?: true,
            confirmBeforeDelete = prefs[Keys.CONFIRM_DELETE] ?: true,
            restoreWorkspaceLayout = prefs[Keys.RESTORE_LAYOUT] ?: true,
            gitDefaultBranch = prefs[Keys.GIT_DEFAULT_BRANCH] ?: "main",
            gitConfirmDestructive = prefs[Keys.GIT_CONFIRM_DESTRUCTIVE] ?: true,
            gitAutoPush = prefs[Keys.GIT_AUTO_PUSH] ?: false,
            gitUseRepositoryDefaultBranch = prefs[Keys.GIT_USE_REPO_DEFAULT_BRANCH] ?: true,
            gitIncludeForks = prefs[Keys.GIT_INCLUDE_FORKS] ?: true,
            gitFetchOnOpen = prefs[Keys.GIT_FETCH_ON_OPEN] ?: false,
            gitAiCreateBranch = prefs[Keys.GIT_AI_CREATE_BRANCH] ?: true,
            gitAiConfirmCommit = prefs[Keys.GIT_AI_CONFIRM_COMMIT] ?: true,
            gitAiCreatePullRequest = prefs[Keys.GIT_AI_CREATE_PR] ?: true,
            gitConfirmMerge = prefs[Keys.GIT_CONFIRM_MERGE] ?: true,
            gitAiActionsEnabled = prefs[Keys.GIT_AI_ACTIONS] ?: true,
            gitReleaseOperationsEnabled = prefs[Keys.GIT_RELEASE_OPERATIONS] ?: true,
            gitNotificationsEnabled = prefs[Keys.GIT_NOTIFICATIONS] ?: true,
            gitParticipatingNotifications = prefs[Keys.GIT_PARTICIPATING_NOTIFICATIONS] ?: true,
            autoBuild = prefs[Keys.AUTO_BUILD] ?: false,
            buildVariant = prefs[Keys.BUILD_VARIANT] ?: "debug",
            buildNotifications = prefs[Keys.BUILD_NOTIFICATIONS] ?: true,
            notifyBuildCompleted = prefs[Keys.NOTIFY_BUILD_DONE] ?: true,
            notifyBuildFailed = prefs[Keys.NOTIFY_BUILD_FAIL] ?: true,
            notifyAITaskCompleted = prefs[Keys.NOTIFY_AI_DONE] ?: true,
            notifyAITaskFailed = prefs[Keys.NOTIFY_AI_FAIL] ?: true,
            notifyGitCompleted = prefs[Keys.NOTIFY_GIT_DONE] ?: true,
            notifyGitFailed = prefs[Keys.NOTIFY_GIT_FAIL] ?: true,
            notifyAppErrors = prefs[Keys.NOTIFY_ERRORS] ?: true,
            notifyDownloads = prefs[Keys.NOTIFY_DOWNLOADS] ?: true,
            analyticsEnabled = prefs[Keys.ANALYTICS] ?: false,
            crashReportingEnabled = prefs[Keys.CRASH_REPORTING] ?: false,
            debugMode = prefs[Keys.DEBUG_MODE] ?: false,
            experimentalFeatures = prefs[Keys.EXPERIMENTAL] ?: false
        )
    }

    // ── Original setters ──────────────────────────────────────────────────
    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
    suspend fun setDynamicColor(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }
    suspend fun setBackgroundSync(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.BACKGROUND_SYNC] = enabled }
    }

    // ── Appearance setters ──────────────────────────────────────────────
    suspend fun setAmoledMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AMOLED] = enabled }
    }
    suspend fun setAccentColor(hex: String) {
        context.appSettingsDataStore.edit { it[Keys.ACCENT_COLOR] = hex }
    }

    /**
     * One-time upgrade for installs that were already running before the
     * default accent color changed from "#5849E8" (Signal Indigo) to
     * "#0B72B9" (Sky Blue). A fresh install never writes ACCENT_COLOR until
     * the user picks one, so it already reads the new Kotlin default — but
     * an *existing* install already has the old default persisted to disk,
     * and a stored value always wins over a code default. This runs once
     * (guarded by ACCENT_MIGRATED_TO_SKY) and only touches the color if it's
     * still exactly the legacy default, so an explicit later re-selection of
     * "Indigo" from the picker is never overwritten.
     */
    suspend fun migrateLegacyAccentDefault() {
        context.appSettingsDataStore.edit { prefs ->
            val alreadyMigrated = prefs[Keys.ACCENT_MIGRATED_TO_SKY] ?: false
            if (!alreadyMigrated) {
                if (prefs[Keys.ACCENT_COLOR] == "#5849E8") {
                    prefs[Keys.ACCENT_COLOR] = "#0B72B9"
                }
                prefs[Keys.ACCENT_MIGRATED_TO_SKY] = true
            }
        }
    }
    suspend fun setUiDensity(density: UiDensity) {
        context.appSettingsDataStore.edit { it[Keys.UI_DENSITY] = density.name }
    }
    suspend fun setAnimationLevel(level: AnimationLevel) {
        context.appSettingsDataStore.edit { it[Keys.ANIMATION_LEVEL] = level.name }
    }
    suspend fun setFontSize(size: FontSize) {
        context.appSettingsDataStore.edit { it[Keys.FONT_SIZE] = size.name }
    }
    suspend fun setFontFamily(value: FontFamilyOption) {
        context.appSettingsDataStore.edit { it[Keys.FONT_FAMILY] = value.name }
    }
    suspend fun setHapticFeedback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.HAPTICS] = enabled }
    }
    suspend fun setSyncFrequency(value: SyncFrequency) {
        context.appSettingsDataStore.edit { it[Keys.SYNC_FREQUENCY] = value.name }
    }
    suspend fun setRestoreLastLocation(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.RESTORE_LAST_LOCATION] = enabled }
    }

    // ── Models setters ──────────────────────────────────────────────────
    suspend fun setDefaultProvider(id: String) {
        context.appSettingsDataStore.edit { it[Keys.DEFAULT_PROVIDER] = id }
    }
    suspend fun setDefaultModel(id: String) {
        context.appSettingsDataStore.edit { it[Keys.DEFAULT_MODEL] = id }
    }
    suspend fun setModelTemperature(value: Float) {
        context.appSettingsDataStore.edit { it[Keys.MODEL_TEMPERATURE] = value }
    }
    suspend fun setModelTopP(value: Float) {
        context.appSettingsDataStore.edit { it[Keys.MODEL_TOP_P] = value }
    }
    suspend fun setModelMaxTokens(value: Int) {
        context.appSettingsDataStore.edit { it[Keys.MODEL_MAX_TOKENS] = value }
    }
    suspend fun setModelContext(value: Int) {
        context.appSettingsDataStore.edit { it[Keys.MODEL_CONTEXT] = value }
    }

    // ── Agent setters ───────────────────────────────────────────────────
    suspend fun setAgentMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AGENT_MODE] = enabled }
    }
    suspend fun setAutoToolExecution(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_TOOL_EXEC] = enabled }
    }
    suspend fun setConfirmDangerousActions(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.CONFIRM_DANGEROUS] = enabled }
    }
    suspend fun setMaxAgentIterations(value: Int) {
        context.appSettingsDataStore.edit { it[Keys.MAX_AGENT_ITER] = value }
    }
    suspend fun setAutoErrorFixing(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_ERROR_FIX] = enabled }
    }
    suspend fun setAutoBuildRetry(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_BUILD_RETRY_AGENT] = enabled }
    }
    suspend fun setAiResponseStyle(style: String) {
        context.appSettingsDataStore.edit { it[Keys.AI_RESPONSE_STYLE] = style }
    }

    // ── Editor setters ──────────────────────────────────────────────────
    suspend fun setEditorFontSize(value: Int) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = value }
    }
    suspend fun setEditorLineNumbers(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_LINE_NUMBERS] = enabled }
    }
    suspend fun setEditorWordWrap(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_WORD_WRAP] = enabled }
    }
    suspend fun setEditorSyntaxHighlighting(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_SYNTAX] = enabled }
    }
    suspend fun setEditorBracketMatching(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_BRACKET] = enabled }
    }
    suspend fun setEditorAutoIndent(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_AUTO_INDENT] = enabled }
    }
    suspend fun setEditorAutoSave(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_AUTO_SAVE] = enabled }
    }
    suspend fun setEditorHighlightCurrentLine(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_HIGHLIGHT_LINE] = enabled }
    }
    suspend fun setEditorTabSize(value: Int) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_TAB_SIZE] = value }
    }
    suspend fun setEditorUseSpaces(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_USE_SPACES] = enabled }
    }
    suspend fun setEditorMinimap(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EDITOR_MINIMAP] = enabled }
    }

    // ── Workspace setters ───────────────────────────────────────────────
    suspend fun setOpenLastProject(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.OPEN_LAST_PROJECT] = enabled }
    }
    suspend fun setWorkspaceAutoSave(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.WORKSPACE_AUTO_SAVE] = enabled }
    }
    suspend fun setConfirmBeforeDelete(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.CONFIRM_DELETE] = enabled }
    }
    suspend fun setRestoreWorkspaceLayout(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.RESTORE_LAYOUT] = enabled }
    }

    // ── Git setters ─────────────────────────────────────────────────────
    suspend fun setGitDefaultBranch(value: String) {
        context.appSettingsDataStore.edit { it[Keys.GIT_DEFAULT_BRANCH] = value }
    }
    suspend fun setGitConfirmDestructive(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_CONFIRM_DESTRUCTIVE] = enabled }
    }
    suspend fun setGitAutoPush(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_AUTO_PUSH] = enabled }
    }
    suspend fun setGitUseRepositoryDefaultBranch(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_USE_REPO_DEFAULT_BRANCH] = enabled }
    }
    suspend fun setGitIncludeForks(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_INCLUDE_FORKS] = enabled }
    }
    suspend fun setGitFetchOnOpen(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_FETCH_ON_OPEN] = enabled }
    }
    suspend fun setGitAiCreateBranch(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_AI_CREATE_BRANCH] = enabled }
    }
    suspend fun setGitAiConfirmCommit(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_AI_CONFIRM_COMMIT] = enabled }
    }
    suspend fun setGitAiCreatePullRequest(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_AI_CREATE_PR] = enabled }
    }
    suspend fun setGitConfirmMerge(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_CONFIRM_MERGE] = enabled }
    }
    suspend fun setGitAiActionsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_AI_ACTIONS] = enabled }
    }
    suspend fun setGitReleaseOperationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_RELEASE_OPERATIONS] = enabled }
    }
    suspend fun setGitNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_NOTIFICATIONS] = enabled }
    }
    suspend fun setGitParticipatingNotifications(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.GIT_PARTICIPATING_NOTIFICATIONS] = enabled }
    }

    // ── Build setters ───────────────────────────────────────────────────
    suspend fun setAutoBuild(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_BUILD] = enabled }
    }
    suspend fun setBuildVariant(value: String) {
        context.appSettingsDataStore.edit { it[Keys.BUILD_VARIANT] = value }
    }
    suspend fun setAutoBuildRetryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_BUILD_RETRY] = enabled }
    }
    suspend fun setBuildNotifications(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.BUILD_NOTIFICATIONS] = enabled }
    }

    // ── Notifications setters ───────────────────────────────────────────
    suspend fun setNotifyBuildCompleted(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_BUILD_DONE] = enabled }
    }
    suspend fun setNotifyBuildFailed(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_BUILD_FAIL] = enabled }
    }
    suspend fun setNotifyAITaskCompleted(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_AI_DONE] = enabled }
    }
    suspend fun setNotifyAITaskFailed(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_AI_FAIL] = enabled }
    }
    suspend fun setNotifyGitCompleted(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_GIT_DONE] = enabled }
    }
    suspend fun setNotifyGitFailed(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_GIT_FAIL] = enabled }
    }
    suspend fun setNotifyAppErrors(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_ERRORS] = enabled }
    }
    suspend fun setNotifyDownloads(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.NOTIFY_DOWNLOADS] = enabled }
    }

    // ── Privacy setters ────────────────────────────────────────────────
    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.ANALYTICS] = enabled }
    }
    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.CRASH_REPORTING] = enabled }
    }

    // ── Advanced setters ────────────────────────────────────────────────
    suspend fun setDebugMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }
    suspend fun setExperimentalFeatures(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.EXPERIMENTAL] = enabled }
    }

    /**
     * PRD §29 — Reset all non-credential settings to safe defaults.
     * API keys are NOT touched (PRD §29 — credential reset is separate).
     */
    suspend fun resetAllSettings() {
        context.appSettingsDataStore.edit { it.clear() }
    }
}
