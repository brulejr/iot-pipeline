/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import java.time.Instant

/**
 * Where sightings of unpromoted devices accumulate, hour by hour.
 */
interface SightingRegistryPort {

    /** Adds [sighting] to its device's bucket for the hour it was received in. */
    fun record(sighting: Sighting)

    /** Every bucket whose hour starts at or after [since]. */
    fun bucketsSince(since: Instant): List<SightingBucket>
}
