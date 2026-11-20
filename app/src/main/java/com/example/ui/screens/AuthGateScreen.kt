package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthManager
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors

enum class AuthMode { SIGN_IN, CREATE_ACCOUNT }

@Composable
fun AuthGateScreen(viewModel: StockViewModel) {
    val ext = LocalExtendedColors.current
    val lockRemaining by viewModel.lockRemainingSeconds.collectAsState()
    val isLocked = lockRemaining > 0

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("admin@hardwarestock.com") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    fun submitAuth() {
        if (isLocked) {
            errorMessage = "Too many wrong attempts. Try again in ${lockRemaining}s."
            return
        }
        errorMessage = ""
        successMessage = ""
        isChecking = true

        val cleanEmail = email.trim()
        val cleanPass = password.trim()

        if (authMode == AuthMode.SIGN_IN) {
            // Sign in requires only Email and Password (No OTP)
            if (cleanEmail.isEmpty() || cleanPass.isEmpty() || !cleanEmail.contains("@")) {
                errorMessage = "Enter a valid email and password."
                isChecking = false
                return
            }
            viewModel.authManager.signInEmailPassword(cleanEmail, cleanPass) { success, err ->
                isChecking = false
                if (success) {
                    successMessage = "Signed in successfully!"
                } else {
                    // Fallback to legacy master unlock app password if email/pass failed
                    val legacyRes = viewModel.authManager.unlockApp(cleanPass)
                    if (legacyRes is AuthManager.AuthResult.Success) {
                        successMessage = "Unlocked successfully!"
                    } else {
                        errorMessage = err ?: "Sign in failed. Check credentials."
                    }
                }
            }
        } else {
            // Create Account flow: requires email, password, and email OTP verification
            if (!cleanEmail.contains("@") || cleanPass.length < 6) {
                errorMessage = "Enter a valid email and password (minimum 6 characters)."
                isChecking = false
                return
            }
            if (!otpSent) {
                // Step 1: Send OTP for account creation
                viewModel.authManager.sendEmailOtp(cleanEmail) { success, msg ->
                    isChecking = false
                    if (success) {
                        otpSent = true
                        successMessage = msg ?: "OTP verification code sent to $cleanEmail."
                    } else {
                        errorMessage = msg ?: "Failed to send OTP code."
                    }
                }
            } else {
                // Step 2: Verify OTP and finalize account creation
                if (otpCode.trim().length < 4) {
                    errorMessage = "Enter the 6-digit verification code."
                    isChecking = false
                    return
                }
                viewModel.authManager.verifyEmailOtp(cleanEmail, otpCode.trim()) { success, msg ->
                    isChecking = false
                    if (success) {
                        // Also register user with password
                        viewModel.authManager.upsertUser(cleanEmail, cleanEmail.contains("admin"))
                        successMessage = "Account created & verified successfully!"
                    } else {
                        errorMessage = msg ?: "Invalid verification code."
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "HardwareStock Portal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (authMode == AuthMode.SIGN_IN) "Sign in with your email & password" else "Create a new account with email verification",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Mode Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TabButton(
                        text = "Sign In",
                        selected = authMode == AuthMode.SIGN_IN,
                        modifier = Modifier.weight(1f)
                    ) {
                        authMode = AuthMode.SIGN_IN
                        otpSent = false
                        otpCode = ""
                        errorMessage = ""
                        successMessage = ""
                    }
                    TabButton(
                        text = "Create Account",
                        selected = authMode == AuthMode.CREATE_ACCOUNT,
                        modifier = Modifier.weight(1f)
                    ) {
                        authMode = AuthMode.CREATE_ACCOUNT
                        otpSent = false
                        otpCode = ""
                        errorMessage = ""
                        successMessage = ""
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = "" },
                    label = { Text("Email Address") },
                    placeholder = { Text("user@store.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    enabled = !isLocked && !isChecking && !(authMode == AuthMode.CREATE_ACCOUNT && otpSent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password Field (shown during Sign In or before OTP is sent in Create Account)
                if (authMode == AuthMode.SIGN_IN || (authMode == AuthMode.CREATE_ACCOUNT && !otpSent)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        label = { Text(if (authMode == AuthMode.SIGN_IN) "Password" else "Create Password") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        enabled = !isLocked && !isChecking,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitAuth() }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide" else "Show"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // OTP verification field (shown only during Create Account after OTP is sent)
                if (authMode == AuthMode.CREATE_ACCOUNT && otpSent) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it; errorMessage = "" },
                        label = { Text("6-Digit Email OTP Code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        enabled = !isLocked && !isChecking,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitAuth() }),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { otpSent = false; otpCode = "" }) {
                        Text("Change email / password or resend OTP", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { submitAuth() },
                    enabled = !isLocked && !isChecking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = when {
                            isLocked -> "Locked (${lockRemaining}s)"
                            isChecking -> "Processing…"
                            authMode == AuthMode.SIGN_IN -> "Sign In"
                            !otpSent -> "Send Verification OTP"
                            else -> "Verify & Create Account"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = ext.danger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (successMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = successMessage,
                        color = ext.positive,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
