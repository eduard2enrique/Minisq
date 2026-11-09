package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.QueryHistoryItem
import com.example.ui.theme.SqlBlue
import com.example.ui.theme.SqlError
import com.example.ui.theme.SqlSuccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorySideDrawer(
    history: List<QueryHistoryItem>,
    activeDbName: String = "otorgamiento_carreras.db",
    onSelectQuery: (sql: String) -> Unit,
    onExecuteQuery: (sql: String) -> Unit,
    onDeleteQuery: (id: String) -> Unit,
    onClearHistory: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // State for tracking which queries are explicitly expanded/collapsed
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog state for choosing how to share
    var queryToShare by remember { mutableStateOf<QueryHistoryItem?>(null) }
    var showShareAllDialog by remember { mutableStateOf(false) }

    // Take max 20 queries as requested
    val last20Queries = remember(history) {
        history.take(20)
    }

    val filteredQueries by remember(last20Queries, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) last20Queries
            else last20Queries.filter { it.sql.contains(searchQuery, ignoreCase = true) }
        }
    }

    fun copyToClipboard(sql: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SQL Query", sql)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Consulta copiada al portapapeles", Toast.LENGTH_SHORT).show()
    }

    fun getRelativeTime(timestamp: Long): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHours = diffMin / 60
        return when {
            diffSec < 45 -> "Hace un momento"
            diffMin < 60 -> "Hace $diffMin min"
            diffHours < 24 -> "Hace $diffHours h"
            else -> timeFormat.format(Date(timestamp))
        }
    }

    // Share query as text formatted for WhatsApp
    fun shareQueryText(item: QueryHistoryItem, targetWhatsApp: Boolean = false) {
        val statusText = if (item.isSuccess) "Ejecución exitosa (${item.rowsCount} filas en ${item.elapsedMs}ms)" else "Error en ejecución"
        val textToSend = """
            *MiniSQL Pro - Consulta SQL*
            Base de datos: `${activeDbName.removeSuffix(".db")}`
            
            ```sql
            ${item.sql.trim()}
            ```
            
            ⏱ $statusText
            _Compartido desde MiniSQL Pro para Android_
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToSend)
            if (targetWhatsApp) {
                setPackage("com.whatsapp")
            }
        }

        try {
            if (targetWhatsApp) {
                context.startActivity(sendIntent)
            } else {
                val chooser = Intent.createChooser(sendIntent, "Compartir consulta SQL")
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            // Fallback if WhatsApp is not installed
            val chooser = Intent.createChooser(sendIntent.apply { setPackage(null) }, "Compartir consulta SQL")
            context.startActivity(chooser)
        }
    }

    // Share query as a .sql file attached
    fun shareQuerySqlFile(item: QueryHistoryItem, targetWhatsApp: Boolean = false) {
        try {
            val cacheDir = File(context.cacheDir, "shared_sql")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "consulta_${System.currentTimeMillis()}.sql"
            val file = File(cacheDir, fileName)
            file.writeText(
                "-- MiniSQL Pro - Consulta Exportada\n" +
                "-- Base de datos: $activeDbName\n" +
                "-- Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))}\n\n" +
                "${item.sql.trim()};\n"
            )

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Consulta SQL: $fileName")
                putExtra(Intent.EXTRA_TEXT, "Adjunto consulta SQL de $activeDbName generada desde MiniSQL Pro.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetWhatsApp) {
                    setPackage("com.whatsapp")
                }
            }

            if (targetWhatsApp) {
                context.startActivity(sendIntent)
            } else {
                val chooser = Intent.createChooser(sendIntent, "Compartir archivo .sql")
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar archivo .sql: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Share entire active history as a single classroom script file
    fun shareAllHistoryScript(targetWhatsApp: Boolean = false) {
        if (last20Queries.isEmpty()) {
            Toast.makeText(context, "El historial está vacío", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cacheDir = File(context.cacheDir, "shared_sql")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "clase_${activeDbName.removeSuffix(".db")}_${System.currentTimeMillis()}.sql"
            val file = File(cacheDir, fileName)

            val sb = StringBuilder()
            sb.append("-- ==========================================\n")
            sb.append("-- MiniSQL Pro - Guión de Consultas de Clase\n")
            sb.append("-- Base de datos: $activeDbName\n")
            sb.append("-- Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            sb.append("-- Total de consultas: ${last20Queries.size}\n")
            sb.append("-- ==========================================\n\n")

            last20Queries.reversed().forEachIndexed { idx, q ->
                sb.append("-- Consulta #${idx + 1} (${timeFormat.format(Date(q.timestamp))})\n")
                sb.append(q.sql.trim().removeSuffix(";"))
                sb.append(";\n\n")
            }

            file.writeText(sb.toString())

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Guión SQL de Clase: $fileName")
                putExtra(Intent.EXTRA_TEXT, "Guión de clase con ${last20Queries.size} consultas SQL (${activeDbName.removeSuffix(".db")}).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetWhatsApp) {
                    setPackage("com.whatsapp")
                }
            }

            if (targetWhatsApp) {
                context.startActivity(sendIntent)
            } else {
                val chooser = Intent.createChooser(sendIntent, "Compartir guión de clase")
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar guión: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 350.dp)
            .testTag("history_side_panel"),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Historial SQL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${last20Queries.size}/20",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Consultas completas y compartibles",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cerrar panel")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar en historial...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subheader with actions: count + Share All + Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredQueries.size} registradas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (last20Queries.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showShareAllDialog = true },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Compartir clase",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("Vaciar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Query items list
            if (filteredQueries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No hay resultados para '$searchQuery'" else "No hay consultas en el historial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Las consultas ejecutadas se registrarán aquí completas para reutilizarlas o compartirlas.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(filteredQueries, key = { _, item -> item.id }) { index, item ->
                        val lineCount = remember(item.sql) { item.sql.lines().size }
                        val isVeryLong = lineCount > 6 || item.sql.length > 250
                        val isExpanded = expandedMap[item.id] ?: (!isVeryLong)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Top Row: Badge, Status, Time, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (item.isSuccess) Icons.Outlined.CheckCircleOutline else Icons.Outlined.ErrorOutline,
                                                contentDescription = null,
                                                tint = if (item.isSuccess) SqlSuccess else SqlError,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (item.isSuccess) "${item.elapsedMs}ms • ${item.rowsCount} filas" else "Error",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (item.isSuccess) SqlSuccess else SqlError,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = getRelativeTime(item.timestamp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        IconButton(
                                            onClick = { onDeleteQuery(item.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Eliminar de historial",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // SQL Code box: Displays COMPLETE query without cutting it off!
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            0.8.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onSelectQuery(item.sql)
                                            onCloseDrawer()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = item.sql.trim(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = Int.MAX_VALUE,
                                            overflow = TextOverflow.Clip,
                                            softWrap = true
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Buttons Row: Cargar, Ejecutar, Copiar, Compartir (WhatsApp / SQL)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Cargar
                                    FilledTonalButton(
                                        onClick = {
                                            onSelectQuery(item.sql)
                                            onCloseDrawer()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Input,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Cargar", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                    }

                                    // 2. Ejecutar
                                    FilledTonalButton(
                                        onClick = {
                                            onExecuteQuery(item.sql)
                                            onCloseDrawer()
                                        },
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(32.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Ejecutar", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                    }

                                    // 3. Copiar
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable { copyToClipboard(item.sql) },
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copiar SQL",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // 4. Compartir (WhatsApp / SQL)
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable { queryToShare = item },
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = "Compartir consulta",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
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
    }

    // Modal: Compartir consulta individual
    queryToShare?.let { item ->
        AlertDialog(
            onDismissRequest = { queryToShare = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text("Compartir Consulta SQL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Cómo deseas enviar esta consulta a tus estudiantes o colegas?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option A: WhatsApp Direct Text
                    Card(
                        onClick = {
                            shareQueryText(item, targetWhatsApp = true)
                            queryToShare = null
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF25D366).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = Color(0xFF128C7E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Enviar texto a WhatsApp",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Mensaje con formato SQL y datos de la consulta",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option B: File .sql
                    Card(
                        onClick = {
                            shareQuerySqlFile(item, targetWhatsApp = true)
                            queryToShare = null
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SqlBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = SqlBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Adjuntar archivo .sql",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Genera un script .sql listo para abrir o importar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option C: Other Apps
                    TextButton(
                        onClick = {
                            shareQueryText(item, targetWhatsApp = false)
                            queryToShare = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compartir con otra aplicación...")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { queryToShare = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal: Compartir guión completo de la clase
    if (showShareAllDialog) {
        AlertDialog(
            onDismissRequest = { showShareAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text("Compartir Guión de Clase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Se compilarán las ${last20Queries.size} consultas del historial en un archivo de script (.sql) ordenado cronológicamente para que tus alumnos lo descarguen y estudien.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareAllHistoryScript(targetWhatsApp = true)
                        showShareAllDialog = false
                    }
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar por WhatsApp")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        shareAllHistoryScript(targetWhatsApp = false)
                        showShareAllDialog = false
                    }
                ) {
                    Text("Otras apps")
                }
            }
        )
    }
}
