package com.pennywiseai.tracker.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pennywiseai.tracker.data.database.converter.Converters
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.MerchantMappingDao
import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity

/**
 * The PennyWise Room database.
 * 
 * This database stores all financial transaction data locally on the device.
 * 
 * @property version Current database version. Increment this when making schema changes.
 * @property entities List of all entities (tables) in the database.
 * @property exportSchema Set to true in production to export schema for version control.
 * @property autoMigrations List of automatic migrations between versions.
 */
@Database(
    entities = [TransactionEntity::class, SubscriptionEntity::class, ChatMessage::class, MerchantMappingEntity::class, CategoryEntity::class, AccountBalanceEntity::class, UnrecognizedSmsEntity::class, CardEntity::class, RuleEntity::class, RuleApplicationEntity::class, ExchangeRateEntity::class],
    version = 25,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11, spec = Migration10To11::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        // Note: v20 to v21 uses manual migration to handle nullable field change
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25)
    ]
)
@TypeConverters(Converters::class)
abstract class PennyWiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun chatDao(): ChatDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun unrecognizedSmsDao(): UnrecognizedSmsDao
    abstract fun cardDao(): CardDao
    abstract fun ruleDao(): RuleDao
    abstract fun ruleApplicationDao(): RuleApplicationDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    
    companion object {
        const val DATABASE_NAME = "pennywise_database"
        
        /**
         * Manual migration from version 1 to 2.
         * Example of how to write manual migrations when auto-migration isn't sufficient.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example: Add a new column
                // db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
            }
        }
        
        /**
         * Manual migration from version 13 to 14.
         * Adds is_deleted column and unique constraint, handling existing duplicates.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if sms_sender column already exists in transactions table
                val cursor = db.query("PRAGMA table_info(transactions)")
                var hasSenderColumn = false
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndex("name"))
                    if (columnName == "sms_sender") {
                        hasSenderColumn = true
                        break
                    }
                }
                cursor.close()
                
                // Add sms_sender column to transactions table only if it doesn't exist
                if (!hasSenderColumn) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN sms_sender TEXT")
                }
                
                // Check if is_deleted column already exists in unrecognized_sms table
                val cursor2 = db.query("PRAGMA table_info(unrecognized_sms)")
                var hasIsDeletedColumn = false
                while (cursor2.moveToNext()) {
                    val columnName = cursor2.getString(cursor2.getColumnIndex("name"))
                    if (columnName == "is_deleted") {
                        hasIsDeletedColumn = true
                        break
                    }
                }
                cursor2.close()
                
                // Only proceed with unrecognized_sms migration if needed
                if (!hasIsDeletedColumn) {
                    // First, add the is_deleted column with default value
                    db.execSQL("ALTER TABLE unrecognized_sms ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                    
                    // Create a temporary table with the new schema (including unique constraint)
                    db.execSQL("""
                        CREATE TABLE unrecognized_sms_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sender TEXT NOT NULL,
                            sms_body TEXT NOT NULL,
                            received_at TEXT NOT NULL,
                            reported INTEGER NOT NULL,
                            is_deleted INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL
                        )
                    """)
                    
                    // Copy data from old table, keeping only the most recent of duplicates
                    db.execSQL("""
                        INSERT INTO unrecognized_sms_new (id, sender, sms_body, received_at, reported, is_deleted, created_at)
                        SELECT id, sender, sms_body, received_at, reported, is_deleted, created_at
                        FROM unrecognized_sms
                        WHERE id IN (
                            SELECT MAX(id)
                            FROM unrecognized_sms
                            GROUP BY sender, sms_body
                        )
                    """)
                    
                    // Drop the old table
                    db.execSQL("DROP TABLE unrecognized_sms")
                    
                    // Rename the new table to the original name
                    db.execSQL("ALTER TABLE unrecognized_sms_new RENAME TO unrecognized_sms")
                    
                    // Create the unique index
                    db.execSQL("CREATE UNIQUE INDEX index_unrecognized_sms_sender_sms_body ON unrecognized_sms (sender, sms_body)")
                }
            }
        }
        
        /**
         * Manual migration from version 12 to 14.
         * Handles direct upgrade from 12 to 14, combining migrations 12->13 and 13->14.
         */
        val MIGRATION_12_14 = object : Migration(12, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Same as MIGRATION_13_14 since we need to handle both cases
                MIGRATION_13_14.migrate(db)
            }
        }
        
        /**
         * Manual migration from version 14 to 15.
         * Adds sms_body column to subscriptions table.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sms_body column to subscriptions table
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN sms_body TEXT")
            }
        }
        
        /**
         * Manual migration from version 20 to 21.
         * Makes next_payment_date nullable in subscriptions table.
         * This fixes the issue where v2.15.18 had non-nullable field but v2.15.19+ needs nullable.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support ALTER COLUMN, so we need to recreate the table
                // Step 1: Create new subscriptions table with nullable next_payment_date
                db.execSQL("""
                    CREATE TABLE subscriptions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant_name TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        next_payment_date TEXT,
                        state TEXT NOT NULL,
                        bank_name TEXT,
                        umn TEXT,
                        category TEXT,
                        sms_body TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)
                
                // Step 2: Copy data from old table to new table
                db.execSQL("""
                    INSERT INTO subscriptions_new (id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at)
                    SELECT id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at
                    FROM subscriptions
                """)
                
                // Step 3: Drop old table
                db.execSQL("DROP TABLE subscriptions")
                
                // Step 4: Rename new table to original name
                db.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
            }
        }

        /**
         * Manual migration from version 21 to 22.
         * Adds transaction_rules and rule_applications tables for the rule engine.
         * Note: This migration is kept for users who might be on v21.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create transaction_rules table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transaction_rules (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        priority INTEGER NOT NULL,
                        conditions TEXT NOT NULL,
                        actions TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_system_template INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)

                // Create indices for transaction_rules
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)")

                // Create rule_applications table
                db.execSQL("""
                    CREATE TABLE rule_applications (
                        id TEXT PRIMARY KEY NOT NULL,
                        rule_id TEXT NOT NULL,
                        rule_name TEXT NOT NULL,
                        transaction_id TEXT NOT NULL,
                        fields_modified TEXT NOT NULL,
                        applied_at TEXT NOT NULL,
                        FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                """)

                // Create indices for rule_applications
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)")
            }
        }

        /**
         * Manual migration from version 22 to 23.
         * Adds transaction_rules and rule_applications tables for the rule engine.
         * This is for users who were already on v22 before the rules feature was added.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop table if exists to ensure clean state
                db.execSQL("DROP TABLE IF EXISTS transaction_rules")
                db.execSQL("DROP TABLE IF EXISTS rule_applications")

                // Create transaction_rules table with all required columns
                db.execSQL("""
                    CREATE TABLE transaction_rules (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        priority INTEGER NOT NULL,
                        conditions TEXT NOT NULL,
                        actions TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_system_template INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)

                // Create indices for transaction_rules
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)")

                // Create rule_applications table
                db.execSQL("""
                    CREATE TABLE rule_applications (
                        id TEXT PRIMARY KEY NOT NULL,
                        rule_id TEXT NOT NULL,
                        rule_name TEXT NOT NULL,
                        transaction_id TEXT NOT NULL,
                        fields_modified TEXT NOT NULL,
                        applied_at TEXT NOT NULL,
                        FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                """)

                // Create indices for rule_applications
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)")
            }
        }
    }
    
    /**
     * Example AutoMigrationSpec for renaming tables or columns.
     * Uncomment and modify when needed.
     */
    // @RenameTable(fromTableName = "transactions", toTableName = "user_transactions")
    // @RenameColumn(
    //     tableName = "transactions",
    //     fromColumnName = "merchant_name", 
    //     toColumnName = "vendor_name"
    // )
    // class Migration1To2 : AutoMigrationSpec {
    //     override fun onPostMigrate(db: SupportSQLiteDatabase) {
    //         // Perform additional operations after migration if needed
    //         // Example: Update default values, create indexes, etc.
    //     }
    // }
}

/**
 * Migration from version 4 to 5.
 * - Removes sessionId column from chat_messages table
 * - Adds isSystemPrompt column to chat_messages table
 */
@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "chat_messages",
        columnName = "sessionId"
    )
)
class Migration4To5 : AutoMigrationSpec

/**
 * Migration from version 7 to 8.
 * - Adds categories table with default categories
 */
class Migration7To8 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        // Insert default categories
        val categories = listOf(
            Triple("Food & Dining", "#FC8019", false),
            Triple("Groceries", "#5AC85A", false),
            Triple("Transportation", "#000000", false),
            Triple("Shopping", "#FF9900", false),
            Triple("Bills & Utilities", "#4CAF50", false),
            Triple("Entertainment", "#E50914", false),
            Triple("Healthcare", "#10847E", false),
            Triple("Investments", "#00D09C", false),
            Triple("Banking", "#004C8F", false),
            Triple("Personal Care", "#6A4C93", false),
            Triple("Education", "#673AB7", false),
            Triple("Mobile", "#2A3890", false),
            Triple("Fitness", "#FF3278", false),
            Triple("Insurance", "#0066CC", false),
            Triple("Travel", "#00BCD4", false),
            Triple("Salary", "#4CAF50", true),
            Triple("Income", "#4CAF50", true),
            Triple("Others", "#757575", false)
        )
        
        categories.forEachIndexed { index, (name, color, isIncome) ->
            db.execSQL("""
                INSERT INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(), arrayOf<Any>(name, color, if (isIncome) 1 else 0, index + 1))
        }
    }
}

/**
 * Migration from version 10 to 11.
 * - Adds account_balances table for tracking account balance history
 * - Migrates existing balance data from transactions table
 */
class Migration10To11 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        // Migrate existing balance data from transactions table
        db.execSQL("""
            INSERT INTO account_balances (bank_name, account_last4, balance, timestamp, transaction_id, created_at)
            SELECT 
                bank_name,
                account_number,
                balance_after,
                date_time,
                id,
                created_at
            FROM transactions
            WHERE balance_after IS NOT NULL 
                AND bank_name IS NOT NULL 
                AND account_number IS NOT NULL
        """.trimIndent())
    }
}