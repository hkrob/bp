package com.robcloud.bloodpressure.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.R
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.bpCategory
import com.robcloud.bloodpressure.reminders.appLaunchIntent
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.history.categoryColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val WIDGET_REQUEST_CODE = 2
private const val WIDGET_TEXT_WHITE = 0xFFFFFFFF.toInt()

/**
 * Text color for the widget's BP line. NORMAL stays white — it reads fine against the widget's
 * dark teal background, unlike the darker green [categoryColor] uses for it elsewhere — every
 * other category gets its usual status color so an out-of-range reading stands out here too.
 */
internal fun widgetBpColor(category: BpCategory): Int =
    if (category == BpCategory.NORMAL) WIDGET_TEXT_WHITE else categoryColor(category).toArgb()

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
                views.setTextColor(R.id.widget_bp, WIDGET_TEXT_WHITE)
                views.setViewVisibility(R.id.widget_irr, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_bp, "${reading.systolicMmHg}/${reading.diastolicMmHg}")
                views.setTextViewText(
                    R.id.widget_subtext,
                    "${reading.heartRateBpm} bpm · ${Formatters.dateTime(reading.takenAt)}"
                )
                // Status colors flag out-of-range values everywhere else (table, log, PDF) — the
                // widget is the most ambient, frequently-glanced-at surface and had neither this
                // nor the irregular-heartbeat mark.
                views.setTextColor(R.id.widget_bp, widgetBpColor(reading.bpCategory()))
                views.setViewVisibility(R.id.widget_irr, if (reading.irregularHeartbeat) View.VISIBLE else View.GONE)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, WIDGET_REQUEST_CODE, appLaunchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
