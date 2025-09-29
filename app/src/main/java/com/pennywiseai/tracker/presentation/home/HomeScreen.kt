package com.pennywiseai.tracker.presentation.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.components.SummaryCard
import com.pennywiseai.tracker.ui.components.ListItemCard
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.AccountBalancesCard
import com.pennywiseai.tracker.ui.components.CreditCardsCard
import com.pennywiseai.tracker.ui.components.UnifiedAccountsCard
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionsWithSearch: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAddScreen: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onFabPositioned: (Rect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val activity = LocalActivity.current
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Check for app updates and reviews when the screen is first displayed
    LaunchedEffect(Unit) {
        activity?.let {
            val componentActivity = it as ComponentActivity
            
            // Check for app updates
            viewModel.checkForAppUpdate(
                activity = componentActivity,
                snackbarHostState = snackbarHostState,
                scope = scope
            )
            
            // Check for in-app review eligibility
            viewModel.checkForInAppReview(componentActivity)
        }
    }
    
    // Refresh hidden accounts whenever this screen becomes visible
    // This ensures changes from ManageAccountsScreen are reflected immediately
    DisposableEffect(Unit) {
        viewModel.refreshHiddenAccounts()
        onDispose { }
    }
    
    // Handle delete undo snackbar
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            viewModel.clearDeletedTransaction()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Transaction deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Pass the transaction directly since state is already cleared
                    viewModel.undoDeleteTransaction(transaction)
                }
            }
        }
    }
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(0.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content,
                bottom = Dimensions.Padding.content + 80.dp // Space for FAB
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Transaction Summary Cards with HorizontalPager
            item {
                TransactionSummaryCards(
                    uiState = uiState,
                    onCurrencySelected = { viewModel.selectCurrency(it) }
                )
            }
            
            // Unified Accounts Section (Credit Cards + Bank Accounts)
            if (uiState.creditCards.isNotEmpty() || uiState.accountBalances.isNotEmpty()) {
                item {
                    UnifiedAccountsCard(
                        creditCards = uiState.creditCards,
                        bankAccounts = uiState.accountBalances,
                        totalBalance = uiState.totalBalance,
                        totalAvailableCredit = uiState.totalAvailableCredit,
                        onAccountClick = { bankName, accountLast4 ->
                            navController.navigate(
                                com.pennywiseai.tracker.navigation.AccountDetail(
                                    bankName = bankName,
                                    accountLast4 = accountLast4
                                )
                            )
                        }
                    )
                }
            }
            
            // Upcoming Subscriptions Alert
            if (uiState.upcomingSubscriptions.isNotEmpty()) {
                item {
                    UpcomingSubscriptionsCard(
                        subscriptions = uiState.upcomingSubscriptions,
                        totalAmount = uiState.upcomingSubscriptionsTotal,
                        onClick = onNavigateToSubscriptions
                    )
                }
            }
            
            // Recent Transactions Section
            item {
                SectionHeader(
                    title = "Recent Transactions",
                    action = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search button
                            IconButton(
                                onClick = onNavigateToTransactionsWithSearch,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search transactions",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // View All button
                            TextButton(onClick = onNavigateToTransactions) {
                                Text("View All")
                            }
                        }
                    }
                )
            }
            
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.Component.minTouchTarget * 2),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.recentTransactions.isEmpty()) {
                item {
                    PennyWiseCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Padding.empty),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.recentTransactions,
                    key = { it.id }
                ) { transaction ->
                    SimpleTransactionItem(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction.id) }
                    )
                }
            }
        }
        
        // FABs - Direct access (no speed dial)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Add FAB (top, small)
            SmallFloatingActionButton(
                onClick = onNavigateToAddScreen,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction or Subscription"
                )
            }
            
            // Sync FAB (bottom, primary)
            FloatingActionButton(
                onClick = { viewModel.scanSmsMessages() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.spotlightTarget(onFabPositioned)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync SMS"
                )
            }
        }
        
        // Scanning overlay
        AnimatedVisibility(
            visible = uiState.isScanning,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                modifier = Modifier
                    .padding(Dimensions.Padding.content)
                    .widthIn(max = 280.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Scanning SMS messages",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Looking for transactions...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Breakdown Dialog
        if (uiState.showBreakdownDialog) {
            BreakdownDialog(
                currentMonthIncome = uiState.currentMonthIncome,
                currentMonthExpenses = uiState.currentMonthExpenses,
                currentMonthTotal = uiState.currentMonthTotal,
                lastMonthIncome = uiState.lastMonthIncome,
                lastMonthExpenses = uiState.lastMonthExpenses,
                lastMonthTotal = uiState.lastMonthTotal,
                onDismiss = { viewModel.hideBreakdownDialog() }
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTransactionItem(
    transaction: TransactionEntity,
    onClick: () -> Unit = {}
) {
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> if (!isSystemInDarkTheme()) income_light else income_dark
        TransactionType.EXPENSE -> if (!isSystemInDarkTheme()) expense_light else expense_dark
        TransactionType.CREDIT -> if (!isSystemInDarkTheme()) credit_light else credit_dark
        TransactionType.TRANSFER -> if (!isSystemInDarkTheme()) transfer_light else transfer_dark
        TransactionType.INVESTMENT -> if (!isSystemInDarkTheme()) investment_light else investment_dark
    }
    
    val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d • h:mm a")
    val dateTimeText = transaction.dateTime.format(dateTimeFormatter)
    
    ListItemCard(
        title = transaction.merchantName,
        subtitle = dateTimeText,
        amount = transaction.formatAmount(),
        amountColor = amountColor,
        onClick = onClick,
        leadingContent = {
            BrandIcon(
                merchantName = transaction.merchantName,
                size = 40.dp,
                showBackground = true
            )
        }
    )
}

@Composable
private fun MonthSummaryCard(
    monthTotal: BigDecimal,
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentExpenses: BigDecimal = BigDecimal.ZERO,
    lastExpenses: BigDecimal = BigDecimal.ZERO,
    onShowBreakdown: () -> Unit = {}
) {
    val isPositive = monthTotal >= BigDecimal.ZERO
    val displayAmount = if (isPositive) {
        "+${CurrencyFormatter.formatCurrency(monthTotal, currency)}"
    } else {
        CurrencyFormatter.formatCurrency(monthTotal, currency)
    }
    val amountColor = if (isPositive) {
        if (!isSystemInDarkTheme()) income_light else income_dark
    } else {
        if (!isSystemInDarkTheme()) expense_light else expense_dark
    }
    
    val expenseChange = currentExpenses - lastExpenses
    val now = LocalDate.now()
    val lastMonth = now.minusMonths(1)
    val periodLabel = "vs ${lastMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} 1-${now.dayOfMonth}"
    
    val subtitle = when {
        // No transactions yet
        currentExpenses == BigDecimal.ZERO && lastExpenses == BigDecimal.ZERO -> {
            "No transactions yet"
        }
        // Spent more than last period
        expenseChange > BigDecimal.ZERO -> {
            "😟 Spent ${CurrencyFormatter.formatCurrency(expenseChange.abs(), currency)} more $periodLabel"
        }
        // Spent less than last period
        expenseChange < BigDecimal.ZERO -> {
            "😊 Spent ${CurrencyFormatter.formatCurrency(expenseChange.abs(), currency)} less $periodLabel"
        }
        // Saved more (higher positive balance)
        monthlyChange > BigDecimal.ZERO && monthTotal > BigDecimal.ZERO -> {
            "🎉 Saved ${CurrencyFormatter.formatCurrency(monthlyChange.abs(), currency)} more $periodLabel"
        }
        // No change
        else -> {
            "Same as last period"
        }
    }
    
    val currentMonth = now.month.name.lowercase().replaceFirstChar { it.uppercase() }

    // Currency symbol mapping for display
    val currencySymbols = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "AED" to "د.إ",
        "NPR" to "₨",
        "ETB" to "ብর"
    )
    val currencySymbol = currencySymbols[currency] ?: currency

    val titleText = "Net Balance ($currencySymbol) • $currentMonth 1-${now.dayOfMonth}"
    
    SummaryCard(
        title = titleText,
        amount = displayAmount,
        subtitle = subtitle,
        amountColor = amountColor,
        onClick = onShowBreakdown
    )
}

@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    onClick: () -> Unit = {}
) {
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> if (!isSystemInDarkTheme()) income_light else income_dark
        TransactionType.EXPENSE -> if (!isSystemInDarkTheme()) expense_light else expense_dark
        TransactionType.CREDIT -> if (!isSystemInDarkTheme()) credit_light else credit_dark
        TransactionType.TRANSFER -> if (!isSystemInDarkTheme()) transfer_light else transfer_dark
        TransactionType.INVESTMENT -> if (!isSystemInDarkTheme()) investment_light else investment_dark
    }
    
    // Get subtle background color based on transaction type
    val cardBackgroundColor = when (transaction.transactionType) {
        TransactionType.CREDIT -> (if (!isSystemInDarkTheme()) credit_light else credit_dark).copy(alpha = 0.05f)
        TransactionType.TRANSFER -> (if (!isSystemInDarkTheme()) transfer_light else transfer_dark).copy(alpha = 0.05f)
        TransactionType.INVESTMENT -> (if (!isSystemInDarkTheme()) investment_light else investment_dark).copy(alpha = 0.05f)
        TransactionType.INCOME -> (if (!isSystemInDarkTheme()) income_light else income_dark).copy(alpha = 0.03f)
        else -> Color.Transparent // Default for regular expenses
    }
    
    ListItemCard(
        title = transaction.merchantName,
        subtitle = transaction.dateTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")),
        amount = transaction.formatAmount(),
        amountColor = amountColor,
        onClick = onClick,
        leadingContent = {
            BrandIcon(
                merchantName = transaction.merchantName,
                size = 40.dp,
                showBackground = true
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Show icon for transaction types
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> Icon(
                        Icons.Default.CreditCard,
                        contentDescription = "Credit Card",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) credit_light else credit_dark
                    )
                    TransactionType.TRANSFER -> Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Transfer",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) transfer_light else transfer_dark
                    )
                    TransactionType.INVESTMENT -> Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Investment",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) investment_light else investment_dark
                    )
                    TransactionType.INCOME -> Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Income",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) income_light else income_dark
                    )
                    TransactionType.EXPENSE -> Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = "Expense",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                    )
                }
                
                // Always show amount
                Text(
                    text = transaction.formatAmount(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownDialog(
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthTotal: BigDecimal,
    lastMonthIncome: BigDecimal,
    lastMonthExpenses: BigDecimal,
    lastMonthTotal: BigDecimal,
    onDismiss: () -> Unit
) {
    val now = LocalDate.now()
    val currentPeriod = "${now.month.name.lowercase().replaceFirstChar { it.uppercase() }} 1-${now.dayOfMonth}"
    val lastMonth = now.minusMonths(1)
    val lastPeriod = "${lastMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} 1-${now.dayOfMonth}"
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md), // Reduced horizontal padding for wider modal
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title
                Text(
                    text = "Calculation Breakdown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Current Period Section
                Text(
                    text = currentPeriod,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                BreakdownRow(
                    label = "Income",
                    amount = currentMonthIncome,
                    isIncome = true
                )
                
                BreakdownRow(
                    label = "Expenses",
                    amount = currentMonthExpenses,
                    isIncome = false
                )
                
                HorizontalDivider()
                
                BreakdownRow(
                    label = "Net Balance",
                    amount = currentMonthTotal,
                    isIncome = currentMonthTotal >= BigDecimal.ZERO,
                    isBold = true
                )
                
                Spacer(modifier = Modifier.height(Spacing.sm))
                
                // Last Period Section
                Text(
                    text = lastPeriod,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                BreakdownRow(
                    label = "Income",
                    amount = lastMonthIncome,
                    isIncome = true
                )
                
                BreakdownRow(
                    label = "Expenses",
                    amount = lastMonthExpenses,
                    isIncome = false
                )
                
                HorizontalDivider()
                
                BreakdownRow(
                    label = "Net Balance",
                    amount = lastMonthTotal,
                    isIncome = lastMonthTotal >= BigDecimal.ZERO,
                    isBold = true
                )
                
                // Formula explanation
                Spacer(modifier = Modifier.height(Spacing.sm))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Formula: Income - Expenses = Net Balance\n" +
                               "Green (+) = Savings | Red (-) = Overspending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(Spacing.sm),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: BigDecimal,
    isIncome: Boolean,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatCurrency(amount.abs())}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isIncome) {
                if (!isSystemInDarkTheme()) income_light else income_dark
            } else {
                if (!isSystemInDarkTheme()) expense_light else expense_dark
            }
        )
    }
}

@Composable
private fun UpcomingSubscriptionsCard(
    subscriptions: List<SubscriptionEntity>,
    totalAmount: BigDecimal,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
                Column {
                    Text(
                        text = "${subscriptions.size} active subscriptions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Monthly total: ${CurrencyFormatter.formatCurrency(totalAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle)
                    )
                }
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionSummaryCards(
    uiState: HomeUiState,
    onCurrencySelected: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Enhanced Currency Selector (if multiple currencies available)
        if (uiState.availableCurrencies.size > 1) {
            EnhancedCurrencySelector(
                selectedCurrency = uiState.selectedCurrency,
                availableCurrencies = uiState.availableCurrencies,
                onCurrencySelected = onCurrencySelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Show empty state if no transactions in selected currency
        if (uiState.currentMonthTotal == BigDecimal.ZERO &&
            uiState.currentMonthIncome == BigDecimal.ZERO &&
            uiState.currentMonthExpenses == BigDecimal.ZERO &&
            uiState.availableCurrencies.size > 1) {
            CurrencyEmptyState(
                selectedCurrency = uiState.selectedCurrency,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = Spacing.md
        ) { page ->
            when (page) {
                0 -> {
                    // Net Balance Card (existing implementation)
                    MonthSummaryCard(
                        monthTotal = uiState.currentMonthTotal,
                        monthlyChange = uiState.monthlyChange,
                        monthlyChangePercent = uiState.monthlyChangePercent,
                        currency = uiState.selectedCurrency,
                        currentExpenses = uiState.currentMonthExpenses,
                        lastExpenses = uiState.lastMonthExpenses,
                        onShowBreakdown = { /* TODO */ }
                    )
                }
                1 -> {
                    // Credit Card Summary
                    TransactionTypeCard(
                        title = "Credit Card",
                        icon = Icons.Default.CreditCard,
                        amount = uiState.currentMonthCreditCard,
                        color = if (!isSystemInDarkTheme()) credit_light else credit_dark,
                        emoji = "💳",
                        currency = uiState.selectedCurrency
                    )
                }
                2 -> {
                    // Transfer Summary
                    TransactionTypeCard(
                        title = "Transfers",
                        icon = Icons.Default.SwapHoriz,
                        amount = uiState.currentMonthTransfer,
                        color = if (!isSystemInDarkTheme()) transfer_light else transfer_dark,
                        emoji = "↔️",
                        currency = uiState.selectedCurrency
                    )
                }
                3 -> {
                    // Investment Summary
                    TransactionTypeCard(
                        title = "Investments",
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        amount = uiState.currentMonthInvestment,
                        color = if (!isSystemInDarkTheme()) investment_light else investment_dark,
                        emoji = "📈",
                        currency = uiState.selectedCurrency
                    )
                }
            }
        }
        
        // Page Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val color = if (pagerState.currentPage == index) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(8.dp)
                        .background(
                            color = color,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun TransactionTypeCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    amount: BigDecimal,
    color: Color,
    emoji: String,
    currency: String
) {
    val currentMonth = LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() }
    val now = LocalDate.now()
    
    val subtitle = when {
        amount > BigDecimal.ZERO -> {
            when (title) {
                "Credit Card" -> "Spent on credit this month"
                "Transfers" -> "Moved between accounts"
                "Investments" -> "Invested this month"
                else -> "Total this month"
            }
        }
        else -> {
            when (title) {
                "Credit Card" -> "No credit card spending"
                "Transfers" -> "No transfers this month"
                "Investments" -> "No investments this month"
                else -> "No transactions"
            }
        }
    }
    
    SummaryCard(
        title = "$emoji $title • $currentMonth",
        subtitle = subtitle,
        amount = CurrencyFormatter.formatCurrency(amount, currency),
        amountColor = color,
        onClick = { /* TODO: Navigate to filtered view */ }
    )
}

@Composable
private fun EnhancedCurrencySelector(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Currency symbol mapping
    val currencySymbols = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "AED" to "د.إ",
        "NPR" to "₨",
        "ETB" to "ብር"
    )

    PennyWiseCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.CurrencyExchange,
                    contentDescription = "Currency",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Currency View",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${availableCurrencies.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Currency chips with FlowRow for better wrapping
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                availableCurrencies.forEach { currency ->
                    val isSelected = selectedCurrency == currency
                    val symbol = currencySymbols[currency] ?: currency

                    Surface(
                        onClick = { onCurrencySelected(currency) },
                        modifier = Modifier.animateContentSize(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = if (isSelected) 16.dp else 12.dp,
                                vertical = 8.dp
                            )
                        ) {
                            // Currency symbol with enhanced styling
                            Text(
                                text = symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )

                            if (symbol != currency) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currency,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                )
                            }

                            // Selected indicator
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyEmptyState(
    selectedCurrency: String,
    modifier: Modifier = Modifier
) {
    val currencySymbols = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "AED" to "د.إ",
        "NPR" to "₨",
        "ETB" to "ብር"
    )
    val currencySymbol = currencySymbols[selectedCurrency] ?: selectedCurrency

    PennyWiseCard(
        modifier = modifier,
        containerColor = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💰",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "No $selectedCurrency transactions yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Start spending or earning in $currencySymbol to see insights here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
