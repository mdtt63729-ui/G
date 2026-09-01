package com.gitofy.feature.authentication

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.PremiumButtonLoader
import com.gitofy.R
import com.gitofy.core.designsystem.motion.GITOFYStaggeredVisibility
import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current


    LaunchedEffect(uiState.status) {
        if (uiState.status == AuthenticationStatus.Success) {
            onAuthenticated()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val contentWidth = minOf(maxWidth - 48.dp, 480.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = contentWidth)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // === Original app icon — the real launcher icon (purple tile
                // + G/Octocat mark), not the unrelated ic_gito_logo asset —
                // with a buttery bouncy scale + fade entrance. ===
                GITOFYStaggeredVisibility(
                    index = 0,
                    enter = com.gitofy.core.designsystem.motion.gitofyBouncyIconEnter,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_adaptive),
                        contentDescription = "GITOFY app icon",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(26.dp))
                    )
                }

                // === GITOFY text ===
                GITOFYStaggeredVisibility(
    index = 1,                ) {
                    Text(
                        text = "GITOFY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                // === Title ===
                GITOFYStaggeredVisibility(
    index = 2,                ) {
                    Text(
                        text = "Welcome to your GitHub workspace",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // === Subtitle ===
                GITOFYStaggeredVisibility(
    index = 3,                ) {
                    Text(
                        text = "Connect your GitHub account to manage repositories, CI, releases and developer tools from one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    )
                }

                // === Input field ===
                GITOFYStaggeredVisibility(
    index = 4,                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                        // Custom outlined text field with blue border
                        OutlinedTextField(
                            value = uiState.token,
                            onValueChange = viewModel::onTokenChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .semantics {
                                    contentDescription = "GitHub Personal Access Token"
                                },
                            placeholder = {
                                Text(
                                    "GitHub Personal Access Token",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (uiState.token.isNotBlank() && !uiState.isLoading) {
                                        focusManager.clearFocus()
                                        viewModel.authenticate()
                                    }
                                }
                            ),
                            isError = uiState.status == AuthenticationStatus.Error,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                errorContainerColor = MaterialTheme.colorScheme.surface,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showToken = !showToken },
                                    enabled = !uiState.isLoading
                                ) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showToken) "Hide token" else "Show token",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        // Helper text below input
                        Text(
                            text = uiState.error ?: "Use a token with the permissions required by your GitHub workflow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.status == AuthenticationStatus.Error)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                }

                // === Security info box ===
                GITOFYStaggeredVisibility(
    index = 5,                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Your credential is stored in secure Android storage and is never rendered in logs, analytics, or error messages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // === Button / Success ===
                GITOFYStaggeredVisibility(
    index = 6,                ) {
                    AnimatedContent(
                        targetState = uiState.status,
                        transitionSpec = {
                            gitofySlideFadeEnter.togetherWith(gitofySlideFadeExit)
                        },
                        label = "auth-state",
                        modifier = Modifier.padding(top = 24.dp)
                    ) { status ->
                        when (status) {
                            AuthenticationStatus.Success -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Authentication successful",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Connected to GitHub",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            else -> {
                                // Premium button with blue background
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.authenticate()
                                    },
                                    enabled = uiState.token.isNotBlank() && !uiState.isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    if (uiState.isLoading) {
                                        PremiumButtonLoader(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            circleSize = 22.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Connecting…",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    } else {
                                        Text(
                                            "Continue with GitHub",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // === Bottom disclaimer ===
                GITOFYStaggeredVisibility(
    index = 7,                ) {
                    Text(
                        text = "Authentication is required only to access your GitHub account and repositories.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}
