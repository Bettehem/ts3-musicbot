package src.main.services

open class Track(val album: String, val artist: String, val title: String, val link: String){
    object Empty : Track("", "", "", "")
    fun isNotEmpty() = album.isNotEmpty() || artist.isNotEmpty() || title.isNotEmpty() || link.isNotEmpty()
    fun isEmpty() = album.isEmpty() && artist.isEmpty() && title.isEmpty() && link.isEmpty()
}

