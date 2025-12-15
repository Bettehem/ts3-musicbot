package ts3musicbot.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ts3musicbot.util.Album
import ts3musicbot.util.Albums
import ts3musicbot.util.Artist
import ts3musicbot.util.Artists
import ts3musicbot.util.Collaboration
import ts3musicbot.util.Description
import ts3musicbot.util.Discover
import ts3musicbot.util.Discoveries
import ts3musicbot.util.Followers
import ts3musicbot.util.Genres
import ts3musicbot.util.Likes
import ts3musicbot.util.Link
import ts3musicbot.util.LinkType
import ts3musicbot.util.Name
import ts3musicbot.util.Playability
import ts3musicbot.util.Playlist
import ts3musicbot.util.Playlists
import ts3musicbot.util.Publicity
import ts3musicbot.util.ReleaseDate
import ts3musicbot.util.Response
import ts3musicbot.util.SearchQuery
import ts3musicbot.util.SearchResult
import ts3musicbot.util.SearchResults
import ts3musicbot.util.SearchType
import ts3musicbot.util.TagOrGenre
import ts3musicbot.util.Track
import ts3musicbot.util.TrackList
import ts3musicbot.util.User
import ts3musicbot.util.sendHttpRequest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SoundCloud : Service(ServiceType.SOUNDCLOUD) {
    var clientId = "H8sYVN4CJ2E8Ij83bJZ1OtB9w4kzyyvy"
    private val api2URI = URI("https://api-v2.soundcloud.com")
    val apiURI = URI("https://api.soundcloud.com")

    override fun getSupportedSearchTypes() =
        listOf(
            LinkType.TRACK,
            LinkType.PLAYLIST,
            LinkType.SYSTEM_PLAYLIST,
            LinkType.ALBUM,
            LinkType.USER,
            LinkType.ARTIST,
            LinkType.DISCOVER,
            LinkType.TAG_OR_GENRE,
        )

    /**
     * Updates the clientId
     * @return returns the new id
     */
    fun updateClientId(): String {
        println("Updating SoundCloud ClientId")
        val lines =
            sendHttpRequest(Link("https://soundcloud.com"))
                .data.data
                .lines()
                .filter { it.contains("^<script crossorigin src=\"https://\\S+\\.js\"></script>".toRegex()) }
        for (line in lines) {
            sendHttpRequest(Link(line.substringAfter('"').substringBefore('"')))
                .data.data
                .let { data ->
                    if (data.contains("client_id=[0-9A-z-_]+\"".toRegex())) {
                        val idLine = data.lines().first { it.contains("client_id=[0-9A-z-_]+\"".toRegex()) }
                        val id = idLine.replace("^.*client_id=".toRegex(), "").replace("(&|\"?\\),).*$".toRegex(), "")
                        synchronized(clientId) { clientId = id }
                    }
                }
        }
        return clientId
    }

    override suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int,
        encodeQuery: Boolean,
    ): SearchResults {
        val searchResults = ArrayList<SearchResult>()

        fun searchData(
            limit: Int = resultLimit,
            offset: Int = 0,
            link: Link = Link(),
        ): Response {
            val linkBuilder = StringBuilder()
            if (link.isNotEmpty()) {
                linkBuilder.append("$link")
            } else {
                linkBuilder.append("$api2URI/search/")
                linkBuilder.append("${searchType.type.replace("artist", "user")}s")
                linkBuilder.append("?q=${if (encodeQuery) encode(searchQuery.query) else searchQuery.query}")
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
                        val track =
                            Track(
                                Album(releaseDate = releaseDate),
                                Artists(
                                    listOf(
                                        Artist(
                                            Name(trackData.getJSONObject("user").getString("username")),
                                            Link(trackData.getJSONObject("user").getString("permalink_url")),
                                        ),
                                    ),
                                ),
                                Name(trackData.getString("title")),
                                Link(trackData.getString("permalink_url")),
                                Playability(trackData.getBoolean("streamable") && trackData.getString("policy") != "BLOCK"),
                            )
                        searchResults.add(
                            SearchResult(
                                track,
                                track.link,
                            ),
                        )
                    }
                }

                "playlist" -> {
                    val playlists = searchData.getJSONArray("collection")
                    for (playlistData in playlists) {
                        playlistData as JSONObject

                        val playlist =
                            Playlist(
                                Name(playlistData.getString("title")),
                                User(
                                    Name(playlistData.getJSONObject("user").getString("username")),
                                    Name(playlistData.getJSONObject("user").getString("permalink")),
                                    Description(
                                        if (!playlistData.getJSONObject("user").isNull("description")) {
                                            playlistData.getJSONObject("user").getString("description")
                                        } else {
                                            ""
                                        },
                                    ),
                                    Followers(playlistData.getJSONObject("user").getInt("followers_count").toLong()),
                                    link = Link(playlistData.getJSONObject("user").getString("permalink_url")),
                                ),
                                Description(if (!playlistData.isNull("description")) playlistData.getString("description") else ""),
                                Followers(
                                    if (!playlistData.isNull(
                                            "likes_count",
                                        )
                                    ) {
                                        playlistData.getInt("likes_count").toLong()
                                    } else {
                                        Followers().amount.toLong()
                                    },
                                ),
                                Publicity(playlistData.getString("sharing") == "public"),
                                Collaboration(false),
                                TrackList(List(playlistData.getInt("track_count")) { Track() }),
                                Link(playlistData.getString("permalink_url")),
                            )
                        searchResults.add(
                            SearchResult(playlist, playlist.link),
                        )
                    }
                }

                "album" -> {
                    val albums = searchData.getJSONArray("collection")
                    for (albumData in albums) {
                        albumData as JSONObject

                        val album =
                            Album(
                                Name(albumData.getString("title")),
                                Artists(
                                    listOf(
                                        Artist(
                                            Name(albumData.getJSONObject("user").getString("username")),
                                            Link(
                                                albumData.getJSONObject("user").getString("permalink_url"),
                                                albumData.getJSONObject("user").getInt("id").toString(),
                                            ),
                                        ),
                                    ),
                                ),
                                ReleaseDate(
                                    LocalDate.parse(
                                        if (!albumData.isNull("published_at")) {
                                            albumData.getString("published_at")
                                        } else {
                                            albumData.getString("display_date")
                                        },
                                        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z")),
                                    ),
                                ),
                                TrackList(
                                    albumData.getJSONArray("tracks").map {
                                        it as JSONObject
                                        Track(link = Link(linkId = it.getInt("id").toString()))
                                    },
                                ),
                                Link(albumData.getString("permalink_url"), albumData.getInt("id").toString()),
                                Genres(listOf(albumData.getString("genre"))),
                            )

                        searchResults.add(
                            SearchResult(album, album.link),
                        )
                    }
                }

                "user" -> {
                    val users = searchData.getJSONArray("collection")
                    for (userData in users) {
                        userData as JSONObject

                        val user =
                            User(
                                Name(userData.getString("username")),
                                Name(userData.getString("permalink")),
                                Description(if (!userData.isNull("description")) userData.getString("description") else ""),
                                Followers(userData.getInt("followers_count").toLong()),
                                fetchUserPlaylists(Link("$apiURI/users/${userData.getInt("id")}")),
                                Link(userData.getString("permalink_url")),
                            )
                        searchResults.add(
                            SearchResult(user, user.link),
                        )
                    }
                }

                "artist" -> {
                    val artists =
                        searchData.getJSONArray("collection").filter {
                            it as JSONObject
                            it.getInt("track_count") > 0
                        }
                    for (artistData in artists) {
                        artistData as JSONObject

                        val artist =
                            Artist(
                                Name(artistData.getString("username")),
                                Link(artistData.getString("permalink_url")),
                                followers = Followers(artistData.getInt("followers_count").toLong()),
                                description =
                                    Description(
                                        if (!artistData.isNull("description")) {
                                            artistData.getString("description")
                                        } else {
                                            ""
                                        },
                                    ),
                            )

                        searchResults.add(
                            SearchResult(artist, artist.link),
                        )
                    }
                }
            }
        }

        println("Searching for \"$searchQuery\"")
        val searches = ArrayList<Pair<Int, Int>>()
        var remainingResults = resultLimit
        var resultOffset = 0
        // SoundCloud allows a maximum of 200 results, so we have to do searches in smaller chunks in case the user wants more than 200 results.
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
                                // JSON broken, try getting the data again
                                println("Failed JSON:\n${searchData.data}\n")
                                println("Failed to get data from JSON, trying again...")
                                searchData = searchData(link = result.link)
                            }
                        }

                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            updateClientId()
                            searchData =
                                searchData(
                                    link =
                                        Link(
                                            result.link
                                                .toString()
                                                .replace("client_id=[a-zA-Z0-9]+".toRegex(), "client_id=$clientId"),
                                        ),
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
            "$api2URI/${
                if (id.startsWith("soundcloud:system-playlists")) {
                    "system-playlists"
                } else {
                    "playlists"
                }
            }/",
        )
        linkBuilder.append(id)
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    private suspend fun parsePlaylistData(
        playlistData: JSONObject,
        isSystemPlaylist: Boolean = false,
        shouldFetchTracks: Boolean = playlistData.getJSONArray("tracks").isEmpty,
    ): Playlist {
        val listLink =
            Link(
                playlistData.getString("permalink_url"),
                if (playlistData.get("id") is String) {
                    playlistData.getString("id")
                } else {
                    playlistData.getInt("id").toString()
                },
            )
        return Playlist(
            Name(playlistData.getString("title")),
            User(
                Name(playlistData.getJSONObject("user").getString("username")),
                Name(playlistData.getJSONObject("user").getString("permalink")),
                link =
                    Link(
                        playlistData.getJSONObject("user").getString("permalink_url"),
                        playlistData.getJSONObject("user").getInt("id").toString(),
                    ),
            ),
            if (!playlistData.isNull("description")) {
                Description(playlistData.getString("description"))
            } else {
                Description()
            },
            if (!playlistData.isNull("likes_count")) {
                Followers(playlistData.getInt("likes_count").toLong())
            } else {
                Followers()
            },
            Publicity(playlistData.getBoolean(if (isSystemPlaylist) "is_public" else "public")),
            tracks =
                if (shouldFetchTracks) {
                    fetchPlaylistTracks(listLink)
                } else {
                    val tracks = playlistData.getJSONArray("tracks")
                    if (!tracks.isEmpty) {
                        tracks.map {
                            it as JSONObject
                            parseTrackData(it)
                        }
                    }
                    if (isSystemPlaylist) {
                        TrackList(List(playlistData.getJSONArray("tracks").length()) { Track() })
                    } else {
                        TrackList(List(playlistData.getInt("track_count")) { Track() })
                    }
                },
            link = listLink,
        )
    }

    override suspend fun fetchPlaylist(
        playlistLink: Link,
        shouldFetchTracks: Boolean,
    ): Playlist {
        lateinit var playlist: Playlist
        val isSystemPlaylist = playlistLink.linkType(this) == LinkType.SYSTEM_PLAYLIST
        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = fetchPlaylistData(playlistLink)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlistJSON = JSONObject(playlistData.data.data)
                            playlist = parsePlaylistData(playlistJSON, isSystemPlaylist, shouldFetchTracks)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
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
    override suspend fun fetchPlaylistTracks(
        playlistLink: Link,
        limit: Int,
    ): TrackList {
        val trackList = ArrayList<Track>()
        val playlistJob = Job()
        withContext(IO + playlistJob) {
            while (true) {
                val playlistData = fetchPlaylistData(playlistLink)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksJSON = JSONObject(playlistData.data.data).getJSONArray("tracks")
                            val apiLinks =
                                tracksJSON.map {
                                    it as JSONObject
                                    Link("$apiURI/tracks/${it.getInt("id")}", it.getInt("id").toString())
                                }
                            val tracks =
                                fetchMultipleTracks(
                                    if (limit != 0 && apiLinks.size > limit) apiLinks.subList(0, limit) else apiLinks,
                                )
                            val sortedList =
                                async {
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
                            // JSON broken, try getting the data again
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
            "$api2URI/${
                if (id.startsWith("soundcloud:system-playlists")) {
                    "system-playlists"
                } else {
                    "playlists"
                }
            }/",
        )
        linkBuilder.append(id)
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    private suspend fun parseAlbumData(
        albumJSON: JSONObject,
        shouldFetchTracks: Boolean = true,
    ): Album {
        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
        val releaseDate =
            ReleaseDate(
                if (albumJSON.has("release_date") || albumJSON.has("created_at")) {
                    LocalDate.parse(
                        if (albumJSON.has(
                                "release_date",
                            ) &&
                            !albumJSON.isNull("release_date")
                        ) {
                            albumJSON.getString("release_date")
                        } else {
                            albumJSON.getString("created_at")
                        },
                        formatter,
                    )
                } else {
                    LocalDate.now()
                },
            )
        return when (val kind = albumJSON.getString("kind")) {
            "track" ->
                Album(
                    Name(
                        if (
                            albumJSON.has("publisher_metadata") &&
                            !albumJSON.isNull("publisher_metadata") &&
                            albumJSON
                                .getJSONObject("publisher_metadata")
                                .has("album_title") &&
                            !albumJSON
                                .getJSONObject("publisher_metadata")
                                .isNull("album_title")
                        ) {
                            albumJSON
                                .getJSONObject("publisher_metadata")
                                .getString("album_title")
                        } else {
                            ""
                        },
                    ),
                    releaseDate = releaseDate,
                )

            "playlist" -> {
                val isAlbum = albumJSON.has("is_album") && albumJSON.has("is_album") && albumJSON.getBoolean("is_album")
                if (isAlbum) {
                    Album(
                        if (albumJSON.has("title")) {
                            Name(albumJSON.getString("title"))
                        } else {
                            Name()
                        },
                        Artists(
                            if (albumJSON.has("user")) {
                                listOf(
                                    Artist(
                                        Name(albumJSON.getJSONObject("user").getString("username")),
                                        Link(
                                            albumJSON.getJSONObject("user").getString("permalink_url"),
                                            albumJSON.getJSONObject("user").getInt("id").toString(),
                                        ),
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        ),
                        releaseDate,
                        if (shouldFetchTracks && albumJSON.has("tracks")) {
                            if (
                                !albumJSON.getJSONArray("tracks").all {
                                    it as JSONObject
                                    it.has("title")
                                }
                            ) {
                                fetchAlbumTracks(Link(albumJSON.getString("permalink_url")))
                            } else {
                                TrackList(
                                    albumJSON.getJSONArray("tracks").map {
                                        it as JSONObject
                                        parseTrackData(it)
                                    },
                                )
                            }
                        } else {
                            TrackList()
                        },
                        Link(albumJSON.getString("permalink_url")),
                    )
                } else {
                    Album()
                }
            }
            else -> {
                println("ERROR! Unsupported JSON type: $kind")
                Album()
            }
        }
    }

    override suspend fun fetchAlbum(albumLink: Link): Album {
        val id = resolveId(albumLink)
        val albumJob = Job()
        return withContext(IO + albumJob) {
            lateinit var album: Album
            while (true) {
                val albumData = fetchAlbumData(id)
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val albumJSON = JSONObject(albumData.data.data)
                            album = parseAlbumData(albumJSON)
                            break
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
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

    override suspend fun fetchAlbumTracks(
        albumLink: Link,
        limit: Int,
    ): TrackList {
        val id = resolveId(albumLink)
        val albumJob = Job()
        return withContext(IO + albumJob) {
            val trackList = ArrayList<Track>()
            while (true) {
                val albumData = fetchAlbumData(id)
                when (albumData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val tracksData = JSONObject(albumData.data.data).getJSONArray("tracks")
                            val apiLinks =
                                tracksData.map {
                                    it as JSONObject
                                    Link("$apiURI/tracks/${it.getInt("id")}", it.getInt("id").toString())
                                }
                            val tracks =
                                fetchMultipleTracks(
                                    if (limit != 0 && apiLinks.size > limit) apiLinks.subList(0, limit) else apiLinks,
                                )
                            val sortedList =
                                async {
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
                            // JSON broken, try getting the data again
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

    private suspend fun parseTrackData(trackData: JSONObject): Track =
        Track(
            parseAlbumData(trackData, false),
            Artists(
                listOf(
                    if (trackData.has("user")) {
                        Artist(
                            Name(trackData.getJSONObject("user").getString("username")),
                            Link(trackData.getJSONObject("user").getString("permalink_url")),
                        )
                    } else {
                        Artist()
                    },
                ),
            ),
            if (trackData.has("title")) {
                Name(trackData.getString("title"))
            } else {
                Name()
            },
            Link(if (trackData.has("permalink_url")) trackData.getString("permalink_url") else "", trackData.getInt("id").toString()),
            if (trackData.has("streamable")) {
                Playability(trackData.getBoolean("streamable") && trackData.getString("policy") != "BLOCK")
            } else {
                Playability()
            },
            if (trackData.has("likes_count") && !trackData.isNull("likes_count")) {
                Likes(trackData.getInt("likes_count"))
            } else {
                Likes()
            },
        )

    /**
     * Fetch a Track object for a given SoundCloud song link
     * @param trackLink link to the song
     * @return returns a Track object with uploader, title and link
     */
    override suspend fun fetchTrack(trackLink: Link): Track {
        lateinit var track: Track

        suspend fun fetchTrackData(): Response {
            val id = resolveId(trackLink)
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/tracks/$id")
            linkBuilder.append("?client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
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
                            // JSON broken, try getting the data again
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
                    if ("$link".startsWith("$apiURI/tracks/")) {
                        "$link".substringAfterLast("/")
                    } else {
                        resolveId(link)
                    } + ",",
                )
            }
            val ids =
                withContext(IO) {
                    URLEncoder.encode(idsBuilder.toString().substringBeforeLast(","), Charsets.UTF_8.toString())
                }
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/tracks?ids=$ids")
            linkBuilder.append("&client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val tracksJob = Job()
        val tracks =
            CoroutineScope(IO + tracksJob).async {
                val trackList = ArrayList<Track>()
                withContext(IO + tracksJob) {
                    val linksToFetch = ArrayList<List<Link>>()
                    var list = ArrayList<Link>()
                    for (link in links) {
                        // create lists of 50 links, because SoundCloud limits searching to 50 items at a time
                        if (list.size < 50) {
                            list.add(link)
                        } else {
                            linksToFetch.add(list)
                            list = ArrayList()
                            list.add(link)
                        }
                    }
                    if (list.isNotEmpty()) {
                        linksToFetch.add(list)
                    }
                    for (linksList in linksToFetch) {
                        launch {
                            while (true) {
                                val tracksData = fetchTracksData(linksList)
                                when (tracksData.code.code) {
                                    HttpURLConnection.HTTP_OK -> {
                                        try {
                                            val data = JSONArray(tracksData.data.data)
                                            synchronized(trackList) {
                                                trackList.addAll(
                                                    data.map {
                                                        it as JSONObject
                                                        runBlocking {
                                                            parseTrackData(it)
                                                        }
                                                    },
                                                )
                                            }
                                            return@launch
                                        } catch (e: JSONException) {
                                            // JSON broken, try getting the data again
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
            val id = resolveId(userLink)
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/users/$id/playlists")
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
                            data.getJSONArray("collection").map {
                                it as JSONObject
                                val isSystemPlaylist =
                                    Link(
                                        it.getString("permalink_url"),
                                    ).linkType(this@SoundCloud) == LinkType.SYSTEM_PLAYLIST
                                playlists.add(parsePlaylistData(it, isSystemPlaylist))
                            }
                            playlistsJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
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
        playlistsOnly: Boolean = false,
    ): TrackList {
        suspend fun fetchData(link: Link = userLink): Response {
            val id = resolveId(Link("$link".substringBeforeLast('/')))
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/users/$id/likes")
            linkBuilder.append("?client_id=$clientId")
            if ("$link".startsWith("$api2URI/users/")) {
                linkBuilder.append("&$link".substringAfter("?"))
            } else {
                linkBuilder.append("&limit=" + if (limit != 0 && limit < 100) limit else 100)
            }
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
                                                    if (limit != 0) {
                                                        if (likes.size < limit) {
                                                            likes.add(parseTrackData(track))
                                                        } else {
                                                            println("Limit reached!")
                                                            likesJob.complete()
                                                            return@withContext
                                                        }
                                                    } else {
                                                        likes.add(parseTrackData(track))
                                                    }
                                                } catch (e: JSONException) {
                                                    // JSON broken, try getting the data again
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
                                                        if (limit != 0 && list.size + likes.size > limit) {
                                                            list.subList(0, limit - likes.size)
                                                        } else {
                                                            list
                                                        }
                                                    },
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
                                    if (likes.size < limit) {
                                        likesData = fetchData(Link(data.getString("next_href")))
                                    }
                                } else {
                                    likesJob.complete()
                                    return@withContext
                                }
                            } else {
                                likesJob.complete()
                                return@withContext
                            }
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
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
        playlistsOnly: Boolean = false,
    ): TrackList {
        suspend fun fetchData(link: Link = userLink): Response {
            val id = resolveId(Link("$link".substringBeforeLast('/')))
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/stream/users/$id/reposts")
            linkBuilder.append("?client_id=$clientId")
            if ("$link".startsWith("$api2URI/stream/users/")) {
                linkBuilder.append("&$link".substringAfter("?"))
            } else {
                linkBuilder.append("&limit=" + if (limit != 0 && limit < 100) limit else 100)
            }

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
                                                    if (limit != 0) {
                                                        if (reposts.size < limit) {
                                                            reposts.add(parseTrackData(track))
                                                        } else {
                                                            println("Limit reached!")
                                                            repostsJob.complete()
                                                            return@withContext
                                                        }
                                                    } else {
                                                        reposts.add(parseTrackData(track))
                                                    }
                                                } catch (e: JSONException) {
                                                    // JSON broken, try getting the data again
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
                                                        if (limit != 0 && list.size + reposts.size > limit) {
                                                            list.subList(0, limit - reposts.size)
                                                        } else {
                                                            list
                                                        }
                                                    },
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
                                    if (reposts.size < limit) {
                                        repostsData = fetchData(Link(data.getString("next_href")))
                                    }
                                } else {
                                    repostsJob.complete()
                                    return@withContext
                                }
                            } else {
                                repostsJob.complete()
                                return@withContext
                            }
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
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

    suspend fun fetchUserTracks(
        link: Link,
        tracksAmount: Int = 0,
    ): TrackList {
        val userId = Link("$link".substringBefore("/tracks")).getId(this)
        // get a max of 500 tracks by default in case we can't get a number from the json
        var amount = 500

        fun getTrackAmount(data: Response) = JSONObject(data.data.data).getInt("track_count")
        val userJob = Job()
        withContext(IO + userJob) {
            while (true) {
                val userData = fetchUserData(userId)
                when (userData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            amount = getTrackAmount(userData)
                        } catch (e: JSONException) {
                            println("Failed JSON:\n${userData.data}\n")
                        }
                        userJob.complete()
                        return@withContext
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE: ${userData.code}")
                }
            }
        }
        return fetchUserTracks(userId, if (tracksAmount > 0) tracksAmount else amount)
    }

    /**
     * Fetch a SoundCloud user's uploaded tracks.
     * @param userId user's id.
     * @param tracksAmount the amount of tracks the user has uploaded.
     *        This is used for the `limit=` parameter in the http post.
     * @return returns a TrackList containing the user's tracks.
     */
    private suspend fun fetchUserTracks(
        userId: String,
        tracksAmount: Int,
    ): TrackList {
        fun fetchTracksData(): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/users/$userId/tracks")
            linkBuilder.append("?limit=$tracksAmount")
            linkBuilder.append("&client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }
        return if (tracksAmount > 0) {
            val tracksJob = Job()
            val list =
                CoroutineScope(IO + tracksJob).async {
                    val trackList = ArrayList<Track>()
                    while (true) {
                        val tracksData = fetchTracksData()
                        when (tracksData.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val data = JSONObject(tracksData.data.data)
                                    trackList.addAll(
                                        data.getJSONArray("collection").map {
                                            it as JSONObject
                                            parseTrackData(it)
                                        },
                                    )
                                    tracksJob.complete()
                                    break
                                } catch (e: JSONException) {
                                    // JSON broken, try getting the data again
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
        linkBuilder.append("$api2URI/users/$userId")
        linkBuilder.append("?client_id=$clientId")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun fetchUser(userLink: Link): User {
        lateinit var user: User
        val id = resolveId(userLink)

        suspend fun parseUserData(userData: JSONObject): User =
            User(
                Name(userData.getString("username")),
                Name(userData.getString("permalink")),
                Description(if (!userData.isNull("description")) userData.getString("description") else ""),
                Followers(userData.getInt("followers_count").toLong()),
                fetchUserPlaylists(Link("$apiURI/users/$id")),
                Link(userData.getString("permalink_url")),
            )

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
                            // JSON broken, try getting the data again
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

    // SoundCloud doesn't have "Artists", but SoundCloud seems to treat users with uploaded tracks as artists,
    // therefore we just fetch a User's data, and present it as an Artist
    override suspend fun fetchArtist(
        artistLink: Link,
        fetchRecommendations: Boolean,
    ): Artist {
        lateinit var artist: Artist
        val id = resolveId(artistLink)
        val artistJob = Job()

        suspend fun parseArtistData(artistData: JSONObject) {
            fun fetchRelatedArtistsData(): Response {
                val linkBuilder = StringBuilder()
                linkBuilder.append("$api2URI/users/$id/relatedartists")
                linkBuilder.append("?client_id=$clientId")
                return sendHttpRequest(Link(linkBuilder.toString()))
            }

            val artistsTracks = fetchUserTracks(id, artistData.getInt("track_count"))
            val topTracks = ArrayList<Track>()
            artistsTracks.trackList
                .sortedByDescending {
                    it.likes.amount
                }.forEach {
                    // get top 10 tracks
                    val topTracksAmount = 10
                    if (topTracks.size < topTracksAmount) {
                        topTracks.add(it)
                    }
                }
            val relatedArtistsJob = Job()
            val relatedArtists =
                withContext(IO + relatedArtistsJob) {
                    val artists = ArrayList<Artist>()
                    while (true) {
                        val artistsData = fetchRelatedArtistsData()
                        when (artistsData.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                try {
                                    val data = JSONObject(artistsData.data.data)
                                    artists.addAll(
                                        data.getJSONArray("collection").map {
                                            it as JSONObject
                                            Artist(
                                                Name(it.getString("username")),
                                                Link(it.getString("permalink_url"), it.getInt("id").toString()),
                                            )
                                        },
                                    )
                                    break
                                } catch (e: JSONException) {
                                    // JSON broken, try getting the data again
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
            artist =
                Artist(
                    Name(artistData.getString("username")),
                    Link(artistData.getString("permalink_url"), artistData.getInt("id").toString()),
                    TrackList(topTracks),
                    Artists(relatedArtists),
                    followers = Followers(artistData.getInt("followers_count").toLong()),
                    description =
                        Description(
                            if (!artistData.isNull("description")) {
                                artistData.getString("description")
                            } else {
                                ""
                            },
                        ),
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
                            // JSON broken, try getting the data again
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

    suspend fun fetchDiscover(discoverLink: Link): Discoveries {
        val discoveries = ArrayList<Discover>()

        fun fetchDiscoverData(): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/mixed-selections?")
            linkBuilder.append("client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        suspend fun parseDiscoverData(jsonData: JSONObject) {
            for (discovery in jsonData.getJSONArray("collection")) {
                discovery as JSONObject
                val name = Name(discovery.getString("title"))
                val albums = ArrayList<Album>()
                val playlists = ArrayList<Playlist>()

                for (item in discovery.getJSONObject("items").getJSONArray("collection")) {
                    item as JSONObject
                    val kind = item.getString("kind")
                    val type =
                        when (kind) {
                            "playlist" -> if (item.getBoolean("is_album")) LinkType.ALBUM else LinkType.PLAYLIST
                            else -> LinkType.valueOf(kind.uppercase())
                        }
                    val link = Link(item.getString("uri"))
                    when (type) {
                        LinkType.PLAYLIST -> playlists.add(fetchPlaylist(link, true))
                        LinkType.ALBUM -> albums.add(fetchAlbum(link))
                        else -> println("Unsupported item type: $kind")
                    }
                }

                discoveries.add(
                    Discover(
                        name,
                        Albums(albums),
                        Playlists(playlists),
                        discoverLink,
                        name.name.endsWith("on SoundCloud"),
                    ),
                )
            }
        }

        val discoverJob = Job()
        withContext(IO + discoverJob) {
            while (true) {
                val discoverData = fetchDiscoverData()
                when (discoverData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(discoverData.data.data)
                            parseDiscoverData(data)
                            discoverJob.complete()
                            return@withContext
                        } catch (e: Exception) {
                            // JSON broken, try getting the data again
                            println("Failed JSON:\n${discoverData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE: ${discoverData.code}")
                }
            }
        }
        return Discoveries(discoveries)
    }

    suspend fun fetchTagOrGenre(
        link: Link,
        limit: Int = 50,
        offset: Int = 0,
    ): TagOrGenre {
        val tracks = ArrayList<Track>()
        val lists = ArrayList<Playlist>()
        var tagOrGenre = ""

        fun fetchTagOrGenreData(
            resultLimit: Int = limit,
            resultOffset: Int = offset,
        ): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$api2URI/")
            if ("$link".substringAfter("tags/").contains('/')) {
                linkBuilder.append("search/")
                val last = "$link".substringAfterLast('/')
                when (last) {
                    "popular-tracks" -> linkBuilder.append("tracks")
                    "playlists" -> linkBuilder.append(last)
                    else -> println("Unsupported link variant: $link")
                }
                tagOrGenre = "$link".substringAfter("tags/").substringBefore('/')
                linkBuilder.append("?q=*&filter.genre_or_tag=$tagOrGenre&")
                if (last == "popular-tracks") {
                    linkBuilder.append("sort=popular&")
                }
            } else {
                linkBuilder.append("recent-tracks/")
                tagOrGenre = "$link".substringAfterLast('/')
                linkBuilder.append("$tagOrGenre?")
            }
            linkBuilder.append("limit=$resultLimit")
            linkBuilder.append("&offset=$resultOffset")
            linkBuilder.append("&client_id=$clientId")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        suspend fun parseTagOrGenreData(data: JSONObject) {
            for (item in data.getJSONArray("collection")) {
                item as JSONObject
                when (item.getString("kind")) {
                    "track" -> tracks.add(parseTrackData(item))
                    "playlist" -> lists.add(parsePlaylistData(item, shouldFetchTracks = true))
                }
            }
        }
        val tagOrGenreJob = Job()
        withContext(IO + tagOrGenreJob) {
            while (true) {
                val tagOrGenreData = fetchTagOrGenreData()
                when (tagOrGenreData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(tagOrGenreData.data.data)
                            parseTagOrGenreData(data)
                            tagOrGenreJob.complete()
                            return@withContext
                        } catch (e: Exception) {
                            // JSON broken, try getting the data again
                            println("Failed JSON:\n${tagOrGenreData.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        updateClientId()
                    }

                    else -> println("HTTP ERROR! CODE: ${tagOrGenreData.code}")
                }
            }
        }
        return TagOrGenre(
            Name(tagOrGenre),
            TrackList(tracks),
            Playlists(lists),
            link,
        )
    }

    private fun fetchResolvedData(link: Link): Response {
        val linkBuilder = StringBuilder()
        linkBuilder.append("$api2URI/resolve?")
        linkBuilder.append("client_id=$clientId")
        linkBuilder.append("&url=${link.link}")
        return sendHttpRequest(Link(linkBuilder.toString()))
    }

    override suspend fun resolveType(link: Link): LinkType {
        val resolveJob = Job()
        var linkToSolve = link.clean(this)
        val urlStart = "(https?://)?soundcloud\\.com"
        val deferredType =
            CoroutineScope(IO + resolveJob).async {
                when {
                    "$linkToSolve".contains(
                        "$urlStart/(?!(playlist|tag)s?)[a-z0-9-_]+/?!(sets|likes|reposts|tracks)\\S+".toRegex(),
                    ) -> LinkType.TRACK
                    "$linkToSolve".contains("$urlStart/[a-z0-9-_]+/likes".toRegex()) -> LinkType.LIKES
                    "$linkToSolve".contains("$urlStart/[a-z0-9-_]+/reposts".toRegex()) -> LinkType.REPOSTS
                    "$linkToSolve".contains("$urlStart/[a-z0-9-_]+/tracks".toRegex()) -> LinkType.TRACKS
                    "$linkToSolve".contains("$urlStart/discover/sets".toRegex()) -> LinkType.SYSTEM_PLAYLIST
                    "$linkToSolve".contains("$urlStart/discover$".toRegex()) -> LinkType.DISCOVER
                    "$linkToSolve".contains("$urlStart/tags".toRegex()) -> LinkType.TAG_OR_GENRE
                    else -> {
                        lateinit var type: LinkType
                        while (true) {
                            if ("$linkToSolve".contains("^(https?://)?on\\.soundcloud\\.com/\\S+$".toRegex())) {
                                linkToSolve =
                                    sendHttpRequest(linkToSolve, followRedirects = false)
                                        .link
                                        .clean(this@SoundCloud)
                            }
                            val typeData = fetchResolvedData(linkToSolve)
                            when (typeData.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    try {
                                        val data = JSONObject(typeData.data.data)
                                        type =
                                            when (val kind = data.getString("kind").uppercase()) {
                                                "PLAYLIST" -> {
                                                    // if LinkType is PLAYLIST, check if it is also an album.
                                                    if (data.getBoolean("is_album")) {
                                                        LinkType.ALBUM
                                                    } else {
                                                        LinkType.valueOf(kind.uppercase())
                                                    }
                                                }

                                                "USER" -> {
                                                    // if LinkType is USER, check if it is also an artist.
                                                    if (data.getInt("track_count") > 0) {
                                                        LinkType.ARTIST
                                                    } else {
                                                        LinkType.valueOf(kind.uppercase())
                                                    }
                                                }

                                                else -> {
                                                    LinkType.valueOf(kind.uppercase().replace('-', '_'))
                                                }
                                            }
                                        break
                                    } catch (e: JSONException) {
                                        // JSON broken, try getting the data again
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
        val deferredId =
            CoroutineScope(IO + resolveJob).async {
                lateinit var id: String
                var linkToSolve = link
                if ("$linkToSolve".contains("^($apiURI|$api2URI)/\\S+".toRegex())) {
                    id =
                        when (resolveType(link)) {
                            LinkType.LIKES, LinkType.REPOSTS, LinkType.TRACKS ->
                                if ("$linkToSolve".contains("^$api2URI/(stream/)?users/".toRegex())) {
                                    "$linkToSolve".substringBeforeLast('/').substringAfterLast('/')
                                } else {
                                    "$linkToSolve".substringBeforeLast('/')
                                }
                            else -> "$linkToSolve".substringAfterLast('/')
                        }
                } else {
                    if ("$link".contains("/(reposts|likes|tracks)$".toRegex())) {
                        // As it turns out, user likes, reposts nor tracks links have an id.
                        // However, when trying to resolve a likes or tracks link, the user is returned, whereas
                        // trying to resolve a reposts link just gives a 404 error.
                        // So instead of trying to resolve reposts/likes, just return an empty id.
                        println("$link doesn't have an id!")
                        id = ""
                        resolveJob.complete()
                    } else {
                        if ("$linkToSolve".contains("^(https?://)?on\\.soundcloud\\.com/\\S+$".toRegex())) {
                            linkToSolve = sendHttpRequest(linkToSolve, followRedirects = false).link.clean(this@SoundCloud)
                        }
                        while (true) {
                            val idData = fetchResolvedData(linkToSolve)
                            when (idData.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    try {
                                        val data = JSONObject(idData.data.data)
                                        when (data.get("id")) {
                                            is String -> id = data.getString("id")
                                            is Int -> id = data.getInt("id").toString()
                                            is Long -> id = data.getLong("id").toString()
                                        }
                                        break
                                    } catch (e: JSONException) {
                                        // JSON broken, try getting the data again
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
                    }
                }
                resolveJob.complete()
                id
            }
        return deferredId.await()
    }
}
