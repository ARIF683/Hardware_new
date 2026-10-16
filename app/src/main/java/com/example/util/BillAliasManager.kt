package com.example.util

import android.content.Context
import org.json.JSONObject

/**
 * Manages store-specific handwriting and abbreviation aliases for the AI Bill Scanner.
 * Enables the merchant to "train" the AI by recording corrections made to scanned item names.
 */
object BillAliasManager {
    private const val PREF_NAME = "bill_ocr_aliases_pref"
    private const val KEY_ALIASES = "learned_aliases_json"

    // Default seed aliases for Indian hardware & paint trade
    private val DEFAULT_ALIASES = mapOf(
        "phirozi" to "Phirozi (Turquoise Blue)",
        "firozi" to "Phirozi (Turquoise Blue)",
        "phyrozi" to "Phirozi (Turquoise Blue)",
        "p.o.red" to "P.O. Red (Post Office Red)",
        "p.o. red" to "P.O. Red (Post Office Red)",
        "po red" to "P.O. Red (Post Office Red)",
        "pored" to "P.O. Red (Post Office Red)",
        "r.oxeed" to "Red Oxide Primer",
        "r.oxide" to "Red Oxide Primer",
        "wood pr" to "Wood Primer",
        "metal" to "Metal Primer / Enamel",
        "wild purple" to "Wild Purple",
        "g.brn" to "Golden Brown",
        "g. brown" to "Golden Brown",
        "kathi" to "C.H. Kathi",
        "checkball" to "Rubber Checkball",
        "tarbrush" to "Tar Brush",
        "sparc int" to "Sparc Interior Emulsion",
        "u/c terracotta" to "Undercoat Terracotta"
    )

    fun getAliases(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ALIASES, null)
        val result = DEFAULT_ALIASES.toMutableMap()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key.lowercase().trim()] = json.getString(key)
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    fun saveAlias(context: Context, rawHandwritten: String, mappedItemName: String) {
        val cleanRaw = rawHandwritten.lowercase().trim()
        val cleanMapped = mappedItemName.trim()
        if (cleanRaw.isBlank() || cleanMapped.isBlank()) return

        val current = getAliases(context).toMutableMap()
        current[cleanRaw] = cleanMapped

        val json = JSONObject()
        for ((k, v) in current) {
            json.put(k, v)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALIASES, json.toString())
            .apply()
    }

    fun removeAlias(context: Context, rawHandwritten: String) {
        val cleanRaw = rawHandwritten.lowercase().trim()
        val current = getAliases(context).toMutableMap()
        current.remove(cleanRaw)

        val json = JSONObject()
        for ((k, v) in current) {
            json.put(k, v)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALIASES, json.toString())
            .apply()
    }

    fun formatPromptAliases(context: Context): String {
        val aliases = getAliases(context)
        if (aliases.isEmpty()) return ""
        return buildString {
            appendLine("LEARNED STORE-SPECIFIC HANDWRITING CORRECTIONS (FROM PREVIOUS STORE BILLS):")
            for ((raw, corrected) in aliases.entries.take(40)) {
                appendLine("- When handwritten as \"$raw\" -> It means \"$corrected\"")
            }
        }
    }

    fun applyAliases(particulars: String, aliases: Map<String, String>): String {
        val lower = particulars.lowercase().trim()
        for ((raw, target) in aliases) {
            if (lower == raw || lower.startsWith("$raw ") || lower.contains(raw)) {
                // If it's an exact match or clear prefix, resolve it
                if (lower == raw) return target
            }
        }
        return particulars
    }
}
