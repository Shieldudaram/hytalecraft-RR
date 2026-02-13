plugins {
    `maven-publish`
    id("hytale-mod") version "0.+"
}

import java.io.File
import org.gradle.api.tasks.Copy
import org.gradle.internal.os.OperatingSystem

group = "com.hytalecraft"
version = "0.2.0"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-mods.dev/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    // this mod is optional, but is included so you can preview your mod icon
    // in the in-game mod list via the /modlist command
    runtimeOnly(libs.bettermodlist)
}

hytale {
    // uncomment if you want to add the Assets.zip file to your external libraries;
    // !!! CAUTION, this file is very big and might make your IDE unresponsive for some time !!!
    //
    // addAssetsDependency = true

    // uncomment if you want to develop your mod against the pre-release version of the game.
    //
    // updateChannel = "pre-release"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

publishing {
    repositories {
        // This is where you put repositories that you want to publish to.
        // Do NOT put repositories for your dependencies here.
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
val runningOnCI = providers.environmentVariable("CI").orNull.toBoolean()
idea {
    module {
        isDownloadSources = !runningOnCI
        isDownloadJavadoc = !runningOnCI
    }
}

fun resolveHytaleHomePath(): String? {
    val explicitHome = (findProperty("hytale_home") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: (findProperty("hytaleHome") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    if (explicitHome != null) return explicitHome

    val userHome = System.getProperty("user.home")
    val os = OperatingSystem.current()
    return when {
        os.isWindows -> "$userHome/AppData/Roaming/Hytale"
        os.isMacOsX -> "$userHome/Library/Application Support/Hytale"
        os.isLinux -> {
            val flatpakHome = File("$userHome/.var/app/com.hypixel.HytaleLauncher/data/Hytale")
            if (flatpakHome.exists()) {
                flatpakHome.absolutePath
            } else {
                "$userHome/.local/share/Hytale"
            }
        }

        else -> null
    }
}

val hytaleHomePath = resolveHytaleHomePath()
val hytaleHomeDir = hytaleHomePath?.let(::File)
val hytaleModsDir = hytaleHomePath?.let { File(it, "UserData/Mods") }
val installSkipMessage = when {
    hytaleHomeDir == null -> "could not resolve Hytale home. Set -Phytale_home=... or -PhytaleHome=..."
    !hytaleHomeDir.exists() -> "Hytale home does not exist at ${hytaleHomeDir.absolutePath}"
    else -> null
}

tasks.register<Copy>("installToHytaleMods") {
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })

    if (installSkipMessage != null) {
        enabled = false
        into(layout.buildDirectory.dir("tmp/installToHytaleMods-noop"))
        logger.lifecycle("[HytaleCraft] installToHytaleMods skipped: $installSkipMessage")
    } else {
        val modsDir = hytaleModsDir!!
        val ready = modsDir.exists() || modsDir.mkdirs()
        if (ready) {
            into(modsDir)
            logger.lifecycle("[HytaleCraft] installToHytaleMods -> ${modsDir.absolutePath}")
        } else {
            enabled = false
            into(layout.buildDirectory.dir("tmp/installToHytaleMods-noop"))
            logger.lifecycle("[HytaleCraft] installToHytaleMods skipped: could not create mods directory at ${modsDir.absolutePath}")
        }
    }
}

tasks.named("build") {
    finalizedBy("installToHytaleMods")
}
