package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.hzcampus.api.feature.GradeApi
import cn.edu.buaa.hzcampus.api.storage.CourseAttributeStore
import cn.edu.buaa.hzcampus.model.dto.GradeData
import cn.edu.buaa.hzcampus.model.dto.Term
import cn.edu.buaa.hzcampus.model.dto.courseAttributesByName
import cn.edu.buaa.hzcampus.repository.GlobalTermRepository
import cn.edu.buaa.hzcampus.repository.TermRepository
import cn.edu.buaa.hzcampus.ui.common.util.requestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal interface GradeDataSource {
  suspend fun getGrades(termCode: String): Result<GradeData>
}

internal interface GradeTermsSource {
  suspend fun getTerms(forceRefresh: Boolean): Result<List<Term>>
}

internal class ApiGradeDataSource(private val gradeApi: GradeApi = GradeApi()) : GradeDataSource {
  override suspend fun getGrades(termCode: String): Result<GradeData> = gradeApi.getGrades(termCode)
}

internal class RepositoryGradeTermsSource(
    private val termRepository: TermRepository = GlobalTermRepository.instance
) : GradeTermsSource {
  override suspend fun getTerms(forceRefresh: Boolean): Result<List<Term>> =
      termRepository.getTerms(forceRefresh)
}

class GradeViewModel
internal constructor(
    private val gradeSource: GradeDataSource = ApiGradeDataSource(),
    private val termsSource: GradeTermsSource = RepositoryGradeTermsSource(),
) : ViewModel() {
  private var loadedOnce = false
  private var termsJob: Job? = null
  private var gradeJob: Job? = null

  private val _uiState = MutableStateFlow(GradeUiState())
  val uiState: StateFlow<GradeUiState> = _uiState.asStateFlow()

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && (loadedOnce || termsJob?.isActive == true)) return
    loadTerms(forceRefresh)
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    termsJob?.cancel()
    gradeJob?.cancel()
    loadedOnce = false
    _uiState.value = GradeUiState()
  }

  fun loadTerms(forceRefresh: Boolean = false) {
    termsJob?.cancel()
    gradeJob?.cancel()
    loadedOnce = false
    termsJob =
        viewModelScope.launch {
          val hasExistingContent = _uiState.value.gradeData != null
          _uiState.value =
              _uiState.value.copy(
                  isLoading = !hasExistingContent,
                  isRefreshing = forceRefresh && hasExistingContent,
                  isSummaryLoading = false,
                  gradeData = if (hasExistingContent) _uiState.value.gradeData else null,
                  termGrades = if (hasExistingContent) _uiState.value.termGrades else emptyMap(),
                  error = null,
              )

          requestResult { termsSource.getTerms(forceRefresh) }
              .onSuccess { terms ->
                val selectedTerm =
                    terms.find { it.itemCode == _uiState.value.selectedTerm?.itemCode }
                        ?: terms.find { it.selected }
                        ?: terms.firstOrNull()
                val selectedGradeData = _uiState.value.termGrades[selectedTerm?.itemCode]
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = selectedTerm != null && selectedGradeData == null,
                        terms = terms,
                        selectedTerm = selectedTerm,
                        gradeData = selectedGradeData,
                        termGrades =
                            _uiState.value.termGrades.filterKeys { code ->
                              terms.any { it.itemCode == code }
                            },
                        error = null,
                    )
                if (selectedTerm == null) {
                  loadedOnce = true
                  _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
                } else {
                  loadAllGrades(terms, selectedTerm)
                }
              }
              .onFailure { exception ->
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = exception.message ?: "加载学期信息失败",
                    )
              }
        }
  }

  fun selectTerm(term: Term) {
    if (_uiState.value.selectedTerm != term) {
      gradeJob?.cancel()
      val cachedGradeData = _uiState.value.termGrades[term.itemCode]
      _uiState.value =
          _uiState.value.copy(
              selectedTerm = term,
              gradeData = cachedGradeData,
              isLoading = cachedGradeData == null,
              error = null,
          )
      if (cachedGradeData == null) {
        loadGrades(term.itemCode)
      }
    }
  }

  private suspend fun loadAllGrades(terms: List<Term>, selectedTerm: Term) = coroutineScope {
    _uiState.value = _uiState.value.copy(isSummaryLoading = terms.size > 1)
    val orderedTerms =
        listOf(selectedTerm) + terms.filterNot { it.itemCode == selectedTerm.itemCode }
    val requests =
        Semaphore(3).let { permits ->
          orderedTerms.map { term ->
            term to
                async {
                  permits.withPermit { requestResult { gradeSource.getGrades(term.itemCode) } }
                }
          }
        }
    val selectedRequest = requests.first()
    val remainingRequests = requests.drop(1)

    val selectedResult = selectedRequest.second.await()
    val selectedGradeData = selectedResult.getOrNull()
    if (selectedGradeData == null) {
      remainingRequests.forEach { (_, request) -> request.cancel() }
      val exception = selectedResult.exceptionOrNull()
      val isSelectedTerm = selectedRequest.first.itemCode == _uiState.value.selectedTerm?.itemCode
      _uiState.value =
          if (isSelectedTerm) {
            _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isSummaryLoading = false,
                error = exception?.message ?: "加载成绩信息失败",
            )
          } else {
            _uiState.value.copy(isRefreshing = false, isSummaryLoading = false)
          }
      return@coroutineScope
    }

    recordCourseAttributes(selectedGradeData)
    loadedOnce = true

    val isSelectedTerm = selectedRequest.first.itemCode == _uiState.value.selectedTerm?.itemCode
    _uiState.value =
        _uiState.value.copy(
            isLoading = if (isSelectedTerm) false else _uiState.value.isLoading,
            isRefreshing = false,
            isSummaryLoading = remainingRequests.isNotEmpty(),
            gradeData = if (isSelectedTerm) selectedGradeData else _uiState.value.gradeData,
            termGrades =
                _uiState.value.termGrades + (selectedRequest.first.itemCode to selectedGradeData),
            error = null,
        )

    remainingRequests.forEachIndexed { index, (term, request) ->
      val result = request.await()
      val gradeData = result.getOrNull()
      if (gradeData == null) {
        val isCurrentSelectedTerm = term.itemCode == _uiState.value.selectedTerm?.itemCode
        _uiState.value =
            if (isCurrentSelectedTerm) {
              val exception = result.exceptionOrNull()
              _uiState.value.copy(
                  isLoading = false,
                  isRefreshing = false,
                  isSummaryLoading = index < remainingRequests.lastIndex,
                  error = exception?.message ?: "加载成绩信息失败",
              )
            } else {
              _uiState.value.copy(
                  isRefreshing = false,
                  isSummaryLoading = index < remainingRequests.lastIndex,
              )
            }
        return@forEachIndexed
      }

      recordCourseAttributes(gradeData)

      val isCurrentSelectedTerm = term.itemCode == _uiState.value.selectedTerm?.itemCode
      _uiState.value =
          _uiState.value.copy(
              isLoading = if (isCurrentSelectedTerm) false else _uiState.value.isLoading,
              isRefreshing = false,
              isSummaryLoading = index < remainingRequests.lastIndex,
              gradeData = if (isCurrentSelectedTerm) gradeData else _uiState.value.gradeData,
              termGrades = _uiState.value.termGrades + (term.itemCode to gradeData),
              error = if (isCurrentSelectedTerm) null else _uiState.value.error,
          )
    }
    _uiState.value = _uiState.value.copy(isRefreshing = false, isSummaryLoading = false)
  }

  private fun loadGrades(termCode: String) {
    gradeJob?.cancel()
    gradeJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, error = null)

          requestResult { gradeSource.getGrades(termCode) }
              .onSuccess { gradeData ->
                recordCourseAttributes(gradeData)
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        gradeData = gradeData,
                        termGrades = _uiState.value.termGrades + (termCode to gradeData),
                        error = null,
                    )
              }
              .onFailure { exception ->
                _uiState.value =
                    _uiState.value.copy(isLoading = false, error = exception.message ?: "加载成绩信息失败")
              }
        }
  }

  /** 成绩数据是课程性质（必修/选修）的唯一来源：每次拿到成绩就沉淀到本地映射，这样成绩页拉过的历史学期课程都能被记住，供首页今日课表显示「必 / 选」。 */
  private fun recordCourseAttributes(gradeData: GradeData) {
    CourseAttributeStore.putAll(gradeData.courseAttributesByName())
  }
}

data class GradeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSummaryLoading: Boolean = false,
    val terms: List<Term> = emptyList(),
    val selectedTerm: Term? = null,
    val gradeData: GradeData? = null,
    val termGrades: Map<String, GradeData> = emptyMap(),
    val error: String? = null,
)
