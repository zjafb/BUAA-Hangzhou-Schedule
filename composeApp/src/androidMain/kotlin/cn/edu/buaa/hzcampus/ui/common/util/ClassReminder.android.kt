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
import cn.edu.buaa.hzcampus.repository.DatedClass
import java.util.Calendar
import kotlinx.datetime.LocalDate

private const val CHANNEL_ID = "class_reminder"
private const val REQUEST_CODE_BASE = 2000

/** 单次排程的闹钟上限（未来一周的课远达不到，纯粹是防御性上限，避免异常数据排爆系统闹钟配额）。 */
private const val MAX_REMINDERS = 200

actual fun scheduleClassReminders(classes: List<DatedClass>, advanceMinutes: Int) {
  val context = AppContextHolder.context ?: return
  runCatching {
    cancelClassReminders()
    createChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    classes.take(MAX_REMINDERS).forEachIndexed { index, item ->
      val startMillis = parseStartMillis(item.date, item.course.time) ?: return@forEachIndexed
      val remindAt = startMillis - advanceMinutes * 60_000L
      if (remindAt <= now) return@forEachIndexed
      val intent =
          Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra("title", item.course.bizName)
            putExtra("place", item.course.place)
            putExtra("advance", advanceMinutes)
          }
      // requestCode 按课程下标递增（跨天连续编号），保证每节课各自拥有独立闹钟，互不覆盖。
      val pi =
          PendingIntent.getBroadcast(
              context,
              REQUEST_CODE_BASE + index,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      scheduleReminderAlarm(alarmManager, remindAt, pi)
    }
  }
}

/**
 * 优先排**精确**闹钟，排不上再退化。
 *
 * 这里不再预先查询 `canScheduleExactAlarms()`：它只反映 `SCHEDULE_EXACT_ALARM` 的 app-op， 在本应用声明了
 * `USE_EXACT_ALARM`（安装即授予）的情况下仍可能返回 false，从而把精确闹钟误降级成 有 1 小时窗口的非精确闹钟（Doze 下提醒会被推迟）。改为直接尝试，未授权时
 * `setExactAndAllowWhileIdle` 会抛 [SecurityException]，再保底用非精确闹钟。
 */
private fun scheduleReminderAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    operation: PendingIntent,
) {
  try {
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    return
  } catch (_: SecurityException) {
    // 用户关闭了「闹钟和提醒」：退化为非精确闹钟，提醒仍会出现，只是可能被系统推迟。
  }
  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
}

actual fun cancelClassReminders() {
  val context = AppContextHolder.context ?: return
  runCatching {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ClassReminderReceiver::class.java)
    for (i in 0 until MAX_REMINDERS) {
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

/**
 * 把「日期 + `HH:mm-HH:mm`」解析成该日该时刻的毫秒时间戳。
 *
 * 日期来自本地课表的周次表（含跨天/跨周），因此不再假定「今天」，避免一周里只有今天的课被排上。
 */
private fun parseStartMillis(dateIso: String, time: String?): Long? {
  val t = time ?: return null
  val start = t.substringBefore("-").trim()
  val parts = start.split(":")
  if (parts.size < 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return null
  val cal =
      Calendar.getInstance().apply {
        set(Calendar.YEAR, date.year)
        // Calendar.MONTH 是 0 基，Month.ordinal 同样是 0 基（JANUARY = 0）。
        set(Calendar.MONTH, date.month.ordinal)
        set(Calendar.DAY_OF_MONTH, date.day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
  return cal.timeInMillis
}
