package cn.edu.buaa.hzcampus.ui.common.util

/**
 * 当前是否可以使用「精确闹钟」调度提醒。
 *
 * Android 12（API 31）及以上需要 SCHEDULE_EXACT_ALARM 权限；未授权时 AlarmManager 只能使用
 * 非精确闹钟，系统在 Doze/待机下可能把提醒推迟几十分钟，表现为"课程提醒不准时/收不到"。
 * 其余平台没有这一限制，恒为 true。
 */
expect fun canScheduleExactAlarms(): Boolean

/** 打开系统的「闹钟和提醒」精确闹钟授权页，供用户手动开启（其余平台为空操作）。 */
expect fun openExactAlarmSettings()
