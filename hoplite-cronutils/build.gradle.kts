plugins {
   kotlin("jvm")
}

dependencies {
   implementation(project(":hoplite-fp"))
   api(project(":hoplite-core"))
   api(Libs.CronUtils.utils)
}

apply("../publish.gradle.kts")
