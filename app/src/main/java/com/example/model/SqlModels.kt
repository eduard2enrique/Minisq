package com.example.model

data class SqlExecutionResult(
    val isSuccess: Boolean,
    val message: String,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val affectedRows: Int = 0,
    val elapsedMs: Long = 0,
    val querySql: String = "",
    val isQuery: Boolean = false
)

data class TableSummary(
    val name: String,
    val rowCount: Long = 0,
    val columnCount: Int = 0
)

data class ColumnInfo(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean = false,
    val defaultValue: String? = null,
    val isPrimaryKey: Boolean = false
)

data class ForeignKeyInfo(
    val id: Int,
    val fromColumn: String,
    val targetTable: String,
    val toColumn: String
)

data class TableSchemaDetail(
    val tableName: String,
    val columns: List<ColumnInfo>,
    val foreignKeys: List<ForeignKeyInfo> = emptyList(),
    val indexes: List<String> = emptyList(),
    val createSql: String = ""
)

data class QueryHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sql: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val elapsedMs: Long,
    val rowsCount: Int = 0
)

data class SavedQueryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val sql: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class EntityPosition(
    val tableName: String,
    val x: Float,
    val y: Float
)

data class SqlExample(
    val title: String,
    val category: String,
    val description: String,
    val sql: String
)
