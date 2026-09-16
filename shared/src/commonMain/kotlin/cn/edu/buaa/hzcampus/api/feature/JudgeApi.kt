package cn.edu.buaa.hzcampus.api.feature

import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.ApiClientProvider
import cn.edu.buaa.hzcampus.api.auth.safeApiCall
import cn.edu.buaa.hzcampus.api.core.ApiClient
import cn.edu.buaa.hzcampus.api.local.LocalJudgeHistoricalCourseStore
import cn.edu.buaa.hzcampus.api.local.resolveJudgeCourseSkipUserKey
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailDto
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailKeyDto
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailsRequest
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailsResponse
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentsResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface JudgeApiBackend {
  suspend fun getAssignments(
      includeExpired: Boolean = false,
      userKey: String? = null,
  ): Result<JudgeAssignmentsResponse>

  suspend fun getAssignmentDetail(
      courseId: String,
      assignmentId: String,
  ): Result<JudgeAssignmentDetailDto>

  suspend fun getAssignmentDetails(
      keys: List<JudgeAssignmentDetailKeyDto>
  ): Result<JudgeAssignmentDetailsResponse>
}

/** 希冀作业查询 API。 */
open class JudgeApi(
    private val backendProvider: () -> JudgeApiBackend = {
      ConnectionRuntime.apiFactory().judgeApi()
    }
) {
  internal constructor(backend: JudgeApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayJudgeApiBackend(apiClient) })

  private fun currentBackend(): JudgeApiBackend = backendProvider()

  /** 获取所有课程下的希冀作业摘要。 */
  open suspend fun getAssignments(
      includeExpired: Boolean = false,
      userKey: String? = null,
  ): Result<JudgeAssignmentsResponse> {
    return currentBackend().getAssignments(includeExpired, userKey)
  }

  /** 获取指定课程下的指定作业详情。 */
  open suspend fun getAssignmentDetail(
      courseId: String,
      assignmentId: String,
  ): Result<JudgeAssignmentDetailDto> {
    return currentBackend().getAssignmentDetail(courseId, assignmentId)
  }

  /** 批量获取希冀作业详情，用于列表摘要的增量补全。 */
  open suspend fun getAssignmentDetails(
      keys: List<JudgeAssignmentDetailKeyDto>
  ): Result<JudgeAssignmentDetailsResponse> {
    return currentBackend().getAssignmentDetails(keys)
  }
}

internal class RelayJudgeApiBackend(private val apiClient: ApiClient = ApiClientProvider.shared) :
    JudgeApiBackend {
  override suspend fun getAssignments(
      includeExpired: Boolean,
      userKey: String?,
  ): Result<JudgeAssignmentsResponse> {
    val mode = ConnectionRuntime.currentMode() ?: ConnectionMode.SERVER_RELAY
    val resolvedUserKey = resolveJudgeCourseSkipUserKey(userKey)
    val skippedCourseIds =
        if (includeExpired) emptySet()
        else LocalJudgeHistoricalCourseStore.get(mode, resolvedUserKey)
    val result =
        safeApiCall<JudgeAssignmentsResponse> {
          apiClient.getClient().get("api/v1/judge/assignments") {
            if (includeExpired) {
              parameter("includeExpired", true)
            }
            skippedCourseIds.forEach { courseId -> parameter("skipCourseId", courseId) }
          }
        }
    result.getOrNull()?.historicalCutoffCourseIds?.let { courseIds ->
      LocalJudgeHistoricalCourseStore.add(mode, resolvedUserKey, courseIds)
    }
    return result
  }

  override suspend fun getAssignmentDetail(
      courseId: String,
      assignmentId: String,
  ): Result<JudgeAssignmentDetailDto> {
    return safeApiCall {
      apiClient.getClient().get("api/v1/judge/courses/$courseId/assignments/$assignmentId")
    }
  }

  override suspend fun getAssignmentDetails(
      keys: List<JudgeAssignmentDetailKeyDto>
  ): Result<JudgeAssignmentDetailsResponse> {
    val distinctKeys = keys.distinct()
    if (distinctKeys.isEmpty()) {
      return Result.success(JudgeAssignmentDetailsResponse(emptyList()))
    }
    return safeApiCall {
      apiClient.getClient().post("api/v1/judge/assignment-details") {
        contentType(ContentType.Application.Json)
        setBody(JudgeAssignmentDetailsRequest(distinctKeys))
      }
    }
  }
}
