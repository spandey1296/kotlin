/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.cocoapods.podfile

class PodfileConfigurator(private val podfileExtension: PodfileExtension) {
    fun configure() {
        with(podfileExtension) {
            check(target != null && xcodeproj != null) { "Cannot configure Podfile for project '${project.name}'" }
            val podfileBuilder = StringBuilder()
            with(target!!){
                podfileBuilder.appendln("target '$name' do")
                podfileBuilder.appendln("    $artifact")
                podfileBuilder.appendln("    $artifact")

            }

            val podfile = xcodeproj!!.parentFile.resolve("Podfile")

        }
    }
}