plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.libsettingsui"
  buildFeatures { compose = true }
}

dependencies {
  implementation(project(":lib-settings"))
  implementation(project(":lib-compose-ui"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
}
