package com.famitube.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.famitube.app.data.local.AchievementManager
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.local.StarPointsManager
import com.famitube.app.data.model.*
import com.famitube.app.data.remote.FamiTubeApiService
import com.famitube.app.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class FamiTubeRepository(
    private val preferences: ParentalPreferences,
    private val achievementManager: AchievementManager? = null,
    private val starPointsManager: StarPointsManager? = null,
    private val appContext: Context? = null
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                preferences.loggedInRole = body.user.role
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

    fun isParentMode(): Boolean = isLoggedIn() && preferences.isParentAccount()

    suspend fun loginChild(email: String, password: String): Result<AuthUser> =
        loginWithType(email, password, "CHILD")

    suspend fun loginParent(email: String, password: String): Result<AuthUser> =
        loginWithType(email, password, "PARENT")

    private suspend fun loginWithType(
        email: String,
        password: String,
        loginType: String
    ): Result<AuthUser> {
        return try {
            val response = api.login(
                LoginRequest(
                    email = email.trim().lowercase(),
                    password = password,
                    loginType = loginType
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.token.isNullOrBlank() && body.user != null) {
                    preferences.authToken = body.token
                    preferences.loggedInEmail = body.user.email
                    preferences.loggedInName = body.user.name
                    preferences.loggedInRole = body.user.role
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
                val fallback = if (loginType == "PARENT") {
                    "Invalid parent email or password."
                } else {
                    "This email/Gmail is not registered as a child account by a parent/guardian."
                }
                Result.failure(Exception(errMsg ?: fallback))
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
        val ageFilter = preferences.selectedAgeFilter.takeIf { it != "ALL" && it.isNotBlank() }
        return try {
            if (isLoggedIn()) {
                val feedRes = api.getKidsFeed(ageRating = ageFilter)
                if (feedRes.success && feedRes.data != null) {
                    feedRes.data.parentalSettings?.let { applyRemoteParentalSettings(it) }
                    val effectiveFilter = preferences.selectedAgeFilter.takeIf { it != "ALL" && it.isNotBlank() }
                    val filtered = if (effectiveFilter == null) {
                        feedRes.data.channels
                    } else {
                        feedRes.data.channels.filter {
                            it.ageRating.equals(effectiveFilter, ignoreCase = true) ||
                                it.ageRating.equals("ALL", ignoreCase = true)
                        }
                    }
                    return Result.success(filtered)
                }
            }
            val res = api.getChannels(ageRating = ageFilter)
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
        val ageFilter = preferences.selectedAgeFilter.takeIf { it != "ALL" && it.isNotBlank() }
        return try {
            val res = api.getChannelDetail(id, ageRating = ageFilter)
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

    val favoriteIdsFlow: StateFlow<Set<String>> get() = preferences.favoriteIdsFlow

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
        if (amount > 0 && isLoggedIn()) {
            syncScope.launch {
                try {
                    val res = api.awardKidStars(StarAwardRequest(amount))
                    if (res.success && res.data != null) {
                        achievementManager?.applyRemoteSummary(res.data)
                    }
                } catch (_: Exception) { }
            }
        }
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
        if (spent && cost > 0 && isLoggedIn()) {
            syncScope.launch {
                try {
                    val res = api.spendKidStars(StarSpendRequest(cost))
                    if (res.success && res.data != null) {
                        achievementManager?.applyRemoteSummary(res.data)
                    }
                } catch (_: Exception) { }
            }
        }
        return spent
    }

    suspend fun syncAchievementsFromBackend(): Result<KidAchievementsDto> {
        if (!isLoggedIn()) return Result.failure(Exception("Not logged in"))
        return try {
            val res = api.getKidAchievements()
            if (res.success && res.data != null) {
                achievementManager?.applyRemoteSummary(res.data)
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to sync achievements"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun recordDailyVisit() {
        achievementManager?.recordDailyVisit()
        if (isLoggedIn()) {
            syncScope.launch { syncAchievementsFromBackend() }
        }
    }

    fun recordVideoWatched(videoId: String, category: String?) {
        achievementManager?.recordVideoWatched(videoId, category)
        if (isLoggedIn()) {
            syncScope.launch { syncAchievementsFromBackend() }
        }
    }

    // ==========================================
    // KID SAVED CHANNELS
    // ==========================================

    suspend fun getKidSavedChannels(): Result<List<Channel>> {
        if (!isLoggedIn()) return Result.success(emptyList())
        return try {
            val res = api.getKidSavedChannels()
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load saved channels"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addKidSavedChannel(channelId: String): Result<Boolean> {
        return try {
            val res = api.addKidSavedChannel(channelId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to add channel"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeKidSavedChannel(channelId: String): Result<Boolean> {
        return try {
            val res = api.removeKidSavedChannel(channelId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to remove channel"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // KID YOUTUBE SEARCH, HISTORY & PLAYLISTS
    // ==========================================

    @SuppressLint("MissingPermission")
    private fun getLastKnownGpsCoords(): Pair<Double, Double>? {
        val ctx = appContext ?: return null
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) return null

        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            loc?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchYouTube(query: String): Result<YouTubeSearchData> {
        return try {
            val locale = java.util.Locale.getDefault()
            val region = locale.country.takeIf { it.length == 2 }
            val lang = locale.language.takeIf { it.length == 2 }
            val gps = getLastKnownGpsCoords()
            val response = api.searchYouTube(
                query = query,
                region = region,
                lang = lang,
                lat = gps?.first,
                lon = gps?.second
            )
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

    suspend fun getKidYouTubeSubscriptions(): Result<List<YouTubeChannelSearchResult>> {
        if (!isLoggedIn()) return Result.success(emptyList())
        return try {
            val res = api.getKidYouTubeSubscriptions()
            if (res.success && res.data != null) Result.success(res.data)
            else Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subscribeKidYouTubeChannel(ch: YouTubeChannelSearchResult): Result<YouTubeChannelSearchResult> {
        return try {
            val res = api.subscribeKidYouTubeChannel(
                SubscribeYouTubeChannelRequest(
                    handle = ch.handle,
                    channelId = ch.channelId,
                    title = ch.title,
                    avatarUrl = ch.avatarUrl
                )
            )
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to subscribe"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsubscribeKidYouTubeChannel(handle: String): Result<Boolean> {
        return try {
            val res = api.unsubscribeKidYouTubeChannel(handle)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to unsubscribe"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getKidYouTubeChannelVideos(handle: String): Result<YouTubeChannelDetailData> {
        return try {
            val res = api.getKidYouTubeChannelVideos(handle)
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load channel videos"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getKidYouTubePlaylistVideos(
        playlistId: String,
        handle: String? = null
    ): Result<YouTubePlaylistDetailData> {
        return try {
            val res = api.getKidYouTubePlaylistVideos(playlistId, handle)
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load playlist videos"))
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

    // ==========================================
    // PARENT MODE
    // ==========================================

    suspend fun getParentGlobalControls(): Result<GlobalParentalControlsDto> {
        return try {
            val res = api.getParentGlobalControls()
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load global controls"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChildActivityHistory(childId: String): Result<KidActivityHistoryData> {
        return try {
            val res = api.getChildActivityHistory(childId)
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load activity history"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearChildSearchHistory(childId: String): Result<Boolean> {
        return try {
            val res = api.clearChildSearchHistory(childId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to clear search history"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearChildWatchHistory(childId: String): Result<Boolean> {
        return try {
            val res = api.clearChildWatchHistory(childId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to clear watch history"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChildYouTubeSubscriptionTrust(
        childId: String,
        handle: String,
        trustLevel: String
    ): Result<YouTubeChannelSearchResult> {
        return try {
            val res = api.updateChildYouTubeSubscriptionTrust(
                childId = childId,
                handle = handle,
                body = UpdateYouTubeSubscriptionTrustRequest(trustLevel)
            )
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to update channel trust"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChildYouTubeSubscription(
        childId: String,
        handle: String
    ): Result<Boolean> {
        return try {
            val res = api.deleteChildYouTubeSubscription(childId, handle)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to remove subscription"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateParentGlobalControls(
        controls: GlobalParentalControlsDto
    ): Result<GlobalParentalControlsDto> {
        return try {
            val response = api.updateParentGlobalControls(controls)
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                Result.failure(Exception(body?.message ?: "Failed to update global controls"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateParentChildOverrides(
        childId: String,
        overrides: Map<String, Any?>
    ): Result<ParentChildDto> {
        return try {
            val response = api.updateParentChild(childId, UpdateChildControlsRequest(overrides))
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                Result.failure(Exception(body?.message ?: "Failed to update child override"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getParentChildren(): Result<List<ParentChildDto>> {
        return try {
            val res = api.getParentChildren()
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load child accounts"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerParentChild(
        email: String,
        name: String,
        password: String,
        ageRating: String
    ): Result<ParentChildDto> {
        return try {
            val response = api.registerParentChild(
                RegisterChildRequest(
                    email = email.trim().lowercase(),
                    name = name.trim(),
                    password = password,
                    ageRating = ageRating
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                val errBody = response.errorBody()?.string()
                val parsed = try {
                    Gson().fromJson(errBody, ApiResponse::class.java)
                } catch (_: Exception) {
                    null
                }
                Result.failure(Exception(parsed?.message ?: body?.message ?: "Failed to register child"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteParentChild(childId: String): Result<Boolean> {
        return try {
            val res = api.deleteParentChild(childId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to remove child"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getParentVideoRequests(): Result<List<ParentVideoRequestDto>> {
        return try {
            val res = api.getParentVideoRequests()
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load approval requests"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decideParentVideoRequest(
        requestId: String,
        decision: String
    ): Result<ParentVideoRequestDto> {
        return try {
            val response = api.decideParentVideoRequest(requestId, DecideVideoRequestBody(decision))
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                Result.failure(Exception(body?.message ?: "Failed to update request"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Notifications & Notification History
    suspend fun getNotifications(limit: Int = 100): Result<NotificationsDataDto> {
        return try {
            val res = api.getNotifications(limit)
            if (res.success && res.data != null) Result.success(res.data)
            else Result.failure(Exception(res.message ?: "Failed to load notifications"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(id: String): Result<Boolean> {
        return try {
            val res = api.markNotificationRead(id)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to mark read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(): Result<Boolean> {
        return try {
            val res = api.markAllNotificationsRead()
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to mark all read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearNotifications(): Result<Boolean> {
        return try {
            val res = api.clearNotifications()
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to clear notifications"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
