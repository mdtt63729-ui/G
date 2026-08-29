package com.gitofy.ai.agent

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskPlanner @Inject constructor(
    private val commandParser: CommandParser
) {

    fun createPlan(command: String, sessionId: String): List<AgentTask> {
        val parsed = commandParser.parse(command)
        val tasks = mutableListOf<AgentTask>()
        var order = 0

        fun task(title: String, desc: String) = AgentTask(
            id = "${sessionId}_task_${order}",
            sessionId = sessionId,
            title = title,
            description = desc,
            order = order++
        )

        tasks.add(task("Identify repository", "Find and verify repository: ${parsed.repository ?: "auto-detect"}"))
        tasks.add(task("Inspect repository", "Fetch repository structure and identify relevant files"))

        if (parsed.targetFeature != null) {
            tasks.add(task("Inspect ${parsed.targetFeature}", "Read and analyze ${parsed.targetFeature} related files"))
        }

        if (parsed.requestedModification.isNotEmpty()) {
            tasks.add(task("Plan changes", "Generate modification plan based on command"))
            tasks.add(task("Apply changes", "Modify code files as needed"))
            tasks.add(task("Validate changes", "Check syntax and dependencies"))
        }

        if (parsed.buildRequired) {
            tasks.add(task("Run build", "Execute Gradle build"))
            tasks.add(task("Fix build errors", "Self-correct any compilation errors"))
        }

        if (parsed.commitRequired) {
            tasks.add(task("Create commit", "Stage and commit changes"))
            tasks.add(task("Push changes", "Push to remote repository"))
        }

        if (parsed.workflowRequired) {
            tasks.add(task("Run workflow", "Dispatch GitHub Actions workflow"))
            tasks.add(task("Monitor workflow", "Track workflow execution"))
            tasks.add(task("Analyze results", "Check for failures and auto-fix if needed"))
        }

        tasks.add(task("Generate report", "Summarize all changes and results"))

        return tasks
    }
}
