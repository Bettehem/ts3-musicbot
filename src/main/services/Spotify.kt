package src.main.services

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import src.main.util.sendHttpRequest
import java.net.URL

class Spotify(private val market: String = "") {
    var accessToken = ""

    fun updateToken() {
        println("Updating Spotify access token...")
        accessToken = getSpotifyToken()
    }

    private fun getSpotifyToken(): String {
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
        val response = JSONObject(rawResponse.second)
        return response.getString("access_token")
    }

    fun searchSpotify(searchType: String, searchQuery: String): String {
        val searchResult = StringBuilder()

        fun searchData(): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/search?")
            urlBuilder.append("q=${searchQuery.replace(" ", "%20").replace("\"", "%22")}")
            urlBuilder.append("&type=$searchType")
            //urlBuilder.append("&limit=10")
            if (market.isNotEmpty()) {
                urlBuilder.append("&market=$market")
            }
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            return if (rawResponse.second.isNotEmpty()) {
                JSONObject(rawResponse)
            } else {
                JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
            }
        }

        fun parseResults(searchData: JSONObject) {
            //token is valid, parse data
            when (searchType) {
                "track" -> {
                    val trackList = searchData.getJSONObject("tracks").getJSONArray("items")
                    for (trackData in trackList) {
                        trackData as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: ")
                        for (artistData in trackData.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }

                        val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                            "${trackData.getJSONObject("album").getString("name")} (Single)"
                        } else {
                            trackData.getJSONObject("album").getString("name")
                        }

                        val songName = trackData.getString("name")

                        val songLink = trackData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  $albumName\n" +
                                    "Title:  $songName\n" +
                                    "Link:   $songLink\n"
                        )
                    }
                }
                "album" -> {
                    val albums = searchData.getJSONObject("albums").getJSONArray("items")
                    for (album in albums) {
                        album as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: ")
                        for (artistData in album.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }


                        val albumName = if (album.getString("album_type") == "single") {
                            "${album.getString("name")} (Single)"
                        } else {
                            album.getString("name")
                        }

                        val albumLink = album.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  $albumName\n" +
                                    "Link:   $albumLink\n"
                        )
                    }

                }
                "playlist" -> {
                    val playlists = searchData.getJSONObject("playlists").getJSONArray("items")
                    for (listData in playlists) {
                        listData as JSONObject

                        val listName = listData.getString("name")
                        val listOwner = if (listData.getJSONObject("owner").get("display_name") != null) {
                            listData.getJSONObject("owner").get("display_name")
                        } else {
                            "N/A"
                        }
                        val listLink = listData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Playlist: $listName\n" +
                                    "Owner:    $listOwner\n" +
                                    "Link:     $listLink\n"
                        )
                    }
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            println("Searching for \"$searchQuery\" on Spotify...")
            val searchData = searchData()
            //check if token is valid
            if (searchData.has("error") && searchData.getJSONObject("error").getInt("status") == 401) {
                //token has expired, update it
                updateToken()
            } else {
                parseResults(searchData)
                gettingData = false
            }
        }
        return searchResult.toString().substringBeforeLast("\n")
    }

    fun getPlaylistTracks(playListLink: String): ArrayList<Track> {
        val trackItems = ArrayList<Track>()

        fun getPlaylistData(): JSONObject {
            //First get the playlist length
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/playlists/")
            urlBuilder.append(
                if (playListLink.contains("spotify:") && playListLink.contains("playlist:")) {
                    playListLink.substringAfterLast(":")
                } else {
                    playListLink.substringAfter("playlist/").substringBefore("?si=")
                }
            )
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            return if (rawResponse.second.isNotEmpty())
                JSONObject(rawResponse.second)
            else
                JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
        }

        fun parsePlaylistData(playlistData: JSONObject) {
            //get playlist length
            var playlistLength = playlistData.getJSONObject("tracks").getInt("total")
            //Now get all tracks
            //spotify only shows 100 items per search, so with each 100 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < playlistLength) {

                fun getItems(): JSONObject {
                    val listUrlBuilder = StringBuilder()
                    listUrlBuilder.append("https://api.spotify.com/v1/playlists/")
                    listUrlBuilder.append(
                        if (playListLink.contains("spotify:") && playListLink.contains("playlist:")) {
                            playListLink.substringAfterLast(":")
                        } else {
                            playListLink.substringAfter("playlist/").substringBefore("?si=")
                        }
                    )
                    listUrlBuilder.append("/tracks?limit=100")
                    listUrlBuilder.append("&offset=$listOffset")
                    if (market.isNotEmpty()) {
                        listUrlBuilder.append("&market=$market")
                    }
                    val listUrl = URL(listUrlBuilder.toString())
                    val listRequestMethod = "GET"
                    val listProperties = arrayOf(
                        "Authorization: Bearer $accessToken",
                        "Content-Type: application/x-www-form-urlencoded",
                        "User-Agent: Mozilla/5.0"
                    )
                    val listRawResponse = sendHttpRequest(listUrl, listRequestMethod, listProperties)
                    return if (listRawResponse.second.isNotEmpty()) {
                        JSONObject(listRawResponse.second)
                    } else {
                        JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
                    }
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        try {
                            if (item.get("track") != null) {
                                if (item.getJSONObject("track").get("id") != null) {
                                    if (!item.getJSONObject("track").getBoolean("is_local")) {
                                        val albumName = if (item.getJSONObject("track").getJSONObject("album")
                                                .getString("album_type") == "single"
                                        ) {
                                            "${item.getJSONObject("track").getJSONObject("album")
                                                .getString("name")} (Single)"
                                        } else {
                                            item.getJSONObject("track").getJSONObject("album").getString("name")
                                        }
                                        val artist = item.getJSONObject("track").getJSONArray("artists")
                                            .forEach { it as JSONObject; StringBuilder().append("${it.getString("name")},") }
                                            .toString().substringBeforeLast(",")
                                        val title = item.getJSONObject("track").getString("name")
                                        val link =
                                            item.getJSONObject("track").getJSONObject("external_urls")
                                                .getString("spotify")
                                        val isPlayable = if (market.isNotEmpty()) {
                                            item.getJSONObject("track").getBoolean("is_playable")
                                        } else {
                                            true
                                        }
                                        trackItems.add(Track(albumName, artist, title, link, isPlayable))
                                    } else {
                                        playlistLength -= 1
                                    }
                                }
                            }
                        } catch (e: JSONException) {
                            playlistLength -= 1
                        }

                    }
                }

                val itemList = getItems()
                //check token
                if (itemList.has("error") && itemList.getJSONObject("error").getInt("status") == 401) {
                    //token expired, update it
                    updateToken()
                } else {
                    parseItems(itemList.getJSONArray("items"))
                    listOffset += 100
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            val playlistData = getPlaylistData()
            //check token
            if (playlistData.has("error") && playlistData.getJSONObject("error").getInt("status") == 401) {
                //token expired, update
                updateToken()
            } else {
                parsePlaylistData(playlistData)
                gettingData = false
            }
        }

        return trackItems
    }

    fun getAlbumTracks(albumLink: String): ArrayList<Track> {
        val trackItems = ArrayList<Track>()

        fun getAlbumData(): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/albums/")
            urlBuilder.append(
                if (albumLink.contains("spotify:") && albumLink.contains("album:")) {
                    albumLink.substringAfterLast(":")
                } else {
                    albumLink.substringAfter("album/").substringBefore("?si=")
                }
            )
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            return if (rawResponse.second.isNotEmpty())
                JSONObject(rawResponse.second)
            else
                JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
        }

        fun parseAlbumData(albumData: JSONObject) {
            val trackItemsLength = albumData.getJSONObject("tracks").getInt("total")
            val albumName = if (albumData.getString("album_type") == "single") {
                "${albumData.getString("name")} (Single)"
            } else {
                albumData.getString("name")
            }

            //Now get all tracks
            //spotify only shows 20 items per search, so with each 20 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < trackItemsLength) {

                fun getAlbumTracks(): JSONObject {
                    val albumUrlBuilder = StringBuilder()
                    albumUrlBuilder.append("https://api.spotify.com/v1/albums/")
                    albumUrlBuilder.append(
                        if (albumLink.contains("spotify:") && albumLink.contains("album:")) {
                            albumLink.substringAfterLast(":")
                        } else {
                            albumLink.substringAfter("album/").substringBefore("?si=")
                        }
                    )
                    albumUrlBuilder.append("/tracks?limit=20")
                    albumUrlBuilder.append("&offset=$listOffset")
                    if (market.isNotEmpty()) {
                        albumUrlBuilder.append("&market=$market")
                    }
                    val albumUrl = URL(albumUrlBuilder.toString())
                    val albumRequestMethod = "GET"
                    val albumProperties = arrayOf(
                        "Authorization: Bearer $accessToken",
                        "Content-Type: application/x-www-form-urlencoded",
                        "User-Agent: Mozilla/5.0"
                    )
                    val albumRawResponse = sendHttpRequest(albumUrl, albumRequestMethod, albumProperties)
                    return if (albumRawResponse.second.isNotEmpty())
                        JSONObject(albumRawResponse.second)
                    else
                        JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        val artist = item.getJSONArray("artists")
                            .forEach { it as JSONObject; StringBuilder().append("${it.getString("name")},") }.toString()
                            .substringBeforeLast(",")
                        val title = item.getString("name")
                        val link = item.getJSONObject("external_urls").getString("spotify")
                        val isPlayable = if (market.isNotEmpty()) {
                            item.getBoolean("is_playable")
                        } else {
                            true
                        }
                        trackItems.add(Track(albumName, artist, title, link, isPlayable))
                    }
                }

                val albumTracks = getAlbumTracks()
                //check token
                if (albumTracks.has("error") && albumTracks.getJSONObject("error").getInt("status") == 401) {
                    //token expired, update it
                    updateToken()
                } else {
                    parseItems(albumTracks.getJSONArray("items"))
                    listOffset += 20
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            val albumData = getAlbumData()
            //check token
            if (albumData.has("error") && albumData.getJSONObject("error").getInt("status") == 401) {
                //token expired, update it
                updateToken()
            } else {
                parseAlbumData(albumData)
                gettingData = false
            }
        }
        return trackItems
    }

    fun getTrack(trackLink: String): Track {

        fun getTrackData(): JSONObject {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/tracks/")
            urlBuilder.append(
                if (trackLink.contains("spotify:") && trackLink.contains(":track:")) {
                    trackLink.substringAfterLast(":")
                } else {
                    trackLink.substringAfter("track/").substringBefore("?si=")
                }
            )
            if (market.isNotEmpty()) {
                urlBuilder.append("?market=$market")
            }
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val rawResponse = sendHttpRequest(url, requestMethod, properties)
            return if (rawResponse.second.isNotEmpty())
                JSONObject(rawResponse.second)
            else
                JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
        }

        fun parseData(trackData: JSONObject): Track {
            val artistsBuilder = StringBuilder()
            trackData.getJSONArray("artists").forEach {
                it as JSONObject
                artistsBuilder.append("${it.getString("name")}, ")
            }
            val artist = artistsBuilder.toString().substringBeforeLast(",")
            val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                "${trackData.getJSONObject("album").getString("name")} (Single)"
            } else {
                trackData.getJSONObject("album").getString("name")
            }
            val title = trackData.getString("name")
            val isPlayable = if (market.isNotEmpty()) {
                trackData.getBoolean("is_playable")
            } else {
                true
            }
            return Track(albumName, artist, title, trackLink, isPlayable)
        }

        var track: Track = Track.Empty
        var gettingData = true
        while (gettingData) {
            val trackData = getTrackData()
            //check token
            if (trackData.has("error") && trackData.getJSONObject("error").getInt("status") == 401) {
                //token expired, update it
                updateToken()
            } else {
                track = parseData(trackData)
                gettingData = false
            }
        }
        return track
    }
}
