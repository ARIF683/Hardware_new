package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
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

            Spacer(modifier = Modifier.height(14.dp))

            // Facebook-style Cover Photo & Profile Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Facebook Cover Photo Banner (Click or long press to change)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable {
                                viewModel.showToast("Opening photo picker for Cover Banner...")
                                bannerPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .onLongPress3s {
                                viewModel.showToast("Opening photo picker for Cover Banner...")
                                bannerPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                    ) {
                        if (!bannerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageModelHelper.rememberImageModel(bannerUrl),
                                contentDescription = "Store Cover Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.img_store_cover_banner_1791306669322),
                                fallback = painterResource(id = R.drawable.img_store_cover_banner_1791306669322)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_store_cover_banner_1791306669322),
                                contentDescription = "Store Cover Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Elegant gradient vignette
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.2f),
                                            Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )

                        if (isUploadingBanner) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating banner...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Top right: Live Sync Status Pill
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isRealtimeLive) SuccessGreen.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isRealtimeLive) "LIVE SYNC" else "LOCAL READY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Profile details with Overlapping Avatar (3s long press to change)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Overlapping Avatar positioned halfway across cover boundary
                        Box(
                            modifier = Modifier
                                .offset(y = (-36).dp)
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clickable {
                                    viewModel.showToast("Opening photo picker for Logo...")
                                    logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .onLongPress3s {
                                    viewModel.showToast("Opening photo picker for Logo...")
                                    logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                        ) {
                            if (!customLogoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageModelHelper.rememberImageModel(customLogoUrl),
                                    contentDescription = "Store Custom Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = selectedLogo.resId),
                                    fallback = painterResource(id = selectedLogo.resId)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = selectedLogo.resId),
                                    contentDescription = selectedLogo.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (isUploadingLogo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
                                }
                            }
                        }
                    }

                    // Store Title, Verification & Tagline
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = uiConfig.theme.storeTitle.ifBlank { "S.A.HARDWARE" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Store",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (uiConfig.theme.tagline.isNotBlank()) {
                            Text(
                                text = uiConfig.theme.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Facebook-Style Action Buttons Row
                        if (uiConfig.features.showQuickActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // + Quick Bill Primary Button
                                Button(
                                    onClick = { viewModel.openBillFlow() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.2f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Quick Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Quotes Button
                                FilledTonalButton(
                                    onClick = { viewModel.navigateToQuotes() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quotes (${quotations.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Khata Button
                                FilledTonalButton(
                                    onClick = { viewModel.navigateToKhata() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp), tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Khata (${ledgerAccounts.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Tint Machine Report Opener Button
                            FilledTonalButton(
                                onClick = { showColorMachineDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tint Machine Report (.XLS / .CSV)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // GST Calculator Button
                            Button(
                                onClick = { viewModel.openGstCalculator() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Text("📊 GST & E-Way Calc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (lowStockCount > 0 && uiConfig.features.showLowStockAlert) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            viewModel.inStockOnly.value = false
                            viewModel.navigateTo(NavigationTab.ITEMS)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "$lowStockCount items low or negative in stock",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Tap to review items in inventory",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Hero Card: Total Stock Value (Showcase Reference Design)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF5B4FFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6B58F6),
                                    Color(0xFF4C36ED),
                                    Color(0xFF3724B8)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Total Stock Value",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Dynamic growth indicator badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (todayDelta >= 0) Icons.Default.TrendingUp else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFF98FFB3),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val deltaFormatted = if (todayDelta >= 0) "+%.0f".format(todayDelta) else "%.0f".format(todayDelta)
                                    Text(
                                        text = "$deltaFormatted today",
                                        color = Color(0xFF98FFB3),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Value (Price valuation)
                        Text(
                            text = StockViewModel.formatRupees(priceVal),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sub stats footer inside hero card (Purchase Cost & Potential Profit)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cost Valuation",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = StockViewModel.formatRupees(costVal),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Potential Profit",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = StockViewModel.formatRupees(profit),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF98FFB3)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2x2 Metric Cards Grid (Total Items, Total Quantity, Potential Profit, Low Stock)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBoxCard(
                    title = "Total Items",
                    value = "$totalItems",
                    icon = Icons.Default.Category,
                    iconBg = Color(0xFFE8F0FE),
                    iconTint = Color(0xFF1A73E8),
                    subtext = "Active products",
                    modifier = Modifier.weight(1f)
                )

                MetricBoxCard(
                    title = "Total Quantity",
                    value = if (totalQty % 1.0 == 0.0) totalQty.toLong().toString() else "%.1f".format(totalQty),
                    icon = Icons.Default.Inventory2,
                    iconBg = Color(0xFFE6F4EA),
                    iconTint = Color(0xFF137333),
                    subtext = "Units in stock",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBoxCard(
                    title = "Estimated Margin",
                    value = if (priceVal > 0) "${((profit / priceVal) * 100).toInt()}%" else "0%",
                    icon = Icons.Default.ShowChart,
                    iconBg = Color(0xFFFEF7E0),
                    iconTint = Color(0xFFEA8600),
                    subtext = StockViewModel.formatRupees(profit),
                    modifier = Modifier.weight(1f)
                )

                MetricBoxCard(
                    title = "Low Stock",
                    value = "$lowStockCount",
                    icon = Icons.Default.Warning,
                    iconBg = if (lowStockCount > 0) Color(0xFFFCE8E6) else Color(0xFFF1F3F4),
                    iconTint = if (lowStockCount > 0) DangerRed else Color(0xFF5F6368),
                    subtext = if (lowStockCount > 0) "Needs restock" else "All healthy",
                    valueColor = if (lowStockCount > 0) DangerRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stock Flow Smooth Bézier Line Chart Card
            StockFlowChartCard(
                points = stockFlowPoints,
                totalTransactionsCount = recentTx.size
            )

            Spacer(modifier = Modifier.height(20.dp))
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
                            imageVector = Icons.Default.ShowChart,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.itemName,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            val noteDetails = listOfNotNull(
                dateFormatted,
                tx.note.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            Text(
                text = noteDetails,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val sign = if (isIn) "+" else "−"
            val qtyStr = if (tx.qty % 1.0 == 0.0) tx.qty.toLong().toString() else "%.1f".format(tx.qty)
            val unitStr = if (tx.unit != "pcs" && tx.unit.isNotBlank()) " ${tx.unit}" else ""
            Text(
                text = "$sign$qtyStr$unitStr",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isIn) SuccessGreen else DangerRed
            )
            Text(
                text = "Bal ${if (tx.balance % 1.0 == 0.0) tx.balance.toLong().toString() else "%.1f".format(tx.balance)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
