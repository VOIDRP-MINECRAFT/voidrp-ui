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

    // Интерфейсы. Плагин стоит на сервере — нам нужны только его классы при компиляции.
    //
    // Собирая рядом с исходниками VoidRP UI, укажите свежий jar:
    //   ./gradlew build -PvoidrpUi=../build/libs/voidrp-ui-0.2.0.jar
    val local = findProperty("voidrpUi") as String?
    if (local != null) {
        compileOnly(files(local))
    } else {
        compileOnly("com.github.VOIDRP-MINECRAFT:voidrp-ui:0.2.0")
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
