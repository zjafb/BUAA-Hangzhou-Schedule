package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.buaa.hzcampus.model.dto.PlanTask
import java.text.SimpleDateFormat
import java.util.Locale

private const val PLAN_REQUEST_CODE_BASE = 4000

actual fun schedulePlanReminders(tasks: List<PlanTask>) {
  val context = AppContextHolder.context ?: return
  runCatching {
    cancelPlanReminders()
    createReminderChannels(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    tasks.forEachIndexed { index, task ->
      val remindAt = parsePlanReminderMillis(task.date, task.reminderAt) ?: return@forEachIndexed
      if (remindAt <= now) return@forEachIndexed
      val intent =
          Intent(context, PlanReminderReceiver::class.java).apply {
            putExtra("title", task.title)
            putExtra("note", task.note)
            putExtra("time", listOfNotNull(task.startTime, task.endTime).joinToString("–"))
            putExtra("notificationId", ("plan:" + task.id).hashCode())
            putExtra("remindAt", remindAt)
          }
      val pi =
          PendingIntent.getBroadcast(
              context,
              PLAN_REQUEST_CODE_BASE + index,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      scheduleClassReminderAlarm(alarmManager, remindAt, pi)
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
    val now = System.currentTimeMillis()
    val remindAt = intent.getLongExtra("remindAt", now)
    if (!intent.hasExtra("notificationId")) {
      intent.putExtra("notificationId", ("plan:" + title + remindAt).hashCode())
    }
    val note = intent.getStringExtra("note").orEmpty()
    val message = if (note.isBlank()) title else "$title\n$note"
    postReminder(context, intent, PLAN_REMINDER_CHANNEL, "今日计划提醒", message, remindAt + 30 * 60_000L)
  }
}

private fun parsePlanReminderMillis(date: String?, reminderAt: String?): Long? {
  val d = date ?: return null
  val t = reminderAt ?: return null
  val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
  return runCatching { formatter.parse("$d $t")?.time }.getOrNull()
}
