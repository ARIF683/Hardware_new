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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.UpdateStatus
import com.example.data.model.Item
import com.example.ui.CurrentScreen
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.components.InputPromptDialog
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.HomeScreen
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
import com.example.util.rememberAppHaptics

class MainActivity : ComponentActivity() {
    private val viewModel: StockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingReportIntent(intent)
        setContent {
            val effectiveThemeColor by viewModel.effectiveThemeColor.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }
            StockManagerTheme(darkTheme = isDarkTheme, customThemeColor = effectiveThemeColor) {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingReportIntent(intent)
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
            if (com.example.data.auth.AuthManager.isAuthDeepLink(uri)) {
                viewModel.authManager.handleAuthDeepLink(uri)
            } else {
                viewModel.loadExternalTintReport(this, uri)
            }
        }
    }
}

@Composable
fun MainAppRoot(viewModel: StockViewModel) {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SmoothSplashScreen(
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
    val haptics = rememberAppHaptics()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val dismissedUpdateTag by viewModel.dismissedUpdateTag.collectAsState()
    val availableUpdate = (updateStatus as? UpdateStatus.Available)
        ?.takeIf { it.info.tagName != dismissedUpdateTag }

    // Dialog states
    var itemForForm by remember { mutableStateOf<Item?>(null) }
    var showItemForm by remember { mutableStateOf(false) }
    var itemForTransaction by remember { mutableStateOf<Item?>(null) }
    var transactionInitialAction by remember { mutableStateOf("in") }
    var showTransactionDialog by remember { mutableStateOf(false) }

    if (availableUpdate != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog(availableUpdate.info.tagName) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(text = "New Update Available (${availableUpdate.info.tagName})")
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = availableUpdate.info.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = availableUpdate.info.releaseNotes.take(300),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.dismissUpdateDialog(availableUpdate.info.tagName)
                        viewModel.startDownloadAndInstall(availableUpdate.info.apkDownloadUrl)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install Now (1 Tap)")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.dismissUpdateDialog(availableUpdate.info.tagName) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Later")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(isAuthed) {
        if (isAuthed) {
            viewModel.ensureRealtimeConnected()
            viewModel.refreshCurrentUserRole()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, isAuthed) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkForUpdatesOnResume()
                if (isAuthed) {
                    viewModel.ensureRealtimeConnected()
                    viewModel.reloadFromDatabase()
                    viewModel.refreshCurrentUserRole()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isAuthed) {
        val handled = viewModel.navigateBack()
        if (!handled && currentTab != NavigationTab.HOME) {
            viewModel.navigateTo(NavigationTab.HOME)
        }
    }

    if (!isAuthed) {
        AuthGateScreen(viewModel = viewModel)
        return
    }

    val navBarStyle by viewModel.navBarStyle.collectAsState()

    data class NavTabSpec(
        val tab: NavigationTab,
        val label: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentScreen is CurrentScreen.Main) {
                val inSelectionMode = currentTab == NavigationTab.ITEMS && isSelectionMode && selectedItemIds.isNotEmpty()
                if (!inSelectionMode) {
                    val tabs = listOf(
                        NavTabSpec(
                            tab = NavigationTab.HOME,
                            label = "Home",
                            selectedIcon = Icons.Filled.GridView,
                            unselectedIcon = Icons.Outlined.GridView
                        ),
                        NavTabSpec(
                            tab = NavigationTab.ITEMS,
                            label = "Items",
                            selectedIcon = Icons.Filled.Inventory2,
                            unselectedIcon = Icons.Outlined.Inventory2
                        ),
                        NavTabSpec(
                            tab = NavigationTab.BILLING,
                            label = "Billing",
                            selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
                            unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong
                        ),
                        NavTabSpec(
                            tab = NavigationTab.SETTINGS,
                            label = "Settings",
                            selectedIcon = Icons.Filled.Settings,
                            unselectedIcon = Icons.Outlined.Settings
                        )
                    )

                    val navBarContent: @Composable () -> Unit = {
                        NavigationBar(
                            modifier = if (navBarStyle == "floating_pill") Modifier.padding(4.dp) else Modifier,
                            containerColor = if (navBarStyle == "floating_pill") Color.Transparent else MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            tabs.forEach { spec ->
                                val selected = currentTab == spec.tab
                                val icon = if (selected) spec.selectedIcon else spec.unselectedIcon
                                NavigationBarItem(
                                    selected = selected,
                                    modifier = Modifier.testTag("nav_tab_${spec.tab.name.lowercase()}"),
                                    onClick = {
                                        if (currentTab != spec.tab) {
                                            haptics.tick()
                                        }
                                        viewModel.navigateTo(spec.tab)
                                    },
                                    icon = {
                                        if (spec.tab == NavigationTab.SETTINGS && isAdmin) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(6.dp)
                                                    )
                                                }
                                            ) {
                                                Icon(icon, contentDescription = spec.label)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = spec.label)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = spec.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                tonalElevation = 6.dp,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                navBarContent()
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                            navBarContent()
                        }
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
                        onOpenTransactionDialog = { targetItem, action ->
                            itemForTransaction = targetItem
                            transactionInitialAction = action
                            showTransactionDialog = true
                        }
                    )
                }

                is CurrentScreen.BillFlow -> {
                    BillScreen(viewModel = viewModel)
                }

                is CurrentScreen.GstCalculator -> {
                    com.example.ui.screens.QuickBillingPosScreen(viewModel = viewModel)
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



                is CurrentScreen.AuditLogs -> {
                    com.example.ui.screens.AuditLogsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.closeAuditLogs() }
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
                            },
                            onOpenTransactionDialog = { targetItem, action ->
                                itemForTransaction = targetItem
                                transactionInitialAction = action
                                showTransactionDialog = true
                            }
                        )
                        NavigationTab.BILLING -> BillingHubScreen(viewModel = viewModel)
                        NavigationTab.TRANSACTIONS -> TransactionsScreen(viewModel = viewModel, isEmbeddedInHub = false)
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
    if (showTransactionDialog) {
        itemForTransaction?.let { txItem ->
            StockTransactionDialog(
                item = txItem,
                viewModel = viewModel,
                initialAction = transactionInitialAction,
                onDismiss = {
                    showTransactionDialog = false
                    itemForTransaction = null
                }
            )
        }
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
