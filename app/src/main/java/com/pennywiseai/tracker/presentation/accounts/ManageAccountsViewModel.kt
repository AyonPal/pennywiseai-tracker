package com.pennywiseai.tracker.presentation.accounts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

data class ManageAccountsUiState(
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val hiddenAccounts: Set<String> = emptySet(),
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val linkedCards: Map<String, List<CardEntity>> = emptyMap(), // accountLast4 -> List of cards
    val orphanedCards: List<CardEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class AccountFormState(
    val bankName: String = "",
    val accountLast4: String = "",
    val balance: String = "",
    val creditLimit: String = "",
    val accountType: AccountType = AccountType.SAVINGS,
    val isValid: Boolean = false,
    val errorMessage: String? = null
)

enum class AccountType {
    SAVINGS,
    CURRENT,
    CREDIT
}

@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository
) : ViewModel() {
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ManageAccountsUiState())
    val uiState: StateFlow<ManageAccountsUiState> = _uiState.asStateFlow()
    
    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()
    
    init {
        loadAccounts()
        loadHiddenAccounts()
        loadCards()
    }
    
    private fun loadAccounts() {
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances()
                .collect { accounts ->
                    _uiState.update { it.copy(accounts = accounts) }
                }
        }
    }
    
    private fun loadCards() {
        viewModelScope.launch {
            cardRepository.getAllCards().collect { allCards ->
                // Group cards by linked account
                val linkedCardsMap = mutableMapOf<String, MutableList<CardEntity>>()
                val orphaned = mutableListOf<CardEntity>()
                
                for (card in allCards) {
                    when {
                        card.cardType == CardType.DEBIT && card.accountLast4 != null -> {
                            linkedCardsMap.getOrPut(card.accountLast4) { mutableListOf() }.add(card)
                        }
                        card.cardType == CardType.DEBIT && card.accountLast4 == null -> {
                            orphaned.add(card)
                        }
                        // Credit cards are not orphaned, they're standalone
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        linkedCards = linkedCardsMap,
                        orphanedCards = orphaned
                    )
                }
            }
        }
    }
    
    private fun loadHiddenAccounts() {
        val hidden = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
        _uiState.update { it.copy(hiddenAccounts = hidden) }
    }
    
    fun updateBankName(name: String) {
        _formState.update { 
            it.copy(
                bankName = name,
                isValid = validateForm(name, it.accountLast4, it.balance)
            )
        }
    }
    
    fun updateAccountLast4(last4: String) {
        // Only allow 4 characters
        if (last4.length <= 4) {
            _formState.update { 
                it.copy(
                    accountLast4 = last4,
                    isValid = validateForm(it.bankName, last4, it.balance)
                )
            }
        }
    }
    
    fun updateBalance(balance: String) {
        // Only allow valid numeric input
        if (balance.isEmpty() || balance.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { 
                it.copy(
                    balance = balance,
                    isValid = validateForm(it.bankName, it.accountLast4, balance)
                )
            }
        }
    }
    
    fun updateCreditLimit(limit: String) {
        // Only allow valid numeric input
        if (limit.isEmpty() || limit.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(creditLimit = limit) }
        }
    }
    
    fun updateAccountType(type: AccountType) {
        _formState.update { it.copy(accountType = type) }
    }
    
    private fun validateForm(bankName: String, last4: String, balance: String): Boolean {
        return bankName.isNotBlank() && 
               last4.length == 4 && 
               balance.isNotBlank() && 
               balance.toDoubleOrNull() != null
    }
    
    fun addAccount() {
        val state = _formState.value
        if (!state.isValid) return
        
        viewModelScope.launch {
            // Check for duplicates
            val existingAccount = accountBalanceRepository.getLatestBalance(
                state.bankName, 
                state.accountLast4
            )
            
            if (existingAccount != null) {
                _formState.update { it.copy(errorMessage = "Account already exists") }
                return@launch
            }
            
            // Add the account
            val creditLimit = if (state.accountType == AccountType.CREDIT && state.creditLimit.isNotBlank()) {
                BigDecimal(state.creditLimit)
            } else null
            
            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = state.bankName,
                    accountLast4 = state.accountLast4,
                    balance = BigDecimal(state.balance),
                    creditLimit = creditLimit,
                    timestamp = LocalDateTime.now(),
                    isCreditCard = (state.accountType == AccountType.CREDIT)
                )
            )
            
            // Clear form
            _formState.value = AccountFormState()
        }
    }
    
    fun updateAccountBalance(bankName: String, accountLast4: String, newBalance: BigDecimal) {
        viewModelScope.launch {
            // Get the latest balance to preserve credit limit
            val latestBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
            
            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    balance = newBalance,
                    creditLimit = latestBalance?.creditLimit,
                    timestamp = LocalDateTime.now()
                )
            )
        }
    }
    
    fun updateCreditCard(bankName: String, accountLast4: String, newBalance: BigDecimal, newLimit: BigDecimal) {
        viewModelScope.launch {
            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    balance = newBalance,
                    creditLimit = newLimit,
                    timestamp = LocalDateTime.now(),
                    isCreditCard = true
                )
            )
        }
    }
    
    fun toggleAccountVisibility(bankName: String, accountLast4: String) {
        val key = "${bankName}_${accountLast4}"
        val hidden = _uiState.value.hiddenAccounts.toMutableSet()
        
        if (hidden.contains(key)) {
            hidden.remove(key)
        } else {
            hidden.add(key)
        }
        
        // Save to SharedPreferences
        sharedPrefs.edit().putStringSet("hidden_accounts", hidden).apply()
        
        // Update UI state
        _uiState.update { it.copy(hiddenAccounts = hidden) }
    }
    
    fun isAccountHidden(bankName: String, accountLast4: String): Boolean {
        val key = "${bankName}_${accountLast4}"
        return _uiState.value.hiddenAccounts.contains(key)
    }
    
    fun clearError() {
        _formState.update { it.copy(errorMessage = null) }
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun loadBalanceHistory(bankName: String, accountLast4: String) {
        viewModelScope.launch {
            val history = accountBalanceRepository.getBalanceHistoryForAccount(bankName, accountLast4)
            _uiState.update { it.copy(balanceHistory = history) }
        }
    }
    
    fun deleteBalanceRecord(id: Long, bankName: String, accountLast4: String) {
        viewModelScope.launch {
            // Check if this is the only record
            val count = accountBalanceRepository.getBalanceCountForAccount(bankName, accountLast4)
            if (count > 1) {
                accountBalanceRepository.deleteBalanceById(id)
                // Reload history and accounts
                loadBalanceHistory(bankName, accountLast4)
                loadAccounts()
            }
        }
    }
    
    fun updateBalanceRecord(id: Long, newBalance: BigDecimal, bankName: String, accountLast4: String) {
        viewModelScope.launch {
            accountBalanceRepository.updateBalanceById(id, newBalance)
            // Reload history and accounts
            loadBalanceHistory(bankName, accountLast4)
            loadAccounts()
        }
    }
    
    fun clearBalanceHistory() {
        _uiState.update { it.copy(balanceHistory = emptyList()) }
    }
    
    fun linkCardToAccount(cardId: Long, accountLast4: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ManageAccountsViewModel", "Starting to link card $cardId to account $accountLast4")
                
                // Get card info first to know if there's a balance to copy
                val card = cardRepository.getCardById(cardId)
                val hasBalance = card?.lastBalance != null
                
                // Link the card to the account
                cardRepository.linkCardToAccount(cardId, accountLast4)
                
                // If card had a balance, copy it to the account
                if (card != null && hasBalance && accountLast4 != null) {
                    try {
                        val insertedId = accountBalanceRepository.insertBalanceUpdate(
                            bankName = card.bankName,
                            accountLast4 = accountLast4,
                            balance = card.lastBalance!!,
                            timestamp = card.lastBalanceDate ?: LocalDateTime.now(),
                            smsSource = card.lastBalanceSource,
                            sourceType = "CARD_LINK"
                        )
                        android.util.Log.d("ManageAccountsViewModel", "Balance copied to account. Insert ID: $insertedId")
                        
                        // Show success message with balance
                        val message = "Card linked successfully. Balance updated to ${CurrencyFormatter.formatCurrency(card.lastBalance)}"
                        _uiState.update { it.copy(successMessage = message) }
                    } catch (e: Exception) {
                        android.util.Log.e("ManageAccountsViewModel", "Failed to copy balance: ${e.message}", e)
                        // Still show success for linking, but note the balance issue
                        _uiState.update { it.copy(successMessage = "Card linked successfully (balance update failed)") }
                    }
                } else {
                    // No balance to copy, just show link success
                    _uiState.update { it.copy(successMessage = "Card linked successfully") }
                }
                
                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }
                
                loadCards() // Reload cards to update UI
                loadAccounts() // Reload accounts to show new balance
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to link card", e)
                _uiState.update { 
                    it.copy(errorMessage = "Failed to link card: ${e.message}")
                }
            }
        }
    }
    
    fun unlinkCard(cardId: Long) {
        viewModelScope.launch {
            cardRepository.unlinkCard(cardId)
            loadCards() // Reload cards to update UI
        }
    }
    
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ManageAccountsViewModel", "Deleting card with ID: $cardId")
                cardRepository.deleteCard(cardId)
                _uiState.update { it.copy(successMessage = "Card deleted successfully") }
                
                // Clear message after delay
                delay(2000)
                _uiState.update { it.copy(successMessage = null) }
                
                loadCards() // Reload cards to update UI
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to delete card", e)
                _uiState.update { 
                    it.copy(errorMessage = "Failed to delete card: ${e.message}")
                }
            }
        }
    }
    
    fun setCardActive(cardId: Long, isActive: Boolean) {
        viewModelScope.launch {
            cardRepository.setCardActive(cardId, isActive)
            loadCards() // Reload cards to update UI
        }
    }
}