package ts3_musicbot

import ts3_musicbot.services.getYouTubePlaylistTracks
import ts3_musicbot.services.getYouTubeVideoTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeTest {
    @Test
    fun testGettingYouTubeTrack() {
        //YouTube link for track: Phace & Noisia - Non-Responsive
        val testYtLink = "https://youtu.be/IKZnGWxJN3I"
        //You need to have youtube-dl installed for the getTitle function to work.
        val title = getYouTubeVideoTitle(testYtLink)
        assertEquals("Phace & Noisia - Non-Responsive", title)
    }

    @Test
    fun testGettingYouTubePlaylist() {
        //YouTube link for playlist: Sheepy. The playlist length is 536 tracks.
        val testYtLink = "https://www.youtube.com/playlist?list=PLVzaRVhV8EbZY5Y6ylmQSsifzcShleNwZ"
        val playlist = getYouTubePlaylistTracks(testYtLink)
        assert(playlist.size == 536)
    }
}