package io.mikoshift.natsu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.mikoshift.natsu.ui.shell.NatsuAppShell
import io.mikoshift.natsu.ui.theme.NatsuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as NatsuApplication).appContainer
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
