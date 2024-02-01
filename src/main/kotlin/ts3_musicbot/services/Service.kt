package ts3_musicbot.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ts3_musicbot.util.*
import java.net.URLDecoder
import java.net.URLEncoder

open class Service(val serviceType: ServiceType) {
    enum class ServiceType {
        SOUNDCLOUD,
        SPOTIFY,
        YOUTUBE,
        OTHER
    }

    protected open fun encode(text: String) = runBlocking {
        withContext(Dispatchers.IO) {
            URLEncoder.encode(text, Charsets.UTF_8.toString())
        }
            .replace("'", "&#39;")
            .replace("&", "&amp;")
            .replace("/", "&#x2F;")
    }

    protected open fun decode(text: String) = runBlocking {
        withContext(Dispatchers.IO) {
            URLDecoder.decode(text, Charsets.UTF_8.toString())
        }
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&#x2F;", "/")
    }

    open suspend fun fetchAlbum(albumLink: Link) = Album()
    open suspend fun fetchAlbumTracks(albumLink: Link, limit: Int = 0) = TrackList()
    open suspend fun fetchArtist(artistLink: Link) = Artist()
    open suspend fun fetchPlaylist(playlistLink: Link) = Playlist()
    open suspend fun fetchPlaylistTracks(playlistLink: Link, limit: Int = 0) = TrackList()
    open suspend fun fetchTrack(trackLink: Link) = Track()
    open suspend fun fetchUser(userLink: Link) = User()
    open suspend fun resolveType(link: Link) = LinkType.OTHER
    open suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int = 10,
        encodeQuery: Boolean = true
    ) = SearchResults(
        emptyList()
    )
}