package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GstBillLineItem(
    val id: String = "",
    val name: String,
    val hsn: String = "",
    val qty: Double = 1.0,
    val rate: Double = 0.0,
    val taxRatePercent: Double = 18.0,
    val taxableAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val unit: String = "pcs",
    val subtitle: String = "",
    val imageUrl: String? = null
)

@Entity(tableName = "gst_bills")
@JsonClass(generateAdapter = true)
data class GstBillRecord(
    @PrimaryKey
    @ColumnInfo(name = "id")
    @Json(name = "id")
    val id: String,

    @ColumnInfo(name = "invoice_no")
    @Json(name = "invoice_no")
    val invoiceNo: String,

    @ColumnInfo(name = "invoice_date")
    @Json(name = "invoice_date")
    val invoiceDate: String,

    @ColumnInfo(name = "customer_name")
    @Json(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "customer_phone")
    @Json(name = "customer_phone")
    val customerPhone: String = "",

    @ColumnInfo(name = "customer_gstin")
    @Json(name = "customer_gstin")
    val customerGstin: String = "",

    @ColumnInfo(name = "customer_address")
    @Json(name = "customer_address")
    val customerAddress: String = "",

    @ColumnInfo(name = "store_gstin")
    @Json(name = "store_gstin")
    val storeGstin: String = "",

    @ColumnInfo(name = "is_inter_state")
    @Json(name = "is_inter_state")
    val isInterState: Boolean = false,

    @ColumnInfo(name = "items_json")
    @Json(name = "items_json")
    val itemsJson: String,

    @ColumnInfo(name = "taxable_total")
    @Json(name = "taxable_total")
    val taxableTotal: Double,

    @ColumnInfo(name = "total_tax_amount")
    @Json(name = "total_tax_amount")
    val totalTaxAmount: Double,

    @ColumnInfo(name = "grand_total")
    @Json(name = "grand_total")
    val grandTotal: Double,

    @ColumnInfo(name = "transporter_id")
    @Json(name = "transporter_id")
    val transporterId: String = "",

    @ColumnInfo(name = "vehicle_no")
    @Json(name = "vehicle_no")
    val vehicleNo: String = "",

    @ColumnInfo(name = "quotation_id")
    @Json(name = "quotation_id")
    val quotationId: String? = null,

    @ColumnInfo(name = "quotation_no")
    @Json(name = "quotation_no")
    val quotationNo: String? = null,

    @ColumnInfo(name = "pdf_path")
    @Json(name = "pdf_path")
    val pdfPath: String? = null,

    @ColumnInfo(name = "status")
    @Json(name = "status")
    val status: String = "Generated", // Generated, Paid, Cancelled

    @ColumnInfo(name = "created_at")
    @Json(name = "created_at")
    val createdAt: String
)
