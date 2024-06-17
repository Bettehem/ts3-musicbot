package ts3musicbot.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ts3musicbot.util.Album
import ts3musicbot.util.Artist
import ts3musicbot.util.Link
import ts3musicbot.util.LinkType
import ts3musicbot.util.Playlist
import ts3musicbot.util.SearchQuery
import ts3musicbot.util.SearchResults
import ts3musicbot.util.SearchType
import ts3musicbot.util.Track
import ts3musicbot.util.TrackList
import ts3musicbot.util.User
import java.net.URLDecoder
import java.net.URLEncoder

open class Service(val serviceType: ServiceType) {
    enum class ServiceType {
        SOUNDCLOUD,
        SPOTIFY,
        YOUTUBE,
        BANDCAMP,
        OTHER,
        ;

        override fun toString(): String {
            return when (this) {
                SOUNDCLOUD -> "SoundCloud"
                SPOTIFY -> "Spotify"
                YOUTUBE -> "YouTube"
                BANDCAMP -> "Bandcamp"
                else -> super.toString()
            }
        }
    }

    protected open fun encode(text: String) =
        runBlocking {
            withContext(Dispatchers.IO) {
                URLEncoder.encode(text, Charsets.UTF_8.toString())
            }
                .replace("'", "&#39;")
                .replace("&", "&amp;")
                .replace("/", "&#x2F;")
        }

    protected open fun decode(text: String) =
        runBlocking {
            withContext(Dispatchers.IO) {
                URLDecoder.decode(text, Charsets.UTF_8.toString())
            }
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
        }

    open suspend fun fetchAlbum(albumLink: Link) = Album()

    open suspend fun fetchAlbumTracks(
        albumLink: Link,
        limit: Int = 0,
    ) = TrackList()

    open suspend fun fetchArtist(
        artistLink: Link,
        fetchRecommendations: Boolean = true,
    ) = Artist()

    open suspend fun fetchPlaylist(
        playlistLink: Link,
        shouldFetchTracks: Boolean = false,
    ) = Playlist()

    open suspend fun fetchPlaylistTracks(
        playlistLink: Link,
        limit: Int = 0,
    ) = TrackList()

    open suspend fun fetchTrack(trackLink: Link) = Track()

    open suspend fun fetchUser(userLink: Link) = User()

    open suspend fun resolveType(link: Link) = LinkType.OTHER

    open suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int = 10,
        encodeQuery: Boolean = true,
    ) = SearchResults(
        emptyList(),
    )
}
