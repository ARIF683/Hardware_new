package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Item
import com.example.data.model.SupplierPurchaseRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun SupplierPriceComparisonScreen(
    preselectedItemId: String? = null,
    viewModel: StockViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allItems by viewModel.allItems.collectAsState()
    val allPurchases by viewModel.allSupplierPurchases.collectAsState()

    var selectedItemId by remember(preselectedItemId) {
        mutableStateOf(preselectedItemId ?: allItems.firstOrNull()?.id)
    }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemPickerExpanded by remember { mutableStateOf(false) }

    val currentItem = remember(selectedItemId, allItems) {
        allItems.find { it.id == selectedItemId } ?: allItems.firstOrNull()
    }

    // Filter purchases for selected item or all
    val relevantPurchases = remember(currentItem, allPurchases, searchQuery) {
        val list = if (currentItem != null) {
            allPurchases.filter { it.itemId == currentItem.id }
        } else {
            allPurchases
        }
        if (searchQuery.isBlank()) {
            list.sortedBy { it.rate }
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            list.filter {
                it.supplierName.lowercase(Locale.ROOT).contains(q) ||
                it.itemName.lowercase(Locale.ROOT).contains(q) ||
                it.invoiceNo.lowercase(Locale.ROOT).contains(q)
            }.sortedBy { it.rate }
        }
    }

    val lowestRate = remember(relevantPurchases) {
        relevantPurchases.minOfOrNull { it.rate }
    }
    val highestRate = remember(relevantPurchases) {
        relevantPurchases.maxOfOrNull { it.rate }
    }
    val avgRate = remember(relevantPurchases) {
        if (relevantPurchases.isNotEmpty()) {
            relevantPurchases.map { it.rate }.average()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- Top Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Supplier Price Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rate history, vendor quotes & cost trends",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Quote", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Item Selector Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SELECT ITEM TO COMPARE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { itemPickerExpanded = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentItem?.name ?: "All Items (Global Comparison)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (currentItem != null) {
                                        Text(
                                            text = "Current In-Store Cost: ₹${"%.2f".format(currentItem.cost)} | Selling: ₹${"%.2f".format(currentItem.price)} / ${currentItem.unit}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Item",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = itemPickerExpanded,
                                onDismissRequest = { itemPickerExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("⚡ All Items / Global Quotes", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedItemId = null
                                        itemPickerExpanded = false
                                    }
                                )
                                HorizontalDivider()
                                allItems.take(50).forEach { itm ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(itm.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "Cost: ₹${itm.cost} • Stock: ${itm.qty.toInt()} ${itm.unit}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedItemId = itm.id
                                            itemPickerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- KPI Overview Cards ---
            if (relevantPurchases.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Lowest Rate Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = SuccessGreen.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lowest Quote", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${"%.2f".format(lowestRate ?: 0.0)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                val bestSupplier = relevantPurchases.minByOrNull { it.rate }?.supplierName ?: ""
                                Text(
                                    text = bestSupplier,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Average Rate Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Market Avg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${"%.2f".format(avgRate ?: 0.0)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${relevantPurchases.size} Quotes Logged",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // In-Store Cost vs Lowest Spread
                        if (currentItem != null && lowestRate != null) {
                            val diff = currentItem.cost - lowestRate
                            val isCheaper = diff > 0.0
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCheaper) BrandAmber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (isCheaper) BrandAmber.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isCheaper) "Potential Saving" else "Current Cost",
                                        fontSize = 11.sp,
                                        color = if (isCheaper) BrandAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isCheaper) "Save ₹${"%.2f".format(diff)}" else "₹${"%.2f".format(currentItem.cost)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCheaper) BrandAmber else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCheaper) "per unit vs store" else "store baseline",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Search Field ---
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Search supplier name, invoice, or item...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true
                )
            }

            // --- List of Supplier Quotes ---
            if (relevantPurchases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Supplier Quotes Logged Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add purchase rates and quotes from local hardware wholesalers and manufacturers to track rates over time and find the best price.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add First Supplier Quote")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "RATES FROM SUPPLIERS (${relevantPurchases.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                }

                items(relevantPurchases, key = { it.id }) { purchase ->
                    val isLowest = lowestRate != null && purchase.rate <= lowestRate
                    SupplierQuoteCard(
                        record = purchase,
                        isLowest = isLowest,
                        currentCost = currentItem?.cost,
                        onApplyAsCost = {
                            if (currentItem != null) {
                                viewModel.saveItem(
                                    id = currentItem.id,
                                    name = currentItem.name,
                                    code = currentItem.code,
                                    barcode = currentItem.barcode,
                                    type = currentItem.type,
                                    brand = currentItem.brand,
                                    size = currentItem.size,
                                    unit = currentItem.unit,
                                    mrp = currentItem.mrp,
                                    cost = purchase.rate,
                                    price = currentItem.price,
                                    low = currentItem.low,
                                    aliases = currentItem.aliases,
                                    openingQty = currentItem.qty,
                                    imageUrl = currentItem.imageUrl,
                                    nfcTagId = currentItem.nfcTagId,
                                    shelfLocation = currentItem.shelfLocation,
                                    secondaryUnit = currentItem.secondaryUnit,
                                    conversionFactor = currentItem.conversionFactor,
                                    secondaryPrice = currentItem.secondaryPrice
                                )
                                viewModel.showToast("Updated store cost price to ₹${purchase.rate}")
                            }
                        },
                        onDelete = {
                            viewModel.deleteSupplierPurchase(purchase.id)
                        },
                        onCallSupplier = {
                            if (purchase.supplierPhone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${purchase.supplierPhone}"))
                                context.startActivity(intent)
                            } else {
                                viewModel.showToast("No phone number recorded for ${purchase.supplierName}")
                            }
                        },
                        onWhatsApp = {
                            if (purchase.supplierPhone.isNotBlank()) {
                                val cleanPhone = purchase.supplierPhone.replace(Regex("[^0-9]"), "")
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=Hello%20${Uri.encode(purchase.supplierName)},%20inquiring%20about%20rate%20for%20${Uri.encode(purchase.itemName)}")
                                )
                                context.startActivity(intent)
                            } else {
                                viewModel.showToast("No phone number recorded for ${purchase.supplierName}")
                            }
                        }
                    )
                }
            }
        }
    }

    // --- Add Supplier Quote Dialog ---
    if (showAddDialog) {
        AddSupplierQuoteDialog(
            preselectedItem = currentItem,
            allItems = allItems,
            onDismiss = { showAddDialog = false },
            onSave = { record, updateItemCost ->
                viewModel.recordSupplierPurchase(record)
                if (updateItemCost && currentItem != null) {
                    viewModel.saveItem(
                        id = currentItem.id,
                        name = currentItem.name,
                        code = currentItem.code,
                        barcode = currentItem.barcode,
                        type = currentItem.type,
                        brand = currentItem.brand,
                        size = currentItem.size,
                        unit = currentItem.unit,
                        mrp = currentItem.mrp,
                        cost = record.rate,
                        price = currentItem.price,
                        low = currentItem.low,
                        aliases = currentItem.aliases,
                        openingQty = currentItem.qty,
                        imageUrl = currentItem.imageUrl,
                        nfcTagId = currentItem.nfcTagId,
                        shelfLocation = currentItem.shelfLocation,
                        secondaryUnit = currentItem.secondaryUnit,
                        conversionFactor = currentItem.conversionFactor,
                        secondaryPrice = currentItem.secondaryPrice
                    )
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SupplierQuoteCard(
    record: SupplierPurchaseRecord,
    isLowest: Boolean,
    currentCost: Double?,
    onApplyAsCost: () -> Unit,
    onDelete: () -> Unit,
    onCallSupplier: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowest) SuccessGreen.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isLowest) 1.5.dp else 1.dp,
            color = if (isLowest) SuccessGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Supplier Name & Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.supplierName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLowest) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = SuccessGreen,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "LOWEST",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    if (record.itemName.isNotBlank()) {
                        Text(
                            text = record.itemName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (record.invoiceNo.isNotBlank() || record.purchaseDate.isNotBlank()) {
                        Text(
                            text = listOfNotNull(
                                if (record.invoiceNo.isNotBlank()) "Inv #${record.invoiceNo}" else null,
                                if (record.purchaseDate.isNotBlank()) record.purchaseDate else null
                            ).joinToString(" • "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Rate Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${"%.2f".format(record.rate)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = if (isLowest) SuccessGreen else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "per ${record.unit}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Details row: Qty, Payment Terms, Tax
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Qty: ${record.qty.toInt()} ${record.unit}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (record.paymentStatus.isNotBlank()) {
                        Surface(
                            color = when (record.paymentStatus.lowercase(Locale.ROOT)) {
                                "paid" -> SuccessGreen.copy(alpha = 0.15f)
                                "credit", "pending" -> BrandAmber.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = record.paymentStatus,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                color = when (record.paymentStatus.lowercase(Locale.ROOT)) {
                                    "paid" -> SuccessGreen
                                    "credit", "pending" -> BrandAmber
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                // Action icons: Call, WhatsApp, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.supplierPhone.isNotBlank()) {
                        IconButton(onClick = onCallSupplier, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onWhatsApp, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Message, contentDescription = "WhatsApp", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (record.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📝 ${record.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Apply as store cost
            if (currentCost != null && currentCost != record.rate) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onApplyAsCost,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PriceCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as Store Cost Price (₹${"%.2f".format(record.rate)})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSupplierQuoteDialog(
    preselectedItem: Item?,
    allItems: List<Item>,
    onDismiss: () -> Unit,
    onSave: (SupplierPurchaseRecord, Boolean) -> Unit
) {
    var selectedItem by remember { mutableStateOf(preselectedItem) }
    var itemPickerExpanded by remember { mutableStateOf(false) }

    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var purchaseDate by remember { mutableStateOf(today) }

    var qtyText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(selectedItem?.unit ?: "pcs") }
    var rateText by remember { mutableStateOf(selectedItem?.cost?.toString() ?: "") }
    var paymentStatus by remember { mutableStateOf("Paid") }
    var notes by remember { mutableStateOf("") }
    var updateItemCost by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Add Supplier Rate Quote",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Record purchase price from vendor",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Item Selector
                Box {
                    OutlinedTextField(
                        value = selectedItem?.name ?: "Tap to choose item",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hardware Item *") },
                        trailingIcon = {
                            IconButton(onClick = { itemPickerExpanded = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { itemPickerExpanded = true }
                    )
                    DropdownMenu(
                        expanded = itemPickerExpanded,
                        onDismissRequest = { itemPickerExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        allItems.take(40).forEach { itm ->
                            DropdownMenuItem(
                                text = { Text(itm.name, fontSize = 13.sp) },
                                onClick = {
                                    selectedItem = itm
                                    unit = itm.unit
                                    if (rateText.isBlank()) rateText = itm.cost.toString()
                                    itemPickerExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Supplier Name & Phone
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("Supplier / Dealer Name *") },
                    placeholder = { Text("e.g. Metro Pipe Fittings, Balaji Hardware") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = supplierPhone,
                        onValueChange = { supplierPhone = it },
                        label = { Text("Phone / WhatsApp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = invoiceNo,
                        onValueChange = { invoiceNo = it },
                        label = { Text("Invoice / Quote #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Rate & Qty
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Rate / Unit (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Payment Status Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Paid", "Pending", "Credit").forEach { status ->
                        val selected = paymentStatus == status
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { paymentStatus = status }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = status,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g. cash discount, freight included)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetItem = selectedItem
                            val rate = rateText.toDoubleOrNull() ?: 0.0
                            val qty = qtyText.toDoubleOrNull() ?: 1.0
                            if (targetItem != null && supplierName.isNotBlank() && rate > 0.0) {
                                val record = SupplierPurchaseRecord(
                                    id = UUID.randomUUID().toString(),
                                    itemId = targetItem.id,
                                    itemName = targetItem.name,
                                    supplierName = supplierName.trim(),
                                    supplierPhone = supplierPhone.trim(),
                                    invoiceNo = invoiceNo.trim(),
                                    purchaseDate = purchaseDate,
                                    qty = qty,
                                    unit = unit.ifBlank { targetItem.unit },
                                    rate = rate,
                                    mrp = targetItem.mrp,
                                    totalAmount = rate * qty,
                                    paymentStatus = paymentStatus,
                                    notes = notes.trim(),
                                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                                )
                                onSave(record, updateItemCost)
                            }
                        },
                        enabled = supplierName.isNotBlank() && (rateText.toDoubleOrNull() ?: 0.0) > 0.0 && selectedItem != null,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Rate Quote")
                    }
                }
            }
        }
    }
}
