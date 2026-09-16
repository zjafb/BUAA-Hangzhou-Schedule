package cn.edu.buaa.hzcampus.ui.common.util

/** 系统通知权限是否已开启（Android 13+ 检查 POST_NOTIFICATIONS，其余平台视为已开启）。 */
expect fun areNotificationsEnabled(): Boolean

/** 打开本应用的通知设置页，供用户手动开启通知权限。 */
expect fun openNotificationSettings()
