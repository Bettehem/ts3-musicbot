package ts3_musicbot

import ts3_musicbot.services.YouTube
import ts3_musicbot.util.Link
import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeTest {
    private val youTube = YouTube()
    @Test
    fun testGettingYouTubeTrack() {
        //YouTube link for track: Phace & Noisia - Non-Responsive
        val testYtLink = Link("https://youtu.be/IKZnGWxJN3I")
        //You need to have youtube-dl installed for the getTitle function to work.
        val title = youTube.getVideoTitle(testYtLink)
        assertEquals("Phace & Noisia - Non-Responsive", title)
    }

    @Test
    fun testGettingYouTubePlaylist() {
        //YouTube link for playlist: prog. The playlist length is 5 tracks.
        val testYtLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
        val playlist = youTube.getPlaylistTracks(testYtLink)
        assert(playlist.size == 5)
    }
}
