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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.ImageModelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private data class BillingSubTabSpec(
    val index: Int,
    val label: String,
    val count: Int?,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun BillingHubScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalExtendedColors.current

    val selectedSubTab by viewModel.billingKhataSubTab.collectAsState()
    val quotations by viewModel.allQuotations.collectAsState()
    val gstBills by viewModel.allGstBills.collectAsState()
    val ledgerAccounts by viewModel.allLedgerAccounts.collectAsState()
    val dailyCashflow by viewModel.allDailyCashflow.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val activeFestivals by viewModel.activeFestivals.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val selectedLogo by viewModel.selectedLogo.collectAsState()
    val customLogoUrl by viewModel.customLogoUrl.collectAsState()
    val lowStockAlertsEnabled by viewModel.lowStockAlertsEnabled.collectAsState()
    val lowStockDefaultThreshold by viewModel.lowStockDefaultThreshold.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    val lowStockCount = remember(allItems, lowStockDefaultThreshold) {
        allItems.count { viewModel.appSettingsManager.isLowOrOutOfStock(it) }
    }

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

    val subTabs = listOf(
        BillingSubTabSpec(
            index = 0,
            label = "POS",
            count = null,
            selectedIcon = Icons.Filled.Bolt,
            unselectedIcon = Icons.Filled.Bolt,
            testTag = "billing_tab_pos"
        ),
        BillingSubTabSpec(
            index = 1,
            label = "Estimates",
            count = quotations.size,
            selectedIcon = Icons.Filled.Description,
            unselectedIcon = Icons.Outlined.Description,
            testTag = "billing_tab_estimates"
        ),
        BillingSubTabSpec(
            index = 2,
            label = "GST Bills",
            count = gstBills.size,
            selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
            unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
            testTag = "billing_tab_gst_bills"
        ),
        BillingSubTabSpec(
            index = 3,
            label = "Khata",
            count = ledgerAccounts.size,
            selectedIcon = Icons.Filled.AccountBalanceWallet,
            unselectedIcon = Icons.Outlined.AccountBalanceWallet,
            testTag = "billing_tab_khata"
        ),
        BillingSubTabSpec(
            index = 4,
            label = "Transactions",
            count = dailyCashflow.size,
            selectedIcon = Icons.Filled.SwapHoriz,
            unselectedIcon = Icons.Filled.SwapHoriz,
            testTag = "billing_tab_transactions"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Store Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
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
                        text = "Billing",
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
                        .testTag("billing_notifications_button"),
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

                // Store Profile Avatar
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
                        .testTag("billing_logo_avatar"),
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

        com.example.ui.components.FestivalHeaderPill(festivals = activeFestivals)

        // Unified 5-Tab Sub-Navigation Strip: POS | Estimates | GST Bills | Khata | Transactions
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(subTabs.size, key = { subTabs[it].index }) { i ->
                val spec = subTabs[i]
                val isSelected = selectedSubTab.coerceIn(0, 4) == spec.index
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                val iconTint = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    spec.index == 0 -> ext.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    tonalElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setBillingKhataSubTab(spec.index) }
                        .testTag(spec.testTag)
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) spec.selectedIcon else spec.unselectedIcon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = spec.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            if (spec.count != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = "${spec.count}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        // Body Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedSubTab.coerceIn(0, 4)) {
                0 -> QuickBillingPosScreen(
                    viewModel = viewModel,
                    isEmbeddedInHub = true
                )
                1 -> QuotationScreen(viewModel = viewModel)
                2 -> GstBillsTabContent(
                    viewModel = viewModel,
                    onOpenGstCalculator = { viewModel.openGstCalculator() },
                    onOpenEstimates = { viewModel.setBillingKhataSubTab(1) },
                    onOpenKhata = { viewModel.setBillingKhataSubTab(3) }
                )
                3 -> LedgerScreen(viewModel = viewModel)
                else -> TransactionsScreen(
                    viewModel = viewModel,
                    isEmbeddedInHub = true
                )
            }
        }
    }
}
