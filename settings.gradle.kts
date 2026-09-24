// Lets Gradle fetch the JDK it needs by itself — otherwise building requires Java 25
// (which the Paper 26.2 API asks for) to be installed already.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "voidrp-ui"
