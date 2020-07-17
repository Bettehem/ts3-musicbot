package ts3_musicbot.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import ts3_musicbot.services.*
import ts3_musicbot.util.*
import ts3_musicbot.util.CommandList.commandList
import ts3_musicbot.util.CommandList.helpMessages
import java.io.File
import java.lang.Runnable
import java.util.*
import kotlin.collections.ArrayList

class ChatReader(
    private var chatFile: File,
    private var onChatUpdateListener: ChatUpdateListener,
    private val apikey: String = "",
    market: String = "",
    private val spotifyPlayer: String = "spotify",
    private val channelName: String,
    private val botName: String
) : PlayStateListener {

    private var shouldRead = false
    private var ytLink = Link("")
    private val commandRunner = CommandRunner()
    private val spotify = Spotify(market)
    private val youTube = YouTube()
    private val soundCloud = SoundCloud()
    private var voteSkipUsers = ArrayList<Pair<String, Boolean>>()

    @Volatile
    private var songQueue = SongQueue(spotifyPlayer, this)

    init {
        //initialise spotify token
        CoroutineScope(Default).launch {
            spotify.updateToken()
        }
    }

    private fun parseLink(link: Link): Link {
        when (chatFile.extension) {
            "html" -> {
                return Link(
                    if (link.link.contains("href=\"")) {
                        link.link.substringAfter(link.link.split(" ".toRegex())[0])
                            .split("href=\"".toRegex())[1].split("\">".toRegex())[0].replace(
                            " -s",
                            ""
                        )
                    } else {
                        link.link
                    }
                )
            }
            "txt" -> {
                return Link(link.link.substringAfter("[URL]").substringBefore("[/URL]"))
            }
        }
        return Link("")
    }

    /**
     * Starts reading the chat
     */
    fun startReading(): Boolean {
        shouldRead = true
        return if (chatFile.isFile) {
            CoroutineScope(IO).launch {
                var currentLine = ""
                while (shouldRead) {
                    val lines = chatFile.readLines()
                    if (currentLine != lines.last()) {
                        currentLine = lines.last()
                        chatUpdated(currentLine)
                    }
                    delay(500)
                }
            }
            true
        } else {
            false
        }
    }

    fun stopReading() {
        shouldRead = false
    }

    /**
     * Parses the new line in the chat file and runs it if it's a command.
     * @param userName
     */
    fun parseLine(userName: String, message: String) {
        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {

            val commandJob: CompletableJob = Job()
            CoroutineScope(Default + commandJob).launch {

                suspend fun executeCommand(commandString: String): Boolean {
                    //parse and execute commands
                    if (commandList.any { commandString.startsWith(it) }) {
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
                                val links = ArrayList<Link>()
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
                                                links.addAll(args[i].split(",\\s*".toRegex()).map { Link(it) })
                                            else
                                                links.add(Link(args[i]))
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
                                        link.link.contains("(https?://open\\.spotify\\.com/)|(spotify:(album|track|playlist):.+)".toRegex()) -> {
                                            //get link type
                                            val type = when {
                                                link.link.contains("(spotify:track:.+)|(open\\.spotify\\.com/track/)".toRegex()) -> "track"
                                                link.link.contains("(spotify:album:.+)|(open\\.spotify\\.com/album/)".toRegex()) -> "album"
                                                link.link.contains("(spotify:playlist:.+)|(open\\.spotify\\.com/playlist/)".toRegex()) -> "playlist"
                                                else -> ""
                                            }
                                            println("Spotify link: $link\nLink type: $type")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = spotify.getTrack(
                                                        Link(
                                                            "https://open.spotify.com/$type/${link.link
                                                                .substringAfter(type).substring(1)
                                                                .substringBefore("[/URL]").substringBefore("?")}"
                                                        )
                                                    )
                                                    if (track.playability.isPlayable)
                                                        songQueue.addToQueue(track, customPosition)
                                                }

                                                "album" -> {
                                                    //get album's tracks
                                                    val albumTracks = TrackList(
                                                        spotify.getAlbumTracks(
                                                            Link(
                                                                "https://open.spotify.com/$type/${link.link
                                                                    .substringAfter(type).substring(1)
                                                                    .substringBefore("[/URL]").substringBefore("?")}"
                                                            )
                                                        ).trackList.filter { it.playability.isPlayable }
                                                    )
                                                    println("Album \"${albumTracks.trackList[0].album}\" has a total of ${albumTracks.trackList.size} tracks.\nAdding to queue...")
                                                    //add tracks to queue
                                                    songQueue.addAllToQueue(
                                                        if (shouldShuffle) TrackList(albumTracks.trackList.shuffled()) else albumTracks,
                                                        customPosition
                                                    )
                                                }

                                                "playlist" -> {
                                                    //get playlist's tracks
                                                    val playlistTracks = TrackList(
                                                        spotify.getPlaylistTracks(
                                                            Link(
                                                                "https://open.spotify.com/$type/${link.link
                                                                    .substringAfter(type).substring(1)
                                                                    .substringAfter("[URL]").substringBefore("[/URL]")
                                                                    .substringBefore("?")}"
                                                            )
                                                        ).trackList.filter { it.playability.isPlayable }
                                                    )
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    //add tracks to queue
                                                    songQueue.addAllToQueue(
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks,
                                                        customPosition
                                                    )
                                                }
                                            }
                                            println("Added to queue.")
                                        }

                                        //YouTube
                                        link.link.contains("https?://(youtu\\.be|(m|www)\\.youtube\\.com)".toRegex()) -> {
                                            //get link type
                                            val type = when {
                                                link.link.contains("https?://((m|www)\\.youtube\\.com/watch\\?v=|youtu\\.be/\\S+)".toRegex()) -> "track"
                                                link.link.contains("https?://((m|www)\\.youtube\\.com/playlist\\?list=\\S+)".toRegex()) -> "playlist"
                                                else -> ""
                                            }
                                            println("YouTube link: $link\nLink type: $type")
                                            //get track/playlist id
                                            val id =
                                                link.link.split("((m|www)\\.youtube\\.com/(watch|playlist)\\?(v|list)=)|(youtu.be/)".toRegex())[1]
                                                    .substringAfter("[URL]")
                                                    .substringBefore("[/URL]")
                                                    .substringBefore("&")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = youTube.getVideo(Link("https://youtu.be/$id"))
                                                    if (track.playability.isPlayable)
                                                        songQueue.addToQueue(track, customPosition)
                                                }

                                                "playlist" -> {
                                                    //get playlist tracks
                                                    val playlistTracks = TrackList(youTube.getPlaylistTracks(
                                                        Link("https://youtube.com/playlist?list=$id")
                                                    ).trackList.filter { it.playability.isPlayable })
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    songQueue.addAllToQueue(
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks,
                                                        customPosition
                                                    )
                                                }
                                            }
                                        }

                                        //SoundCloud
                                        link.link.contains("https?://soundcloud\\.com/".toRegex()) -> {
                                            //get link type
                                            val type = when {
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/(?!sets)\\S+".toRegex()) -> "track"
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/sets/\\S+".toRegex()) -> "playlist"
                                                else -> ""
                                            }
                                            println("SoundCloud link: $link\nLink type: $type")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = soundCloud.getTrack(
                                                        Link(
                                                            link.link.substringAfter("[URL]")
                                                                .substringBefore("[/URL]")
                                                                .substringBefore("?")
                                                        )
                                                    )
                                                    songQueue.addToQueue(track, customPosition)
                                                }

                                                "playlist" -> {
                                                    //get playlist tracks
                                                    val playlistTracks = TrackList(
                                                        soundCloud.getPlaylistTracks(parseLink(link))
                                                            .filter { it.playability.isPlayable }
                                                    )
                                                    songQueue.addAllToQueue(
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks,
                                                        customPosition
                                                    )
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
                                    if (songQueue.queueState != SongQueue.State.QUEUE_STOPPED) {
                                        printToChat(
                                            userName, listOf(
                                                "Queue is already active!",
                                                "Did you mean to type %queue-resume instead?"
                                            ), apikey
                                        )
                                    } else {
                                        printToChat(userName, listOf("Playing Queue."), apikey)
                                        songQueue.startQueue()
                                    }
                                } else {
                                    printToChat(userName, listOf("Queue is empty!"), apikey)
                                }
                                return true
                            }
                            //%queue-list command
                            commandString.contains("^%queue-list\\s*".toRegex()) -> {
                                if (songQueue.queueState != SongQueue.State.QUEUE_STOPPED) {
                                    printToChat(
                                        userName,
                                        listOf("Currently playing:\n${songQueue.nowPlaying().link}"),
                                        apikey
                                    )
                                }
                                printToChat(userName, listOf("Song Queue:"), apikey)
                                val queueList = ArrayList<String>()
                                when {
                                    songQueue.getQueue().isEmpty() -> printToChat(
                                        userName,
                                        listOf("Queue is empty!"),
                                        apikey
                                    )
                                    else -> {
                                        val queue = if (songQueue.getQueue().size <= 15) {
                                            songQueue.getQueue().toMutableList()
                                        } else {
                                            if (commandString.substringAfter("%queue-list")
                                                    .contains(" -a") || commandString.substringAfter("%queue-list")
                                                    .contains(" --all")
                                            ) {
                                                songQueue.getQueue().toMutableList()
                                            } else {
                                                songQueue.getQueue().subList(0, 14).toMutableList()
                                            }
                                        }
                                        while (queue.isNotEmpty()) {
                                            if (queueList.size < 15) {
                                                queueList.add(queue[0].link.link)
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
                                printToChat(
                                    userName,
                                    listOf("Queue Length: ${songQueue.getQueue().size} tracks."),
                                    apikey
                                )
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
                            //%queue-voteskip command
                            commandString.contains("^%queue-voteskip$".toRegex()) -> {
                                val userList = ArrayList<String>()
                                //get channel list
                                val channelList = ArrayList<Pair<String, String>>()
                                val tsChannelListData = commandRunner.runCommand(
                                    "(echo auth apikey=$apikey; echo \"channellist\"; echo quit) | nc localhost 25639",
                                    printOutput = false
                                ).split("\n".toRegex())
                                for (line in tsChannelListData) {
                                    if (line.contains("cid=".toRegex())) {
                                        val channelDataList = line.split("\\|".toRegex())
                                        for (channel in channelDataList) {
                                            val channelData = channel.split(" ".toRegex())
                                            channelList.add(
                                                Pair(
                                                    channelData[3].split("=".toRegex())[1],
                                                    channelData[0].split("=".toRegex())[1]
                                                )
                                            )
                                        }
                                        break
                                    }
                                }

                                //get users in current channel
                                for (channel in channelList) {
                                    if (channel.first == channelName.substringAfterLast("/")) {
                                        val tsUserListData =
                                            commandRunner.runCommand("(echo auth apikey=$apikey; echo \"clientlist\"; echo quit) | nc localhost 25639")
                                                .split("\n".toRegex())
                                        for (line in tsUserListData) {
                                            if (line.contains("clid=".toRegex())) {
                                                val clientDataList = line.split("\\|".toRegex())
                                                for (data in clientDataList) {
                                                    if (data.split(" ".toRegex())[1].split("=".toRegex())[1] == channel.second) {
                                                        userList.add(data.split(" ".toRegex())[3].split("=".toRegex())[1])
                                                    }
                                                }
                                            }
                                        }
                                        break
                                    }
                                }

                                //update voteskip users list
                                val currentList = voteSkipUsers.toList()
                                val newList = ArrayList<Pair<String, Boolean>>()
                                voteSkipUsers.clear()
                                for (user in userList) {
                                    if (user != botName) {
                                        for (voteSkipUser in currentList) {
                                            if (user == voteSkipUser.first) {
                                                if (userName == user) {
                                                    newList.add(Pair(user, true))
                                                } else {
                                                    newList.add(Pair(user, voteSkipUser.second))
                                                }
                                            }
                                        }
                                        if (currentList.isEmpty()) {
                                            newList.add(Pair(user, userName == user))
                                        }
                                    }
                                }
                                voteSkipUsers.addAll(newList)
                                if (voteSkipUsers.any { !it.second }) {
                                    printToChat(
                                        userName,
                                        listOf("\nAll users have not voted yet.\nWaiting for more votes..."),
                                        apikey
                                    )
                                } else {
                                    printToChat(userName, listOf("Skipping current song."), apikey)
                                    voteSkipUsers.clear()
                                    songQueue.skipSong()
                                }
                            }
                            //%queue-move command
                            commandString.contains("^%queue-move\\s+".toRegex()) -> {
                                val link = Link(parseLink(Link(commandString)).link.substringBefore("?"))
                                var position = 0
                                //parse arguments
                                val args = commandString.split("\\s+".toRegex())
                                for (i in args.indices) {
                                    when {
                                        args[i].contains("(-p|--position)".toRegex()) -> {
                                            if (args.size >= i + 1 && args[i + 1].contains("\\d+".toRegex())) {
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
                                        songQueue.moveTrack(Track(link = link), position)
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
                                if (songQueue.queueState != SongQueue.State.QUEUE_STOPPED) {
                                    printToChat(userName, listOf("Queue Status: Active"), apikey)
                                } else {
                                    printToChat(userName, listOf("Queue Status: Not active"), apikey)
                                }
                                return true
                            }
                            //%queue-nowplaying command
                            commandString.contains("^%queue-nowplaying$".toRegex()) -> {
                                if (songQueue.nowPlaying().title.name.isNotEmpty()) {
                                    val currentTrack = songQueue.nowPlaying()
                                    val messageLines = ArrayList<String>()
                                    messageLines.add("Now playing:")
                                    if (currentTrack.album.name.name.isNotEmpty())
                                        messageLines.add("Album Name:  \t${currentTrack.album.name}")
                                    if (currentTrack.album.link.link.isNotEmpty())
                                        messageLines.add("Album Link:  \t\t${currentTrack.album.link}")
                                    if (currentTrack.link.link.contains("(youtube|youtu.be|soundcloud)".toRegex())) {
                                        messageLines.add("Upload Date:   \t${currentTrack.album.releaseDate.date}")
                                    } else {
                                        messageLines.add("Release:     \t\t\t${currentTrack.album.releaseDate.date}")
                                    }
                                    if (currentTrack.artists.artists.isNotEmpty()) {
                                        if (currentTrack.link.link.contains("soundcloud.com".toRegex()))
                                            messageLines.add("Uploader: \t\t\t${currentTrack.artists.artists[0].name}")
                                        else
                                            messageLines.add("Artists:\n${currentTrack.artists}")
                                    }
                                    messageLines.add("Track Title:   \t\t${currentTrack.title}")
                                    messageLines.add("Track Link:    \t\t${currentTrack.link}")
                                    printToChat(userName, messageLines, apikey)
                                } else {
                                    printToChat(userName, listOf("No song playing!"), apikey)
                                }
                                return true
                            }
                            //%queue-pause command
                            commandString.contains("^%queue-pause$".toRegex()) -> {
                                songQueue.pausePlayback()
                                return true
                            }
                            //%queue-resume command
                            commandString.contains("^%queue-resume$".toRegex()) -> {
                                songQueue.resumePlayback()
                                return true
                            }


                            //%sp-pause command
                            commandString.contains("^%sp-pause$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer pause && sleep 1")
                                return true
                            }
                            //%sp-resume & %sp-play command
                            commandString.contains("^%sp-(resume|play)$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer play && sleep 1")
                                return true
                            }
                            //%sp-skip & %sp-next command
                            commandString.contains("^%sp-(skip|next)$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer next && sleep 1")
                                return true
                            }
                            //%sp-prev command
                            commandString.contains("^%sp-prev$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer previous && sleep 0.1 & playerctl -p $spotifyPlayer previous")
                                return true
                            }
                            //%sp-playsong command
                            //Play Spotify song based on link or URI
                            commandString.contains("^%sp-playsong\\s+".toRegex()) -> {
                                if (message.substringAfter("%sp-playsong").isNotEmpty()) {
                                    if (parseLink(Link(message)).link.startsWith("https://open.spotify.com/track")) {
                                        commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer open spotify:track:${
                                            parseLink(Link(message)).link
                                                .split("track/".toRegex())[1]
                                                .split("\\?si=".toRegex())[0]
                                            }"
                                        )
                                    } else {
                                        commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer open spotify:track:${
                                            message.split(" ".toRegex())[1]
                                                .split("track:".toRegex())[1]
                                            }"
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
                                if (message.split(" ".toRegex())[1].startsWith("spotify:user:")
                                    && message.split(" ".toRegex())[1].contains(":playlist:")
                                ) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:user:${
                                        message.split(" ".toRegex())[1]
                                            .split(":".toRegex())[2]
                                        }:playlist:${
                                        message.split(":".toRegex()).last()
                                        }"
                                    )
                                } else if (message.split(" ".toRegex())[1].startsWith("spotify:playlist")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:playlist:${
                                        message.substringAfter("playlist:")
                                        }"
                                    )
                                } else if (parseLink(Link(message)).link.startsWith("https://open.spotify.com/")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:playlist:${
                                        parseLink(Link(message)).link.substringAfter("playlist/")
                                            .substringBefore("?")
                                        }"
                                    )
                                }
                                return true
                            }
                            //%sp-playalbum command
                            commandString.contains("^%sp-playalbum\\s+".toRegex()) -> {
                                if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:album:${
                                        message.substringAfter("album:")
                                        }"
                                    )
                                } else if (parseLink(Link(message)).link.startsWith("https://open.spotify.com/")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:album:${
                                        parseLink(Link(message)).link.substringAfter("album/")
                                            .substringBefore("?")
                                        }"
                                    )
                                }
                                return true
                            }
                            //%sp-nowplaying command
                            commandString.contains("^%sp-nowplaying$".toRegex()) -> {
                                val lines = ArrayList<String>()
                                lines.add("Now playing on Spotify:")
                                val nowPlaying = commandRunner.runCommand("playerctl -p $spotifyPlayer metadata")
                                    .split("\n".toRegex())
                                for (line in nowPlaying) {
                                    when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]) {
                                        "album" -> lines.add(
                                            "Album:\t${line.substringAfter(
                                                line.substringAfter("xesam:")
                                                    .split("\\s+".toRegex())[0]
                                            )}"
                                        )
                                        "artist" -> lines.add(
                                            "Artist:   \t${line.substringAfter(
                                                line.substringAfter("xesam:")
                                                    .split("\\s+".toRegex())[0]
                                            )}"
                                        )
                                        "title" -> lines.add(
                                            "Title:    \t${line.substringAfter(
                                                line.substringAfter("xesam:")
                                                    .split("\\s+".toRegex())[0]
                                            )}"
                                        )
                                        "url" -> lines.add(
                                            "Link:  \t${line.substringAfter(
                                                line.substringAfter("xesam:")
                                                    .split("\\s+".toRegex())[0]
                                            )}"
                                        )
                                    }
                                }
                                printToChat(userName, lines, apikey)
                                return true
                            }
                            //%sp-search command
                            commandString.contains("^%sp-search\\s+".toRegex()) -> {
                                when (message.split(" ".toRegex())[1].toLowerCase()) {
                                    "track", "playlist", "album" -> {

                                        val lines = ArrayList<String>()
                                        lines.add("Searching, please wait...")
                                        printToChat(userName, lines, apikey)
                                        lines.clear()
                                        val searchedLines = ArrayList<String>()
                                        searchedLines.addAll(
                                            spotify.searchSpotify(
                                                SearchType(message.split(" ".toRegex())[1].toLowerCase()),
                                                SearchQuery(
                                                    message.substringAfter(message.split(" ".toRegex())[1] + " ")
                                                        .replace("&owner=\\w+", "")
                                                )
                                            ).split("\n".toRegex())
                                        )
                                        for (line in searchedLines.indices) {
                                            val msg = message.substringAfter(message.split(" ".toRegex())[1])
                                            when {
                                                msg.contains("&owner=")
                                                        && searchedLines[line].replace(" ", "").toLowerCase()
                                                    .contains(
                                                        "Owner:${
                                                        msg.substringAfter("&owner=")
                                                            .substringBefore("&")
                                                        }".toLowerCase().toRegex()
                                                    ) -> {
                                                    lines.addAll(
                                                        listOf(
                                                            searchedLines[line - 1],
                                                            searchedLines[line],
                                                            searchedLines[line + 1],
                                                            searchedLines[line + 2]
                                                        )
                                                    )
                                                }

                                                !msg.contains("&owner=".toRegex()) -> lines.add(searchedLines[line])
                                            }

                                        }

                                        if (lines.size == 1 && lines[0] == "") {
                                            lines.add(
                                                "No search results with \"${
                                                message.substringAfter(
                                                    "${message.split(" ".toRegex())[1]} "
                                                )}\"!"
                                            )
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
                                                    resultLines.add(0, "")
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
                                                    resultLines.add(0, "")
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
                                                    resultLines.add(0, "")
                                                    printToChat(userName, resultLines, apikey)
                                                }
                                            }
                                        }
                                        return true
                                    }
                                    else -> {
                                        val lines = ArrayList<String>()
                                        lines.add("Error! \"${message.split(" ".toRegex())[1]}\" is not a valid search type! See %help for more info.")
                                        printToChat(userName, lines, apikey)
                                        return false
                                    }
                                }
                            }
                            //%sp-info command
                            commandString.contains("%sp-info(\\s+((\\[URL])?http(s)?://open\\.)?spotify(:|\\.com/)(album|track|playlist|artist)[:/]\\S+\$)?".toRegex()) -> {
                                val link = parseLink(Link(commandString.substringAfter("%sp-info ")))
                                when {
                                    link.link.contains("track".toRegex()) -> {
                                        val track = spotify.getTrack(link)
                                        printToChat(
                                            userName, track.toString().lines(), apikey
                                        )
                                        return true
                                    }
                                    link.link.contains("album".toRegex()) -> {
                                        val album = spotify.getAlbum(link)
                                        val lines = ArrayList<String>()
                                        for (line in album.toString().lines()) {
                                            if (lines.size <= 15) {
                                                lines.add(line)
                                            } else {
                                                lines.add(0, "")
                                                lines.add(line)
                                                printToChat(userName, lines, apikey)
                                                lines.clear()
                                            }
                                        }
                                        lines.add(0, "")
                                        printToChat(userName, lines, apikey)
                                        return true
                                    }
                                    link.link.contains("playlist".toRegex()) -> {
                                        val playlist = spotify.getPlaylist(link)
                                        val lines = ArrayList<String>()
                                        for (line in playlist.toString().lines()) {
                                            if (lines.size <= 15) {
                                                lines.add(line)
                                            } else {
                                                lines.add(0, "")
                                                lines.add(line)
                                                printToChat(userName, lines, apikey)
                                                lines.clear()
                                            }
                                        }
                                        lines.add(0, "")
                                        printToChat(userName, lines, apikey)
                                        return true
                                    }
                                    link.link.contains("artist".toRegex()) -> {
                                        val artist = spotify.getArtist(link)
                                        val lines = ArrayList<String>()
                                        for (line in artist.toString().lines()) {
                                            if (lines.size <= 14) {
                                                lines.add(line)
                                            } else {
                                                lines.add(0, "")
                                                lines.add(line)
                                                printToChat(userName, lines, apikey)
                                                lines.clear()
                                            }
                                        }
                                        lines.add(0, "")
                                        printToChat(userName, lines, apikey)
                                        return true
                                    }
                                    else -> {
                                        printToChat(
                                            userName,
                                            listOf("You have to provide a Spotify link or URI to a track!"),
                                            apikey
                                        )
                                        return false
                                    }
                                }
                            }


                            //%yt-pause command
                            commandString.contains("^%yt-pause$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%yt-resume command
                            commandString.contains("^%yt-resume$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%yt-play command
                            commandString.contains("^%yt-play$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%yt-stop command
                            commandString.contains("^%yt-stop$".toRegex()) -> {
                                commandRunner.runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%yt-playsong command
                            commandString.contains("^%yt-playsong\\s+".toRegex()) -> {
                                ytLink = parseLink(Link(message))
                                if (ytLink.link.isNotEmpty()) {
                                    Thread {
                                        Runnable {
                                            run {
                                                commandRunner.runCommand(
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
                                        youTube.getVideoTitle(ytLink),
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
                                        val results =
                                            youTube.searchYoutube(
                                                SearchType(searchType.replace("track", "video")),
                                                SearchQuery(searchQuery)
                                            )
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
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%sc-resume command
                            commandString.contains("^%sc-resume$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%sc-play command
                            commandString.contains("^%sc-play$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%sc-stop command
                            commandString.contains("^%sc-stop$".toRegex()) -> {
                                commandRunner.runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                                return true
                            }
                            //%sc-playsong command
                            commandString.contains("^%sc-playsong\\s+".toRegex()) -> {
                                val scLink = parseLink(Link(message))
                                //Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl -o - \"$scLink\" | mpv --no-terminal --no-video --input-ipc-server=/tmp/mpvsocket - &"))
                                if (scLink.link.isNotEmpty()) {
                                    Thread {
                                        Runnable {
                                            run {
                                                commandRunner.runCommand(
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
                            }

                        }
                    } else {
                        //if userName is set to __console__, allow the usage of %say command
                        if (userName == "__console__") {
                            when {
                                //send a message to the chat
                                message.contains("^%say(\\s+\\S+)+$".toRegex()) -> {
                                    printToChat("", message.substringAfter("%say ").lines(), apikey)
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

                    return false
                }


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
                                if (executeCommand(cmd)) {
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
                                executeCommand(command.substringBeforeLast(";"))

                        }
                    }
                }
            }
        }
    }

    //use ClientQuery to send message (requires apikey)
    private fun printToChat(userName: String, messageLines: List<String>?, apikey: String) {
        if (userName == "__console__") {
            messageLines?.forEach { println(it) }
        } else {
            if (apikey.isNotEmpty()) {
                val stringBuilder = StringBuilder()
                messageLines?.forEach { stringBuilder.appendln(it) }
                val distro = commandRunner.runCommand("cat /etc/issue", printOutput = false)
                val command = when {
                    distro.contains("Ubuntu".toRegex()) -> {
                        "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${stringBuilder.toString()
                            .replace(" ", "\\\\\\s")
                            .replace("\n", "\\\\\\n")
                            .replace("/", "\\/")
                            .replace("|", "\\\\p")
                            .replace("'", "\\\\'")
                            .replace("\"", "\\\"")
                            .replace("`", "\\`")
                            .replace("&quot;", "\\\"")
                            .replace("$", "\\\\$")}\"; echo quit) | nc localhost 25639"
                    }

                    else -> {
                        "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${stringBuilder.toString()
                            .replace(" ", "\\s")
                            .replace("\n", "\\n")
                            .replace("/", "\\/")
                            .replace("|", "\\p")
                            .replace("'", "\\'")
                            .replace("\"", "\\\"")
                            .replace("`", "\\`")
                            .replace("&quot;", "\\\"")
                            .replace("$", "\\$")}\"; echo quit) | nc localhost 25639"
                    }
                }
                commandRunner.runCommand(command, printOutput = false)
            }
        }
    }

    private suspend fun chatUpdated(line: String) {
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
                withContext(Main) {
                    onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
                }
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
                    withContext(Main) {
                        onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
                    }
                }
            }

            else -> {
                println("Error! file format \"${chatFile.extension}\" not supported!")
            }
        }
    }

    override fun onTrackEnded(player: String, track: Track) {}
    override fun onTrackPaused(player: String, track: Track) {}
    override fun onTrackResumed(player: String, track: Track) {}

    override fun onTrackStarted(player: String, track: Track) {
        when (player) {
            "spotify", "ncspot" -> {
                CoroutineScope(Default).launch {
                    parseLine("", "%queue-nowplaying")
                    println("Playing ${track.artists} - ${track.title}")
                }
            }
            "mpv" -> {
                when (track.linkType) {
                    LinkType.YOUTUBE -> {
                        ytLink = track.link
                        println("Playing ${track.title}")

                    }
                    LinkType.SOUNDCLOUD -> {
                        println("Playing ${track.artists} - ${track.title}")
                    }
                    else -> {
                    }
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
