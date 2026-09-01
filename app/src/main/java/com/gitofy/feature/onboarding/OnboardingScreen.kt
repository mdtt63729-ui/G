package com.gitofy.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitofy.R
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gitofy.feature.authentication.AuthenticationViewModel
import com.gitofy.feature.authentication.AuthenticationStatus

private val Bg = Color(0xFFFBF9FF)
private val Navy = Color(0xFF111A3A)
private val Muted = Color(0xFF6C7189)
private val Purple = Color(0xFF5B2BDB)
private val PurpleLight = Color(0xFFF0E9FF)
private val Border = Color(0xFFE6DDF5)
private val Card = Color(0xFFFCFAFF)

@Composable
fun OnboardingScreen(
    onOpenGithub: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onFinish: () -> Unit,
    // FIX: "Configure repository access" and "Apply personalization" used to
    // only flip an internal completion flag with nowhere for the user to
    // actually go — the buttons did nothing visible. They now open the real
    // settings screens that back these steps (GitHub repository defaults,
    // and Appearance) so every onboarding action is backed by working
    // functionality, not a fake checkmark.
    onOpenRepositorySettings: () -> Unit = onOpenGithub,
    onOpenAppearance: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
    // FIX: "Connect GitHub" used to just hop out to the separate Settings
    // screen. The user asked for the Personal Access Token to be entered
    // right here on the onboarding step, so a real AuthenticationViewModel
    // now backs an inline token field — on success we mark the step done
    // and advance to the next onboarding page instead of navigating away.
    authViewModel: AuthenticationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val step = state.currentStep.coerceIn(0, 5)
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authState.status) {
        if (authState.status == AuthenticationStatus.Success) {
            viewModel.markGithub()
            if (step == 0) viewModel.next()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFEFDFF), Bg, Color(0xFFF8F3FF))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        DecorativeBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            StepHeader(step)
            Spacer(Modifier.height(18.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(320)) + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding-page"
            ) { current ->
                OnboardingPage(
                    step = current,
                    state = state,
                    authState = authState,
                    onTokenChange = authViewModel::onTokenChange,
                    onConnectGithub = authViewModel::authenticate,
                    onManageGithub = onOpenGithub,
                    onRepository = {
                        viewModel.markRepository()
                        onOpenRepositorySettings()
                    },
                    onAi = {
                        viewModel.markAi()
                        onOpenAiProviders()
                    },
                    onSync = viewModel::setSync,
                    onAppearance = {
                        viewModel.markAppearance()
                        onOpenAppearance()
                    }
                )
            }

            BottomControls(
                step = step,
                onSkip = {
                    viewModel.skip()
                    onFinish()
                },
                onNext = {
                    if (step == 5) {
                        viewModel.complete()
                        onFinish()
                    } else {
                        viewModel.next()
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Step ${step + 1} of 6", color = Purple, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(6) { index ->
                val completed = index <= step
                val current = index == step
                val width by animateDpAsState(
                    targetValue = if (current) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                    label = "progress-size-$index"
                )
                Box(
                    modifier = Modifier
                        .size(width, 8.dp)
                        .clip(CircleShape)
                        .background(if (completed) Purple else Color(0xFFDCD2F2))
                )
                if (index < 5) {
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(2.dp)
                            .background(if (index < step) Purple else Color(0xFFE8E0F5))
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    step: Int,
    state: OnboardingState,
    authState: com.gitofy.feature.authentication.AuthUiState,
    onTokenChange: (String) -> Unit,
    onConnectGithub: () -> Unit,
    onManageGithub: () -> Unit,
    onRepository: () -> Unit,
    onAi: () -> Unit,
    onSync: (Boolean) -> Unit,
    onAppearance: () -> Unit
) {
    when (step) {
        0 -> StandardPage(
            icon = Icons.Default.Key,
            title = "GitHub Connection",
            description = "Connect Gitofy with your GitHub account using a Personal Access Token."
        ) {
            if (state.githubConnected) {
                StateCard(
                    icon = Icons.Default.Code,
                    statusIcon = Icons.Default.Check,
                    statusColor = Color(0xFF5B2BDB),
                    title = "GitHub is connected",
                    description = "Your GitHub account is ready for Gitofy.",
                    buttonText = "Manage GitHub",
                    buttonIcon = Icons.Default.Code,
                    onButton = onManageGithub,
                    footerIcon = Icons.Default.Lock,
                    footer = "Your token is encrypted and secure"
                )
            } else {
                GithubTokenCard(
                    authState = authState,
                    onTokenChange = onTokenChange,
                    onConnect = onConnectGithub
                )
            }
        }
        1 -> StandardPage(
            icon = Icons.Default.Storage,
            title = "Repository Access",
            description = "Choose the repositories Gitofy should work with."
        ) {
            StateCard(
                icon = Icons.Default.Folder,
                statusIcon = if (state.repositoryConfigured) Icons.Default.Check else Icons.Default.Security,
                statusColor = if (state.repositoryConfigured) Color(0xFF5B2BDB) else Color(0xFF63C9A6),
                title = if (state.repositoryConfigured) "Repositories configured" else "No repositories configured",
                description = if (state.repositoryConfigured) "Gitofy can now work with your selected repositories." else "You haven’t selected any repositories yet. Configure access to get started.",
                buttonText = if (state.repositoryConfigured) "Manage repository access" else "Configure repository access",
                buttonIcon = Icons.Default.Storage,
                onButton = onRepository,
                footerIcon = Icons.Default.Security,
                footer = "You're in control. Access is private and secure."
            )
        }
        2 -> StandardPage(
            icon = Icons.Default.Code,
            title = "AI Provider",
            description = "Connect an AI provider so Gito can work with your repositories."
        ) {
            AIProviderCard(state.apiProviderConfigured, onAi)
        }
        3 -> StandardPage(
            icon = Icons.Default.Sync,
            title = "Background Sync",
            description = "Keep your workspace synchronized automatically."
        ) {
            SyncCard(enabled = state.backgroundSyncEnabled, onCheckedChange = onSync)
        }
        4 -> StandardPage(
            icon = Icons.Default.Palette,
            title = "Personalize Gitofy",
            description = "Choose your theme, motion and interaction preferences."
        ) {
            PersonalizeCard(onAppearance)
        }
        else -> StandardPage(
            icon = Icons.Default.Check,
            title = "You’re All Set",
            description = "Gitofy is ready to go with your configured services."
        ) {
            CompletionCard(state)
        }
    }
}

@Composable
private fun StandardPage(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            OnboardingIllustration(icon, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, color = Navy, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(72.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(Purple))
            Spacer(Modifier.height(15.dp))
            Text(description, color = Muted, fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun OnboardingIllustration(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(122.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFF1EAFF), radius = size.minDimension * 0.42f)
            drawCircle(Color(0xFFE9DEFF).copy(alpha = 0.62f), radius = size.minDimension * 0.31f)
        }
        Icon(icon, null, modifier = Modifier.size(54.dp), tint = Purple)
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.align(Alignment.TopStart).size(15.dp), tint = Color(0xFF9D75EA))
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.align(Alignment.BottomEnd).size(13.dp), tint = Color(0xFFBFA5EF))
    }
}

@Composable
private fun StateCard(
    icon: ImageVector,
    statusIcon: ImageVector,
    statusColor: Color,
    title: String,
    description: String,
    buttonText: String,
    buttonIcon: ImageVector,
    onButton: () -> Unit,
    footerIcon: ImageVector,
    footer: String
) {
    PremiumCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFF0E9FF))
                    drawCircle(Color(0xFFE7DDFB), radius = size.minDimension * 0.36f)
                }
                Icon(icon, null, Modifier.size(56.dp), tint = Color(0xFF7A68A8))
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .padding(7.dp)
                ) { Icon(statusIcon, null, tint = Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(description, color = Muted, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE9E1F4)))
            Spacer(Modifier.height(18.dp))
            PrimaryCta(buttonText, buttonIcon, onButton)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(footerIcon, null, Modifier.size(17.dp), tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text(footer, color = Color(0xFF756A9B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GithubTokenCard(
    authState: com.gitofy.feature.authentication.AuthUiState,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }
    val isLoading = authState.isLoading

    PremiumCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFF0E9FF))
                    drawCircle(Color(0xFFE7DDFB), radius = size.minDimension * 0.36f)
                }
                Icon(Icons.Default.Code, null, Modifier.size(56.dp), tint = Color(0xFF7A68A8))
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE65D67))
                        .padding(7.dp)
                ) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            Text("GitHub is not connected", color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(
                "Paste a Personal Access Token to connect your GitHub account.",
                color = Muted, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = authState.token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Personal Access Token", color = Muted) },
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                isError = authState.status == com.gitofy.feature.authentication.AuthenticationStatus.Error,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple,
                    unfocusedBorderColor = Border,
                    cursorColor = Purple
                ),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }, enabled = !isLoading) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token",
                            tint = Purple
                        )
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                authState.error ?: "Your token is encrypted and stored securely on this device.",
                color = if (authState.status == com.gitofy.feature.authentication.AuthenticationStatus.Error) Color(0xFFE65D67) else Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE9E1F4)))
            Spacer(Modifier.height(18.dp))

            androidx.compose.material3.Button(
                onClick = onConnect,
                enabled = authState.token.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Purple),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting…", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                } else {
                    Icon(Icons.Default.Code, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Connect with GitHub", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(21.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Lock, null, Modifier.size(17.dp), tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text("Your token is encrypted and secure", color = Color(0xFF756A9B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AIProviderCard(configured: Boolean, onConfigure: () -> Unit) {
    PremiumCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFF0E9FF))
                    drawCircle(Color(0xFFE7DDFB), radius = size.minDimension * 0.35f)
                }
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Purple), contentAlignment = Alignment.Center) {
                    Text("AI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(if (configured) Purple else Color(0xFFE65D67)), contentAlignment = Alignment.Center) {
                    Icon(if (configured) Icons.Default.Check else Icons.Default.Close, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(if (configured) "AI provider connected" else "No AI provider connected", color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Connect your preferred AI provider to enable smart code understanding and assistance.", color = Muted, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FeatureCell(Icons.Default.Handshake, "Smarter Assistance", "Get intelligent insights from your code.")
                FeatureDivider()
                FeatureCell(Icons.Default.Lock, "Private & Secure", "Your data stays safe and encrypted.")
                FeatureDivider()
                FeatureCell(Icons.Default.AutoAwesome, "Boost Productivity", "Automate tasks and save your time.")
            }
            Spacer(Modifier.height(20.dp))
            PrimaryCta(if (configured) "Manage AI Provider" else "Configure AI Provider", Icons.Default.Code, onConfigure)
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = Purple)
                Spacer(Modifier.width(7.dp))
                Text("You can change or reconnect any time in Settings.", color = Color(0xFF756A9B), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FeatureCell(icon: ImageVector, title: String, description: String) {
    Column(Modifier.width(0.dp).weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(PurpleLight), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = Purple)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeatureDivider() = Box(Modifier.width(1.dp).height(96.dp).background(Color(0xFFE7DDF3)))

@Composable
private fun SyncCard(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    PremiumCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFF0E9FF)); drawCircle(Color(0xFFE7DDFB), radius = size.minDimension * 0.35f) }
                Icon(Icons.Default.CloudUpload, null, Modifier.size(58.dp), tint = Purple)
            }
            Spacer(Modifier.height(10.dp))
            Text("Background Sync", color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Keep Gitofy synchronized in the background using Android’s scheduled sync service.", color = Muted, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE9E1F4)))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if (enabled) "Background sync enabled" else "Background sync disabled", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(if (enabled) "Gitofy may refresh repository data automatically." else "You can enable it later from Settings.", color = Muted, fontSize = 14.sp)
                }
                Spacer(Modifier.width(16.dp))
                BouncySwitch(enabled, onCheckedChange)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, Modifier.size(17.dp), tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text("You stay in control of background activity.", color = Color(0xFF756A9B), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PersonalizeCard(onConfigure: () -> Unit) {
    PremiumCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(PurpleLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SettingsSuggest, null, Modifier.size(29.dp), tint = Purple)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Make Gitofy yours", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Customize the way Gitofy looks, feels and responds.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Box(Modifier.size(74.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF8B5CF6)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Palette, null, Modifier.size(38.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE9E1F4)))
            Spacer(Modifier.height(17.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF4EEFF)).padding(15.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFECE2FF)), contentAlignment = Alignment.Center) {
                        Text("i", color = Purple, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Appearance is shared with Main Settings", color = Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Theme, Dynamic Color, animation, font and haptics can be changed here or later in Settings.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
            Spacer(Modifier.height(19.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PreferenceCell(Icons.Default.Palette, "Theme", "Light, Dark & System")
                PreferenceCell(Icons.Default.WbSunny, "Dynamic Color", "Match colors with your style")
                PreferenceCell(Icons.Default.AutoAwesome, "Motion", "Smooth and fluid")
                PreferenceCell(Icons.Default.TextFields, "Font", "Choose your preferred font")
                PreferenceCell(Icons.Default.Handshake, "Haptics", "Feel every interaction")
            }
            Spacer(Modifier.height(18.dp))
            PrimaryCta("Apply personalization", Icons.Default.Palette, onConfigure)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreferenceCell(icon: ImageVector, title: String, description: String) {
    Column(Modifier.width(0.dp).weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(PurpleLight), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = Purple) }
        Spacer(Modifier.height(7.dp))
        Text(title, color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(description, color = Muted, fontSize = 8.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompletionCard(state: OnboardingState) {
    PremiumCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFF0E9FF)); drawCircle(Color(0xFFE8DFFF), radius = size.minDimension * 0.35f) }
                Box(Modifier.size(68.dp).clip(CircleShape).background(Purple), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(42.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(9.dp))
            Text("Everything looks great!", color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("You’ve completed all the steps.\nYou can always change these settings later.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            CompletionRow(Icons.Default.Code, "GitHub setup", state.githubConnected)
            CompletionRow(Icons.Default.Storage, "Repository access", state.repositoryConfigured)
            CompletionRow(Icons.Default.Code, "AI provider", state.apiProviderConfigured)
            CompletionRow(Icons.Default.Sync, "Background sync", state.backgroundSyncEnabled)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFFF4EEFF)).padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, Modifier.size(23.dp), tint = Purple)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Your data is secure and your settings are saved.", color = Purple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("You're good to go!", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionRow(icon: ImageVector, title: String, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F2FF)).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = Purple) }
        Spacer(Modifier.width(11.dp))
        Text(title, Modifier.weight(1f), color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) Purple else Color(0xFFD8D2E3)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White) }
    }
}

@Composable
private fun PremiumCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Card)
            .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
            .background(Color(0xFFFCFAFF))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) { content() }
}

@Composable
private fun PrimaryCta(text: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Purple),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.ArrowForward, null, Modifier.size(21.dp))
    }
}

@Composable
private fun BouncySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbX by animateDpAsState(
        targetValue = if (checked) 29.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "onboarding-switch-offset"
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
        label = "onboarding-switch-scale"
    )
    Box(
        Modifier
            .size(58.dp, 34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) Color(0xFF75C9EA) else Color(0xFF454A51))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = thumbX)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (checked) Color(0xFF075777) else Color(0xFFA9AFB7))
        )
    }
}

@Composable
private fun BottomControls(step: Int, onSkip: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.height(54.dp),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.3.dp, Color(0xFFE0D6F2)),
            contentPadding = PaddingValues(horizontal = 25.dp)
        ) {
            Text(if (step == 5) "Finish" else "Skip", color = Purple, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        androidx.compose.material3.Button(
            onClick = onNext,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(30.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Purple),
            contentPadding = PaddingValues(horizontal = 28.dp)
        ) {
            Text(if (step == 5) "Continue" else "Next", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.ArrowForward, null, Modifier.size(21.dp))
        }
    }
}
