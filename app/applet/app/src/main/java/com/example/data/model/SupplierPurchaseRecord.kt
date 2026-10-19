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
    @ColumnInfo(name = "bill_no")
    @Json(name = "bill_no")
    val billNo: String = "",
    @ColumnInfo(name = "purchase_date")
    @Json(name = "purchase_date")
    val purchaseDate: String,
    @ColumnInfo(name = "rate")
    @Json(name = "rate")
    val rate: Double,
    @ColumnInfo(name = "qty")
    @Json(name = "qty")
    val qty: Double = 1.0,
    @ColumnInfo(name = "unit")
    @Json(name = "unit")
    val unit: String = "pcs",
    @ColumnInfo(name = "notes")
    @Json(name = "notes")
    val notes: String = ""
)
