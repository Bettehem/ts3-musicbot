package ts3_musicbot.util

import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.Service
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
import java.lang.IllegalArgumentException
import java.time.LocalDate


enum class LinkType {
    ALBUM,
    ARTIST,
    CHANNEL,
    EPISODE,
    LIKES,
    PLAYLIST,
    REPOSTS,
    SHOW,
    TRACK,
    USER,
    VIDEO,
    OTHER
}

data class Track(
    val album: Album = Album(),
    val artists: Artists = Artists(),
    val title: Name = Name(),
    val link: Link = Link(),
    val playability: Playability = Playability(),
    val likes: Likes = Likes(),
    val serviceType: Service.ServiceType = link.serviceType(),
    val description: Description = Description()
) {
    override fun toString() = "$album\n" +
            artists.ifNotEmpty { "Track Artists:\n$it\n" } +
            "Title:      \t\t\t\t$title\n" +
            "Link:       \t\t\t\t$link\n" +
            description.ifNotEmpty { "Description:\n$it\n" }

    fun toShortString() = "${artists.toShortString()} - $title : $link"

    fun isEmpty() = album.isEmpty() && artists.isEmpty() && title.isEmpty() && link.isEmpty()
    fun isNotEmpty() = album.isNotEmpty() || artists.isNotEmpty() || title.isNotEmpty() || link.isNotEmpty()
}

data class Episode(
    val name: Name = Name(),
    val description: Description = Description(),
    val releaseDate: ReleaseDate = ReleaseDate(),
    val link: Link = Link(),
    val playability: Playability = Playability()
) {
    fun toTrack() = Track(
        title = name,
        link = link,
        playability = playability
    )

    override fun toString() = "Episode Name: \t$name\n" +
            "Release Date: \t\t${releaseDate.date}\n" +
            "Description:\n$description\n" +
            "Link:         \t\t$link"

    fun isEmpty() = name.isEmpty() && description.isEmpty() && link.isEmpty()
    fun isNotEmpty() = name.isNotEmpty() || description.isNotEmpty() || link.isNotEmpty()
}

data class SearchType(val type: String) {

    fun getType() = try {
        LinkType.valueOf(type.uppercase())
    } catch (e: IllegalArgumentException) {
        LinkType.OTHER
    }

    override fun toString() = type
    fun isEmpty() = type.isEmpty()
    fun isNotEmpty() = type.isNotEmpty()
}

data class SearchQuery(val query: String) {
    override fun toString() = query

    fun isEmpty() = query.isEmpty()
    fun isNotEmpty() = query.isNotEmpty()
}

data class SearchResult(val resultText: String, val link: Link) {
    override fun toString() = resultText
}

data class SearchResults(val results: List<SearchResult>) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        results.forEach { strBuilder.appendLine(it) }
        return strBuilder.toString()
    }

    fun isEmpty() = results.isEmpty()
    fun isNotEmpty() = results.isNotEmpty()
}

data class Name(val name: String = "") {
    override fun toString() = name
    fun isEmpty() = name.isEmpty()
    fun isNotEmpty() = name.isNotEmpty()
    fun ifNotEmpty(fn: (name: Name) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class Link(val link: String = "", val linkId: String = "") {
    fun serviceType() = when {
        link.contains("\\S+soundcloud\\S+".toRegex()) -> Service.ServiceType.SOUNDCLOUD
        link.contains("spotify") -> Service.ServiceType.SPOTIFY
        link.contains("\\S+youtu\\.?be\\S+".toRegex()) -> Service.ServiceType.YOUTUBE
        else -> Service.ServiceType.OTHER
    }

    /** Get the link's LinkType.
     * @param service Optionally provide a service class to be used.
     *                     If none provided, a new instance will be created.
     * @return Returns the link's LinkType
     */
    fun linkType(service: Service = Service(serviceType())) = when (service.serviceType) {
        Service.ServiceType.SOUNDCLOUD -> {
            runBlocking {
                if (service is SoundCloud)
                    service.resolveType(this@Link)
                else
                    SoundCloud().resolveType(this@Link)
            }
        }

        Service.ServiceType.SPOTIFY -> {
            runBlocking {
                if (service is Spotify)
                    service.resolveType(this@Link)
                else
                    Spotify().resolveType(this@Link)
            }
        }

        Service.ServiceType.YOUTUBE -> {
            runBlocking {
                if (service is YouTube)
                    service.resolveType(this@Link)
                else
                    YouTube().resolveType(this@Link)
            }
        }

        else -> LinkType.OTHER
    }

    fun getId(service: Service = Service(serviceType())) = linkId.ifEmpty {
        when (service.serviceType) {
            Service.ServiceType.SPOTIFY -> {
                if (link.contains("(https?://)?spotify(\\.app)?\\.link/\\S+".toRegex())) {
                    if (service is Spotify)
                        service.resolveId(this@Link)
                    else
                        Spotify().resolveId(this@Link)
                } else {
                    link.substringAfterLast(":").substringBefore("?")
                        .substringAfterLast("/")
                }
            }

            Service.ServiceType.YOUTUBE -> {
                if (linkType(service) == LinkType.CHANNEL) {
                    runBlocking {
                        if (service is YouTube)
                            service.resolveChannelId(this@Link)
                        else
                            YouTube().resolveChannelId(this@Link)
                    }
                } else {
                    val idFrom = when (linkType(service)) {
                        LinkType.VIDEO -> "(vi?)"
                        LinkType.PLAYLIST -> "(list)"
                        else -> ""
                    }
                    link.substringAfterLast("/")
                        .replace("(\\w*\\?\\S*$idFrom=)?".toRegex(), "")
                        .replace("[&?]\\S+".toRegex(), "")
                }
            }

            Service.ServiceType.SOUNDCLOUD -> {
                runBlocking {
                    val soundCloud = if (service is SoundCloud) service else SoundCloud()
                    if (link.startsWith("${soundCloud.apiURL}/"))
                        clean(soundCloud).link.substringAfterLast("/")
                    else
                        soundCloud.resolveId(this@Link)
                }
            }

            Service.ServiceType.OTHER -> ""
        }
    }

    /**
     * Clean junk from given link
     * @return returns a cleaned link or the same link if no cleaning can be done.
     */
    fun clean(service: Service = Service(serviceType())): Link = when (service.serviceType) {
        Service.ServiceType.SPOTIFY -> Link(
            "https://open.spotify.com/" + linkType(service).name.lowercase() + "/" + getId(service)
        )

        Service.ServiceType.YOUTUBE -> Link(
            when (linkType(service)) {
                LinkType.VIDEO -> "https://youtu.be/" + getId(service)
                LinkType.PLAYLIST -> "https://www.youtube.com/playlist?list=" + getId(service)
                else -> link
            }
        )

        Service.ServiceType.SOUNDCLOUD -> Link(link.substringBefore("?"))
        else -> this
    }

    override fun toString() = link

    fun isEmpty() = link.isEmpty()
    fun isNotEmpty() = link.isNotEmpty()
    fun ifNotEmpty(fn: (link: Link) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class Description(val text: String = "") {
    override fun toString() = text
    fun lines() = text.lines()
    fun isEmpty() = text.isEmpty()
    fun isNotEmpty() = text.isNotEmpty()
    fun ifNotEmpty(fn: (description: Description) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class Publicity(val isPublic: Boolean? = false)
data class Collaboration(val isCollaborative: Boolean = false)
data class Playability(val isPlayable: Boolean = false)
data class Followers(val amount: Int = -1) {
    override fun toString() = amount.toString()
    fun isEmpty() = amount == -1
    fun isNotEmpty() = amount != -1
}

data class Likes(val amount: Int = -1) {
    override fun toString() = amount.toString()
    fun isEmpty() = amount == -1
    fun isNotEmpty() = amount != -1
}

data class Publisher(val name: Name = Name()) {
    override fun toString() = name.name
    fun isEmpty() = name.isEmpty()
    fun isNotEmpty() = name.isNotEmpty()
}

data class Artists(val artists: List<Artist> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        artists.forEach { "${strBuilder.appendLine(it)}" }
        return strBuilder.toString()
    }

    fun toShortString(): String {
        val strBuilder = StringBuilder()
        artists.forEach { strBuilder.append("${it.name}, ") }
        return strBuilder.toString().substringBeforeLast(",")
    }

    fun isEmpty() = artists.isEmpty()
    fun isNotEmpty() = artists.isNotEmpty()
    fun ifNotEmpty(fn: (artists: Artists) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class Albums(val albums: List<Album> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        albums.forEach { "${strBuilder.appendLine(it)}" }
        return strBuilder.toString()
    }

    fun isEmpty() = albums.isEmpty()
    fun isNotEmpty() = albums.isNotEmpty()
}

data class TrackList(val trackList: List<Track> = emptyList()) {
    val size = trackList.size

    override fun toString(): String {
        val strBuilder = StringBuilder()
        if (trackList.isNotEmpty()) {
            trackList.forEachIndexed { i, track ->
                strBuilder.append("${i + 1}: ")
                for (artist in track.artists.artists) {
                    strBuilder.append("${artist.name}, ")
                }
                if (track.artists.artists.isNotEmpty()) {
                    strBuilder.delete(strBuilder.length - 2, strBuilder.length - 1)
                    strBuilder.appendLine(" - ${track.title} : ${track.link}")
                }
            }
        }
        return strBuilder.toString()
    }

    fun shuffled() = TrackList(trackList.shuffled())
    fun reversed() = TrackList(trackList.reversed())

    fun isEmpty() = trackList.isEmpty()
    fun isNotEmpty() = trackList.isNotEmpty()
    fun ifNotEmpty(fn: (tracks: TrackList) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class EpisodeList(val episodes: List<Episode> = emptyList()) {
    val size = episodes.size
    fun toTrackList() = TrackList(episodes.map {
        Track(
            title = it.name,
            link = it.link,
            playability = it.playability
        )
    })

    override fun toString(): String {
        val strBuilder = StringBuilder()
        if (episodes.isNotEmpty()) {
            episodes.forEach {
                strBuilder.appendLine("Episode Name: ${it.name} : ${it.link}")
            }
        }
        return strBuilder.toString()
    }

    fun isEmpty() = episodes.isEmpty()
    fun isNotEmpty() = episodes.isNotEmpty()
    fun ifNotEmpty(fn: (episodes: EpisodeList) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class ReleaseDate(val date: LocalDate = LocalDate.now())

data class Genres(val genres: List<String> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        genres.forEach { strBuilder.append("$it, ") }
        return strBuilder.toString().substringBeforeLast(",")
    }

    fun isEmpty() = genres.isEmpty()
    fun isNotEmpty() = genres.isNotEmpty()
}

data class Artist(
    val name: Name = Name(), val link: Link = Link(), val topTracks: TrackList = TrackList(emptyList()),
    val relatedArtists: Artists = Artists(ArrayList()), val genres: Genres = Genres(emptyList()),
    val followers: Followers = Followers(), val description: Description = Description(),
    val albums: Albums = Albums()
) {
    override fun toString() =
        "${if (link.serviceType() == Service.ServiceType.YOUTUBE) "Uploader:" else "Artist:\t\t"}     \t\t$name\n" +
                "Link:        \t\t\t\t$link\n" +
                if (description.isNotEmpty()) "Description:\n$description\n" else {
                    ""
                } +
                if (genres.genres.isNotEmpty()) "Genres:  \t\t\t\t$genres\n" else {
                    ""
                } +
                if (topTracks.trackList.isNotEmpty()) "Top tracks:\n$topTracks\n" else {
                    ""
                } +
                if (albums.isNotEmpty()) "Albums:\n${
                    Albums(
                        albums.albums.subList(
                            0,
                            if (albums.albums.size >= 10) 9 else albums.albums.lastIndex
                        )
                    )
                }\n" else {
                    ""
                } +
                if (relatedArtists.artists.isNotEmpty()) "Related artists:\n$relatedArtists" else ""
}

data class Album(
    val name: Name = Name(),
    val artists: Artists = Artists(),
    val releaseDate: ReleaseDate = ReleaseDate(),
    val tracks: TrackList = TrackList(),
    val link: Link = Link(),
    val genres: Genres = Genres()
) {
    override fun toString() = "" +
            name.ifNotEmpty { "Album Name:  \t${name.name}\n" } +
            when (link.serviceType()) {
                Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> "Upload Date:  \t\t${releaseDate.date}\n"
                Service.ServiceType.SPOTIFY -> "Release:    \t\t\t${releaseDate.date}\n"
                else -> "Date:      \t\t\t\t${releaseDate.date}\n"
            } +
            link.ifNotEmpty { "Album Link:  \t\t$link\n" } +
            artists.ifNotEmpty { "Album Artists:\n$artists" } +
            tracks.ifNotEmpty { "Tracks:\n$tracks" }

    fun isEmpty() = name.isEmpty() && artists.isEmpty() && tracks.isEmpty() && link.isEmpty() && genres.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || artists.isNotEmpty() || tracks.isNotEmpty() || link.isNotEmpty() || genres.isNotEmpty()
}

data class User(
    val name: Name = Name(),
    val userName: Name = Name(),
    val description: Description = Description(),
    val followers: Followers = Followers(),
    val playlists: Playlists = Playlists(),
    val link: Link = Link()
) {
    override fun toString() = "Name: \t\t\t\t\t\t${name.name}\n" +
            "Username: \t\t\t    ${userName.name}\n" +
            if (description.isNotEmpty()) {
                "Description:\n$description\n"
            } else {
                ""
            } +
            if (link.serviceType() == Service.ServiceType.YOUTUBE) {
                "Subscribers: \t\t    ${followers.amount}\n"
            } else {
                "Followers: \t\t${followers.amount}\n"
            } +
            if (link.serviceType() == Service.ServiceType.YOUTUBE) {
                "Channel Link: \t\t  ${link.link}\n"
            } else {
                "Link: \t\t\t\t\t\t${link.link}"
            } +
            playlists.ifNotEmpty {
                val listsBuilder = StringBuilder()
                listsBuilder.appendLine((if (it.size > 10) "First 10 " else "") + "Playlists:")
                val lists = ArrayList<String>()
                it.lists.forEach { list ->
                    if (lists.size < 10)
                        lists.add(list.toString())
                }
                lists.forEach { list -> listsBuilder.appendLine(list) }
                listsBuilder.toString()
            }

    fun isEmpty() = name.isEmpty() && userName.isEmpty() && followers.isEmpty() && link.isEmpty()
    fun isNotEmpty() = name.isNotEmpty() || userName.isNotEmpty() || followers.isNotEmpty() || link.isNotEmpty()
}

data class Playlist(
    val name: Name = Name(),
    val owner: User = User(),
    val description: Description = Description(),
    val followers: Followers = Followers(),
    val publicity: Publicity = Publicity(),
    val collaboration: Collaboration = Collaboration(),
    val tracks: TrackList = TrackList(),
    val link: Link = Link()
) {
    override fun toString() = "Playlist Name: \t\t${name.name}\n" +
            "Owner:       \t\t\t\t${owner.name}\n" +
            description.ifNotEmpty {
                "Description:\n${description.text}\n"
            } +
            if (link.serviceType() == Service.ServiceType.YOUTUBE) {
                ""
            } else {
                "Followers:\t\t\t\t${followers.amount}\n"
            } +
            "Is Public:   \t\t\t\t ${publicity.isPublic}\n" +
            if (link.serviceType() == Service.ServiceType.YOUTUBE) {
                ""
            } else {
                "Is Collaborative: \t${collaboration.isCollaborative}\n"
            } +
            "Tracks:   \t\t\t\t\t${tracks.size}\n" +
            "Link:    \t\t\t\t\t\t${link.link}\n"

    fun isEmpty() = name.isEmpty() && owner.isEmpty() && description.isEmpty() && followers.isEmpty() && link.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || owner.isNotEmpty() || description.isNotEmpty() || followers.isNotEmpty() || link.isNotEmpty()
}

data class Playlists(val lists: List<Playlist> = emptyList()) {
    val size = lists.size
    fun isEmpty() = lists.isEmpty()
    fun isNotEmpty() = lists.isNotEmpty()
    fun ifNotEmpty(fn: (lists: Playlists) -> Any) = if (isNotEmpty()) fn(this) else this
}

data class Show(
    val name: Name = Name(),
    val publisher: Publisher = Publisher(),
    val description: Description = Description(),
    val episodes: EpisodeList = EpisodeList(),
    val link: Link = Link()
) {
    override fun toString() = "Show Name:  \t\t\t\t$name\n" +
            "Publisher:    \t\t\t\t\t${publisher.name}\n" +
            "Description:\n$description\n\n" +
            "This podcast has ${episodes.size} episodes.\n" +
            episodes.ifNotEmpty {
                "${if (it.size > 10) "First 10 " else ""} Episodes:\n" +
                        "${EpisodeList(it.episodes.subList(0, if (it.size > 10) 10 else it.size - 1))}\n" +
                        "Show Link:       \t\t\t\t\t$link"
            }

    fun isEmpty() =
        name.isEmpty() && publisher.isEmpty() && description.isEmpty() && episodes.isEmpty() && link.isEmpty()

    fun isNotEmpty() =
        name.isNotEmpty() || publisher.isNotEmpty() || description.isNotEmpty() || episodes.isNotEmpty() || link.isNotEmpty()
}
