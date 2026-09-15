package com.derekwinters.intervaltrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * The launcher activity (BUILD-012, BUILD-018).
 *
 * Hosts a single Compose Navigation graph with exactly one destination: a placeholder with no
 * behaviour of its own. This is the shell every later screen issue adds a real destination to, not
 * a screen — none of the six v1 screens (`docs/spec/screens.md`) exists yet.
 *
 * It wraps nothing in a `MaterialTheme` and reads no `androidx.compose.material3` type: `:app`
 * does not depend on Material 3 (BUILD-017) — ADR 0007 moves that dependency into `:designsystem`,
 * which does not exist yet.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntervalTrainerNavHost()
        }
    }
}

private const val PLACEHOLDER_ROUTE = "placeholder"

/**
 * The empty navigation graph (BUILD-018): one route, [PLACEHOLDER_ROUTE], with nothing behind it
 * yet but a label proving the graph and the activity both stood up.
 */
@Composable
private fun IntervalTrainerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PLACEHOLDER_ROUTE) {
        composable(PLACEHOLDER_ROUTE) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                BasicText(text = "Interval Trainer")
            }
        }
    }
}
