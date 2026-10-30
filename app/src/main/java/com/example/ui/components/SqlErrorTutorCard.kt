package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.material3.TextButton
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
    val quickFixLabel: String? = null,
    val suggestedFixedQuery: String? = null
)

object SqlErrorAnalyzer {

    fun analyze(errorMessage: String, query: String): SqlTutorExplanation {
        val lowerErr = errorMessage.lowercase()

        // 1. UNIQUE constraint failed
        if (lowerErr.contains("unique constraint failed")) {
            val match = Regex("""unique constraint failed:\s*(?:([a-zA-Z0-9_]+)\.)?([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE).find(errorMessage)
            val column = match?.groupValues?.getOrNull(2) ?: "clave"
            val table = match?.groupValues?.getOrNull(1)

            val targetDesc = if (table != null) "la columna '$column' de la tabla '$table'" else "la columna '$column'"
            val hasInsert = query.trim().startsWith("insert", ignoreCase = true)
            val canSuggestIgnore = hasInsert && !query.contains("insert or ignore", ignoreCase = true)

            val suggestedSql = if (canSuggestIgnore) {
                query.replaceFirst(Regex("""(?i)INSERT\s+INTO"""), "INSERT OR IGNORE INTO")
            } else null

            return SqlTutorExplanation(
                categoryBadge = "CLAVE DUPLICADA (UNIQUE / PRIMARY KEY)",
                title = "¿Por qué falló la consulta?",
                reason = "SQLite impidió registrar los datos porque $targetDesc ya contiene ese valor. En bases de datos relacionales, las claves primarias (PRIMARY KEY) y restricciones UNIQUE prohíben datos repetidos para garantizar que cada registro sea único e identificable.",
                solution = "• Para scripts re-ejecutables: Utiliza 'INSERT OR IGNORE INTO...' para omitir el duplicado sin error, o 'REPLACE INTO...' (UPSERT) para sobreescribir la fila.\n• Para volver a ejecutar la prueba: Cambia el ID o valor único por uno nuevo.\n• Si la columna es AUTOINCREMENT: Puedes omitir la columna 'id' en el INSERT y SQLite le asignará el próximo número disponible.",
                quickFixLabel = if (suggestedSql != null) "Usar 'INSERT OR IGNORE'" else null,
                suggestedFixedQuery = suggestedSql
            )
        }

        // 2. Table already exists
        if (lowerErr.contains("already exists")) {
            val match = Regex("""table\s+["`]?([a-zA-Z0-9_]+)["`]?\s+already\s+exists""", RegexOption.IGNORE_CASE).find(errorMessage)
            val tableName = match?.groupValues?.getOrNull(1) ?: "esta tabla"

            val canSuggestIfNotExists = query.contains(Regex("""(?i)CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)"""))
            val suggestedSql = if (canSuggestIfNotExists) {
                query.replaceFirst(Regex("""(?i)CREATE\s+TABLE\s+"""), "CREATE TABLE IF NOT EXISTS ")
            } else null

            return SqlTutorExplanation(
                categoryBadge = "TABLA YA EXISTE (DDL)",
                title = "La tabla ya está creada en la base de datos",
                reason = "Intentaste crear '$tableName', pero ya existe una tabla con ese nombre en este proyecto SQLite.",
                solution = "• Estándar recomendado: Añade 'IF NOT EXISTS' a tu sentencia: CREATE TABLE IF NOT EXISTS $tableName (...).\n• Si quieres reiniciar la tabla desde cero: Ejecuta primero 'DROP TABLE IF EXISTS $tableName;' (¡Cuidado!: esto borrará sus registros).",
                quickFixLabel = if (suggestedSql != null) "Añadir 'IF NOT EXISTS'" else null,
                suggestedFixedQuery = suggestedSql
            )
        }

        // 3. No such table
        if (lowerErr.contains("no such table")) {
            val match = Regex("""no\s+such\s+table:\s*["`]?([a-zA-Z0-9_]+)["`]?""", RegexOption.IGNORE_CASE).find(errorMessage)
            val tableName = match?.groupValues?.getOrNull(1) ?: "especificada"

            return SqlTutorExplanation(
                categoryBadge = "TABLA NO ENCONTRADA",
                title = "SQLite no encuentra la tabla '$tableName'",
                reason = "No existe ninguna tabla llamada '$tableName' en la base de datos activa.",
                solution = "• Revisa la ortografía exacta en la cláusula FROM o INTO.\n• Verifica que estés en el proyecto correcto desde el selector de bases de datos.\n• Consulta la pestaña 'Tablas' para inspeccionar qué tablas existen.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // 4. No such column
        if (lowerErr.contains("no such column")) {
            val match = Regex("""no\s+such\s+column:\s*["`]?([a-zA-Z0-9_]+)["`]?""", RegexOption.IGNORE_CASE).find(errorMessage)
            val colName = match?.groupValues?.getOrNull(1) ?: "especificada"

            return SqlTutorExplanation(
                categoryBadge = "COLUMNA NO ENCONTRADA",
                title = "Columna '$colName' inexistente",
                reason = "La columna '$colName' no forma parte de la tabla consultada o se escribió con comillas dobles en vez de simples.",
                solution = "• En SQL, los textos y cadenas deben encerrarse en comillas simples: WHERE estado = 'Activo'.\n• Las comillas dobles (\"nombre\") identifican nombres de columnas o tablas.\n• Revisa la estructura ejecutando 'PRAGMA table_info(nombre_tabla);'.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // 5. FOREIGN KEY constraint failed
        if (lowerErr.contains("foreign key constraint failed")) {
            return SqlTutorExplanation(
                categoryBadge = "INTEGRIDAD REFERENCIAL (FOREIGN KEY)",
                title = "Violación de Clave Foránea",
                reason = "SQLite protegió la integridad relacional de la base de datos: intentaste insertar un registro hijo cuyo ID no existe en la tabla padre, o intentaste eliminar una fila padre que aún tiene hijos vinculados.",
                solution = "• Primero inserta el registro en la tabla padre (ej. cliente o categoría) antes de referenciarlo en la tabla dependiente (ej. pedido o producto).\n• O verifica que el ID foráneo exista realmente en la tabla referenciada.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // 6. NOT NULL constraint failed
        if (lowerErr.contains("not null constraint failed")) {
            val match = Regex("""not null constraint failed:\s*(?:([a-zA-Z0-9_]+)\.)?([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE).find(errorMessage)
            val colName = match?.groupValues?.getOrNull(2) ?: "requerida"

            return SqlTutorExplanation(
                categoryBadge = "CAMPO OBLIGATORIO (NOT NULL)",
                title = "Falta valor en columna obligatoria '$colName'",
                reason = "La columna fue configurada con la restricción NOT NULL y la consulta intentó guardar un valor vacío o nulo.",
                solution = "• Incluye un valor para '$colName' en la lista de valores de tu INSERT o UPDATE.\n• O modifica la tabla para asignarle un valor DEFAULT.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // 7. CHECK constraint failed
        if (lowerErr.contains("check constraint failed")) {
            return SqlTutorExplanation(
                categoryBadge = "RESTRICCIÓN LÓGICA (CHECK)",
                title = "Validación de Datos no Cumplida",
                reason = "El valor enviado no cumple la condición de validación definida en la tabla (por ejemplo, notas entre 0 y 100, o estados permitidos en una lista).",
                solution = "• Revisa los valores permitidos en la definición de la tabla.\n• Asegúrate de ingresar un valor que cumpla con la regla CHECK.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // 8. Syntax error
        if (lowerErr.contains("syntax error") || lowerErr.contains("near \"") || lowerErr.contains("near '")) {
            return SqlTutorExplanation(
                categoryBadge = "SINTAXIS SQL",
                title = "Error de Sintaxis en la Sentencia",
                reason = "El motor SQLite no pudo analizar la consulta debido a un error gramatical en las palabras clave, comas o comillas.",
                solution = "• Revisa comas sobrantes al final de una lista de columnas (error muy frecuente).\n• Comprueba que todas las comillas y paréntesis estén balanceados.\n• Verifica el orden de cláusulas: SELECT ... FROM ... WHERE ... GROUP BY ... ORDER BY.",
                quickFixLabel = null,
                suggestedFixedQuery = null
            )
        }

        // Default Fallback
        return SqlTutorExplanation(
            categoryBadge = "CONSEJO DIDÁCTICO SQL",
            title = "¿Cómo interpretar y resolver este error?",
            reason = "SQLite devolvió una excepción durante la ejecución: \"$errorMessage\".",
            solution = "• Revisa que los nombres de tablas y columnas correspondan al esquema de la base activa.\n• Consulta la sección de Ejemplos Didácticos para comparar con sentencias probadas.",
            quickFixLabel = null,
            suggestedFixedQuery = null
        )
    }
}

@Composable
fun SqlErrorTutorCard(
    errorMessage: String,
    currentQuery: String,
    onApplyFix: ((String) -> Unit)? = null,
    onOpenExamples: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val explanation = remember(errorMessage, currentQuery) {
        SqlErrorAnalyzer.analyze(errorMessage, currentQuery)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Solution section
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
                            .height(36.dp)
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
                        onClick = onOpenExamples,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(36.dp)
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
                            text = "Guía de Ejemplos",
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
