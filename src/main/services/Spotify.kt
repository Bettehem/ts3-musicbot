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
            "album" -> {
                val albums = response.getJSONObject("albums").getJSONArray("items")
                for (album in albums){
                    album as JSONObject
                    
                    val artists = StringBuilder()
                    artists.append("Artist: ")
                    for (artistData in album.getJSONArray("artists")){
                        val artist = artistData as JSONObject
                        artists.append("${artist.getString("name")}, ")
                    }


                    val albumName = if (album.getString("album_type") == "single"){
                        "${album.getString("name")} (Single)"
                    }else{
                        album.getString("name")
                    }

                    val albumLink = album.getJSONObject("external_urls").getString("spotify") 
                        
                    searchResult.appendln("" +
                            "${artists.toString().substringBeforeLast(",")}\n" +
                            "Album:  $albumName\n" +
                            "Link:   $albumLink\n"
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
        //First get the playlist length 
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.spotify.com/v1/playlists/")
        urlBuilder.append(if (playListLink.contains("spotify:") && playListLink.contains("playlist:")){
            playListLink.substringAfterLast(":")
        }else{
            playListLink.substringAfter("playlist/").substringBefore("?si=")
        })
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Authorization: Bearer ${getSpotifyToken()}",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)

        //playlist length
        val playlistLength = response.getJSONObject("tracks").getInt("total")


        //Now get all tracks
        val trackItems = ArrayList<Track>()
        //spotify only shows 100 items per search, so with each 100 items, listOffset will be increased
        var listOffset = 0
        while (trackItems.size < playlistLength){
             
            val listUrlBuilder = StringBuilder()
            listUrlBuilder.append("https://api.spotify.com/v1/playlists/")
            listUrlBuilder.append(if (playListLink.contains("spotify:") && playListLink.contains("playlist:")){
                playListLink.substringAfterLast(":")
            }else{
                playListLink.substringAfter("playlist/").substringBefore("?si=")
            })
            listUrlBuilder.append("/tracks?limit=100")
            listUrlBuilder.append("&offset=$listOffset")
            val listUrl = URL(listUrlBuilder.toString())
            val listRequestMethod = "GET"
            val listProperties = arrayOf(
                "Authorization: Bearer ${getSpotifyToken()}",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val listRawResponse = sendHttpRequest(listUrl, listRequestMethod, listProperties)
            val listResponse = JSONObject(listRawResponse)

            for (item in listResponse.getJSONArray("items")){
                item as JSONObject
                if (item.getJSONObject("track").get("id") != null){
                    val albumName = if (item.getJSONObject("track").getJSONObject("album").getString("album_type") == "single"){
                        "${item.getJSONObject("track").getJSONObject("album").getString("name")} (Single)"
                    }else{
                        item.getJSONObject("track").getJSONObject("album").getString("name")
                    }
                    val artist = item.getJSONObject("track").getJSONArray("artists").forEach { it as JSONObject; StringBuilder().append("${it.getString("name")},") }.toString().substringBeforeLast(",")
                    val title = item.getJSONObject("track").getString("name")
                    val link = item.getJSONObject("track").getJSONObject("external_urls").getString("spotify")
                    trackItems.add(Track(albumName, artist, title, link))
                }
            }

            listOffset += 100
        }
        
        return trackItems
    }

    fun getAlbumTracks(albumLink: String): ArrayList<Track>{
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.spotify.com/v1/albums/")
        urlBuilder.append(if (albumLink.contains("spotify:") && albumLink.contains("album:")){
            albumLink.substringAfterLast(":")
        }else{
            albumLink.substringAfter("album/").substringBefore("?si=")
        })
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Authorization: Bearer ${getSpotifyToken()}",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)

        val trackItemsLength = response.getJSONObject("tracks").getInt("total")
        
        val albumName = if (response.getString("album_type") == "single"){
                "${response.getString("name")} (Single)"        
            }else{
                response.getString("name")
            }

        //Now get all tracks
        val trackItems = ArrayList<Track>()
        //spotify only shows 20 items per search, so with each 20 items, listOffset will be increased
        var listOffset = 0
        while (trackItems.size < trackItemsLength){
             
            val albumUrlBuilder = StringBuilder()
            albumUrlBuilder.append("https://api.spotify.com/v1/albums/")
            albumUrlBuilder.append(if (albumLink.contains("spotify:") && albumLink.contains("album:")){
                albumLink.substringAfterLast(":")
            }else{
                albumLink.substringAfter("album/").substringBefore("?si=")
            })
            albumUrlBuilder.append("/tracks?limit=20")
            albumUrlBuilder.append("&offset=$listOffset")
            val albumUrl = URL(albumUrlBuilder.toString())
            val albumRequestMethod = "GET"
            val albumProperties = arrayOf(
                "Authorization: Bearer ${getSpotifyToken()}",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val albumRawResponse = sendHttpRequest(albumUrl, albumRequestMethod, albumProperties)
            val albumResponse = JSONObject(albumRawResponse)

            for (item in albumResponse.getJSONArray("items")){
                item as JSONObject
                val artist = item.getJSONArray("artists").forEach { it as JSONObject; StringBuilder().append("${it.getString("name")},") }.toString().substringBeforeLast(",")
                val title = item.getString("name")
                val link = item.getJSONObject("external_urls").getString("spotify")
                trackItems.add(Track(albumName, artist, title, link))
            }

            listOffset += 20
        }


        return trackItems
    }

    fun getTrack(trackLink: String): Track{
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.spotify.com/v1/tracks/")
        urlBuilder.append(if (trackLink.contains("spotify:") && trackLink.contains(":track:")){
            trackLink.substringAfterLast(":")
        }else{
            trackLink.substringAfter("track/").substringBefore("?si=")
        })
        val url = URL(urlBuilder.toString())
        val requestMethod = "GET"
        val properties = arrayOf(
            "Authorization: Bearer ${getSpotifyToken()}",
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
        val rawResponse = sendHttpRequest(url, requestMethod, properties)
        val response = JSONObject(rawResponse)
        val artistsBuilder = java.lang.StringBuilder()
        response.getJSONArray("artists").forEach {
            it as JSONObject
            artistsBuilder.append("${it.getString("name")}, ")
        }
        val artist = artistsBuilder.toString().substringBeforeLast(",")
        val albumName = if (response.getJSONObject("album").getString("album_type") == "single"){
                "${response.getJSONObject("album").getString("name")} (Single)"
            }else{
                response.getJSONObject("album").getString("name")
            }
        val title = response.getString("name")
        return Track(albumName, artist, title, trackLink)
    }
}
