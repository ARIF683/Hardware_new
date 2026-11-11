package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.util.ImageModelHelper
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.ui.GroupStat
import com.example.ui.StockViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.InputPromptDialog
import com.example.ui.components.SimpleConfirmDialog
import com.example.ui.components.ValuePickerDialog
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen

@Composable
fun ItemsScreen(
    viewModel: StockViewModel,
    onOpenItemForm: (Item?) -> Unit
) {
    val items by viewModel.filteredItems.collectAsState()
    val allItemsList by viewModel.allItems.collectAsState()
    val groupStats by viewModel.groupStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val groupBy by viewModel.groupBy.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val inStockOnly by viewModel.inStockOnly.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedIds by viewModel.selectedItemIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val itemsViewLayout by viewModel.itemsViewLayout.collectAsState()

    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupByDialog by remember { mutableStateOf(false) }
    var showBulkTypeDialog by remember { mutableStateOf(false) }
    var showBulkUnitDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showColorMachineDialog by remember { mutableStateOf(false) }
    var isUploadingBulkImage by remember { mutableStateOf(false) }
    var scannedBillBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val billPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input)
                        }
                    }
                    if (bitmap != null) {
                        scannedBillBitmap = bitmap
                    } else {
                        viewModel.showToast("Could not decode image from URI")
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Failed to load bill image: ${e.message}")
                }
            }
        }
    }

    val bulkPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedIds.isNotEmpty()) {
            scope.launch {
                isUploadingBulkImage = true
                viewModel.showToast("Uploading photo for ${selectedIds.size} items...")
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                val maxDim = 800
                                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1.0f)
                                val scaled = if (scale < 1.0f) {
                                    android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                                } else bitmap
                                val finalBitmap = if (scaled.hasAlpha()) {
                                    val whiteBg = android.graphics.Bitmap.createBitmap(scaled.width, scaled.height, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(whiteBg)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    canvas.drawBitmap(scaled, 0f, 0f, null)
                                    whiteBg
                                } else scaled
                                val bos = java.io.ByteArrayOutputStream()
                                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                                bos.toByteArray()
                            } else null
                        }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val result = viewModel.uploadItemImage(bytes)
                        if (result.isSuccess) {
                            val newUrl = result.getOrNull()
                            if (!newUrl.isNullOrBlank()) {
                                viewModel.updateSelectedImage(newUrl)
                            }
                        } else {
                            viewModel.showToast("Upload failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Error uploading photo: ${e.message}")
                } finally {
                    isUploadingBulkImage = false
                }
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val normalized = com.example.util.ItemSearchMatcher.normalizeVoiceInput(spokenText)
                viewModel.searchQuery.value = normalized
            }
        }
    }

    val isInGroup = selectedGroup != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tactical Header Top Bar matching mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0D14))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            if (isInGroup) {
                                viewModel.selectedGroup.value = null
                            } else {
                                viewModel.navigateTo(NavigationTab.HOME)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close to Home", tint = Color.White)
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isInGroup) (selectedGroup ?: "GROUP").uppercase() else "INVENTORY & STOCKS",
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
                                    text = "${allItemsList.size} SKUS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Catalog Valuation & SKU Quantities",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Tactical + Add Button
                    Button(
                        onClick = { onOpenItemForm(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color(0xFF022C1A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("ADD SKU", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }

                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "More options", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (itemsViewLayout == "list") "Switch to 2-Column Grid" else "Switch to List View") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setItemsViewLayout(if (itemsViewLayout == "list") "grid" else "list")
                                },
                                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF10B981)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Color tint import") },
                                onClick = {
                                    showMenu = false
                                    showColorMachineDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF10B981)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Camera / Scan Bill") },
                                onClick = {
                                    showMenu = false
                                    viewModel.openBillFlow()
                                },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF10B981)) }
                            )

                        }
                    }
                }
            }

            // Tactical Search Bar with Filter Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0D14))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Compact Tactical Search Input Box (Height 42.dp matched with filter button)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0E131C),
                    border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) Color(0xFF10B981) else Color(0xFF1E2638)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search products, SKU, brands...",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchQuery.value = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Voice Search Button
                        IconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak item name, SKU, or type to search...")
                                }
                                try {
                                    speechRecognizerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    viewModel.showToast("Voice recognition not available")
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        }

                        // Clear Button
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.searchQuery.value = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                // Tactical Filter button (Height 42.dp matched with search box)
                Box {
                    var showFilterMenu by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showFilterMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF121620),
                        border = BorderStroke(1.dp, Color(0xFF1E2638)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Filter",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by...") },
                            onClick = {
                                showFilterMenu = false
                                showSortDialog = true
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = Color(0xFF10B981)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Group by...") },
                            onClick = {
                                showFilterMenu = false
                                showGroupByDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF10B981)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (inStockOnly) "Show All Stock" else "In Stock Only") },
                            onClick = {
                                showFilterMenu = false
                                viewModel.inStockOnly.value = !inStockOnly
                            },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981)) }
                        )
                    }
                }
            }

            // Tactical Category Pills
            val categories = remember(allItemsList) {
                listOf("All") + allItemsList.map { it.type.ifBlank { it.brand } }.filter { it.isNotBlank() }.distinct().sorted()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0D14))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (cat in categories) {
                    val isSelected = if (cat == "All") selectedGroup == null && searchQuery.isEmpty() else selectedGroup == cat || searchQuery.equals(cat, true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF10B981) else Color(0xFF121620),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF1E2638)),
                        modifier = Modifier.clickable {
                            if (cat == "All") {
                                viewModel.selectedGroup.value = null
                                viewModel.searchQuery.value = ""
                            } else {
                                viewModel.groupBy.value = "type"
                                viewModel.selectedGroup.value = cat
                            }
                        }
                    ) {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFF022C1A) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF1E2638))

            // Main List View
            if (groupBy != "none" && !isInGroup) {
                // Group breakdown cards
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (groupStats.isEmpty()) {
                        item {
                            EmptyStateNotice("No items found for grouping")
                        }
                    } else {
                        items(groupStats) { stat ->
                            GroupStatCard(
                                stat = stat,
                                onClick = {
                                    viewModel.selectedGroup.value = stat.groupKey
                                    viewModel.searchQuery.value = ""
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else if (itemsViewLayout == "list") {
                // Single-Column Showcase List matching user reference image
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (items.isEmpty()) {
                        item {
                            EmptyStateNotice("No items found")
                        }
                    } else {
                        items(items, key = { it.id }) { item ->
                            ItemShowcaseListCard(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(item.id),
                                onToggleSelect = { viewModel.toggleItemSelection(item.id) },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        viewModel.openItemDetail(item.id)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            } else {
                // 2-Column Grid Item list
                val chunkedItems = items.chunked(2)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (items.isEmpty()) {
                        item {
                            EmptyStateNotice("No items found")
                        }
                    } else {
                        items(chunkedItems) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (item in rowItems) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ItemGridCard(
                                            item = item,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedIds.contains(item.id),
                                            onToggleSelect = { viewModel.toggleItemSelection(item.id) },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleItemSelection(item.id)
                                                } else {
                                                    viewModel.openItemDetail(item.id)
                                                }
                                            }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            }
        }

        // Dedicated Bottom Selection Options Bar
        if (isSelectionMode && selectedIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Header Row with count, select-all toggle, and clear button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                            Text(
                                text = "${selectedIds.size} selected",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = {
                                if (selectedIds.size == items.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAllFiltered()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == items.size) "Deselect All" else "Select All",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons Row using horizontalScroll so they never wrap, squish or misalign
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo button
                        FilledTonalButton(
                            onClick = {
                                bulkPhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isUploadingBulkImage
                        ) {
                            if (isUploadingBulkImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Photo", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Type button
                        FilledTonalButton(onClick = { showBulkTypeDialog = true }) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Type", fontWeight = FontWeight.Bold)
                        }

                        // Unit button
                        FilledTonalButton(onClick = { showBulkUnitDialog = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unit", fontWeight = FontWeight.Bold)
                        }

                        // Delete button (Only visible in Admin mode)
                        if (isAdmin) {
                            Button(
                                onClick = { showBulkDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Trailing spacer to prevent the last button from being cut off during horizontal scroll
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
            }
        }
    }

    // Sort Dialog
    if (showSortDialog) {
        val sortOptions = listOf(
            Pair("o", "Original order"),
            Pair("n", "Name A–Z"),
            Pair("nz", "Name Z–A"),
            Pair("q", "Quantity (high to low)"),
            Pair("p", "Selling Price (high to low)"),
            Pair("c", "Cost (high to low)")
        )
        ValuePickerDialog(
            title = "Sort by",
            optionsWithCount = sortOptions.map { Pair(it.second, 0) },
            currentValue = sortOptions.firstOrNull { it.first == sortOption }?.second ?: "",
            onSelect = { selectedName ->
                val code = sortOptions.firstOrNull { it.second == selectedName }?.first ?: "o"
                viewModel.sortOption.value = code
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    // Group By Dialog
    if (showGroupByDialog) {
        val gbOptions = listOf(
            Pair("none", "None"),
            Pair("name", "Name"),
            Pair("price", "Selling Price"),
            Pair("cost", "Cost"),
            Pair("type", "Type"),
            Pair("brand", "Brand"),
            Pair("size", "Size"),
            Pair("mrp", "MRP"),
            Pair("unit", "Unit")
        )
        ValuePickerDialog(
            title = "Group by",
            optionsWithCount = gbOptions.map { Pair(it.second, 0) },
            currentValue = gbOptions.firstOrNull { it.first == groupBy }?.second ?: "None",
            onSelect = { selectedName ->
                val code = gbOptions.firstOrNull { it.second == selectedName }?.first ?: "none"
                viewModel.groupBy.value = code
                viewModel.selectedGroup.value = null
                showGroupByDialog = false
            },
            onDismiss = { showGroupByDialog = false }
        )
    }

    // Bulk Type Dialog
    if (showBulkTypeDialog) {
        val types = allItemsList.map { it.type }.filter { it.isNotBlank() }.distinct()
            .map { Pair(it, allItemsList.count { item -> item.type == it }) }
        ValuePickerDialog(
            title = "Set Type for ${selectedIds.size} items",
            optionsWithCount = types,
            currentValue = "",
            onSelect = { newType ->
                viewModel.updateSelectedType(newType)
                showBulkTypeDialog = false
            },
            onDismiss = { showBulkTypeDialog = false }
        )
    }

    // Bulk Unit Dialog
    if (showBulkUnitDialog) {
        val units = listOf("pcs", "kg", "g", "L", "ml", "box", "pack", "bag", "m", "ft", "cm", "set", "pair", "dozen", "roll", "sheet")
            .map { Pair(it, allItemsList.count { item -> item.unit == it }) }
        ValuePickerDialog(
            title = "Set Unit for ${selectedIds.size} items",
            optionsWithCount = units,
            currentValue = "",
            onSelect = { newUnit ->
                if (newUnit.isNotBlank()) {
                    viewModel.updateSelectedUnit(newUnit)
                }
                showBulkUnitDialog = false
            },
            onDismiss = { showBulkUnitDialog = false }
        )
    }

    // Bulk Delete Dialog
    if (showBulkDeleteDialog) {
        SimpleConfirmDialog(
            title = "Delete items",
            message = "Delete ${selectedIds.size} selected item(s)? This cannot be undone.",
            confirmText = "Delete",
            isDanger = true,
            onConfirm = {
                viewModel.deleteSelectedItems()
                showBulkDeleteDialog = false
            },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }

    if (showColorMachineDialog) {
        ColorMachineTintDialog(
            viewModel = viewModel,
            onDismiss = { showColorMachineDialog = false }
        )
    }


}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemShowcaseListCard(
    item: Item,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    val isLow = item.qty < 0 || (item.low > 0 && item.qty <= item.low)
    val isOutOfStock = item.qty <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onClick()
                },
                onLongClick = { onToggleSelect() }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF072116) else Color(0xFF0E131C)
        ),
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF10B981))
        else BorderStroke(1.dp, Color(0xFF1E2638))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Thumbnail image or category icon in tactical dark box
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF141923))
                    .border(0.8.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageModelHelper.rememberImageModel(item.imageUrl),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                item.type.contains("Plumb", true) -> "💧"
                                item.type.contains("Elect", true) -> "⚡"
                                item.type.contains("Paint", true) -> "🎨"
                                item.type.contains("Tool", true) -> "🛠️"
                                item.type.contains("Hardware", true) -> "🔩"
                                else -> "📦"
                            },
                            fontSize = 22.sp
                        )
                        Text(
                            text = (item.type.takeIf { it.isNotBlank() } ?: item.brand.takeIf { it.isNotBlank() } ?: "SKU").take(5).uppercase(),
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF64748B),
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center Column: SKU, Title, Stock Health Progress Bar, and Location
            Column(modifier = Modifier.weight(1f)) {
                // Top SKU line matching mockup
                Text(
                    text = "SKU: ${item.code.ifBlank { item.id.takeLast(5) }}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = Color(0xFFF3F4F6)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tactical Stock Progress Bar & QTY
                val maxStockRef = (if (item.low > 0) item.low * 4.0 else 50.0).coerceAtLeast(10.0)
                val progressFraction = (item.qty / maxStockRef).toFloat().coerceIn(0f, 1f)
                val stockColor = when {
                    isOutOfStock -> Color(0xFFEF4444)
                    isLow -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QTY: ${item.qty.toInt()} ${if (isOutOfStock) "🔴" else if (isLow) "🟡" else "🟢"} ${item.qty.toInt()}/${maxStockRef.toInt()}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Full Width Linear indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1F2937))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isOutOfStock) 0.05f else progressFraction.coerceAtLeast(0.08f))
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(stockColor)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Category tag at bottom left and location on bottom right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (item.type.takeIf { it.isNotBlank() } ?: "GEAR").uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = if (item.brand.isNotBlank()) "Loc: ${item.brand.take(10)}" else "Aisle 4",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Column: Glowing Emerald Price Pill matching mockup
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text(
                    text = StockViewModel.formatRupees(item.price),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemGridCard(
    item: Item,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    val isLow = item.qty < 0 || (item.low > 0 && item.qty <= item.low)
    val isOutOfStock = item.qty <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onClick()
                },
                onLongClick = { onToggleSelect() }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF072116) else Color(0xFF0E131C)
        ),
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF10B981))
        else BorderStroke(1.dp, Color(0xFF1E2638))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Image Box with Tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(Color(0xFF141923))
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageModelHelper.rememberImageModel(item.imageUrl),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121620)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (item.type.takeIf { it.isNotBlank() } ?: item.brand.takeIf { it.isNotBlank() } ?: "SKU").take(6).uppercase(),
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Tactical Stock Pill at top right
                val stockPillBg = if (isOutOfStock) Color(0xFFEF4444) else if (isLow) Color(0xFFF59E0B) else Color(0xFF10B981)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.6.dp, stockPillBg)
                ) {
                    Text(
                        text = if (isOutOfStock) "OUT" else "${item.qty.toInt()} ${item.unit}",
                        color = stockPillBg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                        )
                    }
                }
            }

            // Bottom Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = Color(0xFFF3F4F6)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Selling Price & Cost Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = StockViewModel.formatRupees(item.price),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            letterSpacing = 0.3.sp
                        )
                    }

                    if (item.cost > 0.0) {
                        Text(
                            text = "Cost ₹${item.cost.toInt()}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupStatCard(
    stat: GroupStat,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.groupKey,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${stat.percentage}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stat.itemCount}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val qtyStr = if (stat.totalQty % 1.0 == 0.0) stat.totalQty.toLong().toString() else "%.1f".format(stat.totalQty)
                    Text(
                        text = qtyStr,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quantity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateNotice(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
