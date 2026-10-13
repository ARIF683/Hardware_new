package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.BrandBlue
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
            // Header Top Bar matching third mockup image
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (isInGroup) {
                            viewModel.selectedGroup.value = null
                        } else {
                            viewModel.navigateTo(NavigationTab.HOME)
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close to Home")
                }

                Text(
                    text = if (isInGroup) selectedGroup ?: "Group" else "Items",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )

                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Add item") },
                            onClick = {
                                showMenu = false
                                onOpenItemForm(null)
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Color tint import") },
                            onClick = {
                                showMenu = false
                                showColorMachineDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Camera / Scan Bill") },
                            onClick = {
                                showMenu = false
                                viewModel.openBillFlow()
                            },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }

            // Search Bar with Filter Icon beside it matching reference mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search products...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                }
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Filter button beside search bar with DropdownMenu for Sort, Group, and In Stock
                Box {
                    var showFilterMenu by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showFilterMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Group by...") },
                            onClick = {
                                showFilterMenu = false
                                showGroupByDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (inStockOnly) "Show All Stock" else "In Stock Only") },
                            onClick = {
                                showFilterMenu = false
                                viewModel.inStockOnly.value = !inStockOnly
                            },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }

            // Category Chips Row matching third mockup image
            val categories = remember(allItemsList) {
                listOf("All") + allItemsList.map { it.type.ifBlank { it.brand } }.filter { it.isNotBlank() }.distinct().sorted()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (cat in categories) {
                    val isSelected = if (cat == "All") selectedGroup == null && searchQuery.isEmpty() else selectedGroup == cat || searchQuery.equals(cat, true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (cat == "All") {
                                viewModel.selectedGroup.value = null
                                viewModel.searchQuery.value = ""
                            } else {
                                viewModel.groupBy.value = "type"
                                viewModel.selectedGroup.value = cat
                            }
                        },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

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
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // Floating Selection Bar at Bottom
        if (isSelectionMode && selectedIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Header Row with count and clear button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { viewModel.selectedItemIds.value = emptySet(); viewModel.isSelectionMode.value = false }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel selection")
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
                        // All button
                        OutlinedButton(onClick = { viewModel.selectAllFiltered() }) {
                            Text("All", fontWeight = FontWeight.Bold)
                        }

                        // Photo button
                        OutlinedButton(
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
                                Text("Photo", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Type button
                        OutlinedButton(onClick = { showBulkTypeDialog = true }) {
                            Text("Type", fontWeight = FontWeight.Bold)
                        }

                        // Unit button
                        OutlinedButton(onClick = { showBulkUnitDialog = true }) {
                            Text("Unit", fontWeight = FontWeight.Bold)
                        }

                        // Delete button (Only visible in Admin mode)
                        if (isAdmin) {
                            Button(
                                onClick = { showBulkDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                            ) {
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

@Composable
fun ItemGridCard(
    item: Item,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    val isLow = item.qty < 0 || (item.low > 0 && item.qty <= item.low)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onToggleSelect() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Image Container with Tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.brand.ifBlank { "HARDWARE" },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Category / Type / Brand Badge at top right
                val categoryText = item.type.ifBlank { item.brand.ifBlank { "Item" } }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = categoryText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Bottom Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Selling Price & Cost Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Selling Price",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = StockViewModel.formatRupees(item.price),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (item.cost > 0.0) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Cost",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = StockViewModel.formatRupees(item.cost),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stock Badge
                val stockBg = if (isLow) DangerRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                val stockColor = if (isLow) DangerRed else SuccessGreen
                val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toLong().toString() else "%.1f".format(item.qty)
                val unitStr = if (item.unit != "pcs" && item.unit.isNotBlank()) " ${item.unit}" else ""
                Surface(
                    color = stockBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "In stock: $qtyStr$unitStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = stockColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
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
