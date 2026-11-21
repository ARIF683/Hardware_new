package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthManager
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors

@Composable
fun ChangeEmailDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val currentEmail by viewModel.currentUserEmail.collectAsState()
    val otpRemaining by viewModel.authManager.otpRemainingSeconds.collectAsState()
    val resendCooldown by viewModel.authManager.resendCooldownSeconds.collectAsState()
    val lockRemaining by viewModel.authManager.lockRemainingSeconds.collectAsState()
    val isLocked = lockRemaining > 0

    var newEmail by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Change Email Address", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current email: ${currentEmail ?: "Not signed in"}",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newEmail,
                    onValueChange = {
                        newEmail = it
                        errorMsg = ""
                    },
                    label = { Text("New Email Address") },
                    placeholder = { Text("newname@store.com") },
                    singleLine = true,
                    enabled = !isBusy && !otpSent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_email_new_input")
                )

                if (otpSent) {
                    OtpCountdownBanner(
                        secondsRemaining = otpRemaining,
                        tag = "change_email_timer_badge"
                    )

                    OtpCodeBoxesInput(
                        value = otpCode,
                        onValueChange = { input ->
                            otpCode = input
                            errorMsg = ""
                        },
                        label = "Verification Code (6-digit)",
                        testTag = "change_email_otp_input",
                        enabled = !isBusy
                    )

                    TextButton(
                        onClick = {
                            if (isBusy || resendCooldown > 0) return@TextButton
                            isBusy = true
                            errorMsg = ""
                            statusMsg = ""
                            viewModel.authManager.requestChangeEmailOtp(newEmail) { ok, msg ->
                                isBusy = false
                                if (ok) {
                                    statusMsg = msg ?: "Verification OTP re-sent."
                                } else {
                                    errorMsg = msg ?: "Failed to resend OTP."
                                }
                            }
                        },
                        enabled = !isBusy && resendCooldown <= 0,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("change_email_resend_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend OTP Code"
                        )
                    }
                }

                if (statusMsg.isNotEmpty()) {
                    Text(statusMsg, color = ext.positive, fontSize = 12.sp)
                }
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ext.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (!otpSent) {
                Button(
                    onClick = {
                        if (isBusy || resendCooldown > 0) return@Button
                        isBusy = true
                        errorMsg = ""
                        statusMsg = ""
                        viewModel.authManager.requestChangeEmailOtp(newEmail) { ok, msg ->
                            isBusy = false
                            if (ok) {
                                otpSent = true
                                statusMsg = msg ?: "OTP sent to ${newEmail.trim()}."
                            } else {
                                errorMsg = msg ?: "Could not request email change."
                            }
                        }
                    },
                    enabled = !isBusy && resendCooldown <= 0,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("change_email_send_otp_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        if (resendCooldown > 0) "Send OTP (${resendCooldown}s)" else "Send OTP"
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (isBusy) return@Button
                        isBusy = true
                        errorMsg = ""
                        statusMsg = ""
                        viewModel.authManager.confirmChangeEmailOtp(newEmail, otpCode) { ok, msg ->
                            isBusy = false
                            if (ok) {
                                viewModel.showToast(msg ?: "Email address updated!")
                                onDismiss()
                            } else {
                                errorMsg = msg ?: "Invalid or expired OTP code."
                            }
                        }
                    },
                    enabled = !isBusy && !isLocked,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("change_email_confirm_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isLocked) "Locked (${lockRemaining}s)" else "Verify & Update")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteAccountOtpDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val currentEmail by viewModel.currentUserEmail.collectAsState()
    val otpRemaining by viewModel.authManager.otpRemainingSeconds.collectAsState()
    val resendCooldown by viewModel.authManager.resendCooldownSeconds.collectAsState()
    val lockRemaining by viewModel.authManager.lockRemainingSeconds.collectAsState()
    val isLocked = lockRemaining > 0

    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ext.danger)
                Text("Delete Account", fontWeight = FontWeight.Bold, color = ext.danger)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ext.danger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentEmail ?: "Signed-in account",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.danger
                        )
                        Text(
                            text = "This permanently deletes your login credentials and role access. To confirm, verify with an OTP code sent to your email (expires in 5 minutes).",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (otpSent) {
                    OtpCountdownBanner(
                        secondsRemaining = otpRemaining,
                        tag = "delete_account_timer_badge"
                    )

                    OtpCodeBoxesInput(
                        value = otpCode,
                        onValueChange = { input ->
                            otpCode = input
                            errorMsg = ""
                        },
                        label = "Verification Code (6-digit)",
                        testTag = "delete_account_otp_input",
                        enabled = !isBusy,
                        accentColor = ext.danger
                    )

                    TextButton(
                        onClick = {
                            if (isBusy || resendCooldown > 0) return@TextButton
                            isBusy = true
                            errorMsg = ""
                            statusMsg = ""
                            viewModel.authManager.requestDeleteAccountOtp { ok, msg ->
                                isBusy = false
                                if (ok) {
                                    statusMsg = msg ?: "New deletion OTP sent."
                                } else {
                                    errorMsg = msg ?: "Could not resend OTP."
                                }
                            }
                        },
                        enabled = !isBusy && resendCooldown <= 0,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("delete_account_resend_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend OTP Code"
                        )
                    }
                }

                if (statusMsg.isNotEmpty()) {
                    Text(statusMsg, color = ext.positive, fontSize = 12.sp)
                }
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ext.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (!otpSent) {
                Button(
                    onClick = {
                        if (isBusy || resendCooldown > 0) return@Button
                        isBusy = true
                        errorMsg = ""
                        statusMsg = ""
                        viewModel.authManager.requestDeleteAccountOtp { ok, msg ->
                            isBusy = false
                            if (ok) {
                                otpSent = true
                                statusMsg = msg ?: "Deletion OTP sent."
                            } else {
                                errorMsg = msg ?: "Failed to send deletion OTP."
                            }
                        }
                    },
                    enabled = !isBusy && resendCooldown <= 0,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.danger),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("delete_account_send_otp_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        if (resendCooldown > 0) "Send Deletion OTP (${resendCooldown}s)" else "Send Deletion OTP"
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (isBusy) return@Button
                        isBusy = true
                        errorMsg = ""
                        statusMsg = ""
                        viewModel.authManager.confirmDeleteAccountWithOtp(otpCode) { ok, msg ->
                            isBusy = false
                            if (ok) {
                                viewModel.showToast(msg ?: "Account permanently deleted.")
                                onDismiss()
                            } else {
                                errorMsg = msg ?: "Failed to verify OTP or delete account."
                            }
                        }
                    },
                    enabled = !isBusy && !isLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.danger),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("delete_account_confirm_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isLocked) "Locked (${lockRemaining}s)" else "Verify & Delete")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OtpCountdownBanner(
    secondsRemaining: Int,
    tag: String
) {
    val ext = LocalExtendedColors.current
    val expired = secondsRemaining <= 0
    val tint = if (expired) ext.danger else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = if (expired) {
                    "OTP expired (5-minute limit). Tap Resend for a new code."
                } else {
                    "OTP expires in ${AuthManager.formatOtpCountdown(secondsRemaining)}"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}
