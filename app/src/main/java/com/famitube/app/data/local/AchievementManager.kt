package com.famitube.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.famitube.app.data.model.Achievement
import com.famitube.app.data.model.AchievementCategory
import com.famitube.app.data.model.AchievementProgress
import com.famitube.app.data.model.GamificationSummary
import com.famitube.app.data.model.KidAchievementsDto
import com.famitube.app.data.model.StarSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AchievementManager(
    context: Context,
    private val preferences: ParentalPreferences,
    private val starPointsManager: StarPointsManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("famitube_achievements_prefs", Context.MODE_PRIVATE)

    private val _unlockEvents = MutableSharedFlow<Achievement>(extraBufferCapacity = 8)
    val unlockEvents: SharedFlow<Achievement> = _unlockEvents.asSharedFlow()

    private val _summaryFlow = MutableStateFlow(buildSummary())
    val summaryFlow: StateFlow<GamificationSummary> = _summaryFlow.asStateFlow()

    companion object {
        private const val STARS_PER_LEVEL = 100

        val CATALOG: List<Achievement> = listOf(
            Achievement(
                id = "first_watch",
                title = "First Adventure",
                description = "Watch your very first video on FamiTube.",
                category = AchievementCategory.WATCHING,
                targetProgress = 1,
                starReward = 25,
                badgeEmoji = "🎬"
            ),
            Achievement(
                id = "video_explorer_5",
                title = "Video Explorer",
                description = "Watch 5 different videos.",
                category = AchievementCategory.WATCHING,
                targetProgress = 5,
                starReward = 50,
                badgeEmoji = "🚀"
            ),
            Achievement(
                id = "super_watcher_15",
                title = "Super Watcher",
                description = "Watch 15 different videos.",
                category = AchievementCategory.WATCHING,
                targetProgress = 15,
                starReward = 100,
                badgeEmoji = "🌟"
            ),
            Achievement(
                id = "category_explorer",
                title = "Category Explorer",
                description = "Watch videos from 3 different categories.",
                category = AchievementCategory.WATCHING,
                targetProgress = 3,
                starReward = 60,
                badgeEmoji = "🧭"
            ),
            Achievement(
                id = "first_favorite",
                title = "Heart Collector",
                description = "Save your first video to Favorites.",
                category = AchievementCategory.CURATING,
                targetProgress = 1,
                starReward = 25,
                badgeEmoji = "❤️"
            ),
            Achievement(
                id = "curator_5",
                title = "Mini Curator",
                description = "Save 5 videos to your Favorites.",
                category = AchievementCategory.CURATING,
                targetProgress = 5,
                starReward = 60,
                badgeEmoji = "💖"
            ),
            Achievement(
                id = "playlist_creator",
                title = "DJ Kid",
                description = "Create your first custom playlist.",
                category = AchievementCategory.CURATING,
                targetProgress = 1,
                starReward = 40,
                badgeEmoji = "🎵"
            ),
            Achievement(
                id = "channel_hopper",
                title = "Channel Hopper",
                description = "Visit 3 different creator channels.",
                category = AchievementCategory.LEARNING,
                targetProgress = 3,
                starReward = 40,
                badgeEmoji = "📺"
            ),
            Achievement(
                id = "safe_searcher",
                title = "Curious Mind",
                description = "Search for safe videos 3 times.",
                category = AchievementCategory.HABITS,
                targetProgress = 3,
                starReward = 30,
                badgeEmoji = "🔍"
            ),
            Achievement(
                id = "daily_streak_3",
                title = "3-Day Streak",
                description = "Visit FamiTube on 3 days in a row.",
                category = AchievementCategory.HABITS,
                targetProgress = 3,
                starReward = 100,
                badgeEmoji = "🔥"
            )
        )

        fun rankTitleForLevel(level: Int): String = when {
            level <= 1 -> "Curious Explorer"
            level == 2 -> "Star Watcher"
            level == 3 -> "Super Learner"
            level == 4 -> "Knowledge Champion"
            level == 5 -> "FamiTube Hero"
            else -> "Galactic Legend"
        }
    }

    private fun userPrefix(): String {
        val email = preferences.loggedInEmail.trim().lowercase()
        return if (email.isBlank()) "guest" else email
    }

    private fun key(name: String): String = "${userPrefix()}_$name"

    fun refreshSummary() {
        starPointsManager.refreshForCurrentUser()
        _summaryFlow.value = buildSummary()
    }

    fun getSummary(): GamificationSummary = _summaryFlow.value

    fun recordDailyVisit() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dateFormat.format(Date())
        val lastDate = prefs.getString(key("last_visit_date"), null)

        if (lastDate == today) {
            return
        }

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterday = dateFormat.format(yesterdayCal.time)
        val currentStreak = prefs.getInt(key("streak_days"), 0)
        val newStreak = if (lastDate == yesterday) currentStreak + 1 else 1

        prefs.edit()
            .putString(key("last_visit_date"), today)
            .putInt(key("streak_days"), newStreak)
            .apply()

        evaluateAndNotify()
    }

    fun recordVideoWatched(videoId: String, category: String?) {
        val watched = (prefs.getStringSet(key("watched_videos"), emptySet()) ?: emptySet()).toMutableSet()
        watched.add(videoId)

        val categories = (prefs.getStringSet(key("watched_categories"), emptySet()) ?: emptySet()).toMutableSet()
        if (!category.isNullOrBlank() && category != "All Videos") {
            categories.add(category.trim())
        }

        prefs.edit()
            .putStringSet(key("watched_videos"), watched)
            .putStringSet(key("watched_categories"), categories)
            .apply()

        evaluateAndNotify()
    }

    fun recordFavoritesCount(count: Int) {
        val prevMax = prefs.getInt(key("favorites_max"), 0)
        if (count > prevMax) {
            prefs.edit().putInt(key("favorites_max"), count).apply()
            evaluateAndNotify()
        }
    }

    fun recordChannelVisited(channelId: String) {
        if (channelId.isBlank()) return
        val visited = (prefs.getStringSet(key("visited_channels"), emptySet()) ?: emptySet()).toMutableSet()
        if (visited.add(channelId)) {
            prefs.edit().putStringSet(key("visited_channels"), visited).apply()
            evaluateAndNotify()
        }
    }

    fun recordPlaylistCreated() {
        val current = prefs.getInt(key("playlists_created"), 0)
        prefs.edit().putInt(key("playlists_created"), current + 1).apply()
        evaluateAndNotify()
    }

    fun recordSearchPerformed() {
        val current = prefs.getInt(key("searches_performed"), 0)
        prefs.edit().putInt(key("searches_performed"), current + 1).apply()
        evaluateAndNotify()
    }

    private fun evaluateAndNotify() {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        val newlyUnlocked = mutableListOf<Achievement>()

        for (achievement in CATALOG) {
            val unlockedKey = key("unlocked_at_${achievement.id}")
            val alreadyUnlocked = prefs.getLong(unlockedKey, 0L) > 0L
            if (!alreadyUnlocked && currentProgressFor(achievement.id) >= achievement.targetProgress) {
                editor.putLong(unlockedKey, now)
                newlyUnlocked.add(achievement)
            }
        }
        editor.apply()

        newlyUnlocked.forEach { achievement ->
            starPointsManager.awardStars(
                amount = achievement.starReward,
                source = StarSource.ACHIEVEMENT,
                description = "Badge: ${achievement.title}"
            )
        }

        _summaryFlow.value = buildSummary()
        newlyUnlocked.forEach { _unlockEvents.tryEmit(it) }
    }

    private fun currentProgressFor(achievementId: String): Int {
        return when (achievementId) {
            "first_watch", "video_explorer_5", "super_watcher_15" ->
                prefs.getStringSet(key("watched_videos"), emptySet())?.size ?: 0
            "category_explorer" ->
                prefs.getStringSet(key("watched_categories"), emptySet())?.size ?: 0
            "first_favorite", "curator_5" ->
                maxOf(
                    prefs.getInt(key("favorites_max"), 0),
                    preferences.getFavoriteIds().size
                )
            "playlist_creator" ->
                prefs.getInt(key("playlists_created"), 0)
            "channel_hopper" ->
                prefs.getStringSet(key("visited_channels"), emptySet())?.size ?: 0
            "safe_searcher" ->
                prefs.getInt(key("searches_performed"), 0)
            "daily_streak_3" ->
                prefs.getInt(key("streak_days"), 0)
            else -> 0
        }
    }

    private fun buildSummary(): GamificationSummary {
        var totalBadgeStars = 0
        var unlockedCount = 0

        val items = CATALOG.map { achievement ->
            val unlockedAt = prefs.getLong(key("unlocked_at_${achievement.id}"), 0L)
            val isUnlocked = unlockedAt > 0L
            val rawProgress = currentProgressFor(achievement.id)
            val clampedProgress = rawProgress.coerceAtMost(achievement.targetProgress)

            if (isUnlocked) {
                totalBadgeStars += achievement.starReward
                unlockedCount++
            }

            AchievementProgress(
                achievement = achievement,
                currentProgress = if (isUnlocked) achievement.targetProgress else clampedProgress,
                isUnlocked = isUnlocked,
                unlockedAtMillis = if (isUnlocked) unlockedAt else null
            )
        }

        val totalStarsEarned = maxOf(totalBadgeStars, starPointsManager.getTotalEarned())
        val level = (totalStarsEarned / STARS_PER_LEVEL) + 1
        val starsInLevel = totalStarsEarned % STARS_PER_LEVEL
        val streak = prefs.getInt(key("streak_days"), 0)

        return GamificationSummary(
            totalStarsEarned = totalStarsEarned,
            starBalance = starPointsManager.getBalance(),
            level = level,
            rankTitle = rankTitleForLevel(level),
            starsInCurrentLevel = starsInLevel,
            starsForNextLevel = STARS_PER_LEVEL,
            unlockedCount = unlockedCount,
            totalCount = CATALOG.size,
            currentStreakDays = streak,
            achievements = items
        )
    }

    fun applyRemoteSummary(remote: KidAchievementsDto) {
        starPointsManager.setSyncedBalance(remote.stars)
        val mappedAchievements = remote.achievements.map { dto ->
            val category = when {
                dto.id.contains("playlist") || dto.id.contains("curator") -> AchievementCategory.CURATING
                dto.id.contains("search") -> AchievementCategory.HABITS
                else -> AchievementCategory.WATCHING
            }
            AchievementProgress(
                achievement = Achievement(
                    id = dto.id,
                    title = dto.title,
                    description = dto.description,
                    category = category,
                    targetProgress = dto.target.coerceAtLeast(1),
                    starReward = dto.rewardStars,
                    badgeEmoji = dto.icon.ifBlank { "🌟" }
                ),
                currentProgress = dto.progress,
                isUnlocked = dto.unlocked,
                unlockedAtMillis = if (dto.unlocked) System.currentTimeMillis() else null
            )
        }
        val streak = prefs.getInt(key("streak_days"), 0)
        _summaryFlow.value = GamificationSummary(
            totalStarsEarned = remote.stars,
            starBalance = remote.stars,
            level = remote.level,
            rankTitle = remote.levelTitle,
            starsInCurrentLevel = remote.progressPercent.coerceIn(0, 100),
            starsForNextLevel = 100,
            unlockedCount = remote.unlockedCount,
            totalCount = remote.totalCount.coerceAtLeast(mappedAchievements.size),
            currentStreakDays = streak,
            achievements = mappedAchievements
        )
    }
}
