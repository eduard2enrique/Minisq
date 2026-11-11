package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatabaseManager
import com.example.data.QueryRepository
import com.example.data.SqlAutoCompleter
import com.example.data.SuggestionCategory
import com.example.model.ColumnInfo
import com.example.model.TableSchemaDetail
import com.example.ui.MainScreen
import com.example.ui.components.SqlEditor
import com.example.ui.screens.QueryScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqlEditorUiAndFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSchemas = listOf(
        TableSchemaDetail(
            tableName = "clientes",
            columns = listOf(
                ColumnInfo(0, "id", "INTEGER", true, null, true),
                ColumnInfo(1, "nombre", "TEXT", true, null, false),
                ColumnInfo(2, "ciudad", "TEXT", false, null, false),
                ColumnInfo(3, "saldo", "REAL", false, "0.0", false)
            )
        ),
        TableSchemaDetail(
            tableName = "pedidos",
            columns = listOf(
                ColumnInfo(0, "id", "INTEGER", true, null, true),
                ColumnInfo(1, "cliente_id", "INTEGER", true, null, false),
                ColumnInfo(2, "total", "REAL", false, null, false)
            )
        )
    )

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
    fun `version 1_4 is synced across BuildConfig PackageInfo and MainScreen UI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("1.4", BuildConfig.VERSION_NAME)
        assertEquals(6, BuildConfig.VERSION_CODE)

        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName, 0)
        assertEquals("1.4", pkgInfo.versionName)

        // Verify MainActivity sets softInputMode adjustResize to protect FAB from IME
        val activityInfo = pm.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        val adjustMask = activityInfo.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjustMask)

        val dbManager = DatabaseManager(context)
        val queryRepo = QueryRepository(context)
        try {
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = true) {
                    MainScreen(
                        dbManager = dbManager,
                        queryRepo = queryRepo,
                        isDarkTheme = true,
                        onToggleTheme = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("app_brand_logo_btn")
                .assertIsDisplayed()
                .assertTextContains("v1.4")
            composeTestRule.onNodeWithTag("app_version_badge", useUnmergedTree = true)
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("v1.4", useUnmergedTree = true)
                .assertIsDisplayed()
        } finally {
            dbManager.closeDatabase()
        }
    }

    @Test
    fun `sql autocompleter supports join on group by order by and ignores unclosed string literals`() {
        val tables = sampleSchemas.map { it.tableName }
        val cols = sampleSchemas.flatMap { it.columns.map { c -> c.name } }.distinct()

        // 1. After JOIN table before ON -> ON is top priority
        val afterJoinTable = SqlAutoCompleter.getSuggestions(
            fullText = "SELECT * FROM pedidos p JOIN clientes c ",
            cursorIndex = 40,
            availableTables = tables,
            availableColumns = cols,
            tableSchemas = sampleSchemas
        )
        assertEquals("ON", afterJoinTable.first().displayText)

        // 2. Inside JOIN ON after '=' -> suggests referenced columns
        val onEquals = SqlAutoCompleter.getSuggestions(
            fullText = "SELECT * FROM pedidos p JOIN clientes c ON p.cliente_id = ",
            cursorIndex = 58,
            availableTables = tables,
            availableColumns = cols,
            tableSchemas = sampleSchemas
        )
        assertTrue(onEquals.any { it.category == SuggestionCategory.COLUMN && it.tableTag == "clientes" })

        // 3. After column in ORDER BY -> DESC / ASC / LIMIT
        val orderAfterCol = SqlAutoCompleter.getSuggestions(
            fullText = "SELECT * FROM clientes ORDER BY saldo ",
            cursorIndex = 38,
            availableTables = tables,
            availableColumns = cols,
            tableSchemas = sampleSchemas
        )
        assertEquals("DESC", orderAfterCol.first().displayText)
        assertTrue(orderAfterCol.any { it.displayText == "ASC" })

        // 4. After column in GROUP BY -> HAVING / ORDER BY / LIMIT
        val groupAfterCol = SqlAutoCompleter.getSuggestions(
            fullText = "SELECT ciudad, COUNT(*) FROM clientes GROUP BY ciudad ",
            cursorIndex = 54,
            availableTables = tables,
            availableColumns = cols,
            tableSchemas = sampleSchemas
        )
        assertTrue(groupAfterCol.any { it.displayText == "HAVING" })
        assertTrue(groupAfterCol.any { it.displayText == "ORDER BY" })

        // 5. Inside unclosed string literal -> no intrusive keyword/column suggestions
        val insideQuote = SqlAutoCompleter.getSuggestions(
            fullText = "SELECT * FROM clientes WHERE nombre = 'Carl",
            cursorIndex = 43,
            availableTables = tables,
            availableColumns = cols,
            tableSchemas = sampleSchemas
        )
        assertTrue(insideQuote.isEmpty())
    }

    @Test
    fun `sql editor interactive select flow inserts field transitions to from table where and executes via toolbar`() {
        val tables = sampleSchemas.map { it.tableName }
        val cols = sampleSchemas.flatMap { it.columns.map { c -> c.name } }.distinct()
        var currentText = ""
        var executeClicks = 0

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                var fieldState by remember {
                    mutableStateOf(TextFieldValue("SELECT ", TextRange(7)))
                }
                currentText = fieldState.text
                SqlEditor(
                    value = fieldState,
                    onValueChange = {
                        fieldState = it
                        currentText = it.text
                    },
                    availableTables = tables,
                    availableColumns = cols,
                    tableSchemas = sampleSchemas,
                    isDark = true,
                    onExecuteSql = { executeClicks++ },
                    isExecuting = false
                )
            }
        }
        composeTestRule.waitForIdle()

        // Inline toolbar Ejecutar button is visible
        composeTestRule.onNodeWithTag("editor_execute_sql_button").assertIsDisplayed()

        // Tap column 'nombre' from suggestion chips -> becomes "SELECT nombre " (no forced comma)
        composeTestRule.onNodeWithText("nombre").performClick()
        composeTestRule.waitForIdle()
        assertEquals("SELECT nombre ", currentText)

        // Next suggestion offers FROM and smart "FROM clientes" transition!
        composeTestRule.onNodeWithText("FROM clientes").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("SELECT nombre FROM clientes ", currentText)

        // Next suggestion offers WHERE as first chip!
        composeTestRule.onNodeWithTag("autocomplete_chip_0")
            .assertIsDisplayed()
            .assertTextContains("WHERE")
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("SELECT nombre FROM clientes WHERE ", currentText)

        // Now columns of 'clientes' (like 'saldo') are suggested inside WHERE
        composeTestRule.onNodeWithText("saldo").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("SELECT nombre FROM clientes WHERE saldo ", currentText)

        // Click inline Ejecutar button in editor toolbar
        composeTestRule.onNodeWithTag("editor_execute_sql_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, executeClicks)
    }

    @Test
    fun `query screen multiline where query keeps both inline and floating execute buttons accessible and functional`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbManager = DatabaseManager(context)
        dbManager.switchDatabase("tienda_comercial.db")
        val tables = runBlocking { dbManager.getTables() }
        val queryRepo = QueryRepository(context)
        val multilineWhereSql = """
            SELECT nombre, ciudad, saldo
            FROM clientes
            WHERE saldo > 100
              AND ciudad IS NOT NULL
            ORDER BY saldo DESC;
        """.trimIndent()

        try {
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = true) {
                    QueryScreen(
                        dbManager = dbManager,
                        queryRepo = queryRepo,
                        tables = tables,
                        onRefreshTables = {},
                        isDark = true,
                        activeDbName = "tienda_comercial.db",
                        onExportCsvFile = {},
                        onExportJsonFile = {},
                        persistedSqlQuery = TextFieldValue(multilineWhereSql, TextRange(multilineWhereSql.length))
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Both inline editor button and floating button must be displayed
            composeTestRule.onNodeWithTag("editor_execute_sql_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("floating_execute_sql_button").assertIsDisplayed()

            // Execute directly from the inline toolbar button
            composeTestRule.onNodeWithTag("editor_execute_sql_button").performClick()
            composeTestRule.waitForIdle()

            // Wait briefly for coroutine execution and check results rendered
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Resultado").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("sql_editor_component").assertIsDisplayed()
            composeTestRule.onNodeWithTag("floating_execute_sql_button").assertIsDisplayed()
        } finally {
            dbManager.closeDatabase()
        }
    }
}
