package io.mikoshift.natsu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.mikoshift.natsu.di.AppContainer
import io.mikoshift.natsu.ui.shell.NatsuAppShell
import io.mikoshift.natsu.ui.theme.NatsuTheme

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        enableEdgeToEdge()
        setContent {
            NatsuTheme {
                val navController = rememberNavController()
                NatsuAppShell(
                    appContainer = appContainer,
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
