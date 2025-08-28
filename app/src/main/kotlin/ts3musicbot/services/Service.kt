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

enum class ServiceType {
    SOUNDCLOUD,
    SPOTIFY,
    YOUTUBE,
    BANDCAMP,
    APPLE_MUSIC,
    SONGLINK,
    OTHER,
    ;

    override fun toString(): String =
        when (this) {
            SOUNDCLOUD -> "SoundCloud"
            SPOTIFY -> "Spotify"
            YOUTUBE -> "YouTube"
            BANDCAMP -> "Bandcamp"
            APPLE_MUSIC -> "Apple Music"
            SONGLINK -> "SongLink"
            else -> super.toString()
        }
}

val SERVICE_PRIORITY: List<ServiceType> =
    listOf(
        ServiceType.SPOTIFY,
        ServiceType.BANDCAMP,
        ServiceType.SOUNDCLOUD,
        ServiceType.YOUTUBE,
    )

open class Service(
    val serviceType: ServiceType = ServiceType.OTHER,
) {
    protected open fun encode(text: String) =
        runBlocking {
            withContext(Dispatchers.IO) {
                URLEncoder.encode(text, Charsets.UTF_8.toString())
            }.replace("'", "&#39;")
                .replace("&", "&amp;")
                .replace("/", "&#x2F;")
        }

    protected open fun decode(text: String) =
        runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    URLDecoder.decode(text, Charsets.UTF_8.toString())
                }
            } catch (e: Exception) {
                text
                    .replace("&amp;", "&")
                    .replace("%3A", ":")
                    .replace("%3D", "=")
                    .replace("%3F", "?")
                    .replace("%2F", "/")
            }.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
        }

    open suspend fun fetchAlbum(albumLink: Link): Album {
        println("fetchAlbum is not implemented for " + albumLink.serviceType())
        return Album()
    }

    open suspend fun fetchAlbumTracks(
        albumLink: Link,
        limit: Int = 0,
    ): TrackList {
        println("fetchAlbumTracks is not implemented for " + albumLink.serviceType())
        return TrackList()
    }

    open suspend fun fetchArtist(
        artistLink: Link,
        fetchRecommendations: Boolean = true,
    ): Artist {
        println("fetchArtist is not implemented for " + artistLink.serviceType())
        return Artist()
    }

    open suspend fun fetchPlaylist(
        playlistLink: Link,
        shouldFetchTracks: Boolean = false,
    ): Playlist {
        println("fetchPlaylist is not implemented for " + playlistLink.serviceType())
        return Playlist()
    }

    open suspend fun fetchPlaylistTracks(
        playlistLink: Link,
        limit: Int = 0,
    ): TrackList {
        println("fetchPlaylistTracks is not implemented for " + playlistLink.serviceType())
        return TrackList()
    }

    open suspend fun fetchTrack(trackLink: Link): Track {
        println("fetchTrack is not implemented for " + trackLink.serviceType())
        return Track()
    }

    open suspend fun fetchUser(userLink: Link): User {
        println("fetchUser is not implemented for " + userLink.serviceType())
        return User()
    }

    open suspend fun resolveType(link: Link): LinkType {
        println("resolveType is not implemented for " + link.serviceType())
        return LinkType.OTHER
    }

    open suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int = 10,
        encodeQuery: Boolean = true,
    ): SearchResults {
        println("search is not implemented for " + serviceType)
        return SearchResults(emptyList())
    }

    open fun getSupportedSearchTypes() = emptyList<LinkType>()
}
