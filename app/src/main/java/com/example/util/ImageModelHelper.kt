package com.example.util

import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

object ImageModelHelper {
    fun resolveImageModel(imageUrl: String?): Any? {
        if (imageUrl.isNullOrBlank()) return null
        return if (imageUrl.startsWith("data:image")) {
            try {
                val base64Data = imageUrl.substringAfter(",")
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (_: Exception) {
                imageUrl
            }
        } else {
            imageUrl.trim()
        }
    }

    @Composable
    fun rememberImageModel(imageUrl: String?): Any? {
        return remember(imageUrl) { resolveImageModel(imageUrl) }
    }
}
