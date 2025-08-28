package ts3musicbot.services

import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import ts3musicbot.util.Album
import ts3musicbot.util.Artist
import ts3musicbot.util.Artists
import ts3musicbot.util.BotSettings
import ts3musicbot.util.Description
import ts3musicbot.util.Episode
import ts3musicbot.util.Link
import ts3musicbot.util.LinkType
import ts3musicbot.util.Name
import ts3musicbot.util.Playability
import ts3musicbot.util.Playable
import ts3musicbot.util.Publisher
import ts3musicbot.util.ReleaseDate
import ts3musicbot.util.RequestMethod
import ts3musicbot.util.Response
import ts3musicbot.util.SearchQuery
import ts3musicbot.util.SearchResult
import ts3musicbot.util.SearchResults
import ts3musicbot.util.SearchType
import ts3musicbot.util.Show
import ts3musicbot.util.Track
import ts3musicbot.util.sendHttpRequest
import java.net.HttpURLConnection
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppleMusic(val botSettings: BotSettings) : Service(ServiceType.APPLE_MUSIC) {
    override fun getSupportedSearchTypes() =
        listOf(
            LinkType.TRACK,
            LinkType.ALBUM,
            LinkType.SHOW,
            LinkType.EPISODE,
        )

    override suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int,
        encodeQuery: Boolean,
    ): SearchResults {
        fun searchData(): Response {
            val linkBuilder = StringBuilder()
            linkBuilder.append("https://itunes.apple.com/search?term=")
            linkBuilder.append(encode(searchQuery.query))
            linkBuilder.append("&country=${botSettings.market}")
            val type =
                when (searchType.getType()) {
                    LinkType.TRACK -> "song"
                    LinkType.ALBUM -> "album"
                    LinkType.SHOW -> "podcast"
                    LinkType.EPISODE -> "podcastEpisode"
                    else -> "song,album,podcast,podcastEpisode"
                }
            linkBuilder.append("&entity=$type")
            return sendHttpRequest(Link(linkBuilder.toString()), RequestMethod.GET)
        }

        fun parseResults(resultData: JSONObject): SearchResults {
            return SearchResults(
                resultData.getJSONArray("results").map {
                    it as JSONObject
                    val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
                    val releaseDate =
                        ReleaseDate(
                            LocalDate.parse(it.getString("releaseDate"), formatter),
                        )
                    val type = it.getString("wrapperType")
                    val kind =
                        when (type) {
                            "track", "podcastEpisode" -> it.getString("kind")
                            "collection" -> it.getString("collectionType").lowercase()
                            else -> String()
                        }
                    val link =
                        Link(
                            when (kind) {
                                "song", "podcast-episode" -> it.getString("trackViewUrl")
                                "album", "podcast" -> it.getString("collectionViewUrl")
                                else -> {
                                    println("Unknown result type: $type.\nPlease open an issue about this.")
                                    return SearchResults(emptyList())
                                }
                            },
                        )
                    val resultArtists =
                        Artists(
                            listOf(
                                Artist(
                                    Name(it.getString("artistName")),
                                    Link(it.getString("artistViewUrl")),
                                ),
                            ),
                        )
                    SearchResult(
                        when (kind) {
                            "album" -> {
                                Album(
                                    Name(it.getString("collectionName")),
                                    resultArtists,
                                    releaseDate,
                                    link = link,
                                )
                            }
                            "song" -> {
                                Track(
                                    Album(releaseDate = releaseDate),
                                    resultArtists,
                                    Name(it.getString("trackName")),
                                    link,
                                    Playability(it.getBoolean("isStreamable")),
                                )
                            }
                            "podcast-episode" -> {
                                Episode(
                                    Name(it.getString("trackName")),
                                    Name(it.getString("collectionName")),
                                    Description(
                                        it.getString("description"),
                                        it.getString("shortDescription"),
                                    ),
                                    releaseDate,
                                    link,
                                )
                            }

                            "podcast" -> {
                                Show(
                                    Name(it.getString("collectionName")),
                                    Publisher(Name(it.getString("artistName"))),
                                    releaseDate,
                                    link = link,
                                )
                            }
                            else -> {
                                Playable()
                            }
                        },
                        link,
                    )
                },
            )
        }

        var searchResults = SearchResults(emptyList())
        val searchJob = Job()
        withContext(IO + searchJob) {
            var resultsData = searchData()
            while (true) {
                when (resultsData.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            val jsonResults = JSONObject(resultsData.data.data.replace("(^\\s*\n*\\s*|\\s*\n*\\s*$)".toRegex(), ""))
                            withContext(Default + searchJob) {
                                searchResults = parseResults(jsonResults)
                            }
                            searchJob.complete()
                            return@withContext
                        } catch (e: JSONException) {
                            // JSON broken, try getting the data again
                            println("Failed JSON:\n${resultsData.data}\n")
                            e.printStackTrace()
                            println("Failed to get data from JSON, trying again...")
                            resultsData = searchData()
                        }
                    }

                    else -> {
                        println("HTTP ERROR! CODE ${resultsData.code}")
                        return@withContext
                    }
                }
            }
        }
        return if (resultLimit < searchResults.results.size) {
            SearchResults(searchResults.results.subList(0, resultLimit))
        } else {
            searchResults
        }
    }

    override suspend fun resolveType(link: Link): LinkType {
        return when {
            "$link".contains("apple\\.com/\\w+/album/\\S+[?&]i=[0-9]+".toRegex()) -> LinkType.TRACK
            "$link".contains("apple\\.com/\\w+/album/\\S+".toRegex()) -> LinkType.ALBUM
            "$link".contains("apple\\.com/\\w+/podcast/\\S+[?&]i=[0-9]+".toRegex()) -> LinkType.EPISODE
            "$link".contains("apple\\.com/\\w+/podcast/\\S+".toRegex()) -> LinkType.SHOW
            else -> LinkType.OTHER
        }
    }

    suspend fun resolveId(link: Link): String {
        return when (link.linkType(this)) {
            LinkType.TRACK, LinkType.EPISODE -> "$link".substringAfter("?").substringAfter("i=").substringBefore("&")
            LinkType.ALBUM, LinkType.SHOW -> "$link".substringBefore("?").substringAfterLast("/")
            else -> ""
        }
    }
}
