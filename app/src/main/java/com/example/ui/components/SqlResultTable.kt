package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SqlExecutionResult

private data class InspectedCellData(
    val rowNumber: Int,
    val columnName: String,
    val cellValue: String,
    val rowValues: List<String>
)

/**
 * DataGrid interactivo con soporte de ordenamiento por columnas, paginación configurable,
 * filtro en tiempo real, anchos de columna adaptativos y copiado seguro (pulsación larga o visor).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SqlResultTable(
    result: SqlExecutionResult,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onOpenReport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }

    // Estado de celda inspeccionada al tocar (no copia por accidente)
    var inspectedCell by remember { mutableStateOf<InspectedCellData?>(null) }

    // Estado del ordenamiento por columna
    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var sortAscending by remember { mutableStateOf(true) }

    // Estado de paginación (tamaños: 10, 25, 50, 0 = Todos)
    var pageSize by remember { mutableIntStateOf(10) }
    var currentPage by remember { mutableIntStateOf(0) }

    // Resetear página al cambiar la consulta o la búsqueda
    LaunchedEffect(result, searchQuery, pageSize) {
        currentPage = 0
    }

    // 1. Filtrar filas según la búsqueda
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

    // 2. Ordenar filas según la columna seleccionada
    val sortedRows by remember(filteredRows, sortColumnIndex, sortAscending) {
        derivedStateOf {
            val colIdx = sortColumnIndex ?: return@derivedStateOf filteredRows
            filteredRows.sortedWith { rowA, rowB ->
                val valA = rowA.getOrNull(colIdx) ?: ""
                val valB = rowB.getOrNull(colIdx) ?: ""

                // Manejo de valores NULL al final
                if (valA == "NULL" && valB != "NULL") return@sortedWith if (sortAscending) 1 else -1
                if (valB == "NULL" && valA != "NULL") return@sortedWith if (sortAscending) -1 else 1

                // Comparación numérica si ambos son números, o alfabética
                val numA = valA.toDoubleOrNull()
                val numB = valB.toDoubleOrNull()
                val cmp = if (numA != null && numB != null) {
                    numA.compareTo(numB)
                } else {
                    valA.compareTo(valB, ignoreCase = true)
                }
                if (sortAscending) cmp else -cmp
            }
        }
    }

    // 3. Paginación de filas
    val totalRows = sortedRows.size
    val totalPages = remember(totalRows, pageSize) {
        if (pageSize <= 0 || totalRows == 0) 1
        else ((totalRows + pageSize - 1) / pageSize).coerceAtLeast(1)
    }

    val safeCurrentPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    val displayedRows by remember(sortedRows, safeCurrentPage, pageSize) {
        derivedStateOf {
            if (pageSize <= 0) {
                sortedRows
            } else {
                val fromIndex = (safeCurrentPage * pageSize).coerceAtMost(sortedRows.size)
                val toIndex = ((safeCurrentPage + 1) * pageSize).coerceAtMost(sortedRows.size)
                if (fromIndex >= toIndex) emptyList() else sortedRows.subList(fromIndex, toIndex)
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
            // Barra superior: Búsqueda rápida + Botones de Acción (Informe, CSV, JSON)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                    text = "Filtrar en tabla...",
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

            // Indicador de ordenamiento activo (si hay alguna columna ordenada)
            if (sortColumnIndex != null) {
                val sortedColName = result.columns.getOrNull(sortColumnIndex!!) ?: "Columna"
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (sortAscending) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ordenado por $sortedColName (${if (sortAscending) "A-Z / 0-9" else "Z-A / 9-0"})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Quitar orden",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable {
                                        sortColumnIndex = null
                                        sortAscending = true
                                    }
                            )
                        }
                    }

                    Text(
                        text = "Toca una columna para ordenar",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Grid con anchos de columna adaptativos
            val horizontalScrollState = rememberScrollState()

            val columnWidths = remember(result.columns, result.rows) {
                result.columns.indices.map { colIdx ->
                    val header = result.columns[colIdx]
                    val maxCellLen = result.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
                    val maxLen = maxOf(header.length, maxCellLen)
                    when {
                        maxLen <= 4 -> 72.dp
                        maxLen <= 7 -> 96.dp
                        maxLen <= 12 -> 130.dp
                        maxLen <= 18 -> 175.dp
                        maxLen <= 26 -> 230.dp
                        maxLen <= 38 -> 290.dp
                        maxLen <= 50 -> 345.dp
                        else -> 390.dp
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Encabezado interactivo del DataGrid (Permite ordenar columnas)
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Columna del índice (#)
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(horizontal = 4.dp)
                        )

                        // Columnas con interacción de ordenamiento
                        result.columns.forEachIndexed { colIdx, colName ->
                            val colWidth = columnWidths.getOrElse(colIdx) { 130.dp }
                            val isSorted = sortColumnIndex == colIdx

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSorted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier
                                    .width(colWidth)
                                    .clickable {
                                        if (sortColumnIndex == colIdx) {
                                            if (sortAscending) {
                                                sortAscending = false
                                            } else {
                                                sortColumnIndex = null
                                                sortAscending = true
                                            }
                                        } else {
                                            sortColumnIndex = colIdx
                                            sortAscending = true
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    AutoResizeText(
                                        text = colName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSorted) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        minFontSize = 9.sp,
                                        targetFontSize = 12.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(3.dp))

                                    if (isSorted) {
                                        Icon(
                                            imageVector = if (sortAscending) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = if (sortAscending) "Orden ascendente" else "Orden descendente",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Sort,
                                            contentDescription = "Ordenar por esta columna",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Filas de datos paginadas
                    if (displayedRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No hay filas que coincidan con '$searchQuery'" else "Sin registros",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(displayedRows) { indexInPage, row ->
                                val globalIndex = (safeCurrentPage * pageSize) + indexInPage
                                val rowBg = if (indexInPage % 2 == 0) {
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
                                    // Índice global de la fila
                                    Text(
                                        text = "${globalIndex + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .width(44.dp)
                                            .padding(horizontal = 4.dp)
                                    )

                                    // Celdas de la fila: Toque simple = Visor de lectura seguro; Pulsación larga = Copiar al portapapeles
                                    row.forEachIndexed { colIdx, cellValue ->
                                        val colWidth = columnWidths.getOrElse(colIdx) { 130.dp }
                                        val isNull = cellValue == "NULL"
                                        val colName = result.columns.getOrElse(colIdx) { "Dato" }

                                        Box(
                                            modifier = Modifier
                                                .width(colWidth)
                                                .clip(RoundedCornerShape(4.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        inspectedCell = InspectedCellData(
                                                            rowNumber = globalIndex + 1,
                                                            columnName = colName,
                                                            cellValue = cellValue,
                                                            rowValues = row
                                                        )
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        clipboardManager.setText(AnnotatedString(cellValue))
                                                        Toast.makeText(
                                                            context,
                                                            "Copiado ($colName): $cellValue",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            AutoResizeText(
                                                text = cellValue,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = if (isNull) FontFamily.Monospace else FontFamily.Default,
                                                    lineHeight = 15.sp
                                                ),
                                                color = if (isNull) {
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra inferior interactiva: Paginación + Selector de tamaño de página
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ver:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val pageSizes = listOf(10, 25, 50, 0)
                    pageSizes.forEach { size ->
                        val isSelected = pageSize == size
                        val label = if (size == 0) "Todo" else "$size"

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                0.8.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.clickable { pageSize = size }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (pageSize > 0 && totalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { if (safeCurrentPage > 0) currentPage = safeCurrentPage - 1 },
                            enabled = safeCurrentPage > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Página anterior",
                                modifier = Modifier.size(15.dp),
                                tint = if (safeCurrentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }

                        Text(
                            text = "${safeCurrentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { if (safeCurrentPage < totalPages - 1) currentPage = safeCurrentPage + 1 },
                            enabled = safeCurrentPage < totalPages - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "Página siguiente",
                                modifier = Modifier.size(15.dp),
                                tint = if (safeCurrentPage < totalPages - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila de resumen de conteo y guía de gestos seguros
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val startRow = if (totalRows == 0) 0 else (safeCurrentPage * pageSize) + 1
                val endRow = if (pageSize <= 0) totalRows else minOf((safeCurrentPage + 1) * pageSize, totalRows)

                val summaryText = if (pageSize <= 0 || totalRows <= pageSize) {
                    "$totalRows filas • ${result.columns.size} col"
                } else {
                    "Filas $startRow–$endRow de $totalRows • ${result.columns.size} col"
                }

                AutoResizeText(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    targetFontSize = 10.5.sp,
                    minFontSize = 8.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = "Tocar: ver • Mantener: copiar",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    // Visor seguro de celda (solo lectura hasta confirmar copia)
    inspectedCell?.let { cell ->
        AlertDialog(
            onDismissRequest = { inspectedCell = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Column {
                    Text(
                        text = cell.columnName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fila #${cell.rowNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp, max = 240.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = cell.cellValue,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = if (cell.cellValue == "NULL") FontFamily.Monospace else FontFamily.Default
                                    ),
                                    color = if (cell.cellValue == "NULL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val rowFormatted = result.columns.zip(cell.rowValues)
                                .joinToString(" | ") { (col, v) -> "$col: $v" }
                            clipboardManager.setText(AnnotatedString(rowFormatted))
                            Toast.makeText(context, "Fila #${cell.rowNumber} copiada", Toast.LENGTH_SHORT).show()
                            inspectedCell = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar fila completa")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(cell.cellValue))
                        Toast.makeText(context, "Valor copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        inspectedCell = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar valor")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedCell = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
