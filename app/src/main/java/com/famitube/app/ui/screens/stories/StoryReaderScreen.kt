package com.famitube.app.ui.screens.stories

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.famitube.app.data.model.StoryBookDto
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryReaderScreen(
    repository: FamiTubeRepository,
    storyId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()

    var book by remember { mutableStateOf<StoryBookDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var readingMode by remember {
        mutableStateOf(if (ageFilter == "TODDLER") "FLIP" else "READ_ALONG")
    }
    var pageIndex by remember { mutableIntStateOf(0) }
    val completedPages = remember { mutableStateListOf<Int>() }

    var currentWordIdx by remember { mutableIntStateOf(0) }
    var stuckAttempts by remember { mutableIntStateOf(0) }
    var lastTranscript by remember { mutableStateOf("") }
    var hintMessage by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    var showCompleteDialog by remember { mutableStateOf(false) }
    var awardedFamibux by remember { mutableIntStateOf(0) }
    var alreadyRewardedToday by remember { mutableStateOf(false) }

    // Text-to-Speech setup
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun speakText(text: String, lang: String) {
        if (!ttsReady || text.isBlank()) return
        val locale = if (lang == "id") Locale("id", "ID") else Locale.US
        tts.language = locale
        tts.setSpeechRate(0.88f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story_${System.currentTimeMillis()}")
    }

    LaunchedEffect(storyId) {
        isLoading = true
        val res = repository.getStoryById(storyId)
        book = res.getOrNull()
        isLoading = false
    }

    val currentBook = book
    val pages = currentBook?.pages.orEmpty()
    val currentPage = pages.getOrNull(pageIndex)
    val pageWords = remember(currentPage?.text) {
        StorySpeechMatcher.tokenize(currentPage?.text.orEmpty())
    }
    val isPageUnlocked = readingMode != "READ_ALONG" ||
        pageIndex in completedPages ||
        pageWords.isEmpty() ||
        currentWordIdx >= pageWords.size
    val isLastPage = pages.isNotEmpty() && pageIndex >= pages.lastIndex

    // SpeechRecognizer setup (Main thread)
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun stopMic() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun startMic() {
        val b = currentBook ?: return
        if (speechRecognizer == null) {
            hintMessage = "Speech recognition unavailable on this device. Tap Practice Word below."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (b.language == "id") "id-ID" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isListening = true
        hintMessage = "🎤 Listening... Read each highlighted word aloud!"
        try {
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            hintMessage = "Could not start microphone."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMic()
        } else {
            hintMessage = "Microphone permission is needed for Read-Along."
        }
    }

    fun handleSpeechInput(transcript: String, isFinal: Boolean) {
        val b = currentBook ?: return
        if (pageWords.isEmpty() || currentWordIdx >= pageWords.size) return
        lastTranscript = transcript
        val result = StorySpeechMatcher.matchTranscript(pageWords, currentWordIdx, transcript)
        if (result.advanced) {
            currentWordIdx = result.nextWordIdx
            stuckAttempts = 0
            if (currentWordIdx >= pageWords.size) {
                if (pageIndex !in completedPages) completedPages.add(pageIndex)
                stopMic()
                hintMessage = "🎉 Awesome reading! Page unlocked!"
            } else {
                hintMessage = ""
            }
        } else if (isFinal) {
            stuckAttempts += 1
            if (stuckAttempts >= 3) {
                val stuckWord = pageWords[currentWordIdx]
                speakText(stuckWord, b.language)
                currentWordIdx += 1
                stuckAttempts = 0
                hintMessage = "⭐ Nice try! \"$stuckWord\" got a helper boost."
                if (currentWordIdx >= pageWords.size) {
                    if (pageIndex !in completedPages) completedPages.add(pageIndex)
                    stopMic()
                }
            } else {
                hintMessage = "Try \"${pageWords[currentWordIdx]}\" ($stuckAttempts/3) — tap word to hear!"
            }
        }
    }

    DisposableEffect(speechRecognizer, pageIndex, readingMode, currentBook?.id) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening && !isPageUnlocked && readingMode == "READ_ALONG") {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        handleSpeechInput("", isFinal = true)
                        startMic()
                    } else {
                        isListening = false
                    }
                } else {
                    isListening = false
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty()
                if (best.isNotBlank()) handleSpeechInput(best, isFinal = true)
                if (isListening && !isPageUnlocked && readingMode == "READ_ALONG") {
                    startMic()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty()
                if (best.isNotBlank()) handleSpeechInput(best, isFinal = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            stopMic()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    fun flipToPage(delta: Int) {
        if (delta > 0 && !isPageUnlocked) {
            Toast.makeText(context, "Read all words aloud to unlock the next page!", Toast.LENGTH_SHORT).show()
            hintMessage = "🔒 Read all words aloud to unlock the next page!"
            return
        }
        val next = pageIndex + delta
        if (next !in pages.indices) return
        stopMic()
        tts.stop()
        pageIndex = next
        val nextWords = StorySpeechMatcher.tokenize(pages[next].text)
        currentWordIdx = if (next in completedPages) nextWords.size else 0
        stuckAttempts = 0
        lastTranscript = ""
        hintMessage = ""
    }

    fun finishStory() {
        val b = currentBook ?: return
        if (!isPageUnlocked) {
            Toast.makeText(context, "Read this page aloud to finish the story!", Toast.LENGTH_SHORT).show()
            return
        }
        stopMic()
        tts.stop()
        scope.launch {
            val res = repository.completeStory(b.id, readingMode, b.rewardFamibux)
            val data = res.getOrNull()
            awardedFamibux = data?.awardedFamibux ?: b.rewardFamibux
            alreadyRewardedToday = data?.alreadyRewardedToday ?: false
            showCompleteDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentBook?.title ?: "Storybook",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopMic()
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (currentBook == null || currentPage == null) {
                Text(
                    text = "Storybook could not be loaded.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var dragAccum by remember { mutableFloatStateOf(0f) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .pointerInput(pageIndex, isPageUnlocked, readingMode) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccum = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                                onDragEnd = {
                                    if (dragAccum < -90f) flipToPage(1)
                                    else if (dragAccum > 90f) flipToPage(-1)
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mode Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = readingMode == "FLIP",
                            onClick = {
                                stopMic()
                                readingMode = "FLIP"
                                hintMessage = ""
                            },
                            label = { Text("📖 Story Flip", fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = readingMode == "READ_ALONG",
                            onClick = {
                                stopMic()
                                readingMode = "READ_ALONG"
                                currentWordIdx = if (pageIndex in completedPages) pageWords.size else 0
                                stuckAttempts = 0
                                hintMessage = ""
                            },
                            label = { Text("🎤 Read-Along", fontWeight = FontWeight.Bold) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Page ${pageIndex + 1} / ${pages.size}",
                            fontWeight = FontWeight.ExtraBold,
                            color = BlueSecondary
                        )
                    }

                    // Flip-Book Page Card with Animated Transition
                    AnimatedContent(
                        targetState = pageIndex,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "story_page_flip"
                    ) { targetIdx ->
                        val p = pages[targetIdx]
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(Color(0xFF1E293B))
                                ) {
                                    AsyncImage(
                                        model = p.imageUrl.ifBlank { currentBook.coverUrl },
                                        contentDescription = "Page ${targetIdx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color(0x99000000))
                                                )
                                            )
                                    )
                                    if (!p.illustrationEmoji.isNullOrBlank()) {
                                        Text(
                                            text = p.illustrationEmoji,
                                            fontSize = 32.sp,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(14.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pageWords.forEachIndexed { idx, word ->
                                            val isRead = readingMode == "READ_ALONG" && idx < currentWordIdx
                                            val isCurrent = readingMode == "READ_ALONG" && idx == currentWordIdx
                                            val bg = when {
                                                isRead -> MintSuccess.copy(alpha = 0.2f)
                                                isCurrent -> PinkPrimary.copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                            val borderColor = when {
                                                isRead -> MintSuccess
                                                isCurrent -> PinkPrimary
                                                else -> Color.Transparent
                                            }
                                            val textColor = when {
                                                isRead -> MintSuccess
                                                isCurrent -> PinkPrimary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(bg)
                                                    .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                                                    .clickable { speakText(word, currentBook.language) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = word,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "💡 Tap any word to hear pronunciation",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Mode Controls
                    if (readingMode == "FLIP") {
                        Button(
                            onClick = { speakText(currentPage.text, currentBook.language) },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Listen to Page", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (isListening) {
                                                stopMic()
                                            } else {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) startMic()
                                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isListening) PinkPrimary else BlueSecondary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isListening) "Listening..." else if (isPageUnlocked) "Page Complete ✓" else "Tap Mic & Read",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "${minOf(currentWordIdx, pageWords.size)} / ${pageWords.size} words",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isPageUnlocked) MintSuccess else PinkPrimary
                                    )
                                }

                                if (!speechAvailable && !isPageUnlocked) {
                                    OutlinedButton(
                                        onClick = {
                                            if (currentWordIdx < pageWords.size) {
                                                speakText(pageWords[currentWordIdx], currentBook.language)
                                                currentWordIdx += 1
                                                if (currentWordIdx >= pageWords.size && pageIndex !in completedPages) {
                                                    completedPages.add(pageIndex)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🎙️ Practice Next Word (No-Mic Device)")
                                    }
                                }

                                if (hintMessage.isNotBlank()) {
                                    Text(
                                        text = hintMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (lastTranscript.isNotBlank()) {
                                    Text(
                                        text = "Heard: \"$lastTranscript\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Flip Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { flipToPage(-1) },
                            enabled = pageIndex > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev")
                        }

                        Button(
                            onClick = {
                                if (isLastPage) finishStory() else flipToPage(1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPageUnlocked) PinkPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isPageUnlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (!isPageUnlocked) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isLastPage) "Read Aloud to Finish" else "Read Aloud to Flip")
                            } else if (isLastPage) {
                                Text("🎉 Finish Story (+Famibux)", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Flip Page", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showCompleteDialog = false
                onBackClick()
            },
            title = {
                Text("🎉 Story Complete!", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    text = if (alreadyRewardedToday) {
                        "Great extra reading! You already earned today's Famibux for this story."
                    } else {
                        "Awesome job reading! You earned +$awardedFamibux Famibux!"
                    },
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompleteDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Back to Stories", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
