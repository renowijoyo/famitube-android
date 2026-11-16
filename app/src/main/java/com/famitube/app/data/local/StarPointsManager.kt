package com.famitube.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.famitube.app.data.model.StarSource
import com.famitube.app.data.model.StarTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class StarPointsManager(
    context: Context,
    private val preferences: ParentalPreferences
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("famitube_stars_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _starBalanceFlow = MutableStateFlow(readBalance())
    val starBalanceFlow: StateFlow<Int> = _starBalanceFlow.asStateFlow()

    private val _totalEarnedFlow = MutableStateFlow(readTotalEarned())
    val totalEarnedFlow: StateFlow<Int> = _totalEarnedFlow.asStateFlow()

    private val _transactionsFlow = MutableStateFlow(readTransactions())
    val transactionsFlow: StateFlow<List<StarTransaction>> = _transactionsFlow.asStateFlow()

    companion object {
        private const val MAX_TRANSACTIONS = 30
    }

    private fun userPrefix(): String {
        val email = preferences.loggedInEmail.trim().lowercase()
        return if (email.isBlank()) "guest" else email
    }

    private fun key(name: String): String = "${userPrefix()}_$name"

    fun getBalance(): Int = _starBalanceFlow.value

    fun getTotalEarned(): Int = _totalEarnedFlow.value

    fun canAfford(cost: Int): Boolean = cost <= 0 || getBalance() >= cost

    fun refreshForCurrentUser() {
        _starBalanceFlow.value = readBalance()
        _totalEarnedFlow.value = readTotalEarned()
        _transactionsFlow.value = readTransactions()
    }

    fun awardStars(amount: Int, source: StarSource, description: String): Int {
        if (amount <= 0) return getBalance()

        val newBalance = readBalance() + amount
        val newTotalEarned = readTotalEarned() + amount
        val tx = StarTransaction(
            id = UUID.randomUUID().toString(),
            amount = amount,
            source = source,
            description = description,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedTx = (listOf(tx) + readTransactions()).take(MAX_TRANSACTIONS)

        prefs.edit()
            .putInt(key("star_balance"), newBalance)
            .putInt(key("total_stars_earned"), newTotalEarned)
            .putString(key("star_transactions"), gson.toJson(updatedTx))
            .apply()

        _starBalanceFlow.value = newBalance
        _totalEarnedFlow.value = newTotalEarned
        _transactionsFlow.value = updatedTx
        return newBalance
    }

    fun spendStars(cost: Int, source: StarSource, description: String): Boolean {
        if (cost <= 0) return true
        val current = readBalance()
        if (current < cost) return false

        val newBalance = current - cost
        val tx = StarTransaction(
            id = UUID.randomUUID().toString(),
            amount = -cost,
            source = source,
            description = description,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedTx = (listOf(tx) + readTransactions()).take(MAX_TRANSACTIONS)

        prefs.edit()
            .putInt(key("star_balance"), newBalance)
            .putString(key("star_transactions"), gson.toJson(updatedTx))
            .apply()

        _starBalanceFlow.value = newBalance
        _transactionsFlow.value = updatedTx
        return true
    }

    private fun readBalance(): Int = prefs.getInt(key("star_balance"), 0).coerceAtLeast(0)

    private fun readTotalEarned(): Int = prefs.getInt(key("total_stars_earned"), 0).coerceAtLeast(0)

    private fun readTransactions(): List<StarTransaction> {
        val raw = prefs.getString(key("star_transactions"), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<StarTransaction>>() {}.type
            gson.fromJson<List<StarTransaction>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
