package ts3_musicbot.util

import ts3_musicbot.services.SoundCloud
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
    val linkType: LinkType = link.linkType()
) {
    override fun toString() = "$album\n" +
            "Track Artists:\n$artists\n" +
            "Title:      \t\t\t\t$title\n" +
            "Link:       \t\t\t\t$link\n"

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
    override fun toString() = type

    fun isEmpty() = type.isEmpty()
    fun isNotEmpty() = type.isNotEmpty()
}

data class SearchQuery(val query: String) {
    override fun toString() = query

    fun isEmpty() = query.isEmpty()
    fun isNotEmpty() = query.isNotEmpty()
}

data class Name(val name: String = "") {
    override fun toString() = name

    fun isEmpty() = name.isEmpty()
    fun isNotEmpty() = name.isNotEmpty()
}

data class Link(val link: String = "") {
    fun linkType() = when {
        link.contains("spotify") -> LinkType.SPOTIFY
        link.contains("(\\S+youtube\\S+|\\S+youtu.be\\S+)".toRegex()) -> LinkType.YOUTUBE
        link.contains("\\S+soundcloud\\S+".toRegex()) -> LinkType.SOUNDCLOUD
        else -> LinkType.OTHER
    }

    fun getId() = when (linkType()) {
        LinkType.SPOTIFY -> {
            link.substringAfterLast(":").substringBefore("?si=")
                .substringAfterLast("/")
        }
        LinkType.YOUTUBE -> {
            link.substringAfterLast("/").substringAfter("?v=").substringBefore("&")
        }
        LinkType.SOUNDCLOUD -> {
            SoundCloud().resolveId(Link(link))
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

data class Publicity(val isPublic: Boolean?)
data class Collaboration(val isCollaborative: Boolean)
data class Playability(val isPlayable: Boolean = false)
data class Followers(val amount: Int = -1) {
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
        artists.forEach { "${strBuilder.appendln(it)}" }
        return strBuilder.toString()
    }

    fun isEmpty() = artists.isEmpty()
    fun isNotEmpty() = artists.isNotEmpty()
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
                    strBuilder.appendln(" - ${it.title} : ${it.link}")
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
                strBuilder.appendln("Episode Name: ${it.name} : ${it.link}")
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
    val name: Name, val link: Link, val topTracks: TrackList = TrackList(emptyList()),
    val relatedArtists: Artists = Artists(ArrayList()), val genres: Genres = Genres(emptyList())
) {
    override fun toString() = "Artist:     \t\t\t\t$name\n" +
            "Link:        \t\t\t\t$link\n" +
            if (genres.genres.isNotEmpty()) "Genres:  \t\t\t\t$genres\n" else {
                ""
            } +
            if (topTracks.trackList.isNotEmpty()) "Top tracks:\n$topTracks\n" else {
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
    override fun toString() = "Album Name:  \t${name.name}\n" +
            when {
                link.link.contains("(youtube|youtu.be|soundcloud)".toRegex()) -> "Upload Date:  \t\t${releaseDate.date}\n"
                link.link.contains("spotify".toRegex()) -> "Release:    \t\t\t${releaseDate.date}\n"
                else -> ""
            } +
            "Album Link:  \t\t$link\n\n" +
            "Album Artists:\n$artists\n" +
            if (tracks.trackList.isNotEmpty()) "Tracks:\n$tracks" else ""

    fun isEmpty() = name.isEmpty() && artists.isEmpty() && tracks.isEmpty() && link.isEmpty() && genres.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || artists.isNotEmpty() || tracks.isNotEmpty() || link.isNotEmpty() || genres.isNotEmpty()
}

data class User(
    val name: Name,
    val userName: Name,
    val followers: Followers,
    val link: Link
) {
    override fun toString() = "Name:  \t\t\t\t${name.name}\n" +
            "Username: \t\t${userName.name}\n" +
            "Followers:  \t\t${followers.amount}\n" +
            "Link:    \t\t\t\t${link.link}"

    fun isEmpty() = name.isEmpty() && userName.isEmpty() && followers.isEmpty() && link.isEmpty()
    fun isNotEmpty() = name.isNotEmpty() || userName.isNotEmpty() || followers.isNotEmpty() || link.isNotEmpty()
}

data class Playlist(
    val name: Name,
    val owner: User,
    val description: Description,
    val followers: Followers,
    val publicity: Publicity,
    val collaboration: Collaboration,
    val link: Link
) {
    override fun toString() = "Playlist Name: \t\t${name.name}\n" +
            "Owner:       \t\t\t\t${owner.name}\n" +
            if (description.isNotEmpty()) {
                "Description:\n${description.text}\n"
            } else {
                ""
            } +
            "Followers:\t\t\t\t${followers.amount}\n" +
            "Is Public:   \t\t\t\t${publicity.isPublic}\n" +
            "Is Collaborative: \t${collaboration.isCollaborative}\n" +
            "Link:    \t\t\t\t\t\t${link.link}"

    fun isEmpty() = name.isEmpty() && owner.isEmpty() && description.isEmpty() && followers.isEmpty() && link.isEmpty()
    fun isNotEmpty() =
        name.isNotEmpty() || owner.isNotEmpty() || description.isNotEmpty() || followers.isNotEmpty() || link.isNotEmpty()
}

data class Show(
    val name: Name,
    val publisher: Publisher,
    val description: Description,
    val episodes: EpisodeList,
    val link: Link
) {
    override fun toString() = "Show Name:  \t\t\t\t$name\n" +
            "Publisher:    \t\t\t\t\t${publisher.name}\n" +
            "Description:\n$description\n\n" +
            "This podcast has  ${episodes.episodes.size}  episodes.\n" +
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

    fun isEmpty() =
        name.isEmpty() && publisher.isEmpty() && description.isEmpty() && episodes.isEmpty() && link.isEmpty()

    fun isNotEmpty() =
        name.isNotEmpty() || publisher.isNotEmpty() || description.isNotEmpty() || episodes.isNotEmpty() || link.isNotEmpty()
}
