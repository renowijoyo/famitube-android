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

    suspend fun registerParent(
        name: String,
        email: String,
        password: String,
        referralCode: String? = null
    ): Result<String> {
        return try {
            val response = api.register(
                RegisterParentRequest(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    password = password,
                    referralCode = referralCode?.trim()?.ifBlank { null }
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                val msg = body?.message ?: "Registration successful! Please verify your email before logging in."
                Result.success(msg)
            } else {
                val errBody = response.errorBody()?.string()
                val errMsg = try {
                    Gson().fromJson(errBody, GenericAuthResponse::class.java)?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(Exception(errMsg ?: "Registration failed. Please check your details."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    fun getStarBalance(): Int = starPointsManager?.getBalance() ?: 0

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
        birthday: String? = null,
        religion: String? = null
    ): Result<ParentChildDto> {
        return try {
            val response = api.registerParentChild(
                RegisterChildRequest(
                    email = email.trim().lowercase(),
                    name = name.trim(),
                    password = password,
                    birthday = birthday?.trim()?.takeIf { it.isNotEmpty() },
                    ageRating = ageRating,
                    religion = religion?.trim()?.takeIf { it.isNotEmpty() && it != "NONE" }
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
            id = "pb-en-0001",
            title = "Smile Please!",
            author = "Sanjiv Jaiswal 'Sanjay' • Illus. Ajit Narayan (Pratham Books / CC BY 4.0)",
            description = "A fawn was racing in the forest.",
            coverUrl = "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/01.jpg",
            ageRating = "TODDLER",
            language = "en",
            category = "Stories & Reading",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "A fawn was racing in the forest.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/01.jpg", "📖✨", "#1E293B"),
                StoryPageDto(2, "He was ahead of the rabbit.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/02.jpg", "📖✨", "#0F172A"),
                StoryPageDto(3, "He was ahead of the elephant.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/03.jpg", "📖✨", "#1E3A8A"),
                StoryPageDto(4, "He leapt and cleared the stream.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/04.jpg", "📖✨", "#0C4A6E"),
                StoryPageDto(5, "He ran past the crumbling wall.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/05.jpg", "📖✨", "#064E3B"),
                StoryPageDto(6, "There was a large boulder on the grassy plain. He stumbled and fell down.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/06.jpg", "📖✨", "#14532D"),
                StoryPageDto(7, "He burst into tears.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/07.jpg", "📖✨", "#312E81"),
                StoryPageDto(8, "The monkey massaged his leg. Tears flowed from the fawn's eyes.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/08.jpg", "📖✨", "#4C1D95"),
                StoryPageDto(9, "Brother Bear picked him up. The fawn didn't stop crying.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/09.jpg", "📖✨", "#581C87"),
                StoryPageDto(10, "His mother came. She said, “Look, we'll beat up this bad boulder!”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/10.jpg", "📖✨", "#701A75"),
                StoryPageDto(11, "The fawn said, “Oh, don't do that or he will also start crying.” His mother laughed. So did the fawn.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0001/11.jpg", "📖✨", "#7C2D12")
            )
        ),
        StoryBookDto(
            id = "pb-en-0002",
            title = "Fat King Thin Dog",
            author = "Parismita (Pratham Books / CC BY 4.0)",
            description = "This is a fat king.",
            coverUrl = "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/01.jpg",
            ageRating = "TODDLER",
            language = "en",
            category = "Stories & Reading",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "This is a fat king.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/01.jpg", "📖✨", "#1E293B"),
                StoryPageDto(2, "The fat king has a thin dog. The fat king and his thin dog go for a walk.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/02.jpg", "📖✨", "#0F172A"),
                StoryPageDto(3, "The dog sees a bird. The dog runs after the bird.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/03.jpg", "📖✨", "#1E3A8A"),
                StoryPageDto(4, "The king runs after the dog.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/04.jpg", "📖✨", "#0C4A6E"),
                StoryPageDto(5, "They run and run. They run and run for many days.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/05.jpg", "📖✨", "#064E3B"),
                StoryPageDto(6, "The king catches the dog.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/06.jpg", "📖✨", "#14532D"),
                StoryPageDto(7, "Now the fat king is thin.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0002/07.jpg", "📖✨", "#312E81")
            )
        ),
        StoryBookDto(
            id = "pb-en-0003",
            title = "Vayu, the Wind",
            author = "Madhuri Pai • Illus. Rijuta Ghate (Pratham Books / CC BY 4.0)",
            description = "Everytime I finish my hot, hot bath, My wet body feels so cool, cool cool. Who makes that happen? Vayu the Wind!",
            coverUrl = "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/01.jpg",
            ageRating = "YOUNG_KIDS",
            language = "en",
            category = "Stories & Reading",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Everytime I finish my hot, hot bath, My wet body feels so cool, cool cool. Who makes that happen? Vayu the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/01.jpg", "📖✨", "#1E293B"),
                StoryPageDto(2, "The milk in my cup is too hot, too hot. But soon it is ready for me to gulp. Who makes that happen? Vayu the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/02.jpg", "📖✨", "#0F172A"),
                StoryPageDto(3, "The window curtains flutter And gently brush my face. Who makes it happen? Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/03.jpg", "📖✨", "#1E3A8A"),
                StoryPageDto(4, "A bolt of lightning far away. Black clouds moving my way. Who makes that happen? Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/04.jpg", "📖✨", "#0C4A6E"),
                StoryPageDto(5, "Branches sway and leaves tremble. Flowers gently fall. Who did it all? Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/05.jpg", "📖✨", "#064E3B"),
                StoryPageDto(6, "Far from the house, we are playing, Yet, I can smell the sweets mother is preparing. Who makes it happen? Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/06.jpg", "📖✨", "#14532D"),
                StoryPageDto(7, "A glass tumbler on a window sill, Crashes on the ground. Thank God, I was not around. Who played this mischief? Of course, it was Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/07.jpg", "📖✨", "#312E81"),
                StoryPageDto(8, "A whistle blows. A train rolls in. I cannot see, but I hear its din. Who makes that happen? Vayu, the Wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/08.jpg", "📖✨", "#4C1D95"),
                StoryPageDto(9, "Cannot be seen Cannot be heard Does all the work Without a word. Who can it be?", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/09.jpg", "📖✨", "#581C87"),
                StoryPageDto(10, "Of course! The wind! Vayu, the wind!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0003/10.jpg", "📖✨", "#701A75")
            )
        ),
        StoryBookDto(
            id = "pb-en-0004",
            title = "Chuskit Goes to School!",
            author = "Sujatha Padmanabhan • Illus. Madhuvanti Anantharajan (Pratham Books / CC BY 4.0)",
            description = "Chuskit woke up early that morning. It was a very special day and she was too excited to sleep. She eagerly looked out of the room through t...",
            coverUrl = "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/01.jpg",
            ageRating = "YOUNG_KIDS",
            language = "en",
            category = "Stories & Reading",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Chuskit woke up early that morning. It was a very special day and she was too excited to sleep. She eagerly looked out of the room through the window next to her bed. It was spring time in Ladakh and the apricot trees were in full bloom. Two magpies had already begun their day and were busy looking for insects to eat. Her Ama-ley was awake too. She could hear her in the kitchen making gur-gur tea.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/01.jpg", "📖✨", "#1E293B"),
                StoryPageDto(2, "Chuskit had been awake for the last hour. This was a day she was going to remember for a long time. Can you guess why? No, it was not Losar, the New Year festival. That was many months away. Nor was it any special day in her village, like the gonpa festival, or a wedding day. Today was going to be Chuskit's first day at school. At nine years, she had waited a long, long time for this.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/02.jpg", "📖✨", "#0F172A"),
                StoryPageDto(3, "The school was not very far away from her home. To get there, you had to walk up to the main road. Just before the prayer wheel, you took the path to the left of the road that ran along a narrow stream. Near the poplar trees you crossed the stream by jumping over the big rocks. Once you got to the other side, a short walk up a slope took you to the school. All the children of Skitpo Yul, Chuskit's village, walked to school everyday and with ease. But not little Chuskit. She had a disability and could not walk.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/03.jpg", "📖✨", "#1E3A8A"),
                StoryPageDto(4, "Chuskit was born with legs that did not work like everyone else's. Her father had taken her to the village amchi and then to the doctors in Leh. But no amount of medicine had helped her walk. At first, Chuskit did not realise that she was in any way different from the Stobdan her younger brother, or her cousins. But soon there were many things she could not do as easily as them. “That does not matter,” her Aba-ley would tell her whenever she felt sad. “You can stitch better than any of them. And you draw very well.” Aba-ley often brought her some colour pencils from Leh.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/04.jpg", "📖✨", "#0C4A6E"),
                StoryPageDto(5, "Chuskit would sit at the kitchen window everyday and draw what she saw while her Ama-ley cooked. She could see her family's animals being taken to graze every morning. She could see the blue waters of the stream that flowed near by. And she would be the first one to announce the arrival of a visitor to their house!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/05.jpg", "📖✨", "#064E3B"),
                StoryPageDto(6, "Chuskit used a chair fitted with wheels to move around. Elders called it a wheelchair. You could move it in any direction- forward, left, right and even backward - by moving the wheels with your hands. You only needed to have really strong arms to push the weight of your body and the chair. When Aba-ley first brought the wheelchair home, everyone in her family was very excited.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/06.jpg", "📖✨", "#14532D"),
                StoryPageDto(7, "“Now I won't have to carry Chuskit everywhere. I can wheel her around,” said Ama-ley, with tears of happiness in her eyes. “And I will be able to go wherever I want!” cried Chuskit excitedly. “Please, please, could I ride on your wheelchair once in a way?” begged Stobdan. He wanted to wheel it around too. It looked like fun. Billa, the big black cat, jumped on to the wheel-chair and stretched across the seat. “Yes this is far more comfortable than the sack cloth I sleep on,” she purred.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/07.jpg", "📖✨", "#312E81"),
                StoryPageDto(8, "Slowly, Chuskit learnt to use her wheelchair. She would ask her mother to seat her in the wheelchair outside her home every evening. From here she would watch children returning from school, chattering and laughing merrily in groups. In a while, the evening bus would return from Leh, bringing back people from work and the market place. How much more exciting the world was from here than from the kitchen window!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/08.jpg", "📖✨", "#4C1D95"),
                StoryPageDto(9, "One evening when Chuskit was sitting outside the house with her grandfather, a young boy came up to her. He carried a letter which he handed over to her grandfather. “Julley! The bus driver asked me to give this to you,” he said. “My name is Abdul,” he said turning to Chuskit. “I study Class 6 at the Government School. I have often wondered why you don't come to school.” “Julley, Abdul,” said Chuskit. “I have never been to school. The path to the school is too uneven and pebbly and my wheelchair will get stuck. Besides, I cannot cross the little stream in front of the school. It would be very difficult for my parents to carry me to school and back everyday.” “But would you like to come to school?” asked Abdul.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/09.jpg", "📖✨", "#581C87"),
                StoryPageDto(10, "“Of course!” replied Chuskit. “I hear my younger brother talk of all that he does at school. I want to study like all of you, make friends, play games, wear a uniform and even write exams. Sometimes my brother teaches me songs that he has learnt at school and I love it. You may not believe this, but I sometimes dream of carrying a school bag, even eating packed lunch...” “Enough! Enough!” interrupted her grandfather. “Stop dreaming, Chuskit. You know you cannot go to school. I have told you this many times. Learn as much as you can at home.”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/10.jpg", "📖✨", "#701A75"),
                StoryPageDto(11, "“Meme-ley, please,” pleaded Chuskit softly with tears in her eyes as her grandfather walked away angrily. “Meme-ley does not understand how I feel,” said Chuskit. “I remember the day my brother recited his tables with such pride, even the time he learned to read! My parents were very happy as they have never been to school. I too want to learn to read and do math. Do you understand?” “I do,” said Abdul. “Chuskit, I will see you again. I must go now. My mother will be waiting for me. Julley!”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/11.jpg", "📖✨", "#7C2D12"),
                StoryPageDto(12, "The next day, Abdul went straight to the Headmaster's room after assembly. “Julley, Azhang-ley!” he said. “I want to talk to you about a girl from our village who does not go to school. Her name is Chuskit. She is Stobdan's sister.” “Oh yes,” replied the Headmaster. “I know her. She is disabled, isn't she?”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/12.jpg", "📖✨", "#78350F"),
                StoryPageDto(13, "“Yes, Azhang-ley. She uses a wheelchair to move around but cannot reach school, as the path from her home is too rough and uneven. I was wondering if we could do anything to help her, Azhang-ley. We could all get together and level the path and also build a small bridge across the stream.” “That's very thoughtful of you, Abdul,” said the Headmaster as he patted him on the back. “What made you think of Chuskit and her problems?” “Azhang-ley, last week we read about the fundamental rights of citizens in our civics class. All children have a right to education. That includes Chuskit as well, does it not?” “Yes, you are right, Abdul. Let me discuss this with the other teachers,” replied the Headmaster.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/13.jpg", "📖✨", "#1F2937"),
                StoryPageDto(14, "The next day the Headmaster called all the teachers to a meeting. He shared Abdul's ideas with the group and asked the teachers what they felt. “Impossible!” said one of them. “How can we have a disabled child in our school? How will she be able to write, play and go to the toilet like the other children do?”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/14.jpg", "📖✨", "#1E293B"),
                StoryPageDto(15, "“That's exactly what we have to workout,” said the Headmaster. “I hear that in the village of Mentok Yul, the Village Education Committee has helped build a special toilet for one of their disabled children. We could ask them what they have done. But first we will have to get Chuskit to school. Then we could think of ways of helping her do things in school.”", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/15.jpg", "📖✨", "#0F172A"),
                StoryPageDto(16, "Two weeks later there was great excitement at the Government School. All the children had gathered outside in the playground. They had come to school without their school bags: there were going to be no classes today!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/16.jpg", "📖✨", "#1E3A8A"),
                StoryPageDto(17, "The teachers divided the children into groups. One group worked in front of Chuskit's house and another on the path along the stream. A third group with some of the older children worked hard to help some of their teachers build a strong wooden bridge across the stream.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/17.jpg", "📖✨", "#0C4A6E"),
                StoryPageDto(18, "The children laughed and sang as they removed stones and rocks, levelled paths and carried wooden planks to the stream. The Headmaster went from one group to another, making sure that all went according to plan.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/18.jpg", "📖✨", "#064E3B"),
                StoryPageDto(19, "Chuskit's parents provided everyone with hot tea and biscuits. Chuskit's grandfather sat under a willow tree by the stream watching the busy school children. “I never dreamt I'd see a day like this!” he thought to himself as he wiped a tear that rolled down his cheek. At the end of the day it was a tired but happy group of children that returned to their homes. The path from Chuskit's home to the school was now ready!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/19.jpg", "📖✨", "#14532D"),
                StoryPageDto(20, "Chuskit was going to school for the first time in her life. And that's why she was so excited!", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/20.jpg", "📖✨", "#312E81"),
                StoryPageDto(21, "Words used in the story that may be new to you: Aba-ley: Father Ama-ley: Mother Meme-ley: Grandfather Amchi: A local doctor who has been trained in Tibetan medicine. Azhang-ley: Term used with respect for an elderly man. Gonpa: Buddhist monastery Gur-gur tea: Butter tea that is made in a long cylindrical wooden vessel. The tea is churned with butter, salt and milk in this vessel. A low gur-gur noise is produced during churning. Julley: Term used as a greeting: hello, goodbye.", "https://raw.githubusercontent.com/global-asp/pb-imagebank/master/medium/0004/21.jpg", "📖✨", "#4C1D95")
            )
        ),
        StoryBookDto(
            id = "cr-id-asal-mula-batu-kuwung",
            title = "Asal Mula Batu Kuwung",
            author = "Cerita Rakyat Jawa (Kemdikbudristek / Warisan Budaya Nusantara)",
            description = "Cerita rakyat nusantara dari daerah Jawa: Dahulu, ada seorang Saudagar yang kaya raya. Saudagar ini perilakunya buruk. Ia sombong dan kikir. Karena budi pekertinya yang buruk pendudu...",
            coverUrl = "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_13117a81.jpg",
            ageRating = "YOUNG_KIDS",
            language = "id",
            category = "Stories & Reading",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Dahulu, ada seorang Saudagar yang kaya raya. Saudagar ini perilakunya buruk. Ia sombong dan kikir. Karena budi pekertinya yang buruk penduduk desa sangat membencinya. Pada suatu hari, sang Saudagar kedatangan pengemis berkaki pincangmeminta makanan. Bukannya memberi, Saudagar itu malah menghardik dan mencaci maki. Si Pengemis didorong oleh saudagar hingga jatuh tersungkur. Mendapat perlakuan seperti itu, si Pengemis pun marah.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_13117a81.jpg", "🇮🇩📜", "#1E293B"),
                StoryPageDto(2, "Keesokan harinya, ketika Saudagar bangun dari tidur, kedua kakinya susah digerakkan. Ia tak mampu bangkit dari kasurnya. Ia perintahkan kepada pengawalnya mencari tabib untuk mengobati penyakitnya. Namun, tak satu pun tabib yang berhasil mengobatinya. Saudagar itu pun berjanji bahwa ia akan memberikan setengah dari harta kekayaannya kepada siapa saja yang dapat menyembuhkan penyakitnya.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_88f8c859.jpg", "🇮🇩📜", "#0F172A"),
                StoryPageDto(3, "Mendengar hal itu, si Pengemis datang kembali menjelaskan apa yang menjadi penyebab lumpuhnya kaki Saudagar tersebut. “Musibah yang menimpa dirimu disebabkan oleh sifatmu yang sombong dan kikir. Ada beberapa syarat jika kau ingin sembuh.. Pertama, harus rendah hati dan pemurah. Kedua, pergilah bertapa diatas batu cekung selama tujuh hari tujuh malam. Ketiga, penuhi janjimu untuk membagi separuh kekayaanmu kepada orang miskin disekitar rumahmu”.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_f95f30e8.jpg", "🇮🇩📜", "#1E3A8A"),
                StoryPageDto(4, "Dengan dibantu oleh pelayannya berangkatlah sang Saudagar untuk bertapa di atas batu cekung. Pada hari terakhir pertapaan, kejaiban terjadi. Dari pusat batu cekung menyembur sumber mata air panas. Saudagar itu menghentikan pertapaannya, ia mandi dengan sumber air panas tersebut. Sungguh aneh kedua kakinya yang lumpuh dapat digerakkan kembali. Setelah berendam agak lama, ia pun dapat berjalan dengan normal.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_af6098fe.jpg", "🇮🇩📜", "#0C4A6E"),
                StoryPageDto(5, "Setelah yakin sembuh pulih seperti sedia kala, saudagar itu kembali ke rumahnya. Ia memenuhi janjinya, membagi-bagikan separuh harta kepada orang-orang miskin disekitar tempat tinggalnya. Orang-orang yang dulu membencinya berbalik menyukainya. Perdagangannya semakin lancar, ia bertambah kaya raya. Penduduk setempat menyebut istilah cekung dengan Kuwung, maka Batu Cekung yang telah menjadi sebab kesembuhan si Saudagar disebut Batu Kuwung.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Asal%20Mula%20Batu%20Kuwung/i_e79cb85adcbe0901_html_13117a81.jpg", "🇮🇩📜", "#064E3B")
            )
        ),
        StoryBookDto(
            id = "cr-id-cindelaras",
            title = "Cindelaras",
            author = "Cerita Rakyat Jawa (Kemdikbudristek / Warisan Budaya Nusantara)",
            description = "Cerita rakyat nusantara dari daerah Jawa: Raden Putra adalah raja Kerajaan Jenggala. Ia didampingi seorang permaisuri yang baik hati dan seorang selir yang cantik jelita. Tetapi, sel...",
            coverUrl = "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_77515527.jpg",
            ageRating = "YOUNG_KIDS",
            language = "id",
            category = "Stories & Reading",
            rewardFamibux = 30,
            pages = listOf(
                StoryPageDto(1, "Raden Putra adalah raja Kerajaan Jenggala. Ia didampingi seorang permaisuri yang baik hati dan seorang selir yang cantik jelita. Tetapi, selir Raja Raden Putra memiliki sifat iri dan dengki terhadap sang permaisuri. Ia merencanakan suatu yang buruk kepada permaisuri.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_77515527.jpg", "🇮🇩📜", "#1E293B"),
                StoryPageDto(2, "Selir baginda, berkomplot dengan seorang tabib istana. Ia berpura-pura sakit parah. Tabib istana segera dipanggil. Sang tabib mengatakan bahwa ada seseorang yang telah menaruh racun dalam minuman tuan putri.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_de39e254.jpg", "🇮🇩📜", "#0F172A"),
                StoryPageDto(3, "“ Orang itu tak lain adalah permaisuri Baginda sendiri,” kata sang tabib. Baginda menjadi murka mendengar penjelasan tabib istana. Ia segera memerintahkan patihnya untuk membuang permaisuri ke hutan. Sang patih segera membawa permaisuri yang sedang mengandung itu ke hutan belantara. Tapi, patih yang bijak itu tidak mau membunuhnya. Rupanya sang patih sudah mengetahui niat jahat selir baginda.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_923fb6e3.jpg", "🇮🇩📜", "#1E3A8A"),
                StoryPageDto(4, "Setelah beberapa bulan berada di hutan, lahirlah anak sang permaisuri. Bayi itu diberinya nama Cindelaras. Cindelaras tumbuh menjadi seorang anak yang cerdas dan tampan. Sejak kecil ia sudah berteman dengan binatang penghuni hutan. Suatu hari, ketika sedang asyik bermain, Cindelaras menemukan sebuah telur.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_adc9af4a.jpg", "🇮🇩📜", "#0C4A6E"),
                StoryPageDto(5, "Setelah 3 minggu, telur itu menetas. Cindelaras memelihara anak ayamnya dengan rajin. Anak ayam itu tumbuh menjadi seekor ayam jantan yang bagus dan kuat.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_6148ae74.jpg", "🇮🇩📜", "#064E3B"),
                StoryPageDto(6, "Tapi ada satu keanehan. Bunyi kokok ayam jantan itu sungguh menakjubkan! “Kukuruyuk… Tuanku Cindelaras, rumahnya di tengah rimba, atapnya daun kelapa, ayahnya Raden Putra…” Cindelaras sangat takjub mendengar kokok ayamnya dan segera memperlihatkan pada ibunya.", "https://raw.githubusercontent.com/trionoputra/cerita-rakyat/master/app/src/main/assets/Jawa/Cindelaras/i_b5e81cc34a096363_html_a229f1d7.jpg", "🇮🇩📜", "#14532D")
            )
        ),
        StoryBookDto(
            id = "fabel-kancil-buaya",
            title = "Si Kancil dan Buaya",
            author = "Fabel Nusantara • Cerita Tradisional Indonesia (Public Domain)",
            description = "Kisah kecerdikan Kancil saat ingin menyeberangi sungai deras yang dipenuhi buaya lapar.",
            coverUrl = "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=720&h=440&fit=crop",
            ageRating = "TODDLER",
            language = "id",
            category = "Stories & Reading",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "Kancil merasa sangat lapar di tepi sungai yang rimbun.", "https://images.unsplash.com/photo-1535268647677-300dbf3d78d1?w=720&h=440&fit=crop", "🦌🌿", "#064E3B"),
                StoryPageDto(2, "Di seberang sungai tampak banyak pohon berbuah manis dan segar.", "https://images.unsplash.com/photo-1448375240586-882707db888b?w=720&h=440&fit=crop", "🍎🌳", "#14532D"),
                StoryPageDto(3, "Kancil memanggil para buaya untuk berbaris rapi dari tepi ke tepi.", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=720&h=440&fit=crop", "🐊🌊", "#0C4A6E"),
                StoryPageDto(4, "Satu, dua, tiga, Kancil melompat gembira melintasi punggung buaya.", "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=720&h=440&fit=crop", "✨🐾", "#1E3A8A"),
                StoryPageDto(5, "Sampai di seberang, Kancil makan buah kenyang dan berterima kasih.", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=720&h=440&fit=crop", "🍉😊", "#1E293B")
            )
        ),
        StoryBookDto(
            id = "fabel-kancil-kura",
            title = "Kancil dan Kura-Kura",
            author = "Fabel Nusantara • Dongeng Budi Pekerti (Public Domain)",
            description = "Pelajaran berharga tentang ketekunan kura-kura yang tidak pernah putus asa.",
            coverUrl = "https://images.unsplash.com/photo-1437622368342-7a3d73a34c8f?w=720&h=440&fit=crop",
            ageRating = "TODDLER",
            language = "id",
            category = "Stories & Reading",
            rewardFamibux = 20,
            pages = listOf(
                StoryPageDto(1, "Kancil yang lincah menantang Kura-kura untuk berlomba lari.", "https://images.unsplash.com/photo-1535268647677-300dbf3d78d1?w=720&h=440&fit=crop", "🦌💨", "#1E293B"),
                StoryPageDto(2, "Kura-kura melangkah perlahan namun mantap tanpa henti.", "https://images.unsplash.com/photo-1437622368342-7a3d73a34c8f?w=720&h=440&fit=crop", "🐢🌿", "#064E3B"),
                StoryPageDto(3, "Karena sombong, Kancil tertidur lelap di bawah pohon beringin teduh.", "https://images.unsplash.com/photo-1511497584788-87676104235f?w=720&h=440&fit=crop", "😴🌳", "#312E81"),
                StoryPageDto(4, "Kura-kura terus berjalan hingga akhirnya menyentuh garis akhir terlebih dahulu.", "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=720&h=440&fit=crop", "🏆🐢", "#0F172A")
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
