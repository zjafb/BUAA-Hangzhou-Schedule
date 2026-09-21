package cn.edu.buaa.hzcampus.api.storage

import com.russhwolf.settings.Settings

/** 课程提前时间，以及课程/今日计划共用的提醒显示偏好。 */
object ReminderStore {
  private const val KEY = "class_reminder_advance_minutes"

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun getAdvanceMinutes(): Int = settings.getIntOrNull(KEY) ?: 15

  fun fullScreenEnabled(): Boolean = settings.getBoolean("class_reminder_full_screen", false)

  fun setFullScreenEnabled(enabled: Boolean) =
      settings.putBoolean("class_reminder_full_screen", enabled)

  fun setAdvanceMinutes(minutes: Int) {
    settings.putInt(KEY, minutes)
  }
}
