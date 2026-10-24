package cn.edu.buaa.hzcampus

import android.app.AlarmManager
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import cn.edu.buaa.hzcampus.api.storage.ReminderStore
import cn.edu.buaa.hzcampus.model.dto.TodayClass
import cn.edu.buaa.hzcampus.repository.DatedClass
import cn.edu.buaa.hzcampus.ui.common.util.AppContextHolder
import cn.edu.buaa.hzcampus.ui.common.util.cancelClassReminders
import cn.edu.buaa.hzcampus.ui.common.util.scheduleClassReminders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 在独立模拟器中运行：验证真实 AlarmManager、待机投递、重排和去重。 */
class ReminderInstrumentation : Instrumentation() {
  override fun onCreate(arguments: Bundle?) {
    super.onCreate(arguments)
    start()
  }

  override fun onStart() {
    val result = Bundle()
    val context = targetContext
    AppContextHolder.context = context.applicationContext
    val oldAdvance = ReminderStore.getAdvanceMinutes()
    val manager = context.getSystemService(AlarmManager::class.java)
    val notifications = context.getSystemService(NotificationManager::class.java)
    val prefs = context.getSharedPreferences("class_alarm_state", Context.MODE_PRIVATE)
    var code = 0
    try {
      cancelClassReminders()
      prefs.edit().clear().commit()
      ReminderStore.setAdvanceMinutes(30)
      val now = System.currentTimeMillis()
      val trigger = (now / 60_000L + 1) * 60_000L
      val start = trigger + 30 * 60_000L
      fun format(pattern: String) =
          SimpleDateFormat(pattern, Locale.US)
              .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
              .format(Date(start))
      val classes =
          listOf("课程A", "课程B").map {
            DatedClass(format("yyyy-MM-dd"), TodayClass(it, "测试教室", format("HH:mm") + "-23:59", it))
          }
      scheduleClassReminders(classes, 30)
      check(manager.nextAlarmClock?.triggerTime == trigger) { "30 minute trigger mismatch" }
      scheduleClassReminders(classes.reversed(), 30)
      check(prefs.getStringSet("scheduled", emptySet())!!.size == 2) {
        "reordering overwrote alarm"
      }
      check(manager.nextAlarmClock?.triggerTime == trigger) { "reschedule shifted trigger" }
      uiAutomation.executeShellCommand("dumpsys battery unplug").close()
      uiAutomation.executeShellCommand("input keyevent 223").close()
      uiAutomation.executeShellCommand("dumpsys deviceidle force-idle").close()
      Thread.sleep((trigger - System.currentTimeMillis()).coerceAtLeast(0) + 2500)
      check(prefs.getStringSet("delivered", emptySet())!!.size == 2) { "Doze delivery missing" }
      check(
          notifications.activeNotifications.count {
            it.notification.channelId == "class_reminder" &&
                it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
          } == 2
      ) {
        "notifications missing: " +
            notifications.activeNotifications.joinToString {
              "${it.id}:${it.notification.channelId}:${it.notification.flags}"
            }
      }
      scheduleClassReminders(classes, 30)
      check(prefs.getStringSet("scheduled", emptySet()).isNullOrEmpty()) {
        "delivered course was rescheduled"
      }
      result.putString(
          "result",
          "PASS: 30 minute epoch, reordered classes, 2 simultaneous Doze notifications, no replay",
      )
    } catch (e: Throwable) {
      result.putString("failure", e.toString())
      code = 1
    } finally {
      cancelClassReminders()
      notifications.cancelAll()
      ReminderStore.setAdvanceMinutes(oldAdvance)
      uiAutomation.executeShellCommand("dumpsys deviceidle unforce").close()
      uiAutomation.executeShellCommand("dumpsys battery reset").close()
      uiAutomation.executeShellCommand("input keyevent 224").close()
    }
    finish(code, result)
  }
}
