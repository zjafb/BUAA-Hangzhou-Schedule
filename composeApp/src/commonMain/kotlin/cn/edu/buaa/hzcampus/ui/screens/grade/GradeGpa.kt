package cn.edu.buaa.hzcampus.ui.screens.grade

import cn.edu.buaa.hzcampus.model.dto.Grade
import cn.edu.buaa.hzcampus.model.dto.GradeData
import cn.edu.buaa.hzcampus.model.dto.Term
import kotlin.math.round

/**
 * 成绩页 GPA 统计 / 绩点换算 / 绩点模拟的纯计算逻辑。
 *
 * 这里不依赖任何 Compose 或平台 API，方便单元测试复用。
 *
 * ## 计算口径
 * 1. **绩点优先取官方值**：接口 `JD` 字段（[Grade.gradePoint]）是成绩单上学校给出的官方绩点，
 *    只要它能解析成数字就优先使用（[GradePointSource.OFFICIAL]）。
 * 2. **官方绩点缺失时按常见 4.0 制换算表估算**（[GradePointSource.ESTIMATED]，见
 *    [gradePointFromScore100]）。北航成绩单本身直接给出绩点，这里的分段表是按公开流传的 常见 4.0 制规则整理的，**不是官方文件**，只用于官方绩点缺失时的兜底。
 * 3. **加权 GPA = Σ(课程绩点 × 课程学分) / Σ课程学分**（北航官方 GPA 口径）。
 * 4. 只有同时满足下列条件的成绩条目才参与 GPA：
 *     - 学分存在且大于 0；
 *     - 未标记为未通过（`SFJG_DISPLAY` 为「否」，或成绩文本为「不及格 / 不通过 / 未通过 / 不合格」）；
 *     - 未标记为无效（`SFYX_DISPLAY` 为「否」，例如作弊、取消资格等记录）；
 *     - 能取到官方绩点，或者能按分数估算出绩点（「通过 / 合格」这类只有结论没有分数的课程会被忽略）。 被忽略的条目会被计数并在界面上提示，而不是静默丢弃。
 * 5. 加权平均分 / 算数平均分 / 分数分布使用「有百分制分数的课程」，包含未通过课程（这样 `<60` 分段才有意义），但不包含无效记录。等级制成绩按 优=90 / 良=80 / 中=70
 *    / 及格=60 折算， 「通过 / 不通过」不参与平均分。
 */

/** 绩点来源。 */
internal enum class GradePointSource {
  /** 官方绩点，来自成绩单 `JD` 字段。 */
  OFFICIAL,
  /** 官方绩点缺失，按常见换算规则估算。 */
  ESTIMATED,
}

/** 成绩条目被排除出 GPA 计算的原因。 */
internal enum class GpaSkipReason(val label: String) {
  /** 无效成绩（`SFYX_DISPLAY` 为「否」）。 */
  INVALID("成绩无效"),
  /** 没有学分，无法加权。 */
  NO_CREDIT("无学分"),
  /** 未通过。 */
  FAILED("未通过"),
  /** 既没有官方绩点，也无法按分数换算（如「通过 / 合格」）。 */
  NO_GRADE_POINT("无百分制成绩"),
}

/** 百分制分数 → 4.0 制绩点的常见换算表，按分段从高到低排列。 */
internal val SCORE_TO_GRADE_POINT_TABLE: List<Pair<Double, Double>> =
    listOf(
        90.0 to 4.0,
        85.0 to 3.7,
        82.0 to 3.3,
        78.0 to 3.0,
        75.0 to 2.7,
        72.0 to 2.3,
        68.0 to 2.0,
        64.0 to 1.5,
        60.0 to 1.0,
    )

/** 等级制成绩 → 绩点（仅在缺少官方绩点时使用）。 */
private val LEVEL_GRADE_POINTS: Map<String, Double> =
    mapOf("优" to 4.0, "良" to 3.5, "中" to 2.8, "及格" to 1.7, "不及格" to 0.0)

/** 等级制成绩折算成百分制分数，仅用于加权/算数平均分。 */
private val LEVEL_SCORES: Map<String, Double> =
    mapOf("优" to 90.0, "良" to 80.0, "中" to 70.0, "及格" to 60.0, "不及格" to 0.0)

/** 只有结论、没有分数的成绩文本。 */
private val PASS_ONLY_TEXTS: Set<String> = setOf("通过", "合格", "免修")

/** 表示未通过的成绩文本。 */
private val FAILED_TEXTS: Set<String> = setOf("不及格", "不通过", "未通过", "不合格")

/** 界面上关于估算口径的提示文案。 */
internal const val GPA_ESTIMATE_NOTE = "部分课程绩点按常见规则估算"

/** 界面上关于换算规则来源的提示文案。 */
internal const val GPA_RULE_NOTE = "换算规则仅供参考，以学校官方成绩单为准"

/** 去掉空白并统一等级写法，便于比较。 */
internal fun normalizeScoreText(score: String?): String? {
  val text = score?.filterNot { it.isWhitespace() }?.takeIf { it.isNotEmpty() } ?: return null
  return when (text) {
    "优秀" -> "优"
    "良好" -> "良"
    "中等" -> "中"
    "合格" -> "通过"
    "不合格",
    "不及格" -> "不及格"
    else -> text.removeSuffix("分")
  }
}

/** 百分制分数 → 4.0 制绩点。低于 60 分记 0 分绩点。 */
internal fun gradePointFromScore100(score: Double): Double {
  if (!score.isFinite()) return 0.0
  return SCORE_TO_GRADE_POINT_TABLE.firstOrNull { score >= it.first }?.second ?: 0.0
}

/** 按常见规则估算绩点：先认等级制文案，再认百分制分数；无法判断时返回 null。 */
internal fun estimateGradePointFromScore(score: String?): Double? {
  val text = normalizeScoreText(score) ?: return null
  LEVEL_GRADE_POINTS[text]?.let {
    return it
  }
  val numeric = text.toDoubleOrNull() ?: return null
  return gradePointFromScore100(numeric)
}

/** 把成绩文本折算成百分制分数（等级制按 90/80/70/60 折算），仅用于平均分与分数分布。 */
internal fun numericScoreFromText(score: String?): Double? {
  val text = normalizeScoreText(score) ?: return null
  if (text in PASS_ONLY_TEXTS) return null
  LEVEL_SCORES[text]?.let {
    return it
  }
  val numeric = text.toDoubleOrNull() ?: return null
  return numeric.takeIf { it.isFinite() && it >= 0.0 }
}

/** 解析官方绩点字段，非法值返回 null。 */
internal fun parseOfficialGradePoint(gradePoint: String?): Double? {
  val value = gradePoint?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return null
  return value.takeIf { it.isFinite() && it >= 0.0 }
}

/** 成绩页里唯一标识一门课程，用于「逐门调分模拟」的状态映射。 */
internal fun Grade.gpaKey(): String =
    id?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(termCode, courseCode, courseName).joinToString("|").ifBlank {
          hashCode().toString()
        }

/** 「是 / 否」型标记是否明确为否。 */
private fun flagIsFalse(value: String?): Boolean {
  val text = value?.filterNot { it.isWhitespace() } ?: return false
  if (text.isEmpty()) return false
  return text == "否" ||
      text == "0" ||
      text.equals("no", ignoreCase = true) ||
      text.equals("false", ignoreCase = true)
}

/** 「是 / 否」型标记是否明确为是。 */
private fun flagIsTrue(value: String?): Boolean {
  val text = value?.filterNot { it.isWhitespace() } ?: return false
  if (text.isEmpty()) return false
  return text == "是" ||
      text == "1" ||
      text.equals("yes", ignoreCase = true) ||
      text.equals("true", ignoreCase = true)
}

/** 单门成绩的统计口径。 */
internal data class GradeRecord(
    val key: String,
    val course: Grade,
    /** 大于 0 的学分，缺失或非正数时为 null。 */
    val credit: Double?,
    /** 官方绩点或估算绩点，无法换算时为 null。 */
    val gradePoint: Double?,
    val gradePointSource: GradePointSource?,
    /** 折算成百分制的分数，仅用于平均分与分数分布。 */
    val numericScore: Double?,
    /** null 表示参与 GPA 计算，否则表示被忽略的原因。 */
    val skipReason: GpaSkipReason?,
) {
  val counted: Boolean
    get() = skipReason == null
}

private val GradeRecord.creditValue: Double
  get() = credit ?: 0.0

private val GradeRecord.pointValue: Double
  get() = gradePoint ?: 0.0

/** 把一条成绩转换成统计口径。 */
internal fun toGradeRecord(grade: Grade): GradeRecord {
  val credit = grade.credit?.takeIf { it.isFinite() && it > 0.0 }
  val scoreText = normalizeScoreText(grade.score)
  val officialPoint = parseOfficialGradePoint(grade.gradePoint)
  val estimatedPoint = if (officialPoint == null) estimateGradePointFromScore(scoreText) else null
  val point = officialPoint ?: estimatedPoint
  val source =
      when {
        officialPoint != null -> GradePointSource.OFFICIAL
        estimatedPoint != null -> GradePointSource.ESTIMATED
        else -> null
      }
  val numericScore = numericScoreFromText(scoreText)
  // 未通过的判定：接口明确标记为「否」，或者成绩文本就是未通过；若接口没有给出标记，
  // 则以「百分制低于 60 分即未通过」兜底。
  val failed =
      flagIsFalse(grade.passed) ||
          (!flagIsTrue(grade.passed) &&
              (scoreText in FAILED_TEXTS || (numericScore != null && numericScore < 60.0)))
  val skipReason =
      when {
        flagIsFalse(grade.effective) -> GpaSkipReason.INVALID
        credit == null -> GpaSkipReason.NO_CREDIT
        failed -> GpaSkipReason.FAILED
        point == null -> GpaSkipReason.NO_GRADE_POINT
        else -> null
      }
  return GradeRecord(
      key = grade.gpaKey(),
      course = grade,
      credit = credit,
      gradePoint = point,
      gradePointSource = source,
      numericScore = numericScoreFromText(scoreText),
      skipReason = skipReason,
  )
}

/** 一组成绩的统计口径集合。 */
internal data class GpaBreakdown(val records: List<GradeRecord>) {
  /** 参与 GPA 计算的课程。 */
  val counted: List<GradeRecord>
    get() = records.filter { it.counted }

  /** 被忽略的课程。 */
  val skipped: List<GradeRecord>
    get() = records.filterNot { it.counted }

  /** 有百分制分数、可用于平均分与分数分布的课程（含未通过，不含无效记录）。 */
  val scored: List<GradeRecord>
    get() = records.filter { it.numericScore != null && it.skipReason != GpaSkipReason.INVALID }
}

/** 按成绩列表构建统计口径。 */
internal fun buildGpaBreakdown(grades: List<Grade>): GpaBreakdown =
    GpaBreakdown(grades.map(::toGradeRecord))

/** GPA 汇总结果。 */
internal data class GpaSummary(
    /** 加权 GPA，没有任何课程参与计算时为 null。 */
    val gpa: Double?,
    /** 参与 GPA 计算的学分合计。 */
    val countedCredits: Double,
    /** 参与 GPA 计算的课程数。 */
    val countedCourses: Int,
    /** 其中绩点由估算得到的课程数。 */
    val estimatedCourses: Int,
    /** 成绩条目总数。 */
    val totalCourses: Int,
    /** 被忽略的条目数。 */
    val skippedCourses: Int,
    val weightedAverageScore: Double?,
    val arithmeticAverageScore: Double?,
) {
  val hasEstimate: Boolean
    get() = estimatedCourses > 0
}

private fun round2(value: Double): Double = round(value * 100.0) / 100.0

/** 计算加权 GPA、学分、课程数与平均分。 */
internal fun GpaBreakdown.summary(): GpaSummary {
  val countedRecords = counted
  val countedCredits = countedRecords.sumOf { it.creditValue }
  val gpa =
      if (countedCredits > 0.0) {
        round2(countedRecords.sumOf { it.pointValue * it.creditValue } / countedCredits)
      } else {
        null
      }

  val scoredRecords = scored
  val scoredCredits = scoredRecords.sumOf { it.creditValue }
  val weighted =
      if (scoredCredits > 0.0) {
        round2(scoredRecords.sumOf { (it.numericScore ?: 0.0) * it.creditValue } / scoredCredits)
      } else {
        null
      }
  val numericScores = scoredRecords.mapNotNull { it.numericScore }
  val arithmetic = if (numericScores.isEmpty()) null else round2(numericScores.average())

  return GpaSummary(
      gpa = gpa,
      countedCredits = round2(countedCredits),
      countedCourses = countedRecords.size,
      estimatedCourses = countedRecords.count { it.gradePointSource == GradePointSource.ESTIMATED },
      totalCourses = records.size,
      skippedCourses = records.size - countedRecords.size,
      weightedAverageScore = weighted,
      arithmeticAverageScore = arithmetic,
  )
}

/** 按学期汇总的 GPA，用于「各学期 GPA 趋势」。 */
internal data class TermGpaPoint(
    val termCode: String,
    val termName: String,
    val summary: GpaSummary,
)

/** 学期显示名：优先用选课/成绩接口给的学期名，其次用学期代码。 */
internal fun termDisplayName(term: Term?, termCode: String): String =
    term?.itemName?.takeIf { it.isNotBlank() } ?: termCode

/** 把「学期代码 -> 成绩」整理成趋势图需要的点位，按学期代码升序排列（例如 `2023-2024-1` → `2023-2024-2` → `2024-2025-1`）。 */
internal fun termGpaPoints(
    termGrades: Map<String, GradeData>,
    terms: List<Term>,
): List<TermGpaPoint> =
    termGrades.entries
        .sortedBy { it.key }
        .map { (termCode, gradeData) ->
          val term = terms.firstOrNull { it.itemCode == termCode }
          TermGpaPoint(
              termCode = termCode,
              termName = termDisplayName(term, termCode),
              summary = buildGpaBreakdown(gradeData.grades).summary(),
          )
        }

/** 分数分布的一个分段。 */
internal data class ScoreBucket(
    val label: String,
    /** 分段下界（含）。 */
    val minScore: Double,
    /** 分段上界；最后一段为 null 表示不设上界。 */
    val maxScore: Double?,
    val courseCount: Int,
    val credits: Double,
)

private val SCORE_BUCKET_RANGES: List<Triple<String, Double, Double?>> =
    listOf(
        Triple("<60", 0.0, 60.0),
        Triple("60-69", 60.0, 70.0),
        Triple("70-79", 70.0, 80.0),
        Triple("80-89", 80.0, 90.0),
        Triple("90-100", 90.0, null),
    )

/** 被忽略条目按原因的统计文案，例如「未通过 3 条、无学分 1 条」；没有忽略项时返回 null。 */
internal fun GpaBreakdown.skippedSummaryText(): String? {
  val counts = skipped.groupingBy { it.skipReason }.eachCount()
  if (counts.isEmpty()) return null
  return counts.entries
      .sortedByDescending { it.value }
      .joinToString("、") { (reason, count) -> "${reason?.label ?: "其他"} $count 条" }
}

/** 按分数段统计课程数与学分。分数分布包含未通过课程，但不含无效记录。 */
internal fun GpaBreakdown.scoreDistribution(): List<ScoreBucket> =
    SCORE_BUCKET_RANGES.map { (label, min, max) ->
      val matched =
          scored.filter { record ->
            val score = record.numericScore ?: return@filter false
            score >= min && (max == null || score < max)
          }
      ScoreBucket(
          label = label,
          minScore = min,
          maxScore = max,
          courseCount = matched.size,
          credits = round2(matched.sumOf { it.creditValue }),
      )
    }

/** 绩点模拟结果。 */
internal data class GpaSimulation(
    val baseGpa: Double?,
    val baseCredits: Double,
    val addedCredit: Double,
    val addedScore: Double,
    val addedGradePoint: Double,
    val projectedGpa: Double,
    /** 与当前 GPA 的差值；当前没有可计算的 GPA 时为 null。 */
    val delta: Double?,
)

/** 模拟「新增一门课程」后的 GPA：把新课程按换算表折算成绩点后并入加权平均。 学分或分数非法时返回 null。 */
internal fun simulateAddedCourse(
    summary: GpaSummary,
    credit: Double?,
    score: Double?,
): GpaSimulation? {
  val validCredit = credit?.takeIf { it.isFinite() && it > 0.0 } ?: return null
  val validScore = score?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
  val point = gradePointFromScore100(validScore)
  val baseTotal = (summary.gpa ?: 0.0) * summary.countedCredits
  val projected = round2((baseTotal + point * validCredit) / (summary.countedCredits + validCredit))
  return GpaSimulation(
      baseGpa = summary.gpa,
      baseCredits = summary.countedCredits,
      addedCredit = validCredit,
      addedScore = validScore,
      addedGradePoint = point,
      projectedGpa = projected,
      delta = summary.gpa?.let { round2(projected - it) },
  )
}

/** 模拟「逐门调分」：把若干已参与 GPA 计算的课程换成新分数后重新汇总。 键为 [Grade.gpaKey]，值为新的百分制分数。 */
internal fun GpaBreakdown.withAdjustments(adjustments: Map<String, Double>): GpaBreakdown {
  if (adjustments.isEmpty()) return this
  return GpaBreakdown(
      records.map { record ->
        val newScore = adjustments[record.key]?.takeIf { it.isFinite() && it >= 0.0 }
        if (newScore == null || !record.counted) {
          record
        } else {
          record.copy(
              gradePoint = gradePointFromScore100(newScore),
              gradePointSource = GradePointSource.ESTIMATED,
              numericScore = newScore,
          )
        }
      }
  )
}

/** 成绩页汇总卡片使用的统计值，建立在 [GpaSummary] 之上。 */
internal data class GradeStatistics(
    val courseCount: Int,
    val totalCredits: Double,
    val gpa: Double?,
    val weightedAverage: Double?,
    val arithmeticAverage: Double?,
)

/** 汇总一组成绩的统计值（含官方绩点优先的加权 GPA）。 */
internal fun calculateGradeStatistics(grades: List<Grade>): GradeStatistics {
  val summary = buildGpaBreakdown(grades).summary()
  return GradeStatistics(
      courseCount = summary.totalCourses,
      totalCredits =
          round2(grades.mapNotNull { it.credit?.takeIf { credit -> credit > 0.0 } }.sum()),
      gpa = summary.gpa,
      weightedAverage = summary.weightedAverageScore,
      arithmeticAverage = summary.arithmeticAverageScore,
  )
}
