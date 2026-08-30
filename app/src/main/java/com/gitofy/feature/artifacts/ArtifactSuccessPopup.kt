package com.gitofy.feature.artifacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Metadata describing a successfully produced artifact, surfaced inside
 * [ArtifactSuccessPopup].
 *
 * @property appName           Display name of the application.
 * @property versionName       Version name (e.g. "1.4.2").
 * @property buildType         Build variant (e.g. "debug", "release").
 * @property apkGenerated      Whether the APK was successfully generated.
 * @property artifactUploaded  Whether the artifact was uploaded to storage.
 */
data class ArtifactSuccessInfo(
    val appName: String,
    val versionName: String,
    val buildType: String,
    val apkGenerated: Boolean,
    val artifactUploaded: Boolean,
)

/**
 * Material 3 dialog shown when an artifact/APK build succeeds.
 *
 * PRD §47-48: Artifact Success Popup.
 *
 * @param artifactInfo Metadata about the successful artifact.
 * @param onInstall     Invoked when the user requests APK installation.
 * @param onDismiss     Invoked when the dialog is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactSuccessPopup(
    artifactInfo: ArtifactSuccessInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconPlaceholder()
                Column {
                    Text(
                        text = "Build Successful",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = artifactInfo.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(label = "Version", value = artifactInfo.versionName)
                InfoRow(label = "Build Type", value = artifactInfo.buildType)

                CheckmarkRow(
                    icon = Icons.Filled.Android,
                    label = "APK Generated",
                    checked = artifactInfo.apkGenerated,
                )
                CheckmarkRow(
                    icon = Icons.Filled.CloudUpload,
                    label = "Artifact Uploaded",
                    checked = artifactInfo.artifactUploaded,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(12.dp),
                enabled = artifactInfo.apkGenerated,
            ) {
                Text(text = "Install", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
private fun AppIconPlaceholder() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

@Composable
private fun CheckmarkRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
