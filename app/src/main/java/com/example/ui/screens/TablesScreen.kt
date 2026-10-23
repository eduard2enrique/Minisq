package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.DatabaseManager
import com.example.model.ColumnInfo
import com.example.model.SqlExecutionResult
import com.example.model.TableSchemaDetail
import com.example.model.TableSummary
import com.example.ui.theme.SqlWarning
import kotlinx.coroutines.launch

@Composable
fun TablesScreen(
    dbManager: DatabaseManager,
    tables: List<TableSummary>,
    onRefreshTables: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTableName by remember { mutableStateOf<String?>(null) }

    // Table detail state
    var selectedTableSchema by remember { mutableStateOf<TableSchemaDetail?>(null) }
    var tableDataResult by remember { mutableStateOf<SqlExecutionResult?>(null) }
    var selectedDetailTab by remember { mutableIntStateOf(0) } // 0: Datos, 1: Estructura, 2: SQL DDL
    var isLoadingData by remember { mutableStateOf(false) }

    // Dialog state
    var showInsertRowDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDropConfirmDialog by remember { mutableStateOf(false) }
    var editingRowIndex by remember { mutableStateOf<Int?>(null) }

    fun loadTableData(tableName: String) {
        isLoadingData = true
        scope.launch {
            val schema = dbManager.getTableSchema(tableName)
            selectedTableSchema = schema
            val dataRes = dbManager.executeSql("SELECT * FROM \"$tableName\" LIMIT 200;")
            tableDataResult = dataRes
            isLoadingData = false
        }
    }

    LaunchedEffect(selectedTableName) {
        selectedTableName?.let { loadTableData(it) }
    }

    val filteredTables = remember(tables, searchQuery) {
        if (searchQuery.isBlank()) tables
        else tables.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        if (selectedTableName == null) {
            // Table Overview List
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar tablas...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(end = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                IconButton(onClick = onRefreshTables) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No se encontraron tablas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTables) { table ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTableName = table.name },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = table.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${table.rowCount} filas • ${table.columnCount} columnas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text("Ver detalles ➔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            // Selected Table Detail View
            val tableName = selectedTableName!!
            val schema = selectedTableSchema

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        selectedTableName = null
                        onRefreshTables()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tableDataResult?.rows?.size ?: 0} registros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { showInsertRowDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Fila", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(onClick = { loadTableData(tableName) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-tabs: Datos, Estructura, SQL DDL
            TabRow(
                selectedTabIndex = selectedDetailTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedDetailTab == 0,
                    onClick = { selectedDetailTab = 0 },
                    text = { Text("Datos") }
                )
                Tab(
                    selected = selectedDetailTab == 1,
                    onClick = { selectedDetailTab = 1 },
                    text = { Text("Estructura") }
                )
                Tab(
                    selected = selectedDetailTab == 2,
                    onClick = { selectedDetailTab = 2 },
                    text = { Text("SQL DDL") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoadingData) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedDetailTab) {
                    0 -> {
                        // Data View
                        val data = tableDataResult
                        if (data == null || data.rows.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("La tabla no tiene registros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { showInsertRowDialog = true }) {
                                        Text("Insertar Primer Registro")
                                    }
                                }
                            }
                        } else {
                            val hScroll = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(hScroll)
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(vertical = 8.dp, horizontal = 6.dp)
                                    ) {
                                        Text("#", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                                        data.columns.forEach { col ->
                                            Text(
                                                text = col,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(130.dp).padding(horizontal = 4.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text("Acciones", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold)
                                    }

                                    // Rows
                                    LazyColumn(modifier = Modifier.weight(1f)) {
                                        itemsIndexed(data.rows) { idx, row ->
                                            Row(
                                                modifier = Modifier
                                                    .background(
                                                        if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${idx + 1}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                                                row.forEach { valStr ->
                                                    Text(
                                                        text = valStr,
                                                        modifier = Modifier.width(130.dp).padding(horizontal = 4.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                // Row delete button
                                                IconButton(
                                                    onClick = {
                                                        val pkCol = schema?.columns?.find { it.isPrimaryKey }?.name ?: data.columns.firstOrNull() ?: "id"
                                                        val pkIndex = data.columns.indexOf(pkCol)
                                                        val pkVal = row.getOrNull(if (pkIndex >= 0) pkIndex else 0) ?: ""
                                                        scope.launch {
                                                            dbManager.deleteTableRow(tableName, pkCol, pkVal)
                                                            loadTableData(tableName)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Borrar fila",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Structure View
                        if (schema != null) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text("Columnas (${schema.columns.size})", style = MaterialTheme.typography.titleSmall)
                                }
                                items(schema.columns) { col ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (col.isPrimaryKey) {
                                                    Icon(
                                                        imageVector = Icons.Default.Key,
                                                        contentDescription = "PK",
                                                        tint = SqlWarning,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(col.name, fontWeight = FontWeight.Bold)
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = col.type.ifBlank { "TEXT" },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (col.notNull) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "NOT NULL",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (schema.foreignKeys.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Claves Foráneas (Relaciones)", style = MaterialTheme.typography.titleSmall)
                                    }
                                    items(schema.foreignKeys) { fk ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("${fk.fromColumn} ➔ ${fk.targetTable}(${fk.toColumn})", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showDeleteConfirmDialog = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Vaciar Filas")
                                        }

                                        Button(
                                            onClick = { showDropConfirmDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Eliminar Tabla")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // SQL DDL View
                        val createSql = schema?.createSql ?: "-- No hay DDL disponible"
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Definición SQLite:", style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("SQL DDL", createSql))
                                    Toast.makeText(context, "DDL copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar SQL")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = createSql,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Insert Row Dialog
    if (showInsertRowDialog && selectedTableSchema != null) {
        val schema = selectedTableSchema!!
        val inputValues = remember { mutableStateMapOf<String, String>() }

        AlertDialog(
            onDismissRequest = { showInsertRowDialog = false },
            title = { Text("Insertar Registro en ${schema.tableName}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schema.columns.forEach { col ->
                        val isAutoPk = col.isPrimaryKey && col.type.contains("INT", ignoreCase = true)
                        OutlinedTextField(
                            value = inputValues[col.name] ?: "",
                            onValueChange = { inputValues[col.name] = it },
                            label = {
                                Text("${col.name} (${col.type})${if (col.notNull) " *" else ""}")
                            },
                            placeholder = {
                                if (isAutoPk) Text("(Automático si se deja vacío)")
                                else if (col.defaultValue != null) Text("Default: ${col.defaultValue}")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        dbManager.insertTableRow(schema.tableName, inputValues.toMap())
                        loadTableData(schema.tableName)
                        showInsertRowDialog = false
                    }
                }) {
                    Text("Insertar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertRowDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Modal: Truncate confirmation
    if (showDeleteConfirmDialog && selectedTableName != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Vaciar tabla?") },
            text = { Text("Se eliminarán todos los registros de la tabla '$selectedTableName'. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            dbManager.clearTable(selectedTableName!!)
                            loadTableData(selectedTableName!!)
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Modal: Drop table confirmation
    if (showDropConfirmDialog && selectedTableName != null) {
        AlertDialog(
            onDismissRequest = { showDropConfirmDialog = false },
            title = { Text("¿Eliminar tabla?") },
            text = { Text("Se eliminará completamente la tabla '$selectedTableName' y su estructura.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            dbManager.dropTable(selectedTableName!!)
                            selectedTableName = null
                            onRefreshTables()
                            showDropConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDropConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
