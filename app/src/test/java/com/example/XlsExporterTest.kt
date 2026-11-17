package com.example

import com.example.data.model.DailyCashflowRecord
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.TransactionRecord
import com.example.util.SpreadsheetReportParser
import com.example.util.XlsExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsExporterTest {

    @Test
    fun `buildInventoryWorkbookXml generates valid Excel XML with totals and escaping`() {
        val items = listOf(
            Item(
                id = "1",
                code = "SKU-001",
                barcode = "890123456",
                name = "Anchor Bolt & Nut <M10>",
                brand = "Fischer",
                type = "Fastener",
                size = "M10",
                unit = "pcs",
                qty = 10.0,
                low = 2.0,
                cost = 15.0,
                price = 25.0,
                mrp = 30.0
            )
        )

        val xml = XlsExporter.buildInventoryWorkbookXml(items)
        assertTrue(xml.contains("""<?mso-application progid="Excel.Sheet"?>"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="Inventory">"""))
        assertTrue(xml.contains("Anchor Bolt &amp; Nut &lt;M10&gt;"))
        assertFalse(xml.contains("Anchor Bolt & Nut <M10>"))
        assertTrue(xml.contains("150.00"))
        assertTrue(xml.contains("250.00"))
        assertTrue(xml.contains("TOTAL (1 SKUs)"))

        val parsed = SpreadsheetReportParser.parse(xml.byteInputStream())
        assertEquals("xls_html_xml", parsed.detectedFormat)
        assertTrue(parsed.rows.isNotEmpty())
        assertEquals("SKU", parsed.rows[0][0])
        assertEquals("SKU-001", parsed.rows[1][0])
    }

    @Test
    fun `buildFullBusinessWorkbookXml creates all five sheets safely`() {
        val xml = XlsExporter.buildFullBusinessWorkbookXml(
            items = listOf(Item(id = "i1", name = "Paint", qty = 4.0, cost = 100.0, price = 140.0)),
            cashflow = listOf(
                DailyCashflowRecord(
                    id = "c1",
                    type = "SALE",
                    amount = 500.0,
                    title = "Counter Sale",
                    category = "Sales",
                    date = "2026-11-17",
                    createdAt = "2026-11-17T10:00:00Z"
                )
            ),
            stockMovements = listOf(
                TransactionRecord(
                    clientId = "t1",
                    itemId = "i1",
                    itemName = "Paint",
                    action = "out",
                    qty = 2.0,
                    balance = 4.0,
                    createdAt = "2026-11-17T10:00:00Z"
                )
            ),
            gstBills = listOf(
                GstBillRecord(
                    id = "g1",
                    invoiceNo = "BILL-101",
                    invoiceDate = "17/11/2026",
                    customerName = "Ramesh Traders",
                    itemsJson = "[]",
                    taxableTotal = 1000.0,
                    totalTaxAmount = 180.0,
                    grandTotal = 1180.0,
                    createdAt = "2026-11-17T10:00:00Z"
                )
            ),
            ledgerAccounts = listOf(
                LedgerAccount(
                    id = "l1",
                    name = "Ramesh Traders",
                    type = "CUSTOMER",
                    netBalance = 1180.0,
                    createdAt = "2026-11-17T10:00:00Z",
                    updatedAt = "2026-11-17T10:00:00Z"
                )
            ),
            storeName = "S.A. HARDWARE"
        )

        assertTrue(xml.contains("""<Worksheet ss:Name="Inventory">"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="Cashflow &amp; P&amp;L">"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="Stock Movements">"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="GST Bills">"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="Khata Accounts">"""))
        assertTrue(xml.contains("BILL-101"))
        assertTrue(xml.contains("S.A. HARDWARE"))
    }
}
