package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.TransactionRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SpreadsheetSanitizer {
    fun sanitizeFormulaText(value: String): String {
        if (value.isEmpty()) return ""
        val first = value[0]
        val trimmedFirst = value.trimStart().firstOrNull()
        val isDangerous = first == '=' || first == '+' || first == '-' || first == '@' ||
            first == '\t' || first == '\r' ||
            trimmedFirst == '=' || trimmedFirst == '+' || trimmedFirst == '-' || trimmedFirst == '@'
        return if (isDangerous) "'$value" else value
    }

    fun csvEscape(value: String): String {
        val safe = sanitizeFormulaText(value)
        val cleaned = safe.replace("\"", "\"\"")
        return "\"$cleaned\""
    }
}

/**
 * Generates native multi-sheet Excel SpreadsheetML (.xls) files with formatting,
 * typed numeric cells, and summary totals—requiring zero external libraries or storage permissions.
 */
object XlsExporter {

    private const val XLS_MIME = "application/vnd.ms-excel"

    data class XlsColumn(
        val header: String,
        val width: Int = 110
    )

    sealed class XlsCell {
        data class Text(val value: String) : XlsCell()
        data class Number(val value: Double, val isInteger: Boolean = false) : XlsCell()
    }

    data class XlsSheet(
        val name: String,
        val columns: List<XlsColumn>,
        val rows: List<List<XlsCell>>,
        val summaryRow: List<XlsCell>? = null
    )

    fun buildInventoryWorkbookXml(items: List<Item>): String {
        val columns = listOf(
            XlsColumn("SKU", 95),
            XlsColumn("Barcode", 115),
            XlsColumn("Item Name", 210),
            XlsColumn("Brand", 110),
            XlsColumn("Type", 105),
            XlsColumn("Size", 85),
            XlsColumn("Unit", 65),
            XlsColumn("Quantity", 80),
            XlsColumn("Low Alert", 80),
            XlsColumn("Cost", 90),
            XlsColumn("Price", 90),
            XlsColumn("MRP", 90),
            XlsColumn("Stock Value", 105),
            XlsColumn("Retail Value", 105),
            XlsColumn("Shelf", 85),
            XlsColumn("Aliases", 160)
        )

        var totalQty = 0.0
        var totalCostValue = 0.0
        var totalRetailValue = 0.0

        val rows = items.map { item ->
            val stockVal = item.qty * item.cost
            val retailVal = item.qty * item.price
            totalQty += item.qty
            totalCostValue += stockVal
            totalRetailValue += retailVal

            listOf(
                XlsCell.Text(item.code),
                XlsCell.Text(item.barcode),
                XlsCell.Text(item.name),
                XlsCell.Text(item.brand),
                XlsCell.Text(item.type),
                XlsCell.Text(item.size),
                XlsCell.Text(item.unit),
                XlsCell.Number(item.qty),
                XlsCell.Number(item.low),
                XlsCell.Number(item.cost),
                XlsCell.Number(item.price),
                item.mrp?.let { XlsCell.Number(it) } ?: XlsCell.Text(""),
                XlsCell.Number(stockVal),
                XlsCell.Number(retailVal),
                XlsCell.Text(item.shelfLocation ?: ""),
                XlsCell.Text(item.aliases)
            )
        }

        val summary = if (items.isNotEmpty()) {
            listOf(
                XlsCell.Text("TOTAL (${items.size} SKUs)"),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Number(totalQty),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Number(totalCostValue),
                XlsCell.Number(totalRetailValue),
                XlsCell.Text(""),
                XlsCell.Text("")
            )
        } else null

        return buildWorkbookXml(
            listOf(
                XlsSheet(
                    name = "Inventory",
                    columns = columns,
                    rows = rows,
                    summaryRow = summary
                )
            )
        )
    }

    fun buildGstr1WorkbookXml(
        bills: List<GstBillRecord>,
        storeName: String,
        storeGstMode: StoreGstMode? = null
    ): String {
        val columns = listOf(
            XlsColumn("Store", 140),
            XlsColumn("Invoice No", 110),
            XlsColumn("Invoice Date", 95),
            XlsColumn("GSTR-1 Bucket", 105),
            XlsColumn("Customer Name", 170),
            XlsColumn("Phone", 105),
            XlsColumn("Customer GSTIN", 135),
            XlsColumn("Customer Address", 180),
            XlsColumn("Supply Type", 140),
            XlsColumn("Taxable Value", 105),
            XlsColumn("CGST", 90),
            XlsColumn("SGST", 90),
            XlsColumn("IGST", 90),
            XlsColumn("Total Tax", 95),
            XlsColumn("Grand Total", 105),
            XlsColumn("Status", 90),
            XlsColumn("Estimate Ref", 110)
        )

        var sumTaxable = 0.0
        var sumCgst = 0.0
        var sumSgst = 0.0
        var sumIgst = 0.0
        var sumTax = 0.0
        var sumGrand = 0.0

        val rows = bills.map { bill ->
            val bucket = GstinComplianceHelper.classifyGstr1Bucket(bill, storeGstMode)
            val cgst = if (bill.isInterState) 0.0 else bill.totalTaxAmount / 2.0
            val sgst = if (bill.isInterState) 0.0 else bill.totalTaxAmount / 2.0
            val igst = if (bill.isInterState) bill.totalTaxAmount else 0.0
            val supplyType = if (bill.isInterState) "Inter-State (IGST)" else "Intra-State (CGST+SGST)"

            sumTaxable += bill.taxableTotal
            sumCgst += cgst
            sumSgst += sgst
            sumIgst += igst
            sumTax += bill.totalTaxAmount
            sumGrand += bill.grandTotal

            listOf(
                XlsCell.Text(storeName),
                XlsCell.Text(bill.invoiceNo),
                XlsCell.Text(bill.invoiceDate),
                XlsCell.Text(bucket.code),
                XlsCell.Text(bill.customerName),
                XlsCell.Text(bill.customerPhone),
                XlsCell.Text(bill.customerGstin),
                XlsCell.Text(bill.customerAddress),
                XlsCell.Text(supplyType),
                XlsCell.Number(bill.taxableTotal),
                XlsCell.Number(cgst),
                XlsCell.Number(sgst),
                XlsCell.Number(igst),
                XlsCell.Number(bill.totalTaxAmount),
                XlsCell.Number(bill.grandTotal),
                XlsCell.Text(bill.status),
                XlsCell.Text(bill.quotationNo ?: "")
            )
        }

        val summary = if (bills.isNotEmpty()) {
            listOf(
                XlsCell.Text("TOTAL (${bills.size} Bills)"),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Number(sumTaxable),
                XlsCell.Number(sumCgst),
                XlsCell.Number(sumSgst),
                XlsCell.Number(sumIgst),
                XlsCell.Number(sumTax),
                XlsCell.Number(sumGrand),
                XlsCell.Text(""),
                XlsCell.Text("")
            )
        } else null

        return buildWorkbookXml(
            listOf(
                XlsSheet(
                    name = "GSTR-1 Register",
                    columns = columns,
                    rows = rows,
                    summaryRow = summary
                )
            )
        )
    }

    fun buildTransactionsWorkbookXml(
        cashflow: List<DailyCashflowRecord>,
        stockMovements: List<TransactionRecord>
    ): String {
        return buildWorkbookXml(
            listOf(
                buildCashflowSheet(cashflow),
                buildStockMovementSheet(stockMovements)
            )
        )
    }

    fun buildFullBusinessWorkbookXml(
        items: List<Item>,
        cashflow: List<DailyCashflowRecord>,
        stockMovements: List<TransactionRecord>,
        gstBills: List<GstBillRecord>,
        ledgerAccounts: List<LedgerAccount>,
        storeName: String,
        storeGstMode: StoreGstMode? = null
    ): String {
        val inventorySheet = buildInventorySheet(items)
        val cashflowSheet = buildCashflowSheet(cashflow)
        val movementSheet = buildStockMovementSheet(stockMovements)
        val gstSheet = buildGstr1Sheet(gstBills, storeName, storeGstMode)
        val khataSheet = buildKhataSheet(ledgerAccounts)
        return buildWorkbookXml(listOf(inventorySheet, cashflowSheet, movementSheet, gstSheet, khataSheet))
    }

    private fun buildInventorySheet(items: List<Item>): XlsSheet {
        val columns = listOf(
            XlsColumn("SKU", 95),
            XlsColumn("Barcode", 115),
            XlsColumn("Item Name", 210),
            XlsColumn("Brand", 110),
            XlsColumn("Type", 105),
            XlsColumn("Size", 85),
            XlsColumn("Unit", 65),
            XlsColumn("Quantity", 80),
            XlsColumn("Cost", 90),
            XlsColumn("Price", 90),
            XlsColumn("Stock Value", 105)
        )
        var totalQty = 0.0
        var totalValue = 0.0
        val rows = items.map { i ->
            val v = i.qty * i.cost
            totalQty += i.qty
            totalValue += v
            listOf(
                XlsCell.Text(i.code),
                XlsCell.Text(i.barcode),
                XlsCell.Text(i.name),
                XlsCell.Text(i.brand),
                XlsCell.Text(i.type),
                XlsCell.Text(i.size),
                XlsCell.Text(i.unit),
                XlsCell.Number(i.qty),
                XlsCell.Number(i.cost),
                XlsCell.Number(i.price),
                XlsCell.Number(v)
            )
        }
        val summary = if (items.isNotEmpty()) {
            listOf(
                XlsCell.Text("TOTAL (${items.size} SKUs)"),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Number(totalQty),
                XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Number(totalValue)
            )
        } else null
        return XlsSheet("Inventory", columns, rows, summary)
    }

    private fun buildCashflowSheet(records: List<DailyCashflowRecord>): XlsSheet {
        val columns = listOf(
            XlsColumn("Date", 95),
            XlsColumn("Type", 80),
            XlsColumn("Title", 160),
            XlsColumn("Category", 120),
            XlsColumn("Payment Mode", 105),
            XlsColumn("Amount", 100),
            XlsColumn("Note", 200)
        )
        var netTotal = 0.0
        val rows = records.map { rec ->
            val signedAmount = if (rec.type.equals("EXPENSE", ignoreCase = true)) -rec.amount else rec.amount
            netTotal += signedAmount
            listOf(
                XlsCell.Text(rec.date),
                XlsCell.Text(rec.type),
                XlsCell.Text(rec.title.ifBlank { rec.category }),
                XlsCell.Text(rec.category),
                XlsCell.Text(rec.paymentMode),
                XlsCell.Number(rec.amount),
                XlsCell.Text(rec.note)
            )
        }
        val summary = if (records.isNotEmpty()) {
            listOf(
                XlsCell.Text("NET P&L (${records.size} entries)"),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Text(""),
                XlsCell.Number(netTotal),
                XlsCell.Text("")
            )
        } else null
        return XlsSheet("Cashflow & P&L", columns, rows, summary)
    }

    private fun buildStockMovementSheet(movements: List<TransactionRecord>): XlsSheet {
        val columns = listOf(
            XlsColumn("Timestamp", 145),
            XlsColumn("Item Name", 200),
            XlsColumn("Action", 75),
            XlsColumn("Qty", 80),
            XlsColumn("Unit", 65),
            XlsColumn("Balance After", 95),
            XlsColumn("Note", 210)
        )
        val rows = movements.map { tx ->
            listOf(
                XlsCell.Text(tx.createdAt),
                XlsCell.Text(tx.itemName),
                XlsCell.Text(tx.action.uppercase(Locale.US)),
                XlsCell.Number(tx.qty),
                XlsCell.Text(tx.unit),
                XlsCell.Number(tx.balance),
                XlsCell.Text(tx.note)
            )
        }
        return XlsSheet("Stock Movements", columns, rows, null)
    }

    private fun buildGstr1Sheet(
        bills: List<GstBillRecord>,
        storeName: String,
        storeGstMode: StoreGstMode? = null
    ): XlsSheet {
        val columns = listOf(
            XlsColumn("Store", 130),
            XlsColumn("Invoice No", 105),
            XlsColumn("Date", 95),
            XlsColumn("GSTR-1 Bucket", 105),
            XlsColumn("Customer", 160),
            XlsColumn("GSTIN", 130),
            XlsColumn("Address", 165),
            XlsColumn("Taxable", 95),
            XlsColumn("Total Tax", 90),
            XlsColumn("Grand Total", 105),
            XlsColumn("Status", 85)
        )
        var sumTaxable = 0.0
        var sumTax = 0.0
        var sumGrand = 0.0
        val rows = bills.map { b ->
            val bucket = GstinComplianceHelper.classifyGstr1Bucket(b, storeGstMode)
            sumTaxable += b.taxableTotal
            sumTax += b.totalTaxAmount
            sumGrand += b.grandTotal
            listOf(
                XlsCell.Text(storeName),
                XlsCell.Text(b.invoiceNo),
                XlsCell.Text(b.invoiceDate),
                XlsCell.Text(bucket.code),
                XlsCell.Text(b.customerName),
                XlsCell.Text(b.customerGstin),
                XlsCell.Text(b.customerAddress),
                XlsCell.Number(b.taxableTotal),
                XlsCell.Number(b.totalTaxAmount),
                XlsCell.Number(b.grandTotal),
                XlsCell.Text(b.status)
            )
        }
        val summary = if (bills.isNotEmpty()) {
            listOf(
                XlsCell.Text("TOTAL (${bills.size})"),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Number(sumTaxable),
                XlsCell.Number(sumTax),
                XlsCell.Number(sumGrand),
                XlsCell.Text("")
            )
        } else null
        return XlsSheet("GST Bills", columns, rows, summary)
    }

    fun buildKhataWorkbookXml(accounts: List<LedgerAccount>): String {
        return buildWorkbookXml(listOf(buildKhataSheet(accounts)))
    }

    fun buildLedgerStatementWorkbookXml(
        account: LedgerAccount,
        entries: List<LedgerEntry>,
        storeName: String = "Hardware Store"
    ): String {
        val columns = listOf(
            XlsColumn("Date", 95),
            XlsColumn("Particulars", 210),
            XlsColumn("Payment Mode", 110),
            XlsColumn("Bill / UTR Ref", 120),
            XlsColumn("Site / Project", 120),
            XlsColumn("Due Date", 95),
            XlsColumn("Gave (Debit)", 100),
            XlsColumn("Got (Credit)", 100),
            XlsColumn("Running Bal", 105)
        )
        val sorted = entries.sortedWith(
            compareBy<LedgerEntry>(
                { DateTimeHelper.normalizeDateToIso(it.date, it.date) },
                { if (KhataMetaCodec.parseEntryMeta(it.description).isOpeningBalance) 0 else 1 },
                { it.createdAt },
                { it.id }
            )
        )
        var totalGave = 0.0
        var totalGot = 0.0
        val rows = sorted.map { entry ->
            val meta = KhataMetaCodec.parseEntryMeta(entry.description)
            val isGave = entry.type.equals("GAVE", ignoreCase = true)
            if (isGave) totalGave += entry.amount else totalGot += entry.amount
            val refJoined = listOfNotNull(
                entry.billRef.takeIf { it.isNotBlank() }?.let { "Bill #$it" },
                meta.paymentRef.takeIf { it.isNotBlank() }
            ).joinToString(" / ")
            listOf(
                XlsCell.Text(entry.date),
                XlsCell.Text(meta.cleanDescription.ifBlank { if (isGave) "Debit" else "Credit" }),
                XlsCell.Text(meta.paymentMode),
                XlsCell.Text(refJoined),
                XlsCell.Text(meta.siteName),
                XlsCell.Text(meta.dueDate),
                if (isGave) XlsCell.Number(entry.amount) else XlsCell.Number(0.0),
                if (!isGave) XlsCell.Number(entry.amount) else XlsCell.Number(0.0),
                XlsCell.Number(entry.balanceAfter)
            )
        }
        val status = KhataBalanceHelper.statusFor(account.type, account.netBalance)
        val summary = listOf(
            XlsCell.Text("${account.name} (${storeName.take(16)})"),
            XlsCell.Text("Status: ${status.label}"),
            XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
            XlsCell.Number(totalGave),
            XlsCell.Number(totalGot),
            XlsCell.Number(account.netBalance)
        )
        return buildWorkbookXml(listOf(XlsSheet(account.name, columns, rows, summary)))
    }

    private fun buildKhataSheet(accounts: List<LedgerAccount>): XlsSheet {
        val columns = listOf(
            XlsColumn("Account Name", 180),
            XlsColumn("Type", 95),
            XlsColumn("Status", 90),
            XlsColumn("Phone", 110),
            XlsColumn("GSTIN", 130),
            XlsColumn("Address", 170),
            XlsColumn("Net Balance", 105),
            XlsColumn("Credit Limit", 95),
            XlsColumn("Credit Days", 80),
            XlsColumn("Notes", 160)
        )
        var totalNet = 0.0
        val rows = accounts.map { acc ->
            val meta = KhataMetaCodec.parseAccountMeta(acc.notes)
            val status = KhataBalanceHelper.statusFor(acc.type, acc.netBalance)
            totalNet += acc.netBalance
            listOf(
                XlsCell.Text(acc.name),
                XlsCell.Text(acc.type),
                XlsCell.Text(status.label),
                XlsCell.Text(acc.phone),
                XlsCell.Text(meta.gstin),
                XlsCell.Text(acc.address),
                XlsCell.Number(acc.netBalance),
                XlsCell.Number(acc.creditLimit),
                XlsCell.Number(meta.creditDays.toDouble(), isInteger = true),
                XlsCell.Text(meta.cleanNotes)
            )
        }
        val summary = if (accounts.isNotEmpty()) {
            listOf(
                XlsCell.Text("TOTAL (${accounts.size})"),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text(""),
                XlsCell.Number(totalNet),
                XlsCell.Text(""), XlsCell.Text(""), XlsCell.Text("")
            )
        } else null
        return XlsSheet("Khata Accounts", columns, rows, summary)
    }

    fun buildWorkbookXml(sheets: List<XlsSheet>): String {
        val sb = StringBuilder(4096)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<?mso-application progid="Excel.Sheet"?>""")
        sb.appendLine(
            """<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" xmlns:html="http://www.w3.org/TR/REC-html40">"""
        )
        sb.appendLine("  <Styles>")
        sb.appendLine("""    <Style ss:ID="Default" ss:Name="Normal"><Alignment ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="10" ss:Color="#292524"/></Style>""")
        sb.appendLine("""    <Style ss:ID="Header"><Alignment ss:Vertical="Center" ss:WrapText="1"/><Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#D97757" ss:Pattern="Solid"/></Style>""")
        sb.appendLine("""    <Style ss:ID="Num"><Alignment ss:Horizontal="Right" ss:Vertical="Center"/><NumberFormat ss:Format="0.00"/></Style>""")
        sb.appendLine("""    <Style ss:ID="Int"><Alignment ss:Horizontal="Right" ss:Vertical="Center"/><NumberFormat ss:Format="0"/></Style>""")
        sb.appendLine("""    <Style ss:ID="TotalText"><Alignment ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#292524"/><Interior ss:Color="#FAF7F2" ss:Pattern="Solid"/><Borders><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D97757"/></Borders></Style>""")
        sb.appendLine("""    <Style ss:ID="TotalNum"><Alignment ss:Horizontal="Right" ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#292524"/><Interior ss:Color="#FAF7F2" ss:Pattern="Solid"/><NumberFormat ss:Format="0.00"/><Borders><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D97757"/></Borders></Style>""")
        sb.appendLine("  </Styles>")

        val usedNames = mutableSetOf<String>()
        sheets.forEachIndexed { index, sheet ->
            val safeName = uniqueSheetName(sheet.name, usedNames, index)
            sb.appendLine("""  <Worksheet ss:Name="${escapeXml(safeName)}">""")
            sb.appendLine("    <Table>")
            sheet.columns.forEach { col ->
                sb.appendLine("""      <Column ss:AutoFitWidth="0" ss:Width="${col.width}"/>""")
            }

            // Header row
            sb.appendLine("""      <Row ss:Height="24">""")
            sheet.columns.forEach { col ->
                sb.appendLine("""        <Cell ss:StyleID="Header"><Data ss:Type="String">${escapeXml(col.header)}</Data></Cell>""")
            }
            sb.appendLine("      </Row>")

            // Data rows
            sheet.rows.forEach { row ->
                sb.appendLine("      <Row>")
                row.forEach { cell ->
                    appendCell(sb, cell, isSummary = false)
                }
                sb.appendLine("      </Row>")
            }

            // Summary row
            sheet.summaryRow?.let { sumRow ->
                sb.appendLine("""      <Row ss:Height="22">""")
                sumRow.forEach { cell ->
                    appendCell(sb, cell, isSummary = true)
                }
                sb.appendLine("      </Row>")
            }

            sb.appendLine("    </Table>")
            sb.appendLine("  </Worksheet>")
        }

        sb.appendLine("</Workbook>")
        return sb.toString()
    }

    private fun appendCell(sb: StringBuilder, cell: XlsCell, isSummary: Boolean) {
        when (cell) {
            is XlsCell.Text -> {
                val styleAttr = if (isSummary) """ ss:StyleID="TotalText"""" else ""
                val safeText = SpreadsheetSanitizer.sanitizeFormulaText(cell.value)
                sb.appendLine("""        <Cell$styleAttr><Data ss:Type="String">${escapeXml(safeText)}</Data></Cell>""")
            }
            is XlsCell.Number -> {
                val safeNum = if (cell.value.isFinite()) cell.value else 0.0
                val styleId = when {
                    isSummary -> "TotalNum"
                    cell.isInteger -> "Int"
                    else -> "Num"
                }
                val formatted = if (cell.isInteger) {
                    safeNum.toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", safeNum)
                }
                sb.appendLine("""        <Cell ss:StyleID="$styleId"><Data ss:Type="Number">$formatted</Data></Cell>""")
            }
        }
    }

    private fun uniqueSheetName(raw: String, used: MutableSet<String>, index: Int): String {
        val cleaned = raw
            .replace(Regex("[\\\\/?*\\[\\]:]"), " ")
            .trim()
            .ifBlank { "Sheet${index + 1}" }
            .take(31)
        var candidate = cleaned
        var suffix = 2
        while (!used.add(candidate)) {
            val suffixStr = " ($suffix)"
            candidate = cleaned.take(31 - suffixStr.length) + suffixStr
            suffix++
        }
        return candidate
    }

    private fun escapeXml(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun timestampSuffix(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /**
     * Saves an .xls file directly to the device Downloads collection (zero permissions on Android 10+).
     * Returns the user-visible location string on success.
     */
    fun saveXlsToDownloads(
        context: Context,
        filePrefix: String,
        xmlContent: String
    ): Result<String> {
        return try {
            val safePrefix = filePrefix.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = "${safePrefix}_${timestampSuffix()}.xls"
            val bytes = xmlContent.toByteArray(Charsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, XLS_MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("Could not create file in Downloads"))
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: return Result.failure(IllegalStateException("Could not open Downloads output stream"))
                Result.success("Downloads/$fileName")
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.cacheDir, "exports").apply { mkdirs() }
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                outFile.writeBytes(bytes)
                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Writes an .xls file into cache/exports and opens the Android Share Sheet via FileProvider.
     */
    fun shareXlsFile(
        context: Context,
        filePrefix: String,
        xmlContent: String,
        shareTitle: String
    ): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safePrefix = filePrefix.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val xlsFile = File(exportDir, "${safePrefix}_${timestampSuffix()}.xls")
            xlsFile.writeText(xmlContent, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                xlsFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = XLS_MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Result.success(xlsFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
