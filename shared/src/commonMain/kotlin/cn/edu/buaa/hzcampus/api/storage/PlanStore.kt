package cn.edu.buaa.hzcampus.api.storage

import cn.edu.buaa.hzcampus.model.dto.PlanTask
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/** 今日计划任务本地存储。 */
object PlanStore {
  private const val KEY = "plan_tasks"
  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun list(): List<PlanTask> {
    val raw = settings.getStringOrNull(KEY) ?: return emptyList()
    return runCatching { json.decodeFromString<List<PlanTask>>(raw) }.getOrDefault(emptyList())
  }

  fun upsert(task: PlanTask) {
    val tasks = list().filterNot { it.id == task.id } + task
    settings.putString(KEY, json.encodeToString(tasks))
  }

  fun delete(id: String) {
    settings.putString(KEY, json.encodeToString(list().filterNot { it.id == id }))
  }
}
