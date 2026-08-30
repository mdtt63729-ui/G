package com.gitofy.ai.model

/**
 * AI Task Classification — PRD Section 16.
 * Every AI request should first be classified.
 */
enum class AITaskType {
    GENERAL_QA,
    CODE_EXPLANATION,
    CODE_GENERATION,
    CODE_COMPLETION,
    CODE_REFACTORING,
    CODE_REVIEW,
    BUG_ANALYSIS,
    BUG_FIX,
    ERROR_ANALYSIS,
    BUILD_FAILURE_ANALYSIS,
    LOG_ANALYSIS,
    PROJECT_ANALYSIS,
    REPOSITORY_ANALYSIS,
    ARCHITECTURE_REVIEW,
    ARCHITECTURE_DESIGN,
    PATCH_GENERATION,
    TEST_GENERATION,
    PR_GENERATION,
    COMMIT_MESSAGE,
    WORKFLOW_ANALYSIS,
    WORKFLOW_GENERATION,
    DOCUMENTATION,
    VISION_UI_ANALYSIS,
    IMAGE_ANALYSIS,
    DOCUMENT_ANALYSIS,
    YAML_GENERATION,
    TRANSLATION,
    INDIAN_LANGUAGE_ASSISTANCE,
    UI_ANALYSIS
}

/**
 * AI Confidence Level — PRD Section 29.
 * AI must distinguish: Confirmed, Likely, Possible, Unknown.
 * It must not present speculation as fact.
 */
enum class AIConfidenceLevel(val displayName: String) {
    CONFIRMED("Confirmed"),
    HIGH("Likely"),
    MEDIUM("Possible"),
    LOW("Low Confidence"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(s: String): AIConfidenceLevel =
            entries.find { it.name.equals(s, true) } ?: UNKNOWN
    }
}

/**
 * AI Diagnosis Quality — PRD Section 29.
 */
data class AIDiagnosis(
    val rootCause: String?,
    val evidence: List<String>,
    val affectedFiles: List<String>,
    val confidence: AIConfidenceLevel,
    val recommendedFix: String?,
    val potentialSideEffects: List<String>,
    val isObserved: Boolean,
    val isInferred: Boolean,
    val isSuggested: Boolean
) {
    companion object {
        fun unknown(evidence: List<String>): AIDiagnosis = AIDiagnosis(
            rootCause = null, evidence = evidence, affectedFiles = emptyList(),
            confidence = AIConfidenceLevel.UNKNOWN, recommendedFix = null,
            potentialSideEffects = emptyList(),
            isObserved = false, isInferred = false, isSuggested = false
        )
    }
}

/**
 * AI Structured Output — PRD Section 48.
 * Where possible, use structured responses for actions.
 * Free-form model text must not directly trigger application actions.
 */
data class AIStructuredOutput(
    val analysis: String,
    val confidence: AIConfidenceLevel,
    val evidence: List<String>,
    val recommendations: List<String>,
    val proposedActions: List<com.gitofy.ai.action.AIActionType> = emptyList()
)
