package cn.edu.buaa.hzcampus.api

import cn.edu.buaa.hzcampus.api.feature.ihomeWebsiteUrl
import cn.edu.buaa.hzcampus.api.local.LocalWebVpnSupport
import cn.edu.buaa.hzcampus.api.local.localUpstreamUrl
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalWebVpnSupportTest {
  @BeforeTest
  fun setup() {
    ConnectionModeStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
  }

  @Test
  fun `iHome官网入口遵循连接模式且不传递应用会话`() {
    val direct = "https://i.buaa.edu.cn/web/"
    assertEquals(direct, ihomeWebsiteUrl(ConnectionMode.DIRECT))
    assertEquals(direct, ihomeWebsiteUrl(null))
    val vpn = ihomeWebsiteUrl(ConnectionMode.WEBVPN)
    assertEquals(
        "https://d.buaa.edu.cn/https/77726476706e69737468656265737421f9b94389263126557a1dc7af96/web/",
        vpn,
    )
    assertEquals(direct, LocalWebVpnSupport.fromWebVpnUrl(vpn))
    assertEquals("", io.ktor.http.Url(vpn).encodedQuery)
    assertEquals("", io.ktor.http.Url(vpn).fragment)
  }

  @Test
  fun `WebVPN还原保留尾斜杠重复斜杠与登录片段`() {
    listOf(
            "https://i.buaa.edu.cn/",
            "http://i.buaa.edu.cn/web/#/login?type=0&token=fixture-token",
            "https://i.buaa.edu.cn/web/?q=fixture%2Bquery#/login?type=0&token=fixture-token",
            "https://i.buaa.edu.cn/a//b/?q=test%2Bvalue",
        )
        .forEach { original ->
          assertEquals(
              original,
              LocalWebVpnSupport.fromWebVpnUrl(LocalWebVpnSupport.toWebVpnUrl(original)),
          )
        }
  }

  @Test
  fun `转换后的片段令牌不进入HTTP查询`() {
    val wrapped =
        LocalWebVpnSupport.toWebVpnUrl(
            "http://i.buaa.edu.cn/web/#/login?type=0&token=fixture-token"
        )
    assertEquals(
        "https://d.buaa.edu.cn/http/77726476706e69737468656265737421f9b94389263126557a1dc7af96/web/#/login?type=0&token=fixture-token",
        wrapped,
    )
  }

  @Test
  fun `webvpn codec round-trips https upstream url`() {
    val original = "https://spoc.buaa.edu.cn/spocnewht/cas?token=test-token"

    val wrapped = LocalWebVpnSupport.toWebVpnUrl(original)

    assertTrue(wrapped.startsWith("https://d.buaa.edu.cn/https/"))
    assertEquals(original, LocalWebVpnSupport.fromWebVpnUrl(wrapped))
  }

  @Test
  fun `webvpn codec round-trips custom port url`() {
    val original = "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action?id=1"

    val wrapped = LocalWebVpnSupport.toWebVpnUrl(original)

    assertTrue(wrapped.startsWith("https://d.buaa.edu.cn/http-8081/"))
    assertEquals(original, LocalWebVpnSupport.fromWebVpnUrl(wrapped))
  }

  @Test
  fun `upstream url helper wraps direct upstream when current mode is webvpn`() {
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()

    val resolved = localUpstreamUrl("https://uc.buaa.edu.cn/api/uc/status")

    assertTrue(resolved.startsWith("https://d.buaa.edu.cn/https/"))
    assertEquals("https://uc.buaa.edu.cn/api/uc/status", LocalWebVpnSupport.fromWebVpnUrl(resolved))
  }
}
