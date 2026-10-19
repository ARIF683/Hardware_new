package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ColorTintLog
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.QuotationRecord
import com.example.data.model.GstBillRecord
import com.example.data.model.SupplierPurchaseRecord
import com.example.data.model.SyncQueueItem
import com.example.data.model.TransactionRecord

@Database(
    entities = [
        Item::class,
        TransactionRecord::class,
        SyncQueueItem::class,
        QuotationRecord::class,
        LedgerAccount::class,
        LedgerEntry::class,
        DailyCashflowRecord::class,
        ColorTintLog::class,
        GstBillRecord::class,
        SupplierPurchaseRecord::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun quotationDao(): QuotationDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun dailyCashflowDao(): DailyCashflowDao
    abstract fun colorTintDao(): ColorTintDao
    abstract fun gstBillDao(): GstBillDao
    abstract fun supplierPurchaseDao(): SupplierPurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN image_url TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS color_tint_logs (
                        tint_record_id TEXT PRIMARY KEY NOT NULL,
                        product_name TEXT NOT NULL,
                        shade_name TEXT NOT NULL,
                        shade_code TEXT NOT NULL,
                        base_code TEXT NOT NULL,
                        can_factor TEXT NOT NULL,
                        liters REAL NOT NULL,
                        no_of_cans INTEGER NOT NULL,
                        tint_date TEXT NOT NULL,
                        tint_time TEXT NOT NULL,
                        tint_timestamp TEXT NOT NULL,
                        matched_item_id TEXT,
                        matched_item_name TEXT,
                        qty_deducted REAL NOT NULL,
                        processed_at TEXT NOT NULL,
                        colorant_used TEXT NOT NULL,
                        colorant_quantity TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gst_bills (
                        id TEXT PRIMARY KEY NOT NULL,
                        invoice_no TEXT NOT NULL,
                        invoice_date TEXT NOT NULL,
                        customer_name TEXT NOT NULL,
                        customer_phone TEXT NOT NULL,
                        customer_gstin TEXT NOT NULL,
                        customer_address TEXT NOT NULL,
                        store_gstin TEXT NOT NULL,
                        is_inter_state INTEGER NOT NULL,
                        items_json TEXT NOT NULL,
                        taxable_total REAL NOT NULL,
                        total_tax_amount REAL NOT NULL,
                        grand_total REAL NOT NULL,
                        transporter_id TEXT NOT NULL,
                        vehicle_no TEXT NOT NULL,
                        quotation_id TEXT,
                        quotation_no TEXT,
                        pdf_path TEXT,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supplier_purchases (
                        id TEXT PRIMARY KEY NOT NULL,
                        item_id TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        supplier_name TEXT NOT NULL,
                        supplier_phone TEXT NOT NULL,
                        invoice_no TEXT NOT NULL,
                        purchase_date TEXT NOT NULL,
                        qty REAL NOT NULL,
                        unit TEXT NOT NULL,
                        rate REAL NOT NULL,
                        mrp REAL,
                        discount_pct REAL NOT NULL,
                        tax_pct REAL NOT NULL,
                        total_amount REAL NOT NULL,
                        payment_status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                try { db.execSQL("ALTER TABLE items ADD COLUMN nfc_tag_id TEXT") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE items ADD COLUMN shelf_location TEXT") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE items ADD COLUMN secondary_unit TEXT") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE items ADD COLUMN conversion_factor REAL NOT NULL DEFAULT 1.0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE items ADD COLUMN secondary_price REAL NOT NULL DEFAULT 0.0") } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stock_manager.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
