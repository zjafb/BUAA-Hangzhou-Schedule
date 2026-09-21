package cn.edu.buaa.hzcampus.ui.common.util

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
