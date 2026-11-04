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
        google() // LiteRT-LM is published on Google Maven
        mavenCentral()
    }
}

rootProject.name = "ScamRadar"
include(":core", ":android")
