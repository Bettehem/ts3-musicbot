package src.main.chat

import src.main.services.SoundCloud
import src.main.services.Spotify
import src.main.services.Track
import src.main.services.YouTube
import src.main.util.SongQueue
import src.main.util.PlayStateListener
import src.main.util.Time
import src.main.util.runCommand
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList

class ChatReader(
    private var chatFile: File,
    private var onChatUpdateListener: ChatUpdateListener,
    private val apikey: String = "",
    private val market: String = ""
) : PlayStateListener {

    private var chatListenerThread: Thread
    private var shouldRead = false
    private var ytLink = ""
    private var ytResults = emptyList<String>()
    @Volatile
    private var songQueue = SongQueue()

    init {
        //start listening to new messages in chat
        chatListenerThread = Thread {
            Runnable {
                var currentLine = ""
                while (shouldRead) {
                    val lines = Files.readAllLines(chatFile.toPath().toAbsolutePath(), StandardCharsets.UTF_8)

                    lines.last()
                    if (currentLine != lines.last()) {
                        currentLine = lines.last()
                        chatUpdated(currentLine)
                    }

                    Thread.sleep(100)
                }

            }.run()
        }
    }

    private fun parseLink(link: String): String {
        when (chatFile.extension) {
            "html" -> {
                return if (link.contains("href=\"")) {
                    link.substringAfter(link.split(" ".toRegex())[0]).split("href=\"".toRegex())[1].split("\">".toRegex())[0].replace(
                        " -s",
                        ""
                    )
                } else {
                    link
                }
            }

            "txt" -> {
                /*return link.substringAfter("${link.split(" ".toRegex())[0]} ").substringAfter("[URL]")
                    .substringBefore("[/URL]").replace("-s", "")*/
                return link.substringAfter("[URL]").substringBefore("[/URL]")
            }
        }

        return ""
    }

    fun startReading(): Boolean {
        shouldRead = true
        return if (chatFile.isFile) {
            chatListenerThread.start()
            true
        } else {
            false
        }
    }

    fun stopReading() {
        shouldRead = false
    }

    fun parseLine(userName: String, message: String) {
        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {
            when (message.split(" ".toRegex())[0]) {

                "%help" -> {
                    val lines = ArrayList<String>()
                    lines.add("")
                    lines.add("General commands:")
                    lines.add("%help                        -Shows this help message")
                    lines.add("%queue-add <-s> <link> <pos> -Add track(s) to queue. Also add the -s if you want a list/album to be pre-shuffled before adding to the queue. You can add multiple links separated by a comma \",\". pos can be the position in which you want to place your link(s) in the queue, starting from 0")
                    lines.add("%queue-playnext <-s> <link> <pos> -Add track/playlist/album to the top of the queue. Add multiple links separated by a comma \",\". Shuffle with the -s option")
                    lines.add("%queue-play                  -Play the song queue")
                    lines.add("%queue-list <-a,--all>       -Lists current songs in queue. Add the -a or --all option to show all tracks if there are more than 15")
                    lines.add("%queue-clear                 -Clears the song queue")
                    lines.add("%queue-shuffle               -Shuffles the queue")
                    lines.add("%queue-skip                  -Skips current song")
                    lines.add("%queue-move <link> <pos>     -Moves a track to a desired position in the queue. <link> should be your song link and <pos> should be the new position of your song.")
                    lines.add("%queue-stop                  -Stops the queue")
                    lines.add("%queue-status                -Returns the status of the song queue")
                    lines.add("%queue-nowplaying            -Returns information on the currently playing track")
                    lines.add("%queue-pause                 -Pauses playback")
                    lines.add("%queue-resume                -Resumes playback")
                    lines.add("")
                    printToChat(userName, lines, apikey)
                    lines.clear()
                    lines.add("")
                    lines.add("Player specific commands:")
                    lines.add("")
                    lines.add("Spotify commands:")
                    lines.add("%sp-pause                    -Pauses the Spotify playback")
                    lines.add("%sp-resume                   -Resumes the Spotify playback")
                    lines.add("%sp-play                     -Resumes the Spotify playback")
                    lines.add("%sp-skip                     -Skips the currently playing track")
                    lines.add("%sp-next                     -Skips the currently playing track")
                    lines.add("%sp-prev                     -Plays the previous track")
                    lines.add("%sp-playsong <track>         -Plays a Spotify song. <track> should be your song link or Spotify URI")
                    lines.add("%sp-playlist <playlist>      -Plays a Spotify playlist. <playlist> should be your playlist's link or Spotify URI")
                    lines.add("%sp-playalbum <album>        -Plays a Spotify album <album> should be your album's link or Spotify URI")
                    lines.add("%sp-nowplaying               -Shows information on currently playing track")
                    lines.add("%sp-search <type> <text>     -Search on Spotify. <type> can be track, album or playlist")
                    lines.add("%sp-info <link>              -Shows info on the given link. <link> can be a Spotify track link or Spotify URI")
                    printToChat(userName, lines, apikey)

                    Thread.sleep(200)
                    lines.clear()
                    lines.add("")
                    lines.add("YouTube commands:")
                    lines.add("%yt-pause                    -Pauses the YouTube playback")
                    lines.add("%yt-resume                   -Resumes the YouTube playback")
                    lines.add("%yt-play                     -Resumes the YouTube playback")
                    lines.add("%yt-stop                     -Stops the YouTube playback")
                    lines.add("%yt-playsong <link>          -Plays a YouTube song based on link")
                    lines.add("%yt-nowplaying               -Shows information on currently playing track")
                    lines.add("%yt-search <type> <text>     -Search on YouTube. Shows 10 first results. <type> can be track, video or playlist")


                    lines.add("SoundCloud commands:")
                    lines.add("%sc-pause                    -Pauses SounCloud playback")
                    lines.add("%sc-resume                   -Resumes the SoundCloud playback")
                    lines.add("%sc-play                     -Resumes the SoundCloud playback")
                    lines.add("%sc-stop                     -Stops the SoundCloud playback")
                    lines.add("%sc-playsong <link>          -Plays a SoundCloud song based on link")
                    lines.add("%sc-nowplaying               -Shows information on currently playing track")

                    printToChat(userName, lines, apikey)
                }

                "%queue-add" -> {
                    val links = ArrayList<String>()
                    var linkArg = ""
                    //get arguments in message
                    for (arg in message.split(" ".toRegex())) {
                        //check if argument contains any of the supported link types
                        if (arg.contains("spotify:track") || arg.contains(":playlist:") ||
                            arg.contains(":album:") || arg.contains("https://open.spotify.com") ||
                            arg.contains("youtu.be") || arg.contains("youtube.com") || arg.contains("https://soundcloud.com")
                        ) {
                            //check if there's a "," in the argument, which indicates that there's more than one link
                            if (arg.contains(",")) {
                                //get all links and add to $links ArrayList
                                val args = ArrayList<String>()
                                for (link in arg.split(",".toRegex())) {
                                    args.add(parseLink(link))
                                }
                                links.addAll(args)
                            } else {
                                links.add(arg)
                            }
                            linkArg = arg
                        }
                    }
                    if (links.size > 1) {
                        if (message.substringAfter(linkArg).replace("-s", "").replace(" ", "").isNotEmpty())
                            links.reverse()
                        printToChat(userName, listOf("Adding tracks to queue..."), apikey)
                        //for each link, run the %queue-add command
                        for (link in links) {
                            //add track to queue, but only print it to the console so it won't spam the teamspeak chat
                            parseLine("__console__", "%queue-add [URL]$link[/URL]${message.substringAfter(linkArg)}")
                        }
                        printToChat(userName, listOf("Added tracks to queue."), apikey)
                    } else {
                        if (message.substringAfter("%queue-add ").contains("spotify:") || message.substringAfter("%queue-add ").contains(
                                "https://open.spotify.com"
                            )
                        ) {
                            when {
                                message.substringAfter("%queue-add ").contains("spotify:track:") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("spotify:track:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Adding track to queue..."), apikey)
                                    songQueue.addToQueue(
                                        "https://open.spotify.com/track/${uri.substringAfter("spotify:track:")}",
                                        position
                                    )
                                    printToChat(userName, listOf("Added track to queue"), apikey)
                                }
                                message.substringAfter("%queue-add ").contains("https://open.spotify.com/") && message.substringAfter(
                                    "https://open.spotify.com/"
                                ).substringBefore("?").contains("playlist") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("https://open.spotify.com/")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getPlaylistTracks(parseLink(message).substringBefore("?"))
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()

                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-add ").contains("https://open.spotify.com/") && message.substringAfter(
                                    "https://open.spotify.com/"
                                ).substringBefore("?").contains("album") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("https://open.spotify.com/")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Getting album tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getAlbumTracks(parseLink(message).substringBefore("?"))
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added album tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-add ").contains("spotify:") && message.substringAfter("spotify:").contains(
                                    ":playlist:"
                                ) -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains(":playlist:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList = Spotify(market).getPlaylistTracks(
                                        "https://open.spotify.com/playlist/${uri.substringAfter(":playlist:")}"
                                    )
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-add ").contains("spotify:") && message.substringAfter("spotify:").contains(
                                    ":album:"
                                ) -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains(":album:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Getting album tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getAlbumTracks(
                                            "https://open.spotify.com/album/${uri.substringAfter(
                                                ":album:"
                                            )}"
                                        )
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added album tracks to queue."), apikey)
                                }
                                else -> {
                                    if (Spotify(market).getTrack(parseLink(message).substringBefore("?")).isPlayable) {
                                        printToChat(userName, listOf("Adding track to queue..."), apikey)
                                        val position = if (message.substringAfter(parseLink(message)).replace(
                                                " -s",
                                                ""
                                            ).contains(" ")
                                        ) {
                                            message.substringAfter(parseLink(message)).replace("-s", "")
                                                .substringAfterLast(" ").toInt()
                                        } else {
                                            if (songQueue.getQueue().isNotEmpty()) {
                                                -1
                                            } else {
                                                0
                                            }
                                        }
                                        songQueue.addToQueue(parseLink(message).substringBefore("?"), position)
                                        printToChat(userName, listOf("Added track to queue."), apikey)
                                    } else {
                                        printToChat(userName, listOf("This track isn't playable!"), apikey)
                                    }
                                }
                            }
                        } else if (message.substringAfter("%queue-add ").contains("youtu.be") || message.substringAfter(
                                "%queue-add "
                            ).contains(
                                "youtube.com"
                            )
                        ) {
                            when {
                                message.substringAfter("%queue-add ").contains("?list=") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("youtu.be") || arg.contains("youtube.com")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList = YouTube().getPlaylistTracks(parseLink(message).substringBefore("&"))
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()

                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                else -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("youtu.be") || arg.contains("youtube.com")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        if (songQueue.getQueue().isNotEmpty()) {
                                            -1
                                        } else {
                                            0
                                        }
                                    }
                                    printToChat(userName, listOf("Adding track to queue..."), apikey)
                                    songQueue.addToQueue(parseLink(message), position)
                                    printToChat(userName, listOf("Added track to queue."), apikey)
                                }
                            }
                        } else if (message.substringAfter("%queue-add ").contains("soundcloud.com")) {
                            var uri = ""
                            for (arg in message.split(" ".toRegex())){
                                if (arg.contains("soundcloud.com")) {
                                    uri = arg
                                    break
                                }
                            }
                            val position = if (message.substringAfter(uri).contains(" ")){
                                message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                            }else {
                                if (songQueue.getQueue().isNotEmpty()){
                                    -1
                                }else{
                                    0
                                }
                            }
                            //check if a playlist
                            val trackList = ArrayList<Track>()
                            if (parseLink(message).substringBefore("?").contains("/sets/")){
                                printToChat(userName, listOf("Please wait, getting track list..."), apikey)
                                trackList.addAll(SoundCloud().getPlaylistTracks(parseLink(message)))
                            }else{
                                trackList.add(SoundCloud().getTrack(parseLink(message)))
                            }
                            printToChat(userName, listOf("Adding track" + if (trackList.size > 1) {"s"}else{""} + " to queue..."), apikey)
                            for (track in trackList) {
                                if (track.isPlayable) {
                                    songQueue.addToQueue(track.link, position)
                                }
                            }
                            printToChat(userName, listOf("Added track" + if (trackList.size > 1) {"s"}else{""} + " to queue."), apikey)
                        } else if (message.substringAfter("%queue-add").isEmpty()) {
                            printToChat(
                                userName,
                                listOf("Please provide a track, album or playlist to add to the queue!"),
                                apikey
                            )
                        } else {
                            printToChat(userName, listOf("Adding track to queue..."), apikey)
                            songQueue.addToQueue(parseLink(message.substringBefore("?")).substringAfterLast(" "))
                            printToChat(userName, listOf("Added track to queue."), apikey)
                        }
                    }

                }
                "%queue-playnext" -> {
                    val links = ArrayList<String>()
                    var linkArg = ""
                    //get arguments in message
                    for (arg in message.split(" ".toRegex())) {
                        //check if argument contains any of the supported link types
                        if (arg.contains("spotify:track") || arg.contains(":playlist:") ||
                            arg.contains(":album:") || arg.contains("https://open.spotify.com") ||
                            arg.contains("youtu.be") || arg.contains("youtube.com")
                        ) {
                            //check if there's a "," in the argument, which indicates that there's more than one link
                            if (arg.contains(",")) {
                                //get all links and add to $links Arraylist
                                val args = ArrayList<String>()
                                for (link in arg.split(",".toRegex())) {
                                    args.add(parseLink(link))
                                }
                                links.addAll(args)
                            } else {
                                links.add(arg)
                            }
                            linkArg = arg
                        }
                    }
                    if (links.size > 1) {
                        if (message.substringAfter(linkArg).replace("-s", "").replace(" ", "").isNotEmpty())
                            links.reverse()
                        //for each link, run the %queue-playnext command 
                        for (link in links) {
                            parseLine(userName, "%queue-playnext [URL]$link[/URL]${message.substringAfter(linkArg)}")
                        }
                    } else {
                        if (message.substringAfter("%queue-playnext ").contains("spotify:") ||
                            message.substringAfter("%queue-playnext ").contains("https://open.spotify.com")
                        ) {
                            when {
                                message.substringAfter("%queue-playnext ").contains("spotify:track:") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("spotify:track:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    printToChat(userName, listOf("Adding track to queue..."), apikey)
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }
                                    songQueue.addToQueue(
                                        "https://open.spotify.com/track/${uri.substringAfter(":track:")}", position
                                    )
                                    printToChat(userName, listOf("Added track to queue"), apikey)
                                }
                                message.substringAfter("%queue-playnext ").contains("https://open.spotify.com/") && message.substringAfter(
                                    "https://open.spotify.com/"
                                ).substringBefore("?").contains("playlist") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("https://open.spotify.com/")) {
                                            uri = arg.substringBefore("?")
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }

                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getPlaylistTracks(parseLink(message).substringBefore("?"))
                                    if (message.substringAfter("%queue-playnext").contains(" -s"))
                                        trackList.shuffle()

                                    trackList.reverse()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-playnext ").contains("https://open.spotify.com/") && message.substringAfter(
                                    "https://open.spotify.com/"
                                ).substringBefore("?").contains("album") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("https://open.spotify.com/")) {
                                            uri = arg.substringBefore("?")
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }
                                    printToChat(userName, listOf("Getting album tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getAlbumTracks(parseLink(message).substringBefore("?"))
                                    if (message.substringAfter("%queue-playnext").contains(" -s"))
                                        trackList.shuffle()

                                    trackList.reverse()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added album tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-playnext ").contains("spotify:") && message.substringAfter(
                                    "spotify:"
                                ).contains(
                                    ":playlist:"
                                ) -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("spotify:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }

                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList = Spotify(market).getPlaylistTracks(
                                        "https://open.spotify.com/playlist/${uri.substringAfter(":playlist:")}"
                                    )
                                    if (message.substringAfter("%queue-playnext").contains(" -s"))
                                        trackList.shuffle()

                                    trackList.reverse()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                message.substringAfter("%queue-playnext ").contains("spotify:") && message.substringAfter(
                                    "spotify:"
                                ).contains(
                                    ":album:"
                                ) -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("spotify:")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }

                                    printToChat(userName, listOf("Getting album tracks..."), apikey)
                                    val trackList =
                                        Spotify(market).getAlbumTracks(
                                            "https://open.spotify.com/album/${uri.substringAfter(":album:")}"
                                        )
                                    if (message.substringAfter("%queue-playnext").contains(" -s"))
                                        trackList.shuffle()

                                    trackList.reverse()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added album tracks to queue."), apikey)
                                }
                                else -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("https://open.spotify.com/")) {
                                            uri = arg.substringBefore("?")
                                            break
                                        }
                                    }
                                    val position =
                                        if (message.substringAfter("https://open.spotify.com").contains(" ")) {
                                            message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                        } else {
                                            0
                                        }
                                    if (Spotify(market).getTrack(parseLink(message).substringBefore("?")).isPlayable) {
                                        printToChat(userName, listOf("Adding track to queue..."), apikey)
                                        songQueue.addToQueue(parseLink(message).substringAfterLast(" "), position)
                                        printToChat(userName, listOf("Added track to queue."), apikey)
                                    } else {
                                        printToChat(userName, listOf("This track isn't playable!"), apikey)
                                    }
                                }
                            }
                        } else if (message.substringAfter("%queue-playnext ").contains("youtu.be") || message.substringAfter(
                                "%queue-playnext "
                            ).contains(
                                "youtube.com"
                            )
                        ) {
                            when {
                                message.substringAfter("%queue-playnext ").contains("?list=") -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("youtu.be") || arg.contains("youtube.com")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }

                                    printToChat(userName, listOf("Getting playlist tracks..."), apikey)
                                    val trackList = YouTube().getPlaylistTracks(parseLink(message).substringBefore("&"))
                                    if (message.substringAfter("%queue-add").contains(" -s"))
                                        trackList.shuffle()
                                    for (track in trackList) {
                                        if (track.isPlayable)
                                            songQueue.addToQueue(track.link, position)
                                    }
                                    printToChat(userName, listOf("Added playlist tracks to queue."), apikey)
                                }
                                else -> {
                                    var uri = ""
                                    for (arg in message.split(" ".toRegex())) {
                                        if (arg.contains("youtu.be") || arg.contains("youtube.com")) {
                                            uri = arg
                                            break
                                        }
                                    }
                                    val position = if (message.substringAfter(uri).contains(" ")) {
                                        message.substringAfter(uri).replace("-s", "").replace(" ", "").toInt()
                                    } else {
                                        0
                                    }
                                    printToChat(userName, listOf("Adding track to queue..."), apikey)
                                    songQueue.addToQueue(parseLink(message), position)
                                    printToChat(userName, listOf("Added track to queue."), apikey)
                                }
                            }
                        } else if (message.substringAfter("%queue-playnext").isEmpty()) {
                            printToChat(
                                userName,
                                listOf("Please provide a track, album or playlist to add to the queue!"),
                                apikey
                            )
                        } else {
                            printToChat(userName, listOf("Adding track to queue..."), apikey)
                            songQueue.addToQueue(parseLink(message), 0)
                            printToChat(userName, listOf("Added track to queue."), apikey)
                        }
                    }
                }
                "%queue-play" -> {
                    if (songQueue.getQueue().isNotEmpty()) {
                        if (songQueue.queueActive()) {
                            printToChat(userName, listOf("Queue is already active!"), apikey)
                        } else {
                            printToChat(userName, listOf("Playing Queue."), apikey)
                            songQueue.playQueue(this)
                        }
                    } else {
                        printToChat(userName, listOf("Queue is empty!"), apikey)
                    }
                }
                "%queue-list" -> {
                    if (songQueue.queueActive()) {
                        printToChat(userName, listOf("Currently playing:\n${songQueue.nowPlaying().link}"), apikey)
                    }
                    printToChat(userName, listOf("Song Queue:"), apikey)
                    val queueList = ArrayList<String>()
                    when {
                        songQueue.getQueue().isEmpty() -> printToChat(userName, listOf("Queue is empty!"), apikey)
                        else -> {
                            val queue = if (songQueue.getQueue().size <= 15) {
                                songQueue.getQueue().toMutableList()
                            } else {
                                if (message.substringAfter("%queue-list").contains(" -a") || message.substringAfter("%queue-list").contains(
                                        " --all"
                                    )
                                ) {
                                    songQueue.getQueue().toMutableList()
                                } else {
                                    songQueue.getQueue().subList(0, 14).toMutableList()
                                }
                            }
                            while (queue.isNotEmpty()) {
                                if (queueList.size < 15) {
                                    queueList.add(queue[0])
                                    queue.removeAt(0)
                                } else {
                                    queueList.add(0, "")
                                    printToChat(userName, queueList, apikey)
                                    queueList.clear()
                                }
                            }
                            queueList.add(0, "")
                            printToChat(userName, queueList, apikey)
                        }
                    }
                    printToChat(userName, listOf("Queue Length: ${songQueue.getQueue().size} tracks."), apikey)

                }
                "%queue-clear" -> {
                    songQueue.clearQueue()
                    printToChat(userName, listOf("Cleared the queue."), apikey)
                }
                "%queue-shuffle" -> {
                    songQueue.shuffleQueue()
                    printToChat(userName, listOf("Shuffled the queue."), apikey)
                }
                "%queue-skip" -> songQueue.skipSong()
                "%queue-move" -> {
                    val link = parseLink(message)
                    if (message.substringAfter(" ").contains(" ") && message.substringAfter(" ").split(" ".toRegex()).size == 2) {
                        val position = message.substringAfter(" ").split(" ".toRegex())[1].toInt()
                        when {
                            position > songQueue.getQueue().size - 1 -> printToChat(
                                userName,
                                listOf("lol u think arrays start at 1?"),
                                apikey
                            )

                            position < 0 -> printToChat(
                                userName,
                                listOf("What were you thinking?", "You can't do that."),
                                apikey
                            )

                            else -> songQueue.moveTrack(link, position)
                        }
                    }
                }
                "%queue-stop" -> {
                    songQueue.stopQueue()
                    printToChat(userName, listOf("Stopped the queue."), apikey)
                }
                "%queue-status" -> {
                    if (songQueue.queueActive()) {
                        printToChat(userName, listOf("Queue Status: Active"), apikey)
                    } else {
                        printToChat(userName, listOf("Queue Status: Not active"), apikey)
                    }
                }
                "%queue-nowplaying" -> {
                    if (songQueue.nowPlaying().isNotEmpty()) {
                        val currentTrack = songQueue.nowPlaying()
                        val messageLines = ArrayList<String>()
                        messageLines.add("Now playing:")
                        if (currentTrack.album.isNotEmpty())
                            messageLines.add("Album:\t${currentTrack.album}")
                        if (currentTrack.artist.isNotEmpty()){
                            if (currentTrack.link.contains("soundcloud.com"))
                                messageLines.add("Uploader:   \t${currentTrack.artist}")
                            else
                                messageLines.add("Artist:   \t${currentTrack.artist}")
                        }
                        messageLines.add("Title:    \t${currentTrack.title}")
                        messageLines.add("Link:  \t${currentTrack.link}")
                        printToChat(userName, messageLines, apikey)
                    } else {
                        printToChat(userName, listOf("No song playing!"), apikey)
                    }

                }
                "%queue-pause" -> {
                   songQueue.pause()
                }
                "%queue-resume" -> {
                    songQueue.resume()
                }


                "%sp-pause" -> runCommand("playerctl -p spotify pause && sleep 1")
                "%sp-resume", "%sp-play" -> runCommand("playerctl -p spotify play && sleep 1")
                "%sp-skip", "%sp-next" -> runCommand("playerctl -p spotify next && sleep 1")
                "%sp-prev" -> runCommand("playerctl -p spotify previous && sleep 0.1 & playerctl -p spotify previous")

                //Play Spotify song based on link or URI
                "%sp-playsong" -> {
                    if (message.substringAfter("%sp-playsong").isNotEmpty()) {
                        if (parseLink(message).startsWith("https://open.spotify.com/track")) {
                            runCommand(
                                "playerctl -p spotify open spotify:track:${parseLink(message).split("track/".toRegex())[1].split(
                                    "\\?si=".toRegex()
                                )[0]}"
                            )
                        } else {
                            runCommand(
                                "playerctl -p spotify open spotify:track:${message.split(" ".toRegex())[1].split(
                                    "track:".toRegex()
                                )[1]}"
                            )
                        }
                    } else {
                        printToChat(userName, listOf("Error! Please provide a song to play!"), apikey)
                    }
                }

                //Play Spotify playlist based on link or URI
                "%sp-playlist" -> {
                    if (message.split(" ".toRegex())[1].startsWith("spotify:user:") && message.split(" ".toRegex())[1].contains(
                            ":playlist:"
                        )
                    ) {
                        runCommand(
                            "playerctl -p spotify open spotify:user:${message.split(" ".toRegex())[1].split(":".toRegex())[2]}:playlist:${message.split(
                                ":".toRegex()
                            ).last()}"
                        )
                    } else if (message.split(" ".toRegex())[1].startsWith("spotify:playlist")) {
                        runCommand("playerctl -p spotify open spotify:playlist:${message.substringAfter("playlist:")}")
                    } else if (parseLink(message).startsWith("https://open.spotify.com/")) {
                        runCommand(
                            "playerctl -p spotify open spotify:playlist:${parseLink(message).substringAfter("playlist/").substringBefore(
                                "?"
                            )}"
                        )
                    }
                }

                "%sp-playalbum" -> {
                    if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                        runCommand("playerctl -p spotify open spotify:album:${message.substringAfter("album:")}")
                    } else if (parseLink(message).startsWith("https://open.spotify.com/")) {
                        runCommand(
                            "playerctl -p spotify open spotify:album:${parseLink(message).substringAfter("album/").substringBefore(
                                "?"
                            )}"
                        )
                    }
                }


                "%sp-nowplaying" -> {
                    val lines = ArrayList<String>()
                    lines.add("Now playing on Spotify:")
                    val nowPlaying = runCommand("playerctl -p spotify metadata").split("\n".toRegex())
                    for (line in nowPlaying) {
                        when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]) {
                            "album" -> lines.add("Album:\t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                            "artist" -> lines.add(
                                "Artist:   \t${line.substringAfter(
                                    line.substringAfter("xesam:").split(
                                        "\\s+".toRegex()
                                    )[0]
                                )}"
                            )
                            "title" -> lines.add(
                                "Title:    \t${line.substringAfter(
                                    line.substringAfter("xesam:").split(
                                        "\\s+".toRegex()
                                    )[0]
                                )}"
                            )
                            "url" -> lines.add("Link:  \t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                        }
                    }
                    printToChat(userName, lines, apikey)

                }

                "%sp-search" -> {

                    if (message.split(" ".toRegex())[1].toLowerCase() == "track" || message.split(" ".toRegex())[1].toLowerCase() == "playlist" || message.split(
                            " ".toRegex()
                        )[1].toLowerCase() == "album"
                    ) {
                        val lines = ArrayList<String>()
                        lines.add("Searching, please wait...")
                        printToChat(userName, lines, apikey)
                        lines.clear()
                        val searchedLines = ArrayList<String>()
                        searchedLines.addAll(
                            Spotify(market).searchSpotify(
                                message.split(" ".toRegex())[1].toLowerCase(),
                                message.substringAfter(message.split(" ".toRegex())[1]).replace("&owner=\\w+", "")
                            ).split("\n".toRegex())
                        )
                        for (line in searchedLines.indices) {
                            if (message.substringAfter(message.split(" ".toRegex())[1]).contains("&owner=") && searchedLines[line].replace(
                                    " ",
                                    ""
                                ).toLowerCase().contains(
                                    "Owner:${message.substringAfter(message.split(" ".toRegex())[1]).substringAfter(
                                        "&owner="
                                    ).substringBefore("&")}".toLowerCase()
                                )
                            ) {
                                lines.addAll(
                                    listOf(
                                        searchedLines[line - 1],
                                        searchedLines[line],
                                        searchedLines[line + 1],
                                        searchedLines[line + 2]
                                    )
                                )
                            } else if (!message.substringAfter(message.split(" ".toRegex())[1]).contains("&owner=")) {
                                lines.add(searchedLines[line])
                            }
                        }

                        if (lines.size == 1 && lines[0] == "") {
                            lines.add("No search results with \"${message.substringAfter("${message.split(" ".toRegex())[1]} ")}\"!")
                        }

                        when (message.split(" ".toRegex())[1]) {
                            "track" -> {
                                if (lines.size <= 15) {
                                    lines.add(0, "Search results:")
                                    printToChat(userName, lines, apikey)
                                } else {
                                    val resultLines = ArrayList<String>()
                                    resultLines.add("Search results:")
                                    printToChat(userName, resultLines, apikey)
                                    resultLines.clear()
                                    for (line in lines) {
                                        if (resultLines.size < 14) {
                                            resultLines.add(line)
                                        } else {
                                            resultLines.add(0, "")
                                            printToChat(userName, resultLines, apikey)
                                            resultLines.clear()
                                        }
                                    }
                                    printToChat(userName, resultLines, apikey)
                                }
                            }

                            "album" -> {
                                if (lines.size <= 12) {
                                    lines.add(0, "Search results:")
                                    printToChat(userName, lines, apikey)
                                } else {
                                    val resultLines = ArrayList<String>()
                                    resultLines.add("Search results:")
                                    printToChat(userName, resultLines, apikey)
                                    resultLines.clear()
                                    for (line in lines) {
                                        if (resultLines.size < 11) {
                                            resultLines.add(line)
                                        } else {
                                            resultLines.add(0, "")
                                            printToChat(userName, resultLines, apikey)
                                            resultLines.clear()
                                        }
                                    }
                                    printToChat(userName, resultLines, apikey)
                                }
                            }


                            "playlist" -> {
                                if (lines.size <= 12) {
                                    lines.add(0, "Search results:")
                                    printToChat(userName, lines, apikey)
                                } else {
                                    val resultLines = ArrayList<String>()
                                    resultLines.add("Search results:")
                                    printToChat(userName, resultLines, apikey)
                                    resultLines.clear()
                                    for (line in lines) {
                                        if (resultLines.size < 11) {
                                            resultLines.add(line)
                                        } else {
                                            resultLines.add(0, "")
                                            printToChat(userName, resultLines, apikey)
                                            resultLines.clear()
                                        }

                                    }
                                    printToChat(userName, resultLines, apikey)
                                }
                            }
                        }
                    } else {
                        val lines = ArrayList<String>()
                        lines.add("Error! \"${message.split(" ".toRegex())[1]}\" is not a valid search type! See %help for more info.")
                        printToChat(userName, lines, apikey)
                    }

                }
                "%sp-info" -> {
                    if (message.substringAfter("%sp-info ").isNotEmpty() && message.substringAfter("%sp-info").length > 1) {
                        if (parseLink(message).startsWith("https://open.spotify.com/track")) {
                            val track = Spotify(market).getTrack(parseLink(message))
                            printToChat(
                                userName, listOf(
                                    "",
                                    "Album:     ${track.album}",
                                    "Artist:    ${track.artist}",
                                    "Title:     ${track.title}"
                                ), apikey
                            )
                        } else if (message.substringAfter("%sp-info ").contains("spotify:") &&
                            message.substringAfter("%sp-info ").contains(":track:")
                        ) {
                            val track = Spotify(market).getTrack(message.substringAfter("%sp-info "))
                            printToChat(
                                userName, listOf(
                                    "",
                                    "Album:     ${track.album}",
                                    "Artist:    ${track.artist}",
                                    "Title:     ${track.title}"
                                ), apikey
                            )
                        }
                    }
                }


                "%yt-pause" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-resume" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-play" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-stop" -> runCommand("echo \"stop\" | socat - /tmp/mpvsocket")


                "%yt-playsong" -> {
                    ytLink = parseLink(message)
                    //Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl -o - \"$ytLink\" | mpv --no-terminal --no-video --input-ipc-server=/tmp/mpvsocket - &"))
                    if (ytLink.isNotEmpty()) {
                        Thread {
                            Runnable {
                                run {
                                    runCommand(
                                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $ytLink",
                                        inheritIO = true,
                                        ignoreOutput = true
                                    )
                                }
                            }.run()
                        }.start()
                    }
                }


                "%yt-nowplaying" -> {
                    printToChat(
                        userName, listOf(
                            "Now playing on YouTube:",
                            YouTube().getTitle(ytLink),
                            "Link: $ytLink"
                        ), apikey
                    )
                }

                "%yt-search" -> {
                    val searchType = message.substringAfter("%yt-search ").split(" ".toRegex())[0]
                    val searchQuery = message.substringAfter("$searchType ")

                    when (searchType) {
                        "video", "track", "playlist" -> {
                            printToChat(userName, listOf("Searching, please wait..."), apikey)
                            val results = YouTube().searchYoutube(searchType.replace("track", "video"), searchQuery)
                            val lines = ArrayList<String>()
                            lines.addAll(results.split("\n".toRegex()))
                            if (lines.size <= 12) {
                                lines.add(0, "YouTube search results:")
                                printToChat(userName, lines, apikey)
                            } else {
                                val resultLines = ArrayList<String>()
                                resultLines.add("YouTube search results:")
                                printToChat(userName, resultLines, apikey)
                                resultLines.clear()
                                for (line in lines) {
                                    if (resultLines.size < 11) {
                                        resultLines.add(line)
                                    } else {
                                        resultLines.add(0, "")
                                        printToChat(userName, resultLines, apikey)
                                        resultLines.clear()
                                    }

                                }
                                printToChat(userName, resultLines, apikey)
                            }
                        }
                        else -> {
                            printToChat(
                                userName,
                                listOf(
                                    "",
                                    "$searchType is not a valid search type!",
                                    "Accepted values are: track, video, playlist",
                                    "See %help for more info"
                                ), apikey
                            )
                        }
                    }

                    /*
                    ytResults = runCommand(
                        "youtube-dl --geo-bypass -s -e \"ytsearch10:${message.replace(
                            "\"",
                            "\\\""
                        ).replace("'", "\'")}\"", printErrors = true
                    ).split("\n".toRegex())
                    for (i in ytResults.indices) {
                        lines.add("${i + 1}: ${ytResults[i]}")
                    }
                    lines.add("Use command \"%yt-sel\" followed by the search result number to get the link, for example:")
                    lines.add("%yt-sel 4")
                     */
                }


                "%sc-pause" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%sc-resume" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%sc-play" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%sc-stop" -> runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                "%sc-playsong" -> {
                    val scLink = parseLink(message)
                    //Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl -o - \"$scLink\" | mpv --no-terminal --no-video --input-ipc-server=/tmp/mpvsocket - &"))
                    if (scLink.isNotEmpty()) {
                        Thread {
                            Runnable {
                                run {
                                    runCommand(
                                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $scLink",
                                        inheritIO = true,
                                        ignoreOutput = true
                                    )
                                }
                            }.run()
                        }.start()
                    }
                }

                else -> {
                    if (userName == "__console__") {
                        when (message.split(" ".toRegex())[0]) {
                            "%say" -> {
                                val lines = ArrayList<String>()
                                lines.addAll(message.substringAfterLast("%say ").split("\n"))
                                printToChat("", lines, apikey)
                            }
                        }
                    } else {
                        val lines = ArrayList<String>()
                        lines.add("Command not found! Try %help to see available commands.")
                        printToChat(userName, lines, apikey)
                    }
                }

            }
        }
    }

    //use ClientQuery to send message (requires apikey)
    private fun printToChat(userName: String, messageLines: List<String>, apikey: String) {
        if (userName == "__console__") {
            messageLines.forEach { println(it) }
        } else {
            if (apikey.isNotEmpty()) {
                val stringBuffer = StringBuffer()
                messageLines.forEach { stringBuffer.append(it + "\n") }
                runCommand(
                    "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${stringBuffer.toString().replace(
                        " ",
                        "\\s"
                    ).replace("\n", "\\n").replace("/", "\\/").replace("|", "\\p").replace("'", "\\'").replace(
                        "\"",
                        "\\\""
                    ).replace("$", "\\$")}\"; echo quit) | nc localhost 25639",
                    printOutput = false
                )
            }
        }
    }

    private fun chatUpdated(line: String) {
        when (chatFile.extension) {
            "html" -> {
                //extract message
                val userName = line.split("client://".toRegex())[1].split("&quot;".toRegex())[1]
                val time = Time(Calendar.getInstance())
                val rawTime = line.split("TextMessage_Time\">&lt;".toRegex())[1]
                    .split("&gt;</span>".toRegex())[0]
                    .split(":".toRegex())
                time.hour = rawTime[0]
                time.minute = rawTime[1]
                time.second = rawTime[2]

                val userMessage = line.split("TextMessage_Text\">".toRegex())[1].split("</span>".toRegex())[0]
                parseLine(userName, userMessage)
                onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
            }

            "txt" -> {
                //extract message
                if (line.startsWith("<")) {
                    val userName = line.substringAfter("> ").substringBeforeLast(": ")
                    val time = Time(Calendar.getInstance())
                    val rawTime =
                        line.split(" ".toRegex())[0].substringAfter("<").substringBefore(">").split(":".toRegex())
                    time.hour = rawTime[0]
                    time.minute = rawTime[1]
                    time.second = rawTime[2]

                    val userMessage = line.substringAfter("$userName: ")
                    parseLine(userName, userMessage)
                    onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
                }
            }

            else -> {
                println("Error! file format \"${chatFile.extension}\" not supported!")
            }
        }
    }

    override fun onSongEnded(player: String, track: String) {}

    override fun onNewSongPlaying(player: String, track: String) {
        when (player) {
            "spotify" -> {
                parseLine("", "%queue-nowplaying")
                val spotifyTrack = Spotify(market).getTrack(track)
                println("Playing ${spotifyTrack.artist} - ${spotifyTrack.title}")
            }
            "mpv" -> {
                if (track.startsWith("https://youtube.com") || track.startsWith("https://youtu.be") || track.startsWith(
                        "https://www.youtube.com"
                    )
                ) {
                    ytLink = track
                    println("Playing ${YouTube().getTitle(track)}")
                } else if (track.startsWith("https://soundcloud.com")) {
                    val trackData = SoundCloud().getTrack(track)
                    println("Playing ${trackData.artist} - ${trackData.title}")
                }
                parseLine("", "%queue-nowplaying")
            }
        }
    }

    override fun onAdPlaying() {
        printToChat("__song_queue__", listOf("", "Ad playing."), apikey)
    }

}

class ChatUpdate(val userName: String, val time: Time, val message: String)

interface ChatUpdateListener {
    fun onChatUpdated(update: ChatUpdate)
}
