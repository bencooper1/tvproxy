package com.tvproxy.app.data.epg

import android.util.Xml
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Streaming XMLTV parser (ADR-006), driven by [XmlPullParser] so it can ingest
 * 100k-programme feeds without holding them in memory (budget: < 30 s, plan.md M1).
 *
 * Resilience policy: individual programmes with missing/broken attributes are
 * skipped (provider feeds are dirty), but structurally malformed XML aborts the
 * import with [EpgParseException] so the caller can keep previous data.
 *
 * The parser instance is injectable: production passes `android.util.Xml.newPullParser()`
 * while JVM unit tests pass a kxml2 parser (the Android pull implementation does not
 * exist off-device).
 */
class XmltvParser @Inject constructor() {

    /**
     * Parse [input], invoking [onBatch] with up to [batchSize] items each time
     * (repositories persist per batch inside one import transaction).
     */
    suspend fun parse(
        input: InputStream,
        parser: XmlPullParser,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        onBatch: suspend (List<XmltvItem>) -> Unit,
    ) {
        val batch = ArrayList<XmltvItem>(batchSize)
        try {
            parseEvents(input, parser) { item ->
                batch += item
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
        } catch (e: XmlPullParserException) {
            throw EpgParseException("Malformed XMLTV feed: ${e.message}", e)
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
    }

    private fun parseEvents(
        input: InputStream,
        parser: XmlPullParser,
        emit: (XmltvItem) -> Unit,
    ) {
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) handleTag(parser)?.let(emit)
            event = parser.next()
        }
    }

    private fun handleTag(parser: XmlPullParser): XmltvItem? = when (parser.name) {
        TAG_CHANNEL -> readChannel(parser)
        TAG_PROGRAMME -> readProgramme(parser)
        else -> null
    }

    private fun readChannel(parser: XmlPullParser): XmltvItem {
        val id = parser.getAttributeValue(null, ATTR_ID).orEmpty()
        var displayName: String? = null
        var iconUrl: String? = null
        loopChildren(parser, TAG_CHANNEL) { name ->
            when (name) {
                TAG_DISPLAY_NAME -> displayName = parser.nextText()
                TAG_ICON -> if (iconUrl == null) iconUrl = parser.getAttributeValue(null, ATTR_SRC)
            }
        }
        return XmltvItem.Channel(id = id, displayName = displayName, iconUrl = iconUrl)
    }

    /**
     * Reads a `<programme>`. Returns null (skip) for rows without a usable
     * channel id or timestamps — the import stays resumable across dirty rows.
     */
    private fun readProgramme(parser: XmlPullParser): XmltvItem? {
        val channel = parser.getAttributeValue(null, ATTR_CHANNEL)?.trim().orEmpty()
        val start = parseXmltvDate(parser.getAttributeValue(null, ATTR_START))
        val stop = parseXmltvDate(parser.getAttributeValue(null, ATTR_STOP))
        if (channel.isEmpty() || start == null || stop == null || stop <= start) return null

        val fields = ProgrammeFields()
        loopChildren(parser, TAG_PROGRAMME) { name -> fields.read(parser, name) }
        return XmltvItem.Programme(
            channelEpgId = channel,
            startEpochMs = start,
            endEpochMs = stop,
            title = fields.title,
            subTitle = fields.subTitle,
            description = fields.description,
            seasonNo = fields.seasonNo,
            episodeNo = fields.episodeNo,
            imageUrl = fields.imageUrl,
        )
    }

    /** Iterates [parser] until the end tag of [tag]; [onChild] sees each child start tag. */
    private inline fun loopChildren(
        parser: XmlPullParser,
        tag: String,
        onChild: (name: String) -> Unit,
    ) {
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == tag)) {
            if (event == XmlPullParser.END_DOCUMENT) throw EpgParseException("Unexpected EOF in <$tag>")
            if (event == XmlPullParser.START_TAG) onChild(parser.name.orEmpty())
            event = parser.next()
        }
    }

    /** Mutable accumulator for `<programme>` children — keeps [readProgramme] linear. */
    private class ProgrammeFields {
        var title: String = ""
        var subTitle: String? = null
        var description: String? = null
        var seasonNo: Int? = null
        var episodeNo: Int? = null
        var imageUrl: String? = null

        fun read(parser: XmlPullParser, name: String) {
            when (name) {
                TAG_TITLE -> title = parser.nextText().trim()
                TAG_SUB_TITLE -> subTitle = parser.nextText().trim()
                TAG_DESC -> description = parser.nextText().trim()
                TAG_ICON -> if (imageUrl == null) imageUrl = parser.getAttributeValue(null, ATTR_SRC)
                TAG_EPISODE_NUM -> readEpisodeNum(parser)
            }
        }

        /** xmltv_ns `season.episode.part` is 0-based; we store 1-based numbers. */
        private fun readEpisodeNum(parser: XmlPullParser) {
            val isXmltvNs = parser.getAttributeValue(null, ATTR_SYSTEM) == SYSTEM_XMLTV_NS
            if (isXmltvNs && seasonNo == null && episodeNo == null) {
                val parts = parser.nextText().trim().split(".")
                seasonNo = parts.getOrNull(0)?.trim()?.toIntOrNull()?.plus(1)
                episodeNo = parts.getOrNull(1)?.trim()?.toIntOrNull()?.plus(1)
            }
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 500

        private const val TAG_CHANNEL = "channel"
        private const val TAG_PROGRAMME = "programme"
        private const val TAG_DISPLAY_NAME = "display-name"
        private const val TAG_ICON = "icon"
        private const val TAG_TITLE = "title"
        private const val TAG_SUB_TITLE = "sub-title"
        private const val TAG_DESC = "desc"
        private const val TAG_EPISODE_NUM = "episode-num"
        private const val ATTR_ID = "id"
        private const val ATTR_SRC = "src"
        private const val ATTR_CHANNEL = "channel"
        private const val ATTR_START = "start"
        private const val ATTR_STOP = "stop"
        private const val ATTR_SYSTEM = "system"
        private const val SYSTEM_XMLTV_NS = "xmltv_ns"

        private const val DATE_LENGTH = 14 // yyyyMMddHHmmss; a zone suffix may follow
        private const val PATTERN_WITH_ZONE = "yyyyMMddHHmmss Z"
        private const val PATTERN_NO_ZONE = "yyyyMMddHHmmss"

        /**
         * XMLTV times look like `20240101193000 +0200`. Unzoned stamps are read as UTC —
         * providers are inconsistent, and UTC is the least-surprising neutral choice.
         */
        internal fun parseXmltvDate(raw: String?): Long? {
            val value = raw?.trim().orEmpty()
            val withZone = if (value.isEmpty()) null else runParse(value, PATTERN_WITH_ZONE, null)
            return withZone ?: runParse(value.take(DATE_LENGTH), PATTERN_NO_ZONE, TimeZone.getTimeZone("UTC"))
        }

        private fun runParse(value: String, pattern: String, zone: TimeZone?): Long? {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            if (zone != null) format.timeZone = zone
            return try {
                format.parse(value)?.time
            } catch (e: ParseException) {
                null
            }
        }
    }
}
