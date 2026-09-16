package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.buaa.hzcampus.model.dto.PlanTask
import java.text.SimpleDateFormat
import java.util.Locale

private const val PLAN_CHANNEL_ID = "plan_reminder"
private const val PLAN_REQUEST_CODE_BASE = 4000

actual fun schedulePlanReminders(tasks: List<PlanTask>) {
  val context = AppContextHolder.context ?: return
  runCatching {
    cancelPlanReminders()
    createChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    tasks.forEachIndexed { index, task ->
      val remindAt = parsePlanReminderMillis(task.date, task.reminderAt) ?: return@forEachIndexed
      if (remindAt <= now) return@forEachIndexed
      val intent =
          Intent(context, PlanReminderReceiver::class.java).apply {
            putExtra("title", task.title)
          }
      val pi =
          PendingIntent.getBroadcast(
              context,
              PLAN_REQUEST_CODE_BASE + index,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
      } catch (_: SecurityException) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
      }
    }
  }
}

actual fun cancelPlanReminders() {
  val context = AppContextHolder.context ?: return
  runCatching {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, PlanReminderReceiver::class.java)
    for (i in 0 until 64) {
      val pi =
          PendingIntent.getBroadcast(
              context,
              PLAN_REQUEST_CODE_BASE + i,
              intent,
              PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
          )
      if (pi != null) {
        alarmManager.cancel(pi)
        pi.cancel()
      }
    }
  }
}

class PlanReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra("title") ?: return
    val notification =
        NotificationCompat.Builder(context, PLAN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("今日计划提醒：$title")
            .setAutoCancel(true)
            .build()
    NotificationManagerCompat.from(context).notify(
        (System.currentTimeMillis() % 100000).toInt(),
        notification,
    )
  }
}

private fun createChannel(context: Context) {
  if (android.os.Build.VERSION.SDK_INT >= 26) {
    val channel =
        NotificationChannel(PLAN_CHANNEL_ID, "计划提醒", NotificationManager.IMPORTANCE_HIGH)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}

private fun parsePlanReminderMillis(date: String?, reminderAt: String?): Long? {
  val d = date ?: return null
  val t = reminderAt ?: return null
  val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
  return runCatching { formatter.parse("$d $t")?.time }.getOrNull()
}
