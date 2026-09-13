package com.example.tuner.livecheck

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreamProberTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serve(routes: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.path] ?: MockResponse().setResponseCode(404)
        }
    }

    private fun url(path: String) = server.url(path).toString()

    private fun playlist(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.apple.mpegurl")
        .setBody(body)

    private fun segment(code: Int = 206) = MockResponse()
        .setResponseCode(code)
        .setBody(Buffer().write(ByteArray(1024) { 0x47 }))

    @Test
    fun `master to variant to segment with relative uris is working`() = runBlocking {
        serve(
            mapOf(
                "/live/master.m3u8" to playlist("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nhi/index.m3u8\n"),
                "/live/hi/index.m3u8" to playlist("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\n../seg/100.ts\n"),
                "/live/seg/100.ts" to segment()
            )
        )
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/live/master.m3u8")))
    }

    @Test
    fun `media playlist with reachable segment is working`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to segment(code = 200)
            )
        )
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/index.m3u8")))
    }

    @Test
    fun `blocked segment is not working`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to MockResponse().setResponseCode(403)
            )
        )
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/index.m3u8")))
    }

    @Test
    fun `missing playlist is not working`() = runBlocking {
        serve(emptyMap())
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/gone.m3u8")))
    }

    @Test
    fun `playlist without segments is not working`() = runBlocking {
        serve(mapOf("/empty.m3u8" to playlist("#EXTM3U\n#EXT-X-TARGETDURATION:6\n")))
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/empty.m3u8")))
    }

    @Test
    fun `direct non-hls stream with bytes is working`() = runBlocking {
        serve(mapOf("/direct.ts" to segment(code = 200)))
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/direct.ts")))
    }

    @Test
    fun `unresponsive server times out as not working`() = runBlocking {
        serve(mapOf("/slow.m3u8" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)))
        assertEquals(LiveStatus.NOT_WORKING, StreamProber(timeoutMillis = 500).probe(url("/slow.m3u8")))
    }

    @Test
    fun `invalid url is not working`() = runBlocking {
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe("not a url"))
    }

    @Test
    fun `sends vlc user agent and a range request for the segment`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to segment()
            )
        )
        StreamProber().probe(url("/index.m3u8"))
        val playlistRequest = server.takeRequest()
        val segmentRequest = server.takeRequest()
        assertEquals(StreamProber.USER_AGENT, playlistRequest.getHeader("User-Agent"))
        assertEquals(StreamProber.USER_AGENT, segmentRequest.getHeader("User-Agent"))
        assertEquals("bytes=0-1023", segmentRequest.getHeader("Range"))
    }
}
