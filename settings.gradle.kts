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
include(":launch-contract")
include(":net")
include(":report")
include(":trust")
include(":testpki")
