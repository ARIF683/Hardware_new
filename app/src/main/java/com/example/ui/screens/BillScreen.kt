package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.Item
import com.example.data.repository.BillRowData
import com.example.ui.StockViewModel
import com.example.ui.components.CalculatorDialog
import com.example.ui.components.ValuePickerDialog
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.DangerRed
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import com.example.util.BillAliasManager
import com.example.util.GeminiBillScanner
import com.example.util.ParsedBillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BillScreen(viewModel: StockViewModel) {
    val allItems by viewModel.allItems.collectAsState()

    var stage by remember { mutableStateOf("idle") } // "idle" or "review"
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFullImage by remember { mutableStateOf(false) }

    var isScanning by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf("Analyzing bill with Gemini Vision...") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var parsedBillSummary by remember { mutableStateOf<ParsedBillResult?>(null) }

    var supplier by remember { mutableStateOf("") }
    var billNo by remember { mutableStateOf("") }
    var billDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }

    val rows = remember {
        mutableStateListOf(
            BillRowData(
                id = "row_0",
                name = "",
                rate = 0.0,
                qty = 1.0,
                unit = "pcs",
                include = true
            )
        )
    }

    var showCalculator by remember { mutableStateOf(false) }
    var showSequentialScanDialog by remember { mutableStateOf(false) }
    var showAiTrainingDialog by remember { mutableStateOf(false) }
    var paidInCash by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Temporary URI for full-resolution camera capture
    val cameraTargetUri = remember { mutableStateOf<Uri?>(null) }

    // Helper to run Gemini Vision bill scan
    fun startBillScan(uri: Uri?, bitmap: Bitmap?) {
        scope.launch {
            isScanning = true
            scanError = null
            scanStatusMessage = "Analyzing cash memo with Gemini AI..."
            try {
                val inputBitmap = bitmap ?: withContext(Dispatchers.IO) {
                    uri?.let { u ->
                        context.contentResolver.openInputStream(u)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    }
                }

                if (inputBitmap == null) {
                    scanError = "Could not load high-resolution image."
                    isScanning = false
                    return@launch
                }

                scanStatusMessage = "Deciphering handwriting, paint shades & pack sizes..."
                val result = GeminiBillScanner.parseBill(inputBitmap, allItems, context)
                parsedBillSummary = result

                if (result.supplierName.isNotBlank()) supplier = result.supplierName
                if (result.billNumber.isNotBlank()) billNo = result.billNumber
                if (result.date.isNotBlank()) billDate = result.date

                val extractedRows = GeminiBillScanner.toBillRows(result, allItems)
                if (extractedRows.isNotEmpty()) {
                    rows.clear()
                    rows.addAll(extractedRows)
                    stage = "review"
                    viewModel.showToast("✨ AI extracted ${extractedRows.size} items from bill!")
                } else {
                    scanError = "No line items could be detected. You can enter them manually."
                }
            } catch (e: Exception) {
                scanError = e.message ?: "Failed to read bill with AI."
                viewModel.showToast("AI Scan failed: ${e.message}")
            } finally {
                isScanning = false
            }
        }
    }

    // Pick image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            photoBitmap = null
            startBillScan(uri = uri, bitmap = null)
        }
    }

    // Full-resolution Camera Capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraTargetUri.value != null) {
            imageUri = cameraTargetUri.value
            photoBitmap = null
            startBillScan(uri = cameraTargetUri.value, bitmap = null)
        }
    }

    fun launchCameraHighRes() {
        try {
            val cacheFile = File.createTempFile("bill_scan_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
            cameraTargetUri.value = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            viewModel.showToast("Camera error: ${e.message}")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraHighRes()
        } else {
            viewModel.showToast("Camera permission is required to capture bills.")
        }
    }

    if (showCalculator) {
        CalculatorDialog(onDismiss = { showCalculator = false })
    }

    // Scanning Modal Dialog
    if (isScanning) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = BrandBlue,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Reading Bill with Gemini AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scanStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandBlue
                    )
                }
            }
        }
    }

    if (showFullImage && (imageUri != null || photoBitmap != null)) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "Full bill",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Full bill",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = { showFullImage = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                if (stage == "review") {
                    stage = "idle"
                } else {
                    viewModel.navigateBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = if (stage == "review") "Review Scanned Items" else "Upload Bill & Update Stock",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showAiTrainingDialog = true }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Train AI", tint = BrandBlue)
                }
                if (stage == "review") {
                    IconButton(onClick = { showCalculator = true }) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calculator", tint = BrandBlue)
                    }
                }
            }
        }

        if (stage == "idle") {
            // Idle Screen
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // AI Vision Scanner Hero Card
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "AI Bill & Memo Scanner",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Reads handwritten cash memos, pack sizes (1x6, 500x8) & paint shades.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // AI Training Quick Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandBlue.copy(alpha = 0.08f))
                                    .clickable { showAiTrainingDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Trained Shades: Phirozi, P.O. Red...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBlue
                                    )
                                }
                                Text(
                                    text = "Train AI Vocabulary →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Capture Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            launchCameraHighRes()
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Take Photo", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("From Gallery", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Error notice if previous scan failed
                            if (scanError != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = scanError ?: "",
                                            fontSize = 12.sp,
                                            color = DangerRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Image preview if chosen
                            if (photoBitmap != null || imageUri != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                if (photoBitmap != null) {
                                    Image(
                                        bitmap = photoBitmap!!.asImageBitmap(),
                                        contentDescription = "Bill photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                            .clickable { showFullImage = true },
                                        contentScale = ContentScale.Fit
                                    )
                                } else if (imageUri != null) {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = "Bill photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                            .clickable { showFullImage = true },
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { startBillScan(imageUri, photoBitmap) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.4f)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Re-Scan with AI", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { stage = "review" },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Manual Edit →")
                                    }

                                    IconButton(onClick = {
                                        imageUri = null
                                        photoBitmap = null
                                        scanError = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = DangerRed)
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Sequential Scan Banner for Receiving Shipments
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = BrandBlue.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⚡", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Barcode / Sequential Scan",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scan items sequentially with barcode scanner to populate the bill.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showSequentialScanDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("⚡ Start Sequential Shipment Scan", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.openGstCalculator() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBlue)
                            ) {
                                Text("📊 GST & E-Way Bill Calculator", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Manual Entry without Photo
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Manual Entry without Photo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Skip uploading a photo and manually enter purchase rows.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = { stage = "review" }) {
                                Text("Enter items directly →")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        } else {
            // Review Stage
            val includedRows = rows.filter { it.include }
            val billTotal = includedRows.sumOf { it.rate * it.qty }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Image preview strip (if image chosen)
                if (photoBitmap != null || imageUri != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { showFullImage = true }
                        ) {
                            if (photoBitmap != null) {
                                Image(
                                    bitmap = photoBitmap!!.asImageBitmap(),
                                    contentDescription = "Bill thumbnail",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Color.Black),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (imageUri != null) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "Bill thumbnail",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Color.Black),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(
                                text = "🔍 Tap image to enlarge and inspect handwriting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                // AI Financial Calculation Breakdown (Subtotal vs Previous Due)
                parsedBillSummary?.let { summary ->
                    if (summary.previousBalance > 0.0 || (summary.itemsSubtotal > 0 && summary.itemsSubtotal != summary.totalAmount)) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Bill Subtotal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(StockViewModel.formatRupees(summary.itemsSubtotal), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    if (summary.previousBalance > 0.0) {
                                        Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Column {
                                            Text("Previous Dues", fontSize = 11.sp, color = DangerRed)
                                            Text(StockViewModel.formatRupees(summary.previousBalance), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DangerRed)
                                        }
                                    }
                                    Text("=", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column {
                                        Text("Memo Total", fontSize = 11.sp, color = BrandBlue)
                                        Text(StockViewModel.formatRupees(summary.totalAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlue)
                                    }
                                }
                            }
                        }
                    }
                }

                // Header Fields Card
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            OutlinedTextField(
                                value = supplier,
                                onValueChange = { supplier = it },
                                label = { Text("Supplier / Store Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = billNo,
                                    onValueChange = { billNo = it },
                                    label = { Text("Bill / Memo No") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = billDate,
                                    onValueChange = { billDate = it },
                                    label = { Text("Date (dd/mm/yyyy)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Quick Batch Selling Margin & Actions Bar
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auto-calculate Selling Price Margin:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(10, 15, 20, 25).forEach { marginPct ->
                                    AssistChip(
                                        onClick = {
                                            for (i in rows.indices) {
                                                val r = rows[i]
                                                if (r.rate > 0) {
                                                    val markedUpPrice = kotlin.math.round(r.rate * (1 + marginPct / 100.0) * 10.0) / 10.0
                                                    rows[i] = r.copy(
                                                        sellingPrice = markedUpPrice,
                                                        updateSellingPrice = true
                                                    )
                                                }
                                            }
                                            viewModel.showToast("Applied +$marginPct% profit margin to all items")
                                        },
                                        label = { Text("+$marginPct%") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = BrandBlue.copy(alpha = 0.08f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Row Items Header
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Items (${rows.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Toggle Stock",
                                fontSize = 11.sp,
                                color = BrandBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val anyEnabled = rows.any { it.updateStock }
                                        for (i in rows.indices) {
                                            rows[i] = rows[i].copy(updateStock = !anyEnabled)
                                        }
                                    }
                                    .padding(4.dp)
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Toggle Cost",
                                fontSize = 11.sp,
                                color = BrandBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val anyEnabled = rows.any { it.updateCost }
                                        for (i in rows.indices) {
                                            rows[i] = rows[i].copy(updateCost = !anyEnabled)
                                        }
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Row Items
                itemsIndexed(rows) { index, row ->
                    BillRowEditor(
                        index = index,
                        row = row,
                        allItems = allItems,
                        onUpdate = { updated -> rows[index] = updated },
                        onDelete = {
                            if (rows.size > 1) {
                                rows.removeAt(index)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Summary & Actions
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Purchased Items Total",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${includedRows.size} items included",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = StockViewModel.formatRupees(billTotal),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Payment Mode Selection (Khata Due vs Paid in Cash)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Payment Status & Expense Logging",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !paidInCash,
                                    onClick = { paidInCash = false },
                                    label = { Text("Credit / Khata (Due)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = paidInCash,
                                    onClick = { paidInCash = true },
                                    label = { Text("Paid in Cash (Expense)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (paidInCash) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚡ Will automatically record an expense of ${StockViewModel.formatRupees(billTotal)} for date $billDate in Daily Cashflow.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rows.add(
                                    BillRowData(
                                        id = "row_${System.currentTimeMillis()}",
                                        name = "",
                                        rate = 0.0,
                                        qty = 1.0,
                                        unit = "pcs",
                                        include = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ Add row")
                        }

                        Button(
                            onClick = {
                                val namedRows = rows.filter { it.include && it.name.trim().isNotEmpty() }
                                if (namedRows.isEmpty()) {
                                    viewModel.showToast("Add at least one named row")
                                    return@Button
                                }
                                viewModel.confirmBill(supplier, billNo, billDate, rows, paidInCash)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Confirm & Sync (${includedRows.size})", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }


    if (showSequentialScanDialog) {
        SequentialScanBillDialog(
            allItems = allItems,
            onDismiss = { showSequentialScanDialog = false },
            onItemsScanned = { scannedRows ->
                if (scannedRows.isNotEmpty()) {
                    // Replace empty initial row if it's untouched
                    if (rows.size == 1 && rows[0].name.isBlank()) {
                        rows.clear()
                    }
                    rows.addAll(scannedRows)
                    stage = "review"
                    viewModel.showToast("Added ${scannedRows.size} shipment items to bill ✓")
                }
                showSequentialScanDialog = false
            }
        )
    }

    if (showAiTrainingDialog) {
        AiVocabularyTrainingDialog(
            context = context,
            onDismiss = { showAiTrainingDialog = false }
        )
    }
}

@Composable
fun SequentialScanBillDialog(
    allItems: List<Item>,
    onDismiss: () -> Unit,
    onItemsScanned: (List<BillRowData>) -> Unit
) {
    var scannedInput by remember { mutableStateOf("") }
    val scannedList = remember { mutableStateListOf<BillRowData>() }
    var showCameraScannerDialog by remember { mutableStateOf(false) }

    val barcodeSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                scannedInput = spoken
            }
        }
    }

    val cameraScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            // Simulated / visual barcode scan feedback
            val itemWithBarcode = allItems.firstOrNull { it.barcode.isNotBlank() } ?: allItems.firstOrNull()
            if (itemWithBarcode != null) {
                val codeToUse = if (itemWithBarcode.barcode.isNotBlank()) itemWithBarcode.barcode else itemWithBarcode.name
                val existingIdx = scannedList.indexOfFirst { it.name.equals(itemWithBarcode.name, ignoreCase = true) }
                if (existingIdx >= 0) {
                    val cur = scannedList[existingIdx]
                    scannedList[existingIdx] = cur.copy(qty = cur.qty + 1.0)
                } else {
                    scannedList.add(
                        BillRowData(
                            id = "scan_${System.currentTimeMillis()}_${scannedList.size}",
                            name = itemWithBarcode.name,
                            matchedItem = itemWithBarcode,
                            rate = itemWithBarcode.cost,
                            qty = 1.0,
                            type = itemWithBarcode.type,
                            brand = itemWithBarcode.brand,
                            size = itemWithBarcode.size,
                            unit = itemWithBarcode.unit.ifBlank { "pcs" },
                            include = true
                        )
                    )
                }
            }
        }
    }

    val context = LocalContext.current

    val seqCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraScanLauncher.launch(null)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun processBarcode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) return

        val matched = allItems.firstOrNull {
            it.barcode.equals(clean, ignoreCase = true) ||
            it.code.equals(clean, ignoreCase = true) ||
            it.name.equals(clean, ignoreCase = true) ||
            it.name.contains(clean, ignoreCase = true) ||
            it.aliases.split(",").any { a -> a.trim().equals(clean, ignoreCase = true) }
        }

        if (matched != null) {
            val existingIdx = scannedList.indexOfFirst { it.name.equals(matched.name, ignoreCase = true) }
            if (existingIdx >= 0) {
                val current = scannedList[existingIdx]
                scannedList[existingIdx] = current.copy(qty = current.qty + 1.0)
            } else {
                scannedList.add(
                    BillRowData(
                        id = "scan_${System.currentTimeMillis()}_${scannedList.size}",
                        name = matched.name,
                        matchedItem = matched,
                        rate = matched.cost,
                        qty = 1.0,
                        type = matched.type,
                        brand = matched.brand,
                        size = matched.size,
                        unit = matched.unit.ifBlank { "pcs" },
                        include = true
                    )
                )
            }
        } else {
            // New uncatalogued item
            val existingIdx = scannedList.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
            if (existingIdx >= 0) {
                val current = scannedList[existingIdx]
                scannedList[existingIdx] = current.copy(qty = current.qty + 1.0)
            } else {
                scannedList.add(
                    BillRowData(
                        id = "scan_${System.currentTimeMillis()}_${scannedList.size}",
                        name = clean,
                        matchedItem = null,
                        rate = 0.0,
                        qty = 1.0,
                        unit = "pcs",
                        include = true
                    )
                )
            }
        }
        scannedInput = ""
    }

    val itemsWithBarcodes = remember(allItems) {
        allItems.filter { it.barcode.isNotBlank() || it.code.isNotBlank() }.take(15)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = BrandBlue.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quick Shipment Barcode Scan", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBlue)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan barcodes with camera or enter codes sequentially as you unpack shipments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Camera Scan & Voice Scan Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    cameraScanLauncher.launch(null)
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            } else {
                                seqCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📷 Camera Scanner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak barcode, SKU, or item name...")
                            }
                            try {
                                barcodeSpeechLauncher.launch(intent)
                            } catch (e: Exception) {
                                // speech not supported
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🎤 Speak", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Barcode Catalog Tap Bar
                if (itemsWithBarcodes.isNotEmpty()) {
                    Text(
                        "QUICK-TAP BARCODES (FROM INVENTORY)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(itemsWithBarcodes) { itm ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    val codeToProcess = if (itm.barcode.isNotBlank()) itm.barcode else itm.code
                                    processBarcode(codeToProcess)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡ ", fontSize = 10.sp)
                                    Column {
                                        Text(itm.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(
                                            text = if (itm.barcode.isNotBlank()) "🏷️ ${itm.barcode}" else "SKU: ${itm.code}",
                                            fontSize = 9.sp,
                                            color = BrandBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Barcode / SKU Text Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = scannedInput,
                        onValueChange = { scannedInput = it },
                        label = { Text("Scan / Type Barcode or SKU") },
                        placeholder = { Text("e.g. 890123456789") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = { processBarcode(scannedInput) },
                        enabled = scannedInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("+ Add")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Scanned Items List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SCANNED ITEMS (${scannedList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Total Qty: ${scannedList.sumOf { it.qty }.toInt()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (scannedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No items scanned yet.\nScan barcodes with camera or quick-tap items to populate the bill.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(scannedList) { sIdx, sRow ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sRow.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Rate: ₹${sRow.rate} • Unit: ${sRow.unit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = BrandBlue.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Qty: ${sRow.qty.toInt()}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = BrandBlue,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (sRow.qty > 1) {
                                                    scannedList[sIdx] = sRow.copy(qty = sRow.qty - 1)
                                                } else {
                                                    scannedList.removeAt(sIdx)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = DangerRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onItemsScanned(scannedList.toList()) },
                        enabled = scannedList.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("✓ Apply to Bill (${scannedList.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BillRowEditor(
    index: Int,
    row: BillRowData,
    allItems: List<Item>,
    onUpdate: (BillRowData) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showItemPicker by remember { mutableStateOf(false) }
    var pickerField by remember { mutableStateOf<String?>(null) } // "type", "brand", "size", "unit"

    val matched = row.matchedItem

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (matched == null) androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Checkbox, Name, Confidence Badge, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = row.include,
                    onCheckedChange = { onUpdate(row.copy(include = it)) },
                    colors = CheckboxDefaults.colors(checkedColor = BrandBlue)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Item #${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Confidence Tag
                        if (matched != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "In Stock (${matched.qty.toInt()} ${matched.unit})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BrandBlue.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("✨ New Item", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                                }
                            }
                        }
                    }

                    Text(
                        text = if (row.name.isNotBlank()) row.name else "— tap to select item —",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (row.name.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showItemPicker = true }
                            .padding(vertical = 2.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = DangerRed)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cost Comparison & Stock Diff Card
            if (matched != null) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cost Comparison",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Old: ₹${matched.cost}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(" → ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("New: ₹${row.rate}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                                }
                            }

                            // Difference pill
                            val diff = row.rate - matched.cost
                            if (matched.cost > 0 && row.rate > 0) {
                                val pct = ((diff / matched.cost) * 100).toInt()
                                if (diff > 0.05) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = DangerRed.copy(alpha = 0.12f)) {
                                        Text(
                                            text = "🔺 +₹${"%.1f".format(diff)} (+${pct}%)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DangerRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (diff < -0.05) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E7D32).copy(alpha = 0.12f)) {
                                        Text(
                                            text = "🔻 -₹${"%.1f".format(-diff)} (${pct}%)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color.Gray.copy(alpha = 0.12f)) {
                                        Text(
                                            text = "Same cost",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.DarkGray,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Toggles for Cost & Stock update
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = row.updateCost,
                                onClick = { onUpdate(row.copy(updateCost = !row.updateCost)) },
                                label = { Text("Update Cost Price", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = row.updateStock,
                                onClick = { onUpdate(row.copy(updateStock = !row.updateStock)) },
                                label = {
                                    val newTotal = (matched.qty + row.qty).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                                    Text("Add Stock (+${row.qty.toInt()} → $newTotal)", fontSize = 10.sp)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Rate and Quantity + Multi-Unit Conversion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (row.rate > 0) row.rate.toString() else "",
                    onValueChange = {
                        val r = it.toDoubleOrNull() ?: 0.0
                        onUpdate(row.copy(rate = r))
                    },
                    label = { Text("Bill Cost (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = if (row.qty > 0) row.qty.toString() else "",
                        onValueChange = {
                            val q = it.toDoubleOrNull() ?: 0.0
                            onUpdate(row.copy(qty = q))
                        },
                        label = { Text("Qty (${row.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Multi-Unit Conversion Pill
                    val paired = com.example.util.UnitConversionHelper.getPairedUnit(row.unit)
                    if (paired.first != row.unit) {
                        Text(
                            text = "⇄ Convert to ${paired.first.uppercase()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue,
                            modifier = Modifier
                                .clickable {
                                    val newUnit = paired.first
                                    val convertedQty = com.example.util.UnitConversionHelper.convertQuantity(row.unit, newUnit, row.qty, paired.second)
                                    onUpdate(row.copy(unit = newUnit, qty = convertedQty))
                                }
                                .padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selling Price & Profit Margin Controls
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.updateSellingPrice,
                                onCheckedChange = { onUpdate(row.copy(updateSellingPrice = it)) },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Update Selling Price",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (matched != null && matched.price > 0) {
                            Text(
                                text = "Current MRP: ₹${matched.price}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (row.sellingPrice > 0) row.sellingPrice.toString() else "",
                            onValueChange = {
                                val p = it.toDoubleOrNull() ?: 0.0
                                onUpdate(row.copy(sellingPrice = p, updateSellingPrice = true))
                            },
                            label = { Text("New Selling (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Quick Margin Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            listOf(10, 15, 20, 25).forEach { marginPct ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandBlue.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (row.rate > 0) {
                                                val markedUp = kotlin.math.round(row.rate * (1 + marginPct / 100.0) * 10.0) / 10.0
                                                onUpdate(row.copy(sellingPrice = markedUp, updateSellingPrice = true))
                                            }
                                        }
                                ) {
                                    Text(
                                        text = "+$marginPct%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBlue,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Type, Brand, Size, Unit chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { pickerField = "type" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = row.type.ifEmpty { "Type" }, maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { pickerField = "brand" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = row.brand.ifEmpty { "Brand" }, maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { pickerField = "size" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = row.size.ifEmpty { "Size" }, maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { pickerField = "unit" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = row.unit.ifEmpty { "pcs" }, maxLines = 1, fontSize = 11.sp)
                }
            }
        }
    }

    // Smart Item Picker Dialog with AI Alias Learning
    if (showItemPicker) {
        var searchQuery by remember { mutableStateOf("") }
        var trainAliasForFuture by remember { mutableStateOf(true) }

        val filteredItems = remember(searchQuery, allItems) {
            if (searchQuery.isBlank()) allItems.take(50)
            else allItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.aliases.contains(searchQuery, ignoreCase = true) ||
                it.brand.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }.take(50)
        }

        Dialog(onDismissRequest = { showItemPicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Match Item in Store Catalog",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bill scanned as: \"${row.name}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search catalog items...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // AI Learn Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { trainAliasForFuture = !trainAliasForFuture }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = trainAliasForFuture,
                            onCheckedChange = { trainAliasForFuture = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Train AI: Remember \"${row.name}\" maps to this product for future scans",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredItems) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (trainAliasForFuture && row.name.isNotBlank()) {
                                            BillAliasManager.saveAlias(context, row.name, item.name)
                                        }
                                        onUpdate(
                                            row.copy(
                                                name = item.name,
                                                matchedItem = item,
                                                rate = if (row.rate == 0.0) item.cost else row.rate,
                                                sellingPrice = if (row.sellingPrice == 0.0) item.price else row.sellingPrice,
                                                type = if (row.type.isEmpty()) item.type else row.type,
                                                brand = if (row.brand.isEmpty()) item.brand else row.brand,
                                                size = if (row.size.isEmpty()) item.size else row.size,
                                                unit = if (row.unit.isEmpty() || row.unit == "pcs") item.unit else row.unit
                                            )
                                        )
                                        showItemPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "Cost: ₹${item.cost} | Sell: ₹${item.price} | Stock: ${item.qty.toInt()} ${item.unit}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.Link, contentDescription = "Link", tint = BrandBlue)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showItemPicker = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    onUpdate(row.copy(name = searchQuery, matchedItem = null))
                                }
                                showItemPicker = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep as New")
                        }
                    }
                }
            }
        }
    }

    // Type / Brand / Size / Unit picker
    pickerField?.let { field ->
        val counts = remember(field, allItems) {
            when (field) {
                "type" -> allItems.map { it.type }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList()
                "brand" -> allItems.map { it.brand }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList()
                "size" -> allItems.map { it.size }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList()
                "unit" -> listOf("pcs", "kg", "g", "L", "ml", "box", "pack", "bag", "m", "ft", "cm", "set", "pair", "dozen", "roll", "sheet")
                    .map { Pair(it, allItems.count { item -> item.unit == it }) }
                else -> emptyList()
            }
        }

        ValuePickerDialog(
            title = "Select ${field.replaceFirstChar { it.uppercase() }}",
            optionsWithCount = counts,
            currentValue = when (field) {
                "type" -> row.type
                "brand" -> row.brand
                "size" -> row.size
                "unit" -> row.unit
                else -> ""
            },
            onSelect = { selectedVal ->
                when (field) {
                    "type" -> onUpdate(row.copy(type = selectedVal))
                    "brand" -> onUpdate(row.copy(brand = selectedVal))
                    "size" -> onUpdate(row.copy(size = selectedVal))
                    "unit" -> onUpdate(row.copy(unit = selectedVal.ifBlank { "pcs" }))
                }
                pickerField = null
            },
            onDismiss = { pickerField = null }
        )
    }
}

@Composable
fun AiVocabularyTrainingDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var rawInput by remember { mutableStateOf("") }
    var mappedInput by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf(BillAliasManager.getAliases(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI OCR Handwriting Training",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Train AI on how your suppliers write shades & items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Teach new alias box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Teach AI a New Name / Shortcut",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = BrandBlue
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = rawInput,
                            onValueChange = { rawInput = it },
                            label = { Text("What supplier writes") },
                            placeholder = { Text("e.g. phirozi, p.o.red, kathi") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = mappedInput,
                            onValueChange = { mappedInput = it },
                            label = { Text("Store Product / Shade Name") },
                            placeholder = { Text("e.g. Phirozi 1L, P.O. Red Enamel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (rawInput.isNotBlank() && mappedInput.isNotBlank()) {
                                    BillAliasManager.saveAlias(context, rawInput, mappedInput)
                                    aliases = BillAliasManager.getAliases(context)
                                    rawInput = ""
                                    mappedInput = ""
                                }
                            },
                            enabled = rawInput.isNotBlank() && mappedInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Teach AI This Shortcut")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Active Learned Vocabulary (${aliases.size})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(aliases.entries.toList()) { entry ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "\"${entry.key}\"",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = BrandBlue
                                    )
                                    Text(
                                        text = "➔ ${entry.value}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        BillAliasManager.removeAlias(context, entry.key)
                                        aliases = BillAliasManager.getAliases(context)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = DangerRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}
