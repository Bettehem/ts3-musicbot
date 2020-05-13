package ts3_musicbot.services

import org.json.JSONObject
import ts3_musicbot.util.runCommand
import ts3_musicbot.util.sendHttpRequest
import java.net.URL

class YouTube {
    /**
     * Get the title of a YouTube video
     * @param videoLink link to video
     * @return returns a title
     */
    fun getTitle(videoLink: String): String {
        return runCommand("youtube-dl --no-playlist --geo-bypass -e \"$videoLink\" 2> /dev/null", printErrors = false, printOutput = false)
    }

    /**
     * Get a list of videos/tracks in a given playlist
     * @param link link to playlist
     * @return returns a list of videos/tracks
     */
    fun getPlaylistTracks(link: String): ArrayList<Track> {
        /**
         * Send a request to YouTube API to get playlist items
         * @param maxResults max results to receive. 50 is the maximum per request
         * @param pageToken token to get a specific page of results, if for example there are more than 50 items.
         * @param part tells the API what type of information to return
         * @return returns a JSONObject containing the response from the request
         */
        fun sendRequest(maxResults: Int = 50, pageToken: String = "", part: String = "snippet,status"): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://www.googleapis.com/youtube/v3/playlistItems?")
            urlBuilder.append("playlistId=${link.substringAfter("list=")}")
            urlBuilder.append("&part=${part.replace(",", "%2C")}")
            urlBuilder.append("&key=AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y")
            urlBuilder.append("&maxResults=$maxResults")
            if (pageToken.isNotEmpty())
                urlBuilder.append("&pageToken=$pageToken")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            return try {
                JSONObject(rawResponse.second)
            }catch (e: Exception){
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
                        "https://youtu.be/${item.getJSONObject("snippet").getJSONObject("resourceId").getString("videoId")}"
                        val isPlayable = when (item.getJSONObject("status").getString("privacyStatus")) {
                            "public", "unlisted" -> true
                            else -> false
                        }
                        val track = Track("", "", "", "")
                        track.title = title
                        track.link = videoLink
                        track.isPlayable = isPlayable
                        listItems.add(track)
                    }catch(e: Exception){
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
                    "https://youtu.be/${item.getJSONObject("snippet").getJSONObject("resourceId").getString("videoId")}"
                    val isPlayable = when (item.getJSONObject("status").getString("privacyStatus")) {
                        "public", "unlisted" -> true
                        else -> false
                    }
                    val track = Track("", "", "", "")
                    track.title = title
                    track.link = videoLink
                    track.isPlayable = isPlayable
                    listItems.add(track)
                }catch(e: Exception){
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
    fun searchYoutube(searchType: String, searchQuery: String): String {
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://www.googleapis.com/youtube/v3/search?")
        urlBuilder.append("q=${searchQuery.replace(" ", "%20").replace("\"", "%22")}")
        urlBuilder.append("&type=$searchType")
        urlBuilder.append("&maxResults=10")
        urlBuilder.append("&part=snippet")
        urlBuilder.append("&key=AIzaSyB_FpJTYVMuQ2I_DxaidXUd7z4Q-ScMv6Y")
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse.second)
        val searchResult = StringBuilder()

        when (searchType) {
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
                        "https://www.youtube.com/playlist?list=${resultData.getJSONObject("id").getString("playlistId")}"

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
