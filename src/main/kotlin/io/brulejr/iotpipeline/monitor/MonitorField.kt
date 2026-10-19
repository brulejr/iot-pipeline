/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

/**
 * One top-level field of a reading's payload, as the monitor shows it.
 *
 * @property value the field's value as text; a nested object or array as its JSON.
 * @property changed true when the device sent something else for this field last time,
 *   or did not send it at all. Never true for radio metadata, which differs on every
 *   transmission and says nothing about what the device sensed.
 * @property previous what the device sent for this field last time, when it changed.
 * @property radio true for receiver metadata such as `rssi` or `time`: the fields
 *   dedupe ignores, from `pipeline.fingerprint.excluded-event-fields`.
 */
data class MonitorField(
    val name: String,
    val value: String,
    val changed: Boolean,
    val previous: String?,
    val radio: Boolean,
)
