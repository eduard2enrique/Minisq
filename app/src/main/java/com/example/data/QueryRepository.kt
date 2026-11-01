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
                        title = "Estudiantes, Materias y Profesores (JOIN Triple)",
                        description = "Relaciona cada estudiante con su materia y profesor titular",
                        sql = "SELECT e.matricula, e.nombre AS estudiante, m.nombre AS materia, p.nombre AS profesor, e.promedio\nFROM estudiantes e\nINNER JOIN materias m ON e.materia_id = m.id\nINNER JOIN profesores p ON m.profesor_id = p.id\nORDER BY e.promedio DESC;"
                    ),
                    SavedQueryItem(
                        title = "Cátedras de Ciencias de Datos & IA (Prof. Eduardo Sanamé)",
                        description = "Materias a cargo del titular Prof. Eduardo Sanamé Durán",
                        sql = "SELECT m.codigo, m.nombre AS materia, m.creditos, m.semestre, p.nombre AS profesor, p.especialidad\nFROM materias m\nINNER JOIN profesores p ON m.profesor_id = p.id\nWHERE p.nombre LIKE '%Sanamé%'\nORDER BY m.semestre ASC;"
                    ),
                    SavedQueryItem(
                        title = "Cuadro de Honor (Promedio >= 95.0)",
                        description = "Alumnos de máximo rendimiento académico en la Facultad",
                        sql = "SELECT e.nombre, e.matricula, m.nombre AS materia, e.promedio, e.ano_carrera\nFROM estudiantes e\nJOIN materias m ON e.materia_id = m.id\nWHERE e.promedio >= 95.0\nORDER BY e.promedio DESC;"
                    ),
                    SavedQueryItem(
                        title = "Promedio General por Disciplina Académica",
                        description = "Agrupación de rendimiento medio y total de inscritos por materia",
                        sql = "SELECT m.nombre AS materia, COUNT(e.id) AS total_estudiantes, ROUND(AVG(e.promedio), 2) AS promedio_materia\nFROM materias m\nLEFT JOIN estudiantes e ON m.id = e.materia_id\nGROUP BY m.id, m.nombre\nORDER BY promedio_materia DESC;"
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
                title = "Consulta General de Alumnos (SELECT)",
                category = "Básicas",
                description = "Lista todos los estudiantes ordenados por promedio académico de mayor a menor.",
                sql = "SELECT matricula, nombre, promedio, ano_carrera\nFROM estudiantes\nORDER BY promedio DESC;"
            ),
            SqlExample(
                title = "Filtro de Profesores por Departamento",
                category = "Básicas",
                description = "Filtra profesores del área de Informática y Computación ordenados alfabéticamente.",
                sql = "SELECT nombre, especialidad, email\nFROM profesores\nWHERE departamento = 'Informática y Computación'\nORDER BY nombre ASC;"
            ),
            SqlExample(
                title = "Inserción con Clave Auto-Incremental (INSERT OR IGNORE)",
                category = "Manipulación (DML)",
                description = "Inserta un nuevo estudiante. Con 'OR IGNORE' no falla si la matrícula o ID ya existen.",
                sql = "-- 'INSERT OR IGNORE' garantiza re-ejecución segura sin fallos por clave duplicada:\n" +
                      "INSERT OR IGNORE INTO estudiantes (nombre, matricula, materia_id, promedio, ano_carrera)\n" +
                      "VALUES ('Alejandro Cruz Paz', 'EST-2026-099', 2, 94.5, 1);"
            ),
            SqlExample(
                title = "Inserción Segura de Profesor (INSERT OR IGNORE)",
                category = "Manipulación (DML)",
                description = "Inserta un nuevo profesor titular con correo único institucional.",
                sql = "INSERT OR IGNORE INTO profesores (nombre, especialidad, email, departamento)\n" +
                      "VALUES ('Dr. Fernando Ramos', 'Simulación de Sistemas Cuánticos', 'fernando.ramos@facultadciencias.edu', 'Física Avanzada');"
            ),
            SqlExample(
                title = "Inserción o Reemplazo (REPLACE INTO)",
                category = "Manipulación (DML)",
                description = "Si la clave primaria o valor UNIQUE ya existe, actualiza la fila completa (Upsert).",
                sql = "-- 'REPLACE INTO' actualiza la fila si ya existe o la crea si no existe:\n" +
                      "REPLACE INTO estudiantes (id, nombre, matricula, materia_id, promedio, ano_carrera)\n" +
                      "VALUES (1, 'Gabriel Alonso Pérez', 'EST-2024-001', 2, 98.5, 3);"
            ),
            SqlExample(
                title = "Actualización Segura (UPDATE con WHERE)",
                category = "Manipulación (DML)",
                description = "Modifica registros específicos mediante su clave primaria o matrícula.",
                sql = "-- Regla de oro en SQL: Verifica siempre la condición WHERE antes de ejecutar UPDATE\n" +
                      "UPDATE estudiantes\n" +
                      "SET promedio = 99.0\n" +
                      "WHERE matricula = 'EST-2024-002';"
            ),
            SqlExample(
                title = "Eliminación Controlada (DELETE con WHERE)",
                category = "Manipulación (DML)",
                description = "Elimina registros que cumplan una condición. Sin WHERE, la tabla quedaría completamente vacía.",
                sql = "-- Precaución didáctica: Siempre filtra con WHERE o clave primaria (id):\n" +
                      "DELETE FROM clientes\n" +
                      "WHERE saldo = 0.0 AND ciudad IS NULL;"
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
                title = "Creación de Tabla Segura (IF NOT EXISTS)",
                category = "DDL / Estructura",
                description = "Crea una tabla con 'IF NOT EXISTS' e inserta con 'INSERT OR IGNORE' para total re-ejecución sin errores.",
                sql = """
                    CREATE TABLE IF NOT EXISTS proveedores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nombre_empresa TEXT NOT NULL,
                        telefono TEXT,
                        pais TEXT DEFAULT 'España'
                    );

                    INSERT OR IGNORE INTO proveedores (id, nombre_empresa, telefono)
                    VALUES (1, 'Logística Ibérica SL', '+34 912 345 678');
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
            ),
            SqlExample(
                title = "Provincias de Cuba por Población",
                category = "Cuba Geografía & Cultura",
                description = "Lista las provincias ordenadas de mayor a menor población con su capital y región.",
                sql = "-- Consulta para la base 'cuba_geografia.db':\nSELECT nombre, capital, region, poblacion, area_km2\nFROM provincias\nORDER BY poblacion DESC;"
            ),
            SqlExample(
                title = "Sitios Patrimonio de la Humanidad (UNESCO)",
                category = "Cuba Geografía & Cultura",
                description = "Lugares de interés histórico y natural en Cuba con reconocimiento UNESCO vinculados a su provincia.",
                sql = "SELECT l.nombre AS sitio_patrimonial, l.tipo, p.nombre AS provincia, p.region\nFROM lugares_interes l\nINNER JOIN provincias p ON l.provincia_id = p.id\nWHERE l.patrimonio_unesco = 1\nORDER BY p.nombre ASC;"
            ),
            SqlExample(
                title = "Población y Área por Región de Cuba",
                category = "Cuba Geografía & Cultura",
                description = "Agrupa y totaliza la población y el promedio de área por región geográfica cubana.",
                sql = "SELECT region, COUNT(*) AS total_provincias, SUM(poblacion) AS poblacion_total, ROUND(AVG(area_km2), 1) AS area_promedio_km2\nFROM provincias\nGROUP BY region\nORDER BY poblacion_total DESC;"
            ),
            SqlExample(
                title = "Gastronomía Tradicional Cubana",
                category = "Cuba Geografía & Cultura",
                description = "Platos y delicias típicas cubanas ordenadas por tipo y región de origen.",
                sql = "SELECT nombre, tipo, ingrediente_principal, region_origen\nFROM platos_tradicionales\nORDER BY tipo ASC, nombre ASC;"
            ),
            SqlExample(
                title = "Estudiantes, Materias y Profesores (JOIN)",
                category = "Centro Estudiantil & Ciencias",
                description = "Relación completa entre estudiantes, la materia que cursan y el profesor titular asignado.",
                sql = "-- Consulta relacional completa para 'centro_estudiantil.db':\n" +
                      "SELECT e.matricula, e.nombre AS estudiante, m.nombre AS materia, p.nombre AS profesor, e.promedio\n" +
                      "FROM estudiantes e\n" +
                      "INNER JOIN materias m ON e.materia_id = m.id\n" +
                      "INNER JOIN profesores p ON m.profesor_id = p.id\n" +
                      "ORDER BY e.promedio DESC;"
            ),
            SqlExample(
                title = "Materias impartidas por el Prof. Eduardo Sanamé",
                category = "Centro Estudiantil & Ciencias",
                description = "Filtra las materias de Ciencia de Datos e Inteligencia Artificial impartidas por el profesor titular.",
                sql = "SELECT m.codigo, m.nombre AS materia, m.creditos, m.semestre, p.especialidad\n" +
                      "FROM materias m\n" +
                      "INNER JOIN profesores p ON m.profesor_id = p.id\n" +
                      "WHERE p.nombre LIKE '%Sanamé%';"
            ),
            SqlExample(
                title = "Estudiantes de Honor (Promedio >= 95.0)",
                category = "Centro Estudiantil & Ciencias",
                description = "Lista los alumnos de mayor rendimiento académico vinculando su disciplina científica.",
                sql = "SELECT e.nombre, e.matricula, m.nombre AS materia, e.promedio, e.ano_carrera\n" +
                      "FROM estudiantes e\n" +
                      "JOIN materias m ON e.materia_id = m.id\n" +
                      "WHERE e.promedio >= 95.0\n" +
                      "ORDER BY e.promedio DESC;"
            )
        )
    }
}
