package com.example.util

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.Item
import com.example.data.repository.BillRowData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ParsedBillItem(
    val particulars: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val packSize: String = "",
    val unit: String = "pcs",
    val category: String = "",
    var matchedItem: Item? = null,
    var confidence: String = "auto" // "high", "medium", "new"
)

data class ParsedBillResult(
    val supplierName: String,
    val billNumber: String,
    val date: String,
    val totalAmount: Double,
    val itemsSubtotal: Double = totalAmount,
    val previousBalance: Double = 0.0,
    val items: List<ParsedBillItem>
)

object GeminiBillScanner {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // If image is very large, downscale slightly so network transfer is fast and within limits
        val maxDim = 1600
        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1.0f)
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    fun getSavedCustomApiKey(context: android.content.Context): String {
        return context.getSharedPreferences("gemini_prefs", android.content.Context.MODE_PRIVATE)
            .getString("custom_gemini_api_key", "") ?: ""
    }

    fun setSavedCustomApiKey(context: android.content.Context, key: String) {
        context.getSharedPreferences("gemini_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("custom_gemini_api_key", key.trim())
            .apply()
    }

    suspend fun parseBill(bitmap: Bitmap, inventoryItems: List<Item>, context: android.content.Context? = null): ParsedBillResult = withContext(Dispatchers.IO) {
        val customKey = if (context != null) getSavedCustomApiKey(context) else ""
        val candidateKeys = listOf(
            customKey,
            BuildConfig.GEMINI_KEY,
            BuildConfig.GEMINI_API_KEY
        ).map { it.trim() }
         .filter { it.isNotBlank() && it != "MY_GEMINI_KEY" && it != "MY_GEMINI_API_KEY" }
         .distinct()

        if (candidateKeys.isEmpty()) {
            throw IllegalStateException("Gemini API key is missing. Please add GEMINI_API_KEY or GEMINI_KEY to the AI Studio Secrets panel, or set it in the scanner.")
        }

        val learnedAliasesPrompt = if (context != null) BillAliasManager.formatPromptAliases(context) else ""

        // Build inventory reference string for in-context grounding
        val inventoryReference = inventoryItems.take(200).joinToString("\n") { item ->
            "- ${item.name} | Type: ${item.type} | Brand: ${item.brand} | Size: ${item.size} | Unit: ${item.unit} | Cost: ₹${item.cost} | Price: ₹${item.price} | Aliases: ${item.aliases}"
        }

        val prompt = """
You are an expert OCR, cash memo, and invoice parsing assistant specialized in Indian hardware, electrical, sanitary, and paint retail stores.
Carefully read and interpret this supplier invoice, cash memo, or handwritten rough receipt.

CRITICAL HARDWARE & PAINT DOMAIN KNOWLEDGE (TRAINED RULES):
1. Paint Shade Names & Romanized Abbreviations:
   - "Phirozi" / "Firozi" / "Phyrozi" = Firozi Blue (Turquoise/Sky Blue paint shade). CRITICAL: NEVER misread "Phirozi" as "Primer" or "Pwireji"!
   - "P.O. Red" / "P.O.red" / "P.O.RED" = Post Office Red (Bright Red synthetic enamel). CRITICAL: NEVER misread "P.O.red" as "R.oxeed" or "R.oxide"! The cursive 'P.O.' loops together.
   - "Wild Purple" = Wild Purple paint shade
   - "G.Brn" / "G. Brown" = Golden Brown paint shade
   - "Wood Pr" / "Wood pr" = Wood Primer
   - "Metal" / "Metal Pr" = Metal Primer / Enamel
   - "R.oxeed" / "Red Oxide" / "R. Oxide" = Red Oxide Primer
   - "Sparc Int" / "Sparc Interior" = Sparc Interior Emulsion
   - "U/C Terracotta" = Undercoat / Unitint Terracotta
   - "AB-2", "AB-11", "AB-17" = Paint Shade Codes
   - "B.White" = Brilliant White
   - "Cowhite" = Commercial White
   - "Pista" = Pista Green, "Bus Green", "Oxford Blue", "Smoke Grey", "Deep Orange"

2. Size x Quantity Notation in Indian Paint/Hardware Slips:
   - "1 x 6" or "1x6" = 1 Litre pack size × 6 cans (quantity = 6 cans). If total is 1350, then unit rate = 225.0
   - "500 x 8", "520 x 8", "1/2 x 8" = 500 ml pack size × 8 cans (quantity = 8 cans). If total is 1320, unit rate = 165.0
   - "20 x 1" = 20 Litre drum/bucket × 1 unit (quantity = 1 drum)
   - "200 x 10" = 200 ml pack size × 10 cans (quantity = 10 cans)
   - "4 x 4" = 4 Litre pack size × 4 cans (quantity = 4 cans)

3. Hardware, Electrical & Plumbing Terms:
   - "35mm lock", "50mm lock", "65mm lock" = Padlocks
   - "1\" Brush", "2\" Brush", "3\" Brush", "4\" Brush" = Paint Brushes (unit: pcs)
   - "Mini Shower" = Sanitary mini shower
   - "1\" Blade", "1/2\" Blade" = Hacksaw / Cutting Blades
   - "Kathi", "2\" C.H. Kathi", "1\" Kathi" = Kathi / Rod / Handle
   - "Kathi Washer" = Washers
   - "R. Checkball" = Rubber Check Ball
   - "Level" = Water/Spirit Level tube
   - "Tower Bolt", "3\" Tower bolt", "4\" Tower bolt", "5\" Tower bolt"
   - "15 m. Tape", "30 m. Tape" = Measuring Tape
   - "1\" Socket" = PVC / GI pipe socket
   - "Tester" = Electrical Line Tester
   - "Tarbrush" / "Tar brush" = Wire / Tar brush
   - "Piston Rod (Cr)" = Chrome Piston Rod

4. Calculation & Carryover Dues:
   - If there is an items subtotal and a previous balance added with '+' (e.g. 2820 + 7736 = 10556), distinguish:
     itemsSubtotal = sum of line items
     previousBalance = 7736.0
     totalAmount = 10556.0

$learnedAliasesPrompt

STORE INVENTORY CATALOG FOR DIRECT MATCHING:
$inventoryReference

Instructions:
Extract supplierName, billNumber, date (format DD/MM/YYYY if possible), totalAmount, itemsSubtotal, previousBalance, and all line items.
For each item, provide:
- particulars: cleaned full product name with pack size (e.g. "Phirozi 1L" or "35mm Lock" or "P.O. Red 500ml")
- packSize: e.g. "1L", "500ml", "20L", "35mm", "2 inch", "pcs"
- quantity: number of individual pieces/cans received
- rate: unit purchase cost per piece/can
- amount: line total amount (rate * quantity)
- unit: "can", "pcs", "drum", "mtr", "box", etc.
- category: "Paints", "Hardware", "Electrical", "Plumbing", or "General"
- matchedItemName: if this matches an item in the Store Inventory Catalog above, provide its exact name here.

Return ONLY valid JSON in this exact structure:
{
  "supplierName": "string",
  "billNumber": "string",
  "date": "string",
  "totalAmount": 0.0,
  "itemsSubtotal": 0.0,
  "previousBalance": 0.0,
  "items": [
    {
      "particulars": "string",
      "packSize": "string",
      "quantity": 0.0,
      "rate": 0.0,
      "amount": 0.0,
      "unit": "string",
      "category": "string",
      "matchedItemName": "string"
    }
  ]
}
""".trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", bitmapToBase64(bitmap))
                        }))
                    })
                })
            })
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }

        // Try currently supported multimodal models first. Older model IDs may return 404
        // when they are no longer available to a project or API key.
        val candidateModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash"
        )
        var lastException: Exception? = null
        var responseStr: String? = null

        keyLoop@ for (key in candidateKeys) {
            for (modelName in candidateModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key"
                    val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .header("x-goog-api-key", key)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && !bodyStr.isNullOrBlank()) {
                        responseStr = bodyStr
                        break@keyLoop
                    } else {
                        val friendlyError = try {
                            val errObj = JSONObject(bodyStr ?: "").optJSONObject("error")
                            errObj?.optString("message") ?: bodyStr
                        } catch (_: Exception) {
                            bodyStr
                        }
                        lastException = Exception("Gemini ($modelName) failed (${response.code}): $friendlyError")
                        // If this key is invalid or unauthorized, skip to the next key
                        if (response.code in listOf(400, 401, 403) && (bodyStr?.contains("API key not valid", ignoreCase = true) == true || bodyStr?.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED", ignoreCase = true) == true)) {
                            continue@keyLoop
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        if (responseStr == null) {
            throw (lastException ?: Exception("Failed to get response from Gemini vision service."))
        }

        val root = JSONObject(responseStr)
        val candidates = root.optJSONArray("candidates") ?: throw Exception("No candidates in Gemini response.")
        val firstCandidate = candidates.optJSONObject(0) ?: throw Exception("Empty candidates list.")
        val content = firstCandidate.optJSONObject("content") ?: throw Exception("No content object.")
        val parts = content.optJSONArray("parts") ?: throw Exception("No parts array.")

        // Collect non-thought text parts (some newer models include thought blocks)
        val textBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            val partText = part.optString("text", "")
            if (partText.isNotBlank()) {
                textBuilder.append(partText)
            }
        }
        val text = if (textBuilder.isNotEmpty()) textBuilder.toString() else parts.optJSONObject(0)?.optString("text", "") ?: ""

        val trimmedText = text.trim()
        val withoutFences = if (trimmedText.startsWith("```")) {
            trimmedText.substringAfter("\n").substringBeforeLast("```").trim()
        } else {
            trimmedText
        }
        val firstBrace = withoutFences.indexOf('{')
        val lastBrace = withoutFences.lastIndexOf('}')
        val cleanJson = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            withoutFences.substring(firstBrace, lastBrace + 1)
        } else {
            withoutFences
        }

        val billObj = JSONObject(cleanJson)
        val supplierName = billObj.optString("supplierName", "")
        val billNumber = billObj.optString("billNumber", "")
        val date = billObj.optString("date", "")
        val totalAmount = billObj.optDouble("totalAmount", 0.0)
        val itemsSubtotal = billObj.optDouble("itemsSubtotal", totalAmount)
        val previousBalance = billObj.optDouble("previousBalance", 0.0)

        val itemsArray = billObj.optJSONArray("items") ?: JSONArray()
        val billItems = mutableListOf<ParsedBillItem>()
        val aliases = if (context != null) BillAliasManager.getAliases(context) else emptyMap()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.getJSONObject(i)
            val rawParticulars = itemObj.optString("particulars", "")
            val cleanedParticulars = if (aliases.isNotEmpty()) BillAliasManager.applyAliases(rawParticulars, aliases) else rawParticulars
            val quantity = itemObj.optDouble("quantity", 1.0)
            val rate = itemObj.optDouble("rate", 0.0)
            val amount = itemObj.optDouble("amount", quantity * rate)
            val packSize = itemObj.optString("packSize", "")
            val unit = itemObj.optString("unit", "pcs").ifBlank { "pcs" }
            val category = itemObj.optString("category", "")
            val suggestedMatchName = itemObj.optString("matchedItemName", "").trim()

            val parsedItem = ParsedBillItem(
                particulars = cleanedParticulars,
                quantity = quantity,
                rate = rate,
                amount = amount,
                packSize = packSize,
                unit = unit,
                category = category
            )

            // Direct match from Gemini grounding or fuzzy match against store items
            val matchedByAi = if (suggestedMatchName.isNotBlank()) {
                inventoryItems.firstOrNull { it.name.trim().equals(suggestedMatchName, ignoreCase = true) }
            } else null

            val matched = matchedByAi ?: findBestMatchingInventoryItem(cleanedParticulars, packSize, inventoryItems)
            parsedItem.matchedItem = matched
            parsedItem.confidence = when {
                matched != null && (matched.name.equals(cleanedParticulars, true) || matchedByAi != null) -> "high"
                matched != null -> "medium"
                else -> "new"
            }

            billItems.add(parsedItem)
        }

        ParsedBillResult(
            supplierName = supplierName,
            billNumber = billNumber,
            date = date,
            totalAmount = totalAmount,
            itemsSubtotal = itemsSubtotal,
            previousBalance = previousBalance,
            items = billItems
        )
    }

    private fun findBestMatchingInventoryItem(particulars: String, packSize: String, items: List<Item>): Item? {
        if (items.isEmpty()) return null
        val pClean = particulars.trim().uppercase()
        val tokens = pClean.split(Regex("[\\s-_/,\"]+")).filter { it.length > 1 }

        var bestScore = 0
        var bestItem: Item? = null

        for (item in items) {
            var score = 0
            val nameUpper = item.name.uppercase()
            val codeUpper = item.code.uppercase()
            val aliasesUpper = item.aliases.uppercase()
            val sizeUpper = item.size.uppercase()

            if (nameUpper == pClean || codeUpper == pClean) {
                score += 100
            } else if (nameUpper.contains(pClean) || pClean.contains(nameUpper)) {
                score += 70
            } else if (aliasesUpper.contains(pClean)) {
                score += 65
            } else {
                for (token in tokens) {
                    if (nameUpper.contains(token) || aliasesUpper.contains(token)) {
                        score += 20
                    }
                }
            }

            // Pack size bonus
            if (packSize.isNotBlank() && sizeUpper.isNotBlank()) {
                val cleanPack = packSize.uppercase().replace(" ", "")
                val cleanSize = sizeUpper.replace(" ", "")
                if (cleanSize == cleanPack || cleanSize.contains(cleanPack) || cleanPack.contains(cleanSize)) {
                    score += 25
                }
            }

            if (score > bestScore && score >= 35) {
                bestScore = score
                bestItem = item
            }
        }
        return bestItem
    }

    fun toBillRows(parsed: ParsedBillResult, allItems: List<Item>): List<BillRowData> {
        return parsed.items.mapIndexed { index, item ->
            val matched = item.matchedItem
            val finalName = matched?.name ?: item.particulars
            val currentPrice = matched?.price ?: (if (item.rate > 0) item.rate * 1.20 else 0.0)

            BillRowData(
                id = "row_${System.currentTimeMillis()}_$index",
                name = finalName,
                rate = item.rate,
                qty = item.quantity,
                type = matched?.type ?: item.category,
                brand = matched?.brand ?: "",
                size = matched?.size ?: item.packSize,
                unit = matched?.unit ?: item.unit,
                matchedItem = matched,
                include = true,
                updateCost = true,
                updateStock = true,
                sellingPrice = currentPrice,
                updateSellingPrice = false,
                packSize = item.packSize,
                confidence = item.confidence
            )
        }
    }
}
