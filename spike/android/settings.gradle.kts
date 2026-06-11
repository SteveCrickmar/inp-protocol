// DISPOSABLE Phase-0 spike (OC-5). Throwaway Android probe for INP S0.5.
// Authored on Linux WITHOUT Android SDK validation — a compile pass is pending.
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

rootProject.name = "inp-spike-android"
include(":app")
