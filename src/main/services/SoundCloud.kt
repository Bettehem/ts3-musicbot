package src.main.services

import org.json.JSONArray
import org.json.JSONObject
import src.main.util.runCommand
import src.main.util.sendHttpRequest
import java.net.URL

class SoundCloud {
    private val clientId = "L1Tsmo5VZ0rup3p9fjY67862DyPiWGaG"


    /*
    // youtube-dl way of getting track info
    //TODO delete getSongIfo and getTracks if not needed

    fun getSongInfo(link: String): Track {
        return try {
            val jsonData = JSONObject(runCommand("youtube-dl --no-playlist -i -j $link 2> /dev/null", printOutput = false))
            Track(album = "", artist = jsonData.getString("uploader"), title = jsonData.getString("title"), link = link)
        }catch (e: Exception){
            Track(album = "", artist = "", title = "", link = link)
        }
    }

    //gets info on track(s) based on SoundCloud song/playlist link
    fun getTracks(link: String): ArrayList<Track> {
        val trackList = ArrayList<Track>()
        val data = JSONObject(runCommand("youtube-dl -i -J $link 2> /dev/null", printOutput = false))
        try {
            if (data.getString("_type")!!.contentEquals("playlist")) {
                val list = data.getJSONArray("entries")
                for (item in list) {
                    try {
                        item as JSONObject
                        trackList.add(
                            Track(
                                "",
                                item.getString("uploader"),
                                item.getString("title"),
                                item.getString("webpage_url")
                            )
                        )
                    } catch (e: Exception) {

                    }
                }
            }
        } catch (e: Exception) {
            trackList.add(getSongInfo(link))
        }
        return trackList
    }
     */

    /**
     * Get tracks from a SoundCloud playlist
     * @param link SoundCloud playlist link
     * @return returns an ArrayList containing the playlist's tracks as Track objects
     */
    fun getPlaylistTracks(link: String): ArrayList<Track> {
        return try {
            val id = resolveId(link)

            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.soundcloud.com/playlists/")
            urlBuilder.append("$id/tracks")
            urlBuilder.append("?client_id=$clientId")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            val response = JSONArray(rawResponse)

            val trackList = ArrayList<Track>()
            for (item in response) {
                item as JSONObject
                trackList.add(
                    Track(
                        "",
                        item.getJSONObject("user").getString("username"),
                        item.getString("title"),
                        item.getString("permalink_url")
                    )
                )
            }
            trackList
        } catch (e: Exception) {
            ArrayList()
        }
    }

    /**
     * Get a Track object for a given SoundCloud song link
     * @param link link to the song
     * @return returns a Track object with uploader, title and link
     */
    fun getTrack(link: String): Track {
        return try {
            val id = resolveId(link)


            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.soundcloud.com/tracks/")
            urlBuilder.append(id)
            urlBuilder.append("?client_id=$clientId")
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            val response = JSONObject(rawResponse)

            Track("", response.getJSONObject("user").getString("username"), response.getString("title"), link)
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
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.soundcloud.com/resolve?")
        urlBuilder.append("client_id=$clientId")
        urlBuilder.append("&url=$link")
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)


        return response.getInt("id").toString()
    }
}

