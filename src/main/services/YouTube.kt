package src.main.services

import src.main.util.runCommand

class YouTube{
    fun getTitle(videoLink: String): String{
        return runCommand("youtube-dl --no-playlist --geo-bypass -e \"$videoLink\" 2> /dev/null", printErrors = false)
    }
}
