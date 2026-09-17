package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** JVM 桌面端（Compose Desktop）的成绩图表实现：沿用 commonMain 里零依赖的自绘 Canvas 柱状图， 桌面端不额外引入图表库。 */
@Composable
internal actual fun GradeBarChart(spec: GradeBarChartSpec, modifier: Modifier) {
  CanvasBarChart(spec = spec, modifier = modifier)
}
