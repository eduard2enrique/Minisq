package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.DatabaseManager
import com.example.data.QueryRepository
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var databaseManager: DatabaseManager
    private lateinit var queryRepository: QueryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        databaseManager = DatabaseManager(applicationContext)
        queryRepository = QueryRepository(applicationContext)

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    dbManager = databaseManager,
                    queryRepo = queryRepository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}
