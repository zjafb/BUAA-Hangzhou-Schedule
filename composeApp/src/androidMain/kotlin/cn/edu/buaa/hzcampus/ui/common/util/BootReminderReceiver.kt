package cn.edu.buaa.hzcampus.ui.common.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.buaa.hzcampus.api.storage.PlanStore
import cn.edu.buaa.hzcampus.api.storage.ReminderStore
import cn.edu.buaa.hzcampus.repository.ScheduleRepository

/**
 * 开机后重新排课提醒与计划提醒。
 *
 * AlarmManager 里的闹钟不会跨重启存活：手机重启后如果不重排，用户将再也收不到课前提醒，
 * 直到手动打开一次 App。这里在 BOOT_COMPLETED 之后按本地已存数据重新排一遍。
 *
 * 过程全部容错：读不到课表/计划就静默跳过，任何异常都不会抛出（广播接收器崩溃会弹系统对话框）。
 */
class BootReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    // 提醒的实际实现（Android actual）依赖 AppContextHolder 取 Application Context，
    // 开机时进程是全新创建的，这里必须先补上。
    AppContextHolder.context = context.applicationContext
    runCatching { rescheduleClassReminders() }
    runCatching { reschedulePlanReminders() }
  }

  /** 重新排今日课程提醒；取不到本地课表就当作"今天没有课"，顺手清掉可能残留的闹钟。 */
  private fun rescheduleClassReminders() {
    val classes =
        runCatching { ScheduleRepository().todayClasses().getOrNull() }.getOrNull().orEmpty()
    // scheduleClassReminders 内部会先取消旧的（按 requestCode 前缀），再按 courses 重新排。
    scheduleClassReminders(classes, ReminderStore.getAdvanceMinutes())
  }

  /** 重新排计划提醒；取不到计划就是空列表，同样只会取消旧闹钟。 */
  private fun reschedulePlanReminders() {
    val tasks = runCatching { PlanStore.list() }.getOrNull().orEmpty()
    schedulePlanReminders(tasks)
  }
}
