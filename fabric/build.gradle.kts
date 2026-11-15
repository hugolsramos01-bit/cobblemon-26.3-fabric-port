import net.fabricmc.loom.build.nesting.NestableJarGenerationTask
import net.fabricmc.loom.task.NestJarsAction
import net.fabricmc.loom.util.ModPlatform

/*
 *
 *  * Copyright (C) 2023 Cobblemon Contributors
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

configurations.all {
    resolutionStrategy {
        force(libs.fabric.loader)
    }
}

plugins {
    id("cobblemon.platform-conventions")
    id("cobblemon.publish-conventions")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "cobblemon-gametest"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

// Client GameTest launch config injects its own identity arguments. The shared
// client run config supplies AshKetchum's identity for the normal dev launch,
// so strip those inherited values to avoid duplicate --username/--uuid options.
loom {
    runConfigs.getByName("clientGameTest").apply {
        providers.gradleProperty("cobblemonGameTestFocus").orNull?.let {
            vmArg("-Dcobblemon.gametest.focus=$it")
        }
        programArguments.set(programArguments.get().filterNot {
            it.startsWith("--username") || it.startsWith("--uuid")
        })
    }
}

val generatedResources = file("src/generated/resources")

// Loom's no-remap plugin nests `include` dependencies in `jar`, while our
// combined Common/Fabric artifact comes from shadowJar. Build a separate
// installable artifact, retaining the complete Graal/Mongo dependency graphs.
val distributionLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val packRuntimeMods by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

sourceSets.main {
    resources {
        srcDir(generatedResources)
    }
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.terraformersmc.com/releases/")
}

dependencies {
    distributionLibraries(libs.fabric.kotlin) { isTransitive = false }
    distributionLibraries(libs.fabric.permissions) { isTransitive = false }
    distributionLibraries(libs.bundles.graal)
    distributionLibraries(libs.bundles.mongo)
    packRuntimeMods(libs.fabric.api)
    packRuntimeMods(libs.jei.fabric)
    packRuntimeMods(libs.mod.menu)
    // Flight control GameTests evaluate the same MoLang settings as the live ride.
    add("gametestCompileOnly", libs.molang)
    implementation(project(":common")) {
        isTransitive = false
    }
    bundle(project(":common")) {
        isTransitive = false
    }
    implementation(libs.fabric.loader)
    api(libs.fabric.api)
    api(libs.bundles.fabric)

    compileOnly(libs.bundles.common.integrations.compileOnly) {
        isTransitive = false
    }

    // Keep the optional integration APIs available to compile their entrypoints.
    compileOnly(libs.bundles.fabric.integrations.implementation)
    // Both integrations have Minecraft 26.3 builds and are useful in the normal dev runtime.
    runtimeOnly(libs.bundles.fabric.integrations.implementation)
    runtimeOnly(libs.jei.fabric)

    // These development helpers still target Minecraft 1.21.1 and stay opt-in.
    if (providers.gradleProperty("enableLegacy1211DevMods").orNull == "true") {
        runtimeOnly(libs.fabric.debugutils)
        runtimeOnly(libs.c2me.fabric)
    }
    // LambDynamicLights is a client-side optional integration; opt in only for compatibility testing.
    if (providers.gradleProperty("enableLambDynamicLightsDevMod").orNull == "true") {
        runtimeOnly(libs.lambDynamicLightsMod)
    }
    // Repurposed Structures is an optional Cobbleverse content pilot. Keep it
    // out of Cobblemon's bundled/release configurations; opt in only to local
    // dev and GameTest launches with -PenableRepurposedStructuresDevMod=true.
    if (providers.gradleProperty("enableRepurposedStructuresDevMod").orNull == "true") {
        val repurposedStructures = "maven.modrinth:muf0XoRe:9jHXeIeI" // 7.8.4+26.3-fabric
        val midnightLib = "maven.modrinth:codAaoxh:wJeXgIoa" // 1.9.3+26.3-fabric
        add("runtimeOnly", repurposedStructures)
        add("runtimeOnly", midnightLib)
        add("gametestRuntimeOnly", repurposedStructures)
        add("gametestRuntimeOnly", midnightLib)
    }
    runtimeOnly(libs.bundles.mongo)

//    modImplementation(libs.flywheelFabric)
//    include(libs.flywheelFabric)

    listOf(
        libs.fabric.kotlin,
        libs.bundles.graal,
        libs.bundles.mongo
    ).forEach {
        include(it)
    }

    // Added to make graal available in dev
    runtimeOnly(libs.bundles.graal)

    include(libs.fabric.kotlin)

    listOf(
        libs.molang
    ).forEach {
        bundle(it)
        runtimeOnly(it)
    }

}

val prepareDistributionLibraries by tasks.registering(NestableJarGenerationTask::class) {
    from(distributionLibraries)
    outputDirectory.set(layout.buildDirectory.dir("distributionLibraries"))
    uncompressNestedJars.set(false)
}

val releaseJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the installable Fabric JAR with all nested runtime libraries."
    dependsOn(tasks.shadowJar, prepareDistributionLibraries)
    archiveBaseName.set("Cobblemon-fabric")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(tasks.shadowJar.flatMap { it.archiveFile }.map { zipTree(it) })
    val nestedJars = files(prepareDistributionLibraries.flatMap { it.outputDirectory })
        .asFileTree.matching { include("*.jar") }
    NestJarsAction.addToTask(this, nestedJars, ModPlatform.FABRIC)
}

tasks.register<Sync>("prepareMultiplayerDistribution") {
    group = "distribution"
    description = "Stages the installable mod and optional client mods for modpacks."
    dependsOn(releaseJar)
    into(layout.buildDirectory.dir("multiplayerDistribution"))
    from(releaseJar.flatMap { it.archiveFile }) { into("shared") }
    from(packRuntimeMods) {
        into("runtime-mods")
    }
}

tasks.assemble { dependsOn(releaseJar) }

tasks.named<CopyFile>("copyJar") {
    val productionJar = releaseJar.flatMap { it.archiveFile }
    fileToCopy.set(productionJar)
    destination.set(productionJar.flatMap {
        rootProject.layout.buildDirectory.file("libs/${it.asFile.name}")
    })
}

publishing {
    publications.named<MavenPublication>(project.name) {
        pom.withXml {
            val root = asNode()
            val depsNode = (root["dependencies"] as? groovy.util.NodeList)
                ?.firstOrNull() as? groovy.util.Node
                ?: root.appendNode("dependencies")

            configurations.getByName("include").allDependencies
                .filterIsInstance<ExternalModuleDependency>()
                .forEach { dep ->
                    depsNode.appendNode("dependency").apply {
                        appendNode("groupId", dep.group)
                        appendNode("artifactId", dep.name)
                        appendNode("version", dep.version)
                        appendNode("scope", "compile")
                    }
                }
        }
    }
}

tasks {
    // The AW file is needed in :fabric project resources when the game is run.
    val copyAccessWidener = register<Copy>("copyAccessWidener") {
        from(loom.accessWidenerPath)
        into(generatedResources)
    }

    processResources {
        dependsOn(copyAccessWidener)
        inputs.property("version", rootProject.version)
        inputs.property("fabric_loader_version", libs.fabric.loader.get().version)
        inputs.property("fabric_api_version", libs.fabric.api.get().version)
        inputs.property("minecraft_version", rootProject.property("mc_version").toString())
        inputs.property("java_version", rootProject.property("java_version").toString())
        inputs.property("fabric_kotlin_version", libs.fabric.kotlin.get().version)

        filesMatching("fabric.mod.json") {
            expand(
                "version" to rootProject.version,
                "fabric_loader_version" to libs.fabric.loader.get().version!!,
                "fabric_api_version" to libs.fabric.api.get().version!!,
                "minecraft_version" to rootProject.property("mc_version").toString(),
                "java_version" to rootProject.property("java_version").toString(),
                "fabric_kotlin_version" to libs.fabric.kotlin.get().version!!
            )
        }
    }

    sourcesJar {
        dependsOn(copyAccessWidener)
    }
}
