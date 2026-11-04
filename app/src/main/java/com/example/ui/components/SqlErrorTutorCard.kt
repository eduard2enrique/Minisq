package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SqlBlue
import com.example.ui.theme.SqlSuccess

data class SqlTutorExplanation(
    val categoryBadge: String,
    val title: String,
    val reason: String,
    val solution: String,
    val wrongCodeSnippet: String? = null,
    val fixedCodeSnippet: String? = null,
    val quickFixLabel: String? = null,
    val suggestedFixedQuery: String? = null,
    val openCategory: String = "Solución de Errores"
)

object SqlErrorAnalyzer {

    fun analyze(
        errorMessage: String,
        query: String,
        availableTables: List<String> = emptyList()
    ): SqlTutorExplanation {
        val lowerErr = errorMessage.lowercase()
        val trimmedQuery = query.trim()

        // 1. UNIQUE constraint failed (INSERT repetido o clave duplicada)
        if (lowerErr.contains("unique constraint failed")) {
            val match = Regex("""unique constraint failed:\s*(?:([a-zA-Z0-9_]+)\.)?([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE).find(errorMessage)
            val column = match?.groupValues?.getOrNull(2) ?: "clave_primaria"
            val table = match?.groupValues?.getOrNull(1) ?: "la tabla"

            val targetDesc = "la columna '$column' en '$table'"
            val hasInsert = trimmedQuery.startsWith("insert", ignoreCase = true)
            val canSuggestIgnore = hasInsert && !trimmedQuery.contains("insert or ignore", ignoreCase = true)

            val suggestedSql = if (canSuggestIgnore) {
                trimmedQuery.replaceFirst(Regex("""(?i)INSERT\s+INTO"""), "INSERT OR IGNORE INTO")
            } else null

            return SqlTutorExplanation(
                categoryBadge = "CLAVE DUPLICADA (INSERT REPETIDO)",
                title = "No se pueden insertar registros duplicados",
                reason = "SQLite impidió registrar los datos porque $targetDesc ya contiene ese valor. Ocurre con frecuencia cuando vuelves a ejecutar un INSERT que tiene una clave primaria o restricción UNIQUE fija.",
                solution = "• Para scripts re-ejecutables sin fallos: Usa 'INSERT OR IGNORE INTO...' para omitir duplicados silenciosamente.\n• Para actualizar datos viejos con los nuevos: Usa 'INSERT OR REPLACE INTO...'.\n• Si es clave AUTOINCREMENT: Omite la columna en la lista de campos y SQLite le dará el próximo número libre.",
                wrongCodeSnippet = "INSERT INTO $table ($column, ...) VALUES (1, ...); -- Falla la 2ª vez por duplicado",
                fixedCodeSnippet = "INSERT OR IGNORE INTO $table ($column, ...) VALUES (1, ...); -- O omite '$column' si es autoincremental",
                quickFixLabel = if (suggestedSql != null) "Cambiar a 'INSERT OR IGNORE'" else null,
                suggestedFixedQuery = suggestedSql,
                openCategory = "Solución de Errores"
            )
        }

        // 2. Table already exists (CREATE TABLE repetido)
        if (lowerErr.contains("already exists")) {
            val match = Regex("""table\s+["`]?([a-zA-Z0-9_]+)["`]?\s+already\s+exists""", RegexOption.IGNORE_CASE).find(errorMessage)
            val tableName = match?.groupValues?.getOrNull(1) ?: "esta tabla"

            val canSuggestIfNotExists = trimmedQuery.contains(Regex("""(?i)CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)"""))
            val suggestedSql = if (canSuggestIfNotExists) {
                trimmedQuery.replaceFirst(Regex("""(?i)CREATE\s+TABLE\s+"""), "CREATE TABLE IF NOT EXISTS ")
            } else null

            return SqlTutorExplanation(
                categoryBadge = "TABLA YA EXISTE (DDL)",
                title = "La tabla '$tableName' ya existe en la base",
                reason = "Intentaste ejecutar 'CREATE TABLE $tableName', pero ya existe una tabla con ese nombre en esta base de datos.",
                solution = "• Práctica recomendada: Añade 'IF NOT EXISTS' para que el script no se rompa si la tabla ya fue creada.\n• Si deseas reiniciar la tabla desde cero: Ejecuta primero 'DROP TABLE IF EXISTS $tableName;' antes del CREATE (¡borrará los registros previos!).",
                wrongCodeSnippet = "CREATE TABLE $tableName (...); -- Falla si ya existe en la BD",
                fixedCodeSnippet = "CREATE TABLE IF NOT EXISTS $tableName (...); -- Seguro y re-ejecutable",
                quickFixLabel = if (suggestedSql != null) "Añadir 'IF NOT EXISTS'" else null,
                suggestedFixedQuery = suggestedSql,
                openCategory = "Solución de Errores"
            )
        }

        // 3. Coma sobrante antes de FROM/WHERE (Error de sintaxis común)
        val trailingCommaRegex = Regex(""",\s*(FROM|WHERE|GROUP\s+BY|ORDER\s+BY)""", RegexOption.IGNORE_CASE)
        if (trailingCommaRegex.containsMatchIn(trimmedQuery) || (lowerErr.contains("syntax error") && lowerErr.contains("from"))) {
            val fixedQuery = trailingCommaRegex.replace(trimmedQuery, " $1")
            val hasChanged = fixedQuery != trimmedQuery

            return SqlTutorExplanation(
                categoryBadge = "SINTAXIS (COMA SOBRANTE)",
                title = "Coma sobrante antes de la cláusula",
                reason = "En SQL no debe quedar ninguna coma al final de la lista de columnas antes de la palabra reservada FROM o WHERE.",
                solution = "• Elimina la coma previa al FROM.\n• Asegúrate de que las comas solo separen columnas entre sí.",
                wrongCodeSnippet = "SELECT columna1, columna2, FROM tabla; -- Coma prohibida antes de FROM",
                fixedCodeSnippet = "SELECT columna1, columna2 FROM tabla; -- Sintaxis limpia y correcta",
                quickFixLabel = if (hasChanged) "Quitar coma sobrante" else null,
                suggestedFixedQuery = if (hasChanged) fixedQuery else null,
                openCategory = "Solución de Errores"
            )
        }

        // 4. No such table (Tabla no encontrada con auto-detección inteligente)
        if (lowerErr.contains("no such table")) {
            val match = Regex("""no\s+such\s+table:\s*["`]?([a-zA-Z0-9_]+)["`]?""", RegexOption.IGNORE_CASE).find(errorMessage)
            val tableName = match?.groupValues?.getOrNull(1) ?: "especificada"

            // Buscar coincidencia cercana en la base de datos activa
            val candidate = availableTables.firstOrNull {
                it.equals(tableName, ignoreCase = true) ||
                        it.startsWith(tableName, ignoreCase = true) ||
                        tableName.startsWith(it, ignoreCase = true) ||
                        it.contains(tableName, ignoreCase = true)
            }

            val suggestedSql = if (candidate != null) {
                trimmedQuery.replace(Regex("""\b$tableName\b""", RegexOption.IGNORE_CASE), candidate)
            } else null

            val tablesListStr = if (availableTables.isNotEmpty()) {
                "\n• Tablas disponibles en tu base activa: ${availableTables.joinToString(", ")}"
            } else ""

            return SqlTutorExplanation(
                categoryBadge = "TABLA NO ENCONTRADA",
                title = "No existe la tabla '$tableName'",
                reason = "SQLite no encontró ninguna tabla llamada '$tableName' en la base de datos activa.$tablesListStr",
                solution = if (candidate != null) {
                    "• Detectamos la tabla parecida '$candidate'. Pulsa el botón de abajo para corregir el nombre automáticamente."
                } else {
                    "• Revisa la ortografía en la cláusula FROM o INTO.\n• Revisa la pestaña 'Tablas' para ver las tablas creadas en este proyecto."
                },
                wrongCodeSnippet = "FROM $tableName -- Tabla no registrada",
                fixedCodeSnippet = if (candidate != null) "FROM $candidate -- Tabla existente en la base activa" else null,
                quickFixLabel = if (suggestedSql != null && candidate != null) "Corregir a '$candidate'" else null,
                suggestedFixedQuery = suggestedSql,
                openCategory = "Solución de Errores"
            )
        }

        // 5. No such column (Comillas dobles en vez de simples o columna inexistente)
        if (lowerErr.contains("no such column")) {
            val match = Regex("""no\s+such\s+column:\s*["`]?([a-zA-Z0-9_]+)["`]?""", RegexOption.IGNORE_CASE).find(errorMessage)
            val colName = match?.groupValues?.getOrNull(1) ?: "especificada"

            // Caso típico: WHERE campo = "Texto" en vez de 'Texto'
            val doubleQuoteRegex = Regex("""=\s*"([^"]+)"""")
            val hasDoubleQuoteLiteral = doubleQuoteRegex.containsMatchIn(trimmedQuery)

            val suggestedSql = if (hasDoubleQuoteLiteral) {
                doubleQuoteRegex.replace(trimmedQuery, "= '$1'")
            } else null

            return SqlTutorExplanation(
                categoryBadge = "COLUMNA NO ENCONTRADA (COMILLAS)",
                title = "Columna '$colName' no encontrada",
                reason = if (hasDoubleQuoteLiteral) {
                    "Escribiste un texto entre comillas dobles (\"$colName\"). En SQL estándar, las comillas dobles identifican nombres de columnas. Los valores de texto deben encerrarse en comillas simples ('$colName')."
                } else {
                    "La columna '$colName' no forma parte de la tabla consultada o está mal escrita."
                },
                solution = "• Usa comillas simples ('...') para valores y cadenas literales.\n• Revisa las columnas reales ejecutando 'PRAGMA table_info(nombre_tabla);'.",
                wrongCodeSnippet = "WHERE estado = \"Activo\" -- Comillas dobles buscan columna 'Activo'",
                fixedCodeSnippet = "WHERE estado = 'Activo' -- Comillas simples para valores de texto",
                quickFixLabel = if (suggestedSql != null) "Cambiar a comillas simples" else null,
                suggestedFixedQuery = suggestedSql,
                openCategory = "Solución de Errores"
            )
        }

        // 6. FOREIGN KEY constraint failed (Violación de Integridad Referencial)
        if (lowerErr.contains("foreign key constraint failed")) {
            return SqlTutorExplanation(
                categoryBadge = "CLAVE FORÁNEA (INTEGRIDAD)",
                title = "Violación de Clave Foránea (Foreign Key)",
                reason = "SQLite protegió la integridad referencial: intentaste insertar un registro hijo cuyo ID no existe en la tabla padre (por ejemplo, registrar una boleta para un estudiante inexistente).",
                solution = "• 1°: Inserta primero el registro en la tabla padre antes de referenciarlo.\n• 2°: Verifica que el ID foráneo exista realmente ejecutando un SELECT en la tabla padre.",
                wrongCodeSnippet = "-- Error: insertar en tabla hija con ID padre inexistente\nINSERT INTO boletas_solicitud (id_estudiante, id_carrera, ...) VALUES (999, ...);",
                fixedCodeSnippet = "-- Correcto: verificar primero que el ID exista en la tabla padre\nSELECT id_estudiante FROM estudiantes WHERE id_estudiante = 999;",
                quickFixLabel = null,
                suggestedFixedQuery = null,
                openCategory = "Solución de Errores"
            )
        }

        // 7. NOT NULL constraint failed (Campo obligatorio vacío)
        if (lowerErr.contains("not null constraint failed")) {
            val match = Regex("""not null constraint failed:\s*(?:([a-zA-Z0-9_]+)\.)?([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE).find(errorMessage)
            val colName = match?.groupValues?.getOrNull(2) ?: "requerida"

            return SqlTutorExplanation(
                categoryBadge = "CAMPO OBLIGATORIO (NOT NULL)",
                title = "Falta valor en columna obligatoria '$colName'",
                reason = "La columna '$colName' fue definida como NOT NULL y no tiene un valor por defecto (DEFAULT). La consulta no proporcionó ningún dato para ella.",
                solution = "• Incluye el campo '$colName' con un valor válido en tu sentencia INSERT o UPDATE.",
                wrongCodeSnippet = "INSERT INTO tabla (otro_campo) VALUES ('valor'); -- Falta '$colName'",
                fixedCodeSnippet = "INSERT INTO tabla ($colName, otro_campo) VALUES ('dato', 'valor');",
                quickFixLabel = null,
                suggestedFixedQuery = null,
                openCategory = "Solución de Errores"
            )
        }

        // 8. CHECK constraint failed (Violación de restricción de rango)
        if (lowerErr.contains("check constraint failed")) {
            return SqlTutorExplanation(
                categoryBadge = "RESTRICCIÓN CHECK (RANGO DE VALORES)",
                title = "El valor no cumple la validación CHECK",
                reason = "La tabla tiene una regla de validación (por ejemplo, notas entre 0 y 100, sexo en ('M','F'), o prioridad entre 1 y 10) que fue infringida por el valor enviado.",
                solution = "• Ajusta el valor para que cumpla con los límites permitidos en la definición de la tabla.",
                wrongCodeSnippet = "INSERT INTO estudiantes (indice_academico) VALUES (105.0); -- Fuera de rango",
                fixedCodeSnippet = "INSERT INTO estudiantes (indice_academico) VALUES (95.0); -- Entre 0 y 100",
                quickFixLabel = null,
                suggestedFixedQuery = null,
                openCategory = "Solución de Errores"
            )
        }

        // 9. Error tipográfico en palabras clave
        val typoMap = mapOf(
            Regex("""(?i)\bSELEC\b""") to "SELECT",
            Regex("""(?i)\bWERE\b""") to "WHERE",
            Regex("""(?i)\bWHER\b""") to "WHERE",
            Regex("""(?i)\bINER\s+JOIN\b""") to "INNER JOIN",
            Regex("""(?i)\bLEFT\s+JION\b""") to "LEFT JOIN"
        )
        for ((regex, replacement) in typoMap) {
            if (regex.containsMatchIn(trimmedQuery)) {
                val fixed = regex.replace(trimmedQuery, replacement)
                return SqlTutorExplanation(
                    categoryBadge = "PALABRA CLAVE MAL ESCRITA",
                    title = "Error tipográfico en palabra reservada",
                    reason = "Detectamos una palabra reservada de SQL mal escrita en tu consulta.",
                    solution = "• Cambia la palabra por '$replacement'.",
                    wrongCodeSnippet = trimmedQuery.take(50),
                    fixedCodeSnippet = fixed.take(50),
                    quickFixLabel = "Corregir a '$replacement'",
                    suggestedFixedQuery = fixed,
                    openCategory = "Solución de Errores"
                )
            }
        }

        // Default Fallback
        return SqlTutorExplanation(
            categoryBadge = "CONSEJO DIDÁCTICO SQL",
            title = "¿Cómo interpretar y resolver este error?",
            reason = "SQLite devolvió una excepción durante la ejecución: \"$errorMessage\".",
            solution = "• Revisa que los nombres de tablas y columnas correspondan al esquema de la base activa.\n• Consulta la pestaña 'Solución de Errores' en la Guía de Ejemplos para ver sentencias modelo.",
            wrongCodeSnippet = null,
            fixedCodeSnippet = null,
            quickFixLabel = null,
            suggestedFixedQuery = null,
            openCategory = "Solución de Errores"
        )
    }
}

@Composable
fun SqlErrorTutorCard(
    errorMessage: String,
    currentQuery: String,
    availableTables: List<String> = emptyList(),
    onApplyFix: ((String) -> Unit)? = null,
    onOpenExamples: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val explanation = remember(errorMessage, currentQuery, availableTables) {
        SqlErrorAnalyzer.analyze(errorMessage, currentQuery, availableTables)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sql_tutor_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SqlBlue.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, SqlBlue.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon + Badge + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SqlBlue.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = SqlBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SqlBlue.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = explanation.categoryBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SqlBlue,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "💡 Tutor Didáctico SQL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reason section
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = explanation.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation.reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Comparative Code Blocks: ❌ Error vs ✅ Solución
            if (explanation.wrongCodeSnippet != null || explanation.fixedCodeSnippet != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (explanation.wrongCodeSnippet != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Código con error / común:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = explanation.wrongCodeSnippet,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }

                    if (explanation.fixedCodeSnippet != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SqlSuccess.copy(alpha = 0.09f),
                            border = BorderStroke(1.dp, SqlSuccess.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = SqlSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Solución recomendada:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SqlSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = explanation.fixedCodeSnippet,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Solution guidelines
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "¿Cómo solucionarlo?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SqlSuccess
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation.solution,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions: Apply Fix button and/or Open Examples
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (explanation.quickFixLabel != null && explanation.suggestedFixedQuery != null && onApplyFix != null) {
                    Button(
                        onClick = { onApplyFix(explanation.suggestedFixedQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = SqlBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("sql_tutor_quick_fix_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = explanation.quickFixLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                if (onOpenExamples != null) {
                    OutlinedButton(
                        onClick = { onOpenExamples(explanation.openCategory) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("sql_tutor_examples_btn"),
                        border = BorderStroke(1.dp, SqlBlue.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = SqlBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recetario de Errores",
                            style = MaterialTheme.typography.labelSmall,
                            color = SqlBlue,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
