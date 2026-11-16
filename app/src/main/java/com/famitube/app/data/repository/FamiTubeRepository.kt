package com.famitube.app.data.repository

import android.net.Uri
import com.google.gson.Gson
import com.famitube.app.data.local.AchievementManager
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.local.StarPointsManager
import com.famitube.app.data.model.*
import com.famitube.app.data.remote.FamiTubeApiService
import com.famitube.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FamiTubeRepository(
    private val preferences: ParentalPreferences,
    private val achievementManager: AchievementManager? = null,
    private val starPointsManager: StarPointsManager? = null
) {
    private val _oauthCallbackFlow = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 4)
    val oauthCallbackFlow: SharedFlow<Uri> = _oauthCallbackFlow.asSharedFlow()

    init {
        if (preferences.authToken.isNotBlank()) {
            NetworkModule.updateAuthToken(preferences.authToken)
        }
    }

    fun emitOAuthCallback(uri: Uri) {
        _oauthCallbackFlow.tryEmit(uri)
    }

    fun getGoogleMobileOAuthStartUrl(): String {
        val base = preferences.backendUrl.trimEnd('/')
        return "$base/auth/google/mobile/start"
    }

    suspend fun completeGoogleOAuth(token: String): Result<AuthUser> {
        return try {
            NetworkModule.updateAuthToken(token)
            val response = api.getAuthMe()
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.user != null) {
                preferences.authToken = token
                preferences.loggedInEmail = body.user.email
                preferences.loggedInName = body.user.name
                if (!body.user.ageRating.isNullOrBlank()) {
                    preferences.selectedAgeFilter = body.user.ageRating
                }
                achievementManager?.refreshSummary()
                achievementManager?.recordDailyVisit()
                Result.success(body.user)
            } else {
                NetworkModule.updateAuthToken("")
                Result.failure(Exception("Google sign-in failed to load account."))
            }
        } catch (e: Exception) {
            NetworkModule.updateAuthToken("")
            Result.failure(e)
        }
    }

    private val api: FamiTubeApiService
        get() = NetworkModule.getApiService(preferences.backendUrl)

    fun isLoggedIn(): Boolean = preferences.authToken.isNotBlank()

    suspend fun loginChild(email: String, password: String): Result<AuthUser> {
        return try {
            val response = api.login(
                LoginRequest(
                    email = email.trim().lowercase(),
                    password = password,
                    loginType = "CHILD"
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.token.isNullOrBlank() && body.user != null) {
                    preferences.authToken = body.token
                    preferences.loggedInEmail = body.user.email
                    preferences.loggedInName = body.user.name
                    if (!body.user.ageRating.isNullOrBlank()) {
                        preferences.selectedAgeFilter = body.user.ageRating
                    }
                    NetworkModule.updateAuthToken(body.token)
                    achievementManager?.refreshSummary()
                    achievementManager?.recordDailyVisit()
                    Result.success(body.user)
                } else {
                    Result.failure(Exception(body?.message ?: "Login failed"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val errMsg = try {
                    Gson().fromJson(errBody, LoginResponse::class.java)?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(
                    Exception(
                        errMsg
                            ?: "This email/Gmail is not registered as a child account by a parent/guardian."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        preferences.clearAuthSession()
        NetworkModule.updateAuthToken("")
        achievementManager?.refreshSummary()
    }

    suspend fun getVideos(
        category: String? = null,
        channelId: String? = null,
        ageRating: String? = null,
        search: String? = null,
        featured: Boolean? = null
    ): Result<List<Video>> {
        return try {
            if (isLoggedIn() && category == null && channelId == null && search == null && featured == null) {
                val feedRes = api.getKidsFeed()
                if (feedRes.success && feedRes.data != null) {
                    feedRes.data.parentalSettings?.let { applyRemoteParentalSettings(it) }
                    return Result.success(feedRes.data.videos)
                }
            }
            val filterAge = if (ageRating == "ALL" || ageRating == null) null else ageRating
            val res = api.getVideos(
                category = category,
                channelId = channelId,
                ageRating = filterAge,
                search = search,
                featured = featured
            )
            if (res.success && res.data != null) {
                if (!search.isNullOrBlank()) {
                    achievementManager?.recordSearchPerformed()
                }
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load videos"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetail(id: String): Result<VideoDetailResponse> {
        return try {
            val res = api.getVideoDetail(id)
            if (res.success && res.data != null) {
                // record to watch history
                preferences.addToHistory(id)
                if (!preferences.attentionTrackingEnabled) {
                    achievementManager?.recordVideoWatched(id, res.data.category)
                }
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Video not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategories(): Result<List<Category>> {
        return try {
            val res = api.getCategories()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannels(): Result<List<Channel>> {
        return try {
            if (isLoggedIn()) {
                val feedRes = api.getKidsFeed()
                if (feedRes.success && feedRes.data != null) {
                    return Result.success(feedRes.data.channels)
                }
            }
            val res = api.getChannels()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load channels"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelDetail(id: String): Result<ChannelDetailResponse> {
        return try {
            val res = api.getChannelDetail(id)
            if (res.success && res.data != null) {
                achievementManager?.recordChannelVisited(id)
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Channel not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveChannelState(id: String): Result<LiveChannelState> {
        return try {
            val res = api.getLiveChannelState(id)
            if (res.success && res.data != null) {
                achievementManager?.recordChannelVisited(id)
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Live channel unavailable"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val res = api.getPlaylists()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load playlists"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAppConfig(): Result<AppConfig> {
        return try {
            val res = api.getAppConfig()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load config"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavoriteVideos(): Result<List<Video>> {
        val favIds = preferences.getFavoriteIds()
        if (favIds.isEmpty()) return Result.success(emptyList())

        return try {
            val allRes = api.getVideos()
            if (allRes.success && allRes.data != null) {
                val favList = allRes.data.filter { favIds.contains(it.id) }
                Result.success(favList)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistoryVideos(): Result<List<Video>> {
        val histIds = preferences.getHistoryIds()
        if (histIds.isEmpty()) return Result.success(emptyList())

        return try {
            val allRes = api.getVideos()
            if (allRes.success && allRes.data != null) {
                val idMap = allRes.data.associateBy { it.id }
                val ordered = histIds.mapNotNull { idMap[it] }
                Result.success(ordered)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isFavorite(videoId: String): Boolean = preferences.isFavorite(videoId)

    fun toggleFavorite(videoId: String): Boolean {
        val isNowFav = preferences.toggleFavorite(videoId)
        if (isNowFav) {
            achievementManager?.recordFavoritesCount(preferences.getFavoriteIds().size)
        }
        return isNowFav
    }

    private fun applyRemoteParentalSettings(remote: ParentalSettingsDto) {
        if (remote.parentPin.length == 4 && remote.parentPin.all { it.isDigit() }) {
            preferences.parentPin = remote.parentPin
        }
        preferences.dailyLimitMinutes = remote.dailyLimitMinutes.coerceIn(5, 480)
        if (remote.ageRating.isNotBlank()) {
            preferences.selectedAgeFilter = remote.ageRating
        }
        preferences.attentionTrackingEnabled = remote.attentionTrackingEnabled
        preferences.attentionPauseSeconds = remote.attentionPauseSeconds.coerceIn(5, 300)
        preferences.attentionMinWatchPercent = remote.attentionMinWatchPercent.coerceIn(10, 100)
    }

    fun getPreferences(): ParentalPreferences = preferences

    fun getAchievementManager(): AchievementManager? = achievementManager

    fun getStarPointsManager(): StarPointsManager? = starPointsManager

    fun awardStars(amount: Int, source: StarSource, description: String): Int {
        val updated = starPointsManager?.awardStars(amount, source, description) ?: 0
        achievementManager?.refreshSummary()
        return updated
    }

    fun awardStarsFromParent(amount: Int, reason: String = "Gift from Parent"): Int {
        return awardStars(amount, StarSource.PARENT_GIFT, reason)
    }

    fun spendStarsForVideoRequest(videoTitle: String, cost: Int = StarCosts.VIDEO_REQUEST): Boolean {
        val spent = starPointsManager?.spendStars(
            cost = cost,
            source = StarSource.VIDEO_REQUEST,
            description = "Requested: $videoTitle"
        ) ?: true
        if (spent) {
            achievementManager?.refreshSummary()
        }
        return spent
    }

    fun recordDailyVisit() {
        achievementManager?.recordDailyVisit()
    }

    fun recordVideoWatched(videoId: String, category: String?) {
        achievementManager?.recordVideoWatched(videoId, category)
    }

    // ==========================================
    // CHANNEL QUIZ WALL
    // ==========================================

    suspend fun getChannelAccess(channelId: String): Result<ChannelAccessData> {
        if (!isLoggedIn()) return Result.success(ChannelAccessData(unlocked = true, quizRequired = false))
        return try {
            val res = api.getChannelAccess(channelId)
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to check channel access"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitChannelQuizAnswer(
        channelId: String,
        questionId: String,
        selectedIndex: Int
    ): Result<QuizAnswerData> {
        return try {
            val response = api.submitChannelQuizAnswer(
                channelId = channelId,
                body = QuizAnswerRequest(questionId = questionId, selectedIndex = selectedIndex)
            )
            val body = response.body()
            if (body?.data != null) {
                if (body.data.unlocked) {
                    achievementManager?.recordQuizPassed()
                }
                Result.success(body.data)
            } else {
                val errBody = response.errorBody()?.string()
                val parsed = try {
                    Gson().fromJson(errBody, ApiResponse::class.java)
                } catch (_: Exception) {
                    null
                }
                Result.failure(Exception(parsed?.message ?: body?.message ?: "Failed to submit answer"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // KID YOUTUBE SEARCH, HISTORY & PLAYLISTS
    // ==========================================

    suspend fun searchYouTube(query: String): Result<YouTubeSearchData> {
        return try {
            val response = api.searchYouTube(query)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                if (query.isNotBlank()) {
                    achievementManager?.recordSearchPerformed()
                }
                Result.success(response.body()!!.data!!)
            } else {
                val errBody = response.errorBody()?.string()
                val msg = try {
                    Gson().fromJson(errBody, ApiResponse::class.java)?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(Exception(msg ?: response.body()?.message ?: "YouTube search unavailable"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestOrWatchYouTube(item: YouTubeSearchResult): Result<YouTubeWatchData> {
        return try {
            val response = api.requestOrWatchYouTube(
                YouTubeWatchRequest(
                    youtubeVideoId = item.youtubeVideoId,
                    title = item.title,
                    channelTitle = item.channelTitle,
                    thumbnailUrl = item.thumbnailUrl
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                val errBody = response.errorBody()?.string()
                val msg = try {
                    Gson().fromJson(errBody, ApiResponse::class.java)?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(Exception(msg ?: body?.message ?: "Cannot play video"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getKidHistory(): Result<List<KidHistoryItem>> {
        if (!isLoggedIn()) return Result.success(emptyList())
        return try {
            val res = api.getKidHistory()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getKidPlaylists(): Result<List<KidPlaylist>> {
        if (!isLoggedIn()) return Result.success(emptyList())
        return try {
            val res = api.getKidPlaylists()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createKidPlaylist(title: String): Result<KidPlaylist> {
        return try {
            val res = api.createKidPlaylist(CreateKidPlaylistRequest(title = title.trim()))
            if (res.success && res.data != null) {
                achievementManager?.recordPlaylistCreated()
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to create playlist"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteKidPlaylist(playlistId: String): Result<Boolean> {
        return try {
            val res = api.deleteKidPlaylist(playlistId)
            if (res.success) Result.success(true) else Result.failure(Exception(res.message ?: "Failed to delete"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeKidPlaylistItem(playlistId: String, itemKey: String): Result<KidPlaylist> {
        return try {
            val res = api.removeKidPlaylistItem(playlistId, itemKey)
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to remove item"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
