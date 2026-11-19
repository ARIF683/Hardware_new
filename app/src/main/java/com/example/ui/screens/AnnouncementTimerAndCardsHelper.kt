package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnnouncementConfig
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.FestivalConfig
import com.example.ui.components.resolveFestivalStyle
import com.example.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatTimerButtonLabel(millis: Long, fallback: String): String {
    if (millis <= 0L) return fallback
    return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
}

private fun launchDateTimePicker(
    context: Context,
    initialMillis: Long,
    defaultHour: Int,
    defaultMinute: Int,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance()
    if (initialMillis > 0L) {
        cal.timeInMillis = initialMillis
    } else {
        cal.set(Calendar.HOUR_OF_DAY, defaultHour)
        cal.set(Calendar.MINUTE, defaultMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val chosen = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, hourOfDay, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(chosen.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
fun AnnouncementTimerSection(
    startAtMillis: Long,
    endAtMillis: Long,
    onUpdateRange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val isValidRange = startAtMillis <= 0L || endAtMillis <= 0L || endAtMillis > startAtMillis
    val timerSummary = remember(startAtMillis, endAtMillis) {
        AnnouncementConfig(
            enabled = true,
            message = "preview",
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis
        ).timerSummaryLabel()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (!isValidRange) ext.danger.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (!isValidRange) ext.danger else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Announcement Timer (Auto Keep & Remove)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            !isValidRange -> "End time must be after start time"
                            timerSummary != null -> timerSummary
                            else -> "Always active until turned off manually"
                        },
                        fontSize = 11.sp,
                        color = when {
                            !isValidRange -> ext.danger
                            timerSummary != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Quick duration chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onUpdateRange(0L, 0L) },
                    label = { Text("No Timer", fontSize = 11.sp) },
                    modifier = Modifier.testTag("studio_timer_clear_chip")
                )
                AssistChip(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onUpdateRange(now, now + 3_600_000L)
                    },
                    label = { Text("1 Hr", fontSize = 11.sp) },
                    modifier = Modifier.testTag("studio_timer_1h_chip")
                )
                AssistChip(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onUpdateRange(now, now + 4 * 3_600_000L)
                    },
                    label = { Text("4 Hrs", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val endOfDay = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onUpdateRange(now, if (endOfDay > now) endOfDay else now + 86_400_000L)
                    },
                    label = { Text("Today", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onUpdateRange(now, now + 86_400_000L)
                    },
                    label = { Text("24 Hrs", fontSize = 11.sp) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start Date/Time picker
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            launchDateTimePicker(context, startAtMillis, 9, 0) { picked ->
                                onUpdateRange(picked, endAtMillis)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("studio_timer_start_button")
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimerButtonLabel(startAtMillis, "Start: Now"),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (startAtMillis > 0L) {
                        IconButton(
                            onClick = { onUpdateRange(0L, endAtMillis) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear start time", modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // End Date/Time picker
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val defaultCal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 4) }
                            launchDateTimePicker(
                                context,
                                endAtMillis,
                                defaultCal.get(Calendar.HOUR_OF_DAY),
                                defaultCal.get(Calendar.MINUTE)
                            ) { picked ->
                                onUpdateRange(startAtMillis, picked)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("studio_timer_end_button")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimerButtonLabel(endAtMillis, "End: None"),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (endAtMillis > 0L) {
                        IconButton(
                            onClick = { onUpdateRange(startAtMillis, 0L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear end time", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomFestivalCardItemRow(
    card: FestivalConfig,
    index: Int,
    isEditing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val resolved = remember(card) { resolveFestivalStyle(card) }
    val scheduleLabel = remember(card.startDate, card.endDate) { card.scheduleSummaryLabel() }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isEditing) resolved.primaryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isEditing) 1.5.dp else 1.dp,
            color = if (isEditing) resolved.primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_custom_card_row_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(resolved.primaryColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.festivalName.ifBlank { resolved.badgeText },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (card.enabled) "Active" else "Paused")
                        if (scheduleLabel != null) append(" • $scheduleLabel")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = card.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("studio_custom_toggle_$index")
            )
            IconButton(
                onClick = onSelectEdit,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("studio_edit_custom_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit custom festival card",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("studio_delete_custom_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete custom festival card",
                    tint = ext.danger,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BuiltInCalendarFestivalsManager(
    hiddenKeys: List<String>,
    onToggleHideKey: (String, Boolean) -> Unit,
    onRestoreAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val catalog = remember { AutoFestivalCalendar.upcomingAndActiveCalendarEntries() }
    val hiddenSet = remember(hiddenKeys) {
        hiddenKeys.map { AutoFestivalCalendar.autoFestivalKey(it) }.toSet()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manage Calendar Festivals",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (hiddenSet.isEmpty()) {
                            "All ${catalog.size} built-in calendar festivals enabled"
                        } else {
                            "${hiddenSet.size} hidden • Tap to show/remove individual holidays"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hiddenSet.isNotEmpty()) {
                    TextButton(onClick = onRestoreAll) {
                        Text("Restore All", fontSize = 11.sp)
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("studio_expand_calendar_festivals")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse calendar festivals" else "Expand calendar festivals"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    catalog.forEach { entry ->
                        val key = AutoFestivalCalendar.autoFestivalKey(entry.festivalName)
                        val isVisible = key !in hiddenSet
                        val schedule = entry.scheduleSummaryLabel() ?: "${entry.startDate} – ${entry.endDate}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.festivalName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = schedule,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { shouldShow ->
                                    onToggleHideKey(key, !shouldShow)
                                },
                                modifier = Modifier.testTag("studio_calendar_toggle_${key.replace(" ", "_")}")
                            )
                        }
                    }
                }
            }
        }
    }
}
