package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface ExchangeRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRate(exchangeRate: ExchangeRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRates(exchangeRates: List<ExchangeRateEntity>)

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND expires_at > :currentTime")
    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND expires_at > :currentTime")
    fun getExchangeRateFlow(fromCurrency: String, toCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): Flow<ExchangeRateEntity?>

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND expires_at > :currentTime")
    suspend fun getExchangeRatesForCurrency(fromCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): List<ExchangeRateEntity>

    @Query("SELECT * FROM exchange_rates WHERE updated_at < :expiryTime")
    suspend fun getExpiredRates(expiryTime: LocalDateTime): List<ExchangeRateEntity>

    @Query("DELETE FROM exchange_rates WHERE updated_at < :expiryTime")
    suspend fun deleteExpiredRates(expiryTime: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND expires_at > :currentTime")
    suspend fun hasValidRate(fromCurrency: String, toCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): Int

    @Query("SELECT * FROM exchange_rates ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestRate(): ExchangeRateEntity?

    // Get all unique currencies that have exchange rates
    @Query("SELECT DISTINCT from_currency FROM exchange_rates WHERE expires_at > :currentTime")
    suspend fun getAvailableCurrencies(currentTime: LocalDateTime = LocalDateTime.now()): List<String>

    // Batch get rates for multiple currency pairs
    @Query("SELECT * FROM exchange_rates WHERE (from_currency = :fromCurrency1 AND to_currency = :toCurrency1) OR (from_currency = :fromCurrency2 AND to_currency = :toCurrency2) AND expires_at > :currentTime")
    suspend fun getMultipleRates(
        fromCurrency1: String, toCurrency1: String,
        fromCurrency2: String, toCurrency2: String,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): List<ExchangeRateEntity>
}