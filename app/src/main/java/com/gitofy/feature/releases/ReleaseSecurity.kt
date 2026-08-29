package com.gitofy.feature.releases

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release Security — PRD v6.0 Section 90.
 * Never automatically publish releases. Require explicit user confirmation.
 */
@Singleton
class ReleaseSecurityGuard @Inject constructor() {

    data class PublishConfirmation(
        val requiresConfirmation: Boolean = true,
        val message: String = "Publish this release? This action will make it visible to users."
    )

    fun requirePublishConfirmation(): PublishConfirmation = PublishConfirmation()
}

/**
 * Release Readiness Checklist — PRD v7.0 Section 119.
 * Before publishing, check: CI, PRs, Artifact, Integrity, Workflow, Notes, Issues.
 */
@Singleton
class ReleaseReadinessChecker @Inject constructor() {

    fun check(
        ciPassing: Boolean,
        requiredPRsMerged: Boolean,
        artifactAvailable: Boolean,
        artifactVerified: Boolean,
        noBlockingFailures: Boolean,
        releaseNotesPrepared: Boolean,
        openCriticalIssues: Boolean
    ): com.gitofy.domain.model.ReleaseReadiness {
        val blockers = mutableListOf<String>()
        if (!ciPassing) blockers.add("CI is not passing")
        if (!requiredPRsMerged) blockers.add("Required PRs not merged")
        if (!artifactAvailable) blockers.add("Release artifact not available")
        if (!artifactVerified) blockers.add("Artifact integrity not verified")
        if (!noBlockingFailures) blockers.add("Blocking workflow failures exist")
        if (!releaseNotesPrepared) blockers.add("Release notes not prepared")
        if (openCriticalIssues) blockers.add("Open critical issue")

        return com.gitofy.domain.model.ReleaseReadiness(
            isReady = blockers.isEmpty(),
            ciPassing = ciPassing,
            requiredPRsMerged = requiredPRsMerged,
            artifactAvailable = artifactAvailable,
            artifactVerified = artifactVerified,
            noBlockingFailures = noBlockingFailures,
            releaseNotesPrepared = releaseNotesPrepared,
            blockingIssues = blockers
        )
    }
}

/**
 * Artifact-to-Release Pipeline — PRD v6.0 Section 82.
 * Workflow → Successful Run → Artifact → Select → Create Release → Attach Asset → Publish.
 */
@Singleton
class ArtifactToReleasePipeline @Inject constructor() {

    data class PipelineState(
        val stage: PipelineStage,
        val artifactName: String?,
        val releaseId: Long?,
        val isComplete: Boolean
    )

    enum class PipelineStage {
        SELECT_RUN, SELECT_ARTIFACT, CREATE_RELEASE, ATTACH_ASSET, PREVIEW, PUBLISH, COMPLETE
    }
}
