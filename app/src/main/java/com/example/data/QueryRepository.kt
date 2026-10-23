package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.QueryHistoryItem
import com.example.model.SavedQueryItem
import com.example.model.SqlExample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class QueryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("minisql_queries_prefs", Context.MODE_PRIVATE)

    private val historyList = mutableListOf<QueryHistoryItem>()
    private val savedList = mutableListOf<SavedQueryItem>()

    private val _historyFlow = MutableStateFlow<List<QueryHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<QueryHistoryItem>> = _historyFlow.asStateFlow()

    init {
        loadHistory()
        loadSavedQueries()
        _historyFlow.value = historyList.toList()
    }

    fun getHistory(): List<QueryHistoryItem> = historyList.toList()

    fun getLast20Queries(): List<QueryHistoryItem> = historyList.take(20)

    fun addHistoryItem(item: QueryHistoryItem) {
        historyList.removeAll { it.sql.trim() == item.sql.trim() }
        historyList.add(0, item)
        if (historyList.size > 20) {
            historyList.removeAt(historyList.lastIndex)
        }
        _historyFlow.value = historyList.toList()
        saveHistory()
    }

    fun deleteHistoryItem(id: String) {
        historyList.removeAll { it.id == id }
        _historyFlow.value = historyList.toList()
        saveHistory()
    }

    fun clearHistory() {
        historyList.clear()
        _historyFlow.value = emptyList()
        saveHistory()
    }

    fun getSavedQueries(): List<SavedQueryItem> = savedList.toList()

    fun saveQuery(title: String, sql: String, description: String = "") {
        val existingIndex = savedList.indexOfFirst { it.title.equals(title.trim(), ignoreCase = true) }
        val item = SavedQueryItem(
            title = title.trim(),
            description = description.trim(),
            sql = sql.trim()
        )
        if (existingIndex >= 0) {
            savedList[existingIndex] = item
        } else {
            savedList.add(0, item)
        }
        saveSavedQueries()
    }

    fun deleteSavedQuery(id: String) {
        savedList.removeAll { it.id == id }
        saveSavedQueries()
    }

    private fun loadHistory() {
        historyList.clear()
        val json = prefs.getString("history_json", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                historyList.add(
                    QueryHistoryItem(
                        id = obj.optString("id"),
                        sql = obj.getString("sql"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isSuccess = obj.optBoolean("isSuccess", true),
                        elapsedMs = obj.optLong("elapsedMs", 0),
                        rowsCount = obj.optInt("rowsCount", 0)
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for (item in historyList) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("sql", item.sql)
            obj.put("timestamp", item.timestamp)
            obj.put("isSuccess", item.isSuccess)
            obj.put("elapsedMs", item.elapsedMs)
            obj.put("rowsCount", item.rowsCount)
            arr.put(obj)
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    private fun loadSavedQueries() {
        savedList.clear()
        val json = prefs.getString("saved_json", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    savedList.add(
                        SavedQueryItem(
                            id = obj.optString("id"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            sql = obj.getString("sql"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Add initial default saved queries if empty
        if (savedList.isEmpty()) {
            savedList.addAll(
                listOf(
                    SavedQueryItem(
                        title = "Listar Clientes y Saldos",
                        description = "Consulta con filtro de saldo y orden descendente",
                        sql = "SELECT id, nombre, email, ciudad, saldo\nFROM clientes\nWHERE saldo > 100\nORDER BY saldo DESC;"
                    ),
                    SavedQueryItem(
                        title = "Ventas con Clientes (INNER JOIN)",
                        description = "Relaciona pedidos con información de cliente",
                        sql = "SELECT p.id AS pedido_id, c.nombre AS cliente, p.fecha, p.estado, p.total\nFROM pedidos p\nINNER JOIN clientes c ON p.cliente_id = c.id\nORDER BY p.id DESC;"
                    ),
                    SavedQueryItem(
                        title = "Detalle Completo de Pedidos",
                        description = "Join triple entre clientes, pedidos, detalles y productos",
                        sql = "SELECT p.id AS pedido_id, c.nombre AS cliente, pr.nombre AS producto, d.cantidad, d.precio_unitario, (d.cantidad * d.precio_unitario) AS subtotal\nFROM pedidos p\nJOIN clientes c ON p.cliente_id = c.id\nJOIN detalles_pedido d ON p.id = d.pedido_id\nJOIN productos pr ON d.producto_id = pr.id;"
                    )
                )
            )
            saveSavedQueries()
        }
    }

    private fun saveSavedQueries() {
        val arr = JSONArray()
        for (item in savedList) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("description", item.description)
            obj.put("sql", item.sql)
            obj.put("timestamp", item.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("saved_json", arr.toString()).apply()
    }

    fun getEducationalExamples(): List<SqlExample> {
        return listOf(
            SqlExample(
                title = "Consulta Básica (SELECT)",
                category = "Básicas",
                description = "Selecciona todas las columnas y filas de la tabla de clientes.",
                sql = "SELECT * FROM clientes;"
            ),
            SqlExample(
                title = "Filtro con WHERE y Ordenamiento",
                category = "Básicas",
                description = "Productos de Electrónica con precio mayor a 100 ordenados de mayor a menor.",
                sql = "SELECT nombre, categoria, precio, stock\nFROM productos\nWHERE categoria = 'Electrónica' AND precio > 100\nORDER BY precio DESC;"
            ),
            SqlExample(
                title = "Relación con INNER JOIN",
                category = "Relaciones / JOIN",
                description = "Combina pedidos con sus respectivos clientes usando la clave foránea.",
                sql = "SELECT p.id AS pedido, c.nombre AS cliente, c.ciudad, p.fecha, p.total\nFROM pedidos p\nINNER JOIN clientes c ON p.cliente_id = c.id;"
            ),
            SqlExample(
                title = "LEFT JOIN con Clientes sin Compras",
                category = "Relaciones / JOIN",
                description = "Muestra todos los clientes incluso si aún no han registrado ningún pedido.",
                sql = "SELECT c.nombre, c.email, p.id AS pedido_id, p.total\nFROM clientes c\nLEFT JOIN pedidos p ON c.id = p.cliente_id;"
            ),
            SqlExample(
                title = "Agrupación y Totalización (GROUP BY)",
                category = "Agregaciones",
                description = "Calcula el número de productos y el precio promedio por categoría.",
                sql = "SELECT categoria, COUNT(*) AS total_items, AVG(precio) AS precio_promedio, SUM(stock) AS stock_total\nFROM productos\nGROUP BY categoria\nHAVING COUNT(*) > 0;"
            ),
            SqlExample(
                title = "Ventas por Categoría (Gráfico)",
                category = "Gráficos / Visual",
                description = "Consulta optimizada para visualizar métricas en gráfico de barras o líneas.",
                sql = "SELECT categoria, SUM(stock) AS stock_total, ROUND(AVG(precio), 2) AS precio_promedio\nFROM productos\nGROUP BY categoria\nORDER BY stock_total DESC;"
            ),
            SqlExample(
                title = "Saldos por Ciudad (Gráfico de Pastel)",
                category = "Gráficos / Visual",
                description = "Total de saldo de clientes agrupado por ciudad ideal para gráfico de pastel o dona.",
                sql = "SELECT ciudad, SUM(saldo) AS saldo_acumulado\nFROM clientes\nWHERE ciudad IS NOT NULL\nGROUP BY ciudad\nORDER BY saldo_acumulado DESC;"
            ),
            SqlExample(
                title = "Subconsulta con IN",
                category = "Avanzadas",
                description = "Encuentra clientes que tienen al menos un pedido entregado.",
                sql = "SELECT nombre, email\nFROM clientes\nWHERE id IN (\n    SELECT cliente_id\n    FROM pedidos\n    WHERE estado = 'Entregado'\n);"
            ),
            SqlExample(
                title = "Creación de Tabla con Clave Foránea",
                category = "DDL / Estructura",
                description = "Crea una nueva tabla de proveedores con restricciones de integridad.",
                sql = """
                    CREATE TABLE IF NOT EXISTS proveedores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nombre_empresa TEXT NOT NULL,
                        telefono TEXT,
                        pais TEXT DEFAULT 'España'
                    );
                """.trimIndent()
            ),
            SqlExample(
                title = "Inspección de Metadatos (PRAGMA)",
                category = "Utilidades SQLite",
                description = "Consulta la estructura interna y tipos de datos de una tabla.",
                sql = "PRAGMA table_info(clientes);"
            ),
            SqlExample(
                title = "Plan de Ejecución (EXPLAIN QUERY PLAN)",
                category = "Utilidades SQLite",
                description = "Analiza cómo SQLite resolverá y optimizará la consulta.",
                sql = "EXPLAIN QUERY PLAN\nSELECT * FROM pedidos WHERE cliente_id = 1;"
            )
        )
    }
}
