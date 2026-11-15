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
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val featuredVideo: Video? = null,
    val videos: List<Video> = emptyList(),
    val remainingMinutes: Int = 45,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: FamiTubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        repository.recordDailyVisit()
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val remaining = repository.getPreferences().getRemainingMinutes()
            val ageFilter = repository.getPreferences().selectedAgeFilter

            // 1. Fetch Categories
            val categoriesResult = repository.getCategories()
            val categories = categoriesResult.getOrDefault(emptyList())

            // 2. Fetch Featured Video
            val featuredResult = repository.getVideos(featured = true, ageRating = ageFilter)
            val featured = featuredResult.getOrNull()?.firstOrNull()

            // 3. Fetch Category Videos
            val selectedCat = _uiState.value.selectedCategory
            val videosResult = repository.getVideos(
                category = if (selectedCat == "All Videos" || selectedCat == null) null else selectedCat,
                ageRating = ageFilter
            )

            if (videosResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categories,
                    featuredVideo = featured,
                    videos = videosResult.getOrDefault(emptyList()),
                    remainingMinutes = remaining,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categories,
                    remainingMinutes = remaining,
                    errorMessage = videosResult.exceptionOrNull()?.localizedMessage ?: "Failed to connect to FamiTube server"
                )
            }
        }
    }

    fun selectCategory(categoryName: String) {
        val newSelection = if (_uiState.value.selectedCategory == categoryName) null else categoryName
        _uiState.value = _uiState.value.copy(selectedCategory = newSelection)
        loadVideosForCurrentSelection()
    }

    private fun loadVideosForCurrentSelection() {
        viewModelScope.launch {
            val selectedCat = _uiState.value.selectedCategory
            val ageFilter = repository.getPreferences().selectedAgeFilter
            val result = repository.getVideos(
                category = if (selectedCat == "All Videos" || selectedCat == null) null else selectedCat,
                ageRating = ageFilter
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    videos = result.getOrDefault(emptyList())
                )
            }
        }
    }

    fun updateRemainingTime() {
        _uiState.value = _uiState.value.copy(
            remainingMinutes = repository.getPreferences().getRemainingMinutes()
        )
    }

    fun verifyParentPin(pin: String): Boolean {
        return repository.getPreferences().verifyPin(pin)
    }
}
