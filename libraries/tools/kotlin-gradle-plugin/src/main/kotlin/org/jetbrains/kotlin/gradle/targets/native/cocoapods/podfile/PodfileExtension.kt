/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.cocoapods.podfile

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.tasks.*
import java.io.File

open class PodfileExtension(val project: Project) {
    @InputFile
    @Optional
    var xcodeproj: File? = null

    /**
     * Configure path to the Xcodeproj.
     */
    fun xcodeproj(path: String) {
        xcodeproj = project.file(path)
    }

    @get:Nested
    @Optional
    var target: Target? = null


    private val _kotlinPods = project.container(KotlinPod::class.java)

    // For some reason Gradle doesn't consume the @Nested annotation on NamedDomainObjectContainer.
    @get:Nested
    protected val kotlinPodsAsTaskInput: List<KotlinPod>
        get() = _kotlinPods.toList()

    /**
     * Returns a list of pod dependencies.
     */
    // Already taken into account as a task input in the [podsAsTaskInput] property.
    @get:Internal
    val kotlinPods: NamedDomainObjectSet<KotlinPod>
        get() = _kotlinPods

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    @JvmOverloads
    fun kotlinPod(name: String, path: File) {
        check(_kotlinPods.findByName(name) == null) { "Project already depends on a local Kotlin Pod with name $name" }
        _kotlinPods.add(KotlinPod(name, path))
    }

    data class KotlinPod(
        private val name: String,
        @get:Input val path: File
    ) : Named {
        @Input
        override fun getName(): String = name
    }

    data class Target(
        private val name: String,
        @get:Input val artifact: Artifact,
        @get:Input val platform: Platform
    ) : Named {
        @Input
        override fun getName(): String = name
    }

    data class Platform(
        private val name: String,
        @get:Optional @get:Input val version: String?
    ) : Named {
        @Input
        override fun getName(): String = name
        override fun toString(): String = name + version?.let { ", '$it'" }
    }

    interface Artifact

    val UseModularHeaders = object : Artifact {
        override fun toString(): String = "use_modular_headers!"
    }

    val UseFramework = object : Artifact {
        override fun toString(): String = "use_frameworks!"
    }
}