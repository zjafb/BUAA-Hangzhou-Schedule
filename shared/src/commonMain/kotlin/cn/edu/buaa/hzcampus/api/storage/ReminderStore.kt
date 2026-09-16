package cn.edu.buaa.hzcampus.api.storage

import com.russhwolf.settings.Settings

/** 课前提醒提前分钟数。 */
object ReminderStore {
  private const val KEY = "class_reminder_advance_minutes"

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun getAdvanceMinutes(): Int = settings.getIntOrNull(KEY) ?: 15

  fun setAdvanceMinutes(minutes: Int) {
    settings.putInt(KEY, minutes)
  }
}
