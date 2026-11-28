package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateTimeHelper
import com.example.util.InvoicePrintManager
import com.example.util.KhataBalanceHelper
import com.example.util.KhataMath
import com.example.util.KhataMetaCodec
import com.example.util.XlsExporter
import com.example.util.rememberAppHaptics
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun LedgerScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val accounts by viewModel.allLedgerAccounts.collectAsState()
    val allEntries by viewModel.allLedgerEntries.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Customers, 1 = Contractors, 2 = Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("BALANCE_DESC") } // BALANCE_DESC, NAME_ASC, DUE_ONLY, OVERDUE_ONLY, OVER_LIMIT, ARCHIVED
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var selectedAccountForDetail by remember { mutableStateOf<LedgerAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<LedgerAccount?>(null) }

    var quickEntryAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var quickEntryType by remember { mutableStateOf("GAVE") }

    val inrFormat = remember {
        NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    val todayIso = remember { DateTimeHelper.localDateKey() }

    val overdueAccountIds = remember(accounts, allEntries, todayIso) {
        val accountMap = accounts.associateBy { it.id }
        allEntries.mapNotNull { entry ->
            val acc = accountMap[entry.accountId] ?: return@mapNotNull null
            if (KhataBalanceHelper.isEntryOverdue(acc, entry, todayIso)) acc.id else null
        }.toSet()
    }

    val lastDateByAccount = remember(allEntries) {
        allEntries.groupBy { it.accountId }.mapValues { (_, list) ->
            list.maxOfOrNull { DateTimeHelper.normalizeDateToIso(it.date, it.date) } ?: ""
        }
    }

    val activeAccounts = remember(accounts) {
        accounts.filter { !KhataMetaCodec.parseAccountMeta(it.notes).archived }
    }
    val customerCount = remember(activeAccounts) { activeAccounts.count { it.type.equals("CUSTOMER", ignoreCase = true) } }
    val contractorCount = remember(activeAccounts) { activeAccounts.count { it.type.equals("CONTRACTOR", ignoreCase = true) } }
    val supplierCount = remember(activeAccounts) { activeAccounts.count { it.type.equals("SUPPLIER", ignoreCase = true) } }

    val filteredAccounts = remember(accounts, selectedTab, searchQuery, sortMode, overdueAccountIds) {
        val base = accounts.filter { acc ->
            val meta = KhataMetaCodec.parseAccountMeta(acc.notes)
            val matchArchive = if (sortMode == "ARCHIVED") meta.archived else !meta.archived
            val matchType = when (selectedTab) {
                0 -> acc.type.equals("CUSTOMER", ignoreCase = true)
                1 -> acc.type.equals("CONTRACTOR", ignoreCase = true)
                else -> acc.type.equals("SUPPLIER", ignoreCase = true)
            }
            val matchQuery = searchQuery.isEmpty() ||
                acc.name.contains(searchQuery, ignoreCase = true) ||
                acc.phone.contains(searchQuery, ignoreCase = true) ||
                meta.cleanNotes.contains(searchQuery, ignoreCase = true) ||
                meta.gstin.contains(searchQuery, ignoreCase = true) ||
                meta.siteOrTag.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (sortMode) {
                "DUE_ONLY" -> acc.netBalance > 0.005
                "OVERDUE_ONLY" -> acc.id in overdueAccountIds
                "OVER_LIMIT" -> acc.creditLimit > 0.0 && acc.netBalance > acc.creditLimit
                else -> true
            }
            matchArchive && matchType && matchQuery && matchFilter
        }
        when (sortMode) {
            "NAME_ASC" -> base.sortedBy { it.name.lowercase(Locale.US) }
            else -> base.sortedByDescending { abs(it.netBalance) }
        }
    }

    val totalReceivable = remember(activeAccounts) {
        KhataMath.roundPaise(activeAccounts.filter { !it.type.equals("SUPPLIER", ignoreCase = true) && it.netBalance > 0.005 }.sumOf { it.netBalance })
    }
    val totalPayable = remember(activeAccounts) {
        KhataMath.roundPaise(activeAccounts.filter { it.type.equals("SUPPLIER", ignoreCase = true) && it.netBalance > 0.005 }.sumOf { it.netBalance })
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken.trim()
            }
        }
    }

    val detailAccount = selectedAccountForDetail
    if (detailAccount != null) {
        BackHandler {
            selectedAccountForDetail = null
        }
        val latestAccount = accounts.find { it.id == detailAccount.id } ?: detailAccount
        LedgerAccountDetailScreen(
            account = latestAccount,
            viewModel = viewModel,
            isAdmin = isAdmin,
            onBack = { selectedAccountForDetail = null },
            onAccountDeleted = { selectedAccountForDetail = null }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Khata & Ledger",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (overdueAccountIds.isNotEmpty()) {
                                "${overdueAccountIds.size} overdue party ${if (overdueAccountIds.size == 1) "account" else "accounts"} · Track credit & payouts"
                            } else {
                                "Track customer credit, contractors & suppliers"
                            },
                            fontSize = 12.sp,
                            color = if (overdueAccountIds.isNotEmpty()) ext.danger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val xml = XlsExporter.buildKhataWorkbookXml(accounts)
                                    val res = XlsExporter.saveXlsToDownloads(context, "Khata_Accounts", xml)
                                    res.onSuccess { path ->
                                        haptics.confirm()
                                        viewModel.showToast("Exported to $path ✓")
                                    }.onFailure { err ->
                                        viewModel.showToast("Export failed: ${err.message}")
                                    }
                                }
                                .testTag("khata_export_xls_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Export Khata XLS",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showSortMenu = true }
                                    .testTag("khata_sort_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Sort & Filter Accounts",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf(
                                    "BALANCE_DESC" to "Highest Balance First",
                                    "NAME_ASC" to "Alphabetical (A–Z)",
                                    "DUE_ONLY" to "Due Balances Only",
                                    "OVERDUE_ONLY" to "Overdue Parties (${overdueAccountIds.size})",
                                    "OVER_LIMIT" to "Over Credit Limit",
                                    "ARCHIVED" to "Archived Accounts"
                                ).forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (sortMode == key) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortMode == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (sortMode == key) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            sortMode = key
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Dual Summary Cards: YOU WILL GET | YOU WILL PAY
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.positive.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = ext.positive,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "YOU WILL GET",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ext.positive,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "₹${inrFormat.format(totalReceivable)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ext.positive,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Customer & Site Credit",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = ext.danger,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "YOU WILL PAY",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ext.danger,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "₹${inrFormat.format(totalPayable)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ext.danger,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Supplier Payables",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. 3-Tab Segmented Switcher: Customers | Contractors | Suppliers
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "Customers ($customerCount)", "khata_customers_tab"),
                            Triple(1, "Contractors ($contractorCount)", "khata_contractors_tab"),
                            Triple(2, "Suppliers ($supplierCount)", "khata_suppliers_tab")
                        )
                        tabs.forEach { (tabIdx, title, tag) ->
                            val isSelected = selectedTab == tabIdx
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { selectedTab = tabIdx }
                                    .testTag(tag)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search name, phone, GSTIN, or site...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Search")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak customer, contractor, or supplier name...")
                                    }
                                    try {
                                        voiceSearchLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        viewModel.showToast("Voice input unavailable")
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice search khata",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("khata_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )
            }

            // 5. Account Cards or Empty State
            if (filteredAccounts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (accounts.isEmpty()) "No accounts in Khata yet" else "No matching accounts found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap below to add a customer, contractor, or supplier ledger.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredAccounts, key = { it.id }) { acc ->
                    LedgerAccountCard(
                        account = acc,
                        inrFormat = inrFormat,
                        isOverdue = acc.id in overdueAccountIds,
                        lastActivityDate = lastDateByAccount[acc.id].orEmpty(),
                        isAdmin = isAdmin,
                        onClick = { selectedAccountForDetail = acc },
                        onEdit = { editingAccount = acc },
                        onGive = {
                            quickEntryAccount = acc
                            quickEntryType = "GAVE"
                        },
                        onGot = {
                            quickEntryAccount = acc
                            quickEntryType = "GOT"
                        },
                        onDelete = { accountToDelete = acc }
                    )
                }
            }
        }

        Button(
            onClick = { showAddAccountDialog = true },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .height(48.dp)
                .testTag("khata_add_account_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (selectedTab) {
                    1 -> "Add Contractor"
                    2 -> "Add Supplier"
                    else -> "Add Customer"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showAddAccountDialog) {
        val defaultType = when (selectedTab) {
            1 -> "CONTRACTOR"
            2 -> "SUPPLIER"
            else -> "CUSTOMER"
        }
        AddAccountDialog(
            defaultType = defaultType,
            existingAccounts = accounts,
            onDismiss = { showAddAccountDialog = false },
            onSave = { acc ->
                haptics.confirm()
                viewModel.saveLedgerAccount(acc)
                showAddAccountDialog = false
            }
        )
    }

    editingAccount?.let { target ->
        EditAccountDialog(
            account = target,
            onDismiss = { editingAccount = null },
            onSave = { updated ->
                haptics.confirm()
                viewModel.saveLedgerAccount(updated)
                editingAccount = null
            }
        )
    }

    quickEntryAccount?.let { target ->
        AddLedgerEntryDialog(
            account = target,
            initialType = quickEntryType,
            onDismiss = { quickEntryAccount = null },
            onSave = { type, amount, date, desc, ref, paymentMode, paymentRef, dueDate, siteName, alsoRecordCashflow ->
                haptics.confirm()
                viewModel.addLedgerEntry(
                    accountId = target.id,
                    type = type,
                    amount = amount,
                    date = date,
                    description = desc,
                    billRef = ref,
                    paymentMode = paymentMode,
                    paymentRef = paymentRef,
                    dueDate = dueDate,
                    siteName = siteName,
                    alsoRecordCashflow = alsoRecordCashflow
                )
                quickEntryAccount = null
            }
        )
    }

    accountToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Khata Account?") },
            text = {
                Text("Are you sure you want to delete ${target.name} along with all transaction history? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.reject()
                        viewModel.deleteLedgerAccount(target.id)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LedgerAccountCard(
    account: LedgerAccount,
    inrFormat: NumberFormat,
    isOverdue: Boolean = false,
    lastActivityDate: String = "",
    isAdmin: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onGive: () -> Unit,
    onGot: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isSupplier = account.type.equals("SUPPLIER", ignoreCase = true)
    val meta = remember(account.notes) { KhataMetaCodec.parseAccountMeta(account.notes) }
    val status = remember(account.type, account.netBalance) {
        KhataBalanceHelper.statusFor(account.type, account.netBalance)
    }
    val balanceColor = when {
        status.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
        status.isPositiveHealth -> ext.positive
        else -> ext.danger
    }
    val isOverLimit = !isSupplier && account.creditLimit > 0.0 && account.netBalance > account.creditLimit

    val avatarPalette = listOf(ext.info, ext.positive, MaterialTheme.colorScheme.primary, ext.purple, ext.warning)
    val avatarColor = avatarPalette[(account.name.hashCode() and 0x7FFFFFFF) % avatarPalette.size]

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isOverdue || isOverLimit) ext.danger.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("khata_account_card_${account.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.16f))
                    ) {
                        Text(
                            text = account.name.trim().take(1).uppercase().ifBlank { "?" },
                            fontWeight = FontWeight.ExtraBold,
                            color = avatarColor,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.name.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val subtitleParts = buildList {
                            if (account.phone.isNotBlank()) add(account.phone)
                            if (meta.siteOrTag.isNotBlank()) add(meta.siteOrTag)
                            if (lastActivityDate.isNotBlank()) add("Last: $lastActivityDate")
                            else if (meta.cleanNotes.isNotBlank()) add(meta.cleanNotes)
                        }
                        Text(
                            text = subtitleParts.joinToString(" · ").ifBlank { account.type },
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = status.label,
                            fontSize = 9.5.sp,
                            color = balanceColor,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "₹${inrFormat.format(abs(KhataMath.roundPaise(account.netBalance)))}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = balanceColor
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    var showAccountMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showAccountMenu = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Account Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Ledger") },
                                onClick = {
                                    showAccountMenu = false
                                    onClick()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Account") },
                                onClick = {
                                    showAccountMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Delete Account") },
                                    onClick = {
                                        showAccountMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ext.danger) }
                                )
                            }
                        }
                    }
                }
            }

            if (isOverdue || isOverLimit || meta.archived) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isOverdue) {
                        Surface(color = ext.danger.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                            Text("OVERDUE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ext.danger, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    if (isOverLimit) {
                        Surface(color = ext.warning.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                            Text("LIMIT EXCEEDED", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ext.warning, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    if (meta.archived) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text("ARCHIVED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onGive,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSupplier) ext.positive.copy(alpha = 0.12f) else ext.danger.copy(alpha = 0.14f),
                        contentColor = if (isSupplier) ext.positive else ext.danger
                    ),
                    border = BorderStroke(1.dp, (if (isSupplier) ext.positive else ext.danger).copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isSupplier) "- PAID SUPPLIER" else "- CREDIT GIVEN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onGot,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSupplier) ext.danger else ext.positive,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isSupplier) "+ CREDIT BILL" else "+ GOT PAYMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerAccountDetailScreen(
    account: LedgerAccount,
    viewModel: StockViewModel,
    isAdmin: Boolean = true,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit = onBack
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val bankingInfo by viewModel.bankingInfo.collectAsState()
    val entriesFlow = remember(account.id) { viewModel.getEntriesForAccount(account.id) }
    val entries by entriesFlow.collectAsState(initial = emptyList())

    var showEntryDialog by remember { mutableStateOf(false) }
    var entryType by remember { mutableStateOf("GAVE") }
    var entryToEdit by remember { mutableStateOf<LedgerEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<LedgerEntry?>(null) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showEditAccountDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    var entrySearch by remember { mutableStateOf("") }
    var periodFilter by remember { mutableStateOf("ALL") } // ALL, THIS_MONTH, LAST_30
    var entryTypeFilter by remember { mutableStateOf("ALL") } // ALL, GAVE, GOT, OVERDUE

    val isSupplier = account.type.equals("SUPPLIER", ignoreCase = true)
    val accMeta = remember(account.notes) { KhataMetaCodec.parseAccountMeta(account.notes) }
    val status = remember(account.type, account.netBalance) {
        KhataBalanceHelper.statusFor(account.type, account.netBalance)
    }
    val balanceColor = when {
        status.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
        status.isPositiveHealth -> ext.positive
        else -> ext.danger
    }
    val storeTitle = effectiveStoreTitle.ifBlank { "S.A.HARDWARE" }
    val todayIso = remember { DateTimeHelper.localDateKey() }

    val fromDateIso = remember(periodFilter, todayIso) {
        when (periodFilter) {
            "THIS_MONTH" -> todayIso.take(7) + "-01"
            "LAST_30" -> shiftDaysIso(todayIso, -30)
            else -> ""
        }
    }

    val filteredEntries = remember(entries, fromDateIso, entryTypeFilter, entrySearch, account, todayIso) {
        entries.filter { e ->
            val iso = DateTimeHelper.normalizeDateToIso(e.date, e.date)
            val meta = KhataMetaCodec.parseEntryMeta(e.description)
            val matchPeriod = fromDateIso.isBlank() || iso >= fromDateIso
            val matchType = when (entryTypeFilter) {
                "GAVE" -> e.type.equals("GAVE", ignoreCase = true)
                "GOT" -> e.type.equals("GOT", ignoreCase = true)
                "OVERDUE" -> KhataBalanceHelper.isEntryOverdue(account, e, todayIso)
                else -> true
            }
            val matchQuery = entrySearch.isBlank() ||
                meta.cleanDescription.contains(entrySearch, ignoreCase = true) ||
                e.billRef.contains(entrySearch, ignoreCase = true) ||
                meta.paymentMode.contains(entrySearch, ignoreCase = true) ||
                meta.paymentRef.contains(entrySearch, ignoreCase = true) ||
                meta.siteName.contains(entrySearch, ignoreCase = true)
            matchPeriod && matchType && matchQuery
        }
    }

    val periodGave = remember(filteredEntries) {
        KhataMath.roundPaise(filteredEntries.filter { it.type.equals("GAVE", ignoreCase = true) }.sumOf { it.amount })
    }
    val periodGot = remember(filteredEntries) {
        KhataMath.roundPaise(filteredEntries.filter { it.type.equals("GOT", ignoreCase = true) }.sumOf { it.amount })
    }

    if (showEditAccountDialog) {
        EditAccountDialog(
            account = account,
            onDismiss = { showEditAccountDialog = false },
            onSave = { updated ->
                haptics.confirm()
                viewModel.saveLedgerAccount(updated)
                showEditAccountDialog = false
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete Khata Account?") },
            text = {
                Text("Delete ${account.name} along with all ${entries.size} transaction records? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.reject()
                        viewModel.deleteLedgerAccount(account.id)
                        showDeleteAccountConfirm = false
                        onAccountDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("Cancel") }
            }
        )
    }

    entryToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Transaction Entry?") },
            text = {
                Text("Delete entry of ₹${KhataMath.formatAmount(target.amount)}? The account running balances will be recalculated chronologically.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.reject()
                        viewModel.deleteLedgerEntry(target.id, account.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    entryToEdit?.let { targetEntry ->
        EditLedgerEntryDialog(
            entry = targetEntry,
            account = account,
            onDismiss = { entryToEdit = null },
            onSave = { updated ->
                haptics.confirm()
                viewModel.updateLedgerEntry(updated)
                entryToEdit = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val metaSub = buildList {
                            if (account.phone.isNotBlank()) add(account.phone)
                            add(account.type)
                            if (accMeta.gstin.isNotBlank()) add("GST:${accMeta.gstin}")
                        }.joinToString(" · ")
                        Text(
                            text = metaSub,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showEditAccountDialog = true },
                        modifier = Modifier.testTag("khata_edit_account_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Account", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }, modifier = Modifier.testTag("khata_statement_export_menu")) {
                            Icon(Icons.Default.Share, contentDescription = "Export Statement", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share PDF Statement") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val pdf = InvoicePrintManager.createLedgerStatementPdf(
                                        context = context,
                                        account = account,
                                        entries = entries,
                                        storeName = storeTitle,
                                        fromDate = fromDateIso,
                                        toDate = if (fromDateIso.isNotBlank()) todayIso else "",
                                        upiId = bankingInfo.upiId
                                    )
                                    InvoicePrintManager.sharePdf(context, pdf, "Statement - ${account.name}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print / Save PDF") },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val pdf = InvoicePrintManager.createLedgerStatementPdf(
                                        context = context,
                                        account = account,
                                        entries = entries,
                                        storeName = storeTitle,
                                        fromDate = fromDateIso,
                                        toDate = if (fromDateIso.isNotBlank()) todayIso else "",
                                        upiId = bankingInfo.upiId
                                    )
                                    InvoicePrintManager.printPdf(context, pdf, "Statement_${account.name}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Statement (.XLS)") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val xml = XlsExporter.buildLedgerStatementWorkbookXml(account, filteredEntries, storeTitle)
                                    val res = XlsExporter.saveXlsToDownloads(context, "Khata_${account.name}", xml)
                                    res.onSuccess { viewModel.showToast("Saved XLS to $it ✓") }
                                        .onFailure { viewModel.showToast("Export failed: ${it.message}") }
                                }
                            )
                        }
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = { showDeleteAccountConfirm = true },
                            modifier = Modifier.testTag("khata_delete_account_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Balance & Credit Limit Summary Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(status.label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = balanceColor)
                            Text(
                                text = "₹${KhataMath.formatAmount(account.netBalance)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 23.sp,
                                color = balanceColor
                            )
                        }

                        if (account.phone.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val msg = KhataBalanceHelper.buildWhatsAppReminderText(
                                        account = account,
                                        storeName = storeTitle,
                                        upiId = bankingInfo.upiId,
                                        entries = entries,
                                        todayIso = todayIso
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?phone=${account.phone.replace("+", "").replace(" ", "")}&text=${Uri.encode(msg)}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.w("LedgerScreen", "Could not open WhatsApp", e)
                                        viewModel.showToast("Could not open WhatsApp")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ext.positive, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 3-metric period totals strip
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (isSupplier) "Total Paid" else "Total Gave", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${KhataMath.formatAmount(periodGave)}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (isSupplier) ext.positive else ext.danger)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isSupplier) "Total Bills" else "Total Got", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${KhataMath.formatAmount(periodGot)}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (isSupplier) ext.danger else ext.positive)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Entries", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredEntries.size}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    if (!isSupplier && account.creditLimit > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val util = (account.netBalance.coerceAtLeast(0.0) / account.creditLimit).toFloat().coerceIn(0f, 1f)
                        val over = account.netBalance > account.creditLimit
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "Credit Limit: ₹${KhataMath.formatAmount(account.creditLimit)}",
                                fontSize = 10.5.sp,
                                color = if (over) ext.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (over) FontWeight.Bold else FontWeight.Medium
                            )
                            if (over) {
                                Text("OVER LIMIT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ext.danger)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { util },
                            color = if (over) ext.danger else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                    }

                    if (accMeta.cleanNotes.isNotBlank() || accMeta.creditDays > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val noteText = buildList {
                                if (accMeta.creditDays > 0) add("Terms: Net ${accMeta.creditDays} days")
                                if (accMeta.siteOrTag.isNotBlank()) add("Site: ${accMeta.siteOrTag}")
                                if (accMeta.cleanNotes.isNotBlank()) add(accMeta.cleanNotes)
                            }.joinToString(" · ")
                            Text(
                                text = noteText,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Statement Search & Filter Row
            OutlinedTextField(
                value = entrySearch,
                onValueChange = { entrySearch = it },
                placeholder = { Text("Search bill #, UTR, mode, or site...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (entrySearch.isNotBlank()) {
                    {
                        IconButton(onClick = { entrySearch = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("khata_detail_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL" to "All Dates", "THIS_MONTH" to "This Month", "LAST_30" to "Last 30d").forEach { (k, label) ->
                    FilterChip(
                        selected = periodFilter == k,
                        onClick = { periodFilter = k },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
                listOf("ALL" to "All Types", "GAVE" to "Gave", "GOT" to "Got", "OVERDUE" to "Overdue").forEach { (k, label) ->
                    FilterChip(
                        selected = entryTypeFilter == k,
                        onClick = { entryTypeFilter = k },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching transactions recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val meta = remember(entry.id, entry.description) { KhataMetaCodec.parseEntryMeta(entry.description) }
                        val overdue = remember(entry.id, account.netBalance, todayIso) {
                            KhataBalanceHelper.isEntryOverdue(account, entry, todayIso)
                        }
                        val isGave = entry.type.equals("GAVE", ignoreCase = true)
                        val entryColor = if (isSupplier) {
                            if (isGave) ext.positive else ext.danger
                        } else {
                            if (isGave) ext.danger else ext.positive
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, if (overdue) ext.danger.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth().testTag("khata_entry_card_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = entryColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            val badgeLabel = when {
                                                isSupplier && isGave -> "Paid (-)"
                                                isSupplier && !isGave -> "Bill (+)"
                                                !isSupplier && isGave -> "Gave (-)"
                                                else -> "Got (+)"
                                            }
                                            Text(
                                                text = badgeLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = entryColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (meta.paymentMode.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = meta.paymentMode,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (overdue) {
                                            Surface(color = ext.danger.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = ext.danger, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("OVERDUE", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = ext.danger)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(entry.date, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (meta.cleanDescription.isNotBlank()) {
                                        Text(meta.cleanDescription, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    }
                                    val subTags = buildList {
                                        if (entry.billRef.isNotBlank()) add("Bill #${entry.billRef}")
                                        if (meta.paymentRef.isNotBlank()) add("Ref: ${meta.paymentRef}")
                                        if (meta.siteName.isNotBlank()) add("Site: ${meta.siteName}")
                                        if (meta.dueDate.isNotBlank()) add("Due: ${meta.dueDate}")
                                        if (meta.recordedBy.isNotBlank()) add("By: ${meta.recordedBy.substringBefore("@")}")
                                    }.joinToString(" · ")
                                    if (subTags.isNotBlank()) {
                                        Text(subTags, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isGave) "-" else "+"}₹${KhataMath.formatAmount(entry.amount)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = entryColor
                                        )
                                        val balLabel = if (entry.balanceAfter < -0.005) {
                                            "Adv: ₹${KhataMath.formatAmount(entry.balanceAfter)}"
                                        } else {
                                            "Bal: ₹${KhataMath.formatAmount(entry.balanceAfter)}"
                                        }
                                        Text(
                                            text = balLabel,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))
                                    var showEntryMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showEntryMenu = true },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Entry Options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showEntryMenu,
                                            onDismissRequest = { showEntryMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Entry") },
                                                onClick = {
                                                    showEntryMenu = false
                                                    entryToEdit = entry
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Entry") },
                                                onClick = {
                                                    showEntryMenu = false
                                                    entryToDelete = entry
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            entryType = "GAVE"
                            showEntryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSupplier) ext.positive else ext.danger,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isSupplier) "- PAID SUPPLIER" else "- CREDIT GIVEN", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            entryType = "GOT"
                            showEntryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSupplier) ext.danger else ext.positive,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isSupplier) "+ CREDIT BILL" else "+ GOT PAYMENT", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showEntryDialog) {
        AddLedgerEntryDialog(
            account = account,
            initialType = entryType,
            onDismiss = { showEntryDialog = false },
            onSave = { type, amount, date, desc, ref, paymentMode, paymentRef, dueDate, siteName, alsoRecordCashflow ->
                haptics.confirm()
                viewModel.addLedgerEntry(
                    accountId = account.id,
                    type = type,
                    amount = amount,
                    date = date,
                    description = desc,
                    billRef = ref,
                    paymentMode = paymentMode,
                    paymentRef = paymentRef,
                    dueDate = dueDate,
                    siteName = siteName,
                    alsoRecordCashflow = alsoRecordCashflow
                )
                showEntryDialog = false
            }
        )
    }
}

private fun shiftDaysIso(baseIso: String, deltaDays: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(baseIso) ?: return ""
        val cal = Calendar.getInstance()
        cal.time = parsed
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        sdf.format(cal.time)
    } catch (_: Exception) {
        ""
    }
}
