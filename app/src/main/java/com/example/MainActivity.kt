package com.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.ui.CurrentScreen
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.components.InputPromptDialog
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.HomeScreen
import androidx.compose.material.icons.filled.ReceiptLong
import com.example.ui.screens.BillingHubScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.ItemFormDialog
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.QuotationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmoothSplashScreen
import com.example.ui.screens.StockTransactionDialog
import com.example.ui.screens.TransactionsScreen
import android.content.Intent
import android.net.Uri
import com.example.ui.screens.ColorMachineTintDialog
import com.example.ui.theme.StockManagerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StockViewModel by viewModels()

    private var nfcAdapter: android.nfc.NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingReportIntent(intent)
        handleNfcIntent(intent)
        setContent {
            val customThemeColor by viewModel.customThemeColor.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }
            StockManagerTheme(darkTheme = isDarkTheme, customThemeColor = customThemeColor) {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val filters = arrayOf(
                android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                    try { addDataType("*/*") } catch (_: Exception) {}
                },
                android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED),
                android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED)
            )
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, null)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingReportIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED) {
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG)
            }
            val tagId = tag?.id?.joinToString(":") { "%02X".format(it) } ?: "TAG-${System.currentTimeMillis() % 10000}"
            var payloadStr: String? = null
            val rawMsgs = intent.getParcelableArrayExtra(android.nfc.NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMsgs != null && rawMsgs.isNotEmpty()) {
                val msg = rawMsgs[0] as? android.nfc.NdefMessage
                val record = msg?.records?.firstOrNull()
                if (record != null) {
                    try {
                        payloadStr = String(record.payload, java.nio.charset.Charset.forName("UTF-8")).trim()
                    } catch (_: Exception) {}
                }
            }
            viewModel.onNfcTagScanned(tagId, payloadStr)
        }
    }

    private fun handleIncomingReportIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri: Uri? = when (action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.getItemAt(0)?.uri
            }
            Intent.ACTION_VIEW -> {
                intent.data ?: intent.clipData?.getItemAt(0)?.uri
            }
            else -> null
        }

        if (uri != null) {
            viewModel.loadExternalTintReport(this, uri)
        }
    }
}

@Composable
fun MainAppRoot(viewModel: StockViewModel) {
    var showSplash by remember { mutableStateOf(true) }
    val selectedLogo by viewModel.selectedLogo.collectAsState()

    if (showSplash) {
        SmoothSplashScreen(
            selectedLogo = selectedLogo,
            onAnimationFinished = { showSplash = false }
        )
        return
    }

    val isAuthed by viewModel.isAuthed.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val incomingTintReport by viewModel.incomingTintReport.collectAsState()

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var itemForForm by remember { mutableStateOf<Item?>(null) }
    var showItemForm by remember { mutableStateOf(false) }
    var itemForTransaction by remember { mutableStateOf<Item?>(null) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }

    // Gear triple tap tracking
    var gearTapCount by remember { mutableIntStateOf(0) }
    var lastGearTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(isAuthed) {
        if (isAuthed) {
            viewModel.reconnectRealtime()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, isAuthed) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isAuthed) {
                viewModel.reconnectRealtime()
                viewModel.reloadFromDatabase()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isAuthed) {
        val handled = viewModel.navigateBack()
        if (!handled && currentTab != NavigationTab.ITEMS) {
            viewModel.navigateTo(NavigationTab.ITEMS)
        }
    }

    if (!isAuthed) {
        AuthGateScreen(viewModel = viewModel)
        return
    }

    val navBarStyle by viewModel.navBarStyle.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
            if (currentScreen is CurrentScreen.Main) {
                val inSelectionMode = currentTab == NavigationTab.ITEMS && isSelectionMode && selectedItemIds.isNotEmpty()
                if (!inSelectionMode) {
                    val tabs = listOf(
                        Triple(NavigationTab.HOME, "Home", Icons.Default.Home),
                        Triple(NavigationTab.ITEMS, "Stock", Icons.Default.Inventory2),
                        Triple(NavigationTab.BILLING, "Billing", Icons.Default.ReceiptLong),
                        Triple(NavigationTab.TRANSACTIONS, "History", Icons.AutoMirrored.Filled.CompareArrows),
                        Triple(NavigationTab.SETTINGS, "Settings", Icons.Default.Settings)
                    )

                    val navBarContent: @Composable () -> Unit = {
                        NavigationBar(
                            modifier = if (navBarStyle == "floating_pill") Modifier.padding(4.dp) else Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                            containerColor = if (navBarStyle == "floating_pill") Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            tonalElevation = if (navBarStyle == "floating_pill") 0.dp else 8.dp
                        ) {
                            tabs.forEach { (tab, label, icon) ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab == NavigationTab.SETTINGS) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastGearTapTime < 600) {
                                                gearTapCount++
                                                if (gearTapCount >= 3) {
                                                    if (isAdmin) {
                                                        viewModel.authManager.exitAdmin()
                                                        viewModel.showToast("Admin mode off")
                                                    } else {
                                                        showAdminPinDialog = true
                                                    }
                                                    gearTapCount = 0
                                                }
                                            } else {
                                                gearTapCount = 1
                                            }
                                            lastGearTapTime = now
                                        }
                                        viewModel.navigateTo(tab)
                                    },
                                    icon = {
                                        if (tab == NavigationTab.SETTINGS && isAdmin) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(6.dp)
                                                    )
                                                }
                                            ) {
                                                Icon(icon, contentDescription = label)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = label)
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color(0xFF8E8E93),
                                        unselectedTextColor = Color(0xFF8E8E93),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    if (navBarStyle == "floating_pill") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                tonalElevation = 12.dp,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                navBarContent()
                            }
                        }
                    } else {
                        navBarContent()
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is CurrentScreen.ItemDetail -> {
                    ItemDetailScreen(
                        itemId = screen.itemId,
                        viewModel = viewModel,
                        onEditItem = {
                            itemForForm = it
                            showItemForm = true
                        },
                        onOpenTransactionDialog = {
                            itemForTransaction = it
                            showTransactionDialog = true
                        }
                    )
                }

                is CurrentScreen.BillFlow -> {
                    BillScreen(viewModel = viewModel)
                }

                is CurrentScreen.GstCalculator -> {
                    com.example.ui.screens.GstCalculatorScreen(viewModel = viewModel)
                }

                is CurrentScreen.SmartSynonymSearch -> {
                    com.example.ui.screens.SmartSynonymSearchScreen(
                        viewModel = viewModel,
                        onOpenItemDetail = { itemId ->
                            viewModel.openItemDetail(itemId)
                        }
                    )
                }

                is CurrentScreen.LedgerAccountDetail -> {
                    val accounts by viewModel.allLedgerAccounts.collectAsState()
                    val targetAcc = accounts.find { it.id == screen.accountId }
                    if (targetAcc != null) {
                        com.example.ui.screens.LedgerAccountDetailScreen(
                            account = targetAcc,
                            viewModel = viewModel,
                            onBack = { viewModel.navigateToKhata() }
                        )
                    } else {
                        BillingHubScreen(viewModel = viewModel)
                    }
                }

                is CurrentScreen.SupplierPriceComparison -> {
                    com.example.ui.screens.SupplierPriceComparisonScreen(
                        preselectedItemId = screen.preselectedItemId,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is CurrentScreen.NfcShelfBinManager -> {
                    com.example.ui.screens.NfcShelfBinScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is CurrentScreen.Main -> {
                    when (currentTab) {
                        NavigationTab.HOME -> HomeScreen(viewModel = viewModel)
                        NavigationTab.ITEMS -> ItemsScreen(
                            viewModel = viewModel,
                            onOpenItemForm = {
                                itemForForm = it
                                showItemForm = true
                            }
                        )
                        NavigationTab.BILLING -> BillingHubScreen(viewModel = viewModel)
                        NavigationTab.TRANSACTIONS -> TransactionsScreen(viewModel = viewModel)
                        NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Add / Edit Item Dialog
    if (showItemForm) {
        ItemFormDialog(
            itemToEdit = itemForForm,
            viewModel = viewModel,
            onDismiss = {
                showItemForm = false
                itemForForm = null
            }
        )
    }

    // Quick Transaction Dialog
    if (showTransactionDialog && itemForTransaction != null) {
        StockTransactionDialog(
            item = itemForTransaction!!,
            viewModel = viewModel,
            onDismiss = {
                showTransactionDialog = false
                itemForTransaction = null
            }
        )
    }

    // Admin PIN shortcut dialog
    if (showAdminPinDialog) {
        InputPromptDialog(
            title = "Admin access",
            message = "Enter the admin PIN to reveal restricted actions.",
            placeholder = "PIN",
            confirmText = "Unlock",
            isPassword = true,
            onConfirm = { pin ->
                val ok = viewModel.authManager.unlockAdmin(pin)
                if (ok) {
                    viewModel.showToast("Admin mode on")
                    showAdminPinDialog = false
                } else {
                    viewModel.showToast("Wrong PIN")
                }
            },
            onDismiss = { showAdminPinDialog = false }
        )
    }

    // Color Machine Tint Report Review (opened from external app or intent)
    if (incomingTintReport != null) {
        ColorMachineTintDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearIncomingTintReport()
            }
        )
    }
}
