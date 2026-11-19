package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.AnnouncementConfig
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.FestivalConfig
import com.example.data.pref.FestivalStudioConfig
import com.example.ui.StockViewModel
import com.example.ui.components.FestivalBannerCard
import com.example.ui.components.FestivalPreset
import com.example.ui.theme.LocalExtendedColors
import com.example.util.ImageModelHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class StudioPresetTemplate(
    val preset: FestivalPreset,
    val label: String,
    val defaultHex: String,
    val defaultTitle: String,
    val defaultGreeting: String
)

private val studioPresetTemplates = listOf(
    StudioPresetTemplate(
        preset = FestivalPreset.DIWALI,
        label = "🪔 Diwali",
        defaultHex = "#D97706",
        defaultTitle = "Diwali Festival Special",
        defaultGreeting = "🪔 Happy Diwali! May your business, home & projects shine bright with prosperity and abundance!"
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.EID,
        label = "🌙 Eid",
        defaultHex = "#059669",
        defaultTitle = "Eid Mubarak",
        defaultGreeting = "🌙 Eid Mubarak! Wishing peace, harmony & blessings to all families and customers."
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.HOLI,
        label = "🎨 Holi",
        defaultHex = "#DB2777",
        defaultTitle = "Holi Festival of Colors",
        defaultGreeting = "🎨 Happy Holi! May your paints, projects & celebrations shine with vibrant colors!"
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.CHRISTMAS,
        label = "🎄 Christmas",
        defaultHex = "#15803D",
        defaultTitle = "Christmas Special",
        defaultGreeting = "🎄 Merry Christmas! Wishing warmth, joy & bright celebrations to your home."
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.NEW_YEAR,
        label = "🎆 New Year",
        defaultHex = "#4F46E5",
        defaultTitle = "Happy New Year",
        defaultGreeting = "🎆 Happy New Year! Wishing prosperous projects & strong growth all year long!"
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.INDEPENDENCE_DAY,
        label = "🇮🇳 National",
        defaultHex = "#EA580C",
        defaultTitle = "Patriotic Celebration",
        defaultGreeting = "🇮🇳 Wishing pride, strength & progress in every home and project we help build!"
    ),
    StudioPresetTemplate(
        preset = FestivalPreset.SALE,
        label = "🏷️ Festive Offer",
        defaultHex = "#D97757",
        defaultTitle = "Festive Store Offer",
        defaultGreeting = "🏷️ Special festive prices on bulk paints, putty & hardware — ask at counter for instant GST billing!"
    )
)

@Composable
fun FestivalStudioDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    AnnouncementStudioDialog(viewModel = viewModel, onDismiss = onDismiss)
}

@Composable
fun AnnouncementStudioDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val currentConfig by viewModel.festivalStudioConfig.collectAsState()
    val nextAutoFestival = remember { AutoFestivalCalendar.nextUpcomingFestival() }

    // Announcement state (with timer)
    var announcementEnabled by remember(currentConfig) { mutableStateOf(currentConfig.announcement.enabled) }
    var announcementMessage by remember(currentConfig) { mutableStateOf(currentConfig.announcement.message) }
    var announcementSeverity by remember(currentConfig) { mutableStateOf(currentConfig.announcement.severity.ifBlank { "info" }) }
    var announcementDismissible by remember(currentConfig) { mutableStateOf(currentConfig.announcement.dismissible) }
    var announcementStartAtMillis by remember(currentConfig) { mutableLongStateOf(currentConfig.announcement.startAtMillis) }
    var announcementEndAtMillis by remember(currentConfig) { mutableLongStateOf(currentConfig.announcement.endAtMillis) }

    // Festival & theme state
    var autoCalendarEnabled by remember(currentConfig) { mutableStateOf(currentConfig.autoCalendarEnabled) }
    var applyThemeToApp by remember(currentConfig) { mutableStateOf(currentConfig.applyFestivalThemeToApp) }
    var overrideMode by remember(currentConfig) { mutableStateOf(currentConfig.overrideMode) }

    // Multiple custom cards + hidden auto-calendar keys
    val customCards = remember(currentConfig) {
        currentConfig.effectiveCustomFestivals().toMutableStateList()
    }
    val hiddenAutoKeys = remember(currentConfig) {
        currentConfig.hiddenAutoFestivalKeys.toMutableStateList()
    }
    var editingIndex by remember(currentConfig) {
        mutableStateOf(if (customCards.isNotEmpty()) 0 else null)
    }
    val pickedImageUris = remember(currentConfig) { mutableStateMapOf<Int, Uri>() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val idx = editingIndex
        if (uri != null && idx != null && idx in customCards.indices) {
            pickedImageUris[idx] = uri
        }
    }

    val isTimerValid = announcementStartAtMillis <= 0L ||
        announcementEndAtMillis <= 0L ||
        announcementEndAtMillis > announcementStartAtMillis

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 520.dp)
                .heightIn(max = 780.dp)
                .testTag("announcement_studio_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text("Announcement Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Admin Only • Syncs Across All Devices",
                                fontSize = 11.5.sp,
                                color = ext.positive,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Announcement Studio")
                    }
                }

                // SECTION 1: Store Announcement Notice + Timer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Store Announcement Notice",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Show a live or timed notice banner on Home",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = announcementEnabled,
                                onCheckedChange = { announcementEnabled = it },
                                modifier = Modifier.testTag("studio_announcement_enabled_switch")
                            )
                        }

                        if (announcementEnabled || announcementMessage.isNotBlank()) {
                            OutlinedTextField(
                                value = announcementMessage,
                                onValueChange = { announcementMessage = it },
                                label = { Text("Notice Message") },
                                placeholder = { Text("e.g., Shop closed Sunday / New paint shipment arrived") },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("studio_announcement_message_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val severities = listOf(
                                    "info" to "Info",
                                    "success" to "Success",
                                    "warning" to "Warning",
                                    "alert" to "Alert"
                                )
                                severities.forEach { (sevKey, sevLabel) ->
                                    FilterChip(
                                        selected = announcementSeverity == sevKey,
                                        onClick = { announcementSeverity = sevKey },
                                        label = { Text(sevLabel, fontSize = 11.5.sp) }
                                    )
                                }
                            }

                            AnnouncementTimerSection(
                                startAtMillis = announcementStartAtMillis,
                                endAtMillis = announcementEndAtMillis,
                                onUpdateRange = { start, end ->
                                    announcementStartAtMillis = start
                                    announcementEndAtMillis = end
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Allow staff to dismiss notice",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = announcementDismissible,
                                    onCheckedChange = { announcementDismissible = it }
                                )
                            }

                            if (announcementEnabled && announcementMessage.isNotBlank()) {
                                val previewTint = when (announcementSeverity) {
                                    "warning" -> ext.warning
                                    "alert" -> ext.danger
                                    "success" -> ext.positive
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                val previewIcon = when (announcementSeverity) {
                                    "warning", "alert" -> Icons.Default.Warning
                                    else -> Icons.Default.Info
                                }
                                val previewTimer = AnnouncementConfig(
                                    enabled = true,
                                    message = announcementMessage.trim(),
                                    startAtMillis = announcementStartAtMillis,
                                    endAtMillis = announcementEndAtMillis
                                ).timerSummaryLabel()
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = previewTint.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, previewTint.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(previewIcon, contentDescription = null, tint = previewTint, modifier = Modifier.size(18.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = announcementMessage.trim(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (previewTimer != null) {
                                                Text(
                                                    text = previewTimer,
                                                    fontSize = 10.5.sp,
                                                    color = previewTint,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Built-in Calendar & App-Wide Theme Switches
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Built-in Indian Festival Calendar",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Offline calendar (Diwali, Holi, Eid, Christmas & National days)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCalendarEnabled,
                                onCheckedChange = { autoCalendarEnabled = it },
                                modifier = Modifier.testTag("studio_auto_calendar_switch")
                            )
                        }

                        if (nextAutoFestival != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Next on calendar: ${nextAutoFestival.festivalName} (${nextAutoFestival.scheduleSummaryLabel() ?: ""})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (autoCalendarEnabled && overrideMode != FestivalStudioConfig.MODE_CUSTOM) {
                            BuiltInCalendarFestivalsManager(
                                hiddenKeys = hiddenAutoKeys,
                                onToggleHideKey = { key, shouldHide ->
                                    if (shouldHide) {
                                        if (key !in hiddenAutoKeys) hiddenAutoKeys.add(key)
                                    } else {
                                        hiddenAutoKeys.removeAll { it.equals(key, ignoreCase = true) }
                                    }
                                },
                                onRestoreAll = { hiddenAutoKeys.clear() }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        "Apply Festive Accent to App UI",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Tints buttons & active tabs while festival is live",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = applyThemeToApp,
                                onCheckedChange = { applyThemeToApp = it },
                                modifier = Modifier.testTag("studio_apply_theme_switch")
                            )
                        }
                    }
                }

                // SECTION 3: Festival Display Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Festival Display Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            FestivalStudioConfig.MODE_AUTO to "Calendar + Custom",
                            FestivalStudioConfig.MODE_CUSTOM to "Custom Only",
                            FestivalStudioConfig.MODE_OFF to "Off / Pause"
                        )
                        modes.forEach { (modeKey, modeLabel) ->
                            val selected = overrideMode == modeKey
                            FilterChip(
                                selected = selected,
                                onClick = { overrideMode = modeKey },
                                label = {
                                    Text(
                                        modeLabel,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            )
                        }
                    }
                }

                // SECTION 4: Multiple Custom Festival / Offer Cards
                if (overrideMode != FestivalStudioConfig.MODE_OFF) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Custom Festival & Offer Cards (${customCards.size})",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Add 2+ cards to rotate in Home carousel & header strip",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val tpl = if (customCards.isEmpty()) {
                                            studioPresetTemplates.first()
                                        } else {
                                            studioPresetTemplates.last() // Festive Offer default for additional card
                                        }
                                        customCards.add(
                                            FestivalConfig(
                                                enabled = true,
                                                festivalName = tpl.defaultTitle,
                                                greetingMessage = tpl.defaultGreeting,
                                                badgeText = tpl.preset.defaultBadge,
                                                accentColorHex = tpl.defaultHex,
                                                stylePreset = tpl.preset.key
                                            )
                                        )
                                        editingIndex = customCards.lastIndex
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("studio_add_custom_card_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Card", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (customCards.isEmpty()) {
                                Text(
                                    "No custom cards yet. Tap \"Add Card\" to create a custom festival or store sale banner.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    customCards.forEachIndexed { idx, card ->
                                        CustomFestivalCardItemRow(
                                            card = card,
                                            index = idx,
                                            isEditing = editingIndex == idx,
                                            onToggleEnabled = { checked ->
                                                customCards[idx] = card.copy(enabled = checked)
                                            },
                                            onSelectEdit = {
                                                editingIndex = if (editingIndex == idx) null else idx
                                            },
                                            onDelete = {
                                                customCards.removeAt(idx)
                                                val remappedUris = pickedImageUris.entries
                                                    .filter { it.key != idx }
                                                    .associate { (k, v) -> (if (k > idx) k - 1 else k) to v }
                                                pickedImageUris.clear()
                                                pickedImageUris.putAll(remappedUris)
                                                editingIndex = when {
                                                    customCards.isEmpty() -> null
                                                    editingIndex == idx -> 0
                                                    (editingIndex ?: 0) > idx -> (editingIndex ?: 1) - 1
                                                    else -> editingIndex
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            val activeEditIdx = editingIndex?.takeIf { it in customCards.indices }
                            if (activeEditIdx != null) {
                                val card = customCards[activeEditIdx]
                                val pickedForCard = pickedImageUris[activeEditIdx]

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                                Text(
                                    "Editing Card #${activeEditIdx + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    studioPresetTemplates.forEach { tpl ->
                                        val isChosen = card.stylePreset.equals(tpl.preset.key, ignoreCase = true)
                                        FilterChip(
                                            selected = isChosen,
                                            onClick = {
                                                val oldTpl = studioPresetTemplates.find {
                                                    it.preset.key.equals(card.stylePreset, ignoreCase = true)
                                                }
                                                customCards[activeEditIdx] = card.copy(
                                                    enabled = true,
                                                    stylePreset = tpl.preset.key,
                                                    accentColorHex = tpl.defaultHex,
                                                    festivalName = if (card.festivalName.isBlank() || card.festivalName == oldTpl?.defaultTitle) tpl.defaultTitle else card.festivalName,
                                                    greetingMessage = if (card.greetingMessage.isBlank() || card.greetingMessage == oldTpl?.defaultGreeting) tpl.defaultGreeting else card.greetingMessage,
                                                    badgeText = if (card.badgeText.isBlank() || card.badgeText == oldTpl?.preset?.defaultBadge) tpl.preset.defaultBadge else card.badgeText
                                                )
                                            },
                                            label = { Text(tpl.label, fontSize = 11.5.sp) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = card.festivalName,
                                    onValueChange = { customCards[activeEditIdx] = card.copy(festivalName = it) },
                                    label = { Text("Festival / Offer Title") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("studio_card_title_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = card.badgeText,
                                        onValueChange = { customCards[activeEditIdx] = card.copy(badgeText = it) },
                                        label = { Text("Badge Text") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    OutlinedTextField(
                                        value = card.accentColorHex,
                                        onValueChange = { customCards[activeEditIdx] = card.copy(accentColorHex = it) },
                                        label = { Text("Color Hex") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                }

                                OutlinedTextField(
                                    value = card.greetingMessage,
                                    onValueChange = { customCards[activeEditIdx] = card.copy(greetingMessage = it) },
                                    label = { Text("Greeting / Offer Message") },
                                    maxLines = 3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("studio_card_message_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = card.startDate,
                                        onValueChange = { customCards[activeEditIdx] = card.copy(startDate = it) },
                                        label = { Text("Start (YYYY-MM-DD)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = card.endDate,
                                        onValueChange = { customCards[activeEditIdx] = card.copy(endDate = it) },
                                        label = { Text("End (YYYY-MM-DD)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                            val today = Date()
                                            val cal = Calendar.getInstance().apply {
                                                time = today
                                                add(Calendar.DAY_OF_YEAR, 7)
                                            }
                                            customCards[activeEditIdx] = card.copy(
                                                startDate = sdf.format(today),
                                                endDate = sdf.format(cal.time)
                                            )
                                        },
                                        label = { Text("Today – +7d", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            val cal = Calendar.getInstance()
                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                            cal.set(Calendar.DAY_OF_MONTH, 1)
                                            val s = sdf.format(cal.time)
                                            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                            val e = sdf.format(cal.time)
                                            customCards[activeEditIdx] = card.copy(startDate = s, endDate = e)
                                        },
                                        label = { Text("Full Month", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            customCards[activeEditIdx] = card.copy(startDate = "", endDate = "")
                                        },
                                        label = { Text("No Dates", fontSize = 11.sp) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (pickedForCard != null || card.bannerImageUrl.isNotBlank()) "Change Banner Image" else "Pick Banner Photo",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (pickedForCard != null || card.bannerImageUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                pickedImageUris.remove(activeEditIdx)
                                                customCards[activeEditIdx] = card.copy(bannerImageUrl = "")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear banner image", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                if (pickedForCard != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = pickedForCard),
                                            contentDescription = "Selected festival banner",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else if (card.bannerImageUrl.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                    ) {
                                        AsyncImage(
                                            model = ImageModelHelper.rememberImageModel(card.bannerImageUrl),
                                            contentDescription = "Synced festival banner",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                if (card.festivalName.isNotBlank() || card.greetingMessage.isNotBlank()) {
                                    Text(
                                        "Live Card Preview",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FestivalBannerCard(
                                        festival = card.copy(
                                            enabled = true,
                                            bannerImageUrl = pickedForCard?.toString() ?: card.bannerImageUrl
                                        ),
                                        pageIndex = 0,
                                        totalPages = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 5: Save & Sync CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = isTimerValid,
                        onClick = {
                            val cleanCustomList = customCards.map { card ->
                                card.copy(
                                    festivalName = card.festivalName.trim(),
                                    greetingMessage = card.greetingMessage.trim(),
                                    badgeText = card.badgeText.trim(),
                                    accentColorHex = card.accentColorHex.trim(),
                                    bannerImageUrl = card.bannerImageUrl.trim(),
                                    startDate = card.startDate.trim(),
                                    endDate = card.endDate.trim()
                                )
                            }
                            val cleanAnnouncement = AnnouncementConfig(
                                enabled = announcementEnabled,
                                message = announcementMessage.trim(),
                                severity = announcementSeverity,
                                dismissible = announcementDismissible,
                                startAtMillis = announcementStartAtMillis,
                                endAtMillis = announcementEndAtMillis
                            )
                            val updatedStudio = FestivalStudioConfig(
                                autoCalendarEnabled = autoCalendarEnabled,
                                applyFestivalThemeToApp = applyThemeToApp,
                                overrideMode = overrideMode,
                                customFestival = cleanCustomList.firstOrNull() ?: FestivalConfig(),
                                customFestivals = cleanCustomList,
                                hiddenAutoFestivalKeys = hiddenAutoKeys.distinct(),
                                announcement = cleanAnnouncement,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.saveAndSyncFestivalStudio(updatedStudio, pickedImageUris.toMap())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("studio_save_sync_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Sync Devices", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
