package ts3_musicbot.services

import org.json.JSONObject
import ts3_musicbot.util.CommandRunner
import ts3_musicbot.util.sendHttpRequest
import java.net.HttpURLConnection
import java.net.URL

class SoundCloud {
    private val commandRunner = CommandRunner()
    private var clientId = "dp8jYbqmo9I3kJhH02V2UjpLbmMgwbN5"

    /**
     * Updates the clientId
     */
    private fun updateId() {
        println("Updating SoundCloud ClientId")
        val lines = commandRunner.runCommand(
            "curl https://soundcloud.com 2> /dev/null | grep -E \"<script crossorigin src=\\\"https:\\/\\/\\S*\\.js\\\"></script>\"",
            printOutput = false
        ).split("\n")
        for (line in lines) {
            val url = line.substringAfter("\"").substringBefore("\"")
            val data = commandRunner.runCommand("curl $url 2> /dev/null | grep \"client_id=\"", printOutput = false)
            if (data.isNotEmpty()) {
                val id = data.substringAfter("client_id=").substringBefore("&")
                clientId = id
                break
            }
        }
    }

    /**
     * Get tracks from a SoundCloud playlist
     * @param link SoundCloud playlist link
     * @return returns an ArrayList containing the playlist's tracks as Track objects
     */
    fun getPlaylistTracks(link: String): ArrayList<Track> {
        fun getTracks(): Pair<Int, String> {
            val id = resolveId(link)

            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api-v2.soundcloud.com/playlists/")
            urlBuilder.append(id)
            urlBuilder.append("?client_id=$clientId")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        val trackList = ArrayList<Track>()
        var gettingData = true
        while (gettingData) {
            val trackData = getTracks()
            when (trackData.first) {
                HttpURLConnection.HTTP_OK -> {
                    gettingData = false
                    try {
                        val tracks = JSONObject(trackData.second).getJSONArray("tracks")
                        if (tracks.length() > 50) {
                            println("This playlist has ${tracks.length()} tracks. Please wait...")
                        }
                        for (item in tracks) {
                            item as JSONObject
                            try {
                                trackList.add(getTrack("https://api.soundcloud.com/tracks/${item.getInt("id")}"))
                            } catch (e: Exception) {
                                trackList.add(Track.Empty)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    updateId()
                }
                else -> {
                }
            }
        }
        return trackList
    }

    /**
     * Get a Track object for a given SoundCloud song link
     * @param link link to the song
     * @return returns a Track object with uploader, title and link
     */
    fun getTrack(link: String): Track {
        return try {
            val id = if (link.startsWith("https://api.soundcloud.com/tracks/")) {
                link.substringAfterLast("/")
            } else {
                resolveId(link)
            }


            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api-v2.soundcloud.com/resolve?url=")
            urlBuilder.append("https://api.soundcloud.com/tracks/$id")
            urlBuilder.append("&client_id=$clientId")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            val response = JSONObject(rawResponse.second)

            Track(
                "",
                response.getJSONObject("user").getString("username"),
                response.getString("title"),
                response.getString("permalink_url")
            )
        } catch (e: Exception) {
            Track.Empty
        }
    }

    /**
     * Resolves the given SoundCloud link and returns it's id
     * @param link link to resolve
     * @return returns the corresponding id for the given link as a String
     */
    private fun resolveId(link: String): String {
        fun getId(): Pair<Int, String> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api-v2.soundcloud.com/resolve?")
            urlBuilder.append("client_id=$clientId")
            urlBuilder.append("&url=$link")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        var id = ""
        var gettingData = true
        while (gettingData) {
            val idData = getId()
            when (idData.first) {
                HttpURLConnection.HTTP_OK -> {
                    id = JSONObject(idData.second).getInt("id").toString()
                    gettingData = false
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    updateId()
                }
                else -> {
                    println("Error: code ")
                }
            }
        }
        return id
    }
}
