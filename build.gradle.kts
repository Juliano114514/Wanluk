// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

subprojects {
  pluginManager.withPlugin("org.jetbrains.kotlin.android") {
    extensions.configure<KotlinAndroidProjectExtension> {
      compilerOptions {
        jvmTarget.set(
          JvmTarget.fromTarget(
            (findProperty("wanluk.jvmTarget") ?: error("wanluk.jvmTarget missing")).toString()
          )
        )
      }
    }
  }
}
