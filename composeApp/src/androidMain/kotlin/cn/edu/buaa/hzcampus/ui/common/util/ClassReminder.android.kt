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
import cn.edu.buaa.hzcampus.model.dto.TodayClass
import java.util.Calendar

private const val CHANNEL_ID = "class_reminder"
private const val REQUEST_CODE_BASE = 2000

actual fun scheduleClassReminders(courses: List<TodayClass>, advanceMinutes: Int) {
  val context = AppContextHolder.context ?: return
  runCatching {
    cancelClassReminders()
    createChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    courses.forEachIndexed { index, course ->
      val startMillis = parseStartMillis(course.time) ?: return@forEachIndexed
      val remindAt = startMillis - advanceMinutes * 60_000L
      if (remindAt <= now) return@forEachIndexed
      val intent =
          Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra("title", course.bizName)
            putExtra("place", course.place)
            putExtra("advance", advanceMinutes)
          }
      val pi =
          PendingIntent.getBroadcast(
              context,
              REQUEST_CODE_BASE + index,
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

actual fun cancelClassReminders() {
  val context = AppContextHolder.context ?: return
  runCatching {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ClassReminderReceiver::class.java)
    for (i in 0 until 64) {
      val pi =
          PendingIntent.getBroadcast(
              context,
              REQUEST_CODE_BASE + i,
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

class ClassReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra("title") ?: return
    val place = intent.getStringExtra("place")
    val advance = intent.getIntExtra("advance", 15)
    val message =
        buildString {
          append("「").append(title).append("」还有 ").append(advance).append(" 分钟就要开始了")
          if (!place.isNullOrBlank()) {
            append("，请前往「").append(place).append("」")
          }
        }
    val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课前提醒")
            .setContentText(message)
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
    val channel = NotificationChannel(CHANNEL_ID, "课前提醒", NotificationManager.IMPORTANCE_HIGH)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}

private fun parseStartMillis(time: String?): Long? {
  val t = time ?: return null
  val start = t.substringBefore("-").trim()
  val parts = start.split(":")
  if (parts.size < 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  val cal =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
  return cal.timeInMillis
}
