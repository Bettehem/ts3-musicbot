package ts3_musicbot.services

import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class YouTube {
    private val apiUrl = "https://www.googleapis.com/youtube/v3"
    private val apiKey = "AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y"
    private val commandRunner = CommandRunner()

    /**
     * Get the title of a YouTube video
     * @param videoLink link to video
     * @return returns a title
     */
    fun getVideoTitle(videoLink: Link): String {
        return commandRunner.runCommand(
            "youtube-dl --no-playlist --geo-bypass -e \"$videoLink\" 2> /dev/null",
            printErrors = false,
            printOutput = false
        )
    }

    fun getVideo(videoLink: Link): Track {
        /**
         * Send a request to YouTube API to get playlist items
         * @param part tells the API what type of information to return
         * @return returns a JSONObject containing the response from the request
         */
        fun sendRequest(part: String = "snippet,status"): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiUrl/videos?")
            urlBuilder.append("id=${videoLink.link.substringAfterLast("/").substringAfter("?v=").substringBefore("&")}")
            urlBuilder.append("&part=${part.replace(",", "%2C")}")
            urlBuilder.append("&key=$apiKey")
            val rawResponse = sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
            return try {
                JSONObject(rawResponse.second.data)
            } catch (e: Exception) {
                JSONObject("{pageInfo: {totalResults: 0}, items: []}")
            }
        }

        val response = sendRequest()
        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
        val itemData = response.getJSONArray("items")[0]
        itemData as JSONObject
        val releaseDate =
            ReleaseDate(LocalDate.parse(itemData.getJSONObject("snippet").getString("publishedAt"), formatter))
        val isPlayable = when (itemData.getJSONObject("status").getString("privacyStatus")) {
            "public", "unlisted" -> true
            else -> false
        }
        return Track(
            Album(
                Name(""),
                Artists(emptyList()),
                releaseDate,
                TrackList(emptyList()),
                Link("")
            ),
            Artists(emptyList()),
            Name(itemData.getJSONObject("snippet").getString("title")),
            Link("https://youtu.be/${itemData.getString("id")}"),
            Playability(isPlayable)
        )
    }

    /**
     * Get a list of videos/tracks in a given playlist
     * @param link link to playlist
     * @return returns a list of videos/tracks
     */
    fun getPlaylistTracks(link: Link): ArrayList<Track> {
        /**
         * Send a request to YouTube API to get playlist items
         * @param maxResults max results to receive. 50 is the maximum per request
         * @param pageToken token to get a specific page of results, if for example there are more than 50 items.
         * @param part tells the API what type of information to return
         * @return returns a JSONObject containing the response from the request
         */
        fun sendRequest(maxResults: Int = 50, pageToken: String = "", part: String = "snippet,status"): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$apiUrl/playlistItems?")
            urlBuilder.append("playlistId=${link.link.substringAfter("list=")}")
            urlBuilder.append("&part=${part.replace(",", "%2C")}")
            urlBuilder.append("&key=$apiKey")
            urlBuilder.append("&maxResults=$maxResults")
            if (pageToken.isNotEmpty())
                urlBuilder.append("&pageToken=$pageToken")
            val rawResponse = sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
            return try {
                JSONObject(rawResponse.second.data)
            } catch (e: Exception) {
                JSONObject("{pageInfo: {totalResults: 0}, items: []}")
            }
        }

        var response = sendRequest(1, part = "id")

        val listItems = ArrayList<Track>()
        var totalItems = response.getJSONObject("pageInfo").getInt("totalResults")
        if (totalItems > 50) {
            while (listItems.size < totalItems) {
                response = sendRequest(
                    pageToken = if (listItems.size > 0) {
                        response.getString("nextPageToken")
                    } else {
                        ""
                    }
                )
                for (item in response.getJSONArray("items")) {
                    item as JSONObject

                    try {
                        val title = item.getJSONObject("snippet").getString("title")
                        val videoLink =
                            "https://youtu.be/${item.getJSONObject("snippet").getJSONObject("resourceId")
                                .getString("videoId")}"
                        val isPlayable = when (item.getJSONObject("status").getString("privacyStatus")) {
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
                            Album(
                                Name(""),
                                Artists(emptyList()),
                                releaseDate,
                                TrackList(emptyList()),
                                Link("")
                            ),
                            Artists(emptyList()),
                            Name(title),
                            Link(videoLink),
                            Playability(isPlayable)
                        )
                        listItems.add(track)
                    } catch (e: Exception) {
                        totalItems -= 1
                    }
                }
                if (!response.has("nextPageToken"))
                    break
            }
        } else {
            response = sendRequest()
            for (item in response.getJSONArray("items")) {
                item as JSONObject

                try {
                    val title = item.getJSONObject("snippet").getString("title")
                    val videoLink =
                        "https://youtu.be/${item.getJSONObject("snippet").getJSONObject("resourceId")
                            .getString("videoId")}"
                    val isPlayable = when (item.getJSONObject("status").getString("privacyStatus")) {
                        "public", "unlisted" -> true
                        else -> false
                    }
                    val formatter = DateTimeFormatter.ISO_INSTANT
                    val releaseDate =
                        ReleaseDate(LocalDate.parse(item.getJSONObject("snippet").getString("publishedAt"), formatter))
                    val track = Track(
                        Album(
                            Name(""),
                            Artists(emptyList()),
                            releaseDate,
                            TrackList(emptyList()),
                            Link("")
                        ),
                        Artists(emptyList()),
                        Name(title),
                        Link(videoLink),
                        Playability(isPlayable)
                    )
                    listItems.add(track)
                } catch (e: Exception) {
                    totalItems -= 1
                }
            }
        }
        return listItems
    }

    /**
     * Search on YouTube for a video/track or a playlist
     * @param searchType can be "track", "video" or "playlist"
     * @param searchQuery search keywords
     * @return returns top 10 results from the search
     */
    fun searchYoutube(searchType: SearchType, searchQuery: SearchQuery): String {
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://www.googleapis.com/youtube/v3/search?")
        urlBuilder.append("q=${searchQuery.query.replace(" ", "%20").replace("\"", "%22")}")
        urlBuilder.append("&type=$searchType")
        urlBuilder.append("&maxResults=10")
        urlBuilder.append("&part=snippet")
        urlBuilder.append("&key=AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y")
        val rawResponse = sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        val response = JSONObject(rawResponse.second.data)
        val searchResult = StringBuilder()

        when (searchType.type) {
            "track", "video" -> {
                val results = response.getJSONArray("items")
                for (resultData in results) {
                    resultData as JSONObject

                    val videoTitle = resultData.getJSONObject("snippet").getString("title").replace("&amp;", "&")
                    val videoLink = "https://youtu.be/${resultData.getJSONObject("id").getString("videoId")}"

                    searchResult.appendln(
                        "Title:  $videoTitle\n" +
                                "Link:   $videoLink\n"
                    )
                }
            }
            "playlist" -> {
                val results = response.getJSONArray("items")
                for (resultData in results) {
                    resultData as JSONObject

                    val listTitle = resultData.getJSONObject("snippet").getString("title")
                    val listCreator = resultData.getJSONObject("snippet").getString("channelTitle")
                    val listLink =
                        "https://www.youtube.com/playlist?list=${resultData.getJSONObject("id")
                            .getString("playlistId")}"

                    searchResult.appendln(
                        "Playlist: $listTitle\n" +
                                "Creator:    $listCreator\n" +
                                "Link:     $listLink\n"
                    )
                }
            }
        }
        return searchResult.toString().substringBeforeLast("\n")
    }
}


