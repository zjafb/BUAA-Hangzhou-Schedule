package cn.edu.buaa.hzcampus.api

import cn.edu.buaa.hzcampus.api.storage.CourseAttributeStore
import cn.edu.buaa.hzcampus.model.dto.Grade
import cn.edu.buaa.hzcampus.model.dto.GradeData
import cn.edu.buaa.hzcampus.model.dto.courseAttributeBadgeLabel
import cn.edu.buaa.hzcampus.model.dto.courseAttributesByName
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CourseAttributeStoreTest {
  @AfterTest
  fun tearDown() {
    CourseAttributeStore.settings = MapSettings()
  }

  @Test
  fun `attributes accumulate across terms and are read by course name`() {
    CourseAttributeStore.settings = MapSettings()

    CourseAttributeStore.putAll(mapOf("高等数学" to "必修"))
    CourseAttributeStore.putAll(mapOf("大学英语" to "选修"))

    assertEquals("必修", CourseAttributeStore.get("高等数学"))
    assertEquals("选修", CourseAttributeStore.get(" 大学英语 "))
    assertEquals(mapOf("高等数学" to "必修", "大学英语" to "选修"), CourseAttributeStore.all())
  }

  @Test
  fun `later writes win and blank entries are ignored`() {
    CourseAttributeStore.settings = MapSettings()

    CourseAttributeStore.putAll(mapOf("数据结构" to "选修"))
    CourseAttributeStore.putAll(mapOf("数据结构" to "必修", "   " to "必修", "操作系统" to "  "))

    assertEquals("必修", CourseAttributeStore.get("数据结构"))
    assertNull(CourseAttributeStore.get("操作系统"))
  }

  @Test
  fun `unknown course name or empty store returns null`() {
    CourseAttributeStore.settings = MapSettings()

    assertNull(CourseAttributeStore.get("从没见过的课"))
    assertNull(CourseAttributeStore.get(null))

    CourseAttributeStore.putAll(mapOf("线性代数" to "必修"))
    CourseAttributeStore.clear()

    assertEquals(emptyMap(), CourseAttributeStore.all())
    assertNull(CourseAttributeStore.get("线性代数"))
  }

  @Test
  fun `grade data projects non blank course attributes by course name`() {
    val gradeData =
        GradeData(
            termCode = "2025-2026-1",
            grades =
                listOf(
                    Grade(courseName = "高等数学", courseAttribute = " 必修 "),
                    Grade(courseName = "大学英语", courseAttribute = "选修"),
                    Grade(courseName = "无性质课", courseAttribute = null),
                    Grade(courseName = null, courseAttribute = "必修"),
                ),
        )

    assertEquals(
        mapOf("高等数学" to "必修", "大学英语" to "选修"),
        gradeData.courseAttributesByName(),
    )
  }

  @Test
  fun `badge label classifies required and elective attributes`() {
    assertEquals("必", courseAttributeBadgeLabel("必修"))
    assertEquals("必", courseAttributeBadgeLabel(" 必修 "))
    assertEquals("必", courseAttributeBadgeLabel("学位必修课"))
    assertEquals("选", courseAttributeBadgeLabel("选修"))
    assertEquals("选", courseAttributeBadgeLabel("任选"))
    assertEquals("选", courseAttributeBadgeLabel("限选"))
    assertEquals("选", courseAttributeBadgeLabel("公选"))
    assertEquals("选", courseAttributeBadgeLabel("专业选修课"))
    assertNull(courseAttributeBadgeLabel(null))
    assertNull(courseAttributeBadgeLabel(""))
    assertNull(courseAttributeBadgeLabel("   "))
    assertNull(courseAttributeBadgeLabel("学位课"))
  }
}
