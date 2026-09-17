package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.TodayClass
import cn.edu.buaa.hzcampus.repository.DatedClass
import cn.edu.buaa.hzcampus.repository.ScheduleRepository

/** 课前提醒一次排满的未来天数（含今天）。 */
const val CLASS_REMINDER_DAYS = 7

/** 为未来若干天的课程安排课前提醒（Android 用 AlarmManager，其它平台为空操作）。 */
expect fun scheduleClassReminders(classes: List<DatedClass>, advanceMinutes: Int)

/** 取消所有课前提醒。 */
expect fun cancelClassReminders()

/**
 * 读取本地已保存课表，为未来 [CLASS_REMINDER_DAYS] 天安排课前提醒。
 *
 * 只排「今天」的话，连续几天不打开 App 的那些天就没有提醒，因此这里一次排满未来一周；
 * 本地课表不可用（尚未本地化、缓存损坏）时退回调用方给的今天课程（[fallbackTodayClasses]）。
 */
fun scheduleUpcomingClassReminders(
    advanceMinutes: Int,
    fallbackTodayDate: String,
    fallbackTodayClasses: List<TodayClass>,
) {
  val upcoming =
      runCatching { ScheduleRepository().upcomingClasses(CLASS_REMINDER_DAYS).getOrNull() }
          .getOrNull()
  if (!upcoming.isNullOrEmpty()) {
    scheduleClassReminders(upcoming, advanceMinutes)
    return
  }
  scheduleClassReminders(
      fallbackTodayClasses.map { DatedClass(fallbackTodayDate, it) },
      advanceMinutes,
  )
}
