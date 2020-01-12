package src.main.services

import org.json.JSONObject
import src.main.util.runCommand

class SoundCloud {
    fun getSongInfo(link: String): Track {
        val jsonData = JSONObject(runCommand("youtube-dl --no-playlist -j $link"))
        val track =
            Track(album = "", artist = jsonData.getString("uploader"), title = jsonData.getString("title"), link = link)
        println(jsonData)
        return track
    }

    //gets info on track(s) based on soundcloud song/playlist link
    fun getTracks(link: String): ArrayList<Track> {
        val trackList = ArrayList<Track>()
        val data = StringBuilder()
            .append(runCommand("youtube-dl -j $link").substringBeforeLast("}"))
            .append("}").toString()
        for (line in data.split("\n".toRegex())) {
            val jsonLine = JSONObject(line)
            trackList.add(
                Track(
                    "",
                    jsonLine.getString("uploader"),
                    jsonLine.getString("title"),
                    jsonLine.getString("webpage_url")
                )
            )
        }
        println(trackList)
        return trackList
    }
}

