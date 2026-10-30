package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ParsedSheet

@Composable
fun SpreadsheetImportDialog(
    sheets: List<ParsedSheet>,
    existingTables: List<String>,
    onConfirmImport: (
        sheet: ParsedSheet,
        tableName: String,
        headers: List<String>,
        types: List<String>,
        createNewTable: Boolean
    ) -> Unit,
    isImporting: Boolean,
    onDismiss: () -> Unit
) {
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    val currentSheet = sheets.getOrNull(selectedSheetIndex) ?: sheets.first()

    var createNewTable by remember { mutableStateOf(true) }
    var targetTableName by remember(currentSheet) { mutableStateOf(currentSheet.sheetName) }
    var selectedExistingTable by remember { mutableStateOf(existingTables.firstOrNull() ?: "") }

    // Column types override
    val customTypes = remember(currentSheet) {
        mutableStateListOf<String>().apply {
            addAll(currentSheet.inferredTypes)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importar CSV / Excel")
                }
                IconButton(onClick = onDismiss, enabled = !isImporting) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Multi-sheet tabs if Excel has more than 1 sheet
                if (sheets.size > 1) {
                    Text("Seleccionar hoja:", style = MaterialTheme.typography.labelSmall)
                    ScrollableTabRow(
                        selectedTabIndex = selectedSheetIndex,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        sheets.forEachIndexed { index, sheet ->
                            Tab(
                                selected = selectedSheetIndex == index,
                                onClick = { selectedSheetIndex = index },
                                text = { Text(sheet.sheetName) }
                            )
                        }
                    }
                }

                // Summary Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${currentSheet.headers.size} columnas detectadas",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${currentSheet.rows.size} filas de datos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Target table mode
                Text("Destino de importación:", style = MaterialTheme.typography.labelMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = createNewTable,
                        onClick = { createNewTable = true }
                    )
                    Text("Crear nueva tabla en SQLite", style = MaterialTheme.typography.bodySmall)
                }

                if (existingTables.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !createNewTable,
                            onClick = { createNewTable = false }
                        )
                        Text("Añadir a tabla existente", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (createNewTable) {
                    OutlinedTextField(
                        value = targetTableName,
                        onValueChange = { targetTableName = it },
                        label = { Text("Nombre de la tabla") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_target_table_input")
                    )

                    val tableAlreadyExists = existingTables.any { it.equals(targetTableName.trim(), ignoreCase = true) }
                    if (tableAlreadyExists) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "La tabla '$targetTableName' ya existe. Al importar se reemplazará con las columnas y datos del archivo.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    var expandedExisting by remember { mutableStateOf(false) }
                    Box {
                        FilledTonalButton(
                            onClick = { expandedExisting = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tabla destino: $selectedExistingTable")
                        }
                        DropdownMenu(
                            expanded = expandedExisting,
                            onDismissRequest = { expandedExisting = false }
                        ) {
                            existingTables.forEach { tbl ->
                                DropdownMenuItem(
                                    text = { Text(tbl) },
                                    onClick = {
                                        selectedExistingTable = tbl
                                        expandedExisting = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Columns & Types Preview
                Text("Estructura de Columnas e Inferencia:", style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val supportedTypes = listOf("INTEGER", "REAL", "TEXT", "BLOB")
                    currentSheet.headers.forEachIndexed { idx, colName ->
                        var typeMenuExpanded by remember { mutableStateOf(false) }
                        val currentType = customTypes.getOrNull(idx) ?: "TEXT"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = colName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable { typeMenuExpanded = true }
                                ) {
                                    Text(
                                        text = "$currentType ▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = typeMenuExpanded,
                                    onDismissRequest = { typeMenuExpanded = false }
                                ) {
                                    supportedTypes.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t) },
                                            onClick = {
                                                if (idx in customTypes.indices) {
                                                    customTypes[idx] = t
                                                }
                                                typeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Data Preview (First 5 rows)
                Text("Vista previa de datos (primeras 5 filas):", style = MaterialTheme.typography.labelMedium)

                val horizontalScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScroll)
                    ) {
                        // Headers
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            currentSheet.headers.forEach { h ->
                                Text(
                                    text = h,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(100.dp).padding(horizontal = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // First 5 rows
                        currentSheet.rows.take(5).forEachIndexed { rIdx, row ->
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (rIdx % 2 == 0) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .padding(vertical = 3.dp, horizontal = 4.dp)
                            ) {
                                currentSheet.headers.indices.forEach { cIdx ->
                                    val cellVal = row.getOrNull(cIdx) ?: ""
                                    Text(
                                        text = cellVal,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.width(100.dp).padding(horizontal = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTable = if (createNewTable) targetTableName else selectedExistingTable
                    onConfirmImport(
                        currentSheet,
                        finalTable,
                        currentSheet.headers,
                        customTypes.toList(),
                        createNewTable
                    )
                },
                enabled = !isImporting && (if (createNewTable) targetTableName.isNotBlank() else selectedExistingTable.isNotBlank()),
                modifier = Modifier.testTag("confirm_spreadsheet_import_btn")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importando...")
                } else {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar a SQLite")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancelar")
            }
        }
    )
}
