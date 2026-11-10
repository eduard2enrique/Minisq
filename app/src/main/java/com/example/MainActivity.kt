package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.data.DatabaseManager
import com.example.data.QueryRepository
import com.example.ui.MainScreen
import com.example.ui.components.SqlSplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var databaseManager: DatabaseManager
    private lateinit var queryRepository: QueryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        databaseManager = DatabaseManager(applicationContext)
        queryRepository = QueryRepository(applicationContext)

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            var isSplashVisible by rememberSaveable { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Crossfade(
                    targetState = isSplashVisible,
                    animationSpec = tween(durationMillis = 400),
                    label = "splash_crossfade"
                ) { showingSplash ->
                    if (showingSplash) {
                        SqlSplashScreen(
                            onAnimationFinished = { isSplashVisible = false }
                        )
                    } else {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::databaseManager.isInitialized) {
            databaseManager.closeDatabase()
        }
    }
}
