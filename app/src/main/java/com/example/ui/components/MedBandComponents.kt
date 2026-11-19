package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen

@Composable
fun MedBandCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun AnimatedConnectionBadge(
    label: String,
    color: Color,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse_badge")
    val alpha by transition.animateFloat(
        initialValue = if (isPulsing) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CircularGaugeRing(
    valueText: String,
    unitText: String,
    progress: Float?,
    ringColor: Color,
    size: Dp = 148.dp,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 11.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (progress != null && progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(ringColor, ElectricBlue, CyanPulse, ringColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0.05f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unitText,
                style = MaterialTheme.typography.labelMedium,
                color = ringColor
            )
        }
    }
}

@Composable
fun EcgWaveformCanvas(
    isLive: Boolean,
    color: Color = CyanPulse,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
) {
    val transition = rememberInfiniteTransition(label = "ecg_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecg_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h * 0.54f
        if (!isLive) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }
        val path = Path()
        val segments = 3
        val segW = w / segments
        path.moveTo(0f, midY)
        for (i in 0 until segments) {
            val sx = i * segW
            path.lineTo(sx + segW * 0.25f, midY)
            path.lineTo(sx + segW * 0.34f, midY - h * 0.14f)
            path.lineTo(sx + segW * 0.42f, midY)
            path.lineTo(sx + segW * 0.48f, midY + h * 0.18f)
            path.lineTo(sx + segW * 0.56f, midY - h * 0.44f)
            path.lineTo(sx + segW * 0.64f, midY + h * 0.25f)
            path.lineTo(sx + segW * 0.72f, midY)
            path.lineTo(sx + segW * 0.84f, midY - h * 0.16f)
            path.lineTo(sx + segW, midY)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.88f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val dotX = w * phase
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = Offset(dotX, midY)
        )
    }
}

@Composable
fun TelemetryLineChart(
    values: List<Float>,
    minY: Float,
    maxY: Float,
    lineColor: Color,
    emptyLabel: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(150.dp)
) {
    if (values.size < 2) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val range = (maxY - minY).coerceAtLeast(1f)

        // Subtle horizontal grid lines
        for (i in 0..3) {
            val y = h * (i / 3f)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        val linePath = Path()
        val fillPath = Path()
        val stepX = w / (values.size - 1).coerceAtLeast(1)

        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = ((v - minY) / range).coerceIn(0f, 1f)
            val y = h - norm * h
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == values.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.34f), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun GoogleMapsAttributionFooter(
    modifier: Modifier = Modifier
) {
    // Follows Grounding with Google Maps text attribution rules:
    // Exact casing "Google Maps", single line, normal weight 400, SansSerif, 12sp-16sp, >=4.5:1 contrast.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Google Maps",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun severityColor(severity: String): Color = when (severity.uppercase()) {
    "CRITICAL" -> EmergencyRed
    "WARNING" -> StatusAmber
    else -> StatusGreen
}
