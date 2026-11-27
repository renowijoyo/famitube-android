package com.famitube.app.data.model

import com.google.gson.annotations.SerializedName

enum class SourceType {
    @SerializedName("YOUTUBE")
    YOUTUBE,

    @SerializedName("DIRECT")
    DIRECT
}

enum class AgeRating(val label: String) {
    @SerializedName("ALL")
    ALL("All Ages"),

    @SerializedName("TODDLER")
    TODDLER("Toddlers (2-4)"),

    @SerializedName("YOUNG_KIDS")
    YOUNG_KIDS("Young Kids (5-8)"),

    @SerializedName("OLDER_KIDS")
    OLDER_KIDS("Older Kids (9-12)")
}

data class Video(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("sourceType") val sourceType: SourceType,
    @SerializedName("sourceUrl") val sourceUrl: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("duration") val duration: Int = 180, // in seconds
    @SerializedName("channelId") val channelId: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("channelAvatar") val channelAvatar: String,
    @SerializedName("category") val category: String,
    @SerializedName("categories") val categories: List<String> = emptyList(),
    @SerializedName("ageRating") val ageRating: AgeRating = AgeRating.ALL,
    @SerializedName("isFeatured") val isFeatured: Boolean = false,
    @SerializedName("isApproved") val isApproved: Boolean = true,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("createdAt") val createdAt: String = ""
)

data class VideoDetailResponse(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("sourceType") val sourceType: SourceType,
    @SerializedName("sourceUrl") val sourceUrl: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("duration") val duration: Int = 180,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("channelAvatar") val channelAvatar: String,
    @SerializedName("category") val category: String,
    @SerializedName("categories") val categories: List<String> = emptyList(),
    @SerializedName("ageRating") val ageRating: AgeRating = AgeRating.ALL,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("relatedVideos") val relatedVideos: List<Video> = emptyList()
)

val CATEGORY_TAG_MAP: Map<String, List<String>> = mapOf(
    "Cartoons & Fun" to listOf("category:cartoons & fun", "cartoons & fun", "topic:animation-cartoons", "topic:comedy-clean", "cartoon", "cartoons", "animation", "animated", "comedy", "funny", "puppet", "puppets", "toons"),
    "Learning & ABCs" to listOf("category:learning & abcs", "learning & abcs", "topic:abcs-phonics", "topic:language-learning", "abc", "abcs", "alphabet", "phonics", "letters", "colors", "shapes", "preschool", "spelling", "vocabulary", "counting", "numbers"),
    "Music & Rhymes" to listOf("category:music & rhymes", "music & rhymes", "topic:music-instruments", "purpose:background-audio", "music", "rhyme", "rhymes", "nursery rhyme", "nursery rhymes", "song", "songs", "sing-along", "singalong", "lullaby", "lullabies"),
    "Animals & Nature" to listOf("category:animals & nature", "animals & nature", "topic:animals-nature", "animal", "animals", "nature", "wildlife", "ocean", "reef", "marine", "zoo", "pets", "pet", "dino", "dinosaur", "dinosaurs", "birds", "insects", "jungle", "safari", "bunny", "puppy", "kitten"),
    "Art & Crafts" to listOf("category:art & crafts", "art & crafts", "topic:art-drawing", "topic:crafts-diy", "art", "arts", "craft", "crafts", "drawing", "draw", "painting", "paint", "origami", "coloring", "clay", "illustration"),
    "Science & Space" to listOf("category:science & space", "science & space", "topic:science", "topic:space-astronomy", "science", "space", "astronomy", "planet", "planets", "rocket", "rockets", "nasa", "physics", "chemistry", "biology", "experiment", "experiments", "solar system", "astronaut"),
    "Stories & Reading" to listOf("category:stories & reading", "stories & reading", "topic:reading-books", "purpose:story-narrative", "story", "stories", "reading", "read-aloud", "book", "books", "fairytale", "fairy tale", "bedtime story", "fable", "storytelling"),
    "Math & Coding" to listOf("category:math & coding", "math & coding", "topic:math-logic", "topic:coding-tech", "math", "maths", "counting", "numbers", "addition", "subtraction", "multiplication", "geometry", "logic", "coding", "programming", "computer science", "robotics", "stem"),
    "Kids Yoga & Active" to listOf("category:kids yoga & active", "kids yoga & active", "topic:physical-fitness", "topic:mindfulness-breathing", "context:movement-break", "yoga", "fitness", "exercise", "workout", "stretching", "mindfulness", "breathing", "dance", "movement", "active"),
    "World & Discovery" to listOf("category:world & discovery", "world & discovery", "topic:history-geography", "world", "discovery", "geography", "history", "countries", "culture", "cultures", "earth", "continents", "travel", "landmarks", "civilization"),
    "Video Games & Gaming" to listOf("category:video games & gaming", "video games & gaming", "topic:gaming-wholesome", "gaming", "video game", "video games", "minecraft"),
    "Hobbies & DIY" to listOf("category:hobbies & diy", "hobbies & diy", "topic:crafts-diy", "topic:engineering-robotics", "hobby", "hobbies", "diy", "maker", "lego", "building", "engineering"),
    "Sports & Outdoors" to listOf("category:sports & outdoors", "sports & outdoors", "topic:sports-outdoors", "sport", "sports", "soccer", "football", "basketball", "swimming", "outdoors", "camping", "hiking"),
    "Cooking & Baking" to listOf("category:cooking & baking", "cooking & baking", "topic:cooking-baking", "cooking", "baking", "recipe", "recipes", "kitchen", "chef", "cupcakes")
)

fun Video.resolvedCategories(): List<String> {
    if (categories.isNotEmpty()) return categories
    val tagSet = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
    if (category.isNotBlank() && !category.equals("All Videos", ignoreCase = true)) {
        tagSet.add("category:${category.trim().lowercase()}")
    }
    val matched = CATEGORY_TAG_MAP.entries
        .filter { (catName, aliases) ->
            val lowerCat = catName.lowercase()
            tagSet.contains("category:$lowerCat") ||
                tagSet.contains(lowerCat) ||
                aliases.any { it in tagSet }
        }
        .map { it.key }
    return if (matched.isNotEmpty()) matched
    else if (category.isNotBlank() && !category.equals("All Videos", ignoreCase = true)) listOf(category)
    else emptyList()
}

fun Video.matchesCategory(targetCategory: String): Boolean {
    val clean = targetCategory.trim()
    if (clean.isEmpty() || clean.equals("All Videos", ignoreCase = true)) return true
    if (resolvedCategories().any { it.equals(clean, ignoreCase = true) }) return true
    val lower = clean.lowercase()
    return tags.any { t ->
        val tl = t.trim().lowercase()
        tl == lower || tl == "category:$lower"
    }
}
