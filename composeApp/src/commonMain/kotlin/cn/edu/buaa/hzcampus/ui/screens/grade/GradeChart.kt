package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 成绩分析图表的通用数据描述：一张「按 x 分类的柱状图」。
 *
 * 成绩页目前用它画两种视图：各学期 GPA 趋势、分数分布。把数据描述与绘制实现分开，是为了让 绘制部分能按平台替换（Android / 桌面 / iOS 走 Vico，Web 走这里的自绘
 * Canvas 兜底），而 统计口径、空态判断等逻辑留在公共代码里。
 */
internal data class GradeBarChartSpec(
    /** 每个柱子的横轴标签。 */
    val xLabels: List<String>,
    /** 每个柱子的数值，与 [xLabels] 一一对应。 */
    val values: List<Double>,
    /** 纵轴上限；null 表示按数据自动取值。 */
    val yMax: Double?,
    /** 数值标签的格式化方式。 */
    val valueFormatter: (Double) -> String,
)

/**
 * 柱状图渲染入口。
 *
 * 各平台提供实现：Android / 桌面 / iOS 使用 Vico，Web（js / wasmJs，Vico 没有对应产物）使用 [CanvasBarChart]
 * 自绘。数据为空时调用方会先显示空态，不会绘制空白图表。
 */
@Composable internal expect fun GradeBarChart(spec: GradeBarChartSpec, modifier: Modifier)

/**
 * 自绘 Canvas 柱状图，作为图表渲染的兜底实现（Web 端使用）。
 *
 * 只依赖 Compose 公共 API，因此在所有目标平台都能编译。数据为空时不会画任何东西，调用方需要先用 空态替代（见成绩分析卡片的 `空态` 分支）。
 */
@Composable
internal fun CanvasBarChart(spec: GradeBarChartSpec, modifier: Modifier = Modifier) {
  val textMeasurer = rememberTextMeasurer()
  val colorScheme = MaterialTheme.colorScheme
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
  val valueStyle =
      MaterialTheme.typography.labelSmall.copy(
          color = colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
      )
  val barColor = colorScheme.primary
  val axisColor = colorScheme.outlineVariant

  Canvas(modifier = modifier) {
    val count = minOf(spec.xLabels.size, spec.values.size)
    if (count <= 0 || size.width <= 0f || size.height <= 0f) return@Canvas

    val labelReserve = 22.dp.toPx()
    val valueReserve = 16.dp.toPx()
    val baselineY = size.height - labelReserve
    val drawableHeight = (baselineY - valueReserve).coerceAtLeast(1f)
    val maxValue =
        (spec.yMax ?: spec.values.take(count).max()).takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    val slot = size.width / count
    val barWidth = (slot * 0.5f).coerceIn(4.dp.toPx(), 40.dp.toPx())

    drawLine(
        color = axisColor,
        start = Offset(0f, baselineY),
        end = Offset(size.width, baselineY),
        strokeWidth = 1.dp.toPx(),
    )

    for (index in 0 until count) {
      val value = spec.values[index].takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
      val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f)
      val barHeight = drawableHeight * ratio
      val left = slot * index + (slot - barWidth) / 2f
      val top = baselineY - barHeight
      if (barHeight > 0.5f) {
        drawRoundRect(
            color = barColor,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
      }

      val valueLayout =
          textMeasurer.measure(
              text = AnnotatedString(spec.valueFormatter(value)),
              style = valueStyle,
              maxLines = 1,
          )
      drawText(
          textLayoutResult = valueLayout,
          topLeft =
              Offset(
                  clampStart(
                      slot * index + (slot - valueLayout.size.width) / 2f,
                      valueLayout.size.width,
                      size.width,
                  ),
                  (top - valueLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
              ),
      )

      val labelLayout =
          textMeasurer.measure(
              text = AnnotatedString(spec.xLabels[index]),
              style = labelStyle,
              maxLines = 1,
              constraints = Constraints(maxWidth = slot.toInt().coerceAtLeast(1)),
          )
      drawText(
          textLayoutResult = labelLayout,
          topLeft =
              Offset(
                  clampStart(
                      slot * index + (slot - labelLayout.size.width) / 2f,
                      labelLayout.size.width,
                      size.width,
                  ),
                  baselineY + 4.dp.toPx(),
              ),
      )
    }
  }
}

/** 把文本起点限制在画布内，避免标签被裁掉或越界。 */
private fun clampStart(start: Float, itemWidth: Int, totalWidth: Float): Float {
  val maxStart = totalWidth - itemWidth
  return if (maxStart <= 0f) 0f else start.coerceIn(0f, maxStart)
}
