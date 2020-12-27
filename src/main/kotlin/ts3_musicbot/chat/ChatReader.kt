package ts3_musicbot.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
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
    var commandListener: CommandListener,
    private val apikey: String = "",
    market: String = "",
    private val spotifyPlayer: String = "spotify",
    private val channelName: String,
    private val botName: String
) : PlayStateListener {

    private var shouldRead = false
    private var ytLink = Link("")
    private var scLink = Link("")
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
        return link
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
                    val last = chatFile.readLines().last()
                    if (last != currentLine) {
                        currentLine = last
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
     * @param message the message/command that should be parsed
     */
    fun parseLine(userName: String, message: String) {
        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {

            val commandJob: CompletableJob = Job()
            CoroutineScope(Default + commandJob).launch {
                suspend fun executeCommand(commandString: String): Boolean {
                    //parse and execute commands
                    if (commandList.any { commandString.startsWith(it) }) {
                        println("Running command $commandString")
                        when {
                            //%help command
                            commandString.contains("%help\\s*%?[a-z]*-?[a-z]*".toRegex()) -> {
                                //check extra arguments
                                if (commandString.substringAfter("%help").contains("%?help".toRegex())) {
                                    //print normal help message
                                    printToChat(userName, helpMessages["%help"]?.split("\n".toRegex()), apikey)
                                    commandJob.complete()
                                    return true
                                } else {
                                    //get extra arguments
                                    var args = commandString.substringAfter("%help ")
                                    if (!args.startsWith("%"))
                                        args = "%$args"

                                    //check if command exists
                                    return if (commandList.any { it.contains(args.toRegex()) }) {
                                        //print help for command
                                        printToChat(userName, helpMessages[args]?.split("\n".toRegex()), apikey)
                                        commandJob.complete()
                                        true
                                    } else {
                                        printToChat(
                                            userName,
                                            listOf("Command doesn't exist! See %help for available commands."),
                                            apikey
                                        )
                                        commandJob.complete()
                                        false
                                    }
                                }
                            }

                            //%queue-add and %queue-playnext command
                            commandString.contains("^%queue-(add|playnext)(\\s+-(s|(p\\s+[0-9]+)))*(\\s*(\\[URL])?((spotify:(user:\\S+:)?(track|album|playlist|show|episode|artist):\\S+)|(https?://\\S+))(\\[/URL])?\\s*,?\\s*)+(\\s+-(s|(p\\s+[0-9]+)))*\$".toRegex()) -> {
                                var commandSuccessful = false
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

                                        args[i].contains("((\\[URL])?(https?://(open\\.spotify\\.com|soundcloud\\.com|youtu\\.be|(m|www)\\.youtube\\.com))(\\[/URL])?.+)|(spotify:(track|album|playlist|show|episode|artist):.+)".toRegex()) -> {
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

                                println("Fetching data...")
                                println("Total number of links: ${links.size}")
                                printToChat(userName, listOf("Please wait, fetching data..."), apikey)
                                //add links to queue
                                for (link in links) {
                                    when {
                                        //Spotify
                                        link.link.contains("(https?://open\\.spotify\\.com/)|(spotify:(user:\\S+:)?(album|track|playlist|show|episode|artist):.+)".toRegex()) -> {
                                            //get link type
                                            val type = when {
                                                link.link.contains("(spotify:track:.+)|(open\\.spotify\\.com/track/)".toRegex()) -> "track"
                                                link.link.contains("(spotify:album:.+)|(open\\.spotify\\.com/album/)".toRegex()) -> "album"
                                                link.link.contains("(spotify:(user:\\S+:)?playlist:.+)|(open\\.spotify\\.com/(user/\\S+/)?playlist/)".toRegex()) -> "playlist"
                                                link.link.contains("(spotify:show:.+)|(open\\.spotify\\.com/show/)".toRegex()) -> "show"
                                                link.link.contains("(spotify:episode:.+)|(open\\.spotify\\.com/episode/)".toRegex()) -> "episode"
                                                link.link.contains("(spotify:artist:.+)|(open\\.spotify\\.com/artist/)".toRegex()) -> "artist"
                                                else -> "Not supported!"
                                            }
                                            println("Spotify link: $link\nLink type: $type")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = spotify.getTrack(
                                                        Link(
                                                            "https://open.spotify.com/$type/${
                                                                link.link
                                                                    .substringAfter(type).substring(1)
                                                                    .substringAfter("[URL]").substringBefore("[/URL]")
                                                                    .substringBefore("?")
                                                            }"
                                                        )
                                                    )
                                                    if (track.playability.isPlayable) {
                                                        songQueue.addToQueue(track, customPosition)
                                                        commandSuccessful = if (songQueue.getQueue().contains(track)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added track to queue.",
                                                                track
                                                            )
                                                            true
                                                        } else false
                                                    }
                                                }

                                                "album" -> {
                                                    //get album's tracks
                                                    val albumTracks = TrackList(
                                                        spotify.getAlbumTracks(
                                                            Link(
                                                                "https://open.spotify.com/$type/${
                                                                    link.link
                                                                        .substringAfter(type).substring(1)
                                                                        .substringAfter("[URL]")
                                                                        .substringBefore("[/URL]")
                                                                        .substringBefore("?")
                                                                }"
                                                            )
                                                        ).trackList.filter { it.playability.isPlayable }
                                                    )
                                                    println("Album \"${albumTracks.trackList[0].album}\" has a total of ${albumTracks.trackList.size} tracks.\nAdding to queue...")

                                                    //add tracks to queue
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(albumTracks.trackList.shuffled()) else albumTracks
                                                    songQueue.addAllToQueue(
                                                        trackList,
                                                        customPosition
                                                    )
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added album to queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
                                                }

                                                "playlist" -> {
                                                    //get playlist's tracks
                                                    val playlistTracks = TrackList(
                                                        spotify.getPlaylistTracks(
                                                            Link(
                                                                "https://open.spotify.com/$type/${
                                                                    link.link
                                                                        .substringAfter(type).substring(1)
                                                                        .substringAfter("[URL]")
                                                                        .substringBefore("[/URL]")
                                                                        .substringBefore("?")
                                                                }"
                                                            )
                                                        ).trackList.filter { it.playability.isPlayable }
                                                    )
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    //add tracks to queue
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    songQueue.addAllToQueue(
                                                        trackList,
                                                        customPosition
                                                    )
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added playlist to queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
                                                }

                                                "show" -> {
                                                    //fetch show's episodes
                                                    val episodes = TrackList(
                                                        spotify.getShow(
                                                            Link(
                                                                "https://open.spotify.com/$type/${
                                                                    link.link
                                                                        .substringAfter(type).substring(1)
                                                                        .substringAfter("[URL]")
                                                                        .substringBefore("[/URL]")
                                                                        .substringBefore("?")
                                                                }"
                                                            )
                                                        ).episodes.toTrackList().trackList.filter { it.playability.isPlayable }
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(episodes.trackList.shuffled()) else episodes
                                                    songQueue.addAllToQueue(trackList)
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added podcast to the queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
                                                }

                                                "episode" -> {
                                                    //fetch episode
                                                    val episode = spotify.getEpisode(
                                                        Link(
                                                            "https://open.spotify.com/$type/${
                                                                link.link
                                                                    .substringAfter(type).substring(1)
                                                                    .substringAfter("[URL]").substringBefore("[/URL]")
                                                                    .substringBefore("?")
                                                            }"
                                                        )
                                                    )
                                                    if (episode.playability.isPlayable) {
                                                        songQueue.addToQueue(episode.toTrack(), customPosition)
                                                        commandSuccessful =
                                                            if (songQueue.getQueue().contains(episode.toTrack())) {
                                                                commandListener.onCommandExecuted(
                                                                    commandString,
                                                                    "Added podcast episode to queue.",
                                                                    episode
                                                                )
                                                                true
                                                            } else false
                                                    }
                                                }

                                                "artist" -> {
                                                    //fetch artist's top tracks
                                                    val topTracks = spotify.getArtist(
                                                        Link(
                                                            "https://open.spotify.com/$type/${
                                                                link.link
                                                                    .substringAfter(type).substring(1)
                                                                    .substringAfter("[URL]").substringBefore("[/URL]")
                                                                    .substringBefore("?")
                                                            }"
                                                        )
                                                    ).topTracks.trackList.filter { it.playability.isPlayable }
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(topTracks.shuffled()) else TrackList(
                                                            topTracks
                                                        )
                                                    songQueue.addAllToQueue(trackList)
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added Artist's top track to the queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
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
                                                    if (track.playability.isPlayable) {
                                                        songQueue.addToQueue(track, customPosition)
                                                        commandSuccessful = if (songQueue.getQueue().contains(track)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added track to queue.",
                                                                track
                                                            )
                                                            true
                                                        } else false
                                                    }
                                                }

                                                "playlist" -> {
                                                    //get playlist tracks
                                                    val playlistTracks = TrackList(youTube.getPlaylistTracks(
                                                        Link("https://youtube.com/playlist?list=$id")
                                                    ).trackList.filter { it.playability.isPlayable })
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    songQueue.addAllToQueue(
                                                        trackList,
                                                        customPosition
                                                    )
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added playlist to queue...",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
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
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().contains(track)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added track to queue.",
                                                                track
                                                            )
                                                            true
                                                        } else false
                                                }

                                                "playlist" -> {
                                                    //get playlist tracks
                                                    val playlistTracks = TrackList(
                                                        soundCloud.getPlaylistTracks(parseLink(link)).trackList
                                                            .filter { it.playability.isPlayable }
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    songQueue.addAllToQueue(
                                                        trackList,
                                                        customPosition
                                                    )
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added playlist to queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
                                                }
                                            }
                                        }
                                    }
                                }
                                return if (commandSuccessful) {
                                    printToChat(userName, listOf("Added tracks to queue."), apikey)
                                    commandJob.complete()
                                    true
                                } else {
                                    printToChat(userName, listOf("One or more tracks could not be added :/"), apikey)
                                    commandJob.complete()
                                    false
                                }
                            }
                            //%queue-play command
                            commandString.contains("^%queue-play$".toRegex()) -> {
                                if (songQueue.getQueue().isNotEmpty()) {
                                    return if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                        printToChat(
                                            userName, listOf(
                                                "Queue is already active!",
                                                "Did you mean to type %queue-resume instead?"
                                            ), apikey
                                        )
                                        commandJob.complete()
                                        false
                                    } else {
                                        printToChat(userName, listOf("Playing Queue."), apikey)
                                        songQueue.startQueue()
                                        commandJob.complete()
                                        true
                                    }
                                } else {
                                    printToChat(userName, listOf("Queue is empty!"), apikey)
                                    commandJob.complete()
                                    return false
                                }
                            }
                            //%queue-list command
                            commandString.contains("^%queue-list(.+)?".toRegex()) -> {
                                if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                    val track = songQueue.nowPlaying()
                                    val strBuilder = StringBuilder()
                                    track.artists.artists.forEach {
                                        strBuilder.append("${it.name}, ")
                                    }
                                    printToChat(
                                        userName,
                                        listOf(
                                            if (track.linkType == LinkType.YOUTUBE) {
                                                "Currently playing:\n${track.title} : ${track.link.link}"
                                            } else {
                                                "Currently playing:\n${
                                                    strBuilder.toString().substringBeforeLast(",")
                                                } - ${track.title}" +
                                                        " : ${track.link.link}"
                                            }
                                        ),
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
                                        var queueIndex = 0
                                        while (queue.isNotEmpty()) {
                                            if (queueList.size < 15) {
                                                val track = queue[0]
                                                val strBuilder = StringBuilder()
                                                strBuilder.append("${queueIndex++}: ")
                                                track.artists.artists.forEach {
                                                    strBuilder.append("${it.name}, ")
                                                }
                                                queueList.add(
                                                    if (track.linkType == LinkType.YOUTUBE) {
                                                        "${track.title} : ${track.link.link}"
                                                    } else {
                                                        "${
                                                            strBuilder.toString().substringBeforeLast(",")
                                                        } - ${track.title}" +
                                                                " : ${track.link.link}"
                                                    }
                                                )
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
                                commandListener.onCommandExecuted(
                                    commandString,
                                    queueList.toString(),
                                    TrackList(songQueue.getQueue())
                                )
                                commandJob.complete()
                                return true
                            }
                            //%queue-clear command
                            commandString.contains("^%queue-clear$".toRegex()) -> {
                                songQueue.clearQueue()
                                return if (songQueue.getQueue().isEmpty()) {
                                    printToChat(userName, listOf("Cleared the queue."), apikey)
                                    commandListener.onCommandExecuted(commandString, "Cleared the queue.")
                                    commandJob.complete()
                                    true
                                } else {
                                    printToChat(userName, listOf("Could not clear the queue!"), apikey)
                                    commandListener.onCommandExecuted(commandString, "Could not clear the queue!")
                                    commandJob.complete()
                                    false
                                }
                            }
                            //%queue-shuffle command
                            commandString.contains("^%queue-shuffle$".toRegex()) -> {
                                songQueue.shuffleQueue()
                                printToChat(userName, listOf("Shuffled the queue."), apikey)
                                commandJob.complete()
                                return true
                            }
                            //%queue-skip command
                            commandString.contains("^%queue-skip$".toRegex()) -> {
                                songQueue.skipSong()
                                commandJob.complete()
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
                                ).first.outputText.lines()
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
                                                .first.outputText.lines()
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
                                    val currentSong = songQueue.nowPlaying()
                                    songQueue.skipSong()
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        "Skipping current song",
                                        currentSong
                                    )
                                    commandJob.complete()
                                    return true
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
                                        commandJob.complete()
                                        return false
                                    }

                                    position < 0 -> {
                                        printToChat(
                                            userName,
                                            listOf("What were you thinking?", "You can't do that."),
                                            apikey
                                        )
                                        commandJob.complete()
                                        return false
                                    }

                                    else -> {
                                        songQueue.moveTrack(Track(link = link), position)
                                        return if (songQueue.getQueue()[position].link == link) {
                                            printToChat(
                                                userName,
                                                listOf("Moved track to new position."),
                                                apikey
                                            )
                                            commandListener.onCommandExecuted(
                                                commandString,
                                                "Moved track to new position."
                                            )
                                            commandJob.complete()
                                            true
                                        } else {
                                            printToChat(
                                                userName,
                                                listOf("Couldn't move track to new position."),
                                                apikey
                                            )
                                            commandListener.onCommandExecuted(
                                                commandString,
                                                "Couldn't move track to new position."
                                            )
                                            commandJob.complete()
                                            false
                                        }
                                    }
                                }
                            }
                            //%queue-stop command
                            commandString.contains("^%queue-stop$".toRegex()) -> {
                                songQueue.stopQueue()
                                printToChat(userName, listOf("Stopped the queue."), apikey)
                                commandJob.complete()
                                return true
                            }
                            //%queue-status command
                            commandString.contains("^%queue-status$".toRegex()) -> {
                                if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                    printToChat(userName, listOf("Queue Status: Active"), apikey)
                                } else {
                                    printToChat(userName, listOf("Queue Status: Not active"), apikey)
                                }
                                commandJob.complete()
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
                                    if (currentTrack.link.link.contains("(youtube|youtu\\.be|soundcloud)".toRegex()))
                                        messageLines.add("Upload Date:   \t${currentTrack.album.releaseDate.date}")
                                    else
                                        messageLines.add("Release:     \t\t\t${currentTrack.album.releaseDate.date}")

                                    if (currentTrack.artists.artists.isNotEmpty()) {
                                        if (currentTrack.link.link.contains("soundcloud\\.com".toRegex()))
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
                                commandJob.complete()
                                return true
                            }
                            //%queue-pause command
                            commandString.contains("^%queue-pause$".toRegex()) -> {
                                songQueue.pausePlayback()
                                commandJob.complete()
                                return true
                            }
                            //%queue-resume command
                            commandString.contains("^%queue-resume$".toRegex()) -> {
                                songQueue.resumePlayback()
                                commandJob.complete()
                                return true
                            }


                            //%sp-pause command
                            commandString.contains("^%sp-pause$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer pause && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //%sp-resume & %sp-play command
                            commandString.contains("^%sp-(resume|play)$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer play && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //%sp-skip & %sp-next command
                            commandString.contains("^%sp-(skip|next)$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer next && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //%sp-prev command
                            commandString.contains("^%sp-prev$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer previous && sleep 0.1 & playerctl -p $spotifyPlayer previous")
                                commandJob.complete()
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
                                    commandJob.complete()
                                    return true
                                } else {
                                    printToChat(userName, listOf("Error! Please provide a song to play!"), apikey)
                                    commandJob.complete()
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
                                commandJob.complete()
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
                                commandJob.complete()
                                return true
                            }
                            //%sp-nowplaying command
                            commandString.contains("^%sp-nowplaying$".toRegex()) -> {
                                val lines = ArrayList<String>()
                                lines.add("Now playing on Spotify:")
                                val nowPlaying = commandRunner.runCommand("playerctl -p $spotifyPlayer metadata")
                                    .first.outputText.lines()
                                for (line in nowPlaying) {
                                    when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]) {
                                        "album" -> lines.add(
                                            "Album:\t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "artist" -> lines.add(
                                            "Artist:   \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "title" -> lines.add(
                                            "Title:    \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "url" -> lines.add(
                                            "Link:  \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                    }
                                }
                                printToChat(userName, lines, apikey)
                                commandJob.complete()
                                return true
                            }
                            //%sp-info command
                            commandString.contains("%sp-info(\\s+((\\[URL])?http(s)?://open\\.)?spotify(:|\\.com/)(user([:/])\\S+([:/]))?(album|track|playlist|artist|show|episode)[:/]\\S+\$)?".toRegex()) -> {
                                val link = parseLink(Link(commandString.substringAfter("%sp-info ")))
                                val data: Any? = when {
                                    link.link.contains("track".toRegex()) -> spotify.getTrack(link)
                                    link.link.contains("album".toRegex()) -> spotify.getAlbum(link)
                                    link.link.contains("playlist".toRegex()) -> spotify.getPlaylist(link)
                                    link.link.contains("artist".toRegex()) -> spotify.getArtist(link)
                                    link.link.contains("show".toRegex()) -> spotify.getShow(link)
                                    link.link.contains("episode".toRegex()) -> spotify.getEpisode(link)
                                    link.link.contains("user".toRegex()) -> spotify.getUser(link)
                                    else -> null
                                }
                                if (data != null) {
                                    printToChat(
                                        userName, listOf("", data.toString()), apikey
                                    )
                                    commandListener.onCommandExecuted(commandString, data.toString(), data)
                                    commandJob.complete()
                                    return true
                                } else {
                                    printToChat(
                                        userName,
                                        listOf("You have to provide a Spotify link or URI to a track!"),
                                        apikey
                                    )
                                    commandListener.onCommandExecuted(commandString, data.toString(), data)
                                    commandJob.complete()
                                    return false
                                }
                            }


                            //%yt-pause command
                            commandString.contains("^%yt-pause$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%yt-resume command
                            commandString.contains("^%yt-resume$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%yt-play command
                            commandString.contains("^%yt-play$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%yt-stop command
                            commandString.contains("^%yt-stop$".toRegex()) -> {
                                commandRunner.runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
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
                                                    "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket" +
                                                            "--ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0," +
                                                            "cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" +
                                                            " --ytdl \"$ytLink\"",
                                                    inheritIO = true,
                                                    ignoreOutput = true
                                                )
                                            }
                                        }.run()
                                    }.start()
                                }
                                commandJob.complete()
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
                                commandJob.complete()
                                return true
                            }

                            //%sc-pause command
                            commandString.contains("^%sc-pause$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%sc-resume command
                            commandString.contains("^%sc-resume$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%sc-play command
                            commandString.contains("^%sc-play$".toRegex()) -> {
                                commandRunner.runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%sc-stop command
                            commandString.contains("^%sc-stop$".toRegex()) -> {
                                commandRunner.runCommand("echo \"stop\" | socat - /tmp/mpvsocket")
                                commandJob.complete()
                                return true
                            }
                            //%sc-playsong command
                            commandString.contains("^%sc-playsong\\s+".toRegex()) -> {
                                scLink = parseLink(Link(message))
                                if (scLink.link.isNotEmpty()) {
                                    Thread {
                                        Runnable {
                                            run {
                                                commandRunner.runCommand(
                                                    "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl \"$scLink\"",
                                                    inheritIO = true,
                                                    ignoreOutput = true
                                                )
                                            }
                                        }.run()
                                    }.start()
                                }
                                commandJob.complete()
                                return true
                            }
                            //%sc-nowplaying command
                            commandString.contains("^%sc-nowplaying$".toRegex()) -> {
                                printToChat(
                                    userName, listOf(
                                        "Now playing on SoundCloud:",
                                        "${soundCloud.getTrack(scLink).title}",
                                        "Link: $scLink"
                                    ), apikey
                                )
                                commandJob.complete()
                                return true
                            }

                            //%sp-search/yt-search/sc-search command
                            commandString.contains("^%[a-z]+-search\\s+".toRegex()) -> {
                                val service = commandString.substringBefore("-").substringAfter("%")
                                val searchType =
                                    SearchType(message.substringAfter("%$service-search ").split(" ".toRegex())[0])
                                if (searchType.getType() != SearchType.Type.OTHER && when (service) {
                                        "sp" -> spotify.supportedSearchTypes.contains(searchType.getType())
                                        "yt" -> youTube.supportedSearchTypes.contains(searchType.getType())
                                        "sc" -> soundCloud.supportedSearchTypes.contains(searchType.getType())
                                        else -> false
                                    }
                                ) {
                                    val searchQuery = SearchQuery(message.substringAfter("$searchType "))
                                    printToChat(userName, listOf("Searching, please wait..."), apikey)
                                    val results = when (service) {
                                        "sp" -> spotify.searchSpotify(searchType, searchQuery)
                                        "yt" -> youTube.searchYoutube(searchType, searchQuery)
                                        "sc" -> soundCloud.searchSoundCloud(searchType, searchQuery)
                                        else -> SearchResults(emptyList())
                                    }
                                    if (results.isNotEmpty()) {
                                        val searchResults = ArrayList<SearchResult>()
                                        for (result in results.results) {
                                            if (searchResults.size < 3) {
                                                searchResults.add(result)
                                            } else {
                                                printToChat(
                                                    userName,
                                                    listOf("", SearchResults(searchResults).toString()),
                                                    apikey
                                                )
                                                searchResults.clear()
                                            }
                                        }
                                        printToChat(
                                            userName, listOf("", SearchResults(searchResults).toString()),
                                            apikey
                                        )
                                        commandListener.onCommandExecuted(commandString, results.toString(), results)
                                        commandJob.complete()
                                        return true
                                    } else {
                                        printToChat(userName, listOf("No results found!"), apikey)
                                        commandListener.onCommandExecuted(commandString, results.toString(), results)
                                        commandJob.complete()
                                        return false
                                    }
                                } else {
                                    printToChat(
                                        userName,
                                        listOf("Search type not supported! Run %help %$service-search to see more information on this command."),
                                        apikey
                                    )
                                    commandListener.onCommandExecuted(commandString, "Not supported", searchType)
                                    commandJob.complete()
                                    return false
                                }
                            }
                            else -> {
                                commandJob.complete()
                                return false
                            }

                        }
                    } else {
                        //if userName is set to __console__, allow the usage of %say command
                        if (userName == "__console__") {
                            when {
                                //send a message to the chat
                                message.contains("^%say(\\s+\\S+)+$".toRegex()) -> {
                                    printToChat("", message.substringAfter("%say ").lines(), apikey)
                                    commandJob.complete()
                                    return true
                                }
                            }
                        } else {
                            val lines = ArrayList<String>()
                            lines.add("Command not found! Try %help to see available commands.")
                            printToChat(userName, lines, apikey)
                            commandJob.complete()
                            return false
                        }
                    }
                    commandJob.complete()
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
                commandJob.join()
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
                messageLines?.forEach { stringBuilder.appendLine(it) }
                val distro = commandRunner.runCommand("cat /etc/issue", printOutput = false).first.outputText
                val command = when {
                    distro.contains("Ubuntu".toRegex()) -> {
                        "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${
                            stringBuilder.toString()
                                .replace(" ", "\\\\\\s")
                                .replace("\n", "\\\\\\n")
                                .replace("/", "\\/")
                                .replace("|", "\\\\p")
                                .replace("'", "\\\\'")
                                .replace("\"", "\\\"")
                                .replace("`", "\\`")
                                .replace("&quot;", "\\\"")
                                .replace("$", "\\\\$")
                        }\"; echo quit) | nc localhost 25639"
                    }

                    else -> {
                        "(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${
                            stringBuilder.toString()
                                .replace(" ", "\\s")
                                .replace("\n", "\\n")
                                .replace("/", "\\/")
                                .replace("|", "\\p")
                                .replace("'", "\\'")
                                .replace("\"", "\\\"")
                                .replace("`", "\\`")
                                .replace("&quot;", "\\\"")
                                .replace("$", "\\$")
                        }\"; echo quit) | nc localhost 25639"
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
                    println("Now playing:\n$track")
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

interface CommandListener {
    fun onCommandExecuted(command: String, output: String, extra: Any? = null)
}
