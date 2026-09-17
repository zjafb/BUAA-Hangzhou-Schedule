package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.buaa.hzcampus.model.dto.Grade
import cn.edu.buaa.hzcampus.model.dto.GradeData
import cn.edu.buaa.hzcampus.model.dto.Term

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun GradeScreenScaffold(
    viewModel: GradeViewModel,
    content: @Composable (GradeUiState) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val pullRefreshState =
      rememberPullRefreshState(
          refreshing = uiState.isRefreshing,
          onRefresh = { viewModel.ensureLoaded(forceRefresh = true) },
      )

  Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    when {
      uiState.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      uiState.error != null -> {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
          Text(text = "加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }
      }
      else -> content(uiState)
    }
    PullRefreshIndicator(
        refreshing = uiState.isRefreshing,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

/**
 * 高级功能「成绩查询」：只做统计与分析——GPA 概览、各学期 GPA / 分数分布图表、绩点模拟。
 *
 * 逐门课程的明细（成绩、学分、学时等）已拆到普通功能的 [CourseQueryScreen]；两个页面共用同一个 ViewModel， 因此学期切换、下拉刷新与数据缓存完全一致。
 */
@Composable
fun GradeScreen(viewModel: GradeViewModel) {
  GradeScreenScaffold(viewModel) { uiState ->
    GradeAnalysisContent(
        grades = uiState.gradeData?.grades.orEmpty(),
        termGrades = uiState.termGrades,
        terms = uiState.terms,
        selectedTerm = uiState.selectedTerm,
        isSummaryLoading = uiState.isSummaryLoading,
    )
  }
}

/**
 * 普通功能「课程查询」：只列出逐门课程及其数据。
 *
 * 不含 GPA 统计、成绩分析图表与绩点模拟——这些在高级功能的 [GradeScreen] 中。
 */
@Composable
fun CourseQueryScreen(viewModel: GradeViewModel) {
  GradeScreenScaffold(viewModel) { uiState ->
    val grades = uiState.gradeData?.grades.orEmpty()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (grades.isEmpty()) {
        item {
          Box(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text("暂无成绩", style = MaterialTheme.typography.bodyLarge)
          }
        }
      } else {
        item {
          val totalHours = grades.sumOf { it.hours ?: 0.0 }
          Text(
              text =
                  "共 ${grades.size} 门课程 · " +
                      "${formatNumber(grades.sumOf { it.credit ?: 0.0 })} 学分" +
                      if (totalHours > 0.0) " · ${formatNumber(totalHours)} 学时" else "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        items(grades) { grade -> GradeCard(grade = grade) }
      }
    }
  }
}

/**
 * 成绩分析内容：顶部是 GPA 统计卡片，其下是成绩分析图表与绩点模拟。
 *
 * 统计范围（本学期 / 全部学期）在 GPA 卡片上切换，成绩分析与绩点模拟共用同一个范围，因此 切换学期或范围时三者会一起联动。
 */
@Composable
private fun GradeAnalysisContent(
    grades: List<Grade>,
    termGrades: Map<String, GradeData>,
    terms: List<Term>,
    selectedTerm: Term?,
    isSummaryLoading: Boolean,
) {
  var scope by remember { mutableStateOf(GpaScope.SELECTED_TERM) }
  var analysisView by remember { mutableStateOf(GradeAnalysisView.TERM_GPA) }

  val allGrades = remember(termGrades) { termGrades.values.flatMap { it.grades } }
  val scopedGrades = if (scope == GpaScope.ALL_TERMS) allGrades else grades
  val breakdown = remember(scopedGrades) { buildGpaBreakdown(scopedGrades) }
  val summary = remember(breakdown) { breakdown.summary() }
  val termPoints = remember(termGrades, terms) { termGpaPoints(termGrades, terms) }
  val scopedTermPoints =
      remember(termPoints, scope, selectedTerm) {
        if (scope == GpaScope.ALL_TERMS) termPoints
        else termPoints.filter { it.termCode == selectedTerm?.itemCode }
      }
  val previousTermPoint =
      remember(termPoints, selectedTerm) {
        val index = termPoints.indexOfFirst { it.termCode == selectedTerm?.itemCode }
        if (index > 0) termPoints[index - 1] else null
      }
  val buckets = remember(breakdown) { breakdown.scoreDistribution() }
  val skippedNote = remember(breakdown) { breakdown.skippedSummaryText() }

  val termLabel = termDisplayName(selectedTerm, selectedTerm?.itemCode ?: "本学期")
  val scopeLabel = if (scope == GpaScope.ALL_TERMS) "全部学期（${termGrades.size} 个已加载学期）" else termLabel

  LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      GpaOverviewCard(
          summary = summary,
          scope = scope,
          onScopeChange = { scope = it },
          scopeLabel = scopeLabel,
          skippedNote = skippedNote,
          isLoading = isSummaryLoading && scope == GpaScope.ALL_TERMS,
      )
    }
    item {
      GradeAnalysisCard(
          scopeLabel = if (scope == GpaScope.ALL_TERMS) "全部学期" else termLabel,
          view = analysisView,
          onViewChange = { analysisView = it },
          termPoints = scopedTermPoints,
          previousTermPoint = if (scope == GpaScope.ALL_TERMS) null else previousTermPoint,
          buckets = buckets,
      )
    }
    item { GpaSimulatorCard(breakdown = breakdown, summary = summary, scopeLabel = scopeLabel) }
  }
}

@Composable
private fun GradeCard(grade: Grade) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = grade.courseName ?: "未命名课程",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        GradeBadge(score = grade.score)
      }

      Spacer(modifier = Modifier.height(10.dp))
      gradeDetailRows(grade).forEach { row ->
        GradeInfoRow(
            label = row.label,
            value = row.value,
            icon = if (row.label == "成绩类型") Icons.Default.Person else null,
        )
      }
    }
  }
}

internal data class GradeDetailRow(val label: String, val value: String)

internal fun gradeDetailRows(grade: Grade): List<GradeDetailRow> =
    listOf(
            "课程号" to grade.courseCode,
            "学分" to grade.credit?.let(::formatNumber),
            "学时" to grade.hours?.takeIf { it > 0.0 }?.let(::formatNumber),
            "课程属性" to grade.courseAttribute,
            "课程类别" to (grade.courseCategory ?: grade.courseGroup),
            "考试性质" to grade.examType,
            "考试类型" to grade.examAttempt,
            "成绩类型" to grade.recognitionType,
        )
        .mapNotNull { (label, value) ->
          value?.takeIf { it.isNotBlank() }?.let { GradeDetailRow(label, it) }
        }

@Composable
private fun GradeBadge(score: String?) {
  Card(
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Default.Grade,
          contentDescription = null,
          modifier = Modifier.width(16.dp),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
          score?.takeIf { it.isNotBlank() } ?: "--",
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun GradeInfoRow(
    label: String,
    value: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  val displayValue = value?.takeIf { it.isNotBlank() } ?: return
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    icon?.let {
      Icon(
          it,
          contentDescription = null,
          modifier = Modifier.width(16.dp),
          tint = MaterialTheme.colorScheme.outline,
      )
      Spacer(modifier = Modifier.width(6.dp))
    }
    Text(
        "$label：",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(displayValue, style = MaterialTheme.typography.bodyMedium)
  }
}

internal fun formatNumber(value: Double): String {
  val rounded = kotlin.math.round(value * 100.0) / 100.0
  return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

internal fun formatGradePoint(value: Double): String {
  val rounded = kotlin.math.round(value * 100.0).toLong()
  val integerPart = rounded / 100
  val decimalPart = kotlin.math.abs(rounded % 100)
  return "$integerPart.${decimalPart.toString().padStart(2, '0')}"
}
