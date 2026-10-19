/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console.web

/**
 * A notice shown in the corner of the console after an action, rendered into whatever
 * fragment answers the request and moved into place by htmx.
 *
 * @property level a Bootstrap colour: `success`, `danger`, `warning` or `info`.
 */
data class Toast(val level: String, val message: String) {
    companion object {
        fun success(message: String) = Toast("success", message)

        fun danger(message: String) = Toast("danger", message)
    }
}
