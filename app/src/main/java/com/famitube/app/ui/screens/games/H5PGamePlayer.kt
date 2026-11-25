package com.famitube.app.ui.screens.games

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class FamiTubeH5PBridge(
    private val onAwardStars: (Int, String) -> Unit,
    private val onCelebration: (Int, String) -> Unit
) {
    @JavascriptInterface
    fun onXAPICompleted(score: Int, maxScore: Int, stars: Int, title: String) {
        onAwardStars(stars, "H5P Quest: $title")
        onCelebration(stars, title)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun H5PActivityViewer(
    contentId: String,
    onAwardStars: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastAwardedTitle by remember { mutableStateOf<String?>(null) }
    var lastAwardedStars by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        // Celebration Banner when completed
        if (lastAwardedTitle != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF06D6A0).copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎉 Star Reward: +$lastAwardedStars ⭐",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF06D6A0),
                        fontSize = 13.sp
                    )
                    Text(
                        text = lastAwardedTitle ?: "",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                }
            }
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(0xFF0F1117.toInt())

                    webViewClient = object : WebViewClient() {}
                    webChromeClient = object : WebChromeClient() {}

                    val bridge = FamiTubeH5PBridge(
                        onAwardStars = { stars, reason ->
                            post { onAwardStars(stars, reason) }
                        },
                        onCelebration = { stars, title ->
                            post {
                                lastAwardedStars = stars
                                lastAwardedTitle = title
                            }
                        }
                    )
                    addJavascriptInterface(bridge, "FamiTubeAndroid")

                    loadUrl("file:///android_asset/h5p/h5p-player.html?id=$contentId")
                }
            },
            update = { webView ->
                // Reload with updated contentId if it changed
                val targetUrl = "file:///android_asset/h5p/h5p-player.html?id=$contentId"
                if (webView.url != targetUrl) {
                    webView.loadUrl(targetUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
