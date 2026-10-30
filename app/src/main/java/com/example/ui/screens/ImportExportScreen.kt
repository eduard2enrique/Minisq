package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.data.ParsedSheet
import com.example.data.SpreadsheetImporter
import com.example.data.SqlScriptHelper
import com.example.ui.components.SpreadsheetImportDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImportExportScreen(
    dbManager: DatabaseManager,
    onDatabaseChanged: () -> Unit,
    onNavigateToTables: () -> Unit = {},
    onNavigateToQuery: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateToTables()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spreadsheetImporter = remember { SpreadsheetImporter(context) }

    var isProcessing by remember { mutableStateOf(false) }

    var sqlScriptInput by remember { mutableStateOf("") }
    var scriptOutputMessage by remember { mutableStateOf<String?>(null) }

    // State for CSV / Excel dialog
    var pendingSheets by remember { mutableStateOf<List<ParsedSheet>?>(null) }
    var isImportingSpreadsheet by remember { mutableStateOf(false) }
    var existingTableNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // Launcher for importing CSV / Excel files
    val importSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val tables = dbManager.getTables().map { it.name }
                existingTableNames = tables
                val parseResult = spreadsheetImporter.parseSpreadsheet(uri)
                isProcessing = false
                if (parseResult.isSuccess) {
                    pendingSheets = parseResult.getOrNull()
                } else {
                    Toast.makeText(
                        context,
                        "Error al leer archivo: ${parseResult.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Helper to generate and parse sample CSV for instant testing
    fun loadSampleCsv() {
        isProcessing = true
        scope.launch {
            try {
                val sampleFile = File(context.cacheDir, "ventas_2026_demo.csv")
                FileOutputStream(sampleFile).use { fos ->
                    val csvContent = """
                        id,producto,categoria,unidades_vendidas,precio_unitario,fecha_venta,ciudad
                        101,Monitor UltraWide 34,Hardware,12,489.99,2026-10-15,Madrid
                        102,Teclado Mecánico RGB,Accesorios,45,85.50,2026-10-16,Barcelona
                        103,Licencia IDE DevPro,Software,28,199.00,2026-10-17,Valencia
                        104,Ratón Ergonómico Pro,Accesorios,60,34.90,2026-10-18,Sevilla
                        105,Disco SSD NVMe 2TB,Hardware,18,145.00,2026-10-19,Bilbao
                        106,Auriculares Studio 7,Audio,22,120.00,2026-10-20,Málaga
                    """.trimIndent()
                    fos.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                val tables = dbManager.getTables().map { it.name }
                existingTableNames = tables
                val uri = Uri.fromFile(sampleFile)
                val sheet = spreadsheetImporter.parseCsv(uri)
                pendingSheets = listOf(sheet)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // Launcher for importing .db file
    val importDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val res = dbManager.importDatabaseFromUri(uri)
                isProcessing = false
                if (res.isSuccess) {
                    Toast.makeText(context, "Base de datos '${res.getOrNull()}' importada con éxito.", Toast.LENGTH_LONG).show()
                    onDatabaseChanged()
                    onNavigateToTables()
                } else {
                    Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Launcher for exporting .db file
    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    val success = dbManager.exportDatabaseToStream(stream)
                    if (success) {
                        Toast.makeText(context, "Base de datos exportada correctamente.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error al exportar base de datos.", Toast.LENGTH_SHORT).show()
                    }
                }
                isProcessing = false
            }
        }
    }

    // Launcher for exporting SQL DUMP file
    val exportSqlDumpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/sql")
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val dump = dbManager.exportDatabaseSqlDump()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(dump.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                isProcessing = false
                Toast.makeText(context, "Script SQL exportado exitosamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for importing .sql script files from phone storage
    val importSqlScriptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val res = SqlScriptHelper.readSqlFromUri(context, uri)
                isProcessing = false
                res.onSuccess { loadedFile ->
                    sqlScriptInput = loadedFile.rawSql
                    val tableList = if (loadedFile.tableNames.isNotEmpty()) {
                        " (Tablas detectadas: ${loadedFile.tableNames.joinToString(", ")})"
                    } else ""
                    scriptOutputMessage = "Archivo '${loadedFile.fileName}' cargado (${loadedFile.statementCount} sentencias, ${loadedFile.lineCount} líneas)$tableList."
                    Toast.makeText(context, "Archivo SQL cargado con éxito", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Error al leer archivo .sql: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Helper to generate and load rich sample SQL file with table structures
    fun loadSampleSqlScript() {
        isProcessing = true
        scope.launch {
            try {
                val sampleFile = SqlScriptHelper.createSampleSqlFile(context)
                val loadedFile = SqlScriptHelper.parseSqlContent(sampleFile.name, sampleFile.readText())
                sqlScriptInput = loadedFile.rawSql
                val tableList = if (loadedFile.tableNames.isNotEmpty()) {
                    " (Tablas: ${loadedFile.tableNames.joinToString(", ")})"
                } else ""
                scriptOutputMessage = "Demo cargada: '${loadedFile.fileName}' con ${loadedFile.statementCount} sentencias$tableList."
                Toast.makeText(context, "Estructuras SQL de prueba cargadas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header with Active DB and Quick Close / Dismiss Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Importar y Exportar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Base activa: ${dbManager.getCurrentDbName().removeSuffix(".db")}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onNavigateToTables,
                modifier = Modifier.size(36.dp).testTag("close_import_screen_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cerrar menú importar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: CSV and Excel (.xlsx) Import Card (NEW)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Importar CSV o Excel (.xlsx)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Carga tablas directamente desde archivos CSV, TSV o libros de Excel (.xlsx) con detección automática de tipos (INTEGER, REAL, TEXT):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { importSpreadsheetLauncher.launch("*/*") },
                        modifier = Modifier.weight(1.3f).testTag("import_csv_excel_btn")
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Elegir CSV / Excel")
                    }

                    FilledTonalButton(
                        onClick = { loadSampleCsv() },
                        modifier = Modifier.weight(1f).testTag("load_sample_csv_btn")
                    ) {
                        Text("Demo CSV", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Section 2: SQLite .db File Operations
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Archivos SQLite (.db / .sqlite)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Importa bases de datos desde el almacenamiento local o exporta la base de datos activa '${dbManager.getCurrentDbName()}':",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { importDbLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f).testTag("import_db_file_btn")
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar .db")
                    }

                    FilledTonalButton(
                        onClick = { exportDbLauncher.launch(dbManager.getCurrentDbName()) },
                        modifier = Modifier.weight(1f).testTag("export_db_file_btn")
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar .db")
                    }
                }
            }
        }

        // Section 3: SQL Script Dump
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Generar Volcado SQL (DUMP)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Genera un script .sql completo con sentencias CREATE TABLE e INSERT INTO con todos los datos:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val dump = dbManager.exportDatabaseSqlDump()
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("SQL Dump", dump))
                                Toast.makeText(context, "Volcado SQL copiado al portapapeles.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar SQL")
                    }

                    FilledTonalButton(
                        onClick = {
                            val dumpName = dbManager.getCurrentDbName().removeSuffix(".db") + "_dump.sql"
                            exportSqlDumpLauncher.launch(dumpName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar .sql")
                    }
                }
            }
        }

        // Section 4: Execute SQL Script
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Ejecutar Script SQL por Lotes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Carga archivos .sql desde el teléfono con estructuras CREATE TABLE e inserciones, o pega sentencias separadas por punto y coma (;):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { importSqlScriptLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1.3f).testTag("import_sql_file_btn")
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cargar .sql del teléfono")
                    }

                    FilledTonalButton(
                        onClick = { loadSampleSqlScript() },
                        modifier = Modifier.weight(1f).testTag("load_sample_sql_btn")
                    ) {
                        Text("Demo Estructuras", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = sqlScriptInput,
                    onValueChange = { sqlScriptInput = it },
                    placeholder = {
                        Text(
                            "CREATE TABLE IF NOT EXISTS notas (id INTEGER PRIMARY KEY, texto TEXT);\nINSERT OR IGNORE INTO notas (id, texto) VALUES (1, 'Nota 1');",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (sqlScriptInput.isNotBlank()) {
                            isProcessing = true
                            scope.launch {
                                val res = dbManager.executeSql(sqlScriptInput)
                                isProcessing = false
                                scriptOutputMessage = res.message
                                if (res.isSuccess) {
                                    onDatabaseChanged()
                                }
                            }
                        }
                    },
                    enabled = sqlScriptInput.isNotBlank() && !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ejecutar Script")
                    }
                }

                val outputMsg = scriptOutputMessage
                if (outputMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = outputMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = onNavigateToTables,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Ver Tablas", style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Preset Educational Databases
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Bases de Datos Didácticas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Al seleccionar una base de datos didáctica, se creará y se abrirá automáticamente en la sección Tablas para comenzar a explorar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                fun loadEducationalDb(templateId: String, templateTitle: String) {
                    isProcessing = true
                    scope.launch {
                        val name = "${templateId}_${System.currentTimeMillis() % 10000}.db"
                        val ok = dbManager.createNewDatabase(name, templateId)
                        isProcessing = false
                        if (ok) {
                            onDatabaseChanged()
                            Toast.makeText(context, "Base didáctica '$templateTitle' activada con éxito.", Toast.LENGTH_SHORT).show()
                            // Close import view and navigate straight to tables!
                            onNavigateToTables()
                        } else {
                            Toast.makeText(context, "No se pudo crear la base de datos.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Educational Database 1: Universidad & Cursos
                OutlinedButton(
                    onClick = { loadEducationalDb("university", "Universidad & Cursos") },
                    modifier = Modifier.fillMaxWidth().testTag("didactic_db_university")
                ) {
                    Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Universidad (Estudiantes, Profesores, Cursos, Notas)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Educational Database 2: Comercio Electrónico
                OutlinedButton(
                    onClick = { loadEducationalDb("ecommerce", "Comercio Electrónico") },
                    modifier = Modifier.fillMaxWidth().testTag("didactic_db_ecommerce")
                ) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Comercio Electrónico (Usuarios, Categorías, Artículos)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Educational Database 3: Tienda & Clientes (Relacional)
                OutlinedButton(
                    onClick = { loadEducationalDb("sample", "Tienda & Clientes") },
                    modifier = Modifier.fillMaxWidth().testTag("didactic_db_sample")
                ) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tienda & Pedidos (Clientes, Productos, Facturación)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Educational Database 4: Clínica & Citas Médicas
                OutlinedButton(
                    onClick = { loadEducationalDb("clinic", "Clínica & Citas Médicas") },
                    modifier = Modifier.fillMaxWidth().testTag("didactic_db_clinic")
                ) {
                    Icon(Icons.Outlined.LocalHospital, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clínica Médica (Médicos, Pacientes, Citas)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Educational Database 5: Gestión de Proyectos
                OutlinedButton(
                    onClick = { loadEducationalDb("projects", "Gestión de Proyectos") },
                    modifier = Modifier.fillMaxWidth().testTag("didactic_db_projects")
                ) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gestión de Proyectos (Proyectos, Miembros, Tareas)")
                }
            }
        }
    }

    // Modal Spreadsheet Import Preview & Config Dialog
    pendingSheets?.let { sheets ->
        SpreadsheetImportDialog(
            sheets = sheets,
            existingTables = existingTableNames,
            isImporting = isImportingSpreadsheet,
            onConfirmImport = { sheet, tableName, headers, types, createNew ->
                isImportingSpreadsheet = true
                scope.launch {
                    val db = dbManager.getActiveDatabase()
                    val result = spreadsheetImporter.importSheetToDatabase(
                        db = db,
                        sheet = sheet,
                        tableName = tableName,
                        headersToUse = headers,
                        typesToUse = types,
                        createNewTable = createNew
                    )
                    isImportingSpreadsheet = false
                    pendingSheets = null
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    if (result.isSuccess) {
                        onDatabaseChanged()
                        // Automatically close import flow and navigate to tables to view imported table!
                        onNavigateToTables()
                    }
                }
            },
            onDismiss = { pendingSheets = null }
        )
    }
}
