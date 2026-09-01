package com.gitofy.feature.ci

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workflow YAML Validation — PRD v4.5 Section 39.
 * Before saving workflow changes: parse YAML, detect malformed structure,
 * detect common GitHub Actions mistakes, warn about dangerous configuration.
 * Validation must not claim a workflow is fully semantically correct when only syntax was validated.
 */
@Singleton
class WorkflowYamlValidator @Inject constructor() {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val isSyntaxOnlyValidation: Boolean = true
    )

    fun validate(yamlContent: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check for required top-level keys
        if (!yamlContent.contains("on:") && !yamlContent.contains("\"on\":")) {
            errors.add("Missing 'on:' trigger key. Workflow must define when it runs.")
        }
        if (!yamlContent.contains("jobs:")) {
            errors.add("Missing 'jobs:' key. Workflow must define at least one job.")
        }

        // Check for common mistakes
        if (yamlContent.contains("on: push") && !yamlContent.contains("branches:")) {
            warnings.add("Push trigger without branch filter may trigger on all branches.")
        }
        if (yamlContent.contains("\${{") && yamlContent.contains("secrets.")) {
            warnings.add("Secrets reference detected. Ensure secrets are not logged or exposed.")
        }
        if (yamlContent.contains("runs-on: ubuntu-latest") && yamlContent.contains("sudo")) {
            warnings.add("sudo is not available in GitHub Actions runners.")
        }
        if (yamlContent.contains("actions/checkout@v1") || yamlContent.contains("actions/checkout@v2")) {
            warnings.add("Outdated actions/checkout version. Consider upgrading to v4.")
        }
        if (yamlContent.contains("actions/upload-artifact@v1") || yamlContent.contains("actions/upload-artifact@v2")) {
            warnings.add("Outdated actions/upload-artifact version. Consider upgrading to v4.")
        }

        // Check indentation (basic — YAML requires consistent spaces, not tabs)
        if (yamlContent.contains("\t")) {
            errors.add("YAML must use spaces, not tabs for indentation.")
        }

        // Check for missing runs-on
        val jobsSection = yamlContent.substringAfter("jobs:", "")
        if (jobsSection.isNotEmpty() && !jobsSection.contains("runs-on:")) {
            errors.add("Job missing 'runs-on:' key. Each job must specify a runner.")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            isSyntaxOnlyValidation = true
        )
    }
}
