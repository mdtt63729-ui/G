package com.gitofy.feature.authentication

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // PRD: Smooth premium entrance animation state
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        animationStarted = true
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status == AuthenticationStatus.Success) {
            onAuthenticated()
        }
    }

    Scaffold(
        containerColor = Color.White
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
                // === Logo container — light blue squircle with padlock ===
                val logoScale by animateFloatAsState(
                    targetValue = if (animationStarted) 1f else 0.5f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "logoScale"
                )
                val logoAlpha by animateFloatAsState(
                    targetValue = if (animationStarted) 1f else 0f,
                    animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                    label = "logoAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure GitHub authentication",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // === GITOFY text ===
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 100),
                        initialOffsetY = { it / 4 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
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
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 200),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
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
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 300),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    Text(
                        text = "Connect your GitHub account to manage repositories, CI, releases and developer tools from one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    )
                }

                // === Input field ===
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 450)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 450),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
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
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color.White,
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
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 550)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 550),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
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
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 650)) + slideInVertically(
                        animationSpec = tween(400, delayMillis = 650),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    AnimatedContent(
                        targetState = uiState.status,
                        transitionSpec = {
                            fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
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
                                    val checkScale by animateFloatAsState(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "checkScale"
                                    )
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Authentication successful",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.scale(checkScale).size(20.dp)
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
                                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
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
                AnimatedVisibility(
                    visible = animationStarted,
                    enter = fadeIn(tween(400, delayMillis = 750)),
                    exit = fadeOut(tween(100))
                ) {
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
