package cn.edu.buaa.hzcampus.ui.screens.bykc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.edu.buaa.hzcampus.model.dto.*
import cn.edu.buaa.hzcampus.ui.screens.menu.RegularFeaturesScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class BykcScreensTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun ordinaryEntryAndHomeCallbacksAreConnected() {
    var entered by mutableStateOf(false)
    var destination = ""
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(390.dp, 720.dp)) {
          if (!entered)
              RegularFeaturesScreen(
                  {},
                  {},
                  {},
                  {},
                  {},
                  {},
                  {},
                  {},
                  onBykcClick = { entered = true },
              )
          else
              BykcHomeScreen(
                  { destination = "courses" },
                  { destination = "chosen" },
                  { destination = "statistics" },
              )
        }
      }
    }
    compose.onNodeWithText("博雅课程").performScrollTo().performClick()
    compose.onNodeWithText("选择课程").performClick()
    assertEquals("courses", destination)
    compose.onNodeWithText("我的课程").performClick()
    assertEquals("chosen", destination)
    compose.onNodeWithText("课程统计").performClick()
    assertEquals("statistics", destination)
  }

  @Test
  fun courseListOpensTheSelectedCourse() {
    val item = BykcCourseDto(42, "博雅测试课程", status = BykcCourseStatus.AVAILABLE)
    var selected: Long? = null
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(390.dp, 720.dp)) {
          BykcCoursesScreen(
              listOf(item),
              false,
              false,
              false,
              defaultBykcCourseFilters(),
              null,
              rememberLazyListState(),
              { selected = it.id },
              {},
              {},
              {},
          )
        }
      }
    }
    compose.onNodeWithText(item.courseName).performClick()
    assertEquals(42L, selected)
  }

  @Test
  fun detailSelectsAndDeselectsOnlyWhenClicked() {
    var selected by mutableStateOf(false)
    var calls = 0
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(390.dp, 720.dp)) {
          BykcCourseDetailScreen(
              course =
                  BykcCourseDetailDto(
                      42,
                      "博雅测试课程",
                      status =
                          if (selected) BykcCourseStatus.SELECTED else BykcCourseStatus.AVAILABLE,
                      selected = selected,
                      courseMaxCount = 20,
                      courseCurrentCount = 2,
                  ),
              isLoading = false,
              error = null,
              operationInProgress = false,
              operationMessage = null,
              onSelectClick = {
                calls++
                selected = true
              },
              onDeselectClick = {
                calls++
                selected = false
              },
              onSignInClick = {},
              onSignOutClick = {},
          )
        }
      }
    }
    assertEquals(0, calls)
    compose.onNodeWithText("选择课程").performScrollTo().assertIsEnabled().performClick()
    assertEquals(1, calls)
    compose.onNodeWithText("退选").performScrollTo().performClick()
    assertEquals(2, calls)
  }
}
