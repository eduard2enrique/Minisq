package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.model.TableSummary
import com.example.ui.theme.SqlBlue
import com.example.ui.theme.SqlSuccess
import kotlinx.coroutines.launch

data class DatabaseTemplateInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val tables: List<String>,
    val defaultProjectName: String
)

@Composable
fun ProjectsHubDialog(
    dbManager: DatabaseManager,
    currentDb: String,
    databases: List<String>,
    onSelectDb: (String) -> Unit,
    onCreateDb: (name: String, template: String) -> Unit,
    onDeleteDb: (String) -> Unit,
    onRenameDb: (oldName: String, newName: String) -> Unit,
    onResetDb: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mis Proyectos, 1: Crear con Plantilla

    // Pre-built template definitions
    val templates = remember {
        listOf(
            DatabaseTemplateInfo(
                id = "empty",
                title = "Base de Datos en Blanco",
                description = "Base de datos SQLite limpia sin tablas predefinidas, lista para crear tu propio esquema.",
                icon = Icons.Outlined.TableChart,
                iconColor = Color(0xFF94A3B8),
                tables = emptyList(),
                defaultProjectName = "mi_nueva_base"
            ),
            DatabaseTemplateInfo(
                id = "sample",
                title = "Tienda & Clientes",
                description = "Comercio con clientes, catálogo de productos y pedidos vinculados mediante claves foráneas.",
                icon = Icons.Outlined.Storefront,
                iconColor = Color(0xFF38BDF8),
                tables = listOf("clientes", "productos", "pedidos", "detalles_pedido"),
                defaultProjectName = "tienda_comercial"
            ),
            DatabaseTemplateInfo(
                id = "ecommerce",
                title = "E-Commerce Completo",
                description = "Plataforma de compras con control de usuarios, categorías y catálogo de artículos.",
                icon = Icons.Outlined.ShoppingCart,
                iconColor = Color(0xFFFBBF24),
                tables = listOf("usuarios", "categorias", "articulos"),
                defaultProjectName = "ecommerce_digital"
            ),
            DatabaseTemplateInfo(
                id = "university",
                title = "Universidad & Cursos",
                description = "Gestión académica con profesores, cursos impartidos, alumnos y calificaciones ponderadas.",
                icon = Icons.Outlined.School,
                iconColor = Color(0xFFA78BFA),
                tables = listOf("profesores", "cursos", "estudiantes", "calificaciones"),
                defaultProjectName = "gestion_universidad"
            ),
            DatabaseTemplateInfo(
                id = "projects",
                title = "Gestión de Proyectos",
                description = "Seguimiento de proyectos ágiles, equipo de miembros, tareas y asignaciones por prioridad.",
                icon = Icons.Outlined.TaskAlt,
                iconColor = Color(0xFF34D399),
                tables = listOf("proyectos", "miembros", "tareas"),
                defaultProjectName = "proyectos_equipo"
            ),
            DatabaseTemplateInfo(
                id = "clinic",
                title = "Clínica & Citas Médicas",
                description = "Sistema de salud con médicos especialistas, registro de pacientes e historial de citas.",
                icon = Icons.Outlined.LocalHospital,
                iconColor = Color(0xFFFB7185),
                tables = listOf("medicos", "pacientes", "citas"),
                defaultProjectName = "clinica_salud"
            )
        )
    }

    // New project form state
    var selectedTemplateId by remember { mutableStateOf(templates.first().id) }
    var newProjectName by remember { mutableStateOf(templates.first().defaultProjectName) }

    // Rename target state
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInputName by remember { mutableStateOf("") }
    var searchDbQuery by remember { mutableStateOf("") }
    var confirmResetTarget by remember { mutableStateOf<String?>(null) }

    // Expanded table summaries per database
    val expandedTablesMap = remember { mutableStateMapOf<String, List<TableSummary>>() }
    val expandedStateMap = remember { mutableStateMapOf<String, Boolean>() }
    val loadingStateMap = remember { mutableStateMapOf<String, Boolean>() }

    fun toggleDatabaseTables(dbName: String) {
        val currentExpanded = expandedStateMap[dbName] ?: false
        if (currentExpanded) {
            expandedStateMap[dbName] = false
        } else {
            expandedStateMap[dbName] = true
            if (!expandedTablesMap.containsKey(dbName)) {
                loadingStateMap[dbName] = true
                scope.launch {
                    val summaries = dbManager.getDatabaseTableSummaries(dbName)
                    expandedTablesMap[dbName] = summaries
                    loadingStateMap[dbName] = false
                }
            }
        }
    }

    // Load table summary for current active database immediately
    LaunchedEffect(currentDb) {
        if (!expandedTablesMap.containsKey(currentDb)) {
            val summaries = dbManager.getDatabaseTableSummaries(currentDb)
            expandedTablesMap[currentDb] = summaries
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SqlBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = SqlBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mis Bases de Datos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${databases.size} ${if (databases.size == 1) "base registrada" else "bases registradas"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Tab Switcher: Mis Proyectos vs Nueva BD
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Mis Proyectos",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${databases.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Nueva BD",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (renameTarget != null) {
                    // Inline Rename Project view
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Renombrar proyecto: ${renameTarget}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = renameInputName,
                                onValueChange = { renameInputName = it },
                                label = { Text("Nuevo nombre") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { renameTarget = null }) {
                                    Text("Cancelar")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (renameInputName.isNotBlank()) {
                                            onRenameDb(renameTarget!!, renameInputName)
                                            renameTarget = null
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Guardar")
                                }
                            }
                        }
                    }
                } else if (selectedTab == 0) {
                    // TAB 0: List of Existing Database Projects
                    val displayedDatabases = if (searchDbQuery.isBlank()) databases else databases.filter { it.contains(searchDbQuery, ignoreCase = true) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (databases.size >= 4) {
                            OutlinedTextField(
                                value = searchDbQuery,
                                onValueChange = { searchDbQuery = it },
                                placeholder = { Text("Buscar base de datos...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        if (displayedDatabases.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron bases con \"$searchDbQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayedDatabases) { dbName ->
                                    val isActive = dbName == currentDb
                                    val isExpanded = expandedStateMap[dbName] ?: false
                                    val tables = expandedTablesMap[dbName] ?: emptyList()
                                    val isLoadingTables = loadingStateMap[dbName] ?: false

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!isActive) {
                                                    onSelectDb(dbName)
                                                    onDismiss()
                                                }
                                            }
                                            .testTag("project_item_$dbName"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isActive) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            // Row 1: Icon, Title, Active Badge, Action Buttons
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(9.dp))
                                                            .background(
                                                                if (isActive) SqlBlue.copy(alpha = 0.15f)
                                                                else MaterialTheme.colorScheme.surfaceVariant
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Folder,
                                                            contentDescription = null,
                                                            tint = if (isActive) SqlBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            val cleanDbName = dbName.removeSuffix(".db")
                                                            val dynamicFontSize = when {
                                                                cleanDbName.length > 24 -> 11.5.sp
                                                                cleanDbName.length > 18 -> 12.5.sp
                                                                cleanDbName.length > 13 -> 13.5.sp
                                                                else -> 14.5.sp
                                                            }
                                                            Text(
                                                                text = cleanDbName,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dynamicFontSize),
                                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            if (isActive) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = SqlSuccess.copy(alpha = 0.18f)
                                                                ) {
                                                                    Text(
                                                                        text = "Activo",
                                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                                            fontSize = 9.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        ),
                                                                        color = SqlSuccess,
                                                                        maxLines = 1,
                                                                        softWrap = false,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        val totalRows = tables.sumOf { it.rowCount }
                                                        Text(
                                                            text = if (tables.isNotEmpty()) {
                                                                "${tables.size} tablas • $totalRows registros"
                                                            } else {
                                                                "0 tablas • En blanco"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            renameTarget = dbName
                                                            renameInputName = dbName.removeSuffix(".db")
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Edit,
                                                            contentDescription = "Renombrar",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    if (onResetDb != null) {
                                                        IconButton(
                                                            onClick = { confirmResetTarget = dbName },
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .testTag("reset_db_${dbName}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.RestartAlt,
                                                                contentDescription = "Restablecer a fábrica",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    if (databases.size > 1 && !isActive) {
                                                        IconButton(
                                                            onClick = { onDeleteDb(dbName) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.DeleteOutline,
                                                                contentDescription = "Eliminar",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = { toggleDatabaseTables(dbName) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                                            contentDescription = "Ver tablas",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }

                                            // Quick button to activate if not active
                                            if (!isActive) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FilledTonalButton(
                                                    onClick = {
                                                        onSelectDb(dbName)
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Tocar para activar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                    // Expanded: Tables inside this database
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "Tablas del proyecto:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (isLoadingTables) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                }
                                            } else if (tables.isEmpty()) {
                                                Text(
                                                    text = "Este proyecto no contiene tablas creadas todavía.",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    tables.forEach { tbl ->
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.surface,
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.TableChart,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF2DD4BF),
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = tbl.name,
                                                                    style = TextStyle(
                                                                        fontFamily = FontFamily.Monospace,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "(${tbl.rowCount})",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }
                }
            }
        } else {
                    // TAB 1: Create New Project with Rich Templates
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            label = { Text("Nombre del nuevo proyecto") },
                            placeholder = { Text("ej. mi_tienda, inventario_2026") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Text(
                            text = "Selecciona una plantilla para comenzar:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        templates.forEach { tmpl ->
                            val isSelected = selectedTemplateId == tmpl.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTemplateId = tmpl.id
                                        if (newProjectName.isBlank() || templates.any { it.defaultProjectName == newProjectName }) {
                                            newProjectName = tmpl.defaultProjectName
                                        }
                                    }
                                    .testTag("template_${tmpl.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tmpl.iconColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tmpl.icon,
                                            contentDescription = null,
                                            tint = tmpl.iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = tmpl.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = tmpl.description,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (tmpl.tables.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tablas: ${tmpl.tables.joinToString(", ")}",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (newProjectName.isNotBlank()) {
                                    onCreateDb(newProjectName.trim(), selectedTemplateId)
                                    Toast.makeText(context, "Proyecto '$newProjectName' creado con éxito", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_create_project_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear y Abrir Proyecto", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    if (confirmResetTarget != null) {
        val target = confirmResetTarget!!
        AlertDialog(
            onDismissRequest = { confirmResetTarget = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restablecer Base de Datos")
                }
            },
            text = {
                Text(
                    "¿Deseas restaurar \"$target\" a su estado original de fábrica?\n\nSe restablecerán todas las tablas y registros originales de la plantilla (o se vaciará a 0 tablas si fue creada en blanco). Cualquier cambio manual se descartará.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmResetTarget = null
                        onResetDb?.invoke(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
