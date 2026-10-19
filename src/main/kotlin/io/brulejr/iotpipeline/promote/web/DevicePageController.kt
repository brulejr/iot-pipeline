/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.web

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.console.ConsoleDatafill
import io.brulejr.iotpipeline.console.web.Htmx
import io.brulejr.iotpipeline.console.web.Toast
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.parse.ParseService
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionProblem
import io.brulejr.iotpipeline.promote.PromotionResult
import io.brulejr.iotpipeline.promote.PromotionService
import io.brulejr.iotpipeline.store.StoreService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant

/**
 * The console's device pages: the promoted devices, promoting and editing one, and a
 * page for any device key, promoted or not, with what it last sent and what was stored.
 *
 * A device id contains slashes, so devices are addressed by `source` and `id` query
 * parameters rather than in the path.
 */
@Controller
@RequestMapping("/ui/devices")
class DevicePageController(
    private val promotions: PromotionService,
    private val classification: ClassificationService,
    private val monitor: MonitorService,
    private val parse: ParseService,
    private val store: StoreService,
    private val datafill: ConsoleDatafill,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) area: String?,
        model: Model,
    ): String {
        rows(q, area, model)
        model.addAttribute("areas", promotions.all().map { it.area }.distinct().sorted())
        model.addAttribute("total", promotions.all().size)
        return "ui/devices/list"
    }

    /** The device rows alone, for the filter and after any change to a promotion. */
    @GetMapping("/rows")
    fun rows(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) area: String?,
        model: Model,
    ): String {
        val needle = q?.trim().orEmpty()
        val devices = promotions.all()
            .filter { area.isNullOrBlank() || it.area == area }
            .filter { device ->
                needle.isEmpty() || listOf(device.name, device.type, device.area, device.deviceKey.id)
                    .any { it.contains(needle, ignoreCase = true) }
            }
            .sortedWith(compareBy({ it.area.lowercase() }, { it.name.lowercase() }))
            .map { DeviceRow(it, monitor.heard(it.deviceKey)) }
        model.addAttribute("devices", devices.toMutableList())
        model.addAttribute("q", needle)
        model.addAttribute("area", area.orEmpty())
        return "ui/devices/list :: rows"
    }

    /** Any device, promoted or not: its promotion, what it last sent, and its stored values. */
    @GetMapping("/view")
    fun view(@RequestParam(defaultValue = "rtl433") source: String, @RequestParam id: String, model: Model): String {
        val key = DeviceKey(source, id)
        val heard = monitor.heard(key)
        val recent = store.recent(key, datafill.recentReadings)
        val deviceModel = heard?.let { classification.model(it.modelFingerprint) }
        model.addAttribute("key", key)
        model.addAttribute("promotion", promotions.find(key))
        model.addAttribute("heard", heard)
        model.addAttribute("deviceModel", deviceModel)
        // Java collections throughout: SpEL cannot call methods on Kotlin's empty singletons.
        model.addAttribute("payload", heard?.payload?.properties()?.map { (name, value) ->
            name to if (value.isValueNode) value.asString() else value.toString()
        }.orEmpty().toMutableList())
        model.addAttribute("preview", heard?.let { parse.preview(it.modelFingerprint, it.payload) }?.toMutableList())
        model.addAttribute("latest", recent.distinctBy { it.field }.toMutableList())
        model.addAttribute("recent", recent.toMutableList())
        return "ui/devices/detail"
    }

    /**
     * The promotion dialog. For a device already promoted it edits that promotion;
     * otherwise it promotes, with the key filled in when one is given, as from a
     * recommendation or the live monitor.
     */
    @GetMapping("/form")
    fun form(
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) id: String?,
        model: Model,
    ): String {
        val key = if (id.isNullOrBlank()) null else DeviceKey(source ?: DEFAULT_SOURCE, id)
        val existing = key?.let { promotions.find(it) }
        formModel(
            model,
            editing = existing != null,
            source = key?.source ?: DEFAULT_SOURCE,
            id = key?.id.orEmpty(),
            name = existing?.name.orEmpty(),
            type = existing?.type.orEmpty(),
            area = existing?.area.orEmpty(),
            problems = emptyList(),
        )
        return "ui/devices/form :: dialog"
    }

    /**
     * Saves the dialog. On success the dialog closes and every list of devices reloads;
     * otherwise the dialog is redrawn with each problem against its field.
     *
     * Editing keeps the original promotion time; promoting a device that is already
     * promoted is refused, so a typo in the key cannot overwrite another device.
     */
    @PostMapping
    fun save(
        @RequestParam(defaultValue = "false") editing: Boolean,
        @RequestParam(defaultValue = "") source: String,
        @RequestParam(defaultValue = "") id: String,
        @RequestParam(defaultValue = "") name: String,
        @RequestParam(defaultValue = "") type: String,
        @RequestParam(defaultValue = "") area: String,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val key = DeviceKey(source.trim(), id.trim())
        val existing = promotions.find(key)
        val result = if (!editing && existing != null) {
            PromotionResult.Invalid(listOf(PromotionProblem("deviceId", "is already promoted as \"${existing.name}\"")))
        } else {
            promotions.promote(
                PromotedDevice(key, name.trim(), type.trim(), area.trim(), existing?.promotedAt ?: Instant.now()),
            )
        }
        return when (result) {
            is PromotionResult.Promoted -> {
                Htmx.trigger(response, Htmx.CLOSE_MODAL, DEVICES_CHANGED)
                model.addAttribute(
                    "toast",
                    Toast.success(if (editing) "Saved ${result.device.name}" else "Promoted ${result.device.name}"),
                )
                "ui/layout :: toastOnly"
            }
            is PromotionResult.Invalid -> {
                formModel(model, editing, source, id, name, type, area, result.problems)
                "ui/devices/form :: dialog"
            }
        }
    }

    /** Withdraws a promotion, which also removes the device from Home Assistant. */
    @DeleteMapping
    fun demote(
        @RequestParam(defaultValue = DEFAULT_SOURCE) source: String,
        @RequestParam id: String,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val key = DeviceKey(source, id)
        val name = promotions.find(key)?.name ?: id
        model.addAttribute(
            "toast",
            if (promotions.demote(key)) Toast.success("Demoted $name") else Toast.danger("$name was not promoted"),
        )
        Htmx.trigger(response, DEVICES_CHANGED)
        return "ui/layout :: toastOnly"
    }

    private fun formModel(
        model: Model,
        editing: Boolean,
        source: String,
        id: String,
        name: String,
        type: String,
        area: String,
        problems: List<PromotionProblem>,
    ) {
        val promoted = promotions.all()
        model.addAttribute("editing", editing)
        model.addAttribute("source", source)
        model.addAttribute("id", id)
        model.addAttribute("name", name)
        model.addAttribute("type", type)
        model.addAttribute("area", area)
        model.addAttribute("errors", HashMap(problems.associate { it.field to it.reason }))
        model.addAttribute("types", (TYPES + promoted.map { it.type }).distinct().sorted())
        model.addAttribute("areas", promoted.map { it.area }.distinct().sorted())
        val promotedKeys = promoted.map { it.deviceKey }.toSet()
        model.addAttribute("heard", monitor.heard().filter { it.deviceKey !in promotedKeys }.take(50).toMutableList())
    }

    private companion object {
        const val DEFAULT_SOURCE = "rtl433"

        /** Raised after any promotion changes, so every list of devices reloads. */
        const val DEVICES_CHANGED = "devicesChanged"

        /** Offered while typing. door, window and garage_door also set a contact's class. */
        val TYPES = listOf("door", "window", "garage_door", "contact", "motion", "thermometer", "weather_station")
    }
}
