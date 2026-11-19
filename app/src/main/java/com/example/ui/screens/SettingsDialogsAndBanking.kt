package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.Item
import com.example.data.remote.UpdateStatus
import com.example.ui.StockViewModel
import com.example.ui.theme.LocalExtendedColors
import com.example.util.XlsExporter
import com.example.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BadgePill(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BankingAndSignatureSubScreen(
    viewModel: StockViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("banking_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Banking, GST & Signature",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Invoice footer bank details, UPI QR & signatory",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
        ) {
            item {
                BankingAndSignatureContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BankingAndSignatureContent(viewModel: StockViewModel) {
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val bankingInfo by viewModel.bankingInfo.collectAsState()

    var bankName by remember(bankingInfo.bankName) { mutableStateOf(bankingInfo.bankName) }
    var accountHolder by remember(bankingInfo.accountHolder) { mutableStateOf(bankingInfo.accountHolder) }
    var accountNumber by remember(bankingInfo.accountNumber) { mutableStateOf(bankingInfo.accountNumber) }
    var ifscCode by remember(bankingInfo.ifscCode) { mutableStateOf(bankingInfo.ifscCode) }
    var branchName by remember(bankingInfo.branchName) { mutableStateOf(bankingInfo.branchName) }
    var upiId by remember(bankingInfo.upiId) { mutableStateOf(bankingInfo.upiId) }

    val signatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveSignatureFromUri(uri)
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveQrCodeFromUri(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Header Banner
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "GST Banking & Signature Setup",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                    Text(
                        "Details, QR code, and signature added here sync across all your devices and appear on GST bills & PDFs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Bank Account Details Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bank Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Used for receiving RTGS / NEFT / IMPS transfers from invoice buyers.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    placeholder = { Text("e.g. State Bank of India, HDFC Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it },
                    label = { Text("Account Holder / Business Legal Name") },
                    placeholder = { Text("e.g. HARDWARE & TOOLS STORE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account No.", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("502000xxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.3f).widthIn(min = 0.dp)
                    )
                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it.uppercase() },
                        label = { Text("IFSC Code", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("SBIN0001234") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Branch / City", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("Main Market Branch") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (VPA)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("store@okhdfcbank") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f).widthIn(min = 0.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        haptics.confirm()
                        viewModel.saveBankDetails(bankName, accountHolder, accountNumber, ifscCode, branchName, upiId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("banking_save_details_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Sync Bank Details", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Upload Signature Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorized Signature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Upload an image of your signature. Appears in the bottom-right corner of GST invoices.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val sigFile = bankingInfo.signaturePath?.let { File(it) }?.takeIf { it.exists() }
                val sigModel: Any? = sigFile ?: bankingInfo.signatureUrl?.takeIf { it.isNotBlank() }
                if (sigModel != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            AsyncImage(
                                model = sigModel,
                                contentDescription = "Authorized Signature Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgePill("Signature Active", ext.positive, Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    signatureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.reject()
                                    viewModel.clearSignature()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No signature uploaded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            signatureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("banking_upload_signature_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Signature Image", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Upload Payment QR Code Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UPI Payment QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Upload your PhonePe, Google Pay, Paytm, or BHIM QR code. Customers can scan to pay.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val qrFile = bankingInfo.qrCodePath?.let { File(it) }?.takeIf { it.exists() }
                val qrModel: Any? = qrFile ?: bankingInfo.qrCodeUrl?.takeIf { it.isNotBlank() }
                if (qrModel != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.size(160.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                AsyncImage(
                                    model = qrModel,
                                    contentDescription = "Payment QR Code Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BadgePill("QR Code Active", ext.positive, Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        qrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Change", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.reject()
                                        viewModel.clearQrCode()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Remove", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No UPI QR code uploaded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            qrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("banking_upload_qr_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload QR Code Image", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Live Invoice Footer Preview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Live GST Invoice Footer Preview",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color.LightGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("BANK DETAILS:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Bank: ${bankName.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color.DarkGray)
                            Text("A/C: ${accountNumber.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color.DarkGray)
                            Text("IFSC: ${ifscCode.ifBlank { "Not set" }}", fontSize = 9.sp, color = Color.DarkGray)
                            if (upiId.isNotBlank()) {
                                Text("UPI: $upiId", fontSize = 9.sp, color = Color.DarkGray)
                            }
                        }

                        if (bankingInfo.qrCodePath != null && File(bankingInfo.qrCodePath!!).exists()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color.LightGray),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    AsyncImage(
                                        model = File(bankingInfo.qrCodePath!!),
                                        contentDescription = "QR Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(2.dp)
                                    )
                                }
                                Text("Scan to Pay", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text("For Store", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (bankingInfo.signaturePath != null && File(bankingInfo.signaturePath!!).exists()) {
                                AsyncImage(
                                    model = File(bankingInfo.signaturePath!!),
                                    contentDescription = "Signature Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.height(28.dp).width(70.dp)
                                )
                            } else {
                                Text("[No signature]", fontSize = 8.sp, color = Color.Gray)
                            }
                            HorizontalDivider(modifier = Modifier.width(75.dp).padding(vertical = 2.dp), color = Color.Gray)
                            Text("Authorized Signatory", fontSize = 8.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsThemeAndLayoutDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val customThemeColor by viewModel.customThemeColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val itemsViewLayout by viewModel.itemsViewLayout.collectAsState()
    val navBarStyle by viewModel.navBarStyle.collectAsState()

    val presetColors = remember {
        listOf(
            Pair("Hardware Orange", "#EA580C"),
            Pair("Terracotta", "#D97757"),
            Pair("Emerald", "#10B981"),
            Pair("Blue", "#007AFF"),
            Pair("Indigo", "#5856D6"),
            Pair("Green", "#34C759"),
            Pair("Amber", "#FF9500"),
            Pair("Pink", "#FF2D55"),
            Pair("Purple", "#AF52DE"),
            Pair("Teal", "#0D9488")
        )
    }

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
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = "Appearance & Layout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Theme mode, brand accent, items view & navigation style",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("INTERFACE THEME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple("light", "Light", "Light"),
                        Triple("dark", "Dark", "Dark"),
                        Triple("system", "System", "Auto")
                    )
                    modes.forEach { (mode, label, desc) ->
                        val isSelected = themeMode == mode
                        Card(
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    desc,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("BRAND ACCENT COLOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { (name, hex) ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = customThemeColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setCustomThemeColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected $name",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("ITEMS SCREEN LAYOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isList = itemsViewLayout == "list"
                    Card(
                        onClick = { viewModel.setItemsViewLayout("list") },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isList) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isList) 1.5.dp else 1.dp,
                            if (isList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("List View", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    val isGrid = itemsViewLayout == "grid"
                    Card(
                        onClick = { viewModel.setItemsViewLayout("grid") },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGrid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isGrid) 1.5.dp else 1.dp,
                            if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("2-Col Grid", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("BOTTOM NAVIGATION STYLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isFloating = navBarStyle == "floating_pill"
                    Card(
                        onClick = { viewModel.setNavBarStyle("floating_pill") },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFloating) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isFloating) 1.5.dp else 1.dp,
                            if (isFloating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Floating Pill", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Curved modern", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val isClassic = navBarStyle == "classic"
                    Card(
                        onClick = { viewModel.setNavBarStyle("classic") },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClassic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isClassic) 1.5.dp else 1.dp,
                            if (isClassic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Classic Bar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Edge-to-edge", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsSyncAndUpdatesDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val ext = LocalExtendedColors.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val pendingCount by viewModel.pendingQueueCount.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

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
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cloud Sync & Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Supabase Realtime & GitHub Releases", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isHealthy = viewModel.isCloudConfigured && (isRealtimeLive || syncStatus.startsWith("Synced"))
                    val badgeLabel = when {
                        !viewModel.isCloudConfigured -> "No Secrets"
                        isRealtimeLive -> "Live"
                        syncStatus.startsWith("Synced") -> "Synced"
                        else -> "Offline"
                    }
                    BadgePill(
                        text = badgeLabel,
                        bgColor = if (isHealthy) ext.positive else ext.warning,
                        textColor = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Status: $syncStatus", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (!viewModel.isCloudConfigured) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "SUPABASE_URL and SUPABASE_KEY were not included in this build. Add them to GitHub Actions Secrets or AI Studio Secrets and rebuild.",
                                fontSize = 11.5.sp,
                                color = ext.danger,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "$pendingCount pending operation(s) in queue",
                                fontSize = 12.sp,
                                color = ext.warning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.reloadFromDatabase() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pull DB", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { viewModel.uploadAllToDatabase() },
                        colors = ButtonDefaults.buttonColors(containerColor = ext.positive, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Push All", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.reconnectRealtime() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Reconnect", fontSize = 11.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { viewModel.retrySync() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Retry", fontSize = 11.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    if (pendingCount > 0 || isAdmin) {
                        OutlinedButton(
                            onClick = {
                                haptics.reject()
                                viewModel.clearSyncQueue()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.danger),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Clear", fontSize = 11.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                // APK Release Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App APK Release", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.checkForUpdates() },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check", fontSize = 11.sp)
                    }
                }

                when (val s = updateStatus) {
                    is UpdateStatus.Available -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.startDownloadAndInstall(s.info.apkDownloadUrl) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install ${s.info.tagName} (1-Tap)", fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Downloading: ${(s.progress * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    is UpdateStatus.UpToDate -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ext.positive, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("App is up to date", color = ext.positive, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SettingsProfileEditDialog(
    initialStoreName: String,
    initialName: String,
    initialRole: String,
    initialDepot: String,
    onSave: (storeName: String, name: String, role: String, depot: String) -> Unit,
    onDismiss: () -> Unit
) {
    var storeName by remember { mutableStateOf(initialStoreName) }
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }
    var depot by remember { mutableStateOf(initialDepot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit Store & Terminal Profile", fontWeight = FontWeight.Bold)
                Text(
                    "Syncs across all connected devices",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store / Business Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_store_name_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Supervisor / Operator Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_operator_name_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_role_title_input")
                )
                OutlinedTextField(
                    value = depot,
                    onValueChange = { depot = it },
                    label = { Text("Location / Depot Address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_location_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        storeName.trim(),
                        name.trim().ifBlank { "Shahazim" },
                        role.trim().ifBlank { "Warehouse Supervisor" },
                        depot.trim().ifBlank { "HardwareStock Central Depot" }
                    )
                },
                modifier = Modifier.testTag("profile_save_button")
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsInvoiceSequenceDialog(
    initialPrefix: String,
    initialNextNum: Int,
    onSave: (prefix: String, nextNum: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var prefix by remember { mutableStateOf(initialPrefix) }
    var nextNumText by remember { mutableStateOf(initialNextNum.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invoice Sequence", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Customize default bill prefix and sequence counter.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.uppercase() },
                    label = { Text("Prefix") },
                    placeholder = { Text("BILL-") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextNumText,
                    onValueChange = { nextNumText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Next Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedNum = nextNumText.toIntOrNull()?.coerceAtLeast(1) ?: initialNextNum
                    onSave(prefix.trim().ifBlank { "BILL-" }, parsedNum)
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsDataBackupDialog(
    itemCount: Int,
    onOpenExportXls: () -> Unit,
    onOpenExportCsv: () -> Unit,
    onOpenImportFile: () -> Unit,
    onOpenColorTintImport: () -> Unit,
    onUploadAllToDb: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
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
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Inventory Data & Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$itemCount SKUs currently stored locally & synced to cloud.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        onDismiss()
                        onOpenExportXls()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_backup_export_xls_button")
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Excel Sheet (.XLS)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenExportCsv()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Inventory CSV", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenImportFile()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Excel (.XLSX) / CSV", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenColorTintImport()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Color Machine Tint Import (.CSC/.CSV)", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        onUploadAllToDb()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.positive),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Full Cloud Backup", fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SettingsSupportDiagnosticsDialog(
    viewModel: StockViewModel,
    terminalId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRealtimeLive by viewModel.isRealtimeLive.collectAsState()
    val pendingCount by viewModel.pendingQueueCount.collectAsState()
    val allItems by viewModel.allItems.collectAsState()

    val reportText = remember(syncStatus, isRealtimeLive, pendingCount, allItems.size, terminalId) {
        buildString {
            appendLine("HardwareStock Diagnostics Report")
            appendLine("Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("Terminal: $terminalId")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Realtime Sync: ${if (isRealtimeLive) "Live" else "Offline"}")
            appendLine("Sync Status: $syncStatus")
            appendLine("Pending Sync Ops: $pendingCount")
            appendLine("Total Local SKUs: ${allItems.size}")
        }
    }

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
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Diagnostics & Support", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reportText.trim(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "HardwareStock Diagnostics ($terminalId)")
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostics Report"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Diagnostics Report", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.reconnectRealtime()
                        viewModel.retrySync()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.positive),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reconnect & Flush Sync Queue", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SettingsExportCsvDialog(
    allItems: List<Item>,
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val csvText = remember(allItems) {
        val headers = listOf("SKU", "Barcode", "Item Name", "Cost", "Price", "Type", "Brand", "Size", "Unit", "Mrp", "Quantity", "Aliases")
        val rows = allItems.map { i ->
            listOf(
                i.code, i.barcode, i.name, i.cost.toString(), i.price.toString(),
                i.type, i.brand, i.size, i.unit, i.mrp?.toString() ?: "", i.qty.toString(), i.aliases
            ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
        }
        (listOf(headers.joinToString(",")) + rows).joinToString("\n")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Export Items (CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("${allItems.size} items ready for export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Stock CSV", csvText))
                            viewModel.showToast("Copied to clipboard")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("Copy CSV", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvText)
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share CSV"))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("Share CSV", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = csvText.take(1000) + if (csvText.length > 1000) "\n… (truncated preview)" else "",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun SettingsExportXlsDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by viewModel.allItems.collectAsState()
    val allCashflow by viewModel.allDailyCashflow.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val allGstBills by viewModel.allGstBills.collectAsState()
    val allLedgerAccounts by viewModel.allLedgerAccounts.collectAsState()
    val effectiveStoreTitle by viewModel.effectiveStoreTitle.collectAsState()
    val storeTitle = effectiveStoreTitle.ifBlank { "S.A. HARDWARE" }

    var exportFullWorkbook by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Excel Sheet (.XLS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val invSelected = !exportFullWorkbook
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (invSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (invSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { exportFullWorkbook = false }
                            .testTag("xls_scope_inventory")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                "Inventory Sheet",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = if (invSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val fullSelected = exportFullWorkbook
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (fullSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (fullSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { exportFullWorkbook = true }
                            .testTag("xls_scope_full")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                "Full Business (5 Tabs)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = if (fullSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!exportFullWorkbook) {
                            Text("1 Sheet: Inventory (${allItems.size} SKUs)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Includes SKU, Barcode, Name, Brand, Qty, Cost, Selling Price, MRP, Stock Value, Retail Value & Totals.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("5-Sheet Business Workbook (.XLS)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "• Inventory (${allItems.size} SKUs)\n• Cashflow & P&L (${allCashflow.size} entries)\n• Stock Movements (${allTransactions.size} logs)\n• GST Bills (${allGstBills.size} bills)\n• Khata Accounts (${allLedgerAccounts.size} parties)",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                fun runXlsAction(share: Boolean) {
                    if (isExporting) return
                    isExporting = true
                    scope.launch {
                        val (filePrefix, xmlContent) = withContext(Dispatchers.Default) {
                            if (!exportFullWorkbook) {
                                "Inventory_Stock" to XlsExporter.buildInventoryWorkbookXml(allItems)
                            } else {
                                "Business_Workbook" to XlsExporter.buildFullBusinessWorkbookXml(
                                    items = allItems,
                                    cashflow = allCashflow,
                                    stockMovements = allTransactions,
                                    gstBills = allGstBills,
                                    ledgerAccounts = allLedgerAccounts,
                                    storeName = storeTitle
                                )
                            }
                        }
                        if (share) {
                            val res = XlsExporter.shareXlsFile(
                                context = context,
                                filePrefix = filePrefix,
                                xmlContent = xmlContent,
                                shareTitle = if (exportFullWorkbook) "Share Business Workbook (.XLS)" else "Share Inventory Sheet (.XLS)"
                            )
                            res.onFailure { viewModel.showToast("Share failed: ${it.message}") }
                        } else {
                            val res = withContext(Dispatchers.IO) {
                                XlsExporter.saveXlsToDownloads(context, filePrefix, xmlContent)
                            }
                            res.onSuccess { path ->
                                viewModel.showToast("Saved XLS to $path")
                            }.onFailure { err ->
                                viewModel.showToast("Save failed: ${err.message}")
                            }
                        }
                        isExporting = false
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { runXlsAction(share = false) },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_save_xls_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save .XLS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { runXlsAction(share = true) },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_share_xls_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share .XLS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun SettingsImportItemsDialog(
    replaceExisting: Boolean,
    onReplaceExistingChange: (Boolean) -> Unit,
    onChooseFile: () -> Unit,
    onImportRawCsv: (List<Item>) -> Unit,
    onInvalidCsv: () -> Unit,
    onDismiss: () -> Unit
) {
    var rawCsv by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Import Items (XLSX / CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onChooseFile,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Excel (.xlsx) or CSV File")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Or paste raw CSV text below:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Columns: SKU, Barcode, Item Name, Cost, Price, Type, Brand, Size, Unit, MRP, Quantity, Aliases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawCsv,
                    onValueChange = { rawCsv = it },
                    placeholder = { Text("Paste CSV data here…") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onReplaceExistingChange(!replaceExisting) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = replaceExisting, onCheckedChange = onReplaceExistingChange)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace all existing items", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val parsed = parseCsvItems(rawCsv)
                        if (parsed.isEmpty()) onInvalidCsv() else onImportRawCsv(parsed)
                    }) { Text("Import") }
                }
            }
        }
    }
}

fun parseCsvItems(csv: String): List<Item> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.size <= 1) return emptyList()

    val result = mutableListOf<Item>()
    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())

    for (lineIdx in 1 until lines.size) {
        val parts = lines[lineIdx].split(",").map { it.trim().removeSurrounding("\"") }
        if (parts.size >= 3) {
            val name = parts.getOrNull(2) ?: ""
            if (name.isBlank()) continue

            val id = System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36) + lineIdx
            val item = Item(
                id = id,
                o = lineIdx,
                code = parts.getOrNull(0) ?: "",
                barcode = parts.getOrNull(1) ?: "",
                name = name,
                cost = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
                price = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                type = parts.getOrNull(5) ?: "",
                brand = parts.getOrNull(6) ?: "",
                size = parts.getOrNull(7) ?: "",
                unit = parts.getOrNull(8)?.ifBlank { "pcs" } ?: "pcs",
                mrp = parts.getOrNull(9)?.toDoubleOrNull(),
                qty = parts.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
                aliases = parts.getOrNull(11) ?: "",
                low = 0.0,
                updatedAt = now
            )
            result.add(item)
        }
    }
    return result
}

fun getFileNameFromUri(context: Context, uri: android.net.Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file.xlsx"
}
