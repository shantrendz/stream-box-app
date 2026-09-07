package com.example.tuner

import com.example.tuner.data.parser.M3UParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3UParserTest {

    @Test
    fun `parses standard EXTINF entries with attributes`() {
        val raw = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="https://logos.example/bbc1.png" group-title="News",BBC One
            https://stream.example.com/bbc1.m3u8
            #EXTINF:-1 tvg-id="cnn.us" tvg-logo="https://logos.example/cnn.png" group-title="News",CNN
            https://stream.example.com/cnn.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(raw, "Region: UK")

        assertEquals(2, channels.size)
        assertEquals("BBC One", channels[0].name)
        assertEquals("News", channels[0].group)
        assertEquals("https://stream.example.com/bbc1.m3u8", channels[0].streamUrl)
        assertEquals("https://logos.example/bbc1.png", channels[0].logoUrl)
        assertEquals("Region: UK", channels[0].source)
        assertEquals("CNN", channels[1].name)
    }

    @Test
    fun `missing group-title falls back to Uncategorized`() {
        val raw = """
            #EXTINF:-1 tvg-id="x",Mystery Channel
            https://stream.example.com/x.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(raw, "Category: General")

        assertEquals(1, channels.size)
        assertEquals("Uncategorized", channels[0].group)
    }

    @Test
    fun `bare stream URL with no EXTINF metadata becomes one channel named after source`() {
        val raw = "https://stream.example.com/direct.m3u8"

        val channels = M3UParser.parse(raw, "My Provider")

        assertEquals(1, channels.size)
        assertEquals("My Provider", channels[0].name)
        assertEquals("My Provider", channels[0].source)
        assertEquals("https://stream.example.com/direct.m3u8", channels[0].streamUrl)
        assertNull(channels[0].logoUrl)
    }

    @Test
    fun `empty or unparseable content yields no channels`() {
        assertTrue(M3UParser.parse("", "Empty Source").isEmpty())
        assertTrue(M3UParser.parse("not a playlist at all", "Junk Source").isEmpty())
    }

    @Test
    fun `EXTINF with no comma name falls back to tvg-name`() {
        val raw = """
            #EXTINF:-1 tvg-name="Fallback Name" group-title="Kids"
            https://stream.example.com/kids.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(raw, "Category: Kids")

        assertEquals(1, channels.size)
        assertEquals("Fallback Name", channels[0].name)
    }

    @Test
    fun `ignores unrelated tag lines like EXTGRP`() {
        val raw = """
            #EXTM3U
            #EXTINF:-1 group-title="Sports",ESPN
            #EXTGRP:Sports
            https://stream.example.com/espn.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(raw, "Region: US")

        assertEquals(1, channels.size)
        assertEquals("ESPN", channels[0].name)
    }

    @Test
    fun `every channel keeps the originating source label`() {
        val raw = """
            #EXTINF:-1 group-title="Movies",Movie Channel
            https://stream.example.com/movies.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(raw, "Office IPTV")

        assertEquals("Office IPTV", channels[0].source)
    }
}
