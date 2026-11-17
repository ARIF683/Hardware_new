package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.WarmCreamSplashBg
import com.example.ui.theme.WarmCreamSplashSurface
import com.example.ui.theme.WarmEspressoText
import com.example.ui.theme.WarmSandSplashBorder
import com.example.ui.theme.WarmTaupeSubtext
import com.example.ui.theme.WarmTerracottaAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmoothSplashScreen(
    onAnimationFinished: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isDark = ext.isDark

    val splashBg = if (isDark) MaterialTheme.colorScheme.background else WarmCreamSplashBg
    val cardSurface = if (isDark) MaterialTheme.colorScheme.surfaceVariant else WarmCreamSplashSurface
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outline else WarmSandSplashBorder
    val titleColor = if (isDark) MaterialTheme.colorScheme.onBackground else WarmEspressoText
    val subtitleColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else WarmTaupeSubtext
    val accentColor = if (isDark) MaterialTheme.colorScheme.primary else WarmTerracottaAccent

    val logoScale = remember { Animatable(0.88f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val accentProgress = remember { Animatable(0f) }

    val view = LocalView.current
    DisposableEffect(view, isDark) {
        val window = (view.context as? Activity)?.window
        val controller = if (!view.isInEditMode && window != null) {
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        val prevLightNav = controller?.isAppearanceLightNavigationBars
        if (controller != null) {
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
        onDispose {
            if (controller != null) {
                if (prevLightStatus != null) {
                    controller.isAppearanceLightStatusBars = prevLightStatus
                }
                if (prevLightNav != null) {
                    controller.isAppearanceLightNavigationBars = prevLightNav
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        delay(220)
        launch {
            textAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            accentProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        delay(700)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .size(116.dp)
                    .testTag("splash_app_logo"),
                shape = RoundedCornerShape(28.dp),
                color = cardSurface,
                shadowElevation = if (isDark) 2.dp else 6.dp,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo_foreground),
                        contentDescription = stringResource(R.string.splash_logo_description),
                        modifier = Modifier.size(86.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.splash_brand_title),
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                letterSpacing = 2.5.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .width(36.dp * accentProgress.value.coerceAtLeast(0.05f))
                    .height(3.dp)
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(50)
                    )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.splash_brand_tagline),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor,
                letterSpacing = 0.6.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
