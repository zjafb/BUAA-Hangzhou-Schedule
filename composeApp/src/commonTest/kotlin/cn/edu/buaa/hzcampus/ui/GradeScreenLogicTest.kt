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
    // 官方公式：100 -> 4.0 绩点，80 -> 4 - 3*400/1600 = 3.25 绩点：(4.0*2 + 3.25*3) / 5 = 3.55
    assertEquals(3.55, statistics.gpa)
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
  fun `grade point follows the official buaa formula`() {
    // 北航官方公式：绩点 = 4 − 3 × (100 − X)² ÷ 1600（连续函数，不是分段表）
    assertEquals(4.0, gradePointFromScore100(100.0))
    assertEquals(3.953125, gradePointFromScore100(95.0))
    assertEquals(3.8125, gradePointFromScore100(90.0))
    assertEquals(3.578125, gradePointFromScore100(85.0))
    assertEquals(3.25, gradePointFromScore100(80.0))
    assertEquals(2.828125, gradePointFromScore100(75.0))
    assertEquals(2.3125, gradePointFromScore100(70.0))
    assertEquals(1.703125, gradePointFromScore100(65.0))
    // 60 分正好 1.0
    assertEquals(1.0, gradePointFromScore100(60.0))
    // 不及格记 0（公式在低分会算出负数，这里不采用）
    assertEquals(0.0, gradePointFromScore100(59.9))
    assertEquals(0.0, gradePointFromScore100(0.0))
    // 脏数据兜底：超过满分按满分算，非法值记 0
    assertEquals(4.0, gradePointFromScore100(105.0))
    assertEquals(0.0, gradePointFromScore100(Double.NaN))
    assertEquals(0.0, gradePointFromScore100(Double.POSITIVE_INFINITY))
  }

  @Test
  fun `official grade point wins over estimated grade point`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    Grade(courseName = "A", credit = 2.0, score = "80", gradePoint = "3.3"),
                    Grade(courseName = "B", credit = 2.0, score = "90"),
                )
            )
            .summary()

    // A 用官方绩点 3.3；B 没有官方绩点，90 分按官方公式估算成 3.8125：(3.3*2 + 3.8125*2) / 4 = 3.56
    assertEquals(3.56, summary.gpa)
    assertEquals(1, summary.estimatedCourses)
    assertTrue(summary.hasEstimate)
    assertEquals(2, summary.countedCourses)
    assertEquals(4.0, summary.countedCredits)
    assertEquals(4.0, summary.totalCredits)
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
    assertEquals(3.81, summary.gpa)
    assertEquals(5, summary.totalCourses)
    assertEquals(4, summary.skippedCourses)
    // 「全部」学分不含无效记录（3 学分的作弊记录）：0 + 2 + 0 + 1 + 3 = 6
    assertEquals(6.0, summary.totalCredits)
  }

  @Test
  fun `all courses totals include failed and pass-fail courses`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    grade(score = "90", credit = 3.0),
                    grade(score = "55", credit = 2.0),
                    grade(score = "通过", credit = 1.0),
                    grade(score = "不通过", credit = 1.0),
                    Grade(courseName = "无效", credit = 4.0, score = "90", effective = "否"),
                )
            )
            .summary()

    // 成绩条目总数：含未通过、两级制与无效记录
    assertEquals(5, summary.totalCourses)
    // 总学分：含未通过与两级制课程，不含无效记录（3 + 2 + 1 + 1）
    assertEquals(7.0, summary.totalCredits)
    // 计入 GPA 的只有 90 分那门（未通过、两级制、无效记录都不计入）
    assertEquals(1, summary.countedCourses)
    assertEquals(3.0, summary.countedCredits)
    assertEquals(3.81, summary.gpa)
  }

  @Test
  fun `total hours follow the all-course scope used by total credits`() {
    val summary =
        buildGpaBreakdown(
                listOf(
                    Grade(courseName = "有学时", credit = 3.0, score = "90", hours = 48.0),
                    Grade(courseName = "未通过", credit = 2.0, score = "50", hours = 32.0),
                    Grade(courseName = "两级制", credit = 1.0, score = "通过", hours = 16.0),
                    Grade(courseName = "无效", credit = 4.0, score = "90", hours = 64.0, effective = "否"),
                    Grade(courseName = "无学时", credit = 2.0, score = "80"),
                )
            )
            .summary()

    // 总学时与总学分同口径：含未通过、两级制，不含成绩无效的记录（48 + 32 + 16）
    assertEquals(96.0, summary.totalHours)
    // 计入 GPA 的只有「有学时」那门
    assertEquals(48.0, summary.countedHours)
    // 总学分同样不含无效记录：3 + 2 + 1 + 2 = 8
    assertEquals(8.0, summary.totalCredits)
  }

  @Test
  fun `placeholder zero grade point falls back to official formula`() {
    // 部分接口版本把缺席成绩的绩点写成 0，采信它会直接把 GPA 压成 0，因此 0 视为「没有官方绩点」
    val summary =
        buildGpaBreakdown(listOf(Grade(courseName = "A", credit = 2.0, score = "80", gradePoint = "0")))
            .summary()

    assertEquals(3.25, summary.gpa)
    assertEquals(1, summary.countedCourses)
  }

  @Test
  fun `grade detail rows show hours when the api provides them`() {
    val rows = gradeDetailRows(Grade(courseName = "高等数学", credit = 4.0, hours = 64.0))
    assertTrue(rows.any { it.label == "学时" && it.value == "64" })

    val withoutHours = gradeDetailRows(Grade(courseName = "高等数学", credit = 4.0))
    assertFalse(withoutHours.any { it.label == "学时" })
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
    // 80 分按官方公式 = 3.25 绩点
    assertEquals(3.25, summary.gpa)

    val simulation = simulateAddedCourse(summary, credit = 2.0, score = 90.0)

    assertNotNull(simulation)
    // 90 分按官方公式 = 3.8125 绩点
    assertEquals(3.8125, simulation.addedGradePoint)
    // (3.25*4 + 3.8125*2) / 6 = 3.4375 -> 3.44
    assertEquals(3.44, simulation.projectedGpa)
    assertEquals(0.19, simulation.delta)
    assertEquals("+0.19", formatDelta(simulation.delta))
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
    assertEquals(3.25, breakdown.summary().gpa)

    // 把 A 从 80 分提到 90 分：绩点 3.25 -> 3.8125，GPA (3.8125*2 + 3.25*2) / 4 = 3.53
    val adjusted = breakdown.withAdjustments(mapOf("1" to 90.0)).summary()

    assertEquals(3.53, adjusted.gpa)
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
    // 80 -> 3.25 绩点，90 -> 3.8125 绩点（官方公式）
    assertEquals(listOf(3.25, 3.81), points.map { it.summary.gpa })
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
