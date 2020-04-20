package src.main.chat

import src.main.services.SoundCloud
import src.main.services.Spotify
import src.main.services.Track
import src.main.services.YouTube
import src.main.util.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList

class ChatReader(
    private var chatFile: File,
    private var onChatUpdateListener: ChatUpdateListener,
    private val apikey: String = "",
    market: String = ""
) : PlayStateListener {

    private var chatListenerThread: Thread
    private var shouldRead = false
    private var ytLink = ""
    private val spotify = Spotify(market)

    @Volatile
    private var songQueue = SongQueue(spotify)

    init {
        //initialise spotify token
        spotify.updateToken()

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
                    link.substringAfter(link.split(" ".toRegex())[0])
                        .split("href=\"".toRegex())[1].split("\">".toRegex())[0].replace(
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

    fun parseLine(userName: String, message: String, commandString: String = ""): Boolean {
        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {

            if (commandString.isEmpty()) {
                //check for commands in message and add to list
                val commands = ArrayList<String>()
                commands.addAll("$message;".split("\\s*;+\\s*".toRegex()))
                //loop through command list
                for (command in commands) {
                    when {
                        //check if command contains "&&" and a command following that
                        command.contains("\\s+&{2}\\s+%[a-z]+(-?[a-z])".toRegex()) -> {
                            //run commands
                            for (cmd in command.split("\\s+&{2}\\s+".toRegex())) {
                                if (parseLine(userName, message, cmd)) {
                                    //command was successful, continue to next command
                                    continue
                                } else {
                                    //command failed, don't run next command
                                    break
                                }
                            }
                        }

                        else -> {
                            //run command
                            if (command.isNotEmpty())
                                parseLine(userName, message, command.substringBeforeLast(";"))

                        }
                    }
                }
            } else {
                //parse and execute commands
                when {

                    //%help command
                    commandString.contains("%help\\s*%?[a-z]*-?[a-z]*".toRegex()) -> {
                        //check extra arguments
                        if (commandString.substringAfter("%help").contains("%?help".toRegex())) {
                            //print normal help message
                            printToChat(userName, helpMessages["%help"]?.split("\n".toRegex()), apikey)
                            return true
                        } else {
                            //get extra arguments
                            var args = commandString.substringAfter("%help ")
                            if (!args.startsWith("%"))
                                args = "%$args"

                            //check if command exists
                            if (commandList.any { it.contains(args.toRegex()) }) {
                                //print help for command
                                printToChat(userName, helpMessages[args]?.split("\n".toRegex()), apikey)
                            } else {
                                printToChat(
                                    userName,
                                    listOf("Command doesn't exist! See %help for available commands."),
                                    apikey
                                )
                            }
                        }
                    }

                    //%queue-add and %queue-playnext command
                    commandString.contains("^%queue-(add|playnext)(\\s+-(s|(p\\s+[0-9]+)))*(\\s*(\\[URL])?((spotify:(track|album|playlist):\\S+)|(https?://\\S+))(\\[/URL])?\\s*,?\\s*)+(\\s+-(s|(p\\s+[0-9]+)))*\$".toRegex()) -> {

                        val shouldPlayNext = commandString.contains("^%queue-playnext".toRegex())
                        var shouldShuffle = false
                        var hasCustomPosition = false
                        var customPosition = if (shouldPlayNext) {
                            0
                        } else {
                            -1
                        }
                        val links = ArrayList<String>()
                        //get arguments in command
                        val args = commandString.split("\\s".toRegex())
                        for (i in args.indices) {
                            when {
                                //check if should shuffle
                                args[i].contentEquals("-s") -> shouldShuffle = true

                                //check if custom position is provided
                                args[i].contains("^-p$".toRegex()) -> {
                                    if (args.size >= i + 1) {
                                        if (args[i + 1].contains("-?[0-9]+".toRegex())) {
                                            customPosition = args[i + 1].toInt()
                                            hasCustomPosition = true
                                        }
                                    }
                                }

                                args[i].contains("((\\[URL])?(https?://(open\\.spotify\\.com|soundcloud\\.com|youtu\\.be|(m|www)\\.youtube\\.com))(\\[/URL])?.+)|(spotify:(track|album|playlist):.+)".toRegex()) -> {
                                    //add links to ArrayList
                                    if (args[i].contains(",\\s*".toRegex()))
                                        links.addAll(args[i].split(",\\s*".toRegex()))
                                    else
                                        links.add(args[i])
                                }
                            }
                        }
                        if (shouldPlayNext || hasCustomPosition)
                            links.reverse()

                        println("Getting tracks...")
                        println("Total number of links: ${links.size}")
                        printToChat(userName, listOf("Please wait, getting tracks..."), apikey)
                        //add links to queue
                        for (link in links) {
                            when {
                                //Spotify
                                link.contains("(https?://open\\.spotify\\.com/)|(spotify:(album|track|playlist):.+)".toRegex()) -> {
                                    //get link type
                                    val type = when {
                                        link.contains("(spotify:track:.+)|(open\\.spotify\\.com/track/)".toRegex()) -> "track"
                                        link.contains("(spotify:album:.+)|(open\\.spotify\\.com/album/)".toRegex()) -> "album"
                                        link.contains("(spotify:playlist:.+)|(open\\.spotify\\.com/playlist/)".toRegex()) -> "playlist"
                                        else -> ""
                                    }
                                    println("Spotify link: $link\nLink type: $type")
                                    //check type
                                    when (type) {
                                        "track" -> {
                                            songQueue.addToQueue(
                                                "https://open.spotify.com/$type/${link.substringAfter(type)
                                                    .substring(1).substringBefore("[/URL]").substringBefore("?")}",
                                                customPosition
                                            )
                                        }

                                        "album" -> {
                                            //get album's tracks
                                            val albumTracks = spotify.getAlbumTracks(
                                                "https://open.spotify.com/$type/${link.substringAfter(type).substring(1)
                                                    .substringBefore("[/URL]").substringBefore("?")}"
                                            )
                                            if (shouldShuffle)
                                                albumTracks.shuffle()

                                            val trackList = ArrayList<String>()
                                            for (track in albumTracks) {
                                                if (track.isPlayable)
                                                    trackList.add(track.link)
                                            }
                                            println("Album \"${albumTracks[0].album}\" has a total of ${trackList.size} tracks.\nAdding to queue...")
                                            //add tracks to queue
                                            songQueue.addAllToQueue(trackList, customPosition)
                                        }

                                        "playlist" -> {
                                            //get playlist's tracks
                                            val playlistTracks = spotify.getPlaylistTracks(
                                                "https://open.spotify.com/$type/${link.substringAfter(type).substring(1)
                                                    .substringAfter("[URL]").substringBefore("[/URL]")
                                                    .substringBefore("?")}"
                                            )
                                            if (shouldShuffle)
                                                playlistTracks.shuffle()

                                            val trackList = ArrayList<String>()
                                            for (track in playlistTracks) {
                                                if (track.isPlayable)
                                                    trackList.add(track.link)
                                            }
                                            println("Playlist has a total of ${trackList.size} tracks.\nAdding to queue...")
                                            //add tracks to queue
                                            songQueue.addAllToQueue(trackList, customPosition)
                                        }
                                    }
                                    println("Added to queue.")
                                }

                                //YouTube
                                link.contains("https?://(youtu\\.be|(m|www)\\.youtube\\.com)".toRegex()) -> {
                                    //get link type
                                    val type = when {
                                        link.contains("https?://((m|www)\\.youtube\\.com/watch\\?v=|youtu\\.be/\\S+)".toRegex()) -> "track"
                                        link.contains("https?://((m|www)\\.youtube\\.com/playlist\\?list=\\S+)".toRegex()) -> "playlist"
                                        else -> ""
                                    }
                                    println("YouTube link: $link\nLink type: $type")
                                    //get track/playlist id
                                    val id =
                                        link.split("((m|www)\\.youtube\\.com/(watch|playlist)\\?(v|list)=)|(youtu.be/)".toRegex())[1].substringAfter(
                                            "[URL]"
                                        ).substringBefore("[/URL]").substringBefore(
                                            "&"
                                        )
                                    //check type
                                    when (type) {
                                        "track" -> {
                                            songQueue.addToQueue("https://youtu.be/$id", customPosition)
                                        }

                                        "playlist" -> {
                                            //get playlist tracks
                                            val playlistTracks =
                                                YouTube().getPlaylistTracks("https://youtube.com/playlist?list=$id")
                                            if (shouldShuffle)
                                                playlistTracks.shuffle()
                                            val trackList = ArrayList<String>()
                                            for (track in playlistTracks) {
                                                if (track.isPlayable)
                                                    trackList.add(track.link)
                                            }
                                            println("Playlist has a total of ${trackList.size} tracks.\nAdding to queue...")
                                            songQueue.addAllToQueue(trackList, customPosition)
                                        }
                                    }
                                }

                                //SoundCloud
                                link.contains("https?://soundcloud\\.com/".toRegex()) -> {
                                    //get link type
                                    val type = when {
                                        link.contains("https?://soundcloud\\.com/".toRegex()) && !link.contains("/sets/".toRegex()) -> "track"
                                        link.contains("https?://soundcloud\\.com/\\S+/sets/\\S+".toRegex()) -> "playlist"
                                        else -> ""
                                    }
                                    println("SoundCloud link: $link\nLink type: $type")
                                    //check type
                                    when (type) {
                                        "track" -> {
                                            songQueue.addToQueue(
                                                link.substringAfter("[URL]").substringBefore("[/URL]")
                                                    .substringBefore("?"), customPosition
                                            )
                                        }

                                        "playlist" -> {
                                            //get playlist tracks
                                            val playlistTracks = SoundCloud().getPlaylistTracks(parseLink(link))
                                            if (shouldShuffle)
                                                playlistTracks.shuffle()
                                            val trackList = ArrayList<String>()
                                            for (track in playlistTracks) {
                                                if (track.isPlayable)
                                                    trackList.add(track.link)
                                            }
                                            songQueue.addAllToQueue(trackList, customPosition)
                                        }
                                    }
                                }
                            }
                        }
                        printToChat(userName, listOf("Added tracks to queue."), apikey)
                        return true
                    }

                    //%queue-play command
                    commandString.contains("^%queue-play$".toRegex()) -> {
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
                        return true
                    }
                    //%queue-list command
                    commandString.contains("^%queue-list\\s*".toRegex()) -> {
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
                                    if (commandString.substringAfter("%queue-list")
                                            .contains(" -a") || commandString.substringAfter("%queue-list").contains(
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
                        return true
                    }
                    //%queue-clear command
                    commandString.contains("^%queue-clear$".toRegex()) -> {
                        songQueue.clearQueue()
                        printToChat(userName, listOf("Cleared the queue."), apikey)
                        return true
                    }
                    //%queue-shuffle command
                    commandString.contains("^%queue-shuffle$".toRegex()) -> {
                        songQueue.shuffleQueue()
                        printToChat(userName, listOf("Shuffled the queue."), apikey)
                        return true
                    }
                    //%queue-skip command
                    commandString.contains("^%queue-skip$".toRegex()) -> {
                        songQueue.skipSong()
                        return true
                    }
                    //%queue-move command
                    commandString.contains("^%queue-move\\s+".toRegex()) -> {
                        val link = parseLink(commandString).substringBefore("?")
                        var position = 0
                        //parse arguments
                        val args = commandString.split("\\s+".toRegex())
                        for (i in args.indices){
                            when{
                                args[i].contains("(-p|--position)".toRegex()) -> {
                                    if (args.size >= i + 1 && args[i + 1].contains("\\d+".toRegex())){
                                        position = args[i + 1].toInt()
                                    }
                                }
                            }
                        }
                        when {
                            position > songQueue.getQueue().size - 1 -> {
                                printToChat(
                                    userName,
                                    listOf("lol u think arrays start at 1?"),
                                    apikey
                                )
                                return false
                            }

                            position < 0 -> {
                                printToChat(
                                    userName,
                                    listOf("What were you thinking?", "You can't do that."),
                                    apikey
                                )
                                return false
                            }

                            else -> {
                                songQueue.moveTrack(link, position)
                                return true
                            }
                        }
                    }
                    //%queue-stop command
                    commandString.contains("^%queue-stop$".toRegex()) -> {
                        songQueue.stopQueue()
                        printToChat(userName, listOf("Stopped the queue."), apikey)
                        return true
                    }
                    //%queue-status command
                    commandString.contains("^%queue-status$".toRegex()) -> {
                        if (songQueue.queueActive()) {
                            printToChat(userName, listOf("Queue Status: Active"), apikey)
                        } else {
                            printToChat(userName, listOf("Queue Status: Not active"), apikey)
                        }
                        return true
                    }
                    //%queue-nowplaying command
                    commandString.contains("^%queue-nowplaying$".toRegex()) -> {
                        if (songQueue.nowPlaying().isNotEmpty()) {
                            val currentTrack = songQueue.nowPlaying()
                            val messageLines = ArrayList<String>()
                            messageLines.add("Now playing:")
                            if (currentTrack.album.isNotEmpty())
                                messageLines.add("Album:\t${currentTrack.album}")
                            if (currentTrack.artist.isNotEmpty()) {
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
                        return true
                    }
                    //%queue-pause command
                    commandString.contains("^%queue-pause$".toRegex()) -> {
                        songQueue.pause()
                        return true
                    }
                    //%queue-resume command
                    commandString.contains("^%queue-resume$".toRegex()) -> {
                        songQueue.resume()
                        return true
                    }


                    //%sp-pause command
                    commandString.contains("^%sp-pause$".toRegex()) -> {
                        runCommand("playerctl -p spotify pause && sleep 1")
                        return true
                    }
                    //%sp-resume & %sp-play command
                    commandString.contains("^%sp-(resume|play)$".toRegex()) -> {
                        runCommand("playerctl -p spotify play && sleep 1")
                        return true
                    }
                    //%sp-skip & %sp-next command
                    commandString.contains("^%sp-(skip|next)$".toRegex()) -> {
                        runCommand("playerctl -p spotify next && sleep 1")
                        return true
                    }
                    //%sp-prev command
                    commandString.contains("^%sp-prev$".toRegex()) -> {
                        runCommand("playerctl -p spotify previous && sleep 0.1 & playerctl -p spotify previous")
                        return true
                    }
                    //%sp-playsong command
                    //Play Spotify song based on link or URI
                    commandString.contains("^%sp-playsong\\s+".toRegex()) -> {
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
                            return true
                        } else {
                            printToChat(userName, listOf("Error! Please provide a song to play!"), apikey)
                            return false
                        }
                    }
                    //%sp-playlist command
                    //Play Spotify playlist based on link or URI
                    commandString.contains("^%sp-playlist\\s+".toRegex()) -> {
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
                                "playerctl -p spotify open spotify:playlist:${parseLink(message).substringAfter("playlist/")
                                    .substringBefore(
                                        "?"
                                    )}"
                            )
                        }
                        return true
                    }
                    //%sp-playalbum command
                    commandString.contains("^%sp-playalbum\\s+".toRegex()) -> {
                        if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                            runCommand("playerctl -p spotify open spotify:album:${message.substringAfter("album:")}")
                        } else if (parseLink(message).startsWith("https://open.spotify.com/")) {
                            runCommand(
                                "playerctl -p spotify open spotify:album:${parseLink(message).substringAfter("album/")
                                    .substringBefore(
                                        "?"
                                    )}"
                            )
                        }
                        return true
                    }
                    //%sp-nowplaying command
                    commandString.contains("^%sp-nowplaying$".toRegex()) -> {
                        val lines = ArrayList<String>()
                        lines.add("Now playing on Spotify:")
                        val nowPlaying = runCommand("playerctl -p spotify metadata").split("\n".toRegex())
                        for (line in nowPlaying) {
                            when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]) {
                                "album" -> lines.add(
                                    "Album:\t${line.substringAfter(
                                        line.substringAfter("xesam:").split("\\s+".toRegex())[0]
                                    )}"
                                )
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
                                "url" -> lines.add(
                                    "Link:  \t${line.substringAfter(
                                        line.substringAfter("xesam:").split("\\s+".toRegex())[0]
                                    )}"
                                )
                            }
                        }
                        printToChat(userName, lines, apikey)
                        return true
                    }
                    //%sp-search command
                    commandString.contains("^%sp-search\\s+".toRegex()) -> {

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
                                spotify.searchSpotify(
                                    message.split(" ".toRegex())[1].toLowerCase(),
                                    message.substringAfter(message.split(" ".toRegex())[1]).replace("&owner=\\w+", "")
                                ).split("\n".toRegex())
                            )
                            for (line in searchedLines.indices) {
                                if (message.substringAfter(message.split(" ".toRegex())[1])
                                        .contains("&owner=") && searchedLines[line].replace(
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
                                } else if (!message.substringAfter(message.split(" ".toRegex())[1])
                                        .contains("&owner=")
                                ) {
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
                            return true
                        } else {
                            val lines = ArrayList<String>()
                            lines.add("Error! \"${message.split(" ".toRegex())[1]}\" is not a valid search type! See %help for more info.")
                            printToChat(userName, lines, apikey)
                            return false
                        }

                    }
                    //%sp-info command
                    commandString.contains("^%sp-info\\s+".toRegex()) -> {
                        if (message.substringAfter("%sp-info ")
                                .isNotEmpty() && message.substringAfter("%sp-info").length > 1
                        ) {
                            if (parseLink(message).startsWith("https://open.spotify.com/track")) {
                                val track = spotify.getTrack(parseLink(message))
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
                                val track = spotify.getTrack(message.substringAfter("%sp-info "))
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
                        return true
                    }


                    //%yt-pause command
                    commandString.contains("^%yt-pause$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%yt-resume command
                    commandString.contains("^%yt-resume$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%yt-play command
                    commandString.contains("^%yt-play$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%yt-stop command
                    commandString.contains("^%yt-stop$".toRegex()) -> {
                        runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%yt-playsong command
                    commandString.contains("^%yt-playsong\\s+".toRegex()) -> {
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
                        return true
                    }
                    //%yt-nowplaying command
                    commandString.contains("^%yt-nowplaying$".toRegex()) -> {
                        printToChat(
                            userName, listOf(
                                "Now playing on YouTube:",
                                YouTube().getTitle(ytLink),
                                "Link: $ytLink"
                            ), apikey
                        )
                        return true
                    }
                    //%yt-search command
                    commandString.contains("^%yt-search\\s+".toRegex()) -> {
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
                                return true
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
                                return false
                            }
                        }
                    }

                    //%sc-pause command
                    commandString.contains("^%sc-pause$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%sc-resume command
                    commandString.contains("^%sc-resume$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%sc-play command
                    commandString.contains("^%sc-play$".toRegex()) -> {
                        runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%sc-stop command
                    commandString.contains("^%sc-stop$".toRegex()) -> {
                        runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                        return true
                    }
                    //%sc-playsong command
                    commandString.contains("^%sc-playsong\\s+".toRegex()) -> {
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
                        return true
                    }

                    else -> {
                        //if userName is set to __console__, allow the usage of %say command
                        if (userName == "__console__") {
                            when (message.split(" ".toRegex())[0]) {
                                //send a message to the chat
                                "%say" -> {
                                    val lines = ArrayList<String>()
                                    lines.addAll(message.substringAfterLast("%say ").split("\n"))
                                    printToChat("", lines, apikey)
                                    return true
                                }
                            }
                        } else {
                            val lines = ArrayList<String>()
                            lines.add("Command not found! Try %help to see available commands.")
                            printToChat(userName, lines, apikey)
                            return false
                        }
                    }

                }
            }


        }
        return false
    }

    //use ClientQuery to send message (requires apikey)
    private fun printToChat(userName: String, messageLines: List<String>?, apikey: String) {
        if (userName == "__console__") {
            messageLines?.forEach { println(it) }
        } else {
            if (apikey.isNotEmpty()) {
                val stringBuffer = StringBuffer()
                messageLines?.forEach { stringBuffer.append(it + "\n") }
                runCommand(
                    "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${stringBuffer.toString()
                        .replace(
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
                val spotifyTrack = spotify.getTrack(track)
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
