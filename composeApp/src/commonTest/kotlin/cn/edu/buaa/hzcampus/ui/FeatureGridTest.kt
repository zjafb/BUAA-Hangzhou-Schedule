package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.ui.screens.menu.featureColumnCount
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureGridTest {
  @Test
  fun adaptsToAvailableWidthAndFontSize() {
    assertEquals(2, featureColumnCount(360f, 1f))
    assertEquals(1, featureColumnCount(280f, 1f))
    assertEquals(1, featureColumnCount(360f, 1.5f))
    assertEquals(4, featureColumnCount(1000f, 1f))
  }
}
