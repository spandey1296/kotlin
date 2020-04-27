/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.cocoapods

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.tasks.*
import java.io.File

open class PodfileExtension(private val project: Project) {
    @get:Input
    lateinit var xcodeproj: String

    @get:Nested
    lateinit var target: Target


    private val _kotlinPods = project.container(KotlinPodDependency::class.java)

    // For some reason Gradle doesn't consume the @Nested annotation on NamedDomainObjectContainer.
    @get:Nested
    protected val kotlinPodsAsTaskInputDependency: List<KotlinPodDependency>
        get() = _kotlinPods.toList()

    /**
     * Returns a list of pod dependencies.
     */
    // Already taken into account as a task input in the [podsAsTaskInput] property.
    @get:Internal
    val kotlinPodDependencies: NamedDomainObjectSet<KotlinPodDependency>
        get() = _kotlinPods

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    @JvmOverloads
    fun kotlinPod(name: String) {
        check(_kotlinPods.findByName(name) == null) { "Project already depends on a local Kotlin Pod with name $name" }
        _kotlinPods.add(KotlinPodDependency(name))
    }

    data class KotlinPodDependency(
        private val name: String
    ) : Named {
        @Input
        override fun getName(): String = name
    }

    data class Target(
        private val name: String,
        @get:Nested val dependencyMode: DependencyMode,
        @get:Nested val platform: Platform
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
    }

    sealed class DependencyMode(private val name: String) : Named {
        object UseModularHeaders : DependencyMode("use_modular_headers!")
        object UseFramework : DependencyMode("use_frameworks!")

        @Input
        override fun getName(): String = name
    }

}