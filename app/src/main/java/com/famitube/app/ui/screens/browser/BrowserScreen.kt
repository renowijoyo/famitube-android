package com.famitube.app.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.famitube.app.data.model.BrowserConfigDto
import com.famitube.app.data.model.BrowserHistoryPostEntry
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.PinkPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLEncoder
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    repository: FamiTubeRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val prefs = repository.getPreferences()

    var config by remember { mutableStateOf<BrowserConfigDto?>(null) }
    var isLoadingConfig by remember { mutableStateOf(true) }
    var configError by remember { mutableStateOf<String?>(null) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var showHomeBookmarks by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }

    var blockedInfo by remember { mutableStateOf<Pair<String, String>?>(null) } // url to domain
    var requestStatusMsg by remember { mutableStateOf<String?>(null) }
    var isRequestingAccess by remember { mutableStateOf(false) }

    var remainingSeconds by remember { mutableIntStateOf(prefs.getRemainingSecondsToday()) }
    val isTimeUp = prefs.dailyLimitMinutes > 0 && remainingSeconds <= 0

    // History batch queue
    val historyQueue = remember { mutableStateListOf<BrowserHistoryPostEntry>() }
    var lastLoggedUrl by remember { mutableStateOf("") }

    fun flushHistory() {
        if (historyQueue.isEmpty()) return
        val batch = historyQueue.toList()
        historyQueue.clear()
        scope.launch {
            repository.recordKidBrowserHistory(batch)
        }
    }

    fun enqueueHistory(url: String, title: String, blocked: Boolean) {
        val cfg = config ?: return
        if (!cfg.browserLogHistory) return
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl == "about:blank") return
        if (!blocked && cleanUrl == lastLoggedUrl) return
        if (!blocked) lastLoggedUrl = cleanUrl
        val domain = extractNormalizedDomain(cleanUrl)
        if (domain.isBlank()) return
        historyQueue.add(
            BrowserHistoryPostEntry(
                url = cleanUrl,
                domain = domain,
                title = title.ifBlank { domain },
                blocked = blocked,
                visitedAt = Instant.now().toString()
            )
        )
        if (historyQueue.size >= 5 || blocked) {
            flushHistory()
        }
    }

    LaunchedEffect(Unit) {
        isLoadingConfig = true
        val res = repository.getKidBrowserConfig()
        res.onSuccess {
            config = it
            isLoadingConfig = false
        }.onFailure {
            configError = it.message ?: "Could not load browser settings"
            isLoadingConfig = false
        }
    }

    // Track screen time while browser is open & flush history periodically
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(1000)
            tick++
            if (!showHomeBookmarks && !isTimeUp && config?.browserEnabled == true) {
                prefs.addUsedWatchTime(1)
                remainingSeconds = prefs.getRemainingSecondsToday()
            }
            if (tick % 15 == 0) {
                flushHistory()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            flushHistory()
            webView?.destroy()
        }
    }

    fun isUrlAllowed(url: String, cfg: BrowserConfigDto): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val domain = extractNormalizedDomain(url)
        if (domain.isBlank()) return false

        // Blocked domains always take precedence
        val isExplicitlyBlocked = cfg.blockedDomains.any { blocked ->
            domain == blocked || domain.endsWith(".$blocked")
        }
        if (isExplicitlyBlocked) return false

        if (cfg.browserAllowlistOnly) {
            return cfg.allowedDomains.any { allowed ->
                domain == allowed || domain.endsWith(".$allowed")
            }
        }
        return true
    }

    fun navigateToUrl(rawInput: String) {
        val cfg = config ?: return
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return
        focusManager.clearFocus()
        blockedInfo = null
        requestStatusMsg = null

        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                // Use Google with SafeSearch enabled
                "https://www.google.com/search?safe=active&q=$encoded"
            }
        }

        if (!isUrlAllowed(targetUrl, cfg)) {
            val domain = extractNormalizedDomain(targetUrl)
            blockedInfo = targetUrl to domain
            showHomeBookmarks = false
            enqueueHistory(targetUrl, "Blocked: $domain", blocked = true)
            return
        }

        showHomeBookmarks = false
        currentUrl = targetUrl
        urlInput = targetUrl
        webView?.loadUrl(targetUrl)
    }

    BackHandler(enabled = !showHomeBookmarks && webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (config?.browserEnabled == true && !isTimeUp) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    if (config?.browserAllowlistOnly == true) "Approved sites only"
                                    else "Search or enter website",
                                    fontSize = 13.sp
                                )
                            },
                            readOnly = config?.browserAllowlistOnly == true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigateToUrl(urlInput) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                    } else {
                        Text("Safe Browser", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        flushHistory()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Browser")
                    }
                },
                actions = {
                    if (config?.browserEnabled == true && !isTimeUp) {
                        IconButton(onClick = {
                            blockedInfo = null
                            showHomeBookmarks = true
                        }) {
                            Icon(Icons.Default.Home, contentDescription = "Bookmarks")
                        }
                        if (!showHomeBookmarks) {
                            IconButton(
                                onClick = { webView?.goBack() },
                                enabled = webView?.canGoBack() == true
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            IconButton(
                                onClick = { webView?.goForward() },
                                enabled = webView?.canGoForward() == true
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                            }
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoadingConfig -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                configError != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(configError ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onBack) { Text("Go Back") }
                    }
                }
                config?.browserEnabled == false -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = PinkPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Browser Disabled",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "A parent has turned off web browsing for this profile.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Back to Videos") }
                    }
                }
                isTimeUp -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = PinkPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Daily Screen Time Complete",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Time to rest your eyes! Ask a parent if you need more time.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Go Back") }
                    }
                }
                else -> {
                    val cfg = config!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isPageLoading && !showHomeBookmarks) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (blockedInfo != null) {
                            val (blockedUrl, blockedDomain) = blockedInfo!!
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Website Blocked",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            blockedDomain,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PinkPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            if (cfg.browserAllowlistOnly)
                                                "This site is not on your parent's approved list yet."
                                            else
                                                "This site is blocked by your parent's safety rules.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        if (requestStatusMsg != null) {
                                            Text(
                                                requestStatusMsg!!,
                                                color = Color(0xFF059669),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        } else {
                                            Button(
                                                enabled = !isRequestingAccess,
                                                onClick = {
                                                    isRequestingAccess = true
                                                    scope.launch {
                                                        val res = repository.requestSiteAccess(blockedUrl, blockedDomain)
                                                        isRequestingAccess = false
                                                        requestStatusMsg = res.fold(
                                                            onSuccess = { "Request sent to parent!" },
                                                            onFailure = { it.message ?: "Could not send request" }
                                                        )
                                                    }
                                                }
                                            ) {
                                                Text(if (isRequestingAccess) "Sending..." else "Ask Parent to Allow")
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        OutlinedButton(onClick = {
                                            blockedInfo = null
                                            showHomeBookmarks = true
                                        }) {
                                            Text("Back to Safe Sites")
                                        }
                                    }
                                }
                            }
                        } else if (showHomeBookmarks) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = if (cfg.browserAllowlistOnly) "Approved Safe Sites" else "Quick Bookmarks",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = buildString {
                                            append(if (cfg.browserAllowlistOnly) "Allowlist Mode" else "Open Web Mode")
                                            if (cfg.browserLogHistory) append(" • Parent History Active")
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                items(cfg.allowedDomains) { domain ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { navigateToUrl("https://$domain") },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(PinkPrimary.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Public,
                                                    contentDescription = null,
                                                    tint = PinkPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = domain,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "https://$domain",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            HardenedBrowserWebView(
                                initialUrl = currentUrl,
                                onWebViewCreated = { webView = it },
                                shouldAllowUrl = { url -> isUrlAllowed(url, cfg) },
                                onBlockedUrl = { url, domain ->
                                    blockedInfo = url to domain
                                    enqueueHistory(url, "Blocked: $domain", blocked = true)
                                },
                                onUrlVisited = { url, title ->
                                    currentUrl = url
                                    urlInput = url
                                    enqueueHistory(url, title, blocked = false)
                                },
                                onProgressChanged = { progress ->
                                    pageProgress = progress
                                    isPageLoading = progress in 1..99
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HardenedBrowserWebView(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    shouldAllowUrl: (String) -> Boolean,
    onBlockedUrl: (String, String) -> Unit,
    onUrlVisited: (String, String) -> Unit,
    onProgressChanged: (Int) -> Unit
) {
    val latestShouldAllow by rememberUpdatedState(shouldAllowUrl)
    val latestOnBlocked by rememberUpdatedState(onBlockedUrl)
    val latestOnVisited by rememberUpdatedState(onUrlVisited)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                // Block file/APK downloads inside kid browser
                setDownloadListener { _, _, _, _, _ -> }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        if (request.isForMainFrame && !latestShouldAllow(url)) {
                            latestOnBlocked(url, extractNormalizedDomain(url))
                            return true
                        }
                        return false
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (!isReload && !url.isNullOrBlank() && latestShouldAllow(url)) {
                            latestOnVisited(url, view?.title ?: extractNormalizedDomain(url))
                        }
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (!url.isNullOrBlank() && !latestShouldAllow(url)) {
                            view?.stopLoading()
                            latestOnBlocked(url, extractNormalizedDomain(url))
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!url.isNullOrBlank() && latestShouldAllow(url)) {
                            latestOnVisited(url, view?.title ?: extractNormalizedDomain(url))
                        }
                    }
                }

                onWebViewCreated(this)
                if (initialUrl.isNotBlank()) {
                    loadUrl(initialUrl)
                }
            }
        }
    )
}

private fun extractNormalizedDomain(rawUrl: String): String {
    return try {
        val withProto = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
        val host = URI(withProto).host ?: ""
        host.lowercase().removePrefix("www.")
    } catch (_: Exception) {
        ""
    }
}
