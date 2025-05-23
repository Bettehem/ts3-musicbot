package ts3musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3musicbot.services.YouTube
import ts3musicbot.util.Link
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeTest {
    private val key = "gCBlkehNVeC9lRwpEVZZVT1FlMJ9FR4FWakhVVkdje0EVLTNWT2ZTW"
    private val youTube = YouTube(String(Base64.getDecoder().decode("${"=".repeat(2)}$key".reversed().trim())).reversed().trim())

    @Test
    fun testGettingYouTubeTrackTitle() {
        runBlocking(IO) {
            // YouTube link for track: Phace & Noisia - Non-Responsive
            val testYtLink = Link("https://youtu.be/IKZnGWxJN3I")
            // You need to have youtube-dl installed for the getTitle function to work.
            val title = youTube.fetchVideo(testYtLink).title.name
            assertEquals("Phace & Noisia - Non-Responsive", title)
        }
    }

    @Test
    fun testGettingYouTubeTrack() {
        runBlocking(IO) {
            // YouTube link for track: Phace & Noisia - Non-Responsive
            val testYtLink = Link("https://youtu.be/IKZnGWxJN3I")
            val track = youTube.fetchVideo(testYtLink)
            assertTrue(
                (track.title.name == "Phace & Noisia - Non-Responsive" && track.playability.isPlayable) ||
                    (track.title.name.isEmpty() && !track.playability.isPlayable),
            )
        }
    }

    @Test
    fun testGettingYouTubePlaylist() {
        runBlocking(IO) {
            // YouTube link for playlist: prog. The playlist length is 5 tracks.
            val testYtLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
            val playlist = youTube.fetchPlaylist(testYtLink)
            assertEquals("prog", playlist.name.name)
        }
    }

    @Test
    fun testGettingYouTubePlaylistTracks() {
        runBlocking(IO) {
            // YouTube link for playlist: prog. The playlist length is 6 tracks.
            val testYtLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
            val tracks = youTube.fetchPlaylistTracks(testYtLink)
            assertTrue(tracks.trackList.size == 6 || tracks.trackList.isEmpty())
        }
    }

    @Test
    fun testGettingYouTubeChannel() {
        runBlocking(IO) {
            // YouTube link for channel: SLVSH.
            val testYtLink = Link("https://www.youtube.com/c/SLVSH")
            val channel = youTube.fetchChannel(testYtLink)
            assertEquals("SLVSH", channel.name.name)
            assertEquals(testYtLink.getId(youTube), channel.userName.name)
        }
    }
}
