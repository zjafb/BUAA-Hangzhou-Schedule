package cn.edu.buaa.hzcampus.ui.common.util

/** iOS 没有精确闹钟授权限制。 */
actual fun canScheduleExactAlarms(): Boolean = true

actual fun openExactAlarmSettings() {}
