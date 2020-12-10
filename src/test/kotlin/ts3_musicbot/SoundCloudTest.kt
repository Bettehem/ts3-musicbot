package ts3_musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.util.Link
import ts3_musicbot.util.SearchQuery
import ts3_musicbot.util.SearchType
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundCloudTest {
    private val soundCloud = SoundCloud()

    @Test
    fun testUpdatingId() {
        val currentId = soundCloud.clientId
        val newId = soundCloud.updateClientId()
        assert(currentId == newId || newId.contains("^[a-zA-Z0-9]+$".toRegex()))
    }

    @Test
    fun testSearchingTrack() {
        runBlocking(IO) {
            //SoundCloud link to track: i am leeya - something worth dreaming of (leeyas mashup)
            val testLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
            val result =
                soundCloud.searchSoundCloud(SearchType("track"), SearchQuery("leeya something worth dreaming of"))
                    .toString()
            assert(result.contains("Track Link:    \t\t$testLink"))
        }

    }

    @Test
    fun testSearchingPlaylist() {
        runBlocking(IO) {
            val testLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
            val result = soundCloud.searchSoundCloud(SearchType("playlist"), SearchQuery("jeesjees")).toString()
            assert(result.contains("Link:     \t\t$testLink"))
        }
    }

    @Test
    fun testGettingTrack() {
        //SoundCloud link to track: i am leeya - something worth dreaming of (leeyas mashup)
        val testLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
        val track = soundCloud.getTrack(testLink)
        assertEquals("i am leeya", track.artists.artists[0].name.name)
        assertEquals("something worth dreaming of (leeyas mashup)", track.title.name)
    }

    @Test
    fun testGettingPlaylist() {
        //SoundCloud link to playlist: jeesjees
        val testLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
        val playlist = soundCloud.getPlaylistTracks(testLink)
        assertEquals("Louis The Child", playlist[1].artists.artists[0].name.name)
        assertEquals("Zella Day - Compass (Louis The Child Remix)", playlist[1].title.name)
    }
}