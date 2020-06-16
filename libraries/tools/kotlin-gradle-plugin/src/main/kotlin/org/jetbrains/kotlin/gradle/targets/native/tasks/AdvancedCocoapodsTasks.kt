/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.cocoapodsBuildDirs
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

internal val KotlinNativeTarget.toBuildSettingsFileName: String
    get() = "build-settings-$disambiguationClassifier.properties"

internal val KotlinNativeTarget.toValidSDK: String
    get() = when (konanTarget) {
        KonanTarget.IOS_X64 -> "iphonesimulator"
        KonanTarget.IOS_ARM32, KonanTarget.IOS_ARM64 -> "iphoneos"
        KonanTarget.WATCHOS_X86, KonanTarget.WATCHOS_X64 -> "watchsimulator"
        KonanTarget.WATCHOS_ARM32, KonanTarget.WATCHOS_ARM64 -> "watchos"
        KonanTarget.TVOS_X64 -> "appletvsimulator"
        KonanTarget.TVOS_ARM64 -> "appletvos"
        KonanTarget.MACOS_X64 -> "macos"
        else -> throw IllegalArgumentException("Bad target ${konanTarget.name}.")
    }

internal val KotlinNativeTarget.platformLiteral: String
    get() = when (konanTarget.family) {
        Family.OSX -> "macOS"
        Family.IOS -> "iOS"
        Family.TVOS -> "tvOS"
        Family.WATCHOS -> "watchOS"
        else -> throw IllegalArgumentException("Unsupported native target '${konanTarget.name}'")
    }

/**
 * The task takes the path to the Podfile and calls `pod install`
 * to obtain sources or artifacts for the declared dependencies.
 * This task is a part of CocoaPods integration infrastructure.
 */
open class PodInstallTask : DefaultTask() {
    init {
        onlyIf { cocoapodsExtension.podfile != null }
    }

    @get:Nested
    lateinit var cocoapodsExtension: CocoapodsExtension

    @get:Optional
    @get:InputFile
    internal var podfileProvider: Provider<File>? = null

    @get:Optional
    @get:OutputDirectory
    internal val podsXcodeProjDirProvider: Provider<File>?
        get() = podfileProvider?.get()?.let {
            project.provider { it.parentFile.resolve("Pods").resolve("Pods.xcodeproj") }
        }


    @TaskAction
    fun doPodInstall() {
        podfileProvider?.get()?.parentFile?.also { podfileDir ->
            val podInstallProcess = ProcessBuilder("pod", "install").apply {
                directory(podfileDir)
                inheritIO()
            }.start()
            val podInstallRetCode = podInstallProcess.waitFor()
            check(podInstallRetCode == 0) { "Unable to run 'pod install', return code $podInstallRetCode" }
            with(podsXcodeProjDirProvider) {
                check(this != null && get().exists() && get().isDirectory) {
                    "The directory 'Pods/Pods.xcodeproj' was not created as a result of the `pod install` call."
                }
            }
        }
    }
}

abstract class CocoapodsWithSyntheticTask : DefaultTask() {
    init {
        onlyIf {
            cocoapodsExtension.pods.isNotEmpty()
        }
    }

    @get:Nested
    internal lateinit var cocoapodsExtension: CocoapodsExtension
}

/**
 * The task takes the path to the .podspec file and calls `pod gen`
 * to create synthetic xcode project and workspace.
 */
open class PodGenTask : CocoapodsWithSyntheticTask() {

    @get:InputFile
    internal lateinit var podspecProvider: Provider<File>

    @Internal
    lateinit var kotlinNativeTarget: KotlinNativeTarget

    @get:OutputDirectory
    internal val podsXcodeProjDirProvider: Provider<File>
        get() = project.provider {
            project.cocoapodsBuildDirs.synthetic(kotlinNativeTarget)
                .resolve(podspecProvider.get().nameWithoutExtension)
                .resolve("Pods")
                .resolve("Pods.xcodeproj")
        }

    @TaskAction
    fun generate() {
        val podspecDir = podspecProvider.get().parentFile
        val localPodspecPaths = cocoapodsExtension.pods.mapNotNull { it.podspec?.parentFile?.absolutePath }

        val podGenProcessArgs = listOfNotNull(
            "pod", "gen",
            "--platforms=${kotlinNativeTarget.konanTarget.family.name.toLowerCase()}",
            "--gen-directory=${project.cocoapodsBuildDirs.synthetic(kotlinNativeTarget).absolutePath}",
            localPodspecPaths.takeIf { it.isNotEmpty() }?.joinToString(separator = ",")?.let { "--local-sources=$it" },
            podspecProvider.get().name
        )

        val podGenProcess = ProcessBuilder(podGenProcessArgs).apply {
            directory(podspecDir)
            inheritIO()
        }.start()
        val podGenRetCode = podGenProcess.waitFor()
        check(podGenRetCode == 0) { "Unable to run 'pod gen', return code $podGenRetCode" }

        val podsXcprojFile = podsXcodeProjDirProvider.get()
        check(podsXcprojFile.exists() && podsXcprojFile.isDirectory) {
            "The directory '${podsXcprojFile.path}' was not created as a result of the `pod gen` call."
        }
    }
}


open class PodSetupBuildTask : CocoapodsWithSyntheticTask() {

    @get:InputDirectory
    internal lateinit var podsXcodeProjDirProvider: Provider<File>

    @Internal
    lateinit var kotlinNativeTarget: KotlinNativeTarget

    @get:OutputFile
    internal val buildSettingsFileProvider: Provider<File> = project.provider {
        project.cocoapodsBuildDirs
            .buildSettings
            .resolve(kotlinNativeTarget.toBuildSettingsFileName)
    }

    @TaskAction
    fun setupBuild() {
        val podsXcodeProjDir = podsXcodeProjDirProvider.get()

        val buildSettingsReceivingCommand = listOf(
            "xcodebuild", "-showBuildSettings",
            "-project", podsXcodeProjDir.name,
            "-scheme", cocoapodsExtension.frameworkName,
            "-sdk", kotlinNativeTarget.toValidSDK
        )

        val buildSettingsProcess = ProcessBuilder(buildSettingsReceivingCommand)
            .apply {
                directory(podsXcodeProjDir.parentFile)
            }.start()

        val buildSettingsRetCode = buildSettingsProcess.waitFor()
        check(buildSettingsRetCode == 0) {
            """
                Unable to run '${buildSettingsReceivingCommand.joinToString(" ")}' return code $buildSettingsRetCode.
                Error message:
                ${buildSettingsProcess.errorStream.reader().readText()}
            """.trimIndent()
        }

        val stdOut = buildSettingsProcess.inputStream

        val buildSettingsProperties = PodBuildSettingsProperties.readSettingsFromStream(stdOut)
        buildSettingsFileProvider.get().let { buildSettingsProperties.writeSettings(it) }
    }
}

/**
 * The task compiles external cocoa pods sources.
 */
open class PodBuildTask : CocoapodsWithSyntheticTask() {

    @get:InputDirectory
    internal lateinit var podsXcodeProjDirProvider: Provider<File>

    @get:InputFile
    internal lateinit var buildSettingsFileProvider: Provider<File>

    @Internal
    lateinit var kotlinNativeTarget: KotlinNativeTarget

    @get:Optional
    @get:OutputDirectory
    internal var buildDirProvider: Provider<File>? = null

    @TaskAction
    fun buildDependencies() {
        val podBuildSettings = PodBuildSettingsProperties.readSettingsFromStream(
            FileInputStream(buildSettingsFileProvider.get())
        )

        val podsXcodeProjDir = podsXcodeProjDirProvider.get()

        cocoapodsExtension.pods.all {

            val podXcodeBuildCommand = listOf(
                "xcodebuild",
                "-project", podsXcodeProjDir.name,
                "-scheme", it.moduleName,
                "-sdk", kotlinNativeTarget.toValidSDK,
                "-configuration", podBuildSettings.configuration
            )

            val podBuildProcess = ProcessBuilder(podXcodeBuildCommand)
                .apply {
                    directory(podsXcodeProjDir.parentFile)
                    inheritIO()
                }.start()

            val podBuildRetCode = podBuildProcess.waitFor()
            check(podBuildRetCode == 0) {
                "Unable to run '${podXcodeBuildCommand.joinToString(" ")}' return code $podBuildRetCode"
            }
        }
        buildDirProvider = project.provider { project.file(podBuildSettings.buildDir) }
    }
}

internal data class PodBuildSettingsProperties(
    internal val buildDir: String,
    internal val configuration: String,
    internal val cflags: String? = null,
    internal val headerPaths: String? = null,
    internal val frameworkPaths: String? = null
) {

    fun writeSettings(buildSettingsFile: File) {
        buildSettingsFile.parentFile.mkdirs()
        buildSettingsFile.createNewFile()

        check(buildSettingsFile.exists()) { "Unable to create file ${buildSettingsFile.path}!" }

        with(Properties()) {
            setProperty(BUILD_DIR, buildDir)
            setProperty(CONFIGURATION, configuration)
            cflags?.let { setProperty(OTHER_CFLAGS, it) }
            headerPaths?.let { setProperty(HEADER_SEARCH_PATHS, it) }
            frameworkPaths?.let { setProperty(FRAMEWORK_SEARCH_PATHS, it) }
            buildSettingsFile.outputStream().use {
                store(it, null)
            }
        }
    }

    companion object {
        const val BUILD_DIR: String = "BUILD_DIR"
        const val CONFIGURATION: String = "CONFIGURATION"
        const val OTHER_CFLAGS: String = "OTHER_CFLAGS"
        const val HEADER_SEARCH_PATHS: String = "HEADER_SEARCH_PATHS"
        const val FRAMEWORK_SEARCH_PATHS: String = "FRAMEWORK_SEARCH_PATHS"

        fun readSettingsFromStream(inputStream: InputStream): PodBuildSettingsProperties {
            with(Properties()) {
                load(inputStream)
                return PodBuildSettingsProperties(
                    getProperty(BUILD_DIR),
                    getProperty(CONFIGURATION),
                    getProperty(OTHER_CFLAGS),
                    getProperty(HEADER_SEARCH_PATHS),
                    getProperty(FRAMEWORK_SEARCH_PATHS)
                )
            }
        }
    }
}
