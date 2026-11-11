package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StockViewModel

@Composable
fun BillingHubScreen(viewModel: StockViewModel) {
    val selectedSubTab by viewModel.billingKhataSubTab.collectAsState()
    val quotations by viewModel.allQuotations.collectAsState()
    val gstBills by viewModel.allGstBills.collectAsState()
    val ledgerAccounts by viewModel.allLedgerAccounts.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Segmented Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab.coerceIn(0, 3),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val tabIdx = selectedSubTab.coerceIn(0, tabPositions.size - 1)
                    if (tabIdx < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[tabIdx]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                // Tab 0: Quick POS Billing Register
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { viewModel.setBillingKhataSubTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selectedSubTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "⚡ POS Register",
                                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                )

                // Tab 1: Estimates & Quotes
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { viewModel.setBillingKhataSubTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selectedSubTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Estimates (${quotations.size})",
                                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                )

                // Tab 2: GST Invoices & Bills
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { viewModel.setBillingKhataSubTab(2) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selectedSubTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "GST Bills (${gstBills.size})",
                                fontWeight = if (selectedSubTab == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                )

                // Tab 3: Supplier & Khata Ledger
                Tab(
                    selected = selectedSubTab == 3,
                    onClick = { viewModel.setBillingKhataSubTab(3) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selectedSubTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Khata (${ledgerAccounts.size})",
                                fontWeight = if (selectedSubTab == 3) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                )
            }
        }

        // Body Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedSubTab) {
                0 -> QuickBillingPosScreen(viewModel = viewModel)
                1 -> QuotationScreen(viewModel = viewModel)
                2 -> GstBillsTabContent(viewModel = viewModel, onOpenGstCalculator = { viewModel.openGstCalculator() })
                else -> LedgerScreen(viewModel = viewModel)
            }
        }
    }
}
