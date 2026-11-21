package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.example.ui.components.OTP_CODE_LENGTH
import com.example.ui.components.OtpCodeBoxesInput
import com.example.ui.components.OtpCountdownBanner
import com.example.ui.theme.LocalExtendedColors

enum class AuthMode { SIGN_IN, CREATE_ACCOUNT }

@Composable
fun AuthGateScreen(viewModel: StockViewModel) {
    val ext = LocalExtendedColors.current
    val lockRemaining by viewModel.lockRemainingSeconds.collectAsState()
    val otpRemaining by viewModel.authManager.otpRemainingSeconds.collectAsState()
    val pendingVerificationEmail by viewModel.authManager.pendingVerificationEmail.collectAsState()
    val deepLinkMessage by viewModel.authManager.authDeepLinkMessage.collectAsState()
    val deepLinkError by viewModel.authManager.authDeepLinkError.collectAsState()
    val isLocked = lockRemaining > 0

    var authMode by remember {
        mutableStateOf(
            if (pendingVerificationEmail != null) AuthMode.CREATE_ACCOUNT else AuthMode.SIGN_IN
        )
    }
    var email by remember { mutableStateOf(viewModel.authManager.getLastSignedInEmail()) }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Forgot Password state
    var showForgotPassword by remember { mutableStateOf(false) }
    var resetOtpSent by remember { mutableStateOf(false) }
    var resetOtp by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }
    var resetPasswordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkMessage, deepLinkError) {
        if (!deepLinkMessage.isNullOrBlank()) {
            successMessage = deepLinkMessage!!
            errorMessage = ""
            authMode = AuthMode.SIGN_IN
        }
        if (!deepLinkError.isNullOrBlank()) {
            errorMessage = deepLinkError!!
            successMessage = ""
        }
    }

    fun submitAuth() {
        if (isLocked) {
            errorMessage = "Too many wrong attempts. Try again in ${lockRemaining}s."
            return
        }
        errorMessage = ""
        successMessage = ""
        viewModel.authManager.clearDeepLinkStatus()

        val cleanEmail = email.trim()
        val cleanPass = password.trim()

        if (authMode == AuthMode.SIGN_IN) {
            // Sign in using Email & Password (with legacy master password fallback if no email)
            if (cleanEmail.isNotEmpty() && cleanEmail.contains("@")) {
                if (cleanPass.isEmpty()) {
                    errorMessage = "Enter your password."
                    return
                }
                isChecking = true
                viewModel.authManager.signInEmailPassword(cleanEmail, cleanPass) { success, err ->
                    isChecking = false
                    if (success) {
                        successMessage = "Signed in successfully!"
                    } else {
                        errorMessage = err ?: "Invalid email or password."
                    }
                }
            } else if (cleanPass.isNotEmpty()) {
                isChecking = true
                val res = viewModel.authManager.unlockApp(cleanPass)
                isChecking = false
                when (res) {
                    is AuthManager.AuthResult.Success -> {
                        successMessage = "Unlocked successfully!"
                    }
                    is AuthManager.AuthResult.Locked -> {
                        errorMessage = "Too many wrong attempts. Locked for ${res.secondsLeft}s."
                        password = ""
                    }
                    is AuthManager.AuthResult.WrongPassword -> {
                        val s = if (res.triesLeft == 1) "try" else "tries"
                        errorMessage = "Enter a valid email or correct password. ${res.triesLeft} $s left."
                        password = ""
                    }
                    is AuthManager.AuthResult.Error -> {
                        errorMessage = res.message
                    }
                }
            } else {
                errorMessage = "Enter your email and password."
            }
        } else {
            // Create Account: Email + Password + Email OTP Verification (only for new accounts)
            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                errorMessage = "Enter a valid email address."
                return
            }
            if (cleanPass.length < 6) {
                errorMessage = "Password must be at least 6 characters."
                return
            }
            isChecking = true
            otpCode = ""
            viewModel.authManager.signUpEmailPassword(cleanEmail, cleanPass) { success, msg ->
                isChecking = false
                if (success) {
                    successMessage = msg ?: "OTP code sent to $cleanEmail."
                } else {
                    errorMessage = msg ?: "Could not create account."
                }
            }
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.14f else 0.08f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("auth_gate_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val waitingForOtp = authMode == AuthMode.CREATE_ACCOUNT && !pendingVerificationEmail.isNullOrBlank()

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (waitingForOtp) Icons.Default.MarkEmailRead else Icons.Default.Lock,
                        contentDescription = if (waitingForOtp) "Verify Email OTP" else "Authentication Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when {
                        showForgotPassword -> "Reset Password"
                        waitingForOtp -> "Verify Email OTP"
                        else -> "Stock"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        showForgotPassword && !resetOtpSent -> "Enter your registered email to receive a 5-minute reset OTP."
                        showForgotPassword && resetOtpSent -> "Enter the OTP sent to your email and set a new password."
                        waitingForOtp -> "Enter the verification OTP code sent to your inbox."
                        authMode == AuthMode.SIGN_IN -> "Sign in with your email and password"
                        else -> "Create an account with email OTP verification"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector Tabs
                if (!showForgotPassword) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(
                            text = "Sign In",
                            selected = authMode == AuthMode.SIGN_IN,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_signin")
                        ) {
                            authMode = AuthMode.SIGN_IN
                            errorMessage = ""
                            successMessage = ""
                            viewModel.authManager.clearDeepLinkStatus()
                        }
                        TabButton(
                            text = "Create Account",
                            selected = authMode == AuthMode.CREATE_ACCOUNT,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_signup")
                        ) {
                            authMode = AuthMode.CREATE_ACCOUNT
                            errorMessage = ""
                            successMessage = ""
                            viewModel.authManager.clearDeepLinkStatus()
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (showForgotPassword) {
                    fun sendResetOtp() {
                        if (isChecking) return
                        errorMessage = ""
                        successMessage = ""
                        isChecking = true
                        viewModel.authManager.requestPasswordResetOtp(email) { ok, msg ->
                            isChecking = false
                            if (ok) {
                                resetOtpSent = true
                                successMessage = msg ?: "Reset OTP sent."
                            } else {
                                errorMessage = msg ?: "Could not send reset OTP."
                            }
                        }
                    }

                    fun submitResetPassword() {
                        if (isChecking) return
                        errorMessage = ""
                        successMessage = ""
                        isChecking = true
                        viewModel.authManager.verifyPasswordResetAndUpdate(
                            email = email,
                            otp = resetOtp,
                            newPassword = resetNewPassword
                        ) { ok, msg ->
                            isChecking = false
                            if (ok) {
                                resetOtp = ""
                                resetNewPassword = ""
                                resetOtpSent = false
                                showForgotPassword = false
                                successMessage = msg ?: "Password updated!"
                            } else {
                                errorMessage = msg ?: "Could not reset password."
                            }
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = "" },
                        label = { Text("Account Email Address") },
                        placeholder = { Text("user@store.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        enabled = !isChecking && !resetOtpSent,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_forgot_email_input")
                    )

                    if (!resetOtpSent) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { sendResetOtp() },
                            enabled = !isChecking,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_send_reset_otp_button")
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Send Reset OTP", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))

                        OtpCountdownBanner(
                            secondsRemaining = otpRemaining,
                            tag = "auth_otp_timer_badge"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OtpCodeBoxesInput(
                            value = resetOtp,
                            onValueChange = { input ->
                                resetOtp = input
                                errorMessage = ""
                            },
                            label = "Verification Code (6-digit)",
                            testTag = "auth_forgot_otp_input",
                            enabled = !isChecking,
                            imeAction = ImeAction.Next
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = resetNewPassword,
                            onValueChange = { resetNewPassword = it; errorMessage = "" },
                            label = { Text("New Password (min 6 chars)") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            enabled = !isChecking,
                            visualTransformation = if (resetPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitResetPassword() }),
                            trailingIcon = {
                                IconButton(onClick = { resetPasswordVisible = !resetPasswordVisible }) {
                                    Icon(
                                        imageVector = if (resetPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (resetPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_forgot_new_password_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { submitResetPassword() },
                            enabled = !isChecking && !isLocked,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_confirm_reset_button")
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isLocked) "Locked (${lockRemaining}s)" else "Verify OTP & Reset Password",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { sendResetOtp() },
                            enabled = !isChecking,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_resend_reset_otp_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resend Reset OTP")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = {
                            showForgotPassword = false
                            resetOtpSent = false
                            resetOtp = ""
                            resetNewPassword = ""
                            errorMessage = ""
                            successMessage = ""
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("auth_back_to_signin_button")
                    ) {
                        Text("Back to Sign In", fontSize = 13.sp)
                    }
                } else if (waitingForOtp) {
                    val targetEmail = pendingVerificationEmail.orEmpty()

                    fun submitOtp() {
                        if (isChecking) return
                        errorMessage = ""
                        successMessage = ""
                        val cleanOtp = otpCode.trim()
                        if (cleanOtp.length != OTP_CODE_LENGTH || !cleanOtp.all { it.isDigit() }) {
                            errorMessage = "Enter the 6-digit OTP code from your email."
                            return
                        }
                        isChecking = true
                        viewModel.authManager.verifySignupOtp(targetEmail, cleanOtp) { success, msg ->
                            isChecking = false
                            if (success) {
                                otpCode = ""
                                successMessage = msg ?: "Email verified! Welcome to Stock."
                            } else {
                                errorMessage = msg ?: "Invalid or expired OTP code."
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = targetEmail,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Check your inbox (or Spam folder) for the verification code and enter it below.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OtpCountdownBanner(
                        secondsRemaining = otpRemaining,
                        tag = "auth_otp_timer_badge"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OtpCodeBoxesInput(
                        value = otpCode,
                        onValueChange = { input ->
                            otpCode = input
                            errorMessage = ""
                        },
                        label = "Verification Code (6-digit)",
                        testTag = "auth_otp_input",
                        enabled = !isChecking,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { submitOtp() })
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { submitOtp() },
                        enabled = !isChecking && !isLocked,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_verify_otp_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isLocked) "Locked (${lockRemaining}s)" else "Verify OTP & Enter",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (isChecking) return@OutlinedButton
                            isChecking = true
                            errorMessage = ""
                            successMessage = ""
                            viewModel.authManager.resendConfirmationEmail(targetEmail) { ok, msg ->
                                isChecking = false
                                if (ok) {
                                    successMessage = msg ?: "New OTP code sent."
                                } else {
                                    errorMessage = msg ?: "Could not resend OTP code."
                                }
                            }
                        },
                        enabled = !isChecking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_resend_otp_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Resend OTP Code")
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = {
                            otpCode = ""
                            viewModel.authManager.clearPendingVerification()
                            errorMessage = ""
                            successMessage = ""
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("auth_change_email_button")
                    ) {
                        Text("Use a different email", fontSize = 13.sp)
                    }
                } else {
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = "" },
                        label = { Text("Email Address") },
                        placeholder = { Text("user@store.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        enabled = !isLocked && !isChecking,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        label = {
                            Text(
                                if (authMode == AuthMode.SIGN_IN) "Password" else "Create Password (min 6 chars)"
                            )
                        },
                        placeholder = { Text("••••••••") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
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
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submitAuth() },
                        enabled = !isLocked && !isChecking,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                isLocked -> "Locked (${lockRemaining}s)"
                                isChecking -> "Please wait…"
                                authMode == AuthMode.SIGN_IN -> "Sign In"
                                else -> "Create Account"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (authMode == AuthMode.SIGN_IN) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                showForgotPassword = true
                                resetOtpSent = false
                                resetOtp = ""
                                resetNewPassword = ""
                                errorMessage = ""
                                successMessage = ""
                                viewModel.authManager.clearDeepLinkStatus()
                            },
                            enabled = !isChecking,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("auth_forgot_password_button")
                        ) {
                            Text("Forgot Password?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.danger.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_error_banner")
                        ) {
                            Text(
                                text = errorMessage,
                                color = ext.danger,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = successMessage.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.positive.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, ext.positive.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_success_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ext.positive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = successMessage,
                                    color = ext.positive,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
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
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
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
