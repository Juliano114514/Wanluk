plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
}

apply(from = rootProject.file("foundation/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.lib_room"
}

dependencies {
  implementation(project(":foundation"))
  implementation(libs.bundles.room)
  ksp(libs.room.compiler)
}
