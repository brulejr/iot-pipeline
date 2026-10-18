/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import java.time.Instant

/**
 * What publication has done since startup, and what it is waiting on.
 *
 * @property published messages the broker accepted.
 * @property pending messages held for retry, one per topic.
 * @property superseded held messages replaced by a newer one for the same topic before
 *   they could be delivered. Only the latest value matters to a consumer, and every
 *   reading is in PostgreSQL regardless.
 * @property failures publish attempts that failed.
 * @property dropped messages discarded because publication is disabled.
 * @property circuitOpen true while the broker is failing: new messages are held rather
 *   than tried, so none can overtake one already waiting.
 * @property nextAttemptAt when held messages will next be tried, while the circuit is open.
 */
data class PublishStats(
    val published: Long,
    val pending: Int,
    val superseded: Long,
    val failures: Long,
    val dropped: Long,
    val circuitOpen: Boolean,
    val lastPublishedAt: Instant?,
    val lastFailure: String?,
    val nextAttemptAt: Instant?,
)
