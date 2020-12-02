package ts3_musicbot.services

import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SoundCloud {
    var clientId = "iJfdb4eX2yiDdAlPzFtDnd26wDofArGy"
    private val commandRunner = CommandRunner()
    private val api2URL = URL("https://api-v2.soundcloud.com")
    private val apiURL = URL("https://api.soundcloud.com")

    /**
     * Updates the clientId
     * @return returns the new id
     */
    fun updateClientId(): String {
        println("Updating SoundCloud ClientId")
        val lines = commandRunner.runCommand(
            "curl https://soundcloud.com 2> /dev/null | grep -E \"<script crossorigin src=\\\"https:\\/\\/\\S*\\.js\\\"></script>\"",
            printOutput = false
        ).first.outputText.lines()
        for (line in lines) {
            val url = line.substringAfter("\"").substringBefore("\"")
            val data = commandRunner.runCommand(
                "curl $url 2> /dev/null | grep \"client_id=\"",
                printOutput = false,
                printErrors = false
            ).first.outputText
            if (data.isNotEmpty()) {
                val id = data.substringAfter("client_id=").substringBefore("&")
                clientId = id
                break
            }
        }
        return clientId
    }

    suspend fun searchSoundCloud(searchType: SearchType, searchQuery: SearchQuery): SearchResults {
        val searchResults = ArrayList<SearchResult>()
        fun searchData(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/search/")
            urlBuilder.append("${searchType}s?")
            urlBuilder.append("q=${URLEncoder.encode(searchQuery.query, Charsets.UTF_8.toString())}")
            urlBuilder.append("&limit=10")
            urlBuilder.append("&client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        fun parseResults(searchData: JSONObject) {
            when (searchType.type) {
                "track" -> {
                    val tracks = searchData.getJSONArray("collection")
                    for (trackData in tracks) {
                        trackData as JSONObject

                        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                        val releaseDate = ReleaseDate(LocalDate.parse(trackData.getString("created_at"), formatter))
                        val track = Track(
                            Album(releaseDate = releaseDate),
                            Artists(
                                listOf(
                                    Artist(
                                        Name(trackData.getJSONObject("user").getString("username")),
                                        Link(trackData.getJSONObject("user").getString("permalink_url"))
                                    )
                                )
                            ),
                            Name(trackData.getString("title")),
                            Link(trackData.getString("permalink_url")),
                            Playability(trackData.getBoolean("streamable"))
                        )
                        searchResults.add(
                            SearchResult(
                                "Upload Date:   \t${track.album.releaseDate.date}\n" +
                                        "Uploader: \t\t\t${track.artists.toShortString()}\n" +
                                        "Track Title:   \t\t${track.title}\n" +
                                        "Track Link:    \t\t${track.link}\n"
                            )
                        )
                    }
                }
                "playlist" -> {
                    val playlists = searchData.getJSONArray("collection")
                    for (playlistData in playlists) {
                        playlistData as JSONObject

                        val playlist = Playlist(
                            Name(playlistData.getString("title")),
                            User(
                                Name(playlistData.getJSONObject("user").getString("username")),
                                Name(playlistData.getJSONObject("user").getString("permalink")),
                                Followers(playlistData.getJSONObject("user").getInt("followers_count")),
                                Link(playlistData.getJSONObject("user").getString("permalink_url"))
                            ),
                            Description(if (!playlistData.isNull("description")) playlistData.getString("description") else ""),
                            Followers(playlistData.getInt("likes_count")),
                            Publicity(playlistData.getString("sharing") == "public"),
                            Collaboration(false),
                            Link(playlistData.getString("permalink_url"))
                        )
                        val trackAmount = playlistData.getInt("track_count")
                        searchResults.add(
                            SearchResult(
                                "Playlist:   \t${playlist.name}\n" +
                                        "Owner:    \t${playlist.owner.name}\n" +
                                        "Tracks:    \t$trackAmount\n" +
                                        "Link:     \t\t${playlist.link}\n"
                            )
                        )
                    }
                }
            }
        }

        println("Searching for \"$searchQuery\"")
        val searchJob = Job()
        withContext(IO + searchJob) {
            while (true) {
                val searchData = searchData()
                when (searchData.first.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val resultData = JSONObject(searchData.second.data)
                            withContext(Default + searchJob) {
                                parseResults(resultData)
                            }
                            searchJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            //JSON broken, try getting the data again
                            println("Failed JSON:\n${searchData.second.data}\n")
                            println("Failed to get data from JSON, trying again...")
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> updateClientId()
                    else -> {
                        println("Error: code ${searchData.first.code}")
                    }
                }
            }
        }

        return SearchResults(searchResults)
    }

    /**
     * Get tracks from a SoundCloud playlist
     * @param link SoundCloud playlist link
     * @return returns an ArrayList containing the playlist's tracks as Track objects
     */
    fun getPlaylistTracks(link: Link): ArrayList<Track> {
        fun getTracks(): Pair<ResponseCode, ResponseData> {
            val id = resolveId(link)
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/playlists/")
            urlBuilder.append(id)
            urlBuilder.append("?client_id=$clientId")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        val trackList = ArrayList<Track>()
        var gettingData = true
        while (gettingData) {
            val trackData = getTracks()
            when (trackData.first.code) {
                HttpURLConnection.HTTP_OK -> {
                    gettingData = false
                    try {
                        val tracks = JSONObject(trackData.second.data).getJSONArray("tracks")
                        if (tracks.length() > 50) {
                            println("This playlist has ${tracks.length()} tracks. Please wait...")
                        }
                        for (item in tracks) {
                            item as JSONObject
                            try {
                                trackList.add(getTrack(Link("$apiURL/tracks/${item.getInt("id")}")))
                            } catch (e: Exception) {
                                trackList.add(
                                    Track(
                                        Album(
                                            Name(""),
                                            Artists(emptyList()),
                                            ReleaseDate(LocalDate.now()),
                                            TrackList(emptyList()),
                                            Link("")
                                        ),
                                        Artists(emptyList()),
                                        Name(""),
                                        Link(""),
                                        Playability(false)
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    updateClientId()
                }
                else -> {
                }
            }
        }
        return trackList
    }

    /**
     * Get a Track object for a given SoundCloud song link
     * @param link link to the song
     * @return returns a Track object with uploader, title and link
     */
    fun getTrack(link: Link): Track {
        return try {
            val id = if (link.link.startsWith("$apiURL/tracks/"))
                link.link.substringAfterLast("/")
            else
                resolveId(link)

            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/resolve?url=")
            urlBuilder.append("$apiURL/tracks/$id")
            urlBuilder.append("&client_id=$clientId")
            val rawResponse = sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
            val response = JSONObject(rawResponse.second.data)
            val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
            val releaseDate = ReleaseDate(LocalDate.parse(response.getString("created_at"), formatter))
            Track(
                Album(releaseDate = releaseDate),
                Artists(
                    listOf(
                        Artist(
                            Name(response.getJSONObject("user").getString("username")),
                            Link(response.getJSONObject("user").getString("permalink_url"))
                        )
                    )
                ),
                Name(response.getString("title")),
                Link(response.getString("permalink_url")),
                Playability(response.getBoolean("streamable"))
            )
        } catch (e: Exception) {
            Track(
                Album(
                    Name(""),
                    Artists(emptyList()),
                    ReleaseDate(LocalDate.now()),
                    TrackList(emptyList()),
                    Link("")
                ),
                Artists(emptyList()),
                Name(""),
                Link(""),
                Playability(false)
            )
        }
    }

    /**
     * Resolves the given SoundCloud link and returns it's id
     * @param link link to resolve
     * @return returns the corresponding id for the given link as a String
     */
    fun resolveId(link: Link): String {
        fun getId(): Pair<ResponseCode, ResponseData> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("$api2URL/resolve?")
            urlBuilder.append("client_id=$clientId")
            urlBuilder.append("&url=${link.link}")
            return sendHttpRequest(URL(urlBuilder.toString()), RequestMethod("GET"))
        }

        lateinit var id: String
        var gettingData = true
        while (gettingData) {
            val idData = getId()
            when (idData.first.code) {
                HttpURLConnection.HTTP_OK -> {
                    id = JSONObject(idData.second.data).getInt("id").toString()
                    gettingData = false
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    updateClientId()
                }
                else -> {
                    println("Error: code ${idData.first.code}")
                }
            }
        }
        return id
    }
}
