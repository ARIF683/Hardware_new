package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AuditLogEntity
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class AuditFilter(val label: String) {
    ALL("All"),
    ADDED("Added"),
    UPDATED("Updated"),
    STOCK("Stock"),
    DELETED("Deleted")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
    viewModel: StockViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val ext = LocalExtendedColors.current
    val isAdmin by viewModel.authManager.isAdmin.collectAsState()
    val auditLogs by viewModel.allAuditLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AuditFilter.ALL) }

    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            viewModel.syncAuditLogs()
        }
    }

    val filteredLogs = remember(auditLogs, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        auditLogs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                AuditFilter.ALL -> true
                AuditFilter.ADDED -> log.action.contains("ADD", ignoreCase = true) ||
                    log.action.contains("IMPORT", ignoreCase = true) ||
                    log.action.contains("BILL", ignoreCase = true)
                AuditFilter.UPDATED -> log.action.contains("UPDATE", ignoreCase = true) ||
                    log.action.contains("BULK", ignoreCase = true)
                AuditFilter.STOCK -> log.action.contains("STOCK", ignoreCase = true) ||
                    log.action.contains("TINT", ignoreCase = true) ||
                    log.action.contains("SALE", ignoreCase = true)
                AuditFilter.DELETED -> log.action.contains("DELETE", ignoreCase = true) ||
                    log.action.contains("CLEAR", ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                log.action.lowercase(Locale.ROOT).contains(q) ||
                log.details.lowercase(Locale.ROOT).contains(q) ||
                log.userEmail.lowercase(Locale.ROOT).contains(q)
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Inventory Audit Logs",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("audit_logs_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = { viewModel.syncAuditLogs() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audit_logs_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh audit logs"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.danger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .testTag("audit_logs_admin_required_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Admin access required",
                            tint = ext.danger,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Admin Access Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ext.danger
                        )
                        Text(
                            text = "Only authorized administrators can view inventory audit logs and user activity records.",
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audit_logs_search_input"),
                    placeholder = { Text("Search user email, item, or action…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search audit logs"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuditFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("audit_filter_${filter.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (filteredLogs.size == auditLogs.size) {
                            "Activity & Accountability Trail (${auditLogs.size} events)"
                        } else {
                            "Showing ${filteredLogs.size} of ${auditLogs.size} events"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Admin Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (auditLogs.isEmpty()) {
                                    "No audit logs recorded yet."
                                } else {
                                    "No matching audit events found."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("audit_logs_lazy_column"),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            AuditLogItemCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogItemCard(log: AuditLogEntity) {
    val ext = LocalExtendedColors.current
    val badgeColor: Color = when {
        log.action.contains("DELETE", ignoreCase = true) ||
            log.action.contains("CLEAR", ignoreCase = true) ||
            log.action == "STOCK_OUT" ||
            log.action == "COLOR_TINT_DEDUCT" -> ext.danger
        log.action.contains("ADD", ignoreCase = true) ||
            log.action == "STOCK_IN" ||
            log.action.contains("IMPORT", ignoreCase = true) ||
            log.action == "CONFIRM_BILL" -> ext.positive
        else -> MaterialTheme.colorScheme.primary
    }

    val formattedTime = remember(log.createdAt) {
        formatAuditTimestamp(log.createdAt)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_log_card_${log.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = log.action.replace('_', ' '),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.details,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = log.userEmail,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatAuditTimestamp(raw: String): String {
    if (raw.isBlank()) return ""
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(raw)
            if (date != null) {
                val out = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                return out.format(date)
            }
        } catch (e: Exception) {
            android.util.Log.d("AuditLogsScreen", "Timestamp pattern '$pattern' did not match: ${e.message}")
        }
    }
    return raw.take(19).replace('T', ' ')
}
