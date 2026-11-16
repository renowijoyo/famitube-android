package com.famitube.app

import android.content.Intent
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
        handleOAuthIntent(intent, repository)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as FamiTubeApp
        handleOAuthIntent(intent, app.repository)
    }

    private fun handleOAuthIntent(intent: Intent?, repository: com.famitube.app.data.repository.FamiTubeRepository) {
        val data = intent?.data ?: return
        if (data.scheme == "famitube" && data.host == "auth-callback") {
            repository.emitOAuthCallback(data)
        }
    }
}
