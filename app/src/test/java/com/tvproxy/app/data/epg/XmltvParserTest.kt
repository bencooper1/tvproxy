package com.tvproxy.app.data.epg

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory

/**
 * XMLTV parser unit tests (plan.md M1 gate: fixtures + malformed input).
 *
 * Runs on the JVM via kxml2 — the Android XmlPullParser is not available off-device.
 */
class XmltvParserTest {

    private val parser = XmltvParser()

    private fun pullParser() = XmlPullParserFactory.newInstance().newPullParser()

    private suspend fun parseAll(xml: String): List<XmltvItem> = buildList {
        parser.parse(ByteArrayInputStream(xml.toByteArray()), pullParser()) { addAll(it) }
    }

    @Test
    fun parse_channelAndProgrammeFields() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="fixture">
              <channel id="bbc1.uk">
                <display-name>BBC One</display-name>
                <icon src="http://img/bbc1.png"/>
              </channel>
              <programme start="20240101180000 +0000" stop="20240101190000 +0000" channel="bbc1.uk">
                <title>Evening News</title>
                <sub-title>Headlines</sub-title>
                <desc>Daily news.</desc>
                <icon src="http://img/prog.png"/>
              </programme>
            </tv>
        """.trimIndent()

        val items = parseAll(xml)

        assertThat(items).hasSize(2)
        val channel = items.filterIsInstance<XmltvItem.Channel>().single()
        assertThat(channel.id).isEqualTo("bbc1.uk")
        assertThat(channel.displayName).isEqualTo("BBC One")
        assertThat(channel.iconUrl).isEqualTo("http://img/bbc1.png")

        val programme = items.filterIsInstance<XmltvItem.Programme>().single()
        assertThat(programme.channelEpgId).isEqualTo("bbc1.uk")
        assertThat(programme.title).isEqualTo("Evening News")
        assertThat(programme.subTitle).isEqualTo("Headlines")
        assertThat(programme.description).isEqualTo("Daily news.")
        assertThat(programme.imageUrl).isEqualTo("http://img/prog.png")
        assertThat(programme.startEpochMs).isEqualTo(1_704_132_000_000L) // 2024-01-01T18:00:00Z
        assertThat(programme.endEpochMs - programme.startEpochMs).isEqualTo(3_600_000L)
    }

    @Test
    fun parse_timezoneOffset_appliesToInstant() = runTest {
        // 18:00 at +0200 == 16:00 UTC
        val xml = """
            <tv>
              <programme start="20240101180000 +0200" stop="20240101190000 +0200" channel="ch1">
                <title>A</title>
              </programme>
            </tv>
        """.trimIndent()

        val items = parseAll(xml)
        val programme = items.filterIsInstance<XmltvItem.Programme>().single()

        assertThat(programme.startEpochMs).isEqualTo(1_704_124_800_000L) // 2024-01-01T16:00:00Z
    }

    @Test
    fun parse_unzonedTimestamp_assumedUtc() = runTest {
        val xml = """
            <tv><programme start="20240101180000" stop="20240101190000" channel="ch1"><title>A</title></programme></tv>
        """.trimIndent()

        val items = parseAll(xml)

        assertThat(items.filterIsInstance<XmltvItem.Programme>().single().startEpochMs)
            .isEqualTo(1_704_132_000_000L)
    }

    @Test
    fun parse_xmltvNsEpisodeNums_convertedToOneBased() = runTest {
        val xml = """
            <tv>
              <programme start="20240101180000 +0000" stop="20240101190000 +0000" channel="ch1">
                <title>Ep</title>
                <episode-num system="xmltv_ns">0.4.</episode-num>
              </programme>
            </tv>
        """.trimIndent()

        val items = parseAll(xml)
        val programme = items.filterIsInstance<XmltvItem.Programme>().single()

        assertThat(programme.seasonNo).isEqualTo(1)
        assertThat(programme.episodeNo).isEqualTo(5)
    }

    @Test
    fun parse_programmeWithBadTimes_isSkippedButFeedContinues() = runTest {
        val xml = """
            <tv>
              <programme start="not-a-date" stop="20240101190000 +0000" channel="ch1"><title>Bad</title></programme>
              <programme start="20240101180000 +0000" stop="20240101190000 +0000" channel="ch1"><title>Good</title></programme>
              <programme start="20240101180000 +0000" stop="20240101170000 +0000" channel="ch1"><title>Backwards</title></programme>
              <programme start="20240101180000 +0000" stop="20240101190000 +0000"><title>No Channel</title></programme>
            </tv>
        """.trimIndent()

        val items = parseAll(xml)
        val titles = items.filterIsInstance<XmltvItem.Programme>().map { it.title }

        assertThat(titles).containsExactly("Good")
    }

    @Test
    fun parse_batches_respectBatchSize() = runTest {
        val programmes = (1..7).joinToString("\n") { index ->
            """<programme start="2024010112%02d00 +0000" stop="2024010112%02d00 +0000" channel="ch1"><title>P$index</title></programme>"""
                .format(index * 5, index * 5 + 1) // minutes 05→01, 10→06, … all valid
        }
        val xml = "<tv>\n$programmes\n</tv>"

        val batchSizes = mutableListOf<Int>()
        parser.parse(ByteArrayInputStream(xml.toByteArray()), pullParser(), batchSize = 3) { batch ->
            batchSizes += batch.size
        }

        assertThat(batchSizes).containsExactly(3, 3, 1).inOrder()
    }

    @Test
    fun parse_malformedXml_throwsEpgParseException() = runTest {
        val xml = "<tv><programme start='20240101180000 +0000' stop='20240101190000 +0000' channel='x'><title>oops</title>"

        assertFailsWith<EpgParseException> { parseAll(xml) }
    }
}
