package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DailyCashflowRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(viewModel: StockViewModel) {
    val allCashflow by viewModel.allDailyCashflow.collectAsState()
    val stockTransactions by viewModel.allTransactions.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Daily P&L Cashflow, 1 = Stock Logs
    var filterType by remember { mutableStateOf("ALL") } // "ALL", "SALE", "EXPENSE"
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogInitialType by remember { mutableStateOf("SALE") }
    var recordToEdit by remember { mutableStateOf<DailyCashflowRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<DailyCashflowRecord?>(null) }
    var showCategoryBreakdownDialog by remember { mutableStateOf(false) }

    // Month Selector state
    val calendar = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonthIndex by remember { mutableStateOf(calendar.get(Calendar.MONTH)) } // 0-based

    val selectedMonthKey = remember(selectedYear, selectedMonthIndex) {
        String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonthIndex + 1)
    }

    val monthDisplayName = remember(selectedYear, selectedMonthIndex) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonthIndex)
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Speech Recognizer for Voice Search
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

    // Monthly Calculation
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

    // Today's summary
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

    // Filtered cashflow list
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

    // Group filtered cashflow by date
    val groupedCashflow = remember(filteredCashflow) {
        filteredCashflow.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    // Categories in current month for filter chips
    val monthCategories = remember(monthlyRecords) {
        listOf("All") + monthlyRecords.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    // Stock transaction search filter
    val filteredStock = remember(stockTransactions, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) stockTransactions
        else stockTransactions.filter {
            it.itemName.lowercase(Locale.ROOT).contains(q) || it.note.lowercase(Locale.ROOT).contains(q)
        }
    }

    // Carousel state for top balance cards and interactive dots
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
            .background(Color(0xFF000000))
    ) {
        // Tactical Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0D14))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "LEDGER & TRANSACTIONS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "LIVE P&L",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "Cashflow Logs, Daily Sales & Store Expenses",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            IconButton(
                onClick = { showCategoryBreakdownDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF121620), CircleShape)
                    .border(1.dp, Color(0xFF1E2638), CircleShape)
            ) {
                Icon(Icons.Default.PieChart, contentDescription = "Breakdown", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            }
        }

        // Sub Tabs: 0 = Daily P&L Cashflow, 1 = Stock Logs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF0A0D14),
            contentColor = Color(0xFF10B981),
            divider = { HorizontalDivider(color = Color(0xFF1E2638)) }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Text(
                        "💰 Sales & Expenses (P&L)",
                        fontWeight = if (activeTab == 0) FontWeight.Black else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF94A3B8)
                    )
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Text(
                        "📦 Stock Logs (${stockTransactions.size})",
                        fontWeight = if (activeTab == 1) FontWeight.Black else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (activeTab == 1) Color(0xFF10B981) else Color(0xFF94A3B8)
                    )
                }
            )
        }

        if (activeTab == 0) {
            // ==================== CASHFLOW & MONTHLY P&L TAB ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tactical Account Balance Cards Carousel matching mockup tactical_ledger
                item {
                    LazyRow(
                        state = carouselState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Card 1: Reserve Fund
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("RESERVE FUND", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF64748B)))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyNetProfit.coerceAtLeast(0.0))}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Account ID: 90031102", fontSize = 9.sp, color = Color(0xFF64748B))
                                    Text("Store Cash Reserves", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        // Card 2: Emerald Assets (Hero active glowing card from mockup)
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF072116)),
                                border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                                modifier = Modifier.width(240.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("EMERALD ASSETS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF10B981))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlySales)}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF10B981),
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column {
                                            Text("Account ID: 90031102", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text("Primary Revenue", fontSize = 9.sp, color = Color.White)
                                        }
                                        // Mini bar sparklines matching mockup
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            listOf(6.dp, 10.dp, 14.dp, 8.dp, 18.dp, 22.dp).forEach { h ->
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(h)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(Color(0xFF10B981))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Global Operations / Expenses
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("STORE EXPENSES", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyExpenses)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFEF4444)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Account ID: 90031102", fontSize = 9.sp, color = Color(0xFF64748B))
                                    Text("Total Operations Outflow", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }

                    // Pager dots indicator matching carousel scroll position
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..2).forEach { index ->
                            val isActive = (index == activeCarouselIndex)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (isActive) 8.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) Color(0xFF10B981) else Color(0xFF64748B))
                                    .clickable {
                                        coroutineScope.launch {
                                            carouselState.animateScrollToItem(index)
                                        }
                                    }
                            )
                        }
                    }
                }

                // Tactical Month Selector Bar
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0E131C),
                        border = BorderStroke(1.dp, Color(0xFF1E2638)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Text(
                                    text = monthDisplayName.uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 0.5.sp
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
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Tactical Hero Monthly Profit & Loss Card matching mockup
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                        border = BorderStroke(
                            1.2.dp,
                            if (isProfit) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${monthDisplayName.uppercase()} P&L OVERVIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF94A3B8),
                                    letterSpacing = 0.5.sp
                                )

                                Surface(
                                    color = if (isProfit) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    border = BorderStroke(0.8.dp, if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isProfit) "● NET PROFIT" else "● NET LOSS",
                                        color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Glowing Net Profit/Loss Display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = if (isProfit) "+₹${String.format(Locale.US, "%,.2f", monthlyNetProfit)}" else "-₹${String.format(Locale.US, "%,.2f", -monthlyNetProfit)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 26.sp,
                                        color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444),
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = if (isProfit) "Net Store Earnings this month" else "Expenses exceeded sales this month",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                if (monthlySales > 0) {
                                    Surface(
                                        color = Color(0xFF121620),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF1E2638))
                                    ) {
                                        Text(
                                            text = "MARGIN ${String.format(Locale.US, "%.1f%%", profitMarginPercent)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF1E2638))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Sales & Expenses Subtotals
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Total Sales
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TOTAL REVENUE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
                                    }
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlySales)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF10B981)
                                    )
                                    Text(
                                        text = "${monthlyRecords.count { it.type == "SALE" }} orders logged",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                // Total Expenses
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TOTAL EXPENSES", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
                                    }
                                    Text(
                                        text = "₹${String.format(Locale.US, "%,.2f", monthlyExpenses)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = "${monthlyRecords.count { it.type == "EXPENSE" }} expense entries",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Today's Live Indicator Pill
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF121620),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF1E2638)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡ TODAY:", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
                                    Text("+₹${todaySales.toInt()}", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                    Text("-₹${todayExpenses.toInt()}", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    Text(
                                        "NET: ${if (todayNet >= 0) "+" else ""}₹${todayNet.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (todayNet >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tactical Action Buttons: + Add Sale & + Add Expense
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
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF022C1A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ ADD SALE", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        }

                        Button(
                            onClick = {
                                addDialogInitialType = "EXPENSE"
                                recordToEdit = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1F1216),
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ EXPENSE", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }

                // Tactical Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search title, category, note…", fontSize = 13.sp, color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak search keyword...")
                                        }
                                        try {
                                            speechLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            viewModel.showToast("Voice search not available")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0E131C),
                            unfocusedContainerColor = Color(0xFF0E131C),
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF1E2638),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Tactical Filter Pills: All | Sales | Expenses
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("ALL", "All (${monthlyRecords.size})", Color(0xFF10B981)),
                            Triple("SALE", "🟢 Sales (${monthlyRecords.count { it.type == "SALE" }})", Color(0xFF10B981)),
                            Triple("EXPENSE", "🔴 Expenses (${monthlyRecords.count { it.type == "EXPENSE" }})", Color(0xFFEF4444))
                        ).forEach { (typeKey, label, accentColor) ->
                            val isSel = filterType == typeKey
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSel) accentColor else Color(0xFF121620),
                                border = BorderStroke(1.dp, if (isSel) accentColor else Color(0xFF1E2638)),
                                modifier = Modifier.clickable { filterType = typeKey }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                    color = if (isSel) Color(0xFF022C1A) else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Category Chips
                if (monthCategories.size > 2) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(monthCategories) { cat ->
                                val isSel = selectedCategoryFilter.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Grouped Daily Entries
                if (groupedCashflow.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (monthlyRecords.isEmpty()) "No sales or expenses recorded for $monthDisplayName." else "No transactions match your search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        addDialogInitialType = "SALE"
                                        showAddDialog = true
                                    }) {
                                        Text("+ Add Today's Sale")
                                    }
                                    OutlinedButton(onClick = {
                                        addDialogInitialType = "EXPENSE"
                                        showAddDialog = true
                                    }) {
                                        Text("+ Add Expense")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    groupedCashflow.forEach { (dateKey, entries) ->
                        val daySales = entries.filter { it.type == "SALE" }.sumOf { it.amount }
                        val dayExpenses = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                        val dayNet = daySales - dayExpenses

                        // Date Header
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
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

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (daySales > 0) Text("+$daySales", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        if (dayExpenses > 0) Text("-$dayExpenses", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Net: ${if (dayNet >= 0) "+" else ""}₹${String.format(Locale.US, "%.0f", dayNet)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (dayNet >= 0) SuccessGreen else DangerRed
                                        )
                                    }
                                }
                            }
                        }

                        // Entries for this date
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
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }
        } else {
            // ==================== STOCK LOGS TAB ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search stock logs…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (filteredStock.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (stockTransactions.isEmpty()) "No stock transactions yet.\nOpen an item and tap + New to adjust stock." else "No matching stock logs found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredStock, key = { it.clientId }) { tx ->
                        TransactionRowItem(
                            tx = tx,
                            onItemClick = {
                                if (!tx.itemId.isNullOrEmpty()) {
                                    viewModel.openItemDetail(tx.itemId)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Add / Edit Cashflow Dialog
    if (showAddDialog) {
        AddEditCashflowDialog(
            recordToEdit = recordToEdit,
            initialType = addDialogInitialType,
            onDismiss = {
                showAddDialog = false
                recordToEdit = null
            },
            onSave = { type, amount, title, category, paymentMode, date, note ->
                if (recordToEdit != null) {
                    val updated = recordToEdit!!.copy(
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

    // Delete Confirmation Dialog
    recordToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete ${rec.type.lowercase().replaceFirstChar { it.uppercase() }}?") },
            text = {
                Text("Are you sure you want to delete \"${rec.title}\" of ₹${rec.amount} on ${rec.date}? This will update monthly profit/loss calculations.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDailyCashflow(rec.id)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { recordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Breakdown Dialog
    if (showCategoryBreakdownDialog) {
        CategoryBreakdownDialog(
            monthName = monthDisplayName,
            records = monthlyRecords,
            onDismiss = { showCategoryBreakdownDialog = false }
        )
    }
}

@Composable
fun CashflowRowCard(
    record: DailyCashflowRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isSale = record.type.equals("SALE", ignoreCase = true)
    val accentColor = if (isSale) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactical Left Glowing Accent Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon & Category / Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.5f)),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isSale) "↗" else "↘",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = record.title.ifEmpty { if (isSale) "Cash Sale" else "Store Expense" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFFF3F4F6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF141923),
                                border = BorderStroke(0.6.dp, Color(0xFF1E2638)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = record.category.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            if (record.paymentMode.isNotBlank()) {
                                Text(
                                    text = "• ${record.paymentMode}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            if (record.note.isNotBlank()) {
                                Text(
                                    text = "• ${record.note}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Amount & Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isSale) "+₹${String.format(Locale.US, "%,.2f", record.amount)}" else "-₹${String.format(Locale.US, "%,.2f", record.amount)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.5.sp,
                        color = accentColor,
                        letterSpacing = 0.3.sp
                    )

                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                    }

                    var dropMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { dropMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
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
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF10B981)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Entry") },
                                onClick = {
                                    dropMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditCashflowDialog(
    recordToEdit: DailyCashflowRecord?,
    initialType: String,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, title: String, category: String, paymentMode: String, date: String, note: String) -> Unit
) {
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var type by remember { mutableStateOf(recordToEdit?.type ?: initialType) }

    // Format initial amount without trailing .0 if integer
    val initialAmountFormatted = recordToEdit?.amount?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
    } ?: ""

    // Separate states for Sale and Expense so amounts never leak across types
    var saleAmountText by remember {
        mutableStateOf(if (recordToEdit?.type == "SALE") initialAmountFormatted else "")
    }
    var expenseAmountText by remember {
        mutableStateOf(if (recordToEdit?.type == "EXPENSE") initialAmountFormatted else "")
    }

    var saleTitle by remember {
        mutableStateOf(if (recordToEdit?.type == "SALE") recordToEdit.title else "")
    }
    var expenseTitle by remember {
        mutableStateOf(if (recordToEdit?.type == "EXPENSE") recordToEdit.title else "")
    }

    var saleCategory by remember {
        mutableStateOf(if (recordToEdit?.type == "SALE") recordToEdit.category else "Counter Sale")
    }
    var expenseCategory by remember {
        mutableStateOf(if (recordToEdit?.type == "EXPENSE") recordToEdit.category else "Shop Rent")
    }

    var paymentMode by remember { mutableStateOf(recordToEdit?.paymentMode ?: "Cash") }
    var date by remember { mutableStateOf(recordToEdit?.date ?: todayDate) }
    var note by remember { mutableStateOf(recordToEdit?.note ?: "") }

    val saleCategories = listOf("Counter Sale", "Wholesale", "Retail", "Services", "Custom Work", "Other Sale")
    val expenseCategories = listOf("Shop Rent", "Electricity", "Staff Salary", "Transport/Freight", "Tea & Snacks", "Maintenance", "Packaging", "Tools", "Stationery", "Other Expense")
    val paymentModes = listOf("Cash", "UPI", "Card", "Bank Transfer", "Cheque")

    val activeAmountText = if (type == "SALE") saleAmountText else expenseAmountText
    val activeTitle = if (type == "SALE") saleTitle else expenseTitle
    val activeCategory = if (type == "SALE") saleCategory else expenseCategory
    val parsedAmount = activeAmountText.toDoubleOrNull() ?: 0.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (recordToEdit == null) "Add ${if (type == "SALE") "Sale" else "Expense"}" else "Edit Transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Type Toggle: Sale vs Expense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            type = "SALE"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "SALE") SuccessGreen else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "📈 Sale (Revenue)",
                            color = if (type == "SALE") Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            type = "EXPENSE"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "EXPENSE") DangerRed else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "📉 Expense (Cost)",
                            color = if (type == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                OutlinedTextField(
                    value = activeAmountText,
                    onValueChange = { newVal ->
                        // Accept digits and decimal
                        val cleaned = newVal.filter { it.isDigit() || it == '.' }
                        if (type == "SALE") {
                            saleAmountText = cleaned
                        } else {
                            expenseAmountText = cleaned
                        }
                    },
                    label = { Text("Amount (₹) *") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Title / Description
                OutlinedTextField(
                    value = activeTitle,
                    onValueChange = { newVal ->
                        if (type == "SALE") {
                            saleTitle = newVal
                        } else {
                            expenseTitle = newVal
                        }
                    },
                    label = { Text("Title / Purpose *") },
                    placeholder = { Text(if (type == "SALE") "e.g. Counter Sale / Customer Name" else "e.g. Electricity Bill / Shop Rent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Selector
                Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val availableCats = if (type == "SALE") saleCategories else expenseCategories
                    items(availableCats) { catOpt ->
                        val isSel = activeCategory.equals(catOpt, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (type == "SALE") {
                                    saleCategory = catOpt
                                    if (saleTitle.isBlank()) saleTitle = catOpt
                                } else {
                                    expenseCategory = catOpt
                                    if (expenseTitle.isBlank()) expenseTitle = catOpt
                                }
                            },
                            label = { Text(catOpt, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == "SALE") SuccessGreen else DangerRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Payment Mode & Date
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = paymentMode,
                        onValueChange = { paymentMode = it },
                        label = { Text("Mode") },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Payment Mode Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(paymentModes) { pm ->
                        FilterChip(
                            selected = paymentMode.equals(pm, ignoreCase = true),
                            onClick = { paymentMode = pm },
                            label = { Text(pm, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Save Action Button
                Button(
                    onClick = {
                        if (parsedAmount <= 0.0) return@Button
                        val finalTitle = activeTitle.ifBlank { activeCategory }
                        onSave(type, parsedAmount, finalTitle, activeCategory, paymentMode, date, note)
                    },
                    enabled = parsedAmount > 0.0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == "SALE") SuccessGreen else DangerRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (recordToEdit == null) "Save ${if (type == "SALE") "Sale" else "Expense"}" else "Update Transaction",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownDialog(
    monthName: String,
    records: List<DailyCashflowRecord>,
    onDismiss: () -> Unit
) {
    val sales = records.filter { it.type == "SALE" }
    val expenses = records.filter { it.type == "EXPENSE" }
    val totalSales = sales.sumOf { it.amount }
    val totalExpenses = expenses.sumOf { it.amount }
    val net = totalSales - totalExpenses

    val salesByCategory = sales.groupBy { it.category }.mapValues { it.value.sumOf { item -> item.amount } }
    val expensesByCategory = expenses.groupBy { it.category }.mapValues { it.value.sumOf { item -> item.amount } }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 $monthName Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Net Summary
                    item {
                        Surface(
                            color = if (net >= 0) SuccessGreen.copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Monthly Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sales: ₹${String.format(Locale.US, "%,.2f", totalSales)}", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                    Text("Expenses: ₹${String.format(Locale.US, "%,.2f", totalExpenses)}", color = DangerRed, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Net: ${if (net >= 0) "+" else ""}₹${String.format(Locale.US, "%,.2f", net)} (${if (net >= 0) "Profit" else "Loss"})",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (net >= 0) SuccessGreen else DangerRed
                                )
                            }
                        }
                    }

                    // Expenses Breakdown
                    item {
                        Text("📉 EXPENSES BY CATEGORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DangerRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (expensesByCategory.isEmpty()) {
                            Text("No expenses recorded.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                expensesByCategory.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                    val pct = if (totalExpenses > 0) (amt / totalExpenses) * 100.0 else 0.0
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text("₹${String.format(Locale.US, "%,.2f", amt)} (${String.format(Locale.US, "%.1f%%", pct)})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sales Breakdown
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("📈 SALES BY CATEGORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SuccessGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (salesByCategory.isEmpty()) {
                            Text("No sales recorded.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                salesByCategory.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                    val pct = if (totalSales > 0) (amt / totalSales) * 100.0 else 0.0
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text("₹${String.format(Locale.US, "%,.2f", amt)} (${String.format(Locale.US, "%.1f%%", pct)})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
