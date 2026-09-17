package cn.edu.buaa.hzcampus.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 成绩查询原始响应体。 */
@Serializable
data class GradeResponse(
    val code: String,
    val msg: String? = null,
    val datas: GradeResponseDatas = GradeResponseDatas(),
)

@Serializable data class GradeResponseDatas(val cxwdcj: GradeRows = GradeRows())

@Serializable
data class GradeRows(
    val totalSize: Int = 0,
    val pageSize: Int = 0,
    val rows: List<Grade> = emptyList(),
)

/** 指定学期的成绩列表。 */
@Serializable
data class GradeData(
    val termCode: String,
    val grades: List<Grade> = emptyList(),
)

/** 单门课程成绩。 */
@Serializable
data class Grade(
    @SerialName("WID") val id: String? = null,
    @SerialName("XNXQDM") val termCode: String? = null,
    @SerialName("XNXQDM_DISPLAY") val termName: String? = null,
    @SerialName("KCM") val courseName: String? = null,
    @SerialName("KCH") val courseCode: String? = null,
    @SerialName("TDKCM") val replacementCourseName: String? = null,
    @SerialName("TDKCH") val replacementCourseCode: String? = null,
    @SerialName("XF") val credit: Double? = null,
    /**
     * 学时。
     *
     * 注意：这里**故意不加** `@SerialName("XS")`。本科教务（byxt）的成绩接口不同版本的 `XS` 字段类型不一致
     * （字符串 / 数字都有），若声明成 `Double?` 反序列化会直接抛错并让整个成绩列表加载失败。
     * 学时统一由 [BuaaScoreCourse.toGrade] 按字符串解析后填进来。
     */
    val hours: Double? = null,
    @SerialName("XSZCJ") val score: String? = null,
    @SerialName("JD") val gradePoint: String? = null,
    @SerialName("KCLBDM_DISPLAY") val courseCategory: String? = null,
    @SerialName("XGXKLBDM_DISPLAY") val courseGroup: String? = null,
    @SerialName("KCXZDM_DISPLAY") val courseAttribute: String? = null,
    @SerialName("KSLXDM_DISPLAY") val examType: String? = null,
    @SerialName("CXCKDM_DISPLAY") val examAttempt: String? = null,
    @SerialName("SFJG_DISPLAY") val passed: String? = null,
    @SerialName("SFYX_DISPLAY") val effective: String? = null,
    @SerialName("CJRDFSDM_DISPLAY") val recognitionType: String? = null,
)

/**
 * 提取「课程名 -> 课程性质」映射，用于把成绩数据里的课程性质沉淀到本地映射（见 CourseAttributeStore）。
 *
 * 课程名或课程性质为空白的条目会被忽略；课程性质取自 `KCXZDM_DISPLAY`，值形如「必修」「选修」。
 */
fun GradeData.courseAttributesByName(): Map<String, String> =
    grades
        .mapNotNull { grade ->
          val name = grade.courseName.cleanText() ?: return@mapNotNull null
          val attribute = grade.courseAttribute.cleanText() ?: return@mapNotNull null
          name to attribute
        }
        .toMap()

/**
 * 把课程性质文案归类成课表标签：「必」或「选」，无法可靠判断时返回 null（界面不显示任何标记）。
 *
 * 规则宽松：包含「必修」→「必」；包含「选修 / 任选 / 限选 / 公选」等含「选」字的性质 →「选」；其余无法确认归属的文案（例如「学位课」）返回 null，宁可不显示也不显示错误内容。
 */
fun courseAttributeBadgeLabel(attribute: String?): String? {
  val value = attribute?.filterNot { it.isWhitespace() } ?: return null
  if (value.isEmpty()) return null
  if (value.contains("必")) return "必"
  if (value.contains("选")) return "选"
  return null
}

/** 北航成绩应用响应体。 */
@Serializable
data class BuaaScoreResponse(
    @SerialName("e") val code: Int = 0,
    @SerialName("m") val message: String? = null,
    @SerialName("d") val data: Map<String, BuaaScoreCourse> = emptyMap(),
)

/** 北航成绩应用中的单门课程成绩。 */
@Serializable
data class BuaaScoreCourse(
    @SerialName("kcmc") val courseName: String? = null,
    @SerialName("kch") val courseCode: String? = null,
    @SerialName("xf") val credit: JsonElement? = null,
    @SerialName("kccj") val score: JsonElement? = null,
    @SerialName("fslx") val scoreType: String? = null,
    @SerialName("kclx") val courseType: String? = null,
    /**
     * 学时（`xs`）。
     *
     * 官网成绩页（移动北航 > 成绩查询）的「学时统计」就是这一列之和。接口里该字段可能是字符串
     * （`"48"`）也可能是数字（`48` / `48.0`），因此用 [JsonElement] 接收后再按文本解析。
     */
    @SerialName("xs") val hours: JsonElement? = null,
    /**
     * 学分绩点（`jd`）。
     *
     * 北航历次接口版本里该字段时有时无：存在时它就是成绩单上的官方绩点，必须优先使用；
     * 不存在时为 null，由本地按北航官方公式估算。同样用 [JsonElement] 兼容字符串/数字两种形态。
     */
    @SerialName("jd") val gradePoint: JsonElement? = null,
)

data class BuaaScoreTerm(val year: String, val semester: Int)

fun parseBuaaScoreTermCode(termCode: String): BuaaScoreTerm {
  val match =
      Regex("""^(\d{4}-\d{4})-(\d+)$""").matchEntire(termCode.trim())
          ?: throw IllegalArgumentException("Unsupported term code: $termCode")
  val semester =
      match.groupValues[2].toIntOrNull()
          ?: throw IllegalArgumentException("Unsupported term code: $termCode")
  return BuaaScoreTerm(year = match.groupValues[1], semester = semester)
}

fun BuaaScoreCourse.toGrade(termCode: String): Grade =
    Grade(
        termCode = termCode,
        termName = termCode,
        courseName = courseName.cleanText(),
        courseCode = courseCode.cleanText(),
        credit = credit.asText()?.toDoubleOrNull(),
        hours = hours.asText()?.toDoubleOrNull(),
        score = score.asText(),
        courseAttribute = courseType.cleanText(),
        recognitionType = scoreType.cleanText(),
        gradePoint = gradePoint.asText(),
    )

private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull?.cleanText()

private fun String?.cleanText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
