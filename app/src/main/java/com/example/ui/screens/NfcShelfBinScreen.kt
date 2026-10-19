package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Item
import com.example.ui.StockViewModel
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.SuccessGreen
import java.util.Locale

@Composable
fun NfcShelfBinScreen(
    viewModel: StockViewModel,
    onBack: () -> Unit
) {
    val allItems by viewModel.allItems.collectAsState()
    val scannedTag by viewModel.scannedNfcTag.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // "all", "assigned", "unassigned"
    var showAssignDialog by remember { mutableStateOf(false) }
    var itemToAssign by remember { mutableStateOf<Item?>(null) }
    var showSimulateDialog by remember { mutableStateOf(false) }

    val matchedItem = remember(scannedTag, allItems) {
        if (scannedTag != null) {
            allItems.find {
                it.nfcTagId.equals(scannedTag, ignoreCase = true) ||
                it.shelfLocation.equals(scannedTag, ignoreCase = true) ||
                it.barcode.equals(scannedTag, ignoreCase = true)
            }
        } else null
    }

    val filteredItems = remember(allItems, searchQuery, filterMode) {
        var list = allItems
        when (filterMode) {
            "assigned" -> list = list.filter { !it.shelfLocation.isNullOrBlank() || !it.nfcTagId.isNullOrBlank() }
            "unassigned" -> list = list.filter { it.shelfLocation.isNullOrBlank() && it.nfcTagId.isNullOrBlank() }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                (it.shelfLocation?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (it.nfcTagId?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                it.code.lowercase(Locale.ROOT).contains(q)
            }
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- Header ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NFC / RFID Shelf & Bin",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap shelf stickers or bin tags for instant lookup",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showSimulateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simulate Tap", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- NFC Scanner Radar Card ---
            item {
                NfcTapRadarCard(
                    scannedTag = scannedTag,
                    matchedItem = matchedItem,
                    onOpenItem = { item ->
                        viewModel.openItemDetail(item.id)
                    },
                    onAssignTag = { tag ->
                        itemToAssign = null
                        showAssignDialog = true
                    }
                )
            }

            // --- Filter Tabs & Search ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val assignedCount = allItems.count { !it.shelfLocation.isNullOrBlank() || !it.nfcTagId.isNullOrBlank() }
                    val unassignedCount = allItems.size - assignedCount

                    listOf(
                        Triple("all", "All (${allItems.size})", null),
                        Triple("assigned", "Assigned ($assignedCount)", SuccessGreen),
                        Triple("unassigned", "Need Tag ($unassignedCount)", BrandAmber)
                    ).forEach { (mode, label, tint) ->
                        val isSelected = filterMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable { filterMode = mode }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Search by shelf, bin, item name or NFC tag ID...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true
                )
            }

            // --- Items List with Shelf Bins ---
            item {
                Text(
                    text = "HARDWARE ITEMS & SHELF LOCATIONS (${filteredItems.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
            }

            items(filteredItems, key = { it.id }) { item ->
                ItemShelfRowCard(
                    item = item,
                    onAssign = {
                        itemToAssign = item
                        showAssignDialog = true
                    },
                    onOpen = {
                        viewModel.openItemDetail(item.id)
                    }
                )
            }
        }
    }

    // --- Assign Shelf & NFC Tag Dialog ---
    if (showAssignDialog) {
        AssignShelfTagDialog(
            item = itemToAssign,
            allItems = allItems,
            defaultTagId = scannedTag ?: "",
            onDismiss = {
                showAssignDialog = false
                itemToAssign = null
            },
            onSave = { selectedItemId, shelfLocation, nfcTagId ->
                viewModel.linkNfcTagToItem(selectedItemId, nfcTagId, shelfLocation)
                showAssignDialog = false
                itemToAssign = null
            }
        )
    }

    // --- Simulate NFC Tap Dialog ---
    if (showSimulateDialog) {
        SimulateNfcTapDialog(
            allItems = allItems,
            onDismiss = { showSimulateDialog = false },
            onSimulate = { tagId, payload ->
                viewModel.onNfcTagScanned(tagId, payload)
                showSimulateDialog = false
            }
        )
    }
}

@Composable
private fun NfcTapRadarCard(
    scannedTag: String?,
    matchedItem: Item?,
    onOpenItem: (Item) -> Unit,
    onAssignTag: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            if (matchedItem != null) SuccessGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Radar visual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.12f),
                        radius = (size.minDimension / 2) * pulseScale
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = (size.minDimension / 2) * 0.85f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = "NFC Tap Radar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Hold Phone Near Shelf / Bin NFC Tag",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Auto-detects bin stickers, RFID badges and shelf tags",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Scanned Tag Info Banner
            if (scannedTag != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (matchedItem != null) SuccessGreen.copy(alpha = 0.1f) else BrandAmber.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (matchedItem != null) SuccessGreen.copy(alpha = 0.4f) else BrandAmber.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (matchedItem != null) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = if (matchedItem != null) SuccessGreen else BrandAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (matchedItem != null) "MATCHED SHELF BIN" else "TAG DETECTED (UNASSIGNED)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (matchedItem != null) SuccessGreen else BrandAmber,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = "Tag: $scannedTag",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (matchedItem != null) {
                            Text(
                                text = matchedItem.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "📍 ${matchedItem.shelfLocation ?: "Shelf Bin"} • Stock: ${matchedItem.qty.toInt()} ${matchedItem.unit} • ₹${matchedItem.price}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onOpenItem(matchedItem) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Item & Adjust Stock")
                            }
                        } else {
                            Text(
                                text = "This tag ($scannedTag) is not yet mapped to any hardware product.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onAssignTag(scannedTag) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAmber, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Link Tag to a Hardware Item")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemShelfRowCard(
    item: Item,
    onAssign: () -> Unit,
    onOpen: () -> Unit
) {
    val hasShelf = !item.shelfLocation.isNullOrBlank()
    val hasNfc = !item.nfcTagId.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpen() }
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        if (item.code.isNotBlank()) "Code: ${item.code}" else null,
                        "Stock: ${item.qty.toInt()} ${item.unit}",
                        "₹${item.price}"
                    ).joinToString(" • "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Shelf Location Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasShelf) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (hasShelf) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = item.shelfLocation?.ifBlank { "No Bin" } ?: "No Bin",
                                fontSize = 11.sp,
                                fontWeight = if (hasShelf) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (hasShelf) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // NFC Tag ID Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasNfc) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Nfc,
                                contentDescription = null,
                                tint = if (hasNfc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (hasNfc) item.nfcTagId!! else "No NFC Tag",
                                fontSize = 11.sp,
                                fontWeight = if (hasNfc) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (hasNfc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onAssign) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Shelf Bin & NFC Tag",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AssignShelfTagDialog(
    item: Item?,
    allItems: List<Item>,
    defaultTagId: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var selectedItem by remember { mutableStateOf(item) }
    var itemPickerExpanded by remember { mutableStateOf(false) }
    var shelfLocation by remember { mutableStateOf(item?.shelfLocation ?: "") }
    var nfcTagId by remember { mutableStateOf(item?.nfcTagId ?: defaultTagId) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Link Shelf Bin & NFC Tag",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Assign aisle location and tag ID for instant tapping",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Item Picker
                OutlinedTextField(
                    value = selectedItem?.name ?: "Select an item *",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hardware Item *") },
                    trailingIcon = {
                        IconButton(onClick = { itemPickerExpanded = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { itemPickerExpanded = true }
                )
                DropdownMenu(
                    expanded = itemPickerExpanded,
                    onDismissRequest = { itemPickerExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    allItems.take(50).forEach { itm ->
                        DropdownMenuItem(
                            text = { Text(itm.name, fontSize = 13.sp) },
                            onClick = {
                                selectedItem = itm
                                shelfLocation = itm.shelfLocation ?: shelfLocation
                                nfcTagId = itm.nfcTagId ?: nfcTagId
                                itemPickerExpanded = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Shelf / Bin Location
                OutlinedTextField(
                    value = shelfLocation,
                    onValueChange = { shelfLocation = it },
                    label = { Text("Shelf / Bin Location *") },
                    placeholder = { Text("e.g. Aisle 3 - Rack B - Bin 14") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // NFC Tag ID
                OutlinedTextField(
                    value = nfcTagId,
                    onValueChange = { nfcTagId = it },
                    label = { Text("NFC / RFID Tag Serial / Hex *") },
                    placeholder = { Text("e.g. 04:A2:3F:8B:11 or BIN-A12") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val itm = selectedItem
                            if (itm != null && shelfLocation.isNotBlank()) {
                                onSave(itm.id, shelfLocation.trim(), nfcTagId.trim())
                            }
                        },
                        enabled = selectedItem != null && shelfLocation.isNotBlank()
                    ) {
                        Text("Save Mapping")
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulateNfcTapDialog(
    allItems: List<Item>,
    onDismiss: () -> Unit,
    onSimulate: (String, String?) -> Unit
) {
    var tagIdInput by remember { mutableStateOf("") }
    val presetLocations = listOf("RACK-A1", "BIN-PIPE-2", "SHELF-PAINT-3", "SCREWS-DRAWER-5")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🏷️ Simulate NFC Shelf Tap",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Test shelf tapping without physical NFC hardware",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = tagIdInput,
                    onValueChange = { tagIdInput = it },
                    label = { Text("NFC Tag ID or Shelf Code") },
                    placeholder = { Text("e.g. 04:A2:8B or RACK-A1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "OR TAP A PRESET SHELF TAG:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetLocations.take(2).forEach { loc ->
                        OutlinedButton(
                            onClick = {
                                onSimulate(loc, loc)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(loc, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetLocations.drop(2).forEach { loc ->
                        OutlinedButton(
                            onClick = {
                                onSimulate(loc, loc)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(loc, fontSize = 11.sp)
                        }
                    }
                }

                // If items already have assigned tags, allow tapping them directly!
                val itemsWithTags = remember(allItems) {
                    allItems.filter { !it.nfcTagId.isNullOrBlank() || !it.shelfLocation.isNullOrBlank() }.take(5)
                }
                if (itemsWithTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "OR TAP AN ASSIGNED INVENTORY ITEM:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    itemsWithTags.forEach { itm ->
                        val code = itm.nfcTagId ?: itm.shelfLocation ?: itm.barcode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSimulate(code, itm.shelfLocation) }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(itm.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("🏷️ $code", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (tagIdInput.isNotBlank()) {
                                onSimulate(tagIdInput.trim(), null)
                            }
                        },
                        enabled = tagIdInput.isNotBlank()
                    ) {
                        Text("Simulate Tap")
                    }
                }
            }
        }
    }
}
