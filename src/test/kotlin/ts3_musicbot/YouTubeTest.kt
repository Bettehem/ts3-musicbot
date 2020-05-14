package ts3_musicbot

import ts3_musicbot.services.YouTube
import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeTest {
    @Test
    fun testGettingYouTubeTrack(){
        //YouTube link for track: Phace & Noisia - Non-Responsive
        val testYtLink = "https://youtu.be/IKZnGWxJN3I"
        //You need to have youtube-dl installed for the getTitle function to work.
        val title = YouTube().getTitle(testYtLink)
        assertEquals("Phace & Noisia - Non-Responsive", title)
    }

    @Test
    fun testGettingYouTubePlaylist(){
        //YouTube link for playlist: Sheepy. The playlist length is 536 tracks.
        val testYtLink = "https://www.youtube.com/playlist?list=PLVzaRVhV8EbZY5Y6ylmQSsifzcShleNwZ"
        val playlist = YouTube().getPlaylistTracks(testYtLink)
        assert(playlist.size == 536)
    }
}