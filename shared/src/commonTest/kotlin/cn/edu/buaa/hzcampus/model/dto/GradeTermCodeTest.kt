package cn.edu.buaa.hzcampus.model.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GradeTermCodeTest {
  @Test
  fun supportsGraduateAndTraditionalTerms() {
    assertEquals(BuaaScoreTerm("2026-2027", 1), parseBuaaScoreTermCode("20261"))
    assertEquals(BuaaScoreTerm("2025-2026", 2), parseBuaaScoreTermCode("20252"))
    assertEquals(BuaaScoreTerm("2026-2027", 2), parseBuaaScoreTermCode(" 20262 "))
    assertEquals(BuaaScoreTerm("2026-2027", 3), parseBuaaScoreTermCode("20263"))
    assertEquals(BuaaScoreTerm("2026-2027", 1), parseBuaaScoreTermCode("2026-2027-1"))
    assertFailsWith<IllegalArgumentException> { parseBuaaScoreTermCode("invalid") }
  }
}
