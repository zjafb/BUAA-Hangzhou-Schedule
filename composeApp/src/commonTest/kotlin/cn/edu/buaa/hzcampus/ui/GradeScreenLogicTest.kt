package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.model.dto.Grade
import cn.edu.buaa.hzcampus.model.dto.GradeData
import cn.edu.buaa.hzcampus.model.dto.Term
import cn.edu.buaa.hzcampus.ui.screens.grade.buildGpaBreakdown
import cn.edu.buaa.hzcampus.ui.screens.grade.calculateGradeStatistics
import cn.edu.buaa.hzcampus.ui.screens.grade.formatDelta
import cn.edu.buaa.hzcampus.ui.screens.grade.formatGradePoint
import cn.edu.buaa.hzcampus.ui.screens.grade.gradeDetailRows
import cn.edu.buaa.hzcampus.ui.screens.grade.gradePointFromScore100
import cn.edu.buaa.hzcampus.ui.screens.grade.scoreDistribution
import cn.edu.buaa.hzcampus.ui.screens.grade.shortTermLabel
import cn.edu.buaa.hzcampus.ui.screens.grade.simulateAddedCourse
import cn.edu.buaa.hzcampus.ui.screens.grade.summary
import cn.edu.buaa.hzcampus.ui.screens.grade.termGpaPoints
import cn.edu.buaa.hzcampus.ui.screens.grade.withAdjustments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradeScreenLogicTest {
  @Test
  fun `grade point formatting keeps two decimal places`() {
    assertEquals("2.40", formatGradePoint(2.4))
    assertEquals("3.56", formatGradePoint(3.555))
    assertEquals("4.00", formatGradePoint(3.999))
  }

  @Test
  fun `grade detail rows omit grade point`() {
    val rows =
        gradeDetailRows(
            Grade(
                courseCode = "MATH001",
                credit = 4.0,
                gradePoint = "4.0",
                courseAttribute = "必修",
                recognitionType = "百分制",
            )
        )

    assertFalse(rows.any { it.label == "绩点" })
    assertFalse(rows.any { it.value == "4.0" && it.label == "绩点" })
    assertTrue(rows.any { it.label == "课程号" && it.value == "MATH001" })
  }

  @Test
  fun `grade statistics weight numeric scores by credits`() {
    val statistics =
        calculateGradeStatistics(
            listOf(
                grade(score = "100", credit = 2.0),
                grade(score = "80", credit = 3.0),
            )
        )

    assertEquals(2, statistics.courseCount)
    assertEquals(5.0, statistics.totalCredits)
    // 100 -> 4.0 绩点，80 -> 3.0 绩点：(4.0*2 + 3.0*3) / 5 = 3.4
    assertEquals(3.4, statistics.gpa)
    assertEquals(88.0, statistics.weightedAverage)
    assertEquals(90.0, statistics.arithmeticAverage)
  }

  @Test
  fun `grade statistics convert level scores and skip pass fail courses`() {
    val statistics =
        calculateGradeStatistics(
            listOf(
                grade(score = "优", credit = 2.0),
                grade(score = "良", credit = 2.0),
                grade(score = "通过", credit = 5.0),
                grade(score = "不通过", credit = 5.0),
            )
        )

    assertEquals(4, statistics.courseCount)
    assertEquals(14.0, statistics.totalCredits)
    assertEquals(3.75, statistics.gpa)
    assertEquals(85.0, statistics.weightedAverage)
    assertEquals(85.0, statistics.arithmeticAverage)
  }

  @Test
  fun `grade statistics convert long level scores`() {
    val statistics =
        calculateGradeStatistics(
            listOf(
                grade(score = "优秀", credit = 1.0),
                grade(score = "良好", credit = 1.0),
                grade(score = "中等", credit = 1.0),
                grade(score = "及格", credit = 1.0),
                grade(score = "不及格", credit = 1.0),
            )
        )

    assertEquals(5, statistics.courseCount)
    assertEquals(5.0, statistics.totalCredits)
    // 不及格不参与 GPA：(4.0 + 3.5 + 2.8 + 1.7) / 4 = 3.0
    assertEquals(3.0, statistics.gpa)
    // 平均分包含未通过课程：(90 + 80 + 70 + 60 + 0) / 5 = 60
    assertEquals(60.0, statistics.weightedAverage)
    assertEquals(60.0, statistics.arithmeticAverage)
  }

  @Test
  fun `grade statistics return empty averages when no course participates`() {
    val statistics =
        calculateGradeStatistics(
            listOf(
                grade(score = "通过", credit = 2.0),
                grade(score = "不通过", credit = 2.0),
            )
        )

    assertEquals(2, statistics.courseCount)
    assertEquals(4.0, statistics.totalCredits)
    assertNull(statistics.gpa)
    assertNull(statistics.weightedAverage)
    assertNull(statistics.arithmeticAverage)
  }

  @Test
  fun `score to grade point table follows the common four point scale`() {
    assertEquals(4.0, gradePointFromScore100(100.0))
    assertEquals(4.0, gradePointFromScore100(90.0))
    assertEquals(3.7, gradePointFromScore100(85.0))
    assertEquals(3.3, gradePointFromScore100(84.0))
    assertEquals(3.0, gradePointFromScore100(78.0))
    assertEquals(2.7, gradePointFromScore100(75.0))
    assertEquals(2.3, gradePointFromScore100(72.0))
    assertEquals(2.0, gradePointFromScore100(68.0))
    assertEquals(1.5, gradePointFromScore100(64.0))
    assertEquals(1.0, gradePointFromScore100(60.0))
    assertEquals(0.0, gradePointFromScore100(59.9))
  }

  @Test
  fun `official grade point wins over estimated grade point`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    Grade(courseName = "A", credit = 2.0, score = "80", gradePoint = "3.3"),
                    Grade(courseName = "B", credit = 2.0, score = "80"),
                )
            )
            .summary()

    // A 用官方绩点 3.3，B 没有官方绩点按 80 分估算成 3.0：(3.3*2 + 3.0*2) / 4 = 3.15
    assertEquals(3.15, summary.gpa)
    assertEquals(1, summary.estimatedCourses)
    assertTrue(summary.hasEstimate)
    assertEquals(2, summary.countedCourses)
    assertEquals(4.0, summary.countedCredits)
  }

  @Test
  fun `grades without credit failed or invalid courses are excluded`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    Grade(courseName = "无学分", credit = 0.0, score = "90"),
                    Grade(courseName = "未通过", credit = 2.0, score = "50", passed = "否"),
                    Grade(courseName = "无效", credit = 3.0, score = "90", effective = "否"),
                    Grade(courseName = "通过制", credit = 1.0, score = "通过"),
                    Grade(courseName = "正常", credit = 3.0, score = "90"),
                )
            )
            .summary()

    assertEquals(1, summary.countedCourses)
    assertEquals(3.0, summary.countedCredits)
    assertEquals(4.0, summary.gpa)
    assertEquals(5, summary.totalCourses)
    assertEquals(4, summary.skippedCourses)
  }

  @Test
  fun `score distribution buckets bypassed courses into the lowest bucket`() {
    val buckets =
        buildGpaBreakdown(
                listOf(
                    grade(score = "95", credit = 3.0),
                    grade(score = "85", credit = 2.0),
                    grade(score = "72", credit = 1.0),
                    grade(score = "65", credit = 1.0),
                    grade(score = "45", credit = 2.0),
                    grade(score = "通过", credit = 1.0),
                )
            )
            .scoreDistribution()

    assertEquals(listOf("<60", "60-69", "70-79", "80-89", "90-100"), buckets.map { it.label })
    assertEquals(listOf(1, 1, 1, 1, 1), buckets.map { it.courseCount })
    assertEquals(2.0, buckets.first().credits)
  }

  @Test
  fun `added course simulation projects the new gpa and delta`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    grade(score = "80", credit = 2.0),
                    grade(score = "80", credit = 2.0),
                )
            )
            .summary()
    assertEquals(3.0, summary.gpa)

    val simulation = simulateAddedCourse(summary, credit = 2.0, score = 90.0)

    assertNotNull(simulation)
    assertEquals(4.0, simulation.addedGradePoint)
    // (3.0*4 + 4.0*2) / 6 = 3.33
    assertEquals(3.33, simulation.projectedGpa)
    assertEquals(0.33, simulation.delta)
    assertEquals("+0.33", formatDelta(simulation.delta))
  }

  @Test
  fun `added course simulation rejects invalid input`() {
    val summary = buildGpaBreakdown(listOf(grade(score = "80", credit = 2.0))).summary()

    assertNull(simulateAddedCourse(summary, credit = 0.0, score = 90.0))
    assertNull(simulateAddedCourse(summary, credit = 2.0, score = null))
  }

  @Test
  fun `course adjustment simulation recalculates the gpa`() {
    val breakdown =
        buildGpaBreakdown(
            listOf(
                Grade(id = "1", courseName = "A", credit = 2.0, score = "80"),
                Grade(id = "2", courseName = "B", credit = 2.0, score = "80"),
            )
        )
    assertEquals(3.0, breakdown.summary().gpa)

    // 把 A 从 80 分提到 90 分：绩点 3.0 -> 4.0
    val adjusted = breakdown.withAdjustments(mapOf("1" to 90.0)).summary()

    assertEquals(3.5, adjusted.gpa)
    assertEquals(2, adjusted.countedCourses)
  }

  @Test
  fun `term gpa points are ordered by term code`() {
    val points =
        termGpaPoints(
            termGrades =
                mapOf(
                    "2024-2025-1" to
                        GradeData("2024-2025-1", listOf(grade(score = "90", credit = 2.0))),
                    "2023-2024-2" to
                        GradeData("2023-2024-2", listOf(grade(score = "80", credit = 2.0))),
                ),
            terms =
                listOf(
                    Term(
                        itemCode = "2024-2025-1",
                        itemName = "2024-2025学年第一学期",
                        selected = true,
                        itemIndex = 1,
                    ),
                    Term(
                        itemCode = "2023-2024-2",
                        itemName = "2023-2024学年第二学期",
                        selected = false,
                        itemIndex = 0,
                    ),
                ),
        )

    assertEquals(listOf("2023-2024-2", "2024-2025-1"), points.map { it.termCode })
    assertEquals(listOf(3.0, 4.0), points.map { it.summary.gpa })
    assertEquals("2024-2025学年第一学期", points.last().termName)
  }

  @Test
  fun `term code is shortened for chart labels`() {
    assertEquals("24-25-1", shortTermLabel("2024-2025-1"))
    assertEquals("23-24-2", shortTermLabel("2023-2024-2"))
    assertEquals("自定义学期", shortTermLabel("自定义学期"))
  }
}

private fun grade(score: String, credit: Double): Grade = Grade(score = score, credit = credit)
