package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SqlExecutionResult

@Composable
fun SqlResultTable(
    result: SqlExecutionResult,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onOpenReport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredRows by remember(result.rows, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                result.rows
            } else {
                result.rows.filter { row ->
                    row.any { it.contains(searchQuery, ignoreCase = true) }
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header bar: Compact Search Pill and Export/Report actions aligned at 36dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleek, compact search bar (height 36.dp) matching adjacent buttons perfectly
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Filtrar...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("result_filter_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Limpiar filtro",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onOpenReport,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("open_report_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoResizeText(
                            text = "Informe",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            minFontSize = 8.5.sp,
                            targetFontSize = 11.sp
                        )
                    }

                    FilledTonalButton(
                        onClick = onExportCsv,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("export_csv_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoResizeText(
                            text = "CSV",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            minFontSize = 8.5.sp,
                            targetFontSize = 11.sp
                        )
                    }

                    FilledTonalButton(
                        onClick = onExportJson,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("export_json_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        AutoResizeText(
                            text = "JSON",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            minFontSize = 8.5.sp,
                            targetFontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Grid with content-adaptive column widths
            val horizontalScrollState = rememberScrollState()

            // Calculate optimal column width dynamically based on column name & cell contents
            val columnWidths = remember(result.columns, result.rows) {
                result.columns.indices.map { colIdx ->
                    val header = result.columns[colIdx]
                    val maxCellLen = result.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
                    val maxLen = maxOf(header.length, maxCellLen)
                    when {
                        maxLen <= 4 -> 68.dp
                        maxLen <= 7 -> 90.dp
                        maxLen <= 12 -> 122.dp
                        maxLen <= 18 -> 165.dp
                        maxLen <= 26 -> 220.dp
                        maxLen <= 38 -> 280.dp
                        maxLen <= 50 -> 335.dp
                        else -> 380.dp
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        // Index column
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(40.dp)
                                .padding(horizontal = 4.dp)
                        )

                        result.columns.forEachIndexed { colIdx, colName ->
                            val colWidth = columnWidths.getOrElse(colIdx) { 130.dp }
                            AutoResizeText(
                                text = colName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                minFontSize = 9.sp,
                                targetFontSize = 12.sp,
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    // Table Data Rows
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(filteredRows) { index, row ->
                            val rowBg = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }

                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .width(40.dp)
                                        .padding(horizontal = 4.dp)
                                )

                                row.forEachIndexed { colIdx, cellValue ->
                                    val colWidth = columnWidths.getOrElse(colIdx) { 130.dp }
                                    val isNull = cellValue == "NULL"
                                    val colName = result.columns.getOrElse(colIdx) { "Dato" }

                                    Box(
                                        modifier = Modifier
                                            .width(colWidth)
                                            .padding(horizontal = 6.dp)
                                            .clickable {
                                                if (cellValue.length > 12) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "$colName:\n$cellValue",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                    ) {
                                        AutoResizeText(
                                            text = cellValue,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = if (isNull) FontFamily.Monospace else FontFamily.Default,
                                                lineHeight = 15.sp
                                            ),
                                            color = if (isNull) {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 3,
                                            minFontSize = 8.5.sp,
                                            targetFontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoResizeText(
                    text = "${filteredRows.size} de ${result.rows.size} filas • ${result.columns.size} col",
                    style = MaterialTheme.typography.bodySmall,
                    targetFontSize = 11.sp,
                    minFontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoResizeText(
                            text = "↔ Deslizar columnas",
                            style = MaterialTheme.typography.labelSmall,
                            targetFontSize = 10.sp,
                            minFontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
