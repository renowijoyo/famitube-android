package com.famitube.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famitube.app.data.model.Category
import com.famitube.app.data.model.Video
import com.famitube.app.data.repository.FamiTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoadingTopicVideos: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val videos: List<Video> = emptyList(),
    val allVideos: List<Video> = emptyList(),
    val remainingMinutes: Int = 45,
    val errorMessage: String? = null
)

class HomeViewModel(
    val repository: FamiTubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val starBalance: StateFlow<Int>? = repository.getStarPointsManager()?.starBalanceFlow
    val ageFilterFlow: StateFlow<String> = repository.getPreferences().ageFilterFlow
    val favoriteIdsFlow: StateFlow<Set<String>> = repository.favoriteIdsFlow

    init {
        repository.recordDailyVisit()
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            if (repository.isLoggedIn()) {
                launch { repository.syncAchievementsFromBackend() }
            }

            val remaining = repository.getPreferences().getRemainingMinutes()
            val categoriesResult = repository.getCategories()
            val categories = categoriesResult.getOrDefault(emptyList())
                .filter { it.name != "All Videos" }
            val allVideosResult = repository.getVideos(ageRating = repository.getPreferences().selectedAgeFilter)
            val allVideos = allVideosResult.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

            if (categoriesResult.isSuccess || categories.isNotEmpty() || allVideos.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categories,
                    allVideos = allVideos,
                    remainingMinutes = remaining,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = emptyList(),
                    allVideos = emptyList(),
                    remainingMinutes = remaining,
                    errorMessage = categoriesResult.exceptionOrNull()?.localizedMessage
                        ?: "Failed to connect to FamiTube server"
                )
            }
        }
    }

    fun awardGameStars(amount: Int, description: String = "Game Reward"): Int {
        return repository.awardStars(amount, com.famitube.app.data.model.StarSource.ACHIEVEMENT, description)
    }

    fun selectCategory(categoryName: String) {
        val newSelection = if (_uiState.value.selectedCategory == categoryName) null else categoryName
        if (newSelection == null) {
            _uiState.value = _uiState.value.copy(
                selectedCategory = null,
                videos = emptyList(),
                isLoadingTopicVideos = false
            )
            return
        }
        _uiState.value = _uiState.value.copy(selectedCategory = newSelection, isLoadingTopicVideos = true)
        loadVideosForCurrentSelection()
    }

    private fun loadVideosForCurrentSelection() {
        viewModelScope.launch {
            val selectedCat = _uiState.value.selectedCategory ?: return@launch
            val ageFilter = repository.getPreferences().selectedAgeFilter
            val result = repository.getVideos(
                category = selectedCat,
                ageRating = ageFilter
            )
            _uiState.value = _uiState.value.copy(
                isLoadingTopicVideos = false,
                videos = result.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
            )
        }
    }

    fun updateRemainingTime() {
        _uiState.value = _uiState.value.copy(
            remainingMinutes = repository.getPreferences().getRemainingMinutes()
        )
        if (_uiState.value.selectedCategory != null) {
            loadVideosForCurrentSelection()
        }
    }

    fun verifyParentPin(pin: String): Boolean {
        return repository.getPreferences().verifyPin(pin)
    }
}
