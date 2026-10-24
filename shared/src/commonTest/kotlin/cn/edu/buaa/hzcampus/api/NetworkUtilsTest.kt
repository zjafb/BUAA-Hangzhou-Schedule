package cn.edu.buaa.hzcampus.api

import cn.edu.buaa.hzcampus.api.auth.userFacingMessageForCode
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkUtilsTest {

  @Test
  fun bykcSelectionErrorsPreserveActionableMessages() {
    assertEquals(
        "您已报名过该课程，请勿重复报名",
        userFacingMessageForCode("already_selected", HttpStatusCode.Conflict),
    )
    assertEquals(
        "该课程人数已满，请选择其他课程",
        userFacingMessageForCode("course_full", HttpStatusCode.Conflict),
    )
    assertEquals(
        "该课程当前不可报名",
        userFacingMessageForCode("course_not_selectable", HttpStatusCode.Conflict),
    )
  }

  @Test
  fun gradeErrorCodeHasUserFacingMessage() {
    assertEquals(
        "成绩查询失败，请稍后重试",
        userFacingMessageForCode("grade_error", HttpStatusCode.BadGateway),
    )
  }
}
