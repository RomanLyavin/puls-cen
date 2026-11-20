package com.pulscen.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage
import com.pulscen.app.data.PricePoint
import com.pulscen.app.data.Product
import kotlin.math.max

@Composable
fun BrandMark(size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(BrandGradient, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MonitorHeart,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

@Composable
fun BrandHeader(
    title: String = "Пульс цен",
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark()
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun PriceChangeBadge(product: Product, modifier: Modifier = Modifier) {
    val change = product.changeAmount
    val color = when {
        change == null -> MaterialTheme.colorScheme.onSurfaceVariant
        change < -0.004 -> BrandGreen
        change > 0.004 -> BrandRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        change == null -> "нет истории"
        kotlin.math.abs(change) < 0.005 -> "без изменений"
        else -> "${Money.amount(change)} · ${Money.percent(product.changePercent)}"
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun Sparkline(
    points: List<PricePoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    Canvas(modifier) {
        val min = points.minOf { it.price }
        val max = points.maxOf { it.price }
        val span = max(max - min, max * 0.02).coerceAtLeast(0.01)
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.price - min) / span * size.height * 0.82f).toFloat() -
                size.height * 0.09f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        val last = points.last()
        val lastY = size.height - ((last.price - min) / span * size.height * 0.82f).toFloat() -
            size.height * 0.09f
        drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(size.width, lastY))
    }
}

@Composable
fun PriceChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        val min = points.minOf { it.price }
        val max = points.maxOf { it.price }
        val span = max(max - min, max * 0.02).coerceAtLeast(0.01)
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        repeat(4) { index ->
            val y = top + height * index / 3f
            drawLine(
                color = primary.copy(alpha = 0.12f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val stepX = if (points.size <= 1) 0f else width / (points.size - 1)
        fun yFor(price: Double): Float =
            bottom - ((price - min) / span * height * 0.86).toFloat() - height * 0.07f

        val line = Path()
        points.forEachIndexed { index, point ->
            val x = left + stepX * index
            val y = yFor(point.price)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(left + stepX * (points.size - 1), bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.01f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(line, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        points.forEachIndexed { index, point ->
            if (index == points.lastIndex) {
                drawCircle(primary, 5.dp.toPx(), Offset(left + stepX * index, yFor(point.price)))
                drawCircle(Color.White, 2.dp.toPx(), Offset(left + stepX * index, yFor(point.price)))
            }
        }

        if (points.size == 1) {
            drawLine(primary, Offset(left, yFor(points[0].price)), Offset(right, yFor(points[0].price)), 3.dp.toPx())
            drawCircle(primary, 5.dp.toPx(), Offset(right, yFor(points[0].price)))
        }
    }
}

@Composable
fun ProductImage(
    product: Product,
    modifier: Modifier = Modifier,
    size: Dp = 74.dp
) {
    val shape = RoundedCornerShape(14.dp)
    if (product.imageUrl != null) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
fun AvatarView(
    dataUrl: String?,
    name: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(dataUrl) { decodeDataUrl(dataUrl) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initials = name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "Я" }
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ErrorText(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}

fun decodeDataUrl(dataUrl: String?): android.graphics.Bitmap? {
    if (dataUrl.isNullOrBlank()) return null
    return runCatching {
        val comma = dataUrl.indexOf(',')
        val payload = if (comma >= 0) dataUrl.substring(comma + 1) else dataUrl
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

fun subtitleFor(product: Product): String {
    val article = product.article?.let { "арт. $it" }
    return listOfNotNull(product.marketplace.label, article).joinToString(" · ")
}
