package ts3_musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.Spotify
import ts3_musicbot.util.Link
import ts3_musicbot.util.Name
import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyTest {
    //Set to your own country
    private val spotifyMarket = "US"
    private val spotify = Spotify(spotifyMarket).also { runBlocking(IO) { it.updateToken() } }

    @Test
    fun testGettingTrack() {
        runBlocking(IO) {
            //Spotify link to track: SikTh - Peep Show
            val trackLink = Link("https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB")
            val track = spotify.fetchTrack(trackLink)
            assertEquals("The Trees Are Dead & Dried Out, Wait for Something Wild", track.album.name.name)
            assertEquals("SikTh", track.artists.artists[0].name.name)
            assertEquals("Peep Show", track.title.toString())
        }
    }

    @Test
    fun testGettingPlaylist() {
        runBlocking(IO) {
            //spotify link to playlist: Prog
            val playlistLink = Link("https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt")
            val playlist = spotify.fetchPlaylist(playlistLink)
            assertEquals("Prog", playlist.name.name)
            assertEquals("bettehem", playlist.owner.name.name)
        }
    }

    @Test
    fun testGettingPlaylistTracks() {
        runBlocking(IO) {
            //Spotify link to playlist: Prog
            val playlistLink = Link("https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt")
            val playlistTracks = spotify.fetchPlaylistTracks(playlistLink)
            assertEquals("Altered State", playlistTracks.trackList[2].album.name.name)
            assertEquals("TesseracT", playlistTracks.trackList[2].artists.artists[0].name.name)
            assert(
                playlistTracks.trackList[2].title.name.startsWith("Of Matter")
                        && playlistTracks.trackList[2].title.name.endsWith("Resist")
            )
        }
    }

    @Test
    fun testGettingAlbum() {
        runBlocking(IO) {
            //Spotify link to album: Destrier
            val albumLink = Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA")
            val album = spotify.fetchAlbum(albumLink)
            assertEquals("Destrier", album.name.name)
            assert(album.artists.artists.any { it.name == Name("Agent Fresco") })
        }
    }

    @Test
    fun testGettingAlbumTracks() {
        runBlocking(IO) {
            //Spotify link to album: Destrier
            val albumLink = Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA")
            val albumTracks = spotify.fetchAlbumTracks(albumLink)
            assertEquals("Destrier", albumTracks.trackList[0].album.name.name)
            assertEquals("Agent Fresco", albumTracks.trackList[0].artists.artists[0].name.name)
            assertEquals("Let Them See Us", albumTracks.trackList[0].title.name)
        }
    }

    @Test
    fun testGettingArtist() {
        runBlocking(IO) {
            //Spotify link to artist: TesseracT
            val artistLink = Link("https://open.spotify.com/artist/23ytwhG1pzX6DIVWRWvW1r?si=GNhxGVI_To-4z5doq2PE7A")
            val artist = spotify.fetchArtist(artistLink)
            assertEquals("TesseracT", artist.name.name)
        }
    }

    @Test
    fun testGettingUser() {
        runBlocking(IO) {
            //Spotify link to user: Bettehem
            val userLink = Link("https://open.spotify.com/user/bettehem")
            val user = spotify.fetchUser(userLink)
            assertEquals("bettehem", user.userName.name)
        }
    }

    @Test
    fun testGettingShow() {
        runBlocking(IO) {
            //spotify link to show: Ascension with Simon Dumont
            val showLink = Link("https://open.spotify.com/show/7kTddVIBh6IMGiwDHpj3zL")
            val show = spotify.fetchShow(showLink)
            assertEquals("Ascension with Simon Dumont", show.name.name)
            assertEquals("The Pod Mill", show.publisher.name.name)
        }
    }

    @Test
    fun testGettingEpisode() {
        runBlocking(IO) {
            //spotify link to episode 1 from show Ascension with Simon Dumont
            val episodeLink = Link("https://open.spotify.com/episode/3tR0C41CUqAZfA7f0eek9L")
            val episode = spotify.fetchEpisode(episodeLink)
            assertEquals("Ep. 1 Tom Wallisch", episode.name.name)
            assertEquals(
                "Fresh off of their \"Resurrection\" edit reboot, the God-Fathers of ski style return. " +
                        "Simon Dumont is launching his new show and bringing the crew along with him. " +
                        "Tom Wallisch slides into the studio with Simon in order to quickly go over the past and look forward to the future of skiing.",
                episode.description.text
            )
        }
    }

    @Test
    fun testResolvingNewUrlFormat() {
        runBlocking(IO) {
            //spotify link to track: Tesseract - War of Being
            val link = Link("https://spotify.link/FwzVyxos3Cb")
            val link2 = Link("https://spotify.app.link/FwzVyxos3Cb?_p=c91529c39d067af2e31d90fceb")
            val id = link.getId(spotify)
            val id2 = link2.getId(spotify)
            assertEquals("0vdffbHjc0qSOOoI2d71OP", id)
            assertEquals(id, id2)
        }
    }
}
