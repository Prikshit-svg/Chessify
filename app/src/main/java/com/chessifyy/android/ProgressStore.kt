package com.chessifyy.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.ln

/** Small on-device profile store. Online accounts can sync this model once the service is configured. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("chessifyy.progress", Context.MODE_PRIVATE)

    fun rapidRating() = prefs.getInt("rating.rapid", 1200)
    fun blitzRating() = prefs.getInt("rating.blitz", 1100)
    fun bulletRating() = prefs.getInt("rating.bullet", 1000)
    fun puzzleRating() = prefs.getInt("rating.puzzle", 800)
    fun ratingForMinutes(minutes: Int) = when {
        minutes <= 1 -> bulletRating()
        minutes <= 3 -> blitzRating()
        else -> rapidRating()
    }
    fun streak() = prefs.getInt("streak.days", 0)
    fun dailyPuzzleStreak() = prefs.getInt("puzzles.daily.streak", 0)
    fun hasSolvedDailyPuzzleToday(): Boolean = prefs.getString("puzzles.daily.lastDate", null) == LocalDate.now().toString()

    fun recordDailyPuzzleSolved(): Boolean {
        val today = LocalDate.now()
        val todayKey = today.toString()
        val lastDate = prefs.getString("puzzles.daily.lastDate", null)
        if (lastDate == todayKey) return false
        val previous = runCatching { lastDate?.let(LocalDate::parse) }.getOrNull()
        val nextStreak = if (previous == today.minusDays(1)) dailyPuzzleStreak() + 1 else 1
        prefs.edit().putString("puzzles.daily.lastDate", todayKey).putInt("puzzles.daily.streak", nextStreak).apply()
        return true
    }

    fun recordComputerGame(score: Double, minutes: Int, opponentRating: Int): Int {
        val key = when {
            minutes <= 1 -> "rating.bullet"
            minutes <= 3 -> "rating.blitz"
            else -> "rating.rapid"
        }
        val old = prefs.getInt(key, when (key) {
            "rating.bullet" -> 1000
            "rating.blitz" -> 1100
            else -> 1200
        })
        val expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - old) / 400.0))
        val updated = (old + 24.0 * (score - expected)).toInt().coerceAtLeast(100)
        prefs.edit()
            .putInt("$key.opponent.total", prefs.getInt("$key.opponent.total", 0) + opponentRating)
            .putInt("$key.score.half", prefs.getInt("$key.score.half", 0) + (score * 2).toInt())
            .putInt("$key.sample", prefs.getInt("$key.sample", 0) + 1)
            .apply()
        val resultKey = when {
            score >= 1.0 -> "games.won"
            score <= 0.0 -> "games.lost"
            else -> "games.drawn"
        }
        prefs.edit().putInt(key, updated)
            .putInt("games.played", prefs.getInt("games.played", 0) + 1)
            .putInt(resultKey, prefs.getInt(resultKey, 0) + 1)
            .apply()
        return updated
    }

    /** Smoothed performance estimate from results against the three known offline bot pools. */
    fun estimatedElo(minutes: Int): EloEstimate? {
        val key = when {
            minutes <= 1 -> "rating.bullet"
            minutes <= 3 -> "rating.blitz"
            else -> "rating.rapid"
        }
        val games = prefs.getInt("$key.sample", 0)
        if (games == 0) return null
        val score = prefs.getInt("$key.score.half", 0) / 2.0
        val opponentTotal = prefs.getInt("$key.opponent.total", 0).toDouble()
        // Four neutral prior games keep tiny samples from producing extreme estimates.
        val priorGames = 4.0
        val effectiveGames = games + priorGames
        val effectiveScore = score + priorGames / 2.0
        val averageOpponent = (opponentTotal + 1200.0 * priorGames) / effectiveGames
        val performance = averageOpponent + 400.0 * (ln(effectiveScore / (effectiveGames - effectiveScore)) / ln(10.0))
        return EloEstimate(performance.toInt().coerceIn(100, 3000), games)
    }

    fun markPuzzleSolved(id: String): Boolean {
        val solved = prefs.getStringSet("puzzles.solved", emptySet()).orEmpty().toMutableSet()
        if (!solved.add(id)) return false
        prefs.edit().putStringSet("puzzles.solved", solved).putInt("rating.puzzle", puzzleRating() + 8).apply()
        return true
    }

    fun isPuzzleSolved(id: String) = id in prefs.getStringSet("puzzles.solved", emptySet()).orEmpty()

    fun completeLesson(id: String): Boolean {
        val done = prefs.getStringSet("lessons.completed", emptySet()).orEmpty().toMutableSet()
        if (!done.add(id)) return false
        prefs.edit().putStringSet("lessons.completed", done).apply()
        return true
    }

    fun isLessonComplete(id: String) = id in prefs.getStringSet("lessons.completed", emptySet()).orEmpty()
    fun gamesPlayed() = prefs.getInt("games.played", 0)
    fun gamesWon() = prefs.getInt("games.won", 0)
    fun gamesLost() = prefs.getInt("games.lost", 0)
    fun gamesDrawn() = prefs.getInt("games.drawn", 0)

    fun saveLastGameReview(review: GameReviewData) {
        val history = (recentGameReviews().filterNot { it.savedAt == review.savedAt }).toMutableList()
        history.add(0, review)
        val encodedHistory = JSONArray().apply { history.take(20).forEach { put(encodeReview(it)) } }
        prefs.edit().putString("review.last", encodeReview(review).toString())
            .putString("review.history", encodedHistory.toString()).apply()
    }

    fun lastGameReview(): GameReviewData? = recentGameReviews().firstOrNull()

    fun recentGameReviews(): List<GameReviewData> = runCatching {
        val storedHistory = prefs.getString("review.history", null)
        if (storedHistory == null) {
            listOfNotNull(prefs.getString("review.last", null)?.let { decodeReview(JSONObject(it)) })
        } else {
            val array = JSONArray(storedHistory)
            (0 until array.length()).mapNotNull { decodeReview(array.getJSONObject(it)) }.take(20)
        }
    }.getOrDefault(emptyList())

    private fun encodeReview(review: GameReviewData) = JSONObject()
        .put("positions", JSONArray(review.positions))
        .put("movesUci", JSONArray(review.movesUci))
        .put("movesSan", JSONArray(review.movesSan))
        .put("result", review.result)
        .put("humanSide", review.humanSide?.name)
        .put("savedAt", review.savedAt)
        .put("baselineRating", review.baselineRating)
        .put("opponentRating", review.opponentRating)
        .put("timeControlMinutes", review.timeControlMinutes)
        .put("score", review.score)

    private fun decodeReview(json: JSONObject): GameReviewData? = runCatching {
        fun strings(name: String): List<String> {
            val array = json.getJSONArray(name)
            return (0 until array.length()).map { array.getString(it) }
        }
        val positions = strings("positions")
        val uci = strings("movesUci")
        val san = strings("movesSan")
        if (positions.size != uci.size + 1 || uci.size != san.size) return null
        val side = json.optString("humanSide").takeIf { it.isNotBlank() }?.let { Side.valueOf(it) }
        GameReviewData(
            positions, uci, san, json.optString("result"), side,
            json.optLong("savedAt", System.currentTimeMillis()),
            json.optInt("baselineRating", 1200),
            json.optInt("opponentRating").takeIf { json.has("opponentRating") && !json.isNull("opponentRating") },
            json.optInt("timeControlMinutes", 10),
            if (json.has("score") && !json.isNull("score")) json.optDouble("score") else null
        )
    }.getOrNull()

    fun tournamentJoined() = prefs.getBoolean("tournament.joined", false)
    fun tournamentRoundsPlayed() = prefs.getInt("tournament.rounds", 0)
    fun tournamentPoints() = prefs.getInt("tournament.points.half", 0) / 2.0
    fun joinTournament() {
        prefs.edit().putBoolean("tournament.joined", true).apply()
    }
    fun resetTournament() {
        prefs.edit().putBoolean("tournament.joined", false).putInt("tournament.rounds", 0)
            .putInt("tournament.points.half", 0).apply()
    }
    fun recordTournamentGame(score: Double) {
        if (!tournamentJoined() || tournamentRoundsPlayed() >= 3) return
        val points = when {
            score >= 1.0 -> 2
            score <= 0.0 -> 0
            else -> 1
        }
        prefs.edit().putInt("tournament.rounds", tournamentRoundsPlayed() + 1)
            .putInt("tournament.points.half", prefs.getInt("tournament.points.half", 0) + points).apply()
    }

    fun clubs(): List<String> = decodeList("clubs")
    fun createClub(name: String): Boolean {
        val clean = name.trim().take(36)
        if (clean.isEmpty()) return false
        val clubs = clubs().toMutableSet()
        if (!clubs.add(clean)) return false
        saveList("clubs", clubs.toList())
        return true
    }

    fun chatMessages(): List<String> = decodeList("chat.messages")
    fun addLocalMessage(message: String) {
        val messages = chatMessages().takeLast(79).toMutableList()
        messages += message.take(240)
        saveList("chat.messages", messages)
    }

    private fun decodeList(key: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveList(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }
}

data class EloEstimate(val rating: Int, val games: Int)
