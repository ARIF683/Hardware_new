package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.FestivalConfig
import com.example.data.model.FestivalScheduleHelper
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
    val ext = LocalExtendedColors.current
    val currentConfig by viewModel.festivalStudioConfig.collectAsState()
    val nextAutoFestival = remember { AutoFestivalCalendar.nextUpcomingFestival() }

    var autoCalendarEnabled by remember(currentConfig) { mutableStateOf(currentConfig.autoCalendarEnabled) }
    var applyThemeToApp by remember(currentConfig) { mutableStateOf(currentConfig.applyFestivalThemeToApp) }
    var overrideMode by remember(currentConfig) { mutableStateOf(currentConfig.overrideMode) }

    var customEnabled by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.enabled) }
    var stylePreset by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.stylePreset.ifBlank { "diwali" }) }
    var festivalName by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.festivalName) }
    var badgeText by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.badgeText) }
    var accentColorHex by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.accentColorHex) }
    var greetingMessage by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.greetingMessage) }
    var startDate by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.startDate) }
    var endDate by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.endDate) }
    var bannerImageUrl by remember(currentConfig) { mutableStateOf(currentConfig.customFestival.bannerImageUrl) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri
        }
    }

    val draftCustomFestival = FestivalConfig(
        enabled = customEnabled,
        festivalName = festivalName.trim(),
        greetingMessage = greetingMessage.trim(),
        badgeText = badgeText.trim(),
        accentColorHex = accentColorHex.trim(),
        bannerImageUrl = pickedImageUri?.toString() ?: bannerImageUrl.trim(),
        startDate = startDate.trim(),
        endDate = endDate.trim(),
        stylePreset = stylePreset
    )

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
                .heightIn(max = 760.dp)
                .testTag("festival_studio_dialog")
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
                            Text("Festival Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Admin Only • Syncs Across All Devices",
                                fontSize = 11.5.sp,
                                color = ext.positive,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Festival Studio")
                    }
                }

                // 1. Automatic Calendar & App-Wide Theme Switches
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
                                    "Auto Indian Festival Calendar",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Auto-shows Diwali, Holi, Eid, Christmas & National days",
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
                                    text = "Next auto: ${nextAutoFestival.festivalName} (${nextAutoFestival.scheduleSummaryLabel() ?: ""})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                        "Auto-tints buttons & active tabs while festival is live",
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

                // 2. Priority / Display Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Display Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            FestivalStudioConfig.MODE_AUTO to "Auto + Custom",
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
                                        fontSize = 11.5.sp,
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

                // 3. Custom Banner Override Card
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
                                        "Custom Store Banner Override",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Create or push a festive offer banner anytime",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = customEnabled,
                                    onCheckedChange = { customEnabled = it },
                                    modifier = Modifier.testTag("studio_custom_enabled_switch")
                                )
                            }

                            // Preset Selector Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                studioPresetTemplates.forEach { tpl ->
                                    val isChosen = stylePreset.equals(tpl.preset.key, ignoreCase = true)
                                    FilterChip(
                                        selected = isChosen,
                                        onClick = {
                                            val oldTpl = studioPresetTemplates.find {
                                                it.preset.key.equals(stylePreset, ignoreCase = true)
                                            }
                                            stylePreset = tpl.preset.key
                                            accentColorHex = tpl.defaultHex
                                            if (festivalName.isBlank() || festivalName == oldTpl?.defaultTitle) {
                                                festivalName = tpl.defaultTitle
                                            }
                                            if (greetingMessage.isBlank() || greetingMessage == oldTpl?.defaultGreeting) {
                                                greetingMessage = tpl.defaultGreeting
                                            }
                                            if (badgeText.isBlank() || badgeText == oldTpl?.preset?.defaultBadge) {
                                                badgeText = tpl.preset.defaultBadge
                                            }
                                            customEnabled = true
                                        },
                                        label = { Text(tpl.label, fontSize = 11.5.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = festivalName,
                                onValueChange = { festivalName = it },
                                label = { Text("Festival / Offer Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = badgeText,
                                    onValueChange = { badgeText = it },
                                    label = { Text("Badge Text") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                                OutlinedTextField(
                                    value = accentColorHex,
                                    onValueChange = { accentColorHex = it },
                                    label = { Text("Color Hex") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            OutlinedTextField(
                                value = greetingMessage,
                                onValueChange = { greetingMessage = it },
                                label = { Text("Greeting / Offer Message") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Schedule Dates + Quick Helpers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = startDate,
                                    onValueChange = { startDate = it },
                                    label = { Text("Start (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = endDate,
                                    onValueChange = { endDate = it },
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
                                        startDate = sdf.format(today)
                                        endDate = sdf.format(cal.time)
                                    },
                                    label = { Text("Today – +7d", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        startDate = sdf.format(cal.time)
                                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                        endDate = sdf.format(cal.time)
                                    },
                                    label = { Text("Full Month", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = {
                                        startDate = ""
                                        endDate = ""
                                    },
                                    label = { Text("No Dates", fontSize = 11.sp) }
                                )
                            }

                            // Gallery Image Picker & Preview
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
                                        if (pickedImageUri != null || bannerImageUrl.isNotBlank()) "Change Banner Image" else "Pick Banner Photo",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (pickedImageUri != null || bannerImageUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            pickedImageUri = null
                                            bannerImageUrl = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear banner image", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (pickedImageUri != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = pickedImageUri),
                                        contentDescription = "Selected festival banner",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else if (bannerImageUrl.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageModelHelper.rememberImageModel(bannerImageUrl),
                                        contentDescription = "Synced festival banner",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            if (customEnabled && (festivalName.isNotBlank() || greetingMessage.isNotBlank())) {
                                Text(
                                    "Live Card Preview",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FestivalBannerCard(
                                    festival = draftCustomFestival.copy(enabled = true),
                                    pageIndex = 0,
                                    totalPages = 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 4. Save & Sync CTA
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
                        onClick = {
                            val cleanCustom = FestivalConfig(
                                enabled = customEnabled,
                                festivalName = festivalName.trim(),
                                greetingMessage = greetingMessage.trim(),
                                badgeText = badgeText.trim(),
                                accentColorHex = accentColorHex.trim(),
                                bannerImageUrl = bannerImageUrl.trim(),
                                startDate = startDate.trim(),
                                endDate = endDate.trim(),
                                stylePreset = stylePreset
                            )
                            val updatedStudio = FestivalStudioConfig(
                                autoCalendarEnabled = autoCalendarEnabled,
                                applyFestivalThemeToApp = applyThemeToApp,
                                overrideMode = overrideMode,
                                customFestival = cleanCustom,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.saveAndSyncFestivalStudio(updatedStudio, pickedImageUri)
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
