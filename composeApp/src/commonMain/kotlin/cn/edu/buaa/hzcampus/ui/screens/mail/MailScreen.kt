package cn.edu.buaa.hzcampus.ui.screens.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.buaa.hzcampus.api.storage.MailAccountsStore
import cn.edu.buaa.hzcampus.model.dto.MailAccount
import cn.edu.buaa.hzcampus.model.dto.MailMessage
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 邮件查询主界面：账号管理 + 收件箱列表 + 写邮件。 */
@Composable
fun MailScreen(modifier: Modifier = Modifier) {
  val backend = remember { createMailBackend() }
  val scope = rememberCoroutineScope()
  var accounts by remember { mutableStateOf(MailAccountsStore.list()) }
  var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
  var messages by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  var showAccountForm by remember { mutableStateOf(accounts.isEmpty()) }
  var editingAccount by remember { mutableStateOf<MailAccount?>(null) }
  var showCompose by remember { mutableStateOf(false) }
  var selectedMessage by remember { mutableStateOf<MailMessage?>(null) }
  var selectionMode by remember { mutableStateOf(false) }
  var selectedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
  var showAccountMenu by remember { mutableStateOf(false) }

  val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }

  fun reloadAccounts() {
    accounts = MailAccountsStore.list()
    if (selectedAccountId !in accounts.map { it.id }) {
      selectedAccountId = accounts.firstOrNull()?.id
    }
    selectionMode = false
    selectedUids = emptySet()
  }

  fun refresh() {
    val acc = selectedAccount ?: return
    scope.launch {
      loading = true
      error = null
      runCatching { backend.connectAndList(acc) }
          .onSuccess { messages = it }
          .onFailure { error = it.message ?: "连接失败，请检查账号配置" }
      loading = false
    }
  }

  LaunchedEffect(selectedAccountId) { refresh() }

  if (showAccountForm) {
    MailAccountFormDialog(
        existing = editingAccount,
        onDismiss = {
          showAccountForm = false
          editingAccount = null
          reloadAccounts()
        },
        onSaved = {
          reloadAccounts()
          showAccountForm = false
          editingAccount = null
        },
    )
    return
  }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    // 账号切换栏
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box {
        TextButton(onClick = { showAccountMenu = true }) {
          Text(
              selectedAccount?.name ?: selectedAccount?.email ?: "选择账号",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
          accounts.forEach { acc ->
            DropdownMenuItem(
                text = { Text(acc.name.ifBlank { acc.email }) },
                onClick = {
                  selectedAccountId = acc.id
                  showAccountMenu = false
                },
            )
          }
        }
      }
      Spacer(modifier = Modifier.weight(1f))
      IconButton(
          onClick = {
            editingAccount = null
            showAccountForm = true
          }
      ) {
        Icon(Icons.Default.Add, "添加账号")
      }
    }

    // 操作栏
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selectionMode) {
        TextButton(onClick = { selectedUids = messages.map { it.uid }.toSet() }) { Text("全选") }
        TextButton(
            onClick = {
              val acc = selectedAccount
              if (acc != null) {
                scope.launch {
                  selectedUids.forEach { uid -> runCatching { backend.delete(acc, uid) } }
                  refresh()
                }
              }
              selectionMode = false
              selectedUids = emptySet()
            },
            enabled = selectedUids.isNotEmpty(),
        ) {
          Icon(Icons.Default.Delete, null)
          Text("删除(${selectedUids.size})")
        }
        TextButton(
            onClick = {
              selectionMode = false
              selectedUids = emptySet()
            }
        ) {
          Text("取消")
        }
      } else {
        TextButton(onClick = { showCompose = true }) {
          Icon(Icons.AutoMirrored.Filled.Send, null)
          Text("写邮件")
        }
        TextButton(
            onClick = {
              val acc = selectedAccount
              if (acc != null) {
                scope.launch {
                  runCatching { backend.markAllRead(acc) }
                  refresh()
                }
              }
            },
        ) {
          Icon(Icons.Default.DoneAll, null)
          Text("一键已读")
        }
        IconButton(onClick = { selectionMode = true }) { Icon(Icons.Default.Check, "多选") }
        IconButton(onClick = { refresh() }, enabled = !loading) {
          Icon(Icons.Default.Refresh, "刷新")
        }
      }
    }

    // 列表
    when {
      loading && messages.isEmpty() -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      error != null && messages.isEmpty() -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { refresh() }) { Text("重试") }
          }
        }
      }
      messages.isEmpty() -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("收件箱为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      else -> {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(messages) { msg ->
            MailMessageRow(
                message = msg,
                selectionMode = selectionMode,
                selected = msg.uid in selectedUids,
                onClick = {
                  if (selectionMode) {
                    selectedUids =
                        if (msg.uid in selectedUids) selectedUids - msg.uid
                        else selectedUids + msg.uid
                  } else {
                    selectedMessage = msg
                  }
                },
            )
          }
        }
      }
    }
  }

  // 详情弹窗（正文拉取时顺便标记已读）
  selectedMessage?.let { msg ->
    val acc = selectedAccount
    if (acc != null) {
      MessageDetailDialog(
          message = msg,
          account = acc,
          backend = backend,
          onDismiss = { selectedMessage = null },
      )
    }
  }

  if (showCompose && selectedAccount != null) {
    ComposeDialog(
        account = selectedAccount!!,
        backend = backend,
        onDismiss = { showCompose = false },
        onSent = { showCompose = false },
    )
  }
}

@Composable
private fun MailMessageRow(
    message: MailMessage,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      shape = MaterialTheme.shapes.medium,
      color =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selectionMode) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
      } else {
        Box(
            modifier =
                Modifier.size(10.dp)
                    .background(
                        if (message.unread) Color(0xFFE53935) else Color(0xFF9E9E9E),
                        CircleShape,
                    )
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = message.subject,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (message.unread) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = message.from,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (message.bodyPreview.isNotBlank()) {
          Text(
              text = message.bodyPreview,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
          text = message.date.take(19).replace("T", " "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun MessageDetailDialog(
    message: MailMessage,
    account: MailAccount,
    backend: MailBackend,
    onDismiss: () -> Unit,
) {
  var body by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var retryKey by remember { mutableStateOf(0) }

  LaunchedEffect(message.uid, retryKey) {
    body = null
    error = null
    val result =
        withTimeoutOrNull(30_000) { runCatching { backend.fetchBody(account, message.uid) } }
    when {
      result == null -> error = "加载正文超时，请重试"
      result.isSuccess -> body = result.getOrNull()
      else -> error = result.exceptionOrNull()?.message ?: "加载正文失败"
    }
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(message.subject, maxLines = 3, overflow = TextOverflow.Ellipsis) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("发件人：${message.from}", style = MaterialTheme.typography.bodyMedium)
          Text("时间：${message.date}", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          when {
            error != null -> {
              Text(error!!, color = MaterialTheme.colorScheme.error)
              TextButton(onClick = { retryKey++ }) { Text("重试") }
            }
            body != null -> Text(body!!, style = MaterialTheme.typography.bodyMedium)
            else -> {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在加载正文…", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

@Composable
private fun ComposeDialog(
    account: MailAccount,
    backend: MailBackend,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var to by remember { mutableStateOf("") }
  var subject by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
      Column(
          modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("写邮件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("发件人：${account.email}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text("收件人") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("主题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("正文") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.End),
        ) {
          TextButton(onClick = onDismiss, enabled = !sending) { Text("取消") }
          Button(
              onClick = {
                if (to.isBlank()) return@Button
                scope.launch {
                  sending = true
                  error = null
                  runCatching { backend.send(account, to.trim(), subject.trim(), body) }
                      .onSuccess { onSent() }
                      .onFailure { error = it.message ?: "发送失败" }
                  sending = false
                }
              },
              enabled = !sending && to.isNotBlank(),
          ) {
            if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("发送")
          }
        }
      }
    }
  }
}

@Composable
private fun MailAccountFormDialog(
    existing: MailAccount?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
  var name by remember { mutableStateOf(existing?.name ?: "") }
  var email by remember { mutableStateOf(existing?.email ?: "") }
  var password by remember { mutableStateOf(existing?.password ?: "") }
  var passwordVisible by remember { mutableStateOf(false) }
  var imapHost by remember { mutableStateOf(existing?.imapHost ?: "") }
  var imapPort by remember { mutableStateOf(existing?.imapPort?.toString() ?: "993") }
  var smtpHost by remember { mutableStateOf(existing?.smtpHost ?: "") }
  var smtpPort by remember { mutableStateOf(existing?.smtpPort?.toString() ?: "465") }
  var showHelp by remember { mutableStateOf(false) }

  fun infer(email: String) {
    val s = inferServers(email) ?: return
    imapHost = s.imapHost
    imapPort = s.imapPort.toString()
    smtpHost = s.smtpHost
    smtpPort = s.smtpPort.toString()
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
      Column(
          modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
            if (existing == null) "添加邮箱账号" else "编辑邮箱账号",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = {
              email = it
              if (existing == null) infer(it)
            },
            label = { Text("邮箱地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码/授权码") },
            singleLine = true,
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector =
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                )
              }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = imapHost,
              onValueChange = { imapHost = it },
              label = { Text("IMAP 服务器") },
              singleLine = true,
              modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
              value = imapPort,
              onValueChange = { imapPort = it },
              label = { Text("端口") },
              singleLine = true,
              modifier = Modifier.weight(0.5f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = smtpHost,
              onValueChange = { smtpHost = it },
              label = { Text("SMTP 服务器") },
              singleLine = true,
              modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
              value = smtpPort,
              onValueChange = { smtpPort = it },
              label = { Text("端口") },
              singleLine = true,
              modifier = Modifier.weight(0.5f),
          )
        }
        Text(
            "常见邮箱会自动推断服务器：QQ/163/126/Gmail/Outlook/北航。密码请使用 SMTP/IMAP 授权码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "如何开启 IMAP/SMTP 并获取授权码？点此查看说明",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { showHelp = true },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.End),
        ) {
          TextButton(onClick = onDismiss) { Text("取消") }
          Button(
              onClick = {
                if (email.isBlank() || password.isBlank() || imapHost.isBlank()) return@Button
                MailAccountsStore.upsert(
                    MailAccount(
                        id = existing?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                        name = name.trim(),
                        email = email.trim(),
                        password = password,
                        imapHost = imapHost.trim(),
                        imapPort = imapPort.toIntOrNull() ?: 993,
                        smtpHost = smtpHost.trim(),
                        smtpPort = smtpPort.toIntOrNull() ?: 465,
                        ssl = true,
                    )
                )
                onSaved()
              },
          ) {
            Text("保存")
          }
        }
      }
    }

    if (showHelp) {
      AlertDialog(
          onDismissRequest = { showHelp = false },
          title = { Text("邮箱 IMAP/SMTP 配置说明") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("1. 先在邮箱网页版开启 IMAP 和 SMTP 服务。")
              Text("2. 密码请填写「授权码」（QQ/163/126 在邮箱设置中生成），不是登录密码。")
              Text("3. 服务器与端口（SSL）：")
              Text("　QQ：imap.qq.com:993，smtp.qq.com:465")
              Text("　163：imap.163.com:993，smtp.163.com:465")
              Text("　126：imap.126.com:993，smtp.126.com:465")
              Text("　北航：imap.buaa.edu.cn:993，smtp.buaa.edu.cn:465（仅校园网可达）")
              Text("　Gmail：imap.gmail.com:993，smtp.gmail.com:465（需应用专用密码）")
              Text("　Outlook：outlook.office365.com:993，smtp.office365.com:587（STARTTLS）")
              Text("4. 若提示「服务器或端口不正确」或「连接超时」，多为当前网络封锁邮件端口，请换手机流量再试。")
            }
          },
          confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
      )
    }
  }
}

private data class InferredServers(
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
)

private fun inferServers(email: String): InferredServers? {
  val domain = email.substringAfter('@', "").lowercase()
  if (domain.isBlank()) return null
  return when {
    domain.contains("qq.com") -> InferredServers("imap.qq.com", 993, "smtp.qq.com", 465)
    domain.contains("163.com") -> InferredServers("imap.163.com", 993, "smtp.163.com", 465)
    domain.contains("126.com") -> InferredServers("imap.126.com", 993, "smtp.126.com", 465)
    domain.contains("gmail.com") -> InferredServers("imap.gmail.com", 993, "smtp.gmail.com", 465)
    domain.contains("outlook") || domain.contains("hotmail") ->
        InferredServers("outlook.office365.com", 993, "smtp.office365.com", 587)
    domain == "buaa.edu.cn" || domain.endsWith(".buaa.edu.cn") ->
        InferredServers("imap.buaa.edu.cn", 993, "smtp.buaa.edu.cn", 465)
    else -> null
  }
}
