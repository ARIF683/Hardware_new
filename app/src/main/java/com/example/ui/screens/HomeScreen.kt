package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TransactionRecord
import com.example.data.remote.UpdateStatus
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateTimeHelper
import com.example.util.ImageModelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HomeScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalExtendedColors.current

    val items by viewModel.allItems.collectAsState()
    val recentTx by viewModel.recentTransactions.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val activeAnnouncement by viewModel.activeAnnouncement.collectAsState()
    val activeFestivals by viewModel.activeFestivals.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val storeIdentity by viewModel.storeIdentity.collectAsState()
    val lowStockAlertsEnabled by viewModel.lowStockAlertsEnabled.collectAsState()
    val lowStockDefaultThreshold by viewModel.lowStockDefaultThreshold.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val isAnnouncementDismissed by viewModel.isAnnouncementDismissed.collectAsState()
    val isUpdateBannerDismissed by viewModel.isUpdateBannerDismissed.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val bannerUrl by viewModel.bannerUrl.collectAsState()
    val customLogoUrl by viewModel.customLogoUrl.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    var showColorMachineDialog by remember { mutableStateOf(false) }
    var overviewPeriod by remember { mutableStateOf("This Month") }
    var showPeriodMenu by remember { mutableStateOf(false) }

    // Cover Banner Photo Picker (tapping hero image updates cover banner)
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.showToast("Uploading store banner...")
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
                            viewModel.updateStoreBanner(result.getOrNull())
                        } else {
                            viewModel.showToast("Banner upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error: ${e.message}")
                }
            }
        }
    }

    // Store Logo Photo Picker (tapping avatar updates custom store logo)
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

    val totalItems = items.size
    val inStockCount = items.count { it.qty > 0 }
    val lowStockCount = remember(items, lowStockDefaultThreshold) {
        items.count { viewModel.appSettingsManager.isLowOrOutOfStock(it) }
    }

    val baseRecentTx = if (allTransactions.isNotEmpty()) allTransactions else recentTx
    val filteredRecentTx = remember(baseRecentTx, overviewPeriod) {
        if (overviewPeriod == "All Time") {
            baseRecentTx.take(3)
        } else {
            val cutoffMillis = Calendar.getInstance().apply {
                when (overviewPeriod) {
                    "This Week" -> add(Calendar.DAY_OF_YEAR, -7)
                    "This Month" -> add(Calendar.DAY_OF_YEAR, -30)
                }
            }.timeInMillis
            baseRecentTx.filter { tx ->
                val txMillis = DateTimeHelper.parseTimestampMillis(tx.createdAt)
                txMillis != null && txMillis >= cutoffMillis
            }.take(3)
        }
    }

    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning,"
            hour < 17 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Bar
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
                            text = "Home",
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
                    // Notification Bell with Red Numeric Badge
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
                            .testTag("home_notifications_button"),
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

                    // Store Profile Avatar (tap to update store logo)
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
                            .testTag("home_logo_avatar"),
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

        // Server-Driven + Auto-Calendar Festival Carousel
        if (activeFestivals.isNotEmpty()) {
            item {
                com.example.ui.components.FestivalCarouselSection(
                    festivals = activeFestivals,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Admin-Synced Store Announcement Banner (via Announcement Studio)
        if (activeAnnouncement.isActiveAt() && !isAnnouncementDismissed) {
            item {
                val bannerTint = when (activeAnnouncement.severity) {
                    "warning" -> ext.warning
                    "alert" -> ext.danger
                    "success" -> ext.positive
                    else -> MaterialTheme.colorScheme.primary
                }
                val bannerIcon = when (activeAnnouncement.severity) {
                    "warning", "alert" -> Icons.Default.Warning
                    else -> Icons.Default.Info
                }
                val timerCaption = activeAnnouncement.timerSummaryLabel()
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = bannerTint.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, bannerTint.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().testTag("home_announcement_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerTint)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeAnnouncement.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (timerCaption != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = timerCaption,
                                    fontSize = 11.sp,
                                    color = bannerTint,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (activeAnnouncement.dismissible) {
                            IconButton(onClick = { viewModel.isAnnouncementDismissed.value = true }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-App Auto-Updater Banner
        when (val status = updateStatus) {
            is UpdateStatus.Available -> {
                if (!isUpdateBannerDismissed) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "New Update Available (${status.info.tagName})",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = status.info.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.isUpdateBannerDismissed.value = true }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.startDownloadAndInstall(status.info.apkDownloadUrl) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install Update In 1 Tap", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            is UpdateStatus.Downloading -> {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Downloading update... ${(status.progress * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
            is UpdateStatus.ReadyToInstall -> {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ext.positive.copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, ext.positive.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = ext.positive)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Update Downloaded!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Ready to install on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = ext.positive, contentColor = Color.White)
                            ) {
                                Text("Install")
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        // 2. Hero Welcome Banner Card (always deep slate-navy with warehouse photo on right)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ext.heroBannerStart),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clickable {
                        if (isAdmin) {
                            bannerPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            viewModel.showToast("Admin access required to update store banner")
                        }
                    }
                    .testTag("home_hero_banner")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ext.heroBannerStart, ext.heroBannerEnd)
                            )
                        )
                ) {
                    // Warehouse illustration on the right half
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.54f)
                            .align(Alignment.CenterEnd)
                    ) {
                        // Styled vector & radial gradient fallback when no custom banner is set (or while loading)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                            ext.heroBannerEnd,
                                            ext.heroBannerStart
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warehouse,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .size(132.dp)
                                    .offset(x = 12.dp, y = 10.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                modifier = Modifier.padding(end = 22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = "Hardware Warehouse Banner",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(32.dp)
                                )
                            }
                        }

                        if (!bannerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageModelHelper.rememberImageModel(bannerUrl),
                                contentDescription = "Warehouse Store Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Smooth left-to-right gradient fade into the slate-navy hero background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.0f to ext.heroBannerStart,
                                        0.38f to ext.heroBannerStart.copy(alpha = 0.55f),
                                        1.0f to Color.Transparent
                                    )
                                )
                        )
                    }

                    // Greeting Text Column on the left
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.62f)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = greetingText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${storeIdentity.supervisorName.ifBlank { "Operator" }}!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep your stock in check.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // 3. 4 Quick Action Tiles
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickNavTile(
                    label = "Items",
                    icon = Icons.Outlined.Inventory2,
                    accent = MaterialTheme.colorScheme.primary,
                    isDark = ext.isDark,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_items"),
                    onClick = { viewModel.navigateTo(NavigationTab.ITEMS) }
                )
                QuickNavTile(
                    label = "Billing",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    accent = ext.info,
                    isDark = ext.isDark,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_billing"),
                    onClick = { viewModel.navigateTo(NavigationTab.BILLING) }
                )
                QuickNavTile(
                    label = "Transactions",
                    icon = Icons.Default.SwapHoriz,
                    accent = ext.positive,
                    isDark = ext.isDark,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_transactions"),
                    onClick = { viewModel.navigateToCashflow(0) }
                )
                QuickNavTile(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    accent = ext.purple,
                    isDark = ext.isDark,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_settings"),
                    onClick = { viewModel.navigateTo(NavigationTab.SETTINGS) }
                )
            }
        }

        // 4. Overview Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Overview",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewStatBox(
                            count = "$totalItems",
                            label = "Total Items",
                            icon = Icons.Outlined.Inventory2,
                            accent = ext.info,
                            isDark = ext.isDark,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overview_total_items"),
                            onClick = {
                                viewModel.inStockOnly.value = false
                                viewModel.requestedStockStatusFilter.value = "all"
                                viewModel.navigateTo(NavigationTab.ITEMS)
                            }
                        )
                        OverviewStatBox(
                            count = "$inStockCount",
                            label = "In Stock",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accent = ext.positive,
                            isDark = ext.isDark,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overview_in_stock"),
                            onClick = {
                                viewModel.inStockOnly.value = true
                                viewModel.requestedStockStatusFilter.value = "in_stock"
                                viewModel.navigateTo(NavigationTab.ITEMS)
                            }
                        )
                        OverviewStatBox(
                            count = "$lowStockCount",
                            label = "Low Stock",
                            icon = Icons.Outlined.WarningAmber,
                            accent = ext.danger,
                            isDark = ext.isDark,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overview_low_stock"),
                            onClick = {
                                viewModel.inStockOnly.value = false
                                viewModel.requestedStockStatusFilter.value = "low"
                                viewModel.navigateTo(NavigationTab.ITEMS)
                            }
                        )
                    }
                }
            }
        }

        // 5. Recent Transactions Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recent Transactions",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.navigateToCashflow(1) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("home_view_all_transactions")
                        ) {
                            Text(
                                text = "View All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View All Transactions",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showPeriodMenu = true }
                                .testTag("home_overview_period_filter")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = overviewPeriod,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select time period",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = { showPeriodMenu = false }
                        ) {
                            listOf("This Month", "This Week", "All Time").forEach { period ->
                                DropdownMenuItem(
                                    text = { Text(period) },
                                    onClick = {
                                        overviewPeriod = period
                                        showPeriodMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredRecentTx.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (baseRecentTx.isEmpty()) {
                                    "No recent transactions yet."
                                } else {
                                    "No transactions in selected period."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            filteredRecentTx.forEach { tx ->
                                HomeRecentTxRow(
                                    tx = tx,
                                    onClick = {
                                        if (!tx.itemId.isNullOrEmpty()) {
                                            viewModel.openItemDetail(tx.itemId)
                                        } else {
                                            viewModel.navigateToCashflow(1)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Bottom CTA Banner Card ("Track. Manage. Grow.")
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (ext.isDark) {
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    )
                                }
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Track. Manage. Grow.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Smarter inventory, stronger sales",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.navigateTo(NavigationTab.ITEMS) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("home_get_started_button")
                    ) {
                        Text(
                            text = "Get Started",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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
private fun QuickNavTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .heightIn(min = 90.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = if (isDark) 0.16f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OverviewStatBox(
    count: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (isDark) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeRecentTxRow(
    tx: TransactionRecord,
    onClick: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isIn = tx.action == "in"
    val isSale = !isIn && (
        tx.note.contains("POS", ignoreCase = true) ||
            tx.note.contains("BILL", ignoreCase = true) ||
            tx.note.contains("Sale", ignoreCase = true)
        )

    val rowIcon = when {
        isIn -> Icons.Outlined.Inventory2
        isSale -> Icons.Outlined.ShoppingCart
        else -> Icons.Default.SwapHoriz
    }
    val rowAccent = when {
        isIn -> ext.info
        isSale -> ext.positive
        else -> ext.warning
    }
    val titleText = when {
        isIn -> "Stock Added"
        isSale -> "Sale"
        else -> "Stock Updated"
    }

    val qtyStr = if (tx.qty % 1.0 == 0.0) tx.qty.toLong().toString() else "%.1f".format(tx.qty)
    val unitStr = tx.unit.ifBlank { "pcs" }
    val subtitleText = when {
        isIn -> "$qtyStr $unitStr • ${tx.itemName}"
        isSale && tx.note.isNotBlank() -> "${tx.note} • ${tx.itemName}"
        else -> "${tx.itemName} • $qtyStr $unitStr"
    }
    val valueText = if (isIn) "+ $qtyStr" else "- $qtyStr"
    val valueColor = when {
        isIn -> ext.info
        isSale -> ext.positive
        else -> MaterialTheme.colorScheme.primary
    }

    val timeLabel = remember(tx.createdAt) {
        formatRelativeTimestamp(tx.createdAt)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rowAccent.copy(alpha = if (ext.isDark) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = rowIcon,
                    contentDescription = titleText,
                    tint = rowAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = valueText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatRelativeTimestamp(raw: String): String {
    return try {
        val millis = DateTimeHelper.parseTimestampMillis(raw) ?: return raw
        val date = Date(millis)
        val target = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val timePart = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

        val sameYear = target.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
        when {
            sameYear && dayDiff == 0 -> "Today, $timePart"
            sameYear && dayDiff == 1 -> "Yesterday, $timePart"
            else -> SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        raw
    }
}

@Composable
fun TransactionRowItem(
    tx: TransactionRecord,
    onItemClick: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val isIn = tx.action == "in"
    val dateFormatted = remember(tx.createdAt) {
        formatRelativeTimestamp(tx.createdAt)
    }
    val actionColor = if (isIn) ext.positive else ext.danger

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        .background(actionColor.copy(alpha = 0.14f))
                        .border(1.dp, actionColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIn) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.itemName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = actionColor
                )
                Text(
                    text = "Bal: ${if (tx.balance % 1.0 == 0.0) tx.balance.toLong().toString() else "%.1f".format(tx.balance)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
