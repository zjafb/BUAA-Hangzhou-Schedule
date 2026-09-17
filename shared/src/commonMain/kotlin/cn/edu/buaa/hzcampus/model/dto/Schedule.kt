package cn.edu.buaa.hzcampus.model.dto

import kotlinx.serialization.Serializable

/**
 * 学期信息 DTO。
 *
 * @property itemCode 学期代码（如 "2024-2025-1"）。
 * @property itemName 学期名称（如 "2024-2025学年第一学期"）。
 * @property selected 是否为当前选中的学期。
 * @property itemIndex 学期索引。
 */
@Serializable
data class Term(
    val itemCode: String,
    val itemName: String,
    val selected: Boolean,
    val itemIndex: Int,
)

/**
 * 周次信息 DTO。
 *
 * @property startDate 周开始日期（yyyy-MM-dd）。
 * @property endDate 周结束日期（yyyy-MM-dd）。
 * @property term 所属学期代码。
 * @property curWeek 是否为当前周。
 * @property serialNumber 周次序号。
 * @property name 周次名称（如 "第1周"）。
 */
@Serializable
data class Week(
    val startDate: String,
    val endDate: String,
    val term: String,
    val curWeek: Boolean,
    val serialNumber: Int,
    val name: String,
)

/**
 * 课程班级/排课信息 DTO。
 *
 * @property courseCode 课程代码。
 * @property courseName 课程名称。
 * @property courseSerialNo 课程序列号。
 * @property credit 学分。
 * @property beginTime 开始时间（HH:mm）。
 * @property endTime 结束时间（HH:mm）。
 * @property beginSection 开始节次。
 * @property endSection 结束节次。
 * @property placeName 上课地点。
 * @property weeksAndTeachers 上课周次与教师信息描述。
 * @property teachingTarget 教学对象。
 * @property color UI 显示颜色（十六进制）。
 * @property dayOfWeek 星期几（1-7）。
 */
@Serializable
data class CourseClass(
    val courseCode: String,
    val courseName: String,
    val courseSerialNo: String?,
    val credit: String?,
    val beginTime: String?,
    val endTime: String?,
    val beginSection: Int?,
    val endSection: Int?,
    val placeName: String?,
    val weeksAndTeachers: String?,
    val teachingTarget: String?,
    val color: String?,
    val dayOfWeek: Int?,
)

/**
 * 周课表信息 DTO。
 *
 * @property arrangedList 该周的所有排课列表。
 * @property code 上游课表代码；本科接口返回学号，研究生适配填学期代码，不能用作统一学期标识。
 * @property name 上游课表名称。
 */
@Serializable
data class WeeklySchedule(
    val arrangedList: List<CourseClass>,
    val code: String,
    val name: String,
    val sectionTimes: List<SectionTime> = emptyList(),
)

@Serializable data class SectionTime(val section: Int, val start: String?, val end: String?)

/** 杭州校区固定 14 节：优先用上游作息表，缺失节次用标准 14 节表补全。 */
fun scheduleSectionTimes(schedules: Collection<WeeklySchedule>): List<SectionTime> {
  val explicit = schedules.flatMap { it.sectionTimes }.distinct()
  val courses = schedules.flatMap { it.arrangedList }.distinct()
  // 杭州校区标准 14 节作息表
  val standard =
      listOf(
              "08:00" to "08:45",
              "08:50" to "09:35",
              "09:50" to "10:35",
              "10:40" to "11:25",
              "11:30" to "12:15",
              "14:00" to "14:45",
              "14:50" to "15:35",
              "15:50" to "16:35",
              "16:40" to "17:25",
              "17:30" to "18:15",
              "19:00" to "19:45",
              "19:50" to "20:35",
              "20:40" to "21:25",
              "21:30" to "22:15",
          )
          .mapIndexed { index, (start, end) -> SectionTime(index + 1, start, end) }
  val count =
      maxOf(
          14,
          explicit.maxOfOrNull { it.section } ?: 0,
          courses.maxOfOrNull { it.endSection ?: 0 } ?: 0,
      )
  return (1..count).map { section ->
    val known = explicit.filter { it.section == section }
    val std = standard.firstOrNull { it.section == section }
    val startCandidates =
        when {
          known.isNotEmpty() -> known.mapNotNull { it.start }
          std?.start != null -> listOf(std.start)
          else -> courses.filter { it.beginSection == section }.mapNotNull { it.beginTime }
        }
    val endCandidates =
        when {
          known.isNotEmpty() -> known.mapNotNull { it.end }
          std?.end != null -> listOf(std.end)
          else -> courses.filter { it.endSection == section }.mapNotNull { it.endTime }
        }
    SectionTime(
        section,
        startCandidates.distinct().singleOrNull(),
        endCandidates.distinct().singleOrNull(),
    )
  }
}

/** 上游把周次与教师写在同一个字段里，这里去掉带数字的周次/节次片段（如 `1-16周`、`第1-2节`、`2,4周(单)`）。 */
private val weekDescription = Regex("""第?\d[0-9,，、\-—~～.至到]*\s*[周节](?:[（(]?[单双][）)]?)?""")

/** 周次与教师之间的分隔符；`,`、`、` 不在此列，它们同时用于分隔多个教师。 */
private val teacherSeparators = Regex("""[\s/／|｜;；]+""")

/**
 * 从 [CourseClass.weeksAndTeachers] 中提取教师名。
 *
 * 上游把周次和教师塞进同一个字段，已确认的形式有：
 * - 研究生 GSMIS／历史格式：`<周次> <教师>`，即 `ZCMC` 与 `JSXM` 用一个空格拼接，如 `"2,4周 教师甲,教师乙"`。
 * - 只有教师：GSMIS 没有周次名称时该字段就是教师名，如 `"示例教师"`。
 * - 本科教务常见形式：`"1-16周/张三"`、`"1-16周 张三"`、`"第1-16周（单）张三"`。
 *
 * 规则：按空白和 `/` 等分隔符切分，逐段删掉带数字的周次/节次描述，再丢掉只剩周次用字的片段，其余片段原样保留（多个教师继续沿用上游的 `,`、`、` 分隔）。无法确认教师时返回 null。
 */
fun extractTeachers(weeksAndTeachers: String?): String? =
    weeksAndTeachers
        ?.split(teacherSeparators)
        ?.map { part -> part.replace(weekDescription, " ").trim(*teacherEdgeSeparators) }
        ?.filter { it.isNotEmpty() && !isWeekWordOnly(it) }
        ?.joinToString(" ")
        ?.takeIf { it.isNotEmpty() }

/** 去掉周次片段后可能留在首尾的分隔符。 */
private val teacherEdgeSeparators =
    charArrayOf(' ', '\t', ',', '，', '、', ';', '；', '/', '／', '|', '｜')

/** 只由周次用字、数字和标点组成的片段（如 `第`、`单周`、`(周)`）是周次描述的残留，不是教师名。 */
private fun isWeekWordOnly(part: String): Boolean =
    part.all { it.isDigit() || it in "第周单双全节次上下" || it in "-—~～,，、.至到()（）[]【】" }

/**
 * 今日课程摘要 DTO。
 *
 * @property bizName 课程/业务名称。
 * @property place 上课地点。
 * @property time 上课时间描述。
 * @property shortName 课程简称。
 * @property teacher 授课教师；由课表数据的「周次/教师」字段提取，旧缓存或上游缺失时为 null。
 */
@Serializable
data class TodayClass(
    val bizName: String,
    val place: String?,
    val time: String?,
    val shortName: String?,
    val teacher: String? = null,
)

/** 上游 API 学期列表响应包装类。 */
@Serializable data class TermResponse(val datas: List<Term>, val code: String, val msg: String?)

/** 上游 API 周次列表响应包装类。 */
@Serializable data class WeekResponse(val datas: List<Week>, val code: String, val msg: String?)

/** 上游 API 周课表响应包装类。 */
@Serializable
data class WeeklyScheduleResponse(val datas: WeeklySchedule, val code: String, val msg: String?)

/** 上游 API 今日课表响应包装类。 */
@Serializable
data class TodayScheduleResponse(val datas: List<TodayClass>, val code: String, val msg: String?)
