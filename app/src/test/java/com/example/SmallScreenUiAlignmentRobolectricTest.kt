package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.data.model.Item
import com.example.data.model.QuotationLineItem
import com.example.data.model.QuotationRecord
import com.example.ui.screens.QuotationFormDialog
import com.example.ui.theme.StockManagerTheme
import com.example.util.InvoicePrintManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h569dp-xhdpi")
class SmallScreenUiAlignmentRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testTagStartsWith(prefix: String): SemanticsMatcher =
        SemanticsMatcher("TestTag starts with $prefix") { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            tag != null && tag.startsWith(prefix)
        }

    @Test
    fun `quotation form qty unit and rate boxes stay aligned on 320dp small screen`() {
        val sampleLineItems = listOf(
            QuotationLineItem(
                itemId = "item-1",
                name = "DISTAMPER INDIGO 20KG",
                code = "D-20",
                type = "Paints",
                unit = "pcs",
                qty = 1.0,
                unitPrice = 1100.0,
                total = 1100.0,
                costPrice = 900.0
            )
        )
        val existingQuote = QuotationRecord(
            id = "q-1092",
            quotationNo = "EST-1092",
            customerName = "TARIK BHAIYA",
            customerPhone = "9876543210",
            date = "2026-10-18",
            itemsJson = InvoicePrintManager.toJson(sampleLineItems),
            subtotal = 1100.0,
            grandTotal = 1100.0,
            status = "Draft",
            createdAt = "2026-10-18T10:00:00Z"
        )
        val catalog = listOf(
            Item(
                id = "item-1",
                o = 1,
                code = "D-20",
                name = "DISTAMPER INDIGO 20KG",
                cost = 900.0,
                price = 1100.0,
                type = "Paints",
                brand = "Indigo",
                size = "20KG",
                unit = "pcs",
                qty = 10.0
            )
        )

        composeRule.setContent {
            StockManagerTheme(darkTheme = true) {
                QuotationFormDialog(
                    existingQuotation = existingQuote,
                    availableItems = catalog,
                    onDismiss = {},
                    onSave = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quotation_form_list").performScrollToIndex(4)
        composeRule.waitForIdle()

        val qtyNode = composeRule.onNode(testTagStartsWith("quote_item_qty_"), useUnmergedTree = true)
        val unitNode = composeRule.onNode(testTagStartsWith("quote_item_unit_"), useUnmergedTree = true)
        val rateNode = composeRule.onNode(testTagStartsWith("quote_item_rate_"), useUnmergedTree = true)

        qtyNode.performScrollTo().assertIsDisplayed()
        unitNode.performScrollTo().assertIsDisplayed()
        rateNode.performScrollTo().assertIsDisplayed()

        composeRule.waitForIdle()

        val qtyBounds = qtyNode.fetchSemanticsNode().boundsInRoot
        val unitBounds = unitNode.fetchSemanticsNode().boundsInRoot
        val rateBounds = rateNode.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected Qty (${qtyBounds.top}) and Unit (${unitBounds.top}) boxes to align on the same line",
            abs(qtyBounds.top - unitBounds.top) <= 2f
        )
        assertTrue(
            "Expected Qty (${qtyBounds.top}) and Rate (${rateBounds.top}) boxes to align on the same line",
            abs(qtyBounds.top - rateBounds.top) <= 2f
        )
        assertTrue(
            "Expected Qty (${qtyBounds.height}) and Unit (${unitBounds.height}) heights to match",
            abs(qtyBounds.height - unitBounds.height) <= 2f
        )
        assertTrue(
            "Expected Qty (${qtyBounds.height}) and Rate (${rateBounds.height}) heights to match",
            abs(qtyBounds.height - rateBounds.height) <= 2f
        )
        assertTrue("Expected Qty box width (${qtyBounds.width}) >= 60px", qtyBounds.width >= 60f)
        assertTrue("Expected Unit box width (${unitBounds.width}) >= 60px", unitBounds.width >= 60f)
        assertTrue("Expected Rate box width (${rateBounds.width}) >= 60px", rateBounds.width >= 60f)
    }
}
