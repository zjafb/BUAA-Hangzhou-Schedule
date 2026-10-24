package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cn.edu.buaa.hzcampus.repository.DatedClass

private const val REQUEST_CODE_BASE = 2000

/** 单次排程的闹钟上限（未来一周的课远达不到，纯粹是防御性上限，避免异常数据排爆系统闹钟配额）。 */
private const val MAX_REMINDERS = 200

actual fun scheduleClassReminders(classes: List<DatedClass>, advanceMinutes: Int) {
  val context = AppContextHolder.context ?: return
  runCatching {
        createReminderChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("class_alarm_state", Context.MODE_PRIVATE)
        // 迁移旧版按下标编号的闹钟，之后使用课程身份，避免列表变动覆盖其它课程。
        if (!prefs.getBoolean("migrated", false)) {
          cancelLegacyClassReminders(context, alarmManager)
          prefs.edit().putBoolean("migrated", true).apply()
        }
        val previous = prefs.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty().toSet()
        val now = System.currentTimeMillis()
        val sameAdvance = prefs.getInt("advance", advanceMinutes) == advanceMinutes
        val desired =
            classes
                .mapNotNull { item ->
                  val start =
                      classStartMillis(item.date, item.course.time) ?: return@mapNotNull null
                  val key = "$start|${item.course.bizName}|${item.course.place}|${item.course.time}"
                  Triple(key, start, item)
                }
                .distinctBy { it.first }
                .filter { (key, start, _) ->
                  shouldScheduleClassReminder(
                      start,
                      start - advanceMinutes * 60_000L,
                      now,
                      sameAdvance && key in previous,
                      key in delivered,
                  )
                }
                .take(MAX_REMINDERS)
        val keys = desired.map { it.first }.toSet()
        // 先保存有效身份，接收端拒绝已删除、改期或旧设置的排队广播。
        prefs
            .edit()
            .putStringSet("scheduled", keys)
            .putStringSet(
                "delivered",
                delivered
                    .filter { (it.substringBefore('|').toLongOrNull() ?: 0L) + 10 * 60_000L > now }
                    .toSet(),
            )
            .putInt("advance", advanceMinutes)
            .apply()
        (previous - keys).forEach { key ->
          classPendingIntent(context, key, null, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
          }
        }
        desired.forEach { (key, startMillis, item) ->
          val remindAt = startMillis - advanceMinutes * 60_000L
          val intent =
              Intent(context, ClassReminderReceiver::class.java).apply {
                putExtra("title", item.course.bizName)
                putExtra("place", item.course.place)
                putExtra("advance", advanceMinutes)
                putExtra("startMillis", startMillis)
                putExtra("remindAt", remindAt)
                putExtra("time", item.course.time)
                putExtra("notificationId", key.hashCode())
                putExtra("classKey", key)
              }
          val pi = classPendingIntent(context, key, intent, PendingIntent.FLAG_UPDATE_CURRENT)!!
          scheduleClassReminderAlarm(alarmManager, maxOf(remindAt, now), pi)
        }
        // 顺便预约每天一次的自维护：长期不打开 App 也能持续排上未来一周的提醒。
        scheduleDailyReminderRefresh(context)
      }
      .onFailure { android.util.Log.e("HzcReminder", "Failed to schedule class reminders", it) }
}

/** 用户指定的课程/计划使用可见的闹钟时刻，避免待机配额合并多条相邻提醒。 */
internal fun scheduleClassReminderAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    operation: PendingIntent,
) {
  try {
    val context = AppContextHolder.context ?: return
    val show =
        PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
    return
  } catch (_: SecurityException) {
    // 用户关闭了「闹钟和提醒」：退化为非精确闹钟，提醒仍会出现，只是可能被系统推迟。
  }
  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
}

private fun classPendingIntent(
    context: Context,
    key: String,
    extras: Intent?,
    flags: Int,
): PendingIntent? =
    PendingIntent.getBroadcast(
        context,
        0,
        (extras ?: Intent(context, ClassReminderReceiver::class.java)).apply {
          data = android.net.Uri.parse("hzcampus://class-alarm/${android.net.Uri.encode(key)}")
        },
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

actual fun cancelClassReminders() {
  val context = AppContextHolder.context ?: return
  val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  val prefs = context.getSharedPreferences("class_alarm_state", Context.MODE_PRIVATE)
  prefs.getStringSet("scheduled", emptySet()).orEmpty().forEach { key ->
    classPendingIntent(context, key, null, PendingIntent.FLAG_NO_CREATE)?.let {
      manager.cancel(it)
      it.cancel()
    }
  }
  prefs.edit().remove("scheduled").apply()
  cancelLegacyClassReminders(context, manager)
}

private fun cancelLegacyClassReminders(context: Context, manager: AlarmManager) {
  for (i in 0 until MAX_REMINDERS) {
    PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + i,
            Intent(context, ClassReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        ?.let {
          manager.cancel(it)
          it.cancel()
        }
  }
}

class ClassReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppContextHolder.context = context.applicationContext
    val manager = NotificationManagerCompat.from(context)
    val id = intent.getIntExtra("notificationId", 2901)
    if (intent.action == DISMISS_REMINDER) {
      manager.cancel(id)
      return
    }
    if (!manager.areNotificationsEnabled()) return
    val title = intent.getStringExtra("title") ?: return
    val place = intent.getStringExtra("place")
    val advance = intent.getIntExtra("advance", 15)
    val now = System.currentTimeMillis()
    val startMillis = intent.getLongExtra("startMillis", now + advance * 60_000L)
    val key = intent.getStringExtra("classKey")
    if (key != null) {
      val prefs = context.getSharedPreferences("class_alarm_state", Context.MODE_PRIVATE)
      if (key !in prefs.getStringSet("scheduled", emptySet()).orEmpty()) return
      if (advance != cn.edu.buaa.hzcampus.api.storage.ReminderStore.getAdvanceMinutes()) return
      val remindAt = intent.getLongExtra("remindAt", startMillis - advance * 60_000L)
      if (now < remindAt) return
      val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty().toSet()
      if (key in delivered) return
      prefs.edit().putStringSet("delivered", delivered + key).apply()
      android.util.Log.i("HzcReminder", "class delivery delayMs=${now - remindAt} advance=$advance")
    }
    val timing = classReminderTiming(startMillis, now) ?: return
    val remaining = timing.minutesUntilStart
    val message = buildString {
      append("「").append(title).append("」")
      if (remaining > 0) append("还有 ").append(remaining).append(" 分钟开始") else append("即将开始或已开始")
      if (!place.isNullOrBlank()) {
        append("，请前往「").append(place).append("」")
      }
    }
    postReminder(
        context,
        intent,
        CLASS_REMINDER_CHANNEL,
        "课前提醒",
        message,
        now + timing.expiresInMillis,
    )
  }
}

internal fun canUseClassFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
