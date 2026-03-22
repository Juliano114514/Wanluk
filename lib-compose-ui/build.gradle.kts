plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("foundation/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.lib_compose_ui"

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
