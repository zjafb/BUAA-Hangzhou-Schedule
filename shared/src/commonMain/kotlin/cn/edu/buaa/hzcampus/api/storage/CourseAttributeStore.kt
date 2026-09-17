package cn.edu.buaa.hzcampus.api.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * 本地「课程名 -> 课程性质」映射。
 *
 * 课表数据（CourseClass / TodayClass）里没有课程性质字段，只有成绩数据里的 `Grade.courseAttribute`（KCXZDM_DISPLAY）带这个信息。
 *
 * 这里把历次成绩数据里的课程性质按课程名沉淀下来，跨学期累积，供首页今日课表显示「必 / 选」小标签使用。
 *
 * 映射故意不按用户或连接模式隔离：课程性质由教学计划决定，同一门课在不同学期、不同连接模式下都相同，而「累积」正是这个映射存在的意义。
 *
 * 所有读写都做了容错，存储不可用或缓存损坏时降级为空映射，不会影响成绩与课表主流程。
 */
object CourseAttributeStore {
  private const val KEY = "course_attributes_by_name"
  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  /** 返回全部已知的「课程名 -> 课程性质」；无数据或存储异常时返回空映射。 */
  fun all(): Map<String, String> =
      runCatching { decode(settings.getStringOrNull(KEY)) }.getOrDefault(emptyMap())

  /** 按课程名查询课程性质；课程名空白或未记录时返回 null。 */
  fun get(courseName: String?): String? {
    val key = courseName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return all()[key]
  }

  /** 合并写入；同名课程以本次写入为准，空白课程名或空白性质会被忽略。 */
  fun putAll(attributes: Map<String, String>) {
    val incoming =
        attributes
            .mapNotNull { (name, attribute) ->
              val key = name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
              val value = attribute.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
              key to value
            }
            .toMap()
    if (incoming.isEmpty()) return
    runCatching {
      val known = all()
      val merged = known + incoming
      if (merged != known) settings.putString(KEY, json.encodeToString(merged))
    }
  }

  /** 写入单门课程的课程性质。 */
  fun put(courseName: String?, attribute: String?) {
    if (courseName == null || attribute == null) return
    putAll(mapOf(courseName to attribute))
  }

  fun clear() {
    runCatching { settings.remove(KEY) }
  }

  private fun decode(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return json.decodeFromString<Map<String, String>>(raw)
  }
}
