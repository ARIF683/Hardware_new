package com.example.data.pref

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class BankingInfo(
    val bankName: String = "",
    val accountHolder: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val branchName: String = "",
    val upiId: String = "",
    val signaturePath: String? = null,
    val qrCodePath: String? = null,
    val signatureUrl: String? = null,
    val qrCodeUrl: String? = null
)

class BankingPreferenceManager(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("banking_signature_pref", Context.MODE_PRIVATE)

    companion object {
        private var isInitialized = false
        private val _bankingInfo = MutableStateFlow(BankingInfo())
        val bankingInfo: StateFlow<BankingInfo> = _bankingInfo.asStateFlow()
    }

    init {
        synchronized(BankingPreferenceManager::class.java) {
            if (!isInitialized) {
                _bankingInfo.value = loadBankingInfo()
                isInitialized = true
            }
        }
    }

    val currentBankingInfo: StateFlow<BankingInfo> = Companion.bankingInfo

    fun reloadFromPrefs(): BankingInfo {
        val loaded = loadBankingInfo()
        _bankingInfo.value = loaded
        return loaded
    }

    private fun loadBankingInfo(): BankingInfo {
        val sigPath = prefs.getString("signature_path", null)?.takeIf { File(it).exists() }
        val qrPath = prefs.getString("qr_code_path", null)?.takeIf { File(it).exists() }
        val sigUrl = prefs.getString("signature_url", null)
        val qrUrl = prefs.getString("qr_code_url", null)

        return BankingInfo(
            bankName = prefs.getString("bank_name", "") ?: "",
            accountHolder = prefs.getString("account_holder", "") ?: "",
            accountNumber = prefs.getString("account_number", "") ?: "",
            ifscCode = prefs.getString("ifsc_code", "") ?: "",
            branchName = prefs.getString("branch_name", "") ?: "",
            upiId = prefs.getString("upi_id", "") ?: "",
            signaturePath = sigPath,
            qrCodePath = qrPath,
            signatureUrl = sigUrl,
            qrCodeUrl = qrUrl
        )
    }

    fun saveBankDetails(
        bankName: String,
        accountHolder: String,
        accountNumber: String,
        ifscCode: String,
        branchName: String,
        upiId: String
    ) {
        prefs.edit()
            .putString("bank_name", bankName.trim())
            .putString("account_holder", accountHolder.trim())
            .putString("account_number", accountNumber.trim())
            .putString("ifsc_code", ifscCode.trim().uppercase())
            .putString("branch_name", branchName.trim())
            .putString("upi_id", upiId.trim())
            .apply()

        _bankingInfo.value = _bankingInfo.value.copy(
            bankName = bankName.trim(),
            accountHolder = accountHolder.trim(),
            accountNumber = accountNumber.trim(),
            ifscCode = ifscCode.trim().uppercase(),
            branchName = branchName.trim(),
            upiId = upiId.trim()
        )
    }

    fun applyRemoteBankDetails(
        bankName: String?,
        accountHolder: String?,
        accountNumber: String?,
        ifscCode: String?,
        branchName: String?,
        upiId: String?
    ) {
        val cur = _bankingInfo.value
        val nextBank = bankName?.trim() ?: cur.bankName
        val nextHolder = accountHolder?.trim() ?: cur.accountHolder
        val nextNumber = accountNumber?.trim() ?: cur.accountNumber
        val nextIfsc = ifscCode?.trim()?.uppercase() ?: cur.ifscCode
        val nextBranch = branchName?.trim() ?: cur.branchName
        val nextUpi = upiId?.trim() ?: cur.upiId

        if (nextBank == cur.bankName &&
            nextHolder == cur.accountHolder &&
            nextNumber == cur.accountNumber &&
            nextIfsc == cur.ifscCode &&
            nextBranch == cur.branchName &&
            nextUpi == cur.upiId
        ) {
            return
        }

        prefs.edit()
            .putString("bank_name", nextBank)
            .putString("account_holder", nextHolder)
            .putString("account_number", nextNumber)
            .putString("ifsc_code", nextIfsc)
            .putString("branch_name", nextBranch)
            .putString("upi_id", nextUpi)
            .apply()

        _bankingInfo.value = cur.copy(
            bankName = nextBank,
            accountHolder = nextHolder,
            accountNumber = nextNumber,
            ifscCode = nextIfsc,
            branchName = nextBranch,
            upiId = nextUpi
        )
    }

    fun setSignatureCloudUrl(url: String?) {
        if (url == null) {
            prefs.edit().remove("signature_url").apply()
        } else {
            prefs.edit().putString("signature_url", url).apply()
        }
        _bankingInfo.value = _bankingInfo.value.copy(signatureUrl = url)
    }

    fun setQrCodeCloudUrl(url: String?) {
        if (url == null) {
            prefs.edit().remove("qr_code_url").apply()
        } else {
            prefs.edit().putString("qr_code_url", url).apply()
        }
        _bankingInfo.value = _bankingInfo.value.copy(qrCodeUrl = url)
    }

    suspend fun saveSignatureFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.filesDir, "gst_signature.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input)
                    ?: return@withContext Result.failure(Exception("Could not decode signature image"))

                val maxDim = 600
                val scale = if (original.width > maxDim || original.height > maxDim) {
                    maxDim.toFloat() / maxOf(original.width, original.height)
                } else 1.0f

                val scaled = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                } else original

                FileOutputStream(destinationFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open image stream"))

            val path = destinationFile.absolutePath
            prefs.edit().putString("signature_path", path).apply()
            _bankingInfo.value = _bankingInfo.value.copy(signaturePath = path)
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSignatureFromBytes(bytes: ByteArray, remoteUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("Could not decode remote signature image"))
            val destinationFile = File(context.filesDir, "gst_signature.png")
            FileOutputStream(destinationFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val path = destinationFile.absolutePath
            prefs.edit()
                .putString("signature_path", path)
                .putString("signature_url", remoteUrl)
                .apply()
            _bankingInfo.value = _bankingInfo.value.copy(signaturePath = path, signatureUrl = remoteUrl)
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveQrCodeFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.filesDir, "gst_upi_qr.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input)
                    ?: return@withContext Result.failure(Exception("Could not decode QR code image"))

                val maxDim = 600
                val scale = if (original.width > maxDim || original.height > maxDim) {
                    maxDim.toFloat() / maxOf(original.width, original.height)
                } else 1.0f

                val scaled = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                } else original

                // QR codes must be crisp on white background
                val whiteBg = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(whiteBg)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(scaled, 0f, 0f, null)

                FileOutputStream(destinationFile).use { out ->
                    whiteBg.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open image stream"))

            val path = destinationFile.absolutePath
            prefs.edit().putString("qr_code_path", path).apply()
            _bankingInfo.value = _bankingInfo.value.copy(qrCodePath = path)
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveQrCodeFromBytes(bytes: ByteArray, remoteUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("Could not decode remote QR image"))
            val whiteBg = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(whiteBg)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(decoded, 0f, 0f, null)

            val destinationFile = File(context.filesDir, "gst_upi_qr.png")
            FileOutputStream(destinationFile).use { out ->
                whiteBg.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val path = destinationFile.absolutePath
            prefs.edit()
                .putString("qr_code_path", path)
                .putString("qr_code_url", remoteUrl)
                .apply()
            _bankingInfo.value = _bankingInfo.value.copy(qrCodePath = path, qrCodeUrl = remoteUrl)
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readSignatureBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val path = _bankingInfo.value.signaturePath ?: prefs.getString("signature_path", null)
        val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return@withContext null
        try { file.readBytes() } catch (_: Exception) { null }
    }

    suspend fun readQrCodeBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val path = _bankingInfo.value.qrCodePath ?: prefs.getString("qr_code_path", null)
        val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return@withContext null
        try { file.readBytes() } catch (_: Exception) { null }
    }

    fun clearSignature(markClearedForSync: Boolean = true) {
        val file = File(context.filesDir, "gst_signature.png")
        if (file.exists()) file.delete()
        val editor = prefs.edit().remove("signature_path")
        val nextUrl = if (markClearedForSync) {
            editor.putString("signature_url", "")
            ""
        } else {
            editor.remove("signature_url")
            null
        }
        editor.apply()
        _bankingInfo.value = _bankingInfo.value.copy(signaturePath = null, signatureUrl = nextUrl)
    }

    fun clearQrCode(markClearedForSync: Boolean = true) {
        val file = File(context.filesDir, "gst_upi_qr.png")
        if (file.exists()) file.delete()
        val editor = prefs.edit().remove("qr_code_path")
        val nextUrl = if (markClearedForSync) {
            editor.putString("qr_code_url", "")
            ""
        } else {
            editor.remove("qr_code_url")
            null
        }
        editor.apply()
        _bankingInfo.value = _bankingInfo.value.copy(qrCodePath = null, qrCodeUrl = nextUrl)
    }

    fun getSignatureBitmap(): Bitmap? {
        val path = _bankingInfo.value.signaturePath ?: prefs.getString("signature_path", null)
        if (path != null && File(path).exists()) {
            return try {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun getQrCodeBitmap(): Bitmap? {
        val path = _bankingInfo.value.qrCodePath ?: prefs.getString("qr_code_path", null)
        if (path != null && File(path).exists()) {
            return try {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
