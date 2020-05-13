package ts3_musicbot.services

open class Track(var album: String, var artist: String, var title: String, var link: String, var isPlayable: Boolean = true){
    object Empty : Track("", "", "", "")
    fun isNotEmpty() = album.isNotEmpty() || artist.isNotEmpty() || title.isNotEmpty() || link.isNotEmpty()
    fun isEmpty() = album.isEmpty() && artist.isEmpty() && title.isEmpty() && link.isEmpty()
}

