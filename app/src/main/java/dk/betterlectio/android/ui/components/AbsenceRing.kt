package dk.betterlectio.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Percentage circle inspired by Flutter absence_percentage_circle.
 * @param fraction 0.0–1.0 absence rate
 */
@Composable
fun AbsenceRing(
    fraction: Double,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val pct = (fraction * 100).coerceIn(0.0, 100.0)
    val color = when {
        pct < 5 -> Color(0xFF2E7D32)
        pct < 10 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = size.toPx() * 0.12f, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (360f * fraction.toFloat()).coerceIn(0f, 360f),
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            "%.0f%%".format(pct),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
