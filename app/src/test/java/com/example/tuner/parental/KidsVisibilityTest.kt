package com.example.tuner.parental

import com.example.tuner.data.model.Channel
import com.example.tuner.data.repository.SingleLoadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsVisibilityTest {

    private fun ch(name: String, url: String, source: String = "Kids") =
        Channel(name = name, logoUrl = null, group = "Kids", streamUrl = url, source = source)

    @Test
    fun `visible is catalog plus approved minus hidden, de-duplicated by url`() {
        val catalog = listOf(ch("A", "u/a"), ch("B", "u/b"))
        val approved = listOf(ch("B again", "u/b", source = "All channels"), ch("C", "u/c", source = "All channels"))
        val visible = KidsVisibility.visibleChannels(catalog, approved, hiddenUrls = setOf("u/a"))
        assertEquals(listOf("u/b", "u/c"), visible.map { it.streamUrl })
    }

    @Test
    fun `merge combines both playlists without duplicates`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Success(listOf(ch("A", "u/a"), ch("B", "u/b"))),
            SingleLoadResult.Success(listOf(ch("B", "u/b"), ch("C", "u/c")))
        )
        assertEquals(listOf("u/a", "u/b", "u/c"), (merged as SingleLoadResult.Success).channels.map { it.streamUrl })
    }

    @Test
    fun `merge uses the playlist that loaded when the other fails`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Failure("HTTP 500"),
            SingleLoadResult.Success(listOf(ch("C", "u/c")))
        )
        assertEquals(listOf("u/c"), (merged as SingleLoadResult.Success).channels.map { it.streamUrl })
    }

    @Test
    fun `merge fails only when both fail`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Failure("HTTP 500"),
            SingleLoadResult.Failure("timeout")
        )
        assertTrue(merged is SingleLoadResult.Failure)
    }

    @Test
    fun `hide removes approval and allow removes hidden`() {
        val c = ch("C", "u/c", source = "All channels")
        val hidden = KidsLists(hiddenUrls = emptySet(), approved = listOf(c)).hide(c)
        assertEquals(setOf("u/c"), hidden.hiddenUrls)
        assertTrue(hidden.approved.isEmpty())

        val allowed = hidden.allow(c)
        assertTrue(allowed.hiddenUrls.isEmpty())
        assertEquals(listOf("u/c"), allowed.approved.map { it.streamUrl })
    }

    @Test
    fun `allowing twice does not duplicate and removeApproval clears it`() {
        val c = ch("C", "u/c")
        val lists = KidsLists(emptySet(), emptyList()).allow(c).allow(c)
        assertEquals(1, lists.approved.size)
        assertTrue(lists.removeApproval(c).approved.isEmpty())
    }
}
