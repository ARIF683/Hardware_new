package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Entity(tableName = "items")
@JsonClass(generateAdapter = true)
data class Item(
    @PrimaryKey
    @Json(name = "id")
    val id: String,
    @ColumnInfo(name = "o")
    @Json(name = "o")
    val o: Int = 0,
    @ColumnInfo(name = "name")
    @Json(name = "name")
    val name: String,
    @ColumnInfo(name = "code")
    @Json(name = "code")
    val code: String = "",
    @ColumnInfo(name = "barcode")
    @Json(name = "barcode")
    val barcode: String = "",
    @ColumnInfo(name = "type")
    @Json(name = "type")
    val type: String = "",
    @ColumnInfo(name = "brand")
    @Json(name = "brand")
    val brand: String = "",
    @ColumnInfo(name = "size")
    @Json(name = "size")
    val size: String = "",
    @ColumnInfo(name = "aliases")
    @Json(name = "aliases")
    val aliases: String = "",
    @ColumnInfo(name = "mrp")
    @Json(name = "mrp")
    val mrp: Double? = null,
    @ColumnInfo(name = "cost")
    @Json(name = "cost")
    val cost: Double = 0.0,
    @ColumnInfo(name = "price")
    @Json(name = "price")
    val price: Double = 0.0,
    @ColumnInfo(name = "qty")
    @Json(name = "qty")
    val qty: Double = 0.0,
    @ColumnInfo(name = "low")
    @Json(name = "low")
    val low: Double = 0.0,
    @ColumnInfo(name = "unit")
    @Json(name = "unit")
    val unit: String = "pcs",
    @ColumnInfo(name = "image_url")
    @Json(name = "image_url")
    val imageUrl: String? = null,
    @ColumnInfo(name = "secondary_unit")
    @Json(name = "secondary_unit")
    val secondaryUnit: String = "",
    @ColumnInfo(name = "conversion_factor")
    @Json(name = "conversion_factor")
    val conversionFactor: Double = 1.0,
    @ColumnInfo(name = "secondary_price")
    @Json(name = "secondary_price")
    val secondaryPrice: Double = 0.0,
    @ColumnInfo(name = "shelf_location")
    @Json(name = "shelf_location")
    val shelfLocation: String = "",
    @ColumnInfo(name = "nfc_tag_id")
    @Json(name = "nfc_tag_id")
    val nfcTagId: String = "",
    @ColumnInfo(name = "updated_at")
    @Json(name = "updated_at")
    val updatedAt: String = ""
)
