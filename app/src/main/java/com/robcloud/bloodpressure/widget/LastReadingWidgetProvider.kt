package com.robcloud.bloodpressure.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.MainActivity
import com.robcloud.bloodpressure.R
import com.robcloud.bloodpressure.ui.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen widget showing the most recent reading. Tapping it opens the app. */
class LastReadingWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                refresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Call after any local data change (save/edit/delete/import) so the widget stays current. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LastReadingWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val app = context.applicationContext as BloodPressureApp
            val reading = app.database.readingDao().getLatest()

            val views = RemoteViews(context.packageName, R.layout.widget_last_reading)
            if (reading == null) {
                views.setTextViewText(R.id.widget_bp, "No readings yet")
                views.setTextViewText(R.id.widget_subtext, "Open BP Tracker to add one")
            } else {
                views.setTextViewText(R.id.widget_bp, "${reading.systolicMmHg}/${reading.diastolicMmHg}")
                views.setTextViewText(
                    R.id.widget_subtext,
                    "${reading.heartRateBpm} bpm · ${Formatters.dateTime(reading.takenAt)}"
                )
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
