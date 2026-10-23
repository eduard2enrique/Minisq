package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SqlExecutionResult
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class ChartType {
    BAR, LINE, PIE
}

data class ChartDataPoint(
    val label: String,
    val value: Double,
    val formattedValue: String
)

private val CHART_PALETTE = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFFF43F5E), // Rose
    Color(0xFF14B8A6), // Teal
    Color(0xFFEC4899), // Pink
    Color(0xFF6366F1), // Indigo
    Color(0xFF84CC16)  // Lime
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SqlQueryChart(
    result: SqlExecutionResult,
    modifier: Modifier = Modifier
) {
    if (result.columns.isEmpty() || result.rows.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay datos para graficar en la consulta actual.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    var selectedChartType by remember { mutableStateOf(ChartType.BAR) }
    var isDonutMode by remember { mutableStateOf(true) }
    var showAreaFill by remember { mutableStateOf(true) }

    // Detect numeric and category columns
    val numericColumns = remember(result) {
        result.columns.filterIndexed { colIndex, _ ->
            val sample = result.rows.take(20).mapNotNull { it.getOrNull(colIndex) }
            sample.isNotEmpty() && sample.any { it.toDoubleOrNull() != null }
        }
    }

    val textColumns = remember(result) {
        result.columns.filter { it !in numericColumns }.ifEmpty { result.columns }
    }

    var selectedCategoryColumn by remember(result) {
        mutableStateOf(textColumns.firstOrNull() ?: result.columns.first())
    }

    var selectedMetricColumn by remember(result, numericColumns) {
        mutableStateOf(numericColumns.firstOrNull() ?: result.columns.last())
    }

    val categoryIndex = remember(result, selectedCategoryColumn) {
        result.columns.indexOf(selectedCategoryColumn).coerceAtLeast(0)
    }

    val metricIndex = remember(result, selectedMetricColumn) {
        result.columns.indexOf(selectedMetricColumn).coerceAtLeast(0)
    }

    // Prepare chart data points
    val dataPoints by remember(result, categoryIndex, metricIndex) {
        derivedStateOf {
            result.rows.take(30).mapNotNull { row ->
                val label = row.getOrNull(categoryIndex) ?: "Sin dato"
                val rawVal = row.getOrNull(metricIndex)?.toDoubleOrNull()
                if (rawVal != null) {
                    ChartDataPoint(
                        label = label,
                        value = rawVal,
                        formattedValue = if (rawVal % 1.0 == 0.0) rawVal.toLong().toString()
                        else String.format(Locale.US, "%.2f", rawVal)
                    )
                } else null
            }
        }
    }

    // Selected point for tooltip
    var selectedPointIndex by remember { mutableIntStateOf(-1) }

    // Summary statistics
    val totalSum by remember(dataPoints) { derivedStateOf { dataPoints.sumOf { it.value } } }
    val avgValue by remember(dataPoints) { derivedStateOf { if (dataPoints.isNotEmpty()) totalSum / dataPoints.size else 0.0 } }
    val maxValue by remember(dataPoints) { derivedStateOf { dataPoints.maxOfOrNull { it.value } ?: 0.0 } }
    val minValue by remember(dataPoints) { derivedStateOf { dataPoints.minOfOrNull { it.value } ?: 0.0 } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Header with Chart Type Selector (Recharts style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (selectedChartType) {
                            ChartType.BAR -> Icons.Default.BarChart
                            ChartType.LINE -> Icons.Default.ShowChart
                            ChartType.PIE -> Icons.Default.PieChart
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Visualización Gráfica",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Chart Type Segmented Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ChartType.values().forEach { type ->
                        val isSelected = selectedChartType == type
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedChartType = type }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when (type) {
                                    ChartType.BAR -> "Barras"
                                    ChartType.LINE -> "Líneas"
                                    ChartType.PIE -> "Pastel"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Axis & Column Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // X Axis / Category
                var expandedCat by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { expandedCat = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "X: $selectedCategoryColumn",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = expandedCat,
                        onDismissRequest = { expandedCat = false }
                    ) {
                        result.columns.forEach { col ->
                            DropdownMenuItem(
                                text = { Text(col) },
                                onClick = {
                                    selectedCategoryColumn = col
                                    expandedCat = false
                                }
                            )
                        }
                    }
                }

                // Y Axis / Metric
                var expandedMetric by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { expandedMetric = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Y: $selectedMetricColumn",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = expandedMetric,
                        onDismissRequest = { expandedMetric = false }
                    ) {
                        result.columns.forEach { col ->
                            DropdownMenuItem(
                                text = { Text(col) },
                                onClick = {
                                    selectedMetricColumn = col
                                    expandedMetric = false
                                }
                            )
                        }
                    }
                }
            }

            // Options toggles (Donut / Area)
            if (selectedChartType == ChartType.PIE) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text("Formato Dona", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isDonutMode,
                        onCheckedChange = { isDonutMode = it },
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (selectedChartType == ChartType.LINE) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text("Relleno de Área", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = showAreaFill,
                        onCheckedChange = { showAreaFill = it },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Canvas Chart View
            if (dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "La columna '$selectedMetricColumn' no contiene valores numéricos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    when (selectedChartType) {
                        ChartType.BAR -> BarChartCanvas(
                            dataPoints = dataPoints,
                            selectedIndex = selectedPointIndex,
                            onSelectIndex = { selectedPointIndex = it }
                        )
                        ChartType.LINE -> LineChartCanvas(
                            dataPoints = dataPoints,
                            showArea = showAreaFill,
                            selectedIndex = selectedPointIndex,
                            onSelectIndex = { selectedPointIndex = it }
                        )
                        ChartType.PIE -> PieChartCanvas(
                            dataPoints = dataPoints,
                            isDonut = isDonutMode,
                            selectedIndex = selectedPointIndex,
                            onSelectIndex = { selectedPointIndex = it }
                        )
                    }

                    // Interactive Tooltip Badge
                    if (selectedPointIndex in dataPoints.indices) {
                        val pt = dataPoints[selectedPointIndex]
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 4.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = pt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$selectedMetricColumn: ${pt.formattedValue}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (totalSum % 1.0 == 0.0) totalSum.toLong().toString() else String.format(Locale.US, "%.1f", totalSum),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Text("Promedio", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.2f", avgValue),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text("Máx", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (maxValue % 1.0 == 0.0) maxValue.toLong().toString() else String.format(Locale.US, "%.1f", maxValue),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text("Puntos", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${dataPoints.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Pie Legend items if PIE mode
            if (selectedChartType == ChartType.PIE && dataPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dataPoints.take(8).forEachIndexed { index, dp ->
                        val color = CHART_PALETTE[index % CHART_PALETTE.size]
                        val pct = if (totalSum > 0) (dp.value / totalSum * 100) else 0.0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${dp.label}: ${String.format(Locale.US, "%.1f", pct)}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarChartCanvas(
    dataPoints: List<ChartDataPoint>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val maxVal = remember(dataPoints) { (dataPoints.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(dataPoints) {
                detectTapGestures { offset ->
                    val totalBars = dataPoints.size
                    if (totalBars > 0) {
                        val barSpacing = size.width / totalBars
                        val tappedIndex = (offset.x / barSpacing).toInt().coerceIn(0, totalBars - 1)
                        onSelectIndex(tappedIndex)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height - 24.dp.toPx() // Reserve bottom for labels
        val barCount = dataPoints.size
        val slotWidth = w / barCount
        val barWidth = (slotWidth * 0.65f).coerceAtMost(36.dp.toPx())

        // Draw horizontal gridlines (Recharts look)
        val gridLines = 4
        val gridColor = Color.Gray.copy(alpha = 0.15f)
        for (i in 0..gridLines) {
            val y = h * (i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw bars
        dataPoints.forEachIndexed { i, dp ->
            val barHeight = ((dp.value / maxVal) * h).toFloat().coerceAtLeast(4f)
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            val top = h - barHeight
            val isSelected = selectedIndex == i

            val barBrush = if (isSelected) {
                Brush.verticalGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
            }

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // If selected, draw outline
            if (isSelected) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun LineChartCanvas(
    dataPoints: List<ChartDataPoint>,
    showArea: Boolean,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val maxVal = remember(dataPoints) { (dataPoints.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0) }
    val minVal = remember(dataPoints) { (dataPoints.minOfOrNull { it.value } ?: 0.0).coerceAtLeast(0.0) }
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(dataPoints) {
                detectTapGestures { offset ->
                    val total = dataPoints.size
                    if (total > 1) {
                        val stepX = size.width / (total - 1)
                        val tapped = ((offset.x + stepX / 2f) / stepX).toInt().coerceIn(0, total - 1)
                        onSelectIndex(tapped)
                    } else if (total == 1) {
                        onSelectIndex(0)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height - 20.dp.toPx()
        val total = dataPoints.size
        if (total < 1) return@Canvas

        // Gridlines
        val gridLines = 4
        val gridColor = Color.Gray.copy(alpha = 0.15f)
        for (i in 0..gridLines) {
            val y = h * (i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val stepX = if (total > 1) w / (total - 1) else w / 2f
        val points = dataPoints.mapIndexed { idx, dp ->
            val norm = ((dp.value - minVal) / range).toFloat()
            val y = h - (norm * h * 0.85f) - (h * 0.08f)
            val x = if (total > 1) idx * stepX else w / 2f
            Offset(x, y)
        }

        // Draw Area Fill (Recharts gradient area)
        if (showArea && points.size > 1) {
            val areaPath = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.4f),
                        Color(0xFF0284C7).copy(alpha = 0.02f)
                    ),
                    startY = 0f,
                    endY = h
                )
            )
        }

        // Draw smooth Line
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(
            path = linePath,
            color = Color(0xFF38BDF8),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw points markers
        points.forEachIndexed { i, pt ->
            val isSelected = selectedIndex == i
            val radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()

            drawCircle(
                color = Color(0xFF38BDF8),
                radius = radius,
                center = pt
            )
            drawCircle(
                color = if (isSelected) Color(0xFF60A5FA) else Color.White,
                radius = radius * 0.5f,
                center = pt
            )
        }
    }
}

@Composable
fun PieChartCanvas(
    dataPoints: List<ChartDataPoint>,
    isDonut: Boolean,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val total = remember(dataPoints) { dataPoints.sumOf { it.value }.coerceAtLeast(1.0) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(dataPoints) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f

                    var current = -90f
                    if (current < 0) current += 360f

                    var accAngle = 0f
                    dataPoints.forEachIndexed { idx, dp ->
                        val sweep = (dp.value / total * 360f).toFloat()
                        val start = (accAngle - 90f + 360f) % 360f
                        val end = (start + sweep) % 360f

                        val inSlice = if (start <= end) {
                            angle in start..end
                        } else {
                            angle >= start || angle <= end
                        }

                        if (inSlice) {
                            onSelectIndex(idx)
                            return@detectTapGestures
                        }
                        accAngle += sweep
                    }
                }
            }
    ) {
        val diameter = size.minDimension * 0.9f
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = if (isDonut) radius * 0.45f else radius

        var startAngle = -90f

        dataPoints.forEachIndexed { index, dp ->
            val sweep = (dp.value / total * 360f).toFloat()
            val color = CHART_PALETTE[index % CHART_PALETTE.size]
            val isSelected = selectedIndex == index

            val currentRadius = if (isSelected) radius + 3.dp.toPx() else radius

            if (isDonut) {
                drawArc(
                    color = color,
                    startAngle = startAngle + 1f,
                    sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(center.x - (currentRadius - strokeWidth / 2f), center.y - (currentRadius - strokeWidth / 2f)),
                    size = Size((currentRadius - strokeWidth / 2f) * 2f, (currentRadius - strokeWidth / 2f) * 2f),
                    style = Stroke(width = strokeWidth)
                )
            } else {
                drawArc(
                    color = color,
                    startAngle = startAngle + 0.5f,
                    sweepAngle = (sweep - 1f).coerceAtLeast(1f),
                    useCenter = true,
                    topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
                    size = Size(currentRadius * 2f, currentRadius * 2f)
                )
            }

            startAngle += sweep
        }
    }
}
