/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.resolve.BindingTraceFilter.Companion.ACCEPT_ALL
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

class TemporaryBindingTrace(val trace: BindingTrace, debugName: String, filter: BindingTraceFilter) :
    DelegatingBindingTrace(trace.bindingContext, debugName, true, filter, false) {
    fun commit() {
        addOwnDataTo(trace)
        clear()
    }

    fun commit(filter: TraceEntryFilter, commitDiagnostics: Boolean) {
        addOwnDataTo(trace, filter, commitDiagnostics)
        clear()
    }

    fun getCallbacksToPrecommit(
        filter: (WritableSlice<*, *>?, Any) -> Boolean,
        commitDiagnostics: Boolean
    ): Pair<() -> Unit, () -> Unit> {
        var wasPrecommitted = false
        val precommit = {
            addOwnDataTo(trace, filter, commitDiagnostics)
            wasPrecommitted = true
        }
        val commitAllRemaining = {
            if (wasPrecommitted) {
                addOwnDataTo(trace, { slice, key -> !filter(slice, key) }, commitDiagnostics)
                clear()
            } else {
                commit()
            }
        }

        return precommit to commitAllRemaining
    }

    override fun wantsDiagnostics(): Boolean {
        return trace.wantsDiagnostics()
    }

    companion object {
        @JvmOverloads
        @JvmStatic
        fun create(trace: BindingTrace, debugName: String, filter: BindingTraceFilter = ACCEPT_ALL): TemporaryBindingTrace {
            return TemporaryBindingTrace(trace, debugName, filter)
        }

        @JvmStatic
        fun create(trace: BindingTrace, debugName: String, resolutionSubjectForMessage: Any?): TemporaryBindingTrace {
            return create(trace, AnalyzingUtils.formDebugNameForBindingTrace(debugName, resolutionSubjectForMessage))
        }
    }
}