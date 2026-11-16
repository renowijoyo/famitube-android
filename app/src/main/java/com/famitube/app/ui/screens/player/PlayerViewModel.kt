package com.famitube.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val viewedSeconds: Int = 0,
    val unwatchedSeconds: Int = 0,
    val errorMessage: String? = null
)

class PlayerViewModel(
    private val repository: FamiTubeRepository,
    private val videoId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var lastFaceSeenAtMs: Long = System.currentTimeMillis()

    init {
        _uiState.value = _uiState.value.copy(
            isAttentionTrackingEnabled = repository.getPreferences().attentionTrackingEnabled
        )
        loadVideo()
    }

    fun updateCameraPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    fun onAttentionSample(isViewing: Boolean) {
        if (isViewing) {
            lastFaceSeenAtMs = System.currentTimeMillis()
            if (!_uiState.value.isViewerPresent) {
                _uiState.value = _uiState.value.copy(isViewerPresent = true)
            }
        } else {
            val awayForMs = System.currentTimeMillis() - lastFaceSeenAtMs
            if (awayForMs > 6000L && _uiState.value.isViewerPresent) {
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
                repository.recordVideoWatched(videoId, detail.category)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = detail,
                    isFavorite = isFav,
                    isLimitExceeded = false,
                    remainingMinutes = remaining,
                    errorMessage = null
                )
                startWatchTimer()
                return@launch
            }

            val result = repository.getVideoDetail(videoId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = result.getOrNull(),
                    isFavorite = isFav,
                    isLimitExceeded = false,
                    remainingMinutes = remaining,
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
                repository.getPreferences().addUsedWatchTime(5)
                val remaining = repository.getPreferences().getRemainingMinutes()
                val isExceeded = repository.getPreferences().isDailyLimitExceeded()

                val trackingActive = _uiState.value.isAttentionTrackingEnabled && _uiState.value.hasCameraPermission
                val viewingNow = if (trackingActive) {
                    (System.currentTimeMillis() - lastFaceSeenAtMs) <= 6000L
                } else {
                    true
                }

                _uiState.value = _uiState.value.copy(
                    remainingMinutes = remaining,
                    isLimitExceeded = isExceeded,
                    isViewerPresent = viewingNow,
                    viewedSeconds = if (viewingNow) _uiState.value.viewedSeconds + 5 else _uiState.value.viewedSeconds,
                    unwatchedSeconds = if (!viewingNow) _uiState.value.unwatchedSeconds + 5 else _uiState.value.unwatchedSeconds
                )

                if (isExceeded) {
                    break
                }
            }
        }
    }

    fun toggleFavorite() {
        val newFav = repository.toggleFavorite(videoId)
        _uiState.value = _uiState.value.copy(isFavorite = newFav)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
