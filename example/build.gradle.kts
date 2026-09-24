plugins {
    kotlin("jvm") version "2.4.20"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    // The interface. The plugin itself is on the server; all this needs is its classes to
    // compile against. The version is the release tag, "v" included — that is how JitPack
    // names it.
    //
    // Building next to the VoidRP UI sources instead, point at a fresh jar:
    //   ./gradlew build -PvoidrpUi=../build/libs/voidrp-ui-0.3.9.jar
    val local = findProperty("voidrpUi") as String?
    if (local != null) {
        compileOnly(files(local))
    } else {
        compileOnly("com.github.VOIDRP-MINECRAFT:voidrp-ui:v0.3.9")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    val tokens = mapOf("version" to project.version.toString())
    inputs.properties(tokens)
    filesMatching("plugin.yml") { expand(tokens) }
}
