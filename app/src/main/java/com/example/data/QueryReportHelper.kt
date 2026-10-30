package com.example.data

import com.example.model.SqlExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ColumnStat(
    val name: String,
    val isNumeric: Boolean,
    val totalCount: Int,
    val nullCount: Int,
    val distinctCount: Int,
    val min: Double? = null,
    val max: Double? = null,
    val sum: Double? = null,
    val avg: Double? = null
)

data class QueryReportData(
    val dbName: String,
    val querySql: String,
    val elapsedMs: Long,
    val totalRows: Int,
    val totalColumns: Int,
    val columns: List<String>,
    val columnStats: List<ColumnStat>,
    val timestampStr: String,
    val formattedTextReport: String
)

object QueryReportHelper {

    fun analyzeAndBuildReport(
        result: SqlExecutionResult,
        dbName: String? = null
    ): QueryReportData {
        val databaseName = if (dbName.isNullOrBlank()) "Base Activa" else dbName
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        val stats = mutableListOf<ColumnStat>()
        val numRows = result.rows.size

        result.columns.forEachIndexed { colIdx, colName ->
            val values = result.rows.mapNotNull { row -> row.getOrNull(colIdx) }
            val nonNullValues = values.filter { it != "NULL" && it.isNotBlank() }
            val nullCount = values.size - nonNullValues.size
            val distinctCount = nonNullValues.distinct().size

            // Try parsing numbers (supports period and comma decimal points)
            val parsedNumbers = nonNullValues.mapNotNull { raw ->
                val normalized = raw.trim().replace(',', '.')
                normalized.toDoubleOrNull()
            }

            val isNumeric = nonNullValues.isNotEmpty() && parsedNumbers.size == nonNullValues.size

            if (isNumeric && parsedNumbers.isNotEmpty()) {
                val minVal = parsedNumbers.minOrNull() ?: 0.0
                val maxVal = parsedNumbers.maxOrNull() ?: 0.0
                val sumVal = parsedNumbers.sum()
                val avgVal = sumVal / parsedNumbers.size

                stats.add(
                    ColumnStat(
                        name = colName,
                        isNumeric = true,
                        totalCount = values.size,
                        nullCount = nullCount,
                        distinctCount = distinctCount,
                        min = minVal,
                        max = maxVal,
                        sum = sumVal,
                        avg = avgVal
                    )
                )
            } else {
                stats.add(
                    ColumnStat(
                        name = colName,
                        isNumeric = false,
                        totalCount = values.size,
                        nullCount = nullCount,
                        distinctCount = distinctCount
                    )
                )
            }
        }

        // Build full formatted text report
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("           INFORME DE CONSULTA SQL               ")
        sb.appendLine("             MiniSQL Pro Studio                  ")
        sb.appendLine("==================================================")
        sb.appendLine("Base de Datos:    $databaseName")
        sb.appendLine("Fecha y Hora:     $timestamp")
        sb.appendLine("Tiempo Ejecución: ${result.elapsedMs} ms")
        sb.appendLine("Filas Devueltas:  $numRows")
        sb.appendLine("Total Columnas:   ${result.columns.size}")
        sb.appendLine("Estado:           Ejecutado con éxito")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("SENTENCIA SQL:")
        sb.appendLine(result.querySql.ifBlank { "-- (Consulta no disponible)" })
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("RESUMEN ESTADÍSTICO DE COLUMNAS:")

        if (stats.isEmpty()) {
            sb.appendLine("  (Sin columnas)")
        } else {
            stats.forEach { stat ->
                if (stat.isNumeric) {
                    val avgFormatted = String.format(Locale.US, "%.2f", stat.avg ?: 0.0)
                    val sumFormatted = String.format(Locale.US, "%.2f", stat.sum ?: 0.0)
                    val minFormatted = String.format(Locale.US, "%.2f", stat.min ?: 0.0)
                    val maxFormatted = String.format(Locale.US, "%.2f", stat.max ?: 0.0)
                    sb.appendLine("• ${stat.name} [NUMÉRICA]:")
                    sb.appendLine("    Mínimo: $minFormatted | Máximo: $maxFormatted")
                    sb.appendLine("    Promedio: $avgFormatted | Suma Total: $sumFormatted")
                    sb.appendLine("    Valores únicos: ${stat.distinctCount} | Nulos: ${stat.nullCount}")
                } else {
                    sb.appendLine("• ${stat.name} [TEXTO]:")
                    sb.appendLine("    Valores únicos: ${stat.distinctCount} | Nulos: ${stat.nullCount}")
                }
            }
        }

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("DATOS DEVUELTOS (TABLA):")
        if (result.columns.isNotEmpty()) {
            sb.append("| # | ")
            sb.append(result.columns.joinToString(" | "))
            sb.appendLine(" |")
            sb.append("|---|")
            sb.append(result.columns.joinToString("|") { "---" })
            sb.appendLine("|")

            val rowsToPrint = result.rows.take(100)
            rowsToPrint.forEachIndexed { idx, row ->
                sb.append("| ${idx + 1} | ")
                sb.append(row.joinToString(" | "))
                sb.appendLine(" |")
            }

            if (result.rows.size > 100) {
                sb.appendLine("... (Mostrando las primeras 100 de ${result.rows.size} filas)")
            }
        } else {
            sb.appendLine("  (Sin datos devueltos)")
        }

        sb.appendLine("==================================================")
        sb.appendLine("Generado con MiniSQL Pro para Android")
        sb.appendLine("==================================================")

        return QueryReportData(
            dbName = databaseName,
            querySql = result.querySql,
            elapsedMs = result.elapsedMs,
            totalRows = numRows,
            totalColumns = result.columns.size,
            columns = result.columns,
            columnStats = stats,
            timestampStr = timestamp,
            formattedTextReport = sb.toString()
        )
    }
}
