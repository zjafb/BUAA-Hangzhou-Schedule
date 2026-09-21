package cn.edu.buaa.hzcampus.ui.screens.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal typealias AdvancedFeatureItem = FeatureItem

internal fun advancedFeatureItems(): List<AdvancedFeatureItem> =
    listOf(
        AdvancedFeatureItem(
            id = "grade",
            title = "成绩查询",
            description = "GPA 统计、成绩分析图表与绩点模拟",
            icon = Icons.Default.BarChart,
        ),
        AdvancedFeatureItem(
            id = "evaluation",
            title = "自动评教",
            description = "一键完成学期末评教任务（可用性未知，待测试）",
            icon = Icons.Default.AssignmentTurnedIn,
        ),
    )

@Composable
fun AdvancedFeaturesScreen(
    onGradeClick: () -> Unit,
    onEvaluationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val features = advancedFeatureItems()

  FeatureGrid(modifier = modifier) {
    items(features, key = { it.id }) { feature ->
      FeatureCard(
          feature = feature,
          onClick = {
            when (feature.id) {
              "grade" -> onGradeClick()
              "evaluation" -> onEvaluationClick()
            }
          },
      )
    }
    // 与卡片放在同一滚动布局中，独占下一行，避免网格边界裁切卡片底部。
    item(span = { GridItemSpan(maxLineSpan) }) {
      Text(
          text = "更多高级功能正在开发中……",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )
    }
  }
}
