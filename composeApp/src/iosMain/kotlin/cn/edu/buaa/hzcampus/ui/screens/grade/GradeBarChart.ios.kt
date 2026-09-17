package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS 的成绩图表实现：沿用 commonMain 里零依赖的自绘 Canvas 柱状图。 */
@Composable
internal actual fun GradeBarChart(spec: GradeBarChartSpec, modifier: Modifier) {
  CanvasBarChart(spec = spec, modifier = modifier)
}
