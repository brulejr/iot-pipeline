/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console.web

import io.brulejr.iotpipeline.classify.DeviceKey
import org.springframework.web.util.UriComponentsBuilder

/**
 * Console URLs, for fragments rendered outside a request. A live stream renders each
 * reading on its own thread, where Thymeleaf's `@{…}` links cannot be resolved; pages
 * rendered for a request use `@{…}` instead.
 *
 * A device id contains slashes, so it travels as a query parameter, encoded.
 */
class ConsoleLinks(private val contextPath: String) {

    fun device(key: DeviceKey): String = withKey("/ui/devices/view", key)

    fun monitor(key: DeviceKey): String = withKey("/ui/monitor", key)

    fun promote(key: DeviceKey): String = withKey("/ui/devices/form", key)

    fun model(fingerprint: String): String = "$contextPath/ui/models/$fingerprint"

    private fun withKey(path: String, key: DeviceKey): String = UriComponentsBuilder
        .fromPath(contextPath + path)
        .queryParam("source", "{source}")
        .queryParam("id", "{id}")
        .encode()
        .buildAndExpand(key.source, key.id)
        .toUriString()
}
