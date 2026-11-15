package com.famitube.app.data.model

enum class AchievementCategory(val label: String) {
    WATCHING("Watching"),
    CURATING("Curating"),
    LEARNING("Learning"),
    HABITS("Habits")
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val targetProgress: Int,
    val xpReward: Int,
    val badgeEmoji: String
)

data class AchievementProgress(
    val achievement: Achievement,
    val currentProgress: Int,
    val isUnlocked: Boolean,
    val unlockedAtMillis: Long? = null
) {
    val progressFraction: Float
        get() = if (achievement.targetProgress <= 0) 1f
        else (currentProgress.toFloat() / achievement.targetProgress.toFloat()).coerceIn(0f, 1f)
}

data class GamificationSummary(
    val totalXp: Int,
    val level: Int,
    val rankTitle: String,
    val xpInCurrentLevel: Int,
    val xpForNextLevel: Int,
    val unlockedCount: Int,
    val totalCount: Int,
    val currentStreakDays: Int,
    val achievements: List<AchievementProgress>
) {
    val levelProgressFraction: Float
        get() = if (xpForNextLevel <= 0) 1f
        else (xpInCurrentLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)
}
