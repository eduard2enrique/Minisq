package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatabaseManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MiniSQL Pro", appName)
  }

  @Test
  fun `database manager executes sql correctly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // Execute query
    val result = dbManager.executeSql("SELECT * FROM clientes;")
    assertTrue(result.isSuccess)
    assertTrue(result.columns.contains("nombre"))
    assertTrue(result.rows.isNotEmpty())

    // Create table test
    val createResult = dbManager.executeSql("CREATE TABLE IF NOT EXISTS test_tbl (id INTEGER PRIMARY KEY, item TEXT);")
    assertTrue(createResult.isSuccess)

    // Insert test
    val insertResult = dbManager.executeSql("INSERT INTO test_tbl (item) VALUES ('Prueba');")
    assertTrue(insertResult.isSuccess)

    // Select test
    val selectResult = dbManager.executeSql("SELECT item FROM test_tbl;")
    assertTrue(selectResult.isSuccess)
    assertEquals(1, selectResult.rows.size)
    assertEquals("Prueba", selectResult.rows[0][0])
  }

  @Test
  fun `spreadsheet importer parses csv and imports to sqlite`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)
    val importer = com.example.data.SpreadsheetImporter(context)

    val sampleFile = java.io.File(context.cacheDir, "test_data.csv")
    sampleFile.writeText("codigo,descripcion,precio,stock\n101,Producto A,19.99,50\n102,Producto B,45.00,12\n")

    val uri = android.net.Uri.fromFile(sampleFile)
    val parsedSheet = importer.parseCsv(uri)

    assertEquals(4, parsedSheet.headers.size)
    assertEquals(2, parsedSheet.rows.size)
    assertEquals("codigo", parsedSheet.headers[0])

    val db = dbManager.getActiveDatabase()
    val importResult = importer.importSheetToDatabase(
      db = db,
      sheet = parsedSheet,
      tableName = "inventario_csv",
      headersToUse = parsedSheet.headers,
      typesToUse = parsedSheet.inferredTypes,
      createNewTable = true
    )

    assertTrue(importResult.isSuccess)
    assertEquals(2, importResult.rowsImported)

    val checkResult = dbManager.executeSql("SELECT COUNT(*) FROM inventario_csv;")
    assertTrue(checkResult.isSuccess)
    assertEquals("2", checkResult.rows[0][0])
  }

  @Test
  fun `query repository registers and caps last 20 queries`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.QueryRepository(context)
    repo.clearHistory()

    // Add 25 queries
    for (i in 1..25) {
      repo.addHistoryItem(
        com.example.model.QueryHistoryItem(
          sql = "SELECT $i FROM dual;",
          isSuccess = true,
          elapsedMs = i.toLong(),
          rowsCount = 1
        )
      )
    }

    val last20 = repo.getLast20Queries()
    assertEquals(20, last20.size)
    // Most recent should be first
    assertEquals("SELECT 25 FROM dual;", last20[0].sql)
    assertEquals("SELECT 6 FROM dual;", last20[19].sql)
  }

  @Test
  fun `sql syntax validator detects common errors`() {
    // 1. Unclosed quote
    val unclosedQuoteIssues = com.example.data.SqlSyntaxValidator.validate("SELECT * FROM clientes WHERE nombre = 'Carlos;")
    assertTrue(unclosedQuoteIssues.any { it.message.contains("Comilla simple sin cerrar") })

    // 2. Unbalanced parenthesis
    val unclosedParenIssues = com.example.data.SqlSyntaxValidator.validate("SELECT COUNT(* FROM clientes;")
    assertTrue(unclosedParenIssues.any { it.message.contains("Paréntesis sin cerrar") })

    // 3. Dangling comma
    val commaIssues = com.example.data.SqlSyntaxValidator.validate("SELECT id, nombre, FROM clientes;")
    assertTrue(commaIssues.any { it.message.contains("Coma sobrante") })

    // 4. Invalid clause order (WHERE after ORDER BY)
    val orderIssues = com.example.data.SqlSyntaxValidator.validate("SELECT * FROM clientes ORDER BY id WHERE saldo > 0;")
    assertTrue(orderIssues.any { it.message.contains("WHERE") })

    // 5. Valid query has no error issues
    val cleanIssues = com.example.data.SqlSyntaxValidator.validate("SELECT id, nombre FROM clientes WHERE id > 0 ORDER BY id DESC;")
    assertTrue(cleanIssues.none { it.severity == com.example.data.IssueSeverity.ERROR })

    // 6. Valid multiline JOIN with alias and ON condition does not produce warning
    val joinIssues = com.example.data.SqlSyntaxValidator.validate(
        """
        SELECT p.id, c.nombre, p.fecha, p.total
        FROM pedidos p
        INNER JOIN clientes c ON p.cliente_id = c.id
        ORDER BY p.id DESC;
        """.trimIndent()
    )
    assertTrue(joinIssues.none { it.message.contains("requiere una condición ON") })

    // 7. Invalid JOIN without ON produces warning with line number
    val invalidJoinIssues = com.example.data.SqlSyntaxValidator.validate(
        """
        SELECT *
        FROM pedidos p
        INNER JOIN clientes
        ORDER BY p.id DESC;
        """.trimIndent()
    )
    assertTrue(invalidJoinIssues.any { it.message.contains("requiere una condición ON") })
  }

  @Test
  fun `query execution produces valid data for charting`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // Execute query suitable for charts (ciudad and sum/avg saldo)
    val result = dbManager.executeSql("SELECT ciudad, SUM(saldo) as total_saldo FROM clientes GROUP BY ciudad;")
    assertTrue(result.isSuccess)
    assertTrue(result.isQuery)
    assertEquals(2, result.columns.size)
    assertTrue(result.rows.isNotEmpty())

    // Validate that total_saldo is parsable as numerical values
    val values = result.rows.mapNotNull { row ->
      row.getOrNull(1)?.toDoubleOrNull()
    }
    assertEquals(result.rows.size, values.size)
    assertTrue(values.sum() > 0.0)
  }

  @Test
  fun `sql syntax visual transformation highlights keywords functions strings and comments`() {
    val visualTransformation = com.example.ui.components.SqlSyntaxVisualTransformation(isDark = true)
    val input = androidx.compose.ui.text.AnnotatedString(
      "SELECT id, COUNT(*), 'activo' AS estado FROM clientes WHERE saldo > 100.50; -- comentario SELECT"
    )
    val transformed = visualTransformation.filter(input)
    val spanStyles = transformed.text.spanStyles

    assertTrue("Span styles should not be empty", spanStyles.isNotEmpty())

    // Check that SELECT at the beginning is highlighted with bold font
    val selectSpan = spanStyles.find { it.start == 0 && it.end == 6 }
    org.junit.Assert.assertNotNull("SELECT should have a span style", selectSpan)
    assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, selectSpan?.item?.fontWeight)

    // Check that the comment at the end covers '-- comentario SELECT'
    val commentStart = input.text.indexOf("--")
    val commentSpan = spanStyles.find { it.start == commentStart }
    org.junit.Assert.assertNotNull("Comment should have a span style", commentSpan)
    assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, commentSpan?.item?.fontStyle)
  }

  @Test
  fun `sql script helper correctly splits statements ignoring semicolons in quotes and comments`() {
    val script = """
      -- Inicializar tabla de configuración
      CREATE TABLE IF NOT EXISTS config (
          id INTEGER PRIMARY KEY,
          clave TEXT NOT NULL,
          valor TEXT
      );

      /* Inserciones con punto y coma dentro de literales de cadena */
      INSERT INTO config (clave, valor) VALUES ('url_servidor', 'https://api.empresa.com/v1;debug=true');
      INSERT INTO config (clave, valor) VALUES ('mensaje', 'Hola; bienvenido a MiniSQL');
    """.trimIndent()

    val statements = com.example.data.SqlScriptHelper.splitScriptIntoStatements(script)
    assertEquals(3, statements.size)
    assertTrue(statements[0].startsWith("CREATE TABLE"))
    assertTrue(statements[1].contains("https://api.empresa.com/v1;debug=true"))
    assertTrue(statements[2].contains("Hola; bienvenido a MiniSQL"))

    val tables = com.example.data.SqlScriptHelper.extractCreatedTableNames(script)
    assertEquals(1, tables.size)
    assertEquals("config", tables[0])
  }

  @Test
  fun `sql script helper loads and executes complete sql file with table structures`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // Generate sample SQL file on device storage
    val sampleFile = com.example.data.SqlScriptHelper.createSampleSqlFile(context, "test_empresa_erp.sql")
    assertTrue(sampleFile.exists())
    assertTrue(sampleFile.length() > 0)

    val uri = android.net.Uri.fromFile(sampleFile)
    val readResult = com.example.data.SqlScriptHelper.readSqlFromUri(context, uri)
    assertTrue("Should read file successfully", readResult.isSuccess)

    val loadedFile = readResult.getOrThrow()
    assertEquals("test_empresa_erp.sql", loadedFile.fileName)
    assertTrue("Should detect 4 tables", loadedFile.tableNames.size >= 4)
    assertTrue(loadedFile.tableNames.contains("departamentos"))
    assertTrue(loadedFile.tableNames.contains("empleados"))
    assertTrue(loadedFile.tableNames.contains("proyectos"))
    assertTrue(loadedFile.tableNames.contains("asignaciones"))
    assertTrue("Should detect indexes", loadedFile.indexNames.isNotEmpty())
    assertTrue("Should have multiple inserts", loadedFile.insertCount >= 4)

    // Execute script into active database
    val execResult = dbManager.executeSql(loadedFile.rawSql)
    assertTrue("Execution should succeed: ${execResult.message}", execResult.isSuccess)
    assertTrue(execResult.message.contains("departamentos"))

    // Verify created tables in database
    val dbTables = dbManager.getTables().map { it.name }
    assertTrue(dbTables.contains("departamentos"))
    assertTrue(dbTables.contains("empleados"))
    assertTrue(dbTables.contains("proyectos"))
    assertTrue(dbTables.contains("asignaciones"))

    // Verify relations and data
    val queryResult = dbManager.executeSql(
      "SELECT e.nombre, d.nombre AS depto FROM empleados e JOIN departamentos d ON e.departamento_id = d.id;"
    )
    assertTrue(queryResult.isSuccess)
    assertTrue(queryResult.rows.isNotEmpty())
  }

  @Test
  fun `sql autocompleter provides schema aware keyword suggestions for select from where`() {
    val schema = listOf(
      com.example.model.TableSchemaDetail(
        tableName = "clientes",
        columns = listOf(
          com.example.model.ColumnInfo(0, "id", "INTEGER", true, null, true),
          com.example.model.ColumnInfo(1, "nombre", "TEXT", true, null, false),
          com.example.model.ColumnInfo(2, "email", "TEXT", false, null, false),
          com.example.model.ColumnInfo(3, "ciudad", "TEXT", false, null, false),
          com.example.model.ColumnInfo(4, "saldo", "REAL", false, "0.0", false)
        )
      ),
      com.example.model.TableSchemaDetail(
        tableName = "pedidos",
        columns = listOf(
          com.example.model.ColumnInfo(0, "id", "INTEGER", true, null, true),
          com.example.model.ColumnInfo(1, "cliente_id", "INTEGER", true, null, false),
          com.example.model.ColumnInfo(2, "total", "REAL", false, null, false)
        )
      )
    )
    val tables = schema.map { it.tableName }
    val columns = schema.flatMap { it.columns.map { c -> c.name } }.distinct()

    // 1. Initial / blank text suggests SELECT, INSERT INTO, and schema snippet
    val initialSuggestions = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "",
      cursorIndex = 0,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(initialSuggestions.any { it.insertText.startsWith("SELECT") })

    // 2. Typing 'S' or 'SEL' suggests SELECT and schema snippets
    val prefixS = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "SEL",
      cursorIndex = 3,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(prefixS.any { it.displayText == "SELECT" })

    // 3. Right after "SELECT " suggests schema columns and FROM
    val selectContext = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "SELECT ",
      cursorIndex = 7,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(selectContext.any { it.insertText.contains("FROM") })
    assertTrue(selectContext.any { it.displayText == "id" || it.displayText == "nombre" })

    // 4. Right after "SELECT * FROM " suggests schema tables
    val fromContext = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "SELECT * FROM ",
      cursorIndex = 14,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(fromContext.any { it.displayText == "clientes" })
    assertTrue(fromContext.any { it.displayText == "pedidos" })

    // 5. Typing 'w' or after "FROM clientes " suggests WHERE
    val afterTableContext = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "SELECT * FROM clientes ",
      cursorIndex = 23,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(afterTableContext.any { it.displayText == "WHERE" })

    // 6. Right after "WHERE " suggests columns of 'clientes'
    val whereContext = com.example.data.SqlAutoCompleter.getSuggestions(
      fullText = "SELECT * FROM clientes WHERE ",
      cursorIndex = 29,
      availableTables = tables,
      availableColumns = columns,
      tableSchemas = schema
    )
    assertTrue(whereContext.any { it.displayText == "saldo" || it.displayText == "ciudad" })
  }

  @Test
  fun `sql syntax visual transformation highlights schema tables and schema columns`() {
    val schemaTables = setOf("clientes", "pedidos")
    val schemaColumns = setOf("id", "nombre", "saldo")
    val visualTransformation = com.example.ui.components.SqlSyntaxVisualTransformation(
      isDark = true,
      schemaTables = schemaTables,
      schemaColumns = schemaColumns
    )
    val input = androidx.compose.ui.text.AnnotatedString(
      "SELECT id, nombre FROM clientes WHERE saldo > 50;"
    )
    val transformed = visualTransformation.filter(input)
    val spanStyles = transformed.text.spanStyles

    // Find style covering 'clientes' (index 23 to 31)
    val clientesStart = input.text.indexOf("clientes")
    val clientesSpan = spanStyles.find { it.start == clientesStart && it.end == clientesStart + "clientes".length }
    org.junit.Assert.assertNotNull("Table 'clientes' should be highlighted", clientesSpan)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF2DD4BF), clientesSpan?.item?.color)

    // Find style covering 'saldo'
    val saldoStart = input.text.indexOf("saldo")
    val saldoSpan = spanStyles.find { it.start == saldoStart && it.end == saldoStart + "saldo".length }
    org.junit.Assert.assertNotNull("Column 'saldo' should be highlighted", saldoSpan)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF38BDF8), saldoSpan?.item?.color)
  }

  @Test
  fun `database manager creates projects template and lists table summaries`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // 1. Create project with 'projects' template
    val created = dbManager.createNewDatabase("test_proyectos_hub", "projects")
    assertTrue(created)

    // 2. Query summaries
    val tables = dbManager.getDatabaseTableSummaries("test_proyectos_hub")
    assertTrue(tables.isNotEmpty())
    val tableNames = tables.map { it.name }
    assertTrue(tableNames.contains("proyectos"))
    assertTrue(tableNames.contains("miembros"))
    assertTrue(tableNames.contains("tareas"))

    // 3. Insert row test in table
    val inserted = dbManager.insertTableRow(
      "proyectos",
      mapOf(
        "nombre" to "MiniSQL 2.0 Pro",
        "descripcion" to "Proyectos hub y tabla pro",
        "presupuesto" to "5000.0",
        "estado" to "Activo"
      )
    )
    assertTrue(inserted)

    val res = dbManager.executeSql("SELECT * FROM proyectos WHERE nombre = 'MiniSQL 2.0 Pro';")
    assertTrue(res.isSuccess)
    assertEquals(1, res.rows.size)
  }

  @Test
  fun `spreadsheet importer replaces existing table when createNewTable is true`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)
    val importer = com.example.data.SpreadsheetImporter(context)

    // 1. Manually create an 'estudiantes' table with old schema (id, nombre, telefono)
    val createRes = dbManager.executeSql(
      "CREATE TABLE estudiantes (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, telefono TEXT);"
    )
    assertTrue(createRes.isSuccess)

    // 2. CSV has different columns: matricula, carrera, promedio
    val sampleFile = java.io.File(context.cacheDir, "estudiantes_nuevo.csv")
    sampleFile.writeText("matricula;carrera;promedio\nA001;Ingeniería de Sistemas;18,5\nA002;Administración;16,2\n")

    val uri = android.net.Uri.fromFile(sampleFile)
    val parsedSheet = importer.parseCsv(uri)

    // Verify semicolon and header detection
    assertEquals(3, parsedSheet.headers.size)
    assertEquals("matricula", parsedSheet.headers[0])
    assertEquals("carrera", parsedSheet.headers[1])
    assertEquals("promedio", parsedSheet.headers[2])

    val db = dbManager.getActiveDatabase()
    // 3. Import with createNewTable = true into existing 'estudiantes'
    val result = importer.importSheetToDatabase(
      db = db,
      sheet = parsedSheet,
      tableName = "estudiantes",
      headersToUse = parsedSheet.headers,
      typesToUse = parsedSheet.inferredTypes,
      createNewTable = true
    )

    // Should succeed and NOT throw "table estudiantes has no column named matricula"
    assertTrue("Import should succeed: ${result.message}", result.isSuccess)
    assertEquals(2, result.rowsImported)

    // Verify new table data and columns
    val selectRes = dbManager.executeSql("SELECT matricula, carrera, promedio FROM estudiantes;")
    assertTrue(selectRes.isSuccess)
    assertEquals(2, selectRes.rows.size)
    assertEquals("A001", selectRes.rows[0][0])
    assertEquals("18.5", selectRes.rows[0][2]) // European decimal comma converted to standard decimal point
  }

  @Test
  fun `spreadsheet importer preserves spanish accents and handles csv with existing id column`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)
    val importer = com.example.data.SpreadsheetImporter(context)

    // CSV with Spanish accents in headers and explicit 'id' column
    val sampleFile = java.io.File(context.cacheDir, "estudiantes_acentos.csv")
    sampleFile.writeText("id,cédula,año_ingreso,categoría\n1,V-12345678,2022,Regular\n2,V-87654321,2023,Destacado\n")

    val uri = android.net.Uri.fromFile(sampleFile)
    val parsedSheet = importer.parseCsv(uri)

    assertTrue("Header should preserve accents", parsedSheet.headers.contains("cédula"))
    assertTrue("Header should preserve ñ", parsedSheet.headers.contains("año_ingreso"))
    assertTrue("Header should preserve í", parsedSheet.headers.contains("categoría"))

    val db = dbManager.getActiveDatabase()
    val result = importer.importSheetToDatabase(
      db = db,
      sheet = parsedSheet,
      tableName = "estudiantes_acentos",
      headersToUse = parsedSheet.headers,
      typesToUse = parsedSheet.inferredTypes,
      createNewTable = true
    )

    assertTrue("Import should succeed without duplicate id column error: ${result.message}", result.isSuccess)
    assertEquals(2, result.rowsImported)

    val selectRes = dbManager.executeSql("SELECT \"cédula\", \"categoría\" FROM estudiantes_acentos WHERE id = 1;")
    assertTrue(selectRes.isSuccess)
    assertEquals("V-12345678", selectRes.rows[0][0])
  }

  @Test
  fun `query report helper generates structured report with statistical analysis`() {
    val sampleResult = com.example.model.SqlExecutionResult(
      isSuccess = true,
      message = "Ejecutado con éxito en 4 ms (3 filas devueltas)",
      columns = listOf("id", "nombre", "salario"),
      rows = listOf(
        listOf("1", "Ana Martínez", "2500.50"),
        listOf("2", "Carlos Gómez", "1800.00"),
        listOf("3", "David Silva", "3200.75")
      ),
      affectedRows = 0,
      elapsedMs = 4,
      querySql = "SELECT id, nombre, salario FROM empleados WHERE activo = 1;",
      isQuery = true
    )

    val reportData = com.example.data.QueryReportHelper.analyzeAndBuildReport(sampleResult, "empresa_erp")

    assertEquals("empresa_erp", reportData.dbName)
    assertEquals(3, reportData.totalRows)
    assertEquals(3, reportData.totalColumns)
    assertEquals(4, reportData.elapsedMs)

    // Check statistical analysis
    val salarioStat = reportData.columnStats.find { it.name == "salario" }
    org.junit.Assert.assertNotNull(salarioStat)
    assertTrue(salarioStat!!.isNumeric)
    assertEquals(1800.00, salarioStat.min!!, 0.001)
    assertEquals(3200.75, salarioStat.max!!, 0.001)
    assertEquals(7501.25, salarioStat.sum!!, 0.001)

    val nombreStat = reportData.columnStats.find { it.name == "nombre" }
    org.junit.Assert.assertNotNull(nombreStat)
    org.junit.Assert.assertFalse(nombreStat!!.isNumeric)
    assertEquals(3, nombreStat.distinctCount)

    // Check formatted text contains sections
    val text = reportData.formattedTextReport
    assertTrue(text.contains("INFORME DE CONSULTA SQL"))
    assertTrue(text.contains("empresa_erp"))
    assertTrue(text.contains("SELECT id, nombre, salario FROM empleados"))
    assertTrue(text.contains("RESUMEN ESTADÍSTICO DE COLUMNAS"))
    assertTrue(text.contains("Ana Martínez"))
  }

  @Test
  fun `creating a blank database starts with exactly 0 tables and allows creating tables`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // Initially sample DB has tables
    val initialTables = dbManager.getTables()
    assertTrue(initialTables.isNotEmpty())

    // Create a new blank database
    val created = dbManager.createNewDatabase("base_prueba_blanco", "empty")
    assertTrue(created)
    assertEquals("base_prueba_blanco.db", dbManager.getCurrentDbName())

    // Must have exactly 0 tables
    val blankTables = dbManager.getTables()
    assertEquals(0, blankTables.size)

    // Create first table in blank database
    val createRes = dbManager.executeSql("CREATE TABLE articulos (id INTEGER PRIMARY KEY, titulo TEXT);")
    assertTrue(createRes.isSuccess)

    // Now has 1 table
    val updatedTables = dbManager.getTables()
    assertEquals(1, updatedTables.size)
    assertEquals("articulos", updatedTables[0].name)
  }

  @Test
  fun `sql error analyzer explains unique constraint failure and suggests insert or ignore`() {
    val err = "UNIQUE constraint failed: clientes.email"
    val query = "INSERT INTO clientes (nombre, email) VALUES ('Ana', 'ana@ejemplo.com');"
    val explanation = com.example.ui.components.SqlErrorAnalyzer.analyze(err, query)

    assertTrue(explanation.categoryBadge.contains("UNIQUE"))
    assertTrue(explanation.reason.contains("clientes"))
    assertTrue(explanation.reason.contains("email"))
    assertEquals("Usar 'INSERT OR IGNORE'", explanation.quickFixLabel)
    assertEquals(
      "INSERT OR IGNORE INTO clientes (nombre, email) VALUES ('Ana', 'ana@ejemplo.com');",
      explanation.suggestedFixedQuery
    )
  }

  @Test
  fun `sql error analyzer explains table already exists and suggests if not exists`() {
    val err = "table \"proveedores\" already exists"
    val query = "CREATE TABLE proveedores (id INTEGER PRIMARY KEY);"
    val explanation = com.example.ui.components.SqlErrorAnalyzer.analyze(err, query)

    assertTrue(explanation.categoryBadge.contains("TABLA YA EXISTE"))
    assertTrue(explanation.reason.contains("proveedores"))
    assertEquals("Añadir 'IF NOT EXISTS'", explanation.quickFixLabel)
    assertEquals(
      "CREATE TABLE IF NOT EXISTS proveedores (id INTEGER PRIMARY KEY);",
      explanation.suggestedFixedQuery
    )
  }

  @Test
  fun `sql error analyzer explains foreign key and no such table errors`() {
    val fkErr = "FOREIGN KEY constraint failed"
    val fkExplanation = com.example.ui.components.SqlErrorAnalyzer.analyze(fkErr, "INSERT INTO pedidos ...")
    assertTrue(fkExplanation.categoryBadge.contains("FOREIGN KEY"))

    val tableErr = "no such table: inventario_fantasma"
    val tableExplanation = com.example.ui.components.SqlErrorAnalyzer.analyze(tableErr, "SELECT * FROM inventario_fantasma;")
    assertTrue(tableExplanation.categoryBadge.contains("TABLA NO ENCONTRADA"))
    assertTrue(tableExplanation.reason.contains("inventario_fantasma"))
  }

  @Test
  fun `database manager resetDatabase restores original state`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbManager = DatabaseManager(context)

    // 1. In default sample database (mi_base_de_datos.db), drop a table or add garbage
    dbManager.switchDatabase("mi_base_de_datos.db")
    dbManager.executeSql("DROP TABLE IF EXISTS clientes;")
    val tablesAfterDrop = dbManager.getTables()
    assertTrue(tablesAfterDrop.none { it.name == "clientes" })

    // Reset default database
    val resetOk = dbManager.resetDatabase("mi_base_de_datos.db")
    assertTrue(resetOk)

    // Now 'clientes' must be back!
    val restoredTables = dbManager.getTables()
    assertTrue(restoredTables.any { it.name == "clientes" })
    val clientRows = dbManager.executeSql("SELECT * FROM clientes;")
    assertTrue(clientRows.isSuccess)
    assertTrue(clientRows.rows.isNotEmpty())

    // 2. In blank database: after adding tables, reset clears back to 0 tables
    dbManager.createNewDatabase("temp_blanco", "empty")
    dbManager.executeSql("CREATE TABLE notas (id INTEGER, texto TEXT);")
    assertEquals(1, dbManager.getTables().size)

    dbManager.resetDatabase("temp_blanco")
    assertEquals(0, dbManager.getTables().size)
  }

  @Test
  fun `idempotent create table and insert or ignore and replace into succeed repeatedly`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbManager = DatabaseManager(context)
      dbManager.createNewDatabase("test_idempotent", "empty")
      dbManager.switchDatabase("test_idempotent")

      val ddl = """
        CREATE TABLE IF NOT EXISTS cursos_test (
          id INTEGER PRIMARY KEY,
          titulo TEXT NOT NULL,
          codigo TEXT UNIQUE
        );
      """.trimIndent()

      // Run DDL first time
      val res1 = dbManager.executeSql(ddl)
      assertTrue("DDL first run should succeed: ${res1.message}", res1.isSuccess)

      // Run DDL second time (should NOT fail because IF NOT EXISTS)
      val res2 = dbManager.executeSql(ddl)
      assertTrue("DDL repeat run should succeed: ${res2.message}", res2.isSuccess)

      // Run INSERT OR IGNORE
      val insertIgnore = "INSERT OR IGNORE INTO cursos_test (id, titulo, codigo) VALUES (1, 'SQL 101', 'CS-101');"
      val insRes1 = dbManager.executeSql(insertIgnore)
      assertTrue("First insert should succeed: ${insRes1.message}", insRes1.isSuccess)

      // Run INSERT OR IGNORE a second time (should NOT fail because OR IGNORE)
      val insRes2 = dbManager.executeSql(insertIgnore)
      assertTrue("Repeat insert or ignore should succeed without error: ${insRes2.message}", insRes2.isSuccess)

      // Verify row count is still 1
      val queryRows = dbManager.executeSql("SELECT * FROM cursos_test WHERE id = 1;")
      assertEquals(1, queryRows.rows.size)
      assertEquals("SQL 101", queryRows.rows[0][1])

      // Run REPLACE INTO (should update the existing row without duplicate error)
      val replaceSql = "REPLACE INTO cursos_test (id, titulo, codigo) VALUES (1, 'SQL 101 Avanzado', 'CS-101');"
      val repRes = dbManager.executeSql(replaceSql)
      assertTrue("Replace into should succeed: ${repRes.message}", repRes.isSuccess)

      val updatedRows = dbManager.executeSql("SELECT * FROM cursos_test WHERE id = 1;")
      assertEquals(1, updatedRows.rows.size)
      assertEquals("SQL 101 Avanzado", updatedRows.rows[0][1])

      // Clean up
      dbManager.deleteDatabase("test_idempotent")
    }
  }

  @Test
  fun `sql script helper counts insert or ignore and replace into statements`() {
    val script = """
      CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY, nombre TEXT);
      INSERT INTO items VALUES (1, 'A');
      INSERT OR IGNORE INTO items VALUES (2, 'B');
      INSERT OR REPLACE INTO items VALUES (3, 'C');
      REPLACE INTO items VALUES (4, 'D');
    """.trimIndent()

    val count = com.example.data.SqlScriptHelper.countInserts(script)
    assertEquals(4, count)
  }

  @Test
  fun `query repository examples contain safe idempotent insertions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.QueryRepository(context)
    val examples = repo.getEducationalExamples()

    val insertIgnoreEx = examples.find { it.title.contains("INSERT OR IGNORE") }
    assertNotNull(insertIgnoreEx)
    assertTrue(insertIgnoreEx!!.sql.contains("INSERT OR IGNORE INTO"))

    val replaceEx = examples.find { it.title.contains("REPLACE INTO") }
    assertNotNull(replaceEx)
    assertTrue(replaceEx!!.sql.contains("REPLACE INTO"))

    val createTableEx = examples.find { it.title.contains("IF NOT EXISTS") }
    assertNotNull(createTableEx)
    assertTrue(createTableEx!!.sql.contains("CREATE TABLE IF NOT EXISTS"))
  }
}


