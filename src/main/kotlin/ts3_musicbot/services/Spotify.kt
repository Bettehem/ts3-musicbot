package ts3_musicbot.services

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import org.json.*
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

class Spotify(private val market: String = "") {
    private val defaultMarket = "US"
    private val apiURL = URL("https://api.spotify.com/v1")
    private var accessToken = ""
    val supportedSearchTypes = listOf(
        SearchType.Type.TRACK,
        SearchType.Type.PLAYLIST,
        SearchType.Type.ALBUM,
        SearchType.Type.ARTIST,
        SearchType.Type.SHOW,
        SearchType.Type.EPISODE
    )

    private fun encode(text: String) = runBlocking {
        URLEncoder.encode(text, Charsets.UTF_8.toString())
            .replace("'", "&#39;")
            .replace("&", "&amp;")
            .replace("/", "&#x2F;")
    }

    private fun decode(text: String) = runBlocking {
        URLDecoder.decode(text, Charsets.UTF_8.toString())
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&#x2F;", "/")
    }

    suspend fun updateToken() {
        println("Updating Spotify access token...")
        withContext(IO) {
            accessToken = fetchSpotifyToken()
        }
    }

    private suspend fun fetchSpotifyToken(): String {
        fun getData(): Response {
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
                when (data.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tokenData = JSONObject(data.data.data)
                            token = tokenData.getString("access_token")
                            tokenJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${data.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${data.data.data} seconds.")
                        //wait for given time before next request.
                        delay(data.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${data.code}")

                }
            }
        }
        return token
    }

    suspend fun searchSpotify(type: SearchType, searchQuery: SearchQuery, resultLimit: Int = 10): SearchResults {
        val searchResults = ArrayList<SearchResult>()

        fun searchData(limit: Int = resultLimit, offset: Int = 0, link: Link = Link("")): Response {
            val urlBuilder = StringBuilder()
            if (link.isNotEmpty()) {
                urlBuilder.append("$link")
            } else {
                urlBuilder.append("$apiURL/search?")
                urlBuilder.append("q=${encode(searchQuery.query)}")
                urlBuilder.append(
                    "&type=${
                        if (type.getType() == SearchType.Type.SHOW)
                            type.type.replace("podcast", "show")
                        else
                            type.type
                    }"
                )
                urlBuilder.append("&limit=$limit")
                urlBuilder.append("&offset=$offset")
                if (market.isNotEmpty())
                    urlBuilder.append("&market=$market")
            }
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
                            artists.append("${decode(artist.getString("name"))}, ")
                        }

                        val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                            "${decode(trackData.getJSONObject("album").getString("name"))} (Single)"
                        } else {
                            decode(trackData.getJSONObject("album").getString("name"))
                        }

                        val songName = decode(trackData.getString("name"))

                        val songLink = trackData.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "${artists.toString().substringBeforeLast(",")}\n" +
                                        "Album:  \t$albumName\n" +
                                        "Title:  \t\t$songName\n" +
                                        "Link:   \t\t$songLink\n",
                                Link(songLink)
                            )
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
                            artists.append("${decode(artist.getString("name"))}, ")
                        }
                        val albumName = if (album.getString("album_type") == "single")
                            "${decode(album.getString("name"))} (Single)"
                        else
                            decode(album.getString("name"))


                        val albumLink = album.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "${artists.toString().substringBeforeLast(",")}\n" +
                                        "Album:  \t$albumName\n" +
                                        "Link:   \t\t$albumLink\n",
                                Link(albumLink)
                            )
                        )
                    }
                }
                "playlist" -> {
                    val playlists = searchData.getJSONObject("playlists").getJSONArray("items")
                    for (listData in playlists) {
                        listData as JSONObject

                        val listName = decode(listData.getString("name"))
                        val listOwner = if (listData.getJSONObject("owner").get("display_name") != null)
                            decode(listData.getJSONObject("owner").getString("display_name"))
                        else
                            "N/A"

                        val trackAmount = listData.getJSONObject("tracks").getInt("total")
                        val listLink = listData.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "Playlist:  \t$listName\n" +
                                        "Owner:    \t$listOwner\n" +
                                        "Tracks:   \t$trackAmount\n" +
                                        "Link:     \t\t$listLink\n",
                                Link(listLink)
                            )
                        )
                    }
                }
                "artist" -> {
                    val artists = searchData.getJSONObject("artists").getJSONArray("items")
                    for (artistData in artists) {
                        artistData as JSONObject

                        val artistName = decode(artistData.getString("name"))
                        val followers = artistData.getJSONObject("followers").getLong("total")
                        var genres = ""
                        artistData.getJSONArray("genres").forEach { genres += "$it, " }
                        genres = genres.substringBeforeLast(",")
                        val artistLink = artistData.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "Artist:    \t\t\t$artistName\n" +
                                        "Followers:  \t$followers\n" +
                                        if (genres.isNotEmpty()) {
                                            "Genres:    \t\t$genres\n"
                                        } else {
                                            ""
                                        } +
                                        "Link:      \t\t\t$artistLink\n",
                                Link(artistLink)
                            )
                        )
                    }
                }
                "show", "podcast" -> {
                    val shows = searchData.getJSONObject("shows").getJSONArray("items")
                    for (showData in shows) {
                        showData as JSONObject

                        val showName = decode(showData.getString("name"))
                        val publisher = decode(showData.getString("publisher"))
                        val episodes = showData.getInt("total_episodes")
                        val description = decode(showData.getString("description"))
                        val showLink = showData.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "Show:     \t\t$showName\n" +
                                        "Publisher: \t$publisher\n" +
                                        "Episodes:  \t$episodes\n" +
                                        "Description:\n$description\n" +
                                        "Link:      \t\t$showLink\n",
                                Link(showLink)
                            )
                        )
                    }
                }
                "episode" -> {
                    val episodes = searchData.getJSONObject("episodes").getJSONArray("items")
                    for (episodeData in episodes) {
                        episodeData as JSONObject

                        val episodeName = decode(episodeData.getString("name"))
                        val description = decode(episodeData.getString("description"))
                        val episodeLink = episodeData.getJSONObject("external_urls").getString("spotify")

                        searchResults.add(
                            SearchResult(
                                "Episode Name: \t$episodeName\n" +
                                        "Description:\n$description\n" +
                                        "Link          \t\t\t\t$episodeLink\n",
                                Link(episodeLink)
                            )
                        )
                    }
                }
            }
        }

        println("Searching for \"$searchQuery\" on Spotify...")
        val searches = ArrayList<Pair<Int, Int>>()
        var remainingResults = resultLimit
        var resultOffset = 0
        //Spotify allows a maximum of 50 results, so we have to do searches in smaller chunks in case the user wants more than 50 results
        val maxResults = 50
        while (true) {
            if (remainingResults > maxResults) {
                searches.add(Pair(maxResults, resultOffset))
                remainingResults -= maxResults
                resultOffset += maxResults
            } else {
                searches.add(Pair(remainingResults, resultOffset))
                break
            }
        }
        val resultsData = ArrayList<Response>()
        for (search in searches) {
            resultsData.add(searchData(search.first, search.second))
        }
        for (result in resultsData) {
            val searchJob = Job()
            withContext(IO + searchJob) {
                var searchData = result
                while (true) {
                    //check http return code
                    when (searchData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val resultData = JSONObject(searchData.data.data)
                                withContext(Default + searchJob) {
                                    parseResults(resultData)
                                }
                                searchJob.complete()
                                return@withContext
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${searchData.data}\n")
                                println("Failed to get data from JSON, trying again...")
                                searchData = searchData(link = Link(result.url.toString()))
                            }
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            //token expired, update it.
                            updateToken()
                            searchData = searchData(link = Link(result.url.toString()))
                        }
                        HttpURLConnection.HTTP_BAD_REQUEST -> {
                            println("Error ${searchData.code}! Bad request!!")
                            searchJob.complete()
                            return@withContext
                        }
                        HTTP_TOO_MANY_REQUESTS -> {
                            println("Too many requests! Waiting for ${searchData.data.data} seconds.")
                            //wait for given time before next request.
                            delay(searchData.data.data.toLong() * 1000)
                            searchData = searchData(link = Link(result.url.toString()))
                        }
                        else -> println("HTTP ERROR! CODE: ${searchData.code}")
                    }
                }
            }
        }
        return SearchResults(searchResults)
    }

    private fun getPlaylistData(playlistLink: Link): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/playlists/")
        urlBuilder.append(playlistLink.getId())
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
                Name(decode(data.getString("name"))),
                getUser(Link(data.getJSONObject("owner").getJSONObject("external_urls").getString("spotify"))),
                Description(decode(data.getString("description"))),
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
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(playlistData.data.data)
                            playlist = parsePlaylistData(data)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${playlistData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(playlistData.data.data.toLong() * 1000)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $playlistLink not found!")
                        playlist = Playlist(
                            Name(),
                            User(
                                Name(),
                                Name(),
                                Description(),
                                Followers(),
                                emptyList(),
                                Link()
                            ),
                            Description(),
                            Followers(0),
                            Publicity(false),
                            Collaboration(false),
                            playlistLink
                        )
                        playlistJob.complete()
                        return@withContext
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${playlistData.code}! Bad request!!")
                        playlist = Playlist()
                        playlistJob.complete()
                        return@withContext
                    }
                    else -> println("HTTP ERROR! CODE ${playlistData.code}")
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

                fun getItemData(): Response {
                    val listUrlBuilder = StringBuilder()
                    listUrlBuilder.append("https://api.spotify.com/v1/playlists/")
                    listUrlBuilder.append(playlistLink.getId())
                    listUrlBuilder.append("/tracks?limit=100")
                    listUrlBuilder.append("&offset=$listOffset")
                    listUrlBuilder.append("&market=${market.ifEmpty { defaultMarket }}")
                    val listUrl = URL(listUrlBuilder.toString())
                    return sendHttpRequest(
                        listUrl,
                        RequestMethod("GET"),
                        ExtraProperties(listOf("Authorization: Bearer $accessToken"))
                    )
                }

                suspend fun parseItems(items: JSONArray) {
                    println("Parsing items... (${items.length()})")
                    if (items.isEmpty) {
                        playlistLength -= (playlistLength - trackItems.size)
                    } else {
                        for (item in items) {
                            item as JSONObject
                            val itemJob = Job()
                            withContext(IO + itemJob) {
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
                                                    Name(
                                                        item.getJSONObject("track").getJSONObject("album")
                                                            .getString("name")
                                                    )
                                                }
                                                val artistsData = item.getJSONObject("track").getJSONArray("artists")
                                                val artists = Artists(
                                                    artistsData.map {
                                                        it as JSONObject
                                                        Artist(
                                                            Name(it.getString("name")),
                                                            Link(
                                                                it.getJSONObject("external_urls").getString("spotify")
                                                            ),
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
                                                            val formatter =
                                                                DateTimeFormatterBuilder().appendPattern("yyyy-MM")
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
                                                            val formatter =
                                                                DateTimeFormatterBuilder().appendPattern("yyyy")
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
                                                val isPlayable =
                                                    if (item.getJSONObject("track").getBoolean("is_playable"))
                                                        true
                                                    else {
                                                        println("Track $link playability not certain! Doing extra checks...")
                                                        getTrack(link).playability.isPlayable
                                                    }
                                                trackItems.add(
                                                    Track(
                                                        album,
                                                        artists,
                                                        title,
                                                        link,
                                                        Playability(isPlayable)
                                                    )
                                                )
                                                itemJob.complete()
                                            } else {
                                                println("This is a local track. Skipping...")
                                                playlistLength -= 1
                                                itemJob.complete()
                                            }
                                        } else {
                                            println("Track id is null. Skipping...")
                                            playlistLength -= 1
                                            itemJob.complete()
                                        }
                                    } else {
                                        println("Track data null. Skipping...")
                                        playlistLength -= 1
                                        itemJob.complete()
                                    }
                                } catch (e: JSONException) {
                                    println("Track couldn't be parsed due to JSONException. Skipping...")
                                    playlistLength -= 1
                                    itemJob.complete()
                                }
                            }
                        }
                    }
                }

                val itemJob = Job()
                withContext(IO + itemJob) {
                    while (true) {
                        val itemData = getItemData()
                        when (itemData.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val item = JSONObject(itemData.data.data)
                                    parseItems(item.getJSONArray("items"))
                                    listOffset += 100
                                    itemJob.complete()
                                    return@withContext
                                } catch (e: JSONException) {
                                    //JSON broken, try getting the data again
                                    println("Failed JSON:\n${itemData.data}\n")
                                    println("Failed to get data from JSON, trying again...")
                                }
                            }
                            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                //token expired, update it
                                updateToken()
                            }
                            HTTP_TOO_MANY_REQUESTS -> {
                                println("Too many requests! Waiting for ${itemData.data.data} seconds.")
                                //wait for given time before next request.
                                delay(itemData.data.data.toLong() * 1000)
                            }
                            else -> println("HTTP ERROR! CODE: ${itemData.code}")
                        }
                    }
                }
            }
        }

        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = getPlaylistData(playlistLink)
                //check http return code
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlist = JSONObject(playlistData.data.data)
                            parsePlaylistData(playlist)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $playlistLink not found!")
                        playlistJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${playlistData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(playlistData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${playlistData.code}")
                }
            }
        }
        return TrackList(trackItems)
    }

    private fun getAlbumData(albumLink: Link): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/albums/")
        urlBuilder.append(albumLink.getId())
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
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(albumData.data.data)
                            album = parseAlbumData(data)
                            albumJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $albumLink not found!")
                        album = Album()
                        albumJob.complete()
                        return@withContext
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${albumData.code}! Bad request!!")
                        album = Album()
                        albumJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${albumData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(albumData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.code}")
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
                        LocalDate.parse(albumData.getString("release_date"), formatter)
                    )
                }
                "month" -> {
                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM")
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter()
                    ReleaseDate(
                        LocalDate.parse(albumData.getString("release_date"), formatter)
                    )
                }
                else -> {
                    val formatter = DateTimeFormatterBuilder().appendPattern("yyyy")
                        .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter()
                    ReleaseDate(
                        LocalDate.parse(albumData.getString("release_date"), formatter)
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

                fun getAlbumTrackData(): Response {
                    val albumUrlBuilder = StringBuilder()
                    albumUrlBuilder.append("$apiURL/albums/")
                    albumUrlBuilder.append(albumLink.getId())
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

                val albumTrackJob = Job()
                withContext(IO + albumTrackJob) {
                    while (true) {
                        val albumTrackData = getAlbumTrackData()
                        when (albumTrackData.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val tracks = JSONObject(albumTrackData.data.data)
                                    withContext(Default) {
                                        parseItems(tracks.getJSONArray("items"))
                                    }
                                    listOffset += 20
                                    albumTrackJob.complete()
                                    return@withContext
                                } catch (e: JSONException) {
                                    //JSON broken, try getting the data again
                                    println("Failed JSON:\n${albumTrackData.data}\n")
                                    println("Failed to get data from JSON, trying again...")
                                }
                            }
                            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                //token expired, update it
                                updateToken()
                            }
                            HTTP_TOO_MANY_REQUESTS -> {
                                println("Too many requests! Waiting for ${albumTrackData.data.data} seconds.")
                                //wait for given time before next request.
                                delay(albumTrackData.data.data.toLong() * 1000)
                            }
                            else -> println("HTTP ERROR! CODE ${albumTrackData.code}")
                        }
                    }
                }
            }
        }

        val albumJob = Job()
        withContext(IO + albumJob) {
            while (true) {
                val albumData = getAlbumData(albumLink)
                //check http return code
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(albumData.data.data)
                            parseAlbumData(data)
                            albumJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        //token expired, update it
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $albumLink not found!")
                        albumJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${albumData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(albumData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.code}")
                }
            }
        }
        return TrackList(trackItems)
    }

    suspend fun getTrack(trackLink: Link): Track {
        fun getTrackData(link: Link, spMarket: String = ""): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/tracks/")
            urlBuilder.append(link.getId())
            if (spMarket.isNotEmpty())
                urlBuilder.append("?market=$spMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        suspend fun parseData(trackData: JSONObject): Track {
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
            println("Checking playability...")
            val isPlayable = if (trackData.getBoolean("is_playable")) {
                true
            } else {
                val trackJob = Job()
                var playable: Boolean
                withContext(IO + trackJob) {
                    while (true) {
                        val id = trackData.getString("id")
                        val trackData2 = if (id != trackLink.getId()) {
                            val newLink = Link("https://open.spotify.com/track/$id")
                            getTrackData(newLink)
                        } else {
                            getTrackData(trackLink)
                        }
                        when (trackData2.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val data = JSONObject(trackData2.data.data)
                                    val availableMarkets = data.getJSONArray("available_markets")
                                    playable = availableMarkets.contains(market)
                                            || availableMarkets.contains(defaultMarket)
                                    trackJob.complete()
                                    return@withContext
                                } catch (e: JSONException) {
                                    //JSON broken, try getting the data again
                                    println("Failed JSON:\n${trackData2.data}\n")
                                    println("Failed to get data from JSON, trying again...")
                                }
                            }
                            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                updateToken()
                            }
                            HttpURLConnection.HTTP_NOT_FOUND -> {
                                println("Error 404! $trackLink not found!")
                                playable = false
                                trackJob.complete()
                                return@withContext
                            }
                            HTTP_TOO_MANY_REQUESTS -> {
                                println("Too many requests! Waiting for ${trackData2.data.data} seconds.")
                                //wait for given time before next request.
                                delay(trackData2.data.data.toLong() * 1000)
                            }
                            else -> println("HTTP ERROR! CODE: ${trackData2.code}")
                        }
                    }
                }
                trackJob.join()
                playable
            }
            if (isPlayable)
                println("Track is playable.")
            else
                println("Track isn't playable.")
            return Track(album, artists, title, trackLink, Playability(isPlayable))
        }

        lateinit var track: Track
        val trackJob = Job()
        var isRetry = false
        withContext(IO + trackJob) {
            while (true) {
                val trackData = getTrackData(trackLink, market.ifEmpty { defaultMarket })
                when (trackData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            withContext(Default) {
                                track = parseData(JSONObject(trackData.data.data))
                            }
                            trackJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${trackData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $trackLink not found!")
                        track = Track()
                        trackJob.complete()
                        return@withContext
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${trackData.code}! Bad request!!")
                        track = Track()
                        if (isRetry) {
                            trackJob.complete()
                            return@withContext
                        } else {
                            isRetry = true
                            updateToken()
                        }
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${trackData.data.data} seconds.")
                        //wait for given time before next request. 
                        delay(trackData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${trackData.code}")
                }
            }
        }
        return track
    }

    suspend fun getArtist(artistLink: Link): Artist {
        fun getArtistData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(artistLink.getId())
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getTopTracks(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(artistLink.getId())
            urlBuilder.append("/top-tracks")
            urlBuilder.append("?country=${market.ifEmpty { defaultMarket }}")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getAlbums(offset: Int = 0): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(artistLink.getId())
            urlBuilder.append("/albums")
            urlBuilder.append("?market=${market.ifEmpty { defaultMarket }}")
            //spotify has a max limit of 50 albums per query
            urlBuilder.append("&limit=50")
            urlBuilder.append("&offset=$offset")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getRelatedArtists(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/artists/")
            urlBuilder.append(artistLink.getId())
            urlBuilder.append("/related-artists")
            if (market.isNotEmpty())
                urlBuilder.append("?country=$market")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        suspend fun parseData(
            artistData: JSONObject,
            topTracksData: JSONObject,
            albumsData: JSONObject,
            relatedArtists: JSONObject
        ): Artist {
            val name = Name(artistData.getString("name"))
            val topTracks = ArrayList<Track>()
            val topTracksList = topTracksData.getJSONArray("tracks")
            for (track in topTracksList) {
                track as JSONObject
                val list = ArrayList<Artist>()
                val artists = Artists(track.getJSONArray("artists").mapTo(list) {
                    it as JSONObject
                    Artist(
                        Name(it.getString("name")),
                        Link(it.getJSONObject("external_urls").getString("spotify"))
                    )
                })
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
            val albums = ArrayList<Album>()
            val albumsAmount = albumsData.getInt("total")
            var offset = 0
            var currentAlbumsData = albumsData
            while (albums.size < albumsAmount) {
                albums.addAll(
                    currentAlbumsData.getJSONArray("items").map { data ->
                        data as JSONObject
                        Album(
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
                            TrackList(),
                            Link(data.getJSONObject("external_urls").getString("spotify"))
                        )
                    }
                )
                if (!currentAlbumsData.isNull("next")) {
                    offset += 50
                    val albumsJob = Job()
                    withContext(IO + albumsJob) {
                        while (true) {
                            val albumsResponse = getAlbums(offset)
                            when (albumsResponse.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    try {
                                        currentAlbumsData = JSONObject(albumsResponse.data.data)
                                        return@withContext
                                    } catch (e: JSONException) {
                                        //JSON broken, try getting the data again
                                        println("Failed JSON:\n${albumsResponse.data}\n")
                                        println("Failed to get data from JSON, trying again...")
                                    }
                                }
                                HTTP_TOO_MANY_REQUESTS -> {
                                    println("Too many requests! Waiting for ${albumsResponse.data.data} seconds.")
                                    delay(albumsResponse.data.data.toLong() * 1000)
                                }
                                HttpURLConnection.HTTP_UNAUTHORIZED -> updateToken()
                                else -> println("HTTP ERROR! CODE: ${albumsResponse.code}")
                            }
                        }
                    }
                }
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
            val followers = Followers(artistData.getJSONObject("followers").getInt("total"))
            return Artist(
                name,
                artistLink,
                TrackList(topTracks),
                Artists(related),
                Genres(genres),
                followers,
                Description(), //Maybe some day...
                Albums(albums)
            )
        }

        lateinit var artist: Artist
        val artistJob = Job()
        withContext(IO + artistJob) {
            while (true) {
                val artistData = getArtistData()
                when (artistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            lateinit var topTracks: JSONObject
                            topTracks@ while (true) {
                                val topTracksData = getTopTracks()
                                when (topTracksData.code.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            withContext(Default) {
                                                topTracks = JSONObject(topTracksData.data.data)
                                            }
                                            break@topTracks
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${topTracksData.data.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }
                                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                        updateToken()
                                    }
                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${topTracksData.data.data} seconds.")
                                        //wait for given time before next request.
                                        delay(topTracksData.data.data.toLong() * 1000)
                                    }
                                    else -> println("HTTP ERROR! CODE: ${topTracksData.code.code}")
                                }
                            }
                            lateinit var albums: JSONObject
                            albums@ while (true) {
                                val albumsData = getAlbums()
                                when (albumsData.code.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            withContext(Default) {
                                                albums = JSONObject(albumsData.data.data)
                                            }
                                            break@albums
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${albumsData.data.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }
                                    HttpURLConnection.HTTP_UNAUTHORIZED -> updateToken()
                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${albumsData.data.data} seconds.")
                                        delay(albumsData.data.data.toLong() * 1000)
                                    }
                                    else -> println("HTTP ERROR! CODE: ${albumsData.code.code}")
                                }
                            }
                            lateinit var relatedArtists: JSONObject
                            relatedArtists@ while (true) {
                                val relatedArtistsData = getRelatedArtists()
                                when (relatedArtistsData.code.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            withContext(Default) {
                                                relatedArtists = JSONObject(relatedArtistsData.data.data)
                                            }
                                            break@relatedArtists
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${relatedArtistsData.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }
                                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                        updateToken()
                                    }
                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${relatedArtistsData.data.data} seconds.")
                                        //wait for given time before next request.
                                        delay(relatedArtistsData.data.data.toLong() * 1000)
                                    }
                                    else -> println("HTTP ERROR! CODE: ${relatedArtistsData.code.code}")
                                }
                            }
                            artist = parseData(JSONObject(artistData.data.data), topTracks, albums, relatedArtists)
                            artistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${artistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $artistLink not found!")
                        artist = Artist()
                        artistJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${artistData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(artistData.data.data.toLong() * 1000)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> updateToken()
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${artistData.code}! Bad request!!")
                        artist = Artist()
                        artistJob.complete()
                        return@withContext
                    }
                    else -> println("HTTP ERROR! CODE: ${artistData.code}")
                }
            }
        }
        return artist
    }

    suspend fun getUser(userLink: Link): User {
        lateinit var user: User
        fun getUserData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/users/")
            urlBuilder.append(userLink.getId())
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        fun getUserPlaylistsData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/users/${userLink.getId()}/playlists")
            urlBuilder.append(if (market.isNotEmpty()) "?market=$market" else "?market=$defaultMarket")
            return sendHttpRequest(
                URL(urlBuilder.toString()),
                RequestMethod("GET"),
                ExtraProperties(listOf("Authorization: Bearer $accessToken"))
            )
        }

        suspend fun parseUserData(userData: JSONObject): User {
            val playlists = ArrayList<Playlist>()
            val userPlaylistsJob = Job()
            withContext(IO + userPlaylistsJob) {
                while (true) {
                    val playlistsData = getUserPlaylistsData()
                    when (playlistsData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val data = JSONObject(playlistsData.data.data)
                                playlists.addAll(data.getJSONArray("items").map {
                                    it as JSONObject
                                    Playlist(
                                        Name(it.getString("name")),
                                        User(
                                            Name(it.getJSONObject("owner").getString("display_name")),
                                            Name(it.getJSONObject("owner").getString("id")),
                                            Description(),
                                            Followers(),
                                            emptyList(),
                                            Link(
                                                it.getJSONObject("owner").getJSONObject("external_urls")
                                                    .getString("spotify"), it.getJSONObject("owner").getString("id")
                                            )
                                        ),
                                        Description(it.getString("description")),
                                        Followers(),
                                        Publicity(it.getBoolean("public")),
                                        Collaboration(it.getBoolean("collaborative")),
                                        Link(it.getJSONObject("external_urls").getString("spotify"), it.getString("id"))
                                    )
                                    userPlaylistsJob.complete()
                                    return@withContext
                                })
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${playlistsData.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateToken()
                        }
                        HTTP_TOO_MANY_REQUESTS -> {
                            println("Too many requests! Waiting for ${playlistsData.data.data} seconds.")
                            //wait for given time before next request.
                            delay(playlistsData.data.data.toLong() * 1000)
                        }
                        else -> println("HTTP ERROR! CODE: ${playlistsData.code.code}")
                    }
                }
            }
            return User(
                Name(userData.getString("display_name")),
                Name(userData.getString("id")),
                Description(),
                Followers(userData.getJSONObject("followers").getInt("total")),
                playlists,
                Link(userData.getJSONObject("external_urls").getString("spotify"))
            )
        }

        val userJob = Job()
        withContext(IO + userJob) {
            while (true) {
                val userData = getUserData()
                //check response code
                when (userData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(userData.data.data)
                            user = parseUserData(data)
                            userJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${userData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $userLink not found!")
                        user = User()
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${userData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(userData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${userData.code}")
                }
            }
        }
        return user
    }

    private fun getShowData(showLink: Link): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$apiURL/shows/")
        urlBuilder.append(showLink.getId())
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

            fun getEpisodesData(offset: Int = 0): Response {
                val urlBuilder = StringBuilder()
                urlBuilder.append("$apiURL/shows/")
                urlBuilder.append(showLink.getId())
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
                    }
                    if (!episodesData.isNull("next")) {
                        offset += 50
                        val episodesJob = Job()
                        withContext(episodesJob + IO) {
                            while (true) {
                                val data = getEpisodesData(offset)
                                when (data.code.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            val episodeData = JSONObject(data.data.data)
                                            items = episodeData.getJSONArray("items")
                                            episodesJob.complete()
                                            return@withContext
                                        } catch (e: JSONException) {
                                            //JSON broken, try getting the data again
                                            println("Failed JSON:\n${data.data}\n")
                                            println("Failed to get data from JSON, trying again...")
                                        }
                                    }

                                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                        updateToken()
                                    }

                                    HTTP_TOO_MANY_REQUESTS -> {
                                        println("Too many requests! Waiting for ${data.data.data} seconds.")
                                        //wait for given time before next request.
                                        delay(data.data.data.toLong() * 1000)
                                    }

                                    else -> println("HTTP ERROR! CODE: ${data.code}")
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
                    when (episodesData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val data = JSONObject(episodesData.data.data)
                                episodeList = parseEpisodesData(data)
                                episodeJob.complete()
                                return@withContext
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${episodesData.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateToken()
                        }
                        HTTP_TOO_MANY_REQUESTS -> {
                            println("Too many requests! Waiting for ${episodesData.data.data} seconds.")
                            //wait for given time before next request.
                            delay(episodesData.data.data.toLong() * 1000)
                        }
                        else -> println("HTTP ERROR! CODE: ${episodesData.code.code}")
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
                when (showData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(showData.data.data)
                            show = parseShowData(data)
                            showJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${showData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $showLink not found!")
                        show = Show()
                        showJob.complete()
                        return@withContext
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${showData.code}! Bad request!!")
                        show = Show()
                        showJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${showData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(showData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${showData.code}")
                }
            }
        }
        return show
    }

    suspend fun getEpisode(episodeLink: Link): Episode {
        lateinit var episode: Episode

        fun getEpisodeData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiURL/episodes/")
            urlBuilder.append(episodeLink.getId())
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
                when (episodeData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(episodeData.data.data)
                            episode = parseEpisodeData(data)
                            episodeJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${episodeData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateToken()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $episodeLink not found!")
                        episode = Episode()
                        episodeJob.complete()
                        return@withContext
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${episodeData.code}! Bad request!!")
                        episode = Episode()
                        episodeJob.complete()
                        return@withContext
                    }
                    HTTP_TOO_MANY_REQUESTS -> {
                        println("Too many requests! Waiting for ${episodeData.data.data} seconds.")
                        //wait for given time before next request.
                        delay(episodeData.data.data.toLong() * 1000)
                    }
                    else -> println("HTTP ERROR! CODE: ${episodeData.code}")
                }
            }
        }
        return episode
    }

}
