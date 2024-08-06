package ts3musicbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ts3musicbot.services.Bandcamp
import ts3musicbot.util.Link
import ts3musicbot.util.SearchQuery
import ts3musicbot.util.SearchType
import kotlin.test.Test
import kotlin.test.assertEquals

class BandcampTest {
    private val bandcamp = Bandcamp()

    @Test
    fun testGettingTrack() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to track: Side B from Echo Chamber (2023 Year End Mixtape)
            val testLink = Link("https://visceraandvapor.bandcamp.com/track/side-b-3")
            val track = bandcamp.fetchTrack(testLink)
            assertEquals("https://visceraandvapor.bandcamp.com/album/echo-chamber-2023-year-end-mixtape", track.album.link.link)
            assertEquals("Side B", track.title.name)
        }
    }

    @Test
    fun testGettingTrack2() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to track: Purified by Vengeance (feat. Mark Holcomb of Periphery & Mick Gordon)
            val testLink = Link("https://daathofficial.bandcamp.com/album/the-deceivers#t7")
            val track = bandcamp.fetchTrack(testLink)
            assertEquals("https://daathofficial.bandcamp.com/album/the-deceivers", track.album.link.link)
            assertEquals("Purified by Vengeance (feat. Mark Holcomb of Periphery & Mick Gordon)", track.title.name)
        }
    }

    @Test
    fun testGettingTrack3() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to track: Affinity.exe
            val testLink = Link("https://insideoutmusic.bandcamp.com/track/affinity-exe")
            val track = bandcamp.fetchTrack(testLink)
            assertEquals("haken", track.artists.artists.first().name.name.lowercase())
            assertEquals("Affinity.exe", track.title.name)
            assertEquals("Affinity (Deluxe Edition)", track.album.name.name)
        }
    }

    @Test
    fun testGettingTrack4() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to track: Alan Walker, Ava Max - Alone, Pt. II
            val testLink = Link("https://alanwalkermusic.bandcamp.com/track/alone-pt-ii")
            val track = bandcamp.fetchTrack(testLink)
            assert(
                track.artists.artists.any {
                    it.link.link == "https://alanwalkermusic.bandcamp.com"
                },
            )
        }
    }

    @Test
    fun testGettingAlbum() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to album: Echo Chamber (2023 Year End Mixtape)
            val testLink = Link("https://visceraandvapor.bandcamp.com/album/echo-chamber-2023-year-end-mixtape")
            val album = bandcamp.fetchAlbum(testLink)
            assertEquals("Echo Chamber (2023 Year End Mixtape)", album.name.name)
            assertEquals(2, album.tracks.size)
        }
    }

    @Test
    fun testGettingAlbum2() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to album: Molded By Broken Hands
            val testLink = Link("https://greyskiesfallen.bandcamp.com/album/molded-by-broken-hands")
            val album = bandcamp.fetchAlbum(testLink)
            assertEquals("Molded By Broken Hands", album.name.name)
            assertEquals(7, album.tracks.size)
        }
    }

    @Test
    fun testGettingAlbum3() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to album: War Of Being
            val testLink = Link("https://kscopemusic.bandcamp.com/album/war-of-being")
            val album = bandcamp.fetchAlbum(testLink)
            assertEquals("War Of Being", album.name.name)
            assertEquals("TesseracT", album.artists.artists.first().name.name)
            assertEquals(9, album.tracks.size)
        }
    }

    @Test
    fun testGettingAlbum4() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to album: Chasing Shadows
            val testLink = Link("https://sunnataofficial.bandcamp.com/album/chasing-shadows")
            val album = bandcamp.fetchAlbum(testLink)
            assertEquals("Chasing Shadows", album.name.name)
            assert(album.tracks.trackList[0].playability.isPlayable)
            assert(album.tracks.trackList[1].playability.isPlayable)
            assert(album.tracks.trackList[4].playability.isPlayable)
        }
    }

    @Test
    fun testGettingAlbum5() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to album: Affinity (Deluxe Edition)
            val testLink = Link("https://insideoutmusic.bandcamp.com/album/affinity-deluxe-edition")
            val album = bandcamp.fetchAlbum(testLink)
            assertEquals("Affinity (Deluxe Edition)", album.name.name)
            assertEquals("haken", album.artists.artists.first().name.name.lowercase())
        }
    }

    @Test
    fun testGettingArtist() {
        runBlocking(Dispatchers.IO) {
            // Bandcamp link to artist: dirty river
            val testLink = Link("https://dirty-river.bandcamp.com")
            val artist = bandcamp.fetchArtist(testLink)
            assertEquals("dirty river", artist.name.name)
            assert(artist.albums.albums.first { it.name.name == "dirty river" }.tracks.size == 11)
        }
    }

    @Test
    fun testSearchingAlbum() {
        runBlocking(Dispatchers.IO) {
            // Search on bandcamp for an album with the keywords "tesseract polaris"
            val results = bandcamp.search(SearchType("album"), SearchQuery("tesseract polaris"))
            assert(results.results.any { it.link.link == "https://kscopemusic.bandcamp.com/album/polaris" })
        }
    }

    @Test
    fun testGettingDiscover() {
        runBlocking {
            val testLink = Link("https://bandcamp.com/?g=metal&s=top&p=0&gn=0&f=digital&t=progressive-metal#discover")
            val discover = bandcamp.fetchDiscover(testLink)
            assert(discover.size > 0)
        }
    }

    @Test
    fun testGettingDiscover2() {
        runBlocking {
            val testLink = Link("https://bandcamp.com/discover/metal+progressive-metal/digital")
            val discover = bandcamp.fetchDiscover(testLink)
            assert(discover.size > 0)
        }
    }
}
