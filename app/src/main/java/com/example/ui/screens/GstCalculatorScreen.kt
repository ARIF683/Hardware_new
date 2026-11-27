package com.example.ui.screens

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.StockViewModel
import com.example.ui.components.SavedPaymentQrCard
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateTimeHelper
import com.example.util.InvoicePrintManager
import com.example.util.rememberAppHaptics
import androidx.compose.ui.platform.testTag
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

data class GstInvoiceRow(
    val id: String,
    var itemName: String,
    var hsnCode: String,
    var qty: Double,
    var rate: Double,
    var taxRatePercent: Double, // 0.0, 5.0, 12.0, 18.0, 28.0
    var costPrice: Double = 0.0,
    var unit: String = "pcs",
    var subtitle: String = "",
    var imageUrl: String? = null,
    val rowKey: String = UUID.randomUUID().toString()
)

private fun formatGstNumberInput(value: Double): String {
    if (value == 0.0) return ""
    return if (value % 1.0 == 0.0) value.toLong().toString()
    else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GstCalculatorScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val allItems by viewModel.allItems.collectAsState()
    val prefilledQuotation by viewModel.prefilledQuotationForGst.collectAsState()
    var quotationId by remember { mutableStateOf<String?>(null) }
    var quotationNo by remember { mutableStateOf<String?>(null) }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var storeGstin by remember { mutableStateOf("27AABCS1234F1Z5") }
    var customerGstin by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf(viewModel.previewNextInvoiceNo()) }
    var hasConsumedSequenceNo by remember { mutableStateOf(false) }
    var deductStockAndCashflow by remember { mutableStateOf(true) }
    var invoiceDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var isInterState by remember { mutableStateOf(false) } // false = CGST + SGST, true = IGST

    val rows = remember {
        mutableStateListOf(
            GstInvoiceRow(UUID.randomUUID().toString(), "", "8481", 1.0, 0.0, 18.0)
        )
    }
    var defaultTaxRate by remember { mutableStateOf(18.0) }
    var billDiscount by remember { mutableDoubleStateOf(0.0) }
    var discountText by remember { mutableStateOf("") }
    var savedBillId by remember { mutableStateOf<String?>(null) }
    var gstPaymentMode by remember { mutableStateOf("Cash") }
    var paidNowText by remember { mutableStateOf("") }
    var creditDueDate by remember { mutableStateOf("") }

    var transporterId by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }
    var exportedPdfInfo by remember { mutableStateOf<Pair<File, String>?>(null) }
    var savedBillForCompletion by remember { mutableStateOf<GstBillRecord?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var showInvoicePreviewDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearPrefilledGstQuotation()
        }
    }

    LaunchedEffect(prefilledQuotation) {
        val q = prefilledQuotation
        if (q != null) {
            customerName = q.customerName
            customerPhone = q.customerPhone
            customerAddress = q.customerAddress
            invoiceNo = "GST-${q.quotationNo}"
            hasConsumedSequenceNo = true
            quotationId = q.id
            quotationNo = q.quotationNo
            defaultTaxRate = q.taxPercent
            billDiscount = q.discount.coerceAtLeast(0.0)
            discountText = formatGstNumberInput(billDiscount)

            // Parse quotation line items into rows
            val parsed = InvoicePrintManager.parseLineItems(q.itemsJson)
            rows.clear()
            parsed.forEachIndexed { idx, itm ->
                val matched = allItems.find { item -> item.id == itm.itemId || item.name.equals(itm.name, ignoreCase = true) }
                val effectiveRate = if (itm.qty > 0) itm.total / itm.qty else itm.unitPrice
                rows.add(
                    GstInvoiceRow(
                        id = itm.itemId ?: "qrow_$idx",
                        itemName = itm.name,
                        hsnCode = matched?.code?.ifBlank { "8481" } ?: "8481",
                        qty = itm.qty,
                        rate = effectiveRate,
                        taxRatePercent = q.taxPercent,
                        costPrice = matched?.cost ?: 0.0,
                        unit = itm.unit.ifBlank { matched?.unit ?: "pcs" },
                        subtitle = itm.type,
                        imageUrl = matched?.imageUrl
                    )
                )
            }
            if (rows.isEmpty()) {
                rows.add(GstInvoiceRow(UUID.randomUUID().toString(), "", "8481", 1.0, 0.0, q.taxPercent))
            }
        }
    }

    val scope = rememberCoroutineScope()
    val bankingInfo by viewModel.bankingInfo.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val instantDispatchSlip by viewModel.instantDispatchSlip.collectAsState()

    // Calculations with proportional bill-level discount distribution
    val distributedLines by remember(billDiscount) {
        derivedStateOf {
            val raw = rows.map { r ->
                val rawTaxable = r.qty * r.rate
                val rawTax = rawTaxable * (r.taxRatePercent / 100.0)
                GstBillLineItem(
                    id = r.id,
                    name = r.itemName,
                    hsn = r.hsnCode,
                    qty = r.qty,
                    rate = r.rate,
                    taxRatePercent = r.taxRatePercent,
                    taxableAmount = rawTaxable,
                    taxAmount = rawTax,
                    totalAmount = rawTaxable + rawTax,
                    unit = r.unit,
                    subtitle = r.subtitle,
                    imageUrl = r.imageUrl
                )
            }
            InvoicePrintManager.distributeDiscountAcrossGstLines(raw, billDiscount)
        }
    }
    val rawSubtotal by remember { derivedStateOf { rows.sumOf { it.qty * it.rate } } }
    val taxableTotal by remember { derivedStateOf { distributedLines.sumOf { it.taxableAmount } } }
    val totalTaxAmount by remember { derivedStateOf { distributedLines.sumOf { it.taxAmount } } }
    val grandTotal by remember { derivedStateOf { distributedLines.sumOf { it.totalAmount } } }
    val isEWayBillRequired = grandTotal > 50000.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GST Tax Invoice & E-Way Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Info Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (!quotationNo.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ext.purple.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ext.purple.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ext.purple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Converted from Estimate #$quotationNo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ext.purple
                                    )
                                }
                            }
                        }

                        Text("Invoice & GSTIN Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stackFields = maxWidth < 300.dp
                            if (stackFields) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = storeGstin,
                                        onValueChange = { storeGstin = it },
                                        label = { Text("Store GSTIN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = customerGstin,
                                        onValueChange = { customerGstin = it },
                                        label = { Text("Customer GSTIN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = customerName,
                                        onValueChange = { customerName = it },
                                        label = { Text("Customer Name", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = invoiceNo,
                                        onValueChange = { invoiceNo = it },
                                        label = { Text("Invoice No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = customerPhone,
                                        onValueChange = { customerPhone = it },
                                        label = { Text("Phone", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = customerAddress,
                                        onValueChange = { customerAddress = it },
                                        label = { Text("Address", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        OutlinedTextField(
                                            value = storeGstin,
                                            onValueChange = { storeGstin = it },
                                            label = { Text("Store GSTIN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                        OutlinedTextField(
                                            value = customerGstin,
                                            onValueChange = { customerGstin = it },
                                            label = { Text("Customer GSTIN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        OutlinedTextField(
                                            value = customerName,
                                            onValueChange = { customerName = it },
                                            label = { Text("Customer Name", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                        OutlinedTextField(
                                            value = invoiceNo,
                                            onValueChange = { invoiceNo = it },
                                            label = { Text("Invoice No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        OutlinedTextField(
                                            value = customerPhone,
                                            onValueChange = { customerPhone = it },
                                            label = { Text("Phone", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                        OutlinedTextField(
                                            value = customerAddress,
                                            onValueChange = { customerAddress = it },
                                            label = { Text("Address", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Optional", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Supply Type: ${if (isInterState) "Inter-State (IGST)" else "Intra-State (CGST + SGST)"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = isInterState,
                                onCheckedChange = { isInterState = it }
                            )
                        }
                    }
                }
            }

            // E-Way Bill Rule 138 Warning Card if > 50,000
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEWayBillRequired) ext.warning.copy(alpha = 0.15f) else ext.positive.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, if (isEWayBillRequired) ext.warning else ext.positive)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isEWayBillRequired) Icons.Default.Warning else Icons.Default.Calculate,
                                contentDescription = null,
                                tint = if (isEWayBillRequired) ext.warning else ext.positive,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEWayBillRequired) "⚠️ E-WAY BILL REQUIRED (Value > ₹50,000)" else "✓ E-Way Bill Optional (Invoice Value ≤ ₹50,000)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isEWayBillRequired) ext.warning else ext.positive
                            )
                        }
                        if (isEWayBillRequired) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("As per Rule 138 of CGST Rules, generation of E-Way Bill is mandatory for movement of goods exceeding ₹50,000.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                OutlinedTextField(
                                    value = transporterId,
                                    onValueChange = { transporterId = it },
                                    label = { Text("Transporter ID", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                )
                                OutlinedTextField(
                                    value = vehicleNo,
                                    onValueChange = { vehicleNo = it },
                                    label = { Text("Vehicle No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    placeholder = { Text("e.g. MH12AB1234", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Items Header & Add Row
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Invoice Items (${rows.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedButton(onClick = {
                            rows.add(GstInvoiceRow(UUID.randomUUID().toString(), "", "8481", 1.0, 100.0, defaultTaxRate))
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Item", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bill Tax Preset Bar (0% Tax-Free Bill Option)
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Bill Tax Mode:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { rate ->
                                    val isAllCurrent = rows.isNotEmpty() && rows.all { it.taxRatePercent == rate }
                                    val isSelected = isAllCurrent || (rows.isEmpty() && defaultTaxRate == rate)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) {
                                            if (rate == 0.0) ext.positive else MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.clickable {
                                            defaultTaxRate = rate
                                            for (i in rows.indices) {
                                                rows[i] = rows[i].copy(taxRatePercent = rate)
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (rate == 0.0) "0% (Tax-Free)" else "${rate.toInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Invoice Row Editors
            itemsIndexed(rows, key = { _, row -> row.rowKey }) { rowIndex, row ->
                val rowKey = row.rowKey
                var isSuggestionsOpen by remember(rowKey) { mutableStateOf(false) }
                var matchingItems by remember(rowKey) { mutableStateOf<List<com.example.data.model.Item>>(emptyList()) }

                LaunchedEffect(isSuggestionsOpen, row.itemName, allItems) {
                    if (!isSuggestionsOpen || row.itemName.isBlank()) {
                        matchingItems = emptyList()
                        return@LaunchedEffect
                    }
                    delay(60)
                    matchingItems = com.example.util.ItemSearchMatcher.searchItems(
                        items = allItems,
                        rawQuery = row.itemName,
                        limit = 15
                    )
                }

                fun updateThisRow(transform: (GstInvoiceRow) -> GstInvoiceRow) {
                    val curIdx = rows.indexOfFirst { it.rowKey == rowKey }
                    if (curIdx >= 0) {
                        rows[curIdx] = transform(rows[curIdx])
                    }
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!row.imageUrl.isNullOrBlank()) {
                                val rowImgReq = remember(row.imageUrl) {
                                    ImageRequest.Builder(context)
                                        .data(row.imageUrl)
                                        .size(128)
                                        .crossfade(false)
                                        .build()
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    AsyncImage(
                                        model = rowImgReq,
                                        contentDescription = "Item Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = row.itemName,
                                onValueChange = { newName ->
                                    updateThisRow { it.copy(itemName = newName) }
                                    isSuggestionsOpen = newName.isNotBlank()
                                },
                                label = { Text("Item Name (Search DB)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 0.dp)
                                    .onFocusChanged { if (it.isFocused && row.itemName.isNotBlank()) isSuggestionsOpen = true }
                            )
                            IconButton(
                                onClick = {
                                    val curIdx = rows.indexOfFirst { it.rowKey == rowKey }
                                    if (curIdx >= 0 && rows.size > 1) rows.removeAt(curIdx)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ext.danger)
                            }
                        }

                        // Prominent Cost Price Tab / Badge (Always visible, internal reference only)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (row.costPrice > 0.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (row.costPrice > 0.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (row.costPrice > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cost Price Tab (DB Ref):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(Excluded from invoice)",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (row.costPrice > 0.0) "₹%.2f".format(row.costPrice) else "— (Auto-loads on item selection)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.costPrice > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSuggestionsOpen && matchingItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 2.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    Text("💡 Matching Inventory Items:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                    ) {
                                        items(matchingItems, key = { it.id }) { matched ->
                                            val meta = remember(matched.id, matched.type, matched.brand, matched.size) {
                                                listOfNotNull(
                                                    matched.type.ifBlank { null }?.let { "Type: $it" },
                                                    matched.brand.ifBlank { null }?.let { "Brand: $it" },
                                                    matched.size.ifBlank { null }?.let { "Size: $it" }
                                                ).joinToString(" • ")
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val displayName = if (matched.size.isNotBlank() && !matched.name.contains(matched.size, ignoreCase = true)) {
                                                            "${matched.name} ${matched.size}"
                                                        } else {
                                                            matched.name
                                                        }
                                                        updateThisRow { curRow ->
                                                            curRow.copy(
                                                                id = matched.id,
                                                                itemName = displayName,
                                                                hsnCode = matched.code.ifBlank { curRow.hsnCode.ifBlank { "8481" } },
                                                                costPrice = matched.cost,
                                                                rate = if (matched.price > 0.0) matched.price else (if (matched.cost > 0.0) matched.cost * 1.18 else curRow.rate),
                                                                unit = matched.unit,
                                                                subtitle = meta,
                                                                imageUrl = matched.imageUrl
                                                            )
                                                        }
                                                        isSuggestionsOpen = false
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!matched.imageUrl.isNullOrBlank()) {
                                                    val thumbReq = remember(matched.imageUrl) {
                                                        ImageRequest.Builder(context)
                                                            .data(matched.imageUrl)
                                                            .size(128)
                                                            .crossfade(false)
                                                            .build()
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        AsyncImage(
                                                            model = thumbReq,
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(matched.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    if (meta.isNotBlank()) {
                                                        Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    if (matched.cost > 0.0) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                            modifier = Modifier.padding(bottom = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Cost: ₹${matched.cost}",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    if (matched.price > 0.0) {
                                                        Text("Sell: ₹${matched.price}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ext.positive)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        var qtyText by remember(rowKey) { mutableStateOf(formatGstNumberInput(row.qty)) }
                        LaunchedEffect(row.qty) {
                            val parsed = qtyText.trim().toDoubleOrNull()
                            if (parsed != row.qty && !(qtyText.isBlank() && row.qty == 0.0)) {
                                qtyText = formatGstNumberInput(row.qty)
                            }
                        }
                        var rateText by remember(rowKey) { mutableStateOf(formatGstNumberInput(row.rate)) }
                        LaunchedEffect(row.rate) {
                            val parsed = rateText.trim().toDoubleOrNull()
                            if (parsed != row.rate && !(rateText.isBlank() && row.rate == 0.0)) {
                                rateText = formatGstNumberInput(row.rate)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = { qStr ->
                                    qtyText = qStr
                                    val parsed = qStr.trim().toDoubleOrNull()
                                    if (parsed != null) {
                                        updateThisRow { it.copy(qty = parsed) }
                                    } else if (qStr.isBlank()) {
                                        updateThisRow { it.copy(qty = 0.0) }
                                    }
                                },
                                label = { Text("Qty", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.85f)
                                    .widthIn(min = 0.dp)
                                    .testTag("gst_row_qty_$rowIndex")
                            )
                            OutlinedTextField(
                                value = rateText,
                                onValueChange = { rStr ->
                                    rateText = rStr
                                    val parsed = rStr.trim().toDoubleOrNull()
                                    if (parsed != null) {
                                        updateThisRow { it.copy(rate = parsed) }
                                    } else if (rStr.isBlank()) {
                                        updateThisRow { it.copy(rate = 0.0) }
                                    }
                                },
                                label = { Text("Rate", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.05f)
                                    .widthIn(min = 0.dp)
                                    .testTag("gst_row_rate_$rowIndex")
                            )
                            OutlinedTextField(
                                value = row.hsnCode,
                                onValueChange = { newHsn -> updateThisRow { it.copy(hsnCode = newHsn) } },
                                label = { Text("HSN", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                modifier = Modifier.weight(0.95f).widthIn(min = 0.dp)
                            )
                            val rowGrand = (row.qty * row.rate) * (1.0 + row.taxRatePercent / 100.0)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .padding(top = 8.dp)
                                    .height(56.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "Total",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "₹%.2f".format(rowGrand),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ext.positive,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tax Slab Selector (Supports 0% Tax / Exempt)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("GST Rate:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { slab ->
                                    val selected = row.taxRatePercent == slab
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) {
                                            if (slab == 0.0) ext.positive else MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { updateThisRow { it.copy(taxRatePercent = slab) } }
                                    ) {
                                        Text(
                                            text = if (slab == 0.0) "0% (Exempt)" else "${slab.toInt()}%",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary Breakdown Footer Card inside LazyColumn
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { dStr ->
                                discountText = dStr
                                billDiscount = dStr.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            },
                            label = { Text("Bill Discount (₹)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .testTag("gst_discount_input")
                        )
                        if (billDiscount > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal:", fontSize = 13.sp)
                                Text("₹%.2f".format(rawSubtotal), fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount:", fontSize = 13.sp, color = ext.positive)
                                Text("-₹%.2f".format(billDiscount.coerceAtMost(rawSubtotal)), fontWeight = FontWeight.Bold, color = ext.positive)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Taxable Amount:", fontSize = 13.sp)
                            Text("₹%.2f".format(taxableTotal), fontWeight = FontWeight.Bold)
                        }
                        if (totalTaxAmount == 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GST Tax (0% Rate):", fontSize = 13.sp)
                                Text("₹0.00 (Exempt / Nil-Rated)", fontWeight = FontWeight.Bold, color = ext.positive)
                            }
                        } else if (isInterState) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("IGST Total:", fontSize = 13.sp)
                                Text("₹%.2f".format(totalTaxAmount), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val effPercent = String.format(Locale.US, "%.1f", totalTaxAmount / 2 / taxableTotal.coerceAtLeast(1.0) * 100)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CGST Total ($effPercent%):", fontSize = 13.sp)
                                Text("₹%.2f".format(totalTaxAmount / 2), fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SGST Total ($effPercent%):", fontSize = 13.sp)
                                Text("₹%.2f".format(totalTaxAmount / 2), fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grand Total:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("₹%.2f".format(grandTotal), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Banking & Signature Status Banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Banking, QR & Signature for Invoice", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            val hasSig = bankingInfo.signaturePath != null
                            val hasQr = bankingInfo.qrCodePath != null
                            val bName = if (bankingInfo.bankName.isNotBlank()) bankingInfo.bankName else "Bank details not set"
                            val sigText = if (hasSig) "Signature ✓" else "No Signature"
                            val qrText = if (hasQr) "UPI QR ✓" else "No QR"
                            Text("$bName • $qrText • $sigText", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.navigateTo(com.example.ui.NavigationTab.SETTINGS)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Settings", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Stock Deduction & Cashflow Sale Option + Action Buttons
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { deductStockAndCashflow = !deductStockAndCashflow }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = deductStockAndCashflow,
                        onCheckedChange = { deductStockAndCashflow = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Deduct Inventory Stock & Log Cashflow / Khata",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (deductStockAndCashflow) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        listOf("Cash", "UPI", "Bank", "Khata", "Split").forEach { mode ->
                            FilterChip(
                                selected = gstPaymentMode == mode,
                                onClick = { gstPaymentMode = mode },
                                label = { Text(mode, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                    if (gstPaymentMode == "Khata" || gstPaymentMode == "Split") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (gstPaymentMode == "Split") {
                                OutlinedTextField(
                                    value = paidNowText,
                                    onValueChange = { paidNowText = it },
                                    label = { Text("Paid Now (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = creditDueDate,
                                onValueChange = { creditDueDate = it },
                                label = { Text("Khata Due (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val validRows = rows.filter { it.itemName.isNotBlank() }
                            if (validRows.isEmpty()) {
                                viewModel.showToast("Please enter or select at least one item first!")
                            } else {
                                showInvoicePreviewDialog = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            val validRows = rows.filter { it.itemName.isNotBlank() }
                            if (validRows.isEmpty()) {
                                haptics.reject()
                                viewModel.showToast("Please enter or select at least one item first!")
                                return@Button
                            }
                            if (!hasConsumedSequenceNo && quotationId == null && invoiceNo == viewModel.previewNextInvoiceNo()) {
                                invoiceNo = viewModel.consumeNextInvoiceNo()
                                hasConsumedSequenceNo = true
                            }
                            scope.launch {
                                isGeneratingPdf = true
                                try {
                                    val bInfo = viewModel.bankingInfo.value
                                    val sigBmp = viewModel.bankingPreferenceManager.getSignatureBitmap()
                                    val qrBmp = viewModel.bankingPreferenceManager.getQrCodeBitmap()

                                    val rawValidLines = validRows.map { r ->
                                        val rawTaxable = r.qty * r.rate
                                        val tAmt = rawTaxable * (r.taxRatePercent / 100.0)
                                        GstBillLineItem(
                                            id = r.id,
                                            name = r.itemName,
                                            hsn = r.hsnCode,
                                            qty = r.qty,
                                            rate = r.rate,
                                            taxRatePercent = r.taxRatePercent,
                                            taxableAmount = rawTaxable,
                                            taxAmount = tAmt,
                                            totalAmount = rawTaxable + tAmt,
                                            unit = r.unit,
                                            subtitle = r.subtitle,
                                            imageUrl = r.imageUrl
                                        )
                                    }
                                    val gstLineItems = InvoicePrintManager.distributeDiscountAcrossGstLines(rawValidLines, billDiscount)
                                    val reconciledTaxable = gstLineItems.sumOf { it.taxableAmount }
                                    val reconciledTax = gstLineItems.sumOf { it.taxAmount }
                                    val reconciledGrandTotal = gstLineItems.sumOf { it.totalAmount }

                                    val pdfItems = gstLineItems.map { item ->
                                        InvoicePrintManager.GstTaxPdfItem(
                                            name = item.name,
                                            hsn = item.hsn,
                                            qty = item.qty,
                                            rate = item.rate,
                                            taxRatePercent = item.taxRatePercent,
                                            taxableAmount = item.taxableAmount,
                                            taxAmount = item.taxAmount,
                                            totalAmount = item.totalAmount,
                                            unit = item.unit,
                                            subtitle = item.subtitle
                                        )
                                    }

                                    val file = InvoicePrintManager.createGstTaxInvoicePdf(
                                        context = context,
                                        invoiceNo = invoiceNo,
                                        invoiceDate = invoiceDate,
                                        storeName = effectiveStoreTitle.ifBlank { "HARDWARE & TOOLS STORE" },
                                        storeGstin = storeGstin,
                                        customerName = customerName,
                                        customerGstin = customerGstin,
                                        isInterState = isInterState,
                                        items = pdfItems,
                                        taxableTotal = reconciledTaxable,
                                        totalTaxAmount = reconciledTax,
                                        grandTotal = reconciledGrandTotal,
                                        transporterId = transporterId,
                                        vehicleNo = vehicleNo,
                                        bankingInfo = bInfo,
                                        signatureBitmap = sigBmp,
                                        qrCodeBitmap = qrBmp
                                    )
                                    val cleanInvNo = invoiceNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                    val storagePath = InvoicePrintManager.savePdfToDownloads(
                                        context = context,
                                        sourceFile = file,
                                        displayName = "GST_Invoice_$cleanInvNo.pdf"
                                    )
                                    exportedPdfInfo = Pair(file, storagePath)

                                    val nowIso = DateTimeHelper.nowIso()
                                    val targetId = savedBillId ?: UUID.randomUUID().toString()
                                    val creditToKhata = if (!deductStockAndCashflow) 0.0 else when (gstPaymentMode) {
                                        "Khata" -> reconciledGrandTotal
                                        "Split" -> (reconciledGrandTotal - (paidNowText.trim().toDoubleOrNull() ?: 0.0)).coerceIn(0.0, reconciledGrandTotal)
                                        else -> 0.0
                                    }
                                    val billStatus = when {
                                        creditToKhata >= reconciledGrandTotal - 0.005 -> "Khata Due"
                                        creditToKhata > 0.005 -> "Partial Khata"
                                        else -> "Generated"
                                    }
                                    val gstRecord = GstBillRecord(
                                        id = targetId,
                                        invoiceNo = invoiceNo,
                                        invoiceDate = invoiceDate,
                                        customerName = customerName.ifBlank { if (creditToKhata > 0.005) "Credit Customer" else "Cash Customer" },
                                        customerPhone = customerPhone,
                                        customerGstin = customerGstin,
                                        customerAddress = customerAddress,
                                        storeGstin = storeGstin,
                                        isInterState = isInterState,
                                        itemsJson = InvoicePrintManager.gstItemsToJson(gstLineItems),
                                        taxableTotal = reconciledTaxable,
                                        totalTaxAmount = reconciledTax,
                                        grandTotal = reconciledGrandTotal,
                                        transporterId = transporterId,
                                        vehicleNo = vehicleNo,
                                        quotationId = quotationId,
                                        quotationNo = quotationNo,
                                        pdfPath = storagePath,
                                        status = billStatus,
                                        createdAt = nowIso
                                    )
                                    haptics.confirm()
                                    if (savedBillId == null) {
                                        savedBillId = targetId
                                        viewModel.completePosOrGstSale(
                                            bill = gstRecord,
                                            lineItems = gstLineItems,
                                            deductStock = deductStockAndCashflow,
                                            recordCashflowSale = deductStockAndCashflow,
                                            paymentMode = gstPaymentMode,
                                            creditAmountToKhata = creditToKhata,
                                            creditDueDate = creditDueDate.trim()
                                        )
                                    } else {
                                        viewModel.saveGstBill(gstRecord)
                                    }
                                } catch (e: Exception) {
                                    haptics.reject()
                                    viewModel.showToast("Failed to generate PDF: ${e.message}")
                                } finally {
                                    isGeneratingPdf = false
                                }
                            }
                        },
                        enabled = !isGeneratingPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1.35f)
                    ) {
                        if (isGeneratingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exporting...", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("🖨️ PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Save Bill Direct Button
                    OutlinedButton(
                        onClick = {
                            val validRows = rows.filter { it.itemName.isNotBlank() }
                            if (validRows.isEmpty()) {
                                haptics.reject()
                                viewModel.showToast("Please enter or select at least one item first!")
                                return@OutlinedButton
                            }
                            if (!hasConsumedSequenceNo && quotationId == null && invoiceNo == viewModel.previewNextInvoiceNo()) {
                                invoiceNo = viewModel.consumeNextInvoiceNo()
                                hasConsumedSequenceNo = true
                            }
                            val nowIso = DateTimeHelper.nowIso()
                            val rawValidLines = validRows.map { r ->
                                val rawTaxable = r.qty * r.rate
                                val tAmt = rawTaxable * (r.taxRatePercent / 100.0)
                                GstBillLineItem(
                                    id = r.id,
                                    name = r.itemName,
                                    hsn = r.hsnCode,
                                    qty = r.qty,
                                    rate = r.rate,
                                    taxRatePercent = r.taxRatePercent,
                                    taxableAmount = rawTaxable,
                                    taxAmount = tAmt,
                                    totalAmount = rawTaxable + tAmt,
                                    unit = r.unit,
                                    subtitle = r.subtitle,
                                    imageUrl = r.imageUrl
                                )
                            }
                            val gstLineItems = InvoicePrintManager.distributeDiscountAcrossGstLines(rawValidLines, billDiscount)
                            val reconciledTaxable = gstLineItems.sumOf { it.taxableAmount }
                            val reconciledTax = gstLineItems.sumOf { it.taxAmount }
                            val reconciledGrandTotal = gstLineItems.sumOf { it.totalAmount }
                            val targetId = savedBillId ?: UUID.randomUUID().toString()
                            val creditToKhata = if (!deductStockAndCashflow) 0.0 else when (gstPaymentMode) {
                                "Khata" -> reconciledGrandTotal
                                "Split" -> (reconciledGrandTotal - (paidNowText.trim().toDoubleOrNull() ?: 0.0)).coerceIn(0.0, reconciledGrandTotal)
                                else -> 0.0
                            }
                            val billStatus = when {
                                creditToKhata >= reconciledGrandTotal - 0.005 -> "Khata Due"
                                creditToKhata > 0.005 -> "Partial Khata"
                                else -> "Generated"
                            }
                            val gstRecord = GstBillRecord(
                                id = targetId,
                                invoiceNo = invoiceNo,
                                invoiceDate = invoiceDate,
                                customerName = customerName.ifBlank { if (creditToKhata > 0.005) "Credit Customer" else "Cash Customer" },
                                customerPhone = customerPhone,
                                customerGstin = customerGstin,
                                customerAddress = customerAddress,
                                storeGstin = storeGstin,
                                isInterState = isInterState,
                                itemsJson = InvoicePrintManager.gstItemsToJson(gstLineItems),
                                taxableTotal = reconciledTaxable,
                                totalTaxAmount = reconciledTax,
                                grandTotal = reconciledGrandTotal,
                                transporterId = transporterId,
                                vehicleNo = vehicleNo,
                                quotationId = quotationId,
                                quotationNo = quotationNo,
                                pdfPath = null,
                                status = billStatus,
                                createdAt = nowIso
                            )
                            haptics.confirm()
                            if (savedBillId == null) {
                                savedBillId = targetId
                                viewModel.completePosOrGstSale(
                                    bill = gstRecord,
                                    lineItems = gstLineItems,
                                    deductStock = deductStockAndCashflow,
                                    recordCashflowSale = deductStockAndCashflow,
                                    paymentMode = gstPaymentMode,
                                    creditAmountToKhata = creditToKhata,
                                    creditDueDate = creditDueDate.trim()
                                )
                            } else {
                                viewModel.saveGstBill(gstRecord)
                            }
                            if (instantDispatchSlip) {
                                savedBillForCompletion = gstRecord
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1.1f).testTag("gst_save_bill_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp), tint = ext.positive)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(150.dp))
            }
        }

        // Invoice Interactive Preview Dialog
        if (showInvoicePreviewDialog) {
            val validRows = rows.filter { it.itemName.isNotBlank() }
            AlertDialog(
                onDismissRequest = { showInvoicePreviewDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 520.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GST Tax Invoice Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            // Invoice Header
                            item {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(effectiveStoreTitle.ifBlank { "HARDWARE & TOOLS STORE" }.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                                        Text("GSTIN: $storeGstin  •  TAX INVOICE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                                        Text("Inv #: $invoiceNo  |  Date: $invoiceDate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // Billed to
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Billed To: ${customerName.ifBlank { "Cash / Retail Customer" }}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        if (customerGstin.isNotBlank()) {
                                            Text("GSTIN: $customerGstin", fontSize = 10.sp)
                                        }
                                        Text("Supply: ${if (isInterState) "Inter-State (IGST)" else "Intra-State (CGST + SGST)"}", fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Items Table Preview
                            item {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("#", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                                        Text("Image", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                                        Text("Item", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Qty", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                                        Text("Rate", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                                        Text("Total", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                                    }
                                }
                            }

                            itemsIndexed(validRows) { idx, r ->
                                val rowTotal = (r.qty * r.rate) + ((r.qty * r.rate) * (r.taxRatePercent / 100.0))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}", fontSize = 10.sp, modifier = Modifier.width(18.dp))
                                    // Image: Show thumbnail if present, LEAVE BLANK if no image!
                                    Box(modifier = Modifier.width(42.dp).height(32.dp), contentAlignment = Alignment.CenterStart) {
                                        if (!r.imageUrl.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                AsyncImage(
                                                    model = r.imageUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        // If no image, box is completely blank!
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.itemName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        if (r.subtitle.isNotBlank()) {
                                            Text(r.subtitle, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text("%.1f".format(r.qty), fontSize = 10.sp, modifier = Modifier.width(36.dp))
                                    Text("₹%.0f".format(r.rate), fontSize = 10.sp, modifier = Modifier.width(44.dp))
                                    Text("₹%.2f".format(rowTotal), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }

                            // Summary & Grand Total
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Taxable Amount:", fontSize = 11.sp)
                                            Text("₹%.2f".format(taxableTotal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total GST Tax:", fontSize = 11.sp)
                                            Text("₹%.2f".format(totalTaxAmount), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Grand Total:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("₹%.2f".format(grandTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                // Banking, QR Code & Signature Preview Footer
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Bank details
                                        Column(modifier = Modifier.weight(1.3f)) {
                                            Text("BANK DETAILS:", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                            Text("Bank: ${bankingInfo.bankName.ifBlank { "Store Bank" }}", fontSize = 8.sp, color = Color.DarkGray)
                                            Text("A/C: ${bankingInfo.accountNumber.ifBlank { "-" }}", fontSize = 8.sp, color = Color.DarkGray)
                                            Text("IFSC: ${bankingInfo.ifscCode.ifBlank { "-" }}", fontSize = 8.sp, color = Color.DarkGray)
                                            if (bankingInfo.upiId.isNotBlank()) {
                                                Text("UPI: ${bankingInfo.upiId}", fontSize = 8.sp, color = Color.DarkGray)
                                            }
                                        }

                                        // QR Code if uploaded
                                        val previewQrModel: Any? = bankingInfo.qrCodePath?.let { File(it) }?.takeIf { it.exists() }
                                            ?: bankingInfo.qrCodeUrl?.takeIf { it.isNotBlank() }
                                        if (previewQrModel != null) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    border = BorderStroke(0.5.dp, Color.LightGray),
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = previewQrModel,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize().padding(1.dp)
                                                    )
                                                }
                                                Text("Scan to Pay", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        // Signature if uploaded
                                        val previewSigModel: Any? = bankingInfo.signaturePath?.let { File(it) }?.takeIf { it.exists() }
                                            ?: bankingInfo.signatureUrl?.takeIf { it.isNotBlank() }
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                            Text("For Store", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            if (previewSigModel != null) {
                                                AsyncImage(
                                                    model = previewSigModel,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.height(24.dp).width(60.dp)
                                                )
                                            } else {
                                                Text("[No signature]", fontSize = 8.sp, color = Color.Gray)
                                            }
                                            HorizontalDivider(modifier = Modifier.width(65.dp).padding(vertical = 1.dp), color = Color.Gray)
                                            Text("Authorized Signatory", fontSize = 7.5.sp, color = Color.DarkGray)
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showInvoicePreviewDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Close Preview")
                    }
                }
            )
        }

        // GST Bill Saved Completion Dialog with Saved QR Scanner
        savedBillForCompletion?.let { savedBill ->
            AlertDialog(
                onDismissRequest = { savedBillForCompletion = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ext.positive)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GST Bill Completed!", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Invoice #${savedBill.invoiceNo}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Customer: ${savedBill.customerName}", fontSize = 12.sp)
                                Text(
                                    "Grand Total: ₹${String.format(Locale.US, "%.2f", savedBill.grandTotal)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        SavedPaymentQrCard(
                            bankingInfo = bankingInfo,
                            amount = savedBill.grandTotal,
                            qrSize = 160.dp,
                            onOpenSettings = {
                                savedBillForCompletion = null
                                viewModel.navigateTo(com.example.ui.NavigationTab.SETTINGS)
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { savedBillForCompletion = null },
                        modifier = Modifier.testTag("gst_completion_done_button")
                    ) {
                        Text("Done")
                    }
                }
            )
        }

        // Exported PDF Location, Saved QR & Action Dialog
        exportedPdfInfo?.let { (file, storagePath) ->
            AlertDialog(
                onDismissRequest = { exportedPdfInfo = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ext.danger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GST Bill & PDF Completed!", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Your GST Tax Invoice PDF has been created and saved to your device storage:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Storage Location Box
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Storage Location:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    storagePath,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Show Saved Store Payment QR Code upon GST bill completion
                        SavedPaymentQrCard(
                            bankingInfo = bankingInfo,
                            amount = grandTotal,
                            qrSize = 156.dp,
                            onOpenSettings = {
                                exportedPdfInfo = null
                                viewModel.navigateTo(com.example.ui.NavigationTab.SETTINGS)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Actions
                        OutlinedButton(
                            onClick = {
                                InvoicePrintManager.openPdf(context, file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open / View PDF")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                InvoicePrintManager.sharePdf(context, file, "GST Tax Invoice #$invoiceNo")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share via WhatsApp / Email")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                InvoicePrintManager.printPdf(context, file, "GST_Invoice_$invoiceNo")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print A4 / Save via Android Print")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exportedPdfInfo = null }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}
