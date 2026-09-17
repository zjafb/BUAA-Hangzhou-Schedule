rootProject.name = "BUAA-Hangzhou-Schedule"

// 启用类型安全的项目访问器（如 projects.shared）
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    maven("https://maven.aliyun.com/repository/gradle-plugin")
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/google")
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositories {
    // Vico（成绩页图表库）的 Kotlin Multiplatform 元数据在国内镜像上不完整（镜像有 .aar，
    // 缺少子模块的 .module），因此这个 group 跳过镜像，直接从下方 sonatype releases 解析。
    maven("https://maven.aliyun.com/repository/public") {
      content { excludeGroup("com.patrykandpatrick.vico") }
    }
    maven("https://maven.aliyun.com/repository/central") {
      content { excludeGroup("com.patrykandpatrick.vico") }
    }
    maven("https://maven.aliyun.com/repository/google")
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral { content { excludeGroup("com.patrykandpatrick.vico") } }
    // Kamel 及其依赖所在的镜像仓库（Vico 亦从此仓库解析）
    maven("https://s01.oss.sonatype.org/content/repositories/releases/")
    maven("https://maven.pkg.jetbrains.space/public/p/kamel/maven")
  }
}

// 包含所有子模块
include(":androidApp")

include(":composeApp")

include(":shared")
