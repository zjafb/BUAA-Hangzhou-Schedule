package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.ui.common.util.shouldShowFullScreenReminder
import kotlin.test.*

class ReminderPresentationTest {
  @Test
  fun fullScreenRequiresOptInAndPermissionEvenWhenScreenIsOff() {
    assertFalse(shouldShowFullScreenReminder(false, true, false, true))
    assertFalse(shouldShowFullScreenReminder(true, false, false, true))
  }

  @Test
  fun neverInterruptUnlockedPhoneUseWithFullScreen() {
    assertFalse(shouldShowFullScreenReminder(true, true, true, false))
    assertTrue(shouldShowFullScreenReminder(true, true, false, false))
    assertTrue(shouldShowFullScreenReminder(true, true, true, true))
    assertTrue(shouldShowFullScreenReminder(true, true, false, true))
  }
}
