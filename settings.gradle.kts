rootProject.name = "droidtop-shared"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":version")
include(":net")
include(":trust")
include(":testpki")
