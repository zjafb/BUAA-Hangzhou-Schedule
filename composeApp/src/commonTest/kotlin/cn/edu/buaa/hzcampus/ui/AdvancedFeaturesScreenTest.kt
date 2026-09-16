package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.ui.screens.menu.advancedFeatureItems
import kotlin.test.Test
import kotlin.test.assertFalse

class AdvancedFeaturesScreenTest {
  @Test
  fun `advanced features do not include signin entry`() {
    assertFalse(advancedFeatureItems().any { it.id == "signin" })
  }
}
