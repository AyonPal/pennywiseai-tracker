package com.pennywiseai.tracker.data.parser.bank

import com.pennywiseai.tracker.core.CompiledPatterns
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.parser.ParsedTransaction
import java.math.BigDecimal

/**
 * Base class for bank-specific message parsers.
 * Each bank should extend this class and implement its specific parsing logic.
 */
abstract class BankParser {
    
    /**
     * Returns the name of the bank this parser handles.
     */
    abstract fun getBankName(): String
    
    /**
     * Checks if this parser can handle messages from the given sender.
     */
    abstract fun canHandle(sender: String): Boolean
    
    /**
     * Parses an SMS message and extracts transaction information.
     * Returns null if the message cannot be parsed.
     */
    open fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Skip non-transaction messages
        if (!isTransactionMessage(smsBody)) {
            android.util.Log.d("BankParser", "Not a transaction message: ${smsBody.take(100)}")
            return null
        }
        
        val amount = extractAmount(smsBody)
        if (amount == null) {
            android.util.Log.d("BankParser", "Could not extract amount from: ${smsBody.take(100)}")
            return null
        }
        
        val type = extractTransactionType(smsBody)
        if (type == null) {
            android.util.Log.d("BankParser", "Could not extract transaction type from: ${smsBody.take(100)}")
            return null
        }
        
        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            val limit = extractAvailableLimit(smsBody)
            android.util.Log.d("BankParser", "Credit card transaction detected. Extracted available limit: $limit from message: ${smsBody.take(100)}")
            limit
        } else {
            null
        }
        
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            creditLimit = availableLimit,  // TODO: This is actually available limit, will be fixed in SmsReaderWorker
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody)
        )
    }
    
    /**
     * Checks if the message is a transaction message (not OTP, promotional, etc.)
     */
    protected open fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Skip OTP messages
        if (lowerMessage.contains("otp") || 
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")) {
            return false
        }
        
        // Skip promotional messages
        if (lowerMessage.contains("offer") || 
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")) {
            return false
        }
        
        // Skip payment request messages (common across banks)
        if (lowerMessage.contains("has requested") || 
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")) {
            return false
        }
        
        // Skip merchant payment acknowledgments
        if (lowerMessage.contains("have received payment")) {
            return false
        }
        
        // Skip payment reminder/due messages
        if (lowerMessage.contains("is due") ||
            lowerMessage.contains("min amount due") ||
            lowerMessage.contains("minimum amount due") ||
            lowerMessage.contains("in arrears") ||
            lowerMessage.contains("is overdue") ||
            lowerMessage.contains("ignore if paid") ||
            (lowerMessage.contains("pls pay") && lowerMessage.contains("min of"))) {
            return false
        }
        
        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )
        
        return transactionKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Extracts the transaction amount from the message.
     */
    protected open fun extractAmount(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Amount.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts the transaction type (INCOME/EXPENSE/INVESTMENT).
     */
    protected open fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        
        // Check for investment transactions first (highest priority)
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }
        
        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("deducted") -> TransactionType.EXPENSE
            
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> TransactionType.INCOME
            
            else -> null
        }
    }
    
    /**
     * Checks if the message is for an investment transaction.
     * Can be overridden by specific bank parsers for custom logic.
     */
    protected open fun isInvestmentTransaction(lowerMessage: String): Boolean {
        val investmentKeywords = listOf(
            // Clearing corporations
            "iccl",                         // Indian Clearing Corporation Limited
            "indian clearing corporation",
            "nsccl",                        // NSE Clearing Corporation
            "nse clearing",
            "clearing corporation",
            
            // Auto-pay indicators (excluding mandate/UMRN to avoid subscription false positives)
            "nach",                         // National Automated Clearing House
            "ach",                          // Automated Clearing House
            "ecs",                          // Electronic Clearing Service
            
            // Investment platforms
            "groww",
            "zerodha",
            "upstox",
            "kite",
            "kuvera",
            "paytm money",
            "etmoney",
            "coin by zerodha",
            "smallcase",
            "angel one",
            "angel broking",
            "5paisa",
            "icici securities",
            "icici direct",
            "hdfc securities",
            "kotak securities",
            "motilal oswal",
            "sharekhan",
            "edelweiss",
            "axis direct",
            "sbi securities",
            
            // Investment types
            "mutual fund",
            "sip",                          // Systematic Investment Plan
            "elss",                         // Tax saving funds
            "ipo",                          // Initial Public Offering
            "folio",                        // Mutual fund folio
            "demat",
            "stockbroker",
            
            // Stock exchanges
            "nse",                          // National Stock Exchange
            "bse",                          // Bombay Stock Exchange
            "cdsl",                         // Central Depository Services
            "nsdl"                          // National Securities Depository
        )
        
        return investmentKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Extracts merchant/payee information.
     */
    protected open fun extractMerchant(message: String, sender: String): String? {
        for (pattern in CompiledPatterns.Merchant.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts transaction reference number.
     */
    protected open fun extractReference(message: String): String? {
        for (pattern in CompiledPatterns.Reference.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    /**
     * Extracts last 4 digits of account number.
     */
    protected open fun extractAccountLast4(message: String): String? {
        for (pattern in CompiledPatterns.Account.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    /**
     * Extracts balance after transaction.
     */
    protected open fun extractBalance(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Balance.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts credit card available limit from the message.
     * This is the remaining credit available to spend, NOT the total credit limit.
     */
    protected open fun extractAvailableLimit(message: String): BigDecimal? {
        android.util.Log.d("BankParser", "Attempting to extract credit limit")
        
        // Common patterns for credit limit across banks
        val creditLimitPatterns = listOf(
            // "Available limit Rs.111,111.89" - Federal Bank format (no space after Rs.)
            Regex("""Available\s+limit\s+Rs\.([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit Rs. 111,111.89" or "Available limit: Rs 111,111.89"
            Regex("""Available\s+limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Avl Lmt Rs.111,111.89" or "Avl Lmt: Rs 111,111.89" (ICICI and others)
            Regex("""Avl\s+Lmt:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Avail Limit Rs.111,111.89"
            Regex("""Avail\s+Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available Credit Limit: Rs.111,111.89"
            Regex("""Available\s+Credit\s+Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Limit: Rs.111,111.89" (generic, but only for credit card messages)
            Regex("""(?:^|\s)Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )
        
        for ((index, pattern) in creditLimitPatterns.withIndex()) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                android.util.Log.d("BankParser", "Credit limit pattern matched")
                return try {
                    val limit = BigDecimal(limitStr)
                    android.util.Log.d("BankParser", "Credit limit parsed successfully")
                    limit
                } catch (e: NumberFormatException) {
                    android.util.Log.e("BankParser", "Failed to parse credit limit", e)
                    null
                }
            }
        }
        
        android.util.Log.d("BankParser", "No credit limit pattern matched")
        return null
    }
    
    /**
     * Detects if the transaction is from a card (credit/debit) based on message patterns.
     * First excludes account-related patterns, then checks for actual card patterns.
     */
    protected open fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // FIRST: Explicitly exclude account-related patterns - these are NOT cards
        val accountPatterns = listOf(
            "a/c",           // Account abbreviation (e.g., "from HDFC Bank A/c 120092")
            "account",       // Full word account (e.g., "from HDFC Bank Account XX0093")
            "ac ",           // Account abbreviation with space
            "acc ",          // Account abbreviation
            "saving account",
            "current account",
            "savings a/c",
            "current a/c"
        )
        
        // If message contains account patterns, it's NOT a card transaction
        for (pattern in accountPatterns) {
            if (lowerMessage.contains(pattern)) {
                android.util.Log.d("BankParser", "Account transaction detected (NOT card)")
                return false
            }
        }
        
        // SECOND: Check for actual card-specific patterns
        val cardPatterns = listOf(
            "card ending",
            "card xx",
            "debit card",
            "credit card",
            "card no.",
            "card number",
            "card *",
            "card x"
        )
        
        // Check for card patterns
        for (pattern in cardPatterns) {
            if (lowerMessage.contains(pattern)) {
                android.util.Log.d("BankParser", "Card detected")
                return true
            }
        }
        
        // Check for masked card number patterns (e.g., "XXXX1234", "*1234", "ending 1234")
        // BUT only if we haven't already excluded it as an account transaction
        val maskedCardRegex = Regex("""(?:xx|XX|\*{2,})?\d{4}""")
        if (lowerMessage.contains("ending") && maskedCardRegex.containsMatchIn(message)) {
            android.util.Log.d("BankParser", "Card detected via 'ending' pattern")
            return true
        }
        
        android.util.Log.d("BankParser", "No card pattern found")
        return false
    }
    
    /**
     * Cleans merchant name by removing common suffixes and noise.
     */
    protected open fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(CompiledPatterns.Cleaning.TRAILING_PARENTHESES, "")
            .replace(CompiledPatterns.Cleaning.REF_NUMBER_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.DATE_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.UPI_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TIME_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TRAILING_DASH, "")
            .replace(CompiledPatterns.Cleaning.PVT_LTD, "")
            .replace(CompiledPatterns.Cleaning.LTD, "")
            .trim()
    }
    
    /**
     * Validates if the extracted merchant name is valid.
     */
    protected open fun isValidMerchantName(name: String): Boolean {
        val commonWords = setOf("USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE")
        
        return name.length >= Constants.Parsing.MIN_MERCHANT_NAME_LENGTH && 
               name.any { it.isLetter() } && 
               name.uppercase() !in commonWords &&
               !name.all { it.isDigit() } &&
               !name.contains("@") // Not a UPI ID
    }
}
