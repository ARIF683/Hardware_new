package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import java.nio.charset.Charset
import java.util.Locale

object NfcShelfManager {

    fun isNfcAvailable(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null
    }

    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null && adapter.isEnabled
    }

    fun extractTagIdHex(intent: Intent): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return null

        val idBytes = tag.id ?: return null
        return bytesToHex(idBytes)
    }

    fun extractNdefPayload(intent: Intent): String? {
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        } ?: return null

        for (rawMsg in rawMessages) {
            val msg = rawMsg as? NdefMessage ?: continue
            for (record in msg.records) {
                val payload = parseTextRecord(record)
                if (!payload.isNullOrBlank()) return payload
            }
        }
        return null
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null
        return try {
            val isUtf8 = (payload[0].toInt() and 0x80) == 0
            val languageCodeLength = payload[0].toInt() and 0x3F
            val textEncoding = if (isUtf8) Charset.forName("UTF-8") else Charset.forName("UTF-16")
            String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, textEncoding)
        } catch (e: Exception) {
            try {
                String(payload, Charset.forName("UTF-8"))
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun writeTextToTag(tag: Tag, text: String): Pair<Boolean, String> {
        return try {
            val langBytes = Locale.ENGLISH.language.toByteArray(Charset.forName("US-ASCII"))
            val textBytes = text.toByteArray(Charset.forName("UTF-8"))
            val payload = ByteArray(1 + langBytes.size + textBytes.size)
            payload[0] = langBytes.size.toByte()
            System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
            System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

            val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
            val message = NdefMessage(arrayOf(record))

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return Pair(false, "NFC Tag is write-protected.")
                }
                if (ndef.maxSize < message.byteArrayLength) {
                    ndef.close()
                    return Pair(false, "Tag capacity too small for data.")
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Pair(true, "Successfully encoded shelf bin to NFC tag!")
            } else {
                Pair(false, "Tag does not support standard NDEF formatting.")
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Failed to write NFC tag")
        }
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder()
        for (i in bytes.indices) {
            if (i > 0) sb.append(":")
            sb.append(String.format("%02X", bytes[i]))
        }
        return sb.toString()
    }

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!adapter.isEnabled) return

        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: Exception) {}
        }
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)

        adapter.enableForegroundDispatch(activity, pendingIntent, arrayOf(ndefFilter, tagFilter, techFilter), null)
    }

    fun disableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        try {
            adapter.disableForegroundDispatch(activity)
        } catch (e: Exception) {}
    }
}
