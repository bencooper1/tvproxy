package com.tvproxy.app.data.playlist

import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.CatchupType
import org.junit.Test

/**
 * M3U parser unit tests (plan.md M1 gate: fixture-based, malformed input, perf sanity).
 */
class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun parse_basicEntry_extractsAllAttributes() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://img/bbc1.png" tvg-chno="1" group-title="News",BBC One HD
            http://provider.example/live/bbc1.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries).hasSize(1)
        val entry = entries.single()
        assertThat(entry.name).isEqualTo("BBC One HD")
        assertThat(entry.url).isEqualTo("http://provider.example/live/bbc1.m3u8")
        assertThat(entry.tvgId).isEqualTo("bbc1.uk")
        assertThat(entry.tvgName).isEqualTo("BBC One")
        assertThat(entry.tvgLogo).isEqualTo("http://img/bbc1.png")
        assertThat(entry.tvgChno).isEqualTo(1)
        assertThat(entry.groupTitle).isEqualTo("News")
        assertThat(entry.durationSec).isEqualTo(-1)
        assertThat(entry.catchupType).isEqualTo(CatchupType.NONE)
    }

    @Test
    fun parse_catchupAttributes_mapToCatchupFields() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn.us" catchup="default" catchup-source="http://provider.example/tv?utc={Y}{m}{d}{H}{M}{S}" catchup-days="7",CNN
            http://provider.example/live/cnn.m3u8
            #EXTINF:-1 catchup="shift" catchup-days="3",Shift Tv
            http://provider.example/live/shift.m3u8
            #EXTINF:-1 catchup-source="http://provider.example/archive?id={catchup-id}",SourceOnly Tv
            http://provider.example/live/sourceonly.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries).hasSize(3)
        assertThat(entries[0].catchupType).isEqualTo(CatchupType.DEFAULT)
        assertThat(entries[0].catchupSource).contains("{Y}")
        assertThat(entries[0].catchupDays).isEqualTo(7)
        assertThat(entries[1].catchupType).isEqualTo(CatchupType.SHIFT)
        assertThat(entries[1].catchupDays).isEqualTo(3)
        // catchup-source without an explicit catchup flag implies DEFAULT (provider shorthand)
        assertThat(entries[2].catchupType).isEqualTo(CatchupType.DEFAULT)
    }

    @Test
    fun parse_xstreamCatchupValue_mapsToXtreamType() {
        val text = """
            #EXTM3U
            #EXTINF:-1 catchup="xc" catchup-days="5",Archive Channel
            http://provider.example/live/archive.ts
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries.single().catchupType).isEqualTo(CatchupType.XTREAM)
        assertThat(entries.single().catchupDays).isEqualTo(5)
    }

    @Test
    fun parse_extGrpFallback_usedWhenNoGroupTitle() {
        val text = """
            #EXTM3U
            #EXTGRP:Documentaries
            #EXTINF:-1 tvg-id="natgeo.us",Nat Geo
            http://provider.example/live/natgeo.m3u8
            #EXTINF:-1 tvg-id="disco.us" group-title="Docs Override",Discovery
            http://provider.example/live/discovery.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries[0].groupTitle).isEqualTo("Documentaries")
        assertThat(entries[1].groupTitle).isEqualTo("Docs Override")
    }

    @Test
    fun parse_skipsKodiAndVlcDirectives() {
        val text = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.manifest_type=hls
            #EXTVLCOPT:http-user-agent=Chrome
            #EXTINF:-1,Kodi Channel
            http://provider.example/live/kodi.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().name).isEqualTo("Kodi Channel")
        assertThat(entries.single().url).endsWith("kodi.m3u8")
    }

    @Test
    fun parse_handlesCrlfBlankLinesAndBom() {
        val text = "\uFEFF#EXTM3U\r\n\r\n#EXTINF:-1,Padded\r\n\r\nhttp://provider.example/x.m3u8\r\n"

        val entries = parser.parse(text)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().url).isEqualTo("http://provider.example/x.m3u8")
    }

    @Test
    fun parse_bareUrlWithoutExtinf_derivesNameFromUrl() {
        val text = """
            #EXTM3U
            http://provider.example/live/plain-channel.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().name).isEqualTo("plain-channel.m3u8")
    }

    @Test
    fun parse_malformedExtinfLines_areSkippedGracefully() {
        val text = """
            #EXTM3U
            #EXTINF:garbage,no-attrs-here
            http://provider.example/live/a.m3u8
            #EXTINF:-1 tvg-logo=unquoted-illegal,Turner
            #EXTINF:-1 tvg-id="dangling" group-title="Orphan",Dangling
        """.trimIndent()

        val entries = parser.parse(text)

        // First EXTINF: no quoted attrs; name still parses; url "a.m3u8" attaches to it.
        // Second EXTINF has an unquoted (illegal) attribute — dropped, name survives.
        // Third EXTINF dangles without a URL at EOF and is discarded.
        assertThat(entries).hasSize(2)
        assertThat(entries[0].url).isEqualTo("http://provider.example/live/a.m3u8")
        assertThat(entries[1].name).isEqualTo("Turner")
        assertThat(entries[1].tvgLogo).isNull()
    }

    @Test
    fun parse_nameContainingComma_parsesAfterLastAttrComma() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="x",News, Sports & Weather
            http://provider.example/live/multi.m3u8
        """.trimIndent()

        val entries = parser.parse(text)

        assertThat(entries.single().name).isEqualTo("News, Sports & Weather")
        assertThat(entries.single().tvgId).isEqualTo("x")
    }

    @Test
    fun parse_10kChannels_withinTimeBudget() {
        val single = """
            #EXTINF:-1 tvg-id="ch" tvg-logo="http://img/l.png" group-title="G",Channel
            http://provider.example/live/channel.m3u8
        """.trimIndent()
        val text = buildString {
            appendLine("#EXTM3U")
            repeat(10_000) { index -> appendLine(single.replace("ch", "ch$index")) }
        }

        val start = System.nanoTime()
        val entries = parser.parse(text)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertThat(entries).hasSize(10_000)
        // Budget is < 5 s on a 2015 phone; CI JVM must be comfortably faster.
        assertThat(elapsedMs).isLessThan(2_000)
    }
}
