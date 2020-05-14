package ts3_musicbot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import ts3_musicbot.services.Spotify
import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyTest {
    //Set to your own country
    private val spotifyMarket = "FI"
    private val spotify = Spotify(spotifyMarket).also { CoroutineScope(IO).launch{it.updateToken()} }

    @Test
    fun testGettingTrack() {
        CoroutineScope(IO).launch{
            //Spotify link to track: SikTh - Peep Show
            val testTrackLink = "https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB"
            val track = spotify.getTrack(testTrackLink)
            assertEquals("The Trees Are Dead & Dried Out, Wait for Something Wild", track.album)
            assertEquals("SikTh", track.artist)
            assertEquals("Peep Show", track.title)
        }
    }

    @Test
    fun testGettingPlaylistTracks() {
        CoroutineScope(IO).launch {
            //Spotify link to playlist: Prog
            val testPlaylistLink = "https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt"
            val playlistTracks = spotify.getPlaylistTracks(testPlaylistLink)
            assertEquals("Altered State", playlistTracks[2].album)
            assertEquals("TesseracT", playlistTracks[2].artist)
            assertEquals("Of Matter - Resist", playlistTracks[2].title)
        }
    }

    @Test
    fun testGettingAlbum(){
        CoroutineScope(IO).launch {
            //Spotify link to album: Destrier
            val testAlbumLink = "https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA"
            val albumTracks = spotify.getAlbumTracks(testAlbumLink)
            assertEquals("Destrier", albumTracks[0].album)
            assertEquals("Agent Fresco", albumTracks[0].artist)
            assertEquals("Let Them See Us", albumTracks[0].title)
        }
    }
}