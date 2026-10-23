package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatabaseManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    assertEquals("MiniSQL", appName)
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
}
