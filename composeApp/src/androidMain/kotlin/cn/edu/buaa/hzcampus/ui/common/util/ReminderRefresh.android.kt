package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.buaa.hzcampus.api.storage.PlanStore
import cn.edu.buaa.hzcampus.api.storage.ReminderStore
import cn.edu.buaa.hzcampus.repository.DatedClass
import cn.edu.buaa.hzcampus.repository.ScheduleRepository

/** 每日自维护闹钟的 requestCode（与课程提醒的 2000+、计划提醒的 4000+ 错开）。 */
private const val DAILY_REFRESH_REQUEST_CODE = 2900

/** 自维护间隔：一天。 */
private const val DAILY_REFRESH_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

/**
 * 重新排「未来一周的课程提醒 + 计划提醒」，并预约下一次每日自维护。
 *
 * 全部基于本地已保存数据，不联网；任何一步失败都只是不排，不影响其它步骤（调用方也各自兜底）。
 */
internal fun rescheduleAllReminders() {
  val repository = ScheduleRepository()
  val advance = ReminderStore.getAdvanceMinutes()
  val upcoming =
      runCatching { repository.upcomingClasses(CLASS_REMINDER_DAYS).getOrNull() }.getOrNull()
  if (!upcoming.isNullOrEmpty()) {
    scheduleClassReminders(upcoming, advance)
  } else {
    // 本地课表不可用（尚未本地化 / 缓存损坏）：退回「今天」的课表，取不到就只清掉旧闹钟。
    val today = runCatching { repository.todayIsoDate() }.getOrNull()
    val classes = runCatching { repository.todayClasses().getOrNull() }.getOrNull().orEmpty()
    scheduleClassReminders(
        classes.mapNotNull { today?.let { date -> DatedClass(date, it) } },
        advance,
    )
  }
  schedulePlanReminders(runCatching { PlanStore.list() }.getOrNull().orEmpty())
  AppContextHolder.context?.let { runCatching { scheduleDailyReminderRefresh(it) } }
}

/**
 * 预约下一次每日自维护。
 *
 * 课表提醒一次只排未来一周，而且闹钟在关机、覆盖安装时都会被系统清掉。有了这个每天触发一次的 闹钟，即使长期不打开 App，提醒也会自动续排，不会出现「一周后突然不再提醒」的情况。
 */
internal fun scheduleDailyReminderRefresh(context: Context) {
  val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
  val pi =
      PendingIntent.getBroadcast(
          context,
          DAILY_REFRESH_REQUEST_CODE,
          Intent(context, DailyReminderRefreshReceiver::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  val triggerAt = System.currentTimeMillis() + DAILY_REFRESH_INTERVAL_MILLIS
  // 自维护不需要精确时刻，用 allow-while-idle 的一次性闹钟即可（Doze 下也能被唤醒），
  // 每次触发后再预约下一天，比 setRepeating 更不容易被系统回收。
  runCatching { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
}

/** 每日自维护闹钟的接收器：重排提醒并预约下一次。 */
class DailyReminderRefreshReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppContextHolder.context = context.applicationContext
    runCatching { rescheduleAllReminders() }
  }
}
