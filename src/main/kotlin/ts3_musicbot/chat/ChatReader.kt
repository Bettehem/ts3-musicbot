package ts3_musicbot.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import ts3_musicbot.client.TeamSpeak
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
import ts3_musicbot.util.*
import java.io.File
import java.util.*

class ChatReader(
    private val client: Any,
    private var chatFile: File,
    private var onChatUpdateListener: ChatUpdateListener,
    var commandListener: CommandListener,
    private val apikey: String = "",
    market: String = "",
    private val spotifyPlayer: String = "spotify",
    private val channelName: String,
    private val botName: String,
    private val mpvVolume: Int,
    private val commandList: CommandList = CommandList()
) : PlayStateListener {

    private var shouldRead = false
    private val commandRunner = CommandRunner()
    private val spotify = Spotify(market)
    private val youTube = YouTube()
    private val soundCloud = SoundCloud()
    private var voteSkipUsers = ArrayList<Pair<String, Boolean>>()
    var latestMsgUsername = ""

    @Volatile
    private var songQueue = SongQueue(spotifyPlayer, mpvVolume, this)

    init {
        //initialise spotify token
        CoroutineScope(IO).launch {
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
        return when (client) {
            is TeamSpeak -> {
                //client.addListener(this)
                true
            }
            else -> {
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
        }
    }

    fun stopReading() {
        shouldRead = false
    }

    /**
     * Parses the new line in the chat file and runs it if it's a command.
     * @param message the message/command that should be parsed
     */
    fun parseLine(message: String) {
        //check if message is a command
        if ((message.startsWith(commandList.commandPrefix) || message.startsWith("%")) && message.length > 1) {

            val commandJob = Job()
            CoroutineScope(IO + commandJob).launch {
                suspend fun executeCommand(commandString: String): Boolean {
                    //parse and execute commands
                    if (commandList.commandList.any { commandString.startsWith(it.value) } || commandString.startsWith("%help")) {
                        println("Running command $commandString")
                        when {
                            //help command
                            commandString.contains("(%help|${commandList.commandList["help"]})\\s*${commandList.commandPrefix}?[a-z]*-?[a-z]*".toRegex()) -> {
                                //check extra arguments
                                if (commandString.contains("^(%help|${commandList.commandList["help"]})$".toRegex())) {
                                    //print normal help message
                                    printToChat(
                                        listOf(commandList.helpMessages["help"].orEmpty())
                                    )
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        commandList.helpMessages["help"].orEmpty()
                                    )
                                    commandJob.complete()
                                    return true
                                } else {
                                    //get extra arguments
                                    var args =
                                        commandString.substringAfter("${commandList.commandList["help"]} ")
                                    if (!args.startsWith(commandList.commandPrefix))
                                        args = "${commandList.commandPrefix}$args"

                                    //check if command exists
                                    return if (commandList.commandList.any { it.value.contains(args.toRegex()) }) {
                                        //print help for command
                                        printToChat(
                                            listOf(commandList.helpMessages[commandList.commandList.filterValues { it == args }.keys.first()].orEmpty())
                                        )
                                        commandListener.onCommandExecuted(
                                            commandString,
                                            commandList.helpMessages[commandList.commandList.filterValues { it == args }.keys.first()].orEmpty(),
                                            args
                                        )
                                        commandJob.complete()
                                        true
                                    } else {
                                        printToChat(
                                            listOf("Command doesn't exist! See ${commandList.commandList["help"]} for available commands.")
                                        )
                                        commandListener.onCommandExecuted(
                                            commandString,
                                            "Command doesn't exist! See ${commandList.commandList["help"]} for available commands.",
                                            args
                                        )
                                        commandJob.complete()
                                        false
                                    }
                                }
                            }

                            //queue-add and queue-playnext command
                            commandString.contains("^(${commandList.commandList["queue-add"]}|${commandList.commandList["queue-playnext"]})(\\s+-(s|(p\\s+[0-9]+)))*(\\s*(\\[URL])?((spotify:(user:\\S+:)?(track|album|playlist|show|episode|artist):\\S+)|(https?://\\S+)|((sp|spotify|yt|youtube|sc|soundcloud)\\s+(track|album|playlist|show|episode|artist|video|user)\\s+.+))(\\[/URL])?\\s*,?\\s*)+(\\s+-(s|(p\\s+[0-9]+)))*\$".toRegex()) -> {
                                var commandSuccessful = false
                                val shouldPlayNext =
                                    commandString.contains("^${commandList.commandList["queue-playnext"]}".toRegex())
                                var shouldShuffle = false
                                var hasCustomPosition = false
                                var customPosition = if (shouldPlayNext) {
                                    0
                                } else {
                                    -1
                                }
                                val links = ArrayList<Link>()
                                if (commandString.contains("\\s+(sp|spotify|yt|youtube|sc|soundcloud)\\s+\\w+\\s+.+".toRegex())) {
                                    when {
                                        commandString.contains("\\s+(sp|spotify)\\s+".toRegex()) ->
                                            spotify.searchSpotify(
                                                SearchType(
                                                    commandString.split("\\s+(sp|spotify)\\s+".toRegex()).last()
                                                        .substringBefore(" ")
                                                ),
                                                SearchQuery(
                                                    commandString.split("\\s+(sp|spotify)\\s+\\w+\\s+".toRegex()).last()
                                                ),
                                                1
                                            ).results.let {
                                                if (it.isNotEmpty())
                                                    links.add(it.first().link)
                                            }

                                        commandString.contains("\\s+(yt|youtube)\\s+".toRegex()) ->
                                            youTube.searchYoutube(
                                                SearchType(
                                                    commandString.split("\\s+(yt|youtube)\\s+".toRegex()).last()
                                                        .substringBefore(" ")
                                                ),
                                                SearchQuery(
                                                    commandString.split("\\s+(yt|youtube)\\s+\\w+\\s+".toRegex()).last()
                                                ),
                                                1
                                            ).results.let {
                                                if (it.isNotEmpty())
                                                    links.add(it.first().link)
                                            }

                                        commandString.contains("\\s+(sc|soundcloud)\\s+".toRegex()) ->
                                            soundCloud.searchSoundCloud(
                                                SearchType(
                                                    commandString.split("\\s+(sc|soundcloud)\\s+".toRegex()).last()
                                                        .substringBefore(" ")
                                                ),
                                                SearchQuery(
                                                    commandString.split("\\s+(sc|soundcloud)\\s+\\w+\\s+".toRegex())
                                                        .last()
                                                ),
                                                1
                                            ).results.let {
                                                if (it.isNotEmpty())
                                                    links.add(it.first().link)
                                            }

                                    }
                                }
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

                                        args[i].contains("((\\[URL])?(https?://(open\\.spotify\\.com|soundcloud\\.com|youtu\\.be|((m|www)\\.)?youtube\\.com))(\\[/URL])?.+)|(spotify:(track|album|playlist|show|episode|artist):.+)".toRegex()) -> {
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
                                printToChat(listOf("Please wait, fetching data..."))
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
                                                    if (track.playability.isPlayable)
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
                                                    if (episode.playability.isPlayable)
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
                                                    songQueue.addAllToQueue(trackList, customPosition)
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
                                        link.link.contains("https?://(youtu\\.be|((m|www)\\.)?youtube\\.com)".toRegex()) -> {
                                            //get link type
                                            val type = when {
                                                link.link.contains("https?://(((m|www)\\.)?youtube\\.com/watch\\?v=|youtu\\.be/\\S+)".toRegex()) -> "track"
                                                link.link.contains("https?://(((m|www)\\.)?youtube\\.com/playlist\\?list=\\S+)".toRegex()) -> "playlist"
                                                else -> ""
                                            }
                                            println("YouTube link: $link\nLink type: $type")
                                            //get track/playlist id
                                            val id =
                                                link.link.split("(((m|www)\\.)?youtube\\.com/(watch|playlist)\\?(v|list)=)|(youtu.be/)".toRegex())[1]
                                                    .substringAfter("[URL]")
                                                    .substringBefore("[/URL]")
                                                    .substringBefore("&")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = youTube.fetchVideo(Link("https://youtu.be/$id"))
                                                    if (track.playability.isPlayable)
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

                                                "playlist" -> {
                                                    //get playlist tracks
                                                    val playlistTracks = TrackList(youTube.fetchPlaylistTracks(
                                                        Link("https://www.youtube.com/playlist?list=$id")
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
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/(?!sets)(?!likes)(?!reposts)\\S+".toRegex()) -> "track"
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/sets/\\S+".toRegex()) -> "playlist"
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/likes".toRegex()) -> "likes"
                                                link.link.contains("https?://soundcloud\\.com/[a-z0-9-_]+/reposts".toRegex()) -> "reposts"
                                                else -> ""
                                            }
                                            println("SoundCloud link: $link\nLink type: $type")
                                            //check type
                                            when (type) {
                                                "track" -> {
                                                    val track = soundCloud.fetchTrack(
                                                        Link(
                                                            link.link.substringAfter("[URL]")
                                                                .substringBefore("[/URL]")
                                                                .substringBefore("?")
                                                        )
                                                    )
                                                    if (track.playability.isPlayable)
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

                                                "likes" -> {
                                                    //get likes
                                                    val likes = TrackList(
                                                        soundCloud.fetchUserLikes(parseLink(link)).trackList
                                                            .filter { it.playability.isPlayable }
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(likes.trackList.shuffled()) else likes
                                                    songQueue.addAllToQueue(trackList, customPosition)
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added user's likes to the queue.",
                                                                trackList
                                                            )
                                                            true
                                                        } else false
                                                }

                                                "reposts" -> {
                                                    val reposts = TrackList(
                                                        soundCloud.fetchUserReposts(parseLink(link)).trackList
                                                            .filter { it.playability.isPlayable }
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(reposts.trackList.shuffled()) else reposts
                                                    songQueue.addAllToQueue(trackList, customPosition)
                                                    commandSuccessful =
                                                        if (songQueue.getQueue().containsAll(trackList.trackList)) {
                                                            commandListener.onCommandExecuted(
                                                                commandString,
                                                                "Added user's reposts to the queue.",
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
                                    printToChat(listOf("Added to queue."))
                                    commandJob.complete()
                                    true
                                } else {
                                    printToChat(listOf("One or more tracks could not be added :/"))
                                    commandJob.complete()
                                    false
                                }
                            }
                            //queue-play command
                            commandString.contains("^${commandList.commandList["queue-play"]}$".toRegex()) -> {
                                if (songQueue.getQueue().isNotEmpty()) {
                                    return if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                        printToChat(
                                            listOf(
                                                "Queue is already active!\n" +
                                                        "Did you mean to type ${commandList.commandList["queue-resume"]} instead?"
                                            )
                                        )
                                        commandJob.complete()
                                        false
                                    } else {
                                        printToChat(listOf("Playing Queue."))
                                        songQueue.startQueue()
                                        commandJob.complete()
                                        true
                                    }
                                } else {
                                    printToChat(listOf("Queue is empty!"))
                                    commandJob.complete()
                                    return false
                                }
                            }
                            //queue-list command
                            commandString.contains("^${commandList.commandList["queue-list"]}(\\s+)?(.+)?".toRegex()) -> {
                                if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                    val track = songQueue.nowPlaying()
                                    val strBuilder = StringBuilder()
                                    track.artists.artists.forEach {
                                        strBuilder.append("${it.name}, ")
                                    }
                                    printToChat(
                                        listOf(
                                            if (track.linkType == LinkType.YOUTUBE) {
                                                "Currently playing:\n${track.title} : ${track.link.link}"
                                            } else {
                                                "Currently playing:\n${
                                                    strBuilder.toString().substringBeforeLast(",")
                                                } - ${track.title}" +
                                                        " : ${track.link.link}"
                                            }
                                        )
                                    )
                                }
                                printToChat(listOf("Song Queue:"))
                                val currentQueue = songQueue.getQueue()
                                when {
                                    currentQueue.isEmpty() -> printToChat(
                                        listOf("Queue is empty!")
                                    )
                                    else -> {
                                        fun formatLines(queue: List<Track>): List<String> {
                                            return queue.mapIndexed { index, track ->
                                                val strBuilder = StringBuilder()
                                                strBuilder.append("${if (index < 10) "$index: " else "$index:"} ")
                                                if (track.link.linkType() != LinkType.YOUTUBE) {
                                                    track.artists.artists.forEach { strBuilder.append("${it.name}, ") }
                                                } else {
                                                    strBuilder.append("${track.title},")
                                                }
                                                "${strBuilder.toString().substringBeforeLast(",")} " +
                                                        if (track.link.linkType() != LinkType.YOUTUBE) {
                                                            "- ${track.title} "
                                                        } else {
                                                            ""
                                                        } + ": ${track.link.link}".substringBeforeLast(",")
                                            }
                                        }

                                        val msg = StringBuilder()
                                        if (currentQueue.size <= 15) {
                                            formatLines(currentQueue).forEach { msg.appendLine(it) }
                                        } else {
                                            val commandArgs =
                                                commandString.substringAfter("${commandList.commandList["queue-list"]} ")
                                            when {
                                                commandArgs.contains("(-a|--all)|(-l|--limit)\\s+[0-9]+".toRegex()) -> {
                                                    when (commandArgs.split("\\s+".toRegex()).first()) {
                                                        "-a", "--all" -> {
                                                            formatLines(currentQueue).forEach { msg.appendLine(it) }
                                                        }
                                                        "-l", "--limit" -> {
                                                            val limit = commandArgs.split("\\s+".toRegex())
                                                                .first { it.contains("[0-9]+".toRegex()) }.toInt()
                                                            if (currentQueue.size <= limit)
                                                                formatLines(currentQueue).forEach { msg.appendLine(it) }
                                                            else
                                                                formatLines(currentQueue).subList(0, limit)
                                                                    .forEach { msg.appendLine(it) }
                                                        }
                                                    }
                                                }
                                                else -> formatLines(currentQueue).subList(0, 15).forEach {
                                                    msg.appendLine(it)
                                                }
                                            }
                                        }
                                        msg.appendLine("Queue Length: ${currentQueue.size} tracks.")
                                        printToChat(listOf(msg.toString()))
                                        commandListener.onCommandExecuted(
                                            commandString,
                                            msg.toString(),
                                            TrackList(currentQueue)
                                        )
                                        commandJob.complete()
                                        return true
                                    }
                                }
                            }
                            //queue-delete command
                            commandString.contains("^${commandList.commandList["queue-delete"]}\\s+((-a|--all)?\\s+((\\[URL])?https?://\\S+,?(\\s+)?)+(\\s+(-a|--all))?|([0-9]+,?(\\s+)?)+)".toRegex()) -> {
                                //get links from message
                                val links =
                                    if (commandString.contains("^${commandList.commandList["queue-delete"]}\\s+(-a|--all)?\\s+((\\[URL])?https?://\\S+,?(\\s+)?)+(\\s+(-a|--all))?".toRegex())) {
                                        commandString.split("(\\s+|,\\s+|,)".toRegex()).filter {
                                            it.contains("(\\[URL])?https?://\\S+,?(\\[/URL])?".toRegex())
                                        }.map { parseLink(Link(it.replace(",\\[/URL]".toRegex(), "[/URL]"))) }
                                    } else {
                                        emptyList()
                                    }
                                //get positions from message
                                val positions = if (commandString.contains("([0-9]+(,(\\s+)?)?)+".toRegex())) {
                                    commandString.split("(\\s+|,\\s+|,)".toRegex()).filter {
                                        it.contains("^[0-9]+$".toRegex())
                                    }.map { it.toInt() }.sortedDescending()
                                } else {
                                    emptyList()
                                }

                                val currentList = songQueue.getQueue()
                                //get a list of the tracks to delete
                                val tracksToDelete = currentList.filter { track -> links.contains(track.link) }
                                if (tracksToDelete.isNotEmpty()) {
                                    for (track in tracksToDelete.distinct()) {
                                        //check if there are multiple instances of the track in the queue.
                                        if (currentList.filter { it.link == track.link }.size > 1) {
                                            val duplicates = ArrayList<Int>()
                                            printToChat(
                                                listOf(
                                                    "There are multiple instances of this track:\n" +
                                                            currentList.mapIndexed { i, t ->
                                                                if (t.link == track.link)
                                                                    "$i: ${t.toShortString()}".also { duplicates.add(i) }
                                                                else
                                                                    ""
                                                            }.let {
                                                                val sb = StringBuilder()
                                                                for (item in it) {
                                                                    if (item.isNotEmpty())
                                                                        sb.appendLine(item)
                                                                }
                                                                sb.toString()
                                                            }
                                                )
                                            )
                                            if (commandString.contains("\\s+(-a|--all)(\\s+)?".toRegex())) {
                                                printToChat(
                                                    listOf(
                                                        "You used the -a/--all flag, so deleting all matches of track:\n" +
                                                                track.toShortString()
                                                    )
                                                )
                                                songQueue.deleteTracks(duplicates)
                                            } else {
                                                printToChat(
                                                    listOf(
                                                        "Select the track(s) you want to delete, then run ${commandList.commandList["queue-delete"]} with the position(s) specified, for example:\n" +
                                                                "${commandList.commandList["queue-delete"]} ${duplicates.first()}\n" +
                                                                "Or if you want to delete multiple tracks:\n" +
                                                                "${commandList.commandList["queue-delete"]} ${
                                                                    duplicates.subList(0, 2).let { trackPositions ->
                                                                        val positionsText = StringBuilder()
                                                                        for (pos in trackPositions) {
                                                                            positionsText.append("$pos, ")
                                                                        }
                                                                        positionsText.toString()
                                                                            .substringBeforeLast(",")
                                                                    }
                                                                }"
                                                    )
                                                )
                                            }
                                        } else {
                                            //no duplicates found, delete the track
                                            printToChat(
                                                listOf("Deleting track:\n${track.toShortString()}")
                                            )
                                            songQueue.deleteTrack(track)
                                        }
                                    }
                                } else {
                                    printToChat(listOf("No matches found in the queue!"))
                                }

                                //delete tracks at specified positions
                                if (positions.isNotEmpty()) {
                                    printToChat(
                                        listOf("Deleting track${if (positions.size > 1) "s" else ""}.")
                                    )
                                    songQueue.deleteTracks(positions)
                                }
                            }
                            //queue-clear command
                            commandString.contains("^${commandList.commandList["queue-clear"]}$".toRegex()) -> {
                                songQueue.clearQueue()
                                return if (songQueue.getQueue().isEmpty()) {
                                    printToChat(listOf("Cleared the queue."))
                                    commandListener.onCommandExecuted(commandString, "Cleared the queue.")
                                    commandJob.complete()
                                    true
                                } else {
                                    printToChat(listOf("Could not clear the queue!"))
                                    commandListener.onCommandExecuted(commandString, "Could not clear the queue!")
                                    commandJob.complete()
                                    false
                                }
                            }
                            //queue-shuffle command
                            commandString.contains("^${commandList.commandList["queue-shuffle"]}$".toRegex()) -> {
                                songQueue.shuffleQueue()
                                printToChat(listOf("Shuffled the queue."))
                                commandJob.complete()
                                return true
                            }
                            //queue-skip command
                            commandString.contains("^${commandList.commandList["queue-skip"]}$".toRegex()) -> {
                                voteSkipUsers.clear()
                                songQueue.skipSong()
                                commandJob.complete()
                                return true
                            }
                            //queue-voteskip command
                            commandString.contains("^${commandList.commandList["queue-voteskip"]}$".toRegex()) -> {
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
                                                if (latestMsgUsername == user) {
                                                    newList.add(Pair(user, true))
                                                } else {
                                                    newList.add(Pair(user, voteSkipUser.second))
                                                }
                                            }
                                        }
                                        if (currentList.isEmpty()) {
                                            newList.add(Pair(user, latestMsgUsername == user))
                                        }
                                    }
                                }
                                voteSkipUsers.addAll(newList)
                                if (voteSkipUsers.any { !it.second }) {
                                    printToChat(
                                        listOf("\nAll users have not voted yet.\nWaiting for more votes...")
                                    )
                                } else {
                                    printToChat(listOf("Skipping current song."))
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
                            //queue-move command
                            commandString.contains("^${commandList.commandList["queue-move"]}\\s+".toRegex()) -> {
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
                                            listOf("lol u think arrays start at 1?")
                                        )
                                        commandJob.complete()
                                        return false
                                    }

                                    position < 0 -> {
                                        printToChat(
                                            listOf("What were you thinking?", "You can't do that.")
                                        )
                                        commandJob.complete()
                                        return false
                                    }

                                    else -> {
                                        songQueue.moveTrack(Track(link = link), position)
                                        return if (songQueue.getQueue()[position].link == link) {
                                            printToChat(
                                                listOf("Moved track to new position.")
                                            )
                                            commandListener.onCommandExecuted(
                                                commandString,
                                                "Moved track to new position."
                                            )
                                            commandJob.complete()
                                            true
                                        } else {
                                            printToChat(
                                                listOf("Couldn't move track to new position.")
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
                            //queue-stop command
                            commandString.contains("^${commandList.commandList["queue-stop"]}$".toRegex()) -> {
                                songQueue.stopQueue()
                                printToChat(listOf("Stopped the queue."))
                                commandJob.complete()
                                return true
                            }
                            //queue-status command
                            commandString.contains("^${commandList.commandList["queue-status"]}$".toRegex()) -> {
                                val statusMessage = StringBuilder()
                                statusMessage.append("Queue Status: ")
                                var stateKnown = false
                                when (songQueue.getState()) {
                                    SongQueue.State.QUEUE_PLAYING -> statusMessage.appendLine("Playing")
                                        .also { stateKnown = true }
                                    SongQueue.State.QUEUE_PAUSED -> statusMessage.appendLine("Paused")
                                        .also { stateKnown = true }
                                    SongQueue.State.QUEUE_STOPPED -> statusMessage.appendLine("Stopped")
                                        .also { stateKnown = true }
                                }
                                printToChat(statusMessage.toString().lines())
                                commandListener.onCommandExecuted(commandString, statusMessage.toString())
                                commandJob.complete()
                                return stateKnown
                            }
                            //queue-nowplaying command
                            commandString.contains("^${commandList.commandList["queue-nowplaying"]}$".toRegex()) -> {
                                if (songQueue.nowPlaying().title.name.isNotEmpty()) {
                                    val currentTrack = songQueue.nowPlaying()
                                    val messageLines = StringBuilder()
                                    messageLines.appendLine("Now playing:")
                                    if (currentTrack.album.name.name.isNotEmpty())
                                        messageLines.appendLine("Album Name:  \t${currentTrack.album.name}")
                                    if (currentTrack.album.link.link.isNotEmpty())
                                        messageLines.appendLine("Album Link:  \t\t${currentTrack.album.link}")
                                    if (currentTrack.link.link.contains("(youtube|youtu\\.be|soundcloud)".toRegex()))
                                        messageLines.appendLine("Upload Date:   \t${currentTrack.album.releaseDate.date}")
                                    else
                                        messageLines.appendLine("Release:     \t\t\t${currentTrack.album.releaseDate.date}")

                                    if (currentTrack.artists.artists.isNotEmpty()) {
                                        if (currentTrack.link.link.contains("(youtube|youtu\\.be|soundcloud\\.com)".toRegex()))
                                            messageLines.appendLine("Uploader: \t\t\t${currentTrack.artists.artists[0].name}")
                                        else
                                            messageLines.appendLine("Artists:\n${currentTrack.artists}")
                                    }
                                    messageLines.appendLine("Track Title:   \t\t${currentTrack.title}")
                                    messageLines.appendLine("Track Link:    \t\t${currentTrack.link}")
                                    printToChat(listOf(messageLines.toString()))
                                } else {
                                    printToChat(listOf("No song playing!"))
                                }
                                commandJob.complete()
                                return true
                            }
                            //queue-pause command
                            commandString.contains("^${commandList.commandList["queue-pause"]}$".toRegex()) -> {
                                songQueue.pausePlayback()
                                commandJob.complete()
                                return true
                            }
                            //queue-resume command
                            commandString.contains("^${commandList.commandList["queue-resume"]}$".toRegex()) -> {
                                songQueue.resumePlayback()
                                commandJob.complete()
                                return true
                            }
                            //sp-search/yt-search/sc-search command
                            commandString.contains("^(${commandList.commandList["sp-search"]}|${commandList.commandList["yt-search"]}|${commandList.commandList["sc-search"]})\\s+".toRegex()) -> {
                                val service = when (commandString.substringBefore(" ")) {
                                    commandList.commandList["sp-search"] -> "sp"
                                    commandList.commandList["yt-search"] -> "yt"
                                    commandList.commandList["sc-search"] -> "sc"
                                    else -> ""
                                }
                                val searchType =
                                    SearchType(commandString.substringAfter(" ").substringBefore(" "))
                                if (searchType.getType() != SearchType.Type.OTHER && when (service) {
                                        "sp" -> spotify.supportedSearchTypes.contains(searchType.getType())
                                        "yt" -> youTube.supportedSearchTypes.contains(searchType.getType())
                                        "sc" -> soundCloud.supportedSearchTypes.contains(searchType.getType())
                                        else -> false
                                    }
                                ) {
                                    val limit = if (commandString.contains("(-l|--limit)\\s+[0-9]+".toRegex())) {
                                        commandString.split("\\s+(-l|--limit)\\s+".toRegex()).last()
                                            .substringBefore(" ").toInt()
                                    } else {
                                        10
                                    }
                                    val searchQuery = SearchQuery(
                                        commandString.substringAfter("$searchType ")
                                            .replace("(-l|--limit)\\s+[0-9]+".toRegex(), "")
                                    )
                                    printToChat(listOf("Searching, please wait..."))
                                    val results = when (service) {
                                        "sp" -> spotify.searchSpotify(searchType, searchQuery, limit)
                                        "yt" -> youTube.searchYoutube(searchType, searchQuery, limit)
                                        "sc" -> soundCloud.searchSoundCloud(searchType, searchQuery, limit)
                                        else -> SearchResults(emptyList())
                                    }
                                    return if (results.isNotEmpty()) {
                                        val searchResults = ArrayList<SearchResult>()
                                        results.results.forEach { searchResults.add(it) }
                                        printToChat(
                                            listOf("\n${SearchResults(searchResults)}")
                                        )
                                        commandListener.onCommandExecuted(commandString, results.toString(), results)
                                        commandJob.complete()
                                        true
                                    } else {
                                        printToChat(listOf("No results found!"))
                                        commandListener.onCommandExecuted(commandString, results.toString(), results)
                                        commandJob.complete()
                                        false
                                    }
                                } else {
                                    printToChat(
                                        listOf("Search type not supported! Run ${commandList.commandList["help"]} ${commandList.commandList["$service-search"]} to see more information on this command.")
                                    )
                                    commandListener.onCommandExecuted(commandString, "Not supported", searchType)
                                    commandJob.complete()
                                    return false
                                }
                            }

                            //sp-info, yt-info and sc-info command
                            commandString.contains("^(${commandList.commandList["sp-info"]}|${commandList.commandList["yt-info"]}|${commandList.commandList["sc-info"]})\\s+".toRegex()) -> {
                                val supportedServices = listOf("sp", "yt", "sc")
                                val service = when (commandString.substringBefore(" ")) {
                                    commandList.commandList["sp-info"] -> "sp"
                                    commandList.commandList["yt-info"] -> "yt"
                                    commandList.commandList["sc-info"] -> "sc"
                                    else -> ""
                                }
                                if (supportedServices.contains(service)) {
                                    val link = parseLink(Link(commandString.substringAfter(" ")))
                                    var data: Any? = null
                                    when (service) {
                                        "sp" -> {
                                            data = when {
                                                link.link.contains("track".toRegex()) -> spotify.getTrack(link)
                                                link.link.contains("album".toRegex()) -> spotify.getAlbum(link)
                                                link.link.contains("playlist".toRegex()) -> spotify.getPlaylist(link)
                                                link.link.contains("artist".toRegex()) -> spotify.getArtist(link)
                                                link.link.contains("show".toRegex()) -> spotify.getShow(link)
                                                link.link.contains("episode".toRegex()) -> spotify.getEpisode(link)
                                                link.link.contains("user".toRegex()) -> spotify.getUser(link)
                                                else -> null
                                            }
                                        }
                                        "yt" -> {
                                            data = when (youTube.resolveType(link)) {
                                                "video" -> youTube.fetchVideo(link)
                                                "playlist" -> youTube.fetchPlaylist(link)
                                                "channel" -> youTube.fetchChannel(link)
                                                else -> null
                                            }
                                        }
                                        "sc" -> {
                                            data = when (soundCloud.resolveType(link)) {
                                                "track" -> soundCloud.fetchTrack(link)
                                                "album" -> soundCloud.fetchAlbum(link)
                                                "playlist" -> soundCloud.getPlaylist(link)
                                                "artist" -> soundCloud.fetchArtist(link)
                                                "user" -> soundCloud.fetchUser(link)
                                                else -> null
                                            }
                                        }
                                    }
                                    return if (data != null) {
                                        printToChat(listOf("\n$data"))
                                        commandListener.onCommandExecuted(commandString, data.toString(), data)
                                        commandJob.complete()
                                        true
                                    } else {
                                        printToChat(
                                            listOf("You have to provide a Spotify link or URI to a track!")
                                        )
                                        commandListener.onCommandExecuted(commandString, data.toString(), data)
                                        commandJob.complete()
                                        false
                                    }
                                } else {
                                    printToChat(
                                        listOf(
                                            "This service isn't supported, available commands are:\n" +
                                                    "${supportedServices.map { "${commandList.commandList["$it-info"]}" }}"
                                        )
                                    )
                                    commandListener.onCommandExecuted(commandString, "Not supported")
                                    commandJob.complete()
                                    return false
                                }
                            }

                            //sp-pause command
                            commandString.contains("^${commandList.commandList["sp-pause"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer pause && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-resume & sp-play command
                            commandString.contains("^(${commandList.commandList["sp-resume"]}|${commandList.commandList["sp-play"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer play && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-stop command
                            //Stop spotify playback
                            commandString.contains("^${commandList.commandList["sp-stop"]}$".toRegex()) -> {
                                if (spotifyPlayer == "ncspot")
                                    commandRunner.runCommand("playerctl -p ncspot stop; tmux kill-session -t ncspot")
                                else
                                    commandRunner.runCommand("playerctl -p $spotifyPlayer pause")
                                commandJob.complete()
                                return true
                            }
                            //sp-skip & sp-next command
                            commandString.contains("^(${commandList.commandList["sp-skip"]}|${commandList.commandList["sp-next"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer next && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-prev command
                            commandString.contains("^${commandList.commandList["sp-prev"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p $spotifyPlayer previous && sleep 0.1 & playerctl -p $spotifyPlayer previous")
                                commandJob.complete()
                                return true
                            }
                            //sp-playsong command
                            //Play Spotify song based on link or URI
                            commandString.contains("^${commandList.commandList["sp-playsong"]}\\s+".toRegex()) -> {
                                if (message.substringAfter("${commandList.commandList["sp-playsong"]}")
                                        .isNotEmpty()
                                ) {
                                    //start ncspot if necessary
                                    if (spotifyPlayer == "ncspot") {
                                        if (commandRunner.runCommand(
                                                "ps aux | grep ncspot | grep -v grep",
                                                printOutput = false
                                            ).first.outputText.isEmpty()
                                        ) {
                                            println("Starting ncspot.")
                                            commandRunner.runCommand(
                                                "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                                                ignoreOutput = true,
                                                printCommand = true
                                            )
                                            while (commandRunner.runCommand(
                                                    "playerctl -p ncspot status",
                                                    printOutput = false,
                                                    printErrors = false
                                                ).first.outputText != "Stopped"
                                            ) {
                                                //wait for ncspot to start
                                                println("Waiting for ncspot to start...")
                                                delay(500)
                                            }
                                        }
                                    }
                                    println("Playing song...")
                                    if (
                                        parseLink(Link(commandString.substringAfter("${commandList.commandList["sp-playsong"]} "))).link
                                            .startsWith("https://open.spotify.com/track")
                                    ) {
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
                                    printToChat(listOf("Error! Please provide a song to play!"))
                                    commandJob.complete()
                                    return false
                                }
                            }
                            //sp-playlist command
                            //Play Spotify playlist based on link or URI
                            commandString.contains("^${commandList.commandList["sp-playlist"]}\\s+".toRegex()) -> {
                                //start ncspot if necessary
                                if (spotifyPlayer == "ncspot") {
                                    if (commandRunner.runCommand(
                                            "ps aux | grep ncspot | grep -v grep",
                                            printOutput = false
                                        ).first.outputText.isEmpty()
                                    ) {
                                        println("Starting ncspot.")
                                        commandRunner.runCommand(
                                            "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                        while (commandRunner.runCommand(
                                                "playerctl -p ncspot status",
                                                printOutput = false,
                                                printErrors = false
                                            ).first.outputText != "Stopped"
                                        ) {
                                            //wait for ncspot to start
                                            println("Waiting for ncspot to start...")
                                            delay(500)
                                        }
                                    }
                                }
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
                                } else if (parseLink(Link(message.substringAfter(" "))).link.startsWith("https://open.spotify.com/")) {
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
                            //sp-playalbum command
                            commandString.contains("^${commandList.commandList["sp-playalbum"]}\\s+".toRegex()) -> {
                                //start ncspot if necessary
                                if (spotifyPlayer == "ncspot") {
                                    if (commandRunner.runCommand(
                                            "ps aux | grep ncspot | grep -v grep",
                                            printOutput = false
                                        ).first.outputText.isEmpty()
                                    ) {
                                        println("Starting ncspot.")
                                        commandRunner.runCommand(
                                            "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                        while (commandRunner.runCommand(
                                                "playerctl -p ncspot status",
                                                printOutput = false,
                                                printErrors = false
                                            ).first.outputText != "Stopped"
                                        ) {
                                            //wait for ncspot to start
                                            println("Waiting for ncspot to start...")
                                            delay(500)
                                        }
                                    }
                                }
                                if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:album:${
                                            message.substringAfter("album:")
                                        }"
                                    )
                                } else if (parseLink(Link(message.substringAfter(" "))).link.startsWith("https://open.spotify.com/")) {
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
                            //sp-nowplaying command
                            commandString.contains("^${commandList.commandList["sp-nowplaying"]}$".toRegex()) -> {
                                val lines = StringBuilder()
                                lines.appendLine("Now playing on Spotify:")
                                val nowPlaying = commandRunner.runCommand(
                                    "playerctl -p $spotifyPlayer metadata",
                                    printOutput = false
                                )
                                    .first.outputText.lines()
                                for (line in nowPlaying) {
                                    when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]) {
                                        "album" -> lines.appendLine(
                                            "Album:\t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "artist" -> lines.appendLine(
                                            "Artist:   \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "title" -> lines.appendLine(
                                            "Title:    \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                        "url" -> lines.appendLine(
                                            "Link:  \t${
                                                line.substringAfter(
                                                    line.substringAfter("xesam:")
                                                        .split("\\s+".toRegex())[0]
                                                )
                                            }"
                                        )
                                    }
                                }
                                printToChat(listOf(lines.toString()))
                                commandJob.complete()
                                return true
                            }

                            //yt-pause command
                            commandString.contains("^${commandList.commandList["yt-pause"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv pause")
                                commandJob.complete()
                                return true
                            }
                            //yt-resume and yt-play commands
                            commandString.contains("^(${commandList.commandList["yt-resume"]}|${commandList.commandList["yt-play"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv play")
                                commandJob.complete()
                                return true
                            }
                            //yt-stop command
                            commandString.contains("^${commandList.commandList["yt-stop"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv stop")
                                commandJob.complete()
                                return true
                            }
                            //yt-playsong command
                            commandString.contains("^${commandList.commandList["yt-playsong"]}\\s+".toRegex()) -> {
                                val ytLink = parseLink(Link(message.substringAfter(" ")))
                                if (ytLink.link.isNotEmpty()) {
                                    launch {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video" +
                                                    " --ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0," +
                                                    "cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" +
                                                    " --ytdl \"$ytLink\" --volume=$mpvVolume",
                                            inheritIO = true,
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                    }
                                    commandListener.onCommandExecuted(commandString, "Playing song", ytLink)
                                    commandJob.complete()
                                    return true
                                } else {
                                    commandListener.onCommandExecuted(commandString, "Couldn't play song", ytLink)
                                    commandJob.complete()
                                    return false
                                }
                            }
                            //yt-nowplaying command
                            commandString.contains("^${commandList.commandList["yt-nowplaying"]}$".toRegex()) -> {
                                printToChat(
                                    listOf(
                                        "Now playing on YouTube:\n" +
                                                youTube.fetchVideo(
                                                    Link(
                                                        commandRunner.runCommand(
                                                            "playerctl -p mpv metadata --format '{{ xesam:url }}'",
                                                            printOutput = false
                                                        ).first.outputText
                                                    )
                                                )
                                    )
                                )
                                commandJob.complete()
                                return true
                            }

                            //sc-pause command
                            commandString.contains("^${commandList.commandList["sc-pause"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv pause")
                                commandJob.complete()
                                return true
                            }
                            //sc-resume and sc-play commands
                            commandString.contains("^(${commandList.commandList["sc-resume"]}|${commandList.commandList["sc-play"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv play")
                                commandJob.complete()
                                return true
                            }
                            //sc-stop command
                            commandString.contains("^${commandList.commandList["sc-stop"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p mpv stop")
                                commandJob.complete()
                                return true
                            }
                            //sc-playsong command
                            commandString.contains("^${commandList.commandList["sc-playsong"]}\\s+".toRegex()) -> {
                                val scLink = parseLink(Link(message.substringAfter(" ")))
                                if (scLink.link.isNotEmpty()) {
                                    launch {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video --ytdl \"$scLink\" --volume=$mpvVolume",
                                            inheritIO = true,
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                    }
                                    commandListener.onCommandExecuted(commandString, "Playing song", scLink)
                                    commandJob.complete()
                                    return true
                                } else {
                                    commandListener.onCommandExecuted(commandString, "Couldn't play song", scLink)
                                    commandJob.complete()
                                    return false
                                }

                            }
                            //sc-nowplaying command
                            commandString.contains("^${commandList.commandList["sc-nowplaying"]}$".toRegex()) -> {
                                printToChat(
                                    listOf(
                                        "Now playing on SoundCloud:\n" +
                                                soundCloud.fetchTrack(Link(commandRunner.runCommand("playerctl -p mpv metadata --format '{{ xesam:url }}'").first.outputText))
                                    )
                                )
                                commandJob.complete()
                                return true
                            }

                            else -> {
                                commandJob.complete()
                                return false
                            }

                        }
                    } else {
                        //if userName is set to __console__, allow the usage of %say command
                        if (latestMsgUsername == "__console__") {
                            when {
                                //send a message to the chat
                                message.contains("^${commandList.commandPrefix}say(\\s+\\S+)+$".toRegex()) -> {
                                    printToChat(
                                        listOf(message.substringAfter("${commandList.commandPrefix}say "))
                                    )
                                    commandJob.complete()
                                    return true
                                }
                            }
                        } else {
                            printToChat(
                                listOf("Command not found! Try ${commandList.commandList["help"]} to see available commands.")
                            )
                            commandJob.complete()
                            return false
                        }
                    }
                    commandJob.complete()
                    return false
                }

                //check for commands in message and add to list
                val commands = ArrayList<String>()
                commands.addAll(
                    "${message.replace(";\\[/URL]".toRegex(), "[/URL];")};"
                        .split("(\\s*)?;+\\s*".toRegex())
                )
                //loop through command list
                for (command in commands) {
                    when {
                        //check if command contains "&&" and a command following that
                        command.contains("\\s+&{2}\\s+${commandList.commandPrefix}.+(-?.+)".toRegex()) -> {
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

    //use TeamSpeak ClientQuery to send message(s)
    private fun printToChat(messages: List<String>) {
        /**
         * Send a message to the current TeamSpeak channel's chat
         * @param message The message that should be sent. Max length is 8192 chars!
         */
        fun sendTeamSpeakMessage(message: String) {
            val command = "(echo auth apikey=$apikey; " +
                    "echo \"sendtextmessage targetmode=2 msg=$message\"; " +
                    "echo quit) | nc localhost 25639"
            commandRunner.runCommand(command, printOutput = false)
        }
        if (latestMsgUsername == "__console__") {
            messages.forEach { println(it) }
        } else {
            if (apikey.isNotEmpty()) {
                messages.forEach { message ->
                    //TeamSpeak's character limit is 8192 per message
                    val tsCharLimit = 8192

                    //adds correct escaping to messages
                    fun addEscaping(msg: String): String {
                        val distro = commandRunner.runCommand("cat /etc/issue", printOutput = false).first.outputText
                        return when {
                            distro.contains("Ubuntu".toRegex()) -> {
                                msg.replace(" ", "\\\\\\s")
                                    .replace("\n", "\\\\\\n")
                                    .replace("/", "\\/")
                                    .replace("|", "\\\\p")
                                    .replace("'", "\\\\'")
                                    .replace("\"", "\\\"")
                                    .replace("`", "\\`")
                                    .replace("&quot;", "\\\"")
                                    .replace("$", "\\\\$")
                            }

                            else -> {
                                msg.replace(" ", "\\s")
                                    .replace("\n", "\\n")
                                    .replace("/", "\\/")
                                    .replace("|", "\\p")
                                    .replace("'", "\\'")
                                    .replace("\"", "\\\"")
                                    .replace("`", "\\`")
                                    .replace("&quot;", "\\\"")
                                    .replace("$", "\\$")
                            }
                        }
                    }

                    //splits the message in to size of tsCharLimit at most
                    //and then returns the result as a pair. First item contains the
                    //correctly sized message, and the second part contains anything that is left over.
                    fun splitMessage(msg: String): Pair<String, String> {
                        val escapedLength = addEscaping(
                            msg.substring(
                                0,
                                if (msg.length > tsCharLimit)
                                    tsCharLimit + 1
                                else
                                    msg.lastIndex + 1
                            )
                        ).length
                        val charLimit = if (escapedLength <= tsCharLimit) msg.length else {
                            //address the extended message length that the escape characters add
                            val compensation = escapedLength - msg.substring(
                                0,
                                if (msg.length > tsCharLimit)
                                    tsCharLimit
                                else
                                    msg.lastIndex
                            ).length
                            //first split the message in to a size that fits tsCharLimit
                            msg.substring(0, tsCharLimit - compensation + 1).let { str ->
                                //find index where to split the string.
                                //Only check at most 10 last lines from the message.
                                str.lastIndexOf(
                                    "\n\n",
                                    "\\n".toRegex().findAll(str).map { it.range.first }.toList().let {
                                        if (it.size < 10 && str.length < tsCharLimit - compensation + 1) 0
                                        else it.reversed()[9]
                                    }
                                ).let { index ->
                                    if (index == -1) {
                                        //no empty lines were found so find the last newline character
                                        //and use that as the index
                                        str.let { text ->
                                            text.indexOfLast { it == '\n' }.let { lastNewline ->
                                                if (lastNewline == -1) {
                                                    //if no newlines are found, use the last space
                                                    text.indexOfLast { it == ' ' }.let {
                                                        if (it == -1) tsCharLimit else it
                                                    }
                                                } else lastNewline
                                            }
                                        }
                                    } else {
                                        index + 1
                                    }
                                }
                            }
                        }
                        return Pair(
                            addEscaping(msg.substring(0, charLimit)),
                            if (escapedLength > tsCharLimit)
                                msg.substring(charLimit, msg.lastIndex + 1)
                            else
                                ""
                        )
                    }

                    var msg = message
                    while (true) {
                        //add an empty line to the start of the message if there isn't one already.
                        if ((msg.count { msg.contains('\n') }) > 1 && !msg.startsWith("\n"))
                            msg = "\n$msg"
                        val split = splitMessage(msg)
                        sendTeamSpeakMessage(split.first)
                        val escapedLength = addEscaping(
                            split.second.substring(
                                0,
                                if (split.second.length > tsCharLimit)
                                    tsCharLimit + 1
                                else
                                    split.second.lastIndex + 1
                            )
                        ).length
                        if (escapedLength > tsCharLimit) {
                            msg = split.second
                        } else {
                            if (split.second.isNotEmpty()) {
                                sendTeamSpeakMessage(addEscaping(split.second))
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private suspend fun chatUpdated(line: String) {
        when (chatFile.extension) {
            "html" -> {
                //extract message
                latestMsgUsername = line.split("client://".toRegex())[1].split("&quot;".toRegex())[1]
                val time = Time(Calendar.getInstance())
                val rawTime = line.split("TextMessage_Time\">&lt;".toRegex())[1]
                    .split("&gt;</span>".toRegex())[0]
                    .split(":".toRegex())
                time.hour = rawTime[0]
                time.minute = rawTime[1]
                time.second = rawTime[2]

                val userMessage = line.split("TextMessage_Text\">".toRegex())[1].split("</span>".toRegex())[0]
                parseLine(userMessage)
                withContext(Main) {
                    onChatUpdateListener.onChatUpdated(ChatUpdate(latestMsgUsername, userMessage))
                }
            }

            "txt" -> {
                //extract message
                if (line.startsWith("<")) {
                    latestMsgUsername = line.substringAfter("> ").substringBeforeLast(": ")
                    val time = Time(Calendar.getInstance())
                    val rawTime =
                        line.split(" ".toRegex())[0].substringAfter("<").substringBefore(">").split(":".toRegex())
                    time.hour = rawTime[0]
                    time.minute = rawTime[1]
                    time.second = rawTime[2]

                    val userMessage = line.substringAfter("$latestMsgUsername: ")
                    parseLine(userMessage)
                    withContext(Main) {
                        onChatUpdateListener.onChatUpdated(ChatUpdate(latestMsgUsername, userMessage))
                    }
                }
            }

            else -> {
                println("Error! file format \"${chatFile.extension}\" not supported!")
            }
        }
    }

    override fun onTrackEnded(player: String, track: Track) {
        voteSkipUsers.clear()
    }

    override fun onTrackPaused(player: String, track: Track) {}
    override fun onTrackResumed(player: String, track: Track) {}

    override fun onTrackStarted(player: String, track: Track) {
        when (player) {
            "spotify", "ncspot", "spotifyd", "mpv" -> {
                latestMsgUsername = "__song_queue__"
                parseLine("${commandList.commandList["queue-nowplaying"]}")
                println("Now playing:\n$track")
            }
        }
    }

    override fun onTrackStopped(player: String, track: Track) {}

    override fun onAdPlaying() {
        latestMsgUsername = "__song_queue__"
        printToChat(listOf("\nAd playing."))
    }

}

class ChatUpdate(val userName: String, val message: String)

interface ChatUpdateListener {
    fun onChatUpdated(update: ChatUpdate)
}

interface CommandListener {
    fun onCommandExecuted(command: String, output: String, extra: Any? = null)
}
