package ts3_musicbot

import ts3_musicbot.services.SoundCloud
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundCloudTest {
    private val soundCloud = SoundCloud()

    @Test
    fun testGettingTrack() {
        //SoundCloud link to track: i am leeya - something worth dreaming of (leeyas mashup)
        val testLink = "https://soundcloud.com/iamleeya/something-worth-dreaming-of"
        val track = soundCloud.getTrack(testLink)
        assertEquals("i am leeya", track.artist)
        assertEquals("something worth dreaming of (leeyas mashup)", track.title)
    }

    @Test
    fun testGettingPlaylist() {
        //SoundCloud link to playlist: jeesjees
        val testLink = "https://soundcloud.com/bettehem/sets/jeesjees"
        val playlist = soundCloud.getPlaylistTracks(testLink)
        assertEquals("Louis The Child", playlist[1].artist)
        assertEquals("Zella Day - Compass (Louis The Child Remix)", playlist[1].title)
    }
}