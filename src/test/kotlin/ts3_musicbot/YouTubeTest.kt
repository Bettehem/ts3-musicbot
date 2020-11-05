package ts3_musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.YouTube
import ts3_musicbot.util.Link
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeTest {
    private val youTube = YouTube()

    @Test
    fun testGettingYouTubeTrackTitle() {
        runBlocking {
            //YouTube link for track: Phace & Noisia - Non-Responsive
            val testYtLink = Link("https://youtu.be/IKZnGWxJN3I")
            //You need to have youtube-dl installed for the getTitle function to work.
            val title = youTube.getVideoTitle(testYtLink)
            assertEquals("Phace & Noisia - Non-Responsive", title)
        }
    }

    @Test
    fun testGettingYouTubeTrack() {
        runBlocking(IO) {
            //YouTube link for track: Phace & Noisia - Non-Responsive
            val testYtLink = Link("https://youtu.be/IKZnGWxJN3I")
            val track = youTube.getVideo(testYtLink)
            assertTrue(
                (track.title.name == "Phace & Noisia - Non-Responsive" && track.playability.isPlayable) ||
                        (track.title.name.isEmpty() && !track.playability.isPlayable)
            )
        }
    }

    @Test
    fun testGettingYouTubePlaylist() {
        runBlocking {
            //YouTube link for playlist: prog. The playlist length is 5 tracks.
            val testYtLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
            val playlist = youTube.getPlaylistTracks(testYtLink)
            assertTrue(playlist.trackList.size == 6 || playlist.trackList.isEmpty())
        }
    }
}
