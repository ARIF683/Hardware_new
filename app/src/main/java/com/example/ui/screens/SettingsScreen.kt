package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.notifications.DailyReminderScheduler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.Item
import com.example.data.remote.UpdateStatus
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.components.AdminUserManagementDialog
import com.example.ui.components.ChangeEmailDialog
import com.example.ui.components.DeleteAccountOtpDialog
import com.example.ui.components.InputPromptDialog
import com.example.ui.components.SimpleConfirmDialog
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateTimeHelper
import com.example.util.GeminiBillScanner
import com.example.util.ImageModelHelper
import com.example.util.XlsxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalExtendedColors.current

    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val pendingCount by viewModel.pendingQueueCount.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val festivalStudioConfig by viewModel.festivalStudioConfig.collectAsState()
    val activeFestivals by viewModel.activeFestivals.collectAsState()
    val activeAnnouncement by viewModel.activeAnnouncement.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val customLogoUrl by viewModel.customLogoUrl.collectAsState()
    val bankingInfo by viewModel.bankingInfo.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val itemsViewLayout by viewModel.itemsViewLayout.collectAsState()
    val navBarStyle by viewModel.navBarStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val storeIdentity by viewModel.storeIdentity.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val supervisorName = storeIdentity.supervisorName
    val supervisorRole = storeIdentity.roleTitle
    val depotLocation = storeIdentity.location
    val lowStockAlertsEnabled by viewModel.lowStockAlertsEnabled.collectAsState()
    val lowStockThreshold by viewModel.lowStockDefaultThreshold.collectAsState()
    val invoicePrefix by viewModel.invoicePrefix.collectAsState()
    val nextInvoiceNum by viewModel.nextInvoiceNum.collectAsState()
    val invoicePadDigits by viewModel.invoicePadDigits.collectAsState()
    val invoiceIncludeFy by viewModel.invoiceIncludeFy.collectAsState()
    val quotePrefix by viewModel.quotePrefix.collectAsState()
    val nextQuoteNum by viewModel.nextQuoteNum.collectAsState()
    val quotePadDigits by viewModel.quotePadDigits.collectAsState()
    val allGstBills by viewModel.allGstBills.collectAsState()
    val instantDispatchSlip by viewModel.instantDispatchSlip.collectAsState()
    var showLowStockThresholdDialog by remember { mutableStateOf(false) }

    // Navigation & Dialog States
    var showBankingScreen by remember { mutableStateOf(false) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var showThemeLayoutDialog by remember { mutableStateOf(false) }
    var showInvoiceSequenceDialog by remember { mutableStateOf(false) }
    var showDataBackupDialog by remember { mutableStateOf(false) }
    var showSyncUpdatesDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showFestivalStudioDialog by remember { mutableStateOf(false) }
    var showAdminUserManagementDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(isAdmin) {
        if (!isAdmin) {
            showFestivalStudioDialog = false
            showAdminUserManagementDialog = false
            showBankingScreen = false
            showProfileEditDialog = false
            showInvoiceSequenceDialog = false
        }
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showExportXlsDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showColorMachineDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var inputCustomKey by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }

    val lowStockCount = remember(allItems, lowStockThreshold) {
        allItems.count { viewModel.appSettingsManager.isLowOrOutOfStock(it) }
    }
    val terminalId = remember {
        val modelTag = android.os.Build.MODEL.replace("[^A-Za-z0-9]".toRegex(), "").take(6).uppercase().ifBlank { "01" }
        "HS-DEPOT-$modelTag-SHAH"
    }

    // Store Logo Photo Picker
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.showToast("Uploading store logo...")
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                val maxDim = 512
                                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1.0f)
                                val scaled = if (scale < 1.0f) {
                                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                                } else bitmap
                                val finalBitmap = if (scaled.hasAlpha()) {
                                    val whiteBg = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(whiteBg)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    canvas.drawBitmap(scaled, 0f, 0f, null)
                                    whiteBg
                                } else scaled
                                val bos = ByteArrayOutputStream()
                                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                                bos.toByteArray()
                            } else null
                        }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val result = viewModel.uploadItemImage(bytes)
                        if (result.isSuccess) {
                            viewModel.updateStoreLogo(result.getOrNull())
                        } else {
                            viewModel.showToast("Logo upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error: ${e.message}")
                }
            }
        }
    }

    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = getFileNameFromUri(context, uri)
                    if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                        val parsed = XlsxParser.parseItems(inputStream)
                        if (parsed.isEmpty()) {
                            viewModel.showToast("Could not parse XLSX. Verify sheet layout.")
                        } else {
                            val nowIso = DateTimeHelper.nowIso()
                            val items = parsed.mapIndexed { index, i ->
                                Item(
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
                                    updatedAt = nowIso
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
                viewModel.showToast("Error reading file: ${e.message}")
            }
        }
    }

    BackHandler(enabled = showBankingScreen) {
        showBankingScreen = false
    }

    if (showBankingScreen) {
        BankingAndSignatureSubScreen(
            viewModel = viewModel,
            onBack = { showBankingScreen = false }
        )
        return
    }

    val currentSavedKey = remember(showApiKeyDialog) {
        GeminiBillScanner.getSavedCustomApiKey(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Unified Top Store Header
        item {
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
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warehouse,
                            contentDescription = "Store Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = effectiveStoreTitle.ifBlank { "HARDWARESTOCK" }.uppercase(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Settings",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (lowStockCount > 0) {
                                    viewModel.inStockOnly.value = false
                                    viewModel.requestedStockStatusFilter.value = "low"
                                    viewModel.navigateTo(NavigationTab.ITEMS)
                                } else {
                                    viewModel.showToast("All inventory levels are healthy")
                                }
                            }
                            .testTag("settings_notifications_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Stock Alerts",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                        if (lowStockCount > 0 && lowStockAlertsEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(ext.danger),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (lowStockCount > 99) "99" else "$lowStockCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isAdmin) {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    viewModel.showToast("Admin access required to update store logo")
                                }
                            }
                            .testTag("settings_logo_avatar"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        ) {
                            if (!customLogoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageModelHelper.rememberImageModel(customLogoUrl),
                                    contentDescription = "Store Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = selectedLogo.resId),
                                    fallback = painterResource(id = selectedLogo.resId)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = selectedLogo.resId),
                                    contentDescription = selectedLogo.title,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Profile & Cloud Health Hero Card
        item {
            val heroGradient = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.14f else 0.06f),
                    MaterialTheme.colorScheme.surface
                )
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(heroGradient)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Square Rounded Avatar with Live Green Status Dot
                            Box(modifier = Modifier.size(62.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (isAdmin) {
                                                logoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } else {
                                                viewModel.showToast("Admin access required to update store logo")
                                            }
                                        }
                                ) {
                                    if (!customLogoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageModelHelper.rememberImageModel(customLogoUrl),
                                            contentDescription = supervisorName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = selectedLogo.resId),
                                            fallback = painterResource(id = selectedLogo.resId)
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = selectedLogo.resId),
                                            contentDescription = supervisorName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isRealtimeLive) ext.positive else ext.warning)
                                        .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = supervisorName,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAdmin) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.testTag("settings_admin_badge")
                                    ) {
                                        Text(
                                            text = if (isAdmin) "ADMIN" else "STAFF",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = supervisorRole,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = depotLocation,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Edit Profile Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isAdmin) {
                                        showProfileEditDialog = true
                                    } else {
                                        viewModel.showToast("Admin access required to edit store profile")
                                    }
                                }
                                .testTag("settings_edit_profile_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Terminal Profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Two Metric Inset Cards: Cloud Cache & Sync Health
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.reloadFromDatabase() }
                                .testTag("settings_cloud_cache_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cloud,
                                    contentDescription = null,
                                    tint = ext.info,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Cloud Cache",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${allItems.size} SKUs Ready",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showSyncUpdatesDialog = true }
                                .testTag("settings_sync_health_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isSyncHealthy = isRealtimeLive || syncStatus.startsWith("Synced")
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = if (isSyncHealthy && viewModel.isCloudConfigured) ext.positive else ext.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Sync Health",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val healthLabel = when {
                                        !viewModel.isCloudConfigured -> "No Secrets"
                                        pendingCount > 0 -> "$pendingCount Pending"
                                        isRealtimeLive -> "Live ✓"
                                        syncStatus.startsWith("Synced") -> "Synced ✓"
                                        syncStatus.startsWith("Loading") || syncStatus.startsWith("Syncing") -> "Syncing…"
                                        else -> "Offline"
                                    }
                                    Text(
                                        text = healthLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Section 1: Inventory Logistics
        item {
            SettingsSectionHeader(
                icon = Icons.Filled.Warehouse,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Inventory Logistics",
                rightLabel = "RULES & I/O"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupedCard {
                // Row 1: Low Stock Trigger
                SettingsItemRow(
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = ext.danger,
                    title = "Low Stock Trigger",
                    subtitle = "Warn badge on items ≤ $lowStockThreshold units ($lowStockCount active)",
                    onClick = {
                        viewModel.setLowStockAlertsEnabled(!lowStockAlertsEnabled)
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showLowStockThresholdDialog = true }
                                    .testTag("settings_low_stock_threshold_edit")
                            ) {
                                Text(
                                    text = "≤$lowStockThreshold",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Switch(
                                checked = lowStockAlertsEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.setLowStockAlertsEnabled(checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("settings_low_stock_switch")
                            )
                        }
                    }
                )
                SettingsRowDivider()

                // Row 2: Items Layout & Scanner
                SettingsItemRow(
                    icon = Icons.Filled.QrCodeScanner,
                    iconTint = ext.info,
                    title = "Items View & Scanner",
                    subtitle = if (itemsViewLayout == "list") "Showcase List • Auto-Focus Barcode" else "2-Column Grid • Auto-Focus Barcode",
                    onClick = {
                        val nextLayout = if (itemsViewLayout == "list") "grid" else "list"
                        viewModel.setItemsViewLayout(nextLayout)
                    },
                    testTag = "settings_items_layout_row",
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsInlinePill(
                                text = if (itemsViewLayout == "list") "List" else "Grid",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                SettingsRowDivider()

                // Row 3: Currency, Tax & Banking
                SettingsItemRow(
                    icon = Icons.Filled.CurrencyRupee,
                    iconTint = ext.positive,
                    title = "Currency, GST & Banking",
                    subtitle = if (bankingInfo.upiId.isNotBlank()) {
                        "INR (₹) • UPI: ${bankingInfo.upiId}"
                    } else {
                        "INR (₹) • Bank, Signature & Payment QR"
                    },
                    onClick = {
                        if (isAdmin) {
                            showBankingScreen = true
                        } else {
                            viewModel.showToast("Admin access required to manage banking details")
                        }
                    },
                    testTag = "settings_banking_row",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Banking & Signature",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                SettingsRowDivider()

                // Row 4: Navigation Bar & Display Style
                SettingsItemRow(
                    icon = Icons.Outlined.Straighten,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Navigation & Display",
                    subtitle = if (navBarStyle == "floating_pill") "Floating Pill Nav • Accent Customized" else "Classic Edge Bar • Accent Customized",
                    onClick = { showThemeLayoutDialog = true },
                    testTag = "settings_nav_config_button",
                    trailingContent = {
                        Text(
                            text = "Config",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }

        // 4. Section 2: POS & Dispatch Rules
        item {
            SettingsSectionHeader(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = ext.info,
                title = "POS & Dispatch Rules",
                rightLabel = "INVOICING"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupedCard {
                // Row 1: Invoice Sequence
                val formattedNextInv = remember(invoicePrefix, nextInvoiceNum, invoicePadDigits, invoiceIncludeFy) {
                    com.example.data.pref.AppSettingsManager.formatDocumentNo(
                        prefix = invoicePrefix,
                        num = nextInvoiceNum,
                        padDigits = invoicePadDigits,
                        includeFy = invoiceIncludeFy
                    )
                }
                val formattedNextQuote = remember(quotePrefix, nextQuoteNum, quotePadDigits) {
                    com.example.data.pref.AppSettingsManager.formatDocumentNo(
                        prefix = quotePrefix,
                        num = nextQuoteNum,
                        padDigits = quotePadDigits,
                        includeFy = false,
                        defaultPrefix = com.example.data.pref.AppSettingsManager.DEFAULT_QUOTE_PREFIX
                    )
                }
                SettingsItemRow(
                    icon = Icons.Filled.Tag,
                    iconTint = ext.warning,
                    title = "Invoice & Estimate Sequence",
                    subtitle = "Next Bill: $formattedNextInv • Quote: $formattedNextQuote",
                    onClick = {
                        if (isAdmin) {
                            showInvoiceSequenceDialog = true
                        } else {
                            viewModel.showToast("Only Admin can update invoice sequence")
                        }
                    },
                    testTag = "settings_invoice_seq_edit",
                    trailingContent = {
                        SettingsInlinePill(
                            text = if (isAdmin) "Edit" else "Locked",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isAdmin) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                SettingsRowDivider()

                // Row 2: Instant Dispatch Slip
                SettingsItemRow(
                    icon = Icons.Filled.Share,
                    iconTint = ext.positive,
                    title = "Instant Dispatch Slip",
                    subtitle = "Auto WhatsApp PDF + Thermal Print prompt",
                    onClick = {
                        viewModel.setInstantDispatchSlip(!instantDispatchSlip)
                    },
                    trailingContent = {
                        Switch(
                            checked = instantDispatchSlip,
                            onCheckedChange = { checked ->
                                viewModel.setInstantDispatchSlip(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("settings_dispatch_slip_switch")
                        )
                    }
                )
                SettingsRowDivider()

                // Row 3: Gemini AI Bill Scanner
                SettingsItemRow(
                    icon = Icons.Filled.Key,
                    iconTint = ext.info,
                    title = "Gemini AI Bill Scanner",
                    subtitle = if (currentSavedKey.isNotBlank()) {
                        "API Key Configured ✓ • Photo Bill Scan"
                    } else {
                        "Enter Gemini API key for bill scanning"
                    },
                    onClick = {
                        inputCustomKey = GeminiBillScanner.getSavedCustomApiKey(context)
                        showApiKeyDialog = true
                    },
                    testTag = "settings_gemini_key_row",
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsInlinePill(
                                text = if (currentSavedKey.isNotBlank()) "Active" else "Set Key",
                                containerColor = if (currentSavedKey.isNotBlank()) {
                                    ext.positive.copy(alpha = 0.14f)
                                } else {
                                    ext.warning.copy(alpha = 0.14f)
                                },
                                contentColor = if (currentSavedKey.isNotBlank()) ext.positive else ext.warning
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                SettingsRowDivider()

                // Row 4: Color Machine Tint Import
                SettingsItemRow(
                    icon = Icons.Filled.Palette,
                    iconTint = ext.purple,
                    title = "Color Machine Tint Logs",
                    subtitle = "Import .CSC / .CSV dispenser deduction batches",
                    onClick = { showColorMachineDialog = true },
                    testTag = "settings_color_tint_row",
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ext.positive)
                            )
                            Text(
                                text = "Ready",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }

        // 5. Section 3: Data & Security
        item {
            SettingsSectionHeader(
                icon = Icons.Filled.Shield,
                iconTint = ext.positive,
                title = "Data & Security",
                rightLabel = "ACCESS"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupedCard {
                // Row 1: Role & Account Access Control
                SettingsItemRow(
                    icon = Icons.Filled.AdminPanelSettings,
                    iconTint = ext.info,
                    title = if (isAdmin) "Admin Role Active" else "Staff Operator Access",
                    subtitle = if (isAdmin) {
                        "${currentUserEmail ?: "Admin Account"} • Manage user roles below"
                    } else {
                        "${currentUserEmail ?: "Staff Account"} • Tap to refresh role from database"
                    },
                    onClick = {
                        viewModel.refreshCurrentUserRole(showFeedback = true)
                    },
                    testTag = "settings_admin_access_row",
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsInlinePill(
                                text = if (isAdmin) "ADMIN" else "STAFF",
                                containerColor = if (isAdmin) ext.positive.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isAdmin) ext.positive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isAdmin) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (isAdmin) ext.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                // Expanded Admin Danger Controls when unlocked
                if (isAdmin) {
                    SettingsRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                            border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Clear History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { showDeleteAllDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                            border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Delete All Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SettingsRowDivider()
                    val noticeActive = activeAnnouncement.isActiveAt()
                    val noticeScheduled = !noticeActive && festivalStudioConfig.announcement.enabled && festivalStudioConfig.announcement.message.isNotBlank()
                    val customActiveCount = festivalStudioConfig.effectiveCustomFestivals().count { it.enabled }
                    val festSummary = when (festivalStudioConfig.overrideMode) {
                        com.example.data.pref.FestivalStudioConfig.MODE_OFF -> "Festivals Off"
                        com.example.data.pref.FestivalStudioConfig.MODE_CUSTOM ->
                            if (customActiveCount > 0) "$customActiveCount Custom Card${if (customActiveCount > 1) "s" else ""}" else "Custom Inactive"
                        else -> {
                            val calLabel = if (festivalStudioConfig.autoCalendarEnabled) "Calendar + Custom" else "Custom Only"
                            if (activeFestivals.isNotEmpty()) "$calLabel (${activeFestivals.size} live)" else calLabel
                        }
                    }
                    val studioSummary = when {
                        noticeActive -> "Notice Live • $festSummary"
                        noticeScheduled -> "Notice Scheduled • $festSummary"
                        else -> "$festSummary • Notices & Festivals"
                    }
                    SettingsItemRow(
                        icon = Icons.Filled.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Announcement Studio",
                        subtitle = studioSummary,
                        onClick = { showFestivalStudioDialog = true },
                        testTag = "settings_announcement_studio_row",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SettingsInlinePill(
                                    text = "Admin",
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    SettingsRowDivider()
                    SettingsItemRow(
                        icon = Icons.Filled.AdminPanelSettings,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Staff & Admin Accounts",
                        subtitle = "Add, remove, or promote staff and admin accounts",
                        onClick = { showAdminUserManagementDialog = true },
                        testTag = "settings_admin_user_management_row",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val userCount by viewModel.authManager.registeredUsers.collectAsState()
                                SettingsInlinePill(
                                    text = "${userCount.size} Users",
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    SettingsRowDivider()
                    SettingsItemRow(
                        icon = Icons.Filled.History,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Inventory Audit Logs",
                        subtitle = "Track who added, updated, or deleted inventory items",
                        onClick = { viewModel.openAuditLogs() },
                        testTag = "settings_audit_logs_row",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SettingsInlinePill(
                                    text = "Admin",
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open Inventory Audit Logs",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
                SettingsRowDivider()

                // Change Account Email Address (OTP-verified)
                SettingsItemRow(
                    icon = Icons.Filled.AlternateEmail,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Change Email Address",
                    subtitle = currentUserEmail ?: "Update login email via OTP verification",
                    onClick = { showChangeEmailDialog = true },
                    testTag = "settings_change_email_row",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Change Email Address",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                SettingsRowDivider()

                // Row 2: Inventory Data Export & Import
                SettingsItemRow(
                    icon = Icons.Filled.Download,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Inventory Data Export",
                    subtitle = "${allItems.size} SKUs • Export .XLS / .CSV or import .XLSX",
                    onClick = { showDataBackupDialog = true },
                    testTag = "settings_export_button",
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "Export",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                )
                SettingsRowDivider()

                // Row 3: Interface Theme
                SettingsItemRow(
                    icon = if (ext.isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Interface Theme",
                    subtitle = when (themeMode) {
                        "dark" -> "Tactile Industrial Dark"
                        "light" -> "Tactile Industrial Light"
                        else -> "System Auto (${if (ext.isDark) "Dark" else "Light"})"
                    },
                    onClick = { showThemeLayoutDialog = true },
                    testTag = "settings_theme_button",
                    trailingContent = {
                        SettingsInlinePill(
                            text = "Active",
                            containerColor = ext.positive.copy(alpha = 0.14f),
                            contentColor = ext.positive
                        )
                    }
                )
                SettingsRowDivider()

                // Row 4: Daily 7:00 - 7:30 PM Sales & Expenses Reminder
                val dailyReminderEnabled by viewModel.isDailyReminderEnabled.collectAsState()
                val hasNotifPerm = DailyReminderScheduler.hasNotificationPermission(context)
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.setDailyReminderEnabled(true)
                        viewModel.sendTestDailyReminder()
                    } else {
                        viewModel.showToast("Notification permission is required for evening popups")
                    }
                }
                SettingsItemRow(
                    icon = Icons.Outlined.Notifications,
                    iconTint = if (dailyReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Evening Sales Reminder",
                    subtitle = when {
                        !dailyReminderEnabled -> "Paused • Toggle on for 7:00–7:30 PM popup"
                        !hasNotifPerm -> "Permission required • Tap to allow popups"
                        else -> "Every day 7:00–7:30 PM • Tap row to test"
                    },
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !DailyReminderScheduler.hasNotificationPermission(context)
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.sendTestDailyReminder()
                        }
                    },
                    testTag = "settings_daily_reminder_row",
                    trailingContent = {
                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !DailyReminderScheduler.hasNotificationPermission(context)
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setDailyReminderEnabled(checked)
                                }
                            },
                            modifier = Modifier.testTag("settings_daily_reminder_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                )
            }
        }

        // 6. Standalone Sync Diagnostics & Updates Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        showSyncUpdatesDialog = true
                        viewModel.checkForUpdates()
                    }
                    .testTag("settings_updates_button")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ext.warning.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = null,
                                tint = ext.warning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Diagnostics & Updates",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subtitleText = when (val s = updateStatus) {
                                is UpdateStatus.Checking -> "Checking for updates…"
                                is UpdateStatus.Available -> "Update ${s.info.tagName} ready to install!"
                                is UpdateStatus.Downloading -> "Downloading update ${(s.progress * 100).toInt()}%…"
                                is UpdateStatus.Error -> "Update check failed • Tap for details"
                                else -> "$syncStatus • Check OTA & DB sync"
                            }
                            Text(
                                text = subtitleText,
                                fontSize = 12.sp,
                                color = when (updateStatus) {
                                    is UpdateStatus.Available -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.Error -> ext.danger
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsInlinePill(
                        text = "Run ▸",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 7. Build Info & Bottom Action Buttons
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val packageInfo = remember(context) {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val appVersionName = packageInfo?.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
                    val appVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
                        ?: BuildConfig.VERSION_CODE.toLong()
                    Text(
                        text = "Stock Core • App v$appVersionName (Build $appVersionCode)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Terminal ID: $terminalId",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Operational Diagnostics Support Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSupportDialog = true }
                        .testTag("settings_support_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SupportAgent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnostics & Support Line",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sign Out from Depot Terminal Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ext.danger.copy(alpha = if (ext.isDark) 0.14f else 0.08f),
                    border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLogoutDialog = true }
                        .testTag("settings_signout_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = ext.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out from Depot Terminal",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.danger
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Permanently Delete Account (OTP Verified)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDeleteAccountDialog = true }
                        .testTag("settings_delete_account_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = ext.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete Account (OTP Verified)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ext.danger
                        )
                    }
                }
            }
        }
    }

    // ==================== DIALOGS ====================
    if (showProfileEditDialog && isAdmin) {
        SettingsProfileEditDialog(
            initialStoreName = storeIdentity.storeName.ifBlank { effectiveStoreTitle },
            initialName = supervisorName,
            initialRole = supervisorRole,
            initialDepot = depotLocation,
            onSave = { storeName, name, role, depot ->
                viewModel.saveStoreIdentity(
                    storeName = storeName,
                    supervisorName = name,
                    roleTitle = role,
                    location = depot
                )
                showProfileEditDialog = false
            },
            onDismiss = { showProfileEditDialog = false }
        )
    }

    if (showThemeLayoutDialog) {
        SettingsThemeAndLayoutDialog(
            viewModel = viewModel,
            onDismiss = { showThemeLayoutDialog = false }
        )
    }

    if (showLowStockThresholdDialog) {
        InputPromptDialog(
            title = "Default Low Stock Threshold",
            message = "Current default threshold: $lowStockThreshold units. Items with custom alerts override this value.",
            placeholder = lowStockThreshold.toString(),
            confirmText = "Save",
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()?.coerceAtLeast(0)
                if (parsed == null) {
                    viewModel.showToast("Enter a valid whole number (0 or higher)")
                } else {
                    viewModel.setLowStockDefaultThreshold(parsed)
                    showLowStockThresholdDialog = false
                }
            },
            onDismiss = { showLowStockThresholdDialog = false }
        )
    }

    if (showInvoiceSequenceDialog && isAdmin) {
        SettingsInvoiceSequenceDialog(
            initialPrefix = invoicePrefix,
            initialNextNum = nextInvoiceNum,
            initialPadDigits = invoicePadDigits,
            initialIncludeFy = invoiceIncludeFy,
            initialQuotePrefix = quotePrefix,
            initialNextQuoteNum = nextQuoteNum,
            initialQuotePadDigits = quotePadDigits,
            existingInvoiceNos = allGstBills.map { it.invoiceNo },
            onSaveDetailed = { prefix, nextNum, padDigits, includeFy, qPrefix, qNext, qPad ->
                viewModel.setInvoiceSequence(
                    prefix = prefix,
                    nextNum = nextNum,
                    padDigits = padDigits,
                    includeFy = includeFy,
                    quotePrefix = qPrefix,
                    nextQuoteNum = qNext,
                    quotePadDigits = qPad
                )
                showInvoiceSequenceDialog = false
            },
            onDismiss = { showInvoiceSequenceDialog = false }
        )
    }

    if (showDataBackupDialog) {
        SettingsDataBackupDialog(
            itemCount = allItems.size,
            onOpenExportXls = { showExportXlsDialog = true },
            onOpenExportCsv = { showExportDialog = true },
            onOpenImportFile = { showImportDialog = true },
            onOpenColorTintImport = { showColorMachineDialog = true },
            onUploadAllToDb = { viewModel.uploadAllToDatabase() },
            onDismiss = { showDataBackupDialog = false }
        )
    }

    if (showSyncUpdatesDialog) {
        SettingsSyncAndUpdatesDialog(
            viewModel = viewModel,
            onDismiss = { showSyncUpdatesDialog = false }
        )
    }

    if (showSupportDialog) {
        SettingsSupportDiagnosticsDialog(
            viewModel = viewModel,
            terminalId = terminalId,
            onDismiss = { showSupportDialog = false }
        )
    }

    if (showFestivalStudioDialog && isAdmin) {
        FestivalStudioDialog(
            viewModel = viewModel,
            onDismiss = { showFestivalStudioDialog = false }
        )
    }

    if (showAdminUserManagementDialog && isAdmin) {
        AdminUserManagementDialog(
            viewModel = viewModel,
            onDismiss = { showAdminUserManagementDialog = false }
        )
    }

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

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        GeminiBillScanner.setSavedCustomApiKey(context, inputCustomKey.trim())
                        showApiKeyDialog = false
                        viewModel.showToast("API key saved!")
                    }
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        val logoutMessage = if (pendingCount > 0) {
            "You have $pendingCount unsynced change(s) pending upload. Signing out clears local cache and discards unsynced changes."
        } else {
            "You'll need to enter the password again to unlock the terminal."
        }
        SimpleConfirmDialog(
            title = "Sign Out",
            message = logoutMessage,
            confirmText = if (pendingCount > 0) "Discard & Sign Out" else "Sign Out",
            isDanger = true,
            onConfirm = {
                viewModel.authManager.logout()
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showChangeEmailDialog) {
        ChangeEmailDialog(
            viewModel = viewModel,
            onDismiss = { showChangeEmailDialog = false }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountOtpDialog(
            viewModel = viewModel,
            onDismiss = { showDeleteAccountDialog = false }
        )
    }

    if (showExportXlsDialog) {
        SettingsExportXlsDialog(
            viewModel = viewModel,
            onDismiss = { showExportXlsDialog = false }
        )
    }

    if (showExportDialog) {
        SettingsExportCsvDialog(
            allItems = allItems,
            viewModel = viewModel,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showImportDialog) {
        SettingsImportItemsDialog(
            replaceExisting = replaceExisting,
            onReplaceExistingChange = { replaceExisting = it },
            onChooseFile = { selectFileLauncher.launch("*/*") },
            onImportRawCsv = { parsed ->
                viewModel.importItems(parsed, replaceExisting)
                showImportDialog = false
            },
            onInvalidCsv = { viewModel.showToast("Could not parse items. Check CSV format.") },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showColorMachineDialog) {
        ColorMachineTintDialog(
            viewModel = viewModel,
            onDismiss = { showColorMachineDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    rightLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = rightLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun SettingsGroupedCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (testTag != null) mod.testTag(testTag) else mod }
            .clickable(onClick = onClick)
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        trailingContent()
    }
}

@Composable
private fun SettingsInlinePill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
