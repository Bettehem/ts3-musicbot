package ts3_musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.util.Link
import ts3_musicbot.util.SearchQuery
import ts3_musicbot.util.SearchType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundCloudTest {
    private val soundCloud = SoundCloud()

    @Test
    fun testUpdatingId() {
        val currentId = soundCloud.clientId
        val newId = soundCloud.updateClientId()
        assert(currentId == newId || newId.contains("^[0-9A-z-_]+$".toRegex()))
    }

    @Test
    fun testSearchingTrack() {
        runBlocking(IO) {
            //SoundCloud link to track: i am leeya - something worth dreaming of (leeyas mashup)
            val testLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
            val result =
                soundCloud.search(SearchType("track"), SearchQuery("leeya something worth dreaming of"))
                    .toString()
            assert(result.contains("Track Link:    \t\t$testLink"))
        }

    }

    @Test
    fun testSearchingPlaylist() {
        runBlocking(IO) {
            val testLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
            val result = soundCloud.search(SearchType("playlist"), SearchQuery("jeesjees")).toString()
            assert(result.contains("Link:     \t\t$testLink"))
        }
    }

    @Test
    fun testGettingTrack() {
        runBlocking(IO) {
            //SoundCloud link to track: i am leeya - something worth dreaming of (leeyas mashup)
            val testLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
            val track = soundCloud.fetchTrack(testLink)
            assertEquals("ajnabiyeh", track.artists.artists[0].name.name)
            assertEquals("something worth dreaming of (leeyas mashup)", track.title.name)
        }
    }

    @Test
    fun testGettingPlaylist() {
        runBlocking(IO) {
            //SoundCloud link to playlist: jeesjees
            val testLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
            val playlist = soundCloud.fetchPlaylist(testLink)
            assertEquals("jeesjees", playlist.name.name)
            assertEquals("Just chill out..", playlist.description.text)
        }
    }

    @Test
    fun testGettingPlaylistTracks() {
        runBlocking(IO) {
            //SoundCloud link to playlist: jeesjees
            val testLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
            val playlist = soundCloud.fetchPlaylistTracks(testLink)
            assertEquals("Louis The Child", playlist.trackList[1].artists.artists[0].name.name)
            assertEquals("Zella Day - Compass (Louis The Child Remix)", playlist.trackList[1].title.name)
        }
    }

    @Test
    fun testGettingUserLikes() {
        runBlocking(IO) {
            //SoundCloud link to user bettehem's likes
            val testLink = Link("https://soundcloud.com/bettehem/likes")
            val likes = soundCloud.fetchUserLikes(testLink)
            assertTrue { likes.isNotEmpty() }
        }
    }

    @Test
    fun testGettingUserReposts() {
        runBlocking(IO) {
            //SoundCloud link to user bettehem's reposts
            val testLink = Link("https://soundcloud.com/bettehem/reposts")
            val reposts = soundCloud.fetchUserReposts(testLink)
            assertTrue { reposts.isNotEmpty() }
        }
    }

    @Test
    fun testGettingAlbum() {
        runBlocking(IO) {
            //SoundCloud link to album: Brute Force
            val testLink = Link("https://soundcloud.com/the-algorithm/sets/brute-force-1")
            val album = soundCloud.fetchAlbum(testLink)
            assertEquals("Brute Force", album.name.name)
            assertEquals("boot", album.tracks.trackList.first().title.name)
            assertEquals("trojans (hard mode)", album.tracks.trackList.last().title.name)
        }
    }

    @Test
    fun testGettingUser() {
        runBlocking(IO) {
            //SoundCloud link to user: bettehem
            val testLink = Link("https://soundcloud.com/bettehem")
            val user = soundCloud.fetchUser(testLink)
            assertEquals("bettehem", user.userName.name)
        }
    }

    @Test
    fun testResolvingNewUrlFormat() {
        runBlocking(IO) {
            //SoundCloud link to track: Episode 130 - Dino Shadix
            val shortLink = Link("https://on.soundcloud.com/2k9V4")
            val normalLink = Link("https://soundcloud.com/emengypodcast/episode-130-dino-shadix")
            val id = shortLink.getId(soundCloud)
            val id2 = normalLink.getId(soundCloud)
            assertEquals("1574693542", id)
            assertEquals(id, id2)
        }
    }
}
