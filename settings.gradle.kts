// Позволяет Gradle самому скачать JDK нужной версии — иначе сборка требует,
// чтобы на машине уже стояла Java 25 (её требует Paper API 26.2).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "voidrp-ui"
