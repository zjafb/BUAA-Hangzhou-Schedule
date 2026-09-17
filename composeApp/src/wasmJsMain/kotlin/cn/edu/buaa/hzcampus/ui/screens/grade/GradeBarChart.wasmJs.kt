package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Wasm 浏览器端的成绩图表实现：Vico 没有发布 Wasm 产物，因此沿用 commonMain 里零依赖的自绘 Canvas 柱状图。 */
@Composable
internal actual fun GradeBarChart(spec: GradeBarChartSpec, modifier: Modifier) {
  CanvasBarChart(spec = spec, modifier = modifier)
}
