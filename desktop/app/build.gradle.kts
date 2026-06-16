import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val appVersionName = "1.1.5"
val supportedServerRange = ">=1.1.5"

val lwjglVersion = libs.versions.lwjgl.get()
val lwjglNatives = run {
    val name = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm = arch.contains("aarch64") || arch.contains("arm")
    when {
        name.contains("win") -> "natives-windows"
        name.contains("mac") || name.contains("darwin") -> if (arm) "natives-macos-arm64" else "natives-macos"
        else -> if (arm) "natives-linux-arm64" else "natives-linux"
    }
}

val generateBuildConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildConfig/kotlin")
    val versionName = appVersionName
    val supported = supportedServerRange
    outputs.dir(outDir)
    doLast {
        val pkg = outDir.get().asFile.resolve("com/jahirtrap/cconnect")
        pkg.mkdirs()
        pkg.resolve("BuildConfig.kt").writeText(
            """
            package com.jahirtrap.cconnect

            object BuildConfig {
                const val VERSION_NAME = "$versionName"
                const val SUPPORTED_SERVER = "$supported"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(generateBuildConfig)

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.desktop.currentOs)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.okhttp)

                implementation(libs.commonmark)
                implementation(libs.commonmark.ext.tables)
                implementation(libs.commonmark.ext.strikethrough)
                implementation(libs.commonmark.ext.task.list)
                implementation(libs.commonmark.ext.footnotes)
                implementation(libs.commonmark.ext.autolink)
                implementation(libs.commonmark.ext.ins)

                implementation(libs.coil.compose)
                implementation(libs.coil.network.okhttp)
                implementation(libs.coil.svg)

                implementation(libs.lifecycle.viewmodel.compose)

                implementation(libs.icons.lucide)
                implementation(libs.icons.font.awesome.brands)

                implementation(libs.sshj)
                implementation(libs.bouncycastle)

                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.tinyfd)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-tinyfd:$lwjglVersion:$lwjglNatives")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.jahirtrap.cconnect.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.jahirtrap.cconnect.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            packageName = "CConnect"
            packageVersion = appVersionName
            description = "Bridge between the CConnect desktop client and Claude Code"
            vendor = "jahirtrap"
            windows {
                iconFile.set(project.file("icons/cconnect.ico"))
                menuGroup = "CConnect"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f2a6c41-3b7e-4d59-9a1c-2e5f7b0d4c83"
            }
            linux {
                iconFile.set(project.file("icons/cconnect.png"))
            }
            macOS {
                iconFile.set(project.file("icons/cconnect.icns"))
            }
        }
    }
}
