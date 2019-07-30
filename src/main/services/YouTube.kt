package src.main.services

import src.main.util.runCommand

class YouTube{
    fun getTitle(videoLink: String): String{
        return runCommand("youtube-dl --geo-bypass -s -e \"$videoLink\"", printErrors = false)
    }
}