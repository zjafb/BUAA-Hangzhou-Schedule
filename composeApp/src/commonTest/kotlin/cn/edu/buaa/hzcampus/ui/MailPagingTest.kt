package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.model.dto.MailAccount
import cn.edu.buaa.hzcampus.model.dto.MailMessage
import cn.edu.buaa.hzcampus.ui.screens.mail.MailBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class MailPagingTest {
  @Test
  fun pagesNewestFirstAndCursorSurvivesNewMail() = runTest {
    var count = 23
    val backend =
        object : MailBackend {
          override suspend fun markRead(account: MailAccount, uid: String) = Unit

          override suspend fun markAllRead(account: MailAccount) = Unit

          override suspend fun delete(account: MailAccount, uid: String) = Unit

          override suspend fun send(
              account: MailAccount,
              to: String,
              subject: String,
              body: String,
          ) = Unit

          override suspend fun fetchBody(account: MailAccount, uid: String) = ""

          override suspend fun countUnread(account: MailAccount) = 0

          override suspend fun connectAndList(account: MailAccount) =
              (1..count).map { MailMessage("$it", "邮件$it", "sender", "", true, "") }
        }
    val account = MailAccount("test", "test", "", "", "", 993, "", 465)
    val first = backend.loadPage(account, 10)
    assertEquals((23 downTo 14).map { "$it" }, first.messages.map { it.uid })
    count++
    val second = backend.loadPage(account, 10, first.nextBeforeUid)
    assertEquals((13 downTo 4).map { "$it" }, second.messages.map { it.uid })
    val last = backend.loadPage(account, 10, second.nextBeforeUid)
    assertEquals(listOf("3", "2", "1"), last.messages.map { it.uid })
    assertNull(last.nextBeforeUid)
  }
}
