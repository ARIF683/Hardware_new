package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.TransactionRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class StockPeriodFilter(val label: String, val badgeLabel: String) {
    ALL_TIME("All Time", "All Time"),
    THIS_MONTH("This Month", "This Month"),
    LAST_30_DAYS("Last 30 Days", "Last 30 Days")
}

private enum class StockDirectionFilter(val label: String) {
    ALL("All Movement"),
    INWARD("Inward (Stock Added)"),
    OUTWARD("Outward (Sales/Deducted)")
}

@Composable
fun StockMovementContent(
    viewModel: StockViewModel,
    stockTransactions: List<TransactionRecord>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var periodFilter by remember { mutableStateOf(StockPeriodFilter.ALL_TIME) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var directionFilter by remember { mutableStateOf(StockDirectionFilter.ALL) }

    val todayKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val yesterdayKey = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val currentMonthKey = remember(todayKey) { todayKey.take(7) }
    val currentMonthName = remember {
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date())
    }
    val thirtyDaysAgoKey = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val periodScoped = remember(stockTransactions, periodFilter, currentMonthKey, thirtyDaysAgoKey) {
        when (periodFilter) {
            StockPeriodFilter.ALL_TIME -> stockTransactions
            StockPeriodFilter.THIS_MONTH -> stockTransactions.filter { it.createdAt.startsWith(currentMonthKey) }
            StockPeriodFilter.LAST_30_DAYS -> stockTransactions.filter { it.createdAt.take(10) >= thirtyDaysAgoKey }
        }
    }

    val totalInward = remember(periodScoped) {
        periodScoped.filter { it.action.equals("in", ignoreCase = true) }.sumOf { it.qty }
    }
    val totalOutward = remember(periodScoped) {
        periodScoped.filter { it.action.equals("out", ignoreCase = true) }.sumOf { it.qty }
    }
    val netDelta = totalInward - totalOutward

    val filteredLogs = remember(periodScoped, directionFilter, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        periodScoped.filter { tx ->
            val matchesDirection = when (directionFilter) {
                StockDirectionFilter.ALL -> true
                StockDirectionFilter.INWARD -> tx.action.equals("in", ignoreCase = true)
                StockDirectionFilter.OUTWARD -> tx.action.equals("out", ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                tx.itemName.lowercase(Locale.ROOT).contains(q) ||
                tx.note.lowercase(Locale.ROOT).contains(q) ||
                tx.unit.lowercase(Locale.ROOT).contains(q)
            matchesDirection && matchesQuery
        }
    }

    val groupedByDate = remember(filteredLogs) {
        filteredLogs.groupBy { it.createdAt.take(10).ifBlank { "Unknown" } }
            .toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 6.dp)
    ) {
        // Header Row: Stock Movement + Export + Period Picker
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Stock Movement",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Audit log & real-time warehouse flow",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Export CSV
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (filteredLogs.isEmpty()) {
                                    viewModel.showToast("No stock logs to export")
                                } else {
                                    val csvBuilder = StringBuilder()
                                    csvBuilder.append("DateTime,Item Name,Direction,Qty,Unit,Balance,Note\n")
                                    filteredLogs.forEach { tx ->
                                        val cleanName = tx.itemName.replace("\"", "\"\"")
                                        val cleanNote = tx.note.replace("\"", "\"\"")
                                        csvBuilder.append("\"${tx.createdAt}\",\"$cleanName\",\"${tx.action.uppercase()}\",${tx.qty},\"${tx.unit}\",${tx.balance},\"$cleanNote\"\n")
                                    }
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Stock Movement Report - ${periodFilter.label}")
                                        putExtra(Intent.EXTRA_TEXT, csvBuilder.toString())
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Export Stock Movement CSV"))
                                }
                            }
                            .testTag("stock_movement_export_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export Stock Logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Export",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Period Selector
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showPeriodMenu = true }
                                .testTag("stock_movement_period_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Select Period",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = periodFilter.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = { showPeriodMenu = false }
                        ) {
                            StockPeriodFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (option == StockPeriodFilter.THIS_MONTH) "$currentMonthName (This Month)" else option.label,
                                            fontWeight = if (periodFilter == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (periodFilter == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        periodFilter = option
                                        showPeriodMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Monthly / Period Throughput Summary Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Stock Throughput",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (periodFilter == StockPeriodFilter.THIS_MONTH) currentMonthName else periodFilter.badgeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThroughputMetricBox(
                            value = formatMovementQty(totalInward),
                            label = "Inward",
                            icon = Icons.Default.ArrowDownward,
                            accent = ext.positive,
                            isDark = ext.isDark,
                            modifier = Modifier.weight(1f)
                        )
                        ThroughputMetricBox(
                            value = formatMovementQty(totalOutward),
                            label = "Outward",
                            icon = Icons.Default.ArrowUpward,
                            accent = ext.danger,
                            isDark = ext.isDark,
                            modifier = Modifier.weight(1f)
                        )
                        ThroughputMetricBox(
                            value = "${if (netDelta >= 0) "+" else "-"}${formatMovementQty(kotlin.math.abs(netDelta))}",
                            label = "Net Delta",
                            icon = Icons.Default.SwapHoriz,
                            accent = ext.info,
                            isDark = ext.isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search by Bill #, item name, or note...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onVoiceSearchClick,
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
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
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
                    .testTag("stock_movement_search")
            )
        }

        // Movement Filter Pills
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(StockDirectionFilter.entries) { filter ->
                    val isSelected = directionFilter == filter
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { directionFilter = filter }
                            .testTag("stock_filter_${filter.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Date-Grouped Movement List
        if (groupedByDate.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (stockTransactions.isEmpty()) {
                                "No stock movement events recorded yet.\nAdjust stock or generate a bill to track flow."
                            } else {
                                "No stock movements match the selected filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            groupedByDate.forEach { (dateKey, events) ->
                item(key = "header_$dateKey") {
                    val headerLabel = when (dateKey) {
                        todayKey -> "TODAY"
                        yesterdayKey -> "YESTERDAY"
                        else -> formatReadableDateHeading(dateKey)
                    }
                    val dotColor = if (dateKey == todayKey) ext.positive else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = headerLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "${events.size} ${if (events.size == 1) "event" else "events"} recorded",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(events, key = { it.clientId }) { tx ->
                    StockMovementRowCard(
                        tx = tx,
                        onClick = {
                            if (!tx.itemId.isNullOrEmpty()) {
                                viewModel.openItemDetail(tx.itemId)
                            }
                        }
                    )
                }
            }
        }

        // Live Automated Stock Sync Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Automated Stock Sync",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = syncStatus.ifBlank { "Real-time cloud & scanner sync active" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val badgeColor = if (isRealtimeLive) ext.positive else ext.warning
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isRealtimeLive) "LIVE" else "SYNC",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ThroughputMetricBox(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outline else accent.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (isDark) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) MaterialTheme.colorScheme.onSurface else accent,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StockMovementRowCard(
    tx: TransactionRecord,
    onClick: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isInward = tx.action.equals("in", ignoreCase = true)
    val noteLower = tx.note.lowercase(Locale.ROOT)
    val badgeLabel = when {
        noteLower.contains("bill") || noteLower.contains("sale") -> "Sale"
        noteLower.contains("po") || noteLower.contains("restock") || noteLower.contains("purchase") -> "Restock"
        noteLower.contains("audit") || noteLower.contains("damage") || noteLower.contains("scrap") -> "Audit"
        isInward -> "Inward"
        else -> "Outward"
    }
    val rowAccent = if (isInward) ext.positive else ext.danger
    val iconAccent = when {
        badgeLabel == "Restock" -> ext.info
        isInward -> ext.positive
        else -> ext.danger
    }
    val rowIcon = when {
        badgeLabel == "Restock" -> Icons.Outlined.Inventory2
        isInward -> Icons.Outlined.AddBox
        else -> Icons.Outlined.ShoppingCart
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !tx.itemId.isNullOrEmpty(), onClick = onClick)
            .testTag("stock_movement_row_${tx.clientId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconAccent.copy(alpha = if (ext.isDark) 0.16f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rowIcon,
                        contentDescription = null,
                        tint = iconAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tx.itemName.ifBlank { "Inventory Item" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val subtitle = buildString {
                        append("${formatMovementQty(tx.qty)} ${tx.unit}")
                        if (tx.note.isNotBlank()) {
                            append(" • ${tx.note}")
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                val timeLabel = formatTxTimeLabel(tx.createdAt)
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${if (isInward) "+" else "-"}${formatMovementQty(tx.qty)} ${tx.unit}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = rowAccent
                )
                Text(
                    text = "Bal: ${formatMovementQty(tx.balance)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val ext = LocalExtendedColors.current
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var type by remember { mutableStateOf(recordToEdit?.type ?: initialType) }

    val initialAmountFormatted = recordToEdit?.amount?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
    } ?: ""

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (recordToEdit == null) "Add ${if (type == "SALE") "Sale" else "Expense"}" else "Edit Transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { type = "SALE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "SALE") ext.positive else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_type_sale"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = if (type == "SALE") Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sale",
                            color = if (type == "SALE") Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { type = "EXPENSE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "EXPENSE") ext.danger else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_type_expense"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (type == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Expense",
                            color = if (type == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = activeAmountText,
                    onValueChange = { newVal ->
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

                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                selectedContainerColor = if (type == "SALE") ext.positive else ext.danger,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("YYYY-MM-DD", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1.1f).widthIn(min = 0.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = paymentMode,
                        onValueChange = { paymentMode = it },
                        label = { Text("Mode", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(0.9f).widthIn(min = 0.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.heightIn(min = 44.dp)
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

                Button(
                    onClick = {
                        if (parsedAmount <= 0.0) return@Button
                        val finalTitle = activeTitle.ifBlank { activeCategory }
                        onSave(type, parsedAmount, finalTitle, activeCategory, paymentMode, date, note)
                    },
                    enabled = parsedAmount > 0.0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == "SALE") ext.positive else ext.danger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_save_cashflow_button"),
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
    val ext = LocalExtendedColors.current
    val sales = records.filter { it.type.equals("SALE", ignoreCase = true) }
    val expenses = records.filter { it.type.equals("EXPENSE", ignoreCase = true) }
    val totalSales = sales.sumOf { it.amount }
    val totalExpenses = expenses.sumOf { it.amount }
    val net = totalSales - totalExpenses

    val salesByCategory = sales.groupBy { it.category }.mapValues { it.value.sumOf { item -> item.amount } }
    val expensesByCategory = expenses.groupBy { it.category }.mapValues { it.value.sumOf { item -> item.amount } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$monthName Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = if (net >= 0) ext.positive.copy(alpha = 0.12f) else ext.danger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (net >= 0) ext.positive else ext.danger).copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Monthly Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sales: ₹${String.format(Locale.US, "%,.2f", totalSales)}", color = ext.positive, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                    Text("Expenses: ₹${String.format(Locale.US, "%,.2f", totalExpenses)}", color = ext.danger, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Net: ${if (net >= 0) "+" else "-"}₹${String.format(Locale.US, "%,.2f", kotlin.math.abs(net))} (${if (net >= 0) "Profit" else "Loss"})",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (net >= 0) ext.positive else ext.danger
                                )
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = ext.danger, modifier = Modifier.size(14.dp))
                            Text("EXPENSES BY CATEGORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ext.danger)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (expensesByCategory.isEmpty()) {
                            Text("No expenses recorded.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                expensesByCategory.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                    val pct = if (totalExpenses > 0) (amt / totalExpenses) * 100.0 else 0.0
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                "₹${String.format(Locale.US, "%,.2f", amt)} (${String.format(Locale.US, "%.1f%%", pct)})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ext.danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = ext.positive, modifier = Modifier.size(14.dp))
                            Text("SALES BY CATEGORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ext.positive)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (salesByCategory.isEmpty()) {
                            Text("No sales recorded.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                salesByCategory.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                    val pct = if (totalSales > 0) (amt / totalSales) * 100.0 else 0.0
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                "₹${String.format(Locale.US, "%,.2f", amt)} (${String.format(Locale.US, "%.1f%%", pct)})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ext.positive
                                            )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatMovementQty(qty: Double): String {
    return if (qty % 1.0 == 0.0) {
        String.format(Locale.US, "%,d", qty.toLong())
    } else {
        String.format(Locale.US, "%,.1f", qty)
    }
}

private fun formatTxTimeLabel(createdAt: String): String {
    return try {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                val parsed = parser.parse(createdAt.take(24))
                if (parsed != null) {
                    return SimpleDateFormat("hh:mm a", Locale.US).format(parsed)
                }
            } catch (e: Exception) {
                android.util.Log.d("StockMovement", "Timestamp pattern '$pattern' did not match: ${e.message}")
            }
        }
        ""
    } catch (_: Exception) {
        ""
    }
}

private fun formatReadableDateHeading(dateKey: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = parser.parse(dateKey)
        if (parsed != null) {
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(parsed).uppercase(Locale.ENGLISH)
        } else {
            dateKey
        }
    } catch (_: Exception) {
        dateKey
    }
}

@Composable
fun SalesBarChartCard(
    monthlyRecords: List<DailyCashflowRecord>,
    allCashflow: List<DailyCashflowRecord>,
    selectedYear: Int,
    selectedMonthIndex: Int,
    monthDisplayName: String
) {
    val ext = LocalExtendedColors.current
    var chartMode by remember { mutableStateOf("DAILY") } // "DAILY" or "MONTHLY"
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    val daysInMonth = remember(selectedYear, selectedMonthIndex) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonthIndex)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val dailySalesList = remember(monthlyRecords, selectedYear, selectedMonthIndex, daysInMonth) {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonthIndex)

        (1..daysInMonth).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = df.format(cal.time)
            val daySum = monthlyRecords.filter { it.date == dateStr && it.type.equals("SALE", true) }.sumOf { it.amount }
            Pair("$day", daySum)
        }
    }

    val monthlySalesTrend = remember(allCashflow) {
        val dfKey = SimpleDateFormat("yyyy-MM", Locale.US)
        val dfLabel = SimpleDateFormat("MMM", Locale.ENGLISH)

        (5 downTo 0).map { monthsAgo ->
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -monthsAgo)
            val key = dfKey.format(c.time)
            val label = dfLabel.format(c.time)
            val sum = allCashflow.filter { it.date.startsWith(key) && it.type.equals("SALE", true) }.sumOf { it.amount }
            Triple(label, sum, key)
        }
    }

    val activeData = if (chartMode == "DAILY") dailySalesList.map { it.first to it.second } else monthlySalesTrend.map { it.first to it.second }
    val maxVal = activeData.maxOfOrNull { it.second }?.coerceAtLeast(100.0) ?: 100.0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_bar_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sales Performance Trend",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (chartMode == "DAILY") "Daily breakdown for $monthDisplayName" else "Trailing 6-month revenue performance",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        listOf("DAILY" to "Daily", "MONTHLY" to "Monthly").forEach { (mode, label) ->
                            val isSel = chartMode == mode
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        chartMode = mode
                                        selectedBarIndex = null
                                    }
                                    .testTag("sales_chart_mode_${mode.lowercase()}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val tappedItem = selectedBarIndex?.let { idx -> activeData.getOrNull(idx) }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
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
                    Text(
                        text = if (tappedItem != null) "Period: ${tappedItem.first}" else "Tap any bar to inspect sales amount",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = if (tappedItem != null) "₹${String.format(Locale.US, "%,.2f", tappedItem.second)}" else "Max: ₹${String.format(Locale.US, "%,.0f", maxVal)}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        color = ext.positive,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("sales_chart_max_label")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val barColor = ext.positive
            val selectedBarColor = MaterialTheme.colorScheme.primary
            val textColorInt = android.graphics.Color.GRAY

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(activeData) {
                        detectTapGestures { offset ->
                            val barWidthPx = size.width / activeData.size
                            val tappedIdx = (offset.x / barWidthPx).toInt().coerceIn(0, activeData.size - 1)
                            selectedBarIndex = tappedIdx
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height - 24.dp.toPx()
                    val barCount = activeData.size
                    if (barCount == 0) return@Canvas

                    val totalGap = 6.dp.toPx() * (barCount + 1)
                    val availableWidth = (canvasWidth - totalGap).coerceAtLeast(10f)
                    val barWidth = (availableWidth / barCount).coerceAtLeast(4f)

                    val paint = android.graphics.Paint().apply {
                        color = textColorInt
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    activeData.forEachIndexed { index, (label, amount) ->
                        val ratio = (amount / maxVal).coerceIn(0.0, 1.0).toFloat()
                        val barHeight = (canvasHeight * ratio).coerceAtLeast(4f)
                        val left = 6.dp.toPx() + index * (barWidth + 6.dp.toPx())
                        val top = canvasHeight - barHeight
                        val right = left + barWidth
                        val bottom = canvasHeight

                        val isSelected = (selectedBarIndex == index)
                        val colorToDraw = if (isSelected) selectedBarColor else barColor

                        drawRoundRect(
                            color = colorToDraw,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )

                        val showLabel = if (chartMode == "DAILY") {
                            (index + 1 == 1 || (index + 1) % 5 == 0 || index + 1 == barCount)
                        } else {
                            true
                        }

                        if (showLabel) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                left + barWidth / 2f,
                                size.height - 4f,
                                paint
                            )
                        }
                    }
                }
            }
        }
    }
}
