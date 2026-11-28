package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateTimeHelper
import com.example.util.KhataAccountMeta
import com.example.util.KhataBalanceHelper
import com.example.util.KhataEntryMeta
import com.example.util.KhataMath
import com.example.util.KhataMetaCodec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAccountDialog(
    defaultType: String,
    existingAccounts: List<LedgerAccount> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(defaultType) }
    var gstin by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("") }
    var creditDaysText by remember { mutableStateOf("") }
    var siteOrTag by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }
    var openingIsAdvance by remember { mutableStateOf(false) }

    val ext = LocalExtendedColors.current
    val duplicateMatch = remember(name, phone, type, existingAccounts) {
        val trimName = name.trim()
        val trimPhone = phone.trim()
        existingAccounts.firstOrNull { acc ->
            val sameGroup = acc.type.equals("SUPPLIER", ignoreCase = true) == type.equals("SUPPLIER", ignoreCase = true)
            sameGroup && (
                (trimName.isNotBlank() && acc.name.trim().equals(trimName, ignoreCase = true)) ||
                    (trimPhone.length >= 6 && acc.phone.trim() == trimPhone)
                )
        }
    }

    val todayStr = remember { DateTimeHelper.nowIso() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 500.dp),
        title = {
            Text(
                when (type) {
                    "SUPPLIER" -> "Add Supplier"
                    "CONTRACTOR" -> "Add Contractor"
                    else -> "Add Customer"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full / Firm Name *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().testTag("khata_account_name_input"),
                    singleLine = true
                )
                if (duplicateMatch != null) {
                    Surface(
                        color = ext.warning.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ext.warning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Existing Khata found: ${duplicateMatch.name} (${duplicateMatch.type}, Bal: ₹${KhataMath.formatAmount(duplicateMatch.netBalance)})",
                                fontSize = 11.sp,
                                color = ext.warning,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase(Locale.US) },
                        label = { Text("GSTIN (Optional)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / City", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = type == "CUSTOMER",
                        onClick = { type = "CUSTOMER" },
                        label = { Text("Customer", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                    FilterChip(
                        selected = type == "CONTRACTOR",
                        onClick = { type = "CONTRACTOR" },
                        label = { Text("Contractor", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                    FilterChip(
                        selected = type == "SUPPLIER",
                        onClick = { type = "SUPPLIER" },
                        label = { Text("Supplier", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("Credit Limit (₹)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("Optional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp).testTag("khata_account_credit_limit_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = creditDaysText,
                        onValueChange = { creditDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Credit Days", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("e.g. 15 or 30") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = siteOrTag,
                    onValueChange = { siteOrTag = it },
                    label = { Text("Default Site / Route / Tag", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Remarks", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = { Text("Opening Balance (₹)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp).testTag("khata_opening_balance_input"),
                        singleLine = true
                    )
                    FilterChip(
                        selected = !openingIsAdvance,
                        onClick = { openingIsAdvance = false },
                        label = {
                            Text(
                                if (type == "SUPPLIER") "You Pay" else "You Get",
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    )
                    FilterChip(
                        selected = openingIsAdvance,
                        onClick = { openingIsAdvance = true },
                        label = { Text("Advance", fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val rawInit = initialBalanceText.trim().toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0
                    val signedOpening = KhataMath.roundPaise(if (openingIsAdvance) -rawInit else rawInit)
                    val parsedLimit = KhataMath.roundPaise((creditLimitText.trim().toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0))
                    val parsedDays = (creditDaysText.trim().toIntOrNull() ?: 0).coerceIn(0, 3650)
                    val encodedNotes = KhataMetaCodec.encodeAccountMeta(
                        KhataAccountMeta(
                            cleanNotes = notes.trim(),
                            gstin = gstin.trim(),
                            creditDays = parsedDays,
                            siteOrTag = siteOrTag.trim(),
                            archived = false
                        )
                    )
                    val acc = LedgerAccount(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        type = type,
                        netBalance = signedOpening,
                        creditLimit = parsedLimit,
                        notes = encodedNotes,
                        createdAt = todayStr,
                        updatedAt = todayStr
                    )
                    onSave(acc)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("khata_save_account_button")
            ) {
                Text("Save Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditAccountDialog(
    account: LedgerAccount,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit
) {
    val existingMeta = remember(account.id, account.notes) { KhataMetaCodec.parseAccountMeta(account.notes) }
    var name by remember { mutableStateOf(account.name) }
    var phone by remember { mutableStateOf(account.phone) }
    var address by remember { mutableStateOf(account.address) }
    var type by remember { mutableStateOf(account.type) }
    var gstin by remember { mutableStateOf(existingMeta.gstin) }
    var creditLimitText by remember {
        mutableStateOf(if (account.creditLimit > 0.0) KhataMath.formatAmount(account.creditLimit) else "")
    }
    var creditDaysText by remember {
        mutableStateOf(if (existingMeta.creditDays > 0) existingMeta.creditDays.toString() else "")
    }
    var siteOrTag by remember { mutableStateOf(existingMeta.siteOrTag) }
    var notes by remember { mutableStateOf(existingMeta.cleanNotes) }
    var archived by remember { mutableStateOf(existingMeta.archived) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 500.dp),
        title = { Text("Edit Khata Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full / Firm Name *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase(Locale.US) },
                        label = { Text("GSTIN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / City", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = type == "CUSTOMER", onClick = { type = "CUSTOMER" }, label = { Text("Customer") })
                    FilterChip(selected = type == "CONTRACTOR", onClick = { type = "CONTRACTOR" }, label = { Text("Contractor") })
                    FilterChip(selected = type == "SUPPLIER", onClick = { type = "SUPPLIER" }, label = { Text("Supplier") })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("Credit Limit (₹)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp).testTag("khata_edit_credit_limit_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = creditDaysText,
                        onValueChange = { creditDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Credit Days", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = siteOrTag,
                    onValueChange = { siteOrTag = it },
                    label = { Text("Default Site / Route / Tag", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Remarks", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Archive Account", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Hide from active Khata list without deleting history", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val parsedLimit = KhataMath.roundPaise((creditLimitText.trim().toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0))
                    val parsedDays = (creditDaysText.trim().toIntOrNull() ?: 0).coerceIn(0, 3650)
                    val encodedNotes = KhataMetaCodec.encodeAccountMeta(
                        KhataAccountMeta(
                            cleanNotes = notes.trim(),
                            gstin = gstin.trim(),
                            creditDays = parsedDays,
                            siteOrTag = siteOrTag.trim(),
                            archived = archived
                        )
                    )
                    onSave(
                        account.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            type = type,
                            creditLimit = parsedLimit,
                            notes = encodedNotes
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("khata_update_account_button")
            ) {
                Text("Update Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddLedgerEntryDialog(
    account: LedgerAccount,
    initialType: String,
    onDismiss: () -> Unit,
    onSave: (
        type: String,
        amount: Double,
        date: String,
        description: String,
        billRef: String,
        paymentMode: String,
        paymentRef: String,
        dueDate: String,
        siteName: String,
        alsoRecordCashflow: Boolean
    ) -> Unit
) {
    val ext = LocalExtendedColors.current
    val isSupplier = account.type.equals("SUPPLIER", ignoreCase = true)
    val accMeta = remember(account.notes) { KhataMetaCodec.parseAccountMeta(account.notes) }

    var type by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var billRef by remember { mutableStateOf("") }
    val todayDate = remember { DateTimeHelper.localDateKey() }
    var date by remember { mutableStateOf(todayDate) }

    val defaultMode = remember(initialType, isSupplier) {
        if (KhataBalanceHelper.isCreditEntryType(account.type, initialType)) "Credit Bill" else "Cash"
    }
    var paymentMode by remember { mutableStateOf(defaultMode) }
    var paymentRef by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf(accMeta.siteOrTag) }

    val defaultDue = remember(accMeta.creditDays, todayDate) {
        if (accMeta.creditDays > 0 && KhataBalanceHelper.isCreditEntryType(account.type, initialType)) {
            addDaysToIso(todayDate, accMeta.creditDays)
        } else ""
    }
    var dueDate by remember { mutableStateOf(defaultDue) }
    var alsoRecordCashflow by remember { mutableStateOf(false) }

    val isCreditAction = KhataBalanceHelper.isCreditEntryType(account.type, type)
    val enteredAmt = amountText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val projectedBalance = if (isCreditAction) account.netBalance + enteredAmt else account.netBalance - enteredAmt
    val exceedsLimit = !isSupplier && account.creditLimit > 0.0 && isCreditAction && projectedBalance > account.creditLimit

    val gaveLabel = if (isSupplier) "Paid Supplier (-)" else "Credit Given (-)"
    val gotLabel = if (isSupplier) "Bill / Credit (+)" else "Payment Got (+)"
    val titleText = if (type == "GAVE") {
        if (isSupplier) "Record Payment to Supplier" else "Record Credit Given"
    } else {
        if (isSupplier) "Record Purchase / Credit Due" else "Record Payment Received"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 500.dp),
        title = {
            Text(
                text = titleText,
                color = if (isCreditAction) ext.danger else ext.positive,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Account: ${account.name} (${account.type})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = type == "GAVE",
                        onClick = {
                            type = "GAVE"
                            if (paymentMode == "Credit Bill" && isSupplier) paymentMode = "Cash"
                        },
                        label = { Text(gaveLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                    FilterChip(
                        selected = type == "GOT",
                        onClick = {
                            type = "GOT"
                            if (paymentMode == "Credit Bill" && !isSupplier) paymentMode = "Cash"
                        },
                        label = { Text(gotLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("khata_entry_amount_input"),
                    singleLine = true
                )

                if (exceedsLimit) {
                    Surface(
                        color = ext.danger.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ext.danger, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Warning: Balance (₹${KhataMath.formatAmount(projectedBalance)}) exceeds credit limit ₹${KhataMath.formatAmount(account.creditLimit)}",
                                fontSize = 11.sp,
                                color = ext.danger,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text("Payment / Entry Mode", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Cash", "UPI", "Bank", "Cheque", "Credit Bill").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = {
                                paymentMode = mode
                                if (mode == "Credit Bill" && accMeta.creditDays > 0 && dueDate.isBlank()) {
                                    dueDate = addDaysToIso(date, accMeta.creditDays)
                                }
                            },
                            label = { Text(mode, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Items / Reason", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billRef,
                        onValueChange = { billRef = it },
                        label = { Text("Bill No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = paymentRef,
                        onValueChange = { paymentRef = it },
                        label = { Text("UTR / Cheque #", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                if (account.type == "CONTRACTOR" || siteName.isNotBlank() || isCreditAction) {
                    OutlinedTextField(
                        value = siteName,
                        onValueChange = { siteName = it },
                        label = { Text("Site / Project Name (Optional)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (!isCreditAction && paymentMode != "Credit Bill") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { alsoRecordCashflow = !alsoRecordCashflow }
                            .padding(vertical = 2.dp)
                            .testTag("khata_sync_cashflow_checkbox")
                    ) {
                        Checkbox(checked = alsoRecordCashflow, onCheckedChange = { alsoRecordCashflow = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSupplier) "Also log Expense in Daily Cashflow ($paymentMode)"
                            else "Also log Collection as Sale in Daily Cashflow ($paymentMode)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return@Button
                    onSave(
                        type,
                        amt,
                        date.trim(),
                        description.trim(),
                        billRef.trim(),
                        paymentMode.trim(),
                        paymentRef.trim(),
                        dueDate.trim(),
                        siteName.trim(),
                        alsoRecordCashflow
                    )
                },
                enabled = enteredAmt > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "GAVE") ext.danger else ext.positive
                ),
                modifier = Modifier.testTag("khata_save_entry_button")
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditLedgerEntryDialog(
    entry: LedgerEntry,
    account: LedgerAccount,
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit
) {
    val existingMeta = remember(entry.id, entry.description) { KhataMetaCodec.parseEntryMeta(entry.description) }
    val isSupplier = account.type.equals("SUPPLIER", ignoreCase = true)

    var type by remember { mutableStateOf(entry.type) }
    var amountText by remember { mutableStateOf(KhataMath.formatAmount(entry.amount)) }
    var description by remember { mutableStateOf(existingMeta.cleanDescription) }
    var billRef by remember { mutableStateOf(entry.billRef) }
    var date by remember { mutableStateOf(entry.date) }
    var paymentMode by remember { mutableStateOf(existingMeta.paymentMode.ifBlank { "Cash" }) }
    var paymentRef by remember { mutableStateOf(existingMeta.paymentRef) }
    var dueDate by remember { mutableStateOf(existingMeta.dueDate) }
    var siteName by remember { mutableStateOf(existingMeta.siteName) }

    val validAmt = amountText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 500.dp),
        title = { Text("Edit Transaction Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Account: ${account.name} (${account.type})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = type == "GAVE",
                        onClick = { type = "GAVE" },
                        label = { Text(if (isSupplier) "Paid Supplier (-)" else "Credit Given (-)") }
                    )
                    FilterChip(
                        selected = type == "GOT",
                        onClick = { type = "GOT" },
                        label = { Text(if (isSupplier) "Bill / Credit (+)" else "Payment Got (+)") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Cash", "UPI", "Bank", "Cheque", "Credit Bill", "Opening Balance").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = { Text(mode, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Reason", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billRef,
                        onValueChange = { billRef = it },
                        label = { Text("Bill No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = paymentRef,
                        onValueChange = { paymentRef = it },
                        label = { Text("UTR / Cheque #", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it },
                    label = { Text("Site / Project", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validAmt <= 0.0) return@Button
                    val encodedDesc = KhataMetaCodec.encodeEntryMeta(
                        existingMeta.copy(
                            cleanDescription = description.trim(),
                            paymentMode = paymentMode.trim(),
                            paymentRef = paymentRef.trim(),
                            dueDate = dueDate.trim(),
                            siteName = siteName.trim()
                        )
                    )
                    onSave(
                        entry.copy(
                            type = type,
                            amount = KhataMath.roundPaise(validAmt),
                            date = date.trim(),
                            description = encodedDesc,
                            billRef = billRef.trim()
                        )
                    )
                },
                enabled = validAmt > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun addDaysToIso(baseIso: String, days: Int): String {
    if (days <= 0) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(DateTimeHelper.normalizeDateToIso(baseIso, DateTimeHelper.localDateKey())) ?: Date()
        val cal = Calendar.getInstance()
        cal.time = parsed
        cal.add(Calendar.DAY_OF_YEAR, days)
        sdf.format(cal.time)
    } catch (_: Exception) {
        ""
    }
}
