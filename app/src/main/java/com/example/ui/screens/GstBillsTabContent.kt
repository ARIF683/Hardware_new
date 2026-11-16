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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GstBillRecord
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.InvoicePrintManager
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GstBillsTabContent(
    viewModel: StockViewModel,
    onOpenGstCalculator: () -> Unit,
    onOpenEstimates: () -> Unit = { viewModel.setBillingKhataSubTab(1) },
    onOpenKhata: () -> Unit = { viewModel.setBillingKhataSubTab(3) }
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()
    val allBills by viewModel.allGstBills.collectAsState()
    val ledgerAccounts by viewModel.allLedgerAccounts.collectAsState()
    val uiConfig by viewModel.uiConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All") } // All, Paid, Pending, This Month, Today

    var billForDetails by remember { mutableStateOf<GstBillRecord?>(null) }
    var billToDelete by remember { mutableStateOf<GstBillRecord?>(null) }
    var isProcessingPdf by remember { mutableStateOf(false) }

    val todayDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val thisMonthPrefix = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date()) }
    val inrFormat = remember {
        NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val isBillPending: (GstBillRecord) -> Boolean = { bill ->
        bill.status.contains("pending", ignoreCase = true) ||
            bill.status.contains("unpaid", ignoreCase = true) ||
            bill.status.contains("due", ignoreCase = true) ||
            bill.status.contains("credit", ignoreCase = true)
    }

    val paidCount = remember(allBills) { allBills.count { !isBillPending(it) } }
    val pendingCount = remember(allBills) { allBills.count { isBillPending(it) } }
    val monthCount = remember(allBills, thisMonthPrefix) {
        allBills.count { it.invoiceDate.endsWith(thisMonthPrefix) }
    }
    val todayCount = remember(allBills, todayDateStr) {
        allBills.count { it.invoiceDate == todayDateStr || it.createdAt.startsWith(todayDateStr) }
    }

    val filteredBills = remember(allBills, searchQuery, filterMode, todayDateStr, thisMonthPrefix) {
        allBills.filter { bill ->
            val q = searchQuery.trim()
            val matchesSearch = q.isBlank() ||
                bill.invoiceNo.contains(q, ignoreCase = true) ||
                bill.customerName.contains(q, ignoreCase = true) ||
                bill.customerPhone.contains(q, ignoreCase = true) ||
                bill.itemsJson.contains(q, ignoreCase = true) ||
                (bill.quotationNo ?: "").contains(q, ignoreCase = true)

            val matchesFilter = when (filterMode) {
                "Paid" -> !isBillPending(bill)
                "Pending" -> isBillPending(bill)
                "This Month" -> bill.invoiceDate.endsWith(thisMonthPrefix)
                "Today" -> bill.invoiceDate == todayDateStr || bill.createdAt.startsWith(todayDateStr)
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val totalSales = remember(allBills) { allBills.sumOf { it.grandTotal } }
    val totalTaxable = remember(allBills) { allBills.sumOf { it.taxableTotal } }
    val totalTax = remember(allBills) { allBills.sumOf { it.totalTaxAmount } }

    val customerCreditDue = remember(ledgerAccounts) {
        ledgerAccounts.filter { it.type != "SUPPLIER" && it.netBalance > 0 }.sumOf { it.netBalance }
    }
    val dueCustomersCount = remember(ledgerAccounts) {
        ledgerAccounts.count { it.type != "SUPPLIER" && it.netBalance > 0 }
    }
    val pendingBillsTotal = remember(allBills) {
        allBills.filter { isBillPending(it) }.sumOf { it.grandTotal }
    }
    val totalReceivables = customerCreditDue + pendingBillsTotal

    val storeTitle = uiConfig.theme.storeTitle.ifBlank { "HARDWARE & TOOLS STORE" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Billing & Invoices Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.22f else 0.14f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GST Active • Live Register",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Billing & Invoices",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Track counter receipts, credit ledger & POs",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .then(
                                    Modifier.padding(1.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Dual KPI Cards: TOTAL SALES & RECEIVABLES
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Sales Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL SALES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ext.positive.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = ext.positive,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${inrFormat.format(totalSales)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Taxable ₹${inrFormat.format(totalTaxable)} • GST ₹${inrFormat.format(totalTax)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ext.positive,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$monthCount this mo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${allBills.size} Invoices",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Receivables Card (tap jumps to Khata tab)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenKhata() }
                        .testTag("billing_receivables_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECEIVABLES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ext.warning.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = ext.warning,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${inrFormat.format(totalReceivables)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ext.warning,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pendingCount bills • $dueCustomersCount khata",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ext.warning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Khata Credit",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Open Khata ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalReceivables > 0) ext.danger else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 3. Primary Action Buttons: Create Invoice | Supplier PO
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenGstCalculator,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("billing_create_invoice_button")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Invoice", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.openBillFlow() },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("billing_supplier_po_button")
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        tint = ext.info,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Supplier PO", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 4. Filter Pills Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    BillingFilterPill(
                        label = "All Bills",
                        count = allBills.size,
                        dotColor = null,
                        isSelected = filterMode == "All",
                        onClick = { filterMode = "All" },
                        testTag = "billing_filter_all"
                    )
                }
                item {
                    BillingFilterPill(
                        label = "Paid",
                        count = paidCount,
                        dotColor = ext.positive,
                        isSelected = filterMode == "Paid",
                        onClick = { filterMode = "Paid" },
                        testTag = "billing_filter_paid"
                    )
                }
                if (pendingCount > 0) {
                    item {
                        BillingFilterPill(
                            label = "Pending",
                            count = pendingCount,
                            dotColor = ext.warning,
                            isSelected = filterMode == "Pending",
                            onClick = { filterMode = "Pending" },
                            testTag = "billing_filter_pending"
                        )
                    }
                }
                item {
                    BillingFilterPill(
                        label = "This Month",
                        count = monthCount,
                        dotColor = ext.warning,
                        isSelected = filterMode == "This Month",
                        onClick = { filterMode = "This Month" },
                        testTag = "billing_filter_month"
                    )
                }
                item {
                    BillingFilterPill(
                        label = "Today",
                        count = todayCount,
                        dotColor = ext.info,
                        isSelected = filterMode == "Today",
                        onClick = { filterMode = "Today" },
                        testTag = "billing_filter_today"
                    )
                }
                item {
                    BillingFilterPill(
                        label = "Quotations",
                        count = null,
                        dotColor = ext.info,
                        isSelected = false,
                        onClick = onOpenEstimates,
                        testTag = "billing_filter_quotations"
                    )
                }
            }
        }

        // 5. Recent Invoices Section Header + Search Input
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recent Invoices",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "${filteredBills.size} of ${allBills.size} shown",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by customer, bill # or item...",
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
                                Icon(Icons.Default.Close, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("billing_invoice_search")
                )
            }
        }

        // 6. Invoice List or Empty State
        if (filteredBills.isEmpty()) {
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
                            text = if (searchQuery.isNotBlank() || filterMode != "All") "No matching invoices found" else "No GST invoices yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Create Invoice to generate a bill or convert an approved estimate.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onOpenGstCalculator,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create First Invoice", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(filteredBills, key = { it.id }) { bill ->
                InvoiceSummaryCard(
                    bill = bill,
                    isPending = isBillPending(bill),
                    inrFormat = inrFormat,
                    onOpenDetails = { billForDetails = bill },
                    onPrintPdf = {
                        scope.launch {
                            isProcessingPdf = true
                            try {
                                val bInfo = viewModel.bankingInfo.value
                                val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                                val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                                val pdfFile = InvoicePrintManager.createGstBillPdf(
                                    context = context,
                                    bill = bill,
                                    storeName = storeTitle,
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
                    onSharePdf = {
                        scope.launch {
                            isProcessingPdf = true
                            try {
                                val bInfo = viewModel.bankingInfo.value
                                val sig = viewModel.bankingPreferenceManager.getSignatureBitmap()
                                val qr = viewModel.bankingPreferenceManager.getQrCodeBitmap()
                                val pdfFile = InvoicePrintManager.createGstBillPdf(
                                    context = context,
                                    bill = bill,
                                    storeName = storeTitle,
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
                    onRemind = {
                        if (bill.customerPhone.isNotBlank()) {
                            val msg = "Hello ${bill.customerName}, gentle reminder for Invoice #${bill.invoiceNo} (₹${String.format(Locale.US, "%.2f", bill.grandTotal)}) with $storeTitle."
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=${bill.customerPhone.replace("+", "").replace(" ", "")}&text=${Uri.encode(msg)}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                viewModel.showToast("Could not open WhatsApp")
                            }
                        } else {
                            viewModel.showToast("No customer phone number on this bill")
                        }
                    },
                    onDelete = { billToDelete = bill }
                )
            }
        }

        // 7. Bottom GST Filing Ready Export Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.14f else 0.10f)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GST Filing Ready",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (filteredBills.isEmpty()) "Generate bills to export GSTR-1 CSV"
                                    else "GSTR-1 report (${filteredBills.size} bills) ready for export",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (filteredBills.isEmpty()) {
                                    viewModel.showToast("No invoices available to export")
                                } else {
                                    try {
                                        InvoicePrintManager.exportGstr1CsvAndShare(
                                            context = context,
                                            bills = filteredBills,
                                            storeName = storeTitle
                                        )
                                        viewModel.showToast("Exported GSTR-1 CSV for ${filteredBills.size} bills")
                                    } catch (e: Exception) {
                                        viewModel.showToast("Export failed: ${e.message}")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("billing_export_gstr1_button")
                        ) {
                            Text("Export", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        Text(
                            "Invoice #: ${b.invoiceNo}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
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
                                    Text(
                                        "Date: ${b.invoiceDate}  •  Type: ${if (b.isInterState) "IGST (Inter-State)" else "CGST+SGST (Intra-State)"}",
                                        fontSize = 11.sp
                                    )
                                    if (!b.quotationNo.isNullOrBlank()) {
                                        Text(
                                            "Converted from Estimate #${b.quotationNo}",
                                            fontSize = 11.sp,
                                            color = ext.purple,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "Line Items (${parsedItems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

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
                                            color = ext.warning
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
                                            Text("IGST:", fontSize = 12.sp, color = ext.warning)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ext.warning)
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("CGST (Half):", fontSize = 11.sp, color = ext.warning)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount / 2.0), fontSize = 11.sp, color = ext.warning)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("SGST (Half):", fontSize = 11.sp, color = ext.warning)
                                            Text(String.format(Locale.US, "₹%.2f", b.totalTaxAmount / 2.0), fontSize = 11.sp, color = ext.warning)
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
                                storeName = storeTitle,
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
                                storeName = storeTitle,
                                bankingInfo = bInfo,
                                signatureBitmap = sig,
                                qrCodeBitmap = qr
                            )
                            InvoicePrintManager.sharePdf(context, pdfFile, "GST Invoice #${b.invoiceNo}")
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.positive)
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
                    colors = ButtonDefaults.buttonColors(containerColor = ext.danger)
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

@Composable
private fun BillingFilterPill(
    label: String,
    count: Int?,
    dotColor: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
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
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            if (count != null) {
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

@Composable
private fun InvoiceSummaryCard(
    bill: GstBillRecord,
    isPending: Boolean,
    inrFormat: NumberFormat,
    onOpenDetails: () -> Unit,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onRemind: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val lineItems = remember(bill.itemsJson) { InvoicePrintManager.parseGstLineItems(bill.itemsJson) }
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = if (isPending) ext.warning else ext.positive
    val iconTint = when {
        isPending -> ext.warning
        bill.isInterState -> ext.info
        bill.customerName.contains("walk", ignoreCase = true) -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> ext.positive
    }
    val leadingIcon = when {
        isPending -> Icons.Default.Build
        bill.isInterState -> Icons.Default.Storefront
        bill.customerName.contains("walk", ignoreCase = true) -> Icons.Default.Person
        else -> Icons.Default.Storefront
    }

    val itemNamesPreview = remember(lineItems) {
        if (lineItems.isEmpty()) "GST Tax Invoice"
        else lineItems.joinToString(", ") { it.name }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenDetails() }
            .testTag("billing_invoice_card_${bill.invoiceNo}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconTint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bill.customerName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "#${bill.invoiceNo}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${lineItems.size} ${if (lineItems.size == 1) "item" else "items"} • $itemNamesPreview",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${inrFormat.format(bill.grandTotal)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.14f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = bill.status.ifBlank { "Paid" },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

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
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = bill.invoiceDate,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!bill.quotationNo.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ext.purple.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "#${bill.quotationNo}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.purple,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isPending && bill.customerPhone.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRemind() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Remind",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Remind",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPrintPdf() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "PDF",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSharePdf() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Invoice Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Details") },
                                onClick = {
                                    showMenu = false
                                    onOpenDetails()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print A4 PDF") },
                                onClick = {
                                    showMenu = false
                                    onPrintPdf()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Bill") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ext.danger)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
