package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.TodayClass

/** 为今天的课程安排课前提醒（Android 用 AlarmManager，其它平台为空操作）。 */
expect fun scheduleClassReminders(courses: List<TodayClass>, advanceMinutes: Int)

/** 取消所有课前提醒。 */
expect fun cancelClassReminders()
