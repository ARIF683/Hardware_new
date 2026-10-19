package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@Entity(tableName = "supplier_purchases")
@JsonClass(generateAdapter = true)
data class SupplierPurchaseRecord(
    @PrimaryKey
    @ColumnInfo(name = "id")
    @Json(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "item_id")
    @Json(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "item_name")
    @Json(name = "item_name")
    val itemName: String,

    @ColumnInfo(name = "supplier_name")
    @Json(name = "supplier_name")
    val supplierName: String,

    @ColumnInfo(name = "supplier_phone")
    @Json(name = "supplier_phone")
    val supplierPhone: String = "",

    @ColumnInfo(name = "invoice_no")
    @Json(name = "invoice_no")
    val invoiceNo: String = "",

    @ColumnInfo(name = "purchase_date")
    @Json(name = "purchase_date")
    val purchaseDate: String, // YYYY-MM-DD

    @ColumnInfo(name = "qty")
    @Json(name = "qty")
    val qty: Double = 1.0,

    @ColumnInfo(name = "unit")
    @Json(name = "unit")
    val unit: String = "pcs",

    @ColumnInfo(name = "rate")
    @Json(name = "rate")
    val rate: Double, // Purchase price / quotation per unit

    @ColumnInfo(name = "mrp")
    @Json(name = "mrp")
    val mrp: Double? = null,

    @ColumnInfo(name = "discount_pct")
    @Json(name = "discount_pct")
    val discountPct: Double = 0.0,

    @ColumnInfo(name = "tax_pct")
    @Json(name = "tax_pct")
    val taxPct: Double = 0.0,

    @ColumnInfo(name = "total_amount")
    @Json(name = "total_amount")
    val totalAmount: Double = 0.0,

    @ColumnInfo(name = "payment_status")
    @Json(name = "payment_status")
    val paymentStatus: String = "Paid", // "Paid", "Pending", "Credit"

    @ColumnInfo(name = "notes")
    @Json(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "created_at")
    @Json(name = "created_at")
    val createdAt: String = ""
)
