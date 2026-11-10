package com.example.data

import com.example.model.SqlExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportContentMode {
    FULL_DETAILS,
    DATA_ONLY
}

enum class ReportTextFormat {
    RICH_TEXT,
    WHATSAPP
}

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
    val rows: List<List<String>> = emptyList(),
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

            val lowerName = colName.lowercase().trim()
            val isIdentifierOrCode = lowerName == "id" ||
                lowerName.endsWith("_id") ||
                lowerName.startsWith("id_") ||
                lowerName.contains("carnet") ||
                lowerName.contains("identidad") ||
                lowerName.contains("dni") ||
                lowerName.contains("ci") ||
                lowerName.contains("codigo") ||
                lowerName.contains("code") ||
                lowerName.contains("telefono") ||
                lowerName.contains("phone") ||
                lowerName.contains("movil") ||
                lowerName.contains("folio") ||
                lowerName.contains("tomo") ||
                lowerName.contains("orden") ||
                lowerName.contains("prioridad") ||
                lowerName.contains("opcion") ||
                lowerName.contains("posicion") ||
                lowerName.contains("year") ||
                lowerName.contains("anio") ||
                lowerName.contains("postal")

            val parsedNumbers = nonNullValues.mapNotNull { raw ->
                val normalized = raw.trim().replace(',', '.')
                normalized.toDoubleOrNull()
            }

            val isPureNumber = nonNullValues.isNotEmpty() && parsedNumbers.size == nonNullValues.size
            val isMeasurableMetric = isPureNumber && !isIdentifierOrCode

            if (isMeasurableMetric && parsedNumbers.isNotEmpty()) {
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

        val initialData = QueryReportData(
            dbName = databaseName,
            querySql = result.querySql,
            elapsedMs = result.elapsedMs,
            totalRows = numRows,
            totalColumns = result.columns.size,
            columns = result.columns,
            rows = result.rows,
            columnStats = stats,
            timestampStr = timestamp,
            formattedTextReport = ""
        )

        val defaultText = buildFormattedReport(
            reportData = initialData,
            mode = ReportContentMode.FULL_DETAILS,
            format = ReportTextFormat.RICH_TEXT,
            includeAllRows = false
        )

        return initialData.copy(formattedTextReport = defaultText)
    }

    fun buildFormattedReport(
        reportData: QueryReportData,
        mode: ReportContentMode,
        format: ReportTextFormat,
        includeAllRows: Boolean = false
    ): String {
        val cleanDb = reportData.dbName.removeSuffix(".db")
        val rowLimit = if (includeAllRows) reportData.rows.size else 100
        val rowsToPrint = reportData.rows.take(rowLimit)
        val isTruncated = reportData.rows.size > rowsToPrint.size

        return when (format) {
            ReportTextFormat.RICH_TEXT -> buildRichTextReport(
                reportData = reportData,
                cleanDb = cleanDb,
                rowsToPrint = rowsToPrint,
                isTruncated = isTruncated,
                mode = mode
            )
            ReportTextFormat.WHATSAPP -> buildWhatsAppReport(
                reportData = reportData,
                cleanDb = cleanDb,
                rowsToPrint = rowsToPrint,
                isTruncated = isTruncated,
                mode = mode
            )
        }
    }

    private fun buildRichTextReport(
        reportData: QueryReportData,
        cleanDb: String,
        rowsToPrint: List<List<String>>,
        isTruncated: Boolean,
        mode: ReportContentMode
    ): String {
        val sb = StringBuilder()

        if (mode == ReportContentMode.DATA_ONLY) {
            sb.appendLine("==================================================")
            sb.appendLine("  DATOS DE CONSULTA - $cleanDb")
            sb.appendLine("  Filas: ${reportData.totalRows} | Columnas: ${reportData.totalColumns} | ${reportData.timestampStr}")
            sb.appendLine("==================================================")
            sb.appendLine()
            sb.append(buildAlignedUnicodeTable(reportData.columns, rowsToPrint))
            if (isTruncated) {
                sb.appendLine("... (Mostrando ${rowsToPrint.size} de ${reportData.totalRows} filas)")
            }
            return sb.toString().trimEnd()
        }

        // Modo FULL_DETAILS
        sb.appendLine("==================================================")
        sb.appendLine("           INFORME DE CONSULTA SQL                ")
        sb.appendLine("             MiniSQL Pro Studio                   ")
        sb.appendLine("==================================================")
        sb.appendLine("Base de Datos:    ${reportData.dbName}")
        sb.appendLine("Fecha y Hora:     ${reportData.timestampStr}")
        sb.appendLine("Tiempo Ejecución: ${reportData.elapsedMs} ms")
        sb.appendLine("Filas Devueltas:  ${reportData.totalRows}")
        sb.appendLine("Total Columnas:   ${reportData.totalColumns}")
        sb.appendLine("Estado:           Ejecutado con éxito")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("SENTENCIA SQL:")
        sb.appendLine(reportData.querySql.ifBlank { "-- (Consulta no disponible)" }.trim())
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("RESUMEN ESTADÍSTICO DE COLUMNAS:")

        if (reportData.columnStats.isEmpty()) {
            sb.appendLine("  (Sin columnas)")
        } else {
            reportData.columnStats.forEach { stat ->
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
        if (reportData.columns.isNotEmpty()) {
            sb.append(buildAlignedUnicodeTable(reportData.columns, rowsToPrint))
            if (isTruncated) {
                sb.appendLine("... (Mostrando las primeras ${rowsToPrint.size} de ${reportData.totalRows} filas)")
            }
        } else {
            sb.appendLine("  (Sin datos devueltos)")
        }

        sb.appendLine("==================================================")
        sb.appendLine("Generado con MiniSQL Pro para Android")
        sb.appendLine("==================================================")
        return sb.toString().trimEnd()
    }

    private fun buildWhatsAppReport(
        reportData: QueryReportData,
        cleanDb: String,
        rowsToPrint: List<List<String>>,
        isTruncated: Boolean,
        mode: ReportContentMode
    ): String {
        val sb = StringBuilder()

        if (mode == ReportContentMode.DATA_ONLY) {
            sb.appendLine("📋 *Datos SQL — $cleanDb*")
            sb.appendLine("_${reportData.totalRows} filas • ${reportData.totalColumns} columnas_")
            sb.appendLine()
            sb.appendLine("```")
            sb.append(buildAlignedUnicodeTable(reportData.columns, rowsToPrint).trimEnd())
            sb.appendLine()
            sb.appendLine("```")
            if (isTruncated) {
                sb.appendLine("_Mostrando ${rowsToPrint.size} de ${reportData.totalRows} filas_")
            }
            return sb.toString().trimEnd()
        }

        // Modo FULL_DETAILS en WhatsApp
        sb.appendLine("📊 *INFORME DE CONSULTA SQL*")
        sb.appendLine("*Base de datos:* $cleanDb")
        sb.appendLine("*Fecha:* ${reportData.timestampStr}")
        sb.appendLine("*Resumen:* ${reportData.totalRows} filas • ${reportData.totalColumns} col • ${reportData.elapsedMs} ms")
        sb.appendLine()
        sb.appendLine("*Consulta SQL ejecutada:*")
        sb.appendLine("```sql")
        sb.appendLine(reportData.querySql.ifBlank { "-- (Consulta no disponible)" }.trim())
        sb.appendLine("```")

        if (reportData.columnStats.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("*Resumen estadístico:*")
            reportData.columnStats.forEach { stat ->
                if (stat.isNumeric) {
                    val avgF = String.format(Locale.US, "%.2f", stat.avg ?: 0.0)
                    val sumF = String.format(Locale.US, "%.2f", stat.sum ?: 0.0)
                    val minF = String.format(Locale.US, "%.2f", stat.min ?: 0.0)
                    val maxF = String.format(Locale.US, "%.2f", stat.max ?: 0.0)
                    sb.appendLine("• *${stat.name}*: Mín `$minF` | Máx `$maxF` | Prom `$avgF` | Suma `$sumF`")
                } else {
                    sb.appendLine("• *${stat.name}*: ${stat.distinctCount} únicos • ${stat.nullCount} nulos")
                }
            }
        }

        sb.appendLine()
        sb.appendLine("*Datos devueltos:*")
        if (reportData.columns.isNotEmpty()) {
            sb.appendLine("```")
            sb.append(buildAlignedUnicodeTable(reportData.columns, rowsToPrint).trimEnd())
            sb.appendLine()
            sb.appendLine("```")
            if (isTruncated) {
                sb.appendLine("_Mostrando las primeras ${rowsToPrint.size} de ${reportData.totalRows} filas_")
            }
        } else {
            sb.appendLine("_(Sin datos devueltos)_")
        }

        sb.appendLine()
        sb.appendLine("_Generado con MiniSQL Pro_")
        return sb.toString().trimEnd()
    }

    /**
     * Genera una tabla ASCII/Unicode perfectamente alineada en ancho fijo por columna.
     */
    private fun buildAlignedUnicodeTable(
        columns: List<String>,
        rows: List<List<String>>
    ): String {
        if (columns.isEmpty()) return "  (Sin columnas)\n"

        val maxColWidth = 36
        fun sanitizeCell(raw: String): String {
            val singleLine = raw.replace("\r", " ").replace("\n", " ").trim()
            return if (singleLine.length > maxColWidth) {
                singleLine.take(maxColWidth - 1) + "…"
            } else {
                singleLine
            }
        }

        val indexWidth = maxOf(1, rows.size.toString().length)
        val colWidths = columns.indices.map { colIdx ->
            val headerLen = sanitizeCell(columns[colIdx]).length
            val maxDataLen = rows.maxOfOrNull { row ->
                sanitizeCell(row.getOrNull(colIdx) ?: "").length
            } ?: 0
            maxOf(headerLen, maxDataLen).coerceIn(1, maxColWidth)
        }

        val allWidths = listOf(indexWidth) + colWidths

        fun buildBorder(left: String, mid: String, right: String): String {
            return allWidths.joinToString(
                separator = mid,
                prefix = left,
                postfix = right
            ) { width -> "─".repeat(width + 2) }
        }

        fun buildRow(cells: List<String>): String {
            val formatted = allWidths.indices.map { idx ->
                val text = cells.getOrNull(idx) ?: ""
                " " + text.padEnd(allWidths[idx]) + " "
            }
            return formatted.joinToString(separator = "│", prefix = "│", postfix = "│")
        }

        val sb = StringBuilder()
        sb.appendLine(buildBorder("┌", "┬", "┐"))
        sb.appendLine(buildRow(listOf("#") + columns.map { sanitizeCell(it) }))
        sb.appendLine(buildBorder("├", "┼", "┤"))

        if (rows.isEmpty()) {
            val emptyCells = listOf("-") + columns.map { "" }
            sb.appendLine(buildRow(emptyCells))
        } else {
            rows.forEachIndexed { idx, row ->
                val rowCells = listOf((idx + 1).toString()) + columns.indices.map { colIdx ->
                    sanitizeCell(row.getOrNull(colIdx) ?: "")
                }
                sb.appendLine(buildRow(rowCells))
            }
        }

        sb.appendLine(buildBorder("└", "┴", "┘"))
        return sb.toString()
    }
}
