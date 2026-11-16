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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.InvoicePrintManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@Composable
fun LedgerScreen(viewModel: StockViewModel) {
    val ext = LocalExtendedColors.current
    val accounts by viewModel.allLedgerAccounts.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Customers/Contractors, 1 = Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("BALANCE_DESC") } // BALANCE_DESC, NAME_ASC, DUE_ONLY
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
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

    val customerCount = remember(accounts) { accounts.count { it.type != "SUPPLIER" } }
    val supplierCount = remember(accounts) { accounts.count { it.type == "SUPPLIER" } }

    val filteredAccounts = remember(accounts, selectedTab, searchQuery, sortMode) {
        val base = accounts.filter { acc ->
            val matchType = if (selectedTab == 0) acc.type != "SUPPLIER" else acc.type == "SUPPLIER"
            val matchQuery = searchQuery.isEmpty() ||
                acc.name.contains(searchQuery, ignoreCase = true) ||
                acc.phone.contains(searchQuery, ignoreCase = true) ||
                acc.notes.contains(searchQuery, ignoreCase = true)
            val matchDue = if (sortMode == "DUE_ONLY") acc.netBalance > 0.0 else true
            matchType && matchQuery && matchDue
        }
        when (sortMode) {
            "NAME_ASC" -> base.sortedBy { it.name.lowercase() }
            else -> base.sortedByDescending { abs(it.netBalance) }
        }
    }

    val totalReceivable = remember(accounts) {
        accounts.filter { it.type != "SUPPLIER" && it.netBalance > 0 }.sumOf { it.netBalance }
    }
    val totalPayable = remember(accounts) {
        accounts.filter { it.type == "SUPPLIER" && it.netBalance > 0 }.sumOf { it.netBalance }
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
            // 1. Header Row: Khata & Ledger + Sort/Filter Button
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
                            text = "Track customer credit & supplier balances",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                "DUE_ONLY" to "Due Balances Only"
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
                                text = "Customer Credit Due",
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
                                text = "Supplier Balances",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Pill Segmented Switcher: Customers | Suppliers
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
                            0 to "Customers ($customerCount)",
                            1 to "Suppliers ($supplierCount)"
                        )
                        tabs.forEach { (tabIdx, title) ->
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
                                    .testTag(if (tabIdx == 0) "khata_customers_tab" else "khata_suppliers_tab")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Search Bar with Voice Icon
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search account by name or phone...",
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
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak customer or supplier name...")
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
                        onClick = { selectedAccountForDetail = acc },
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

        // Centered Floating Pill Button matching Khata mockup
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
                text = if (selectedTab == 0) "Add Customer / Contractor" else "Add Supplier",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AddAccountDialog(
            defaultType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER",
            onDismiss = { showAddAccountDialog = false },
            onSave = { acc ->
                viewModel.saveLedgerAccount(acc)
                showAddAccountDialog = false
            }
        )
    }

    // Quick Entry Dialog (Give / Got)
    if (quickEntryAccount != null) {
        val target = quickEntryAccount!!
        AddLedgerEntryDialog(
            account = target,
            initialType = quickEntryType,
            onDismiss = { quickEntryAccount = null },
            onSave = { type, amount, date, desc, ref ->
                viewModel.addLedgerEntry(target.id, type, amount, date, desc, ref)
                quickEntryAccount = null
            }
        )
    }

    // Delete Account Confirmation
    if (accountToDelete != null) {
        val target = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Khata Account?") },
            text = {
                Text("Are you sure you want to delete ${target.name} along with all transaction history? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
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
    onClick: () -> Unit,
    onGive: () -> Unit,
    onGot: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isSupplier = account.type == "SUPPLIER"
    val isDue = account.netBalance > 0
    val balanceColor = if (isSupplier) {
        if (isDue) ext.danger else ext.positive
    } else {
        if (isDue) ext.positive else ext.danger
    }

    val avatarPalette = listOf(ext.info, ext.positive, MaterialTheme.colorScheme.primary, ext.purple, ext.warning)
    val avatarColor = avatarPalette[(account.name.hashCode() and 0x7FFFFFFF) % avatarPalette.size]

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        val subtitleText = when {
                            account.notes.isNotBlank() -> account.notes
                            account.phone.isNotBlank() -> account.phone
                            else -> "Balance verified"
                        }
                        Text(
                            text = subtitleText,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        val statusLabel = if (isSupplier) {
                            if (account.netBalance > 0) "YOU PAY" else if (account.netBalance < 0) "ADVANCE" else "SETTLED"
                        } else {
                            if (account.netBalance > 0) "YOU GET" else if (account.netBalance < 0) "ADVANCE" else "SETTLED"
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 9.5.sp,
                            color = balanceColor,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "₹${inrFormat.format(abs(account.netBalance))}",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions: - GAVE (DEBIT) | + GOT (CREDIT)
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
                        containerColor = ext.danger.copy(alpha = 0.14f),
                        contentColor = ext.danger
                    ),
                    border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("- GAVE (DEBIT)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = onGot,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.positive,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+ GOT (CREDIT)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun LedgerAccountDetailScreen(
    account: LedgerAccount,
    viewModel: StockViewModel,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit = onBack
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val uiConfig by viewModel.uiConfig.collectAsState()
    val entriesFlow = remember(account.id) { viewModel.getEntriesForAccount(account.id) }
    val entries by entriesFlow.collectAsState(initial = emptyList())

    var showEntryDialog by remember { mutableStateOf(false) }
    var entryType by remember { mutableStateOf("GAVE") }
    var entryToEdit by remember { mutableStateOf<LedgerEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<LedgerEntry?>(null) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    val balanceColor = if (account.netBalance >= 0) ext.positive else ext.danger
    val storeTitle = uiConfig.theme.storeTitle.ifBlank { "S.A.HARDWARE" }

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

    if (entryToDelete != null) {
        val target = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Transaction Entry?") },
            text = {
                Text("Delete entry of ₹${target.amount} (${if (target.type == "GAVE") "Gave" else "Got"})? The account balance will be automatically recalculated.")
            },
            confirmButton = {
                Button(
                    onClick = {
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

    if (entryToEdit != null) {
        EditLedgerEntryDialog(
            entry = entryToEdit!!,
            account = account,
            onDismiss = { entryToEdit = null },
            onSave = { updated ->
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (account.phone.isNotEmpty()) "${account.phone} • ${account.type}" else account.type,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            val pdf = InvoicePrintManager.createLedgerStatementPdf(context, account, entries)
                            InvoicePrintManager.sharePdf(context, pdf, "Statement - ${account.name}")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Statement", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            val pdf = InvoicePrintManager.createLedgerStatementPdf(context, account, entries)
                            InvoicePrintManager.printPdf(context, pdf, "Statement_${account.name}")
                        }
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { showDeleteAccountConfirm = true }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = MaterialTheme.colorScheme.error)
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
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "₹%.2f", abs(account.netBalance)),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = balanceColor
                            )
                        }

                        if (account.phone.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val msg = "Hello ${account.name}, your current ledger balance with $storeTitle is Rs. ${String.format(Locale.US, "%.2f", abs(account.netBalance))}."
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?phone=${account.phone.replace("+", "").replace(" ", "")}&text=${Uri.encode(msg)}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
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

                    if (account.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Note: ${account.notes}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = "TRANSACTION HISTORY (${entries.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (entry.type == "GAVE") ext.danger.copy(alpha = 0.15f) else ext.positive.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (entry.type == "GAVE") "Gave (-)" else "Got (+)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (entry.type == "GAVE") ext.danger else ext.positive,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(entry.date, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (entry.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(entry.description, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    }
                                    if (entry.billRef.isNotEmpty()) {
                                        Text("Bill #${entry.billRef}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = String.format(Locale.US, "%s₹%.2f", if (entry.type == "GAVE") "-" else "+", entry.amount),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = if (entry.type == "GAVE") ext.danger else ext.positive
                                        )
                                        Text(
                                            text = String.format(Locale.US, "Bal: ₹%.2f", entry.balanceAfter),
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
                        colors = ButtonDefaults.buttonColors(containerColor = ext.danger, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("- YOU GAVE (₹)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            entryType = "GOT"
                            showEntryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ext.positive, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ YOU GOT (₹)", fontWeight = FontWeight.Bold)
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
            onSave = { type, amount, date, desc, ref ->
                viewModel.addLedgerEntry(account.id, type, amount, date, desc, ref)
                showEntryDialog = false
            }
        )
    }
}

@Composable
fun EditLedgerEntryDialog(
    entry: LedgerEntry,
    account: LedgerAccount,
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit
) {
    var type by remember { mutableStateOf(entry.type) }
    var amountText by remember { mutableStateOf(entry.amount.toString()) }
    var description by remember { mutableStateOf(entry.description) }
    var billRef by remember { mutableStateOf(entry.billRef) }
    var date by remember { mutableStateOf(entry.date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Transaction Entry", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account: ${account.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "GAVE",
                        onClick = { type = "GAVE" },
                        label = { Text("You Gave (-)") }
                    )
                    FilterChip(
                        selected = type == "GOT",
                        onClick = { type = "GOT" },
                        label = { Text("You Got (+)") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billRef,
                        onValueChange = { billRef = it },
                        label = { Text("Bill # (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) return@Button
                    onSave(
                        entry.copy(
                            type = type,
                            amount = amt,
                            date = date,
                            description = description.trim(),
                            billRef = billRef.trim()
                        )
                    )
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
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

@Composable
fun AddAccountDialog(
    defaultType: String,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(defaultType) }
    var notes by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == "SUPPLIER") "Add Supplier" else "Add Customer / Contractor") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / City") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "CUSTOMER",
                        onClick = { type = "CUSTOMER" },
                        label = { Text("Customer / Contractor") }
                    )
                    FilterChip(
                        selected = type == "SUPPLIER",
                        onClick = { type = "SUPPLIER" },
                        label = { Text("Supplier") }
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Description / Notes (e.g. GSTIN, site, remarks)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text("Opening Balance (₹) [Optional]") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val initBal = initialBalanceText.toDoubleOrNull() ?: 0.0
                    val acc = LedgerAccount(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        type = type,
                        notes = notes.trim(),
                        netBalance = initBal,
                        createdAt = todayStr,
                        updatedAt = todayStr
                    )
                    onSave(acc)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddLedgerEntryDialog(
    account: LedgerAccount,
    initialType: String,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, date: String, description: String, billRef: String) -> Unit
) {
    val ext = LocalExtendedColors.current
    var type by remember { mutableStateOf(initialType) } // "GAVE" or "GOT"
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var billRef by remember { mutableStateOf("") }
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var date by remember { mutableStateOf(todayDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (type == "GAVE") "Record You Gave (Debit)" else "Record You Got (Credit)",
                color = if (type == "GAVE") ext.danger else ext.positive,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account: ${account.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "GAVE",
                        onClick = { type = "GAVE" },
                        label = { Text("You Gave (-)") }
                    )
                    FilterChip(
                        selected = type == "GOT",
                        onClick = { type = "GOT" },
                        label = { Text("You Got (+)") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billRef,
                        onValueChange = { billRef = it },
                        label = { Text("Bill # (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) return@Button
                    onSave(type, amt, date, description.trim(), billRef.trim())
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "GAVE") ext.danger else ext.positive
                )
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
