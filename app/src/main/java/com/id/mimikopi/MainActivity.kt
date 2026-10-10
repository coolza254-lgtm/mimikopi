package com.id.mimikopi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.id.mimikopi.ui.about.AboutScreen
import com.id.mimikopi.ui.diag.DiagnosticsScreen
import com.id.mimikopi.ui.home.HomeScreen
import com.id.mimikopi.ui.theme.MimikopiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MimikopiTheme { AppNavigation() }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
