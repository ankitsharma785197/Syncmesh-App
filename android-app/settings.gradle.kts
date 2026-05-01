pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
    versionCatalogs {
        create("tools") {
            from(files("gradle/tools.versions.toml"))
        }
    }
}

rootProject.name = "Syncmesh"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":keyboard_floris")
project(":keyboard_floris").projectDir = file("keyboard_floris/app")
include(":lib:android")
project(":lib:android").projectDir = file("keyboard_floris/lib/android")
include(":lib:color")
project(":lib:color").projectDir = file("keyboard_floris/lib/color")
include(":lib:compose")
project(":lib:compose").projectDir = file("keyboard_floris/lib/compose")
include(":lib:kotlin")
project(":lib:kotlin").projectDir = file("keyboard_floris/lib/kotlin")
include(":lib:native")
project(":lib:native").projectDir = file("keyboard_floris/lib/native")
include(":lib:snygg")
project(":lib:snygg").projectDir = file("keyboard_floris/lib/snygg")
