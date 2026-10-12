/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.mongo

import io.brulejr.iotpipeline.recommend.Sighting
import io.brulejr.iotpipeline.recommend.SightingBucket
import io.brulejr.iotpipeline.recommend.SightingRegistryPort
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * MongoDB-backed [SightingRegistryPort], so a day of history survives a restart and
 * recommendations do not start from nothing each time the application does.
 */
class MongoSightingRegistry(private val mongo: MongoOperations) : SightingRegistryPort {

    init {
        // Created here rather than declared with @Indexed: annotation-driven index
        // creation runs when the Mongo template is built, which connects to MongoDB even
        // when every registry is in memory. This only runs when this registry is in use.
        mongo.indexOps(SightingBucketDocument::class.java)
            .createIndex(Index().on("hour", Sort.Direction.ASC).expire(RETENTION))
    }

    override fun record(sighting: Sighting) {
        val hour = sighting.receivedAt.truncatedTo(ChronoUnit.HOURS)
        val update = Update()
            .inc("count", 1)
            .max("lastSeen", sighting.receivedAt)
            .setOnInsert("source", sighting.deviceKey.source)
            .setOnInsert("deviceId", sighting.deviceKey.id)
            .setOnInsert("hour", hour)
            .setOnInsert("modelName", sighting.modelName)
            .setOnInsert("modelFingerprint", sighting.modelFingerprint)
        sighting.signal?.let { update.inc("signalSum", it).inc("signalCount", 1) }
        // One atomic upsert: concurrent readings of one device cannot lose a count.
        mongo.upsert(
            Query(Criteria.where("_id").`is`(SightingBucketDocument.idOf(sighting.deviceKey, hour))),
            update,
            SightingBucketDocument::class.java,
        )
    }

    override fun bucketsSince(since: Instant): List<SightingBucket> = mongo
        .find(Query(Criteria.where("hour").gte(since)), SightingBucketDocument::class.java)
        .map { it.toBucket() }

    private companion object {
        /** Well beyond the scoring window, so a longer window still has history. */
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
