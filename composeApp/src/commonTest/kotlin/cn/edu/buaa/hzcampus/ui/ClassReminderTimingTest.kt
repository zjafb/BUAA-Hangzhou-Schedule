package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.ui.common.util.classReminderTiming
import cn.edu.buaa.hzcampus.ui.common.util.classStartMillis
import cn.edu.buaa.hzcampus.ui.common.util.shouldScheduleClassReminder
import kotlin.test.*

class ClassReminderTimingTest {
  @Test
  fun courseTimeUsesShanghaiAndRejectsInvalidValues() {
    assertEquals(
        kotlin.time.Instant.parse("2026-10-24T00:00:00Z").toEpochMilliseconds(),
        classStartMillis("2026-10-24", "08:00-09:35"),
    )
    assertNull(classStartMillis("2026-10-24", "25:00-26:00"))
    assertNull(classStartMillis("invalid", "08:00-09:35"))
  }

  @Test
  fun reschedulingPreservesDueAlarmsWithoutReplayingDeliveredOrNewPastCourses() {
    val start = 3_000_000L
    val trigger = start - 30 * 60_000L
    assertTrue(shouldScheduleClassReminder(start, trigger, trigger - 1, false, false))
    assertTrue(shouldScheduleClassReminder(start, trigger, trigger, true, false))
    assertTrue(shouldScheduleClassReminder(start, trigger, trigger + 5 * 60_000L, true, false))
    assertFalse(shouldScheduleClassReminder(start, trigger, trigger + 1, false, false))
    assertFalse(shouldScheduleClassReminder(start, trigger, trigger + 1, true, true))
    assertFalse(shouldScheduleClassReminder(start, trigger, start + 10 * 60_000L, true, false))
  }

  @Test
  fun delayedDeliveryUsesActualTimeAndSuppressesExpiredClasses() {
    val now = 1_000_000L
    assertEquals(3L, assertNotNull(classReminderTiming(now + 150_000, now)).minutesUntilStart)
    assertEquals(0L, assertNotNull(classReminderTiming(now - 60_000, now)).minutesUntilStart)
    assertNull(classReminderTiming(now - 600_000, now))
  }
}
