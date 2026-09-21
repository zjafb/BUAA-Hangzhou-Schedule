package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.ui.common.util.classReminderTiming
import kotlin.test.*

class ClassReminderTimingTest {
  @Test
  fun delayedDeliveryUsesActualTimeAndSuppressesExpiredClasses() {
    val now = 1_000_000L
    assertEquals(3L, assertNotNull(classReminderTiming(now + 150_000, now)).minutesUntilStart)
    assertEquals(0L, assertNotNull(classReminderTiming(now - 60_000, now)).minutesUntilStart)
    assertNull(classReminderTiming(now - 600_000, now))
  }
}
