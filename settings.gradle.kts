@file:Suppress("UnstableApiUsage") // dependencyResolutionManagement / RepositoriesMode still @Incubating

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Lume"
include(":app")
include(":core:model")
include(":core:common")
include(":core:data")
include(":core:ui")
include(":baselineprofile")
