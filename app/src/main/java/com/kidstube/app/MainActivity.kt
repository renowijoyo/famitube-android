package com.kidstube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.kidstube.app.ui.navigation.KidsTubeNavGraph
import com.kidstube.app.ui.theme.KidsTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as KidsTubeApp
        val repository = app.repository

        setContent {
            KidsTubeTheme {
                val navController = rememberNavController()
                KidsTubeNavGraph(
                    navController = navController,
                    repository = repository
                )
            }
        }
    }
}
