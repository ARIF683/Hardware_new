package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.SyncQueueItem
import com.example.data.remote.SupabaseClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object DailyReminderScheduler {
    private const val TAG = "DailyReminderScheduler"
    private const val PREFS_NAME = "daily_reminder_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_FIRED_DATE = "last_fired_date"
    private const val KEY_PERMISSION_PROMPTED = "permission_prompted"

    const val CHANNEL_ID = "daily_sales_expense_reminder"
    const val NOTIFICATION_ID = 7001
    const val REQUEST_CODE_ALARM = 7001
    const val REQUEST_CODE_OPEN_APP = 7002
    const val REQUEST_CODE_QUICK_SALE = 7003
    const val REQUEST_CODE_QUICK_EXPENSE = 7004

    const val ACTION_DAILY_REMINDER = "com.example.ACTION_DAILY_SALES_REMINDER"
    const val ACTION_QUICK_ADD_SALE = "com.example.ACTION_QUICK_ADD_SALE"
    const val ACTION_QUICK_ADD_EXPENSE = "com.example.ACTION_QUICK_ADD_EXPENSE"

    const val KEY_SALE_INPUT = "key_quick_sale_input"
    const val KEY_EXPENSE_INPUT = "key_quick_expense_input"

    const val EXTRA_OPEN_ROUTE = "open_route"
    const val ROUTE_DAILY_CASHFLOW = "daily_sales_expenses"

    // Target 7:05 PM local time so system Doze batching lands reliably inside 7:00 - 7:30 PM.
    const val TARGET_HOUR = 19
    const val TARGET_MINUTE = 5

    private val moshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }
    private val cashflowListAdapter by lazy {
        moshi.adapter<List<DailyCashflowRecord>>(
            Types.newParameterizedType(List::class.java, DailyCashflowRecord::class.java)
        ).serializeNulls()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleNext(context)
        } else {
            cancel(context)
        }
    }

    fun wasPermissionPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMISSION_PROMPTED, false)

    fun markPermissionPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.daily_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.daily_reminder_channel_desc)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    internal fun computeNextTriggerMillis(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, TARGET_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildReplyPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context.applicationContext, DailyReminderReceiver::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            flags
        )
    }

    fun ensureScheduled(context: Context) {
        ensureNotificationChannel(context)
        if (isEnabled(context)) {
            scheduleNext(context)
        }
    }

    fun scheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()): Long? {
        ensureNotificationChannel(context)
        if (!isEnabled(context)) {
            cancel(context)
            return null
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val triggerAt = computeNextTriggerMillis(nowMillis)
        val pendingIntent = buildAlarmPendingIntent(context)

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
            Log.d(TAG, "Scheduled daily sales & expenses reminder for ${Date(triggerAt)}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling daily reminder alarm", e)
        }
        return triggerAt
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildAlarmPendingIntent(context))
    }

    internal fun todayKey(nowMillis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))

    internal fun parseQuickCashflowInput(
        rawText: String,
        type: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyCashflowRecord? {
        val normalizedType = if (type.uppercase(Locale.US) == "EXPENSE") "EXPENSE" else "SALE"
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return null

        // Extract first numeric amount (handles commas, decimals, currency prefixes like ₹ or Rs.)
        val amountRegex = Regex("""(?:₹|rs\.?\s*)?(\d[\d,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(cleaned) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return null

        var remainder = (cleaned.substring(0, match.range.first) + " " + cleaned.substring(match.range.last + 1))
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', ':', '|', ',')
            .trim()

        val lower = remainder.lowercase(Locale.US)
        val paymentMode = when {
            Regex("""\b(upi|gpay|phonepe|paytm)\b""").containsMatchIn(lower) -> "UPI"
            Regex("""\b(card|debit|credit)\b""").containsMatchIn(lower) -> "Card"
            Regex("""\b(bank|neft|rtgs|imps|transfer)\b""").containsMatchIn(lower) -> "Bank"
            else -> "Cash"
        }

        // Strip standalone payment keywords from title if descriptive text exists alongside them
        val strippedNote = remainder
            .replace(Regex("""\b(upi|gpay|phonepe|paytm|cash|card|bank)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val defaultTitle = if (normalizedType == "SALE") "Counter Sale" else "General Expense"
        val resolvedTitle = strippedNote.ifBlank { defaultTitle }.take(80)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val createdAtStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(nowMillis))

        return DailyCashflowRecord(
            id = UUID.randomUUID().toString(),
            type = normalizedType,
            amount = amount,
            title = resolvedTitle,
            category = resolvedTitle,
            paymentMode = paymentMode,
            date = dateStr,
            note = strippedNote,
            createdAt = createdAtStr
        )
    }

    private fun formatInr(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
        nf.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        return "₹${nf.format(amount)}"
    }

    suspend fun handleQuickAddFromNotification(
        context: Context,
        type: String,
        rawInput: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyCashflowRecord? {
        val parsed = parseQuickCashflowInput(rawInput, type, nowMillis)
        if (parsed == null) {
            postOrUpdateNotification(
                context = context,
                title = "Could not read amount",
                shortText = "Example: 1500 or 1500 Paint (or 450 Transport)",
                expandedText = "Try typing a valid amount like '2500' or '2500 Paint' inside + Add Sale / + Add Expense.",
                silentUpdate = true
            )
            return null
        }

        val appContext = context.applicationContext
        val db = AppDatabase.getDatabase(appContext)
        db.dailyCashflowDao().insert(parsed)

        val payloadJson = cashflowListAdapter.toJson(listOf(parsed))
        val queueId = db.syncQueueDao().insert(
            SyncQueueItem(
                type = "upsertCashflow",
                payloadJson = payloadJson,
                timestamp = nowMillis
            )
        )

        var syncedCloud = false
        try {
            val authManager = AuthManager(appContext)
            val client = SupabaseClient().apply {
                configureAuth(
                    accessTokenProvider = { authManager.activeAccessToken() },
                    onRefreshSession = { authManager.refreshSessionBlocking() }
                )
            }
            if (client.isConfigured()) {
                client.upsertCashflow(listOf(parsed))
                db.syncQueueDao().deleteById(queueId)
                syncedCloud = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background cloud sync deferred for quick cashflow entry", e)
        }

        val kindLabel = if (parsed.type == "SALE") "Sale" else "Expense"
        val formattedAmt = formatInr(parsed.amount)
        val syncLabel = if (syncedCloud) "Synced to cloud" else "Saved locally (syncs automatically)"
        postOrUpdateNotification(
            context = appContext,
            title = "✓ Saved $formattedAmt $kindLabel",
            shortText = "${parsed.title} (${parsed.paymentMode}) • Add another below",
            expandedText = "✓ Recorded $formattedAmt $kindLabel — ${parsed.title} (${parsed.paymentMode}).\n$syncLabel. Use + Add Sale or + Add Expense below to add more.",
            silentUpdate = true
        )
        return parsed
    }

    fun showReminderNotification(
        context: Context,
        forceTest: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!forceTest && !isEnabled(context)) return false
        ensureNotificationChannel(context)

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing; skipping reminder notification")
            return false
        }

        val today = todayKey(nowMillis)
        if (!forceTest) {
            val lastFired = prefs(context).getString(KEY_LAST_FIRED_DATE, null)
            if (lastFired == today) {
                Log.d(TAG, "Daily reminder already shown today ($today)")
                return false
            }
            prefs(context).edit().putString(KEY_LAST_FIRED_DATE, today).apply()
        }

        return postOrUpdateNotification(
            context = context,
            title = context.getString(R.string.daily_reminder_title),
            shortText = context.getString(R.string.daily_reminder_body),
            expandedText = context.getString(R.string.daily_reminder_expanded),
            silentUpdate = false
        )
    }

    private fun postOrUpdateNotification(
        context: Context,
        title: String,
        shortText: String,
        expandedText: String,
        silentUpdate: Boolean
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val tapIntent = Intent(context.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_DAILY_CASHFLOW)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context.applicationContext,
            REQUEST_CODE_OPEN_APP,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val saleRemoteInput = RemoteInput.Builder(KEY_SALE_INPUT)
            .setLabel(context.getString(R.string.daily_reminder_hint_sale))
            .build()
        val saleAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_reminder,
            context.getString(R.string.daily_reminder_action_sale),
            buildReplyPendingIntent(context, ACTION_QUICK_ADD_SALE, REQUEST_CODE_QUICK_SALE)
        )
            .addRemoteInput(saleRemoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val expenseRemoteInput = RemoteInput.Builder(KEY_EXPENSE_INPUT)
            .setLabel(context.getString(R.string.daily_reminder_hint_expense))
            .build()
        val expenseAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_reminder,
            context.getString(R.string.daily_reminder_action_expense),
            buildReplyPendingIntent(context, ACTION_QUICK_ADD_EXPENSE, REQUEST_CODE_QUICK_EXPENSE)
        )
            .addRemoteInput(expenseRemoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(silentUpdate)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(saleAction)
            .addAction(expenseAction)

        if (!silentUpdate) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        } else {
            builder.setSilent(true)
        }

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied when posting reminder", e)
            false
        }
    }
}
