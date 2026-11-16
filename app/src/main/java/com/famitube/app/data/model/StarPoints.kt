package com.famitube.app.data.model

enum class StarSource(val label: String) {
    ACHIEVEMENT("Badge Reward"),
    PARENT_GIFT("Parent Gift"),
    VIDEO_REQUEST("Video Request"),
    REWARD_REDEMPTION("Reward"),
    OTHER("Other")
}

data class StarTransaction(
    val id: String,
    val amount: Int, // positive = earned, negative = spent
    val source: StarSource,
    val description: String,
    val timestampMillis: Long
)

object StarCosts {
    const val VIDEO_REQUEST = 10
}
