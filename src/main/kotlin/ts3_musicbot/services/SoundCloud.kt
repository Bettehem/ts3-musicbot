package ts3_musicbot.services

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

class SoundCloud {
    var clientId = "NpVHurnc1OKS80l6zlXrEVN4VEXrbZG4"
    private val commandRunner = CommandRunner()
    private val api2URL = URL("https://api-v2.soundcloud.com")
    val apiURL = URL("https://api.soundcloud.com")
    val supportedSearchTypes = listOf(
        SearchType.Type.TRACK,
        SearchType.Type.PLAYLIST,
        SearchType.Type.ALBUM,
        SearchType.Type.USER
    )

    /**
     * Updates the clientId
     * @return returns the new id
     */
    fun updateClientId(): String {
        println("Updating SoundCloud ClientId")
        val lines = commandRunner.runCommand(
            "curl https://soundcloud.com 2> /dev/null | grep -E \"<script crossorigin src=\\\"https:\\/\\/\\S*\\.js\\\"></script>\"",
            printOutput = false
        ).first.outputText.lines()
        for (line in lines) {
            val url = line.substringAfter("\"").substringBefore("\"")
            val data = commandRunner.runCommand(
                "curl $url 2> /dev/null | grep \"client_id=\"",
                printOutput = false,
                printErrors = false
            ).first.outputText
            if (data.isNotEmpty()) {
                val id = data.substringAfter("client_id=").substringBefore("&")
                clientId = id
                break
            }
        }
        return clientId
    }

    suspend fun searchSoundCloud(searchType: SearchType, searchQuery: SearchQuery): SearchResults {
        val searchResults = ArrayList<SearchResult>()
        fun searchData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/search/")
            urlBuilder.append("${searchType}s?")
            urlBuilder.append("q=${URLEncoder.encode(searchQuery.query, Charsets.UTF_8.toString())}")
            urlBuilder.append("&limit=10")
            urlBuilder.append("&client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        fun parseResults(searchData: JSONObject) {
            when (searchType.type) {
                "track" -> {
                    val tracks = searchData.getJSONArray("collection")
                    for (trackData in tracks) {
                        trackData as JSONObject

                        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                        val releaseDate = ReleaseDate(LocalDate.parse(trackData.getString("created_at"), formatter))
                        val track = Track(
                            Album(releaseDate = releaseDate),
                            Artists(
                                listOf(
                                    Artist(
                                        Name(trackData.getJSONObject("user").getString("username")),
                                        Link(trackData.getJSONObject("user").getString("permalink_url"))
                                    )
                                )
                            ),
                            Name(trackData.getString("title")),
                            Link(trackData.getString("permalink_url")),
                            Playability(trackData.getBoolean("streamable"))
                        )
                        searchResults.add(
                            SearchResult(
                                "Upload Date:   \t${track.album.releaseDate.date}\n" +
                                        "Uploader: \t\t\t${track.artists.toShortString()}\n" +
                                        "Track Title:   \t\t${track.title}\n" +
                                        "Track Link:    \t\t${track.link}\n"
                            )
                        )
                    }
                }
                "playlist" -> {
                    val playlists = searchData.getJSONArray("collection")
                    for (playlistData in playlists) {
                        playlistData as JSONObject

                        val playlist = Playlist(
                            Name(playlistData.getString("title")),
                            User(
                                Name(playlistData.getJSONObject("user").getString("username")),
                                Name(playlistData.getJSONObject("user").getString("permalink")),
                                Description(
                                    if (!playlistData.getJSONObject("user").isNull("description"))
                                        playlistData.getJSONObject("user").getString("description")
                                    else
                                        ""
                                ),
                                Followers(playlistData.getJSONObject("user").getInt("followers_count")),
                                Link(playlistData.getJSONObject("user").getString("permalink_url"))
                            ),
                            Description(if (!playlistData.isNull("description")) playlistData.getString("description") else ""),
                            Followers(playlistData.getInt("likes_count")),
                            Publicity(playlistData.getString("sharing") == "public"),
                            Collaboration(false),
                            Link(playlistData.getString("permalink_url"))
                        )
                        val trackAmount = playlistData.getInt("track_count")
                        searchResults.add(
                            SearchResult(
                                "Playlist:   \t${playlist.name}\n" +
                                        "Owner:    \t${playlist.owner.name}\n" +
                                        "Tracks:    \t$trackAmount\n" +
                                        "Link:     \t\t${playlist.link}\n"
                            )
                        )
                    }
                }

                "album" -> {
                    val albums = searchData.getJSONArray("collection")
                    for (albumData in albums) {
                        albumData as JSONObject

                        val album = Album(
                            Name(albumData.getString("title")),
                            Artists(
                                listOf(
                                    Artist(
                                        Name(albumData.getJSONObject("user").getString("username")),
                                        Link(
                                            albumData.getJSONObject("user").getString("permalink_url"),
                                            albumData.getJSONObject("user").getInt("id").toString()
                                        )
                                    )
                                )
                            ),
                            ReleaseDate(
                                LocalDate.parse(
                                    albumData.getString("published_at"),
                                    DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                                )
                            ),
                            TrackList(albumData.getJSONArray("tracks").map {
                                it as JSONObject
                                Track(link = Link(linkId = it.getInt("id").toString()))
                            }),
                            Link(albumData.getString("permalink_url"), albumData.getInt("id").toString()),
                            Genres(listOf(albumData.getString("genre")))
                        )

                        searchResults.add(
                            SearchResult(
                                "Album:   \t${album.name}\n" +
                                        "Artist:     \t${album.artists.toShortString()}\n" +
                                        "Tracks:    \t${album.tracks.trackList.size}\n" +
                                        "Link:     \t\t${album.link}\n"
                            )
                        )
                    }
                }

                "user" -> {
                    val users = searchData.getJSONArray("collection")
                    for (userData in users) {
                        userData as JSONObject

                        val user = User(
                            Name(userData.getString("username")),
                            Name(userData.getString("permalink")),
                            Description(userData.getString("description")),
                            Followers(userData.getInt("followers_count")),
                            Link(userData.getString("permalink_url"))
                        )
                        searchResults.add(SearchResult(user.toString()))
                    }
                }
            }
        }

        println("Searching for \"$searchQuery\"")
        val searchJob = Job()
        withContext(IO + searchJob) {
            while (true) {
                val searchData = searchData()
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
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> updateClientId()
                    else -> println("HTTP ERROR! CODE ${searchData.code}")
                }
            }
        }

        return SearchResults(searchResults)
    }

    /**
     * Get tracks from a SoundCloud playlist
     * @param link SoundCloud playlist link
     * @return returns an ArrayList containing the playlist's tracks as Track objects
     */
    suspend fun getPlaylistTracks(link: Link): TrackList {
        suspend fun getTracks(): Response {
            val id = resolveId(link)
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/${if (id.startsWith("soundcloud:system-playlists")) "system-playlists" else "playlists"}/")
            urlBuilder.append(id)
            urlBuilder.append("?client_id=$clientId")
            @Suppress("BlockingMethodInNonBlockingContext")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val trackList = ArrayList<Track>()
        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val trackData = getTracks()
                when (trackData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksJSON = JSONObject(trackData.data.data).getJSONArray("tracks")
                            if (tracksJSON.length() > 50) {
                                println("This playlist has ${tracksJSON.length()} tracks. Please wait...")
                            }
                            val apiLinks = tracksJSON.map {
                                it as JSONObject
                                Link("$apiURL/tracks/${it.getInt("id")}", it.getInt("id").toString())
                            }
                            val tracks = getMultipleTracks(apiLinks)
                            val sortedList = async {
                                val newList = ArrayList<Track>()
                                val idList = apiLinks.map { it.getId() }
                                for (originalId in idList) {
                                    for (track in tracks.trackList) {
                                        if (track.link.getId() == originalId) {
                                            newList.add(track)
                                            break
                                        }
                                    }
                                }
                                newList
                            }
                            trackList.addAll(sortedList.await())
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${trackData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    else -> println("HTTP ERROR! CODE ${trackData.code}")
                }
            }
        }
        return TrackList(trackList)
    }

    /**
     * Get a Track object for a given SoundCloud song link
     * @param link link to the song
     * @return returns a Track object with uploader, title and link
     */
    suspend fun getTrack(link: Link): Track {
        lateinit var track: Track

        suspend fun fetchTrackData(): Response {
            val id = if (link.link.startsWith("$apiURL/tracks/"))
                link.link.substringAfterLast("/")
            else
                resolveId(link)

            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/tracks/$id")
            urlBuilder.append("?client_id=$clientId")
            @Suppress("BlockingMethodInNonBlockingContext")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        fun parseTrackData(trackData: JSONObject): Track {
            val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
            val releaseDate = ReleaseDate(LocalDate.parse(trackData.getString("created_at"), formatter))
            return Track(
                Album(releaseDate = releaseDate),
                Artists(
                    listOf(
                        Artist(
                            Name(trackData.getJSONObject("user").getString("username")),
                            Link(trackData.getJSONObject("user").getString("permalink_url"))
                        )
                    )
                ),
                Name(trackData.getString("title")),
                Link(trackData.getString("permalink_url"), trackData.getInt("id").toString()),
                Playability(trackData.getBoolean("streamable"))
            )
        }

        val trackJob = Job()
        withContext(IO + trackJob) {
            while (true) {
                val trackData = fetchTrackData()
                when (trackData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(trackData.data.data)
                            track = parseTrackData(data)
                            trackJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${trackData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $link not found!")
                        track = Track()
                        trackJob.complete()
                        return@withContext
                    }
                    else -> println("HTTP ERROR! CODE ${trackData.code}")
                }
            }
        }
        return track
    }

    suspend fun getMultipleTracks(links: List<Link>): TrackList {
        @Suppress("BlockingMethodInNonBlockingContext")
        suspend fun fetchTracksData(trackLinks: List<Link>): Response {
            val idsBuilder = StringBuilder()
            for (link in trackLinks) {
                idsBuilder.append(
                    if (link.link.startsWith("$apiURL/tracks/")) {
                        link.link.substringAfterLast("/")
                    } else {
                        resolveId(link)
                    } + ","
                )
            }
            val ids = URLEncoder.encode(idsBuilder.toString().substringBeforeLast(","), Charsets.UTF_8.toString())
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/tracks?ids=$ids")
            urlBuilder.append("&client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        fun parseTracksData(tracksData: JSONArray): TrackList {
            val trackList = ArrayList<Track>()
            for (trackData in tracksData) {
                trackData as JSONObject
                val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                val releaseDate = ReleaseDate(LocalDate.parse(trackData.getString("created_at"), formatter))
                trackList.add(
                    Track(
                        Album(releaseDate = releaseDate),
                        Artists(
                            listOf(
                                Artist(
                                    Name(trackData.getJSONObject("user").getString("username")),
                                    Link(trackData.getJSONObject("user").getString("permalink_url"))
                                )
                            )
                        ),
                        Name(trackData.getString("title")),
                        Link(trackData.getString("permalink_url"), trackData.getInt("id").toString()),
                        Playability(trackData.getBoolean("streamable"))
                    )
                )
            }
            return TrackList(trackList)
        }

        val tracksJob = Job()
        val tracks = CoroutineScope(IO + tracksJob).async {
            val trackList = ArrayList<Track>()
            withContext(IO + tracksJob) {
                val linksToFetch = ArrayList<List<Link>>()
                var list = ArrayList<Link>()
                for (link in links) {
                    //create lists of 50 links, because SoundCloud limits searching to 50 items at a time
                    if (list.size < 50)
                        list.add(link)
                    else {
                        linksToFetch.add(list)
                        list = ArrayList()
                        list.add(link)
                    }
                }
                linksToFetch.add(list)
                for (linksList in linksToFetch) {
                    launch {
                        while (true) {
                            val tracksData = fetchTracksData(linksList)
                            when (tracksData.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    try {
                                        val data = JSONArray(tracksData.data.data)
                                        synchronized(trackList) {
                                            trackList.addAll(parseTracksData(data).trackList)
                                        }
                                        return@launch
                                    } catch (e: JSONException) {
                                        //JSON broken, try getting the data again
                                        println("Failed JSON:\n${tracksData.data}\n")
                                        println("Failed to get data from JSON, trying again...")
                                    }
                                }
                                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                    updateClientId()
                                }
                                HttpURLConnection.HTTP_NOT_FOUND -> {
                                    println("Error 404! Link not found!")
                                    return@launch
                                }
                                else -> println("HTTP ERROR! CODE ${tracksData.code}")
                            }
                        }
                    }
                }
            }
            TrackList(trackList)
        }
        return tracks.await()
    }

    suspend fun getUser(link: Link): User {
        lateinit var user: User
        suspend fun fetchUserData(): Response {
            val id = if (link.link.startsWith("$apiURL/users/"))
                link.link.substringAfterLast("/")
            else
                resolveId(link)
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/users/$id")
            urlBuilder.append("?client_id=$clientId")
            @Suppress("BlockingMethodInNonBlockingContext")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        fun parseUserData(userData: JSONObject): User {
            return User(
                Name(userData.getString("username")),
                Name(userData.getString("permalink")),
                Description(userData.getString("description")),
                Followers(userData.getInt("followers_count")),
                Link(userData.getString("permalink_url"))
            )
        }

        val userJob = Job()
        withContext(IO + userJob) {
            while (true) {
                val userData = fetchUserData()
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
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE: ${userData.code}")
                }
            }
        }
        return user
    }

    /**
     * Resolves the given SoundCloud link and returns it's id
     * @param link link to resolve
     * @return returns the corresponding id for the given link as a String
     */
    suspend fun resolveId(link: Link): String {
        fun fetchIdData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/resolve?")
            urlBuilder.append("client_id=$clientId")
            urlBuilder.append("&url=${link.link}")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val resolveJob = Job()
        val deferredId = CoroutineScope(IO + resolveJob).async {
            lateinit var id: String
            while (true) {
                val idData = fetchIdData()
                when (idData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(idData.data.data)
                            when (data.get("id")) {
                                is String -> id = data.getString("id")
                                is Int -> id = data.getInt("id").toString()
                            }
                            break
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${idData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $idData not found!")
                        id = ""
                        break
                    }
                    else -> println("HTTP ERROR! CODE ${idData.code}")
                }
            }
            resolveJob.complete()
            id

        }
        return deferredId.await()
    }
}
