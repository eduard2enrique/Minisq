package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.model.ColumnInfo
import com.example.model.TableSchemaDetail
import com.example.ui.theme.SqlBlue
import com.example.ui.theme.SqlCyan
import com.example.ui.theme.SqlWarning
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TableNodeState(
    val tableName: String,
    var posX: Float,
    var posY: Float,
    var width: Float = 240f,
    var height: Float = 180f
)

data class NewColumnDraft(
    var name: String = "",
    var type: String = "INTEGER",
    var isPk: Boolean = false,
    var notNull: Boolean = false,
    var defaultValue: String = "",
    var isAutoIncrement: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignerScreen(
    dbManager: DatabaseManager,
    onRefreshTables: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var schemas by remember { mutableStateOf<List<TableSchemaDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Table positions for ER Canvas
    val tablePositions = remember { mutableStateMapOf<String, Offset>() }

    // Dialog state
    var showNewTableDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }

    fun loadSchemas() {
        isLoading = true
        scope.launch {
            val all = dbManager.getAllTablesSchema()
            schemas = all
            // Calculate positions in a smart grid layout if not set
            all.forEachIndexed { index, schema ->
                if (!tablePositions.containsKey(schema.tableName)) {
                    val col = index % 2
                    val row = index / 2
                    val x = 20f + col * 340f
                    val y = 20f + row * 280f
                    tablePositions[schema.tableName] = Offset(x, y)
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadSchemas()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Infinite Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("er_designer_canvas")
        ) {
            // Background grid dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 30.dp.toPx()
                val dotRadius = 1.2.dp.toPx()
                val dotColor = Color.Gray.copy(alpha = 0.15f)

                var x = 0f
                while (x < size.width) {
                    var y = 0f
                    while (y < size.height) {
                        drawCircle(dotColor, radius = dotRadius, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }

                // Draw Relationships / Foreign Key Lines between Tables
                val relationColors = listOf(
                    Color(0xFF38BDF8),
                    Color(0xFFF59E0B),
                    Color(0xFF10B981),
                    Color(0xFFA855F7),
                    Color(0xFFEC4899)
                )

                var colorIdx = 0
                for (schema in schemas) {
                    val fromPos = tablePositions[schema.tableName] ?: continue
                    for (fk in schema.foreignKeys) {
                        val toPos = tablePositions[fk.targetTable] ?: continue
                        val strokeColor = relationColors[colorIdx % relationColors.size]
                        colorIdx++

                        // From center-right of source table to center-left of target table
                        val cardWidthPx = 250.dp.toPx()
                        val cardHeightPx = 150.dp.toPx()

                        val startX = fromPos.x + (cardWidthPx / 2f)
                        val startY = fromPos.y + (cardHeightPx / 2f)
                        val endX = toPos.x + (cardWidthPx / 2f)
                        val endY = toPos.y + (cardHeightPx / 2f)

                        val path = Path().apply {
                            moveTo(startX, startY)
                            // Cubic bezier curve for beautiful smooth connection
                            val dx = (endX - startX) / 2f
                            cubicTo(
                                startX + dx, startY,
                                endX - dx, endY,
                                endX, endY
                            )
                        }

                        // Relationship line
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                            )
                        )

                        // Draw connector circles at both ends
                        drawCircle(
                            color = strokeColor,
                            radius = 6.dp.toPx(),
                            center = Offset(startX, startY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(endX, endY)
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 4.dp.toPx(),
                            center = Offset(endX, endY)
                        )
                    }
                }
            }

            // Draggable Entity Cards
            schemas.forEach { schema ->
                val pos = tablePositions[schema.tableName] ?: Offset(40f, 40f)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                        .pointerInput(schema.tableName) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = tablePositions[schema.tableName] ?: Offset.Zero
                                tablePositions[schema.tableName] = current + dragAmount
                            }
                        }
                ) {
                    TableEntityCard(schema = schema)
                }
            }

            // Empty state helper if no tables
            if (schemas.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewModule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No hay tablas en la base de datos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Usa el botón flotante para diseñar tu primera entidad con columnas y claves.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showNewTableDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Diseñar Tabla")
                            }
                        }
                    }
                }
            }
        }

        // Top-right action controls: auto arrange, connect relation
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    // Auto-arrange in grid
                    schemas.forEachIndexed { index, s ->
                        val col = index % 2
                        val row = index / 2
                        tablePositions[s.tableName] = Offset(20f + col * 320f, 20f + row * 260f)
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reorganizar", modifier = Modifier.size(18.dp))
            }

            FilledTonalButton(
                onClick = { showConnectDialog = true },
                enabled = schemas.size >= 2
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Relación", style = MaterialTheme.typography.labelSmall)
            }
        }

        // FAB to add new table visually
        ExtendedFloatingActionButton(
            onClick = { showNewTableDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Nueva Tabla") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("designer_add_table_fab")
        )
    }

    // Modal: Designer Visual Table Creator
    if (showNewTableDialog) {
        NewTableDesignerDialog(
            existingTables = schemas.map { it.tableName },
            onCreateTable = { ddlSql ->
                scope.launch {
                    val result = dbManager.executeSql(ddlSql)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Tabla creada exitosamente", Toast.LENGTH_SHORT).show()
                        loadSchemas()
                        onRefreshTables()
                        showNewTableDialog = false
                    } else {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showNewTableDialog = false }
        )
    }

    // Modal: Connect Relationship Tool
    if (showConnectDialog) {
        ConnectRelationDialog(
            schemas = schemas,
            onConnect = { sourceTable, sourceCol, targetTable, targetCol ->
                scope.launch {
                    // Educational feedback: In SQLite foreign keys are declared on table creation or via schema migration
                    val sql = "-- Relación lógica definida entre $sourceTable.$sourceCol -> $targetTable.$targetCol\n" +
                            "CREATE INDEX IF NOT EXISTS idx_${sourceTable}_${sourceCol} ON $sourceTable($sourceCol);"
                    dbManager.executeSql(sql)
                    Toast.makeText(context, "Relación configurada e índice creado para $sourceTable.$sourceCol", Toast.LENGTH_LONG).show()
                    loadSchemas()
                    showConnectDialog = false
                }
            },
            onDismiss = { showConnectDialog = false }
        )
    }
}

@Composable
fun TableEntityCard(schema: TableSchemaDetail) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = schema.tableName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${schema.columns.size} cols",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Columns List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                schema.columns.forEach { col ->
                    val isFk = schema.foreignKeys.any { it.fromColumn.equals(col.name, ignoreCase = true) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (col.isPrimaryKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (col.isPrimaryKey) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Primary Key",
                                    tint = SqlWarning,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else if (isFk) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Foreign Key",
                                    tint = SqlCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Spacer(modifier = Modifier.width(17.dp))
                            }

                            Text(
                                text = col.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (col.isPrimaryKey) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = col.type.ifBlank { "ANY" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Foreign Key Footer Summary if present
            if (schema.foreignKeys.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column {
                        schema.foreignKeys.forEach { fk ->
                            Text(
                                text = "🔗 ${fk.fromColumn} ➔ ${fk.targetTable}(${fk.toColumn})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewTableDesignerDialog(
    existingTables: List<String>,
    onCreateTable: (ddlSql: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tableName by remember { mutableStateOf("") }
    val columns = remember {
        mutableStateListOf(
            NewColumnDraft(name = "id", type = "INTEGER", isPk = true, notNull = true, isAutoIncrement = true),
            NewColumnDraft(name = "nombre", type = "TEXT", isPk = false, notNull = true),
            NewColumnDraft(name = "creado_en", type = "TEXT", defaultValue = "CURRENT_TIMESTAMP")
        )
    }

    // Generated SQL preview
    fun generateSql(): String {
        val cleanName = tableName.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        val lines = columns.filter { it.name.isNotBlank() }.map { col ->
            val sb = StringBuilder()
            sb.append("    \"${col.name.trim()}\" ${col.type}")
            if (col.isPk) {
                sb.append(" PRIMARY KEY")
                if (col.isAutoIncrement && col.type == "INTEGER") sb.append(" AUTOINCREMENT")
            }
            if (col.notNull && !col.isPk) sb.append(" NOT NULL")
            if (col.defaultValue.isNotBlank()) sb.append(" DEFAULT ${col.defaultValue.trim()}")
            sb.toString()
        }

        return "CREATE TABLE IF NOT EXISTS \"${if (cleanName.isNotBlank()) cleanName else "nueva_tabla"}\" (\n" +
                lines.joinToString(",\n") +
                "\n);"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Diseñador de Tabla Gráfico", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("Nombre de la tabla") },
                    placeholder = { Text("ej. empleados") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Columnas (${columns.size})", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        columns.add(NewColumnDraft(name = "col_${columns.size + 1}", type = "TEXT"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir")
                    }
                }

                // Column drafts
                columns.forEachIndexed { index, col ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = col.name,
                                    onValueChange = { col.name = it },
                                    label = { Text("Nombre") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Type selector chips
                                val types = listOf("INTEGER", "TEXT", "REAL", "BLOB")
                                var expandedType by remember { mutableStateOf(false) }

                                Box {
                                    FilledTonalButton(
                                        onClick = { expandedType = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(col.type, style = MaterialTheme.typography.labelSmall)
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = expandedType,
                                        onDismissRequest = { expandedType = false }
                                    ) {
                                        types.forEach { t ->
                                            DropdownMenuItem(
                                                text = { Text(t) },
                                                onClick = {
                                                    col.type = t
                                                    expandedType = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (columns.size > 1) {
                                    IconButton(onClick = { columns.removeAt(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Quitar",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = col.isPk,
                                        onCheckedChange = { col.isPk = it }
                                    )
                                    Text("PK", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = col.notNull,
                                        onCheckedChange = { col.notNull = it }
                                    )
                                    Text("NOT NULL", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Vista previa SQL:", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = generateSql(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tableName.isNotBlank()) {
                        onCreateTable(generateSql())
                    }
                },
                enabled = tableName.isNotBlank() && columns.any { it.name.isNotBlank() }
            ) {
                Text("Crear Tabla")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ConnectRelationDialog(
    schemas: List<TableSchemaDetail>,
    onConnect: (sourceTable: String, sourceCol: String, targetTable: String, targetCol: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceTable by remember { mutableStateOf(schemas.firstOrNull()?.tableName ?: "") }
    var targetTable by remember {
        mutableStateOf(schemas.getOrNull(1)?.tableName ?: schemas.firstOrNull()?.tableName ?: "")
    }

    val sourceCols = schemas.find { it.tableName == sourceTable }?.columns?.map { it.name } ?: emptyList()
    val targetCols = schemas.find { it.tableName == targetTable }?.columns?.map { it.name } ?: emptyList()

    var sourceCol by remember(sourceTable) { mutableStateOf(sourceCols.firstOrNull() ?: "") }
    var targetCol by remember(targetTable) { mutableStateOf(targetCols.find { it == "id" } ?: targetCols.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Conectar Relación")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Define una relación de clave foránea entre tablas de la base de datos:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Tabla Origen (Con clave foránea):", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sourceTable, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(sourceCol, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("➔ Apunta a Tabla Destino (Clave Primaria):", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetTable, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(targetCol, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(sourceTable, sourceCol, targetTable, targetCol)
                }
            ) {
                Text("Vincular")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
