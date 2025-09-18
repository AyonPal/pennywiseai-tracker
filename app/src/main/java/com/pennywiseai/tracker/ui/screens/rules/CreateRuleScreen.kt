package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleScreen(
    onNavigateBack: () -> Unit,
    onSaveRule: (TransactionRule) -> Unit,
    existingRule: TransactionRule? = null
) {
    var ruleName by remember { mutableStateOf(existingRule?.name ?: "") }
    var description by remember { mutableStateOf(existingRule?.description ?: "") }

    // Simple condition state
    var selectedField by remember { mutableStateOf(TransactionField.AMOUNT) }
    var fieldDropdownExpanded by remember { mutableStateOf(false) }
    var selectedOperator by remember { mutableStateOf(ConditionOperator.LESS_THAN) }
    var conditionValue by remember { mutableStateOf("") }

    // Simple action state
    var actionField by remember { mutableStateOf(TransactionField.CATEGORY) }
    var actionFieldDropdownExpanded by remember { mutableStateOf(false) }
    var actionValue by remember { mutableStateOf("") }

    // Common presets for quick setup
    val commonPresets = listOf(
        "Small amounts → Food" to {
            ruleName = "Small Food Payments"
            selectedField = TransactionField.AMOUNT
            selectedOperator = ConditionOperator.LESS_THAN
            conditionValue = "200"
            actionField = TransactionField.CATEGORY
            actionValue = "Food & Dining"
        },
        "Standardize Merchant" to {
            ruleName = "Standardize Merchant Name"
            selectedField = TransactionField.MERCHANT
            selectedOperator = ConditionOperator.CONTAINS
            conditionValue = "AMZN"
            actionField = TransactionField.MERCHANT
            actionValue = "Amazon"
        },
        "Mark as Income" to {
            ruleName = "Mark Credits as Income"
            selectedField = TransactionField.SMS_TEXT
            selectedOperator = ConditionOperator.CONTAINS
            conditionValue = "credited"
            actionField = TransactionField.TYPE
            actionValue = "income"
        },
        "Add Description" to {
            ruleName = "Add Transaction Note"
            selectedField = TransactionField.MERCHANT
            selectedOperator = ConditionOperator.CONTAINS
            conditionValue = ""
            actionField = TransactionField.NARRATION
            actionValue = ""
        }
    )

    PennyWiseScaffold(
        title = if (existingRule != null) "Edit Rule" else "Create Rule",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            TextButton(
                onClick = {
                    if (ruleName.isNotBlank() && conditionValue.isNotBlank() && actionValue.isNotBlank()) {
                        val rule = TransactionRule(
                            id = existingRule?.id ?: UUID.randomUUID().toString(),
                            name = ruleName,
                            description = description.takeIf { it.isNotBlank() },
                            priority = 100,
                            conditions = listOf(
                                RuleCondition(
                                    field = selectedField,
                                    operator = selectedOperator,
                                    value = conditionValue
                                )
                            ),
                            actions = listOf(
                                RuleAction(
                                    field = actionField,
                                    actionType = ActionType.SET,
                                    value = actionValue
                                )
                            ),
                            isActive = true
                        )
                        onSaveRule(rule)
                        // Navigation is handled in PennyWiseNavHost after saving
                    }
                },
                enabled = ruleName.isNotBlank() && conditionValue.isNotBlank() && actionValue.isNotBlank()
            ) {
                Text("Save")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding() // Push content up when keyboard appears
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Quick presets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Quick Templates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonPresets.forEach { (label, action) ->
                            AssistChip(
                                onClick = action,
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }

            // Rule name and description
            OutlinedTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g., Food expenses under ₹200") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("What does this rule do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Condition section
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "When",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Field selector
                    ExposedDropdownMenuBox(
                        expanded = fieldDropdownExpanded,
                        onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = when(selectedField) {
                                TransactionField.AMOUNT -> "Amount"
                                TransactionField.MERCHANT -> "Merchant"
                                TransactionField.CATEGORY -> "Category"
                                TransactionField.SMS_TEXT -> "SMS Text"
                                TransactionField.TYPE -> "Transaction Type"
                                else -> "Amount"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Field") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = fieldDropdownExpanded,
                            onDismissRequest = { fieldDropdownExpanded = false }
                        ) {
                            listOf(
                                TransactionField.AMOUNT to "Amount",
                                TransactionField.TYPE to "Transaction Type",
                                TransactionField.CATEGORY to "Category",
                                TransactionField.MERCHANT to "Merchant",
                                TransactionField.SMS_TEXT to "SMS Text",
                                TransactionField.BANK_NAME to "Bank Name"
                            ).forEach { (field, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedField = field
                                        fieldDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Operator selector
                    val operators = when(selectedField) {
                        TransactionField.AMOUNT -> listOf(
                            ConditionOperator.LESS_THAN to "<",
                            ConditionOperator.GREATER_THAN to ">",
                            ConditionOperator.EQUALS to "="
                        )
                        else -> listOf(
                            ConditionOperator.CONTAINS to "contains",
                            ConditionOperator.EQUALS to "equals",
                            ConditionOperator.STARTS_WITH to "starts with"
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        operators.forEach { (op, label) ->
                            FilterChip(
                                selected = selectedOperator == op,
                                onClick = { selectedOperator = op },
                                label = { Text(label) }
                            )
                        }
                    }

                    // Value input
                    OutlinedTextField(
                        value = conditionValue,
                        onValueChange = { conditionValue = it },
                        label = { Text("Value") },
                        placeholder = {
                            Text(
                                when(selectedField) {
                                    TransactionField.AMOUNT -> "e.g., 200"
                                    TransactionField.MERCHANT -> "e.g., Swiggy"
                                    TransactionField.SMS_TEXT -> "e.g., salary"
                                    else -> "Enter value"
                                }
                            )
                        },
                        keyboardOptions = if (selectedField == TransactionField.AMOUNT) {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else {
                            KeyboardOptions.Default
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Action section
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Then",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action field selector
                    ExposedDropdownMenuBox(
                        expanded = actionFieldDropdownExpanded,
                        onExpandedChange = { actionFieldDropdownExpanded = !actionFieldDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = when(actionField) {
                                TransactionField.CATEGORY -> "Set Category"
                                TransactionField.MERCHANT -> "Set Merchant Name"
                                TransactionField.TYPE -> "Set Transaction Type"
                                TransactionField.NARRATION -> "Set Description"
                                else -> "Set Field"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Action") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionFieldDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = actionFieldDropdownExpanded,
                            onDismissRequest = { actionFieldDropdownExpanded = false }
                        ) {
                            listOf(
                                TransactionField.CATEGORY to "Set Category",
                                TransactionField.MERCHANT to "Set Merchant Name",
                                TransactionField.TYPE to "Set Transaction Type",
                                TransactionField.NARRATION to "Set Description"
                            ).forEach { (field, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        actionField = field
                                        actionFieldDropdownExpanded = false
                                        actionValue = "" // Reset value when changing field
                                    }
                                )
                            }
                        }
                    }

                    // Dynamic value input based on selected action field
                    when (actionField) {
                        TransactionField.CATEGORY -> {
                            // Category chips and input
                            val commonCategories = listOf(
                                "Food & Dining", "Transportation", "Shopping",
                                "Bills & Utilities", "Entertainment", "Healthcare",
                                "Investments", "Others"
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                commonCategories.forEach { category ->
                                    FilterChip(
                                        selected = actionValue == category,
                                        onClick = { actionValue = category },
                                        label = { Text(category, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Category Name") },
                                placeholder = { Text("e.g., Rent") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        TransactionField.TYPE -> {
                            // Transaction type chips
                            Text(
                                text = "Select transaction type:",
                                style = MaterialTheme.typography.bodySmall
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("income", "expense", "transfer").forEach { type ->
                                    FilterChip(
                                        selected = actionValue == type,
                                        onClick = { actionValue = type },
                                        label = {
                                            Text(
                                                type.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        TransactionField.MERCHANT -> {
                            // Merchant name input with common suggestions
                            val commonMerchants = listOf(
                                "Amazon", "Swiggy", "Zomato", "Uber",
                                "Netflix", "Google", "Flipkart"
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                commonMerchants.forEach { merchant ->
                                    AssistChip(
                                        onClick = { actionValue = merchant },
                                        label = { Text(merchant, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Merchant Name") },
                                placeholder = { Text("e.g., Amazon") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        TransactionField.NARRATION -> {
                            // Description/Narration input
                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Description") },
                                placeholder = { Text("e.g., Monthly subscription payment") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 3
                            )
                        }

                        else -> {
                            // Generic text input for other fields
                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Value") },
                                placeholder = { Text("Enter value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Preview
            if (ruleName.isNotBlank() && conditionValue.isNotBlank() && actionValue.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "Rule Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildString {
                                append("When ")
                                append(when(selectedField) {
                                    TransactionField.AMOUNT -> "amount"
                                    TransactionField.TYPE -> "type"
                                    TransactionField.CATEGORY -> "category"
                                    TransactionField.MERCHANT -> "merchant"
                                    TransactionField.SMS_TEXT -> "SMS text"
                                    TransactionField.BANK_NAME -> "bank"
                                    else -> "field"
                                })
                                append(" ")
                                append(when(selectedOperator) {
                                    ConditionOperator.LESS_THAN -> "is less than"
                                    ConditionOperator.GREATER_THAN -> "is greater than"
                                    ConditionOperator.EQUALS -> "equals"
                                    ConditionOperator.CONTAINS -> "contains"
                                    ConditionOperator.STARTS_WITH -> "starts with"
                                    else -> "matches"
                                })
                                append(" ")
                                append(conditionValue)
                                append(", ")
                                append(when(actionField) {
                                    TransactionField.CATEGORY -> "set category to "
                                    TransactionField.MERCHANT -> "set merchant to "
                                    TransactionField.TYPE -> "set type to "
                                    TransactionField.NARRATION -> "set description to "
                                    else -> "set field to "
                                })
                                append(actionValue)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}