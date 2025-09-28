package com.pennywiseai.tracker.presentation.common

import java.math.BigDecimal

/**
 * Data class to hold financial totals grouped by currency
 */
data class CurrencyGroupedTotals(
    val totalsByCurrency: Map<String, CurrencyTotals> = emptyMap(),
    val availableCurrencies: List<String> = emptyList(),
    val transactionCount: Int = 0
) {
    fun getTotalsForCurrency(currency: String): CurrencyTotals {
        return totalsByCurrency[currency] ?: CurrencyTotals(currency = currency)
    }

    fun hasAnyCurrency(): Boolean = availableCurrencies.isNotEmpty()

    fun getPrimaryCurrency(): String {
        // Return INR if available, otherwise first available currency
        return when {
            availableCurrencies.contains("INR") -> "INR"
            availableCurrencies.isNotEmpty() -> availableCurrencies.first()
            else -> "INR" // Default fallback
        }
    }
}

/**
 * Financial totals for a specific currency
 */
data class CurrencyTotals(
    val currency: String,
    val income: BigDecimal = BigDecimal.ZERO,
    val expenses: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    val transfer: BigDecimal = BigDecimal.ZERO,
    val investment: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0
) {
    val netBalance: BigDecimal
        get() = income - expenses - credit - transfer - investment
}