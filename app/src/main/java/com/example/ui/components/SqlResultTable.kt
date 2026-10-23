package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    fun copyAsMarkdown() {
        val sb = StringBuilder()
        sb.append("| ").append(result.columns.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(result.columns.joinToString(" | ") { "---" }).append(" |\n")
        for (r in filteredRows) {
            sb.append("| ").append(r.joinToString(" | ")).append(" |\n")
        }
        copyToClipboard(sb.toString(), "Tabla Markdown")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header bar: Search filter and Export actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Filtrar en resultados...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar filtro",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(end = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onExportCsv,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("export_csv_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = onExportJson,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("export_json_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("JSON", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = { copyAsMarkdown() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar como Markdown",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Grid
            val horizontalScrollState = rememberScrollState()

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

                        result.columns.forEach { colName ->
                            Text(
                                text = colName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(130.dp)
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

                                row.forEach { cellValue ->
                                    val isNull = cellValue == "NULL"
                                    Text(
                                        text = cellValue,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = if (isNull) FontFamily.Monospace else FontFamily.Default,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isNull) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .width(130.dp)
                                            .padding(horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
