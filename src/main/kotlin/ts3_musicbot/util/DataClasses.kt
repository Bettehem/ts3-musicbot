package ts3_musicbot.util

import java.time.LocalDate

data class Track(var album: Album, var artists: Artists, var title: Name, var link: Link, var isPlayable: Playability) {
    override fun toString(): String {
        return "Album: \t\t$album\n" +
                if (artists.artists.size > 1) "Artists:" else {
                    "Artist:"
                } + " \t\t$artists\n" +
                "Title: \t\t$title\n" +
                "Link: $link"
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

data class Name(val name: String) {
    override fun toString(): String {
        return name
    }
}

data class Link(val link: String) {
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
data class Playability(val isPlayable: Boolean = true)
data class Followers(val amount: Int)

data class Artists(val artists: List<Artist>) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        artists.forEach { "${strBuilder.appendln(it)}" }
        return strBuilder.toString()
    }
}

data class TrackList(val trackList: List<Track>) {
    override fun toString(): String {
        val strBuilder = StringBuilder()
        trackList.forEach {
            strBuilder.appendln(
                "${it.artists.artists.forEach { artist -> "${artist.name}, " }}".substringBeforeLast(
                    ","
                ) + " - ${it.title} : ${it.link}"
            )
        }
        return strBuilder.toString()
    }
}

data class ReleaseDate(val date: LocalDate)
data class Genres(val genres: List<String>) {
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
    val name: Name,
    val artists: Artists,
    val releaseDate: ReleaseDate,
    val tracks: TrackList,
    val link: Link,
    val genres: Genres = Genres(emptyList())
) {
    override fun toString(): String {
        return "Album Name:  \t${name.name}\n" +
                when {
                    link.link.contains("(youtube|youtu.be|soundcloud)".toRegex()) -> "Upload Date:  \t\t${releaseDate.date}\n"
                    link.link.contains("spotify".toRegex()) -> "Release:    \t\t\t${releaseDate.date}\n"
                    else -> ""
                } +
                "Album Link:  \t\t$link\n" +
                "\nArtists:\n$artists\n" +
                "Tracks:\n$tracks"
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
