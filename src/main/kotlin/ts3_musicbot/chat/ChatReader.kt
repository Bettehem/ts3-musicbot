package ts3_musicbot.chat

import com.github.manevolent.ts3j.event.TS3Listener
import com.github.manevolent.ts3j.event.TextMessageEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.*
import ts3_musicbot.client.Client
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.client.TeamSpeak
import ts3_musicbot.services.Service
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
import ts3_musicbot.util.*
import java.util.*

class ChatReader(
    private val client: Client,
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
    private val voteSkipUsers = ArrayList<Pair<String, Boolean>>()
    private val trackCache = ArrayList<Pair<Link, TrackList>>()
    var latestMsgUsername = ""

    @Volatile
    private var songQueue = SongQueue(botSettings, client, this)

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
                        text.replace("\\[/?URL]|,(\$|\\s)".toRegex(), "")
                    }

                    else -> {
                        println("This extension isn't supported! Use channel.txt as the chat file.")
                        text
                    }
                }
            }

            is TeamSpeak -> text.replace("\\[/?URL]|,(\$|\\s)".toRegex(), "")

            else -> {
                println("Couldn't remove tags!\n$client is not a supported client!")
                println("Returning original text...")
                text
            }
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

                        "ncspot" -> {
                            playerctl(botSettings.spotifyPlayer, "stop")
                            commandRunner.runCommand("tmux kill-session -t ncspot", ignoreOutput = true)
                        }

                        "spotifyd" -> commandRunner.runCommand("echo \"spotifyd isn't well supported yet, please kill it manually.\"")
                        else -> commandRunner.runCommand("echo \"${botSettings.spotifyPlayer} is not a supported player!\" > /dev/stderr; return 2")
                    }

                    fun startCommand() = when (botSettings.spotifyPlayer) {
                        "spotify" -> commandRunner.runCommand(
                            "xvfb-run -a spotify --no-zygote --disable-gpu" +
                                    if (botSettings.spotifyUsername.isNotEmpty() && botSettings.spotifyPassword.isNotEmpty()) {
                                        " --username=${botSettings.spotifyUsername} --password=${botSettings.spotifyPassword}"
                                    } else {
                                        ""
                                    } + " &",
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

                    if (checkProcess().outputText.isEmpty())
                        startCommand()
                    //sometimes the spotify player has problems starting, so ensure it actually starts.
                    while (checkProcess().outputText.isEmpty()) {
                        delay(7000)
                        if (checkProcess().outputText.isEmpty()) {
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
                        ).outputText.isEmpty()
                    ) {
                        //do nothing
                        println("Waiting for ${botSettings.spotifyPlayer} to start")
                        delay(10)
                    }
                    delay(5000)
                }

                /**
                 * Execute a musicbot command.
                 * @param commandString The command to execute
                 * @return Returns a Pair containing a boolean value indicating whether the command was successful, and any extra data.
                 */
                suspend fun executeCommand(commandString: String): Pair<Boolean, Any?> {
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
                                    return Pair(true, commandList.helpMessages["help"])
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
                                        Pair(true, args)
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
                                        Pair(false, args)
                                    }
                                }
                            }

                            //queue-add command
                            //queue-playnext command
                            commandString.contains("^(${commandList.commandList["queue-add"]}|${commandList.commandList["queue-playnext"]})(\\s+-\\w*(r|s|t|P|([lp]\\s*[0-9]+)))*(\\s*(\\[URL])?((spotify:(user:\\S+:)?(track|album|playlist|show|episode|artist):\\S+)|(https?://\\S+)|((sp|spotify|yt|youtube|sc|soundcloud)\\s+(track|album|playlist|show|episode|artist|video|user)\\s+.+))(\\[/URL])?\\s*,?\\s*)+(\\s+-\\w*(r|s|t|P|([lp]\\s*[0-9]+)))*\$".toRegex()) -> {
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
                                var trackLimit = 0
                                var tracksOnly = false
                                var playlistsOnly = false
                                var hasCustomPosition = false
                                var shouldReverse = false
                                var customPosition = if (shouldPlayNext) 0 else null
                                val links = ArrayList<Link>()
                                val msgBuilder = StringBuilder()

                                /**
                                 * Filter out unplayable tracks from a TrackList
                                 * and inform user if unplayable tracks are found.
                                 * @param trackList list to filter
                                 * @param playlistLink link to the playlist.
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
                                            msgBuilder.appendLine(someTracksNotPlayableMsg)
                                            msgBuilder.appendLine(
                                                String.format(
                                                    considerDeletingTracksMsg,
                                                    playlistLink
                                                )
                                            )
                                            msgBuilder.appendLine(TrackList(it).toString())
                                        }
                                    }
                                    return TrackList(trackList.trackList.filter { it.playability.isPlayable })
                                }

                                if (commandString.contains("\\s+(sp|spotify|yt|youtube|sc|soundcloud)\\s+\\w+\\s+.+".toRegex())) {
                                    when {
                                        commandString.contains("\\s+(sp|spotify)\\s+".toRegex()) ->
                                            spotify.search(
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
                                            youTube.search(
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
                                            soundCloud.search(
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
                                val args = commandString.split("\\s+".toRegex())
                                val validOptions = listOf("s", "l", "t", "P", "p", "r").joinToString("")
                                for (i in args.indices) {
                                    val pattern = "^-[$validOptions]"
                                    //check if the tracks should be shuffled
                                    if (args[i].contains("$pattern*s".toRegex())) {
                                        shouldShuffle = true
                                    }
                                    // check if the amount of tracks should be limited
                                    if (args[i].contains("$pattern*l".toRegex())) {
                                        if (args.size >= i + 1) {
                                            trackLimit = args[i + 1].toInt()
                                        }
                                    }
                                    //check if only tracks from SoundCloud likes/reposts should be included
                                    if (args[i].contains("$pattern*t".toRegex())) {
                                        tracksOnly = true
                                        playlistsOnly = false
                                    }
                                    //check if only playlists from SoundCloud likes/reposts should be included
                                    if (args[i].contains("$pattern*P".toRegex())) {
                                        playlistsOnly = true
                                        tracksOnly = false
                                    }
                                    //check if custom position is provided
                                    if (args[i].contains("$pattern*p".toRegex())) {
                                        if (args.size >= i + 1) {
                                            if (args[i + 1].contains("-?[0-9]+".toRegex())) {
                                                customPosition = args[i + 1].toInt()
                                                hasCustomPosition = true
                                            }
                                        }
                                    }
                                    if (args[i].contains("$pattern*r".toRegex())) {
                                        shouldReverse = true
                                    }
                                    if (args[i].contains("((\\[URL])?((https?://)?(spotify\\.link|link\\.tospotify\\.com|open\\.spotify\\.com|soundcloud\\.com|((m|www)\\.)?youtu\\.?be(\\.com)?)).+(\\[/URL])?)|(spotify:(track|album|playlist|show|episode|artist):.+)".toRegex())) {
                                        //add links to ArrayList
                                        if (args[i].contains(","))
                                            links.addAll(
                                                args[i].split(",").map { Link(removeTags(it)) }
                                            )
                                        else
                                            links.add(Link(removeTags(args[i])))
                                    }
                                }
                                if (shouldPlayNext || hasCustomPosition)
                                    links.reverse()

                                println("Fetching data...")
                                println("Total number of links: ${links.size}")
                                printToChat(listOf("Please wait, fetching data..."))
                                //add links to queue
                                for (rawLink in links) {
                                    println("Link to handle: $rawLink")
                                    fun Link.getService() = when (this.serviceType()) {
                                        Service.ServiceType.SOUNDCLOUD -> soundCloud
                                        Service.ServiceType.SPOTIFY -> spotify
                                        Service.ServiceType.YOUTUBE -> youTube
                                        Service.ServiceType.OTHER -> Service(Service.ServiceType.OTHER)
                                    }

                                    val service = rawLink.getService()
                                    val id = rawLink.getId(service)
                                    //Remove tracking stuff & other junk from the link
                                    val link = rawLink.clean(service)
                                    println("Cleaned Link: $link")

                                    if (trackCache.any { it.first == link }) {
                                        println("Match found in cache for link: $link")
                                        val tracks = filterList(
                                            trackCache.first { it.first == link }.second,
                                            link
                                        )
                                        var trackList = if (shouldReverse) tracks.reversed() else tracks
                                        trackList = if (shouldShuffle) trackList.shuffled() else trackList
                                        val tracksAdded = songQueue.addAllToQueue(trackList, customPosition)
                                        val msg = if (tracksAdded) {
                                            if (trackList.size == 1) trackAddedMsg else tracksAddedMsg
                                        } else {
                                            if (trackList.size == 1) trackNotPlayableMsg else tracksAddingErrorMsg
                                        }
                                        commandListener.onCommandProgress(commandString, msg, trackList)
                                        commandSuccessful.add(Pair(tracksAdded, Pair(msg, trackList)))
                                    } else {
                                        when (
                                            val type = link.linkType(service)
                                                .let { type -> println("Link type: $type\nLink id: ${id.ifEmpty { "N/A" }}"); type }
                                        ) {
                                            LinkType.TRACK, LinkType.VIDEO, LinkType.EPISODE -> {
                                                val track = if (type == LinkType.EPISODE && service is Spotify)
                                                    service.fetchEpisode(link).toTrack()
                                                else
                                                    service.fetchTrack(link)

                                                val trackAdded = if (track.playability.isPlayable)
                                                    songQueue.addToQueue(track, customPosition)
                                                else false

                                                val msg = if (type == LinkType.EPISODE) {
                                                    if (trackAdded)
                                                        "Added podcast episode to queue."
                                                    else
                                                        "Episode not playable."
                                                } else {
                                                    if (trackAdded) trackAddedMsg else trackNotPlayableMsg
                                                }
                                                if (trackAdded)
                                                    trackCache.add(Pair(track.link, TrackList(listOf(track))))
                                                commandListener.onCommandProgress(commandString, msg, track)
                                                commandSuccessful.add(Pair(trackAdded, Pair(msg, track)))
                                            }

                                            LinkType.PLAYLIST, LinkType.SHOW, LinkType.ALBUM -> {
                                                val tracks = filterList(
                                                    when (type) {
                                                        LinkType.PLAYLIST -> service.fetchPlaylistTracks(
                                                            link,
                                                            trackLimit
                                                        )

                                                        LinkType.SHOW -> {
                                                            if (service is Spotify)
                                                                TrackList(
                                                                    service.fetchShow(link).episodes.toTrackList()
                                                                        .trackList.let { list ->
                                                                            if (trackLimit != 0 && list.size > trackLimit)
                                                                                list.subList(0, trackLimit)
                                                                            else
                                                                                list
                                                                        }
                                                                )
                                                            else
                                                                TrackList()
                                                        }

                                                        LinkType.ALBUM -> service.fetchAlbumTracks(link, trackLimit)
                                                        else -> TrackList()
                                                    }, link
                                                )
                                                println("The ${type.name.lowercase()} has a total of ${tracks.size} tracks.\nAdding to queue...")
                                                //add tracks to queue
                                                var trackList = if (shouldReverse) tracks.reversed() else tracks
                                                trackList = if (shouldShuffle) trackList.shuffled() else trackList
                                                val tracksAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                val msg = if (tracksAdded) tracksAddedMsg else tracksAddingErrorMsg
                                                if (tracksAdded)
                                                    trackCache.add(Pair(link, tracks))
                                                commandListener.onCommandProgress(commandString, msg, trackList)
                                                commandSuccessful.add(Pair(tracksAdded, Pair(msg, trackList)))
                                            }

                                            LinkType.LIKES, LinkType.REPOSTS -> {
                                                //fetch likes/reposts
                                                if (service is SoundCloud) {
                                                    val likes = filterList(
                                                        if (type == LinkType.LIKES) {
                                                            service.fetchUserLikes(
                                                                link, trackLimit,
                                                                tracksOnly, playlistsOnly
                                                            )
                                                        } else {
                                                            service.fetchUserReposts(
                                                                link, trackLimit,
                                                                tracksOnly, playlistsOnly
                                                            )
                                                        },
                                                        link
                                                    )
                                                    var trackList = if (shouldReverse) likes.reversed() else likes
                                                    trackList = if (shouldShuffle) trackList.shuffled() else trackList
                                                    val tracksAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                    val msg =
                                                        if (tracksAdded)
                                                            "Added user's ${type.name.lowercase()} to the queue."
                                                        else
                                                            tracksAddingErrorMsg

                                                    if (tracksAdded)
                                                        trackCache.add(Pair(link, likes))
                                                    commandListener.onCommandProgress(commandString, msg, trackList)
                                                    commandSuccessful.add(Pair(tracksAdded, Pair(msg, trackList)))
                                                } else {
                                                    val msg = "Link type \"$type\" for link $link is not supported!"
                                                    commandListener.onCommandProgress(commandString, msg, link)
                                                    commandSuccessful.add(Pair(false, Pair(msg, link)))
                                                }
                                            }

                                            LinkType.ARTIST -> {
                                                //fetch artist's top tracks
                                                val topTracks = TrackList(
                                                    filterList(service.fetchArtist(link).topTracks, link)
                                                        .trackList.let { list ->
                                                            if (trackLimit != 0 && list.size > trackLimit)
                                                                list.subList(0, trackLimit)
                                                            else
                                                                list
                                                        }
                                                )
                                                var trackList = if (shouldReverse) topTracks.reversed() else topTracks
                                                trackList = if (shouldShuffle) trackList.shuffled() else trackList
                                                val tracksAdded = songQueue.addAllToQueue(trackList, customPosition)
                                                val msg =
                                                    if (tracksAdded)
                                                        "Added Artist's top tracks to the queue."
                                                    else
                                                        tracksAddingErrorMsg
                                                if (tracksAdded)
                                                    trackCache.add(Pair(link, topTracks))
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
                                }
                                return if (commandSuccessful.all { it.first }) {
                                    val msg = commandSuccessful.filter { it.first }
                                        .joinToString("\n") { it.second.first }
                                    if (msgBuilder.lines().size <= 1)
                                        msgBuilder.append(msg)
                                    else
                                        msgBuilder.appendLine(msg)
                                    printToChat(listOf(msgBuilder.toString()))
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        msgBuilder.toString(),
                                        commandSuccessful.first().second.second
                                    )
                                    commandJob.complete()
                                    Pair(true, commandSuccessful.first().second.second)
                                } else {
                                    val unPlayableTracks = commandSuccessful.filter { !it.first }
                                    msgBuilder.appendLine("${unPlayableTracks.size} track${if (unPlayableTracks.size > 1) "s" else ""} could not be added :/")
                                    for (unplayable in unPlayableTracks)
                                        msgBuilder.appendLine(
                                            listOf(
                                                unplayable.second.first,
                                                unplayable.second.second.toString()
                                            ).joinToString("\n")
                                        )
                                    printToChat(listOf(msgBuilder.toString()))
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        unPlayableTracks.joinToString("\n") { it.second.first },
                                        unPlayableTracks
                                    )
                                    commandJob.complete()
                                    Pair(false, unPlayableTracks)
                                }
                            }
                            //queue-playnow command
                            commandString.contains("^${commandList.commandList["queue-playnow"]}\\s+.+$".toRegex()) ->  {
                                if (executeCommand("${commandList.commandList["queue-playnext"]} ${commandString.replace("^${commandList.commandList["queue-playnow"]}\\s+".toRegex(), "")}").first)
                                    return executeCommand("${commandList.commandList["queue-skip"]}")
                            }
                            //queue-play command
                            commandString.contains("^${commandList.commandList["queue-play"]}$".toRegex()) -> {
                                if (songQueue.getQueue().isNotEmpty()) {
                                    return if (songQueue.getState() != SongQueue.State.QUEUE_STOPPED) {
                                        printToChat(
                                            listOf(
                                                "Queue is already active!\n" +
                                                        "Running ${commandList.commandList["queue-resume"]} instead."
                                            )
                                        )
                                        executeCommand("${commandList.commandList["queue-resume"]}")
                                        commandJob.complete()
                                        Pair(true, null)
                                    } else {
                                        printToChat(listOf("Playing Queue."))
                                        songQueue.startQueue()
                                        commandJob.complete()
                                        Pair(true, null)
                                    }
                                } else {
                                    printToChat(listOf("Queue is empty!"))
                                    commandJob.complete()
                                    return Pair(false, null)
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
                                            if (track.serviceType == Service.ServiceType.YOUTUBE) {
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
                                                if (track.link.serviceType() != Service.ServiceType.YOUTUBE) {
                                                    track.artists.artists.forEach { strBuilder.append("${it.name}, ") }
                                                } else {
                                                    strBuilder.append("${track.title},")
                                                }
                                                "${strBuilder.toString().substringBeforeLast(",")} " +
                                                        if (track.link.serviceType() != Service.ServiceType.YOUTUBE) {
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
                                        return Pair(true, TrackList(currentQueue))
                                    }
                                }
                            }
                            //queue-delete command
                            commandString.contains("^${commandList.commandList["queue-delete"]}((\\s+(-a|--all|-A|--all-artist-tracks|-f|--first))?\\s+((sp|spotify|yt|youtube|sc|soundcloud)\\s+\\w+\\s+|((\\[URL])?https?://\\S+,?\\s*))*(\\s+(-a|--all|-A|--all-artist-tracks|-f|--first))?|([0-9]+,?\\s*)+)".toRegex()) -> {
                                fun getService(link: Link): Service = when (link.serviceType()) {
                                    Service.ServiceType.SPOTIFY -> spotify
                                    Service.ServiceType.SOUNDCLOUD -> soundCloud
                                    Service.ServiceType.YOUTUBE -> youTube
                                    else -> Service(Service.ServiceType.OTHER)
                                }
                                if (songQueue.getQueue().isNotEmpty()) {
                                    var allTracks = false
                                    var allArtistTracks = false
                                    var firstMatchOnly = false
                                    val validOptions =
                                        listOf("a", "all", "A", "all-artist-tracks", "f", "first").joinToString("|")
                                    val args = commandString.split("\\s+".toRegex())
                                    for (i in args.indices) {
                                        val pattern = "^-+($validOptions)*"
                                        if (args[i].contains("$pattern(a|all)".toRegex()))
                                            allTracks = true
                                        if (args[i].contains("$pattern(A|all-artist-tracks)".toRegex()))
                                            allArtistTracks = true
                                        if (args[i].contains("$pattern(f|first)".toRegex()))
                                            firstMatchOnly = true
                                    }
                                    //get links from message
                                    val services = listOf("sp", "spotify", "yt", "youtube", "sc", "soundcloud")
                                    val links = when {
                                        commandString.contains("^${commandList.commandList["queue-delete"]}(\\s+-+($validOptions)+)*\\s+((\\[URL])?https?://\\S+,?(\\s+)?)+(\\s+-+($validOptions)+)*".toRegex()) -> {
                                            commandString.split("(\\s+|,\\s+|,)".toRegex()).filter {
                                                it.contains("(\\[URL])?https?://\\S+,?(\\[/URL])?".toRegex())
                                            }.map { Link(removeTags(it.replace(",\\[/URL]".toRegex(), "[/URL]"))) }
                                        }
                                        commandString.contains("^${commandList.commandList["queue-delete"]}(\\s+-+($validOptions)+)*\\s+(${services.joinToString("|")})\\s+\\w+\\s+.+(\\s+-+($validOptions)+)*".toRegex()) -> {
                                            latestMsgUsername = "__console__"
                                            executeCommand("${commandList.commandList["search"]}${commandString.replace("^${commandList.commandList["queue-delete"]}(\\s+-+($validOptions)+)*".toRegex(), "")}").second.let{ results ->
                                                latestMsgUsername = ""
                                                if (results is SearchResults)
                                                    listOf(results.results.first().link)
                                                else
                                                    emptyList()
                                            }
                                        }
                                        else -> emptyList()
                                    }.toMutableList()
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
                                        for (i in links.indices) {
                                            val rawLink = links[i]
                                            val service = getService(rawLink)
                                            val link = rawLink.clean(service)
                                            when (link.linkType()) {
                                                LinkType.PLAYLIST -> {
                                                    printToChat(listOf("Please wait, fetching tracks in the list:\n$link"))
                                                    links.remove(link)
                                                    links.addAll(
                                                        if (trackCache.any { it.first == link }) {
                                                            println("Tracks found in cache!")
                                                            trackCache.first { it.first == link }.second.trackList
                                                        } else {
                                                            service.fetchPlaylistTracks(link).trackList
                                                        }
                                                            .map { track -> track.link }
                                                    )
                                                }

                                                LinkType.ALBUM -> {
                                                    printToChat(listOf("Please wait, fetching tracks in the album:\n$link"))
                                                    links.remove(link)
                                                    links.addAll(
                                                        if (trackCache.any { it.first == link }) {
                                                            println("Tracks found in cache!")
                                                            trackCache.first { it.first == link }.second.trackList
                                                        } else {
                                                            service.fetchAlbumTracks(link).trackList
                                                        }
                                                            .map { track -> track.link }
                                                    )
                                                }

                                                LinkType.ARTIST -> {
                                                    if (allArtistTracks) {
                                                        printToChat(
                                                            listOf(
                                                                "You used the -A/--all-artist-tracks flag, so deleting all tracks from this artist:\n$link"
                                                            )
                                                        )
                                                    } else {
                                                        printToChat(listOf("Please wait, fetching the artist's top tracks"))
                                                        links.remove(link)
                                                        links.addAll(
                                                            if (trackCache.any { it.first == link }) {
                                                                println("Tracks found in cache!")
                                                                trackCache.first { it.first == link }.second.trackList
                                                            } else {
                                                                service.fetchArtist(link).topTracks.trackList
                                                            }
                                                                .map { track -> track.link }
                                                        )
                                                    }

                                                }

                                                LinkType.USER, LinkType.CHANNEL -> {
                                                    links.remove(link)
                                                    if (service is YouTube)
                                                        printToChat(listOf("Please wait, fetching channel's playlists"))
                                                    else
                                                        printToChat(listOf("Please wait, fetching user's playlists"))

                                                    service.fetchUser(link).playlists.lists.forEach { playlist ->
                                                        links.addAll(
                                                            if (trackCache.any { it.first == link }) {
                                                                println("Tracks found in cache!")
                                                                trackCache.first { it.first == link }.second.trackList
                                                            } else {
                                                                service.fetchPlaylistTracks(playlist.link).trackList
                                                            }
                                                                .map { track -> track.link }
                                                        )
                                                    }
                                                }

                                                LinkType.LIKES -> {
                                                    links.remove(link)
                                                    if (service is SoundCloud) {
                                                        printToChat(listOf("Please wait, fetching user's likes"))
                                                        links.addAll(
                                                            if (trackCache.any { it.first == link }) {
                                                                println("Tracks found in cache!")
                                                                trackCache.first { it.first == link }.second.trackList
                                                            } else {
                                                                service.fetchUserLikes(link).trackList
                                                            }
                                                                .map { track -> track.link }
                                                        )
                                                    }
                                                }

                                                LinkType.REPOSTS -> {
                                                    links.remove(link)
                                                    if (service is SoundCloud) {
                                                        printToChat(listOf("Please wait, fetching user's reposts"))
                                                        links.addAll(
                                                            if (trackCache.any { it.first == link }) {
                                                                println("Tracks found in cache!")
                                                                trackCache.first { it.first == link }.second.trackList
                                                            } else {
                                                                service.fetchUserReposts(link).trackList
                                                            }
                                                                .map { track -> track.link }
                                                        )
                                                    }
                                                }

                                                LinkType.SHOW -> {
                                                    links.remove(link)
                                                    if (service is Spotify) {
                                                        printToChat(listOf("Please wait, fetching podcast episodes"))
                                                        links.addAll(
                                                            if (trackCache.any { it.first == link }) {
                                                                println("Tracks found in cache!")
                                                                trackCache.first { it.first == link }.second.trackList
                                                            } else {
                                                                service.fetchShow(link).episodes.toTrackList().trackList
                                                            }
                                                                .map { episode -> episode.link }
                                                        )
                                                    }
                                                }

                                                LinkType.VIDEO, LinkType.EPISODE, LinkType.TRACK, LinkType.OTHER -> {}
                                            }
                                        }
                                        val currentList = songQueue.getQueue()
                                        val messages: ArrayList<String> = ArrayList()
                                        //get a list of the tracks to delete
                                        val tracksToDelete = ArrayList<Track>()
                                        println("Filtering tracks from queue")
                                        if (currentList.size > 100000)
                                            printToChat(
                                                listOf(
                                                    "Are you insane? Your queue has ${currentList.size} tracks!",
                                                    "This might take some time..."
                                                )
                                            )

                                        currentList.asFlow().filter { track ->
                                            links.any { link ->
                                                if (allArtistTracks) {
                                                    link == track.link || track.artists.artists.any { artist -> artist.link == link }
                                                } else {
                                                    link == track.link
                                                }
                                            }
                                        }.flowOn(Default).collect { track ->
                                            tracksToDelete.add(track)
                                        }
                                        if (tracksToDelete.isNotEmpty()) {
                                            val trackAmount = tracksToDelete.distinct().size
                                            printToChat(listOf("$trackAmount track${if (trackAmount != 1) "s" else ""} found."))
                                            val msg = StringBuilder()
                                            if (allTracks) {
                                                msg.appendLine(
                                                    "You used the -a/--all flag, so deleting all matches of track${
                                                        if (tracksToDelete.size > 1) "s" else ""
                                                    }:"
                                                )
                                            } else {
                                                msg.appendLine("Deleting track${if (tracksToDelete.size > 1) "s" else ""}:")
                                            }
                                            for (track in tracksToDelete.distinct()) {
                                                if (allTracks) {
                                                    msg.appendLine(track.toShortString())
                                                    songQueue.deleteTracks(tracksToDelete.distinct())
                                                } else {
                                                    //check if there are multiple instances of the track in the queue.
                                                    if (currentList.filter { it.link == track.link }.size > 1) {
                                                        val duplicates = ArrayList<Int>()
                                                        msg.appendLine(
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
                                                        if (firstMatchOnly) {
                                                            msg.appendLine("However, you used the -f/--first flag, so deleting only the first match")
                                                            songQueue.deleteTrack(duplicates.first())
                                                        } else {
                                                            msg.appendLine(
                                                                "Select the track(s) you want to delete, then run ${commandList.commandList["queue-delete"]} with the position(s) specified, for example:\n" +
                                                                        "${commandList.commandList["queue-delete"]} ${duplicates.first()}\n" +
                                                                        "Or if you want to delete multiple tracks:\n" +
                                                                        "${commandList.commandList["queue-delete"]} ${
                                                                            duplicates.subList(0, 2)
                                                                                .let { trackPositions ->
                                                                                    val positionsText = StringBuilder()
                                                                                    for (pos in trackPositions) {
                                                                                        positionsText.append("$pos, ")
                                                                                    }
                                                                                    positionsText.toString()
                                                                                        .substringBeforeLast(",")
                                                                                }
                                                                        }\n\n"
                                                            )
                                                        }
                                                    } else {
                                                        //no duplicates found, delete the track
                                                        msg.appendLine(track.toShortString())
                                                        songQueue.deleteTrack(track)
                                                    }
                                                }
                                            }
                                            messages.add(msg.toString())
                                        } else {
                                            if (positions.isEmpty())
                                                messages.add("No matches found in the queue!")
                                        }
                                        //delete tracks at specified positions
                                        if (positions.isNotEmpty()) {
                                            val posAmount = positions.distinct().size
                                            messages.add("Deleting track${if (posAmount != 1) "s" else ""}.")
                                            songQueue.deleteTracks(positions.distinct())
                                        }
                                        printToChat(messages)
                                    }
                                } else {
                                    printToChat(listOf("The queue is empty, not doing anything..."))
                                }
                            }
                            //queue-clear command
                            commandString.contains("^${commandList.commandList["queue-clear"]}$".toRegex()) -> {
                                songQueue.clearQueue()
                                return Pair(
                                    if (songQueue.getQueue().isEmpty()) {
                                        printToChat(listOf("Cleared the queue."))
                                        trackCache.clear()
                                        println("Cleared the track cache.")
                                        commandListener.onCommandExecuted(commandString, "Cleared the queue.")
                                        commandJob.complete()
                                        true
                                    } else {
                                        printToChat(listOf("Could not clear the queue!"))
                                        commandListener.onCommandExecuted(commandString, "Could not clear the queue!")
                                        commandJob.complete()
                                        false
                                    },
                                    null
                                )
                            }
                            //queue-shuffle command
                            commandString.contains("^${commandList.commandList["queue-shuffle"]}$".toRegex()) -> {
                                songQueue.shuffleQueue()
                                printToChat(listOf("Shuffled the queue."))
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //queue-skip command
                            commandString.contains("^${commandList.commandList["queue-skip"]}$".toRegex()) -> {
                                voteSkipUsers.clear()
                                songQueue.skipSong()
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //queue-voteskip command
                            commandString.contains("^${commandList.commandList["queue-voteskip"]}$".toRegex()) -> {
                                val userList = ArrayList<String>()
                                //get channel list
                                val channelList = client.getChannelList().map {
                                    Pair(
                                        it.substringAfter("channel_name=").substringBefore(" "),
                                        it.substringAfter("cid=").substringBefore(" ")
                                    )
                                }

                                //get users in current channel
                                for (channel in channelList) {
                                    if (channel.first == botSettings.channelName.substringAfterLast("/")) {
                                        val tsUserListData = client.getClientList()
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
                                val currentSong = songQueue.nowPlaying()
                                if (voteSkipUsers.any { !it.second }) {
                                    printToChat(
                                        listOf("\nAll users have not voted yet.\nWaiting for more votes...")
                                    )
                                    commandJob.complete()
                                    return Pair(false, currentSong)
                                } else {
                                    printToChat(listOf("Skipping current song."))
                                    voteSkipUsers.clear()
                                    songQueue.skipSong()
                                    commandListener.onCommandExecuted(
                                        commandString,
                                        "Skipping current song",
                                        currentSong
                                    )
                                    commandJob.complete()
                                    return Pair(true, currentSong)
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
                                        }.map { Link(removeTags(it)).clean() }
                                    } else {
                                        emptyList()
                                    }
                                val noArgsCommand = removeTags(
                                    commandString.replace(
                                        ("(${commandList.commandList["queue-move"]}\\s+)|" +
                                                "(\\s*(${availableArgs.joinToString("|")})(\\s*[0-9]+)?\\s*)").toRegex(),
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
                                                    newArgs = Link(newArgs).clean().link.replace(
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
                                                        newArgs = Link(newArgs).clean().link.replace(
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
                                                currentList.indexOfFirst { "${it.link}" == "$link" }.let {
                                                    indexList.add(Pair(link, listOf(it)))
                                                }
                                            }
                                            if (indexList.isNotEmpty())
                                                links.forEachIndexed { index, link ->
                                                    newArgs = Link(newArgs).clean().link.replace(
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
                                        return Pair(false, newPosition)
                                    }

                                    newPosition < 0 -> {
                                        printToChat(
                                            listOf("What were you thinking?", "You can't do that.")
                                        )
                                        commandJob.complete()
                                        return Pair(false, newPosition)
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
                                        return Pair(
                                            if (
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
                                            },
                                            newPosition
                                        )
                                    }
                                }
                            }
                            //queue-stop command
                            commandString.contains("^${commandList.commandList["queue-stop"]}$".toRegex()) -> {
                                songQueue.stopQueue()
                                printToChat(listOf("Stopped the queue."))
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //queue-status command
                            commandString.contains("^${commandList.commandList["queue-status"]}$".toRegex()) -> {
                                val statusMessage = StringBuilder()
                                statusMessage.append("Queue Status: ")
                                var stateKnown = false
                                val state = songQueue.getState()
                                when (state) {
                                    SongQueue.State.QUEUE_PLAYING -> statusMessage.appendLine("Playing")
                                        .also { stateKnown = true }

                                    SongQueue.State.QUEUE_PAUSED -> statusMessage.appendLine("Paused")
                                        .also { stateKnown = true }

                                    SongQueue.State.QUEUE_STOPPED -> statusMessage.appendLine("Stopped")
                                        .also { stateKnown = true }
                                }
                                val trackLength = songQueue.getTrackLength()
                                if (trackLength > 0) {
                                    val trackPosition = songQueue.getTrackPosition()
                                    statusMessage.appendLine("Track Position: $trackPosition/$trackLength seconds.")
                                }
                                printToChat(statusMessage.toString().lines())
                                commandListener.onCommandExecuted(commandString, statusMessage.toString())
                                commandJob.complete()
                                return Pair(stateKnown, state)
                            }
                            //queue-nowplaying command
                            commandString.contains("^${commandList.commandList["queue-nowplaying"]}$".toRegex()) -> {
                                val currentTrack = songQueue.nowPlaying()
                                if (currentTrack.title.name.isNotEmpty()) {
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
                                return Pair(true, currentTrack)
                            }
                            //queue-pause command
                            commandString.contains("^${commandList.commandList["queue-pause"]}$".toRegex()) -> {
                                songQueue.pausePlayback()
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //queue-resume command
                            commandString.contains("^${commandList.commandList["queue-resume"]}$".toRegex()) -> {
                                songQueue.resumePlayback()
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //queue-repeat command
                            commandString.contains("^${commandList.commandList["queue-repeat"]}\\s*((-a|--amount=?)\\s*[0-9]+)?$".toRegex()) -> {
                                val amount = if (commandString.contains("(-a|--amount=?)\\s*[0-9]+".toRegex())) {
                                    commandString.replace("^${commandList.commandList["queue-repeat"]}\\s*(-a|--amount=?)\\s*".toRegex(), "").toInt()
                                } else {
                                    1
                                }
                                val tracks = List(amount) { songQueue.nowPlaying().link.link }.joinToString(",")
                                executeCommand("${commandList.commandList["queue-playnext"]} $tracks")
                            }
                            //search command
                            commandString.contains("^${commandList.commandList["search"]}\\s+".toRegex()) -> {
                                val searchCommand = commandList.commandList["search"]
                                val serviceText = commandString.replace("^$searchCommand\\s+".toRegex(), "")
                                    .replace("\\s+.*$".toRegex(), "")
                                val service = when (serviceText) {
                                    "sc", "soundcloud" -> Service.ServiceType.SOUNDCLOUD
                                    "sp", "spotify" -> Service.ServiceType.SPOTIFY
                                    "yt", "youtube" -> Service.ServiceType.YOUTUBE
                                    else -> Service.ServiceType.OTHER
                                }
                                val searchType = SearchType(
                                    commandString.replace("^$searchCommand\\s+$serviceText\\s+".toRegex(), "")
                                        .replace("\\s+.*$".toRegex(), "")
                                )
                                if (
                                    when (service) {
                                        Service.ServiceType.SPOTIFY -> spotify.supportedSearchTypes.contains(searchType.getType())
                                        Service.ServiceType.YOUTUBE -> youTube.supportedSearchTypes.contains(searchType.getType())
                                        Service.ServiceType.SOUNDCLOUD -> soundCloud.supportedSearchTypes.contains(
                                            searchType.getType()
                                        )

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
                                        Service.ServiceType.SOUNDCLOUD -> soundCloud.search(
                                            searchType,
                                            searchQuery,
                                            limit
                                        )

                                        Service.ServiceType.SPOTIFY -> spotify.search(
                                            searchType,
                                            searchQuery,
                                            limit
                                        )

                                        Service.ServiceType.YOUTUBE -> youTube.search(
                                            searchType,
                                            searchQuery,
                                            limit
                                        )

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
                                        Pair(true, results)
                                    } else {
                                        printToChat(listOf("No results found!"))
                                        commandListener.onCommandExecuted(commandString, results.toString(), results)
                                        commandJob.complete()
                                        Pair(false, results)
                                    }
                                } else {
                                    printToChat(
                                        listOf("Search type not supported! Run ${commandList.commandList["help"]} $searchCommand to see more information on this command.")
                                    )
                                    commandListener.onCommandExecuted(commandString, "Not supported", searchType)
                                    commandJob.complete()
                                    return Pair(false, searchType)
                                }
                            }
                            //info command
                            commandString.contains("^${commandList.commandList["info"]}\\s+".toRegex()) -> {
                                val services = listOf("sp", "spotify", "yt", "youtube", "sc", "soundcloud")
                                val links = if (commandString.contains(
                                        "^${commandList.commandList["info"]}\\s+(${
                                            services.joinToString("|")
                                        })\\s+\\w+\\s+".toRegex()
                                    )
                                ) {
                                    latestMsgUsername = "__console__"
                                    executeCommand("${commandList.commandList["search"]}${commandString.substringAfter("${commandList.commandList["info"]}")}").second.let { results ->
                                        latestMsgUsername = ""
                                        if (results is SearchResults)
                                            listOf(results.results.first().link)
                                        else
                                            emptyList()
                                    }
                                } else {
                                    removeTags(
                                        commandString.replace("${commandList.commandList["info"]}\\s+".toRegex(), "")
                                    ).split("\\s*,\\s*".toRegex()).map { Link(it) }
                                }
                                val output = ArrayList<Any>()
                                var success = false
                                for (link in links) {
                                    @Suppress("IMPLICIT_CAST_TO_ANY")
                                    val data: Any? = when (link.serviceType()) {
                                        Service.ServiceType.SPOTIFY -> when (link.linkType()) {
                                            LinkType.TRACK -> spotify.fetchTrack(link)
                                            LinkType.ALBUM -> spotify.fetchAlbum(link)
                                            LinkType.PLAYLIST -> spotify.fetchPlaylist(link)
                                            LinkType.ARTIST -> spotify.fetchArtist(link)
                                            LinkType.SHOW -> spotify.fetchShow(link)
                                            LinkType.EPISODE -> spotify.fetchEpisode(link)
                                            LinkType.USER -> spotify.fetchUser(link)
                                            else -> null
                                        }.also { if (it != null) output.add(it) }

                                        Service.ServiceType.YOUTUBE -> when (link.linkType()) {
                                            LinkType.VIDEO -> youTube.fetchVideo(link)
                                            LinkType.PLAYLIST -> youTube.fetchPlaylist(link)
                                            LinkType.CHANNEL -> youTube.fetchChannel(link)
                                            else -> null
                                        }.also { if (it != null) output.add(it) }

                                        Service.ServiceType.SOUNDCLOUD -> when (link.linkType()) {
                                            LinkType.TRACK -> soundCloud.fetchTrack(link)
                                            LinkType.ALBUM -> soundCloud.fetchAlbum(link)
                                            LinkType.PLAYLIST -> soundCloud.fetchPlaylist(link)
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
                                return Pair(success, links)
                            }
                            //goto command
                            commandString.contains("^${commandList.commandList["goto"]}\\s+\\S+".toRegex()) -> {
                                val password = if (commandString.contains("-p\\s+\\S+".toRegex())) {
                                    commandString.replace("^.*-p\\s*".toRegex(), "")
                                } else { "" }.replace("(^\"|\"$)".toRegex(), "")
                                val channelToJoin = commandString.replace("^${commandList.commandList["goto"]}\\s+".toRegex(), "")
                                    .replace("\\s*-p\\s*\"?.*\"?(\\s+|$)".toRegex(), "")
                                    .replace("(^\"|\"$)".toRegex(), "")
                                client.joinChannel(channelToJoin, password)
                            }
                            //return command
                            commandString.contains("^${commandList.commandList["return"]}".toRegex()) -> {
                                client.joinChannel()
                            }
                            //sp-pause command
                            commandString.contains("^${commandList.commandList["sp-pause"]}$".toRegex()) -> {
                                playerctl(botSettings.spotifyPlayer, "pause")
                                delay(1000)
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-resume & sp-play command
                            commandString.contains("^(${commandList.commandList["sp-resume"]}|${commandList.commandList["sp-play"]})$".toRegex()) -> {
                                playerctl(botSettings.spotifyPlayer, "play")
                                delay(1000)
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-stop command
                            //Stop spotify playback
                            commandString.contains("^${commandList.commandList["sp-stop"]}$".toRegex()) -> {
                                when (val player = botSettings.spotifyPlayer) {
                                    "ncspot" -> {
                                        playerctl(player, "stop")
                                        commandRunner.runCommand("tmux kill-session -t $player")
                                    }

                                    "spotify" -> {
                                        commandRunner.runCommand("pkill -9 $player")
                                    }

                                    else -> {
                                        playerctl(player, "stop")
                                    }
                                }
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-skip & sp-next command
                            commandString.contains("^(${commandList.commandList["sp-skip"]}|${commandList.commandList["sp-next"]})$".toRegex()) -> {
                                playerctl(botSettings.spotifyPlayer, "next")
                                delay(1000)
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-prev command
                            commandString.contains("^${commandList.commandList["sp-prev"]}$".toRegex()) -> {
                                playerctl(botSettings.spotifyPlayer, "previous")
                                delay(100)
                                playerctl(botSettings.spotifyPlayer, "previous")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-playsong command
                            //Play Spotify song based on link or URI
                            commandString.contains("^${commandList.commandList["sp-playsong"]}\\s+".toRegex()) -> {
                                if (message.substringAfter("${commandList.commandList["sp-playsong"]}")
                                        .isNotEmpty()
                                ) {
                                    startSpotifyPlayer()
                                    println("Playing song...")
                                    playerctl(
                                        botSettings.spotifyPlayer, "open",
                                        "spotify:track:" + if (
                                            Link(removeTags(commandString.substringAfter("${commandList.commandList["sp-playsong"]} "))).link
                                                .startsWith("https://open.spotify.com/track")
                                        ) {
                                            removeTags(message)
                                                .substringAfterLast("/")
                                                .substringBefore("?")
                                        } else {
                                            message.split(" ".toRegex())[1]
                                                .split("track:".toRegex())[1]
                                        }
                                    )
                                    commandJob.complete()
                                    return Pair(true, null)
                                } else {
                                    printToChat(listOf("Error! Please provide a song to play!"))
                                    commandJob.complete()
                                    return Pair(false, null)
                                }
                            }
                            //sp-playlist command
                            //Play Spotify playlist based on link or URI
                            commandString.contains("^${commandList.commandList["sp-playlist"]}\\s+".toRegex()) -> {
                                startSpotifyPlayer()
                                playerctl(
                                    botSettings.spotifyPlayer, "open", "spotify:" +
                                            if (message.split(" ".toRegex())[1].startsWith("spotify:user:")
                                                && message.split(" ".toRegex())[1].contains(":playlist:")
                                            ) {
                                                "user:${message.split(" ".toRegex())[1].split(":".toRegex())[2]}" +
                                                        ":playlist:${message.split(":".toRegex()).last()}"
                                            } else if (message.split(" ".toRegex())[1].startsWith("spotify:playlist")) {
                                                "playlist:${message.substringAfter("playlist:")}"
                                            } else if (removeTags(message.substringAfter(" ")).startsWith("https://open.spotify.com/")) {
                                                "playlist:${
                                                    removeTags(message).substringAfter("playlist/").substringBefore("?")
                                                }"
                                            } else ""
                                )
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-playalbum command
                            commandString.contains("^${commandList.commandList["sp-playalbum"]}\\s+".toRegex()) -> {
                                startSpotifyPlayer()
                                playerctl(
                                    botSettings.spotifyPlayer, "open", "spotify:album:" +
                                            if (message.split(" ".toRegex())[1].startsWith("spotify:album")) {
                                                message.substringAfter("album:")
                                            } else if (removeTags(message.substringAfter(" ")).startsWith("https://open.spotify.com/")) {
                                                removeTags(message).substringAfter("album/").substringBefore("?")
                                            } else ""
                                )
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sp-nowplaying command
                            commandString.contains("^${commandList.commandList["sp-nowplaying"]}$".toRegex()) -> {
                                val lines = StringBuilder()
                                lines.appendLine("Now playing on Spotify:")
                                val nowPlaying = playerctl(botSettings.spotifyPlayer, "metadata")
                                    .outputText.lines()
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
                                return Pair(true, lines)
                            }
                            //yt-pause command
                            commandString.contains("^${commandList.commandList["yt-pause"]}$".toRegex()) -> {
                                playerctl("mpv", "pause")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //yt-resume and yt-play commands
                            commandString.contains("^(${commandList.commandList["yt-resume"]}|${commandList.commandList["yt-play"]})$".toRegex()) -> {
                                playerctl("mpv", "play")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //yt-stop command
                            commandString.contains("^${commandList.commandList["yt-stop"]}$".toRegex()) -> {
                                playerctl("mpv", "stop")
                                commandJob.complete()
                                return Pair(true, null)
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
                                                    " --ytdl \"$ytLink\" --volume=${botSettings.ytVolume}",
                                            inheritIO = true,
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                    }
                                    commandListener.onCommandExecuted(commandString, "Playing song", ytLink)
                                    commandJob.complete()
                                    return Pair(true, ytLink)
                                } else {
                                    commandListener.onCommandExecuted(commandString, "Couldn't play song", ytLink)
                                    commandJob.complete()
                                    return Pair(false, ytLink)
                                }
                            }
                            //yt-nowplaying command
                            commandString.contains("^${commandList.commandList["yt-nowplaying"]}$".toRegex()) -> {
                                val metadata = playerctl("mpv", "metadata")
                                if (metadata.errorText.isEmpty()) {
                                    val nowPlaying = youTube.fetchVideo(
                                        Link(
                                            metadata.outputText.lines()
                                                .first { it.contains("xesam:url") }
                                                .replace("(^.+\\s+\"?|\"?$)".toRegex(), "")
                                        )
                                    )
                                    printToChat(listOf("Now playing from YouTube:\n$nowPlaying"))
                                    commandJob.complete()
                                    return Pair(true, nowPlaying)
                                } else {
                                    println("Failed to fetch metadata!\n${metadata.errorText}")
                                    commandJob.complete()
                                    return Pair(true, metadata.errorText)
                                }
                            }
                            //sc-pause command
                            commandString.contains("^${commandList.commandList["sc-pause"]}$".toRegex()) -> {
                                playerctl("mpv", "pause")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sc-resume and sc-play commands
                            commandString.contains("^(${commandList.commandList["sc-resume"]}|${commandList.commandList["sc-play"]})$".toRegex()) -> {
                                playerctl("mpv", "play")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sc-stop command
                            commandString.contains("^${commandList.commandList["sc-stop"]}$".toRegex()) -> {
                                playerctl("mpv", "stop")
                                commandJob.complete()
                                return Pair(true, null)
                            }
                            //sc-playsong command
                            commandString.contains("^${commandList.commandList["sc-playsong"]}\\s+".toRegex()) -> {
                                val scLink = Link(removeTags(message.substringAfter(" ")))
                                if (scLink.link.isNotEmpty()) {
                                    launch {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video --ytdl \"$scLink\" --volume=${botSettings.scVolume}",
                                            inheritIO = true,
                                            ignoreOutput = true,
                                            printCommand = true
                                        )
                                    }
                                    commandListener.onCommandExecuted(commandString, "Playing song", scLink)
                                    commandJob.complete()
                                    return Pair(true, scLink)
                                } else {
                                    commandListener.onCommandExecuted(commandString, "Couldn't play song", scLink)
                                    commandJob.complete()
                                    return Pair(false, scLink)
                                }

                            }
                            //sc-nowplaying command
                            commandString.contains("^${commandList.commandList["sc-nowplaying"]}$".toRegex()) -> {
                                val metadata = playerctl("mpv", "metadata")
                                if (metadata.errorText.isEmpty()) {
                                    val nowPlaying = youTube.fetchVideo(
                                        Link(
                                            metadata.outputText.lines()
                                                .first { it.contains("xesam:url") }
                                                .replace("(^.+\\s+\"?|\"?$)".toRegex(), "")
                                        )
                                    )
                                    printToChat(listOf("Now playing from SoundCloud:\n$nowPlaying"))
                                    commandJob.complete()
                                    return Pair(true, nowPlaying)
                                } else {
                                    println("Failed to fetch metadata!\n${metadata.errorText}")
                                    commandJob.complete()
                                    return Pair(true, metadata.errorText)
                                }
                            }

                            else -> {
                                commandJob.complete()
                                return Pair(false, null)
                            }
                        }
                    } else {
                        printToChat(
                            listOf("Command not found! Try ${commandList.commandList["help"]} to see available commands.")
                        )
                        commandJob.complete()
                        return Pair(false, "Command Not found")
                    }
                    commandJob.complete()
                    return Pair(false, null)
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
                                if (executeCommand(cmd).first) {
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

    //Send a message to the current channel's chat
    private fun printToChat(messages: List<String>) {
        if (latestMsgUsername == "__console__") {
            messages.forEach { println(it) }
        } else {
            for (message in messages) {
                client.sendMsgToChannel(message)
            }
        }
    }

    private suspend fun chatUpdated(line: String, userName: String = "") {
        when (client) {
            is TeamSpeak -> withContext(IO) {
                parseLine(line)
                onChatUpdateListener.onChatUpdated(ChatUpdate(userName, line))
            }

            is OfficialTSClient -> when (client.channelFile.extension) {
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
