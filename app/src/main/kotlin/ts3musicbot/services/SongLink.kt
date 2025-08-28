package ts3musicbot.services

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ts3musicbot.util.Album
import ts3musicbot.util.Artist
import ts3musicbot.util.Artists
import ts3musicbot.util.BotSettings
import ts3musicbot.util.Description
import ts3musicbot.util.Link
import ts3musicbot.util.LinkType
import ts3musicbot.util.Name
import ts3musicbot.util.Playability
import ts3musicbot.util.SearchQuery
import ts3musicbot.util.SearchResult
import ts3musicbot.util.SearchResults
import ts3musicbot.util.SearchType
import ts3musicbot.util.Track
import ts3musicbot.util.sendHttpRequest
import java.net.HttpURLConnection

class SongLink(
    val spotify: Spotify,
    val soundCloud: SoundCloud,
    val youTube: YouTube,
    val bandcamp: Bandcamp,
    val appleMusic: AppleMusic,
    val botSettings: BotSettings,
) : Service(ServiceType.SONGLINK) {
    override fun getSupportedSearchTypes() =
        listOf(
            LinkType.TRACK,
            LinkType.ALBUM,
            LinkType.SHOW,
        )

    override suspend fun search(
        searchType: SearchType,
        searchQuery: SearchQuery,
        resultLimit: Int,
        encodeQuery: Boolean,
    ): SearchResults {
        // the song.link website uses apple music's search for its search feature, so let's use that here too
        val appleMusicResults = appleMusic.search(searchType, searchQuery, resultLimit, encodeQuery)
        val songLinkResults = ArrayList<SearchResult>()
        for (result in appleMusicResults.results) {
            val songLink = fetchLink(result.link)
            songLinkResults.add(
                SearchResult(result.result, songLink),
            )
        }
        return SearchResults(songLinkResults)
    }

    /**
     * Fetches the songlink link for the given link
     * @param link The link we should get a songlink link for
     * @return Returns a songlink link for the given link
     */
    suspend fun fetchLink(link: Link): Link {
        val sb = StringBuilder()
        sb.append("https://api.song.link/v1-alpha.1/links")
        sb.append("?userCountry=" + botSettings.market)
        sb.append("&songIfSingle=true")
        sb.append("&key=")
        sb.append("&url=$link")
        val response = sendHttpRequest(Link(sb.toString()))

        var songLink = Link()
        val fetchJob = Job()
        withContext(IO + fetchJob) {
            while (true) {
                when (response.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        val jsonData = JSONObject(response.data.data)
                        songLink = Link(jsonData.getString("pageUrl"))
                        fetchJob.complete()
                        return@withContext
                    }
                    else -> {
                        println("HTTP ERROR! CODE ${response.code}")
                        fetchJob.complete()
                        return@withContext
                    }
                }
            }
        }
        return songLink
    }

    override suspend fun fetchTrack(trackLink: Link): Track {
        lateinit var track: Track
        val trackJob = Job()
        val request = sendHttpRequest(trackLink)
        withContext(IO + trackJob) {
            while (true) {
                when (request.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        val line = request.data.data
                        val pattern = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                        val jsonString = line.substringAfter(pattern).substringBefore("</script></body></html>")
                        val trackData = JSONObject(jsonString)
                        val items =
                            trackData
                                .getJSONObject("props")
                                .getJSONObject("pageProps")
                                .getJSONObject("pageData")
                                .getJSONArray("sections")

                        val metadata = items[0] as JSONObject
                        val linksData = items[1] as JSONObject

                        val links = ArrayList<Link>()
                        for (item in linksData.getJSONArray("links")) {
                            item as JSONObject
                            val url =
                                if (item.has("url")) {
                                    item.getString("url")
                                } else {
                                    ""
                                }
                            links.add(Link(url))
                        }

                        var isPlayable = false
                        if (links.any { link ->
                                SERVICE_PRIORITY.any { it == link.serviceType() }
                            }
                        ) {
                            for (serviceType in SERVICE_PRIORITY) {
                                val service =
                                    when (serviceType) {
                                        ServiceType.SOUNDCLOUD -> soundCloud
                                        ServiceType.SPOTIFY -> spotify
                                        ServiceType.YOUTUBE -> youTube
                                        ServiceType.BANDCAMP -> bandcamp
                                        else -> Service(ServiceType.OTHER)
                                    }
                                for (link in links) {
                                    if (link.serviceType() == serviceType) {
                                        isPlayable = service.fetchTrack(link).playability.isPlayable
                                        if (isPlayable) {
                                            break
                                        }
                                    }
                                }
                                if (isPlayable) {
                                    break
                                }
                            }
                        }

                        track =
                            Track(
                                Album(),
                                Artists(
                                    listOf(
                                        Artist(
                                            Name(metadata.getString("artistName")),
                                        ),
                                    ),
                                ),
                                Name(metadata.getString("title")),
                                Link(trackLink.link, trackLink.getId(this@SongLink), links),
                                Playability(isPlayable),
                                serviceType = ServiceType.SONGLINK,
                                description = Description(metadata.getString("description")),
                            )

                        trackJob.complete()
                        return@withContext
                    }

                    else -> {
                        println("HTTP ERROR! CODE: ${request.code}")
                        track = Track()
                        return@withContext
                    }
                }
            }
        }
        return track
    }

    override suspend fun fetchAlbum(albumLink: Link): Album {
        val albumLinks = ArrayList<Link>()
        var album = Album()

        val albumJob = Job()
        withContext(IO + albumJob) {
            while (true) {
                val request = sendHttpRequest(albumLink)
                // check http return code
                when (request.code.code) {
                    HttpURLConnection.HTTP_OK -> {
                        val line = request.data.data
                        val pattern = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                        val jsonString = line.substringAfter(pattern).substringBefore("</script></body></html>")
                        val albumData = JSONObject(jsonString)
                        val items =
                            albumData
                                .getJSONObject("props")
                                .getJSONObject("pageProps")
                                .getJSONObject("pageData")
                                .getJSONArray("sections")

                        val metadata = items[0] as JSONObject
                        val linksData = items[1] as JSONObject

                        val links = ArrayList<Link>()
                        for (item in linksData.getJSONArray("links")) {
                            item as JSONObject
                            val url =
                                if (item.has("url")) {
                                    item.getString("url")
                                } else {
                                    ""
                                }
                            links.add(Link(url))
                        }

                        var realAlbum = Album()
                        if (links.any { link ->
                                SERVICE_PRIORITY.any { it == link.serviceType() }
                            }
                        ) {
                            for (serviceType in SERVICE_PRIORITY) {
                                val service =
                                    when (serviceType) {
                                        ServiceType.SOUNDCLOUD -> soundCloud
                                        ServiceType.SPOTIFY -> spotify
                                        ServiceType.YOUTUBE -> youTube
                                        ServiceType.BANDCAMP -> bandcamp
                                        else -> Service(ServiceType.OTHER)
                                    }
                                for (link in links) {
                                    if (link.serviceType() == serviceType) {
                                        albumLinks.add(link)
                                        if (realAlbum.isEmpty()) {
                                            realAlbum = service.fetchAlbum(link)
                                        }
                                        break
                                    }
                                }
                            }
                        }
                        album =
                            Album(
                                Name(metadata.getString("title")),
                                Artists(listOf(Artist(Name(metadata.getString("artistName"))))),
                                realAlbum.releaseDate,
                                realAlbum.tracks,
                                Link(albumLink.link, alternativeLinks = albumLinks),
                                realAlbum.genres,
                            )
                        albumJob.complete()
                        return@withContext
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        println("Error 404! $albumLink not found!")
                        album = Album()
                        albumJob.complete()
                        return@withContext
                    }

                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        println("Error ${request.code}! Bad request!!")
                        album = Album()
                        albumJob.complete()
                        return@withContext
                    }
                    else -> {
                        println("HTTP ERROR! CODE ${request.code}")
                        albumJob.complete()
                        return@withContext
                    }
                }
            }
        }
        return album
    }

    override suspend fun resolveType(link: Link): LinkType =
        when {
            "$link".contains("https?://song\\.link/.+".toRegex()) -> LinkType.TRACK
            "$link".contains("https?://album\\.link/.+".toRegex()) -> LinkType.ALBUM
            "$link".contains("https?://pods\\.link/.+".toRegex()) -> LinkType.SHOW
            else -> LinkType.OTHER
        }
}
