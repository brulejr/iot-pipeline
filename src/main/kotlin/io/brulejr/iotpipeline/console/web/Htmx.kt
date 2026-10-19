/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console.web

import jakarta.servlet.http.HttpServletResponse

/** Response headers htmx acts on, named once. */
object Htmx {

    /** Events raised in the page when the response arrives, e.g. `devicesChanged`. */
    fun trigger(response: HttpServletResponse, vararg events: String) {
        response.setHeader("HX-Trigger", events.joinToString(", "))
    }

    /** Sends the browser to [url], as a full page load. */
    fun redirect(response: HttpServletResponse, url: String) {
        response.setHeader("HX-Redirect", url)
    }

    /** Puts [url] in the address bar without loading it. */
    fun pushUrl(response: HttpServletResponse, url: String) {
        response.setHeader("HX-Push-Url", url)
    }

    /** Raised to close the dialog a form was submitted from. */
    const val CLOSE_MODAL = "closeModal"
}
