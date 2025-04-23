package ts3musicbot.services

import org.json.JSONObject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ts3musicbot.util.HTTP_TOO_MANY_REQUESTS
import java.net.HttpURLConnection
import ts3musicbot.util.*;
import ts3musicbot.util.Link
import ts3musicbot.util.LinkType
import ts3musicbot.util.Track
import ts3musicbot.util.Album
import ts3musicbot.util.Artists
import ts3musicbot.util.Artist
import ts3musicbot.util.Name
import ts3musicbot.util.sendHttpRequest
import ts3musicbot.util.Playability
import ts3musicbot.services.SERVICE_PRIORITY
import java.util.Locale

class SongLink (
    val spotify: Spotify,
    val soundCloud: SoundCloud,
    val youTube: YouTube,
) : Service(ServiceType.SONGLINK) {

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
                        val items = trackData
                            .getJSONObject("props")
                            .getJSONObject("pageProps")
                            .getJSONObject("pageData")
                            .getJSONArray("sections")

                        val metadata = items[0] as JSONObject
                        val linksData = items[1] as JSONObject

                        val links = ArrayList<Link>()
                        for (item in linksData.getJSONArray("links")) {
                            item as JSONObject
                            val url = if (item.has("url")) {
                                item.getString("url")
                            } else {
                                ""
                            }
                            links.add(Link(url))
                        }

                        var isPlayable = false
                        if (links.any { link ->
                                SERVICE_PRIORITY.any { it == link.serviceType() }
                            }) {
                            for (serviceType in SERVICE_PRIORITY) {
                                val service = when (serviceType) {
                                    ServiceType.SOUNDCLOUD -> soundCloud
                                    ServiceType.SPOTIFY -> spotify
                                    ServiceType.YOUTUBE -> youTube
                                    ServiceType.BANDCAMP -> Bandcamp()
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

                        track = Track(
                            Album(),
                            Artists(
                                listOf(
                                    Artist(
                                        Name(metadata.getString("artistName"))
                                    )
                                )
                            ),
                            Name(metadata.getString("title")),
                            Link(trackLink.link, trackLink.getId(this@SongLink), links),
                            Playability(isPlayable),
                            serviceType = ServiceType.SONGLINK,
                            description = Description(metadata.getString("description"))
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

    override suspend fun resolveType(link: Link): LinkType =
        when {
            "$link".contains("https?://song\\.link/.+".toRegex()) -> LinkType.TRACK
            "$link".contains("https?://album\\.link/.+".toRegex()) -> LinkType.ALBUM
            else -> LinkType.OTHER
        }
}
