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
    // Android 12+ 未授权精确闹钟时只能退化为非精确闹钟（Doze 下可能被大幅延迟），
    // 这里统一判定一次，避免每节课都重复查询。
    val exactAllowed = canScheduleExactAlarms()
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
      // requestCode 按课程下标递增，保证同一天的多节课各自拥有独立闹钟，互不覆盖。
      val pi =
          PendingIntent.getBroadcast(
              context,
              REQUEST_CODE_BASE + index,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      scheduleReminderAlarm(alarmManager, remindAt, pi, exactAllowed)
    }
  }
}

/**
 * 尽量使用精确闹钟；未授权精确闹钟权限（Android 12+）或系统拒绝时，
 * 保底退化为 [AlarmManager.setAndAllowWhileIdle] 的非精确闹钟，保证提醒仍然存在。
 */
private fun scheduleReminderAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    operation: PendingIntent,
    exactAllowed: Boolean,
) {
  if (exactAllowed) {
    try {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
      return
    } catch (_: SecurityException) {
      // 权限在运行中被系统收回，落到下面的非精确闹钟。
    }
  }
  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
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
    val message = buildString {
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
    NotificationManagerCompat.from(context)
        .notify(
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
