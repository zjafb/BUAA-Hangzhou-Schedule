package cn.edu.buaa.hzcampus.ui.common.util

/** 全屏只用于息屏/锁屏；权限不足时仍保留普通提醒。 */
internal fun shouldShowFullScreenReminder(
    enabled: Boolean,
    permitted: Boolean,
    interactive: Boolean,
    locked: Boolean,
): Boolean = enabled && permitted && (!interactive || locked)
