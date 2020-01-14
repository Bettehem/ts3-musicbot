package src.main.services

import org.json.JSONObject
import src.main.util.runCommand

class SoundCloud {
    fun getSongInfo(link: String): Track {
        return try {
            val jsonData = JSONObject(runCommand("youtube-dl --no-playlist -i -j $link"))
            Track(album = "", artist = jsonData.getString("uploader"), title = jsonData.getString("title"), link = link)
        }catch (e: Exception){
            Track(album = "", artist = "", title = "", link = link)
        }
    }

    //gets info on track(s) based on SoundCloud song/playlist link
    fun getTracks(link: String): ArrayList<Track> {
        val trackList = ArrayList<Track>()
        val data = JSONObject(runCommand("youtube-dl -i -J $link"))
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
            trackList.add(SoundCloud().getSongInfo(link))
        }
        return trackList
    }
}

