package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthManager
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.rememberAppHaptics

@Composable
fun AdminPinUnlockDialog(
    viewModel: StockViewModel,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val haptics = rememberAppHaptics()
    val lockSeconds by viewModel.adminLockRemainingSeconds.collectAsState()
    val hasCustomPin by viewModel.hasCustomAdminPin.collectAsState()
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lockSeconds) {
        if (lockSeconds == 0 && inlineError?.contains("Locked", ignoreCase = true) == true) {
            inlineError = null
        }
    }

    fun submitPin() {
        when (val res = viewModel.authManager.unlockAdminWithResult(pin)) {
            is AuthManager.AdminAuthResult.Success -> {
                haptics.confirm()
                inlineError = null
                onSuccess()
            }
            is AuthManager.AdminAuthResult.Locked -> {
                haptics.reject()
                inlineError = "Too many wrong attempts. Locked for ${res.secondsLeft}s."
                pin = ""
            }
            is AuthManager.AdminAuthResult.WrongPin -> {
                haptics.reject()
                inlineError = "Incorrect PIN. ${res.triesLeft} ${if (res.triesLeft == 1) "attempt" else "attempts"} remaining."
                pin = ""
            }
            is AuthManager.AdminAuthResult.Error -> {
                haptics.reject()
                inlineError = res.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("admin_pin_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("Admin Access", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (hasCustomPin) "Protected with salted PIN" else "Default PIN active",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the admin PIN to reveal Announcement Studio and restricted controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        inlineError = null
                    },
                    enabled = lockSeconds == 0,
                    label = { Text("Admin PIN") },
                    placeholder = { Text("Enter PIN") },
                    singleLine = true,
                    isError = inlineError != null || lockSeconds > 0,
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (lockSeconds == 0) submitPin() }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input")
                )

                if (lockSeconds > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.danger.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = ext.danger,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Too many wrong attempts. Try again in ${lockSeconds}s",
                                color = ext.danger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("admin_pin_lock_banner")
                            )
                        }
                    }
                } else if (inlineError != null) {
                    Text(
                        text = inlineError!!,
                        color = ext.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("admin_pin_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitPin() },
                enabled = lockSeconds == 0,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("admin_pin_unlock_button")
            ) {
                Text(if (lockSeconds > 0) "Locked (${lockSeconds}s)" else "Unlock")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangeAdminPinDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val haptics = rememberAppHaptics()
    val lockSeconds by viewModel.adminLockRemainingSeconds.collectAsState()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPins by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    fun submitChange() {
        val cleanNew = newPin.trim()
        val cleanConfirm = confirmPin.trim()
        if (cleanNew.length < 4) {
            haptics.reject()
            inlineError = "New PIN must be at least 4 digits."
            return
        }
        if (cleanNew != cleanConfirm) {
            haptics.reject()
            inlineError = "New PIN and confirmation do not match."
            return
        }
        when (val res = viewModel.changeAdminPin(currentPin, cleanNew)) {
            is AuthManager.AdminAuthResult.Success -> {
                haptics.confirm()
                onDismiss()
            }
            is AuthManager.AdminAuthResult.Locked -> {
                haptics.reject()
                inlineError = "Too many wrong attempts. Try again in ${res.secondsLeft}s."
                currentPin = ""
            }
            is AuthManager.AdminAuthResult.WrongPin -> {
                haptics.reject()
                inlineError = "Current PIN is wrong (${res.triesLeft} ${if (res.triesLeft == 1) "try" else "tries"} left)."
                currentPin = ""
            }
            is AuthManager.AdminAuthResult.Error -> {
                haptics.reject()
                inlineError = res.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("change_admin_pin_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("Change Admin PIN", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Salted PBKDF2-SHA256 • Syncs to all devices",
                        fontSize = 11.sp,
                        color = ext.positive,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val visual = if (showPins) VisualTransformation.None else PasswordVisualTransformation()
                val kbd = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)

                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it; inlineError = null },
                    enabled = lockSeconds == 0,
                    label = { Text("Current Admin PIN") },
                    singleLine = true,
                    visualTransformation = visual,
                    keyboardOptions = kbd,
                    trailingIcon = {
                        IconButton(onClick = { showPins = !showPins }) {
                            Icon(
                                imageVector = if (showPins) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPins) "Hide PINs" else "Show PINs"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_current_input")
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it; inlineError = null },
                    enabled = lockSeconds == 0,
                    label = { Text("New Admin PIN (min 4 digits)") },
                    singleLine = true,
                    visualTransformation = visual,
                    keyboardOptions = kbd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_new_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it; inlineError = null },
                    enabled = lockSeconds == 0,
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = visual,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (lockSeconds == 0) submitChange() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_confirm_input")
                )

                if (lockSeconds > 0) {
                    Text(
                        text = "Locked for ${lockSeconds}s due to repeated wrong attempts.",
                        color = ext.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (inlineError != null) {
                    Text(
                        text = inlineError!!,
                        color = ext.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("change_pin_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitChange() },
                enabled = lockSeconds == 0,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("change_pin_save_button")
            ) {
                Text(if (lockSeconds > 0) "Locked (${lockSeconds}s)" else "Save & Sync PIN")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
