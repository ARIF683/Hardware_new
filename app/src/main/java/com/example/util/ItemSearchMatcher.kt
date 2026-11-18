package com.example.util

import com.example.data.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ItemSearchMatcher {

    private val REGEX_LTR = Regex("(\\d+)\\s*(ltrs?|liters?|litres?|l)\\b")
    private val REGEX_LTR_SHORT = Regex("(\\d+)\\s*(l|ltr|liter|litres?)")
    private val REGEX_KG = Regex("(\\d+)\\s*(kgs?|kilos?|kilograms?)\\b")
    private val REGEX_GM = Regex("(\\d+)\\s*(gms?|grams?|g)\\b")
    private val REGEX_ML = Regex("(\\d+)\\s*(mls?|milliliters?)\\b")
    private val REGEX_MTR = Regex("(\\d+)\\s*(mtrs?|meters?|metres?|m)\\b")
    private val REGEX_INCH = Regex("(\\d+)\\s*(inch|inches|\")\\b")
    private val REGEX_NON_ALNUM = Regex("[^a-z0-9]")
    private val REGEX_LETTER_NUM = Regex("([a-z]+)(\\d+)")
    private val REGEX_NUM_LETTER = Regex("(\\d+)([a-z]+)")
    private val REGEX_WS = Regex("\\s+")
    private val REGEX_WORD_SPLIT = Regex("[^a-z0-9]+")
    private val REGEX_PURE_LETTER_NUM = Regex("^([a-z]+)(\\d+)$")
    private val REGEX_VOICE_JOIN = Regex("(?i)\\b([a-z]{1,4})\\s+(\\d+)\\b")
    private val REGEX_VOICE_LTR = Regex("(?i)\\b(\\d+)\\s+(ltr|l|liter|litres|litre)\\b")

    private data class PreparedItem(
        val signature: Int,
        val nameLower: String,
        val cleanName: String,
        val codeLower: String,
        val cleanCode: String,
        val barcodeLower: String,
        val haystack: String,
        val cleanHaystack: String,
        val hayWords: List<String>
    )

    private val preparedCache = ConcurrentHashMap<String, PreparedItem>()

    private fun itemSignature(item: Item): Int {
        var result = item.name.hashCode()
        result = 31 * result + item.size.hashCode()
        result = 31 * result + item.brand.hashCode()
        result = 31 * result + item.type.hashCode()
        result = 31 * result + item.code.hashCode()
        result = 31 * result + item.barcode.hashCode()
        result = 31 * result + item.aliases.hashCode()
        result = 31 * result + item.unit.hashCode()
        result = 31 * result + (item.mrp?.hashCode() ?: 0)
        return result
    }

    private fun getPrepared(item: Item): PreparedItem {
        val sig = itemSignature(item)
        val key = item.id.ifBlank { item.name }
        val cached = preparedCache[key]
        if (cached != null && cached.signature == sig) {
            return cached
        }
        val nameLower = item.name.lowercase(Locale.ROOT)
        val cleanName = nameLower.replace(REGEX_NON_ALNUM, "")
        val codeLower = item.code.lowercase(Locale.ROOT)
        val cleanCode = codeLower.replace(REGEX_NON_ALNUM, "")
        val barcodeLower = item.barcode.lowercase(Locale.ROOT)
        val haystack = buildItemSearchHaystack(item)
        val cleanHaystack = haystack.replace(REGEX_NON_ALNUM, "")
        val hayWords = haystack.split(REGEX_WORD_SPLIT).filter { it.length >= 2 }
        val entry = PreparedItem(
            signature = sig,
            nameLower = nameLower,
            cleanName = cleanName,
            codeLower = codeLower,
            cleanCode = cleanCode,
            barcodeLower = barcodeLower,
            haystack = haystack,
            cleanHaystack = cleanHaystack,
            hayWords = hayWords
        )
        if (preparedCache.size > 4000) {
            preparedCache.clear()
        }
        preparedCache[key] = entry
        return entry
    }

    /**
     * Normalizes text by expanding common unit abbreviations and normalizing spaces.
     * e.g. "20l" -> "20ltr 20l 20 liter"
     */
    fun normalizeQuery(query: String): String {
        var q = query.lowercase(Locale.ROOT).trim()

        // Normalize units attached or separated
        q = q.replace(REGEX_LTR, "$1ltr $1l")
        q = q.replace(REGEX_KG, "$1kg")
        q = q.replace(REGEX_GM, "$1gm")
        q = q.replace(REGEX_ML, "$1ml")
        q = q.replace(REGEX_MTR, "$1mtr")
        q = q.replace(REGEX_INCH, "$1inch")

        return q
    }

    /**
     * Normalizes spoken voice query to improve hardware/paint recognition.
     * e.g. "ab 2 20 ltr" -> "ab2 20ltr"
     */
    fun normalizeVoiceInput(spoken: String): String {
        var text = spoken.trim()
        // Join letter prefix with numbers: "ab 2" -> "ab2", "ab 6" -> "ab6"
        text = text.replace(REGEX_VOICE_JOIN, "$1$2")
        // Standardize 20 l / 20 ltr -> 20ltr
        text = text.replace(REGEX_VOICE_LTR, "$1ltr")
        return text
    }

    /**
     * Builds a comprehensive search haystack for an Item containing all fields
     * and their normalized variants (spaces, combined letters+numbers, units).
     */
    fun buildItemSearchHaystack(item: Item): String {
        val sb = StringBuilder(128)
        val nameLower = item.name.lowercase(Locale.ROOT)
        sb.append(nameLower).append(' ')
        // Name without spaces/punctuation: e.g. "AB 2" -> "ab2"
        sb.append(nameLower.replace(REGEX_NON_ALNUM, "")).append(' ')
        // Name with letter-number split: e.g. "AB2" -> "ab 2"
        val splitName = nameLower.replace(REGEX_LETTER_NUM, "$1 $2")
        sb.append(splitName).append(' ')

        // Size & Unit variants
        val sizeLower = item.size.lowercase(Locale.ROOT)
        if (sizeLower.isNotBlank()) {
            sb.append(sizeLower).append(' ')
            sb.append(sizeLower.replace(REGEX_NON_ALNUM, "")).append(' ')
            sb.append(normalizeQuery(sizeLower)).append(' ')
            val sizeSplit = sizeLower.replace(REGEX_NUM_LETTER, "$1 $2")
            sb.append(sizeSplit).append(' ')

            // If size is e.g. "20ltr" or "20 ltr", also inject "20l", "20 l", "20 liter"
            val literMatch = REGEX_LTR_SHORT.find(sizeLower)
            if (literMatch != null) {
                val n = literMatch.groupValues[1]
                sb.append("${n}l ${n}ltr $n l $n ltr $n liter ")
            }
        }

        sb.append(item.brand.lowercase(Locale.ROOT)).append(' ')
        sb.append(item.type.lowercase(Locale.ROOT)).append(' ')
        sb.append(item.code.lowercase(Locale.ROOT)).append(' ')
        sb.append(item.barcode.lowercase(Locale.ROOT)).append(' ')
        sb.append(item.aliases.lowercase(Locale.ROOT)).append(' ')
        sb.append(item.unit.lowercase(Locale.ROOT)).append(' ')
        if (item.mrp != null) sb.append(item.mrp.toString()).append(' ')

        return sb.toString()
    }

    /**
     * Off-main-thread search helper that ranks matching items with optional category filtering.
     */
    suspend fun searchItems(
        items: List<Item>,
        rawQuery: String,
        typeFilter: String = "",
        limit: Int = 25
    ): List<Item> = withContext(Dispatchers.Default) {
        val q = rawQuery.trim()
        val curType = typeFilter.trim()
        val categoryFilterActive = curType.isNotBlank() && !curType.equals("All", ignoreCase = true)

        if (q.isBlank()) {
            val base = if (categoryFilterActive) {
                val filtered = items.filter { it.type.trim().equals(curType, ignoreCase = true) }
                if (filtered.isNotEmpty()) filtered else items
            } else {
                items
            }
            return@withContext if (limit > 0) base.take(limit) else base
        }

        if (categoryFilterActive) {
            val inCategoryMatches = items.mapNotNull { dbItem ->
                if (!dbItem.type.trim().equals(curType, ignoreCase = true)) return@mapNotNull null
                val score = matchScore(dbItem, q)
                if (score > 0) Pair(dbItem, score) else null
            }.sortedByDescending { it.second }.map { it.first }

            if (inCategoryMatches.isNotEmpty()) {
                return@withContext if (limit > 0) inCategoryMatches.take(limit) else inCategoryMatches
            }
        }

        val allMatches = items.mapNotNull { dbItem ->
            val score = matchScore(dbItem, q)
            if (score > 0) Pair(dbItem, score) else null
        }.sortedByDescending { it.second }.map { it.first }

        if (limit > 0) allMatches.take(limit) else allMatches
    }

    /**
     * Calculates relevance score (0 = no match, >0 = matches).
     * Handles compound searches across name + size (e.g. "ab2 20l", "ab6 4ltr", "asian paints 20l").
     */
    fun matchScore(item: Item, rawQuery: String): Int {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return 1

        val prepared = getPrepared(item)
        val nameLower = prepared.nameLower
        val codeLower = prepared.codeLower
        val barcodeLower = prepared.barcodeLower

        // Exact match on code, barcode, or exact name
        if (codeLower == q || barcodeLower == q || nameLower == q) return 1000

        // Exact prefix match on name
        if (nameLower.startsWith(q)) return 800

        // Clean query matching (ignoring spaces & symbols: e.g. 'Ab 11' matches 'Ab11', 'AB-11', 'AB11')
        val cleanQ = q.replace(REGEX_NON_ALNUM, "")
        val cleanName = prepared.cleanName
        val cleanCode = prepared.cleanCode
        if (cleanQ.isNotEmpty()) {
            if (cleanName == cleanQ || cleanCode == cleanQ) return 980
            if (cleanName.startsWith(cleanQ) || cleanCode.startsWith(cleanQ)) return 850
            if (cleanName.contains(cleanQ) || cleanCode.contains(cleanQ)) return 750
        }

        // Split query into words
        val words = q.split(REGEX_WS).filter { it.isNotBlank() }
        if (words.isEmpty()) return 1

        val hay = prepared.haystack
        val cleanHay = prepared.cleanHaystack
        val hayWords = prepared.hayWords

        fun wordMatches(w: String): Boolean {
            if (hay.contains(w)) return true
            val cleanW = w.replace(REGEX_NON_ALNUM, "")
            if (cleanW.isNotEmpty() && cleanHay.contains(cleanW)) return true
            if (w.endsWith("l") && (hay.contains("${w}tr") || hay.contains("${w}iter"))) return true
            if (w.endsWith("ltr") && (hay.contains(w.removeSuffix("tr")) || hay.contains(w.removeSuffix("ltr") + "l"))) return true
            if (REGEX_PURE_LETTER_NUM.matches(w) && hay.contains(w.replace(REGEX_LETTER_NUM, "$1 $2"))) return true

            // Fuzzy typo matching against hayWords (Levenshtein distance)
            if (w.length >= 3) {
                val maxDist = if (w.length <= 4) 1 else 2
                for (hw in hayWords) {
                    if (kotlin.math.abs(hw.length - w.length) <= maxDist) {
                        if (levenshtein(w, hw) <= maxDist) return true
                    }
                }
            }
            return false
        }

        // Check if words match the item
        val matchCount = words.count { wordMatches(it) }
        if (matchCount == words.size) {
            val firstWord = words.first()
            val bonus = if (nameLower.startsWith(firstWord) || nameLower.contains(firstWord)) 300 else 100
            return 500 + bonus - (item.name.length.coerceAtMost(40))
        }

        // Clean query matching (ignoring punctuation/spaces) e.g. "ab6" matches "AB 6" or "ab 6" matches "AB6"
        if (cleanQ.isNotEmpty() && cleanHay.contains(cleanQ)) {
            val bonus = if (cleanName.startsWith(cleanQ)) 200 else 50
            return 300 + bonus
        }

        // Partial word matching for multi-word or single word
        if (matchCount > 0) {
            return 180 * matchCount
        }

        return 0
    }

    /**
     * Levenshtein edit distance for typo tolerance.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
