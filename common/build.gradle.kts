
import utilities.isSnapshot
import utilities.version
import utilities.gitBranch
import utilities.gitCommit

/*
 *
 *  * Copyright (C) 2023 Cobblemon Contributors
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

plugins {
    id("cobblemon.base-conventions")
    id("cobblemon.publish-conventions")

    id("net.kyori.blossom")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

architectury {
    common("fabric")
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.neoforged.net/releases")
    maven(url = "https://maven.gegy.dev")
    mavenLocal()
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.fabric.loader)
    api(libs.molang)

    // Integrations
    compileOnlyApi(libs.jei.api)
    compileOnlyApi(libs.lambDynamicLights)
    compileOnly(libs.bundles.common.integrations.compileOnly) {
        isTransitive = false
    }
    // Flywheel has no common dep so just pick one and don't use any platform specific code in common
    // modCompileOnly(libs.flywheelFabric)

    // Showdown
    compileOnly(libs.graal.core)
    compileOnly(libs.graal.sdk)

    // Data Storage
    compileOnly(libs.bundles.mongo)

    // Unit Testing
    testImplementation(libs.bundles.unitTesting)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        setEvents(listOf("failed"))
        setExceptionFormat("full")
    }
}

// Git info lives in its own output dir (not processResources) so a new commit only rewrites this one file.
val writeGitInfo by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/gitInfo/cobblemon-git.properties")
    property("commit", gitCommit())
    property("branch", gitBranch())
}
sourceSets.main { output.dir(mapOf("builtBy" to writeGitInfo), layout.buildDirectory.dir("generated/gitInfo")) }

tasks.processResources {
    exclude("data/**/README.md")
}

sourceSets {
    main {
        blossom {
            kotlinSources {
                fun generateLicenseHeader() : String {
                    val builder = StringBuilder()
                    builder.append("/*\n")
                    rootProject.file("HEADER").forEachLine {
                        if(it.isEmpty()) {
                            builder.append(" *").append("\n")
                        } else {
                            builder.append(" * ").append(it).append("\n")
                        }
                    }

                    return builder.append(" */").append("\n").toString()
                }

                property("license", generateLicenseHeader())
                property("modid", "cobblemon")
                property("version", project.version())
                property("isSnapshot", if(rootProject.isSnapshot()) "true" else "false")
                System.getProperty("buildNumber")?.let { property("buildNumber", it) }
            }
        }
    }
}

// The platform jars are built from :common:jar, so the remapped common jars are only needed by `publish`
tasks.assemble {
    setDependsOn(dependsOn.filterNot { (it as? TaskProvider<*>)?.name in setOf("remapJar", "remapSourcesJar") })
}
