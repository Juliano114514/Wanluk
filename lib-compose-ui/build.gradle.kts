plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.libcomposeui"

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":foundation"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
  debugImplementation(libs.androidx.ui.tooling)
}
