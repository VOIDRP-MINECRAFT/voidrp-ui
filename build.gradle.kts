plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "8.3.10"
}

group = "ru.voidrp"
version = "0.1.0"

kotlin {
    // Paper 26.2 ships Java 25 bytecode, so the plugin is built on 25 too.
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.124-stable")
    // Paper ships Gson at runtime; we only need it to read our own width table.
    compileOnly("com.google.code.gson:gson:2.11.0")
    implementation(kotlin("stdlib"))
}

tasks.shadowJar {
    archiveClassifier.set("all")
    relocate("kotlin", "ru.voidrp.ui.shaded.kotlin")
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
