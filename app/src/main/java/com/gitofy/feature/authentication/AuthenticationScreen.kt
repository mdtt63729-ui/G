package com.gitofy.feature.authentication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }

    if (uiState.error == null && !uiState.isLoading && uiState.token.isNotEmpty()) {
        // Check if auth succeeded — the navigation happens via onAuthenticated callback
    }

    // Detect successful auth
    LaunchedEffect(uiState.isLoading, uiState.error) {
        if (!uiState.isLoading && uiState.error == null && viewModel.uiState.value.token.isNotEmpty()) {
            // Auth succeeded if we got here without error after loading
        }
    }

    // Monitor for auth success from the parent
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = LocalSpacing.current.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(LocalSpacing.current.lg))

            Text(
                text = "Sign in to GitHub",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))

            Text(
                text = "Enter your GitHub Personal Access Token to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(LocalSpacing.current.xxl))

            // Token input
            OutlinedTextField(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Personal Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    autoCorrectEnabled = false
                ),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token"
                        )
                    }
                },
                isError = uiState.error != null,
                supportingText = {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(LocalSpacing.current.lg))

            // Secure notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Your token is stored securely using Android Keystore. It never appears in logs or analytics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(LocalSpacing.current.lg)
                )
            }

            Spacer(modifier = Modifier.height(LocalSpacing.current.lg))

            GITOFYButton(
                text = "Sign In",
                onClick = { viewModel.authenticate() },
                loading = uiState.isLoading,
                fullWidth = true
            )

            // Handle success — when error is null and not loading, signal success
            if (!uiState.isLoading && uiState.error == null) {
                // Will be triggered by the ViewModel state — handled by LaunchedEffect below
            }
        }
    }

    // Observe for successful authentication
    val authState by remember { viewModel.uiState }.let { state ->
        mutableStateOf(state)
    }

    // Use a separate trigger — when isLoading goes from true to false without error
    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (wasLoading && !uiState.isLoading && uiState.error == null) {
            onAuthenticated()
        }
        wasLoading = uiState.isLoading
    }
}
