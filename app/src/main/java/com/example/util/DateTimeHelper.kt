package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared timezone-safe date and UTC ISO timestamp helpers.
 * All stored/cloud timestamps are UTC ISO-8601 (`...Z`), while UI groupings,
 * date keys, and time labels convert explicitly into the device's local timezone.
 */
object DateTimeHelper {

    fun nowIso(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(nowMillis))
    }

    fun localDateKey(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(nowMillis))
    }

    fun localMonthKey(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return SimpleDateFormat("yyyy-MM", Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(nowMillis))
    }

    fun parseTimestampMillis(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val utcPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in utcPatterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        // Offset-aware ISO strings such as 2026-11-24T23:50:00+05:30 or +00:00
        val offsetPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        )
        for (pattern in offsetPatterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        // Plain date-time or date-only strings already in local wall time
        val localPatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in localPatterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getDefault()
                }
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    fun utcIsoToLocalDateKey(
        raw: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val millis = parseTimestampMillis(raw)
        if (millis != null) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                this.timeZone = timeZone
            }.format(Date(millis))
        }
        return raw.trim().take(10)
    }

    fun utcIsoToLocalMonthKey(
        raw: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val millis = parseTimestampMillis(raw)
        if (millis != null) {
            return SimpleDateFormat("yyyy-MM", Locale.US).apply {
                this.timeZone = timeZone
            }.format(Date(millis))
        }
        return raw.trim().take(7)
    }

    fun utcIsoToLocalTimeLabel(
        raw: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val millis = parseTimestampMillis(raw) ?: return ""
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }.format(Date(millis))
    }

    /**
     * Converts common UI/user date strings (dd/MM/yyyy, dd-MM-yyyy, yyyy/MM/dd, etc.)
     * into canonical "yyyy-MM-dd" for SQL/lexicographic sorting and period filtering.
     */
    fun normalizeDateToIso(
        raw: String,
        fallbackDate: String = localDateKey()
    ): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fallbackDate

        // Already ISO yyyy-MM-dd (or yyyy-MM-ddTHH:...)
        val isoPrefix = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s].*)?$""").matchEntire(trimmed)
        if (isoPrefix != null) {
            val y = isoPrefix.groupValues[1].toIntOrNull()
            val m = isoPrefix.groupValues[2].toIntOrNull()
            val d = isoPrefix.groupValues[3].toIntOrNull()
            if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
            }
        }

        val inputPatterns = listOf(
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "yyyy/MM/dd",
            "dd MMM yyyy",
            "dd-MMM-yyyy"
        )
        for (pattern in inputPatterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val parsed = fmt.parse(trimmed)
                if (parsed != null) {
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                }
            } catch (_: Exception) {
            }
        }
        return trimmed
    }
}
