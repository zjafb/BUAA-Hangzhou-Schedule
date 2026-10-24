package cn.edu.buaa.hzcampus.ui.common.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal data class ClassReminderTiming(val minutesUntilStart: Long, val expiresInMillis: Long)

/** 系统延迟投递时使用真实剩余时间；过期课程不再弹出全屏提醒。 */
internal fun classReminderTiming(startMillis: Long, nowMillis: Long): ClassReminderTiming? {
  val expiresIn = startMillis + 10 * 60_000L - nowMillis
  if (expiresIn <= 0) return null
  return ClassReminderTiming(
      ((startMillis - nowMillis + 59_999) / 60_000L).coerceAtLeast(0),
      expiresIn,
  )
}

/** 课表固定使用北京时间，不跟随手机或模拟器时区。 */
internal fun classStartMillis(date: String, time: String?): Long? =
    runCatching {
          val day = LocalDate.parse(date)
          val start = LocalTime.parse(requireNotNull(time).substringBefore('-').trim())
          LocalDateTime(day, start).toInstant(TimeZone.of("Asia/Shanghai")).toEpochMilliseconds()
        }
        .getOrNull()

/** 已投递不重发；重排时保留已到点但尚未投递的有效提醒。 */
internal fun shouldScheduleClassReminder(
    start: Long,
    remindAt: Long,
    now: Long,
    previouslyScheduled: Boolean,
    delivered: Boolean,
): Boolean = !delivered && start + 10 * 60_000L > now && (remindAt > now || previouslyScheduled)
