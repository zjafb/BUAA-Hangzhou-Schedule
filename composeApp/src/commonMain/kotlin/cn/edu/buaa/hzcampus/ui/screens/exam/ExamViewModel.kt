package cn.edu.buaa.hzcampus.ui.screens.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.hzcampus.api.feature.ScheduleApi
import cn.edu.buaa.hzcampus.model.dto.ExamArrangementData
import cn.edu.buaa.hzcampus.model.dto.Term
import cn.edu.buaa.hzcampus.repository.GlobalTermRepository
import cn.edu.buaa.hzcampus.repository.TermRepository
import cn.edu.buaa.hzcampus.ui.common.util.requestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExamViewModel(
    private val scheduleApi: ScheduleApi = ScheduleApi(),
    private val termRepository: TermRepository = GlobalTermRepository.instance,
) : ViewModel() {
  private var loadedOnce = false
  private var termsJob: Job? = null
  private var examsJob: Job? = null

  private val _uiState = MutableStateFlow(ExamUiState())
  val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && (loadedOnce || termsJob?.isActive == true || examsJob?.isActive == true))
        return
    loadTerms(forceRefresh)
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    termsJob?.cancel()
    examsJob?.cancel()
    loadedOnce = false
    _uiState.value = ExamUiState()
  }

  fun loadTerms(forceRefresh: Boolean = false) {
    termsJob?.cancel()
    examsJob?.cancel()
    loadedOnce = false
    termsJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, error = null)

          requestResult { termRepository.getTerms(forceRefresh) }
              .onSuccess { terms ->
                val selectedTerm =
                    terms.find { it.itemCode == _uiState.value.selectedTerm?.itemCode }
                        ?: terms.find { it.selected }
                        ?: terms.firstOrNull()
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        terms = terms,
                        selectedTerm = selectedTerm,
                        examData =
                            if (selectedTerm == _uiState.value.selectedTerm) _uiState.value.examData
                            else null,
                        error = null,
                    )
                if (selectedTerm == null) loadedOnce = true else loadExams(selectedTerm.itemCode)
              }
              .onFailure { exception ->
                _uiState.value =
                    _uiState.value.copy(isLoading = false, error = exception.message ?: "加载学期信息失败")
              }
        }
  }

  fun selectTerm(term: Term) {
    if (_uiState.value.selectedTerm != term) {
      termsJob?.cancel()
      _uiState.value = _uiState.value.copy(selectedTerm = term, examData = null)
      loadExams(term.itemCode)
    }
  }

  private fun loadExams(termCode: String) {
    examsJob?.cancel()
    examsJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, error = null)

          requestResult { scheduleApi.getExamArrangement(termCode) }
              .onSuccess { examData ->
                loadedOnce = true
                _uiState.value =
                    _uiState.value.copy(isLoading = false, examData = examData, error = null)
              }
              .onFailure { exception ->
                loadedOnce = false
                _uiState.value =
                    _uiState.value.copy(isLoading = false, error = exception.message ?: "加载考试信息失败")
              }
        }
  }
}

data class ExamUiState(
    val isLoading: Boolean = false,
    val terms: List<Term> = emptyList(),
    val selectedTerm: Term? = null,
    val examData: ExamArrangementData? = null,
    val error: String? = null,
)
