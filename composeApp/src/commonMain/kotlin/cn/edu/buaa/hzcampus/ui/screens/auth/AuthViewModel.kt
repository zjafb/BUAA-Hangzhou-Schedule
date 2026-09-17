package cn.edu.buaa.hzcampus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.ApiCallException
import cn.edu.buaa.hzcampus.api.auth.AuthService
import cn.edu.buaa.hzcampus.api.auth.CaptchaRequiredClientException
import cn.edu.buaa.hzcampus.api.auth.UserService
import cn.edu.buaa.hzcampus.api.storage.AuthTokensStore
import cn.edu.buaa.hzcampus.api.storage.CredentialStore
import cn.edu.buaa.hzcampus.model.dto.CaptchaInfo
import cn.edu.buaa.hzcampus.model.dto.UserData
import cn.edu.buaa.hzcampus.model.dto.UserInfo
import cn.edu.buaa.hzcampus.repository.ScheduleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 身份认证与用户信息管理的 ViewModel。 负责处理登录流程（含预加载和验证码）、自动登录逻辑、用户信息加载以及会话状态同步。 */
class AuthViewModel(
    private val authService: AuthService = AuthService(),
    private val userService: UserService = UserService(),
) : ViewModel() {
  private var userInfoLoadedOnce = false
  private var userInfoLoading = false

  private val _uiState = MutableStateFlow(AuthUiState())
  /** 整体认证状态流。 */
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private val _loginForm = MutableStateFlow(LoginFormState())
  /** 登录表单输入状态流。 */
  val loginForm: StateFlow<LoginFormState> = _loginForm.asStateFlow()

  init {
    loadSavedCredentials()
  }

  /** 从本地持久化存储加载保存的凭据。 */
  private fun loadSavedCredentials() {
    val remember = CredentialStore.isRememberPassword()
    val auto = CredentialStore.isAutoLogin()
    if (remember) {
      val username = CredentialStore.getUsername() ?: ""
      val password = CredentialStore.getPassword() ?: ""
      _loginForm.value =
          _loginForm.value.copy(
              username = username,
              password = password,
              rememberPassword = true,
              autoLogin = auto,
          )
    } else {
      _loginForm.value = _loginForm.value.copy(rememberPassword = false, autoLogin = false)
    }
  }

  /** 更新表单中的用户名。 */
  fun updateUsername(username: String) {
    _loginForm.value = _loginForm.value.copy(username = username)
  }

  /** 更新表单中的密码。 */
  fun updatePassword(password: String) {
    _loginForm.value = _loginForm.value.copy(password = password)
  }

  /** 更新表单中的验证码。 */
  fun updateCaptcha(captcha: String) {
    _loginForm.value = _loginForm.value.copy(captcha = captcha)
  }

  /** 更新“记住密码”勾选状态。 */
  fun updateRememberPassword(enabled: Boolean) {
    _loginForm.value = _loginForm.value.copy(rememberPassword = enabled)
    if (!enabled) _loginForm.value = _loginForm.value.copy(autoLogin = false)
  }

  /** 更新“自动登录”勾选状态。 */
  fun updateAutoLogin(enabled: Boolean) {
    _loginForm.value = _loginForm.value.copy(autoLogin = enabled)
    if (enabled) _loginForm.value = _loginForm.value.copy(rememberPassword = true)
  }

  /** 预加载登录状态。 访问服务端探测当前 SSO 是否已登录，并获取登录所需的执行标识（execution）或验证码。 */
  fun preloadLoginState() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isPreloading = true, error = null)
      authService
          .preloadLoginState()
          .onSuccess { response ->
            if (response.userData != null) {
              response.userData?.let { ScheduleStore.useAccount(it.schoolid) }
              // SSO 已登录，执行自动登录逻辑
              _uiState.value =
                  _uiState.value.copy(
                      isPreloading = false,
                      isLoggedIn = true,
                      userData = response.userData,
                      accessToken = response.accessToken?.takeIf { it.isNotBlank() },
                  )
              resetUserInfoState()
              _loginForm.value = LoginFormState()
            } else {
              _uiState.value =
                  _uiState.value.copy(
                      isPreloading = false,
                      captchaRequired = response.captchaRequired,
                      captchaInfo = response.captcha,
                      execution = response.execution,
                  )
              _loginForm.value = _loginForm.value.copy(captcha = "")
            }
            // 预加载结束即为确定结果：已登录进主页，未登录则需要人工输入，落到登录页。
            markStartupAuthResolved()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(isPreloading = false, error = "加载登录状态失败: ${it.message}")
            markStartupAuthResolved()
          }
    }
  }

  /** 仅刷新验证码图片，不重置其他表单状态。 */
  fun refreshCaptcha() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRefreshingCaptcha = true, error = null)
      authService
          .preloadLoginState()
          .onSuccess { response ->
            _uiState.value =
                _uiState.value.copy(
                    isRefreshingCaptcha = false,
                    captchaRequired = response.captchaRequired,
                    captchaInfo = response.captcha,
                    execution = response.execution,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(isRefreshingCaptcha = false, error = "刷新验证码失败: ${it.message}")
          }
    }
  }

  /** 执行登录提交。 */
  fun login() {
    val form = _loginForm.value
    val state = _uiState.value
    if (form.username.isBlank() || form.password.isBlank()) {
      _uiState.value = _uiState.value.copy(error = "用户名和密码不能为空")
      markStartupAuthResolved()
      return
    }
    if (state.captchaRequired && form.captcha.isBlank()) {
      _uiState.value = _uiState.value.copy(error = "请输入验证码")
      markStartupAuthResolved()
      return
    }

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      val captcha = if (state.captchaRequired) form.captcha else null
      authService
          .login(form.username, form.password, captcha, state.execution)
          .onSuccess { loginResponse ->
            ScheduleStore.useAccount(loginResponse.user.schoolid)
            _uiState.value =
                _uiState.value.copy(
                    isLoggedIn = true,
                    isLoading = false,
                    userData = loginResponse.user,
                    accessToken = loginResponse.accessToken.takeIf { it.isNotBlank() },
                )
            resetUserInfoState()
            CredentialStore.setRememberPassword(form.rememberPassword)
            CredentialStore.setAutoLogin(form.autoLogin)
            if (form.rememberPassword) CredentialStore.saveCredentials(form.username, form.password)
            if (!form.rememberPassword) _loginForm.value = LoginFormState()
            markStartupAuthResolved()
          }
          .onFailure { exception ->
            if (exception is CaptchaRequiredClientException) {
              _uiState.value =
                  _uiState.value.copy(
                      isLoading = false,
                      captchaRequired = true,
                      captchaInfo = exception.captcha,
                      execution = exception.execution,
                  )
            } else {
              _uiState.value =
                  _uiState.value.copy(isLoading = false, error = exception.message ?: "登录失败")
            }
            // 自动登录失败（密码错误 / 需要验证码 / 网络异常）：确定结果已给出，可以落到登录页。
            markStartupAuthResolved()
          }
    }
  }

  /**
   * 启动时是否存在可自动恢复的登录：勾选了「自动登录」，或本地存有可恢复的持久会话。
   *
   * 判定分支与 [initializeApp] 保持一致，供启动界面决定是否需要保持加载态、直到认证给出确定结果。
   */
  fun hasRestorableSession(): Boolean =
      CredentialStore.isAutoLogin() ||
          runCatching { authService.hasPersistedSession() }.getOrDefault(false)

  /** 应用全局初始化入口。 用于检查本地 Token 是否有效，若失效则根据设置决定跳转登录页或尝试自动登录。 */
  fun initializeApp() {
    if (ConnectionRuntime.currentMode() == null) return
    viewModelScope.launch {
      val restoredAccessToken = AuthTokensStore.get()?.accessToken?.takeIf { it.isNotBlank() }
      if (!authService.hasPersistedSession()) {
        // 启动流程仍会继续（自动登录或预加载登录状态），由它们给出确定结果。
        if (CredentialStore.isAutoLogin()) login() else preloadLoginState()
        return@launch
      }

      authService.applyStoredTokens()
      _uiState.value = _uiState.value.copy(isLoading = true)
      authService
          .getAuthStatus()
          .onSuccess { status ->
            ScheduleStore.useAccount(status.user.schoolid)
            _uiState.value =
                _uiState.value.copy(
                    isLoggedIn = true,
                    isLoading = false,
                    userData = status.user,
                    accessToken = restoredAccessToken,
                )
            resetUserInfoState()
            markStartupAuthResolved()
          }
          .onFailure { error ->
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (error is ApiCallException && error.code == "auth_upstream_timeout") {
              _uiState.value = _uiState.value.copy(error = error.message)
              markStartupAuthResolved()
              return@onFailure
            }
            authService.clearStoredSession()
            // 注意：这里不结束启动流程，后面还会尝试自动登录/预加载登录状态，
            // 由它们的终态（成功或失败）来结束，避免中途闪出登录页。
            if (CredentialStore.isAutoLogin()) login() else preloadLoginState()
          }
    }
  }

  /** 标记启动认证流程已经给出确定结果（成功登录、确定失败，或明确需要人工登录）。 */
  private fun markStartupAuthResolved() {
    if (_uiState.value.isStartupAuthResolved) return
    _uiState.value = _uiState.value.copy(isStartupAuthResolved = true)
  }

  fun switchConnectionMode(mode: ConnectionMode) {
    viewModelScope.launch {
      ConnectionRuntime.switchMode(mode)
      resetUserInfoState()
      _uiState.value = AuthUiState()
      loadSavedCredentials()
      preloadLoginState()
    }
  }

  /** 注销登录，清理所有本地状态。 */
  fun logout() {
    ScheduleStore.forgetAccount()
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true)
      authService
          .logout()
          .onSuccess {
            resetUserInfoState()
            _uiState.value = AuthUiState()
            _loginForm.value = LoginFormState()
            preloadLoginState()
          }
          .onFailure {
            resetUserInfoState()
            _uiState.value = AuthUiState(error = "注销完成但有警告: ${it.message}")
            preloadLoginState()
          }
    }
  }

  /** 按需加载用户的详细档案信息。 */
  fun ensureUserInfoLoaded(forceRefresh: Boolean = false) {
    if (userInfoLoading) return
    if (!forceRefresh && (userInfoLoadedOnce || _uiState.value.userInfo != null)) {
      return
    }
    userInfoLoading = true
    viewModelScope.launch {
      userService
          .getUserInfo()
          .onSuccess { info ->
            userInfoLoadedOnce = true
            _uiState.value = _uiState.value.copy(userInfo = info)
          }
          .onFailure { userInfoLoadedOnce = false }
      userInfoLoading = false
    }
  }

  /** 前台恢复时验证当前会话是否仍然有效。 如果会话过期或网络暂时不可用，根据错误类型决定保留还是清除会话。 */
  fun validateSession() {
    if (!_uiState.value.isLoggedIn) return
    viewModelScope.launch {
      authService
          .getAuthStatus()
          .onSuccess { status ->
            ScheduleStore.useAccount(status.user.schoolid)
            _uiState.value =
                _uiState.value.copy(
                    isLoggedIn = true,
                    userData = status.user,
                )
          }
          .onFailure { error ->
            if (error is ApiCallException && error.code == "auth_upstream_timeout") {
              // 网络暂时不可用，保留会话状态
              return@onFailure
            }
            // 会话确实过期了
            authService.clearStoredSession()
            resetUserInfoState()
            _uiState.value = AuthUiState()
            if (CredentialStore.isAutoLogin()) login() else preloadLoginState()
          }
    }
  }

  /** 清除当前的错误提示。 */
  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  private fun resetUserInfoState() {
    userInfoLoadedOnce = false
    userInfoLoading = false
    _uiState.value = _uiState.value.copy(userInfo = null)
  }
}

/** 认证模块 UI 状态模型。 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isPreloading: Boolean = false,
    val isRefreshingCaptcha: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userData: UserData? = null,
    val userInfo: UserInfo? = null,
    val accessToken: String? = null,
    val error: String? = null,
    val captchaRequired: Boolean = false,
    val captchaInfo: CaptchaInfo? = null,
    val execution: String? = null,
    /**
     * 启动认证流程是否已经给出确定结果（成功登录 / 确定失败 / 明确需要人工登录）。
     *
     * 默认 false，因此启动界面在"存在可自动恢复的登录"时可以安全地保持 Splash， 直到置位后才允许落到登录页，避免自动登录期间闪一下登录页。
     */
    val isStartupAuthResolved: Boolean = false,
)

/** 登录表单本地交互状态模型。 */
data class LoginFormState(
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
)
