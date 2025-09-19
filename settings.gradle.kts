pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "MiniScoreBoard"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":nms:v1_21_8")
findProject(":nms:v1_18_2")?.name = "v1_21_8"
include("nms:Base")
findProject(":nms:Base")?.name = "Base"
