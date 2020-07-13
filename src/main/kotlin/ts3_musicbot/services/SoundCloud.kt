package ts3_musicbot.services

import org.json.JSONObject
import ts3_musicbot.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SoundCloud {
    private val commandRunner = CommandRunner()
    private var clientId = "dp8jYbqmo9I3kJhH02V2UjpLbmMgwbN5"
    private val api2URL = URL("https://api-v2.soundcloud.com")
    private val apiURL = URL("https://api.soundcloud.com")

    /**
     * Updates the clientId
     */
    private fun updateId() {
        println("Updating SoundCloud ClientId")
        val lines = commandRunner.runCommand(
            "curl https://soundcloud.com 2> /dev/null | grep -E \"<script crossorigin src=\\\"https:\\/\\/\\S*\\.js\\\"></script>\"",
            printOutput = false
        ).split("\n")
        for (line in lines) {
            val url = line.substringAfter("\"").substringBefore("\"")
            val data = commandRunner.runCommand(
                "curl $url 2> /dev/null | grep \"client_id=\"",
                printOutput = false,
                printErrors = false
            )
            if (data.isNotEmpty()) {
                val id = data.substringAfter("client_id=").substringBefore("&")
                clientId = id
                break
            }
        }
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
                    updateId()
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
                Album(
                    Name(""),
                    Artists(emptyList()),
                    releaseDate,
                    TrackList(emptyList()),
                    Link("")
                ),
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
    private fun resolveId(link: Link): String {
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
                    updateId()
                }
                else -> {
                    println("Error: code ${idData.first.code}")
                }
            }
        }
        return id
    }
}
