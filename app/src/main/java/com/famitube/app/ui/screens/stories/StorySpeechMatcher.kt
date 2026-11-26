package com.famitube.app.ui.screens.stories

object StorySpeechMatcher {

    private val numberSynonyms: List<Set<String>> = listOf(
        setOf("0", "zero", "nol"),
        setOf("1", "one", "satu"),
        setOf("2", "two", "dua"),
        setOf("3", "three", "tiga"),
        setOf("4", "four", "empat"),
        setOf("5", "five", "lima"),
        setOf("6", "six", "enam"),
        setOf("7", "seven", "tujuh"),
        setOf("8", "eight", "delapan"),
        setOf("9", "nine", "sembilan"),
        setOf("10", "ten", "sepuluh")
    )

    fun tokenize(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    fun normalizeWord(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) {
                    prev
                } else {
                    1 + minOf(prev, dp[j], dp[j - 1])
                }
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun wordsMatch(targetRaw: String, spokenRaw: String): Boolean {
        val target = normalizeWord(targetRaw)
        val spoken = normalizeWord(spokenRaw)
        if (target.isEmpty() || spoken.isEmpty()) return false
        if (target == spoken) return true
        for (group in numberSynonyms) {
            if (target in group && spoken in group) return true
        }
        val dist = levenshtein(target, spoken)
        if (target.length >= 7 && dist <= 2) return true
        if (target.length >= 4 && dist <= 1) return true
        return false
    }

    data class MatchResult(
        val nextWordIdx: Int,
        val advanced: Boolean
    )

    fun matchTranscript(
        pageWords: List<String>,
        currentWordIdx: Int,
        transcript: String
    ): MatchResult {
        if (currentWordIdx >= pageWords.size) {
            return MatchResult(currentWordIdx, false)
        }
        val spokenTokens = tokenize(transcript)
        var idx = currentWordIdx
        var advanced = false
        for (tok in spokenTokens) {
            if (idx >= pageWords.size) break
            if (wordsMatch(pageWords[idx], tok)) {
                idx++
                advanced = true
            }
        }
        return MatchResult(idx, advanced)
    }
}
