package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Item
import com.example.ui.StockViewModel
import com.example.ui.components.InputPromptDialog
import com.example.ui.components.SimpleConfirmDialog
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import java.io.File
import com.example.BuildConfig
import com.example.data.remote.UpdateStatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.pref.AppLogoStyle

@Composable
fun SettingsScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val pendingCount by viewModel.pendingQueueCount.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val uiConfig by viewModel.uiConfig.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val isUiConfigRefreshing by viewModel.isUiConfigRefreshing.collectAsState()
    val lastUiSyncedTime by viewModel.lastUiSyncedTime.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val itemsViewLayout by viewModel.itemsViewLayout.collectAsState()

    var selectedSettingsTab by remember { mutableStateOf(0) }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showColorMachineDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var inputCustomKey by remember { mutableStateOf("") }

    var replaceExisting by remember { mutableStateOf(false) }

    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = getFileName(context, uri)
                    if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                        val parsed = com.example.util.XlsxParser.parseItems(inputStream)
                        if (parsed.isEmpty()) {
                            viewModel.showToast("Could not parse XLSX. Verify sheet layout.")
                        } else {
                            val items = parsed.mapIndexed { index, i ->
                                com.example.data.model.Item(
                                    id = System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36) + index,
                                    o = index,
                                    code = i.code,
                                    barcode = i.barcode,
                                    name = i.name,
                                    cost = i.cost,
                                    price = i.price,
                                    type = i.type,
                                    brand = i.brand,
                                    size = i.size,
                                    unit = i.unit,
                                    mrp = i.mrp,
                                    qty = i.qty,
                                    aliases = i.aliases,
                                    low = 0.0,
                                    updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
                                )
                            }
                            viewModel.importItems(items, replaceExisting)
                            showImportDialog = false
                            viewModel.showToast("Successfully imported ${items.size} items from Excel!")
                        }
                    } else {
                        val csvText = inputStream.bufferedReader().use { it.readText() }
                        val parsed = parseCsvItems(csvText)
                        if (parsed.isEmpty()) {
                            viewModel.showToast("Could not parse CSV. Verify formatting.")
                        } else {
                            viewModel.importItems(parsed, replaceExisting)
                            showImportDialog = false
                            viewModel.showToast("Successfully imported ${parsed.size} items from CSV file!")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showToast("Error reading file: ${e.message}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = selectedSettingsTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = BrandBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedSettingsTab == 0,
                    onClick = { selectedSettingsTab = 0 },
                    text = { Text("General & Data", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedSettingsTab == 1,
                    onClick = { selectedSettingsTab = 1 },
                    text = { Text("Banking & Signature", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (selectedSettingsTab == 0) {
            // 1. Cloud Sync & Realtime Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Cloud Sync & Realtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Supabase database synchronization", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isRealtimeLive) {
                                BadgePill("Live ✓", SuccessGreen, Color.White)
                            } else {
                                BadgePill("Offline", WarningAmber, Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Database Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(syncStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (pendingCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BadgePill("$pendingCount pending", WarningAmber, Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Database Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.reloadFromDatabase() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync from DB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.uploadAllToDatabase() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload All to DB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.reconnectRealtime() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reconnect Live", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.retrySync() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry Queue", fontSize = 12.sp)
                            }

                            if (pendingCount > 0 || isAdmin) {
                                OutlinedButton(
                                    onClick = { viewModel.clearSyncQueue() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear Queue", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 1B. Gemini AI API Key Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                val currentSavedKey = remember(showApiKeyDialog) {
                    com.example.util.GeminiBillScanner.getSavedCustomApiKey(context)
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Gemini AI API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Powers bill photo scanning & inventory updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (currentSavedKey.isNotBlank()) {
                                BadgePill("Configured ✓", SuccessGreen, Color.White)
                            } else {
                                BadgePill("Not Set", WarningAmber, Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                inputCustomKey = com.example.util.GeminiBillScanner.getSavedCustomApiKey(context)
                                showApiKeyDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (currentSavedKey.isNotBlank()) "Change Gemini API Key" else "Enter Gemini API Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Appearance & Store Theme Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                val customThemeColor by viewModel.customThemeColor.collectAsState()
                val presetColors = listOf(
                    Pair("White", "#FFFFFF"),
                    Pair("Blue", "#3B82F6"),
                    Pair("Indigo", "#4F46E5"),
                    Pair("Emerald", "#10B981"),
                    Pair("Rose", "#F43F5E"),
                    Pair("Amber", "#F59E0B"),
                    Pair("Violet", "#8B5CF6"),
                    Pair("Teal", "#0EA5E9"),
                    Pair("Slate", "#475569")
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Theme & Layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Accent color and product view layout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("ACCENT COLOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Row of preset colors
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            presetColors.forEach { (name, hex) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isWhitePreset = hex.equals("#FFFFFF", ignoreCase = true)
                                val isSelected = customThemeColor.uppercase() == hex.uppercase()
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else if (isWhitePreset) Color.Gray.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setCustomThemeColor(hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (isWhitePreset) Color(0xFF0F172A) else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("ITEMS SCREEN LAYOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isList = itemsViewLayout == "list"
                            Card(
                                onClick = { viewModel.setItemsViewLayout("list") },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isList) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isList) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("List View", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Showcase rows", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            val isGrid = itemsViewLayout == "grid"
                            Card(
                                onClick = { viewModel.setItemsViewLayout("grid") },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isGrid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isGrid) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("2-Column Grid", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Classic cards", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Data Management & Backup Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Data Management & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Import, export, and color tint logs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showExportDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showImportDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showColorMachineDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Color Machine Tint Import (.CSC / .CSV)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.uploadAllToDatabase() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload All to Database (Cloud Backup)")
                        }
                    }
                }
            }

            // 4. Over-The-Air UI & App Updates Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Updates & GitHub Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic UI Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic UI Configuration", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (lastUiSyncedTime > 0) "Synced with GitHub ✓" else "Store: \"${uiConfig.theme.storeTitle}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (lastUiSyncedTime > 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isUiConfigRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.refreshUiConfig() },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // App APK Updates Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App APK Release", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Check GitHub for newer versions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Check", fontSize = 11.sp)
                            }
                        }

                        when (val s = updateStatus) {
                            is UpdateStatus.Available -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.startDownloadAndInstall(s.info.apkDownloadUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install ${s.info.tagName} (1-Tap)", fontWeight = FontWeight.Bold)
                                }
                            }
                            is UpdateStatus.Downloading -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                                Text("Downloading: ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            is UpdateStatus.UpToDate -> {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("App is up to date", color = SuccessGreen, fontSize = 12.sp)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // 5. Security & Admin Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAdmin) BrandBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (isAdmin) BrandBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAdmin) SuccessGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAdmin) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isAdmin) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Security & Administration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(if (isAdmin) "Admin access active" else "Protected with admin PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isAdmin) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.authManager.exitAdmin()
                                        viewModel.showToast("Admin mode off")
                                    },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Lock", fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = { showAdminPinDialog = true },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Unlock", fontSize = 11.sp)
                                }
                            }
                        }

                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showClearHistoryDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear History", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showDeleteAllDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete All Data", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 6. Account & Session Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DangerRed.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Account Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Active store terminal session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Button(
                                onClick = { showLogoutDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Log Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hardware store data is saved in local database and synced to Supabase cloud. Pending changes retry automatically when online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        } else {
            item {
                BankingAndSignatureContent(viewModel = viewModel)
            }
        }
    }

    // Admin PIN Dialog
    if (showAdminPinDialog) {
        InputPromptDialog(
            title = "Admin access",
            message = "Enter the admin PIN to reveal restricted actions.",
            placeholder = "PIN",
            confirmText = "Unlock",
            isPassword = true,
            onConfirm = { pin ->
                val ok = viewModel.authManager.unlockAdmin(pin)
                if (ok) {
                    viewModel.showToast("Admin mode on")
                    showAdminPinDialog = false
                } else {
                    viewModel.showToast("Wrong PIN")
                }
            },
            onDismiss = { showAdminPinDialog = false }
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        SimpleConfirmDialog(
            title = "Clear history",
            message = "Clear all transaction history? This cannot be undone.",
            confirmText = "Clear",
            isDanger = true,
            onConfirm = {
                viewModel.clearTransactionHistory()
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    // Delete All Items Dialog
    if (showDeleteAllDialog) {
        InputPromptDialog(
            title = "Delete everything",
            message = "This permanently deletes ALL ${allItems.size} items, sales/expenses, khata ledger, and quotations from the app and cloud database. Type DELETE to confirm.",
            placeholder = "Type DELETE",
            confirmText = "Delete everything",
            isDanger = true,
            onConfirm = { text ->
                if (text.trim() == "DELETE") {
                    viewModel.deleteAllData()
                    showDeleteAllDialog = false
                } else {
                    viewModel.showToast("Not deleted. You must type DELETE.")
                }
            },
            onDismiss = { showDeleteAllDialog = false }
        )
    }

    // Gemini API Key Dialog
    if (showApiKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = BrandBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column {
                    Text(
                        "Paste or update your Gemini API key from Google AI Studio. It is saved directly on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputCustomKey,
                        onValueChange = { inputCustomKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AQ... or AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inputCustomKey.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val formatLabel = when {
                            inputCustomKey.startsWith("AQ.") -> "Format: Google Auth Key (AQ)"
                            inputCustomKey.startsWith("AIza") -> "Format: Standard Key (AIza)"
                            else -> "Custom Key"
                        }
                        Text(
                            text = formatLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandBlue
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.util.GeminiBillScanner.setSavedCustomApiKey(context, inputCustomKey.trim())
                        showApiKeyDialog = false
                        viewModel.showToast("API key saved!")
                    }
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        SimpleConfirmDialog(
            title = "Log out",
            message = "You'll need to enter the password again to use the app.",
            confirmText = "Log out",
            isDanger = true,
            onConfirm = {
                viewModel.authManager.logout()
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val csvText = remember(allItems) {
            val headers = listOf("SKU", "Barcode", "Item Name", "Cost", "Price", "Type", "Brand", "Size", "Unit", "Mrp", "Quantity", "Aliases")
            val rows = allItems.map { i ->
                listOf(
                    i.code, i.barcode, i.name, i.cost.toString(), i.price.toString(),
                    i.type, i.brand, i.size, i.unit, i.mrp?.toString() ?: "", i.qty.toString(), i.aliases
                ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            }
            listOf(headers.joinToString(",")) + rows
        }.joinToString("\n")

        Dialog(onDismissRequest = { showExportDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Export items (CSV)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${allItems.size} items ready for export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Stock CSV", csvText))
                            viewModel.showToast("Copied to clipboard")
                        }) {
                            Text("Copy CSV")
                        }

                        OutlinedButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvText)
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share CSV"))
                        }) {
                            Text("Share CSV")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = csvText.take(1000) + if (csvText.length > 1000) "\n… (truncated preview)" else "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showExportDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    // Import Dialog
    if (showImportDialog) {
        var rawCsv by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Import items (XLSX / CSV)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { selectFileLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Excel (.xlsx) or CSV File")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Or paste raw CSV text below:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Columns: SKU, Barcode, Item Name, Cost, Price, Type, Brand, Size, Unit, MRP, Quantity, Aliases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = rawCsv,
                        onValueChange = { rawCsv = it },
                        placeholder = { Text("Paste CSV data here…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceExisting = !replaceExisting },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = replaceExisting,
                            onCheckedChange = { replaceExisting = it }
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "Replace all existing items",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showImportDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Button(onClick = {
                            val parsed = parseCsvItems(rawCsv)
                            if (parsed.isEmpty()) {
                                viewModel.showToast("Could not parse items. Check CSV format.")
                            } else {
                                viewModel.importItems(parsed, replaceExisting)
                                showImportDialog = false
                            }
                        }) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }

    if (showColorMachineDialog) {
        ColorMachineTintDialog(
            viewModel = viewModel,
            onDismiss = { showColorMachineDialog = false }
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        trailingContent()
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (titleColor != MaterialTheme.colorScheme.onSurface) FontWeight.Bold else FontWeight.Normal
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun BadgePill(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun parseCsvItems(csv: String): List<Item> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.size <= 1) return emptyList()

    val result = mutableListOf<Item>()
    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())

    for (lineIdx in 1 until lines.size) {
        val parts = lines[lineIdx].split(",").map { it.trim().removeSurrounding("\"") }
        if (parts.size >= 3) {
            val name = parts.getOrNull(2) ?: ""
            if (name.isBlank()) continue

            val id = System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36) + lineIdx
            val item = Item(
                id = id,
                o = lineIdx,
                code = parts.getOrNull(0) ?: "",
                barcode = parts.getOrNull(1) ?: "",
                name = name,
                cost = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
                price = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                type = parts.getOrNull(5) ?: "",
                brand = parts.getOrNull(6) ?: "",
                size = parts.getOrNull(7) ?: "",
                unit = parts.getOrNull(8)?.ifBlank { "pcs" } ?: "pcs",
                mrp = parts.getOrNull(9)?.toDoubleOrNull(),
                qty = parts.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
                aliases = parts.getOrNull(11) ?: "",
                low = 0.0,
                updatedAt = now
            )
            result.add(item)
        }
    }
    return result
}

private fun getFileName(context: Context, uri: android.net.Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file.xlsx"
}

@Composable
fun BankingAndSignatureContent(viewModel: StockViewModel) {
    val context = LocalContext.current
    val bankingInfo by viewModel.bankingInfo.collectAsState()

    var bankName by remember(bankingInfo.bankName) { mutableStateOf(bankingInfo.bankName) }
    var accountHolder by remember(bankingInfo.accountHolder) { mutableStateOf(bankingInfo.accountHolder) }
    var accountNumber by remember(bankingInfo.accountNumber) { mutableStateOf(bankingInfo.accountNumber) }
    var ifscCode by remember(bankingInfo.ifscCode) { mutableStateOf(bankingInfo.ifscCode) }
    var branchName by remember(bankingInfo.branchName) { mutableStateOf(bankingInfo.branchName) }
    var upiId by remember(bankingInfo.upiId) { mutableStateOf(bankingInfo.upiId) }

    val signatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveSignatureFromUri(uri)
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveQrCodeFromUri(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Header Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BrandBlue.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandBlue,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("GST Banking & Signature Setup", fontWeight = FontWeight.Bold, color = BrandBlue, fontSize = 15.sp)
                    Text("Details, QR code, and signature added here will automatically appear on all GST Tax Invoices and PDF exports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 1. Bank Account Details Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bank Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Used for receiving RTGS / NEFT / IMPS transfers from invoice buyers.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    placeholder = { Text("e.g. State Bank of India, HDFC Bank, ICICI Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it },
                    label = { Text("Account Holder / Business Legal Name") },
                    placeholder = { Text("e.g. HARDWARE & TOOLS STORE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        placeholder = { Text("502000xxxxxxx") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it.uppercase() },
                        label = { Text("IFSC Code") },
                        placeholder = { Text("SBIN0001234") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Branch / City") },
                        placeholder = { Text("Main Market Branch") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (VPA)") },
                        placeholder = { Text("store@okhdfcbank") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.saveBankDetails(bankName, accountHolder, accountNumber, ifscCode, branchName, upiId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Bank Details", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Upload Signature Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorized Signature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Upload an image of your signature. Appears in the bottom-right corner of GST invoices.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                val hasSig = bankingInfo.signaturePath != null && File(bankingInfo.signaturePath!!).exists()
                if (hasSig) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            AsyncImage(
                                model = File(bankingInfo.signaturePath!!),
                                contentDescription = "Authorized Signature Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgePill("✓ Signature Active", SuccessGreen, Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    signatureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearSignature() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No signature uploaded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            signatureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Signature Image (Gallery)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Upload Payment QR Code Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UPI Payment QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Upload your PhonePe, Google Pay, Paytm, or BHIM QR code. Customers can scan to pay.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                val hasQr = bankingInfo.qrCodePath != null && File(bankingInfo.qrCodePath!!).exists()
                if (hasQr) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.size(160.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                AsyncImage(
                                    model = File(bankingInfo.qrCodePath!!),
                                    contentDescription = "Payment QR Code Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BadgePill("✓ QR Code Active", SuccessGreen, Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        qrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Change", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearQrCode() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Remove", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No UPI QR code uploaded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            qrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload QR Code Image (Gallery)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Live Invoice Footer Preview Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("👁️ Live GST Invoice Footer Preview", fontWeight = FontWeight.Bold, color = BrandBlue, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Bank details & QR
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("BANK DETAILS:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = BrandBlue)
                            Text("Bank: ${bankName.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color(0xFF334155))
                            Text("A/C: ${accountNumber.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color(0xFF334155))
                            Text("IFSC: ${ifscCode.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color(0xFF334155))
                            if (upiId.isNotBlank()) {
                                Text("UPI: $upiId", fontSize = 9.sp, color = Color(0xFF334155))
                            }
                        }

                        // Middle: QR Code if present
                        if (bankingInfo.qrCodePath != null && File(bankingInfo.qrCodePath!!).exists()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    AsyncImage(
                                        model = File(bankingInfo.qrCodePath!!),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(2.dp)
                                    )
                                }
                                Text("Scan to Pay", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                            }
                        }

                        // Right: Signature
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text("For Store", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            if (bankingInfo.signaturePath != null && File(bankingInfo.signaturePath!!).exists()) {
                                AsyncImage(
                                    model = File(bankingInfo.signaturePath!!),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.height(28.dp).width(70.dp)
                                )
                            } else {
                                Text("[No signature]", fontSize = 8.sp, color = Color.Gray)
                            }
                            HorizontalDivider(modifier = Modifier.width(75.dp).padding(vertical = 2.dp), color = Color(0xFF94A3B8))
                            Text("Authorized Signatory", fontSize = 8.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

