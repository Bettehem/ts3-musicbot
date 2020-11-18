package ts3_musicbot.services

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class YouTube {
    private val apiUrl = "https://www.googleapis.com/youtube/v3"
    private val apiKey1 = "AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y"
    private val apiKey2 = "AIzaSyCQBDN5QIpKCub2nNMR7WJiZY7_LYiZImA"
    private val commandRunner = CommandRunner()

    /**
     * Get the title of a YouTube video
     * @param videoLink link to video
     * @return returns a title
     */
    suspend fun getVideoTitle(videoLink: Link): String {
        return getVideo(videoLink).title.name
    }

    suspend fun getVideo(videoLink: Link): Track {
        /**
         * Send a request to YouTube API to get playlist items
         * @param part tells the API what type of information to return
         * @return returns a JSONObject containing the response from the request
         */
        fun sendRequest(part: String = "snippet,status", key: String = apiKey1): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiUrl/videos?")
            urlBuilder.append("id=${videoLink.link.substringAfterLast("/").substringAfter("?v=").substringBefore("&")}")
            urlBuilder.append("&part=${part.replace(",", "%2C")}")
            urlBuilder.append("&key=$key")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
        var key = apiKey1
        var track = Track()
        val ytJob = Job()
        withContext(IO + ytJob) {
            while (true) {
                val response = sendRequest(key = key)
                when (response.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val itemData = JSONObject(response.second.data).getJSONArray("items")[0]
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
                                Artists(),
                                Name(itemData.getJSONObject("snippet").getString("title")),
                                Link("https://youtu.be/${itemData.getString("id")}"),
                                Playability(isPlayable)
                            )
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
                            println("HTTP ERROR! CODE: ${response.first.code}")
                            return@withContext
                        }
                    }
                    else -> {
                        println("HTTP ERROR! CODE: ${response.first.code}")
                        return@withContext
                    }
                }
            }
        }
        return track
    }

    /**
     * Get a list of videos/tracks in a given playlist
     * @param link link to playlist
     * @return returns a list of videos/tracks
     */
    suspend fun getPlaylistTracks(link: Link): TrackList {
        /**
         * Send a request to YouTube API to get playlist items
         * @param maxResults max results to receive. 50 is the maximum per request
         * @param pageToken token to get a specific page of results, if for example there are more than 50 items.
         * @param part tells the API what type of information to return
         * @return returns a JSONObject containing the response from the request
         */
        fun sendRequest(
            maxResults: Int = 50,
            pageToken: String = "",
            part: String = "snippet,status",
            apiKey: String = apiKey1
        ): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiUrl/playlistItems?")
            urlBuilder.append("playlistId=${link.link.substringAfter("list=")}")
            urlBuilder.append("&part=${part.replace(",", "%2C")}")
            urlBuilder.append("&key=$apiKey")
            urlBuilder.append("&maxResults=$maxResults")
            if (pageToken.isNotEmpty())
                urlBuilder.append("&pageToken=$pageToken")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        suspend fun parseItems(data: JSONObject): TrackList {
            val listItems = ArrayList<Track>()
            var totalItems = data.getJSONObject("pageInfo").getInt("totalResults")
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
                            when (result.first.code) {
                                HttpURLConnection.HTTP_OK -> {
                                    pageData = JSONObject(result.second.data)
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
                                                Artists(),
                                                Name(title),
                                                Link(videoLink),
                                                Playability(isPlayable)
                                            )
                                            listItems.add(track)
                                        } catch (e: Exception) {
                                            totalItems -= 1
                                        }
                                    }
                                    if (!pageData.has("nextPageToken"))
                                        return@withContext
                                }
                                HttpURLConnection.HTTP_FORBIDDEN -> {
                                    if (key == apiKey1)
                                        key = apiKey2
                                    else {
                                        println("HTTP ERROR! CODE: ${result.first.code}")
                                        return@withContext
                                    }
                                }
                                else -> {
                                    println("HTTP ERROR! CODE: ${result.first.code}")
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
                        when (result.first.code) {
                            HttpURLConnection.HTTP_OK -> {
                                itemData = JSONObject(result.second.data)
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
                                            Artists(),
                                            Name(title),
                                            Link(videoLink),
                                            Playability(isPlayable)
                                        )
                                        listItems.add(track)
                                    } catch (e: Exception) {
                                        totalItems -= 1
                                    }
                                }
                                return@withContext
                            }
                            HttpURLConnection.HTTP_FORBIDDEN -> {
                                if (key == apiKey1)
                                    key = apiKey2
                                else {
                                    println("HTTP ERROR! CODE: ${result.first.code}")
                                    return@withContext
                                }
                            }
                            else -> {
                                println("HTTP ERROR! CODE: ${result.first.code}")
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
                when (response.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val data = JSONObject(response.second.data)
                            trackList = parseItems(data)
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
                            println("HTTP ERROR! CODE: ${response.first.code}")
                            return@withContext
                        }
                    }
                    else -> {
                        println("HTTP ERROR! CODE: ${response.first.code}")
                        return@withContext
                    }
                }

            }
        }
        return trackList
    }

    /**
     * Search on YouTube for a video/track or a playlist
     * @param searchType can be "track", "video" or "playlist"
     * @param searchQuery search keywords
     * @return returns top 10 results from the search
     */
    suspend fun searchYoutube(searchType: SearchType, searchQuery: SearchQuery): String {
        fun searchData(apiKey: String = apiKey1): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiUrl/search?")
            urlBuilder.append("q=${searchQuery.query.replace(" ", "%20").replace("\"", "%22")}")
            urlBuilder.append("&type=$searchType")
            urlBuilder.append("&maxResults=10")
            urlBuilder.append("&part=snippet")
            urlBuilder.append("&key=$apiKey")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val searchResult = StringBuilder()
        val searchJob = Job()
        var key = apiKey1
        withContext(IO + searchJob) {
            while (true) {
                val result = searchData(key)
                when (result.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val responseData = JSONObject(result.second.data)
                            when (searchType.type) {
                                "track", "video" -> {
                                    val results = responseData.getJSONArray("items")
                                    for (resultData in results) {
                                        resultData as JSONObject

                                        val videoTitle =
                                            resultData.getJSONObject("snippet").getString("title").replace("&amp;", "&")
                                        val videoLink =
                                            "https://youtu.be/${resultData.getJSONObject("id").getString("videoId")}"

                                        searchResult.appendln(
                                            "Title:  $videoTitle\n" +
                                                    "Link:   $videoLink\n"
                                        )
                                    }
                                }
                                "playlist" -> {
                                    val results = responseData.getJSONArray("items")
                                    for (resultData in results) {
                                        resultData as JSONObject

                                        val listTitle = resultData.getJSONObject("snippet").getString("title")
                                        val listCreator = resultData.getJSONObject("snippet").getString("channelTitle")
                                        val listLink =
                                            "https://www.youtube.com/playlist?list=${
                                                resultData.getJSONObject("id")
                                                    .getString("playlistId")
                                            }"

                                        searchResult.appendln(
                                            "Playlist: $listTitle\n" +
                                                    "Creator:    $listCreator\n" +
                                                    "Link:     $listLink\n"
                                        )
                                    }
                                }
                            }
                            return@withContext
                        } catch (e: Exception) {
                            e.printStackTrace()
                            println("Error! JSON Broken!")
                            return@withContext
                        }

                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        if (key == apiKey1)
                            key = apiKey2
                        else {
                            println("HTTP ERROR! CODE: ${result.first.code}")
                            return@withContext
                        }
                    }
                    else -> {
                        println("HTTP ERROR! CODE: ${result.first.code}")
                        return@withContext
                    }
                }
            }
        }

        return searchResult.toString().substringBeforeLast("\n")
    }
}


