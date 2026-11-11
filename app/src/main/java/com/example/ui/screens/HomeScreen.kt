package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.util.ImageModelHelper
import com.example.R
import com.example.data.model.TransactionRecord
import com.example.data.remote.UpdateStatus
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.TacticalGreen
import com.example.ui.theme.TacticalDarkBg
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalCard
import com.example.ui.theme.TacticalBorder
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

// Custom tap & long press modifier for banner and logo
fun Modifier.onLongPress3s(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onTap = { onLongPress() },
        onLongPress = { onLongPress() }
    )
}

@Composable
fun HomeScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items by viewModel.allItems.collectAsState()
    val recentTx by viewModel.recentTransactions.collectAsState()
    val uiConfig by viewModel.uiConfig.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val isAnnouncementDismissed by viewModel.isAnnouncementDismissed.collectAsState()
    val isUpdateBannerDismissed by viewModel.isUpdateBannerDismissed.collectAsState()
    val quotations by viewModel.allQuotations.collectAsState()
    val ledgerAccounts by viewModel.allLedgerAccounts.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val bannerUrl by viewModel.bannerUrl.collectAsState()
    val customLogoUrl by viewModel.customLogoUrl.collectAsState()
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val stockFlowPoints by viewModel.stockFlow7Days.collectAsState()
    val todayDelta by viewModel.todayQtyDelta.collectAsState()

    var isUploadingBanner by remember { mutableStateOf(false) }
    var isUploadingLogo by remember { mutableStateOf(false) }
    var showColorMachineDialog by remember { mutableStateOf(false) }

    // Cover Banner Photo Picker
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingBanner = true
                viewModel.showToast("Uploading new banner to Cloudinary...")
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                val maxW = 1600
                                val maxH = 900
                                val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height, 1.0f)
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
                            val url = result.getOrNull()
                            viewModel.updateStoreBanner(url)
                        } else {
                            viewModel.showToast("Banner upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error: ${e.message}")
                } finally {
                    isUploadingBanner = false
                }
            }
        }
    }

    // Store Logo Photo Picker
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingLogo = true
                viewModel.showToast("Uploading new logo to Cloudinary...")
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
                            val url = result.getOrNull()
                            viewModel.updateStoreLogo(url)
                        } else {
                            viewModel.showToast("Logo upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error: ${e.message}")
                } finally {
                    isUploadingLogo = false
                }
            }
        }
    }

    val totalItems = items.size
    val totalQty = items.sumOf { it.qty }
    val costVal = items.sumOf { it.qty * it.cost }
    val priceVal = items.sumOf { it.qty * it.price }
    val profit = priceVal - costVal
    val lowStockCount = items.count { it.qty < 0 || (it.low > 0 && it.qty <= it.low) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Dynamic Announcement Banner from Server-Driven UI (changes immediately without APK reinstall)
        if (uiConfig.announcement.enabled && !isAnnouncementDismissed && uiConfig.announcement.message.isNotBlank()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                val bannerBg = when (uiConfig.announcement.severity) {
                    "warning" -> WarningAmber.copy(alpha = 0.15f)
                    "alert" -> DangerRed.copy(alpha = 0.15f)
                    "success" -> SuccessGreen.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                }
                val bannerIcon = when (uiConfig.announcement.severity) {
                    "warning" -> Icons.Default.Warning
                    "alert" -> Icons.Default.Warning
                    else -> Icons.Default.Info
                }
                val bannerTint = when (uiConfig.announcement.severity) {
                    "warning" -> WarningAmber
                    "alert" -> DangerRed
                    "success" -> SuccessGreen
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = bannerBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerTint)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiConfig.announcement.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiConfig.announcement.dismissible) {
                            IconButton(onClick = { viewModel.isAnnouncementDismissed.value = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // In-App Seamless Auto-Updater Banner
        when (val status = updateStatus) {
            is UpdateStatus.Available -> {
                if (!isUpdateBannerDismissed) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "New Update Available (${status.info.tagName})",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = status.info.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.isUpdateBannerDismissed.value = true }) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.startDownloadAndInstall(status.info.apkDownloadUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install Update In 1 Tap")
                                }
                            }
                        }
                    }
                }
            }
            is UpdateStatus.Downloading -> {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Downloading update... ${(status.progress * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            is UpdateStatus.ReadyToInstall -> {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Update Downloaded!", fontWeight = FontWeight.Bold)
                                Text("Ready to install on your device.", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Install")
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        item {
            val selectedLogo by viewModel.selectedLogo.collectAsState()
            val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()

            // Tactical Top Header Row: S.A. Hardware & Manager on left side, Actions on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // S.A. Hardware Name, Avatar & Manager role on left side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            viewModel.showToast("Opening photo picker for Logo...")
                            logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141923))
                            .border(1.5.dp, TacticalGreen, CircleShape)
                    ) {
                        if (!customLogoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageModelHelper.rememberImageModel(customLogoUrl),
                                contentDescription = "Store Logo",
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = uiConfig.theme.storeTitle.ifBlank { "S.A. HARDWARE" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TacticalGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Manager",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Actions on the right side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Notification Bell with unread badge
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF121620))
                            .clickable {
                                if (lowStockCount > 0) {
                                    viewModel.inStockOnly.value = false
                                    viewModel.navigateTo(NavigationTab.ITEMS)
                                } else {
                                    viewModel.showToast("No active stock alerts")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Alerts",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        if (lowStockCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }

                    // Settings Gear Trigger
                    IconButton(
                        onClick = { viewModel.navigateTo(NavigationTab.SETTINGS) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF121620))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Screen Title & Live Timestamp matching mockup
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    text = "STORE DASHBOARD",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                val dateFormattedStr = remember {
                    SimpleDateFormat("EEE, dd MMM | hh:mm a", Locale.getDefault()).format(Date()).uppercase()
                }
                Text(
                    text = dateFormattedStr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
            }

            // Tactical Hero Card: Daily Sales / Valuation with Neon Green Bézier Wave Chart
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                border = BorderStroke(1.2.dp, TacticalGreen.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "DAILY SALES (TODAY)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TacticalGreen,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = StockViewModel.formatRupees(priceVal),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "${recentTx.size + quotations.size} ORDERS • $totalItems ITEMS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF9AA3B2)
                            )
                        }

                        // Growth Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TacticalGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TacticalGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = TacticalGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "+14.3% vs. Yest.",
                                    color = TacticalGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tactical Neon Bézier Wave Chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height - 18.dp.toPx()
                            val pointsNorm = listOf(0.35f, 0.45f, 0.38f, 0.72f, 0.52f, 0.88f, 0.65f)
                            val step = w / (pointsNorm.size - 1)

                            val coords = pointsNorm.mapIndexed { idx, v ->
                                androidx.compose.ui.geometry.Offset(idx * step, h - (v * (h - 20.dp.toPx())))
                            }

                            val path = Path().apply {
                                moveTo(coords[0].x, coords[0].y)
                                for (i in 0 until coords.size - 1) {
                                    val p0 = coords[i]
                                    val p1 = coords[i + 1]
                                    cubicTo((p0.x + p1.x) / 2f, p0.y, (p0.x + p1.x) / 2f, p1.y, p1.x, p1.y)
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(path)
                                lineTo(w, h)
                                lineTo(0f, h)
                                close()
                            }

                            // Emerald glow area gradient
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        TacticalGreen.copy(alpha = 0.35f),
                                        TacticalGreen.copy(alpha = 0.02f)
                                    )
                                )
                            )

                            // Glowing neon stroke
                            drawPath(
                                path = path,
                                color = TacticalGreen,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Glowing peak nodes
                            coords.forEachIndexed { i, pt ->
                                if (i == 3 || i == 5) {
                                    drawCircle(color = TacticalGreen.copy(alpha = 0.4f), radius = 7.dp.toPx(), center = pt)
                                    drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = pt)
                                }
                            }
                        }
                    }

                    // Time labels under chart matching mockup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("14:00", "08:00", "12:00", "15:00", "18:00").forEach { t ->
                            Text(text = t, fontSize = 9.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two-Column Grid matching mockup exactly
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LEFT COLUMN: Low Stock Alerts + Reports / Billing
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Low Stock Alerts
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                        border = BorderStroke(1.dp, Color(0xFF212836)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.inStockOnly.value = false
                                viewModel.navigateTo(NavigationTab.ITEMS)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "LOW STOCK ALERTS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9AA3B2)
                                )
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (lowStockCount > 0) DangerRed else TacticalGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$lowStockCount",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (lowStockCount > 0) DangerRed else Color.White
                            )
                            Text(
                                text = if (lowStockCount > 0) "ITEMS LOW" else "ALL STOCKED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Mini low items with progress bars matching mockup
                            val lowSamples = items.filter { it.qty <= it.low }.take(3)
                            if (lowSamples.isNotEmpty()) {
                                lowSamples.forEach { itm ->
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                itm.name,
                                                fontSize = 9.5.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("(${itm.qty.toInt()})", fontSize = 9.5.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LinearProgressIndicator(
                                            progress = { (itm.qty / maxOf(itm.low, 1.0)).toFloat().coerceIn(0.1f, 1f) },
                                            color = if (itm.qty <= 0) DangerRed else WarningAmber,
                                            trackColor = Color(0xFF1E2633),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            } else {
                                Text("No stock depletion ✓", fontSize = 9.5.sp, color = TacticalGreen, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Card 2: Reports & POS Buttons matching mockup
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                        border = BorderStroke(1.dp, Color(0xFF212836)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Reports Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalGreen.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clickable { showColorMachineDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Assessment, contentDescription = null, tint = Color(0xFF022C1A), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REPORTS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF022C1A))
                                }
                            }

                            // New POS Billing Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalGreen.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clickable { viewModel.openGstCalculator() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF022C1A), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("NEW POS BILL", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF022C1A))
                                }
                            }
                        }
                    }
                }

                // RIGHT COLUMN: Store Efficiency Gauge + Tactical Quick Actions
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Store Efficiency Gauge Meter
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                        border = BorderStroke(1.dp, Color(0xFF212836)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "STORE EFFICIENCY",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9AA3B2),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val scorePct = if (items.isNotEmpty()) (((items.size - lowStockCount).toFloat() / items.size) * 100).toInt() else 94

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeW = 6.dp.toPx()
                                    drawArc(
                                        color = Color(0xFF1E2633),
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                    val sweep = (scorePct.toFloat() / 100f) * 270f
                                    drawArc(
                                        color = TacticalGreen,
                                        startAngle = 135f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$scorePct%", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text("OPTIMAL", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = TacticalGreen)
                                }
                            }
                        }
                    }

                    // Card 2: Quick Actions Pills matching mockup
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
                        border = BorderStroke(1.dp, Color(0xFF212836)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "QUICK ACTIONS",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9AA3B2),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            // Action 1: New Transaction
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TacticalGreen.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, TacticalGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.navigateTo(NavigationTab.TRANSACTIONS) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = TacticalGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("NEW TRANSACTION", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }

                            // Action 2: Check Inventory
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF141923),
                                border = BorderStroke(0.8.dp, Color(0xFF212836)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.navigateTo(NavigationTab.ITEMS) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = TacticalGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CHECK INVENTORY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }

                            // Action 3: Restock Request / Supplier Rates
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF141923),
                                border = BorderStroke(0.8.dp, Color(0xFF212836)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.navigateTo(NavigationTab.ITEMS) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = TacticalGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RESTOCK REQUEST", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }

                            // Action 4: Add Product
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF141923),
                                border = BorderStroke(0.8.dp, Color(0xFF212836)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.inStockOnly.value = false
                                        viewModel.navigateTo(NavigationTab.ITEMS)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = TacticalGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ADD PRODUCT", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.navigateTo(NavigationTab.TRANSACTIONS) }) {
                    Text("View all")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentTx.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions yet.\nOpen an item and tap + New.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(recentTx) { tx ->
                TransactionRowItem(
                    tx = tx,
                    onItemClick = {
                        if (!tx.itemId.isNullOrEmpty()) {
                            viewModel.openItemDetail(tx.itemId)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
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
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricBoxCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    subtext: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StockFlowChartCard(
    points: List<com.example.ui.StockFlowDayPoint>,
    totalTransactionsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Subtitle + 7D badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Stock Flow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = Color(0xFF5B4FFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Net stock movement over 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Past 7 Days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Custom Smooth Canvas Line Chart
            val safePoints = remember(points) {
                if (points.isNotEmpty()) points else {
                    listOf(
                        com.example.ui.StockFlowDayPoint("Mon", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Tue", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Wed", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Thu", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Fri", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Sat", "", 0.0, 0.0, 0.0),
                        com.example.ui.StockFlowDayPoint("Today", "", 0.0, 0.0, 0.0)
                    )
                }
            }

            val primaryChartColor = Color(0xFF5B4FFF)
            val chartFillBrush = Brush.verticalGradient(
                colors = listOf(
                    primaryChartColor.copy(alpha = 0.35f),
                    primaryChartColor.copy(alpha = 0.02f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bottomPadding = 24.dp.toPx()
                    val graphHeight = canvasHeight - bottomPadding

                    val values = safePoints.map { it.netQuantity.toFloat() }
                    val minVal = minOf(values.minOrNull() ?: 0f, 0f)
                    val maxVal = maxOf(values.maxOrNull() ?: 0f, 10f)
                    val range = if (maxVal - minVal > 0f) (maxVal - minVal) else 1f

                    val stepX = if (safePoints.size > 1) canvasWidth / (safePoints.size - 1) else canvasWidth

                    // Draw subtle horizontal grid lines (3 guidelines)
                    val gridLines = 3
                    for (i in 0..gridLines) {
                        val y = graphHeight * (i.toFloat() / gridLines)
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Build path coordinates
                    val coords = safePoints.mapIndexed { index, pt ->
                        val x = index * stepX
                        val normalized = (pt.netQuantity.toFloat() - minVal) / range
                        val y = graphHeight - (normalized * (graphHeight - 20.dp.toPx())) - 10.dp.toPx()
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    if (coords.isNotEmpty()) {
                        // Smooth cubic curve path
                        val strokePath = Path().apply {
                            moveTo(coords[0].x, coords[0].y)
                            for (i in 0 until coords.size - 1) {
                                val p0 = coords[i]
                                val p1 = coords[i + 1]
                                val controlPoint1 = androidx.compose.ui.geometry.Offset((p0.x + p1.x) / 2f, p0.y)
                                val controlPoint2 = androidx.compose.ui.geometry.Offset((p0.x + p1.x) / 2f, p1.y)
                                cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p1.x, p1.y)
                            }
                        }

                        // Fill path
                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(coords.last().x, graphHeight)
                            lineTo(coords.first().x, graphHeight)
                            close()
                        }

                        drawPath(path = fillPath, brush = chartFillBrush)
                        drawPath(
                            path = strokePath,
                            color = primaryChartColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Draw point dots
                        coords.forEachIndexed { idx, point ->
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = point
                            )
                            drawCircle(
                                color = primaryChartColor,
                                radius = 2.5.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }

                // Day Labels at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    safePoints.forEach { pt ->
                        Text(
                            text = pt.dayLabel,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (pt.dayLabel == "Today") FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic flow summary footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalIn7D = safePoints.sumOf { it.inQty }
                val totalOut7D = safePoints.sumOf { it.outQty }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Added: +%.0f".format(totalIn7D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Issued: -%.0f".format(totalOut7D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRowItem(
    tx: TransactionRecord,
    onItemClick: () -> Unit
) {
    val isIn = tx.action == "in"
    val dateFormatted = remember(tx.createdAt) {
        try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdfIn.parse(tx.createdAt.take(19))
            val sdfOut = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            date?.let { sdfOut.format(it) } ?: tx.createdAt
        } catch (e: Exception) {
            tx.createdAt
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131C)),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isIn) TacticalGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f))
                        .border(1.dp, if (isIn) TacticalGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIn) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isIn) TacticalGreen else DangerRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.itemName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val noteDetails = listOfNotNull(
                        dateFormatted,
                        tx.note.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Text(
                        text = noteDetails,
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val sign = if (isIn) "+" else "−"
                val qtyStr = if (tx.qty % 1.0 == 0.0) tx.qty.toLong().toString() else "%.1f".format(tx.qty)
                val unitStr = if (tx.unit != "pcs" && tx.unit.isNotBlank()) " ${tx.unit}" else ""
                Text(
                    text = "$sign$qtyStr$unitStr",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isIn) TacticalGreen else DangerRed
                )
                Text(
                    text = "Bal: ${if (tx.balance % 1.0 == 0.0) tx.balance.toLong().toString() else "%.1f".format(tx.balance)}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
