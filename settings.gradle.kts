pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "Gradle releases"
            url = uri("https://repo.gradle.org/gradle/libs-releases")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "Gradle releases"
            url = uri("https://repo.gradle.org/gradle/libs-releases")
        }
    }
}

rootProject.name = "SanghaWorld"
include(":app")
