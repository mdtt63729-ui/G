package com.gitofy.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitofy.R
import kotlinx.coroutines.delay

private val OnboardingBg = Color(0xFFFBF9FF)
private val OnboardingNavy = Color(0xFF111A3A)
private val OnboardingMuted = Color(0xFF6E7187)
private val OnboardingPurple = Color(0xFF5B2BDB)
private val OnboardingPurple2 = Color(0xFF7B45E8)

@Composable
fun IntroductionScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFEFDFF), OnboardingBg, Color(0xFFF8F4FF))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        DecorativeBackground(modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(420)) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(520, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(70.dp))
                        .shadow(24.dp, RoundedCornerShape(70.dp), clip = false)
                        .background(Color(0xFFF3ECFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.gitofy_new_app_icon),
                        contentDescription = "Gitofy",
                        modifier = Modifier.size(222.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(42.dp))
                Text(
                    text = "Gitofy",
                    color = OnboardingNavy,
                    fontSize = 50.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Welcome to Gitofy",
                    color = OnboardingNavy,
                    fontSize = 27.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your Git workflow, simplified and connected.",
                    color = OnboardingMuted,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(34.dp))
                OnboardingPrimaryButton(
                    text = "Get Started",
                    icon = Icons.Default.AutoAwesome,
                    onClick = onGetStarted
                )
                Spacer(Modifier.height(14.dp))
                OnboardingSecondaryButton(
                    text = "Skip",
                    icon = Icons.Default.ArrowForward,
                    onClick = onSkip
                )
            }
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .height(58.dp)
            .shadow(13.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = OnboardingPurple,
            contentColor = Color.White
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OnboardingSecondaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .height(58.dp),
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDCCFFF)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = OnboardingPurple
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DecorativeBackground(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFFEFE5FF).copy(alpha = 0.42f), radius = w * 0.34f, center = androidx.compose.ui.geometry.Offset(w * 1.05f, -h * 0.02f))
        drawCircle(Color(0xFFF0E8FF).copy(alpha = 0.5f), radius = w * 0.24f, center = androidx.compose.ui.geometry.Offset(-w * 0.04f, h * 1.03f))
        drawCircle(Color(0xFFE8DEFF).copy(alpha = 0.34f), radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.28f))
        drawCircle(Color(0xFFB65CEB).copy(alpha = 0.55f), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.20f))
    }
}
