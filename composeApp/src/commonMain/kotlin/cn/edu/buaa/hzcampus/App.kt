package cn.edu.buaa.hzcampus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.AnnouncementService
import cn.edu.buaa.hzcampus.api.auth.AppAnnouncement
import cn.edu.buaa.hzcampus.api.auth.AppVersionCheckResponse
import cn.edu.buaa.hzcampus.api.auth.UpdateService
import cn.edu.buaa.hzcampus.api.storage.AnnouncementReadStore
import cn.edu.buaa.hzcampus.repository.ScheduleStore
import cn.edu.buaa.hzcampus.ui.common.components.ReleaseNotesText
import cn.edu.buaa.hzcampus.ui.navigation.MainAppScreen
import cn.edu.buaa.hzcampus.ui.screens.auth.AuthViewModel
import cn.edu.buaa.hzcampus.ui.screens.auth.ConnectionModeSelectionScreen
import cn.edu.buaa.hzcampus.ui.screens.auth.LoginScreen
import cn.edu.buaa.hzcampus.ui.screens.schedule.OfflineScheduleScreen
import cn.edu.buaa.hzcampus.ui.screens.splash.SplashScreen
import cn.edu.buaa.hzcampus.ui.theme.HzCampusTheme
import cn.edu.buaa.hzcampus.ui.theme.PreloadFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用程序顶层入口 Composable。 负责全局状态管理，包括：
 * 1. 字体预加载。
 * 2. 整体主题应用。
 * 3. 认证状态监听与自动登录逻辑。
 * 4. 启动界面 (Splash) 与主界面/登录界面的切换。
 * 5. 软件更新检测与弹窗提示。
 */
@Composable
@Preview
fun App() {
  // 预加载应用所需的中文字体
  PreloadFonts()

  HzCampusTheme {
    val authViewModel: AuthViewModel = viewModel { AuthViewModel() }
    val uiState by authViewModel.uiState.collectAsState()
    val loginForm by authViewModel.loginForm.collectAsState()
    val appScope = rememberCoroutineScope()
    val availableConnectionModes = remember { ConnectionRuntime.availableModes() }
    var selectedConnectionMode by remember { mutableStateOf<ConnectionMode?>(null) }
    // 启动判定结果：null 表示尚未完成解析，此时必须停留在 Splash。
    var startupDecision by remember { mutableStateOf<StartupDecision?>(null) }
    var showOfflineSchedule by remember { mutableStateOf(false) }

    // 启动流程控制状态
    var isSplashFinished by remember { mutableStateOf(false) }

    // 更新检测逻辑
    val updateService = remember { UpdateService() }
    val announcementService = remember { AnnouncementService() }
    var updateInfo by remember { mutableStateOf<AppVersionCheckResponse?>(null) }
    var announcementInfo by remember { mutableStateOf<AppAnnouncement?>(null) }
    val uriHandler = LocalUriHandler.current

    suspend fun checkStartupPrompts() {
      // 北航杭州版不接入 UBAA 服务器的版本检查与公告，避免误报 UBAA 版本(1.8.0)和公告。
    }

    suspend fun bootstrapForMode(mode: ConnectionMode) {
      selectedConnectionMode = mode
      launchStartupTasks(
          scope = appScope,
          initializeAuthentication = authViewModel::initializeApp,
          checkStartupPrompts = ::checkStartupPrompts,
      )
    }

    LaunchedEffect(Unit) {
      val mode = ConnectionRuntime.resolveSelectedMode()
      selectedConnectionMode = mode
      // 启动瞬间同步判定"是否存在可自动恢复的登录"：勾选了自动登录，或本地存有可恢复的持久会话。
      // 该判定必须与 AuthViewModel.initializeApp() 的分支保持一致，否则会出现
      // "自动登录还没跑完就先结束 Splash → 闪一下登录页 → 再切回主页"。
      val restoreExpected =
          mode != null &&
              runCatching { authViewModel.hasRestorableSession() }.getOrDefault(false)
      // 没有自动恢复能力时保留快速路径：本地有课表就尽早结束 Splash，
      // 让用户直接看到登录页与「查看离线课表」入口。
      if (!restoreExpected && ScheduleStore.hasSavedSchedule()) isSplashFinished = true
      // 先启动认证流程（它内部会立刻置位认证状态），再发布启动判定，
      // 让"判定结果"与"认证进行中"在同一帧内一起生效，避免中间帧闪出登录页。
      mode?.let { bootstrapForMode(it) }
      startupDecision = StartupDecision(mode = mode, authRestoreExpected = restoreExpected)
    }

    // 前台恢复时验证会话有效性
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, uiState.isLoggedIn) {
      var wasInBackground = false
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_STOP -> wasInBackground = true
          Lifecycle.Event.ON_RESUME -> {
            if (wasInBackground) {
              wasInBackground = false
              if (uiState.isLoggedIn) authViewModel.validateSession()
            }
          }
          else -> {}
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      try {
        awaitCancellation()
      } finally {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    // 根据认证状态与启动判定决定何时隐藏 Splash 界面。
    //
    // 三种结果分别怎么走（仅在"存在可自动恢复的登录"时才会等待）：
    //   1. 成功（isLoggedIn && userData != null）→ 结束 Splash，进入主页；
    //   2. 确定失败 / 需要人工输入（密码错误、会话失效且未开启自动登录、需要验证码、
    //      超时或断网导致的 error）→ 结束 Splash，落到登录页；
    //   3. 仍在恢复登录中（isStartupAuthResolved == false）→ 保持 Splash，绝不中间闪登录页。
    //
    // 没有自动恢复能力时 authRestoreExpected 为 false，行为与旧版一致：尽快显示登录页。
    LaunchedEffect(
        uiState.isLoggedIn,
        uiState.error,
        uiState.isLoading,
        uiState.isPreloading,
        uiState.isRefreshingCaptcha,
        uiState.isStartupAuthResolved,
        startupDecision,
    ) {
      val decision = startupDecision
      val authBusy = uiState.isLoading || uiState.isPreloading || uiState.isRefreshingCaptcha
      val restoreInFlight =
          decision != null && decision.authRestoreExpected && !uiState.isStartupAuthResolved
      val shouldEndSplash =
          (uiState.isLoggedIn && uiState.userData != null) ||
              (uiState.error != null && !authBusy) ||
              (decision != null &&
                  !restoreInFlight &&
                  !authBusy &&
                  !uiState.isLoggedIn &&
                  uiState.error == null)

      if (shouldEndSplash) isSplashFinished = true
    }

    // 安全网：认证流程因任何异常原因始终没有给出确定结果时，也不会永久卡在 Splash。
    // 取值明显大于认证客户端的超时（连接 8s / 请求 15s），正常流程不会走到这里。
    LaunchedEffect(startupDecision) {
      val decision = startupDecision ?: return@LaunchedEffect
      if (!decision.authRestoreExpected) return@LaunchedEffect
      delay(SPLASH_RESTORE_TIMEOUT_MS)
      if (!isSplashFinished) isSplashFinished = true
    }

    // 版本更新对话框
    if (updateInfo != null) {
      val release = updateInfo!!
      val releaseNotes = release.releaseNotes?.takeIf { it.isNotBlank() } ?: "点击下方按钮下载最新客户端。"
      val updateMessage = buildString {
        append("当前客户端版本：")
        append(AppInfo.version)
        append('\n')
        append("最新客户端版本：")
        append(release.latestVersion)
        append("\n\n")
        append(releaseNotes)
      }

      AlertDialog(
          onDismissRequest = { updateInfo = null },
          title = { Text("发现新版本") },
          text = {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
              ReleaseNotesText(updateMessage)
            }
          },
          confirmButton = {
            TextButton(
                onClick = {
                  uriHandler.openUri(release.downloadUrl)
                  updateInfo = null
                }
            ) {
              Text("前往下载")
            }
          },
          dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("稍后再说") } },
      )
    }

    if (shouldShowAnnouncementDialog(updateInfo, announcementInfo)) {
      val announcement = announcementInfo!!
      AlertDialog(
          onDismissRequest = {},
          title = { Text(announcement.title) },
          text = {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
              Text(announcement.content)
            }
          },
          confirmButton = {
            TextButton(
                onClick = {
                  confirmAnnouncement(
                      announcement = announcement,
                      openUri = uriHandler::openUri,
                      markRead = AnnouncementReadStore::markRead,
                  )
                  announcementInfo = null
                }
            ) {
              Text(announcement.confirmText?.takeIf { it.isNotBlank() } ?: "我知道了")
            }
          },
      )
    }

    // 视图切换状态机
    when {
      startupDecision == null -> SplashScreen(modifier = Modifier.fillMaxSize())
      selectedConnectionMode == null ->
          ConnectionModeSelectionScreen(
              availableModes = availableConnectionModes,
              onConfirm = { mode ->
                appScope.launch { bootstrapForMode(mode.also(ConnectionRuntime::switchMode)) }
              },
              modifier = Modifier.fillMaxSize(),
          )
      !isSplashFinished -> SplashScreen(modifier = Modifier.fillMaxSize())
      showOfflineSchedule -> OfflineScheduleScreen(onBack = { showOfflineSchedule = false })
      uiState.isLoggedIn && uiState.userData != null -> {
        val userData = uiState.userData!!
        MainAppScreen(
            userData = userData,
            userInfo = uiState.userInfo,
            connectionMode = selectedConnectionMode ?: ConnectionMode.SERVER_RELAY,
            availableConnectionModes = availableConnectionModes,
            onEnsureUserInfo = { authViewModel.ensureUserInfoLoaded() },
            onConnectionModeSelected = { mode ->
              selectedConnectionMode = mode
              authViewModel.switchConnectionMode(mode)
              appScope.launch { checkStartupPrompts() }
            },
            onLogoutClick = { authViewModel.logout() },
            modifier = Modifier.fillMaxSize(),
        )
      }
      else -> {
        LoginScreen(
            loginFormState = loginForm,
            currentConnectionMode = selectedConnectionMode ?: ConnectionMode.SERVER_RELAY,
            availableConnectionModes = availableConnectionModes,
            onUsernameChange = { authViewModel.updateUsername(it) },
            onPasswordChange = { authViewModel.updatePassword(it) },
            onCaptchaChange = { authViewModel.updateCaptcha(it) },
            onRememberPasswordChange = { authViewModel.updateRememberPassword(it) },
            onAutoLoginChange = { authViewModel.updateAutoLogin(it) },
            onConnectionModeSelected = { mode ->
              selectedConnectionMode = mode
              authViewModel.switchConnectionMode(mode)
              appScope.launch { checkStartupPrompts() }
            },
            onLoginClick = { authViewModel.login() },
            onRefreshCaptcha = { authViewModel.refreshCaptcha() },
            isLoading = uiState.isLoading,
            isRefreshingCaptcha = uiState.isRefreshingCaptcha,
            captchaRequired = uiState.captchaRequired,
            captchaInfo = uiState.captchaInfo,
            error = uiState.error,
            onOfflineSchedule =
                if (ScheduleStore.hasSavedSchedule()) ({ showOfflineSchedule = true }) else null,
            modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
        )
      }
    }

    // 错误消息自动淡出
    LaunchedEffect(uiState.error) {
      if (uiState.error != null) {
        delay(5000)
        authViewModel.clearError()
      }
    }
  }
}

internal fun launchStartupTasks(
    scope: CoroutineScope,
    initializeAuthentication: () -> Unit,
    checkStartupPrompts: suspend () -> Unit,
) {
  scope.launch { initializeAuthentication() }
  scope.launch { checkStartupPrompts() }
}

/** 等待自动登录/会话恢复给出确定结果的兜底时长，超过后强制结束 Splash。 */
private const val SPLASH_RESTORE_TIMEOUT_MS = 30_000L

/**
 * 启动阶段的判定结果。
 *
 * @property mode 解析出的连接模式；为 null 表示需要用户先选择连接模式（此时不会等待认证）。
 * @property authRestoreExpected 启动时是否存在可自动恢复的登录（勾选自动登录，或本地存有可恢复的持久会话）。
 *   为 true 时必须保持 Splash 直到认证给出确定结果，避免先闪一下登录页再自动登录进去。
 */
internal data class StartupDecision(
    val mode: ConnectionMode?,
    val authRestoreExpected: Boolean,
)

internal fun shouldShowAnnouncementDialog(
    updateInfo: AppVersionCheckResponse?,
    announcement: AppAnnouncement?,
): Boolean = updateInfo == null && announcement != null

internal fun confirmAnnouncement(
    announcement: AppAnnouncement,
    openUri: (String) -> Unit,
    markRead: (String) -> Unit,
) {
  markRead(announcement.id)
  announcement.linkUrl?.takeIf { it.isNotBlank() }?.let(openUri)
}
