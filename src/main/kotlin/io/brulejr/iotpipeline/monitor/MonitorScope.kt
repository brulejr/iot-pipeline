/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

/** Which devices a stream follows, by whether they have been promoted. */
enum class MonitorScope { ALL, UNPROMOTED, PROMOTED }
