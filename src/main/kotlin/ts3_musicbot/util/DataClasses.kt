package ts3_musicbot.util

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
    override fun toString(): String {
        return "$album\n" +
                "Track Artists:\n$artists\n" +
                "Title:      \t\t\t\t$title\n" +
                "Link:       \t\t\t\t$link\n"
    }
}

data class SearchType(val type: String) {
    override fun toString(): String {
        return type
    }
}

data class SearchQuery(val query: String) {
    override fun toString(): String {
        return query
    }
}

data class Name(val name: String = "") {
    override fun toString(): String {
        return name
    }
}

data class Link(val link: String = "") {
    fun linkType(): LinkType {
        return when {
            link.contains("\\S+spotify\\S+".toRegex()) -> LinkType.SPOTIFY
            link.contains("(\\S+youtube\\S+|\\S+youtu.be\\S+)".toRegex()) -> LinkType.YOUTUBE
            link.contains("\\S+soundcloud\\S+".toRegex()) -> LinkType.SOUNDCLOUD
            else -> LinkType.OTHER
        }
    }

    override fun toString(): String {
        return link
    }
}

data class Description(val text: String) {
    override fun toString(): String {
        return text
    }
}

data class Publicity(val isPublic: Boolean?)
data class Collaboration(val isCollaborative: Boolean)
data class Playability(val isPlayable: Boolean = false)
data class Followers(val amount: Int)

data class Artists(val artists: List<Artist> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        artists.forEach { "${strBuilder.appendln(it)}" }
        return strBuilder.toString()
    }
}

data class TrackList(val trackList: List<Track> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        if (trackList.isNotEmpty()) {
            trackList.forEach {
                for (artist in it.artists.artists){
                    strBuilder.append("${artist.name}, ")
                }
                if (it.artists.artists.isNotEmpty()) {
                    strBuilder.delete(strBuilder.length-2, strBuilder.length-1)
                    strBuilder.appendln(" - ${it.title} : ${it.link}")
                }
            }
        }
        return strBuilder.toString()
    }
}

data class ReleaseDate(val date: LocalDate = LocalDate.now())
data class Genres(val genres: List<String> = emptyList()) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        genres.forEach { strBuilder.append("$it, ") }
        return strBuilder.toString().substringBeforeLast(",")
    }
}

data class Artist(
    val name: Name, val link: Link, val topTracks: TrackList = TrackList(emptyList()),
    val relatedArtists: Artists = Artists(ArrayList()), val genres: Genres = Genres(emptyList())
) {
    override fun toString(): String {
        return "Artist:     \t\t\t\t$name\n" +
                "Link:       \t\t\t\t$link\n" +
                if (genres.genres.isNotEmpty()) "Genres:  \t\t\t\t$genres\n" else {
                    ""
                } +
                if (topTracks.trackList.isNotEmpty()) "Top tracks:\n$topTracks\n" else {
                    ""
                } +
                if (relatedArtists.artists.isNotEmpty()) "Related artists:\n$relatedArtists" else ""
    }
}

data class Album(
    val name: Name = Name(),
    val artists: Artists = Artists(),
    val releaseDate: ReleaseDate = ReleaseDate(),
    val tracks: TrackList = TrackList(),
    val link: Link = Link(),
    val genres: Genres = Genres()
) {
    override fun toString(): String {
        return "Album Name:  \t${name.name}\n" +
                when {
                    link.link.contains("(youtube|youtu.be|soundcloud)".toRegex()) -> "Upload Date:  \t\t${releaseDate.date}\n"
                    link.link.contains("spotify".toRegex()) -> "Release:    \t\t\t${releaseDate.date}\n"
                    else -> ""
                } +
                "Album Link:  \t\t$link\n\n" +
                "Album Artists:\n$artists\n" +
                if (tracks.trackList.isNotEmpty()) "Tracks:\n$tracks" else ""
    }
}

data class User(
    val name: Name,
    val userName: Name,
    val followers: Followers,
    val link: Link
) {
    override fun toString(): String {
        return "Name:  \t\t\t${name.name}\n" +
                "Username: \t\t${userName.name}\n" +
                "Followers:  \t\t${followers.amount}\n" +
                "Link:    \t\t\t${link.link}"
    }
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
    override fun toString(): String {
        return "Playlist Name: \t${name.name}\n" +
                "Owner:    \t\t\t${owner.name}\n" +
                "Description:\n${description.text}\n" +
                "Followers: \t\t${followers.amount}\n" +
                "Is Public:   \t\t${publicity.isPublic}\n" +
                "Is Collaborative: \t\t${collaboration.isCollaborative}\n" +
                "Link:    \t\t\t${link.link}"
    }
}
