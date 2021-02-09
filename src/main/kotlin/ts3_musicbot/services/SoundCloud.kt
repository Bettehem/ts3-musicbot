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

class SoundCloud {
    var clientId = "NpVHurnc1OKS80l6zlXrEVN4VEXrbZG4"
    private val commandRunner = CommandRunner()
    private val api2URL = URL("https://api-v2.soundcloud.com")
    val apiURL = URL("https://api.soundcloud.com")
    val supportedSearchTypes = listOf(
        SearchType.Type.TRACK,
        SearchType.Type.PLAYLIST,
        SearchType.Type.ALBUM,
        SearchType.Type.USER,
        SearchType.Type.ARTIST
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
            urlBuilder.append(if (searchType.type == "artist") "users" else "${searchType}s")
            urlBuilder.append("?q=${URLEncoder.encode(searchQuery.query, Charsets.UTF_8.toString())}")
            urlBuilder.append("&limit=10")
            urlBuilder.append("&client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        suspend fun parseResults(searchData: JSONObject) {
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
                                fetchUserPlaylists(
                                    Link(
                                        "$apiURL/users/${
                                            playlistData.getJSONObject("user").getInt("id")
                                        }"
                                    )
                                ),
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
                                    if (!albumData.isNull("published_at")) {
                                        albumData.getString("published_at")
                                    } else {
                                        albumData.getString("display_date")
                                    },
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
                            Description(if (!userData.isNull("description")) userData.getString("description") else ""),
                            Followers(userData.getInt("followers_count")),
                            fetchUserPlaylists(Link("$apiURL/users/${userData.getInt("id")}")),
                            Link(userData.getString("permalink_url"))
                        )
                        searchResults.add(
                            SearchResult(
                                "Name:   \t\t\t${user.name}\n" +
                                        "Username:   \t${user.userName}\n" +
                                        if (user.description.isNotEmpty()) {
                                            "Description:\n${user.description}\n"
                                        } else {
                                            ""
                                        } +
                                        "Followers:  \t${user.followers}\n" +
                                        "Link:      \t\t\t${user.link}\n"
                            )
                        )
                    }
                }

                "artist" -> {
                    val artists = searchData.getJSONArray("collection").filter {
                        it as JSONObject
                        it.getInt("track_count") > 0
                    }
                    for (artistData in artists) {
                        artistData as JSONObject

                        val artist = Artist(
                            Name(artistData.getString("username")),
                            Link(artistData.getString("permalink_url")),
                            followers = Followers(artistData.getInt("followers_count")),
                            description = Description(
                                if (!artistData.isNull("description"))
                                    artistData.getString("description")
                                else
                                    ""
                            )
                        )

                        searchResults.add(
                            SearchResult(
                                "Artist:    \t\t\t${artist.name}\n" +
                                        if (artist.description.isNotEmpty()) {
                                            "Description:\n${artist.description}\n"
                                        } else {
                                            ""
                                        } +
                                        "Followers:  \t${artist.followers}\n" +
                                        "Link:      \t\t\t${artist.link}\n"
                            )
                        )
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

    private suspend fun fetchPlaylistData(link: Link): Response {
        val id = resolveId(link)
        val urlBuilder = StringBuilder()
        urlBuilder.append("$api2URL/${if (id.startsWith("soundcloud:system-playlists")) "system-playlists" else "playlists"}/")
        urlBuilder.append(id)
        urlBuilder.append("?client_id=$clientId")
        @Suppress("BlockingMethodInNonBlockingContext")
        return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
    }

    suspend fun getPlaylist(link: Link): Playlist {
        lateinit var playlist: Playlist
        fun parsePlaylistData(playlistData: JSONObject): Playlist {
            return Playlist(
                Name(playlistData.getString("title")),
                User(
                    Name(playlistData.getJSONObject("user").getString("username")),
                    Name(playlistData.getJSONObject("user").getString("permalink")),
                    link = Link(
                        playlistData.getJSONObject("user").getString("permalink_url"),
                        playlistData.getJSONObject("user").getInt("id").toString()
                    )
                ),
                Description(playlistData.getString("description")),
                Followers(playlistData.getInt("likes_count")),
                Publicity(playlistData.getBoolean("public")),
                Collaboration(false),
                Link(
                    playlistData.getJSONObject("user").getString("permalink_url"),
                    if (playlistData.get("id") is String) {
                        playlistData.getJSONObject("user").getString("id")
                    } else {
                        playlistData.getJSONObject("user").getInt("id").toString()
                    }
                )
            )
        }

        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = fetchPlaylistData(link)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlistJSON = JSONObject(playlistData.data.data)
                            playlist = parsePlaylistData(playlistJSON)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.data.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    else -> println("HTTP ERROR! CODE ${playlistData.code.code}")
                }
            }
        }
        return playlist
    }

    /**
     * Get tracks from a SoundCloud playlist
     * @param link SoundCloud playlist link
     * @return returns a TrackList containing the playlist's tracks
     */
    suspend fun getPlaylistTracks(link: Link): TrackList {
        val trackList = ArrayList<Track>()
        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = fetchPlaylistData(link)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksJSON = JSONObject(playlistData.data.data).getJSONArray("tracks")
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
                            println("Failed JSON:\n${playlistData.data.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    else -> println("HTTP ERROR! CODE ${playlistData.code.code}")
                }
            }
        }
        return TrackList(trackList)
    }

    private fun fetchAlbumData(id: String): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$api2URL/${if (id.startsWith("soundcloud:system-playlists")) "system-playlists" else "playlists"}/")
        urlBuilder.append(id)
        urlBuilder.append("?client_id=$clientId")
        return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
    }

    suspend fun fetchAlbum(link: Link): Album {
        val id = if (link.link.startsWith("$apiURL/"))
            link.link.substringAfterLast("/")
        else
            resolveId(link)
        val albumJob = Job()
        return withContext(IO + albumJob) {
            lateinit var album: Album
            while (true) {
                val albumData = fetchAlbumData(id)
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val albumJSON = JSONObject(albumData.data.data)
                            album = Album(
                                Name(albumJSON.getString("title")),
                                Artists(
                                    listOf(
                                        Artist(
                                            Name(albumJSON.getJSONObject("user").getString("username")),
                                            Link(
                                                albumJSON.getJSONObject("user").getString("permalink_url"),
                                                albumJSON.getJSONObject("user").getInt("id").toString()
                                            )
                                        )
                                    )
                                ),
                                ReleaseDate(
                                    LocalDate.parse(
                                        albumJSON.getString("release_date"),
                                        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                                    )
                                ),
                                fetchAlbumTracks(link),

                                )
                            break
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.data.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.code.code}")
                }
            }
            albumJob.complete()
            album
        }
    }

    suspend fun fetchAlbumTracks(link: Link): TrackList {
        val id = if (link.link.startsWith("$apiURL/"))
            link.link.substringAfterLast("/")
        else
            resolveId(link)

        val albumJob = Job()
        return withContext(IO + albumJob) {
            val trackList = ArrayList<Track>()
            while (true) {
                val albumData = fetchAlbumData(id)
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksData = JSONObject(albumData.data.data).getJSONArray("tracks")
                            val apiLinks = tracksData.map {
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
                            break
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.data.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    else -> println("HTTP ERROR! CODE ${albumData.code.code}")
                }
            }
            TrackList(trackList)
        }
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
                Playability(trackData.getBoolean("streamable")),
                Likes(trackData.getInt("likes_count"))
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

    private suspend fun getMultipleTracks(links: List<Link>): TrackList {
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

    private suspend fun fetchUserPlaylists(userLink: Link): List<Playlist> {
        suspend fun fetchData(): Response {
            val id = if (userLink.link.startsWith("$apiURL/users/"))
                userLink.link.substringAfterLast("/")
            else
                resolveId(userLink)
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/users/$id/playlists")
            urlBuilder.append("?client_id=$clientId")
            @Suppress("BlockingMethodInNonBlockingContext")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val playlists = ArrayList<Playlist>()
        val playlistsJob = Job()
        withContext(IO + playlistsJob) {
            while (true) {
                val playlistsData = fetchData()
                when (playlistsData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(playlistsData.data.data)
                            playlists.addAll(
                                data.getJSONArray("collection").map {
                                    it as JSONObject
                                    Playlist(
                                        Name(it.getString("title")),
                                        User(),
                                        Description(if (!it.isNull("description")) it.getString("description") else ""),
                                        Followers(it.getInt("likes_count")),
                                        Publicity(it.getBoolean("public")),
                                        Collaboration(false),
                                        Link(
                                            it.getString("permalink_url"),
                                            if (it.get("id") is Int)
                                                it.getInt("id").toString()
                                            else
                                                it.getString("id")
                                        )
                                    )
                                }
                            )
                            playlistsJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistsData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                }
            }
        }
        return playlists
    }

    /**
     * Fetch a SoundCloud user's uploaded tracks
     * @param userId user's id
     * @param tracksAmount the amount of tracks the user has uploaded. This is used for the `limit=` parameter in the http post
     * @return returns a TrackList containing the user's tracks
     */
    private suspend fun fetchUserTracks(userId: String, tracksAmount: Int): TrackList {
        fun fetchTracksData(): Response {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/users/$userId/tracks")
            urlBuilder.append("?limit=$tracksAmount")
            urlBuilder.append("&client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }
        return if (tracksAmount > 0) {
            val tracksJob = Job()
            val list = CoroutineScope(IO + tracksJob).async {
                val trackList = ArrayList<Track>()
                while (true) {
                    val tracksData = fetchTracksData()
                    when (tracksData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val data = JSONObject(tracksData.data.data)
                                trackList.addAll(data.getJSONArray("collection").map {
                                    it as JSONObject
                                    Track(
                                        Album(
                                            Name(
                                                if (
                                                    !it.isNull("publisher_metadata") &&
                                                    it.getJSONObject("publisher_metadata").has("album_title") &&
                                                    !it.getJSONObject("publisher_metadata").isNull("album_title")
                                                )
                                                    it.getJSONObject("publisher_metadata").getString("album_title")
                                                else
                                                    ""
                                            )
                                        ),
                                        Artists(
                                            listOf(
                                                Artist(
                                                    Name(it.getJSONObject("user").getString("username")),
                                                    Link(
                                                        it.getJSONObject("user").getString("permalink_url"),
                                                        it.getJSONObject("user").getInt("id").toString()
                                                    )
                                                )
                                            )
                                        ),
                                        Name(it.getString("title")),
                                        Link(it.getString("permalink_url"), it.getInt("id").toString()),
                                        Playability(it.getBoolean("streamable")),
                                        Likes(it.getInt("likes_count"))
                                    )
                                })
                                tracksJob.complete()
                                break
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${tracksData.data.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }

                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateClientId()
                        }

                        else -> println("HTTP ERROR! CODE: ${tracksData.code.code}")
                    }
                }
                TrackList(trackList)
            }
            list.await()
        } else {
            TrackList(emptyList())
        }
    }

    private fun fetchUserData(userId: String): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$api2URL/users/$userId")
        urlBuilder.append("?client_id=$clientId")
        return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
    }

    suspend fun fetchUser(link: Link): User {
        lateinit var user: User
        val id = if (link.link.startsWith("$apiURL/users/"))
            link.link.substringAfterLast("/")
        else
            resolveId(link)


        suspend fun parseUserData(userData: JSONObject): User {
            return User(
                Name(userData.getString("username")),
                Name(userData.getString("permalink")),
                Description(if (!userData.isNull("description")) userData.getString("description") else ""),
                Followers(userData.getInt("followers_count")),
                fetchUserPlaylists(Link("$apiURL/users/$id")),
                Link(userData.getString("permalink_url"))
            )
        }

        val userJob = Job()
        withContext(IO + userJob) {
            while (true) {
                val userData = fetchUserData(id)
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

    //SoundCloud doesn't have "Artists", but usually users with tracks uploaded, happen to be artists,
    //therefore we just fetch an User's data, and present it as an Artist
    suspend fun fetchArtist(link: Link): Artist {
        lateinit var artist: Artist
        val id = if (link.link.startsWith("$apiURL/users/"))
            link.link.substringAfterLast("/")
        else
            resolveId(link)
        val artistJob = Job()

        suspend fun parseArtistData(artistData: JSONObject) {
            fun fetchRelatedArtistsData(): Response {
                val urlBuilder = StringBuilder()
                urlBuilder.append("$api2URL/users/$id/relatedartists")
                urlBuilder.append("?client_id=$clientId")
                return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
            }

            val artistsTracks = fetchUserTracks(id, artistData.getInt("track_count"))
            val topTracks = ArrayList<Track>()
            artistsTracks.trackList.sortedByDescending {
                it.likes.amount
            }.forEach {
                if (topTracks.size < 10)
                    topTracks.add(it)
            }
            val relatedArtistsJob = Job()
            val relatedArtists = withContext(IO + relatedArtistsJob) {
                val artists = ArrayList<Artist>()
                while (true) {
                    val artistsData = fetchRelatedArtistsData()
                    when (artistsData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val data = JSONObject(artistsData.data.data)
                                artists.addAll(data.getJSONArray("collection").map {
                                    it as JSONObject
                                    Artist(
                                        Name(it.getString("username")),
                                        Link(it.getString("permalink_url"), it.getInt("id").toString())
                                    )
                                })
                                break
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${artistsData.data}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }

                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateClientId()
                        }

                        else -> println("HTTP ERROR! CODE: ${artistsData.code.code}")
                    }
                }
                relatedArtistsJob.complete()
                artists
            }
            artist = Artist(
                Name(artistData.getString("username")),
                Link(artistData.getString("permalink_url"), artistData.getInt("id").toString()),
                TrackList(topTracks),
                Artists(relatedArtists),
                followers = Followers(artistData.getInt("followers_count")),
                description = Description(if (!artistData.isNull("description")) artistData.getString("description") else "")
            )
        }

        withContext(IO + artistJob) {
            while (true) {
                val artistData = fetchUserData(id)
                when (artistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(artistData.data.data)
                            parseArtistData(data)
                            artistJob.complete()
                            return@withContext
                        } catch (e: Exception) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${artistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE: ${artistData.code.code}")
                }
            }
        }
        return artist
    }

    private fun fetchResolvedData(link: Link): Response {
        val urlBuilder = StringBuilder()
        urlBuilder.append("$api2URL/resolve?")
        urlBuilder.append("client_id=$clientId")
        urlBuilder.append("&url=${link.link}")
        return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
    }

    suspend fun resolveType(link: Link): String {
        val resolveJob = Job()
        val deferredType = CoroutineScope(IO + resolveJob).async {
            lateinit var type: String
            while (true) {
                val typeData = fetchResolvedData(link)
                when (typeData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(typeData.data.data)
                            type = when (val kind = data.getString("kind")) {
                                "playlist" -> {
                                    if (data.getBoolean("is_album"))
                                        "album"
                                    else
                                        kind
                                }
                                "user" -> {
                                    if (data.getInt("track_count") > 0)
                                        "artist"
                                    else
                                        kind
                                }
                                else -> kind
                            }
                            resolveJob.complete()
                            break
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${typeData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $typeData not found!")
                        type = ""
                        break
                    }
                    else -> println("HTTP ERROR! CODE ${typeData.code.code}")
                }
            }
            type
        }
        return deferredType.await()
    }

    /**
     * Resolves the given SoundCloud link and returns it's id
     * @param link link to resolve
     * @return returns the corresponding id for the given link as a String
     */
    suspend fun resolveId(link: Link): String {
        val resolveJob = Job()
        val deferredId = CoroutineScope(IO + resolveJob).async {
            lateinit var id: String
            while (true) {
                val idData = fetchResolvedData(link)
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
