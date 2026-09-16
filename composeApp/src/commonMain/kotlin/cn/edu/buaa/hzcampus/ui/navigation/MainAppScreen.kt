package cn.edu.buaa.hzcampus.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.model.dto.CourseClass
import cn.edu.buaa.hzcampus.model.dto.UserData
import cn.edu.buaa.hzcampus.model.dto.UserInfo
import cn.edu.buaa.hzcampus.ui.common.components.AppTopBar
import cn.edu.buaa.hzcampus.ui.common.components.BottomNavTab
import cn.edu.buaa.hzcampus.ui.common.components.BottomNavigation
import cn.edu.buaa.hzcampus.ui.common.components.Sidebar
import cn.edu.buaa.hzcampus.ui.common.util.BackHandlerCompat
import cn.edu.buaa.hzcampus.ui.common.util.rememberOpenDingTalkSpaceReservation
import cn.edu.buaa.hzcampus.ui.screens.classroom.ClassroomQueryScreen
import cn.edu.buaa.hzcampus.ui.screens.classroom.ClassroomViewModel
import cn.edu.buaa.hzcampus.ui.screens.evaluation.EvaluationScreen
import cn.edu.buaa.hzcampus.ui.screens.evaluation.EvaluationViewModel
import cn.edu.buaa.hzcampus.ui.screens.exam.ExamScreen
import cn.edu.buaa.hzcampus.ui.screens.exam.ExamUiState
import cn.edu.buaa.hzcampus.ui.screens.exam.ExamViewModel
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeScoreWatchViewModel
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeScreen
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeUiState
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeViewModel
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeAssignmentDetailScreen
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeAssignmentsScreen
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeSortField
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeViewModel
import cn.edu.buaa.hzcampus.ui.screens.menu.*
import cn.edu.buaa.hzcampus.ui.screens.schedule.CourseDetailScreen
import cn.edu.buaa.hzcampus.ui.screens.schedule.ScheduleScreen
import cn.edu.buaa.hzcampus.ui.screens.schedule.ScheduleViewModel
import cn.edu.buaa.hzcampus.ui.screens.spoc.SpocAssignmentDetailScreen
import cn.edu.buaa.hzcampus.ui.screens.spoc.SpocAssignmentsScreen
import cn.edu.buaa.hzcampus.ui.screens.spoc.SpocSortField
import cn.edu.buaa.hzcampus.ui.screens.spoc.SpocViewModel
import cn.edu.buaa.hzcampus.ui.screens.ygdk.YgdkClockinFormScreen
import cn.edu.buaa.hzcampus.ui.screens.ygdk.YgdkHomeScreen
import cn.edu.buaa.hzcampus.ui.screens.ygdk.YgdkUiState
import cn.edu.buaa.hzcampus.ui.screens.ygdk.YgdkViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 应用程序所有的屏幕页面定义。 */
enum class AppScreen {
  HOME,
  REGULAR,
  ADVANCED,
  MY,
  SETTINGS,
  ABOUT,
  SCHEDULE,
  EXAM,
  GRADE,
  COURSE_DETAIL,
  CLASSROOM_QUERY,
  EVALUATION,
  SPOC_ASSIGNMENTS,
  SPOC_ASSIGNMENT_DETAIL,
  JUDGE_ASSIGNMENTS,
  JUDGE_ASSIGNMENT_DETAIL,
  YGDK_HOME,
  YGDK_FORM,
}

/**
 * 主界面支架组件。 整合了侧边栏、顶部栏、底部导航栏以及各业务模块的屏幕切换。 负责协调 ViewModel 的初始化和导航状态的分发。
 *
 * @param userData 登录用户的基础数据。
 * @param userInfo 登录用户的详细信息。
 * @param onLogoutClick 注销回调。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun MainAppScreen(
    userData: UserData,
    userInfo: UserInfo?,
    connectionMode: ConnectionMode,
    availableConnectionModes: List<ConnectionMode>,
    onEnsureUserInfo: () -> Unit,
    onConnectionModeSelected: (ConnectionMode) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val navController = rememberNavigationController()
  val currentScreen = navController.currentScreen
  val openDingTalkSpaceReservation = rememberOpenDingTalkSpaceReservation()
  val ygdkScreens = remember { setOf(AppScreen.YGDK_HOME, AppScreen.YGDK_FORM) }

  var selectedBottomTab by remember { mutableStateOf(BottomNavTab.HOME) }
  var showSidebar by remember { mutableStateOf(false) }
  var homeManualRefreshPending by remember { mutableStateOf(false) }
  var homeManualRefreshStarted by remember { mutableStateOf(false) }
  val homeSnackbarHostState = remember { SnackbarHostState() }
  val homeBootstrapCoordinator = remember(scope) { HomeBootstrapCoordinator(scope) }
  val homeBootstrapRunning by homeBootstrapCoordinator.isRunning.collectAsState()
  val homeNow by
      produceState(
          initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      ) {
        while (true) {
          delay(60_000)
          value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
      }
  // 初始化各模块 ViewModel
  val scheduleViewModel: ScheduleViewModel = viewModel { ScheduleViewModel() }
  val scheduleUiState by scheduleViewModel.uiState.collectAsState()
  val todayScheduleState by scheduleViewModel.todayScheduleState.collectAsState()
  LaunchedEffect(homeNow.date) {
    scheduleViewModel.loadTodaySchedule()
    scheduleViewModel.ensureCurrentWeekLoaded(forceRefresh = true)
    scheduleUiState.selectedTerm?.let(scheduleViewModel::loadWeeks)
  }

  val examViewModel: ExamViewModel? =
      if (currentScreen == AppScreen.EXAM) {
        viewModel(key = "exam") { ExamViewModel() }
      } else {
        null
      }
  val examUiState = examViewModel?.uiState?.collectAsState()?.value ?: ExamUiState()
  var showExamTermMenu by remember { mutableStateOf(false) }
  val gradeViewModel: GradeViewModel? =
      if (currentScreen == AppScreen.GRADE) {
        viewModel(key = "grade") { GradeViewModel() }
      } else {
        null
      }
  val gradeUiState = gradeViewModel?.uiState?.collectAsState()?.value ?: GradeUiState()
  val gradeScoreWatchViewModel: GradeScoreWatchViewModel =
      viewModel(key = "grade-score-watch-${userData.schoolid}") {
        GradeScoreWatchViewModel(userKey = userData.schoolid)
      }
  val gradeScoreWatchUiState by gradeScoreWatchViewModel.uiState.collectAsState()

  val evaluationViewModel: EvaluationViewModel? =
      if (currentScreen == AppScreen.EVALUATION) {
        viewModel(key = "evaluation") { EvaluationViewModel() }
      } else {
        null
      }
  val classroomViewModel: ClassroomViewModel? =
      if (currentScreen == AppScreen.CLASSROOM_QUERY) {
        viewModel(key = "classroom") { ClassroomViewModel() }
      } else {
        null
      }
  val spocViewModel: SpocViewModel =
      viewModel(key = "spoc-${userData.schoolid}") { SpocViewModel() }
  val spocUiState by spocViewModel.uiState.collectAsState()
  val judgeViewModel: JudgeViewModel =
      viewModel(key = "judge-${userData.schoolid}") { JudgeViewModel(userKey = userData.schoolid) }
  val judgeUiState by judgeViewModel.uiState.collectAsState()
  val ygdkViewModel: YgdkViewModel? =
      if (currentScreen == AppScreen.HOME || currentScreen in ygdkScreens) {
        viewModel(key = "ygdk-${userData.schoolid}") { YgdkViewModel(userKey = userData.schoolid) }
      } else {
        null
      }
  val ygdkUiState = ygdkViewModel?.uiState?.collectAsState()?.value ?: YgdkUiState()
  val currentWeek = scheduleUiState.currentWeek
  val ygdkWeekReminderKey = currentWeek?.let { "${it.term}:${it.serialNumber}" }
  val ygdkTermReminderKey = currentWeek?.term ?: scheduleUiState.selectedTerm?.itemCode
  val ygdkWeekDone = ygdkWeekReminderKey?.let { ygdkViewModel?.isHomeReminderWeekDone(it) } ?: false
  val ygdkTermDone = ygdkTermReminderKey?.let { ygdkViewModel?.isHomeReminderTermDone(it) } ?: false
  val isYgdkReminderWeek = currentWeek?.serialNumber?.let { it in 11..14 } == true
  val shouldLoadYgdkHomeOverview =
      ygdkViewModel != null &&
          ygdkUiState.homeReminderEnabled &&
          isYgdkReminderWeek &&
          !ygdkWeekDone &&
          !ygdkTermDone

  var selectedCourse by remember { mutableStateOf<CourseClass?>(null) }
  var selectedSpocAssignmentId by remember { mutableStateOf<String?>(null) }
  var showSpocSortFilterDialog by remember { mutableStateOf(false) }
  var selectedJudgeCourseId by remember { mutableStateOf<String?>(null) }
  var selectedJudgeAssignmentId by remember { mutableStateOf<String?>(null) }
  var showJudgeSortFilterDialog by remember { mutableStateOf(false) }
  val homeTodoItems =
      remember(
          spocUiState.assignmentsResponse,
          judgeUiState.assignmentsResponse,
          ygdkUiState.overview,
          currentWeek,
          ygdkUiState.homeReminderEnabled,
          ygdkWeekDone,
          ygdkTermDone,
          homeNow,
      ) {
        buildHomeTodoItems(
            spocAssignments = spocUiState.assignmentsResponse?.assignments.orEmpty(),
            judgeAssignments = judgeUiState.assignmentsResponse?.assignments.orEmpty(),
            ygdkOverview = ygdkUiState.overview,
            currentWeek = currentWeek,
            ygdkReminderEnabled = ygdkUiState.homeReminderEnabled,
            ygdkWeekDone = ygdkWeekDone,
            ygdkTermDone = ygdkTermDone,
            now = homeNow,
        )
      }
  val homeTodoLoadingSources = buildList {
    if (spocUiState.isLoading || spocUiState.isRefreshing) add(HomeTodoSource.SPOC)
    if (judgeUiState.isLoading || judgeUiState.isRefreshing) add(HomeTodoSource.JUDGE)
    if (shouldLoadYgdkHomeOverview && ygdkUiState.isLoading) add(HomeTodoSource.YGDK)
  }
  val homeTodoLoading = homeTodoLoadingSources.isNotEmpty()
  val homeContentLoading =
      todayScheduleState.isLoading || homeTodoLoading || gradeScoreWatchUiState.isLoading
  val homeIsRefreshing =
      homeManualRefreshPending &&
          (homeManualRefreshStarted || homeBootstrapRunning || homeContentLoading)
  val homeTodoFailedSources = buildList {
    if (spocUiState.error != null) add(HomeTodoSource.SPOC)
    if (judgeUiState.error != null) add(HomeTodoSource.JUDGE)
    if (shouldLoadYgdkHomeOverview && ygdkUiState.loadError != null) add(HomeTodoSource.YGDK)
  }

  fun startHomeBootstrap(forceRefresh: Boolean = false) {
    val showLoading =
        forceRefresh ||
            !scheduleViewModel.hasTodayLoaded() ||
            !spocViewModel.hasAssignmentsLoaded() ||
            !judgeViewModel.hasAssignmentsLoaded() ||
            !scheduleViewModel.hasCurrentWeekLoaded() ||
            (shouldLoadYgdkHomeOverview && ygdkViewModel.hasOverviewLoaded() != true) ||
            !gradeScoreWatchViewModel.hasChecked()
    homeBootstrapCoordinator.restart(
        HomeBootstrapActions(
            loadTodaySchedule = { force ->
              scheduleViewModel.ensureTodayLoaded(forceRefresh = force)
            },
            loadSpoc = { force -> spocViewModel.ensureAssignmentsLoaded(forceRefresh = force) },
            loadJudge = { force -> judgeViewModel.ensureAssignmentsLoaded(forceRefresh = force) },
            loadYgdk = { force ->
              scheduleViewModel.ensureCurrentWeekLoaded(forceRefresh = force)
              if (shouldLoadYgdkHomeOverview) {
                ygdkViewModel.ensureOverviewLoaded(forceRefresh = force)
              }
            },
            checkGradeScores = { force ->
              gradeScoreWatchViewModel.checkForUpdates(forceRefresh = force)
            },
        ),
        forceRefresh = forceRefresh,
        showLoading = showLoading,
    )
  }

  fun refreshHomeData() {
    homeManualRefreshPending = true
    startHomeBootstrap(forceRefresh = true)
  }

  /** 重置导航栈至指定根页面。 */
  fun setRoot(screen: AppScreen, tab: BottomNavTab) {
    navController.setRoot(screen)
    selectedBottomTab = tab
    showSidebar = false
  }

  /** 跳转至指定页面，并自动更新底部 Tab 激活状态。 */
  fun navigateTo(screen: AppScreen, bottomTab: BottomNavTab? = null) {
    navController.navigateTo(screen)
    val tab =
        bottomTab
            ?: when (screen) {
              AppScreen.HOME -> BottomNavTab.HOME
              AppScreen.REGULAR,
              AppScreen.SCHEDULE,
              AppScreen.EXAM,
              AppScreen.GRADE,
              AppScreen.COURSE_DETAIL,
              AppScreen.CLASSROOM_QUERY,
              AppScreen.SPOC_ASSIGNMENTS,
              AppScreen.SPOC_ASSIGNMENT_DETAIL,
              AppScreen.JUDGE_ASSIGNMENTS,
              AppScreen.JUDGE_ASSIGNMENT_DETAIL -> BottomNavTab.REGULAR
              AppScreen.ADVANCED,
              AppScreen.EVALUATION,
              AppScreen.YGDK_HOME,
              AppScreen.YGDK_FORM -> BottomNavTab.ADVANCED
              else -> null
            }
    tab?.let { selectedBottomTab = it }
    if (screen !in listOf(AppScreen.MY, AppScreen.SETTINGS, AppScreen.ABOUT)) showSidebar = false
  }

  /** 统一的返回逻辑处理。 */
  fun navigateBack() {
    if (navController.navigateBack()) {
      val top = navController.currentScreen
      val tab =
          when (top) {
            AppScreen.HOME -> BottomNavTab.HOME
            AppScreen.REGULAR,
            AppScreen.SCHEDULE,
            AppScreen.EXAM,
            AppScreen.GRADE,
            AppScreen.COURSE_DETAIL,
            AppScreen.CLASSROOM_QUERY,
            AppScreen.SPOC_ASSIGNMENTS,
            AppScreen.SPOC_ASSIGNMENT_DETAIL,
            AppScreen.JUDGE_ASSIGNMENTS,
            AppScreen.JUDGE_ASSIGNMENT_DETAIL -> BottomNavTab.REGULAR
            AppScreen.ADVANCED,
            AppScreen.EVALUATION,
            AppScreen.YGDK_HOME,
            AppScreen.YGDK_FORM -> BottomNavTab.ADVANCED
            else -> null
          }
      tab?.let { selectedBottomTab = it }
      if (top in listOf(AppScreen.MY, AppScreen.SETTINGS, AppScreen.ABOUT)) showSidebar = true
    } else {
      selectedBottomTab =
          when (navController.currentScreen) {
            AppScreen.REGULAR -> BottomNavTab.REGULAR
            AppScreen.ADVANCED -> BottomNavTab.ADVANCED
            else -> BottomNavTab.HOME
          }
      showSidebar = false
    }
  }

  fun handleHomeTodoClick(todoItem: HomeTodoItem) {
    when (val action = todoItem.action) {
      is HomeTodoAction.OpenSpocAssignment -> {
        selectedSpocAssignmentId = action.assignmentId
        spocViewModel.loadAssignmentDetail(action.assignmentId)
        navigateTo(AppScreen.SPOC_ASSIGNMENT_DETAIL)
      }
      is HomeTodoAction.OpenJudgeAssignment -> {
        selectedJudgeCourseId = action.courseId
        selectedJudgeAssignmentId = action.assignmentId
        judgeViewModel.loadAssignmentDetail(action.courseId, action.assignmentId)
        navigateTo(AppScreen.JUDGE_ASSIGNMENT_DETAIL)
      }
      HomeTodoAction.OpenYgdkHome -> navigateTo(AppScreen.YGDK_HOME)
    }
  }

  fun openScoresFromHomeNotice() {
    gradeScoreWatchViewModel.consumeNotice()
    navigateTo(AppScreen.GRADE)
  }

  LaunchedEffect(
      ygdkUiState.overview,
      ygdkWeekReminderKey,
      ygdkTermReminderKey,
      ygdkUiState.homeReminderEnabled,
  ) {
    if (!ygdkUiState.homeReminderEnabled) return@LaunchedEffect
    val viewModel = ygdkViewModel ?: return@LaunchedEffect
    val summary = ygdkUiState.overview?.summary ?: return@LaunchedEffect
    ygdkWeekReminderKey?.let { key ->
      if ((summary.weekCount ?: 0) >= 4) {
        viewModel.markHomeReminderWeekDone(key)
      }
    }
    ygdkTermReminderKey?.let { key ->
      if (summary.termCount >= 16) {
        viewModel.markHomeReminderTermDone(key)
      }
    }
  }

  LaunchedEffect(currentScreen, currentWeek, shouldLoadYgdkHomeOverview) {
    if (currentScreen == AppScreen.HOME && shouldLoadYgdkHomeOverview) {
      ygdkViewModel.ensureOverviewLoaded()
    }
  }

  LaunchedEffect(currentScreen) {
    if (currentScreen != AppScreen.HOME) {
      homeManualRefreshPending = false
      homeManualRefreshStarted = false
    }
    if (currentScreen == AppScreen.MY) {
      onEnsureUserInfo()
    }
  }

  // 连接模式切换后，重置所有 ViewModel 的加载标记与缓存数据，并强制刷新当前页面
  LaunchedEffect(connectionMode, userData.schoolid) {
    // 常驻 ViewModel
    scheduleViewModel.resetLoadedState()
    spocViewModel.resetLoadedState()
    judgeViewModel.resetLoadedState()
    // 按需 ViewModel（当前可能为 null，仅在存活时重置）
    ygdkViewModel?.resetLoadedState()
    examViewModel?.resetLoadedState()
    gradeViewModel?.resetLoadedState()
    gradeScoreWatchViewModel.resetLoadedState()
    evaluationViewModel?.resetLoadedState()
    // 刷新当前页面数据
    when (currentScreen) {
      AppScreen.HOME -> startHomeBootstrap(forceRefresh = true)
      AppScreen.SCHEDULE -> scheduleViewModel.ensureScheduleLoaded(forceRefresh = true)
      AppScreen.EXAM -> examViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.GRADE -> gradeViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.SPOC_ASSIGNMENTS -> spocViewModel.ensureAssignmentsLoaded(forceRefresh = true)
      AppScreen.JUDGE_ASSIGNMENTS -> judgeViewModel.ensureAssignmentsLoaded(forceRefresh = true)
      AppScreen.YGDK_HOME,
      AppScreen.YGDK_FORM -> ygdkViewModel?.ensureLoaded(forceRefresh = true)
      else -> Unit
    }
  }

  // Don't clear a manual refresh until we've observed real loading at least once.
  LaunchedEffect(
      homeManualRefreshPending,
      homeManualRefreshStarted,
      homeBootstrapRunning,
      homeContentLoading,
  ) {
    if (!homeManualRefreshPending) {
      homeManualRefreshStarted = false
    } else if (homeBootstrapRunning || homeContentLoading) {
      homeManualRefreshStarted = true
    } else if (homeManualRefreshStarted) {
      homeManualRefreshPending = false
      homeManualRefreshStarted = false
    }
  }

  LaunchedEffect(currentScreen) {
    if (currentScreen != AppScreen.HOME) {
      homeBootstrapCoordinator.cancel()
    }
    when (currentScreen) {
      AppScreen.HOME -> startHomeBootstrap()
      AppScreen.SCHEDULE -> scheduleViewModel.ensureScheduleLoaded()
      AppScreen.EXAM -> examViewModel?.ensureLoaded()
      AppScreen.GRADE -> gradeViewModel?.ensureLoaded()
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded()
      AppScreen.SPOC_ASSIGNMENTS,
      AppScreen.SPOC_ASSIGNMENT_DETAIL -> spocViewModel.ensureAssignmentsLoaded()
      AppScreen.JUDGE_ASSIGNMENTS,
      AppScreen.JUDGE_ASSIGNMENT_DETAIL -> judgeViewModel.ensureAssignmentsLoaded()
      AppScreen.YGDK_HOME,
      AppScreen.YGDK_FORM -> ygdkViewModel?.ensureLoaded()
      else -> Unit
    }
  }

  val screenTitle =
      when (currentScreen) {
        AppScreen.HOME -> "首页"
        AppScreen.REGULAR -> "普通功能"
        AppScreen.ADVANCED -> "高级功能"
        AppScreen.MY -> "我的"
        AppScreen.SETTINGS -> "设置"
        AppScreen.ABOUT -> "关于"
        AppScreen.SCHEDULE -> "课程表"
        AppScreen.EXAM -> "考试查询"
        AppScreen.GRADE -> gradeUiState.selectedTerm?.itemName ?: "成绩查询"
        AppScreen.COURSE_DETAIL -> "课程详情"
        AppScreen.CLASSROOM_QUERY -> "空教室查询"
        AppScreen.EVALUATION -> "自动评教"
        AppScreen.SPOC_ASSIGNMENTS -> "SPOC作业"
        AppScreen.SPOC_ASSIGNMENT_DETAIL -> "作业详情"
        AppScreen.JUDGE_ASSIGNMENTS -> "希冀作业"
        AppScreen.JUDGE_ASSIGNMENT_DETAIL -> "作业详情"
        AppScreen.YGDK_HOME -> "阳光打卡"
        AppScreen.YGDK_FORM -> "新增打卡"
      }

  Box(modifier = modifier.fillMaxSize()) {
    BackHandlerCompat(enabled = showSidebar || navController.navStack.size > 1) {
      if (showSidebar) showSidebar = false else if (navController.navStack.size > 1) navigateBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
      val isRootScreen =
          currentScreen in listOf(AppScreen.HOME, AppScreen.REGULAR, AppScreen.ADVANCED)
      val showGlobalTopBar = currentScreen != AppScreen.SCHEDULE
      if (showGlobalTopBar) {
        AppTopBar(
            title = screenTitle,
            canNavigateBack = !isRootScreen,
            onNavigationIconClick = {
              if (isRootScreen) showSidebar = !showSidebar else navigateBack()
            },
            actions = {
              // 特殊页面的顶部栏动作按钮
              if (currentScreen == AppScreen.EXAM) {
                Box {
                  TextButton(onClick = { showExamTermMenu = true }) {
                    Text(examUiState.selectedTerm?.itemName ?: "选择学期")
                    Icon(Icons.Default.ArrowDropDown, null)
                  }
                  DropdownMenu(
                      expanded = showExamTermMenu,
                      onDismissRequest = { showExamTermMenu = false },
                  ) {
                    examUiState.terms.forEach {
                      DropdownMenuItem(
                          text = { Text(it.itemName) },
                          onClick = {
                            examViewModel?.selectTerm(it)
                            showExamTermMenu = false
                          },
                      )
                    }
                  }
                }
              } else if (currentScreen == AppScreen.GRADE) {
                val currentTermIndex = gradeUiState.terms.indexOf(gradeUiState.selectedTerm)
                IconButton(
                    onClick = {
                      if (currentTermIndex > 0) {
                        gradeViewModel?.selectTerm(gradeUiState.terms[currentTermIndex - 1])
                      }
                    },
                    enabled = currentTermIndex > 0,
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一学期")
                }
                IconButton(
                    onClick = {
                      if (
                          currentTermIndex != -1 && currentTermIndex < gradeUiState.terms.size - 1
                      ) {
                        gradeViewModel?.selectTerm(gradeUiState.terms[currentTermIndex + 1])
                      }
                    },
                    enabled =
                        currentTermIndex != -1 && currentTermIndex < gradeUiState.terms.size - 1,
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一学期")
                }
              } else if (currentScreen == AppScreen.SPOC_ASSIGNMENTS) {
                IconButton(onClick = { showSpocSortFilterDialog = true }) {
                  Icon(Icons.Default.Tune, contentDescription = "排序和筛选")
                }
              } else if (currentScreen == AppScreen.JUDGE_ASSIGNMENTS) {
                IconButton(onClick = { showJudgeSortFilterDialog = true }) {
                  Icon(Icons.Default.Tune, contentDescription = "排序和筛选")
                }
              }
            },
        )
      }

      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        when (currentScreen) {
          AppScreen.HOME ->
              HomeScreen(
                  todayClasses = todayScheduleState.todayClasses,
                  isLoading = todayScheduleState.isLoading,
                  isRefreshing = homeIsRefreshing,
                  error = todayScheduleState.error,
                  todoItems = homeTodoItems,
                  todoLoading = homeTodoLoading,
                  todoLoadingSources = homeTodoLoadingSources,
                  todoFailedSources = homeTodoFailedSources,
                  scoreUpdateNotice = gradeScoreWatchUiState.notice,
                  onRetrySchedule = { scheduleViewModel.loadTodaySchedule() },
                  onRefresh = { refreshHomeData() },
                  onOpenScoresClick = { openScoresFromHomeNotice() },
                  onDismissScoreNotice = { gradeScoreWatchViewModel.consumeNotice() },
                  onTodoClick = { todoItem -> handleHomeTodoClick(todoItem) },
              )
          AppScreen.REGULAR ->
              RegularFeaturesScreen(
                  onScheduleClick = { navigateTo(AppScreen.SCHEDULE) },
                  onExamClick = { navigateTo(AppScreen.EXAM) },
                  onGradeClick = { navigateTo(AppScreen.GRADE) },
                  onClassroomClick = { navigateTo(AppScreen.CLASSROOM_QUERY) },
                  onSpocClick = { navigateTo(AppScreen.SPOC_ASSIGNMENTS) },
                  onJudgeClick = { navigateTo(AppScreen.JUDGE_ASSIGNMENTS) },
                  onSpaceReservationClick = openDingTalkSpaceReservation,
              )
          AppScreen.ADVANCED ->
              AdvancedFeaturesScreen(
                  onEvaluationClick = { navigateTo(AppScreen.EVALUATION) },
                  onYgdkClick = { navigateTo(AppScreen.YGDK_HOME) },
              )
          AppScreen.MY -> MyScreen(userInfo = userInfo)
          AppScreen.SETTINGS ->
              SettingsScreen(
                  currentMode = connectionMode,
                  availableModes = availableConnectionModes,
                  onModeSelected = onConnectionModeSelected,
              )
          AppScreen.ABOUT -> AboutScreen()
          AppScreen.SCHEDULE ->
              ScheduleScreen(
                  terms = scheduleUiState.terms,
                  weeks = scheduleUiState.weeks,
                  weeklySchedule = scheduleUiState.weeklySchedule,
                  weekSchedules = scheduleUiState.weekSchedules,
                  selectedTerm = scheduleUiState.selectedTerm,
                  selectedWeek = scheduleUiState.selectedWeek,
                  isLoading = scheduleUiState.isLoading,
                  error = scheduleUiState.error,
                  isUpdating = scheduleUiState.isUpdating,
                  updatedAt = scheduleUiState.updatedAt,
                  diagnosticResponse = scheduleUiState.diagnosticResponse,
                  onUpdate = { scheduleViewModel.updateSchedule() },
                  onRefresh = { scheduleViewModel.ensureScheduleLoaded(forceRefresh = true) },
                  onImportCurrentTerm = { scheduleViewModel.updateSchedule(currentTerm = true) },
                  onTermSelected = { scheduleViewModel.selectTerm(it) },
                  onWeekSelected = { scheduleViewModel.selectWeek(it) },
                  onNavigateBack = { navigateBack() },
                  onCourseClick = {
                    selectedCourse = it
                    navigateTo(AppScreen.COURSE_DETAIL)
                  },
              )
          AppScreen.EXAM -> examViewModel?.let { ExamScreen(viewModel = it) }
          AppScreen.GRADE -> gradeViewModel?.let { GradeScreen(viewModel = it) }
          AppScreen.COURSE_DETAIL -> selectedCourse?.let { CourseDetailScreen(course = it) }
          AppScreen.CLASSROOM_QUERY ->
              classroomViewModel?.let {
                ClassroomQueryScreen(viewModel = it, onBackClick = { navigateBack() })
              }
          AppScreen.EVALUATION -> evaluationViewModel?.let { EvaluationScreen(viewModel = it) }
          AppScreen.YGDK_HOME ->
              ygdkViewModel?.let {
                YgdkHomeScreen(
                    uiState = ygdkUiState,
                    onRefresh = { it.refreshAll() },
                    onLoadMore = { it.loadMoreRecords() },
                    onAddClick = { navigateTo(AppScreen.YGDK_FORM) },
                    onHomeReminderEnabledChange = { enabled -> it.setHomeReminderEnabled(enabled) },
                    onMessageConsumed = { it.clearSubmitMessage() },
                )
              }
          AppScreen.YGDK_FORM ->
              ygdkViewModel?.let { viewModel ->
                YgdkClockinFormScreen(
                    uiState = ygdkUiState,
                    imagePicker =
                        cn.edu.buaa.hzcampus.ui.common.util.rememberPlatformImagePicker(
                            onImagePicked = { viewModel.setPhoto(it) },
                            onError = { viewModel.showMessage(it) },
                        ),
                    onItemSelected = { viewModel.updateItemId(it) },
                    onStartTimeChange = { viewModel.updateStartTime(it) },
                    onEndTimeChange = { viewModel.updateEndTime(it) },
                    onPlaceChange = { viewModel.updatePlace(it) },
                    onShareChange = { viewModel.setShareToSquare(it) },
                    onClearPhoto = { viewModel.clearPhoto() },
                    onSubmit = { viewModel.submitClockin { navigateBack() } },
                )
              }
          AppScreen.SPOC_ASSIGNMENTS ->
              SpocAssignmentsScreen(
                  viewModel = spocViewModel,
                  onAssignmentClick = {
                    selectedSpocAssignmentId = it.assignmentId
                    spocViewModel.loadAssignmentDetail(it.assignmentId)
                    navigateTo(AppScreen.SPOC_ASSIGNMENT_DETAIL)
                  },
              )
          AppScreen.SPOC_ASSIGNMENT_DETAIL ->
              SpocAssignmentDetailScreen(
                  viewModel = spocViewModel,
                  onRetry = {
                    selectedSpocAssignmentId?.let { assignmentId ->
                      spocViewModel.loadAssignmentDetail(assignmentId)
                    }
                  },
              )
          AppScreen.JUDGE_ASSIGNMENTS ->
              JudgeAssignmentsScreen(
                  viewModel = judgeViewModel,
                  onAssignmentClick = {
                    selectedJudgeCourseId = it.courseId
                    selectedJudgeAssignmentId = it.assignmentId
                    judgeViewModel.loadAssignmentDetail(it.courseId, it.assignmentId)
                    navigateTo(AppScreen.JUDGE_ASSIGNMENT_DETAIL)
                  },
              )
          AppScreen.JUDGE_ASSIGNMENT_DETAIL ->
              JudgeAssignmentDetailScreen(
                  viewModel = judgeViewModel,
                  onRetry = {
                    val courseId = selectedJudgeCourseId
                    val assignmentId = selectedJudgeAssignmentId
                    if (courseId != null && assignmentId != null) {
                      judgeViewModel.loadAssignmentDetail(courseId, assignmentId)
                    }
                  },
              )
        }
      }

      if (
          currentScreen !in
              listOf(
                  AppScreen.SCHEDULE,
                  AppScreen.EXAM,
                  AppScreen.GRADE,
                  AppScreen.COURSE_DETAIL,
                  AppScreen.MY,
                  AppScreen.SETTINGS,
                  AppScreen.ABOUT,
                  AppScreen.CLASSROOM_QUERY,
                  AppScreen.EVALUATION,
                  AppScreen.YGDK_HOME,
                  AppScreen.YGDK_FORM,
                  AppScreen.SPOC_ASSIGNMENTS,
                  AppScreen.SPOC_ASSIGNMENT_DETAIL,
                  AppScreen.JUDGE_ASSIGNMENTS,
                  AppScreen.JUDGE_ASSIGNMENT_DETAIL,
              )
      ) {
        BottomNavigation(
            currentTab = selectedBottomTab,
            onTabSelected = { tab ->
              when (tab) {
                BottomNavTab.HOME -> setRoot(AppScreen.HOME, BottomNavTab.HOME)
                BottomNavTab.REGULAR -> setRoot(AppScreen.REGULAR, BottomNavTab.REGULAR)
                BottomNavTab.ADVANCED -> setRoot(AppScreen.ADVANCED, BottomNavTab.ADVANCED)
              }
            },
        )
      }
    }

    AnimatedVisibility(visible = showSidebar, enter = fadeIn(), exit = fadeOut()) {
      Box(
          Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable {
            showSidebar = false
          }
      )
    }

    AnimatedVisibility(
        visible = showSidebar,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(targetOffsetX = { -it * 2 }),
    ) {
      Box(Modifier.fillMaxHeight(), Alignment.CenterStart) {
        Sidebar(
            userData = userData,
            onLogoutClick = {
              showSidebar = false
              onLogoutClick()
            },
            onMyClick = {
              showSidebar = false
              navigateTo(AppScreen.MY)
            },
            onSettingsClick = {
              showSidebar = false
              navigateTo(AppScreen.SETTINGS)
            },
            onAboutClick = {
              showSidebar = false
              navigateTo(AppScreen.ABOUT)
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
      }
    }

    if (currentScreen == AppScreen.HOME) {
      SnackbarHost(
          hostState = homeSnackbarHostState,
          modifier =
              Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 88.dp),
      )
    }

    if (showSpocSortFilterDialog && currentScreen == AppScreen.SPOC_ASSIGNMENTS) {
      SpocSortFilterDialog(
          sortField = spocUiState.sortField,
          sortAscending = spocUiState.sortAscending,
          showExpired = spocUiState.showExpired,
          showOnlyUnsubmitted = spocUiState.showOnlyUnsubmitted,
          onDismiss = { showSpocSortFilterDialog = false },
          onApply = { sortField, sortAscending, showExpired, showOnlyUnsubmitted ->
            spocViewModel.setSortField(sortField)
            if (spocUiState.sortAscending != sortAscending) {
              spocViewModel.toggleSortDirection()
            }
            spocViewModel.setShowExpired(showExpired)
            spocViewModel.setShowOnlyUnsubmitted(showOnlyUnsubmitted)
            showSpocSortFilterDialog = false
          },
      )
    }

    if (showJudgeSortFilterDialog && currentScreen == AppScreen.JUDGE_ASSIGNMENTS) {
      JudgeSortFilterDialog(
          sortField = judgeUiState.sortField,
          sortAscending = judgeUiState.sortAscending,
          showExpired = judgeUiState.showExpired,
          showOnlyUnfinished = judgeUiState.showOnlyUnfinished,
          onDismiss = { showJudgeSortFilterDialog = false },
          onApply = { sortField, sortAscending, showExpired, showOnlyUnfinished ->
            judgeViewModel.setSortField(sortField)
            if (judgeUiState.sortAscending != sortAscending) {
              judgeViewModel.toggleSortDirection()
            }
            judgeViewModel.setShowExpired(showExpired)
            judgeViewModel.setShowOnlyUnfinished(showOnlyUnfinished)
            showJudgeSortFilterDialog = false
          },
      )
    }
  }
}

@Composable
private fun SpocSortFilterDialog(
    sortField: SpocSortField,
    sortAscending: Boolean,
    showExpired: Boolean,
    showOnlyUnsubmitted: Boolean,
    onDismiss: () -> Unit,
    onApply: (SpocSortField, Boolean, Boolean, Boolean) -> Unit,
) {
  var selectedSortField by remember(sortField) { mutableStateOf(sortField) }
  var selectedSortAscending by remember(sortAscending) { mutableStateOf(sortAscending) }
  var selectedShowExpired by remember(showExpired) { mutableStateOf(showExpired) }
  var selectedShowOnlyUnsubmitted by
      remember(showOnlyUnsubmitted) { mutableStateOf(showOnlyUnsubmitted) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("排序和筛选") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("排序字段", style = MaterialTheme.typography.titleSmall)
          SpocDialogOptionRow(
              label = "按截止时间",
              selected = selectedSortField == SpocSortField.DUE_TIME,
              onClick = { selectedSortField = SpocSortField.DUE_TIME },
          )
          SpocDialogOptionRow(
              label = "按开始时间",
              selected = selectedSortField == SpocSortField.START_TIME,
              onClick = { selectedSortField = SpocSortField.START_TIME },
          )

          Text("排序方向", style = MaterialTheme.typography.titleSmall)
          SpocDialogOptionRow(
              label = "升序",
              selected = selectedSortAscending,
              onClick = { selectedSortAscending = true },
          )
          SpocDialogOptionRow(
              label = "降序",
              selected = !selectedSortAscending,
              onClick = { selectedSortAscending = false },
          )

          Text("筛选条件", style = MaterialTheme.typography.titleSmall)
          SpocCheckboxRow(
              label = "仅显示未提交",
              checked = selectedShowOnlyUnsubmitted,
              onCheckedChange = { selectedShowOnlyUnsubmitted = it },
          )
          SpocCheckboxRow(
              label = "显示已截止",
              checked = selectedShowExpired,
              onCheckedChange = { selectedShowExpired = it },
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              onApply(
                  selectedSortField,
                  selectedSortAscending,
                  selectedShowExpired,
                  selectedShowOnlyUnsubmitted,
              )
            }
        ) {
          Text("应用")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun JudgeSortFilterDialog(
    sortField: JudgeSortField,
    sortAscending: Boolean,
    showExpired: Boolean,
    showOnlyUnfinished: Boolean,
    onDismiss: () -> Unit,
    onApply: (JudgeSortField, Boolean, Boolean, Boolean) -> Unit,
) {
  var selectedSortField by remember(sortField) { mutableStateOf(sortField) }
  var selectedSortAscending by remember(sortAscending) { mutableStateOf(sortAscending) }
  var selectedShowExpired by remember(showExpired) { mutableStateOf(showExpired) }
  var selectedShowOnlyUnfinished by
      remember(showOnlyUnfinished) { mutableStateOf(showOnlyUnfinished) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("排序和筛选") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("排序字段", style = MaterialTheme.typography.titleSmall)
          SpocDialogOptionRow(
              label = "按截止时间",
              selected = selectedSortField == JudgeSortField.DUE_TIME,
              onClick = { selectedSortField = JudgeSortField.DUE_TIME },
          )
          SpocDialogOptionRow(
              label = "按开始时间",
              selected = selectedSortField == JudgeSortField.START_TIME,
              onClick = { selectedSortField = JudgeSortField.START_TIME },
          )

          Text("排序方向", style = MaterialTheme.typography.titleSmall)
          SpocDialogOptionRow(
              label = "升序",
              selected = selectedSortAscending,
              onClick = { selectedSortAscending = true },
          )
          SpocDialogOptionRow(
              label = "降序",
              selected = !selectedSortAscending,
              onClick = { selectedSortAscending = false },
          )

          Text("筛选条件", style = MaterialTheme.typography.titleSmall)
          SpocCheckboxRow(
              label = "仅显示未完成",
              checked = selectedShowOnlyUnfinished,
              onCheckedChange = { selectedShowOnlyUnfinished = it },
          )
          SpocCheckboxRow(
              label = "显示已截止",
              checked = selectedShowExpired,
              onCheckedChange = { selectedShowExpired = it },
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              onApply(
                  selectedSortField,
                  selectedSortAscending,
                  selectedShowExpired,
                  selectedShowOnlyUnfinished,
              )
            }
        ) {
          Text("应用")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun SpocDialogOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun SpocCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = { onCheckedChange(!checked) }),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
  }
}
