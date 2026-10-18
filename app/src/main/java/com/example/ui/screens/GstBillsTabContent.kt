package com.example.ui.screens

import androidx.compose.material3.MaterialTheme

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.InvoicePrintManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GstBillsTabContent(
    viewModel: StockViewModel,
    onOpenGstCalculator: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allBills by viewModel.allGstBills.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterPeriod by remember { mutableStateOf("All") } // All, Today, This Month

    var billForDetails by remember { mutableStateOf<GstBillRecord?>(null) }
    var billToDelete by remember { mutableStateOf<GstBillRecord?>(null) }
    var isProcessingPdf by remember { mutableStateOf(false) }

    val todayDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val thisMonthPrefix = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date()) }

    // Filter bills
    val filteredBills = remember(allBills, searchQuery, filterPeriod) {
        allBills.filter { bill ->
            val matchesSearch = searchQuery.isBlank() ||
                    bill.invoiceNo.contains(searchQuery, ignoreCase = true) ||
                    bill.customerName.contains(searchQuery, ignoreCase = true) ||
                    bill.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    (bill.quotationNo ?: "").contains(searchQuery, ignoreCase = true)

            val matchesPeriod = when (filterPeriod) {
                "Today" -> bill.invoiceDate == todayDateStr || bill.createdAt.startsWith(todayDateStr)
                "This Month" -> bill.invoiceDate.endsWith(thisMonthPrefix)
                else -> true
            }

            matchesSearch && matchesPeriod
        }
    }

    // Financial totals for the filtered bills
    val totalTaxable = filteredBills.sumOf { it.taxableTotal }
    val totalTax = filteredBills.sumOf { it.totalTaxAmount }
    val totalGrand = filteredBills.sumOf { it.grandTotal }

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card at top
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header + "+ New GST Bill" Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GST Tax Invoices & Bills",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredBills.size} bill(s) recorded in database",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onOpenGstCalculator,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ New GST Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Financial Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Taxable
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Taxable Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "₹%.2f", totalTaxable),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Total GST Tax
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarningAmber.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, WarningAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Total GST Tax", fontSize = 10.sp, color = WarningAmber)
                            Text(
                                text = String.format(Locale.US, "₹%.2f", totalTax),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                        }
                    }

                    // Total Revenue
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Grand Total", fontSize = 10.sp, color = SuccessGreen)
                            Text(
                                text = String.format(Locale.US, "₹%.2f", totalGrand),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        // Search & Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search invoice, customer, phone...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            )

            // Period Filter Chips
            listOf("All", "Today", "This Month").forEach { period ->
                val isSelected = filterPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { filterPeriod = period },
                    label = { Text(period, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Bill List
        if (filteredBills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || filterPeriod != "All") "No matching GST bills found" else "No GST bills generated yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate a new GST invoice or convert an estimate from the Quotations tab.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenGstCalculator,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open GST Calculator", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBills, key = { it.id }) { bill ->
                    val lineItems = remember(bill.itemsJson) {
                        InvoicePrintManager.parseGstLineItems(bill.itemsJson)
                    }
                    val isEWay = bill.grandTotal > 50000.0

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Invoice #, Badges & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = bill.invoiceNo,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (bill.isInterState) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0284C7).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "IGST",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0284C7),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = SuccessGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "CGST+SGST",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = bill.invoiceDate,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Converted from Quotation Badge
                            if (!bill.quotationNo.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF9333EA).copy(alpha = 0.10f),
                                    border = BorderStroke(0.5.dp, Color(0xFF9333EA).copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF9333EA),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "From Estimate #${bill.quotationNo}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9333EA)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Customer & Financial Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bill.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (bill.customerPhone.isNotBlank()) {
                                        Text(
                                            text = "📞 ${bill.customerPhone}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (bill.customerGstin.isNotBlank()) {
                                        Text(
                                            text = "GSTIN: ${bill.customerGstin}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${lineItems.size} item(s) • Taxable: ₹${String.format(Locale.US, "%.2f", bill.taxableTotal)} + Tax: ₹${String.format(Locale.US, "%.2f", bill.totalTaxAmount)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Grand Total",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(Locale.US, "₹%.2f", bill.grandTotal),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (isEWay) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = WarningAmber.copy(alpha = 0.15f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = "🚚 E-Way Bill Rule 138",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningAmber,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // View Details
                                    IconButton(
                                        onClick = { billForDetails = bill },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = "View Details",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Print A4 PDF
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                isProcessingPdf = true
                                                try {
                                                    val bInfo = viewModel.bankingInfo.value
                                                    val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                                                    val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                                                    val pdfFile = InvoicePrintManager.createGstBillPdf(
                                                        context = context,
                                                        bill = bill,
                                                        storeName = viewModel.uiConfig.value.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" },
                                                        bankingInfo = bInfo,
                                                        signatureBitmap = sig,
                                                        qrCodeBitmap = qr
                                                    )
                                                    InvoicePrintManager.printPdf(context, pdfFile, "GST_${bill.invoiceNo}")
                                                } catch (e: Exception) {
                                                    viewModel.showToast("Print error: ${e.message}")
                                                } finally {
                                                    isProcessingPdf = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = "Print PDF",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Share PDF
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                isProcessingPdf = true
                                                try {
                                                    val bInfo = viewModel.bankingInfo.value
                                                    val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                                                    val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                                                    val pdfFile = InvoicePrintManager.createGstBillPdf(
                                                        context = context,
                                                        bill = bill,
                                                        storeName = viewModel.uiConfig.value.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" },
                                                        bankingInfo = bInfo,
                                                        signatureBitmap = sig,
                                                        qrCodeBitmap = qr
                                                    )
                                                    InvoicePrintManager.sharePdf(context, pdfFile, "GST Invoice #${bill.invoiceNo}")
                                                } catch (e: Exception) {
                                                    viewModel.showToast("Share error: ${e.message}")
                                                } finally {
                                                    isProcessingPdf = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share PDF",
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete Bill
                                    IconButton(
                                        onClick = { billToDelete = bill },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = DangerRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "✓ Saved & Synced",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bill Details Dialog
    if (billForDetails != null) {
        val b = billForDetails!!
        val parsedItems = remember(b.itemsJson) {
            InvoicePrintManager.parseGstLineItems(b.itemsJson)
        }

        AlertDialog(
            onDismissRequest = { billForDetails = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("GST Tax Invoice Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Invoice #: ${b.invoiceNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { billForDetails = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Meta Info
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Customer: ${b.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (b.customerPhone.isNotBlank()) Text("Phone: ${b.customerPhone}", fontSize = 11.sp)
                                    if (b.customerGstin.isNotBlank()) Text("Customer GSTIN: ${b.customerGstin}", fontSize = 11.sp)
                                    if (b.customerAddress.isNotBlank()) Text("Address: ${b.customerAddress}", fontSize = 11.sp)
                                    Text("Date: ${b.invoiceDate}  •  Type: ${if (b.isInterState) "IGST (Inter-State)" else "CGST+SGST (Intra-State)"}", fontSize = 11.sp)
                                    if (!b.quotationNo.isNullOrBlank()) {
                                        Text("Converted from Estimate #${b.quotationNo}", fontSize = 11.sp, color = Color(0xFF9333EA), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Items Header
                        item {
                            Text("Line Items (${parsedItems.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        // Items List
                        items(parsedItems) { item ->
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("HSN: ${item.hsn.ifBlank { "8481" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Qty: ${item.qty} ${item.unit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("@ ₹${String.format(Locale.US, "%.2f", item.rate)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            "Tax ${item.taxRatePercent}% (₹${String.format(Locale.US, "%.2f", item.taxAmount)})",
                                            fontSize = 10.sp,
                                            color = WarningAmber
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.US, "₹%.2f", item.totalAmount),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Financial Breakup
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Taxable Total:", fontSize = 12.sp)
                                        Text(String.format(Locale.US, "₹%.2f", b.taxableTotal), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                    if (b.isInterState) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("IGST:", fontSize = 12.sp, color = WarningAmber)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = WarningAmber)
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("CGST (Half):", fontSize = 11.sp, color = WarningAmber)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount / 2.0), fontSize = 11.sp, color = WarningAmber)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("SGST (Half):", fontSize = 11.sp, color = WarningAmber)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount / 2.0), fontSize = 11.sp, color = WarningAmber)
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Grand Total:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(String.format(Locale.US, "₹%.2f", b.grandTotal), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val bInfo = viewModel.bankingInfo.value
                            val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                            val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                            val pdfFile = InvoicePrintManager.createGstBillPdf(
                                context = context,
                                bill = b,
                                storeName = viewModel.uiConfig.value.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" },
                                bankingInfo = bInfo,
                                signatureBitmap = sig,
                                qrCodeBitmap = qr
                            )
                            InvoicePrintManager.printPdf(context, pdfFile, "GST_${b.invoiceNo}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print A4 PDF")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val bInfo = viewModel.bankingInfo.value
                            val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                            val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                            val pdfFile = InvoicePrintManager.createGstBillPdf(
                                context = context,
                                bill = b,
                                storeName = viewModel.uiConfig.value.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" },
                                bankingInfo = bInfo,
                                signatureBitmap = sig,
                                qrCodeBitmap = qr
                            )
                            InvoicePrintManager.sharePdf(context, pdfFile, "GST Invoice #${b.invoiceNo}")
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (billToDelete != null) {
        val b = billToDelete!!
        AlertDialog(
            onDismissRequest = { billToDelete = null },
            title = { Text("Delete GST Bill?") },
            text = { Text("Are you sure you want to delete GST Bill #${b.invoiceNo} for ${b.customerName}? This will remove it from stored billing records.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGstBill(b.id)
                        billToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { billToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
