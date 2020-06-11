/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.browser

import org.w3c.dom.*

@Deprecated(
    message = "This API is deprecated and will be removed soon, use 'kotlinx.browser' package instead",
    replaceWith = ReplaceWith("window", "kotlinx.browser.window")
)
public external val window: Window

@Deprecated(
    message = "This API is deprecated and will be removed soon, use 'kotlinx.browser' package instead",
    replaceWith = ReplaceWith("document", "kotlinx.browser.document")
)
public external val document: Document

@Deprecated(
    message = "This API is deprecated and will be removed soon, use 'kotlinx.browser' package instead",
    replaceWith = ReplaceWith("localStorage", "kotlinx.browser.localStorage")
)
public external val localStorage: Storage

@Deprecated(
    message = "This API is deprecated and will be removed soon, use 'kotlinx.browser' package instead",
    replaceWith = ReplaceWith("sessionStorage", "kotlinx.browser.sessionStorage")
)
public external val sessionStorage: Storage

