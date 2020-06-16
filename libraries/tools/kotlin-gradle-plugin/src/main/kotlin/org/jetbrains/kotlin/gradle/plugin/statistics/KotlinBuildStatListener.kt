/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.statistics

import org.gradle.api.invocation.Gradle
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.jetbrains.kotlin.statistics.BuildSessionLogger
import javax.management.ObjectName

open class KotlinBuildStatListener(val gradle: Gradle, val beanName: ObjectName) : OperationCompletionListener {
    private val sessionLogger = BuildSessionLogger(gradle.gradleUserHomeDir)

    override fun onFinish(event: FinishEvent?) {
        //todo is it any chamce to get failure exception?
        KotlinBuildStatHandler().buildFinished(gradle, beanName, sessionLogger, event?.descriptor?.name, null)
    }
}