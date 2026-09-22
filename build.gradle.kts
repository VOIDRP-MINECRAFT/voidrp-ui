plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "8.3.10"
    `maven-publish`
}

group = "ru.voidrp"
version = "0.1.0"

kotlin {
    // Paper 26.2's own API is Java 25, so it takes a 25 compiler to read it...
    jvmToolchain(25)
    compilerOptions {
        // ...but the plugin itself is emitted for 21, because a 1.21.6 server runs on 21
        // and a jar it cannot load is not support, whatever the README says.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    withSourcesJar()
}

// Java sources (there are none today, but a contributor may add some) follow Kotlin.
tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "21"
    sourceCompatibility = "21"
}

// Paper's own API is built for 25, and Gradle would otherwise refuse to put a 25 library
// on the classpath of something emitted for 21. Reading it is fine; what matters is that
// the classes we produce load on an older server.
listOf(configurations.compileClasspath, configurations.testCompileClasspath, configurations.testRuntimeClasspath)
    .forEach { configuration ->
        configuration {
            attributes {
                attribute(
                    org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                    25,
                )
            }
        }
    }

// Published so other plugins can compile against the API — see README.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "voidrp-ui"
        }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // PacketEvents, which the plugin uses if the server happens to have it — see
    // ru.voidrp.ui.input.PacketAim for what it buys.
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.124-stable")
    // Paper ships Gson at runtime; we only need it to read our own width table.
    compileOnly("com.google.code.gson:gson:2.11.0")
    // Optional: read the player's look the moment it arrives instead of on the next tick.
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    implementation(kotlin("stdlib"))

    // The tests read the pack we build and add up a line the way the client would.
    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.124-stable")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

/**
 * Renders the demo pages to PNGs, so the design can be looked at without logging in:
 * `./gradlew preview`.
 */
tasks.register<JavaExec>("preview") {
    group = "voidrp"
    description = "Рисует демо-страницы в build/preview"
    mainClass.set("ru.voidrp.ui.Preview")
    classpath = sourceSets["test"].runtimeClasspath
    environment("VOIDRP_CLIENT_JAR", System.getenv("VOIDRP_CLIENT_JAR") ?: "")
}

tasks.register<JavaExec>("bench") {
    group = "voidrp"
    description = "Меряет, во что обходится отрисовка страницы"
    mainClass.set("ru.voidrp.ui.PageBench")
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.register<JavaExec>("packWeight") {
    group = "voidrp"
    description = "Собирает пак и печатает его вес"
    mainClass.set("ru.voidrp.ui.PackWeight")
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
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
