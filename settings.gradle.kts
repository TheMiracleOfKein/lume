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
include(":feature:comics")
include(":feature:novel")
include(":baselineprofile")
include(":core:translate")
// The translation add-on needs the bergamot-translator submodule (git submodule update --init
// --recursive); without it the rest of the project still builds.
if (file("translate-addon/third_party/bergamot-translator/CMakeLists.txt").exists()) {
    include(":translate-addon")
}
