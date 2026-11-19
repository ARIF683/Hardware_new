package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Item
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.components.SimpleConfirmDialog
import com.example.ui.theme.LocalExtendedColors
import com.example.util.ImageModelHelper
import com.example.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ItemDetailScreen(
    itemId: String,
    viewModel: StockViewModel,
    onEditItem: (Item) -> Unit,
    onOpenTransactionDialog: (Item, String) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val ext = LocalExtendedColors.current

    val items by viewModel.allItems.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val customLogoUrl by viewModel.customLogoUrl.collectAsState()

    val item = items.firstOrNull { it.id == itemId }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPackagingDialog by remember { mutableStateOf(false) }
    var showEditPriceDialog by remember { mutableStateOf(false) }
    var showBarcodeInfoDialog by remember { mutableStateOf(false) }
    var isUploadingImage by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && item != null) {
            scope.launch {
                isUploadingImage = true
                viewModel.showToast("Uploading item photo...")
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                val maxDim = 800
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
                            val newUrl = result.getOrNull()
                            val updatedItem = item.copy(imageUrl = newUrl)
                            haptics.confirm()
                            viewModel.updateItem(updatedItem)
                            viewModel.showToast("Item photo uploaded & synced!")
                        } else {
                            viewModel.showToast("Upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error: ${e.message}")
                } finally {
                    isUploadingImage = false
                }
            }
        }
    }

    if (item == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Item not found", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.navigateBack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val itemTransactions = remember(allTransactions, item.id) {
        allTransactions.filter { it.itemId == item.id }
    }

    val isOutOfStock = item.qty <= 0.0
    val isLowStock = !isOutOfStock && (item.low > 0.0 && item.qty <= item.low)
    val unitStr = item.unit.ifBlank { "pcs" }
    val skuStr = item.code.ifBlank { item.id.takeLast(6).uppercase() }

    val marginPct = if (item.cost > 0.0) ((item.price - item.cost) / item.cost) * 100.0 else null
    val marginText = when {
        marginPct == null -> "—"
        marginPct >= 0 -> "+${"%.1f".format(marginPct)}%"
        else -> "${"%.1f".format(marginPct)}%"
    }
    val isPositiveMargin = (marginPct ?: 0.0) >= 0.0
    val stockValue = (item.qty.coerceAtLeast(0.0)) * item.cost

    val minAlert = item.low.coerceAtLeast(0.0)
    val maxRef = maxOf(item.qty * 1.4, minAlert * 4.0, 100.0)
    val progressFraction = (item.qty / maxRef).toFloat().coerceIn(0f, 1f)
    val alertMarkerFraction = if (minAlert > 0.0) (minAlert / maxRef).toFloat().coerceIn(0.08f, 0.5f) else 0f

    val statusText: String
    val standingBadgeText: String
    val statusColor: Color
    when {
        isOutOfStock -> {
            statusText = "Out of Stock"
            standingBadgeText = "OUT OF STOCK"
            statusColor = ext.danger
        }
        isLowStock -> {
            statusText = "Low Stock"
            standingBadgeText = "LOW LEVEL"
            statusColor = ext.warning
        }
        else -> {
            statusText = "In Stock"
            standingBadgeText = "OPTIMAL LEVEL"
            statusColor = ext.positive
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "ITEM DETAILS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = effectiveStoreTitle.ifBlank { "HardwareStock" },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("detail_overflow_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (item.imageUrl.isNullOrBlank()) "Attach Photo" else "Change Photo") },
                                    onClick = {
                                        showMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Item Details") },
                                    onClick = {
                                        showMenu = false
                                        onEditItem(item)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configure Packaging") },
                                    onClick = {
                                        showMenu = false
                                        showPackagingDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Item", color = ext.danger) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ext.danger) }
                                    )
                                }
                            }
                        }

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
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenTransactionDialog(item, "out") },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_adjust_stock_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adjust Stock", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onOpenTransactionDialog(item, "in") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_reorder_button")
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reorder Stock", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Product Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (ext.isDark) {
                                        listOf(
                                            MaterialTheme.colorScheme.surface,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                        )
                                    } else {
                                        listOf(
                                            MaterialTheme.colorScheme.surface,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    }
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // Top Category + Status Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.18f else 0.10f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.type.ifBlank { "GENERAL INVENTORY" }.uppercase(),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = statusColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                                border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    if (isOutOfStock) {
                                        Icon(Icons.Default.Block, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                                    } else if (isLowStock) {
                                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = statusText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Item Name & Spec Subtitle
                        Text(
                            text = item.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 27.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitleParts = listOfNotNull(
                            item.brand.takeIf { it.isNotBlank() },
                            item.size.takeIf { it.isNotBlank() }?.let { "Size: $it" },
                            "Unit: $unitStr"
                        )
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Technical / Product Photo Blueprint Frame (tappable to change photo)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(186.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ext.heroBannerStart, ext.heroBannerEnd)
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("detail_hero_image_box")
                        ) {
                            if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageModelHelper.rememberImageModel(item.imageUrl),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Attach product photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap to Attach Product Photo",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }

                            // Bottom-left technical tag overlay (Size / Standard / Photo badge)
                            val overlayLabel = item.size.ifBlank { item.brand.ifBlank { unitStr.uppercase() } }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ext.heroBannerStart.copy(alpha = 0.85f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (item.imageUrl.isNullOrBlank()) overlayLabel else "$overlayLabel • Tap to update",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            if (isUploadingImage) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Uploading photo...",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // SKU & BARCODE Metadata Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = "SKU",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = skuStr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                onClick = { showBarcodeInfoDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "BARCODE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.barcode.ifBlank { "—" },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Barcode details",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Stock Standing Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.18f else 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Stock Standing",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                                border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = standingBadgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    letterSpacing = 0.4.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formatCompactQty(item.qty),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isOutOfStock) ext.danger else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$unitStr available",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }

                            Text(
                                text = "Min: ${formatCompactQty(minAlert)} • Max: ${formatCompactQty(maxRef)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Segmented Threshold Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (progressFraction > 0f) {
                                val firstSegment = minOf(progressFraction, if (alertMarkerFraction > 0f) alertMarkerFraction else 0.25f)
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (!isOutOfStock && firstSegment > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(firstSegment)
                                                .fillMaxHeight()
                                                .background(if (isLowStock) ext.warning else MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    val remaining = (progressFraction - firstSegment).coerceAtLeast(0f)
                                    if (remaining > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(remaining / (1f - firstSegment).coerceAtLeast(0.01f))
                                                .fillMaxHeight()
                                                .background(if (isLowStock) ext.warning else ext.positive)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (minAlert > 0) "Reorder Alert at ${formatCompactQty(minAlert)}" else "No reorder alert set",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val buffer = item.qty - minAlert
                            Text(
                                text = when {
                                    buffer > 0 && minAlert > 0 -> "${formatCompactQty(buffer)} $unitStr buffer"
                                    buffer < 0 -> "Short ${formatCompactQty(-buffer)} $unitStr"
                                    else -> "Value: ${formatCompactRupees(stockValue)}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (buffer >= 0) ext.positive else ext.danger
                            )
                        }
                    }
                }
            }

            // 3. 4 Quick Action Tiles (+ Stock In, - Stock Out, Barcode, Edit Item)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailQuickActionTile(
                        label = "+ Stock In",
                        icon = Icons.Default.AddBox,
                        iconTint = ext.positive,
                        containerTint = ext.positive.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_quick_stock_in"),
                        onClick = { onOpenTransactionDialog(item, "in") }
                    )
                    DetailQuickActionTile(
                        label = "- Stock Out",
                        icon = Icons.Default.IndeterminateCheckBox,
                        iconTint = MaterialTheme.colorScheme.primary,
                        containerTint = MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_quick_stock_out"),
                        onClick = { onOpenTransactionDialog(item, "out") }
                    )
                    DetailQuickActionTile(
                        label = "Barcode",
                        icon = Icons.Default.QrCodeScanner,
                        iconTint = ext.info,
                        containerTint = ext.info.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_quick_barcode"),
                        onClick = { showBarcodeInfoDialog = true }
                    )
                    DetailQuickActionTile(
                        label = "Edit Item",
                        icon = Icons.Default.Tune,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        containerTint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_quick_edit"),
                        onClick = { onEditItem(item) }
                    )
                }
            }

            // 4. Specifications & Pricing Card
            item {
                DetailSpecificationsCard(
                    item = item,
                    marginText = marginText,
                    isPositiveMargin = isPositiveMargin,
                    stockValue = stockValue,
                    onEditPrice = { showEditPriceDialog = true }
                )
            }

            // 5. Multi-Unit & Packaging Converter Card
            item {
                DetailPackagingConverterCard(
                    item = item,
                    onConfigureClick = { showPackagingDialog = true }
                )
            }

            // 6. Movement History Card
            item {
                DetailMovementHistoryCard(
                    itemTransactions = itemTransactions,
                    defaultUnit = unitStr,
                    onViewAll = { viewModel.navigateToCashflow(1) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    // Barcode & SKU Quick View / Copy Dialog
    if (showBarcodeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBarcodeInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SKU & Barcode Label", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SKU CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(skuStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("BARCODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.barcode.ifBlank { "Not assigned" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val copyVal = item.barcode.ifBlank { skuStr }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Item Code", copyVal))
                        viewModel.showToast("Copied $copyVal")
                        showBarcodeInfoDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Code")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBarcodeInfoDialog = false
                    onEditItem(item)
                }) {
                    Text("Edit Code")
                }
            }
        )
    }

    // Configure Packaging Dialog
    if (showPackagingDialog) {
        var inputSecUnit by remember { mutableStateOf(item.secondaryUnit ?: "Carton") }
        var inputFactor by remember { mutableStateOf(if (item.conversionFactor > 1.0) item.conversionFactor.toInt().toString() else "24") }
        var inputSecPrice by remember { mutableStateOf(if (item.secondaryPrice > 0.0) item.secondaryPrice.toString() else "") }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showPackagingDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Configure Packaging & Units",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "e.g. 1 Carton = 24 Pieces, 1 Box = 10 Pcs",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = inputSecUnit,
                        onValueChange = { inputSecUnit = it },
                        label = { Text("Secondary / Bulk Unit") },
                        placeholder = { Text("Carton, Box, Roll, Bundle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputFactor,
                        onValueChange = { inputFactor = it },
                        label = { Text("Pieces per $inputSecUnit") },
                        placeholder = { Text("e.g. 24") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputSecPrice,
                        onValueChange = { inputSecPrice = it },
                        label = { Text("Wholesale Price per $inputSecUnit (₹)") },
                        placeholder = { Text("Optional carton selling price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPackagingDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val fact = inputFactor.toDoubleOrNull() ?: 1.0
                                val price = inputSecPrice.toDoubleOrNull() ?: 0.0
                                haptics.confirm()
                                viewModel.updateItemPackaging(
                                    itemId = item.id,
                                    secondaryUnit = inputSecUnit.trim(),
                                    conversionFactor = if (fact > 0) fact else 1.0,
                                    secondaryPrice = price
                                )
                                showPackagingDialog = false
                            },
                            enabled = inputSecUnit.isNotBlank() && (inputFactor.toDoubleOrNull() ?: 0.0) > 0.0
                        ) {
                            Text("Save Packaging")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        SimpleConfirmDialog(
            title = "Delete item",
            message = "Delete “${item.name}”? This cannot be undone.",
            confirmText = "Delete",
            isDanger = true,
            onConfirm = {
                viewModel.deleteItem(item.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showEditPriceDialog) {
        var editPriceText by remember(item.id) {
            mutableStateOf(if (item.price > 0.0) (if (item.price % 1.0 == 0.0) item.price.toLong().toString() else item.price.toString()) else "")
        }
        AlertDialog(
            onDismissRequest = { showEditPriceDialog = false },
            title = { Text("Update Selling Price", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (item.cost > 0.0) {
                        Text(
                            text = "Purchase Cost: ₹${String.format(java.util.Locale.US, "%.2f", item.cost)}",
                            fontSize = 12.sp,
                            color = ext.warning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editPriceText,
                        onValueChange = { editPriceText = it },
                        label = { Text("New Selling Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (item.cost > 0.0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Quick Margin Shortcuts:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(10, 15, 20, 25).forEach { pct ->
                                val targetP = kotlin.math.round(item.cost * (1.0 + pct / 100.0))
                                OutlinedButton(
                                    onClick = {
                                        haptics.tick()
                                        editPriceText = targetP.toInt().toString()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+$pct%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = editPriceText.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                        haptics.confirm()
                        viewModel.updateItemSellingPrice(item.id, parsed)
                        showEditPriceDialog = false
                    }
                ) {
                    Text("Save Price")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPriceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
