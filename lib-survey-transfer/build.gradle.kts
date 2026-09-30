plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android { namespace = "com.wanluk.libsurveytransfer" }

dependencies {
  implementation(project(":foundation"))
  implementation(libs.gson)
  api(libs.androidx.activity)
  implementation(libs.zxing.core)
  implementation(libs.zxing.embedded)
}
