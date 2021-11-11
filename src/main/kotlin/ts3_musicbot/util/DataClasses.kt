package ts3_musicbot.util

import kotlinx.coroutines.runBlocking
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.YouTube
import java.time.LocalDate

enum class LinkType {
    SPOTIFY,
    YOUTUBE,
    SOUNDCLOUD,
    OTHER
}

data class Track(
    val album: Album = Album(),
    val artists: Artists = Artists(),
    val title: Name = Name(),
    val link: Link = Link(),
    val playability: Playability = Playability(),
    val likes: Likes = Likes(),
    val linkType: LinkType = link.linkType(),
    val description: Description = Description()
) {
    override fun toString() = "$album\n" +
            if (artists.isNotEmpty()) {
                "Track Artists:\n$artists\n"
            } else {
                ""
            } +
            "Title:      \t\t\t\t$title\n" +
            "Link:       \t\t\t\t$link\n" +
            if (description.isNotEmpty()) {
                "Description:    \t\t$description\n"
            } else {
                ""
            }

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
    enum class Type {
        TRACK, VIDEO, EPISODE,
        ALBUM, PLAYLIST,
        ARTIST,
        USER,
        SHOW,
        OTHER;
    }

    fun getType() = when (type.lowercase()) {
        "track" -> Type.TRACK
        "video" -> Type.VIDEO
        "episode" -> Type.EPISODE
        "album" -> Type.ALBUM
        "playlist" -> Type.PLAYLIST
        "artist" -> Type.ARTIST
        "user" -> Type.USER
        "show", "podcast" -> Type.SHOW
        else -> Type.OTHER
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
}

data class Link(val link: String = "", val linkId: String = "") {
    fun linkType() = when {
        link.contains("spotify") -> LinkType.SPOTIFY
        link.contains("(\\S+youtube\\S+|\\S+youtu.be\\S+)".toRegex()) -> LinkType.YOUTUBE
        link.contains("\\S+soundcloud\\S+".toRegex()) -> LinkType.SOUNDCLOUD
        else -> LinkType.OTHER
    }

    fun getId() = when (linkType()) {
        LinkType.SPOTIFY -> {
            linkId.ifEmpty {
                link.substringAfterLast(":").substringBefore("?si=")
                    .substringAfterLast("/")
            }
        }
        LinkType.YOUTUBE -> {
            linkId.ifEmpty {
                if (link.contains("https?://(www\\.)?youtube\\.com/c/\\S+".toRegex())) {
                    runBlocking { YouTube().resolveChannelId(link.substringAfterLast("/")) }
                } else {
                    link.substringAfterLast("/").substringAfter("?v=").substringBefore("&")
                        .substringAfter("?list=")
                }
            }
        }
        LinkType.SOUNDCLOUD -> {
            runBlocking {
                linkId.ifEmpty {
                    val soundCloud = SoundCloud()
                    if (link.startsWith("${soundCloud.apiURL}/"))
                        link.substringAfterLast("/")
                    else
                        soundCloud.resolveId(Link(link))
                }
            }
        }
        LinkType.OTHER -> ""
    }

    override fun toString() = link

    fun isEmpty() = link.isEmpty()
    fun isNotEmpty() = link.isNotEmpty()
}

data class Description(val text: String = "") {
    override fun toString() = text

    fun isEmpty() = text.isEmpty()
    fun isNotEmpty() = text.isNotEmpty()
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
    override fun toString(): String {
        val strBuilder = StringBuilder()
        if (trackList.isNotEmpty()) {
            trackList.forEach {
                for (artist in it.artists.artists) {
                    strBuilder.append("${artist.name}, ")
                }
                if (it.artists.artists.isNotEmpty()) {
                    strBuilder.delete(strBuilder.length - 2, strBuilder.length - 1)
                    strBuilder.appendLine(" - ${it.title} : ${it.link}")
                }
            }
        }
        return strBuilder.toString()
    }

    fun isEmpty() = trackList.isEmpty()
    fun isNotEmpty() = trackList.isNotEmpty()
}

data class EpisodeList(val episodes: List<Episode> = emptyList()) {
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
        "${if (link.linkType() == LinkType.SPOTIFY) "Artist:\t\t" else "Uploader:"}     \t\t$name\n" +
                "Link:        \t\t\t\t$link\n" +
                if (description.isNotEmpty()) "Description:\n$description" else {
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
            if (name.isNotEmpty()) {
                "Album Name:  \t${name.name}\n"
            } else {
                ""
            } +
            when (link.linkType()) {
                LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> "Upload Date:  \t\t${releaseDate.date}\n"
                LinkType.SPOTIFY -> "Release:    \t\t\t${releaseDate.date}\n"
                else -> "Date:      \t\t\t\t${releaseDate.date}\n"
            } +
            if (link.isNotEmpty()) {
                "Album Link:  \t\t$link\n"
            } else {
                ""
            } +
            if (artists.isNotEmpty()) {
                "Album Artists:\n$artists"
            } else {
                ""
            } +
            if (tracks.trackList.isNotEmpty()) "Tracks:\n$tracks" else ""

    fun isEmpty() = name.isEmpty() && artists.isEmpty() && tracks.isEmpty() && link.isEmpty() && genres.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || artists.isNotEmpty() || tracks.isNotEmpty() || link.isNotEmpty() || genres.isNotEmpty()
}

data class User(
    val name: Name = Name(),
    val userName: Name = Name(),
    val description: Description = Description(),
    val followers: Followers = Followers(),
    val playlists: List<Playlist> = emptyList(),
    val link: Link = Link()
) {
    override fun toString() = "Name: \t\t\t\t\t\t${name.name}\n" +
            "Username: \t\t\t    ${userName.name}\n" +
            if (description.isNotEmpty()) {
                "Description:\n$description\n"
            } else {
                ""
            } +
            if (link.linkType() == LinkType.YOUTUBE) {
                "Subscribers: \t\t    ${followers.amount}\n"
            } else {
                "Followers: \t\t${followers.amount}\n"
            } +
            if (link.linkType() == LinkType.YOUTUBE) {
                "Channel Link: \t\t  ${link.link}\n"
            } else {
                "Link: \t\t\t\t\t\t${link.link}"
            } +
            if (playlists.isNotEmpty()) {
                val listsBuilder = StringBuilder()
                listsBuilder.appendLine((if (playlists.size > 10) "First 10 " else "") + "Playlists:")
                val lists = ArrayList<String>()
                playlists.forEach {
                    if (lists.size < 10)
                        lists.add(it.toString())
                }
                lists.forEach { listsBuilder.appendLine(it) }
                listsBuilder.toString()
            } else {
                ""
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
    val link: Link = Link()
) {
    override fun toString() = "Playlist Name: \t\t${name.name}\n" +
            "Owner:       \t\t\t\t${owner.name}\n" +
            if (description.isNotEmpty()) {
                "Description:\n${description.text}\n"
            } else {
                ""
            } +
            if (link.linkType() == LinkType.YOUTUBE) {
                ""
            } else {
                "Followers:\t\t\t\t${followers.amount}\n"
            } +
            "Is Public:   \t\t\t\t ${publicity.isPublic}\n" +
            if (link.linkType() == LinkType.YOUTUBE) {
                ""
            } else {
                "Is Collaborative: \t${collaboration.isCollaborative}\n"
            } +
            "Link:    \t\t\t\t\t\t${link.link}\n"

    fun isEmpty() = name.isEmpty() && owner.isEmpty() && description.isEmpty() && followers.isEmpty() && link.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || owner.isNotEmpty() || description.isNotEmpty() || followers.isNotEmpty() || link.isNotEmpty()
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
            "This podcast has  ${episodes.episodes.size}  episodes.\n" +
            if (episodes.episodes.isNotEmpty()) {
                "${if (episodes.episodes.size > 10) "First 10 " else ""} Episodes:\n" +
                        "${
                            EpisodeList(
                                episodes.episodes.subList(
                                    0,
                                    if (episodes.episodes.size > 10) 10 else episodes.episodes.size - 1
                                )
                            )
                        }\n" +
                        "Show Link:       \t\t\t\t\t$link"
            } else {
                ""
            }

    fun isEmpty() =
        name.isEmpty() && publisher.isEmpty() && description.isEmpty() && episodes.isEmpty() && link.isEmpty()

    fun isNotEmpty() =
        name.isNotEmpty() || publisher.isNotEmpty() || description.isNotEmpty() || episodes.isNotEmpty() || link.isNotEmpty()
}
