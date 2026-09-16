import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "cn.edu.buaa.hzcampus.android"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "cn.edu.buaa.hzcampus"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = project.property("project.version.code").toString().toInt()
    versionName = project.property("project.version").toString()
  }

  val localProperties =
      Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
          localPropertiesFile.inputStream().use { load(it) }
        }
      }

  signingConfigs {
    create("release") {
      val keyPath = localProperties.getProperty("SIGNING_KEY") ?: System.getenv("SIGNING_KEY")
      storeFile = keyPath?.let { rootProject.file(it) }
      storePassword =
          localProperties.getProperty("SIGNING_STORE_PASSWORD")
              ?: System.getenv("SIGNING_STORE_PASSWORD")
      keyAlias =
          localProperties.getProperty("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
      keyPassword =
          localProperties.getProperty("SIGNING_KEY_PASSWORD")
              ?: System.getenv("SIGNING_KEY_PASSWORD")
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  packaging {
    resources {
      excludes +=
          setOf(
              "META-INF/NOTICE.md",
              "META-INF/LICENSE.md",
              "META-INF/LICENSE",
              "META-INF/NOTICE",
              "META-INF/DEPENDENCIES",
          )
    }
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

dependencies {
  implementation(projects.composeApp)
  implementation(libs.androidx.activity.compose)
  implementation(compose.preview)
  debugImplementation(compose.uiTooling)
}
