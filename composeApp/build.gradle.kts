import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  // Changed from androidApplication to androidKotlinMultiplatformLibrary
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  // alias(libs.plugins.composeHotReload)
}

val composeVersion = libs.versions.composeMultiplatform.get()
val composeMaterial3Version = "1.9.0"
val composeMaterialIconsExtendedVersion = "1.7.3"

kotlin {
  jvmToolchain(21)

  android {
    namespace = "cn.edu.buaa.hzcampus.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    androidResources.enable = true
  }

  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
      binaryOption("bundleShortVersionString", project.property("project.version").toString())
      binaryOption("bundleVersion", project.property("project.version.code").toString())
    }
  }

  jvm()

  js {
    browser()
    binaries.executable()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    androidMain.dependencies {
      implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
      implementation(libs.androidx.activity.compose)
      implementation("org.eclipse.angus:angus-mail:2.0.3")
      // 课表导出 ICS：biweekly 生成 iCalendar（仅 JVM/Android 可用，故只放 androidMain）
      implementation("net.sf.biweekly:biweekly:0.6.8")
      // 成绩页图表：Vico 2.4.4 的 Compose Multiplatform 版本，与本项目 Kotlin 2.3.20 / Material3 1.9.0 兼容。
      // 它只发布 android / desktop / ios 产物，因此只在 Android 上启用，其余平台（含 Web）用自绘 Canvas 兜底。
      implementation("com.patrykandpatrick.vico:multiplatform-m3:2.4.4")
    }
    commonMain.dependencies {
      implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
      implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
      implementation("org.jetbrains.compose.material:material:$composeVersion")
      implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
      implementation("org.jetbrains.compose.ui:ui:$composeVersion")
      implementation(
          "org.jetbrains.compose.material:material-icons-extended:$composeMaterialIconsExtendedVersion"
      )
      implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
      implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
      implementation(kotlin("reflect"))
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.kotlinx.coroutinesCore)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kamel.image)
      implementation(libs.coil3.compose)
      implementation(libs.coil3.network.ktor)
      implementation(libs.ktor.serialization.kotlinx.json)
      implementation(projects.shared)
      implementation("org.jetbrains.kotlin:kotlin-metadata-jvm")
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutinesSwing)
    }

    jvmTest.dependencies {
      implementation("org.jetbrains.compose.ui:ui-test-junit4:$composeVersion")
    }
  }
}

compose.desktop {
  application {
    mainClass = "cn.edu.buaa.hzcampus.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
      packageName = "HzCampus"
      packageVersion = project.property("project.version").toString()
      macOS { iconFile = project.file("icons/app.icns") }
      linux { iconFile = project.file("icons/app.png") }

      buildTypes.release.proguard {
        isEnabled.set(false)
        version.set("7.8.2")
        configurationFiles.from("compose-desktop.pro")
      }

      windows {
        iconFile = project.file("icons/app.ico")
        menu = true
        shortcut = true
        perUserInstall = false
      }
    }
  }
}
