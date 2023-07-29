package ts3_musicbot.services

import ts3_musicbot.util.*

open class Service(val serviceType: ServiceType) {

    enum class ServiceType {
        SOUNDCLOUD,
        SPOTIFY,
        YOUTUBE,
        OTHER
    }

    open suspend fun fetchAlbum(albumLink: Link) = Album()
    open suspend fun fetchAlbumTracks(albumLink: Link, limit: Int = 0) = TrackList()
    open suspend fun fetchArtist(artistLink: Link) = Artist()
    open suspend fun fetchPlaylist(playlistLink: Link) = Playlist()
    open suspend fun fetchPlaylistTracks(playlistLink: Link, limit: Int = 0) = TrackList()
    open suspend fun fetchTrack(trackLink: Link) = Track()
    open suspend fun fetchUser(userLink: Link) = User()
    open suspend fun resolveType(link: Link) = LinkType.OTHER
    open suspend fun search(searchType: SearchType, searchQuery: SearchQuery, resultLimit: Int = 10) = SearchResults(
        emptyList()
    )
}