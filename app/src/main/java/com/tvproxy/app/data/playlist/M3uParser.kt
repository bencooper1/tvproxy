package com.tvproxy.app.data.playlist

import com.tvproxy.app.core.model.CatchupType
import java.io.BufferedReader
import java.io.StringReader
import javax.inject.Inject

/**
 * Streaming M3U/M3U8 extended-playlist parser (ADR-006).
 *
 * Handles `#EXTINF` attribute pairs (`tvg-id`, `tvg-name`, `tvg-logo`, `tvg-chno`,
 * `group-title`, `catchup*`), `#EXTGRP` fallbacks, and tolerates unknown directives
 * (`#KODIPROP`, `#EXTVLCOPT`, `#PLAYLIST`, …), malformed lines, CRLF input and a
 * leading BOM — real-world provider playlists are messy. Pure JVM: unit tests feed
 * fixtures straight through [parse], including a 10k-entry performance sanity check
 * (plan.md §4 M1: parse 10k channels < 5 s on the baseline tier).
 */
class M3uParser @Inject constructor() {

    /** Parse [text], returning all entries in file order. */
    fun parse(text: String): List<M3uEntry> = buildList {
        parse(BufferedReader(StringReader(text))) { add(it) }
    }

    /**
     * Streaming parse: [emit] is invoked once per entry, in file order. A dangling
     * `#EXTINF` without a following URL line is dropped; URL lines without a pending
     * `#EXTINF` become bare entries named after their URL's last segment.
     */
    fun parse(reader: BufferedReader, emit: (M3uEntry) -> Unit) {
        var state = ParseState()
        reader.useLines { lines ->
            for (raw in lines) {
                state = state.consume(raw.trim().removePrefix(BOM), emit)
            }
        }
        // EOF: any pending EXTINF without a URL is discarded by construction.
    }

    /** Immutable per-line loop state — kept separate to stay below cyclomatic limits. */
    private class ParseState(
        val pending: ExtInf? = null,
        val pendingExtGrp: String? = null,
    ) {
        fun consume(line: String, emit: (M3uEntry) -> Unit): ParseState = when {
            line.isEmpty() -> this
            line.startsWith(EXTINF) -> copy(pending = parseExtInf(line))
            line.startsWith(EXTGRP) -> copy(pendingExtGrp = line.substringAfter(EXTGRP).ifBlank { null })
            line.startsWith(DIRECTIVE_PREFIX) -> this // #EXTM3U header, #KODIPROP/#EXTVLCOPT etc.
            else -> emitEntry(line, emit)
        }

        /** A non-directive line is the stream URL for the pending EXTINF (or a bare entry). */
        private fun emitEntry(url: String, emit: (M3uEntry) -> Unit): ParseState {
            emit(pending?.toEntry(url, pendingExtGrp) ?: M3uEntry(name = bareName(url), url = url))
            return ParseState()
        }

        private fun bareName(url: String): String = url.substringAfterLast('/').ifBlank { url }

        private fun ExtInf.toEntry(url: String, extGrp: String?): M3uEntry = M3uEntry(
            name = name,
            url = url,
            tvgId = attributes[ATTR_TVG_ID],
            tvgName = attributes[ATTR_TVG_NAME],
            tvgLogo = attributes[ATTR_TVG_LOGO],
            tvgChno = attributes[ATTR_TVG_CHNO]?.toIntOrNull(),
            groupTitle = attributes[ATTR_GROUP_TITLE] ?: extGrp,
            catchupType = catchupType(attributes),
            catchupSource = attributes[ATTR_CATCHUP_SOURCE],
            catchupDays = attributes[ATTR_CATCHUP_DAYS]?.toIntOrNull(),
            durationSec = durationSec,
        )
    }

    /** One parsed `#EXTINF` line before its URL arrives. */
    internal data class ExtInf(
        val attributes: Map<String, String>,
        val durationSec: Int?,
        val name: String,
    )

    companion object {
        private const val BOM = "\uFEFF"
        private const val DIRECTIVE_PREFIX = "#"
        private const val EXTINF = "#EXTINF"
        private const val EXTGRP = "#EXTGRP:"

        private const val ATTR_TVG_ID = "tvg-id"
        private const val ATTR_TVG_NAME = "tvg-name"
        private const val ATTR_TVG_LOGO = "tvg-logo"
        private const val ATTR_TVG_CHNO = "tvg-chno"
        private const val ATTR_GROUP_TITLE = "group-title"
        private const val ATTR_CATCHUP = "catchup"
        private const val ATTR_CATCHUP_SOURCE = "catchup-source"
        private const val ATTR_CATCHUP_DAYS = "catchup-days"

        // "#EXTINF:<duration> <attrs>,<display name>" — the name starts after the first
        // comma that follows the attribute block (attribute values are always quoted).
        private val EXTINF_PATTERN = Regex("""^#EXTINF\s*:\s*(-?\d+(?:\.\d+)?)?\s*(.*?),(.*)$""")
        private val ATTR_PATTERN = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

        private fun parseExtInf(line: String): ExtInf {
            val groups = EXTINF_PATTERN.find(line)?.groupValues
            return ExtInf(
                attributes = groups?.getOrNull(IDX_ATTRS)?.let(::attrMap).orEmpty(),
                durationSec = groups?.getOrNull(IDX_DURATION)?.toDoubleOrNull()?.toInt(),
                name = groups?.getOrNull(IDX_NAME)?.trim().orEmpty(),
            )
        }

        private const val IDX_DURATION = 1
        private const val IDX_ATTRS = 2
        private const val IDX_NAME = 3

        private fun attrMap(attrText: String): Map<String, String> =
            ATTR_PATTERN.findAll(attrText).associate { (key, value) -> key.lowercase() to value }

        private fun catchupType(attrs: Map<String, String>): CatchupType {
            val hasSource = !attrs[ATTR_CATCHUP_SOURCE].isNullOrBlank()
            return when (attrs[ATTR_CATCHUP]?.lowercase().orEmpty()) {
                "default" -> CatchupType.DEFAULT
                "append" -> CatchupType.APPEND
                "shift" -> CatchupType.SHIFT
                "flussonic" -> CatchupType.FLUSSONIC
                "xc", "xstream", "xtream" -> CatchupType.XTREAM
                else -> if (hasSource) CatchupType.DEFAULT else CatchupType.NONE
            }
        }
    }
}
