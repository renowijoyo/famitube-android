package com.famitube.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.data.model.ChannelAccessData
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChannelQuizWall(
    repository: FamiTubeRepository,
    channelId: String,
    initialAccess: ChannelAccessData,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var access by remember(initialAccess) { mutableStateOf(initialAccess) }
    var countdown by remember(access.status, access.remainingSeconds) {
        mutableIntStateOf(access.remainingSeconds.coerceAtLeast(0))
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Tick down during COOLDOWN or LOCKED and refresh automatically when timer hits 0
    LaunchedEffect(access.status, access.remainingSeconds) {
        if (access.status == "COOLDOWN" || access.status == "LOCKED") {
            var left = access.remainingSeconds.coerceAtLeast(0)
            countdown = left
            while (left > 0) {
                delay(1000)
                left--
                countdown = left
            }
            val refreshed = repository.getChannelAccess(channelId).getOrNull()
            if (refreshed != null) {
                if (refreshed.unlocked) {
                    onUnlocked()
                } else {
                    access = refreshed
                    feedbackMessage = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (access.status) {
                "LOCKED", "COOLDOWN" -> {
                    val isLocked = access.status == "LOCKED"
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFFFF6B6B) else CyanAccent,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = when {
                            isLocked && access.lockoutAction == "ALLOW_AFTER_WAIT" -> "Wait a Moment to Watch"
                            isLocked -> "Channel Temporarily Locked"
                            else -> "Quick Breather!"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = feedbackMessage ?: when {
                            isLocked && access.lockoutAction == "ALLOW_AFTER_WAIT" ->
                                "Wait until the timer finishes and this channel will open automatically."
                            isLocked ->
                                "Too many wrong answers. You can try again when the timer ends."
                            else ->
                                "Get ready for the next question when the timer finishes."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLightDark)
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        val mins = countdown / 60
                        val secs = countdown % 60
                        val formatted = if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
                        Text(
                            text = formatted,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PinkPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Attempts used: ${access.wrongAttempts} / ${access.maxAttempts}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                else -> {
                    val question = access.question
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = PinkPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unlock This Channel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Answer correctly to watch! (${access.wrongAttempts}/${access.maxAttempts} attempts used)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (!feedbackMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = feedbackMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB74D),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (question != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLightDark)
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = question.prompt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        question.options.forEachIndexed { index, option ->
                            Button(
                                onClick = {
                                    if (isSubmitting) return@Button
                                    isSubmitting = true
                                    scope.launch {
                                        val res = repository.submitChannelQuizAnswer(
                                            channelId = channelId,
                                            questionId = question.id,
                                            selectedIndex = index
                                        )
                                        isSubmitting = false
                                        val data = res.getOrNull()
                                        if (data != null) {
                                            if (data.unlocked) {
                                                onUnlocked()
                                            } else {
                                                feedbackMessage = data.message
                                                access = access.copy(
                                                    unlocked = false,
                                                    status = data.status ?: "COOLDOWN",
                                                    lockoutAction = data.lockoutAction ?: access.lockoutAction,
                                                    remainingSeconds = data.remainingSeconds,
                                                    wrongAttempts = data.wrongAttempts,
                                                    maxAttempts = data.maxAttempts
                                                )
                                            }
                                        } else {
                                            feedbackMessage = res.exceptionOrNull()?.localizedMessage
                                                ?: "Could not submit answer"
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceLightDark,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = option,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val refreshed = repository.getChannelAccess(channelId).getOrNull()
                                    if (refreshed != null) {
                                        if (refreshed.unlocked) onUnlocked() else access = refreshed
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                        ) {
                            Text("Load Question")
                        }
                    }
                }
            }
        }
    }
}
