package cn.edu.buaa.hzcampus.ui.common.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 覆盖安装后重新排课提醒与计划提醒。
 *
 * AlarmManager 里的闹钟既不会跨重启存活，也会在应用被覆盖安装时被系统清掉：这两种情况如果不重排，
 * 用户将再也收不到课前提醒，直到手动打开一次 App。这里在 BOOT_COMPLETED 与 MY_PACKAGE_REPLACED
 * 之后按本地已存数据重新排一遍（未来一周 + 每日自维护闹钟）。
 *
 * 过程全部容错：读不到课表/计划就静默跳过，任何异常都不会抛出（广播接收器崩溃会弹系统对话框）。
 */
class BootReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
      else -> return
    }
    // 提醒的实际实现（Android actual）依赖 AppContextHolder 取 Application Context，
    // 开机 / 升级时进程是全新创建的，这里必须先补上。
    AppContextHolder.context = context.applicationContext
    android.util.Log.d("HzcReminder", "reschedule on ${intent.action}")
    runCatching { rescheduleAllReminders() }
  }
}
