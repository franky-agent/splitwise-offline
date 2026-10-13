package dev.splitwise.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.splitwise.offline.ui.nav.AppNavHost
import dev.splitwise.offline.ui.theme.SplitwiseOfflineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitwiseOfflineTheme {
                AppNavHost()
            }
        }
    }
}