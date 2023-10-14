package ts3_musicbot.services

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class YouTube : Service(ServiceType.YOUTUBE) {
    private val apiUrl = "https://www.googleapis.com/youtube/v3"
    private val apiKey1 = "AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y"
    private val apiKey2 = "AIzaSyCQBDN5QIpKCub2nNMR7WJiZY7_LYiZImA"
    val supportedSearchTypes = listOf(
        LinkType.TRACK,
        LinkType.VIDEO,
        LinkType.PLAYLIST,
        LinkType.CHANNEL,
    )

    /**
     * Fetch a video/track from YouTube
     * @param videoLink link to a video
     * @return returns a Track containing the video's information.
     */
    suspend fun fetchVideo(videoLink: Link): Track {
        /**
         * Send a request to YouTube API to get playlist items
         * @param part tells the API what type of information to return
         * @return returns a Response
         */
        fun sendRequest(part: String = "snippet,status", key: String = apiKey1): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/videos?")
            linkBuilder.append("id=${videoLink.getId()}")
            linkBuilder.append("&part=${part.replace(",", "%2C")}")
            linkBuilder.append("&key=$key")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
        var key = apiKey1
        var track = Track()
        val ytJob = Job()
        withContext(IO + ytJob) {
            while (true) {
                val response = sendRequest(key = key)
                when (response.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val itemData = JSONObject(response.data.data).getJSONArray("items").let {
                                if (it.length() > 0) {
                                    it.first()
                                } else {
                                    track = Track(link = videoLink)
                                    ytJob.complete()
                                    return@withContext
                                }
                            }
                            itemData as JSONObject
                            val releaseDate = ReleaseDate(
                                LocalDate.parse(itemData.getJSONObject("snippet").getString("publishedAt"), formatter)
                            )
                            val isPlayable = when (itemData.getJSONObject("status").getString("privacyStatus")) {
                                "public", "unlisted" -> true
                                else -> false
                            }
                            track = Track(
                                Album(releaseDate = releaseDate),
                                Artists(
                                    listOf(
                                        itemData.getJSONObject("snippet").let { artist ->
                                            Artist(
                                                Name(artist.getString("channelTitle")),
                                                Link("https://www.youtube.com/channel/${artist.getString("channelId")}")
                                            )
                                        }
                                    )
                                ),
                                Name(itemData.getJSONObject("snippet").getString("title")),
                                Link("https://youtu.be/${itemData.getString("id")}"),
                                Playability(isPlayable),
                                description = Description(itemData.getJSONObject("snippet").getString("description"))
                            )
                            ytJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            println("broken JSON, trying again")
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1) {
                            //try with another api key
                            key = apiKey2
                        } else {
                            println("HTTP ERROR! CODE: ${response.code}")
                            ytJob.complete()
                            return@withContext
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${response.code}")
                        ytJob.complete()
                        return@withContext
                    }
                }
            }
        }
        return track
    }

    override suspend fun fetchTrack(trackLink: Link): Track {
        return fetchVideo(trackLink)
    }

    /**
     * Fetch a playlist's data (not tracks) from YouTube
     * @param playlistLink link to playlist
     * @return returns a Playlist containing the given playlist's information
     */
    override suspend fun fetchPlaylist(playlistLink: Link): Playlist {
        fun fetchPlaylistData(apiKey: String = apiKey1): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/playlists")
            linkBuilder.append("?part=snippet%2Cstatus%2CcontentDetails")
            linkBuilder.append("&id=${playlistLink.getId()}")
            linkBuilder.append("&key=$apiKey")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val playlistJob = Job()
        var key = apiKey1
        return withContext(IO + playlistJob) {
            var playlist = Playlist()
            while (true) {
                val playlistData = fetchPlaylistData(key)
                when (playlistData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlistJSON = JSONObject(playlistData.data.data).getJSONArray("items").first()
                            playlistJSON as JSONObject
                            playlist = Playlist(
                                Name(playlistJSON.getJSONObject("snippet").getString("title")),
                                User(Name(playlistJSON.getJSONObject("snippet").getString("channelTitle"))),
                                Description(playlistJSON.getJSONObject("snippet").getString("description")),
                                publicity = Publicity(
                                    playlistJSON.getJSONObject("status").getString("privacyStatus") == "public"
                                ),
                                tracks = TrackList(
                                    List(playlistJSON.getJSONObject("contentDetails").getInt("itemCount")) { Track() }
                                ),
                                link = Link("https://www.youtube.com/playlist?list=${playlistJSON.getString("id")}")
                            )
                            break
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            println("broken JSON, trying again")
                            break
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1) {
                            //try with another api key
                            key = apiKey2
                        } else {
                            println("HTTP ERROR! CODE: ${playlistData.code}")
                            break
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${playlistData.code}")
                        break
                    }
                }
            }
            playlistJob.complete()
            playlist
        }
    }

    /**
     * Fetch a list of YouTube videos/tracks in a given playlist
     * @param playlistLink link to playlist
     * @param limit limit the amount of tracks to be returned
     * @return returns a list of videos/tracks
     */
    override suspend fun fetchPlaylistTracks(playlistLink: Link, limit: Int): TrackList {
        /**
         * Send a request to YouTube API to get playlist items
         * @param maxResults max results to receive. 50 is the maximum per request
         * @param pageToken token to get a specific page of results, if for example there are more than 50 items.
         * @param part tells the API what type of information to return
         * @return returns a Response
         */
        fun sendRequest(
            maxResults: Int = 50,
            pageToken: String = "",
            part: String = "snippet,status",
            apiKey: String = apiKey1
        ): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/playlistItems?")
            linkBuilder.append("playlistId=${playlistLink.getId()}")
            linkBuilder.append("&part=${part.replace(",", "%2C")}")
            linkBuilder.append("&key=$apiKey")
            linkBuilder.append("&maxResults=" + if (limit != 0 && limit < maxResults) limit else maxResults)
            if (pageToken.isNotEmpty())
                linkBuilder.append("&pageToken=$pageToken")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        suspend fun parseItems(data: JSONObject): TrackList {
            val listItems = ArrayList<Track>()
            var totalItems = data.getJSONObject("pageInfo").getInt("totalResults")
            if (limit != 0 && totalItems > limit) {
                totalItems = limit
            }
            lateinit var itemData: JSONObject
            if (totalItems > 50) {
                var key = apiKey1
                val getPageJob = Job()
                var pageData = data
                withContext(IO + getPageJob) {
                    while (listItems.size < totalItems) {
                        while (true) {
                            val result = sendRequest(
                                pageToken = if (listItems.size > 0) {
                                    pageData.getString(("nextPageToken"))
                                } else {
                                    ""
                                }, apiKey = key
                            )
                            when (result.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    pageData = JSONObject(result.data.data)
                                    for (item in pageData.getJSONArray("items")) {
                                        item as JSONObject

                                        try {
                                            val title = item.getJSONObject("snippet").getString("title")
                                            val videoLink =
                                                "https://youtu.be/${
                                                    item.getJSONObject("snippet")
                                                        .getJSONObject("resourceId")
                                                        .getString("videoId")
                                                }"
                                            val isPlayable =
                                                when (item.getJSONObject("status").getString("privacyStatus")) {
                                                    "public", "unlisted" -> true
                                                    else -> false
                                                }
                                            val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                                            val releaseDate = ReleaseDate(
                                                LocalDate.parse(
                                                    item.getJSONObject("snippet").getString("publishedAt"),
                                                    formatter
                                                )
                                            )
                                            val track = Track(
                                                Album(releaseDate = releaseDate),
                                                Artists(
                                                    listOf(
                                                        item.getJSONObject("snippet").let { artist ->
                                                            Artist(
                                                                Name(artist.getString("videoOwnerChannelTitle")),
                                                                Link(
                                                                    "https://www.youtube.com/channel/${
                                                                        artist.getString("videoOwnerChannelId")
                                                                    }"
                                                                )
                                                            )
                                                        }
                                                    )
                                                ),
                                                Name(title),
                                                Link(videoLink),
                                                Playability(isPlayable)
                                            )
                                            if (limit != 0) {
                                                if (listItems.size < limit) {
                                                    listItems.add(track)
                                                } else {
                                                    println("Limit reached!")
                                                    getPageJob.complete()
                                                    return@withContext
                                                }
                                            } else {
                                                listItems.add(track)
                                            }
                                        } catch (e: Exception) {
                                            totalItems -= 1
                                        }
                                    }
                                    if (!pageData.has("nextPageToken")) {
                                        getPageJob.complete()
                                        return@withContext
                                    }
                                }

                                HttpURLConnection.HTTP_FORBIDDEN -> {
                                    if (key == apiKey1)
                                        key = apiKey2
                                    else {
                                        println("HTTP ERROR! CODE: ${result.code}")
                                        getPageJob.complete()
                                        return@withContext
                                    }
                                }

                                else -> {
                                    println("HTTP ERROR! CODE: ${result.code}")
                                    getPageJob.complete()
                                    return@withContext
                                }
                            }

                        }
                    }
                }
            } else {
                val playlistJob = Job()
                var key = apiKey1
                withContext(IO + playlistJob) {
                    while (true) {
                        val result = sendRequest(apiKey = key)
                        when (result.code.code) {
                            HttpURLConnection.HTTP_OK -> {
                                itemData = JSONObject(result.data.data)
                                for (item in itemData.getJSONArray("items")) {
                                    item as JSONObject

                                    try {
                                        val title = item.getJSONObject("snippet").getString("title")
                                        val videoLink =
                                            "https://youtu.be/${
                                                item.getJSONObject("snippet")
                                                    .getJSONObject("resourceId")
                                                    .getString("videoId")
                                            }"
                                        val isPlayable =
                                            when (item.getJSONObject("status").getString("privacyStatus")) {
                                                "public", "unlisted" -> true
                                                else -> false
                                            }
                                        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                                        val releaseDate =
                                            ReleaseDate(
                                                LocalDate.parse(
                                                    item.getJSONObject("snippet").getString("publishedAt"), formatter
                                                )
                                            )
                                        val track = Track(
                                            Album(releaseDate = releaseDate),
                                            Artists(
                                                listOf(
                                                    item.getJSONObject("snippet").let { artist ->
                                                        Artist(
                                                            Name(artist.getString("videoOwnerChannelTitle")),
                                                            Link("https://www.youtube.com/channel/${artist.getString("videoOwnerChannelId")}")
                                                        )
                                                    }
                                                )
                                            ),
                                            Name(title),
                                            Link(videoLink),
                                            Playability(isPlayable)
                                        )
                                        if (limit != 0)
                                            if (listItems.size < limit)
                                                listItems.add(track)
                                            else {
                                                println("Limit reached!")
                                                break
                                            }
                                        else
                                            listItems.add(track)
                                    } catch (e: Exception) {
                                        totalItems -= 1
                                    }
                                }
                                playlistJob.complete()
                                return@withContext
                            }

                            HttpURLConnection.HTTP_FORBIDDEN -> {
                                if (key == apiKey1)
                                    key = apiKey2
                                else {
                                    println("HTTP ERROR! CODE: ${result.code}")
                                    playlistJob.complete()
                                    return@withContext
                                }
                            }

                            else -> {
                                println("HTTP ERROR! CODE: ${result.code}")
                                playlistJob.complete()
                                return@withContext
                            }
                        }
                    }
                }
            }
            return TrackList(listItems)
        }

        var trackList = TrackList()
        val playlistJob = Job()
        var key = apiKey1
        withContext(IO + playlistJob) {
            while (true) {
                val response = sendRequest(part = "id", apiKey = key)
                when (response.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(response.data.data)
                            trackList = parseItems(data)
                            playlistJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            println("Error! Broken JSON!")
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1)
                            key = apiKey2
                        else {
                            println("HTTP ERROR! CODE: ${response.code}")
                            playlistJob.complete()
                            return@withContext
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${response.code}")
                        playlistJob.complete()
                        return@withContext
                    }
                }
            }
        }
        return trackList
    }

    /**
     * Fetch a channel's playlists from YouTube
     * @param channelLink link to a YouTube channel
     * @return returns a list of the channel's playlists.
     */
    private suspend fun fetchChannelPlaylists(channelLink: Link): Playlists {
        fun fetchPlaylistsData(apiKey: String = apiKey1, pageToken: String = ""): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/playlists")
            linkBuilder.append("?channelId=${channelLink.getId()}")
            linkBuilder.append("&part=snippet%2Cstatus&maxResults=50")
            linkBuilder.append("&key=$apiKey")
            if (pageToken.isNotEmpty())
                linkBuilder.append("&pageToken=$pageToken")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        suspend fun parsePlaylistsData(playlistsData: JSONObject): List<Playlist> {
            val playlists = ArrayList<Playlist>()
            var items = playlistsData.getJSONArray("items")
            while (playlists.size < playlistsData.getJSONObject("pageInfo").getInt("totalResults")) {
                if (items.isEmpty && playlistsData.has("nextPageToken")) {
                    val itemsJob = Job()
                    var key = apiKey1
                    var pageToken = playlistsData.getString("nextPageToken")
                    items = withContext(IO + itemsJob) {
                        lateinit var newItems: JSONObject
                        while (true) {
                            val newPageData = fetchPlaylistsData(key, pageToken)
                            when (newPageData.code.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    try {
                                        val pageJSON = JSONObject(newPageData.data.data)
                                        if (pageJSON.has("nextPageToken"))
                                            pageToken = pageJSON.getString("nextPageToken")
                                        newItems = pageJSON
                                        break
                                    } catch (e: JSONException) {
                                        e.printStackTrace()
                                        println("Error! JSON Broken!")
                                        break
                                    }
                                }

                                HttpURLConnection.HTTP_FORBIDDEN -> {
                                    if (key == apiKey1)
                                        key = apiKey2
                                    else {
                                        println("HTTP ERROR! CODE: ${newPageData.code}")
                                        break
                                    }
                                }

                                else -> {
                                    println("HTTP ERROR! CODE: ${newPageData.code}")
                                    break
                                }
                            }
                        }
                        newItems.getJSONArray("items")
                    }
                }
                playlists.addAll(
                    items.map {
                        it as JSONObject
                        val snippet = it.getJSONObject("snippet")
                        Playlist(
                            Name(snippet.getString("title")),
                            User(Name(snippet.getString("channelTitle"))),
                            Description(snippet.getString("description")),
                            publicity = Publicity(it.getJSONObject("status").getString("privacyStatus") == "public"),
                            link = Link("https://www.youtube.com/playlist?list=${it.getString("id")}")
                        )
                    }
                )
            }
            return playlists
        }

        val playlistJob = Job()
        var key = apiKey1
        return withContext(IO + playlistJob) {
            val lists = ArrayList<Playlist>()
            while (true) {
                val playlistsData = fetchPlaylistsData(key)
                when (playlistsData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val playlistsJSON = JSONObject(playlistsData.data.data)
                            lists.addAll(parsePlaylistsData(playlistsJSON))
                            break
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            println("Error! JSON Broken!")
                            break
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1)
                            key = apiKey2
                        else {
                            println("HTTP ERROR! CODE: ${playlistsData.code}")
                            break
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${playlistsData.code}")
                        break
                    }
                }
            }
            Playlists(lists)
        }
    }

    /**
     * Fetch a channel's data on YouTube
     * @param link a YouTube channel's link
     * @return returns the channel's data as a User data class
     */
    suspend fun fetchChannel(link: Link): User {
        fun fetchChannelData(apiKey: String = apiKey1): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/channels")
            linkBuilder.append("?id=${link.getId()}")
            linkBuilder.append("&part=snippet%2Cstatistics")
            linkBuilder.append("&key=$apiKey")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val channelJob = Job()
        var key = apiKey1
        return withContext(IO + channelJob) {
            var channel = User()
            while (true) {
                val channelData = fetchChannelData(key)
                when (channelData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val channelJSON = JSONObject(channelData.data.data).getJSONArray("items").first()
                            channelJSON as JSONObject
                            channel = User(
                                Name(channelJSON.getJSONObject("snippet").getString("title")),
                                Name(channelJSON.getString("id")),
                                Description(channelJSON.getJSONObject("snippet").getString("description")),
                                Followers(channelJSON.getJSONObject("statistics").getInt("subscriberCount")),
                                fetchChannelPlaylists(link),
                                link
                            )
                            break
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            println("Error! JSON Broken!")
                            break
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1)
                            key = apiKey2
                        else {
                            println("HTTP ERROR! CODE: ${channelData.code.code}")
                            break
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${channelData.code.code}")
                        break
                    }
                }
            }
            channelJob.complete()
            channel
        }
    }

    override suspend fun fetchUser(userLink: Link): User {
        return fetchChannel(userLink)
    }

    /**
     * Search on YouTube for a video/track or a playlist
     * @param searchType can be "track", "video", "playlist" or "channel"
     * @param searchQuery search keywords
     * @param resultLimit limit how many results are retrieved.
     * @return returns results from the search
     */
    override suspend fun search(searchType: SearchType, searchQuery: SearchQuery, resultLimit: Int): SearchResults {
        fun encode(text: String) = runBlocking {
            withContext(IO) {
                URLEncoder.encode(text, Charsets.UTF_8.toString())
            }
                .replace("'", "&#39;")
                .replace("&", "&amp;")
                .replace("/", "&#x2F;")
        }

        fun decode(text: String) = runBlocking {
            withContext(IO) {
                URLDecoder.decode(text, Charsets.UTF_8.toString())
            }
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&x2F;", "/")
        }

        fun searchData(
            apiKey: String = apiKey1,
            limit: Int = resultLimit,
            pageToken: String = "",
            link: Link = Link()
        ): Response {
            val linkBuilder = StringBuilder()
            if (link.isNotEmpty()) {
                linkBuilder.append("$link".substringBefore("&key="))
                linkBuilder.append("&key=$apiKey")
            } else {
                linkBuilder.append("$apiUrl/search?")
                linkBuilder.append("q=${encode(searchQuery.query)}")
                linkBuilder.append("&type=${searchType.type.replace("track", "video")}")
                linkBuilder.append("&maxResults=$limit")
                linkBuilder.append("&part=snippet")
                if (pageToken.isNotEmpty())
                    linkBuilder.append("&pageToken=$pageToken")
                linkBuilder.append("&key=$apiKey")
            }
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val searchResults = ArrayList<SearchResult>()
        println("Searching for \"$searchQuery\" on YouTube...")
        val searchJob = Job()
        var key = apiKey1
        withContext(IO + searchJob) {
            //YouTube allows a maximum of 50 results, so we have to do searches in smaller chunks in case the user wants more than 50 results
            val maxResults = 50
            var searchData = searchData(key, if (resultLimit > maxResults) maxResults else resultLimit)
            while (true) {
                when (searchData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val responseData = JSONObject(searchData.data.data)
                            val results = responseData.getJSONArray("items")
                            for (resultData in results) {
                                resultData as JSONObject

                                when (val type = resultData.getJSONObject("id").getString("kind").substringAfter("#")) {
                                    "video" -> {
                                        val videoUploader =
                                        decode(resultData.getJSONObject("snippet").getString("channelTitle"))
                                        val videoTitle = decode(resultData.getJSONObject("snippet").getString("title"))
                                        val videoLink =
                                        "https://youtu.be/${resultData.getJSONObject("id").getString("videoId")}"

                                        if (searchType.type.contains("track|$type".toRegex())) {
                                            searchResults.add(
                                                SearchResult(
                                                    "Uploader: $videoUploader\n" +
                                                    "Title:    $videoTitle\n" +
                                                    "Link:     $videoLink\n",
                                                    Link(videoLink)
                                                )
                                            )
                                        }
                                    }
                                    "playlist" -> {
                                        val listTitle = decode(resultData.getJSONObject("snippet").getString("title"))
                                        val listCreator = resultData.getJSONObject("snippet").getString("channelTitle")
                                        val listLink =
                                            "https://www.youtube.com/playlist?list=${
                                                resultData.getJSONObject("id")
                                                    .getString("playlistId")
                                            }"

                                        if (searchType.type == type) {
                                            searchResults.add(
                                                SearchResult(
                                                    "Playlist: $listTitle\n" +
                                                    "Creator:    $listCreator\n" +
                                                    "Link:     $listLink\n",
                                                    Link(listLink)
                                                )
                                            )
                                        }
                                    }
                                    "channel" -> {
                                        val channelTitle =
                                            decode(resultData.getJSONObject("snippet").getString("title"))
                                        val channelLink =
                                            "https://www.youtube.com/channel/${
                                                resultData.getJSONObject("id").getString("channelId")
                                            }"

                                        if (searchType.type == type) {
                                            searchResults.add(
                                                SearchResult(
                                                    "Channel: $channelTitle\n" +
                                                    "Link:    $channelLink\n",
                                                    Link(channelLink)
                                                )
                                            )
                                        }
                                    }
                                }

                            }
                            if (searchResults.size < resultLimit && responseData.has("nextPageToken")) {
                                searchData = searchData(
                                    key,
                                    resultLimit - searchResults.size,
                                    responseData.getString("nextPageToken")
                                )
                            } else {
                                searchJob.complete()
                                return@withContext
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            println("Error! JSON Broken!")
                            searchJob.complete()
                            return@withContext
                        }
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1) {
                            key = apiKey2
                            searchData = searchData(key, link = searchData.link)
                        } else {
                            println("HTTP ERROR! CODE: ${searchData.code}")
                            searchJob.complete()
                            return@withContext
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${searchData.code}")
                        searchJob.complete()
                        return@withContext
                    }
                }
            }
        }
        return SearchResults(searchResults)
    }

    /**
     * Resolve the type of given YouTube link
     * @param link link to be resolved
     * @return returns a String containing the type of link. Possible values: video, channel, playlist
     */
    override suspend fun resolveType(link: Link): LinkType {
        fun fetchData(apiKey: String = apiKey1): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/search?")
            linkBuilder.append("q=${link.getId()}")
            linkBuilder.append("&part=snippet")
            linkBuilder.append("&key=$apiKey")
            return sendHttpRequest(Link(linkBuilder.toString()))
        }

        val resolveJob = Job()
        var key = apiKey1
        return when {
            "$link".contains("\\S+/playlist\\?\\S+".toRegex()) -> LinkType.PLAYLIST
            "$link".contains("\\S+/c(hannel)?/\\S+".toRegex()) -> LinkType.CHANNEL
            "$link".contains("(youtu.be|\\S+((\\w*\\?)?\\S*(vi?))[=/]\\S+)".toRegex()) -> LinkType.VIDEO
            else -> withContext(IO + resolveJob) {
                //Attempt to resolve the track type via YouTube API
                var linkType = LinkType.OTHER
                while (true) {
                    val linkData = fetchData(key)
                    when (linkData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val dataJSON = JSONObject(linkData.data.data)
                                linkType = dataJSON.getJSONArray("items").first { itemData ->
                                    itemData as JSONObject
                                    itemData.getJSONObject("id").getString("kind").substringAfter("#").let {
                                        itemData.getJSONObject("id").getString("${it}Id")
                                    } == link.getId()
                                }.let { itemData ->
                                    itemData as JSONObject
                                    LinkType.valueOf(
                                        itemData.getJSONObject("id").getString("kind").substringAfter("#").uppercase()
                                    )
                                }
                                break
                            } catch (e: JSONException) {
                                e.printStackTrace()
                                println("Error! Broken JSON!")
                            }
                        }

                        HttpURLConnection.HTTP_FORBIDDEN -> {
                            if (key == apiKey1)
                                key = apiKey2
                            else {
                                println("HTTP ERROR! CODE: ${linkData.code}")
                                break
                            }
                        }

                        else -> {
                            println("HTTP ERROR! CODE: ${linkData.code}")
                            break
                        }

                    }
                }
                resolveJob.complete()
                linkType
            }
        }
    }

    suspend fun resolveChannelId(channelLink: Link): String {
        val channelName = "$channelLink".substringAfterLast("/")
        fun fetchData(apiKey: String = apiKey1): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("$apiUrl/search?")
            linkBuilder.append("q=$channelName")
            linkBuilder.append("&part=snippet")
            linkBuilder.append("&type=channel")
            linkBuilder.append("&key=$apiKey")
            return sendHttpRequest(Link(linkBuilder.toString()), RequestMethod.GET)
        }

        return if ("$channelLink".contains("\\S+/channel/\\S+".toRegex())) {
            "$channelLink".substringAfterLast("/")
        } else {
            val idJob = Job()
            var key = apiKey1
            return withContext(IO + idJob) {
                var id = ""
                while (true) {
                    val channelData = fetchData(key)
                    when (channelData.code.code) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val resultsJSON = JSONObject(channelData.data.data)
                                id = resultsJSON.getJSONArray("items").first {
                                    it as JSONObject
                                    it.getJSONObject("id").getString("kind").substringAfter("#") == "channel"
                                            && it.getJSONObject("snippet").getString("title")
                                        .replace(" ", "") == "$channelLink".substringAfterLast("/")
                                }.let {
                                    it as JSONObject
                                    it.getJSONObject("id").getString("channelId")
                                }
                                break
                            } catch (e: JSONException) {
                                e.printStackTrace()
                                println("Error! JSON Broken!")
                                break
                            }
                        }

                        HttpURLConnection.HTTP_FORBIDDEN -> {
                            if (key == apiKey1)
                                key = apiKey2
                            else {
                                println("HTTP ERROR! CODE: ${channelData.code.code}")
                                break
                            }
                        }

                        else -> {
                            println("HTTP ERROR! CODE: ${channelData.code.code}")
                            break
                        }
                    }
                }
                idJob.complete()
                id
            }
        }
    }

}

