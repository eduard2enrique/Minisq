package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParsedSheet(
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val inferredTypes: List<String>
)

data class ImportResult(
    val isSuccess: Boolean,
    val tableName: String,
    val rowsImported: Int,
    val message: String
)

class SpreadsheetImporter(private val context: Context) {

    suspend fun parseSpreadsheet(uri: Uri): Result<List<ParsedSheet>> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri).lowercase()
            if (fileName.endsWith(".xlsx")) {
                val sheets = parseXlsx(uri)
                if (sheets.isEmpty()) {
                    Result.failure(Exception("No se encontraron hojas válidas en el archivo Excel."))
                } else {
                    Result.success(sheets)
                }
            } else {
                // Treat as CSV / TSV / Delimited text
                val sheet = parseCsv(uri)
                Result.success(listOf(sheet))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "datos.csv"
    }

    fun parseCsv(uri: Uri): ParsedSheet {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("No se pudo abrir el archivo.")

        if (bytes.isEmpty()) {
            throw Exception("El archivo CSV está vacío.")
        }

        // Try decoding as UTF-8; if it contains replacement char '\uFFFD', fallback to ISO-8859-1 (standard Spanish Windows Excel)
        var content = String(bytes, Charsets.UTF_8)
        if (content.contains('\uFFFD')) {
            val isoCandidate = String(bytes, Charset.forName("ISO-8859-1"))
            if (!isoCandidate.contains('\uFFFD')) {
                content = isoCandidate
            }
        }

        // Strip UTF-8 BOM if present
        content = content.removePrefix("\uFEFF").replace("\uFEFF", "")

        val allLines = content.lines().filter { it.isNotBlank() }.take(5000)
        if (allLines.isEmpty()) {
            throw Exception("El archivo CSV no contiene filas de datos.")
        }

        // Auto-detect delimiter from first 5 lines
        val delimiter = detectDelimiter(allLines.take(5))
        val parsedRows = allLines.map { parseCsvLine(it, delimiter) }
        val rawHeaders = parsedRows.firstOrNull() ?: emptyList()
        val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

        val headers = sanitizeHeaders(rawHeaders)
        val inferredTypes = inferColumnTypes(dataRows, headers.size)

        val rawName = getFileName(uri).substringBeforeLast('.')
        val sheetName = sanitizeTableName(rawName)

        return ParsedSheet(
            sheetName = sheetName,
            headers = headers,
            rows = dataRows,
            inferredTypes = inferredTypes
        )
    }

    private fun detectDelimiter(sampleLines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        var bestChar = ','
        var maxCount = -1

        for (c in candidates) {
            val counts = sampleLines.map { line -> countCharOutsideQuotes(line, c) }
            val first = counts.firstOrNull() ?: 0
            if (first > 0 && counts.all { it == first || (it > 0 && Math.abs(it - first) <= 2) }) {
                if (first > maxCount) {
                    maxCount = first
                    bestChar = c
                }
            }
        }
        return bestChar
    }

    private fun countCharOutsideQuotes(line: String, target: Char): Int {
        var count = 0
        var inQuotes = false
        for (ch in line) {
            if (ch == '\"') inQuotes = !inQuotes
            else if (ch == target && !inQuotes) count++
        }
        return count
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++ // Skip escaped quote
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == delimiter && !inQuotes) {
                tokens.add(cleanCsvToken(sb.toString()))
                sb.clear()
            } else {
                sb.append(ch)
            }
            i++
        }
        tokens.add(cleanCsvToken(sb.toString()))
        return tokens
    }

    private fun cleanCsvToken(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        }
        return s
    }

    private fun parseXlsx(uri: Uri): List<ParsedSheet> {
        val sharedStrings = mutableListOf<String>()
        val sheetEntries = mutableMapOf<String, ByteArray>()
        val sheetNames = mutableListOf<Pair<String, String>>() // id to name

        // 1. Read files from XLSX ZIP
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml") {
                        val bytes = zis.readBytes()
                        parseSharedStrings(bytes, sharedStrings)
                    } else if (name == "xl/workbook.xml") {
                        val bytes = zis.readBytes()
                        parseWorkbookSheetNames(bytes, sheetNames)
                    } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        sheetEntries[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val result = mutableListOf<ParsedSheet>()

        // 2. Parse sheet contents
        sheetEntries.entries.forEachIndexed { index, (path, bytes) ->
            val fallbackName = "Hoja_${index + 1}"
            val sheetName = sheetNames.getOrNull(index)?.second ?: fallbackName
            val rows = parseWorksheetXml(bytes, sharedStrings)
            if (rows.isNotEmpty()) {
                val headers = sanitizeHeaders(rows.first())
                val dataRows = if (rows.size > 1) rows.subList(1, rows.size) else emptyList()
                val types = inferColumnTypes(dataRows, headers.size)
                result.add(
                    ParsedSheet(
                        sheetName = sanitizeTableName(sheetName),
                        headers = headers,
                        rows = dataRows,
                        inferredTypes = types
                    )
                )
            }
        }

        return result
    }

    private fun parseSharedStrings(bytes: ByteArray, outList: MutableList<String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            var inT = false
            val currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) currentText.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        else if (parser.name == "si") {
                            outList.add(currentText.toString())
                            currentText.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
    }

    private fun parseWorkbookSheetNames(bytes: ByteArray, outSheets: MutableList<Pair<String, String>>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Hoja"
                    val sheetId = parser.getAttributeValue(null, "sheetId") ?: ""
                    outSheets.add(sheetId to name)
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
    }

    private fun parseWorksheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType

            val currentRow = mutableMapOf<Int, String>()
            var cellType = ""
            var cellRef = ""
            var inV = false
            var inT = false
            var cellVal = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> currentRow.clear()
                            "c" -> {
                                cellRef = parser.getAttributeValue(null, "r") ?: ""
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                cellVal.clear()
                            }
                            "v" -> inV = true
                            "t" -> inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV || inT) cellVal.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inV = false
                            "t" -> inT = false
                            "c" -> {
                                val colIdx = cellRefToColIndex(cellRef)
                                val text = if (cellType == "s") {
                                    val stringIdx = cellVal.toString().toIntOrNull()
                                    if (stringIdx != null && stringIdx in sharedStrings.indices) {
                                        sharedStrings[stringIdx]
                                    } else {
                                        cellVal.toString()
                                    }
                                } else {
                                    cellVal.toString()
                                }
                                currentRow[colIdx] = text
                            }
                            "row" -> {
                                if (currentRow.isNotEmpty()) {
                                    val maxCol = currentRow.keys.maxOrNull() ?: 0
                                    val rowList = (0..maxCol).map { currentRow[it] ?: "" }
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return rows
    }

    private fun cellRefToColIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else break
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun sanitizeHeaders(rawHeaders: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return rawHeaders.mapIndexed { idx, raw ->
            val clean = raw.trim()
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\"", "")
                .replace("'", "")
                .replace(Regex("[^\\p{L}0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifEmpty { "col_${idx + 1}" }

            var unique = clean
            var counter = 1
            while (seen.contains(unique.lowercase())) {
                unique = "${clean}_$counter"
                counter++
            }
            seen.add(unique.lowercase())
            unique
        }
    }

    private fun sanitizeTableName(raw: String): String {
        val clean = raw.trim()
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\"", "")
            .replace("'", "")
            .replace(Regex("[^\\p{L}0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "datos_importados" }
        return clean
    }

    private fun inferColumnTypes(rows: List<List<String>>, numCols: Int): List<String> {
        val types = MutableList(numCols) { "INTEGER" }
        for (col in 0 until numCols) {
            var canBeInt = true
            var canBeReal = true
            var nonNullCount = 0

            for (row in rows.take(200)) {
                val value = row.getOrNull(col)?.trim() ?: ""
                if (value.isNotEmpty() && !value.equals("NULL", ignoreCase = true)) {
                    nonNullCount++
                    // Check integer
                    if (canBeInt && !value.matches(Regex("^-?\\d+$"))) {
                        canBeInt = false
                    }
                    // Check real / float (supporting both dot and European comma: 12.50 or 12,50)
                    val normalizedNum = value.replace(",", ".")
                    if (canBeReal && !normalizedNum.matches(Regex("^-?\\d+(\\.\\d+)?$"))) {
                        canBeReal = false
                    }
                }
            }

            types[col] = when {
                nonNullCount == 0 -> "TEXT"
                canBeInt -> "INTEGER"
                canBeReal -> "REAL"
                else -> "TEXT"
            }
        }
        return types
    }

    suspend fun importSheetToDatabase(
        db: SQLiteDatabase,
        sheet: ParsedSheet,
        tableName: String,
        headersToUse: List<String>,
        typesToUse: List<String>,
        createNewTable: Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        val cleanTableName = sanitizeTableName(tableName)
        try {
            db.beginTransaction()

            if (createNewTable) {
                // DROP TABLE IF EXISTS so that old schema from previous designs is completely replaced
                db.execSQL("DROP TABLE IF EXISTS \"$cleanTableName\";")

                val hasIdColumn = headersToUse.any { it.equals("id", ignoreCase = true) }
                val colDefs = headersToUse.mapIndexed { idx, col ->
                    val type = typesToUse.getOrNull(idx) ?: "TEXT"
                    if (col.equals("id", ignoreCase = true)) {
                        "\"$col\" INTEGER PRIMARY KEY"
                    } else {
                        "\"$col\" $type"
                    }
                }

                val primaryKeyPrefix = if (!hasIdColumn) "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" else ""
                val createSql = "CREATE TABLE IF NOT EXISTS \"$cleanTableName\" (\n" +
                        primaryKeyPrefix +
                        "    " + colDefs.joinToString(",\n    ") + "\n);"
                db.execSQL(createSql)
            } else {
                // Appending to existing table: inspect existing table columns
                val existingCols = mutableSetOf<String>()
                val cursor = db.rawQuery("PRAGMA table_info(\"$cleanTableName\")", null)
                cursor.use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        if (nameIdx != -1) existingCols.add(c.getString(nameIdx).lowercase())
                    }
                }

                if (existingCols.isEmpty()) {
                    // Table did not actually exist, create it
                    val hasIdColumn = headersToUse.any { it.equals("id", ignoreCase = true) }
                    val colDefs = headersToUse.mapIndexed { idx, col ->
                        val type = typesToUse.getOrNull(idx) ?: "TEXT"
                        if (col.equals("id", ignoreCase = true)) "\"$col\" INTEGER PRIMARY KEY" else "\"$col\" $type"
                    }
                    val primaryKeyPrefix = if (!hasIdColumn) "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" else ""
                    val createSql = "CREATE TABLE IF NOT EXISTS \"$cleanTableName\" (\n" +
                            primaryKeyPrefix +
                            "    " + colDefs.joinToString(",\n    ") + "\n);"
                    db.execSQL(createSql)
                } else {
                    // Add any missing columns from CSV via ALTER TABLE
                    for (i in headersToUse.indices) {
                        val colName = headersToUse[i]
                        if (!existingCols.contains(colName.lowercase())) {
                            val colType = typesToUse.getOrNull(i) ?: "TEXT"
                            try {
                                db.execSQL("ALTER TABLE \"$cleanTableName\" ADD COLUMN \"$colName\" $colType;")
                                existingCols.add(colName.lowercase())
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val insertCols = headersToUse.joinToString(", ") { "\"$it\"" }
            val placeholders = headersToUse.joinToString(", ") { "?" }
            val insertSql = "INSERT OR IGNORE INTO \"$cleanTableName\" ($insertCols) VALUES ($placeholders);"
            val stmt = db.compileStatement(insertSql)

            var insertedCount = 0
            for (row in sheet.rows) {
                stmt.clearBindings()
                for (i in headersToUse.indices) {
                    val rawVal = row.getOrNull(i)?.trim() ?: ""
                    if (rawVal.isEmpty() || rawVal.equals("NULL", ignoreCase = true)) {
                        stmt.bindNull(i + 1)
                    } else {
                        val type = typesToUse.getOrNull(i) ?: "TEXT"
                        when (type) {
                            "INTEGER" -> {
                                val cleanNum = rawVal.replace(".", "").replace(",", "").trim()
                                val longVal = rawVal.toLongOrNull() ?: cleanNum.toLongOrNull()
                                if (longVal != null) stmt.bindLong(i + 1, longVal)
                                else stmt.bindString(i + 1, rawVal)
                            }
                            "REAL" -> {
                                val normalizedVal = rawVal.replace(",", ".")
                                val dblVal = rawVal.toDoubleOrNull() ?: normalizedVal.toDoubleOrNull()
                                if (dblVal != null) stmt.bindDouble(i + 1, dblVal)
                                else stmt.bindString(i + 1, rawVal)
                            }
                            else -> stmt.bindString(i + 1, rawVal)
                        }
                    }
                }
                stmt.executeInsert()
                insertedCount++
            }

            db.setTransactionSuccessful()
            ImportResult(
                isSuccess = true,
                tableName = cleanTableName,
                rowsImported = insertedCount,
                message = "Se importaron $insertedCount filas en la tabla '$cleanTableName' exitosamente."
            )
        } catch (e: Exception) {
            ImportResult(
                isSuccess = false,
                tableName = cleanTableName,
                rowsImported = 0,
                message = "Error al importar datos: ${e.localizedMessage ?: e.message}"
            )
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }
}
