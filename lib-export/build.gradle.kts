plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.libexport"
}

dependencies {
  implementation(project(":foundation"))
  implementation(libs.gson)
}
