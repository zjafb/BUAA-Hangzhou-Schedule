package cn.edu.buaa.hzcampus.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.edu.buaa.hzcampus.ui.screens.menu.AdvancedFeaturesScreen
import cn.edu.buaa.hzcampus.ui.screens.menu.RegularFeaturesScreen
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class FeatureLayoutTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun advancedCardsMatchRegularCardsAndFooterDoesNotOverlap() {
    var advanced by mutableStateOf(false)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(360.dp, 700.dp)) {
          if (advanced) AdvancedFeaturesScreen({}, {})
          else RegularFeaturesScreen({}, {}, {}, {}, {}, {}, {}, {})
        }
      }
    }
    val regular = compose.onNodeWithText("课表查询").fetchSemanticsNode().boundsInRoot
    compose.runOnIdle { advanced = true }
    val card = compose.onNodeWithText("成绩查询").fetchSemanticsNode().boundsInRoot
    val second = compose.onNodeWithText("自动评教").fetchSemanticsNode().boundsInRoot
    val footer = compose.onNodeWithText("更多高级功能正在开发中……").fetchSemanticsNode().boundsInRoot
    assertEquals(regular.width, card.width)
    assertEquals(regular.height, card.height)
    assertTrue(footer.top >= maxOf(card.bottom, second.bottom))
  }
}
