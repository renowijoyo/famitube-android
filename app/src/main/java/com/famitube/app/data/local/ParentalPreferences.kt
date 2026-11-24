package com.famitube.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.famitube.app.data.model.AddKidPlaylistItemRequest
import com.famitube.app.data.model.KidPlaylist
import com.famitube.app.data.model.KidPlaylistItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ParentalPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("famitube_parental_prefs", Context.MODE_PRIVATE)

    private val _ageFilterFlow = MutableStateFlow(prefs.getString(KEY_AGE_FILTER, "ALL") ?: "ALL")
    val ageFilterFlow: StateFlow<String> = _ageFilterFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "LIGHT") ?: "LIGHT")
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _favoriteIdsFlow = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())
    val favoriteIdsFlow: StateFlow<Set<String>> = _favoriteIdsFlow.asStateFlow()

    companion object {
        private const val KEY_PARENT_PIN = "parent_pin"
        private const val KEY_DAILY_LIMIT_MINS = "daily_limit_mins"
        private const val KEY_USED_SECONDS_TODAY = "used_seconds_today"
        private const val KEY_LAST_USAGE_DATE = "last_usage_date"
        private const val KEY_AGE_FILTER = "age_filter"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_FAVORITES = "favorite_videos_set"
        private const val KEY_HISTORY = "history_videos_list"
        private const val KEY_KID_PLAYLISTS = "kid_custom_playlists_json"
        private const val KEY_ATTENTION_TRACKING = "attention_tracking_enabled"
        private const val KEY_ATTENTION_PAUSE_SECONDS = "attention_pause_seconds"
        private const val KEY_ATTENTION_MIN_WATCH_PERCENT = "attention_min_watch_percent"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "LIGHT") ?: "LIGHT"
        set(value) {
            val normalized = value.uppercase().let {
                if (it in listOf("SYSTEM", "LIGHT", "DARK")) it else "LIGHT"
            }
            prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
            _themeModeFlow.value = normalized
        }

    var parentPin: String
        get() = prefs.getString(KEY_PARENT_PIN, "1234") ?: "1234"
        set(value) = prefs.edit().putString(KEY_PARENT_PIN, value).apply()

    var dailyLimitMinutes: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT_MINS, 45)
        set(value) = prefs.edit().putInt(KEY_DAILY_LIMIT_MINS, value).apply()

    var attentionTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_ATTENTION_TRACKING, true)
        set(value) = prefs.edit().putBoolean(KEY_ATTENTION_TRACKING, value).apply()

    var attentionPauseSeconds: Int
        get() = prefs.getInt(KEY_ATTENTION_PAUSE_SECONDS, 15)
        set(value) = prefs.edit().putInt(KEY_ATTENTION_PAUSE_SECONDS, value.coerceIn(5, 300)).apply()

    var attentionMinWatchPercent: Int
        get() = prefs.getInt(KEY_ATTENTION_MIN_WATCH_PERCENT, 70)
        set(value) = prefs.edit().putInt(KEY_ATTENTION_MIN_WATCH_PERCENT, value.coerceIn(10, 100)).apply()

    var selectedAgeFilter: String
        get() = prefs.getString(KEY_AGE_FILTER, "ALL") ?: "ALL"
        set(value) {
            prefs.edit().putString(KEY_AGE_FILTER, value).apply()
            _ageFilterFlow.value = value
        }

    fun isSimpleKidMode(): Boolean =
        selectedAgeFilter == "TODDLER" || selectedAgeFilter == "YOUNG_KIDS"

    var backendUrl: String
        get() = com.famitube.app.data.remote.NetworkModule.DEFAULT_BASE_URL
        set(_) { /* Hardcoded to production FamiTube API */ }

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var loggedInEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var loggedInName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var loggedInRole: String
        get() = prefs.getString(KEY_USER_ROLE, "CHILD") ?: "CHILD"
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value.uppercase()).apply()

    fun isParentAccount(): Boolean = loggedInRole.equals("PARENT", ignoreCase = true)

    fun clearAuthSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        return pin.trim() == parentPin
    }

    fun getUsedSecondsToday(): Int {
        checkAndResetDailyCounter()
        return prefs.getInt(KEY_USED_SECONDS_TODAY, 0)
    }

    fun addUsedWatchTime(seconds: Int) {
        checkAndResetDailyCounter()
        val current = getUsedSecondsToday()
        prefs.edit().putInt(KEY_USED_SECONDS_TODAY, current + seconds).apply()
    }

    fun isDailyLimitExceeded(): Boolean {
        if (dailyLimitMinutes <= 0) return false
        val usedMins = getUsedSecondsToday() / 60
        return usedMins >= dailyLimitMinutes
    }

    fun getRemainingMinutes(): Int {
        if (dailyLimitMinutes <= 0) return -1
        val remaining = dailyLimitMinutes - (getUsedSecondsToday() / 60)
        return if (remaining < 0) 0 else remaining
    }

    fun getRemainingSecondsToday(): Int {
        if (dailyLimitMinutes <= 0) return Int.MAX_VALUE
        val remaining = (dailyLimitMinutes * 60) - getUsedSecondsToday()
        return if (remaining < 0) 0 else remaining
    }

    private fun checkAndResetDailyCounter() {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_USAGE_DATE, "")
        if (todayStr != lastDate) {
            prefs.edit()
                .putString(KEY_LAST_USAGE_DATE, todayStr)
                .putInt(KEY_USED_SECONDS_TODAY, 0)
                .apply()
        }
    }

    // Favorites
    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(videoId: String): Boolean {
        val current = getFavoriteIds().toMutableSet()
        val isNowFav: Boolean
        if (current.contains(videoId)) {
            current.remove(videoId)
            isNowFav = false
        } else {
            current.add(videoId)
            isNowFav = true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        _favoriteIdsFlow.value = current.toSet()
        return isNowFav
    }

    fun isFavorite(videoId: String): Boolean {
        return getFavoriteIds().contains(videoId)
    }

    // History
    fun addToHistory(videoId: String) {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        val list = raw.split(",").filter { it.isNotBlank() && it != videoId }.toMutableList()
        list.add(0, videoId)
        val trimmed = list.take(50).joinToString(",")
        prefs.edit().putString(KEY_HISTORY, trimmed).apply()
    }

    fun getHistoryIds(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    // Kid Playlists / Library
    private val gson = Gson()

    fun getKidPlaylists(): List<KidPlaylist> {
        val json = prefs.getString(KEY_KID_PLAYLISTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<KidPlaylist>>() {}.type
            gson.fromJson<List<KidPlaylist>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveKidPlaylists(playlists: List<KidPlaylist>) {
        val json = gson.toJson(playlists)
        prefs.edit().putString(KEY_KID_PLAYLISTS, json).apply()
    }

    fun createKidPlaylist(title: String): KidPlaylist {
        val current = getKidPlaylists().toMutableList()
        val newPlaylist = KidPlaylist(
            id = "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            title = title.trim(),
            items = emptyList(),
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        )
        current.add(0, newPlaylist)
        saveKidPlaylists(current)
        return newPlaylist
    }

    fun addKidPlaylistItem(playlistId: String, item: AddKidPlaylistItemRequest): KidPlaylist? {
        val current = getKidPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        val target = current[index]
        if (target.items.any { it.key == item.key || (item.youtubeVideoId != null && it.youtubeVideoId == item.youtubeVideoId) || (item.videoId != null && it.videoId == item.videoId) }) {
            return target
        }
        val newItem = KidPlaylistItem(
            key = item.key.ifBlank { "item_${System.currentTimeMillis()}" },
            youtubeVideoId = item.youtubeVideoId,
            videoId = item.videoId,
            title = item.title,
            channelTitle = item.channelTitle,
            thumbnailUrl = item.thumbnailUrl,
            sourceType = item.sourceType,
            sourceUrl = item.sourceUrl,
            addedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        )
        val updated = target.copy(items = target.items + newItem)
        current[index] = updated
        saveKidPlaylists(current)
        return updated
    }

    fun removeKidPlaylistItem(playlistId: String, itemKey: String): KidPlaylist? {
        val current = getKidPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null
        val target = current[index]
        val updated = target.copy(items = target.items.filter { it.key != itemKey && it.videoId != itemKey && it.youtubeVideoId != itemKey })
        current[index] = updated
        saveKidPlaylists(current)
        return updated
    }

    fun deleteKidPlaylist(playlistId: String): Boolean {
        val current = getKidPlaylists().toMutableList()
        val removed = current.removeAll { it.id == playlistId }
        if (removed) saveKidPlaylists(current)
        return removed
    }
}
