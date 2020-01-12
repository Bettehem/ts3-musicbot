package src.main.services

import org.json.JSONObject
import src.main.util.runCommand

class SoundCloud {
    fun getSongInfo(link: String): Track {
        val jsonData = JSONObject(runCommand("youtube-dl --no-playlist -j $link"))
        val track = Track(album = "", artist = jsonData.getString("uploader"), title = jsonData.getString("title"), link = link)
        println(jsonData)
        return track
    }
}

