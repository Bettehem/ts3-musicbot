package src.main.services

import org.json.JSONArray
import org.json.JSONObject
import src.main.util.runCommand
import src.main.util.sendHttpRequest
import java.net.URL

class SoundCloud {
    private val clientId = "7jmNkIpPjgR8GNweCuKnUywa81GQsSGT"


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
            urlBuilder.append("https://api-v2.soundcloud.com/playlists/")
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

            val trackList = ArrayList<Track>()
            for (item in response.getJSONArray("tracks")) {
                item as JSONObject
                try {
                    trackList.add(getTrack("https://api.soundcloud.com/tracks/${item.getInt("id")}"))
                }catch (e: Exception){
                    trackList.add(Track.Empty)
                }
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
            val id = if (link.startsWith("https://api.soundcloud.com/tracks/")){
                link.substringAfterLast("/")
            }else{
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
            val response = JSONObject(rawResponse)

            Track("", response.getJSONObject("user").getString("username"), response.getString("title"), response.getString("permalink_url"))
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
        urlBuilder.append("https://api-v2.soundcloud.com/resolve?")
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

