package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.round

/** 统计范围：只看当前学期，还是全部学期。 */
internal enum class GpaScope {
  SELECTED_TERM,
  ALL_TERMS,
}

/** 成绩分析的视图。 */
internal enum class GradeAnalysisView {
  TERM_GPA,
  SCORE_DISTRIBUTION,
}

/** 绩点模拟的模式。 */
private enum class GpaSimulationMode {
  ADD_COURSE,
  ADJUST_COURSES,
}

/** 调分模拟里最多列出多少门课程，避免卡片过长。 */
private const val MAX_ADJUSTABLE_COURSES = 40

/**
 * GPA 概览卡片：加权 GPA、本学期课程数与总学分、其中计入 GPA 的门数与学分，以及按学期 / 全部学期的范围切换。
 *
 * 两组数字同时呈现：
 * - 「课程数 / 总学分」是**全部有效课程**的口径（含未通过与两级制课程）；
 * - 「计入 GPA 门数 / 学分」只统计真正参与加权 GPA 的课程（北航口径下未通过与两级制课程不计入 GPA）。
 *
 * 这里的 GPA 优先使用官方 `JD` 绩点，官方绩点缺失时才按北航官方换算公式估算，界面上会显式提示。
 */
@Composable
internal fun GpaOverviewCard(
    summary: GpaSummary,
    scope: GpaScope,
    onScopeChange: (GpaScope) -> Unit,
    scopeLabel: String,
    skippedNote: String?,
    isLoading: Boolean,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("GPA 统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = scope == GpaScope.SELECTED_TERM,
            onClick = { onScopeChange(GpaScope.SELECTED_TERM) },
            label = { Text("本学期") },
        )
        FilterChip(
            selected = scope == GpaScope.ALL_TERMS,
            onClick = { onScopeChange(GpaScope.ALL_TERMS) },
            label = { Text("全部学期") },
        )
      }

      Text(
          text = scopeLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        GpaMetric(
            label = if (scope == GpaScope.ALL_TERMS) "全部学期 GPA" else "本学期 GPA",
            value =
                when {
                  isLoading -> "统计中"
                  else -> summary.gpa?.let(::formatGradePoint) ?: "--"
                },
            emphasize = true,
            modifier = Modifier.weight(1f),
        )
        GpaMetric(
            label = "加权平均分",
            value =
                if (isLoading) "统计中" else summary.weightedAverageScore?.let(::formatNumber) ?: "--",
            emphasize = true,
            modifier = Modifier.weight(1f),
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        GpaMetric(
            label = "总学时",
            value = summary.totalHours.takeIf { it > 0.0 }?.let(::formatNumber) ?: "--",
            modifier = Modifier.weight(1f),
        )
        GpaMetric(
            label = "计入 GPA 课程数",
            value = summary.countedCourses.toString(),
            modifier = Modifier.weight(1f),
        )
        GpaMetric(
            label = "计入 GPA 学分",
            value =
                if (summary.countedCredits > 0.0) formatNumber(summary.countedCredits) else "--",
            modifier = Modifier.weight(1f),
        )
      }

      if (summary.totalCourses > 0) {
        val hoursText =
            if (summary.totalHours > 0.0) " / ${formatNumber(summary.totalHours)} 学时" else ""
        val countedHoursText =
            if (summary.countedHours > 0.0) " / ${formatNumber(summary.countedHours)} 学时" else ""
        Text(
            text =
                "全部：${summary.totalCourses} 门 / ${formatNumber(summary.totalCredits)} 学分$hoursText · " +
                    "计入 GPA：${summary.countedCourses} 门 / ${formatNumber(summary.countedCredits)} 学分$countedHoursText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      val notes = buildList {
        if (summary.totalCourses > 0) {
          add(GPA_TOTAL_SCOPE_NOTE)
        }
        if (summary.hasEstimate) {
          add("$GPA_ESTIMATE_NOTE（${summary.estimatedCourses} 门缺少官方绩点）")
        }
        if (summary.skippedCourses > 0) {
          add("已忽略 ${summary.skippedCourses} 条记录${skippedNote?.let { "（$it）" } ?: ""}")
        }
        if (summary.countedCourses > 0) {
          add("GPA = Σ(绩点 × 学分) ÷ Σ学分")
          add(GPA_FORMULA_NOTE)
        }
      }
      if (notes.isNotEmpty()) {
        Text(
            text = notes.joinToString("\n"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
          text = GPA_RULE_NOTE,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun GpaMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
  Column(modifier = modifier) {
    Text(
        text = value,
        style =
            if (emphasize) MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * 成绩分析卡片：在各学期 GPA 趋势与分数分布之间切换，数据跟随上方的统计范围。
 *
 * 数据为空时显示空态文案，不绘制空白图表。
 */
@Composable
internal fun GradeAnalysisCard(
    scopeLabel: String,
    view: GradeAnalysisView,
    onViewChange: (GradeAnalysisView) -> Unit,
    termPoints: List<TermGpaPoint>,
    previousTermPoint: TermGpaPoint?,
    buckets: List<ScoreBucket>,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("成绩分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = view == GradeAnalysisView.TERM_GPA,
            onClick = { onViewChange(GradeAnalysisView.TERM_GPA) },
            label = { Text("各学期 GPA") },
        )
        FilterChip(
            selected = view == GradeAnalysisView.SCORE_DISTRIBUTION,
            onClick = { onViewChange(GradeAnalysisView.SCORE_DISTRIBUTION) },
            label = { Text("分数分布") },
        )
      }

      when (view) {
        GradeAnalysisView.TERM_GPA -> {
          val points = termPoints.filter { it.summary.gpa != null }
          if (points.isEmpty()) {
            ChartEmptyState("暂无可统计的学期 GPA")
          } else {
            Text(
                text = termGpaCaption(scopeLabel, points, previousTermPoint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradeBarChart(
                spec =
                    GradeBarChartSpec(
                        xLabels = points.map { shortTermLabel(it.termCode) },
                        values = points.map { it.summary.gpa ?: 0.0 },
                        yMax = 4.0,
                        valueFormatter = ::formatGradePoint,
                    ),
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
          }
        }
        GradeAnalysisView.SCORE_DISTRIBUTION -> {
          val total = buckets.sumOf { it.courseCount }
          if (total == 0) {
            ChartEmptyState("暂无百分制成绩，无法统计分数分布")
          } else {
            Text(
                text = "统计范围：$scopeLabel · 共 $total 门有百分制分数的课程（含未通过）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradeBarChart(
                spec =
                    GradeBarChartSpec(
                        xLabels = buckets.map { it.label },
                        values = buckets.map { it.courseCount.toDouble() },
                        yMax = null,
                        valueFormatter = { formatNumber(it) },
                    ),
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
          }
        }
      }
    }
  }
}

private fun termGpaCaption(
    scopeLabel: String,
    points: List<TermGpaPoint>,
    previousTermPoint: TermGpaPoint?,
): String {
  val base = "统计范围：$scopeLabel · 共 ${points.size} 个学期"
  val current = points.singleOrNull()?.summary?.gpa
  val previous = previousTermPoint?.summary?.gpa
  if (current == null || previous == null) return base
  val delta = round((current - previous) * 100.0) / 100.0
  val comparison =
      when {
        delta > 0.0 -> "较上一学期 +${formatGradePoint(delta)}"
        delta < 0.0 -> "较上一学期 -${formatGradePoint(-delta)}"
        else -> "与上一学期持平"
      }
  return "$base · $comparison"
}

/** 学期代码压缩成图表标签：`2024-2025-1` → `24-25-1`。 */
internal fun shortTermLabel(termCode: String): String {
  val parts = termCode.split("-")
  if (
      parts.size == 3 &&
          parts.all { it.isNotBlank() } &&
          parts[0].length == 4 &&
          parts[1].length == 4
  ) {
    return "${parts[0].takeLast(2)}-${parts[1].takeLast(2)}-${parts[2]}"
  }
  return termCode
}

@Composable
private fun ChartEmptyState(text: String) {
  Box(
      modifier = Modifier.fillMaxWidth().height(120.dp),
      contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
          Icons.Default.BarChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outline,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
          text = text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * 绩点模拟卡片，包含两种模式：
 * - 「新增课程」：输入预计学分与分数，实时算出加上这门课后的 GPA 与变化量。
 * - 「已有课程调分」：对当前范围内参与 GPA 计算的课程逐门加减分，实时看 GPA 变化。
 */
@Composable
internal fun GpaSimulatorCard(
    breakdown: GpaBreakdown,
    summary: GpaSummary,
    scopeLabel: String,
) {
  var mode by remember { mutableStateOf(GpaSimulationMode.ADD_COURSE) }
  var creditText by remember { mutableStateOf("2") }
  var scoreText by remember { mutableStateOf("90") }
  var adjustments by remember { mutableStateOf(emptyMap<String, Double>()) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("绩点模拟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(
          text =
              "基准：$scopeLabel（GPA ${summary.gpa?.let(::formatGradePoint) ?: "--"} · " +
                  "${formatNumber(summary.countedCredits)} 学分）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == GpaSimulationMode.ADD_COURSE,
            onClick = { mode = GpaSimulationMode.ADD_COURSE },
            label = { Text("新增课程") },
        )
        FilterChip(
            selected = mode == GpaSimulationMode.ADJUST_COURSES,
            onClick = { mode = GpaSimulationMode.ADJUST_COURSES },
            label = { Text("已有课程调分") },
        )
      }

      when (mode) {
        GpaSimulationMode.ADD_COURSE ->
            AddCourseSimulation(
                summary = summary,
                creditText = creditText,
                onCreditChange = { creditText = it },
                scoreText = scoreText,
                onScoreChange = { scoreText = it },
            )
        GpaSimulationMode.ADJUST_COURSES ->
            AdjustCoursesSimulation(
                breakdown = breakdown,
                summary = summary,
                adjustments = adjustments,
                onAdjustmentsChange = { adjustments = it },
            )
      }
    }
  }
}

@Composable
private fun AddCourseSimulation(
    summary: GpaSummary,
    creditText: String,
    onCreditChange: (String) -> Unit,
    scoreText: String,
    onScoreChange: (String) -> Unit,
) {
  val credit = creditText.trim().toDoubleOrNull()
  val score = scoreText.trim().toDoubleOrNull()
  val simulation = simulateAddedCourse(summary, credit, score)

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
        value = creditText,
        onValueChange = onCreditChange,
        label = { Text("学分") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f),
    )
    OutlinedTextField(
        value = scoreText,
        onValueChange = onScoreChange,
        label = { Text("预计分数") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f),
    )
  }

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SimulationMetric(
        label = "模拟 GPA",
        value = simulation?.projectedGpa?.let(::formatGradePoint) ?: "--",
        modifier = Modifier.weight(1f),
    )
    SimulationMetric(
        label = "变化",
        value = formatDelta(simulation?.delta),
        valueColor =
            when {
              simulation?.delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
              simulation.delta > 0.0 -> MaterialTheme.colorScheme.primary
              simulation.delta < 0.0 -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.weight(1f),
    )
    SimulationMetric(
        label = "折算绩点",
        value = simulation?.addedGradePoint?.let(::formatGradePoint) ?: "--",
        modifier = Modifier.weight(1f),
    )
  }

  Text(
      text =
          if (simulation == null) {
            "请输入大于 0 的学分与 0-100 之间的分数"
          } else {
            "按北航官方公式 ${formatNumber(simulation.addedScore)} 分 → ${formatGradePoint(simulation.addedGradePoint)} 绩点；" +
                "$GPA_RULE_NOTE"
          },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun AdjustCoursesSimulation(
    breakdown: GpaBreakdown,
    summary: GpaSummary,
    adjustments: Map<String, Double>,
    onAdjustmentsChange: (Map<String, Double>) -> Unit,
) {
  val counted = breakdown.counted
  val adjustedSummary =
      remember(breakdown, adjustments) { breakdown.withAdjustments(adjustments).summary() }
  val adjustedGpa = adjustedSummary.gpa
  val delta =
      if (adjustedGpa != null && summary.gpa != null) {
        round((adjustedGpa - summary.gpa) * 100.0) / 100.0
      } else {
        null
      }

  if (counted.isEmpty()) {
    Text(
        text = "当前范围内没有可调分的课程",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SimulationMetric(
        label = "模拟 GPA",
        value = adjustedGpa?.let(::formatGradePoint) ?: "--",
        modifier = Modifier.weight(1f),
    )
    SimulationMetric(
        label = "变化",
        value = formatDelta(delta),
        valueColor =
            when {
              delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
              delta > 0.0 -> MaterialTheme.colorScheme.primary
              delta < 0.0 -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.weight(1f),
    )
    SimulationMetric(
        label = "已调分",
        value = "${adjustments.size} 门",
        modifier = Modifier.weight(1f),
    )
  }

  if (adjustments.isNotEmpty()) {
    TextButton(onClick = { onAdjustmentsChange(emptyMap()) }) { Text("重置调分") }
  }

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    counted.take(MAX_ADJUSTABLE_COURSES).forEach { record ->
      AdjustCourseRow(
          record = record,
          adjustedScore = adjustments[record.key],
          onAdjust = { newScore -> onAdjustmentsChange(adjustments + (record.key to newScore)) },
      )
    }
  }

  val hint =
      if (counted.size > MAX_ADJUSTABLE_COURSES) {
        "仅列出前 $MAX_ADJUSTABLE_COURSES 门课程（共 ${counted.size} 门）；$GPA_RULE_NOTE"
      } else {
        "调分后的绩点按北航官方公式重新估算；$GPA_RULE_NOTE"
      }
  Text(
      text = hint,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun AdjustCourseRow(
    record: GradeRecord,
    adjustedScore: Double?,
    onAdjust: (Double) -> Unit,
) {
  val baseScore = adjustedScore ?: record.numericScore ?: 60.0
  val displayScore = adjustedScore ?: record.numericScore
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = record.course.courseName ?: "未命名课程",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text =
              "原成绩 ${record.course.score?.takeIf { it.isNotBlank() } ?: "--"} · " +
                  "${formatNumber(record.credit ?: 0.0)} 学分",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = { onAdjust((baseScore - 5.0).coerceIn(0.0, 100.0)) }) {
      Icon(Icons.Default.Remove, contentDescription = "减 5 分")
    }
    Text(
        text = displayScore?.let(::formatNumber) ?: "--",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(44.dp),
    )
    IconButton(onClick = { onAdjust((baseScore + 5.0).coerceIn(0.0, 100.0)) }) {
      Icon(Icons.Default.Add, contentDescription = "加 5 分")
    }
  }
}

@Composable
private fun SimulationMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
  Column(modifier = modifier) {
    Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = valueColor,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** 变化量格式化：`+0.06` / `-0.05` / `0.00`。 */
internal fun formatDelta(value: Double?): String =
    when {
      value == null -> "--"
      value > 0.0 -> "+${formatGradePoint(value)}"
      value < 0.0 -> "-${formatGradePoint(-value)}"
      else -> formatGradePoint(0.0)
    }
