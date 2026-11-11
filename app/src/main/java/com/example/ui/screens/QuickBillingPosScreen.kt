package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.QuotationLineItem
import com.example.ui.StockViewModel
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.SuccessGreen
import com.example.util.InvoicePrintManager
import com.example.util.ItemSearchMatcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Cart Line Item representing an item currently staged in Quick Billing POS.
 */
data class PosCartItem(
    val item: Item,
    var qty: Double = 1.0,
    var customPrice: Double = item.price,
    var customTaxRate: Double = 18.0
) {
    val total: Double get() = qty * customPrice
    val totalCost: Double get() = qty * item.cost
    val margin: Double get() = total - totalCost
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBillingPosScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val allItems by viewModel.allItems.collectAsState()
    val prefilledQuotation by viewModel.prefilledQuotationForGst.collectAsState()

    // Mode Toggle: POS Grid vs Detailed Manual GST Invoice Table
    var isDetailedGstMode by remember { mutableStateOf(false) }

    if (isDetailedGstMode) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 Detailed GST Table Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { isDetailedGstMode = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⚡ Switch to POS Grid", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            GstCalculatorScreen(viewModel = viewModel)
        }
        return
    }

    BackHandler {
        viewModel.navigateBack()
    }

    // POS State
    val cartItems = remember { mutableStateListOf<PosCartItem>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    var customerName by remember { mutableStateOf("Cash Customer") }
    var customerPhone by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var taxRateSelected by remember { mutableDoubleStateOf(18.0) }

    var showCartDrawer by remember { mutableStateOf(false) }
    var showCashCheckoutDialog by remember { mutableStateOf(false) }
    var showUpiCheckoutDialog by remember { mutableStateOf(false) }
    var showReceiptSuccessDialog by remember { mutableStateOf(false) }
    var lastSavedBill by remember { mutableStateOf<GstBillRecord?>(null) }
    var showThermalDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showCustomQtyDialogFor by remember { mutableStateOf<PosCartItem?>(null) }

    // Pre-populate if converted from Estimate
    LaunchedEffect(prefilledQuotation) {
        val quote = prefilledQuotation
        if (quote != null) {
            customerName = quote.customerName.ifBlank { "Cash Customer" }
            customerPhone = quote.customerPhone
            taxRateSelected = quote.taxPercent
            discountText = quote.discount.toString()

            val parsedItems = InvoicePrintManager.parseLineItems(quote.itemsJson)
            cartItems.clear()
            parsedItems.forEach { qItem ->
                val matched = allItems.find { it.id == qItem.itemId || it.name.equals(qItem.name, ignoreCase = true) }
                val fallbackItem = matched ?: Item(
                    id = qItem.itemId ?: UUID.randomUUID().toString(),
                    name = qItem.name,
                    price = qItem.unitPrice,
                    cost = qItem.costPrice,
                    unit = qItem.unit,
                    type = qItem.type
                )
                cartItems.add(
                    PosCartItem(
                        item = fallbackItem,
                        qty = qItem.qty,
                        customPrice = qItem.unitPrice,
                        customTaxRate = quote.taxPercent
                    )
                )
            }
        }
    }

    // Category list derived from database items
    val distinctCategories = remember(allItems) {
        val fromDb = allItems.map { it.type.trim() }.filter { it.isNotBlank() }
        val defaults = listOf("PLUMBING", "ELECTRICAL", "PAINTS", "TOOLS", "HARDWARE", "SANITARY")
        listOf("ALL") + (defaults + fromDb).distinct().sorted()
    }

    // Filter items based on active category and search
    val filteredItems = remember(searchQuery, selectedCategory, allItems) {
        val q = searchQuery.trim()
        val curCat = selectedCategory.trim()
        val base = if (curCat.equals("ALL", ignoreCase = true)) {
            allItems
        } else {
            allItems.filter { it.type.trim().equals(curCat, ignoreCase = true) }
        }

        if (q.isBlank()) {
            base
        } else {
            // First search within selected category
            val inCatMatches = base.mapNotNull { item ->
                val score = ItemSearchMatcher.matchScore(item, q)
                if (score > 0) Pair(item, score) else null
            }.sortedByDescending { it.second }.map { it.first }

            if (inCatMatches.isNotEmpty()) {
                inCatMatches
            } else {
                // Fallback across all categories if user searched for another product
                allItems.mapNotNull { item ->
                    val score = ItemSearchMatcher.matchScore(item, q)
                    if (score > 0) Pair(item, score) else null
                }.sortedByDescending { it.second }.map { it.first }
            }
        }
    }

    // Financial Totals
    val totalCartQty = cartItems.sumOf { it.qty }
    val cartSubtotal = cartItems.sumOf { it.total }
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val taxableTotal = (cartSubtotal - discount).coerceAtLeast(0.0)
    val taxAmount = (taxableTotal * (taxRateSelected / 100.0))
    val grandTotal = taxableTotal + taxAmount
    val totalCost = cartItems.sumOf { it.totalCost }
    val estimatedProfit = (taxableTotal - totalCost)

    // Voice Search Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = ItemSearchMatcher.normalizeVoiceInput(spoken)
            }
        }
    }

    // Helper functions for cart
    fun addItemToCart(item: Item) {
        val existingIndex = cartItems.indexOfFirst { it.item.id == item.id }
        if (existingIndex >= 0) {
            val cur = cartItems[existingIndex]
            cartItems[existingIndex] = cur.copy(qty = cur.qty + 1.0)
        } else {
            cartItems.add(PosCartItem(item = item, qty = 1.0, customPrice = item.price, customTaxRate = taxRateSelected))
        }
    }

    fun decrementItemInCart(item: Item) {
        val existingIndex = cartItems.indexOfFirst { it.item.id == item.id }
        if (existingIndex >= 0) {
            val cur = cartItems[existingIndex]
            if (cur.qty > 1.0) {
                cartItems[existingIndex] = cur.copy(qty = cur.qty - 1.0)
            } else {
                cartItems.removeAt(existingIndex)
            }
        }
    }

    fun finalizeAndSaveBill(paymentMode: String) {
        if (cartItems.isEmpty()) {
            viewModel.showToast("Cart is empty! Add products to bill.")
            return
        }
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val invoiceNo = "POS-${System.currentTimeMillis().toString().takeLast(6)}"
        val invoiceDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val gstLineItems = cartItems.map { c ->
            val tAmt = (c.qty * c.customPrice) * (c.customTaxRate / 100.0)
            GstBillLineItem(
                id = c.item.id,
                name = c.item.name,
                hsn = c.item.code.ifBlank { "8481" },
                qty = c.qty,
                rate = c.customPrice,
                taxRatePercent = c.customTaxRate,
                taxableAmount = c.qty * c.customPrice,
                taxAmount = tAmt,
                totalAmount = (c.qty * c.customPrice) + tAmt,
                unit = c.item.unit.ifBlank { "pcs" },
                subtitle = "Type: ${c.item.type}",
                imageUrl = c.item.imageUrl
            )
        }

        val billRecord = GstBillRecord(
            id = UUID.randomUUID().toString(),
            invoiceNo = invoiceNo,
            invoiceDate = invoiceDate,
            customerName = customerName.ifBlank { "Cash Customer" },
            customerPhone = customerPhone.trim(),
            customerGstin = "",
            customerAddress = "",
            storeGstin = "27AABCS1234F1Z5",
            isInterState = false,
            itemsJson = InvoicePrintManager.gstItemsToJson(gstLineItems),
            taxableTotal = taxableTotal,
            totalTaxAmount = taxAmount,
            grandTotal = grandTotal,
            transporterId = paymentMode, // Tag payment mode
            vehicleNo = "",
            quotationId = prefilledQuotation?.id,
            quotationNo = prefilledQuotation?.quotationNo,
            pdfPath = null,
            status = "Paid ($paymentMode)",
            createdAt = nowIso
        )

        viewModel.saveGstBill(billRecord)
        if (prefilledQuotation != null) {
            viewModel.updateQuotationStatus(prefilledQuotation!!, "Converted")
        }

        lastSavedBill = billRecord
        showCartDrawer = false
        showCashCheckoutDialog = false
        showUpiCheckoutDialog = false
        showReceiptSuccessDialog = true
        viewModel.showToast("✅ Bill #$invoiceNo saved via $paymentMode!")
    }

    var showDiscountDialog by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }

    // Quick Discount Dialog
    if (showDiscountDialog) {
        var tempDiscount by remember { mutableStateOf(discountText) }
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("🏷️ Apply POS Bill Discount", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter discount amount in ₹ to deduct from current subtotal (₹${String.format(Locale.US, "%.2f", cartSubtotal)}):", fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempDiscount,
                        onValueChange = { tempDiscount = it },
                        label = { Text("Discount Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0", "50", "100", "200").forEach { preset ->
                            OutlinedButton(
                                onClick = { tempDiscount = preset },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text("₹$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        discountText = tempDiscount.toDoubleOrNull()?.toString() ?: "0"
                        showDiscountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Apply", color = Color(0xFF022C1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick Customer Dialog
    if (showCustomerDialog) {
        var tempName by remember { mutableStateOf(customerName) }
        var tempPhone by remember { mutableStateOf(customerPhone) }
        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text("👤 Customer Details", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Customer / Contractor Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempPhone,
                        onValueChange = { tempPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        customerName = tempName.ifBlank { "Cash Customer" }
                        customerPhone = tempPhone.trim()
                        showCustomerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Save", color = Color(0xFF022C1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0A0D14),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Tactical Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.navigateBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "⚡ TACTICAL POS REGISTER",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                                        border = BorderStroke(0.8.dp, Color(0xFF10B981)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${allItems.size} items in catalog • 1-Tap Fast Billing",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Barcode Scanner button
                            IconButton(
                                onClick = { showBarcodeDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF121620), CircleShape)
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan barcode",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Mode toggle to Detailed Table
                            OutlinedButton(
                                onClick = { isDetailedGstMode = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF121620)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.TableView, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GST Table", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Tactical Search & Voice Field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search products, barcodes, sizes...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak product name...")
                                            }
                                            try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0E131C),
                                unfocusedContainerColor = Color(0xFF0E131C),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF1E2638),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Tactical Category Filter Pills matching mockup
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(distinctCategories) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            val catIcon = when {
                                cat.equals("ALL", ignoreCase = true) -> "📦"
                                cat.contains("Plumb", true) -> "💧"
                                cat.contains("Elect", true) -> "⚡"
                                cat.contains("Paint", true) -> "🎨"
                                cat.contains("Tool", true) -> "🛠️"
                                cat.contains("Hardware", true) -> "🔩"
                                cat.contains("Sanitary", true) -> "🚿"
                                else -> "🏷️"
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF10141E),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF1E2638)),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(text = catIcon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cat,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF022C1A) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E2638))
                }
            }
        },
        bottomBar = {
            // Tactical Express Bottom Cart Dock matching Mockup
            Surface(
                color = Color(0xFF080C14),
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Quick Action Row: Discount, Customer, Clear Cart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discount Badge Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF121620),
                            border = BorderStroke(0.8.dp, if (discount > 0) Color(0xFF10B981) else Color(0xFF1E2638)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDiscountDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (discount > 0) "🏷️ -₹${discount.toInt()}" else "🏷️ Discount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (discount > 0) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }

                        // Customer Badge Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF121620),
                            border = BorderStroke(0.8.dp, if (customerName != "Cash Customer") Color(0xFF10B981) else Color(0xFF1E2638)),
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable { showCustomerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "👤 ${customerName.take(12)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customerName != "Cash Customer") Color(0xFF10B981) else Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Clear Cart Button
                        if (cartItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1F1216),
                                border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { cartItems.clear() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("CLEAR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }

                    // Tactical Split Checkout Dock
                    // Tactical Split Checkout Dock matching mockup
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable { showCartDrawer = true }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "SUBTOTAL: ₹${String.format(Locale.US, "%.2f", cartSubtotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "TAX: ₹${String.format(Locale.US, "%.2f", taxAmount)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // View Cart Mini Tag
                            Surface(
                                color = Color(0xFF121620),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                                modifier = Modifier.clickable { showCartDrawer = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${cartItems.size}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                                }
                            }

                            // Full Glowing Emerald Checkout Button matching mockup
                            Button(
                                onClick = {
                                    if (cartItems.isEmpty()) {
                                        viewModel.showToast("Please add items to cart first!")
                                    } else {
                                        showCashCheckoutDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color(0xFF022C1A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(46.dp)
                                    .padding(start = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "CHECKOUT - ₹${String.format(Locale.US, "%.2f", grandTotal)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${cartItems.size} Items →",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF022C1A).copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingVals ->
        // Main POS Touch Grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "No inventory items in $selectedCategory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { searchQuery = ""; selectedCategory = "ALL" }) {
                        Text("Reset Filters")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { dbItem ->
                        val cartEntry = cartItems.find { it.item.id == dbItem.id }
                        val inCartQty = cartEntry?.qty ?: 0.0

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (inCartQty > 0) Color(0xFF072116) else Color(0xFF0E131C)
                            ),
                            border = if (inCartQty > 0) BorderStroke(1.5.dp, Color(0xFF10B981))
                            else BorderStroke(1.dp, Color(0xFF1E2638)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addItemToCart(dbItem) }
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                // Top Header Row: SKU tag & Cart multiplier badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${dbItem.code.ifBlank { dbItem.id.takeLast(4) }}",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                    if (inCartQty > 0) {
                                        Text(
                                            text = "x${inCartQty.toInt()}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Product Image or Category Icon Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF141923)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!dbItem.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = dbItem.imageUrl,
                                            contentDescription = dbItem.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = when {
                                                    dbItem.type.contains("Plumb", true) -> "💧"
                                                    dbItem.type.contains("Elect", true) -> "⚡"
                                                    dbItem.type.contains("Paint", true) -> "🎨"
                                                    dbItem.type.contains("Tool", true) -> "🛠️"
                                                    dbItem.type.contains("Hardware", true) -> "🔩"
                                                    else -> "📦"
                                                },
                                                fontSize = 24.sp
                                            )
                                            Text(
                                                text = dbItem.type.ifBlank { "General" }.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF64748B),
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }

                                    // Tactical Stock Fraction Badge at top-right
                                    Surface(
                                        color = if (dbItem.qty > 0) Color(0xFF000000).copy(alpha = 0.85f) else Color(0xFF3B0707),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.6.dp, if (dbItem.qty > 0) Color(0xFF10B981) else Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = if (dbItem.qty > 0) "🟢 ${dbItem.qty.toInt()} ${dbItem.unit}" else "🔴 0 OUT",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (dbItem.qty > 0) Color(0xFF10B981) else Color(0xFFFCA5A5),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Product Name
                                Text(
                                    text = dbItem.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFFF3F4F6)
                                )

                                if (dbItem.size.isNotBlank() || dbItem.code.isNotBlank()) {
                                    Text(
                                        text = listOf(dbItem.size, dbItem.code).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Price & Add/Counter Control Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                val entry = cartItems.find { it.item.id == dbItem.id }
                                                if (entry != null) {
                                                    showCustomQtyDialogFor = entry
                                                } else {
                                                    val newEntry = PosCartItem(item = dbItem, qty = 1.0, customPrice = dbItem.price, customTaxRate = taxRateSelected)
                                                    cartItems.add(newEntry)
                                                    showCustomQtyDialogFor = newEntry
                                                }
                                            }
                                            .padding(1.dp)
                                    ) {
                                        Text(
                                            text = "₹${String.format(Locale.US, "%.2f", dbItem.price)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF10B981),
                                            letterSpacing = 0.3.sp
                                        )
                                        if (dbItem.cost > 0.0) {
                                            Text(
                                                text = "Cost ₹${dbItem.cost.toInt()}",
                                                fontSize = 9.sp,
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Tactical Stepper: [-] [qty] [+] or [+ ADD]
                                    if (inCartQty > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 2.dp, vertical = 1.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable { decrementItemInCart(dbItem) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrement", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                            Text(
                                                text = "${inCartQty.toInt()}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = Color(0xFF10B981),
                                                modifier = Modifier
                                                    .clickable {
                                                        if (cartEntry != null) showCustomQtyDialogFor = cartEntry
                                                    }
                                                    .padding(horizontal = 4.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable { addItemToCart(dbItem) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increment", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFF10B981),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .height(26.dp)
                                                .clickable { addItemToCart(dbItem) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF022C1A), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("ADD", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF022C1A))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Full Cart Breakdown Drawer
    if (showCartDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showCartDrawer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛒 Active POS Cart (${cartItems.size} items)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { cartItems.clear() }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Cart Line Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(cartItems) { cartItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCustomQtyDialogFor = cartItem }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cartItem.item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "₹${cartItem.customPrice} × ${cartItem.qty} = ₹${String.format(Locale.US, "%.2f", cartItem.total)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (cartItem.item.cost > 0.0) {
                                        Text(
                                            text = "(Cost: ₹${cartItem.item.cost.toInt()})",
                                            fontSize = 11.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }

                            // Stepper (- / +)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { decrementItemInCart(cartItem.item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "${cartItem.qty.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { addItemToCart(cartItem.item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Customer Name / Phone inputs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Financial Summary Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%.2f", cartSubtotal)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (taxAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GST ($taxRateSelected%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+₹${String.format(Locale.US, "%.2f", taxAmount)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (totalCost > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Est. Cost & Margin", fontSize = 11.sp, color = Color(0xFFB45309))
                                Text("Cost: ₹${totalCost.toInt()} • Margin: +₹${estimatedProfit.toInt()}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF15803D))
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text("₹${String.format(Locale.US, "%.2f", grandTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Checkout buttons inside drawer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showCartDrawer = false
                            showCashCheckoutDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("₹ CASH CHECKOUT", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showCartDrawer = false
                            showUpiCheckoutDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("₹ UPI CHECKOUT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 1-Tap Cash Checkout Confirmation Dialog
    if (showCashCheckoutDialog) {
        var cashTenderedText by remember { mutableStateOf(grandTotal.toInt().toString()) }
        val cashTendered = cashTenderedText.toDoubleOrNull() ?: grandTotal
        val changeReturn = (cashTendered - grandTotal).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showCashCheckoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF0F766E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cash Payment", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Total Amount Due: ₹${String.format(Locale.US, "%.2f", grandTotal)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cashTenderedText,
                        onValueChange = { cashTenderedText = it },
                        label = { Text("Cash Received (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Tender denominations
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(500, 1000, 2000).forEach { note ->
                            if (note >= grandTotal) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clickable { cashTenderedText = note.toString() }
                                        .padding(2.dp)
                                ) {
                                    Text("₹$note", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Change to Return:", fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            Text("₹${String.format(Locale.US, "%.2f", changeReturn)}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF166534))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { finalizeAndSaveBill("Cash") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                ) {
                    Text("Complete & Print Receipt", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashCheckoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 1-Tap UPI Checkout Dialog with QR mockup and App links
    if (showUpiCheckoutDialog) {
        AlertDialog(
            onDismissRequest = { showUpiCheckoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UPI Payment (QR)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total to Collect:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${String.format(Locale.US, "%.2f", grandTotal)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    // QR Display Placeholder
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color(0xFF2563EB)),
                        modifier = Modifier.size(160.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Scan via GPay / PhonePe", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("merchant@upi", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ask customer to scan QR code and pay.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { finalizeAndSaveBill("UPI") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Payment Received", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiCheckoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Receipt Success Dialog after Payment
    if (showReceiptSuccessDialog && lastSavedBill != null) {
        val bill = lastSavedBill!!
        AlertDialog(
            onDismissRequest = {
                showReceiptSuccessDialog = false
                cartItems.clear()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bill Completed!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Invoice #${bill.invoiceNo}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Amount Paid: ₹${String.format(Locale.US, "%.2f", bill.grandTotal)} (${bill.status})", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("Customer: ${bill.customerName}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Inventory stock levels have been automatically updated.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReceiptSuccessDialog = false
                        showThermalDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print Thermal Receipt")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showReceiptSuccessDialog = false
                        cartItems.clear()
                    }
                ) {
                    Text("Start Next Bill")
                }
            }
        )
    }

    // Thermal Print Dialog Integration
    if (showThermalDialog && lastSavedBill != null) {
        val bill = lastSavedBill!!
        val parsedLines = InvoicePrintManager.parseGstLineItems(bill.itemsJson).map {
            QuotationLineItem(
                name = it.name,
                qty = it.qty,
                unitPrice = it.rate,
                total = it.totalAmount,
                unit = it.unit
            )
        }
        ThermalPrintDialog(
            title = "SALE RECEIPT",
            refNo = bill.invoiceNo,
            date = bill.invoiceDate,
            customerName = bill.customerName,
            items = parsedLines,
            subtotal = bill.taxableTotal,
            discount = 0.0,
            grandTotal = bill.grandTotal,
            onDismiss = {
                showThermalDialog = false
                cartItems.clear()
            },
            onPrintSuccess = {
                showThermalDialog = false
                cartItems.clear()
                viewModel.showToast("Receipt printed successfully!")
            }
        )
    }

    // Quick Barcode / SKU dialog
    if (showBarcodeDialog) {
        var barcodeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBarcodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan / Enter Barcode")
                }
            },
            text = {
                Column {
                    Text("Scan or enter product barcode, SKU or code:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = barcodeInput,
                        onValueChange = { barcodeInput = it },
                        placeholder = { Text("e.g. 890123456789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = barcodeInput.trim()
                        val matched = allItems.find { it.barcode.equals(trimmed, ignoreCase = true) || it.code.equals(trimmed, ignoreCase = true) }
                        if (matched != null) {
                            addItemToCart(matched)
                            viewModel.showToast("Added ${matched.name} to cart!")
                            showBarcodeDialog = false
                        } else {
                            viewModel.showToast("No product found with barcode $trimmed")
                        }
                    }
                ) {
                    Text("Add to Cart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBarcodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Quantity & Selling Price dialog
    if (showCustomQtyDialogFor != null) {
        val target = showCustomQtyDialogFor!!
        var customQtyText by remember(target.item.id) {
            mutableStateOf(if (target.qty % 1.0 == 0.0) target.qty.toLong().toString() else target.qty.toString())
        }
        var customPriceText by remember(target.item.id) {
            val p = target.customPrice
            mutableStateOf(if (p % 1.0 == 0.0) p.toLong().toString() else String.format(Locale.US, "%.2f", p))
        }
        var updateMasterPriceInDb by remember(target.item.id) { mutableStateOf(false) }

        val currentEnteredPrice = customPriceText.trim().toDoubleOrNull() ?: target.customPrice
        val currentEnteredQty = customQtyText.trim().toDoubleOrNull() ?: target.qty
        val unitMargin = currentEnteredPrice - target.item.cost

        AlertDialog(
            onDismissRequest = { showCustomQtyDialogFor = null },
            title = {
                Column {
                    Text(target.item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "Customize Quantity & Price in POS Bill",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Quantity Section
                    Text("Quantity (${target.item.unit.ifBlank { "pcs" }}):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customQtyText,
                        onValueChange = { customQtyText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Quick add buttons with flexible weight and compact padding to prevent overflow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 20, 50).forEach { num ->
                            OutlinedButton(
                                onClick = {
                                    val cur = customQtyText.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                                    val next = cur + num
                                    customQtyText = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+$num", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Selling Price Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selling Price (₹):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (target.item.cost > 0.0) {
                            Text(
                                text = "Cost: ₹${String.format(Locale.US, "%.0f", target.item.cost)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customPriceText,
                        onValueChange = { customPriceText = it },
                        label = { Text("Rate per ${target.item.unit.ifBlank { "pcs" }}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            if (target.item.cost > 0.0 && currentEnteredPrice > 0.0) {
                                val marginPct = ((unitMargin / target.item.cost) * 100).toInt()
                                Text(
                                    text = if (unitMargin >= 0) "Profit Margin: +₹${String.format(Locale.US, "%.2f", unitMargin)} (+$marginPct%)"
                                    else "Margin: -₹${String.format(Locale.US, "%.2f", -unitMargin)} ($marginPct%)",
                                    color = if (unitMargin >= 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Master Inventory DB Update Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { updateMasterPriceInDb = !updateMasterPriceInDb }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = updateMasterPriceInDb,
                            onCheckedChange = { updateMasterPriceInDb = it },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update item selling price in inventory (master)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Line item subtotal preview
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Line Total:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", currentEnteredQty * currentEnteredPrice)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = customQtyText.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 1.0
                        val p = customPriceText.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: target.customPrice
                        val idx = cartItems.indexOfFirst { it.item.id == target.item.id }
                        if (idx >= 0) {
                            if (q > 0) {
                                cartItems[idx] = target.copy(qty = q, customPrice = p)
                            } else {
                                cartItems.removeAt(idx)
                            }
                        }
                        if (updateMasterPriceInDb && p > 0.0) {
                            viewModel.updateItemSellingPrice(target.item.id, p)
                        }
                        showCustomQtyDialogFor = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val idx = cartItems.indexOfFirst { it.item.id == target.item.id }
                        if (idx >= 0) cartItems.removeAt(idx)
                        showCustomQtyDialogFor = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
