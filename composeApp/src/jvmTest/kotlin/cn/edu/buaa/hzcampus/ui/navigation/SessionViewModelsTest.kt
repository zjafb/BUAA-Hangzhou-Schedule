package cn.edu.buaa.hzcampus.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import org.junit.Rule
import org.junit.Test

class SessionViewModelsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun switchingSessionClearsHiddenPagesAndCreatesFreshModels() {
    var session by mutableStateOf("first")
    var visible by mutableStateOf(true)
    var clears = 0
    var current: ViewModel? = null
    compose.setContent {
      SessionViewModels(session) {
        if (visible) {
          current =
              viewModel(key = "test-page") {
                object : ViewModel() {
                  override fun onCleared() {
                    clears++
                  }
                }
              }
        }
      }
    }
    compose.waitForIdle()
    val first = current
    compose.runOnIdle { visible = false }
    compose.runOnIdle {
      assertEquals(0, clears)
      session = "second"
    }
    compose.runOnIdle {
      assertEquals(1, clears)
      visible = true
    }
    compose.runOnIdle { assertNotSame(first, current) }
  }
}
