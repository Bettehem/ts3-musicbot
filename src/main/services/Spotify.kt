package src.main.services

import org.json.JSONObject
import src.main.util.sendHttpRequest
import java.net.URL

class Spotify {
    private fun getSpotifyToken(): String{
        val auth = "ZGUzZGFlNGUxZTE3NGRkNGFjYjY0YWYyMjcxMWEwYmI6ODk5OGQxMmJjZDBlNDAzM2E2Mzg2ZTg4Y2ZjZTk2NDg="
        val url = URL("https://accounts.spotify.com/api/token")
        val requestMethod = "POST"
        val properties = arrayOf(
            "Authorization: Basic $auth",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val data = arrayOf("grant_type=client_credentials")
        val rawResponse = sendHttpRequest(url, requestMethod, properties, data)
        val response = JSONObject(rawResponse)
        return response.getString("access_token")
    }

    fun searchSpotify(searchType: String, searchQuery: String): String{
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.spotify.com/v1/search?")
        urlBuilder.append("q=${searchQuery.replace(" ", "%20").replace("\"", "%22")}")
        urlBuilder.append("&type=$searchType")
        //urlBuilder.append("&limit=10")
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Authorization: Bearer ${getSpotifyToken()}",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)
        val searchResult = StringBuilder()

        when (searchType){
            "track" -> {
                val trackList = response.getJSONObject("tracks").getJSONArray("items")
                for (trackData in trackList){
                    trackData as JSONObject

                    val artists = StringBuilder()
                    artists.append("Artist: ")
                    for (artistData in trackData.getJSONArray("artists")){
                        val artist = artistData as JSONObject
                        artists.append("${artist.getString("name")}, ")
                    }

                    val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single"){
                        "${trackData.getJSONObject("album").getString("name")} (Single)"
                    }else{
                        trackData.getJSONObject("album").getString("name")
                    }

                    val songName = trackData.getString("name")

                    val songLink = trackData.getJSONObject("external_urls").getString("spotify")

                    searchResult.appendln("" +
                            "${artists.toString().substringBeforeLast(",")}\n" +
                            "Album:  $albumName\n" +
                            "Title:  $songName\n" +
                            "Link:   $songLink\n"
                    )
                }
            }
            "playlist" -> {
                val playlists = response.getJSONObject("playlists").getJSONArray("items")
                for (listData in playlists){
                    listData as JSONObject

                    val listName = listData.getString("name")
                    val listOwner = if (listData.getJSONObject("owner").get("display_name") != null){
                        listData.getJSONObject("owner").get("display_name")
                    }else{
                        "N/A"
                    }
                    val listLink = listData.getJSONObject("external_urls").getString("spotify")

                    searchResult.appendln("" +
                            "Playlist: $listName\n" +
                            "Owner:    $listOwner\n" +
                            "Link:     $listLink\n"
                    )
                }
            }
        }
        return searchResult.toString().substringBeforeLast("\n")
    }

    fun getPlaylistTracks(playListLink: String): ArrayList<Track>{
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.spotify.com/v1/playlists/")
        urlBuilder.append(if (playListLink.contains("spotify:") && playListLink.contains("playlist:")){
            playListLink.substringAfterLast(":")
        }else{
            playListLink.substringAfter("playlist/").substringBefore("?si=")
        })
        urlBuilder.append("/tracks")
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Authorization: Bearer ${getSpotifyToken()}",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)
        val trackList = ArrayList<Track>()

        for (item in response.getJSONArray("items")){
            item as JSONObject
            val album = item.getJSONObject("track").getJSONObject("album").getString("name")
            val artist = item.getJSONObject("track").getJSONArray("artists").forEach { it as JSONObject; StringBuilder().append("${it.getString("name")},") }.toString().substringBeforeLast(",")
            val title = item.getJSONObject("track").getString("name")
            val link = item.getJSONObject("track").getJSONObject("external_urls").getString("spotify")
            trackList.add(Track(album, artist, title, link))
        }


        return trackList
    }

    class Track(val album: String, val artist: String, val title: String, val link: String)
}
