package com.famitube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.famitube.app.ui.navigation.FamiTubeNavGraph
import com.famitube.app.ui.theme.FamiTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FamiTubeApp
        val repository = app.repository

        setContent {
            FamiTubeTheme {
                val navController = rememberNavController()
                FamiTubeNavGraph(
                    navController = navController,
                    repository = repository
                )
            }
        }
    }
}
