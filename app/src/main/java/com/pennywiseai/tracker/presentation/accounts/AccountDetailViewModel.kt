package com.pennywiseai.tracker.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.ui.components.BalancePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) : ViewModel() {
    
    private val bankName: String = savedStateHandle.get<String>("bankName") ?: ""
    private val accountLast4: String = savedStateHandle.get<String>("accountLast4") ?: ""
    
    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()
    
    private val _selectedDateRange = MutableStateFlow(DateRange.LAST_30_DAYS)
    val selectedDateRange: StateFlow<DateRange> = _selectedDateRange.asStateFlow()
    
    init {
        loadAccountData()
        observeTransactions()
        observeBalanceHistory()
        observeBalanceChartData()
    }
    
    private fun loadAccountData() {
        _uiState.update { it.copy(
            bankName = bankName,
            accountLast4 = accountLast4,
            isLoading = true
        ) }
    }
    
    private fun observeTransactions() {
        viewModelScope.launch {
            combine(
                selectedDateRange,
                transactionRepository.getTransactionsByAccount(bankName, accountLast4)
            ) { dateRange, allTransactions ->
                val (startDate, endDate) = getDateRangeValues(dateRange)
                
                val filteredTransactions = if (dateRange == DateRange.ALL_TIME) {
                    allTransactions
                } else {
                    allTransactions.filter { transaction ->
                        transaction.dateTime.isAfter(startDate) && 
                        transaction.dateTime.isBefore(endDate)
                    }
                }
                
                val totalIncome = filteredTransactions
                    .filter { it.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                    
                val totalExpenses = filteredTransactions
                    .filter { it.transactionType != com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                
                _uiState.update { state ->
                    state.copy(
                        transactions = filteredTransactions,
                        totalIncome = totalIncome,
                        totalExpenses = totalExpenses,
                        netBalance = totalIncome - totalExpenses,
                        primaryCurrency = getPrimaryCurrencyForAccount(filteredTransactions),
                        isLoading = false
                    )
                }
            }.collect()
        }
    }
    
    private fun observeBalanceHistory() {
        viewModelScope.launch {
            accountBalanceRepository.getLatestBalanceFlow(bankName, accountLast4)
                .collect { latestBalance ->
                    _uiState.update { state ->
                        state.copy(currentBalance = latestBalance)
                    }
                }
        }
        
        viewModelScope.launch {
            selectedDateRange.flatMapLatest { dateRange ->
                val (startDate, endDate) = getDateRangeValues(dateRange)
                accountBalanceRepository.getBalanceHistory(
                    bankName, 
                    accountLast4,
                    startDate,
                    endDate
                )
            }.collect { balanceHistory ->
                _uiState.update { state ->
                    state.copy(balanceHistory = balanceHistory)
                }
            }
        }
    }
    
    private fun observeBalanceChartData() {
        // Fetch balance chart data based on selected date range
        viewModelScope.launch {
            selectedDateRange.flatMapLatest { dateRange ->
                val (startDate, endDate) = getDateRangeValues(dateRange)

                // For chart purposes, extend the range to show more context
                val chartStartDate = when (dateRange) {
                    DateRange.LAST_7_DAYS -> endDate.minusDays(14)  // Show 2 weeks for 7-day view
                    DateRange.LAST_30_DAYS -> endDate.minusMonths(2)  // Show 2 months for 30-day view
                    DateRange.LAST_3_MONTHS -> endDate.minusMonths(4)  // Show 4 months for 3-month view
                    DateRange.LAST_6_MONTHS -> endDate.minusMonths(8)  // Show 8 months for 6-month view
                    DateRange.LAST_YEAR -> endDate.minusMonths(15)  // Show 15 months for 1-year view
                    DateRange.ALL_TIME -> LocalDateTime.of(2000, 1, 1, 0, 0)  // Show all available data
                }

                accountBalanceRepository.getBalanceHistory(
                    bankName,
                    accountLast4,
                    chartStartDate,
                    endDate
                )
            }.collect { balanceHistory ->
                // Convert to BalancePoint for chart
                val chartData = balanceHistory.map { entity ->
                    BalancePoint(
                        timestamp = entity.timestamp,
                        balance = entity.balance,
                        currency = entity.currency
                    )
                }

                _uiState.update { state ->
                    state.copy(balanceChartData = chartData)
                }
            }
        }
    }
    
    fun selectDateRange(dateRange: DateRange) {
        _selectedDateRange.value = dateRange
    }
    
    private fun getDateRangeValues(dateRange: DateRange): Pair<LocalDateTime, LocalDateTime> {
        val endDate = LocalDateTime.now()
        val startDate = when (dateRange) {
            DateRange.LAST_7_DAYS -> endDate.minusDays(7)
            DateRange.LAST_30_DAYS -> endDate.minusDays(30)
            DateRange.LAST_3_MONTHS -> endDate.minusMonths(3)
            DateRange.LAST_6_MONTHS -> endDate.minusMonths(6)
            DateRange.LAST_YEAR -> endDate.minusYears(1)
            DateRange.ALL_TIME -> LocalDateTime.of(2000, 1, 1, 0, 0)
        }
        return startDate to endDate
    }

    private fun getPrimaryCurrencyForAccount(transactions: List<TransactionEntity>): String {
        val availableCurrencies = transactions.map { it.currency }.distinct()
        return when {
            availableCurrencies.contains("AED") -> "AED"  // FAB bank uses AED
            availableCurrencies.contains("INR") -> "INR"
            availableCurrencies.isNotEmpty() -> availableCurrencies.first()
            else -> "INR" // Default fallback
        }
    }
}

data class AccountDetailUiState(
    val bankName: String = "",
    val accountLast4: String = "",
    val currentBalance: AccountBalanceEntity? = null,
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val balanceChartData: List<BalancePoint> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val primaryCurrency: String = "INR",
    val isLoading: Boolean = true
)

enum class DateRange(val label: String) {
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time")
}