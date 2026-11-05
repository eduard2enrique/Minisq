package com.example.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SqlBlue
import kotlinx.coroutines.delay

/**
 * Animated Splash Screen that smoothly draws "S - Q - L" using vector stroke animation
 * when the application launches, creating a responsive and engaging startup feel.
 */
@Composable
fun SqlSplashScreen(
    onAnimationFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var showSubtitle by remember { mutableStateOf(false) }

    // Glow pulse animation once drawing completes
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        // 1. Progressively draw letters S, Q, L
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
        showSubtitle = true
        // 2. Pause briefly for visual appreciation of complete brand
        delay(450)
        // 3. Transition to main screen
        onAnimationFinished()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Animated SQL Vector Drawing
                Box(
                    modifier = Modifier.size(width = 280.dp, height = 130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Coordinates for 3 letters: S, Q, L
                        // S: x in [0.08w, 0.32w], y in [0.15h, 0.85h]
                        val sPath = android.graphics.Path().apply {
                            val x0 = w * 0.31f
                            val y0 = h * 0.25f
                            moveTo(x0, y0)
                            cubicTo(
                                w * 0.26f, h * 0.12f,
                                w * 0.10f, h * 0.12f,
                                w * 0.10f, h * 0.32f
                            )
                            cubicTo(
                                w * 0.10f, h * 0.48f,
                                w * 0.31f, h * 0.52f,
                                w * 0.31f, h * 0.68f
                            )
                            cubicTo(
                                w * 0.31f, h * 0.88f,
                                w * 0.14f, h * 0.88f,
                                w * 0.09f, h * 0.75f
                            )
                        }

                        // Q: Oval in [0.38w, 0.62w] + Tail
                        val qPath = android.graphics.Path().apply {
                            val left = w * 0.38f
                            val top = h * 0.18f
                            val right = w * 0.62f
                            val bottom = h * 0.82f
                            addOval(
                                android.graphics.RectF(left, top, right, bottom),
                                android.graphics.Path.Direction.CW
                            )
                        }
                        val qTailPath = android.graphics.Path().apply {
                            moveTo(w * 0.52f, h * 0.66f)
                            lineTo(w * 0.65f, h * 0.86f)
                        }

                        // L: Vertical and horizontal in [0.68w, 0.90w]
                        val lPath = android.graphics.Path().apply {
                            moveTo(w * 0.72f, h * 0.18f)
                            lineTo(w * 0.72f, h * 0.82f)
                            lineTo(w * 0.90f, h * 0.82f)
                        }

                        val strokePx = 7.dp.toPx()
                        val glowStrokePx = 14.dp.toPx()
                        val currentVal = progress.value

                        // Progression breakdown:
                        // S drawn in [0.00, 0.40]
                        // Q drawn in [0.30, 0.75]
                        // L drawn in [0.65, 1.00]
                        val sProg = (currentVal / 0.40f).coerceIn(0f, 1f)
                        val qProg = ((currentVal - 0.28f) / 0.42f).coerceIn(0f, 1f)
                        val lProg = ((currentVal - 0.62f) / 0.38f).coerceIn(0f, 1f)

                        fun drawSegment(path: android.graphics.Path, fraction: Float) {
                            if (fraction <= 0f) return
                            val measure = PathMeasure(path, false)
                            val totalLen = measure.length
                            val dst = android.graphics.Path()
                            measure.getSegment(0f, totalLen * fraction, dst, true)

                            val composePath = dst.asComposePath()

                            // Outer glow
                            drawPath(
                                path = composePath,
                                color = SqlBlue.copy(alpha = glowAlpha * 0.4f),
                                style = Stroke(
                                    width = glowStrokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Core crisp line
                            drawPath(
                                path = composePath,
                                color = SqlBlue,
                                style = Stroke(
                                    width = strokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        drawSegment(sPath, sProg)
                        drawSegment(qPath, qProg)
                        if (qProg > 0.8f) {
                            val tailProg = ((qProg - 0.8f) / 0.2f).coerceIn(0f, 1f)
                            drawSegment(qTailPath, tailProg)
                        }
                        drawSegment(lPath, lProg)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // App Title
                Text(
                    text = "MiniSQL Pro",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                AnimatedVisibility(
                    visible = showSubtitle,
                    enter = fadeIn(animationSpec = tween(300))
                ) {
                    Text(
                        text = "SQLite Studio • Motor Nativo",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
