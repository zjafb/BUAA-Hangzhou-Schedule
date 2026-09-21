package cn.edu.buaa.hzcampus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** 登录会话结束或连接方式改变时，连同不可见页面一起释放请求与页面缓存。 */
@Composable
internal fun SessionViewModels(sessionKey: String, content: @Composable () -> Unit) {
  key(sessionKey) {
    val owner = remember {
      object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
      }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
  }
}
