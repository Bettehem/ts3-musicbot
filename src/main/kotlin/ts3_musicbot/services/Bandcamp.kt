package ts3_musicbot.services

import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class Bandcamp : Service(ServiceType.BANDCAMP) {
    private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z")
    override suspend fun fetchTrack(trackLink: Link): Track {
        val request = sendHttpRequest(trackLink)
        return when (request.code.code) {
            HttpURLConnection.HTTP_OK -> {
                val lines = request.data.data.lines()
                val trackData =
                    JSONObject(lines[lines.indexOfFirst { it.contains("<script type=\"application/ld+json\">") } + 1])
                Track(
                    Album(
                        Name(trackData.getJSONObject("inAlbum").getString("name")),
                        Artists(
                            listOf(
                                Artist(
                                    Name(trackData.getJSONObject("byArtist").getString("name")),
                                    Link(trackData.getJSONObject("byArtist").getString("@id"))
                                )
                            )
                        ),
                        ReleaseDate(LocalDate.parse(trackData.getString("datePublished"), formatter)),
                        TrackList(),
                        Link(trackData.getJSONObject("inAlbum").getString("@id")),
                        Genres(trackData.getJSONArray("keywords").map { it as String })
                    ),
                    Artists(
                        listOf(
                            Artist(
                                Name(trackData.getJSONObject("byArtist").getString("name")),
                                Link(trackData.getJSONObject("byArtist").getString("@id"))
                            )
                        )
                    ),
                    Name(trackData.getString("name")),
                    Link(trackData.getString("@id")),
                    Playability(true)
                )
            }

            else -> Track()
        }
    }

    override suspend fun fetchAlbum(albumLink: Link): Album {
        val request = sendHttpRequest(albumLink)
        return when (request.code.code) {
            HttpURLConnection.HTTP_OK -> {
                val lines = request.data.data.lines()
                val albumData =
                    JSONObject(lines[lines.indexOfFirst { it.contains("<script type=\"application/ld+json\">") } + 1])
                Album(
                    Name(albumData.getString("name")),
                    Artists(
                        listOf(
                            Artist(
                                Name(albumData.getJSONObject("byArtist").getString("name")),
                                Link(albumData.getString("@id").substringBefore("/album"))
                            )
                        )
                    ),
                    ReleaseDate(LocalDate.parse(albumData.getString("datePublished"), formatter)),
                    fetchAlbumTracks(albumLink),
                    Link(albumData.getString("@id")),
                    Genres(albumData.getJSONArray("keywords").map { it as String })
                )
            }

            else -> Album()
        }
    }

    override suspend fun fetchAlbumTracks(albumLink: Link, limit: Int): TrackList {
        val request = sendHttpRequest(albumLink)
        return when (request.code.code) {
            HttpURLConnection.HTTP_OK -> {
                val lines = request.data.data.lines()
                val albumData =
                    JSONObject(lines[lines.indexOfFirst { it.contains("<script type=\"application/ld+json\">") } + 1])
                TrackList(
                    albumData.getJSONObject("track").getJSONArray("itemListElement").map {
                        it as JSONObject
                        val trackData = it.getJSONObject("item")
                        Track(
                            Album(
                                Name(albumData.getString("name")),
                                link = Link(albumData.getString("@id"))
                            ),
                            Artists(
                                listOf(
                                    Artist(
                                        Name(albumData.getJSONObject("byArtist").getString("name")),
                                        Link(albumData.getString("@id").substringBefore("/album"))
                                    )
                                )
                            ),
                            Name(trackData.getString("name")),
                            Link(trackData.getString("@id")),
                            Playability(true)
                        )
                    }.let { list ->
                        if (limit != 0 && list.size > limit)
                            list.subList(0, limit)
                        else
                            list
                    }
                )
            }

            else -> TrackList()
        }
    }

    override suspend fun fetchArtist(artistLink: Link, fetchRecommendations: Boolean): Artist {
        val request = sendHttpRequest(artistLink)
        return when (request.code.code) {
            HttpURLConnection.HTTP_OK -> {
                val lines = request.data.data.lines()
                var name = Name()
                val link = Link(artistLink.link.replace("\\.com.*".toRegex(), ".com"))
                val topTracks = ArrayList<Track>()
                val relatedArtists = if (fetchRecommendations) {
                    fetchRecommendedArtists(
                        Link(
                            "https://bandcamp.com/recommended/${
                                link.link.replace("(^https://|\\.bandcamp\\.com.*$)".toRegex(), "")
                            }"
                        )
                    )
                } else {
                    Artists()
                }
                val albums = ArrayList<Album>()

                for (line in lines) {
                    when {
                        line.contains("<meta property=\"og:site_name\"") -> {
                            name = Name(line.substringAfter("content=\"").substringBeforeLast("\">"))
                        }

                        line.contains("href=\"/album/") && fetchRecommendations -> {
                            albums.add(
                                fetchAlbum(
                                    Link(
                                        "$link/album/" + line
                                            .substringAfter("/album/")
                                            .substringBeforeLast("\">")
                                    )
                                )
                            )
                        }
                    }
                }

                //Since bandcamp has no such thing as top tracks, just pick (up to) 10 tracks from the albums
                for (album in albums) {
                    for (track in album.tracks.trackList) {
                        if (topTracks.size < 10)
                            topTracks.add(track)
                        else break
                    }
                }

                Artist(name, link, TrackList(topTracks), relatedArtists, albums = Albums(albums))
            }

            else -> Artist()
        }
    }

    /**
     * Fetch recommended artists for the given artist link
     * @param recommendationsLink Link to get recommendations from
     */
    suspend fun fetchRecommendedArtists(recommendationsLink: Link): Artists {
        val response = sendHttpRequest(recommendationsLink)

        return when (response.code.code) {
            HttpURLConnection.HTTP_OK -> {
                Artists(
                    response.data.data.lines()
                        .filter { it.contains("href=\"https://\\S+\\.bandcamp\\.com/album/\\S+".toRegex()) }
                        .map { Link("https://" + it.substringAfter("href=\"https://").substringBefore('/')) }
                        .distinct().map { fetchArtist(it, false) }
                )
            }

            else -> Artists()
        }
    }

    suspend fun fetchRecommendedAlbums(recommendationsLink: Link): Albums {
        val response = sendHttpRequest(recommendationsLink)

        return when (response.code.code) {
            HttpURLConnection.HTTP_OK -> {
                Albums(
                    response.data.data.lines()
                        .filter { it.contains("href=\"https://\\S+\\.bandcamp\\.com/album/\\S+".toRegex()) }
                        .map { fetchAlbum(Link(it.substringAfter("href=\"").substringBefore('?'))) }
                )
            }

            else -> Albums()
        }
    }

    override suspend fun resolveType(link: Link): LinkType {
        return when {
            "$link".contains("https?://\\S+\\.bandcamp\\.com/track/\\S+".toRegex()) -> LinkType.TRACK
            "$link".contains("https?://\\S+\\.bandcamp\\.com/album/\\S+".toRegex()) -> LinkType.ALBUM
            "$link".contains("https?://bandcamp\\.com/recommended/\\S+".toRegex()) -> LinkType.RECOMMENDED
            "$link".contains("https?://\\S+\\.bandcamp\\.com(/(music|merch|community))?".toRegex()) -> LinkType.ARTIST
            else -> LinkType.OTHER
        }
    }
}
