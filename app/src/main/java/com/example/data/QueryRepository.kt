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
                        title = "Nivel 1: Top 5 de Estudiantes (Mejor Índice)",
                        description = "Alumnos de máximo rendimiento antes de los exámenes de ingreso",
                        sql = "SELECT nombre_completo, sexo, indice_academico, centro_estudio\nFROM estudiantes\nORDER BY indice_academico DESC\nLIMIT 5;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 1: Carreras Técnicas en La Habana",
                        description = "Aspirantes a carreras técnicas de la capital (JOIN triple con filtro)",
                        sql = "SELECT e.nombre_completo, c.nombre_carrera, u.siglas, u.provincia\nFROM estudiantes e\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nJOIN carreras c ON b.id_carrera = c.id_carrera\nJOIN universidades u ON c.id_universidad = u.id_universidad\nWHERE u.provincia = 'La Habana' AND c.id_agrupacion = 2;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 2: Promedios en Exámenes de Ingreso",
                        description = "Promedio de notas en Matemática, Español e Historia",
                        sql = "SELECT \n    ROUND(AVG(nota_matematica), 2) AS Promedio_Mate,\n    ROUND(AVG(nota_espanol), 2) AS Promedio_Espanol,\n    ROUND(AVG(nota_historia), 2) AS Promedio_Historia\nFROM examenes_ingreso;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 2: Carrera Más Popular (Demanda 1ra Opción)",
                        description = "Total de estudiantes que la solicitaron como prioridad 1",
                        sql = "SELECT c.nombre_carrera, u.siglas, COUNT(b.id_estudiante) AS Cantidad_Estudiantes\nFROM boletas_solicitud b\nJOIN carreras c ON b.id_carrera = c.id_carrera\nJOIN universidades u ON c.id_universidad = u.id_universidad\nWHERE b.orden_prioridad = 1\nGROUP BY c.nombre_carrera, u.siglas\nORDER BY Cantidad_Estudiantes DESC;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 2: Género en Ingeniería Informática",
                        description = "Distribución de mujeres y hombres que piden Informática (CUJAE)",
                        sql = "SELECT e.sexo, COUNT(*) AS Cantidad\nFROM estudiantes e\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nWHERE b.id_carrera = 4\nGROUP BY e.sexo;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 3: Índice de Corte (Informática)",
                        description = "Nota del último estudiante que alcanza una de las 4 plazas (LIMIT 1 OFFSET 3)",
                        sql = "SELECT e.nombre_completo, \n       ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nWHERE b.id_carrera = 4\nORDER BY Indice_Final DESC\nLIMIT 1 OFFSET 3;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 3: El 'Efecto Dominó' (Plan B)",
                        description = "Aspirantes fuera del Top 4 de Informática y cuál es su 2da opción",
                        sql = "SELECT \n    e.nombre_completo AS Estudiante,\n    ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final,\n    c2.nombre_carrera AS Segunda_Opcion,\n    u2.siglas AS Universidad_Plan_B\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nJOIN boletas_solicitud b1 ON e.id_estudiante = b1.id_estudiante AND b1.id_carrera = 4\nJOIN boletas_solicitud b2 ON e.id_estudiante = b2.id_estudiante AND b2.orden_prioridad = 2\nJOIN carreras c2 ON b2.id_carrera = c2.id_carrera\nJOIN universidades u2 ON c2.id_universidad = u2.id_universidad\nWHERE e.id_estudiante NOT IN (\n    SELECT e2.id_estudiante\n    FROM estudiantes e2\n    JOIN examenes_ingreso ex2 ON e2.id_estudiante = ex2.id_estudiante\n    JOIN boletas_solicitud b3 ON e2.id_estudiante = b3.id_estudiante\n    WHERE b3.id_carrera = 4\n    ORDER BY (e2.indice_academico * 0.5) + (((ex2.nota_matematica + ex2.nota_espanol + ex2.nota_historia) / 3.0) * 0.5) DESC\n    LIMIT 4\n)\nORDER BY Indice_Final DESC;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 4: Escalafón por Carrera (ROW_NUMBER)",
                        description = "Numera el puesto exacto de cada estudiante por carrera según su nota final",
                        sql = "WITH NotasFinales AS (\n    SELECT \n        e.nombre_completo,\n        b.id_carrera,\n        ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS indice_final\n    FROM estudiantes e\n    JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\n    JOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\n)\nSELECT \n    c.nombre_carrera,\n    nf.nombre_completo,\n    nf.indice_final,\n    ROW_NUMBER() OVER (PARTITION BY nf.id_carrera ORDER BY nf.indice_final DESC) AS Puesto_En_Carrera\nFROM NotasFinales nf\nJOIN carreras c ON nf.id_carrera = c.id_carrera\nORDER BY c.nombre_carrera, Puesto_En_Carrera;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 4: Simulador de Primer Otorgamiento",
                        description = "Cruza el puesto con la tabla plazas_oferta para saber si entra o va a espera",
                        sql = "WITH Escalafon AS (\n    SELECT \n        b.id_estudiante,\n        b.id_carrera,\n        ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS indice_final,\n        ROW_NUMBER() OVER (PARTITION BY b.id_carrera ORDER BY ((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5)) DESC) AS puesto\n    FROM boletas_solicitud b\n    JOIN estudiantes e ON b.id_estudiante = e.id_estudiante\n    JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\n    WHERE b.orden_prioridad = 1\n)\nSELECT \n    e.nombre_completo,\n    c.nombre_carrera,\n    esc.indice_final,\n    po.cantidad_plazas,\n    CASE \n        WHEN esc.puesto <= po.cantidad_plazas THEN '¡ADENTRO!'\n        ELSE 'Lista de espera'\n    END AS Resultado_Otorgamiento\nFROM Escalafon esc\nJOIN estudiantes e ON esc.id_estudiante = e.id_estudiante\nJOIN carreras c ON esc.id_carrera = c.id_carrera\nJOIN plazas_oferta po ON c.id_carrera = po.id_carrera\nORDER BY c.nombre_carrera, esc.puesto;"
                    ),
                    SavedQueryItem(
                        title = "Nivel 5: Escalafón Integral con Desempate",
                        description = "Escalafón general oficial ponderado con desempate por nota de Matemática",
                        sql = "SELECT \n    ROW_NUMBER() OVER (\n        ORDER BY \n            ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) DESC,\n            ex.nota_matematica DESC,\n            e.indice_academico DESC\n    ) AS Puesto_General,\n    e.nombre_completo,\n    e.sexo,\n    e.indice_academico,\n    ex.nota_matematica AS Mate,\n    ex.nota_espanol AS Esp,\n    ex.nota_historia AS Hist,\n    ROUND((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0, 2) AS Prom_Examenes,\n    ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nORDER BY Puesto_General ASC;"
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
            // Nivel 1: Básico (Filtros y Ordenamiento)
            SqlExample(
                title = "1. Top 5 de Alumnos por Índice Académico",
                category = "Nivel 1: Básico (Filtros y Ordenamiento)",
                description = "El Top 5 de los estudiantes con mejor índice académico antes de los exámenes.",
                sql = "SELECT nombre_completo, sexo, indice_academico, centro_estudio\nFROM estudiantes\nORDER BY indice_academico DESC\nLIMIT 5;"
            ),
            SqlExample(
                title = "2. Carreras Técnicas en La Habana (JOIN)",
                category = "Nivel 1: Básico (Filtros y Ordenamiento)",
                description = "Aspirantes a carreras técnicas en la capital combinando boletas, carreras y universidades.",
                sql = "SELECT e.nombre_completo, c.nombre_carrera, u.siglas, u.provincia\nFROM estudiantes e\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nJOIN carreras c ON b.id_carrera = c.id_carrera\nJOIN universidades u ON c.id_universidad = u.id_universidad\nWHERE u.provincia = 'La Habana' AND c.id_agrupacion = 2; -- 2 = Ciencias Técnicas"
            ),

            // Nivel 2: Intermedio (Cálculos, Promedios y Agrupaciones)
            SqlExample(
                title = "3. Promedio por Asignatura en Exámenes de Ingreso",
                category = "Nivel 2: Intermedio (Cálculos y Agrupaciones)",
                description = "Promedio de notas en Matemática, Español e Historia redondeado a 2 decimales.",
                sql = "SELECT \n    ROUND(AVG(nota_matematica), 2) AS Promedio_Mate,\n    ROUND(AVG(nota_espanol), 2) AS Promedio_Espanol,\n    ROUND(AVG(nota_historia), 2) AS Promedio_Historia\nFROM examenes_ingreso;"
            ),
            SqlExample(
                title = "4. Carrera Más Popular en 1ra Opción (Demanda)",
                category = "Nivel 2: Intermedio (Cálculos y Agrupaciones)",
                description = "Cantidad de estudiantes que solicitaron cada carrera como su primera prioridad.",
                sql = "SELECT c.nombre_carrera, u.siglas, COUNT(b.id_estudiante) AS Cantidad_Estudiantes\nFROM boletas_solicitud b\nJOIN carreras c ON b.id_carrera = c.id_carrera\nJOIN universidades u ON c.id_universidad = u.id_universidad\nWHERE b.orden_prioridad = 1\nGROUP BY c.nombre_carrera, u.siglas\nORDER BY Cantidad_Estudiantes DESC;"
            ),
            SqlExample(
                title = "5. Comparación de Género en Ingeniería Informática",
                category = "Nivel 2: Intermedio (Cálculos y Agrupaciones)",
                description = "Conteo de mujeres y hombres aspirantes a Informática en la CUJAE.",
                sql = "SELECT e.sexo, COUNT(*) AS Cantidad\nFROM estudiantes e\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nWHERE b.id_carrera = 4 -- Ingeniería Informática\nGROUP BY e.sexo;"
            ),

            // Nivel 3: Avanzado (Subconsultas y Lógica del Escalafón)
            SqlExample(
                title = "6. El Índice de Corte (Nota del Último en Entrar)",
                category = "Nivel 3: Avanzado (Subconsultas y Escalafón)",
                description = "Informática tiene 4 plazas. Ordena los 7 aspirantes y con LIMIT 1 OFFSET 3 da la nota de corte.",
                sql = "SELECT e.nombre_completo, \n       ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nJOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\nWHERE b.id_carrera = 4\nORDER BY Indice_Final DESC\nLIMIT 1 OFFSET 3;"
            ),
            SqlExample(
                title = "7. El 'Efecto Dominó' (Plan B de los Fuera de Corte)",
                category = "Nivel 3: Avanzado (Subconsultas y Escalafón)",
                description = "Estudiantes fuera del Top 4 de Informática y su segunda opción de carrera.",
                sql = "SELECT \n    e.nombre_completo AS Estudiante,\n    ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final,\n    c2.nombre_carrera AS Segunda_Opcion,\n    u2.siglas AS Universidad_Plan_B\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nJOIN boletas_solicitud b1 ON e.id_estudiante = b1.id_estudiante AND b1.id_carrera = 4\nJOIN boletas_solicitud b2 ON e.id_estudiante = b2.id_estudiante AND b2.orden_prioridad = 2\nJOIN carreras c2 ON b2.id_carrera = c2.id_carrera\nJOIN universidades u2 ON c2.id_universidad = u2.id_universidad\nWHERE e.id_estudiante NOT IN (\n    SELECT e2.id_estudiante\n    FROM estudiantes e2\n    JOIN examenes_ingreso ex2 ON e2.id_estudiante = ex2.id_estudiante\n    JOIN boletas_solicitud b3 ON e2.id_estudiante = b3.id_estudiante\n    WHERE b3.id_carrera = 4\n    ORDER BY (e2.indice_academico * 0.5) + (((ex2.nota_matematica + ex2.nota_espanol + ex2.nota_historia) / 3.0) * 0.5) DESC\n    LIMIT 4\n)\nORDER BY Indice_Final DESC;"
            ),

            // Nivel 4: Nivel Dios (CTE y Window Functions)
            SqlExample(
                title = "8. Escalafón Exacto por Carrera (ROW_NUMBER)",
                category = "Nivel 4: Window Functions & CTE",
                description = "Asigna a cada estudiante su número de puesto dentro de la carrera que solicitó.",
                sql = "WITH NotasFinales AS (\n    SELECT \n        e.nombre_completo,\n        b.id_carrera,\n        ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS indice_final\n    FROM estudiantes e\n    JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\n    JOIN boletas_solicitud b ON e.id_estudiante = b.id_estudiante\n)\nSELECT \n    c.nombre_carrera,\n    nf.nombre_completo,\n    nf.indice_final,\n    ROW_NUMBER() OVER (PARTITION BY nf.id_carrera ORDER BY nf.indice_final DESC) AS Puesto_En_Carrera\nFROM NotasFinales nf\nJOIN carreras c ON nf.id_carrera = c.id_carrera\nORDER BY c.nombre_carrera, Puesto_En_Carrera;"
            ),
            SqlExample(
                title = "9. Simulador de Primer Otorgamiento (Plazas vs Demanda)",
                category = "Nivel 4: Window Functions & CTE",
                description = "Cruza el puesto con las plazas ofertadas: '¡ADENTRO!' si puesto <= plazas, o 'Lista de espera'.",
                sql = "WITH Escalafon AS (\n    SELECT \n        b.id_estudiante,\n        b.id_carrera,\n        ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS indice_final,\n        ROW_NUMBER() OVER (PARTITION BY b.id_carrera ORDER BY ((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5)) DESC) AS puesto\n    FROM boletas_solicitud b\n    JOIN estudiantes e ON b.id_estudiante = e.id_estudiante\n    JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\n    WHERE b.orden_prioridad = 1 -- Evaluando solo la primera opción\n)\nSELECT \n    e.nombre_completo,\n    c.nombre_carrera,\n    esc.indice_final,\n    po.cantidad_plazas,\n    CASE \n        WHEN esc.puesto <= po.cantidad_plazas THEN '¡ADENTRO!'\n        ELSE 'Lista de espera'\n    END AS Resultado_Otorgamiento\nFROM Escalafon esc\nJOIN estudiantes e ON esc.id_estudiante = e.id_estudiante\nJOIN carreras c ON esc.id_carrera = c.id_carrera\nJOIN plazas_oferta po ON c.id_carrera = po.id_carrera\nORDER BY c.nombre_carrera, esc.puesto;"
            ),

            // Nivel 5: Escalafón General Ponderado con Criterios de Desempate
            SqlExample(
                title = "10. Escalafón General Integral con Desempate",
                category = "Nivel 5: Escalafón Ponderado Completo",
                description = "Ranking oficial de todos los aspirantes: 50% Preuniversitario + 50% Exámenes con desempate por Matemática.",
                sql = "SELECT \n    ROW_NUMBER() OVER (\n        ORDER BY \n            ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) DESC,\n            ex.nota_matematica DESC,\n            e.indice_academico DESC\n    ) AS Puesto_General,\n    e.nombre_completo,\n    e.sexo,\n    e.centro_estudio,\n    e.indice_academico,\n    ex.nota_matematica AS Mate,\n    ex.nota_espanol AS Esp,\n    ex.nota_historia AS Hist,\n    ROUND((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0, 2) AS Prom_Examenes,\n    ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS Indice_Final\nFROM estudiantes e\nJOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante\nORDER BY Puesto_General ASC;"
            ),

            // Utilidades DDL y SQLite
            SqlExample(
                title = "Inspección de Esquema (PRAGMA table_info)",
                category = "Utilidades SQLite",
                description = "Consulta la estructura interna, tipos de datos y restricciones de la tabla estudiantes.",
                sql = "PRAGMA table_info(estudiantes);"
            ),
            SqlExample(
                title = "Inspección de Relaciones (PRAGMA foreign_key_list)",
                category = "Utilidades SQLite",
                description = "Lista las claves foráneas activas y tablas padre para la tabla boletas_solicitud.",
                sql = "PRAGMA foreign_key_list(boletas_solicitud);"
            ),

            // Sección Didáctica: Solución de Errores Frecuentes
            SqlExample(
                title = "Evitar Error de INSERT Duplicado (INSERT OR IGNORE)",
                category = "Solución de Errores",
                description = "Omite la inserción si el ID o clave UNIQUE ya existe, evitando 'UNIQUE constraint failed'.",
                sql = "-- Si el carnet ya existe, no lanza error y continúa\nINSERT OR IGNORE INTO estudiantes (\n    carnet_identidad, carnet_preuniversitario, nombre_completo, sexo,\n    via_ingreso, indice_academico, provincia, municipio, centro_estudio\n) VALUES (\n    '95010112345', '2026-001', 'Melisa María Fat Tamayo', 'F',\n    'Preuniversitario', 98.50, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'\n);"
            ),
            SqlExample(
                title = "Actualizar Registro si ya Existe (INSERT OR REPLACE)",
                category = "Solución de Errores",
                description = "Actualiza los datos del registro si la clave primaria ya existía en la base.",
                sql = "-- Si existe el id=1, sobreescribe sus datos (UPSERT)\nINSERT OR REPLACE INTO estudiantes (\n    id_estudiante, carnet_identidad, carnet_preuniversitario, nombre_completo, sexo,\n    via_ingreso, indice_academico, provincia, municipio, centro_estudio\n) VALUES (\n    1, '95010112345', '2026-001', 'Melisa María Fat Tamayo Actualizada', 'F',\n    'Preuniversitario', 99.00, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'\n);"
            ),
            SqlExample(
                title = "Creación Segura de Tablas (CREATE TABLE IF NOT EXISTS)",
                category = "Solución de Errores",
                description = "Evita 'table ... already exists' permitiendo volver a ejecutar el script cuantas veces quieras.",
                sql = "-- La cláusula IF NOT EXISTS asegura que no falle si la tabla ya existe\nCREATE TABLE IF NOT EXISTS notas_adicionales (\n    id_nota INTEGER PRIMARY KEY AUTOINCREMENT,\n    id_estudiante INTEGER NOT NULL,\n    materia TEXT NOT NULL,\n    puntos REAL CHECK (puntos BETWEEN 0 AND 100),\n    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante)\n);"
            ),
            SqlExample(
                title = "Reiniciar Tabla desde Cero (DROP TABLE IF EXISTS)",
                category = "Solución de Errores",
                description = "Elimina la tabla anterior de forma segura antes de crear una versión limpia.",
                sql = "-- Borra la tabla vieja si existe para recrearla limpia sin error\nDROP TABLE IF EXISTS tabla_temporal;\n\nCREATE TABLE tabla_temporal (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    descripcion TEXT NOT NULL\n);"
            ),
            SqlExample(
                title = "Comillas Simples vs Dobles en Filtros (WHERE)",
                category = "Solución de Errores",
                description = "Las cadenas de texto deben ir entre comillas simples ('Moa'). Las comillas dobles (\"Moa\") buscan columnas.",
                sql = "-- Correcto: comillas simples '...' para valores de texto\nSELECT nombre_completo, municipio, centro_estudio\nFROM estudiantes\nWHERE provincia = 'Holguín' AND municipio = 'Moa';\n-- ❌ Error común: WHERE municipio = \"Moa\" (SQLite busca columna llamada Moa)"
            ),
            SqlExample(
                title = "Prevenir Cálculos Nulos con COALESCE",
                category = "Solución de Errores",
                description = "Si un valor es NULL (ej. examen no presentado), COALESCE(nota, 0) evita que todo el cálculo resulte en NULL.",
                sql = "-- COALESCE reemplaza cualquier valor NULL con 0 para no romper el promedio\nSELECT \n    e.nombre_completo,\n    e.indice_academico,\n    COALESCE(ex.nota_matematica, 0) AS nota_mat,\n    ROUND((e.indice_academico + COALESCE(ex.nota_matematica, 0)) / 2.0, 2) AS promedio_seguro\nFROM estudiantes e\nLEFT JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante;"
            ),
            SqlExample(
                title = "Claves Foráneas: Orden Correcto de Inserción",
                category = "Solución de Errores",
                description = "Inserta primero el registro padre antes de referenciar su ID en una tabla hija.",
                sql = "-- 1. Comprobar que la carrera existe en la tabla padre 'carreras':\nSELECT id_carrera, nombre_carrera FROM carreras WHERE id_carrera = 6;\n\n-- 2. Ahora sí podemos insertar la solicitud en la tabla hija 'boletas_solicitud':\nINSERT INTO boletas_solicitud (id_estudiante, id_carrera, orden_prioridad)\nVALUES (1, 6, 1);"
            )
        )
    }

    fun getEducationalExamplesForDb(dbName: String): List<SqlExample> {
        val lower = dbName.lowercase()
        return when {
            lower.contains("secretaria") -> listOf(
                SqlExample(
                    title = "1. Exploración de Grupos Escolares",
                    category = "Modo Aula: Exploración Básica",
                    description = "Ver los grupos de 10mo, 11no y 12mo grado, aulas y profesores tutores.",
                    sql = "SELECT id_grupo, codigo_grupo, grado, especialidad, aula, profesor_tutor, turno\nFROM grupos\nORDER BY grado ASC, codigo_grupo ASC;"
                ),
                SqlExample(
                    title = "2. Claustro Docente y Materias",
                    category = "Modo Aula: Exploración Básica",
                    description = "Listado de materias escolares, departamentos y profesores titulares.",
                    sql = "SELECT codigo_materia, nombre_materia, departamento, horas_semanales, profesor_titular\nFROM materias\nORDER BY departamento, nombre_materia;"
                ),
                SqlExample(
                    title = "3. Estudiantes y Matrícula Activa",
                    category = "Modo Aula: Exploración Básica",
                    description = "Alumnos registrados, expediente oficial, grupo asignado y promedio de ingreso.",
                    sql = "SELECT numero_expediente, nombre_completo, sexo, id_grupo, promedio_ingreso, estado_matricula\nFROM estudiantes_matricula\nWHERE estado_matricula = 'Activo'\nORDER BY promedio_ingreso DESC;"
                ),
                SqlExample(
                    title = "4. Historial de Reasignaciones y Trámites",
                    category = "Modo Aula: Historial y Trámites",
                    description = "Registro de cambios de grupo, permutas, nivelaciones y dictámenes de secretaría.",
                    sql = "SELECT fecha_tramite, tipo_tramite, nombre_estudiante, motivo, secretario_tramitador, estado_dictamen\nFROM reasignaciones\nORDER BY fecha_tramite DESC;"
                ),
                SqlExample(
                    title = "Solución de Error: IF NOT EXISTS",
                    category = "Solución de Errores",
                    description = "Evita error al crear tablas en clases si ya existían.",
                    sql = "CREATE TABLE IF NOT EXISTS asistencias (\n    id_asistencia INTEGER PRIMARY KEY AUTOINCREMENT,\n    id_estudiante INTEGER,\n    fecha TEXT NOT NULL,\n    asistio INTEGER CHECK (asistio IN (0, 1)),\n    FOREIGN KEY (id_estudiante) REFERENCES estudiantes_matricula(id_estudiante)\n);"
                )
            )

            lower.contains("cuba") -> listOf(
                SqlExample(
                    title = "1. Provincias por Población (ORDER BY)",
                    category = "Geografía Básica",
                    description = "Ranking de provincias cubanas ordenadas de mayor a menor población.",
                    sql = "SELECT nombre, capital, region, poblacion\nFROM provincias\nORDER BY poblacion DESC;"
                ),
                SqlExample(
                    title = "2. Municipios de la Provincia de Holguín",
                    category = "Geografía Básica",
                    description = "Filtro de municipios correspondientes a la provincia de Holguín.",
                    sql = "SELECT m.nombre AS Municipio, p.nombre AS Provincia, p.region\nFROM municipios m\nJOIN provincias p ON m.provincia_id = p.id\nWHERE p.nombre = 'Holguín'\nORDER BY m.nombre ASC;"
                ),
                SqlExample(
                    title = "3. Conteo de Municipios por Provincia (GROUP BY)",
                    category = "Agrupaciones y Resumen",
                    description = "Cantidad total de municipios en cada provincia de Cuba.",
                    sql = "SELECT p.nombre AS Provincia, COUNT(m.id) AS Cantidad_Municipios\nFROM provincias p\nLEFT JOIN municipios m ON p.id = m.provincia_id\nGROUP BY p.id, p.nombre\nORDER BY Cantidad_Municipios DESC;"
                ),
                SqlExample(
                    title = "4. Lugares de Interés Histórico y Natural (LIKE)",
                    category = "Filtros y Búsqueda",
                    description = "Sitios patrimoniales y parques naturales destacados.",
                    sql = "SELECT nombre, tipo, descripcion\nFROM lugares_interes\nWHERE tipo IN ('Natural', 'Histórico')\nORDER BY tipo, nombre;"
                ),
                SqlExample(
                    title = "Evitar Duplicados con INSERT OR IGNORE",
                    category = "Solución de Errores",
                    description = "Inserta un lugar de interés sin fallar si ya existía su clave única.",
                    sql = "INSERT OR IGNORE INTO lugares_interes (nombre, provincia_id, tipo, descripcion)\nVALUES ('Salto del Guayabo', 10, 'Natural', 'Una de las mayores cascadas de Cuba en Mayarí, Holguín.');"
                )
            )

            lower.contains("tienda") || lower.contains("comercial") -> listOf(
                SqlExample(
                    title = "1. Productos con Stock Bajo (< 20 unidades)",
                    category = "Gestión Comercial",
                    description = "Listado de inventario crítico que requiere reabastecimiento.",
                    sql = "SELECT nombre, categoria, precio, stock\nFROM productos\nWHERE stock < 20\nORDER BY stock ASC;"
                ),
                SqlExample(
                    title = "2. Total de Ventas por Cliente (JOIN y SUM)",
                    category = "Finanzas y Reportes",
                    description = "Gasto total acumulado por cada cliente con pedidos registrados.",
                    sql = "SELECT c.nombre AS Cliente, c.ciudad, COUNT(p.id) AS Total_Pedidos, SUM(p.total) AS Gasto_Total\nFROM clientes c\nJOIN pedidos p ON c.id = p.cliente_id\nGROUP BY c.id, c.nombre, c.ciudad\nORDER BY Gasto_Total DESC;"
                ),
                SqlExample(
                    title = "3. Clientes y Saldo Promedio por Ciudad",
                    category = "Finanzas y Reportes",
                    description = "Cantidad de clientes y saldo bancario medio por ciudad.",
                    sql = "SELECT ciudad, COUNT(*) AS Clientes, ROUND(AVG(saldo), 2) AS Saldo_Medio\nFROM clientes\nGROUP BY ciudad\nORDER BY Clientes DESC;"
                ),
                SqlExample(
                    title = "4. Detalle de Pedidos con Productos (JOIN Múltiple)",
                    category = "Consultas Avanzadas",
                    description = "Relaciona cada pedido con el cliente y los productos despachados.",
                    sql = "SELECT p.id AS Pedido, c.nombre AS Cliente, pr.nombre AS Producto, dp.cantidad, dp.precio_unitario\nFROM pedidos p\nJOIN clientes c ON p.cliente_id = c.id\nJOIN detalles_pedidos dp ON p.id = dp.pedido_id\nJOIN productos pr ON dp.producto_id = pr.id\nLIMIT 20;"
                ),
                SqlExample(
                    title = "Actualización Segura (UPDATE con WHERE)",
                    category = "Solución de Errores",
                    description = "Siempre usa WHERE en UPDATE para no alterar toda la tabla por error.",
                    sql = "-- Siempre especifica el id para no modificar todos los productos\nUPDATE productos\nSET stock = stock + 10\nWHERE id = 1;"
                )
            )

            lower.contains("facultad") -> listOf(
                SqlExample(
                    title = "1. Estudiantes con Promedio Mayor a 90 puntos",
                    category = "Académico",
                    description = "Estudiantes destacados de la Facultad de Ciencias.",
                    sql = "SELECT matricula, nombre, promedio, ano_carrera\nFROM estudiantes\nWHERE promedio >= 90.0\nORDER BY promedio DESC;"
                ),
                SqlExample(
                    title = "2. Claustro Docente y Materias Impartidas (JOIN)",
                    category = "Académico",
                    description = "Relación de asignaturas con sus profesores titulares.",
                    sql = "SELECT m.nombre AS Materia, m.codigo, p.nombre AS Profesor, p.departamento\nFROM materias m\nJOIN profesores p ON m.profesor_id = p.id\nORDER BY p.departamento;"
                ),
                SqlExample(
                    title = "3. Promedio Académico por Año de Carrera",
                    category = "Académico",
                    description = "Resumen de matrícula y rendimiento general por nivel de año.",
                    sql = "SELECT ano_carrera AS Anio, COUNT(*) AS Total_Alumnos, ROUND(AVG(promedio), 2) AS Promedio_General\nFROM estudiantes\nGROUP BY ano_carrera\nORDER BY ano_carrera ASC;"
                ),
                SqlExample(
                    title = "Clave Foránea Correcta",
                    category = "Solución de Errores",
                    description = "Inserción garantizando existencia previa del profesor padre.",
                    sql = "INSERT INTO materias (codigo, nombre, profesor_id, creditos)\nVALUES ('IA-401', 'Aprendizaje Automático', 1, 4);"
                )
            )

            else -> getEducationalExamples() // Default: Otorgamiento Carreras
        }
    }
}
