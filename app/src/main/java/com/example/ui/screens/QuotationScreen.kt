package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Item
import com.example.data.model.QuotationLineItem
import com.example.data.model.QuotationRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.InvoicePrintManager
import com.example.util.rememberAppHaptics
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun QuotationScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val quotations by viewModel.allQuotations.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val uiConfig by viewModel.uiConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    var quotationToEdit by remember { mutableStateOf<QuotationRecord?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var thermalPrintTarget by remember { mutableStateOf<QuotationRecord?>(null) }
    var quotationToDelete by remember { mutableStateOf<QuotationRecord?>(null) }
    var quotationToConvert by remember { mutableStateOf<QuotationRecord?>(null) }

    val storeTitle = uiConfig.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" }
    val inrFormat = remember {
        NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    val draftCount = remember(quotations) { quotations.count { it.status.equals("Draft", ignoreCase = true) } }
    val sentCount = remember(quotations) { quotations.count { it.status.equals("Sent", ignoreCase = true) } }
    val acceptedCount = remember(quotations) { quotations.count { it.status.equals("Accepted", ignoreCase = true) } }
    val convertedCount = remember(quotations) { quotations.count { it.status.equals("Converted", ignoreCase = true) } }

    val filtered = remember(quotations, searchQuery, selectedStatusFilter) {
        quotations.filter { q ->
            val matchQuery = searchQuery.isEmpty() ||
                q.customerName.contains(searchQuery, ignoreCase = true) ||
                q.quotationNo.contains(searchQuery, ignoreCase = true) ||
                q.customerPhone.contains(searchQuery, ignoreCase = true)

            val matchStatus = if (selectedStatusFilter == "ALL") true else q.status.equals(selectedStatusFilter, ignoreCase = true)
            matchQuery && matchStatus
        }
    }

    val conversionPct = remember(quotations, convertedCount) {
        if (quotations.isEmpty()) 0 else ((convertedCount.toFloat() / quotations.size.toFloat()) * 100f).toInt()
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken.trim()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Row: Estimates & Quotes + NEW button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estimates & Quotes",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${quotations.size} active records created",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        quotationToEdit = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("estimates_new_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NEW", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                }
            }
        }

        // 2. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search by customer, quote #, phone...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak customer name or estimate number...")
                                }
                                try {
                                    voiceSearchLauncher.launch(intent)
                                } catch (_: Exception) {
                                    viewModel.showToast("Voice input unavailable")
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice search estimates",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("estimates_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )
        }

        // 3. Status Filter Chips Row
        item {
            val filterSpecs = listOf(
                Triple("ALL", null, quotations.size),
                Triple("Draft", ext.warning, draftCount),
                Triple("Sent", MaterialTheme.colorScheme.primary, sentCount),
                Triple("Accepted", ext.positive, acceptedCount),
                Triple("Converted", ext.info, convertedCount)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterSpecs) { (statusKey, dotColor, count) ->
                    val isSelected = selectedStatusFilter == statusKey
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable { selectedStatusFilter = statusKey }
                            .testTag("estimates_filter_${statusKey.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            if (dotColor != null && !isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            Text(
                                text = statusKey,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            if (count > 0 && statusKey != "ALL") {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color.White.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Estimates List or Empty State
        if (filtered.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (quotations.isEmpty()) "No estimates created yet" else "No matching estimates found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (quotations.isEmpty()) "Create customer estimates and convert them directly to GST bills or sales." else "Try adjusting your search or filter selection.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (quotations.isEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    quotationToEdit = null
                                    showFormDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Estimate", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { quote ->
                QuotationCard(
                    quotation = quote,
                    inrFormat = inrFormat,
                    onEdit = {
                        quotationToEdit = quote
                        showFormDialog = true
                    },
                    onDelete = { quotationToDelete = quote },
                    onConvertToSale = { quotationToConvert = quote },
                    onStatusChange = { newStatus ->
                        haptics.tick()
                        viewModel.updateQuotationStatus(quote, newStatus)
                    },
                    onPrintPdf = {
                        val pdf = InvoicePrintManager.createQuotationPdf(context, quote, storeName = storeTitle)
                        InvoicePrintManager.printPdf(context, pdf, "Quote_${quote.quotationNo}")
                    },
                    onSharePdf = {
                        val pdf = InvoicePrintManager.createQuotationPdf(context, quote, storeName = storeTitle)
                        InvoicePrintManager.sharePdf(context, pdf, "Estimate #${quote.quotationNo}")
                    },
                    onThermalPrint = { thermalPrintTarget = quote }
                )
            }
        }

        // 5. Quotations Conversion Rate Summary Card
        if (quotations.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quotations Conversion Rate",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$conversionPct% SUCCESS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ext.positive
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (conversionPct / 100f).coerceIn(0f, 1f) },
                            color = ext.info,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$draftCount Draft (Pending Approval)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$convertedCount Converted to Sale/Invoice",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Form Dialog
    if (showFormDialog) {
        QuotationFormDialog(
            existingQuotation = quotationToEdit,
            availableItems = allItems,
            onDismiss = { showFormDialog = false },
            onSave = { record ->
                haptics.confirm()
                viewModel.saveQuotation(record)
                showFormDialog = false
            }
        )
    }

    // Thermal Print Dialog
    if (thermalPrintTarget != null) {
        val q = thermalPrintTarget!!
        val lineItems = InvoicePrintManager.parseLineItems(q.itemsJson)
        ThermalPrintDialog(
            title = "ESTIMATE / QUOTE",
            refNo = q.quotationNo,
            date = q.date,
            customerName = q.customerName,
            items = lineItems,
            subtotal = q.subtotal,
            discount = q.discount,
            grandTotal = q.grandTotal,
            onDismiss = { thermalPrintTarget = null },
            onPrintSuccess = {
                viewModel.showToast("Receipt printed successfully!")
                thermalPrintTarget = null
            }
        )
    }

    // Delete Confirmation
    if (quotationToDelete != null) {
        AlertDialog(
            onDismissRequest = { quotationToDelete = null },
            title = { Text("Delete Estimate") },
            text = { Text("Are you sure you want to delete Estimate #${quotationToDelete!!.quotationNo} for ${quotationToDelete!!.customerName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.reject()
                        viewModel.deleteQuotation(quotationToDelete!!.id)
                        quotationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { quotationToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Convert Quotation Dialog (Sale or GST Bill)
    if (quotationToConvert != null) {
        val q = quotationToConvert!!
        AlertDialog(
            onDismissRequest = { quotationToConvert = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convert Estimate #${q.quotationNo}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose how you want to convert this quotation for ${q.customerName} (${InvoicePrintManager.parseLineItems(q.itemsJson).size} items):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val quoteToConvert = q
                            quotationToConvert = null
                            viewModel.openGstCalculatorWithQuotation(quoteToConvert)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Convert to GST Tax Bill & Store", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.convertQuotationToSale(q)
                            quotationToConvert = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.positive)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Regular Stock Sale (Deduct Stock)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { quotationToConvert = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuotationCard(
    quotation: QuotationRecord,
    inrFormat: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvertToSale: () -> Unit,
    onStatusChange: (String) -> Unit,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onThermalPrint: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val items = remember(quotation.itemsJson) { InvoicePrintManager.parseLineItems(quotation.itemsJson) }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val statusColor = when (quotation.status) {
        "Converted" -> ext.info
        "Accepted" -> ext.positive
        "Sent" -> MaterialTheme.colorScheme.primary
        else -> ext.warning
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("estimate_card_${quotation.quotationNo}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Quote # Pill, Date & Interactive Status Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ext.info.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${quotation.quotationNo}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ext.info,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = quotation.date,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .clickable { showStatusDropdown = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = quotation.status.uppercase(),
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Change Status",
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showStatusDropdown,
                        onDismissRequest = { showStatusDropdown = false }
                    ) {
                        listOf("Draft", "Sent", "Accepted", "Converted").forEach { s ->
                            val sColor = when (s) {
                                "Converted" -> ext.info
                                "Accepted" -> ext.positive
                                "Sent" -> MaterialTheme.colorScheme.primary
                                else -> ext.warning
                            }
                            val isCurrent = quotation.status.equals(s, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(sColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (s == "Converted") "Converted (Sale)" else s,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) sColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = sColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showStatusDropdown = false
                                    if (s == "Converted" && quotation.status != "Converted") {
                                        onConvertToSale()
                                    } else if (!quotation.status.equals(s, ignoreCase = true)) {
                                        onStatusChange(s)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Row: Customer Info on left & Grand Total inset card on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quotation.customerName.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (quotation.customerPhone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = quotation.customerPhone,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = ext.info,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${items.size} item(s)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ext.info
                        )
                    }
                    if (quotation.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = quotation.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(start = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "GRAND TOTAL",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${inrFormat.format(quotation.grandTotal)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ext.info
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Action Row: 5 icon tiles + Convert to Sale button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EstimateActionIconTile(
                        icon = Icons.Default.Print,
                        contentDescription = "Print A4 PDF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onPrintPdf
                    )
                    EstimateActionIconTile(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Thermal Print",
                        tint = ext.warning,
                        onClick = onThermalPrint
                    )
                    EstimateActionIconTile(
                        icon = Icons.Default.Share,
                        contentDescription = "Share PDF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onSharePdf
                    )
                    EstimateActionIconTile(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit Estimate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onEdit
                    )
                    EstimateActionIconTile(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete Estimate",
                        tint = ext.danger,
                        onClick = onDelete
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (quotation.status != "Converted") {
                    Button(
                        onClick = onConvertToSale,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.positive,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Convert to Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ext.info, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Converted",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimateActionIconTile(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotationFormDialog(
    existingQuotation: QuotationRecord?,
    availableItems: List<Item>,
    onDismiss: () -> Unit,
    onSave: (QuotationRecord) -> Unit
) {
    val ext = LocalExtendedColors.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val nextQuoteNo = remember { "EST-" + (1000..9999).random().toString() }

    var customerName by remember { mutableStateOf(existingQuotation?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(existingQuotation?.customerPhone ?: "") }
    var quoteNo by remember { mutableStateOf(existingQuotation?.quotationNo ?: nextQuoteNo) }
    var date by remember { mutableStateOf(existingQuotation?.date ?: todayStr) }
    var validUntil by remember { mutableStateOf(existingQuotation?.validUntil ?: "") }
    var status by remember { mutableStateOf(existingQuotation?.status ?: "Draft") }
    var discountText by remember { mutableStateOf(existingQuotation?.discount?.toString() ?: "0") }
    var taxPercentText by remember { mutableStateOf(existingQuotation?.taxPercent?.toString() ?: "0") }
    var notes by remember { mutableStateOf(existingQuotation?.notes ?: "") }
    var lastSelectedType by remember { mutableStateOf("") }
    var voiceTargetIdx by remember { mutableStateOf<Int?>(null) }

    val lineItems = remember {
        mutableStateListOf<QuotationLineItem>().apply {
            if (existingQuotation != null) {
                val parsed = InvoicePrintManager.parseLineItems(existingQuotation.itemsJson)
                addAll(parsed)
                if (parsed.isNotEmpty()) {
                    lastSelectedType = parsed.last().type
                }
            } else {
                add(QuotationLineItem(name = "", qty = 1.0, unitPrice = 0.0, total = 0.0, type = ""))
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val normalized = com.example.util.ItemSearchMatcher.normalizeVoiceInput(spoken)
                val target = voiceTargetIdx
                if (target != null && target in lineItems.indices) {
                    lineItems[target] = lineItems[target].copy(name = normalized)
                }
            }
        }
    }

    val distinctTypes = remember(availableItems) {
        val defaults = listOf("Plumbing", "Electrical", "Hardware", "Sanitary", "Paints", "Tools")
        val fromDb = availableItems.map { it.type.trim() }.filter { it.isNotEmpty() }
        listOf("All") + (defaults + fromDb).distinct().sorted()
    }

    val subtotal = lineItems.sumOf { it.total }
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val taxPercent = taxPercentText.toDoubleOrNull() ?: 0.0
    val afterDiscount = (subtotal - discount).coerceAtLeast(0.0)
    val taxAmount = (afterDiscount * taxPercent) / 100.0
    val grandTotal = afterDiscount + taxAmount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingQuotation == null) "New Estimate / Quote" else "Edit Estimate #${existingQuotation.quotationNo}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Customer info
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer Name *") },
                                modifier = Modifier.weight(1.3f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Phone") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quoteNo,
                                onValueChange = { quoteNo = it },
                                label = { Text("Quote No.") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = date,
                                onValueChange = { date = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    item {
                        Column {
                            Text("STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Draft", "Sent", "Accepted", "Converted").forEach { s ->
                                    val isSelected = status.equals(s, ignoreCase = true)
                                    val chipColor = when (s) {
                                        "Converted" -> ext.purple
                                        "Accepted" -> ext.positive
                                        "Sent" -> MaterialTheme.colorScheme.primary
                                        else -> ext.warning
                                    }
                                    Surface(
                                        color = if (isSelected) chipColor else chipColor.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (isSelected) chipColor else chipColor.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { status = s }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = s,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else chipColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LINE ITEMS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            TextButton(
                                onClick = {
                                    val rowType = if (lineItems.isNotEmpty() && lineItems.last().type.isNotBlank()) lineItems.last().type else lastSelectedType
                                    lineItems.add(
                                        QuotationLineItem(
                                            name = "",
                                            qty = 1.0,
                                            unitPrice = 0.0,
                                            total = 0.0,
                                            type = rowType
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item")
                            }
                        }
                    }

                    // Line items list
                    items(lineItems.size) { idx ->
                        val item = lineItems[idx]
                        var isSuggestionsOpen by remember { mutableStateOf(false) }

                        val matchedDbItem = remember(item.itemId, item.name, availableItems) {
                            if (!item.itemId.isNullOrBlank()) {
                                availableItems.firstOrNull { it.id == item.itemId }
                            } else if (item.name.isNotBlank()) {
                                availableItems.firstOrNull { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
                            } else null
                        }
                        val effectiveCost = if (item.costPrice > 0.0) item.costPrice else (matchedDbItem?.cost ?: 0.0)

                        val matchingItems = remember(item.name, item.type, availableItems) {
                            val q = item.name.trim()
                            val curType = item.type.trim()
                            if (q.isBlank()) {
                                // When query is empty (user focused on field, tapped dropdown arrow, or clicked clear):
                                // Show items in curType if filtered, or show all available inventory items!
                                val baseList = if (curType.isNotBlank() && !curType.equals("All", ignoreCase = true)) {
                                    val filtered = availableItems.filter { it.type.trim().equals(curType, ignoreCase = true) }
                                    if (filtered.isNotEmpty()) filtered else availableItems
                                } else {
                                    availableItems
                                }
                                baseList.take(30)
                            } else {
                                // Query is not blank: first search with category constraint if set
                                val categoryFilterActive = curType.isNotBlank() && !curType.equals("All", ignoreCase = true)
                                val inCategoryMatches = if (categoryFilterActive) {
                                    availableItems.mapNotNull { dbItem ->
                                        if (!dbItem.type.trim().equals(curType, ignoreCase = true)) return@mapNotNull null
                                        val score = com.example.util.ItemSearchMatcher.matchScore(dbItem, q)
                                        if (score > 0) Pair(dbItem, score) else null
                                    }.sortedByDescending { it.second }.map { it.first }
                                } else emptyList()

                                if (inCategoryMatches.isNotEmpty()) {
                                    inCategoryMatches.take(25)
                                } else {
                                    // Either no category filter OR category filter yielded 0 results (e.g. user typed an item from a different category or mistyped)
                                    // Search ACROSS ALL ITEMS regardless of category!
                                    val allMatches = availableItems.mapNotNull { dbItem ->
                                        val score = com.example.util.ItemSearchMatcher.matchScore(dbItem, q)
                                        if (score > 0) Pair(dbItem, score) else null
                                    }.sortedByDescending { it.second }.map { it.first }

                                    allMatches.take(25)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Item Name Field with instant autocomplete, Voice Search, Clear button & Dropdown toggle
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = item.name,
                                        onValueChange = { name ->
                                            // When user changes/edits name, clear itemId so cost & search re-evaluate freshly
                                            lineItems[idx] = item.copy(name = name, itemId = null)
                                            isSuggestionsOpen = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { if (it.isFocused) isSuggestionsOpen = true },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("Item Name (type to search)")
                                                if (effectiveCost > 0.0) {
                                                    Text(
                                                        "• Cost: ₹${String.format(Locale.US, "%.2f", effectiveCost)}",
                                                        color = ext.warning,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.name.isNotBlank()) {
                                                    IconButton(
                                                        onClick = {
                                                            lineItems[idx] = item.copy(
                                                                name = "",
                                                                itemId = null,
                                                                unitPrice = 0.0,
                                                                costPrice = 0.0,
                                                                total = 0.0,
                                                                type = ""
                                                            )
                                                            isSuggestionsOpen = true
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Clear item name",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { isSuggestionsOpen = !isSuggestionsOpen },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        if (isSuggestionsOpen) Icons.Default.ArrowDropDown else Icons.Default.Search,
                                                        contentDescription = "Toggle suggestions list",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        voiceTargetIdx = idx
                                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                             putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                             putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                             putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak item name (e.g. Socket, Pipe, Elbow)...")
                                                         }
                                                         try {
                                                             speechLauncher.launch(intent)
                                                         } catch (e: Exception) {
                                                             // voice not supported
                                                         }
                                                     },
                                                     modifier = Modifier.size(36.dp)
                                                 ) {
                                                     Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = MaterialTheme.colorScheme.primary)
                                                 }
                                                 if (lineItems.size > 1) {
                                                     IconButton(
                                                         onClick = { lineItems.removeAt(idx) },
                                                         modifier = Modifier.size(36.dp)
                                                     ) {
                                                         Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                     }
                                                 }
                                             }
                                         },
                                         singleLine = true
                                     )
                                 }

                                 // Live Cost & Margin Indicator right below item name
                                 if (effectiveCost > 0.0) {
                                     Surface(
                                         shape = RoundedCornerShape(6.dp),
                                         color = ext.warning.copy(alpha = 0.12f),
                                         border = androidx.compose.foundation.BorderStroke(1.dp, ext.warning.copy(alpha = 0.4f)),
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .padding(top = 6.dp)
                                     ) {
                                         Row(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .padding(horizontal = 8.dp, vertical = 5.dp),
                                             horizontalArrangement = Arrangement.SpaceBetween,
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 horizontalArrangement = Arrangement.spacedBy(4.dp)
                                             ) {
                                                 Text(
                                                     "🏷️ Cost Price: ",
                                                     fontSize = 11.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     color = MaterialTheme.colorScheme.onSurface
                                                 )
                                                 Text(
                                                     "₹${String.format(Locale.US, "%.2f", effectiveCost)}",
                                                     fontSize = 12.sp,
                                                     fontWeight = FontWeight.ExtraBold,
                                                     color = ext.warning
                                                 )
                                                 if (matchedDbItem?.qty != null) {
                                                     Text(
                                                         " • In Stock: ${matchedDbItem.qty} ${matchedDbItem.unit}",
                                                         fontSize = 10.sp,
                                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                                     )
                                                 }
                                             }
                                             val unitMargin = item.unitPrice - effectiveCost
                                             val marginPct = if (effectiveCost > 0.0) (unitMargin / effectiveCost) * 100.0 else 0.0
                                             val isProfit = unitMargin >= 0.0
                                             Text(
                                                 text = if (item.unitPrice <= 0.0) "Rate not set" else if (isProfit) "Margin: +₹${String.format(Locale.US, "%.2f", unitMargin)} (+${marginPct.toInt()}%)" else "⚠️ Below Cost by ₹${String.format(Locale.US, "%.2f", -unitMargin)}",
                                                 fontSize = 11.sp,
                                                 fontWeight = FontWeight.Bold,
                                                 color = if (item.unitPrice <= 0.0) MaterialTheme.colorScheme.onSurfaceVariant else if (isProfit) ext.positive else ext.danger
                                             )
                                         }
                                     }
                                 }

                                 // Suggestions popup list with Cost Price and Selling Price prominently displayed
                                 if (isSuggestionsOpen) {
                                     Surface(
                                         shape = RoundedCornerShape(8.dp),
                                         shadowElevation = 6.dp,
                                         color = MaterialTheme.colorScheme.surface,
                                         border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .padding(top = 4.dp)
                                     ) {
                                         Column {
                                             Surface(
                                                 color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 Row(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(horizontal = 10.dp, vertical = 6.dp),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Text(
                                                         if (matchingItems.isNotEmpty()) {
                                                             if (item.name.isBlank()) "📦 All Inventory Items (tap to select)" else "💡 Matching Items (Cost & Selling Rate)"
                                                         } else "⚠️ No Direct Match for \"${item.name}\"",
                                                         fontSize = 11.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         color = if (matchingItems.isNotEmpty()) MaterialTheme.colorScheme.primary else ext.warning
                                                     )
                                                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                         Text(
                                                             "${if (matchingItems.isNotEmpty()) matchingItems.size else availableItems.size} items",
                                                             fontSize = 10.sp,
                                                             color = MaterialTheme.colorScheme.onSurfaceVariant
                                                         )
                                                         IconButton(
                                                             onClick = { isSuggestionsOpen = false },
                                                             modifier = Modifier.size(22.dp)
                                                         ) {
                                                             Icon(Icons.Default.Close, contentDescription = "Close suggestions", modifier = Modifier.size(14.dp))
                                                         }
                                                     }
                                                 }
                                             }

                                             if (matchingItems.isEmpty()) {
                                                 // When mistyped or no item matched query:
                                                 Column(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(horizontal = 10.dp, vertical = 8.dp),
                                                     horizontalAlignment = Alignment.CenterHorizontally
                                                 ) {
                                                     Text(
                                                         text = "No item found matching \"${item.name}\"",
                                                         fontSize = 12.sp,
                                                         fontWeight = FontWeight.SemiBold,
                                                         color = MaterialTheme.colorScheme.onSurface
                                                     )
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Text(
                                                         text = "Choose an item below from inventory or clear search:",
                                                         fontSize = 11.sp,
                                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                                     )
                                                     Spacer(modifier = Modifier.height(6.dp))
                                                     OutlinedButton(
                                                         onClick = {
                                                             lineItems[idx] = item.copy(
                                                                 name = "",
                                                                 itemId = null,
                                                                 unitPrice = 0.0,
                                                                 costPrice = 0.0,
                                                                 total = 0.0,
                                                                 type = ""
                                                             )
                                                         },
                                                         shape = RoundedCornerShape(6.dp),
                                                         modifier = Modifier.height(32.dp)
                                                     ) {
                                                         Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                                         Spacer(modifier = Modifier.width(4.dp))
                                                         Text("Clear & Browse All Items", fontSize = 11.sp)
                                                     }
                                                 }
                                                 HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                             }

                                             val displayList = if (matchingItems.isNotEmpty()) matchingItems else availableItems.take(25)
                                             displayList.forEach { matched ->
                                                 Row(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .clickable {
                                                             val curQty = if (item.qty <= 0.0) 1.0 else item.qty
                                                             val displayName = if (matched.size.isNotBlank() && !matched.name.contains(matched.size, ignoreCase = true)) {
                                                                 "${matched.name} ${matched.size}"
                                                             } else {
                                                                 matched.name
                                                             }
                                                             val updated = item.copy(
                                                                 itemId = matched.id,
                                                                 name = displayName,
                                                                 code = matched.code,
                                                                 type = if (matched.type.isNotBlank()) matched.type else item.type,
                                                                 unit = matched.unit.ifBlank { "pcs" },
                                                                 unitPrice = matched.price,
                                                                 costPrice = matched.cost,
                                                                 total = curQty * matched.price
                                                             )
                                                             lineItems[idx] = updated
                                                             if (matched.type.isNotBlank()) {
                                                                 lastSelectedType = matched.type
                                                             }
                                                             isSuggestionsOpen = false
                                                         }
                                                         .padding(horizontal = 10.dp, vertical = 8.dp),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                         ) {
                                                             Text(matched.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                             if (matched.size.isNotBlank()) {
                                                                 Surface(
                                                                     color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                                     shape = RoundedCornerShape(4.dp),
                                                                     border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                                 ) {
                                                                     Text(
                                                                         text = matched.size,
                                                                         fontSize = 10.sp,
                                                                         fontWeight = FontWeight.ExtraBold,
                                                                         color = MaterialTheme.colorScheme.primary,
                                                                         modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                     )
                                                                 }
                                                             }
                                                             // Item Cost Price badge right beside the item name!
                                                             if (matched.cost > 0.0) {
                                                                 Surface(
                                                                     color = ext.warning.copy(alpha = 0.14f),
                                                                     shape = RoundedCornerShape(4.dp),
                                                                     border = androidx.compose.foundation.BorderStroke(0.8.dp, ext.warning.copy(alpha = 0.5f))
                                                                 ) {
                                                                     Text(
                                                                         text = "Cost: ₹${String.format(Locale.US, "%.2f", matched.cost)}",
                                                                         fontSize = 10.sp,
                                                                         fontWeight = FontWeight.ExtraBold,
                                                                         color = ext.warning,
                                                                         modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                     )
                                                                 }
                                                             }
                                                         }
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                         ) {
                                                             if (matched.type.isNotBlank()) {
                                                                 Surface(
                                                                     color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                     shape = RoundedCornerShape(4.dp)
                                                                 ) {
                                                                     Text(
                                                                         text = matched.type,
                                                                         fontSize = 10.sp,
                                                                         fontWeight = FontWeight.Bold,
                                                                         color = MaterialTheme.colorScheme.primary,
                                                                         modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                     )
                                                                 }
                                                             }
                                                             Text("📦 Stock: ${matched.qty} ${matched.unit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                             if (matched.cost > 0.0 && matched.price > 0.0) {
                                                                 val margin = matched.price - matched.cost
                                                                 val marginPct = (margin / matched.cost * 100).toInt()
                                                                 Text(
                                                                     text = "• Margin: +₹${String.format(Locale.US, "%.0f", margin)} (+$marginPct%)",
                                                                     fontSize = 10.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     color = if (margin >= 0) ext.positive else ext.danger
                                                                 )
                                                             }
                                                         }
                                                     }
                                                     Column(horizontalAlignment = Alignment.End) {
                                                         Text(
                                                             "Sale: " + String.format(Locale.US, "₹%.2f", matched.price),
                                                             fontWeight = FontWeight.Bold,
                                                             fontSize = 13.sp,
                                                             color = MaterialTheme.colorScheme.primary
                                                         )
                                                         if (matched.cost > 0.0) {
                                                             Text(
                                                                 "Cost: " + String.format(Locale.US, "₹%.2f", matched.cost),
                                                                 fontWeight = FontWeight.ExtraBold,
                                                                 fontSize = 11.sp,
                                                                 color = ext.warning
                                                             )
                                                         }
                                                     }
                                                 }
                                                 HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                             }
                                         }
                                     }
                                 }

                                 Spacer(modifier = Modifier.height(6.dp))

                                // Type Selection Row beneath Item Name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Type: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(distinctTypes) { tOption ->
                                            val isSelected = if (tOption.equals("All", ignoreCase = true)) {
                                                item.type.trim().isEmpty() || item.type.equals("All", ignoreCase = true)
                                            } else {
                                                item.type.trim().equals(tOption.trim(), ignoreCase = true)
                                            }
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val newType = if (tOption.equals("All", ignoreCase = true)) "" else tOption
                                                    lineItems[idx] = item.copy(type = newType)
                                                    lastSelectedType = newType
                                                    isSuggestionsOpen = true
                                                },
                                                label = {
                                                    Text(
                                                        text = tOption,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Qty, Unit, Unit Price (Rate), and Total Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = if (item.qty == 0.0) "" else item.qty.toString(),
                                        onValueChange = { qStr ->
                                            val qVal = qStr.toDoubleOrNull() ?: 0.0
                                            val tot = qVal * item.unitPrice
                                            lineItems[idx] = item.copy(qty = qVal, total = tot)
                                        },
                                        label = { Text("Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    // Unit with Quick Multi-Unit Toggle
                                    Column(modifier = Modifier.weight(1.1f)) {
                                        OutlinedTextField(
                                            value = item.unit,
                                            onValueChange = { u -> lineItems[idx] = item.copy(unit = u) },
                                            label = { Text("Unit") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        // Unit Conversion Quick Pill
                                        val paired = com.example.util.UnitConversionHelper.getPairedUnit(item.unit)
                                        if (paired.first != item.unit) {
                                            Text(
                                                text = "⇄ ${paired.first.uppercase()}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable {
                                                        val newUnit = paired.first
                                                        val convertedQty = com.example.util.UnitConversionHelper.convertQuantity(item.unit, newUnit, item.qty, paired.second)
                                                        lineItems[idx] = item.copy(unit = newUnit, qty = convertedQty, total = convertedQty * item.unitPrice)
                                                    }
                                                    .padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = if (item.unitPrice == 0.0) "" else item.unitPrice.toString(),
                                        onValueChange = { rStr ->
                                            val rVal = rStr.toDoubleOrNull() ?: 0.0
                                            val tot = item.qty * rVal
                                            lineItems[idx] = item.copy(unitPrice = rVal, total = tot)
                                        },
                                        label = {
                                            Text(
                                                if (effectiveCost > 0.0) "Rate (Cost ₹${String.format(Locale.US, "%.0f", effectiveCost)})"
                                                else "Rate (₹)"
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format(Locale.US, "₹%.2f", item.total), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = discountText,
                                    onValueChange = { discountText = it },
                                    label = { Text("Discount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = taxPercentText,
                                    onValueChange = { taxPercentText = it },
                                    label = { Text("GST/Tax (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Quick Tax: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf("0", "5", "12", "18", "28").forEach { pct ->
                                    val isSelected = taxPercentText.trim() == pct
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable { taxPercentText = pct }
                                    ) {
                                        Text(
                                            text = if (pct == "0") "0%" else "$pct%",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Payment Terms") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                // Summary & Save Footer with proper generous padding
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalEstimateCost = lineItems.sumOf { itm ->
                            val c = if (itm.costPrice > 0.0) itm.costPrice else (availableItems.firstOrNull { it.id == itm.itemId || it.name.trim().equals(itm.name.trim(), ignoreCase = true) }?.cost ?: 0.0)
                            c * itm.qty
                        }
                        val totalEstimateProfit = (grandTotal - totalEstimateCost)

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subtotal: ₹${String.format(Locale.US, "%.2f", subtotal)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (totalEstimateCost > 0.0) {
                                val isPositive = totalEstimateProfit >= 0
                                Text(
                                    "Est. Cost: ₹${String.format(Locale.US, "%.2f", totalEstimateCost)} • Margin: ${if (isPositive) "+" else "-"}₹${String.format(Locale.US, "%.2f", kotlin.math.abs(totalEstimateProfit))}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) ext.positive else ext.danger
                                )
                            }
                            if (taxAmount > 0) Text("Tax ($taxPercent%): +₹${String.format(Locale.US, "%.2f", taxAmount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Grand Total: ₹${String.format(Locale.US, "%.2f", grandTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                if (customerName.isBlank()) return@Button
                                val validItems = lineItems.filter { it.name.isNotBlank() }
                                if (validItems.isEmpty()) return@Button

                                val safeDate = date.trim().ifBlank { todayStr }
                                val record = QuotationRecord(
                                    id = existingQuotation?.id ?: UUID.randomUUID().toString(),
                                    quotationNo = quoteNo.ifEmpty { nextQuoteNo },
                                    customerName = customerName.trim(),
                                    customerPhone = customerPhone.trim(),
                                    customerAddress = "",
                                    date = safeDate,
                                    validUntil = validUntil.trim(),
                                    itemsJson = InvoicePrintManager.toJson(validItems),
                                    subtotal = subtotal,
                                    discount = discount,
                                    taxPercent = taxPercent,
                                    taxAmount = taxAmount,
                                    grandTotal = grandTotal,
                                    status = status,
                                    notes = notes.trim(),
                                    createdAt = existingQuotation?.createdAt?.ifBlank { null }
                                        ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                                )
                                onSave(record)
                            },
                            enabled = customerName.isNotBlank() && lineItems.any { it.name.isNotBlank() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Estimate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
