package ts3_musicbot.services

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.*
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

class Spotify(private val market: String = "") {
    private val defaultMarket = "US"
    private val apiURL = URL("https://api.spotify.com/v1")
    private var accessToken = ""

    suspend fun updateToken() {
        println("Updating Spotify access token...")
        withContext(IO) {
            accessToken = getSpotifyToken()
        }
    }

    private suspend fun getSpotifyToken(): String {
        fun getData(): Pair<ResponseCode, ResponseData> {
            val auth = "ZGUzZGFlNGUxZTE3NGRkNGFjYjY0YWYyMjcxMWEwYmI6ODk5OGQxMmJjZDBlNDAzM2E2Mzg2ZTg4Y2ZjZTk2NDg="
            return sendHttpRequest(
                URL("https://accounts.spotify.com/api/token"),
                RequestMethod("POST"),
                ExtraProperties(listOf("Authorization: Basic $auth")),
                PostData(listOf("grant_type=client_credentials"))
            )
        }

        lateinit var token: String
        val tokenJob = Job()
        withContext(IO + tokenJob) {
            while (true) {
                val data = getData()
                //check http return code
                when (data.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tokenData = JSONObject(data.second.data)
                            token = tokenData.getString("access_token")
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${data.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${data.second.data} seconds.")
                        //wait for given time before next request.
                        delay(data.second.data.toLong() * 1000 + 500)
                    }
                    else -> {
                        println("HTTP ERROR! CODE: ${data.first.code}")
                    }
                }
            }
        }
        return token
    }

    suspend fun searchSpotify(type: SearchType, searchQuery: SearchQuery): String {
        val searchResult = StringBuilder()

        fun searchData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/search?")
            urlBuilder.append("q=${URLEncoder.encode(searchQuery.query, Charsets.UTF_8.toString())}")
            urlBuilder.append("&type=${type.type}")
            if (market.isNotEmpty())
                urlBuilder.append("&market=$market")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun parseResults(searchData: JSONObject) {
            //token is valid, parse data
            when (type.type) {
                "track" -> {
                    val trackList = searchData.getJSONObject("tracks").getJSONArray("items")
                    for (trackData in trackList) {
                        trackData as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: \t\t")
                        for (artistData in trackData.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }

                        val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                            "${trackData.getJSONObject("album").getString("name")} (Single)"
                        } else {
                            trackData.getJSONObject("album").getString("name")
                        }

                        val songName = trackData.getString("name")

                        val songLink = trackData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  \t$albumName\n" +
                                    "Title:  \t\t$songName\n" +
                                    "Link:   \t\t$songLink\n"
                        )
                    }
                }
                "album" -> {
                    val albums = searchData.getJSONObject("albums").getJSONArray("items")
                    for (album in albums) {
                        album as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: \t\t")
                        for (artistData in album.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }


                        val albumName = if (album.getString("album_type") == "single") {
                            "${album.getString("name")} (Single)"
                        } else {
                            album.getString("name")
                        }

                        val albumLink = album.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  \t$albumName\n" +
                                    "Link:   \t\t$albumLink\n"
                        )
                    }
                }
                "playlist" -> {
                    val playlists = searchData.getJSONObject("playlists").getJSONArray("items")
                    for (listData in playlists) {
                        listData as JSONObject

                        val listName = listData.getString("name")
                        val listOwner = if (listData.getJSONObject("owner").get("display_name") != null) {
                            listData.getJSONObject("owner").get("display_name")
                        } else {
                            "N/A"
                        }
                        val trackAmount = listData.getJSONObject("tracks").getInt("total")
                        val listLink = listData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Playlist:  \t$listName\n" +
                                    "Owner:    \t$listOwner\n" +
                                    "Tracks:   \t$trackAmount\n" +
                                    "Link:     \t\t$listLink\n"
                        )
                    }
                }
                "artist" -> {
                    val artists = searchData.getJSONObject("artists").getJSONArray("items")
                    for (artistData in artists) {
                        artistData as JSONObject

                        val artistName = artistData.getString("name")
                        val followers = artistData.getJSONObject("followers").getLong("total")
                        var genres = ""
                        artistData.getJSONArray("genres").forEach { genres += "$it, " }
                        genres = genres.substringBeforeLast(",")
                        val artistLink = artistData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Artist:    \t\t\t$artistName\n" +
                                    "Followers:  \t$followers\n" +
                                    "Genres:    \t\t$genres\n" +
                                    "Link:      \t\t\t$artistLink\n"
                        )
                    }
                }
                "show" -> {
                    val shows = searchData.getJSONObject("shows").getJSONArray("items")
                    for (showData in shows) {
                        showData as JSONObject

                        val showName = showData.getString("name")
                        val publisher = showData.getString("publisher")
                        val episodes = showData.getInt("total_episodes")
                        val description = showData.getString("description")
                        val showLink = showData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Show:     \t\t$showName\n" +
                                    "Publisher: \t$publisher\n" +
                                    "Episodes:  \t$episodes\n" +
                                    "Description:\n$description\n" +
                                    "Link:      \t\t$showLink\n"
                        )
                    }
                }
                "episode" -> {
                    val episodes = searchData.getJSONObject("episodes").getJSONArray("items")
                    for (episodeData in episodes) {
                        episodeData as JSONObject

                        val episodeName = episodeData.getString("name")
                        val description = episodeData.getString("description")
                        val episodeLink = episodeData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Episode Name: \t$episodeName\n" +
                                    "Description:\n$description\n" +
                                    "Link          \t\t\t\t$episodeLink\n"
                        )
                    }
                }
            }
        }

        println("Searching for \"${searchQuery}\" on Spotify...")
        val searchJob = Job()
        withContext(IO + searchJob) {
            while (true) {
                val searchData = searchData()
                //check http return code
                when (searchData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val resultData = JSONObject(searchData.second.data)
                            withContext(Default + searchJob) {
                                parseResults(resultData)
                            }
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${searchData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> updateToken() //token expired, update it.

                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${searchData.first.code}! Bad request!!")
                        return@withContext
                    }

                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${searchData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(searchData.second.data.toLong() * 1000 + 500)
                    }

                    else -> println("HTTP ERROR! CODE: ${searchData.first.code}")

                }
            }
        }
        return searchResult.toString().substringBeforeLast("\n")
    }

    private fun getPlaylistData(playlistLink: Link): Pair<ResponseCode, ResponseData> {
        //First get the playlist length
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/playlists/")
        urlBuilder.append(
            playlistLink.link.substringAfterLast(":")
                .substringBefore("?si=")
                .substringAfterLast("/")
        )
        urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
        return sendHttpRequest(
            URL(urlBuilder.toString()),
            RequestMethod("GET"),
            ExtraProperties(listOf("Authorization: Bearer $accessToken"))
        )
    }

    suspend fun getPlaylist(playlistLink: Link): Playlist {
        lateinit var playlist: Playlist
        suspend fun parsePlaylistData(data: JSONObject): Playlist {
            return Playlist(
                Name(data.getString("name")),
                getUser(Link(data.getJSONObject("owner").getJSONObject("external_urls").getString("spotify"))),
                Description(data.getString("description")),
                Followers(data.getJSONObject("followers").getInt("total")),
                Publicity(data.getBoolean("public")),
                Collaboration(data.getBoolean("collaborative")),
                Link(data.getJSONObject("external_urls").getString("spotify"))
            )
        }

        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = getPlaylistData(playlistLink)
                //check http return code
                when (playlistData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(playlistData.second.data)
                            playlist = parsePlaylistData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${playlistData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(playlistData.second.data.toLong() * 1000 + 500)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $playlistLink not found!")
                        playlist = Playlist(
                            Name(),
                            User(
                                Name(),
                                Name(),
                                Followers(),
                                Link()
                            ),
                            Description(),
                            Followers(0),
                            Publicity(false),
                            Collaboration(false),
                            playlistLink
                        )
                        return@withContext
                    }
                    else -> println("HTTP ERROR! CODE ${playlistData.first.code}")
                }
            }
        }
        return playlist
    }

    suspend fun getPlaylistTracks(playlistLink: Link): TrackList {
        val trackItems = ArrayList<Track>()
        suspend fun parsePlaylistData(playlistData: JSONObject) {
            //get playlist length
            var playlistLength = playlistData.getJSONObject("tracks").getInt("total")
            //Now get all tracks
            //spotify only shows 100 items per search, so with each 100 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < playlistLength) {

                fun getItemData(): Pair<ResponseCode, ResponseData> {
                    val listUrlBuilder = StringBuilder()
                    listUrlBuilder.append("https://api.spotify.com/v1/playlists/")
                    listUrlBuilder.append(
                        playlistLink.link.substringAfterLast(":")
                            .substringBefore("?si=")
                            .substringAfterLast("/")
                    )
                    listUrlBuilder.append("/tracks?limit=100")
                    listUrlBuilder.append("&offset=$listOffset")
                    val listUrl = URL(listUrlBuilder.toString())
                    return sendHttpRequest(
                        listUrl,
                        RequestMethod("GET"),
                        ExtraProperties(listOf("Authorization: Bearer $accessToken"))
                    )
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        try {
                            if (item.get("track") != null) {
                                if (item.getJSONObject("track").get("id") != null) {
                                    if (!item.getJSONObject("track").getBoolean("is_local")) {
                                        val albumName = if (item.getJSONObject("track").getJSONObject("album")
                                                .getString("album_type") == "single"
                                        ) {
                                            Name(
                                                "${
                                                    item.getJSONObject("track").getJSONObject("album")
                                                        .getString("name")
                                                } (Single)"
                                            )
                                        } else {
                                            Name(item.getJSONObject("track").getJSONObject("album").getString("name"))
                                        }
                                        val artistsData = item.getJSONObject("track").getJSONArray("artists")
                                        val artists = Artists(
                                            artistsData.map {
                                                it as JSONObject
                                                Artist(
                                                    Name(it.getString("name")),
                                                    Link(it.getJSONObject("external_urls").getString("spotify")),
                                                    TrackList(emptyList()),
                                                    Artists(emptyList())
                                                )
                                            }
                                        )
                                        val album = Album(
                                            albumName,
                                            artists,
                                            when (item.getJSONObject("track").getJSONObject("album")
                                                .getString("release_date_precision")) {
                                                "day" -> {
                                                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                                    ReleaseDate(
                                                        LocalDate.parse(
                                                            item.getJSONObject("track").getJSONObject("album")
                                                                .getString("release_date"), formatter
                                                        )
                                                    )
                                                }
                                                "month" -> {
                                                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM")
                                                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                                        .toFormatter()
                                                    ReleaseDate(
                                                        LocalDate.parse(
                                                            item.getJSONObject("track").getJSONObject("album")
                                                                .getString("release_date"), formatter
                                                        )
                                                    )
                                                }
                                                else -> {
                                                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy")
                                                        .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                                                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                                        .toFormatter()
                                                    ReleaseDate(
                                                        LocalDate.parse(
                                                            item.getJSONObject("track").getJSONObject("album")
                                                                .getString("release_date"), formatter
                                                        )
                                                    )
                                                }
                                            },
                                            TrackList(emptyList()),
                                            Link(
                                                item.getJSONObject("track").getJSONObject("album")
                                                    .getJSONObject("external_urls").getString("spotify")
                                            )
                                        )
                                        val title = Name(item.getJSONObject("track").getString("name"))
                                        val link = Link(
                                            item.getJSONObject("track").getJSONObject("external_urls")
                                                .getString("spotify")
                                        )
                                        val availableMarkets =
                                            item.getJSONObject("track").getJSONArray("available_markets")
                                        val isPlayable = (
                                                availableMarkets.contains(market) ||
                                                        availableMarkets.contains(defaultMarket)
                                                )
                                        trackItems.add(
                                            Track(
                                                album,
                                                artists,
                                                title,
                                                link,
                                                Playability(isPlayable),
                                                LinkType.SPOTIFY
                                            )
                                        )
                                    } else {
                                        playlistLength -= 1
                                    }
                                }
                            }
                        } catch (e: JSONException) {
                            playlistLength -= 1
                        }
                    }
                }

                val itemJob = Job()
                withContext(IO + itemJob) {
                    while (true) {
                        val itemData = getItemData()
                        when (itemData.first.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val item = JSONObject(itemData.second.data)
                                    withContext(Default) {
                                        parseItems(item.getJSONArray("items"))
                                    }
                                    listOffset += 100
                                    return@withContext
                                } catch (e: JSONException) {
                                    //JSON broken, try getting the data again
                                    println("Failed JSON:\n${itemData.second.data}\n")
                                    println("Failed to get data from JSON, trying again...")
                                }
                            }
                            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                //token expired, update it
                                updateToken()
                            }
                            HTTP_TOO_MANY_REQUESTS -> {
                                println("Too many requests! Waiting for ${itemData.second.data} seconds.")
                                //wait for given time before next request.
                                delay(itemData.second.data.toLong() * 1000 + 500)
                            }
                            else -> {
                                println("HTTP ERROR! CODE: ${itemData.first.code}")
                            }
                        }
                    }
                }
            }
        }

        val playlistJob: CompletableJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = getPlaylistData(playlistLink)
                //check http return code
                when (playlistData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlist = JSONObject(playlistData.second.data)
                            parsePlaylistData(playlist)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $playlistLink not found!")
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${playlistData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(playlistData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE: ${playlistData.first.code}")
                }
            }
        }
        return TrackList(trackItems)
    }

    private fun getAlbumData(albumLink: Link): Pair<ResponseCode, ResponseData> {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/albums/")
        urlBuilder.append(
            albumLink.link.substringAfterLast(":")
                .substringBefore("?si=")
                .substringAfterLast("/")
        )
        urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")

        return sendHttpRequest(
            URL(urlBuilder.toString()),
            RequestMethod("GET"),
            ExtraProperties(listOf("Authorization: Bearer $accessToken"))
        )
    }

    suspend fun getAlbum(albumLink: Link): Album {
        lateinit var album: Album
        suspend fun parseAlbumData(data: JSONObject): Album {
            return Album(
                Name(data.getString("name")),
                Artists(data.getJSONArray("artists").map {
                    it as JSONObject
                    val artistName = Name(it.getString("name"))
                    val link = Link(it.getJSONObject("external_urls").getString("spotify"))
                    Artist(artistName, link)
                }),
                when (data.getString("release_date_precision")) {
                    "day" -> {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        ReleaseDate(
                            LocalDate.parse(
                                data.getString("release_date"), formatter
                            )
                        )
                    }
                    "month" -> {
                        val formatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM")
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter()
                        ReleaseDate(
                            LocalDate.parse(
                                data.getString("release_date"), formatter
                            )
                        )
                    }
                    else -> {
                        val formatter = DateTimeFormatterBuilder().appendPattern("yyyy")
                            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter()
                        ReleaseDate(
                            LocalDate.parse(
                                data.getString("release_date"), formatter
                            )
                        )
                    }
                },
                getAlbumTracks(albumLink),
                albumLink,
                Genres(data.getJSONArray("genres").map {
                    if (it is String) it else ""
                })
            )
        }

        val albumJob = Job()
        withContext(IO + albumJob) {
            while (true) {
                val albumData = getAlbumData(albumLink)
                //check http return code
                when (albumData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(albumData.second.data)
                            album = parseAlbumData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $albumLink not found!")
                        album = Album(
                            Name(),
                            Artists(),
                            ReleaseDate(),
                            TrackList(),
                            Link(),
                            Genres()
                        )
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${albumData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(albumData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.first.code}")
                }
            }
        }
        return album
    }

    suspend fun getAlbumTracks(albumLink: Link): TrackList {
        val trackItems = ArrayList<Track>()

        suspend fun parseAlbumData(albumData: JSONObject) {
            val trackItemsLength = albumData.getJSONObject("tracks").getInt("total")
            val albumName = if (albumData.getString("album_type") == "single")
                Name("${albumData.getString("name")} (Single)")
            else
                Name(albumData.getString("name"))

            val releaseDate = when (albumData.getString("release_date_precision")) {
                "day" -> {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    ReleaseDate(
                        LocalDate.parse(
                            albumData.getString("release_date"), formatter
                        )
                    )
                }
                "month" -> {
                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM")
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter()
                    ReleaseDate(
                        LocalDate.parse(
                            albumData.getString("release_date"), formatter
                        )
                    )
                }
                else -> {
                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy")
                        .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter()
                    ReleaseDate(
                        LocalDate.parse(
                            albumData.getString("release_date"), formatter
                        )
                    )
                }
            }
            val albumArtists = Artists(albumData.getJSONArray("artists").map {
                it as JSONObject
                Artist(
                    Name(it.getString("name")),
                    Link(it.getJSONObject("external_urls").getString("spotify")),
                    TrackList(emptyList()),
                    Artists(emptyList())
                )
            })

            //Now get all tracks
            //spotify only shows 20 items per search, so with each 20 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < trackItemsLength) {

                fun getAlbumTrackData(): Pair<ResponseCode, ResponseData> {
                    val albumUrlBuilder = StringBuilder()
                    albumUrlBuilder.append("$apiURL/albums/")
                    albumUrlBuilder.append(
                        albumLink.link.substringAfterLast(":")
                            .substringBefore("?si=")
                            .substringAfterLast("/")
                    )
                    albumUrlBuilder.append("/tracks?limit=20")
                    albumUrlBuilder.append("&offset=$listOffset")
                    if (market.isNotEmpty())
                        albumUrlBuilder.append("&market=$market")
                    return sendHttpRequest(
                        URL(albumUrlBuilder.toString()),
                        RequestMethod("GET"),
                        ExtraProperties(listOf("Authorization: Bearer $accessToken"))
                    )
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        val artistsData = item.getJSONArray("artists")
                        val artists = Artists(artistsData.map {
                            it as JSONObject
                            Artist(
                                Name(it.getString("name")),
                                Link(it.getJSONObject("external_urls").getString("spotify")),
                                TrackList(emptyList()),
                                Artists(emptyList())
                            )
                        })
                        val title = Name(item.getString("name"))
                        val link = Link(item.getJSONObject("external_urls").getString("spotify"))
                        val isPlayable = if (market.isNotEmpty()) {
                            item.getBoolean("is_playable")
                        } else {
                            true
                        }
                        val album = Album(albumName, albumArtists, releaseDate, TrackList(emptyList()), albumLink)
                        trackItems.add(Track(album, artists, title, link, Playability(isPlayable)))
                    }
                }

                var gettingData = true
                while (gettingData) {
                    val albumTrackData = getAlbumTrackData()
                    when (albumTrackData.first.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val tracks = JSONObject(albumTrackData.second.data)
                                withContext(Default) {
                                    parseItems(tracks.getJSONArray("items"))
                                }
                                listOffset += 20
                                gettingData = false
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${albumTrackData.second.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            //token expired, update it
                            updateToken()
                        }
                        HTTP_TOO_MANY_REQUESTS -> {
                            println("Too many requests! Waiting for ${albumTrackData.second.data} seconds.")
                            //wait for given time before next request.
                            delay(albumTrackData.second.data.toLong() * 1000)
                        }
                        else -> println("HTTP ERROR! CODE ${albumTrackData.first.code}")
                    }
                }
            }
        }

        val albumJob = Job()
        withContext(IO + albumJob) {
            while (true) {
                val albumData = getAlbumData(albumLink)
                //check http return code
                when (albumData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(albumData.second.data)
                            parseAlbumData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $albumLink not found!")
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${albumData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(albumData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.first.code}")
                }
            }
        }
        return TrackList(trackItems)
    }

    suspend fun getTrack(trackLink: Link): Track {
        fun getTrackData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/tracks/")
            urlBuilder.append(
                trackLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun parseData(trackData: JSONObject): Track {
            val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                Name("${trackData.getJSONObject("album").getString("name")} (Single)")
            } else {
                Name(trackData.getJSONObject("album").getString("name"))
            }
            val albumArtists = Artists(trackData.getJSONObject("album").getJSONArray("artists").map {
                it as JSONObject
                Artist(
                    Name(it.getString("name")),
                    Link(it.getJSONObject("external_urls").getString("spotify")),
                    TrackList(emptyList())
                )
            })
            val album = Album(
                albumName,
                albumArtists,
                when (trackData.getJSONObject("album").getString("release_date_precision")) {
                    "day" -> {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        ReleaseDate(
                            LocalDate.parse(
                                trackData.getJSONObject("album").getString("release_date"), formatter
                            )
                        )
                    }
                    "month" -> {
                        val formatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM")
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter()
                        ReleaseDate(
                            LocalDate.parse(
                                trackData.getJSONObject("album").getString("release_date"), formatter
                            )
                        )
                    }
                    else -> {
                        val formatter = DateTimeFormatterBuilder().appendPattern("yyyy")
                            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter()
                        ReleaseDate(
                            LocalDate.parse(
                                trackData.getJSONObject("album").getString("release_date"), formatter
                            )
                        )
                    }
                },
                TrackList(emptyList()),
                Link(trackData.getJSONObject("album").getJSONObject("external_urls").getString("spotify"))
            )
            val artists = Artists(trackData.getJSONArray("artists").map {
                it as JSONObject
                Artist(
                    Name(it.getString("name")),
                    Link(it.getJSONObject("external_urls").getString("spotify")),
                    TrackList(emptyList())
                )
            })
            val title = Name(trackData.getString("name"))
            val isPlayable = trackData.getJSONArray("available_markets").contains(market)
            return Track(album, artists, title, trackLink, Playability(isPlayable))
        }

        lateinit var track: Track
        val trackJob = Job()
        withContext(IO + trackJob) {
            while (true) {
                val trackData = getTrackData()
                when (trackData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            withContext(Default) {
                                track = parseData(JSONObject(trackData.second.data))
                            }
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${trackData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $trackLink not found!")
                        track = Track()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${trackData.second.data} seconds.")
                        //wait for given time before next request. 
                        delay(trackData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE: ${trackData.first.code}")
                }
            }
        }
        return track
    }

    suspend fun getArtist(artistLink: Link): Artist {
        fun getArtistData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(
                artistLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getTopTracks(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(
                artistLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            urlBuilder.append("/top-tracks")
            urlBuilder.append("?country=${if (market.isNotEmpty()) market else defaultMarket}")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getRelatedArtists(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(
                artistLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            urlBuilder.append("/related-artists")
            if (market.isNotEmpty())
                urlBuilder.append("?country=$market")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        suspend fun parseData(artistData: JSONObject, topTracksData: JSONObject, relatedArtists: JSONObject): Artist {
            val name = Name(artistData.getString("name"))
            val topTracks = ArrayList<Track>()
            val topTracksList = topTracksData.getJSONArray("tracks")
            for (track in topTracksList) {
                track as JSONObject
                val list = ArrayList<Artist>()
                val artists = Artists(track.getJSONArray("artists").mapTo(list, {
                    it as JSONObject
                    Artist(
                        Name(it.getString("name")),
                        Link(it.getJSONObject("external_urls").getString("spotify"))
                    )
                }))
                val album = Album(
                    Name(track.getString("name")),
                    artists,
                    ReleaseDate(
                        when (track.getJSONObject("album").getString("release_date_precision")) {
                            "day" -> {
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                LocalDate.parse(track.getJSONObject("album").getString("release_date"), formatter)
                            }
                            "month" -> {
                                val formatter = DateTimeFormatterBuilder()
                                    .appendPattern("yyyy-MM")
                                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                    .toFormatter()
                                LocalDate.parse(track.getJSONObject("album").getString("release_date"), formatter)
                            }
                            else -> {
                                val formatter = DateTimeFormatterBuilder()
                                    .appendPattern("yyyy")
                                    .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                    .toFormatter()
                                LocalDate.now().withYear(2)
                                LocalDate.parse(track.getJSONObject("album").getString("release_date"), formatter)
                            }
                        }
                    ),
                    getAlbumTracks(Link(track.getJSONObject("album").getString("uri"))),
                    artistLink
                )
                topTracks.add(
                    Track(
                        album,
                        artists,
                        Name(track.getString("name")),
                        Link(track.getJSONObject("external_urls").getString("spotify")),
                        Playability(if (market.isNotEmpty()) track.getBoolean("is_playable") else true)
                    )
                )
            }
            val related = ArrayList<Artist>()
            for (artist in relatedArtists.getJSONArray("artists")) {
                artist as JSONObject
                val artistName = Name(artist.getString("name"))
                val link = Link(artist.getJSONObject("external_urls").getString("spotify"))
                related.add(Artist(artistName, link))
            }
            val genres: List<String> = artistData.getJSONArray("genres").map {
                if (it is String) it else ""
            }
            return Artist(
                name,
                artistLink,
                TrackList(topTracks),
                Artists(related),
                Genres(genres)
            )
        }

        lateinit var artist: Artist
        val artistJob = Job()
        withContext(IO + artistJob) {
            while (true) {
                val artistData = getArtistData()
                when (artistData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            lateinit var topTracks: JSONObject
                            topTracks@ while (true) {
                                val topTracksData = getTopTracks()
                                when (topTracksData.first.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            withContext(Default) {
                                                topTracks = JSONObject(topTracksData.second.data)
                                            }
                                            break@topTracks
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${topTracksData.second.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }
                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${topTracksData.second.data} seconds.")
                                        //wait for given time before next request.
                                        delay(topTracksData.second.data.toLong() * 1000 + 500)
                                    }
                                    else -> println("HTTP ERROR! CODE: ${topTracksData.first.code}")
                                }
                            }
                            lateinit var relatedArtists: JSONObject
                            relatedArtists@ while (true) {
                                val relatedArtistsData = getRelatedArtists()
                                when (relatedArtistsData.first.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            withContext(Default) {
                                                relatedArtists = JSONObject(relatedArtistsData.second.data)
                                            }
                                            break@relatedArtists
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${relatedArtistsData.second.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }
                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${relatedArtistsData.second.data} seconds.")
                                        //wait for given time before next request.
                                        delay(relatedArtistsData.second.data.toLong() * 1000 + 500)
                                    }
                                    else -> println("HTTP ERROR! CODE: ${relatedArtistsData.first.code}")
                                }
                            }
                            artist = parseData(JSONObject(artistData.second.data), topTracks, relatedArtists)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${artistData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $artistLink not found!")
                        artist = Artist(
                            Name(),
                            Link(),
                            TrackList(),
                            Artists(),
                            Genres()
                        )
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${artistData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(artistData.second.data.toLong() * 1000 + 500)
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    else -> println("HTTP ERROR! CODE: ${artistData.first.code}")
                }
            }
        }
        return artist
    }

    suspend fun getUser(userLink: Link): User {
        lateinit var user: User
        fun getUserData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/users/")
            urlBuilder.append(
                userLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun parseUserData(userData: JSONObject): User {
            return User(
                Name(userData.getString("display_name")),
                Name(userData.getString("id")),
                Followers(userData.getJSONObject("followers").getInt("total")),
                Link(userData.getJSONObject("external_urls").getString("spotify"))
            )
        }

        val userJob = Job()
        withContext(IO + userJob) {
            while (true) {
                val userData = getUserData()
                //check response code
                when (userData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(userData.second.data)
                            user = parseUserData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${userData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $userLink not found!")
                        user = User(
                            Name(),
                            Name(),
                            Followers(),
                            Link()
                        )
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${userData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(userData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE: ${userData.first.code}")
                }
            }
        }
        return user
    }

    private fun getShowData(showLink: Link): Pair<ResponseCode, ResponseData> {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/shows/")
        urlBuilder.append(
            showLink.link.substringAfterLast(":")
                .substringBefore("?si=")
                .substringAfterLast("/")
        )
        urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
        return sendHttpRequest(
            URL(urlBuilder.toString()),
            RequestMethod("GET"),
            ExtraProperties(listOf("Authorization: Bearer $accessToken"))
        )
    }

    suspend fun getShow(showLink: Link): Show {
        lateinit var show: Show

        suspend fun getShowEpisodes(showLink: Link, totalItems: Int): EpisodeList {
            lateinit var episodeList: EpisodeList

            fun getEpisodesData(offset: Int = 0): Pair<ResponseCode, ResponseData> {
                val urlBuilder = StringBuilder()
                urlBuilder.append("$apiURL/shows/")
                urlBuilder.append(
                    showLink.link.substringAfterLast(":")
                        .substringBefore("?si=")
                        .substringAfterLast("/")
                )
                urlBuilder.append("/episodes")
                urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
                urlBuilder.append("&limit=50")
                urlBuilder.append("&offset=$offset")
                return sendHttpRequest(
                    URL(urlBuilder.toString()),
                    RequestMethod("GET"),
                    ExtraProperties(listOf("Authorization: Bearer $accessToken"))
                )
            }

            suspend fun parseEpisodesData(episodesData: JSONObject): EpisodeList {
                val episodes = ArrayList<Episode>()
                var items = episodesData.getJSONArray("items")
                var offset = 0
                while (episodes.size < totalItems) {
                    for (item in items) {
                        item as JSONObject
                        episodes.add(
                            Episode(
                                Name(item.getString("name")),
                                Description(item.getString("description")),
                                ReleaseDate(
                                    when (item.getString("release_date_precision")) {
                                        "day" -> {
                                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                            LocalDate.parse(item.getString("release_date"), formatter)
                                        }
                                        "month" -> {
                                            val formatter = DateTimeFormatterBuilder()
                                                .appendPattern("yyyy-MM")
                                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                                .toFormatter()
                                            LocalDate.parse(item.getString("release_date"), formatter)
                                        }
                                        else -> {
                                            val formatter = DateTimeFormatterBuilder()
                                                .appendPattern("yyyy")
                                                .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                                .toFormatter()
                                            LocalDate.now().withYear(2)
                                            LocalDate.parse(item.getString("release_date"), formatter)
                                        }
                                    }
                                ),
                                Link(item.getJSONObject("external_urls").getString("spotify")),
                                Playability(item.getBoolean("is_playable"))
                            )
                        )
                        if (!episodesData.isNull("next")) {
                            offset += 50
                            val episodesJob = Job()
                            withContext(episodesJob + IO) {
                                while (true) {
                                    val data = getEpisodesData(offset)

                                    when (data.first.code) {
                                        HttpURLConnection.HTTP_OK -> {
                                            try {
                                                val episodeData = JSONObject(data.second.data)
                                                items = episodeData.getJSONArray("items")
                                                return@withContext
                                            } catch (e: JSONException) {
                                                //JSON broken, try getting the data again
                                                println("Failed JSON:\n${data.second.data}\n")
                                                println("Failed to get data from JSON, trying again...")
                                            }
                                        }

                                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                            updateToken()
                                        }

                                        HTTP_TOO_MANY_REQUESTS -> {
                                            println("Too many requests! Waiting for ${data.second.data} seconds.")
                                            //wait for given time before next request.
                                            delay(data.second.data.toLong() * 1000 + 500)
                                        }

                                        else -> println("HTTP ERROR! CODE: ${data.first.code}")
                                    }
                                }
                            }
                        }
                    }
                }
                return EpisodeList(episodes)
            }

            val episodeJob = Job()
            withContext(IO + episodeJob) {
                while (true) {
                    val episodesData = getEpisodesData()
                    when (episodesData.first.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val data = JSONObject(episodesData.second.data)
                                episodeList = parseEpisodesData(data)
                                return@withContext
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${episodesData.second.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }

                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateToken()
                        }

                        HTTP_TOO_MANY_REQUESTS -> {
                            println("Too many requests! Waiting for ${episodesData.second.data} seconds.")
                            //wait for given time before next request.
                            delay(episodesData.second.data.toLong() * 1000 + 500)
                        }
                        else -> println("HTTP ERROR! CODE: ${episodesData.first.code}")
                    }
                }
            }
            return EpisodeList(episodeList.episodes.reversed())
        }

        suspend fun parseShowData(showData: JSONObject): Show {
            val episodes = getShowEpisodes(showLink, showData.getInt("total_episodes"))
            return Show(
                Name(showData.getString("name")),
                Publisher(Name(showData.getString("publisher"))),
                Description(showData.getString("description")),
                episodes,
                showLink
            )
        }

        val showJob = Job()
        withContext(IO + showJob) {
            while (true) {
                val showData = getShowData(showLink)
                when (showData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(showData.second.data)
                            show = parseShowData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${showData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $showLink not found!")
                        show = Show(
                            Name(),
                            Publisher(),
                            Description(),
                            EpisodeList(),
                            Link()
                        )
                    }

                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${showData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(showData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE: ${showData.first.code}")
                }
            }
        }
        return show
    }

    suspend fun getEpisode(episodeLink: Link): Episode {
        lateinit var episode: Episode

        fun getEpisodeData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/episodes/")
            urlBuilder.append(
                episodeLink.link.substringAfterLast(":")
                    .substringBefore("?si=")
                    .substringAfterLast("/")
            )
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun parseEpisodeData(episodeData: JSONObject): Episode {
            return Episode(
                Name(episodeData.getString("name")),
                Description(episodeData.getString("description")),
                ReleaseDate(
                    when (episodeData.getString("release_date_precision")) {
                        "day" -> {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            LocalDate.parse(episodeData.getString("release_date"), formatter)
                        }
                        "month" -> {
                            val formatter = DateTimeFormatterBuilder()
                                .appendPattern("yyyy-MM")
                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                .toFormatter()
                            LocalDate.parse(episodeData.getString("release_date"), formatter)
                        }
                        else -> {
                            val formatter = DateTimeFormatterBuilder()
                                .appendPattern("yyyy")
                                .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                .toFormatter()
                            LocalDate.now().withYear(2)
                            LocalDate.parse(episodeData.getString("release_date"), formatter)
                        }
                    }
                ),
                Link(episodeData.getJSONObject("external_urls").getString("spotify")),
                Playability(episodeData.getBoolean("is_playable"))
            )
        }

        val episodeJob = Job()
        withContext(IO + episodeJob) {
            while (true) {
                val episodeData = getEpisodeData()
                when (episodeData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(episodeData.second.data)
                            episode = parseEpisodeData(data)
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${episodeData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $episodeLink not found!")
                        episode = Episode()
                        return@withContext
                    }

                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${episodeData.second.data} seconds.")
                        //wait for given time before next request.
                        delay(episodeData.second.data.toLong() * 1000 + 500)
                    }
                    else -> println("HTTP ERROR! CODE: ${episodeData.first.code}")
                }
            }
        }
        return episode
    }

}
