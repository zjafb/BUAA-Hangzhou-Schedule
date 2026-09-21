package cn.edu.buaa.hzcampus.ui.screens.schedule

import cn.edu.buaa.hzcampus.model.dto.PlanTask
import cn.edu.buaa.hzcampus.model.dto.SectionTime
import cn.edu.buaa.hzcampus.model.dto.Week
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanCellTest {
  private val week = Week("2026-09-14", "2026-09-20", "20261", true, 2, "第2周")
  private val times = listOf(SectionTime(3, "09:50", "10:35"), SectionTime(4, "10:40", "11:25"))

  @Test
  fun deadlineUsesEightNinthsOfThirdPeriod() {
    val cell =
        assertNotNull(
            planTaskToCell(PlanTask("a", "任务", "2026-09-14", "09:50", "10:30"), week, times)
        )
    assertEquals(0f, cell.startFraction)
    assertEquals(8f / 9f, cell.endFraction, 0.0001f)
    assertEquals(1, cell.span)
  }

  @Test
  fun partialStartAndEndAcrossPeriods() {
    val cell =
        assertNotNull(
            planTaskToCell(PlanTask("a", "任务", "2026-09-14", "10:05", "10:55"), week, times)
        )
    assertEquals(1f / 3f, cell.startFraction, 0.0001f)
    assertEquals(1f / 3f, cell.endFraction, 0.0001f)
    assertEquals(2, cell.span)
  }

  @Test
  fun invalidDurationDoesNotDrawAnInvertedCard() {
    assertNull(planTaskToCell(PlanTask("a", "任务", "2026-09-14", "10:05", "10:00"), week, times))
  }
}
