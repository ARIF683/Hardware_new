package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.notifications.DailyReminderReceiver
import com.example.notifications.DailyReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyReminderSchedulerTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daily_reminder_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `computeNextTriggerMillis schedules today 19_00 when before evening window`() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 23, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerMillis = DailyReminderScheduler.computeNextTriggerMillis(now.timeInMillis, tz)
        val triggerCal = Calendar.getInstance(tz).apply { timeInMillis = triggerMillis }

        assertEquals(23, triggerCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertEquals(0, triggerCal.get(Calendar.SECOND))
        assertEquals(30L * 60L * 1000L, DailyReminderScheduler.WINDOW_LENGTH_MILLIS)
    }

    @Test
    fun `computeNextTriggerMillis schedules tomorrow 19_00 when already inside or past evening window`() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 23, 19, 10, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerMillis = DailyReminderScheduler.computeNextTriggerMillis(now.timeInMillis, tz)
        val triggerCal = Calendar.getInstance(tz).apply { timeInMillis = triggerMillis }

        assertEquals(24, triggerCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun `scheduleNext registers alarm, ensureScheduled avoids redundant reschedule, and cancel clears it`() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)

        DailyReminderScheduler.ensureScheduled(context)
        val firstAlarm = shadowAlarm.peekNextScheduledAlarm()
        assertNotNull(firstAlarm)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)

        // Calling ensureScheduled again while alarm is active must not re-schedule redundantly
        DailyReminderScheduler.ensureScheduled(context)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)

        DailyReminderScheduler.setEnabled(context, false)
        assertFalse(DailyReminderScheduler.isEnabled(context))
        assertNull(shadowAlarm.peekNextScheduledAlarm())
    }

    @Test
    fun `receiver posts sales and expenses notification with inline RemoteInput actions`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)

        val receiver = DailyReminderReceiver()
        receiver.onReceive(context, Intent(DailyReminderScheduler.ACTION_DAILY_REMINDER))

        val posted = shadowNotificationManager.allNotifications
        assertEquals(1, posted.size)
        val notification = posted[0]
        assertNotNull(notification.smallIcon)
        assertNotNull(notification.getLargeIcon())
        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty()
        assertTrue(title.contains("Sales", ignoreCase = true))

        // Verify both RemoteInput actions (+ Add Sale & + Add Expense) are attached
        val actionCount = NotificationCompat.getActionCount(notification)
        assertEquals(2, actionCount)
        val saleAction = NotificationCompat.getAction(notification, 0)
        val expenseAction = NotificationCompat.getAction(notification, 1)
        assertNotNull(saleAction?.remoteInputs?.firstOrNull())
        assertNotNull(expenseAction?.remoteInputs?.firstOrNull())
        assertEquals(DailyReminderScheduler.KEY_SALE_INPUT, saleAction?.remoteInputs?.firstOrNull()?.resultKey)
        assertEquals(DailyReminderScheduler.KEY_EXPENSE_INPUT, expenseAction?.remoteInputs?.firstOrNull()?.resultKey)
        assertNotNull(shadowAlarm.peekNextScheduledAlarm())

        // Second broadcast on the same day should not post a duplicate popup
        receiver.onReceive(context, Intent(DailyReminderScheduler.ACTION_DAILY_REMINDER))
        assertEquals(1, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `parseQuickCashflowInput parses amount, note, and payment mode cleanly`() {
        val simpleSale = DailyReminderScheduler.parseQuickCashflowInput("2500", "SALE")
        assertNotNull(simpleSale)
        assertEquals("SALE", simpleSale!!.type)
        assertEquals(2500.0, simpleSale.amount, 0.001)
        assertEquals("Counter Sale", simpleSale.title)
        assertEquals("Cash", simpleSale.paymentMode)

        val upiSale = DailyReminderScheduler.parseQuickCashflowInput("₹1,250.50 Paint UPI", "SALE")
        assertNotNull(upiSale)
        assertEquals(1250.50, upiSale!!.amount, 0.001)
        assertEquals("Paint", upiSale.title)
        assertEquals("UPI", upiSale.paymentMode)

        val expense = DailyReminderScheduler.parseQuickCashflowInput("Tea & Auto 320", "EXPENSE")
        assertNotNull(expense)
        assertEquals("EXPENSE", expense!!.type)
        assertEquals(320.0, expense.amount, 0.001)
        assertEquals("Tea & Auto", expense.title)

        assertNull(DailyReminderScheduler.parseQuickCashflowInput("no numbers here", "SALE"))
        assertNull(DailyReminderScheduler.parseQuickCashflowInput("0", "EXPENSE"))
    }

    @Test
    fun `handleQuickAddFromNotification persists record to Room and updates notification`() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)

        val saved = DailyReminderScheduler.handleQuickAddFromNotification(
            context = context,
            type = "EXPENSE",
            rawInput = "450 Transport UPI"
        )
        assertNotNull(saved)
        assertEquals("EXPENSE", saved!!.type)
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("Transport", saved.title)
        assertEquals("UPI", saved.paymentMode)

        val db = AppDatabase.getDatabase(context)
        val records = db.dailyCashflowDao().getAllCashflowList()
        assertTrue(records.any { it.id == saved.id && it.amount == 450.0 })

        val posted = shadowNotificationManager.allNotifications
        assertEquals(1, posted.size)
        val title = posted[0].extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty()
        assertTrue(title.contains("Saved", ignoreCase = true) && title.contains("Expense", ignoreCase = true))
    }

    @Test
    fun `receiver reschedules alarm on device boot when enabled`() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)
        assertNull(shadowAlarm.peekNextScheduledAlarm())

        DailyReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(shadowAlarm.peekNextScheduledAlarm())
    }
}
