package com.example.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.DailyCashflowRecord
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.XlsExporter
import com.example.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    viewModel: StockViewModel,
    isEmbeddedInHub: Boolean = false
) {
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val allCashflow by viewModel.allDailyCashflow.collectAsState()
    val stockTransactions by viewModel.allTransactions.collectAsState()
    val activeTab by viewModel.transactionsSubTab.collectAsState()

    if (!isEmbeddedInHub) {
        BackHandler {
            viewModel.navigateTo(NavigationTab.HOME)
        }
    }

    var filterType by remember { mutableStateOf("ALL") } // "ALL", "SALE", "EXPENSE"
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogInitialType by remember { mutableStateOf("SALE") }
    var recordToEdit by remember { mutableStateOf<DailyCashflowRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<DailyCashflowRecord?>(null) }
    var showCategoryBreakdownDialog by remember { mutableStateOf(false) }
    var showExportXlsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val calendar = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonthIndex by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }

    val selectedMonthKey = remember(selectedYear, selectedMonthIndex) {
        String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonthIndex + 1)
    }

    val monthDisplayName = remember(selectedYear, selectedMonthIndex) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonthIndex)
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
            }
        }
    }

    val launchVoiceSearch: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak search keyword...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            viewModel.showToast("Voice search not available")
        }
    }

    val monthlyRecords = remember(allCashflow, selectedMonthKey) {
        allCashflow.filter { it.date.startsWith(selectedMonthKey) }
    }

    val monthlySales = remember(monthlyRecords) {
        monthlyRecords.filter { it.type.equals("SALE", ignoreCase = true) }.sumOf { it.amount }
    }

    val monthlyExpenses = remember(monthlyRecords) {
        monthlyRecords.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
    }

    val monthlyNetProfit = monthlySales - monthlyExpenses
    val isProfit = monthlyNetProfit >= 0
    val profitMarginPercent = if (monthlySales > 0) (monthlyNetProfit / monthlySales) * 100.0 else 0.0

    val todayRecords = remember(allCashflow, todayDateStr) {
        allCashflow.filter { it.date == todayDateStr }
    }
    val todaySales = remember(todayRecords) {
        todayRecords.filter { it.type.equals("SALE", ignoreCase = true) }.sumOf { it.amount }
    }
    val todayExpenses = remember(todayRecords) {
        todayRecords.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
    }
    val todayNet = todaySales - todayExpenses

    val filteredCashflow = remember(monthlyRecords, filterType, searchQuery, selectedCategoryFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        monthlyRecords.filter { rec ->
            val matchesType = when (filterType) {
                "SALE" -> rec.type.equals("SALE", ignoreCase = true)
                "EXPENSE" -> rec.type.equals("EXPENSE", ignoreCase = true)
                else -> true
            }
            val matchesCategory = selectedCategoryFilter.equals("All", ignoreCase = true) ||
                rec.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                rec.title.lowercase(Locale.ROOT).contains(q) ||
                rec.category.lowercase(Locale.ROOT).contains(q) ||
                rec.note.lowercase(Locale.ROOT).contains(q) ||
                rec.paymentMode.lowercase(Locale.ROOT).contains(q) ||
                rec.amount.toString().contains(q)

            matchesType && matchesCategory && matchesQuery
        }
    }

    val groupedCashflow = remember(filteredCashflow) {
        filteredCashflow.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    val monthCategories = remember(monthlyRecords) {
        listOf("All") + monthlyRecords.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val carouselState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val activeCarouselIndex by remember {
        derivedStateOf {
            carouselState.firstVisibleItemIndex.coerceIn(0, 2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header: Clean Ledger Title + Breakdown button (and optional back button when standalone)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!isEmbeddedInHub) {
                    IconButton(
                        onClick = { viewModel.navigateTo(NavigationTab.HOME) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("transactions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ledger & Transactions",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "LIVE P&L",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Cashflow logs, daily sales & warehouse movement",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { showExportXlsDialog = true }
                        .testTag("transactions_export_xls_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export XLS Sheet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { showCategoryBreakdownDialog = true }
                        .testTag("transactions_breakdown_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = "Category Breakdown",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Tactile Segmented Sub-Tab Switcher: Sales & Expenses (P&L) | Stock Movement
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SegmentedLedgerTab(
                    selected = activeTab == 0,
                    label = "Sales & Expenses",
                    badgeText = null,
                    icon = Icons.Default.Payments,
                    onClick = { viewModel.setTransactionsSubTab(0) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transactions_subtab_cashflow")
                )
                SegmentedLedgerTab(
                    selected = activeTab == 1,
                    label = "Stock Movement",
                    badgeText = "${stockTransactions.size}",
                    icon = Icons.Default.Inventory2,
                    onClick = { viewModel.setTransactionsSubTab(1) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transactions_subtab_stock")
                )
            }
        }

        if (activeTab == 0) {
            // ==================== TAB 0: DAILY P&L CASHFLOW ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp)
            ) {
                // Horizontal Balance Carousel + Pager Dots
                item {
                    LazyRow(
                        state = carouselState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Reserve Fund Card
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RESERVE FUND",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.6.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyNetProfit.coerceAtLeast(0.0))}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Account ID: 90031102", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Store Cash Reserves", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Primary Revenue Card
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = ext.positive.copy(alpha = if (ext.isDark) 0.14f else 0.08f)
                                ),
                                border = BorderStroke(1.5.dp, ext.positive.copy(alpha = 0.75f)),
                                modifier = Modifier.width(240.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "MONTHLY REVENUE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ext.positive,
                                            letterSpacing = 0.6.sp
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = ext.positive,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlySales)}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ext.positive
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column {
                                            Text("Account ID: 90031102", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Primary Revenue", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            listOf(6.dp, 10.dp, 14.dp, 9.dp, 18.dp, 22.dp).forEach { h ->
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(h)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(ext.positive)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Store Expenses Card
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.45f)),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "STORE EXPENSES",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ext.danger,
                                            letterSpacing = 0.6.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(ext.danger)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyExpenses)}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ext.danger
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Account ID: 90031102", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Operations Outflow", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..2).forEach { index ->
                            val isActive = (index == activeCarouselIndex)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(width = if (isActive) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            carouselState.animateScrollToItem(index)
                                        }
                                    }
                            )
                        }
                    }
                }

                // Month Selector Bar
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedMonthIndex == 0) {
                                        selectedMonthIndex = 11
                                        selectedYear -= 1
                                    } else {
                                        selectedMonthIndex -= 1
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("cashflow_prev_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = monthDisplayName.uppercase(Locale.ENGLISH),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.6.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (selectedMonthIndex == 11) {
                                        selectedMonthIndex = 0
                                        selectedYear += 1
                                    } else {
                                        selectedMonthIndex += 1
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("cashflow_next_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Monthly P&L Overview Card
                item {
                    val plAccent = if (isProfit) ext.positive else ext.danger
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.2.dp, plAccent.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${monthDisplayName.uppercase(Locale.ENGLISH)} P&L OVERVIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )

                                Surface(
                                    color = plAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(0.8.dp, plAccent),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(plAccent)
                                        )
                                        Text(
                                            text = if (isProfit) "NET PROFIT" else "NET LOSS",
                                            color = plAccent,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = if (isProfit) {
                                            "+₹${String.format(Locale.US, "%,.2f", monthlyNetProfit)}"
                                        } else {
                                            "-₹${String.format(Locale.US, "%,.2f", -monthlyNetProfit)}"
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 26.sp,
                                        color = plAccent
                                    )
                                    Text(
                                        text = if (isProfit) "Net Store Earnings this month" else "Expenses exceeded sales this month",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (monthlySales > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(
                                            text = "MARGIN ${String.format(Locale.US, "%.1f%%", profitMarginPercent)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = plAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = ext.positive,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "TOTAL REVENUE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlySales)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.5.sp,
                                        color = ext.positive
                                    )
                                    Text(
                                        text = "${monthlyRecords.count { it.type.equals("SALE", true) }} orders logged",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = ext.danger,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "TOTAL EXPENSES",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyExpenses)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.5.sp,
                                        color = ext.danger
                                    )
                                    Text(
                                        text = "${monthlyRecords.count { it.type.equals("EXPENSE", true) }} expense entries",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = ext.warning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "TODAY",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "+₹${todaySales.toInt()}",
                                        fontSize = 11.5.sp,
                                        color = ext.positive,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "-₹${todayExpenses.toInt()}",
                                        fontSize = 11.5.sp,
                                        color = ext.danger,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "NET: ${if (todayNet >= 0) "+" else "-"}₹${kotlin.math.abs(todayNet.toInt())}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (todayNet >= 0) ext.positive else ext.danger
                                    )
                                }
                            }
                        }
                    }
                }

                // Sales Performance Bar Chart Card (Daily / Monthly Trend)
                item {
                    SalesBarChartCard(
                        monthlyRecords = monthlyRecords,
                        allCashflow = allCashflow,
                        selectedYear = selectedYear,
                        selectedMonthIndex = selectedMonthIndex,
                        monthDisplayName = monthDisplayName
                    )
                }

                // Primary Quick Actions: + Add Sale & + Expense
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                addDialogInitialType = "SALE"
                                recordToEdit = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.positive,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cashflow_add_sale_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADD SALE", fontWeight = FontWeight.Black, fontSize = 12.5.sp, letterSpacing = 0.4.sp)
                        }

                        Button(
                            onClick = {
                                addDialogInitialType = "EXPENSE"
                                recordToEdit = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.danger.copy(alpha = 0.14f),
                                contentColor = ext.danger
                            ),
                            border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cashflow_add_expense_btn")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPENSE", fontWeight = FontWeight.Black, fontSize = 12.5.sp, letterSpacing = 0.4.sp)
                        }
                    }
                }

                // Search Bar with Voice Input
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search title, category, mode, note…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = launchVoiceSearch,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cashflow_search_field")
                    )
                }

                // Type Filter Pills: All | Sales | Expenses
                item {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val pills = listOf(
                        Triple("ALL", "All (${monthlyRecords.size})", primaryColor),
                        Triple("SALE", "Sales (${monthlyRecords.count { it.type.equals("SALE", true) }})", ext.positive),
                        Triple("EXPENSE", "Expenses (${monthlyRecords.count { it.type.equals("EXPENSE", true) }})", ext.danger)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pills) { (typeKey, label, accentColor) ->
                            val isSel = filterType == typeKey
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) accentColor else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) accentColor else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { filterType = typeKey }
                                    .testTag("cashflow_filter_${typeKey.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    if (typeKey != "ALL") {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isSel) Color.White else accentColor)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Chips
                if (monthCategories.size > 2) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(monthCategories) { cat ->
                                val isSel = selectedCategoryFilter.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                            }
                        }
                    }
                }

                // Grouped Daily Entries
                if (groupedCashflow.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (monthlyRecords.isEmpty()) {
                                        "No sales or expenses recorded for $monthDisplayName."
                                    } else {
                                        "No transactions match your search."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            addDialogInitialType = "SALE"
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.heightIn(min = 44.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("+ Add Sale")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            addDialogInitialType = "EXPENSE"
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.heightIn(min = 44.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("+ Add Expense")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    groupedCashflow.forEach { (dateKey, entries) ->
                        val daySales = entries.filter { it.type.equals("SALE", true) }.sumOf { it.amount }
                        val dayExpenses = entries.filter { it.type.equals("EXPENSE", true) }.sumOf { it.amount }
                        val dayNet = daySales - dayExpenses

                        item(key = "cashflow_date_$dateKey") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateDisplay = if (dateKey == todayDateStr) "Today ($dateKey)" else dateKey
                                    Text(
                                        text = dateDisplay,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (daySales > 0) {
                                            Text(
                                                text = "+₹${daySales.toInt()}",
                                                fontSize = 11.sp,
                                                color = ext.positive,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (dayExpenses > 0) {
                                            Text(
                                                text = "-₹${dayExpenses.toInt()}",
                                                fontSize = 11.sp,
                                                color = ext.danger,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Net: ${if (dayNet >= 0) "+" else "-"}₹${String.format(Locale.US, "%.0f", kotlin.math.abs(dayNet))}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (dayNet >= 0) ext.positive else ext.danger
                                        )
                                    }
                                }
                            }
                        }

                        items(entries, key = { it.id }) { rec ->
                            CashflowRowCard(
                                record = rec,
                                onEdit = {
                                    recordToEdit = rec
                                    showAddDialog = true
                                },
                                onDelete = {
                                    recordToDelete = rec
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        } else {
            // ==================== TAB 1: STOCK MOVEMENT LOGS ====================
            StockMovementContent(
                viewModel = viewModel,
                stockTransactions = stockTransactions,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onVoiceSearchClick = launchVoiceSearch
            )
        }
    }

    if (showAddDialog) {
        AddEditCashflowDialog(
            recordToEdit = recordToEdit,
            initialType = addDialogInitialType,
            onDismiss = {
                showAddDialog = false
                recordToEdit = null
            },
            onSave = { type, amount, title, category, paymentMode, date, note ->
                haptics.confirm()
                val editing = recordToEdit
                if (editing != null) {
                    val updated = editing.copy(
                        type = type,
                        amount = amount,
                        title = title,
                        category = category,
                        paymentMode = paymentMode,
                        date = date,
                        note = note
                    )
                    viewModel.updateDailyCashflow(updated)
                } else {
                    viewModel.addDailyCashflow(
                        type = type,
                        amount = amount,
                        title = title,
                        category = category,
                        paymentMode = paymentMode,
                        date = date,
                        note = note
                    )
                }
                showAddDialog = false
                recordToEdit = null
            }
        )
    }

    recordToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete ${rec.type.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }}?") },
            text = {
                Text("Are you sure you want to delete \"${rec.title}\" of ₹${rec.amount} on ${rec.date}? This will update monthly profit/loss calculations.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.reject()
                        viewModel.deleteDailyCashflow(rec.id)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.danger, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { recordToDelete = null },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategoryBreakdownDialog) {
        CategoryBreakdownDialog(
            monthName = monthDisplayName,
            records = monthlyRecords,
            onDismiss = { showCategoryBreakdownDialog = false }
        )
    }

    if (showExportXlsDialog) {
        var exportAllHistory by remember { mutableStateOf(false) }
        var isExporting by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showExportXlsDialog = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Ledger Sheet (.XLS)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val monthSelected = !exportAllHistory
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (monthSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (monthSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportAllHistory = false }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    monthDisplayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (monthSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val allSelected = exportAllHistory
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportAllHistory = true }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    "All History",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (allSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    val targetCashflow = if (exportAllHistory) allCashflow else monthlyRecords
                    val targetMovements = if (exportAllHistory) {
                        stockTransactions
                    } else {
                        stockTransactions.filter { it.createdAt.startsWith(selectedMonthKey) }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Includes 2 Excel tabs:\n• Cashflow & P&L (${targetCashflow.size} entries)\n• Stock Movements (${targetMovements.size} logs)",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    fun exportLedgerXls(share: Boolean) {
                        if (isExporting) return
                        isExporting = true
                        coroutineScope.launch {
                            val prefix = if (exportAllHistory) "Ledger_All" else "Ledger_$selectedMonthKey"
                            val xml = withContext(Dispatchers.Default) {
                                XlsExporter.buildTransactionsWorkbookXml(targetCashflow, targetMovements)
                            }
                            if (share) {
                                XlsExporter.shareXlsFile(
                                    context = context,
                                    filePrefix = prefix,
                                    xmlContent = xml,
                                    shareTitle = "Share Ledger Sheet (.XLS)"
                                ).onFailure { viewModel.showToast("Share failed: ${it.message}") }
                            } else {
                                val res = withContext(Dispatchers.IO) {
                                    XlsExporter.saveXlsToDownloads(context, prefix, xml)
                                }
                                res.onSuccess { viewModel.showToast("Saved XLS to $it") }
                                    .onFailure { viewModel.showToast("Save failed: ${it.message}") }
                            }
                            isExporting = false
                            showExportXlsDialog = false
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { exportLedgerXls(share = false) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("transactions_save_xls_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save .XLS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { exportLedgerXls(share = true) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("transactions_share_xls_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share .XLS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showExportXlsDialog = false }) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedLedgerTab(
    selected: Boolean,
    label: String,
    badgeText: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(5.dp))
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (selected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CashflowRowCard(
    record: DailyCashflowRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isSale = record.type.equals("SALE", ignoreCase = true)
    val accentColor = if (isSale) ext.positive else ext.danger

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cashflow_row_${record.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(68.dp)
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.45f)),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSale) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = if (isSale) "Sale" else "Expense",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.title.ifEmpty { if (isSale) "Cash Sale" else "Store Expense" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = record.category.uppercase(Locale.ENGLISH),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = accentColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            if (record.paymentMode.isNotBlank()) {
                                Text(
                                    text = record.paymentMode,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            if (record.note.isNotBlank()) {
                                Text(
                                    text = "• ${record.note}",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSale) {
                            "+₹${String.format(Locale.US, "%,.2f", record.amount)}"
                        } else {
                            "-₹${String.format(Locale.US, "%,.2f", record.amount)}"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = accentColor
                    )

                    var dropMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { dropMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("cashflow_more_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = dropMenu,
                            onDismissRequest = { dropMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Entry") },
                                onClick = {
                                    dropMenu = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Entry") },
                                onClick = {
                                    dropMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ext.danger)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
