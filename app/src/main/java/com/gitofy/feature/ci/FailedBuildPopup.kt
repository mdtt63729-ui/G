package com.gitofy.feature.ci

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Holds the contextual information about a failed CI build that should be
 * surfaced inside [FailedBuildPopup].
 *
 * @property appName      Display name of the application whose build failed.
 * @property workflowName Name of the GitHub Actions workflow that ran.
 * @property failedJob    The job inside the workflow where the failure occurred.
 * @property failedStep   The specific step within the job that failed.
 * @property logSnippet   A short excerpt of the build log useful for quick triage.
 */
data class FailedBuildInfo(
    val appName: String,
    val workflowName: String,
    val failedJob: String,
    val failedStep: String,
    val logSnippet: String,
)

/**
 * Material 3 dialog shown when a CI build fails.
 *
 * PRD §44-46: Failed Build Popup.
 *
 * @param buildInfo  Contextual metadata about the failed build.
 * @param onViewLog  Invoked when the user requests the full build log.
 * @param onCopyLog  Invoked when the user wants to copy the log snippet.
 * @param onAskGito  Invoked when the user asks GITO for remediation help.
 * @param onDismiss  Invoked when the dialog is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FailedBuildPopup(
    buildInfo: FailedBuildInfo,
    onViewLog: () -> Unit,
    onCopyLog: () -> Unit,
    onAskGito: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Build Failed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoRow(label = "App Name", value = buildInfo.appName)
                InfoRow(label = "Workflow", value = buildInfo.workflowName)
                InfoRow(label = "Failed Job", value = buildInfo.failedJob)
                InfoRow(label = "Failed Step", value = buildInfo.failedStep)

                if (buildInfo.logSnippet.isNotBlank()) {
                    Text(
                        text = buildInfo.logSnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAskGito,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = "Ask GITO", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onViewLog,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = "View Full Log")
                }
                OutlinedButton(
                    onClick = onCopyLog,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = "Copy Log")
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Close")
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
