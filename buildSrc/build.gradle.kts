plugins { `kotlin-dsl` }

repositories {
  maven("https://maven.aliyun.com/repository/gradle-plugin")
  maven("https://maven.aliyun.com/repository/central")
  maven("https://maven.aliyun.com/repository/public")
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  testImplementation(kotlin("test-junit5"))
}

tasks.test { useJUnitPlatform() }
