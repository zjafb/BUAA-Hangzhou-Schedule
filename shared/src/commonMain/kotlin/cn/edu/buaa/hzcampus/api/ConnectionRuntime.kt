package cn.edu.buaa.hzcampus.api

import cn.edu.buaa.hzcampus.api.auth.ApiClientProvider
import cn.edu.buaa.hzcampus.api.core.ApiFactory
import cn.edu.buaa.hzcampus.api.core.DefaultApiFactory
import cn.edu.buaa.hzcampus.api.local.LocalAuthSessionStore
import cn.edu.buaa.hzcampus.api.local.LocalCookieStore
import cn.edu.buaa.hzcampus.api.local.LocalUpstreamClientProvider
import cn.edu.buaa.hzcampus.api.storage.AuthTokensStore
import cn.edu.buaa.hzcampus.api.storage.ClientIdStore
import cn.edu.buaa.hzcampus.api.storage.CredentialStore
import cn.edu.buaa.hzcampus.api.storage.GradeScoreCacheStore
import cn.edu.buaa.hzcampus.repository.GlobalTermRepository
import cn.edu.buaa.hzcampus.supportsLocalConnectionModes
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionMode(
    val storageKey: String,
    val displayName: String,
    val description: String,
) {
  DIRECT(
      storageKey = "direct",
      displayName = "直连模式",
      description = "直接访问校内上游。",
  ),
  WEBVPN(
      storageKey = "webvpn",
      displayName = "WebVPN模式",
      description = "通过北航 WebVPN 访问上游。",
  ),
  SERVER_RELAY(
      storageKey = "server_relay",
      displayName = "服务器中转模式",
      description = "使用服务器代理访问。",
  );

  companion object {
    fun fromStorageKey(value: String?): ConnectionMode? =
        entries.firstOrNull { it.storageKey == value?.trim()?.lowercase() }
  }
}

object ConnectionModeStore {
  private const val KEY_CONNECTION_MODE = "connection_mode"
  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun save(mode: ConnectionMode) {
    settings.putString(KEY_CONNECTION_MODE, mode.storageKey)
  }

  fun get(): ConnectionMode? =
      ConnectionMode.fromStorageKey(settings.getStringOrNull(KEY_CONNECTION_MODE))

  fun clear() {
    settings.remove(KEY_CONNECTION_MODE)
  }
}

object ConnectionRuntime {
  internal var apiFactoryProvider: () -> ApiFactory = { DefaultApiFactory }

  private val _currentMode = MutableStateFlow(ConnectionModeStore.get())
  val currentModeFlow: StateFlow<ConnectionMode?> = _currentMode.asStateFlow()

  internal fun apiFactory(): ApiFactory = apiFactoryProvider()

  fun availableModes(): List<ConnectionMode> =
      if (supportsLocalConnectionModes()) {
        ConnectionMode.entries
      } else {
        listOf(ConnectionMode.SERVER_RELAY)
      }

  fun resolveSelectedMode(): ConnectionMode? {
    val allowedModes = availableModes()
    val storedMode = ConnectionModeStore.get()
    val resolvedMode =
        when {
          storedMode != null && storedMode in allowedModes -> storedMode
          allowedModes.size == 1 -> allowedModes.single().also(ConnectionModeStore::save)
          else -> null
        }
    _currentMode.value = resolvedMode
    return resolvedMode
  }

  fun currentMode(): ConnectionMode? = _currentMode.value ?: resolveSelectedMode()

  fun switchMode(mode: ConnectionMode) {
    require(mode in availableModes()) {
      "Connection mode ${mode.name} is not supported on this platform"
    }
    val current = currentMode()
    if (current == mode && ConnectionModeStore.get() == mode) {
      return
    }
    resetSession()
    ConnectionModeStore.save(mode)
    _currentMode.value = mode
  }

  fun resetSession() {
    AuthTokensStore.clearAllScopes()
    ClientIdStore.clearAllScopes()
    GradeScoreCacheStore.clearAllScopes()
    LocalAuthSessionStore.clearAllScopes()
    LocalCookieStore.clearAllScopes()
    CredentialStore.setAutoLogin(false)
    ApiClientProvider.reset()
    DefaultApiFactory.clearCachedBackends()
    LocalUpstreamClientProvider.reset()
    GlobalTermRepository.instance.clear()
  }

  fun clearSelectedMode() {
    resetSession()
    ConnectionModeStore.clear()
    _currentMode.value = null
  }
}

internal object ModeScopedSessionStore {
  private const val LEGACY_PREFIX = ""

  fun scopedKey(key: String, mode: ConnectionMode? = ConnectionModeStore.get()): String {
    val effectiveMode = mode ?: ConnectionMode.SERVER_RELAY
    return "mode_${effectiveMode.storageKey}_$key"
  }

  fun legacyKey(key: String): String = "$LEGACY_PREFIX$key"
}
