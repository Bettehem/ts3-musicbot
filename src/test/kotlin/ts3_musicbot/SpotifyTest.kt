package ts3_musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.*
import ts3_musicbot.util.Link
import ts3_musicbot.util.Name
import kotlin.test.*

class SpotifyTest {
    //Set to your own country
    private val spotifyMarket = "FI"
    private val spotify = Spotify(spotifyMarket).also { runBlocking(IO) { it.updateToken() } }

    @Test
    fun testGettingTrack() {
        runBlocking(IO) {
            //Spotify link to track: SikTh - Peep Show
            val trackLink = Link("https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB")
            val track = spotify.getTrack(trackLink)
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
            val playlist = spotify.getPlaylist(playlistLink)
            assertEquals("Prog", playlist.name.name)
            assertEquals("bettehem", playlist.owner.name.name)
        }
    }

    @Test
    fun testGettingPlaylistTracks() {
        runBlocking(IO) {
            //Spotify link to playlist: Prog
            val playlistLink = Link("https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt")
            val playlistTracks = spotify.getPlaylistTracks(playlistLink)
            assertEquals("Altered State", playlistTracks.trackList[2].album.name.name)
            assertEquals("TesseracT", playlistTracks.trackList[2].artists.artists[0].name.name)
            assertEquals("Of Matter - Resist", playlistTracks.trackList[2].title.name)
        }
    }

    @Test
    fun testGettingAlbum() {
        runBlocking(IO) {
            //Spotify link to album: Destrier
            val albumLink = Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA")
            val album = spotify.getAlbum(albumLink)
            assertEquals("Destrier", album.name.name)
            assert(album.artists.artists.any { it.name == Name("Agent Fresco") })
        }
    }

    @Test
    fun testGettingAlbumTracks() {
        runBlocking(IO) {
            //Spotify link to album: Destrier
            val albumLink = Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA")
            val albumTracks = spotify.getAlbumTracks(albumLink)
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
            val artist = spotify.getArtist(artistLink)
            assertEquals("TesseracT", artist.name.name)
        }
    }

    @Test
    fun testGettingUser() {
        runBlocking(IO) {
            //Spotify link to user: Bettehem
            val userLink = Link("https://open.spotify.com/user/bettehem")
            val user = spotify.getUser(userLink)
            assertEquals("bettehem", user.userName.name)
        }
    }


}
