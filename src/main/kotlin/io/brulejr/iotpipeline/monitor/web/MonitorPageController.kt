/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor.web

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.console.ConsoleDatafill
import io.brulejr.iotpipeline.console.web.ConsoleLinks
import io.brulejr.iotpipeline.console.web.Htmx
import io.brulejr.iotpipeline.monitor.MonitorDatafill
import io.brulejr.iotpipeline.monitor.MonitorEvent
import io.brulejr.iotpipeline.monitor.MonitorFilter
import io.brulejr.iotpipeline.monitor.MonitorScope
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.monitor.MonitorSink
import io.brulejr.iotpipeline.promote.PromotionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.util.UriComponentsBuilder
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

/**
 * The live monitor page: readings streamed as they are classified, filtered to one
 * device or a search, with each field that changed since the device's last
 * transmission marked. Beside it, the devices heard recently.
 *
 * The stream is server-sent events carrying rendered HTML, which htmx's SSE extension
 * prepends to the feed. Each event is rendered on the stream's own thread, outside any
 * request, so its links come from [ConsoleLinks] rather than Thymeleaf's `@{…}`.
 */
@Controller
@RequestMapping("/ui/monitor")
class MonitorPageController(
    private val monitor: MonitorService,
    private val promotions: PromotionService,
    private val templates: ITemplateEngine,
    private val datafill: MonitorDatafill,
    private val console: ConsoleDatafill,
) {

    @GetMapping
    fun page(
        @RequestParam(defaultValue = DEFAULT_SOURCE) source: String,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "ALL") scope: MonitorScope,
        @RequestParam(defaultValue = "false") changes: Boolean,
        model: Model,
    ): String {
        feedModel(model, source, id, q, scope, changes)
        heard(model)
        return "ui/monitor/page"
    }

    /** A new feed for a changed filter; replacing the old one closes its stream. */
    @GetMapping("/feed")
    fun feed(
        @RequestParam(defaultValue = DEFAULT_SOURCE) source: String,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "ALL") scope: MonitorScope,
        @RequestParam(defaultValue = "false") changes: Boolean,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        feedModel(model, source, id, q, scope, changes)
        Htmx.pushUrl(response, query(request.contextPath + "/ui/monitor", source, id, q, scope, changes))
        return "ui/monitor/page :: feed"
    }

    /** The heard-devices panel, reloaded on a timer. */
    @GetMapping("/heard")
    fun heard(model: Model): String {
        val promoted = promotions.all().associateBy { it.deviceKey }
        // A Java list: SpEL cannot call methods on Kotlin's empty singletons.
        model.addAttribute("heard", monitor.heard().take(HEARD_SHOWN).toMutableList())
        model.addAttribute("heardTotal", monitor.stats().heardDevices)
        model.addAttribute("promoted", promoted)
        model.addAttribute("refreshSeconds", REFRESH_SECONDS)
        return "ui/monitor/page :: heard"
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @RequestParam(defaultValue = DEFAULT_SOURCE) source: String,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "ALL") scope: MonitorScope,
        @RequestParam(defaultValue = "false") changes: Boolean,
        request: HttpServletRequest,
    ): SseEmitter {
        val emitter = SseEmitter(datafill.streamTimeout.toMillis())
        val links = ConsoleLinks(request.contextPath)
        val sink = object : MonitorSink {
            override fun event(event: MonitorEvent) {
                emitter.send(SseEmitter.event().name("reading").id(event.sequence.toString()).data(render(event, links)))
            }

            override fun heartbeat() {
                emitter.send(SseEmitter.event().comment("still here"))
            }

            override fun closed() {
                emitter.complete()
            }
        }
        val subscription = monitor.subscribe(filter(source, id, q, scope, changes), sink)
        if (subscription == null) {
            emitter.send(SseEmitter.event().name("refused").data(refusal()))
            emitter.complete()
            return emitter
        }
        emitter.onCompletion(subscription::close)
        emitter.onTimeout(subscription::close)
        emitter.onError { subscription.close() }
        return emitter
    }

    private fun feedModel(
        model: Model,
        source: String,
        id: String?,
        q: String?,
        scope: MonitorScope,
        changes: Boolean,
    ) {
        val filter = filter(source, id, q, scope, changes)
        model.addAttribute("source", source)
        model.addAttribute("id", id.orEmpty())
        model.addAttribute("q", q.orEmpty())
        model.addAttribute("scope", scope)
        model.addAttribute("changes", changes)
        model.addAttribute("filter", filter)
        model.addAttribute("watched", filter.deviceKey)
        model.addAttribute("watchedPromotion", filter.deviceKey?.let { promotions.find(it) })
        model.addAttribute("streamUrl", query("/ui/monitor/stream", source, id, q, scope, changes))
        model.addAttribute("scopes", MonitorScope.entries)
        model.addAttribute("refreshSeconds", console.refreshInterval.toSeconds())
    }

    private fun filter(source: String, id: String?, q: String?, scope: MonitorScope, changes: Boolean) =
        MonitorFilter(
            deviceKey = id?.trim()?.takeIf { it.isNotEmpty() }?.let { DeviceKey(source.trim(), it) },
            text = q?.trim()?.takeIf { it.isNotEmpty() },
            scope = scope,
            changesOnly = changes,
        )

    /** An SSE data field is one line, so the fragment's own line breaks are folded away. */
    private fun render(event: MonitorEvent, links: ConsoleLinks): String {
        val context = Context(Locale.getDefault(), mapOf("event" to event, "links" to links))
        return templates.process("ui/monitor/event", setOf("event"), context).replace(LINE_BREAKS, " ")
    }

    private fun refusal(): String {
        val context = Context(Locale.getDefault(), mapOf("max" to datafill.maxSubscribers))
        return templates.process("ui/monitor/event", setOf("refused"), context).replace(LINE_BREAKS, " ")
    }

    private fun query(path: String, source: String, id: String?, q: String?, scope: MonitorScope, changes: Boolean) =
        UriComponentsBuilder.fromPath(path)
            .apply {
                if (!id.isNullOrBlank()) queryParam("source", "{source}").queryParam("id", "{id}")
                if (!q.isNullOrBlank()) queryParam("q", "{q}")
                if (scope != MonitorScope.ALL) queryParam("scope", scope.name)
                if (changes) queryParam("changes", "true")
            }
            .encode()
            .buildAndExpand(mapOf("source" to source, "id" to id.orEmpty(), "q" to q.orEmpty()))
            .toUriString()

    private companion object {
        const val DEFAULT_SOURCE = "rtl433"
        const val HEARD_SHOWN = 60
        const val REFRESH_SECONDS = 3L
        val LINE_BREAKS = Regex("\\s*[\\r\\n]+\\s*")
    }
}
