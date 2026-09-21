package cn.edu.buaa.hzcampus.ui.screens.judge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.ApiCallException
import cn.edu.buaa.hzcampus.api.feature.JudgeApi
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailDto
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentDetailKeyDto
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentSummaryDto
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentsResponse
import cn.edu.buaa.hzcampus.model.dto.JudgeSubmissionStatus
import cn.edu.buaa.hzcampus.ui.common.util.requestResult
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class JudgeSortField {
  START_TIME,
  DUE_TIME,
}

data class JudgeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val assignmentsResponse: JudgeAssignmentsResponse? = null,
    val visibleAssignments: List<JudgeAssignmentSummaryDto> = emptyList(),
    val error: String? = null,
    val isEnrichingAssignments: Boolean = false,
    val isDetailLoading: Boolean = false,
    val assignmentDetail: JudgeAssignmentDetailDto? = null,
    val detailError: String? = null,
    val searchQuery: String = "",
    val sortField: JudgeSortField = JudgeSortField.DUE_TIME,
    val sortAscending: Boolean = true,
    val showExpired: Boolean = false,
    val showOnlyUnfinished: Boolean = true,
)

/** 希冀作业查询模块 ViewModel。 */
@OptIn(ExperimentalTime::class)
class JudgeViewModel(
    private val judgeApi: JudgeApi = JudgeApi(),
    private val userKey: String? = null,
    private val nowProvider: () -> LocalDateTime = {
      Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    },
) : ViewModel() {
  private var assignmentsLoadedOnce = false
  private var assignmentLoadVersion = 0
  private var assignmentsJob: Job? = null
  private var detailJob: Job? = null
  private var assignmentDetailEnrichmentJob: Job? = null
  private val assignmentDetailCache = mutableMapOf<String, JudgeAssignmentDetailDto>()
  private val _uiState = MutableStateFlow(JudgeUiState())
  val uiState: StateFlow<JudgeUiState> = _uiState.asStateFlow()

  fun ensureAssignmentsLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && (assignmentsLoadedOnce || assignmentsJob?.isActive == true)) return
    loadAssignments(refresh = forceRefresh)
  }

  internal fun hasAssignmentsLoaded(): Boolean = assignmentsLoadedOnce

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    assignmentLoadVersion++
    assignmentsJob?.cancel()
    detailJob?.cancel()
    assignmentDetailEnrichmentJob?.cancel()
    assignmentsLoadedOnce = false
    assignmentDetailCache.clear()
    _uiState.value = JudgeUiState()
  }

  fun loadAssignments(refresh: Boolean = false) {
    assignmentsJob?.cancel()
    assignmentsLoadedOnce = false
    assignmentLoadVersion++
    val loadVersion = assignmentLoadVersion
    val includeExpired = _uiState.value.showExpired
    assignmentDetailEnrichmentJob?.cancel()
    assignmentDetailCache.clear()
    assignmentsJob =
        viewModelScope.launch {
          val hasExistingData = _uiState.value.assignmentsResponse != null
          _uiState.value =
              _uiState.value.copy(
                  isLoading = !hasExistingData,
                  isRefreshing = refresh || hasExistingData,
                  isEnrichingAssignments = false,
                  error = null,
              )

          // 摘要阶段加了超时兜底：任何情况下都不能让待办区永远停在 loading。
          val result =
              withTimeoutOrNull(JUDGE_ASSIGNMENTS_TIMEOUT_MILLIS) {
                requestResult {
                  judgeApi.getAssignments(includeExpired = includeExpired, userKey = userKey)
                }
              }
          if (loadVersion != assignmentLoadVersion) return@launch
          if (result == null) {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isEnrichingAssignments = false,
                    error = judgeAssignmentsTimeoutMessage(),
                )
            return@launch
          }

          result
              .onSuccess { response ->
                assignmentsLoadedOnce = true
                val currentState = _uiState.value
                val shouldEnrichAssignments =
                    response.assignments.any { it.needsDetailEnrichment() }
                _uiState.value =
                    currentState.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isEnrichingAssignments = shouldEnrichAssignments,
                        assignmentsResponse = response,
                        visibleAssignments =
                            buildVisibleAssignments(response.assignments, currentState),
                        error = null,
                    )
                startAssignmentDetailEnrichment(response.assignments, loadVersion)
              }
              .onFailure { exception ->
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isEnrichingAssignments = false,
                        error = judgeAssignmentsErrorMessage(exception),
                    )
              }
        }
  }

  fun setSearchQuery(query: String) {
    updateVisibleAssignments { copy(searchQuery = query) }
  }

  fun setSortField(field: JudgeSortField) {
    updateVisibleAssignments { copy(sortField = field) }
  }

  fun toggleSortDirection() {
    updateVisibleAssignments { copy(sortAscending = !sortAscending) }
  }

  fun setShowExpired(enabled: Boolean) {
    val shouldRefresh = enabled && !_uiState.value.showExpired
    updateVisibleAssignments {
      copy(
          showExpired = enabled,
          showOnlyUnfinished = if (enabled) false else showOnlyUnfinished,
      )
    }
    if (shouldRefresh) {
      loadAssignments(refresh = true)
    }
  }

  fun setShowOnlyUnfinished(enabled: Boolean) {
    updateVisibleAssignments { copy(showOnlyUnfinished = enabled && !showExpired) }
  }

  fun loadAssignmentDetail(courseId: String, assignmentId: String) {
    detailJob?.cancel()
    assignmentDetailCache[detailCacheKey(courseId, assignmentId)]?.let { cachedDetail ->
      _uiState.value =
          _uiState.value.copy(
              isDetailLoading = false,
              assignmentDetail = cachedDetail,
              detailError = null,
          )
      return
    }

    detailJob =
        viewModelScope.launch {
          _uiState.value =
              _uiState.value.copy(
                  isDetailLoading = true,
                  assignmentDetail = null,
                  detailError = null,
              )

          requestResult { judgeApi.getAssignmentDetail(courseId, assignmentId) }
              .onSuccess { detail ->
                assignmentDetailCache[detailCacheKey(detail.courseId, detail.assignmentId)] = detail
                _uiState.value =
                    _uiState.value.copy(
                        isDetailLoading = false,
                        assignmentDetail = detail,
                        detailError = null,
                    )
              }
              .onFailure { exception ->
                _uiState.value =
                    _uiState.value.copy(
                        isDetailLoading = false,
                        assignmentDetail = null,
                        detailError = exception.message ?: "加载作业详情失败",
                    )
              }
        }
  }

  fun clearAssignmentDetail() {
    detailJob?.cancel()
    _uiState.value =
        _uiState.value.copy(
            isDetailLoading = false,
            assignmentDetail = null,
            detailError = null,
        )
  }

  private fun updateVisibleAssignments(transform: JudgeUiState.() -> JudgeUiState) {
    val nextState = _uiState.value.transform()
    _uiState.value =
        nextState.copy(
            visibleAssignments =
                buildVisibleAssignments(
                    nextState.assignmentsResponse?.assignments.orEmpty(),
                    nextState,
                )
        )
  }

  private fun startAssignmentDetailEnrichment(
      summaries: List<JudgeAssignmentSummaryDto>,
      loadVersion: Int,
  ) {
    assignmentDetailEnrichmentJob?.cancel()
    val summariesToEnrich = summaries.filter { it.needsDetailEnrichment() }
    if (summariesToEnrich.isEmpty()) {
      _uiState.value = _uiState.value.copy(isEnrichingAssignments = false)
      return
    }

    assignmentDetailEnrichmentJob =
        viewModelScope.launch {
          try {
            withTimeoutOrNull(JUDGE_ASSIGNMENTS_TIMEOUT_MILLIS) {
              summariesToEnrich.enrichDetailsInBatches(loadVersion)
            }
          } finally {
            // 无论成功、失败还是被新一次加载取消，都要结束「补全中」状态。
            if (loadVersion == assignmentLoadVersion) {
              val currentState = _uiState.value
              _uiState.value =
                  currentState.copy(
                      isEnrichingAssignments = false,
                      visibleAssignments =
                          buildVisibleAssignments(
                              currentState.assignmentsResponse?.assignments.orEmpty(),
                              currentState,
                          ),
                  )
            }
          }
        }
  }

  private suspend fun List<JudgeAssignmentSummaryDto>.enrichDetailsInBatches(loadVersion: Int) {
    val keys =
        map { summary ->
              JudgeAssignmentDetailKeyDto(
                  courseId = summary.courseId,
                  assignmentId = summary.assignmentId,
              )
            }
            .distinct()
    for (chunk in keys.chunked(JUDGE_DETAIL_ENRICHMENT_BATCH_SIZE)) {
      if (loadVersion != assignmentLoadVersion) return
      val batchResult = requestResult { judgeApi.getAssignmentDetails(chunk) }
      if (loadVersion != assignmentLoadVersion) return
      val details = batchResult.getOrNull()?.details.orEmpty()
      details.forEach { detail ->
        assignmentDetailCache[detailCacheKey(detail.courseId, detail.assignmentId)] = detail
        applyEnrichedAssignmentDetail(detail, loadVersion)
      }
      val loadedKeys = details.map { detailCacheKey(it.courseId, it.assignmentId) }.toSet()
      // 批量接口不可达时停止补全，避免再串行发送一整批必然失败的请求。
      if (batchResult.isFailure) return
      val missingKeys =
          chunk.filter { key -> detailCacheKey(key.courseId, key.assignmentId) !in loadedKeys }
      missingKeys.forEach { key ->
        if (loadVersion != assignmentLoadVersion) return
        val detail =
            requestResult { judgeApi.getAssignmentDetail(key.courseId, key.assignmentId) }
                .getOrNull() ?: return@forEach
        assignmentDetailCache[detailCacheKey(detail.courseId, detail.assignmentId)] = detail
        applyEnrichedAssignmentDetail(detail, loadVersion)
      }
    }
  }

  private fun applyEnrichedAssignmentDetail(
      detail: JudgeAssignmentDetailDto,
      loadVersion: Int,
  ) {
    if (loadVersion != assignmentLoadVersion) return
    _uiState.update { currentState ->
      val currentAssignments = currentState.assignmentsResponse?.assignments.orEmpty()
      val updatedAssignments =
          currentAssignments.map { assignment ->
            if (
                assignment.courseId == detail.courseId &&
                    assignment.assignmentId == detail.assignmentId
            ) {
              detail.toSummary()
            } else {
              assignment
            }
          }
      val response =
          JudgeAssignmentsResponse(
              assignments = updatedAssignments,
              historicalCutoffCourseIds =
                  currentState.assignmentsResponse?.historicalCutoffCourseIds.orEmpty(),
          )
      currentState.copy(
          assignmentsResponse = response,
          visibleAssignments = buildVisibleAssignments(response.assignments, currentState),
      )
    }
  }

  private fun buildVisibleAssignments(
      assignments: List<JudgeAssignmentSummaryDto>,
      state: JudgeUiState,
  ): List<JudgeAssignmentSummaryDto> {
    val query = state.searchQuery.trim().lowercase()

    return assignments
        .asSequence()
        .filter { assignment ->
          query.isBlank() ||
              assignment.courseName.lowercase().contains(query) ||
              assignment.title.lowercase().contains(query)
        }
        .filter { assignment -> !state.showOnlyUnfinished || assignment.isUnfinished() }
        .filter { assignment -> state.showExpired || !isExpired(assignment) }
        .sortedWith(compareAssignments(state.sortField, state.sortAscending))
        .toList()
  }

  private fun compareAssignments(
      sortField: JudgeSortField,
      ascending: Boolean,
  ): Comparator<JudgeAssignmentSummaryDto> {
    val comparator =
        Comparator<JudgeAssignmentSummaryDto> { left, right ->
          val leftTime = parseDateTime(resolveSortValue(left, sortField))
          val rightTime = parseDateTime(resolveSortValue(right, sortField))
          when {
            leftTime == null && rightTime == null ->
                compareValuesBy(left, right, { it.courseName }, { it.title })
            leftTime == null -> 1
            rightTime == null -> -1
            else -> {
              val timeComparison = leftTime.compareTo(rightTime)
              if (timeComparison != 0) {
                timeComparison
              } else {
                compareValuesBy(left, right, { it.courseName }, { it.title })
              }
            }
          }
        }
    return if (ascending) comparator else comparator.reversed()
  }

  private fun resolveSortValue(
      assignment: JudgeAssignmentSummaryDto,
      sortField: JudgeSortField,
  ): String? =
      when (sortField) {
        JudgeSortField.START_TIME -> assignment.startTime
        JudgeSortField.DUE_TIME -> assignment.dueTime
      }

  private fun isExpired(assignment: JudgeAssignmentSummaryDto): Boolean {
    val dueTime = parseDateTime(assignment.dueTime) ?: return false
    return dueTime < nowProvider()
  }

  private fun parseDateTime(value: String?): LocalDateTime? {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() }?.replace(" ", "T") ?: return null
    return runCatching { LocalDateTime.parse(normalized) }.getOrNull()
  }

  /** 摘要阶段的「未完成」判定：除已提交外都算未完成。 摘要在详情补全之前状态为 UNKNOWN，若把它排除会让列表和待办区先空一阵再突然出现。 */
  private fun JudgeAssignmentSummaryDto.isUnfinished(): Boolean =
      submissionStatus != JudgeSubmissionStatus.SUBMITTED

  private fun JudgeAssignmentSummaryDto.needsDetailEnrichment(): Boolean =
      startTime.isNullOrBlank() ||
          dueTime.isNullOrBlank() ||
          submissionStatus == JudgeSubmissionStatus.UNKNOWN

  private fun JudgeAssignmentDetailDto.toSummary(): JudgeAssignmentSummaryDto =
      JudgeAssignmentSummaryDto(
          courseId = courseId,
          courseName = courseName,
          assignmentId = assignmentId,
          title = title,
          startTime = startTime,
          dueTime = dueTime,
          maxScore = maxScore,
          myScore = myScore,
          totalProblems = totalProblems,
          submittedCount = submittedCount,
          submissionStatus = submissionStatus,
          submissionStatusText = submissionStatusText,
      )

  private fun detailCacheKey(courseId: String, assignmentId: String): String =
      "$courseId:$assignmentId"

  companion object {
    private const val JUDGE_DETAIL_ENRICHMENT_BATCH_SIZE = 12

    /** 摘要阶段超时上限，避免首页待办区因为网络问题无限期停在加载状态。 */
    private const val JUDGE_ASSIGNMENTS_TIMEOUT_MILLIS = 45_000L
  }
}

/** 中转服务不可用时给出可执行的恢复方式，避免把后端故障笼统显示为希冀超时。 */
internal fun judgeAssignmentsTimeoutMessage(): String =
    if (ConnectionRuntime.currentMode() == ConnectionMode.SERVER_RELAY) {
      "中转服务器请求超时，请在设置中切换为直连模式或 WebVPN 模式后重试"
    } else {
      "希冀作业同步超时，请下拉刷新或稍后重试"
    }

internal fun judgeAssignmentsErrorMessage(error: Throwable): String {
  val message = error.message ?: "加载希冀作业失败"
  val relayUnavailable =
      (error as? ApiCallException)?.status?.value in listOf(408, 502, 503, 504) ||
          message.contains("超时") ||
          message.contains("网关") ||
          message.contains("服务器暂时不可用") ||
          message.contains("502")
  return if (ConnectionRuntime.currentMode() == ConnectionMode.SERVER_RELAY && relayUnavailable) {
    "中转服务器暂时不可用，请在设置中切换为直连模式或 WebVPN 模式后重试"
  } else {
    message
  }
}
