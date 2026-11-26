package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val isNewerThanCurrent: Boolean,
    val expectedSha256: String? = null
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val info: ReleaseInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = defaultHttpClient()
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_RELEASE_API = "https://api.github.com/repos/ARIF683/Hardware_new/releases/latest"
        val CURRENT_VERSION_TAG = "v${BuildConfig.VERSION_NAME}"
        val CURRENT_VERSION_DISPLAY = "v${BuildConfig.VERSION_NAME} (b${BuildConfig.VERSION_CODE})"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private val SHA256_HEX_REGEX = Regex("^[a-fA-F0-9]{64}$")

        internal fun isAllowedDownloadUrl(url: String, allowRedirectHost: Boolean = false): Boolean {
            return try {
                val trimmed = url.trim()
                if (trimmed.contains("..") || trimmed.contains("\\")) return false
                val uri = URI(trimmed)
                if (!uri.scheme.equals("https", ignoreCase = true)) return false
                val host = uri.host?.lowercase() ?: return false
                val normalizedPath = uri.normalize().path.orEmpty()
                if (normalizedPath.contains("..")) return false

                val isExactGitHubRelease = (host == "github.com") &&
                    normalizedPath.startsWith("/ARIF683/Hardware_new/releases/download/")

                if (allowRedirectHost) {
                    isExactGitHubRelease ||
                        host == "objects.githubusercontent.com" ||
                        host.endsWith(".objects.githubusercontent.com") ||
                        host == "release-assets.githubusercontent.com" ||
                        host.endsWith(".release-assets.githubusercontent.com")
                } else {
                    isExactGitHubRelease
                }
            } catch (_: Exception) {
                false
            }
        }

        internal fun extractExpectedSha256(assetDigest: String, releaseNotes: String): String? {
            val digestMatch = Regex("""(?:sha256:)?([a-fA-F0-9]{64})""").find(assetDigest.trim())
            if (digestMatch != null) return digestMatch.groupValues[1].lowercase()
            val notesMatch = Regex("""(?:apk_sha256|sha256)[:\s=]+([a-fA-F0-9]{64})""", RegexOption.IGNORE_CASE).find(releaseNotes)
            return notesMatch?.groupValues?.getOrNull(1)?.lowercase()
        }

        internal fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun parseRemoteVersionName(tag: String, name: String, notes: String): String {
        val nameRegex = Regex("""(?:version_name|versionName)[:\s=]+([0-9]+\.[0-9]+\.[0-9]+)""", RegexOption.IGNORE_CASE)
        nameRegex.find(notes)?.groupValues?.get(1)?.let { return it }
        nameRegex.find(name)?.groupValues?.get(1)?.let { return it }

        val tagClean = tag.trim().removePrefix("v").removePrefix("V")
        return tagClean.ifBlank { "0.0.0" }
    }

    internal fun compareVersions(v1: String, v2: String): Int {
        val digitPrefix = Regex("""^\d+""")
        val parts1 = v1.split(".").map { digitPrefix.find(it.trim())?.value?.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { digitPrefix.find(it.trim())?.value?.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun parseRemoteVersionCode(tag: String, name: String, notes: String): Int? {
        val codeRegex = Regex("""(?:version_code|versionCode)[:\s=]+(\d+)""", RegexOption.IGNORE_CASE)
        codeRegex.find(notes)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        codeRegex.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        val tagRegex = Regex("""v?(?:[0-9]+\.[0-9]+\.)?(\d+)$""", RegexOption.IGNORE_CASE)
        tagRegex.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        return null
    }

    suspend fun checkForUpdates(): ReleaseInfo? = withContext(Dispatchers.IO) {
        _status.value = UpdateStatus.Checking
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASE_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hardware-StockManager-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val msg = if (code == 404) {
                        "GitHub repository is private or no release is published (HTTP 404). Make releases public to receive OTA updates."
                    } else {
                        "GitHub API returned code $code"
                    }
                    _status.value = UpdateStatus.Error(msg)
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val tagName = json.optString("tag_name", "")
                val name = json.optString("name", "New Update")
                val notes = json.optString("body", "Bug fixes and performance improvements.")
                val publishedAt = json.optString("published_at", "")

                // Find APK asset
                var downloadUrl = ""
                var apkSize = 0L
                var assetDigest = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            assetDigest = asset.optString("digest", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isNotEmpty()) {
                    if (!isAllowedDownloadUrl(downloadUrl)) {
                        _status.value = UpdateStatus.Error("Blocked untrusted update download host")
                        return@withContext null
                    }
                    val currentCode = BuildConfig.VERSION_CODE
                    val remoteCode = parseRemoteVersionCode(tagName, name, notes)
                    val currentVersionName = BuildConfig.VERSION_NAME
                    val remoteVersionName = parseRemoteVersionName(tagName, name, notes)
                    val expectedSha256 = extractExpectedSha256(assetDigest, notes)

                    val versionComparison = compareVersions(remoteVersionName, currentVersionName)
                    val isNewer = if (versionComparison != 0) {
                        versionComparison > 0
                    } else if (remoteCode != null) {
                        remoteCode > currentCode
                    } else {
                        val installedTag = "v${BuildConfig.VERSION_NAME}"
                        tagName.isNotBlank() && tagName != installedTag && !tagName.equals("latest", ignoreCase = true)
                    }

                    val info = ReleaseInfo(
                        tagName = tagName,
                        title = name,
                        releaseNotes = notes,
                        apkDownloadUrl = downloadUrl,
                        apkSizeBytes = apkSize,
                        publishedAt = publishedAt,
                        isNewerThanCurrent = isNewer,
                        expectedSha256 = expectedSha256
                    )

                    if (isNewer) {
                        _status.value = UpdateStatus.Available(info)
                    } else {
                        _status.value = UpdateStatus.UpToDate
                    }
                    return@withContext info
                } else {
                    _status.value = UpdateStatus.UpToDate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            _status.value = UpdateStatus.Error(e.message ?: "Failed to check for updates")
        }
        null
    }

    suspend fun downloadAndInstall(downloadUrl: String, expectedSha256: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (!isAllowedDownloadUrl(downloadUrl)) {
                _status.value = UpdateStatus.Error("Untrusted update download URL blocked")
                return@withContext
            }

            val resolvedExpectedHash = (expectedSha256?.takeIf { it.isNotBlank() }
                ?: (status.value as? UpdateStatus.Available)?.info?.expectedSha256)
                ?.trim()
                ?.lowercase()

            if (resolvedExpectedHash.isNullOrBlank() || !SHA256_HEX_REGEX.matches(resolvedExpectedHash)) {
                _status.value = UpdateStatus.Error("Update rejected: release is missing a valid SHA-256 checksum.")
                return@withContext
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "StockManager_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Hardware-StockManager-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _status.value = UpdateStatus.Error("Failed to download APK: HTTP ${response.code}")
                    return@withContext
                }
                val finalUrl = response.request.url.toString()
                if (!isAllowedDownloadUrl(finalUrl, allowRedirectHost = true)) {
                    _status.value = UpdateStatus.Error("Redirect to untrusted host blocked")
                    return@withContext
                }

                val body = response.body ?: throw Exception("Empty download response")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0.5f
                            _status.value = UpdateStatus.Downloading(progress, downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }

                val actualHash = computeSha256(apkFile)
                if (!actualHash.equals(resolvedExpectedHash, ignoreCase = true)) {
                    apkFile.delete()
                    _status.value = UpdateStatus.Error("APK integrity check failed (SHA-256 mismatch)")
                    return@withContext
                }

                _status.value = UpdateStatus.ReadyToInstall(apkFile)
                installApk(apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _status.value = UpdateStatus.Error("Download failed: ${e.message}")
        }
    }

    fun installApk(apkFile: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed", e)
            _status.value = UpdateStatus.Error("Cannot launch installer: ${e.message}")
        }
    }
}
