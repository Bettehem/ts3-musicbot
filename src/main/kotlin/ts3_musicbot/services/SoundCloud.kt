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

class SoundCloud : Service(ServiceType.SOUNDCLOUD) {
    var clientId = "f8eB0B44lHhYd7Kvli0IGN2ykOUAscWj"
    private val api2URL = URL("https://api-v2.soundcloud.com")
    val apiURL = URL("https://api.soundcloud.com")
    val supportedSearchTypes = listOf(
        LinkType.TRACK,
        LinkType.PLAYLIST,
        LinkType.ALBUM,
        LinkType.USER,
        LinkType.ARTIST
    )

    /**
     * Updates the clientId
     * @return returns the new id
     */
    fun updateClientId(): String {
        println("Updating SoundCloud ClientId")
        val lines = sendHttpRequest(Link("https://soundcloud.com")).data.data.lines()
            .filter { it.contains("^<script crossorigin src=\"https://\\S+\\.js\"></script>".toRegex()) }
        for (line in lines)
            sendHttpRequest(Link(line.substringAfter('"').substringBefore('"')))
                .data.data.let { data ->
                    if (data.contains("client_id=[0-9A-z-_]+\"".toRegex())) {
                        val idLine = data.lines().first { it.contains("client_id=[0-9A-z-_]+\"".toRegex()) }
                        val id = idLine.replace("^.*client_id=".toRegex(), "").replace("(&|\"?\\),).*$".toRegex(), "")
                        synchronized(clientId) { clientId = id }
                    }
                }
        return clientId
    }

    override suspend fun search(searchType: SearchType, searchQuery: SearchQuery, resultLimit: Int): SearchResults {
        val searchResults = ArrayList<SearchResult>()
        fun searchData(limit: Int = resultLimit, offset: Int = 0, link: Link = Link()): Response {
            val linkBuilder = StringBuilder()
            if (link.isNotEmpty()) {
                linkBuilder.append("$link")
            } else {
                linkBuilder.append("$api2URL/search/")
                linkBuilder.append("${searchType.type.replace("artist", "user")}s")
                linkBuilder.append("?q=${URLEncoder.encode(searchQuery.query, Charsets.UTF_8.toString())}")
                linkBuilder.append("&limit=$limit")
                linkBuilder.append("&offset=$offset")
                linkBuilder.append("&client_id=$clientId")
            }
            return sendHttpRequest(Link(linkBuilder.toString()))
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
                            Playability(trackData.getBoolean("streamable") && trackData.getString("policy") != "BLOCK")
                        )
                        searchResults.add(
                            SearchResult(
                                "Upload Date:   \t${track.album.releaseDate.date}\n" +
                                        "Uploader: \t\t\t${track.artists.toShortString()}\n" +
                                        "Track Title:   \t\t${track.title}\n" +
                                        "Track Link:    \t\t${track.link}\n",
                                track.link
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
                                link = Link(playlistData.getJSONObject("user").getString("permalink_url"))
                            ),
                            Description(if (!playlistData.isNull("description")) playlistData.getString("description") else ""),
                            Followers(if (!playlistData.isNull("likes_count")) playlistData.getInt("likes_count") else Followers().amount),
                            Publicity(playlistData.getString("sharing") == "public"),
                            Collaboration(false),
                            TrackList(List(playlistData.getInt("track_count")) { Track() }),
                            Link(playlistData.getString("permalink_url"))
                        )
                        searchResults.add(
                            SearchResult(
                                "Playlist:   \t${playlist.name}\n" +
                                        "Owner:    \t${playlist.owner.name}\n" +
                                        "Tracks:    \t${playlist.tracks.size}\n" +
                                        "Link:     \t\t${playlist.link}\n",
                                playlist.link
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
                                        "Link:     \t\t${album.link}\n",
                                album.link
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
                            SearchResult("\n" +
                                    "Name:   \t\t\t${user.name}\n" +
                                    "Username:   \t${user.userName}\n" +
                                    "Description:\n${
                                        user.description.ifNotEmpty {
                                            if (it.lines().size <= 5)
                                                it
                                            else
                                                Description(
                                                    "WARNING! Very long description! Showing only the first 5 lines:\n" +
                                                            it.lines().subList(0, 5).joinToString("\n")
                                                )
                                        }
                                    }\n" +
                                    "Followers:  \t${user.followers}\n" +
                                    "Link:      \t\t\t${user.link}\n",
                                user.link
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
                                        "Description:\n${
                                            artist.description.ifNotEmpty {
                                                if (it.lines().size <= 5)
                                                    it
                                                else
                                                    Description(
                                                        "WARNING! Very long description! Showing only first 5 lines\n" +
                                                                it.lines().subList(0, 5).joinToString("\n")
                                                    )
                                            }
                                        }\n" +
                                        "Followers:  \t${artist.followers}\n" +
                                        "Link:      \t\t\t${artist.link}\n",
                                artist.link
                            )
                        )
                    }
                }
            }
        }

        println("Searching for \"$searchQuery\"")
        val searches = ArrayList<Pair<Int, Int>>()
        var remainingResults = resultLimit
        var resultOffset = 0
        //SoundCloud allows a maximum of 200 results, so we have to do searches in smaller chunks in case the user wants more than 200 results.
        val maxResults = 200
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
                                searchData = searchData(link = result.link)
                            }
                        }

                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateClientId()
                            searchData = searchData(
                                link = Link(
                                    result.link.toString()
                                        .replace("client_id=[a-zA-Z0-9]+".toRegex(), "client_id=$clientId")
                                )
                            )
                        }

                        else -> {
                            println("HTTP ERROR! CODE ${searchData.code}")
                        }
                    }
                }
            }
        }

        return SearchResults(searchResults)
    }

    private suspend fun fetchPlaylistData(link: Link): Response {
        val id = resolveId(link)
        val linkBuilder = StringBuilder()
        linkBuilder.append(
            "$api2URL/${
                if (id.startsWith("soundcloud:system-playlists"))
                    "system-playlists"
                else
                    "playlists"
            }/"
        )
        linkBuilder.append(id)
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun fetchPlaylist(playlistLink: Link): Playlist {
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
                if (!playlistData.isNull("likes_count")) {
                    Followers(playlistData.getInt("likes_count"))
                } else {
                    Followers()
                },
                Publicity(playlistData.getBoolean("public")),
                tracks = TrackList(List(playlistData.getInt("track_count")) { Track() }),
                link = Link(
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
                val playlistData = fetchPlaylistData(playlistLink)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlistJSON = JSONObject(playlistData.data.data)
                            playlist = parsePlaylistData(playlistJSON)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${playlistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE ${playlistData.code}")
                }
            }
        }
        return playlist
    }

    /**
     * Fetch tracks from a SoundCloud playlist
     * @param playlistLink SoundCloud playlist link
     * @param limit set a limit to the amount of tracks to return
     * @return returns a TrackList containing the playlist's tracks
     */
    override suspend fun fetchPlaylistTracks(playlistLink: Link, limit: Int): TrackList {
        val trackList = ArrayList<Track>()
        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = fetchPlaylistData(playlistLink)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksJSON = JSONObject(playlistData.data.data).getJSONArray("tracks")
                            val apiLinks = tracksJSON.map {
                                it as JSONObject
                                Link("$apiURL/tracks/${it.getInt("id")}", it.getInt("id").toString())
                            }
                            val tracks = fetchMultipleTracks(
                                if (limit != 0 && apiLinks.size > limit) apiLinks.subList(0, limit) else apiLinks
                            )
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
                            println("Failed JSON:\n${playlistData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE ${playlistData.code}")
                }
            }
        }
        return TrackList(trackList)
    }

    private fun fetchAlbumData(id: String): Response {
        val linkBuilder = StringBuilder()
        linkBuilder.append(
            "$api2URL/${
                if (id.startsWith("soundcloud:system-playlists"))
                    "system-playlists"
                else
                    "playlists"
            }/"
        )
        linkBuilder.append(id)
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun fetchAlbum(albumLink: Link): Album {
        val id = if ("$albumLink".startsWith("$apiURL/"))
            "$albumLink".substringAfterLast("/")
        else
            resolveId(albumLink)
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
                                fetchAlbumTracks(albumLink)
                            )
                            break
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${albumData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE ${albumData.code}")
                }
            }
            albumJob.complete()
            album
        }
    }

    override suspend fun fetchAlbumTracks(albumLink: Link, limit: Int): TrackList {
        val id = if ("$albumLink".startsWith("$apiURL/"))
            "$albumLink".substringAfterLast("/")
        else
            resolveId(albumLink)

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
                            val tracks = fetchMultipleTracks(
                                if (limit != 0 && apiLinks.size > limit) apiLinks.subList(0, limit) else apiLinks
                            )
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
                            println("Failed JSON:\n${albumData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE ${albumData.code}")
                }
            }
            TrackList(trackList)
        }
    }

    /**
     * Fetch a Track object for a given SoundCloud song link
     * @param trackLink link to the song
     * @return returns a Track object with uploader, title and link
     */
    override suspend fun fetchTrack(trackLink: Link): Track {
        lateinit var track: Track

        suspend fun fetchTrackData(): Response {
            val id = if ("$trackLink".startsWith("$apiURL/tracks/"))
                "$trackLink".substringAfterLast("/")
            else
                resolveId(trackLink)

            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/tracks/$id")
            linkBuilder.append("?client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
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
                Playability(trackData.getBoolean("streamable") && trackData.getString("policy") != "BLOCK"),
                if (!trackData.isNull("likes_count")) {
                    Likes(trackData.getInt("likes_count"))
                } else {
                    Likes()
                }
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
                        println("Error 404! $trackLink not found!")
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

    private suspend fun fetchMultipleTracks(links: List<Link>): TrackList {
        suspend fun fetchTracksData(trackLinks: List<Link>): Response {
            val idsBuilder = StringBuilder()
            for (link in trackLinks) {
                idsBuilder.append(
                    if ("$link".startsWith("$apiURL/tracks/")) {
                        "$link".substringAfterLast("/")
                    } else {
                        resolveId(link)
                    } + ","
                )
            }
            val ids = withContext(IO) {
                URLEncoder.encode(idsBuilder.toString().substringBeforeLast(","), Charsets.UTF_8.toString())
            }
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/tracks?ids=$ids")
            linkBuilder.append("&client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        fun parseTracksData(tracksData: JSONArray): TrackList {
            val trackList = ArrayList<Track>()
            for (trackData in tracksData) {
                trackData as JSONObject
                val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                val releaseDate = ReleaseDate(LocalDate.parse(trackData.getString("created_at"), formatter))
                trackList.add(
                    Track(
                        Album(
                            Name(
                                if (
                                    !trackData.isNull("publisher_metadata") &&
                                    trackData.getJSONObject("publisher_metadata")
                                        .has("album_title") &&
                                    !trackData.getJSONObject("publisher_metadata")
                                        .isNull("album_title")
                                )
                                    trackData.getJSONObject("publisher_metadata")
                                        .getString("album_title")
                                else
                                    ""
                            ),
                            releaseDate = releaseDate
                        ),
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
                        Playability(trackData.getBoolean("streamable") && trackData.getString("policy") != "BLOCK")
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
                if (list.isNotEmpty())
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
                                    println("Error 404! $linksToFetch not found!")
                                    tracksJob.complete()
                                    return@launch
                                }

                                HttpURLConnection.HTTP_BAD_REQUEST -> {
                                    println("Bad request!")
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

    private suspend fun fetchUserPlaylists(userLink: Link): Playlists {
        suspend fun fetchData(): Response {
            val id = if ("$userLink".startsWith("$apiURL/users/"))
                "$userLink".substringAfterLast("/")
            else
                resolveId(userLink)
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/users/$id/playlists")
            linkBuilder.append("?client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
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
                                        description = Description(
                                            if (!it.isNull("description")) it.getString("description") else ""
                                        ),
                                        followers = if (!it.isNull("likes_count")) {
                                            Followers(it.getInt("likes_count"))
                                        } else {
                                            Followers()
                                        },
                                        publicity = Publicity(it.getBoolean("public")),
                                        tracks = TrackList(
                                            List(it.getInt("track_count")) { Track() }
                                        ),
                                        link = Link(
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

                    else -> println("HTTP ERROR! CODE ${playlistsData.code}")
                }
            }
        }
        return Playlists(playlists)
    }

    suspend fun fetchUserLikes(
        userLink: Link,
        limit: Int = 0,
        tracksOnly: Boolean = false,
        playlistsOnly: Boolean = false
    ): TrackList {
        suspend fun fetchData(link: Link = userLink): Response {
            val id = if ("$link".startsWith("$api2URL/users/"))
                "$link".substringBeforeLast("/").substringAfterLast("/")
            else
                resolveId(Link("$link".substringBeforeLast("/")))
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/users/$id/likes")
            linkBuilder.append("?client_id=$clientId")
            if ("$link".startsWith("$api2URL/users/"))
                linkBuilder.append("&$link".substringAfter("?"))
            else
                linkBuilder.append("&limit=" + if (limit != 0 && limit < 100) limit else 100)
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val likes = ArrayList<Track>()
        val likesJob = Job()
        withContext(IO + likesJob) {
            var likesData = fetchData()
            while (true) {
                when (likesData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(likesData.data.data)
                            data.getJSONArray("collection").forEach {
                                it as JSONObject
                                when {
                                    it.has("track") -> {
                                        if (!playlistsOnly) {
                                            it.getJSONObject("track").let { track ->
                                                try {
                                                    fun parseTrack(): Track = Track(
                                                        Album(
                                                            Name(
                                                                if (
                                                                    !track.isNull("publisher_metadata") &&
                                                                    track.getJSONObject("publisher_metadata")
                                                                        .has("album_title") &&
                                                                    !track.getJSONObject("publisher_metadata")
                                                                        .isNull("album_title")
                                                                )
                                                                    track.getJSONObject("publisher_metadata")
                                                                        .getString("album_title")
                                                                else
                                                                    ""
                                                            ),
                                                            releaseDate = ReleaseDate(
                                                                LocalDate.parse(
                                                                    track.getString("created_at"),
                                                                    DateTimeFormatter.ISO_INSTANT.withZone(
                                                                        ZoneId.of("Z")
                                                                    )
                                                                )
                                                            )
                                                        ),
                                                        Artists(
                                                            listOf(
                                                                track.getJSONObject("user").let { user ->
                                                                    Artist(
                                                                        Name(user.getString("username")),
                                                                        Link(
                                                                            user.getString("permalink_url"),
                                                                            user.getInt("id").toString()
                                                                        )
                                                                    )
                                                                }
                                                            )
                                                        ),
                                                        Name(track.getString("title")),
                                                        Link(
                                                            track.getString("permalink_url"),
                                                            track.getInt("id").toString()
                                                        ),
                                                        Playability(
                                                            track.getBoolean("streamable") &&
                                                                    track.getString("policy") != "BLOCK"
                                                        ),
                                                        Likes(
                                                            if (!track.isNull("likes_count"))
                                                                track.getInt("likes_count")
                                                            else
                                                                0
                                                        )
                                                    )
                                                    if (limit != 0) {
                                                        if (likes.size < limit) {
                                                            likes.add(parseTrack())
                                                        } else {
                                                            println("Limit reached!")
                                                            likesJob.complete()
                                                            return@withContext
                                                        }
                                                    } else {
                                                        likes.add(parseTrack())
                                                    }
                                                } catch (e: JSONException) {
                                                    //JSON broken, try getting the data again
                                                    println("Failed JSON:\n${track.toString(4)}\n")
                                                    println("Failed to get data from JSON:\n${e.printStackTrace()}")
                                                }
                                            }
                                        }
                                    }

                                    it.has("playlist") -> {
                                        if (!tracksOnly) {
                                            it.getJSONObject("playlist").let { playlist ->
                                                likes.addAll(
                                                    fetchPlaylistTracks(
                                                        Link(playlist.getString("permalink_url")),
                                                    ).trackList.let { list ->
                                                        if (limit != 0 && list.size + likes.size > limit)
                                                            list.subList(0, limit - likes.size)
                                                        else
                                                            list
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        println("Data type not recognized:\n$it")
                                    }
                                }
                            }

                            if (!data.isNull("next_href")) {
                                if (limit != 0) {
                                    if (likes.size < limit)
                                        likesData = fetchData(Link(data.getString("next_href")))
                                } else {
                                    likesJob.complete()
                                    return@withContext
                                }
                            } else {
                                likesJob.complete()
                                return@withContext
                            }
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${likesData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE ${likesData.code}")
                }
            }
        }
        return TrackList(likes)
    }

    suspend fun fetchUserReposts(
        userLink: Link,
        limit: Int = 0,
        tracksOnly: Boolean = false,
        playlistsOnly: Boolean = false
    ): TrackList {
        suspend fun fetchData(link: Link = userLink): Response {
            val id = if ("$link".startsWith("$api2URL/stream/users/"))
                "$link".substringBeforeLast("/").substringAfterLast("/")
            else
                resolveId(Link("$link".substringBeforeLast("/")))
        val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/stream/users/$id/reposts")
            linkBuilder.append("?client_id=$clientId")
            if ("$link".startsWith("$api2URL/stream/users/"))
                linkBuilder.append("&$link".substringAfter("?"))
            else
                linkBuilder.append("&limit=" + if (limit != 0 && limit < 100) limit else 100)

            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val reposts = ArrayList<Track>()
        val repostsJob = Job()
        withContext(IO + repostsJob) {
            var repostsData = fetchData()
            while (true) {
                when (repostsData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(repostsData.data.data)
                            data.getJSONArray("collection").forEach {
                                it as JSONObject
                                when {
                                    it.has("track") -> {
                                        if (!playlistsOnly) {
                                            it.getJSONObject("track").let { track ->
                                                try {
                                                    fun parseTrack() = Track(
                                                        Album(
                                                            Name(
                                                                if (
                                                                    !track.isNull("publisher_metadata") &&
                                                                    track.getJSONObject("publisher_metadata")
                                                                        .has("album_title") &&
                                                                    !track.getJSONObject("publisher_metadata")
                                                                        .isNull("album_title")
                                                                )
                                                                    track.getJSONObject("publisher_metadata")
                                                                        .getString("album_title")
                                                                else
                                                                    ""
                                                            ),
                                                            releaseDate = ReleaseDate(
                                                                LocalDate.parse(
                                                                    track.getString("created_at"),
                                                                    DateTimeFormatter.ISO_INSTANT.withZone(
                                                                        ZoneId.of("Z")
                                                                    )
                                                                )
                                                            )
                                                        ),
                                                        Artists(
                                                            listOf(
                                                                track.getJSONObject("user").let { user ->
                                                                    Artist(
                                                                        Name(user.getString("username")),
                                                                        Link(
                                                                            user.getString("permalink_url"),
                                                                            user.getInt("id").toString()
                                                                        )
                                                                    )
                                                                }
                                                            )
                                                        ),
                                                        Name(track.getString("title")),
                                                        Link(
                                                            track.getString("permalink_url"),
                                                            track.getInt("id").toString()
                                                        ),
                                                        Playability(
                                                            track.getBoolean("streamable") && track.getString(
                                                                "policy"
                                                            ) != "BLOCK"
                                                        ),
                                                        Likes(
                                                            if (!track.isNull("likes_count"))
                                                                track.getInt("likes_count")
                                                            else
                                                                0
                                                        )
                                                    )
                                                    if (limit != 0) {
                                                        if (reposts.size < limit)
                                                            reposts.add(parseTrack())
                                                        else {
                                                            println("Limit reached!")
                                                            repostsJob.complete()
                                                            return@withContext
                                                        }
                                                    } else {
                                                        reposts.add(parseTrack())
                                                    }
                                                } catch (e: JSONException) {
                                                    //JSON broken, try getting the data again
                                                    println("Failed JSON:\n${track.toString(4)}\n")
                                                    println("Failed to get data from JSON, trying again...")
                                                }
                                            }
                                        }

                                    }

                                    it.has("playlist") -> {
                                        if (!tracksOnly) {
                                            it.getJSONObject("playlist").let { playlist ->
                                                reposts.addAll(
                                                    fetchPlaylistTracks(
                                                        Link(playlist.getString("permalink_url")),
                                                    ).trackList.let { list ->
                                                        if (limit != 0 && list.size + reposts.size > limit)
                                                            list.subList(0, limit - reposts.size)
                                                        else
                                                            list
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        println("Data type not recognized:\n$it")
                                    }
                                }
                            }

                            if (!data.isNull("next_href"))
                                if (limit != 0) {
                                    if (reposts.size < limit)
                                        repostsData = fetchData(Link(data.getString("next_href")))
                                } else {
                                    repostsJob.complete()
                                    return@withContext
                                }
                            else {
                                repostsJob.complete()
                                return@withContext
                            }
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${repostsData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    HttpURLConnection.HTTP_BAD_GATEWAY -> {
                        println("HTTP ERROR! CODE ${repostsData.code} BAD GATEWAY")
                        repostsJob.complete()
                        return@withContext
                    }

                    else -> println("HTTP ERROR! CODE ${repostsData.code}")
                }
            }
        }
        return TrackList(reposts)
    }

    /**
     * Fetch a SoundCloud user's uploaded tracks.
     * @param userId user's id.
     * @param tracksAmount the amount of tracks the user has uploaded.
     *        This is used for the `limit=` parameter in the http post.
     * @return returns a TrackList containing the user's tracks.
     */
    private suspend fun fetchUserTracks(userId: String, tracksAmount: Int): TrackList {
        fun fetchTracksData(): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URL/users/$userId/tracks")
            linkBuilder.append("?limit=$tracksAmount")
            linkBuilder.append("&client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
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
                                            ),
                                            releaseDate = ReleaseDate(
                                                LocalDate.parse(
                                                    it.getString("created_at"),
                                                    DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                                                )
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
                                        Playability(it.getBoolean("streamable") && it.getString("policy") != "BLOCK"),
                                        if (!it.isNull("likes_count")) {
                                            Likes(it.getInt("likes_count"))
                                        } else {
                                            Likes()
                                        }
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
        val linkBuilder = StringBuilder()
        linkBuilder.append("$api2URL/users/$userId")
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun fetchUser(userLink: Link): User {
        lateinit var user: User
        val id = if ("$userLink".startsWith("$apiURL/users/"))
            "$userLink".substringAfterLast("/")
        else
            resolveId(userLink)


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

    //SoundCloud doesn't have "Artists", but SoundCloud seems to treat users with uploaded tracks as artists,
    //therefore we just fetch a User's data, and present it as an Artist
    override suspend fun fetchArtist(artistLink: Link): Artist {
        lateinit var artist: Artist
        val id = if ("$artistLink".startsWith("$apiURL/users/"))
            "$artistLink".substringAfterLast("/")
        else
            resolveId(artistLink)
        val artistJob = Job()

        suspend fun parseArtistData(artistData: JSONObject) {
            fun fetchRelatedArtistsData(): Response {
                val linkBuilder = StringBuilder()
                linkBuilder.append("$api2URL/users/$id/relatedartists")
                linkBuilder.append("?client_id=$clientId")
                return sendHttpRequest(Link(linkBuilder.toString()))
            }

            val artistsTracks = fetchUserTracks(id, artistData.getInt("track_count"))
            val topTracks = ArrayList<Track>()
            artistsTracks.trackList.sortedByDescending {
                it.likes.amount
            }.forEach {
                //get top 10 tracks
                val topTracksAmount = 10
                if (topTracks.size < topTracksAmount)
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

                        else -> println("HTTP ERROR! CODE: ${artistsData.code}")
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
                description = Description(
                    if (!artistData.isNull("description"))
                        artistData.getString("description")
                    else
                        ""
                )
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

                    else -> println("HTTP ERROR! CODE: ${artistData.code}")
                }
            }
        }
        return artist
    }

    private fun fetchResolvedData(link: Link): Response {
        val linkBuilder = StringBuilder()
        linkBuilder.append("$api2URL/resolve?")
        linkBuilder.append("client_id=$clientId")
        linkBuilder.append("&url=${link.link}")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun resolveType(link: Link): LinkType {
        val resolveJob = Job()
        var linkToSolve = link.clean(this)
        val urlStart = "(https?://)?soundcloud\\.com/[a-z0-9-_]+"
        val deferredType = CoroutineScope(IO + resolveJob).async {
            when {
                "$linkToSolve".contains("$urlStart/(?!sets|likes|reposts)\\S+".toRegex()) -> LinkType.TRACK
                "$linkToSolve".contains("$urlStart/likes".toRegex()) -> LinkType.LIKES
                "$linkToSolve".contains("$urlStart/reposts".toRegex()) -> LinkType.REPOSTS
                else -> {
                    lateinit var type: LinkType
                    while (true) {
                        if ("$linkToSolve".contains("^(https?://)?on\\.soundcloud\\.com/\\S+$".toRegex()))
                            linkToSolve = sendHttpRequest(linkToSolve, followRedirects = false).link.clean(this@SoundCloud)
                        val typeData = fetchResolvedData(linkToSolve)
                        when (typeData.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val data = JSONObject(typeData.data.data)
                                    type = when (val kind = data.getString("kind").uppercase()) {
                                        "PLAYLIST" -> {
                                            //if LinkType is PLAYLIST, check if it is also an album.
                                            if (data.getBoolean("is_album")) {
                                                LinkType.ALBUM
                                            } else {
                                                LinkType.valueOf(kind)
                                            }
                                        }

                                        "USER" -> {
                                            //if LinkType is USER, check if it is also an artist.
                                            if (data.getInt("track_count") > 0)
                                                LinkType.ARTIST
                                            else
                                                LinkType.valueOf(kind)
                                        }

                                        else -> {
                                            LinkType.valueOf(kind)
                                        }
                                    }
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
                                println("Error 404! $linkToSolve not found!")
                                type = LinkType.OTHER
                                break
                            }

                            else -> println("HTTP ERROR! CODE ${typeData.code.code}")
                        }
                    }
                    type
                }
            }
        }
        return deferredType.await()
    }

    /**
     * Resolves the given SoundCloud link and returns its id
     * @param link link to resolve
     * @return returns the corresponding id for the given link as a String
     */
    suspend fun resolveId(link: Link): String {
        val resolveJob = Job()
        val deferredId = CoroutineScope(IO + resolveJob).async {
            lateinit var id: String
            var linkToSolve = link
            if ("$link".contains("/(reposts|likes)$".toRegex())) {
                //As it turns out, neither likes nor reposts have an id.
                //However, when trying to resolve a likes link, the user is returned, whereas
                //trying to resolve a reposts link just gives a 404 error.
                //So instead of trying to resolve reposts/likes, just return an empty id.
                println("$link doesn't have an id!")
                id = ""
                resolveJob.complete()
                id
            } else {
                if ("$linkToSolve".contains("^(https?://)?on\\.soundcloud\\.com/\\S+$".toRegex()))
                    linkToSolve = sendHttpRequest(linkToSolve, followRedirects = false).link.clean(this@SoundCloud)
                while (true) {
                    val idData = fetchResolvedData(linkToSolve)
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
                            println("Error 404! $link not found!")
                            id = ""
                            break
                        }

                        else -> println("HTTP ERROR! CODE ${idData.code}")
                    }
                }
                resolveJob.complete()
                id
            }
        }
        return deferredId.await()
    }
}
