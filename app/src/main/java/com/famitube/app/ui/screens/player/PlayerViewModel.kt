package com.famitube.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famitube.app.data.model.AddKidPlaylistItemRequest
import com.famitube.app.data.model.KidPlaylist
import com.famitube.app.data.model.SourceType
import com.famitube.app.data.model.VideoDetailResponse
import com.famitube.app.data.repository.FamiTubeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val video: VideoDetailResponse? = null,
    val isFavorite: Boolean = false,
    val isLimitExceeded: Boolean = false,
    val remainingMinutes: Int = 45,
    val isAttentionTrackingEnabled: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isViewerPresent: Boolean = true,
    val isAutoPausedByInattention: Boolean = false,
    val attentionPauseSeconds: Int = 15,
    val viewedSeconds: Int = 0,
    val unwatchedSeconds: Int = 0,
    val requiredViewedSeconds: Int = 126,
    val hasEarnedCompletionReward: Boolean = false,
    val errorMessage: String? = null
)

class PlayerViewModel(
    private val repository: FamiTubeRepository,
    private val videoId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val favoriteIdsFlow: StateFlow<Set<String>> = repository.favoriteIdsFlow

    private var timerJob: Job? = null
    private var lastFaceSeenAtMs: Long = System.currentTimeMillis()

    init {
        val prefs = repository.getPreferences()
        _uiState.value = _uiState.value.copy(
            isAttentionTrackingEnabled = prefs.attentionTrackingEnabled,
            attentionPauseSeconds = prefs.attentionPauseSeconds
        )
        loadVideo()
    }

    fun updateCameraPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    fun resumeFromInattention() {
        lastFaceSeenAtMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isAutoPausedByInattention = false,
            isViewerPresent = true
        )
    }

    fun onAttentionSample(isViewing: Boolean) {
        if (_uiState.value.isAutoPausedByInattention) return
        val now = System.currentTimeMillis()
        if (isViewing) {
            lastFaceSeenAtMs = now
            if (!_uiState.value.isViewerPresent) {
                _uiState.value = _uiState.value.copy(isViewerPresent = true)
            }
        } else {
            val awayForMs = now - lastFaceSeenAtMs
            val pauseThresholdMs = _uiState.value.attentionPauseSeconds * 1000L
            if (awayForMs >= pauseThresholdMs) {
                _uiState.value = _uiState.value.copy(
                    isViewerPresent = false,
                    isAutoPausedByInattention = true
                )
            } else if (awayForMs > 10_000L && _uiState.value.isViewerPresent) {
                _uiState.value = _uiState.value.copy(isViewerPresent = false)
            }
        }
    }

    private fun loadVideo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val isExceeded = repository.getPreferences().isDailyLimitExceeded()
            val remaining = repository.getPreferences().getRemainingMinutes()
            val isFav = repository.isFavorite(videoId)

            if (isExceeded) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLimitExceeded = true,
                    remainingMinutes = 0
                )
                return@launch
            }

            if (videoId.startsWith("yt:")) {
                val ytId = videoId.removePrefix("yt:")
                val historyItem = repository.getKidHistory().getOrNull()?.firstOrNull {
                    it.youtubeVideoId == ytId || it.itemKey == ytId
                }
                val detail = VideoDetailResponse(
                    id = videoId,
                    title = historyItem?.title ?: "Approved Video",
                    description = "Approved YouTube video for safe watching.",
                    sourceType = SourceType.YOUTUBE,
                    sourceUrl = "https://www.youtube.com/watch?v=$ytId",
                    youtubeVideoId = ytId,
                    thumbnailUrl = historyItem?.thumbnailUrl ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg",
                    channelId = "",
                    channelTitle = historyItem?.channelTitle ?: "YouTube",
                    channelAvatar = "",
                    category = "YouTube"
                )
                val prefs = repository.getPreferences()
                val requiredSec = computeRequiredViewedSeconds(detail.duration, prefs.attentionMinWatchPercent)
                if (!prefs.attentionTrackingEnabled) {
                    repository.recordVideoWatched(videoId, detail.category)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = detail,
                    isFavorite = isFav,
                    isLimitExceeded = false,
                    remainingMinutes = remaining,
                    requiredViewedSeconds = requiredSec,
                    hasEarnedCompletionReward = !prefs.attentionTrackingEnabled,
                    errorMessage = null
                )
                startWatchTimer()
                return@launch
            }

            val result = repository.getVideoDetail(videoId)
            if (result.isSuccess) {
                val detail = result.getOrNull()
                val prefs = repository.getPreferences()
                val requiredSec = computeRequiredViewedSeconds(detail?.duration ?: 180, prefs.attentionMinWatchPercent)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = detail,
                    isFavorite = isFav,
                    isLimitExceeded = false,
                    remainingMinutes = remaining,
                    requiredViewedSeconds = requiredSec,
                    hasEarnedCompletionReward = !prefs.attentionTrackingEnabled,
                    errorMessage = null
                )
                startWatchTimer()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to load video"
                )
            }
        }
    }

    private fun startWatchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(5000) // tick every 5 seconds
                val current = _uiState.value
                if (current.isAutoPausedByInattention) {
                    continue
                }

                repository.getPreferences().addUsedWatchTime(5)
                val remaining = repository.getPreferences().getRemainingMinutes()
                val isExceeded = repository.getPreferences().isDailyLimitExceeded()

                val trackingActive = current.isAttentionTrackingEnabled && current.hasCameraPermission
                val awayForMs = System.currentTimeMillis() - lastFaceSeenAtMs
                val pauseThresholdMs = current.attentionPauseSeconds * 1000L
                val autoPausedNow = trackingActive && awayForMs >= pauseThresholdMs
                val viewingNow = if (trackingActive) awayForMs <= 5000L else true

                val newViewed = if (viewingNow) current.viewedSeconds + 5 else current.viewedSeconds
                val newUnwatched = if (!viewingNow) current.unwatchedSeconds + 5 else current.unwatchedSeconds

                var earnedNow = current.hasEarnedCompletionReward
                if (!earnedNow && current.isAttentionTrackingEnabled && newViewed >= current.requiredViewedSeconds) {
                    repository.recordVideoWatched(videoId, current.video?.category)
                    earnedNow = true
                }

                _uiState.value = current.copy(
                    remainingMinutes = remaining,
                    isLimitExceeded = isExceeded,
                    isViewerPresent = viewingNow,
                    isAutoPausedByInattention = autoPausedNow,
                    viewedSeconds = newViewed,
                    unwatchedSeconds = newUnwatched,
                    hasEarnedCompletionReward = earnedNow
                )

                if (isExceeded) {
                    break
                }
            }
        }
    }

    private fun computeRequiredViewedSeconds(durationSec: Int, minPercent: Int): Int {
        val safeDuration = durationSec.coerceAtLeast(10)
        return ((safeDuration * minPercent) / 100).coerceAtLeast(5)
    }

    fun toggleFavorite() {
        val newFav = repository.toggleFavorite(videoId)
        _uiState.value = _uiState.value.copy(isFavorite = newFav)
    }

    fun getKidPlaylists(onResult: (List<KidPlaylist>) -> Unit) {
        viewModelScope.launch {
            val res = repository.getKidPlaylists()
            onResult(res.getOrDefault(emptyList()))
        }
    }

    fun addVideoToPlaylist(playlistId: String, onComplete: (Boolean) -> Unit) {
        val video = _uiState.value.video ?: return
        viewModelScope.launch {
            val req = AddKidPlaylistItemRequest(
                key = "yt_${video.youtubeVideoId.ifBlank { video.id }}",
                youtubeVideoId = video.youtubeVideoId.ifBlank { video.id },
                videoId = video.id,
                title = video.title,
                channelTitle = video.channelTitle,
                thumbnailUrl = video.thumbnailUrl,
                sourceType = "YOUTUBE",
                sourceUrl = video.videoUrl
            )
            val res = repository.addKidPlaylistItem(playlistId, req)
            onComplete(res.isSuccess)
        }
    }

    fun createPlaylistAndAddVideo(title: String, onComplete: (Boolean) -> Unit) {
        val video = _uiState.value.video ?: return
        viewModelScope.launch {
            val createRes = repository.createKidPlaylist(title)
            val playlist = createRes.getOrNull()
            if (playlist != null) {
                val req = AddKidPlaylistItemRequest(
                    key = "yt_${video.youtubeVideoId.ifBlank { video.id }}",
                    youtubeVideoId = video.youtubeVideoId.ifBlank { video.id },
                    videoId = video.id,
                    title = video.title,
                    channelTitle = video.channelTitle,
                    thumbnailUrl = video.thumbnailUrl,
                    sourceType = "YOUTUBE",
                    sourceUrl = video.videoUrl
                )
                val addRes = repository.addKidPlaylistItem(playlist.id, req)
                onComplete(addRes.isSuccess)
            } else {
                onComplete(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
