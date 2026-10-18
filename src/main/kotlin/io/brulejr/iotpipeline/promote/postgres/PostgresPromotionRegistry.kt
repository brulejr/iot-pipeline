/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.pipeline.getInstant
import io.brulejr.iotpipeline.pipeline.toTimestamptz
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionOperations
import java.sql.ResultSet

/**
 * PostgreSQL-backed [PromotionPort], so an approval survives a restart. Promotions are
 * hand-made decisions; losing them would silence every device until someone approved
 * them all again.
 */
class PostgresPromotionRegistry(
    private val jdbc: JdbcClient,
    private val transactions: TransactionOperations,
) : PromotionPort {

    override fun find(deviceKey: DeviceKey): PromotedDevice? = jdbc
        .sql("SELECT * FROM promoted_devices WHERE source = :source AND device_id = :deviceId")
        .param("source", deviceKey.source)
        .param("deviceId", deviceKey.id)
        .query { rs, _ -> rs.toDevice() }
        .optional()
        .orElse(null)

    override fun all(): List<PromotedDevice> = jdbc
        // Promotions made together share a time, so the key breaks the tie predictably.
        .sql("SELECT * FROM promoted_devices ORDER BY promoted_at, source, device_id")
        .query { rs, _ -> rs.toDevice() }
        .list()

    override fun save(device: PromotedDevice) {
        // Replaces any earlier promotion: re-promoting is how a label gets corrected.
        jdbc
            .sql(
                """
                INSERT INTO promoted_devices (source, device_id, name, type, area, promoted_at)
                VALUES (:source, :deviceId, :name, :type, :area, :promotedAt)
                ON CONFLICT (source, device_id) DO UPDATE
                SET name = EXCLUDED.name, type = EXCLUDED.type, area = EXCLUDED.area,
                    promoted_at = EXCLUDED.promoted_at
                """,
            )
            .param("source", device.deviceKey.source)
            .param("deviceId", device.deviceKey.id)
            .param("name", device.name)
            .param("type", device.type)
            .param("area", device.area)
            .param("promotedAt", device.promotedAt.toTimestamptz())
            .update()
    }

    override fun saveAll(devices: List<PromotedDevice>) {
        transactions.executeWithoutResult { devices.forEach(::save) }
    }

    override fun demote(deviceKey: DeviceKey): Boolean = jdbc
        .sql("DELETE FROM promoted_devices WHERE source = :source AND device_id = :deviceId")
        .param("source", deviceKey.source)
        .param("deviceId", deviceKey.id)
        .update() > 0

    private fun ResultSet.toDevice() = PromotedDevice(
        deviceKey = DeviceKey(getString("source"), getString("device_id")),
        name = getString("name"),
        type = getString("type"),
        area = getString("area"),
        promotedAt = checkNotNull(getInstant("promoted_at")),
    )
}
