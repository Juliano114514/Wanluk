plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.foundation"
}

dependencies {
  api(libs.androidx.core.ktx)
  api(libs.bundles.coroutines)
}
