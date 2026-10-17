// Buildscript de Minecraft 1.21.1 (ofuscado): Loom con remapeo, mappings oficiales de Mojang y Java 21.
// Lo demás debe coincidir con build.gradle.kts: si cambias algo común allí, cámbialo también aquí.
plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val modId = sc.properties.get<String>("mod.id")
val modName = sc.properties.get<String>("mod.name")
val modVersion = sc.properties.get<String>("mod.version")
val mcVersion = sc.current.version
val javaVersion = sc.properties.get<String>("deps.java").toInt()

// Nombre del jar: <modid>-<versión del mod>+mc<versión>.jar
version = "$modVersion+mc$mcVersion"
group = sc.properties.get<String>("mod.group")
base.archivesName = modId

repositories {
    // Cada grupo se busca solo en su repositorio
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDirectory = rootProject.file("run")
        generateRunConfig = true
    }
}

// Código y mixins solo de 26.x (paquetes "modern", *.modern.mixins.json): fuera de la build de 1.21.1
sourceSets.configureEach {
    java.exclude("**/modern/**")
    resources.exclude("*.modern.mixins.json")
}

fabricApi {
    configureTests {
        createSourceSet = true          // src/gametest/java + src/gametest/resources
        modId = "$modId-test"
        enableGameTests = true          // runGameTest (servidor dedicado)
        enableClientGameTests = false   // la API de tests de cliente no existe en 1.21.1
        eula = true                     // imprescindible: sin esto el servidor de la CI no arranca
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Integraciones opcionales: compileOnly aquí + FabricLoader.isModLoaded(...) en ejecución
    // compileOnly("maven.modrinth:<slug>:${sc.properties.get<String>("compat.<slug>")}")

    // Modpack de compatibilidad (va en la CI): ./gradlew :26.3:runGameTest -PcompatPack
    if (project.hasProperty("compatPack")) {
        modLocalRuntime("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
        modLocalRuntime("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = javaVersion
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    test { useJUnitPlatform() }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("deps.fabric_loader"),
            "java" to javaVersion,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
            // Las configuraciones de mixins de 26.x no existen en este jar
            filter { it.replace(Regex(""""[^"]*\.modern\.mixins\.json",\s*"""), "") }
        }
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
        manifest.attributes(
            "Implementation-Title" to modName,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "TakumiStudios",
        )
    }

    // Sin fabric.mod.json el jar de código fuente no es un mod: si alguien lo pone en mods/ por error, Fabric Loader lo
    // ignora en vez de fallar al arrancar con "Invalid mod id ${id}" (las propiedades solo se sustituyen en el jar normal)
    named<Jar>("sourcesJar") {
        exclude("fabric.mod.json")
    }
}
