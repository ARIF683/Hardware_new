package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DailyReminderScheduler.ACTION_DAILY_REMINDER -> {
                DailyReminderScheduler.showReminderNotification(context)
                DailyReminderScheduler.scheduleNext(context)
            }
            DailyReminderScheduler.ACTION_QUICK_ADD_SALE -> {
                val input = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(DailyReminderScheduler.KEY_SALE_INPUT)
                    ?.toString()
                    .orEmpty()
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        DailyReminderScheduler.handleQuickAddFromNotification(context, "SALE", input)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            DailyReminderScheduler.ACTION_QUICK_ADD_EXPENSE -> {
                val input = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(DailyReminderScheduler.KEY_EXPENSE_INPUT)
                    ?.toString()
                    .orEmpty()
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        DailyReminderScheduler.handleQuickAddFromNotification(context, "EXPENSE", input)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                DailyReminderScheduler.ensureScheduled(context)
            }
        }
    }
}
