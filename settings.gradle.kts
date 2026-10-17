pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.19.1"
        // 1.21.1 está ofuscado: mismo artefacto de Loom, en modo remapeo (build.obf.gradle.kts)
        id("net.fabricmc.fabric-loom-remap") version "1.19.1"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Una build por versión de Minecraft: 26.x y 1.21.1 como soporte extendido (con su propio buildscript)
        version("1.21.1").buildscript("build.obf.gradle.kts")
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "hammer_repair"
