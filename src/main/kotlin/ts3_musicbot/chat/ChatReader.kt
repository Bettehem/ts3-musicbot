package ts3_musicbot.chat

import com.github.manevolent.ts3j.event.TS3Listener
import com.github.manevolent.ts3j.event.TextMessageEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.client.TeamSpeak
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
import ts3_musicbot.util.*
import java.util.*

class ChatReader(
    private val client: Any,
    private val botSettings: BotSettings,
    private var onChatUpdateListener: ChatUpdateListener,
    var commandListener: CommandListener,
    private val commandList: CommandList = CommandList()
) : PlayStateListener {

    private var shouldRead = false
    private val commandRunner = CommandRunner()
    private val spotify = Spotify(botSettings.market)
    private val youTube = YouTube()
    private val soundCloud = SoundCloud()
    private var voteSkipUsers = ArrayList<Pair<String, Boolean>>()
    var latestMsgUsername = ""

    @Volatile
    private var songQueue = SongQueue(botSettings.spotifyPlayer, botSettings.mpvVolume, this)

    init {
        //initialise spotify token
        CoroutineScope(IO).launch {
            spotify.updateToken()
        }
    }

    /**
     * Removes the URL tags and commas from the given String
     * @param text The text to remove url tags from
     * @return Return parsed text or original if parsing is unsuccessful
     */
    private fun removeTags(text: String): String {
        return when (client) {
            is OfficialTSClient -> {
                when (client.channelFile.extension) {
                    "txt" -> {
                        text.replace("(\\[/?URL]|,(\$|\\s))".toRegex(), "")
                    }

                    else -> {
                        println("This extension isn't supported! Use channel.txt as the chat file.")
                        text
                    }
                }
            }

            else -> text
        }
    }

    /**
     * Starts reading the chat
     */
    fun startReading(): Boolean {
        return when (client) {
            is TeamSpeak -> {
                client.addListener(object : TS3Listener {
                    override fun onTextMessage(e: TextMessageEvent?) {
                        CoroutineScope(IO).launch {
                            e?.let { chatUpdated(it.message, it.invokerName) }
                        }
                    }
                })
                true
            }

            is OfficialTSClient -> {
                shouldRead = true
                val channelFile = client.channelFile
                return if (channelFile.isFile) {
                    CoroutineScope(IO).launch {
                        var currentLine = ""
                        while (shouldRead) {
                            val last = channelFile.readLines().last()
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

            else -> false
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
                suspend fun startSpotifyPlayer() {
                    fun killCommand() = when (botSettings.spotifyPlayer) {
                        "spotify" -> commandRunner.runCommand(
                            "pkill -9 spotify",
                            ignoreOutput = true
                        )

                        "ncspot" -> commandRunner.runCommand(
                            "playerctl -p ncspot stop; tmux kill-session -t ncspot",
                            ignoreOutput = true
                        )

                        "spotifyd" -> commandRunner.runCommand("echo \"spotifyd isn't well supported yet, please kill it manually.\"")
                        else -> commandRunner.runCommand("echo \"${botSettings.spotifyPlayer} is not a supported player!\" > /dev/stderr; return 2")
                    }

                    fun startCommand() = when (botSettings.spotifyPlayer) {
                        "spotify" -> commandRunner.runCommand(
                            "${botSettings.spotifyPlayer} &",
                            ignoreOutput = true,
                            printCommand = true,
                            inheritIO = true
                        )

                        "ncspot" -> commandRunner.runCommand(
                            "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                            ignoreOutput = true,
                            printCommand = true
                        )

                        "spotifyd" -> commandRunner.runCommand("echo \"spotifyd isn't well supported yet, please start it manually.\"")
                        else -> commandRunner.runCommand("echo \"${botSettings.spotifyPlayer} is not a supported player!\" > /dev/stderr; return 2")
                    }

                    fun checkProcess() = commandRunner.runCommand(
                        "ps aux | grep ${botSettings.spotifyPlayer} | grep -v grep",
                        printOutput = false
                    )

                    if (checkProcess().first.outputText.isEmpty())
                        startCommand()
                    //sometimes the spotify player has problems starting, so ensure it actually starts.
                    while (checkProcess().first.outputText.isEmpty()) {
                        delay(7000)
                        if (checkProcess().first.outputText.isEmpty()) {
                            repeat(2) { killCommand() }
                            delay(500)
                            startCommand()
                            delay(2000)
                        }
                    }
                    //wait for the spotify player to start.
                    while (commandRunner.runCommand(
                            "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${botSettings.spotifyPlayer}(\\s+\\S+)?$\" | grep -v \"grep\"",
                            printOutput = false
                        ).first.outputText.isEmpty()
                    ) {
                        //do nothing
                        println("Waiting for ${botSettings.spotifyPlayer} to start")
                        delay(10)
                    }
                    delay(5000)
                }

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
                            commandString.contains("^(${commandList.commandList["queue-add"]}|${commandList.commandList["queue-playnext"]})(\\s+-(s|(p\\s*[0-9]+)))*(\\s*(\\[URL])?((spotify:(user:\\S+:)?(track|album|playlist|show|episode|artist):\\S+)|(https?://\\S+)|((sp|spotify|yt|youtube|sc|soundcloud)\\s+(track|album|playlist|show|episode|artist|video|user)\\s+.+))(\\[/URL])?\\s*,?\\s*)+(\\s+-(s|(p\\s*[0-9]+)))*\$".toRegex()) -> {
                                val trackAddedMsg = "Added track to queue."
                                val trackNotPlayableMsg = "Track is not playable."
                                val tracksAddedMsg = "Added tracks to queue."
                                val tracksAddingErrorMsg = "One or more tracks couldn't be added to the queue."
                                val someTracksNotPlayableMsg =
                                    "Some tracks aren't playable in this list, so they won't be added."
                                val considerDeletingTracksMsg =
                                    "Consider removing these unplayable tracks from your list\n%s"
                                //                                isSuccessful,     reason,   extraData
                                val commandSuccessful = ArrayList<Pair<Boolean, Pair<String, Any?>>>()
                                val shouldPlayNext =
                                    commandString.contains("^${commandList.commandList["queue-playnext"]}".toRegex())
                                var shouldShuffle = false
                                var hasCustomPosition = false
                                var customPosition = if (shouldPlayNext) 0 else null
                                val links = ArrayList<Link>()

                                /**
                                 * Filter out unplayable tracks from a TrackList
                                 * and inform user if unplayable tracks are found.
                                 * @param trackList list to filter
                                 * @return returns a list with only playable tracks
                                 */
                                fun filterList(trackList: TrackList, playlistLink: Link): TrackList {
                                    trackList.trackList.filterNot { it.playability.isPlayable }.let {
                                        if (it.isNotEmpty()) {
                                            commandListener.onCommandProgress(
                                                commandString,
                                                trackNotPlayableMsg,
                                                TrackList(it)
                                            )
                                            printToChat(
                                                listOf(
                                                    someTracksNotPlayableMsg,
                                                    String.format(
                                                        considerDeletingTracksMsg,
                                                        playlistLink
                                                    ),
                                                    TrackList(it).toString()
                                                )
                                            )
                                        }
                                    }
                                    return TrackList(trackList.trackList.filter { it.playability.isPlayable })
                                }

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
                                            youTube.searchYouTube(
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

                                        args[i].contains("((\\[URL])?((https?://)?(open\\.spotify\\.com|soundcloud\\.com|((m|www)\\.)?youtu\\.?be(\\.com)?)).+(\\[/URL])?)|(spotify:(track|album|playlist|show|episode|artist):.+)".toRegex()) -> {
                                            //add links to ArrayList
                                            if (args[i].contains(",\\s*".toRegex()))
                                                links.addAll(
                                                    args[i].split(",\\s*".toRegex()).map { Link(removeTags(it)) })
                                            else
                                                links.add(Link(removeTags(args[i])))
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
                                        link.link.contains("((https?://)?open\\.spotify\\.com/)|(spotify:(user:\\S+:)?(album|track|playlist|show|episode|artist):.+)".toRegex()) -> {
                                            //get link type
                                            val type = link.linkType(spotify)
                                            val id = link.getId()
                                            println("Spotify link: $link\nLink type: $type\nLink id: $id")
                                            //check type
                                            when (type) {
                                                LinkType.TRACK -> {
                                                    val track = spotify.fetchTrack(
                                                        Link("https://open.spotify.com/track/$id", id)
                                                    )
                                                    val trackAdded =
                                                        if (track.playability.isPlayable)
                                                            songQueue.addToQueue(track, customPosition)
                                                        else false
                                                    val msg = if (trackAdded) trackAddedMsg else trackNotPlayableMsg
                                                    commandListener.onCommandProgress(commandString, msg, track)
                                                    commandSuccessful.add(Pair(trackAdded, Pair(msg, track)))
                                                }

                                                LinkType.ALBUM -> {
                                                    //fetch album's tracks
                                                    val albumLink = Link("https://open.spotify.com/album/$id", id)
                                                    val albumTracks = filterList(
                                                        spotify.fetchAlbumTracks(albumLink), albumLink
                                                    )
                                                    println("Album \"${albumTracks.trackList[0].album}\" has a total of ${albumTracks.trackList.size} tracks.\nAdding to queue...")

                                                    //add tracks to queue
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(albumTracks.trackList.shuffled()) else albumTracks
                                                    val albumAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg = if (albumAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(albumAdded, Pair(msg, trackList)))
                                                }

                                                LinkType.PLAYLIST -> {
                                                    //fetch playlist's tracks
                                                    val playlistLink = Link("https://open.spotify.com/playlist/$id", id)
                                                    val playlistTracks = filterList(
                                                        spotify.fetchPlaylistTracks(playlistLink), playlistLink
                                                    )
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    //add tracks to queue
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    val playlistAdded =
                                                        songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (playlistAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(playlistAdded, Pair(msg, trackList)))
                                                }

                                                LinkType.SHOW -> {
                                                    //fetch show's episodes
                                                    val showLink = Link("https://open.spotify.com/show/$id", id)
                                                    val episodes = filterList(
                                                        spotify.fetchShow(showLink).episodes.toTrackList(),
                                                        showLink
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(episodes.trackList.shuffled()) else episodes
                                                    val showAdded =
                                                        songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg = if (showAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(showAdded, Pair(msg, trackList)))
                                                }

                                                LinkType.EPISODE -> {
                                                    //fetch episode
                                                    val episode = spotify.fetchEpisode(
                                                        Link("https://open.spotify.com/episode/$id", id)
                                                    )
                                                    val episodeAdded =
                                                        if (episode.playability.isPlayable)
                                                            songQueue.addToQueue(episode.toTrack(), customPosition)
                                                        else false
                                                    val msg =
                                                        if (episodeAdded)
                                                            "Added podcast episode to queue."
                                                        else
                                                            "Episode not playable."
                                                    commandListener.onCommandProgress(commandString, msg, episode)
                                                    commandSuccessful.add(Pair(episodeAdded, Pair(msg, episode)))
                                                }

                                                LinkType.ARTIST -> {
                                                    //fetch artist's top tracks
                                                    val artistLink = Link("https://open.spotify.com/artist/$id", id)
                                                    val topTracks = filterList(
                                                        spotify.fetchArtist(artistLink).topTracks,
                                                        artistLink
                                                    )
                                                    val trackList =
                                                        if (shouldShuffle)
                                                            TrackList(topTracks.trackList.shuffled())
                                                        else
                                                            topTracks
                                                    val tracksAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (tracksAdded)
                                                            "Added Artist's top track to the queue."
                                                        else
                                                            tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(tracksAdded, Pair(msg, trackList)))
                                                }

                                                else -> {
                                                    val msg = "Link type \"$type\" for link $link is not supported!"
                                                    commandListener.onCommandProgress(commandString, msg, link)
                                                    commandSuccessful.add(Pair(false, Pair(msg, link)))
                                                }
                                            }
                                        }

                                        //YouTube
                                        link.link.contains("(https?://)?((m|www)\\.)?youtu\\.?be(\\.com)?".toRegex()) -> {
                                            //get link type
                                            val type = link.linkType(youTube)
                                            println("YouTube link: $link\nLink type: $type")
                                            //video/playlist id
                                            val id = link.getId()
                                            //check type
                                            when (type) {
                                                LinkType.VIDEO -> {
                                                    val track = youTube.fetchVideo(Link("https://youtu.be/$id", id))
                                                    val trackAdded =
                                                        if (track.playability.isPlayable)
                                                            songQueue.addToQueue(track, customPosition)
                                                        else false
                                                    val msg = if (trackAdded) trackAddedMsg else trackNotPlayableMsg
                                                    commandListener.onCommandProgress(commandString, msg, track)
                                                    commandSuccessful.add(Pair(trackAdded, Pair(msg, track)))
                                                }

                                                LinkType.PLAYLIST -> {
                                                    //fetch playlist tracks
                                                    val playlistLink =
                                                        Link("https://www.youtube.com/playlist?list=$id", id)
                                                    val playlistTracks = filterList(
                                                        youTube.fetchPlaylistTracks(playlistLink),
                                                        playlistLink
                                                    )
                                                    println("Playlist has a total of ${playlistTracks.trackList.size} tracks.\nAdding to queue...")
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    val playlistAdded =
                                                        songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (playlistAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(playlistAdded, Pair(msg, trackList)))
                                                }

                                                else -> {
                                                    val msg = "Link type \"$type\" for link $link is not supported!"
                                                    commandListener.onCommandProgress(commandString, msg, link)
                                                    commandSuccessful.add(Pair(false, Pair(msg, link)))
                                                }
                                            }
                                        }

                                        //SoundCloud
                                        link.link.contains("(https?://)?soundcloud\\.com/".toRegex()) -> {
                                            //get link type
                                            val type = link.linkType(soundCloud)
                                            println("SoundCloud link: $link\nLink type: $type")
                                            //check type
                                            when (type) {
                                                LinkType.TRACK -> {
                                                    val track = soundCloud.fetchTrack(link)
                                                    val trackAdded =
                                                        if (track.playability.isPlayable)
                                                            songQueue.addToQueue(track, customPosition)
                                                        else false
                                                    val msg = if (trackAdded) trackAddedMsg else trackNotPlayableMsg
                                                    commandListener.onCommandProgress(commandString, msg, track)
                                                    commandSuccessful.add(Pair(trackAdded, Pair(msg, track)))
                                                }

                                                LinkType.PLAYLIST -> {
                                                    //fetch playlist tracks
                                                    val playlistTracks =
                                                        filterList(soundCloud.fetchPlaylistTracks(link), link)
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(playlistTracks.trackList.shuffled()) else playlistTracks
                                                    val playlistAdded =
                                                        songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (playlistAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(playlistAdded, Pair(msg, trackList)))
                                                }

                                                LinkType.LIKES -> {
                                                    //fetch likes
                                                    val likes = filterList(soundCloud.fetchUserLikes(link), link)
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(likes.trackList.shuffled()) else likes
                                                    val likesAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (likesAdded)
                                                            "Added user's likes to the queue."
                                                        else
                                                            tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(likesAdded, Pair(msg, trackList)))
                                                }

                                                LinkType.REPOSTS -> {
                                                    val reposts = filterList(soundCloud.fetchUserReposts(link), link)
                                                    val trackList =
                                                        if (shouldShuffle) TrackList(reposts.trackList.shuffled()) else reposts
                                                    val repostsAdded =
                                                        songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (repostsAdded)
                                                            "Added user's reposts to the queue."
                                                        else
                                                            tracksAddingErrorMsg
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(repostsAdded, Pair(msg, trackList)))
                                                }

                                                else -> {
                                                    val msg = "Link type \"$type\" for link $link is not supported!"
                                                    commandListener.onCommandProgress(commandString, msg, link)
                                                    commandSuccessful.add(Pair(false, Pair(msg, link)))
                                                }
                                            }
                                        }
                                    }
                                }
                                return if (commandSuccessful.all { it.first }) {
                                    val msg = commandSuccessful.filter { it.first }.map { it.second.first }
                                    printToChat(msg)
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        msg.toString(),
                                        commandSuccessful.first().second.second
                                    )
                                    commandJob.complete()
                                    true
                                } else {
                                    val unPlayableTracks = commandSuccessful.filter { !it.first }
                                    printToChat(listOf("${unPlayableTracks.size} track${if (unPlayableTracks.size > 1) "s" else ""} could not be added :/"))
                                    for (unplayable in unPlayableTracks)
                                        printToChat(
                                            listOf(
                                                unplayable.second.first,
                                                unplayable.second.second.toString()
                                            )
                                        )
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        unPlayableTracks.joinToString("\n") { it.second.first },
                                        unPlayableTracks
                                    )
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
                                            if (track.service == Service.YOUTUBE) {
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
                                                if (track.link.service() != Service.YOUTUBE) {
                                                    track.artists.artists.forEach { strBuilder.append("${it.name}, ") }
                                                } else {
                                                    strBuilder.append("${track.title},")
                                                }
                                                "${strBuilder.toString().substringBeforeLast(",")} " +
                                                        if (track.link.service() != Service.YOUTUBE) {
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
                            commandString.contains("^${commandList.commandList["queue-delete"]}((\\s+(-a|--all))?\\s+((\\[URL])?https?://\\S+,?\\s*)*(\\s+(-a|--all))?|([0-9]+,?\\s*)+)".toRegex()) -> {
                                //get links from message
                                val links =
                                    if (commandString.contains("^${commandList.commandList["queue-delete"]}(\\s+(-a|--all))?\\s+((\\[URL])?https?://\\S+,?(\\s+)?)+(\\s+(-a|--all))?".toRegex())) {
                                        commandString.split("(\\s+|,\\s+|,)".toRegex()).filter {
                                            it.contains("(\\[URL])?https?://\\S+,?(\\[/URL])?".toRegex())
                                        }.map { Link(removeTags(it.replace(",\\[/URL]".toRegex(), "[/URL]"))) }
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
                                if (links.isEmpty() && positions.isEmpty()) {
                                    printToChat(
                                        listOf(
                                            "You need to specify what tracks to delete!\n" +
                                                    "You can get more help by running ${commandList.commandList["help"]} ${commandList.commandList["queue-delete"]}"
                                        )
                                    )
                                } else {
                                    val currentList = songQueue.getQueue()
                                    //get a list of the tracks to delete
                                    val tracksToDelete = currentList.filter { track -> links.contains(track.link) }
                                    if (tracksToDelete.isNotEmpty()) {
                                        for (track in tracksToDelete.distinct()) {
                                            if (commandString.contains("\\s+(-a|--all)(\\s+)?".toRegex())) {
                                                printToChat(
                                                    listOf(
                                                        "You used the -a/--all flag, so deleting all matches of track:\n" +
                                                                track.toShortString()
                                                    )
                                                )
                                                songQueue.deleteTracks(tracksToDelete.distinct())
                                            } else {
                                                //check if there are multiple instances of the track in the queue.
                                                if (currentList.filter { it.link == track.link }.size > 1) {
                                                    val duplicates = ArrayList<Int>()
                                                    printToChat(
                                                        listOf(
                                                            "There are multiple instances of this track:\n" +
                                                                    currentList.mapIndexed { i, t ->
                                                                        if (t.link == track.link)
                                                                            "$i: ${t.toShortString()}".also {
                                                                                duplicates.add(i)
                                                                            }
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
                                                } else {
                                                    //no duplicates found, delete the track
                                                    printToChat(
                                                        listOf("Deleting track:\n${track.toShortString()}")
                                                    )
                                                    songQueue.deleteTrack(track)
                                                }
                                            }
                                        }
                                    } else {
                                        if (positions.isEmpty())
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
                                val channelList = when (client) {
                                    is TeamSpeak -> {
                                        client.getChannelList()
                                    }

                                    is OfficialTSClient -> {
                                        client.getChannelList()
                                    }

                                    else -> emptyList()
                                }.map {
                                    Pair(
                                        it.substringAfter("channel_name=").substringBefore(" "),
                                        it.substringAfter("cid=").substringBefore(" ")
                                    )
                                }

                                //get users in current channel
                                for (channel in channelList) {
                                    if (channel.first == botSettings.channelName.substringAfterLast("/")) {
                                        val tsUserListData = when (client) {
                                            is TeamSpeak -> client.getClientList()
                                            is OfficialTSClient -> client.getClientList()
                                            else -> emptyList()
                                        }
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
                                    if (user != botSettings.nickname) {
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
                            commandString.contains("^${commandList.commandList["queue-move"]}".toRegex()) -> {
                                val availableArgs = listOf("-p", "--position", "-a", "--all", "-f", "--first")
                                val currentList = songQueue.getQueue()
                                val newPosition = commandString.split("\\s+".toRegex()).let { args ->
                                    if (args.any { it.contains("(-p|--position)".toRegex()) }) {
                                        val posIndex = args.indexOfFirst { it.contains("(-p|--position)".toRegex()) }
                                        if (args[posIndex].contains("[0-9]+".toRegex()))
                                            args[posIndex].replace("(-p|--position)".toRegex(), "")
                                                .ifEmpty { "0" }.toInt()
                                        else
                                            args[posIndex + 1].ifEmpty { "0" }.toInt()
                                    } else {
                                        printToChat(listOf("You didn't specify a position using -p or --position! Defaulting to position 0."))
                                        0
                                    }
                                }
                                val links =
                                    if (commandString.contains(
                                            "^${commandList.commandList["queue-move"]}(\\s+(${
                                                availableArgs.joinToString("|")
                                            }))?\\s+((\\[URL])?https?://\\S+,?\\s*(${
                                                availableArgs.joinToString("|")
                                            })?)".toRegex()
                                        )
                                    ) {
                                        commandString.split("(\\s+|,\\s*)".toRegex()).filter {
                                            it.contains("(\\[URL])?https?://\\S+,?(\\[/URL])?".toRegex())
                                        }.map { Link(removeTags(it)) }
                                    } else {
                                        emptyList()
                                    }
                                val noArgsCommand = removeTags(
                                    commandString.replace(
                                        ("(${commandList.commandList["queue-move"]}\\s+)|" +
                                                "(\\s*(${availableArgs.joinToString("|")})(\\s*[0-9]+)?)").toRegex(),
                                        ""
                                    )
                                ).let { args ->
                                    var newArgs = args
                                    if (commandString.contains("https?://\\S+".toRegex())) {
                                        //convert links to positions
                                        if (commandString.contains("\\s+(-a|--all)\\s*".toRegex())) {
                                            val indexList = ArrayList<Pair<Link, List<Int>>>()
                                            printToChat(
                                                listOf(
                                                    "You used the -a/--all flag, so moving all matches of track(s)"
                                                )
                                            )
                                            links.forEach { link ->
                                                val indexes = ArrayList<Int>()
                                                currentList.forEachIndexed { index, track ->
                                                    if (track.link == link)
                                                        indexes.add(index)
                                                }
                                                indexList.add(Pair(link, indexes))
                                            }
                                            if (indexList.isNotEmpty())
                                                links.forEachIndexed { index, link ->
                                                    newArgs = newArgs.replace(
                                                        link.link,
                                                        indexList[index].second.joinToString(",")
                                                    )
                                                }
                                            else
                                                printToChat(listOf("No matches found in the queue!"))

                                        } else if (currentList.filter { track -> links.any { it == track.link } }.size > 1) {
                                            if (commandString.contains("\\s+(-f|--first)\\s*".toRegex())) {
                                                val indexList = ArrayList<Pair<Link, List<Int>>>()
                                                printToChat(
                                                    listOf("You used the -f/--first flag, so moving only first match of track(s)")
                                                )
                                                links.forEach { link ->
                                                    currentList.indexOfFirst { it.link == link }.let {
                                                        indexList.add(Pair(link, listOf(it)))
                                                    }
                                                }
                                                if (indexList.isNotEmpty())
                                                    links.forEachIndexed { index, link ->
                                                        newArgs = newArgs.replace(
                                                            link.link,
                                                            indexList[index].second.joinToString(",")
                                                        )
                                                    }
                                                else
                                                    printToChat(listOf("No matches found in the queue!"))
                                            } else {
                                                val duplicates = ArrayList<Int>()
                                                printToChat(
                                                    listOf(
                                                        "There are multiple instances of these tracks:\n" +
                                                                currentList.mapIndexed { i, t ->
                                                                    if (links.any { it == t.link })
                                                                        "$i: ${t.toShortString()}".also {
                                                                            duplicates.add(i)
                                                                        }
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
                                                printToChat(
                                                    listOf(
                                                        "Decide which track(s) you want to move, then run ${commandList.commandList["queue-move"]} with the new position specified.\n" +
                                                                "For examples, run ${commandList.commandList["help"]} ${commandList.commandList["queue-move"]}\n"
                                                    )
                                                )
                                            }
                                        } else {
                                            val indexList = ArrayList<Pair<Link, List<Int>>>()
                                            links.forEach { link ->
                                                currentList.indexOfFirst { it.link == link }.let {
                                                    indexList.add(Pair(link, listOf(it)))
                                                }
                                            }
                                            if (indexList.isNotEmpty())
                                                links.forEachIndexed { index, link ->
                                                    newArgs = newArgs.replace(
                                                        link.link,
                                                        indexList[index].second.joinToString(",")
                                                    )
                                                }
                                            else
                                                printToChat(listOf("No matches found in the queue!"))
                                        }
                                    }
                                    newArgs
                                }
                                val positions: ArrayList<Int> =
                                    if (noArgsCommand.contains("([0-9]+(,\\s*)?)+".toRegex())) {
                                        noArgsCommand.split("(\\s+|,\\s*)".toRegex()).filter {
                                            it.contains("^[0-9]+$".toRegex())
                                        }.map { it.toInt() } as ArrayList<Int>
                                    } else {
                                        ArrayList<Int>()
                                    }
                                when {
                                    newPosition > songQueue.getQueue().size - 1 -> {
                                        printToChat(
                                            listOf("lol u think arrays start at 1?")
                                        )
                                        commandJob.complete()
                                        return false
                                    }

                                    newPosition < 0 -> {
                                        printToChat(
                                            listOf("What were you thinking?", "You can't do that.")
                                        )
                                        commandJob.complete()
                                        return false
                                    }

                                    else -> {
                                        var newPosIsOffset = false
                                        if (positions.isNotEmpty()) {
                                            newPosIsOffset = songQueue.moveTracks(positions, newPosition)
                                        } else {
                                            printToChat(
                                                listOf(
                                                    "You need to specify which tracks to move!\n" +
                                                            "You can get more help by running ${commandList.commandList["help"]} ${commandList.commandList["queue-move"]}"
                                                )
                                            )
                                        }
                                        val newList = songQueue.getQueue()
                                        return if (
                                            currentList.filterIndexed { index, _ -> positions.any { it == index } }
                                                .let { tracks ->
                                                    if (newPosIsOffset) {
                                                        if (newPosition == 0) {
                                                            tracks.any { it == newList[newPosition] }
                                                        } else {
                                                            tracks.any { it == newList[newPosition - 1] }
                                                        }
                                                    } else {
                                                        tracks.any { it == newList[newPosition] }
                                                    }
                                                }
                                        ) {
                                            printToChat(
                                                listOf("Moved track${if (positions.size > 1) "s" else ""} to new position.")
                                            )
                                            commandListener.onCommandExecuted(
                                                commandString,
                                                "Moved track${if (positions.size > 1) "s" else ""} to new position."
                                            )
                                            commandJob.complete()
                                            true
                                        } else {
                                            printToChat(
                                                listOf("Couldn't move track${if (positions.size > 1) "s" else ""} to new position.")
                                            )
                                            commandListener.onCommandExecuted(
                                                commandString,
                                                "Couldn't move track${if (positions.size > 1) "s" else ""} to new position."
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
                                    if (currentTrack.link.link.contains("(youtu\\.?be|soundcloud)".toRegex()))
                                        messageLines.appendLine("Upload Date:   \t${currentTrack.album.releaseDate.date}")
                                    else
                                        messageLines.appendLine("Release:     \t\t\t${currentTrack.album.releaseDate.date}")

                                    if (currentTrack.artists.artists.isNotEmpty()) {
                                        if (currentTrack.link.link.contains("(youtu\\.?be|soundcloud\\.com)".toRegex()))
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
                            //search command
                            commandString.contains("^${commandList.commandList["search"]}\\s+".toRegex()) -> {
                                val searchCommand = commandList.commandList["search"]
                                val serviceText = commandString.replace("^$searchCommand\\s+".toRegex(), "")
                                    .replace("\\s+.*$".toRegex(), "")
                                val service = when (serviceText) {
                                    "sc", "soundcloud" -> Service.SOUNDCLOUD
                                    "sp", "spotify" -> Service.SPOTIFY
                                    "yt", "youtube" -> Service.YOUTUBE
                                    else -> Service.OTHER
                                }
                                val searchType = SearchType(
                                    commandString.replace("^$searchCommand\\s+$serviceText\\s+".toRegex(), "")
                                        .replace("\\s+.*$".toRegex(), "")
                                )
                                if (
                                    when (service) {
                                        Service.SPOTIFY -> spotify.supportedSearchTypes.contains(searchType.getType())
                                        Service.YOUTUBE -> youTube.supportedSearchTypes.contains(searchType.getType())
                                        Service.SOUNDCLOUD -> soundCloud.supportedSearchTypes.contains(searchType.getType())
                                        else -> false
                                    }
                                ) {
                                    val limit =
                                        if (commandString.contains("(-l|--limit)\\s+[0-9]+".toRegex()))
                                            commandString.split("\\s+(-l|--limit)\\s+".toRegex()).last()
                                                .replace("\\s+.*$".toRegex(), "").toInt()
                                        else
                                            10

                                    val searchQuery = SearchQuery(
                                        commandString.replace(
                                            "^$searchCommand\\s+$serviceText\\s+$searchType\\s+".toRegex(),
                                            ""
                                        )
                                            .replace("(-l|--limit)\\s+[0-9]+".toRegex(), "")
                                    )
                                    printToChat(listOf("Searching, please wait..."))
                                    val results = when (service) {
                                        Service.SOUNDCLOUD -> soundCloud.searchSoundCloud(
                                            searchType,
                                            searchQuery,
                                            limit
                                        )

                                        Service.SPOTIFY -> spotify.searchSpotify(searchType, searchQuery, limit)
                                        Service.YOUTUBE -> youTube.searchYouTube(searchType, searchQuery, limit)
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
                                        listOf("Search type not supported! Run ${commandList.commandList["help"]} $searchCommand to see more information on this command.")
                                    )
                                    commandListener.onCommandExecuted(commandString, "Not supported", searchType)
                                    commandJob.complete()
                                    return false
                                }
                            }
                            //info command
                            commandString.contains("^${commandList.commandList["info"]}\\s+".toRegex()) -> {
                                val links = removeTags(
                                    commandString.replace("${commandList.commandList["info"]}\\s+".toRegex(), "")
                                ).split("\\s*,\\s*".toRegex()).map { Link(it) }

                                val output = ArrayList<Any>()
                                var success = false
                                for (link in links) {
                                    @Suppress("IMPLICIT_CAST_TO_ANY")
                                    val data: Any? = when (link.service()) {
                                        Service.SPOTIFY -> when (link.linkType()) {
                                            LinkType.TRACK -> spotify.fetchTrack(link)
                                            LinkType.ALBUM -> spotify.fetchAlbum(link)
                                            LinkType.PLAYLIST -> spotify.fetchPlaylist(link)
                                            LinkType.ARTIST -> spotify.fetchArtist(link)
                                            LinkType.SHOW -> spotify.fetchShow(link)
                                            LinkType.EPISODE -> spotify.fetchEpisode(link)
                                            LinkType.USER -> spotify.fetchUser(link)
                                            else -> null
                                        }.also { if (it != null) output.add(it) }

                                        Service.YOUTUBE -> when (link.linkType()) {
                                            LinkType.VIDEO -> youTube.fetchVideo(link)
                                            LinkType.PLAYLIST -> youTube.fetchPlaylist(link)
                                            LinkType.CHANNEL -> youTube.fetchChannel(link)
                                            else -> null
                                        }.also { if (it != null) output.add(it) }

                                        Service.SOUNDCLOUD -> when (link.linkType()) {
                                            LinkType.TRACK -> soundCloud.fetchTrack(link)
                                            LinkType.ALBUM -> soundCloud.fetchAlbum(link)
                                            LinkType.PLAYLIST -> soundCloud.getPlaylist(link)
                                            LinkType.ARTIST -> soundCloud.fetchArtist(link)
                                            LinkType.USER -> soundCloud.fetchUser(link)
                                            else -> null
                                        }.also { if (it != null) output.add(it) }

                                        else -> {
                                            printToChat(listOf("Link type not supported for this link: $link"))
                                                .also { output.add(it) }
                                            null
                                        }
                                    }
                                    success = if (data != null) {
                                        printToChat(listOf("\n$data"))
                                        true
                                    } else {
                                        val msg = "This link isn't supported: $link"
                                        printToChat(listOf(msg))
                                        commandListener.onCommandExecuted(commandString, msg)
                                        commandJob.complete()
                                        false
                                    }
                                }
                                commandListener.onCommandExecuted(
                                    commandString,
                                    output.map { it.toString() }.toString(),
                                    links
                                )
                                commandJob.complete()
                                return success
                            }
                            //sp-pause command
                            commandString.contains("^${commandList.commandList["sp-pause"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p ${botSettings.spotifyPlayer} pause && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-resume & sp-play command
                            commandString.contains("^(${commandList.commandList["sp-resume"]}|${commandList.commandList["sp-play"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p ${botSettings.spotifyPlayer} play && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-stop command
                            //Stop spotify playback
                            commandString.contains("^${commandList.commandList["sp-stop"]}$".toRegex()) -> {
                                if (botSettings.spotifyPlayer == "ncspot")
                                    commandRunner.runCommand("playerctl -p ncspot stop; tmux kill-session -t ncspot")
                                else
                                    commandRunner.runCommand("playerctl -p ${botSettings.spotifyPlayer} pause")
                                commandJob.complete()
                                return true
                            }
                            //sp-skip & sp-next command
                            commandString.contains("^(${commandList.commandList["sp-skip"]}|${commandList.commandList["sp-next"]})$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p ${botSettings.spotifyPlayer} next && sleep 1")
                                commandJob.complete()
                                return true
                            }
                            //sp-prev command
                            commandString.contains("^${commandList.commandList["sp-prev"]}$".toRegex()) -> {
                                commandRunner.runCommand("playerctl -p ${botSettings.spotifyPlayer} previous && sleep 0.1 & playerctl -p ${botSettings.spotifyPlayer} previous")
                                commandJob.complete()
                                return true
                            }
                            //sp-playsong command
                            //Play Spotify song based on link or URI
                            commandString.contains("^${commandList.commandList["sp-playsong"]}\\s+".toRegex()) -> {
                                if (message.substringAfter("${commandList.commandList["sp-playsong"]}")
                                        .isNotEmpty()
                                ) {
                                    startSpotifyPlayer()
                                    println("Playing song...")
                                    if (
                                        Link(removeTags(commandString.substringAfter("${commandList.commandList["sp-playsong"]} "))).link
                                            .startsWith("https://open.spotify.com/track")
                                    ) {
                                        commandRunner.runCommand(
                                            "playerctl -p ${botSettings.spotifyPlayer} open spotify:track:${
                                                removeTags(message)
                                                    .substringAfterLast("/")
                                                    .substringBefore("?")
                                            }"
                                        )
                                    } else {
                                        commandRunner.runCommand(
                                            "playerctl -p ${botSettings.spotifyPlayer} open spotify:track:${
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
                                startSpotifyPlayer()
                                if (message.split(" ".toRegex())[1].startsWith("spotify:user:")
                                    && message.split(" ".toRegex())[1].contains(":playlist:")
                                ) {
                                    commandRunner.runCommand(
                                        "playerctl -p ${botSettings.spotifyPlayer} open spotify:user:${
                                            message.split(" ".toRegex())[1]
                                                .split(":".toRegex())[2]
                                        }:playlist:${
                                            message.split(":".toRegex()).last()
                                        }"
                                    )
                                } else if (message.split(" ".toRegex())[1].startsWith("spotify:playlist")) {
                                    commandRunner.runCommand(
                                        "playerctl -p ${botSettings.spotifyPlayer} open spotify:playlist:${
                                            message.substringAfter("playlist:")
                                        }"
                                    )
                                } else if (removeTags(message.substringAfter(" ")).startsWith("https://open.spotify.com/")) {
                                    commandRunner.runCommand(
                                        "playerctl -p ${botSettings.spotifyPlayer} open spotify:playlist:${
                                            removeTags(message).substringAfter("playlist/")
                                                .substringBefore("?")
                                        }"
                                    )
                                }
                                commandJob.complete()
                                return true
                            }
                            //sp-playalbum command
                            commandString.contains("^${commandList.commandList["sp-playalbum"]}\\s+".toRegex()) -> {
                                startSpotifyPlayer()
                                if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                                    commandRunner.runCommand(
                                        "playerctl -p ${botSettings.spotifyPlayer} open spotify:album:${
                                            message.substringAfter("album:")
                                        }"
                                    )
                                } else if (removeTags(message.substringAfter(" ")).startsWith("https://open.spotify.com/")) {
                                    commandRunner.runCommand(
                                        "playerctl -p ${botSettings.spotifyPlayer} open spotify:album:${
                                            removeTags(message).substringAfter("album/")
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
                                    "playerctl -p ${botSettings.spotifyPlayer} metadata",
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
                                val ytLink = Link(removeTags(message.substringAfter(" ")))
                                if (ytLink.link.isNotEmpty()) {
                                    launch {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video" +
                                                    " --ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0," +
                                                    "cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" +
                                                    " --ytdl \"$ytLink\" --volume=${botSettings.mpvVolume}",
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
                                val scLink = Link(removeTags(message.substringAfter(" ")))
                                if (scLink.link.isNotEmpty()) {
                                    launch {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video --ytdl \"$scLink\" --volume=${botSettings.mpvVolume}",
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
                        printToChat(
                            listOf("Command not found! Try ${commandList.commandList["help"]} to see available commands.")
                        )
                        commandJob.complete()
                        return false
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
        if (latestMsgUsername == "__console__") {
            messages.forEach { println(it) }
        } else {
            for (message in messages) {
                when (client) {
                    is TeamSpeak -> client.sendMsgToChannel(message)
                    is OfficialTSClient -> client.sendMsgToChannel(message)
                }
            }
        }
    }

    private suspend fun chatUpdated(line: String, userName: String = "") {
        when (client) {
            is TeamSpeak -> {
                withContext(IO) {
                    parseLine(line)
                    onChatUpdateListener.onChatUpdated(ChatUpdate(userName, line))
                }
            }

            is OfficialTSClient -> {
                when (client.channelFile.extension) {
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
                        withContext(IO) {
                            onChatUpdateListener.onChatUpdated(ChatUpdate(latestMsgUsername, userMessage))
                        }
                    }

                    "txt" -> {
                        //extract message
                        if (line.startsWith("<")) {
                            latestMsgUsername = line.substringAfter("> ").substringBeforeLast(": ")
                            val time = Time(Calendar.getInstance())
                            val rawTime =
                                line.split(" ".toRegex())[0].substringAfter("<").substringBefore(">")
                                    .split(":".toRegex())
                            time.hour = rawTime[0]
                            time.minute = rawTime[1]
                            time.second = rawTime[2]

                            val userMessage = line.substringAfter("$latestMsgUsername: ")
                            parseLine(userMessage)
                            withContext(IO) {
                                onChatUpdateListener.onChatUpdated(ChatUpdate(latestMsgUsername, userMessage))
                            }
                        }
                    }

                    else -> {
                        println("Error! file format \"${client.channelFile.extension}\" not supported!")
                    }
                }
            }
        }
    }

    override fun onTrackEnded(player: String, track: Track) {
        voteSkipUsers.clear()
    }

    override fun onTrackPaused(player: String, track: Track) {
        printToChat(listOf("Playback paused."))
    }

    override fun onTrackResumed(player: String, track: Track) {
        printToChat(listOf("Playback resumed."))
    }

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
    fun onCommandProgress(command: String, output: String, extra: Any? = null)
}
