/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

/**
 * What dedupe has decided since startup.
 *
 * @property unique readings passed on.
 * @property duplicates readings discarded as repeats of one already passed.
 * @property rememberedDevices transmitters currently held in the window, approximately.
 */
data class DedupeStats(
    val unique: Long,
    val duplicates: Long,
    val rememberedDevices: Long,
)
