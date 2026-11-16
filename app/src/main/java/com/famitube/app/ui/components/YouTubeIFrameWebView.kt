package com.famitube.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeIFramePlayer(
    videoId: String,
    controls: Boolean = true,
    startSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(videoId, startSeconds, controls, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val origin = "https://${wv.context.packageName}"
        val html = buildYouTubeEmbedHtml(
            videoId = videoId,
            origin = origin,
            controls = if (controls) 1 else 0,
            startSeconds = startSeconds.coerceAtLeast(0)
        )
        wv.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val host = request?.url?.host?.lowercase() ?: return true
                        // Keep playback sandboxed inside FamiTube; block external YouTube navigation
                        val allowed = host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("googlevideo.com") ||
                            host.endsWith("ytimg.com") ||
                            host == ctx.packageName.lowercase()
                        return !allowed
                    }
                }
                webViewRef = this
                val origin = "https://${ctx.packageName}"
                val html = buildYouTubeEmbedHtml(
                    videoId = videoId,
                    origin = origin,
                    controls = if (controls) 1 else 0,
                    startSeconds = startSeconds.coerceAtLeast(0)
                )
                loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
            if (webViewRef === view) webViewRef = null
        },
        modifier = modifier
    )
}

private fun buildYouTubeEmbedHtml(
    videoId: String,
    origin: String,
    controls: Int,
    startSeconds: Int
): String {
    val safeVideoId = Uri.encode(videoId)
    val safeOrigin = Uri.encode(origin)
    val embedSrc =
        "https://www.youtube.com/embed/$safeVideoId" +
            "?autoplay=1&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3" +
            "&controls=$controls&start=$startSeconds&enablejsapi=1&origin=$safeOrigin"
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
            iframe { width: 100%; height: 100%; border: 0; display: block; }
          </style>
        </head>
        <body>
          <iframe
            src="$embedSrc"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
}
