package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.util.UUID
import kotlin.math.roundToInt

data class TableNodeState(
    val tableName: String,
    var posX: Float,
    var posY: Float,
    var width: Float = 240f,
    var height: Float = 180f
)

data class NewColumnDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "INTEGER",
    val isPk: Boolean = false,
    val notNull: Boolean = false,
    val defaultValue: String = "",
    val isAutoIncrement: Boolean = false,
    val isFk: Boolean = false,
    val fkTargetTable: String = "",
    val fkTargetColumn: String = "id",
    val fkOnDelete: String = "CASCADE"
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
                                imageVector = Icons.Outlined.ViewModule,
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
                                Icon(Icons.Outlined.Add, contentDescription = null)
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
                Icon(Icons.Outlined.Refresh, contentDescription = "Reorganizar", modifier = Modifier.size(18.dp))
            }

            FilledTonalButton(
                onClick = { showConnectDialog = true },
                enabled = schemas.size >= 2
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Relación", style = MaterialTheme.typography.labelSmall)
            }
        }

        // FAB to add new table visually
        ExtendedFloatingActionButton(
            onClick = { showNewTableDialog = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
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
            allSchemas = schemas,
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
                        imageVector = Icons.Outlined.TableChart,
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
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = "Primary Key",
                                    tint = SqlWarning,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else if (isFk) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
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
    allSchemas: List<TableSchemaDetail> = emptyList(),
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
        }.toMutableList()

        // Foreign Key constraints
        columns.filter { it.isFk && it.fkTargetTable.isNotBlank() && it.name.isNotBlank() }.forEach { col ->
            val targetCol = if (col.fkTargetColumn.isNotBlank()) col.fkTargetColumn else "id"
            val onDelete = if (col.fkOnDelete.isNotBlank()) " ON DELETE ${col.fkOnDelete}" else ""
            lines.add("    FOREIGN KEY (\"${col.name.trim()}\") REFERENCES \"${col.fkTargetTable}\"(\"$targetCol\")$onDelete")
        }

        return "CREATE TABLE IF NOT EXISTS \"${if (cleanName.isNotBlank()) cleanName else "nueva_tabla"}\" (\n" +
                lines.joinToString(",\n") +
                "\n);"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pinned Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SqlBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TableChart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Diseñador de Tabla",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Define columnas, tipos y restricciones",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Scrollable Form Body (Adapts dynamically when soft keyboard appears)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Table Name Field
                        OutlinedTextField(
                            value = tableName,
                            onValueChange = { tableName = it },
                            label = { Text("Nombre de la tabla *") },
                            placeholder = { Text("ej. empleados, productos, notas") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Columns Header & Add Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Columnas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${columns.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    columns.add(
                                        NewColumnDraft(
                                            name = "col_${columns.size + 1}",
                                            type = "TEXT"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Añadir Columna", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (columns.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No hay columnas en esta tabla",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = {
                                        columns.add(NewColumnDraft(name = "id", type = "INTEGER", isPk = true, notNull = true))
                                    }) {
                                        Text("+ Agregar columna 'id' (PK)")
                                    }
                                }
                            }
                        }

                        // Column draft cards
                        columns.forEach { col ->
                            val colIndex = columns.indexOfFirst { it.id == col.id }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (col.isPk) SqlWarning.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Row 1: Name input and Delete Button (ALWAYS functional)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = col.name,
                                            onValueChange = { newName ->
                                                if (colIndex >= 0) {
                                                    columns[colIndex] = col.copy(name = newName)
                                                }
                                            },
                                            label = { Text("Nombre de campo") },
                                            placeholder = { Text("ej. precio, email") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Explicit Red Trash Icon Button to Delete Column
                                        IconButton(
                                            onClick = {
                                                columns.remove(col)
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Eliminar columna",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Row 2: Quick Data Type Selector Chips
                                    Text(
                                        text = "Tipo de dato:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val supportedTypes = listOf("INTEGER", "TEXT", "REAL", "BLOB")
                                        supportedTypes.forEach { typeOption ->
                                            val isSelected = col.type == typeOption
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (colIndex >= 0) {
                                                            columns[colIndex] = col.copy(type = typeOption)
                                                        }
                                                    },
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = typeOption,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Row 3: Constraints (PK, NOT NULL, AUTOINCREMENT)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Primary Key
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                if (colIndex >= 0) {
                                                    val newPk = !col.isPk
                                                    columns[colIndex] = col.copy(
                                                        isPk = newPk,
                                                        isAutoIncrement = if (newPk && col.type == "INTEGER") col.isAutoIncrement else false
                                                    )
                                                }
                                            }
                                        ) {
                                            Checkbox(
                                                checked = col.isPk,
                                                onCheckedChange = { isChecked ->
                                                    if (colIndex >= 0) {
                                                        columns[colIndex] = col.copy(
                                                            isPk = isChecked,
                                                            isAutoIncrement = if (isChecked && col.type == "INTEGER") col.isAutoIncrement else false
                                                        )
                                                    }
                                                }
                                            )
                                            Icon(
                                                imageVector = Icons.Outlined.Key,
                                                contentDescription = null,
                                                tint = SqlWarning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "PK",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Auto Increment (if PK & INTEGER)
                                        if (col.isPk && col.type == "INTEGER") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    if (colIndex >= 0) {
                                                        columns[colIndex] = col.copy(isAutoIncrement = !col.isAutoIncrement)
                                                    }
                                                }
                                            ) {
                                                Checkbox(
                                                    checked = col.isAutoIncrement,
                                                    onCheckedChange = { isChecked ->
                                                        if (colIndex >= 0) {
                                                            columns[colIndex] = col.copy(isAutoIncrement = isChecked)
                                                        }
                                                    }
                                                )
                                                Text("AUTO_INC", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                                            }
                                        }

                                        // Not Null (if not PK)
                                        if (!col.isPk) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    if (colIndex >= 0) {
                                                        columns[colIndex] = col.copy(notNull = !col.notNull)
                                                    }
                                                }
                                            ) {
                                                Checkbox(
                                                    checked = col.notNull,
                                                    onCheckedChange = { isChecked ->
                                                        if (colIndex >= 0) {
                                                            columns[colIndex] = col.copy(notNull = isChecked)
                                                        }
                                                    }
                                                )
                                                Text("NOT NULL", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                                            }
                                        }

                                        // Foreign Key Checkbox (if other tables exist)
                                        if (!col.isPk && existingTables.isNotEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    if (colIndex >= 0) {
                                                        val newIsFk = !col.isFk
                                                        val defaultTarget = existingTables.firstOrNull() ?: ""
                                                        columns[colIndex] = col.copy(
                                                            isFk = newIsFk,
                                                            fkTargetTable = if (newIsFk && col.fkTargetTable.isBlank()) defaultTarget else col.fkTargetTable
                                                        )
                                                    }
                                                }
                                            ) {
                                                Checkbox(
                                                    checked = col.isFk,
                                                    onCheckedChange = { isChecked ->
                                                        if (colIndex >= 0) {
                                                            val defaultTarget = existingTables.firstOrNull() ?: ""
                                                            columns[colIndex] = col.copy(
                                                                isFk = isChecked,
                                                                fkTargetTable = if (isChecked && col.fkTargetTable.isBlank()) defaultTarget else col.fkTargetTable
                                                            )
                                                        }
                                                    }
                                                )
                                                Text("FK (Relación)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    // Foreign Key Target Table Configuration
                                    if (col.isFk && existingTables.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("Vincular como Clave Foránea a:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                var expandTargetTable by remember { mutableStateOf(false) }
                                                Box {
                                                    FilledTonalButton(
                                                        onClick = { expandTargetTable = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Tabla destino: ${col.fkTargetTable.ifBlank { "Elegir tabla..." }}", style = MaterialTheme.typography.labelSmall)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                                                    }

                                                    DropdownMenu(
                                                        expanded = expandTargetTable,
                                                        onDismissRequest = { expandTargetTable = false }
                                                    ) {
                                                        existingTables.forEach { tbl ->
                                                            DropdownMenuItem(
                                                                text = { Text(tbl) },
                                                                onClick = {
                                                                    if (colIndex >= 0) {
                                                                        columns[colIndex] = col.copy(fkTargetTable = tbl)
                                                                    }
                                                                    expandTargetTable = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Default value (optional)
                                    if (!col.isPk) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = col.defaultValue,
                                            onValueChange = { newVal ->
                                                if (colIndex >= 0) {
                                                    columns[colIndex] = col.copy(defaultValue = newVal)
                                                }
                                            },
                                            label = { Text("Valor por defecto (opcional)") },
                                            placeholder = { Text("ej. 'activo', 0, CURRENT_TIMESTAMP") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live SQL Preview
                        Text(
                            text = "Código SQL generado automáticamente:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generateSql(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Pinned Bottom Actions
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = {
                                    if (tableName.isNotBlank()) {
                                        onCreateTable(generateSql())
                                    }
                                },
                                enabled = tableName.isNotBlank() && columns.any { it.name.isNotBlank() },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Crear Tabla", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
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

    var sourceCol by remember(sourceTable) { mutableStateOf(sourceCols.find { it.endsWith("_id", ignoreCase = true) } ?: sourceCols.firstOrNull() ?: "") }
    var targetCol by remember(targetTable) { mutableStateOf(targetCols.find { it == "id" } ?: targetCols.firstOrNull() ?: "") }

    var expandSourceTable by remember { mutableStateOf(false) }
    var expandSourceCol by remember { mutableStateOf(false) }
    var expandTargetTable by remember { mutableStateOf(false) }
    var expandTargetCol by remember { mutableStateOf(false) }

    val relationSql = remember(sourceTable, sourceCol, targetTable, targetCol) {
        "-- Relación relacional:\n" +
        "-- Clave foránea: $sourceTable.$sourceCol ➔ $targetTable.$targetCol\n" +
        "CREATE INDEX IF NOT EXISTS idx_${sourceTable}_${sourceCol} ON \"$sourceTable\"(\"$sourceCol\");"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vincular Relación (FK)")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Selecciona las tablas y los campos que componen la relación de clave foránea:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Source Table & Column
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("1. Tabla Origen (Donde reside la FK):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Source Table Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { expandSourceTable = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(sourceTable.ifBlank { "Tabla..." }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = expandSourceTable, onDismissRequest = { expandSourceTable = false }) {
                                    schemas.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.tableName) },
                                            onClick = {
                                                sourceTable = s.tableName
                                                expandSourceTable = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Source Column Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { expandSourceCol = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(sourceCol.ifBlank { "Columna..." }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = expandSourceCol, onDismissRequest = { expandSourceCol = false }) {
                                    sourceCols.forEach { colName ->
                                        DropdownMenuItem(
                                            text = { Text(colName) },
                                            onClick = {
                                                sourceCol = colName
                                                expandSourceCol = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Target Table & Column
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("2. Apunta a Tabla Destino (Clave Primaria PK):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Target Table Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { expandTargetTable = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(targetTable.ifBlank { "Destino..." }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = expandTargetTable, onDismissRequest = { expandTargetTable = false }) {
                                    schemas.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.tableName) },
                                            onClick = {
                                                targetTable = s.tableName
                                                expandTargetTable = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Target Column Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { expandTargetCol = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(targetCol.ifBlank { "Columna..." }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = expandTargetCol, onDismissRequest = { expandTargetCol = false }) {
                                    targetCols.forEach { colName ->
                                        DropdownMenuItem(
                                            text = { Text(colName) },
                                            onClick = {
                                                targetCol = colName
                                                expandTargetCol = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Educational SQL Preview
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = relationSql,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sourceTable.isNotBlank() && sourceCol.isNotBlank() && targetTable.isNotBlank() && targetCol.isNotBlank()) {
                        onConnect(sourceTable, sourceCol, targetTable, targetCol)
                    }
                },
                enabled = sourceTable.isNotBlank() && sourceCol.isNotBlank() && targetTable.isNotBlank() && targetCol.isNotBlank()
            ) {
                Text("Vincular Relación")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
