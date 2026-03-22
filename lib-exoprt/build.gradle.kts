plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

apply(from = rootProject.file("foundation/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.lib_exoprt"
}

dependencies {
  implementation(project(":foundation"))
  implementation(libs.gson)
}
