package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChipBlack
import com.example.ui.theme.CopperTrace
import com.example.ui.theme.GoldTestPoint
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000)
        onNavigateNext()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipPulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "traceGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChipBlack),
        contentAlignment = Alignment.Center
    ) {
        // Background Circuit Trace Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)

            // Draw circuit trace lines extending out from center chip
            val strokeWidth = 3.dp.toPx()
            val traceColor = CopperTrace.copy(alpha = glowAlpha)

            // Horizontal & Vertical traces
            drawLine(traceColor, Offset(center.x - 180f, center.y), Offset(center.x - 300f, center.y), strokeWidth)
            drawLine(traceColor, Offset(center.x + 180f, center.y), Offset(center.x + 300f, center.y), strokeWidth)
            drawLine(traceColor, Offset(center.x, center.y - 180f), Offset(center.x, center.y - 300f), strokeWidth)
            drawLine(traceColor, Offset(center.x, center.y + 180f), Offset(center.x, center.y + 300f), strokeWidth)

            // Diagonal corner traces
            val p1 = Path().apply {
                moveTo(center.x - 140f, center.y - 140f)
                lineTo(center.x - 220f, center.y - 220f)
                lineTo(center.x - 320f, center.y - 220f)
            }
            drawPath(p1, traceColor, style = Stroke(strokeWidth))

            val p2 = Path().apply {
                moveTo(center.x + 140f, center.y + 140f)
                lineTo(center.x + 220f, center.y + 220f)
                lineTo(center.x + 320f, center.y + 220f)
            }
            drawPath(p2, traceColor, style = Stroke(strokeWidth))

            // Test point pads at end of traces
            drawCircle(GoldTestPoint, 8.dp.toPx(), Offset(center.x - 300f, center.y))
            drawCircle(GoldTestPoint, 8.dp.toPx(), Offset(center.x + 300f, center.y))
            drawCircle(GoldTestPoint, 8.dp.toPx(), Offset(center.x - 320f, center.y - 220f))
            drawCircle(GoldTestPoint, 8.dp.toPx(), Offset(center.x + 320f, center.y + 220f))
        }

        // Center Pulsing Chip Component
        Box(
            modifier = Modifier
                .scale(scalePulse)
                .size(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF22242B))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "T",
                        color = CopperTrace,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(GoldTestPoint)
                    )
                    Text(
                        text = "M",
                        color = CopperTrace,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "SENSEI AI",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
