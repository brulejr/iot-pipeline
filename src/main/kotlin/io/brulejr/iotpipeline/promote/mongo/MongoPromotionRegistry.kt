/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.mongo

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionPort
import io.brulejr.iotpipeline.promote.PromotionResult
import io.brulejr.iotpipeline.promote.validatePromotion
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * MongoDB-backed [PromotionPort], so an approval survives a restart. Promotions are
 * hand-made decisions; losing them would silence every device until someone approved
 * them all again.
 */
class MongoPromotionRegistry(private val mongo: MongoOperations) : PromotionPort {

    override fun find(deviceKey: DeviceKey): PromotedDevice? = mongo
        .findById(PromotedDeviceDocument.idOf(deviceKey), PromotedDeviceDocument::class.java)
        ?.toDevice()

    override fun all(): List<PromotedDevice> = mongo
        .find(Query().with(Sort.by(Sort.Direction.ASC, "promotedAt")), PromotedDeviceDocument::class.java)
        .map { it.toDevice() }

    override fun promote(device: PromotedDevice): PromotionResult {
        validatePromotion(device)?.let { return it }
        // Replaces any earlier promotion: re-promoting is how a label gets corrected.
        mongo.save(PromotedDeviceDocument.of(device))
        return PromotionResult.Promoted(device)
    }

    override fun demote(deviceKey: DeviceKey): Boolean = mongo
        .remove(
            Query(Criteria.where("_id").`is`(PromotedDeviceDocument.idOf(deviceKey))),
            PromotedDeviceDocument::class.java,
        )
        .deletedCount > 0
}
