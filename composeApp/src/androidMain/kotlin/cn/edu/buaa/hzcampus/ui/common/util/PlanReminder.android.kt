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
          Intent(context, PlanReminderReceiver::class.java).apply { putExtra("title", task.title) }
      val pi =
          PendingIntent.getBroadcast(
              context,
              PLAN_REQUEST_CODE_BASE + index,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      scheduleReminderAlarm(alarmManager, remindAt, pi)
    }
  }
}

/**
 * 优先排**精确**闹钟，排不上再退化（与课程提醒同一策略）。
 *
 * 不预先查询 `canScheduleExactAlarms()`：它只反映 `SCHEDULE_EXACT_ALARM` 的 app-op，
 * 应用声明了 `USE_EXACT_ALARM`（安装即授予）时仍可能返回 false，会把精确闹钟误降级。
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
    NotificationManagerCompat.from(context)
        .notify(
            (System.currentTimeMillis() % 100000).toInt(),
            notification,
        )
  }
}

private fun createChannel(context: Context) {
  if (android.os.Build.VERSION.SDK_INT >= 26) {
    val channel = NotificationChannel(PLAN_CHANNEL_ID, "计划提醒", NotificationManager.IMPORTANCE_HIGH)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}

private fun parsePlanReminderMillis(date: String?, reminderAt: String?): Long? {
  val d = date ?: return null
  val t = reminderAt ?: return null
  val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
  return runCatching { formatter.parse("$d $t")?.time }.getOrNull()
}
