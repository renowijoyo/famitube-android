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
        preferences.contentLanguage = remote.contentLanguage
        preferences.cultureMode = remote.cultureMode
        preferences.toddlerLoopMinutes = remote.toddlerLoopMinutes
        preferences.toddlerCooldownMinutes = remote.toddlerCooldownMinutes
        preferences.toddlerSubStage = remote.toddlerSubStage
        if (remote.toddlerEnabledTopics.isNotEmpty()) {
            preferences.toddlerEnabledTopics = remote.toddlerEnabledTopics
        }
        preferences.toddlerHiddenVideoIds = remote.toddlerHiddenVideoIds
        if (!remote.toddlerCooldownUntil.isNullOrBlank()) {
            try {
                val parsed = java.time.Instant.parse(remote.toddlerCooldownUntil).toEpochMilli()
                if (parsed > System.currentTimeMillis()) {
                    preferences.toddlerCooldownUntilMs = parsed
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getToddlerTv(childId: String? = null): Result<ToddlerTvDataDto> {
        return try {
            val res = api.getToddlerTv(childId)
            if (res.success && res.data != null) {
                val s = res.data.settings
                preferences.contentLanguage = s.contentLanguage
                preferences.cultureMode = s.cultureMode
                preferences.toddlerLoopMinutes = s.toddlerLoopMinutes
                preferences.toddlerCooldownMinutes = s.toddlerCooldownMinutes
                preferences.toddlerSubStage = s.toddlerSubStage
                if (s.toddlerEnabledTopics.isNotEmpty()) {
                    preferences.toddlerEnabledTopics = s.toddlerEnabledTopics
                }
                preferences.toddlerHiddenVideoIds = s.toddlerHiddenVideoIds
                if (!res.data.cooldownUntil.isNullOrBlank()) {
                    try {
                        val parsed = java.time.Instant.parse(res.data.cooldownUntil).toEpochMilli()
                        preferences.toddlerCooldownUntilMs = parsed
                    } catch (_: Exception) {}
                }
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load Toddler TV"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startRemoteToddlerCooldown(): Result<Boolean> {
        preferences.startToddlerCooldown()
        if (!isLoggedIn()) return Result.success(true)
        return try {
            val res = api.updateToddlerCooldown(ToddlerCooldownRequest(action = "START"))
            Result.success(res.isSuccessful)
        } catch (_: Exception) {
            Result.success(true)
        }
    }

    suspend fun wakeToddlerTvWithPin(pin: String): Result<Boolean> {
        if (!isLoggedIn()) {
            return if (preferences.verifyPin(pin)) {
                preferences.wakeToddlerTv()
                Result.success(true)
            } else {
                Result.failure(Exception("Incorrect Parent PIN"))
            }
        }
        return try {
            val res = api.updateToddlerCooldown(ToddlerCooldownRequest(action = "WAKE", pin = pin))
            if (res.isSuccessful) {
                preferences.wakeToddlerTv()
                Result.success(true)
            } else {
                Result.failure(Exception("Incorrect Parent PIN"))
            }
        } catch (e: Exception) {
            if (preferences.verifyPin(pin)) {
                preferences.wakeToddlerTv()
                Result.success(true)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun wakeChildToddlerTv(childId: String): Result<Boolean> {
        return try {
            val res = api.wakeChildToddlerTv(childId)
            if (res.success) Result.success(true)
            else Result.failure(Exception(res.message ?: "Failed to wake Toddler TV"))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        val localList = preferences.getKidPlaylists()
        if (!isLoggedIn()) return Result.success(localList)
        return try {
            val res = api.getKidPlaylists()
            if (res.success && res.data != null) {
                preferences.saveKidPlaylists(res.data)
                Result.success(res.data)
            } else {
                Result.success(localList)
            }
        } catch (e: Exception) {
            Result.success(localList)
        }
    }

    suspend fun createKidPlaylist(title: String): Result<KidPlaylist> {
        val trimmed = title.trim()
        if (!isLoggedIn()) {
            val local = preferences.createKidPlaylist(trimmed)
            achievementManager?.recordPlaylistCreated()
            return Result.success(local)
        }
        return try {
            val res = api.createKidPlaylist(CreateKidPlaylistRequest(title = trimmed))
            if (res.success && res.data != null) {
                achievementManager?.recordPlaylistCreated()
                // Update local storage
                val current = preferences.getKidPlaylists().filter { it.id != res.data.id }.toMutableList()
                current.add(0, res.data)
                preferences.saveKidPlaylists(current)
                Result.success(res.data)
            } else {
                val local = preferences.createKidPlaylist(trimmed)
                achievementManager?.recordPlaylistCreated()
                Result.success(local)
            }
        } catch (e: Exception) {
            val local = preferences.createKidPlaylist(trimmed)
            achievementManager?.recordPlaylistCreated()
            Result.success(local)
        }
    }

    suspend fun addKidPlaylistItem(playlistId: String, item: AddKidPlaylistItemRequest): Result<KidPlaylist> {
        val localUpdated = preferences.addKidPlaylistItem(playlistId, item)
        if (!isLoggedIn()) {
            return if (localUpdated != null) Result.success(localUpdated)
            else Result.failure(Exception("Playlist not found"))
        }
        return try {
            val res = api.addKidPlaylistItem(playlistId, item)
            if (res.success && res.data != null) {
                val current = preferences.getKidPlaylists().map { if (it.id == res.data.id) res.data else it }
                preferences.saveKidPlaylists(current)
                Result.success(res.data)
            } else if (localUpdated != null) {
                Result.success(localUpdated)
            } else {
                Result.failure(Exception(res.message ?: "Failed to add video to playlist"))
            }
        } catch (e: Exception) {
            if (localUpdated != null) Result.success(localUpdated)
            else Result.failure(e)
        }
    }

    suspend fun deleteKidPlaylist(playlistId: String): Result<Boolean> {
        val localDeleted = preferences.deleteKidPlaylist(playlistId)
        if (!isLoggedIn()) return Result.success(localDeleted)
        return try {
            val res = api.deleteKidPlaylist(playlistId)
            if (res.success) Result.success(true) else Result.success(localDeleted)
        } catch (e: Exception) {
            Result.success(localDeleted)
        }
    }

    suspend fun removeKidPlaylistItem(playlistId: String, itemKey: String): Result<KidPlaylist> {
        val localUpdated = preferences.removeKidPlaylistItem(playlistId, itemKey)
        if (!isLoggedIn()) {
            return if (localUpdated != null) Result.success(localUpdated)
            else Result.failure(Exception("Playlist not found"))
        }
        return try {
            val res = api.removeKidPlaylistItem(playlistId, itemKey)
            if (res.success && res.data != null) {
                val current = preferences.getKidPlaylists().map { if (it.id == res.data.id) res.data else it }
                preferences.saveKidPlaylists(current)
                Result.success(res.data)
            } else if (localUpdated != null) {
                Result.success(localUpdated)
            } else {
                Result.failure(Exception(res.message ?: "Failed to remove item"))
            }
        } catch (e: Exception) {
            if (localUpdated != null) Result.success(localUpdated)
            else Result.failure(e)
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
        ageRating: String,
        birthday: String? = null
    ): Result<ParentChildDto> {
        return try {
            val response = api.registerParentChild(
                RegisterChildRequest(
                    email = email.trim().lowercase(),
                    name = name.trim(),
                    password = password,
                    birthday = birthday?.trim()?.takeIf { it.isNotEmpty() },
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

    // Storybooks & Read-Along
    suspend fun getStories(
        ageRating: String? = null,
        language: String? = null,
        includeAllAges: Boolean = false,
        search: String? = null,
        category: String? = null,
        page: Int? = null,
        limit: Int? = null
    ): Result<List<StoryBookDto>> {
        return try {
            val res = api.getStories(
                ageRating = ageRating,
                language = language,
                includeAllAges = if (includeAllAges) true else null,
                search = search?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                page = page,
                limit = limit
            )
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.success(FallbackStoryCatalog.filter(ageRating, language, includeAllAges))
            }
        } catch (_: Exception) {
            Result.success(FallbackStoryCatalog.filter(ageRating, language, includeAllAges))
        }
    }

    suspend fun getStoryById(storyId: String): Result<StoryBookDto> {
        return try {
            val res = api.getStoryDetail(storyId)
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                FallbackStoryCatalog.findById(storyId)?.let { Result.success(it) }
                    ?: Result.failure(Exception("Storybook not found"))
            }
        } catch (_: Exception) {
            FallbackStoryCatalog.findById(storyId)?.let { Result.success(it) }
                ?: Result.failure(Exception("Storybook not found"))
        }
    }

    suspend fun completeStory(
        storyId: String,
        mode: String,
        fallbackReward: Int
    ): Result<CompleteStoryResponseDto> {
        return try {
            if (isLoggedIn()) {
                val res = api.completeStory(storyId, CompleteStoryRequest(mode))
                if (res.success && res.data != null) {
                    res.data.summary?.let { achievementManager?.refreshSummary() }
                    return Result.success(res.data)
                }
            }
            val localAward = if (mode == "READ_ALONG") fallbackReward else (fallbackReward + 1) / 2
            awardStars(localAward, com.famitube.app.data.model.StarSource.ACHIEVEMENT, "Storybook Complete")
            Result.success(CompleteStoryResponseDto(awardedFamibux = localAward, mode = mode))
        } catch (_: Exception) {
            val localAward = if (mode == "READ_ALONG") fallbackReward else (fallbackReward + 1) / 2
            awardStars(localAward, com.famitube.app.data.model.StarSource.ACHIEVEMENT, "Storybook Complete")
            Result.success(CompleteStoryResponseDto(awardedFamibux = localAward, mode = mode))
        }
    }
}

object FallbackStoryCatalog {
    val books: List<StoryBookDto> = listOf(
        StoryBookDto(
            id = "story-tod-en-1",
            title = "Little Bear's Sleepy Moon",
            author = "FamiTube Storybooks",
            description = "A gentle bedtime flip-book following Little Bear as he says goodnight to the forest.",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&h=400&fit=crop",
            ageRating = "TODDLER",
            language = "en",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "Little Bear sees the bright moon.", "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=720&h=440&fit=crop", "🐻🌙", "#1E293B"),
                StoryPageDto(2, "Soft stars twinkle in the sky.", "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=720&h=440&fit=crop", "✨🌟", "#312E81"),
                StoryPageDto(3, "Owl says hoot hoot goodnight.", "https://images.unsplash.com/photo-1543549790-8b5f4a028cfb?w=720&h=440&fit=crop", "🦉🌲", "#1E3A5F"),
                StoryPageDto(4, "Little Bear curls up to sleep.", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=720&h=440&fit=crop", "🐻💤", "#2E1065")
            )
        ),
        StoryBookDto(
            id = "story-tod-en-2",
            title = "Splash Goes the Yellow Duck",
            author = "FamiTube Storybooks",
            description = "Count and splash along with a happy yellow duck in the sunny pond.",
            coverUrl = "https://images.unsplash.com/photo-1555854877-bab0e564b8d5?w=600&h=400&fit=crop",
            ageRating = "TODDLER",
            language = "en",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "One yellow duck swims today.", "https://images.unsplash.com/photo-1555854877-bab0e564b8d5?w=720&h=440&fit=crop", "🦆💧", "#0C4A6E"),
                StoryPageDto(2, "Two green frogs hop and play.", "https://images.unsplash.com/photo-1579380656108-f98e4df8ea62?w=720&h=440&fit=crop", "🐸🌿", "#064E3B"),
                StoryPageDto(3, "Splash splash goes the cool water.", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=720&h=440&fit=crop", "💦🌊", "#155E75"),
                StoryPageDto(4, "Three happy friends wave goodbye.", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=720&h=440&fit=crop", "🦆🐸👋", "#1E3A8A")
            )
        ),
        StoryBookDto(
            id = "story-tod-id-1",
            title = "Kucing Kecil Suka Susu",
            author = "Cerita FamiTube",
            description = "Cerita bergambar lembut tentang kucing kecil yang mencari mangkuk susu hangat.",
            coverUrl = "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600&h=400&fit=crop",
            ageRating = "TODDLER",
            language = "id",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "Ini kucing kecil yang lucu.", "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=720&h=440&fit=crop", "🐱💛", "#4C1D95"),
                StoryPageDto(2, "Kucing minum susu putih hangat.", "https://images.unsplash.com/photo-1573865526739-10659fec78a5?w=720&h=440&fit=crop", "🥛🐾", "#1E3A8A"),
                StoryPageDto(3, "Lalu kucing main bola merah.", "https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=720&h=440&fit=crop", "🧶🔴", "#831843"),
                StoryPageDto(4, "Sekarang kucing tidur nyenyak.", "https://images.unsplash.com/photo-1548802673-380ab8ebc7b7?w=720&h=440&fit=crop", "🐱💤", "#312E81")
            )
        ),
        StoryBookDto(
            id = "story-tod-id-2",
            title = "Pelangi di Taman Kelinci",
            author = "Cerita FamiTube",
            description = "Mengenal warna pelangi bersama kelinci putih di taman bunga.",
            coverUrl = "https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?w=600&h=400&fit=crop",
            ageRating = "TODDLER",
            language = "id",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "Kelinci melompat di taman hijau.", "https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?w=720&h=440&fit=crop", "🐰🌱", "#065F46"),
                StoryPageDto(2, "Hujan turun pelan lalu berhenti.", "https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?w=720&h=440&fit=crop", "🌦️💧", "#0E7490"),
                StoryPageDto(3, "Lihat ada pelangi indah sekali.", "https://images.unsplash.com/photo-1507608616759-54f48f0af0ee?w=720&h=440&fit=crop", "🌈✨", "#4338CA"),
                StoryPageDto(4, "Kelinci tersenyum senang sekali.", "https://images.unsplash.com/photo-1452857297128-d9c29adba80b?w=720&h=440&fit=crop", "🐰🌸", "#701A75")
            )
        ),
        StoryBookDto(
            id = "story-yk-en-1",
            title = "Rocket Pup Visits the Red Planet",
            author = "FamiTube Storybooks",
            description = "Read aloud with Astro the puppy as he blasts off to explore Mars!",
            coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&h=400&fit=crop",
            ageRating = "YOUNG_KIDS",
            language = "en",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Astro the puppy put on his shiny silver helmet and climbed into his red rocket.", "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=720&h=440&fit=crop", "🐶🚀", "#1E1B4B"),
                StoryPageDto(2, "Three, two, one, blast off! The rocket zoomed past fluffy white clouds into space.", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=720&h=440&fit=crop", "☁️🛸", "#0F172A"),
                StoryPageDto(3, "Soon Astro landed softly on the dusty red rocks of Mars and wagged his tail.", "https://images.unsplash.com/photo-1614728894747-a83421e2b9c9?w=720&h=440&fit=crop", "🔴🪐", "#7C2D12"),
                StoryPageDto(4, "He found a friendly little rover robot taking pictures of bright sparkling stones.", "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=720&h=440&fit=crop", "🤖💎", "#1E3A8A"),
                StoryPageDto(5, "After sharing a snack, Astro flew home safely to tell his family all about Mars.", "https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?w=720&h=440&fit=crop", "🌍🏡", "#312E81")
            )
        ),
        StoryBookDto(
            id = "story-yk-en-2",
            title = "Maya and the Singing Treehouse",
            author = "FamiTube Storybooks",
            description = "Maya discovers a magical wooden treehouse where every leaf plays a musical note.",
            coverUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=600&h=400&fit=crop",
            ageRating = "YOUNG_KIDS",
            language = "en",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Maya climbed the wooden ladder to her cozy treehouse behind the garden.", "https://images.unsplash.com/photo-1448375240586-882707db888b?w=720&h=440&fit=crop", "🌳🪜", "#064E3B"),
                StoryPageDto(2, "When the warm breeze blew, the golden leaves began to hum a gentle tune.", "https://images.unsplash.com/photo-1502082553048-f009c37129b9?w=720&h=440&fit=crop", "🍃🎶", "#14532D"),
                StoryPageDto(3, "Two bluebirds perched on the window sill and whistled along with the song.", "https://images.unsplash.com/photo-1444464666168-49d633b86797?w=720&h=440&fit=crop", "🐦🎵", "#0C4A6E"),
                StoryPageDto(4, "Maya tapped a rhythm on her wooden desk and smiled at the forest orchestra.", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=720&h=440&fit=crop", "🥁🌿", "#3F6212"),
                StoryPageDto(5, "As the sun turned orange, Maya waved to the birds and promised to sing again tomorrow.", "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?w=720&h=440&fit=crop", "🌅💛", "#7C2D12")
            )
        ),
        StoryBookDto(
            id = "story-yk-id-1",
            title = "Petualangan Bima dan Penyu Laut",
            author = "Cerita FamiTube",
            description = "Bacakan dengan suara lantang petualangan Bima menolong penyu kecil kembali ke laut biru.",
            coverUrl = "https://images.unsplash.com/photo-1437622368342-7a3d73a34c8f?w=600&h=400&fit=crop",
            ageRating = "YOUNG_KIDS",
            language = "id",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Pagi itu Bima berjalan di pantai pasir putih bersama ayahnya yang ramah.", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=720&h=440&fit=crop", "🏖️👦", "#0E7490"),
                StoryPageDto(2, "Mereka melihat seekor penyu kecil yang tersangkut di antara ranting kayu.", "https://images.unsplash.com/photo-1437622368342-7a3d73a34c8f?w=720&h=440&fit=crop", "🐢🌿", "#115E59"),
                StoryPageDto(3, "Dengan hati hati Bima mengangkat ranting itu agar penyu bisa bergerak bebas.", "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=720&h=440&fit=crop", "🤲🌊", "#1E40AF"),
                StoryPageDto(4, "Penyu kecil berenang gembira menuju ombak biru yang jernih dan tenang.", "https://images.unsplash.com/photo-1582967788606-a171c1080cb0?w=720&h=440&fit=crop", "🐢💙", "#1D4ED8"),
                StoryPageDto(5, "Bima berjanji selalu menjaga kebersihan pantai demi semua hewan laut.", "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=720&h=440&fit=crop", "🌏✨", "#0F766E")
            )
        ),
        StoryBookDto(
            id = "story-yk-id-2",
            title = "Kereta Bintang Malam Hari",
            author = "Cerita FamiTube",
            description = "Naiklah ke kereta api bercahaya yang melaju melintasi awan malam penuh bintang.",
            coverUrl = "https://images.unsplash.com/photo-1474487548417-781cb71495f3?w=600&h=400&fit=crop",
            ageRating = "YOUNG_KIDS",
            language = "id",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Lampu kereta biru menyala terang saat malam tiba di stasiun desa.", "https://images.unsplash.com/photo-1474487548417-781cb71495f3?w=720&h=440&fit=crop", "🚂🌙", "#1E293B"),
                StoryPageDto(2, "Rara duduk di dekat jendela sambil memegang buku gambar kesayangannya.", "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=720&h=440&fit=crop", "👧📖", "#312E81"),
                StoryPageDto(3, "Kereta melaju melewati bukit hijau dan jembatan panjang di bawah bulan.", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=720&h=440&fit=crop", "🌉🌕", "#1E1B4B"),
                StoryPageDto(4, "Kunang kunang menari di luar kaca seperti ribuan bintang kecil.", "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=720&h=440&fit=crop", "✨🪲", "#3B0764"),
                StoryPageDto(5, "Kereta pun sampai di kota tujuan dengan selamat dan penuh senyuman.", "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=720&h=440&fit=crop", "🏙️😊", "#0F172A")
            )
        )
    )

    fun findById(id: String): StoryBookDto? = books.find { it.id == id }

    fun filter(ageRating: String?, language: String?, includeAllAges: Boolean): List<StoryBookDto> {
        val age = ageRating?.uppercase() ?: ""
        if (!includeAllAges && (age == "OLDER_KIDS" || age == "ALL")) return emptyList()
        return books.filter { b ->
            val ageOk = includeAllAges || age.isBlank() || when (age) {
                "TODDLER" -> b.ageRating == "TODDLER"
                "YOUNG_KIDS" -> b.ageRating == "YOUNG_KIDS" || b.ageRating == "TODDLER"
                else -> false
            }
            val langOk = language.isNullOrBlank() || language == "bilingual" || b.language.equals(language, ignoreCase = true)
            ageOk && langOk
        }.let { list ->
            if (!includeAllAges && age == "YOUNG_KIDS") {
                list.sortedBy { if (it.ageRating == "YOUNG_KIDS") 0 else 1 }
            } else list
        }
    }
}
