package com.tvproxy.app.data.epg

import java.io.IOException

/** Items yielded by [XmltvParser], streamed in document order. */
sealed interface XmltvItem {

    /** XMLTV `<channel>` element (id ↔ display name mapping; joined via `tvg-id`). */
    data class Channel(
        val id: String,
        val displayName: String? = null,
        val iconUrl: String? = null,
    ) : XmltvItem

    /** XMLTV `<programme>` element. Times are epoch milliseconds (offset-aware). */
    data class Programme(
        val channelEpgId: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val title: String,
        val subTitle: String? = null,
        val description: String? = null,
        val seasonNo: Int? = null,
        val episodeNo: Int? = null,
        val imageUrl: String? = null,
    ) : XmltvItem
}

/** Fatal parse failure — the feed is structurally broken XML. */
class EpgParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
