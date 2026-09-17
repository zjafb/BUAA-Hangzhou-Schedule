package cn.edu.buaa.hzcampus.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.storage.CourseAttributeStore
import cn.edu.buaa.hzcampus.api.storage.MailAccountsStore
import cn.edu.buaa.hzcampus.api.storage.PlanStore
import cn.edu.buaa.hzcampus.api.storage.ReminderStore
import cn.edu.buaa.hzcampus.model.dto.CourseClass
import cn.edu.buaa.hzcampus.model.dto.PlanTask
import cn.edu.buaa.hzcampus.model.dto.TodayClass
import cn.edu.buaa.hzcampus.model.dto.UserData
import cn.edu.buaa.hzcampus.model.dto.UserInfo
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule
import cn.edu.buaa.hzcampus.model.dto.scheduleSectionTimes
import cn.edu.buaa.hzcampus.repository.savedWeeklyScheduleFor
import cn.edu.buaa.hzcampus.ui.common.components.AppTopBar
import cn.edu.buaa.hzcampus.ui.common.components.BottomNavTab
import cn.edu.buaa.hzcampus.ui.common.components.BottomNavigation
import cn.edu.buaa.hzcampus.ui.common.components.Sidebar
import cn.edu.buaa.hzcampus.ui.common.util.BackHandlerCompat
import cn.edu.buaa.hzcampus.ui.common.util.cancelClassReminders
import cn.edu.buaa.hzcampus.ui.common.util.rememberOpenCampusGuide
import cn.edu.buaa.hzcampus.ui.common.util.rememberOpenDingTalkSpaceReservation
import cn.edu.buaa.hzcampus.ui.common.util.schedulePlanReminders
import cn.edu.buaa.hzcampus.ui.common.util.scheduleUpcomingClassReminders
import cn.edu.buaa.hzcampus.ui.screens.classroom.ClassroomQueryScreen
import cn.edu.buaa.hzcampus.ui.screens.classroom.ClassroomViewModel
import cn.edu.buaa.hzcampus.ui.screens.evaluation.EvaluationScreen
import cn.edu.buaa.hzcampus.ui.screens.evaluation.EvaluationViewModel
import cn.edu.buaa.hzcampus.ui.screens.exam.ExamScreen
import cn.edu.buaa.hzcampus.ui.screens.exam.ExamViewModel
import cn.edu.buaa.hzcampus.ui.screens.grade.CourseQueryScreen
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeScoreWatchViewModel
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeScreen
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeUiState
import cn.edu.buaa.hzcampus.ui.screens.grade.GradeViewModel
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeAssignmentDetailScreen
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeAssignmentsScreen
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeSortField
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeUiState
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeViewModel
import cn.edu.buaa.hzcampus.ui.screens.mail.MailScreen
import cn.edu.buaa.hzcampus.ui.screens.mail.createMailBackend
import cn.edu.buaa.hzcampus.ui.screens.menu.*
import cn.edu.buaa.hzcampus.ui.screens.plan.PlanEditDialog
import cn.edu.buaa.hzcampus.ui.screens.schedule.CourseDetailScreen
import cn.edu.buaa.hzcampus.ui.screens.schedule.ScheduleScreen
import cn.edu.buaa.hzcampus.ui.screens.schedule.ScheduleViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
  /** 高级功能「成绩查询」：GPA 统计、成绩分析、绩点模拟。 */
  GRADE,
  /** 普通功能「课程查询」：逐门课程及其成绩、学分、学时明细。 */
  COURSE_QUERY,
  COURSE_DETAIL,
  CLASSROOM_QUERY,
  MAIL,
  EVALUATION,
  JUDGE_ASSIGNMENTS,
  JUDGE_ASSIGNMENT_DETAIL,
}

private data class PlanEditRequest(
    val existing: PlanTask?,
    val date: String?,
    val startTime: String?,
    val endTime: String?,
)

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
  val openCampusGuide = rememberOpenCampusGuide()

  // 今日计划
  var planTasks by remember { mutableStateOf(PlanStore.list()) }
  var planEdit by remember { mutableStateOf<PlanEditRequest?>(null) }
  var planDeleteCandidate by remember { mutableStateOf<PlanTask?>(null) }

  // 计划增删改后同步计划提醒闹钟。
  LaunchedEffect(planTasks) { runCatching { schedulePlanReminders(planTasks) } }

  // 邮件未读数
  val mailBackend = remember { createMailBackend() }
  var mailUnread by remember { mutableStateOf(MailAccountsStore.lastUnreadCount()) }

  // 课程名 -> 课程性质（必修/选修）。数据来自成绩数据，跨学期累积，见 CourseAttributeStore。
  var courseAttributes by remember { mutableStateOf(CourseAttributeStore.all()) }

  fun openPlanEditor(existing: PlanTask?, date: String?, start: String?, end: String?) {
    planEdit = PlanEditRequest(existing, date, start, end)
  }

  /** 长按计划块后先弹出删除确认，确认后再落库删除。 */
  fun requestPlanDelete(task: PlanTask) {
    planDeleteCandidate = task
  }

  fun refreshMailUnread() {
    scope.launch {
      val accounts = MailAccountsStore.list()
      if (accounts.isEmpty()) {
        mailUnread = 0
        MailAccountsStore.saveLastUnreadCount(0)
      } else {
        var total = 0
        accounts.forEach { acc ->
          total +=
              try {
                mailBackend.countUnread(acc)
              } catch (e: Exception) {
                0
              }
        }
        mailUnread = total
        MailAccountsStore.saveLastUnreadCount(total)
      }
    }
  }

  var reminderAdvanceMinutes by remember { mutableStateOf(ReminderStore.getAdvanceMinutes()) }

  LaunchedEffect(currentScreen) {
    if (currentScreen == AppScreen.HOME) {
      planTasks = PlanStore.list()
      refreshMailUnread()
      // 从设置页返回时重新读取提前分钟数，保证修改立即生效。
      reminderAdvanceMinutes = ReminderStore.getAdvanceMinutes()
    }
  }

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
  LaunchedEffect(todayScheduleState.todayClasses, reminderAdvanceMinutes) {
    // 一次排满未来一周（读本地课表，不联网），本地课表不可用时退回今天的在线课表。
    runCatching {
      scheduleUpcomingClassReminders(
          advanceMinutes = reminderAdvanceMinutes,
          fallbackTodayDate = homeNow.date.toString(),
          fallbackTodayClasses = todayScheduleState.todayClasses,
      )
    }
  }
  LaunchedEffect(homeNow.date) {
    scheduleViewModel.loadTodaySchedule()
    scheduleViewModel.ensureCurrentWeekLoaded(forceRefresh = true)
    scheduleUiState.selectedTerm?.let(scheduleViewModel::loadWeeks)
  }

  // 考试 ViewModel 常驻：首页的「考试倒计时」需要考试数据，不能等进入考试页才创建（原先按需创建）。
  val examViewModel: ExamViewModel = viewModel(key = "exam") { ExamViewModel() }
  val examUiState by examViewModel.uiState.collectAsState()
  var showExamTermMenu by remember { mutableStateOf(false) }
  // 课程查询 / 成绩查询共用的学期下拉菜单开关。
  var showGradeTermMenu by remember { mutableStateOf(false) }
  // 首页「考试倒计时」：从已加载的考试安排里取「今天及以后」最近的一场；取不到就是 null，卡片整块隐藏。
  val homeUpcomingExam =
      remember(examUiState.examData, homeNow.date) {
        findUpcomingExam(examUiState.examData?.arranged.orEmpty(), homeNow.date)
      }
  // 首页需要考试数据，这里让它跟着首页一起加载。ensureLoaded 自带 loadedOnce 守卫且异步执行，
  // 既不会重复请求，也不会阻塞首页其它内容；失败时首页静默地不显示倒计时卡片。
  LaunchedEffect(currentScreen, homeNow.date) {
    if (currentScreen == AppScreen.HOME) examViewModel.ensureLoaded()
  }
  val gradeViewModel: GradeViewModel? =
      if (currentScreen == AppScreen.GRADE || currentScreen == AppScreen.COURSE_QUERY) {
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

  // 成绩数据落库后刷新课程性质映射：首页成绩监控或成绩页写完映射，今日课表的「必 / 选」标签随之出现。
  LaunchedEffect(currentScreen, homeNow.date, gradeScoreWatchUiState.isLoading) {
    courseAttributes = CourseAttributeStore.all()
  }

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
  val judgeViewModel: JudgeViewModel =
      viewModel(key = "judge-${userData.schoolid}") { JudgeViewModel(userKey = userData.schoolid) }
  val judgeUiState by judgeViewModel.uiState.collectAsState()

  var selectedCourse by remember { mutableStateOf<CourseClass?>(null) }
  var judgeDetailKey by remember { mutableStateOf<Pair<String, String>?>(null) }
  var showJudgeSortFilterDialog by remember { mutableStateOf(false) }
  val homeTodoItems =
      remember(
          judgeUiState.assignmentsResponse?.assignments,
          homeNow,
      ) {
        buildHomeTodoItems(
            judgeAssignments = judgeUiState.assignmentsResponse?.assignments.orEmpty(),
            now = homeNow,
        )
      }
  // 只有真正的首次加载/手动刷新才算 loading；后台补全详情（isEnrichingAssignments）不算，
  // 否则待办区会因为「摘要之后仍在拉详情」一直转圈。
  val homeTodoLoadingSources = buildList {
    if (judgeUiState.isLoading || judgeUiState.isRefreshing) add(HomeTodoSource.JUDGE)
  }
  val homeTodoLoading = homeTodoLoadingSources.isNotEmpty()
  val homeContentLoading =
      todayScheduleState.isLoading || homeTodoLoading || gradeScoreWatchUiState.isLoading
  val homeIsRefreshing =
      homeManualRefreshPending &&
          (homeManualRefreshStarted || homeBootstrapRunning || homeContentLoading)
  val homeTodoFailedSources = buildList {
    if (judgeUiState.error != null) add(HomeTodoSource.JUDGE)
  }

  fun startHomeBootstrap(forceRefresh: Boolean = false) {
    val showLoading =
        forceRefresh ||
            !scheduleViewModel.hasTodayLoaded() ||
            !scheduleViewModel.hasCurrentWeekLoaded() ||
            !judgeViewModel.hasAssignmentsLoaded() ||
            !gradeScoreWatchViewModel.hasChecked()
    homeBootstrapCoordinator.restart(
        HomeBootstrapActions(
            loadTodaySchedule = { force ->
              scheduleViewModel.ensureTodayLoaded(forceRefresh = force)
            },
            loadJudge = { force -> judgeViewModel.ensureAssignmentsLoaded(forceRefresh = force) },
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
    // 考试数据也一起刷新，但不算进 homeContentLoading：倒计时卡片不是首页的关键内容，
    // 不该让下拉刷新一直转圈等它。
    examViewModel.ensureLoaded(forceRefresh = true)
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
              AppScreen.COURSE_QUERY,
              AppScreen.COURSE_DETAIL,
              AppScreen.CLASSROOM_QUERY,
              AppScreen.MAIL,
              AppScreen.JUDGE_ASSIGNMENTS,
              AppScreen.JUDGE_ASSIGNMENT_DETAIL -> BottomNavTab.REGULAR
              AppScreen.ADVANCED,
              AppScreen.GRADE,
              AppScreen.EVALUATION -> BottomNavTab.ADVANCED
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
            AppScreen.COURSE_QUERY,
            AppScreen.COURSE_DETAIL,
            AppScreen.CLASSROOM_QUERY,
            AppScreen.MAIL,
            AppScreen.JUDGE_ASSIGNMENTS,
            AppScreen.JUDGE_ASSIGNMENT_DETAIL -> BottomNavTab.REGULAR
            AppScreen.ADVANCED,
            AppScreen.GRADE,
            AppScreen.EVALUATION -> BottomNavTab.ADVANCED
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

  fun openJudgeAssignment(courseId: String, assignmentId: String) {
    judgeDetailKey = courseId to assignmentId
    judgeViewModel.loadAssignmentDetail(courseId, assignmentId)
    navigateTo(AppScreen.JUDGE_ASSIGNMENT_DETAIL)
  }

  /**
   * 首页点击今日课程：首页只有今日摘要，课程详情页需要周课表里的 [CourseClass]。
   *
   * 先在已加载的周课表中按「课程名 + 今天星期几」定位同一门课；内存里还没有周课表（刚进首页）时再用已本地化的整学期课表兜底，不联网。都定位不到时什么都不做。
   */
  fun openTodayClassDetail(todayClass: TodayClass) {
    val currentWeek =
        scheduleUiState.currentWeek?.serialNumber
            ?: scheduleUiState.weeks.firstOrNull { it.curWeek }?.serialNumber
    val loaded =
        homeWeekSchedules(
            currentWeek = currentWeek,
            weekSchedules = scheduleUiState.weekSchedules,
            weeklySchedule = scheduleUiState.weeklySchedule,
        )
    val course =
        findCourseForTodayClass(todayClass, loaded, homeNow.date.dayOfWeek.ordinal + 1)
            ?: findCourseForTodayClass(
                todayClass,
                listOfNotNull(savedWeeklyScheduleFor(homeNow.date)),
                homeNow.date.dayOfWeek.ordinal + 1,
            )
            ?: return
    selectedCourse = course
    navigateTo(AppScreen.COURSE_DETAIL)
  }

  fun handleHomeTodoClick(todoItem: HomeTodoItem) {
    when (val action = todoItem.action) {
      is HomeTodoAction.OpenJudgeAssignment ->
          openJudgeAssignment(action.courseId, action.assignmentId)
    }
  }

  fun openScoresFromHomeNotice() {
    gradeScoreWatchViewModel.consumeNotice()
    // 「有成绩更新」的提示直接跳到逐门课程的「课程查询」，用户首先想看的是哪几门课出了分。
    navigateTo(AppScreen.COURSE_QUERY)
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
    examViewModel.resetLoadedState()
    // 按需 ViewModel（当前可能为 null，仅在存活时重置）
    gradeViewModel?.resetLoadedState()
    gradeScoreWatchViewModel.resetLoadedState()
    evaluationViewModel?.resetLoadedState()
    judgeViewModel.resetLoadedState()
    // 刷新当前页面数据
    when (currentScreen) {
      AppScreen.HOME -> {
        startHomeBootstrap(forceRefresh = true)
        // 首页的考试倒计时也要跟着换连接模式后的新数据。
        examViewModel.ensureLoaded(forceRefresh = true)
      }
      AppScreen.SCHEDULE -> scheduleViewModel.ensureScheduleLoaded(forceRefresh = true)
      AppScreen.EXAM -> examViewModel.ensureLoaded(forceRefresh = true)
      AppScreen.GRADE,
      AppScreen.COURSE_QUERY -> gradeViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.JUDGE_ASSIGNMENTS,
      AppScreen.JUDGE_ASSIGNMENT_DETAIL ->
          judgeViewModel.ensureAssignmentsLoaded(forceRefresh = true)
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
      AppScreen.EXAM -> examViewModel.ensureLoaded()
      AppScreen.GRADE,
      AppScreen.COURSE_QUERY -> gradeViewModel?.ensureLoaded()
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded()
      AppScreen.JUDGE_ASSIGNMENTS,
      AppScreen.JUDGE_ASSIGNMENT_DETAIL -> judgeViewModel.ensureAssignmentsLoaded()
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
        AppScreen.GRADE -> "成绩查询"
        AppScreen.COURSE_QUERY -> "课程查询"
        AppScreen.COURSE_DETAIL -> "课程详情"
        AppScreen.CLASSROOM_QUERY -> "空教室查询"
        AppScreen.MAIL -> "邮件查询"
        AppScreen.EVALUATION -> "自动评教"
        AppScreen.JUDGE_ASSIGNMENTS -> "希冀作业"
        AppScreen.JUDGE_ASSIGNMENT_DETAIL -> "作业详情"
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
                            examViewModel.selectTerm(it)
                            showExamTermMenu = false
                          },
                      )
                    }
                  }
                }
              } else if (
                  currentScreen == AppScreen.GRADE || currentScreen == AppScreen.COURSE_QUERY
              ) {
                // 课程查询与成绩查询共用同一个 ViewModel 和学期选择，这里用下拉菜单而不是左右箭头，
                // 这样两个页面的顶部栏都能直接看到当前学期。
                Box {
                  TextButton(onClick = { showGradeTermMenu = true }) {
                    Text(
                        text = gradeUiState.selectedTerm?.itemName ?: "选择学期",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                  }
                  DropdownMenu(
                      expanded = showGradeTermMenu,
                      onDismissRequest = { showGradeTermMenu = false },
                  ) {
                    gradeUiState.terms.forEach { term ->
                      DropdownMenuItem(
                          text = { Text(term.itemName) },
                          onClick = {
                            gradeViewModel?.selectTerm(term)
                            showGradeTermMenu = false
                          },
                      )
                    }
                  }
                }
              } else if (currentScreen == AppScreen.JUDGE_ASSIGNMENTS) {
                IconButton(onClick = { showJudgeSortFilterDialog = true }) {
                  Icon(Icons.Default.Tune, "排序与筛选")
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
                  courseAttributes = courseAttributes,
                  isLoading = todayScheduleState.isLoading,
                  isRefreshing = homeIsRefreshing,
                  error = todayScheduleState.error,
                  todoItems = homeTodoItems,
                  todoLoading = homeTodoLoading,
                  todoLoadingSources = homeTodoLoadingSources,
                  todoFailedSources = homeTodoFailedSources,
                  todoEnriching = judgeUiState.isEnrichingAssignments,
                  scoreUpdateNotice = gradeScoreWatchUiState.notice,
                  todayPlanTasks =
                      planTasks
                          .filter { it.date == homeNow.date.toString() }
                          .sortedBy { it.startTime ?: "23:59" },
                  mailUnreadCount = mailUnread,
                  upcomingExam = homeUpcomingExam,
                  currentWeek = scheduleUiState.currentWeek,
                  onRetrySchedule = { scheduleViewModel.loadTodaySchedule() },
                  onRefresh = { refreshHomeData() },
                  onOpenScoresClick = { openScoresFromHomeNotice() },
                  onDismissScoreNotice = { gradeScoreWatchViewModel.consumeNotice() },
                  onTodoClick = { todoItem -> handleHomeTodoClick(todoItem) },
                  onAddPlanClick = { openPlanEditor(null, homeNow.date.toString(), null, null) },
                  onPlanClick = { task -> openPlanEditor(task, null, null, null) },
                  onPlanDelete = { task -> requestPlanDelete(task) },
                  onMailClick = { navigateTo(AppScreen.MAIL) },
                  onTodayClassClick = { todayClass -> openTodayClassDetail(todayClass) },
                  onExamClick = { navigateTo(AppScreen.EXAM) },
              )
          AppScreen.REGULAR ->
              RegularFeaturesScreen(
                  onScheduleClick = { navigateTo(AppScreen.SCHEDULE) },
                  onExamClick = { navigateTo(AppScreen.EXAM) },
                  onCourseQueryClick = { navigateTo(AppScreen.COURSE_QUERY) },
                  onClassroomClick = { navigateTo(AppScreen.CLASSROOM_QUERY) },
                  onJudgeClick = { navigateTo(AppScreen.JUDGE_ASSIGNMENTS) },
                  onSpaceReservationClick = openDingTalkSpaceReservation,
                  onMailClick = { navigateTo(AppScreen.MAIL) },
                  onCampusGuideClick = openCampusGuide,
              )
          AppScreen.ADVANCED ->
              AdvancedFeaturesScreen(
                  onGradeClick = { navigateTo(AppScreen.GRADE) },
                  onEvaluationClick = { navigateTo(AppScreen.EVALUATION) },
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
                  onExportCalendar = {
                    scheduleViewModel.exportScheduleToCalendar(scheduleUiState.selectedTerm)
                  },
                  isExportingCalendar = scheduleUiState.isExportingCalendar,
                  calendarExportMessage = scheduleUiState.calendarExportMessage,
                  onCalendarExportMessageShown = scheduleViewModel::clearCalendarExportMessage,
                  onTermSelected = { scheduleViewModel.selectTerm(it) },
                  onWeekSelected = { scheduleViewModel.selectWeek(it) },
                  onNavigateBack = { navigateBack() },
                  onEmptySlotClick = { dayOfWeek, section ->
                    val times = scheduleSectionTimes(listOfNotNull(scheduleUiState.weeklySchedule))
                    val sectionTime = times.firstOrNull { it.section == section }
                    val date =
                        scheduleUiState.selectedWeek?.let { week ->
                          runCatching {
                                LocalDate.parse(week.startDate)
                                    .plus(DatePeriod(days = dayOfWeek - 1))
                                    .toString()
                              }
                              .getOrNull()
                        }
                    openPlanEditor(null, date, sectionTime?.start, sectionTime?.end)
                  },
                  onCourseClick = {
                    selectedCourse = it
                    navigateTo(AppScreen.COURSE_DETAIL)
                  },
                  planTasks = planTasks,
                  onPlanClick = { task -> openPlanEditor(task, null, null, null) },
                  onPlanLongClick = { task -> requestPlanDelete(task) },
              )
          AppScreen.EXAM -> ExamScreen(viewModel = examViewModel)
          AppScreen.GRADE -> gradeViewModel?.let { GradeScreen(viewModel = it) }
          AppScreen.COURSE_QUERY -> gradeViewModel?.let { CourseQueryScreen(viewModel = it) }
          AppScreen.COURSE_DETAIL -> selectedCourse?.let { CourseDetailScreen(course = it) }
          AppScreen.CLASSROOM_QUERY ->
              classroomViewModel?.let {
                ClassroomQueryScreen(viewModel = it, onBackClick = { navigateBack() })
              }
          AppScreen.MAIL -> MailScreen(onMailChanged = { refreshMailUnread() })
          AppScreen.EVALUATION -> evaluationViewModel?.let { EvaluationScreen(viewModel = it) }
          AppScreen.JUDGE_ASSIGNMENTS ->
              JudgeAssignmentsScreen(
                  viewModel = judgeViewModel,
                  onAssignmentClick = { assignment ->
                    openJudgeAssignment(assignment.courseId, assignment.assignmentId)
                  },
              )
          AppScreen.JUDGE_ASSIGNMENT_DETAIL ->
              JudgeAssignmentDetailScreen(
                  viewModel = judgeViewModel,
                  onRetry = {
                    judgeDetailKey?.let { (courseId, assignmentId) ->
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
                  AppScreen.COURSE_QUERY,
                  AppScreen.COURSE_DETAIL,
                  AppScreen.MY,
                  AppScreen.SETTINGS,
                  AppScreen.ABOUT,
                  AppScreen.CLASSROOM_QUERY,
                  AppScreen.MAIL,
                  AppScreen.EVALUATION,
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
              cancelClassReminders()
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

    planEdit?.let { request ->
      PlanEditDialog(
          existing = request.existing,
          prefillDate = request.date,
          prefillStartTime = request.startTime,
          prefillEndTime = request.endTime,
          onDismiss = { planEdit = null },
          onSaved = {
            planEdit = null
            planTasks = PlanStore.list()
          },
          todayClasses = todayScheduleState.todayClasses,
      )
    }

    planDeleteCandidate?.let { task ->
      AlertDialog(
          onDismissRequest = { planDeleteCandidate = null },
          title = { Text("删除计划") },
          text = { Text("确定要删除计划「${task.title}」吗？删除后无法恢复。") },
          confirmButton = {
            TextButton(
                onClick = {
                  PlanStore.delete(task.id)
                  planTasks = PlanStore.list()
                  planDeleteCandidate = null
                }
            ) {
              Text("删除")
            }
          },
          dismissButton = { TextButton(onClick = { planDeleteCandidate = null }) { Text("取消") } },
      )
    }

    if (showJudgeSortFilterDialog) {
      JudgeSortFilterDialog(
          uiState = judgeUiState,
          onSortFieldChange = judgeViewModel::setSortField,
          onToggleSortDirection = judgeViewModel::toggleSortDirection,
          onShowExpiredChange = judgeViewModel::setShowExpired,
          onShowOnlyUnfinishedChange = judgeViewModel::setShowOnlyUnfinished,
          onDismiss = { showJudgeSortFilterDialog = false },
      )
    }
  }
}

@Composable
private fun JudgeSortFilterDialog(
    uiState: JudgeUiState,
    onSortFieldChange: (JudgeSortField) -> Unit,
    onToggleSortDirection: () -> Unit,
    onShowExpiredChange: (Boolean) -> Unit,
    onShowOnlyUnfinishedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("排序与筛选") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("排序字段", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                  selected = uiState.sortField == JudgeSortField.DUE_TIME,
                  onClick = { onSortFieldChange(JudgeSortField.DUE_TIME) },
                  label = { Text("截止时间") },
              )
              FilterChip(
                  selected = uiState.sortField == JudgeSortField.START_TIME,
                  onClick = { onSortFieldChange(JudgeSortField.START_TIME) },
                  label = { Text("开始时间") },
              )
            }
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("升序排列", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = uiState.sortAscending, onCheckedChange = { onToggleSortDirection() })
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("显示已截止作业", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = uiState.showExpired, onCheckedChange = { onShowExpiredChange(it) })
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("仅显示未完成", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = uiState.showOnlyUnfinished,
                onCheckedChange = { onShowOnlyUnfinishedChange(it) },
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
  )
}

/**
 * 首页点击今日课程时使用的候选周课表：当前周优先，其次是正在浏览的周，最后是其他已加载的周。
 *
 * 顺序只影响同名课程的去重选择，避免把其他周的同名课程当成今天的课。
 */
private fun homeWeekSchedules(
    currentWeek: Int?,
    weekSchedules: Map<Int, WeeklySchedule>,
    weeklySchedule: WeeklySchedule?,
): List<WeeklySchedule> =
    buildList {
          currentWeek?.let { number -> weekSchedules[number]?.let { add(it) } }
          weeklySchedule?.let { add(it) }
          addAll(weekSchedules.entries.sortedBy { it.key }.map { it.value })
        }
        .distinct()

/**
 * 在首页已加载的周课表中为今日课程摘要定位 [CourseClass]，供课程详情页使用。
 *
 * 首页只有今日摘要，没有详情页需要的课程代码、节次和学分。依次尝试：课程名 + 星期 + 开始时间 → 课程名 + 星期 → 课程名（任意周）。全部失败时返回 null，调用方保持原状，不弹错误。
 */
internal fun findCourseForTodayClass(
    todayClass: TodayClass,
    schedules: List<WeeklySchedule>,
    dayOfWeek: Int,
): CourseClass? {
  val courses = schedules.flatMap { it.arrangedList }
  if (courses.isEmpty()) return null
  val sameName = courses.filter { it.courseName == todayClass.bizName }
  val beginTime = todayClass.time?.substringBefore('-')?.trim()?.takeIf { it.isNotEmpty() }
  return sameName.firstOrNull { it.dayOfWeek == dayOfWeek && it.beginTime == beginTime }
      ?: sameName.firstOrNull { it.dayOfWeek == dayOfWeek }
      ?: sameName.firstOrNull()
}
