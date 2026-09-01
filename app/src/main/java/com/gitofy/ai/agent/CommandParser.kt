package com.gitofy.ai.agent

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandParser @Inject constructor() {

    data class ParsedCommand(
        val repository: String? = null,
        val branch: String? = null,
        val targetFeature: String? = null,
        val targetFiles: List<String> = emptyList(),
        val requestedModification: String = "",
        val buildRequired: Boolean = false,
        val workflowRequired: Boolean = false,
        val commitRequired: Boolean = true,
        val rawCommand: String = "",
        val toolName: String? = null,
        val parameters: Map<String, String> = emptyMap()
    )

    fun parse(command: String): ParsedCommand {
        val lower = command.lowercase().trim()

        val repository = extractRepository(lower, command)
        val buildRequired = lower.contains("build") || lower.contains("apk") ||
            lower.contains("compile") || lower.contains("assemble")
        val workflowRequired = lower.contains("workflow") || lower.contains("action") ||
            lower.contains("ci") || lower.contains("run")
        val targetFeature = extractFeature(lower)
        val modification = extractModification(lower)

        return ParsedCommand(
            repository = repository,
            targetFeature = targetFeature,
            requestedModification = modification,
            buildRequired = buildRequired,
            workflowRequired = workflowRequired,
            commitRequired = true,
            rawCommand = command
        )
    }

    private fun extractRepository(lower: String, original: String): String? {
        val patterns = listOf("-এর ", "-এ ", " repository", " repo ", " project")
        for (pattern in patterns) {
            val idx = lower.indexOf(pattern)
            if (idx > 0) {
                val before = lower.substring(0, idx).trim()
                val lastWord = before.split(" ").lastOrNull()
                if (lastWord != null && lastWord.length > 1) {
                    return lastWord.trim()
                }
            }
        }
        val words = original.split(" ", ",", "?", "!", ".")
        for (word in words) {
            val cleaned = word.trim()
            if (cleaned.length > 2 && cleaned.any { it.isUpperCase() } && cleaned.any { it.isLowerCase() }) {
                return cleaned
            }
            if (cleaned.contains("-") && cleaned.length > 3) {
                return cleaned
            }
        }
        return null
    }

    private fun extractFeature(lower: String): String? {
        val features = listOf(
            "player" to "Player",
            "home" to "Home Screen",
            "settings" to "Settings Screen",
            "theme" to "Theme",
            "workflow" to "Workflow",
            "build" to "Build System",
            "navigation" to "Navigation",
            "login" to "Login Screen",
            "repository" to "Repository",
            "upload" to "Upload",
            "inbox" to "Inbox",
            "search" to "Search",
            "ai" to "AI Screen"
        )
        for ((keyword, feature) in features) {
            if (lower.contains(keyword)) return feature
        }
        return null
    }

    private fun extractModification(lower: String): String {
        return when {
            lower.contains("fix") -> "Fix"
            lower.contains("update") || lower.contains("improve") -> "Update"
            lower.contains("upgrade") -> "Upgrade"
            lower.contains("change") -> "Change"
            lower.contains("add") -> "Add"
            lower.contains("remove") -> "Remove"
            lower.contains("material 3") || lower.contains("material3") -> "Material 3 Modernization"
            lower.contains("build") -> "Build"
            lower.contains("run") -> "Run"
            lower.contains("download") -> "Download"
            else -> "Modify"
        }
    }
}
