package ts3_musicbot.util

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.util.*
import kotlin.collections.ArrayList
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.services.Service
import java.io.File

private var songQueue = Collections.synchronizedList(ArrayList<Track>())

class SongQueue(
    botSettings: BotSettings,
    teamSpeak: Any,
    private val playStateListener: PlayStateListener
) : PlayStateListener {
    private var queueState = State.QUEUE_STOPPED

    enum class State {
        QUEUE_PLAYING,
        QUEUE_PAUSED,
        QUEUE_STOPPED
    }

    private fun setState(state: State) {
        synchronized(queueState) {
            queueState = state
        }
    }

    fun getState() = synchronized(queueState) { queueState }

    private val trackPlayer = TrackPlayer(botSettings, teamSpeak, this)

    private fun setCurrent(track: Track) = synchronized(trackPlayer) { trackPlayer.track = track }
    private fun getCurrent() = synchronized(trackPlayer) { trackPlayer.track }

    /**
     * Adds track to queue
     * @param track song's link
     * @param position position in which the song should be added.
     */
    fun addToQueue(track: Track, position: Int? = null): Boolean {
        synchronized(songQueue) {
            if (position != null)
                songQueue.add(position, track)
            else
                songQueue.add(track)

            return songQueue.contains(track)
        }
    }

    /**
     * Adds a list of tracks to the queue
     * @param trackList list of tracks to add to the queue
     * @param position position in which the tracks should be added
     */
    fun addAllToQueue(trackList: TrackList, position: Int? = null): Boolean {
        synchronized(songQueue) {
            if (position != null)
                songQueue.addAll(position, trackList.trackList)
            else
                songQueue.addAll(trackList.trackList)

            return songQueue.containsAll(trackList.trackList)
        }
    }

    /**
     * Clear the queue
     */
    fun clearQueue() {
        synchronized(songQueue) {
            songQueue.clear()
        }
    }

    /**
     * Delete a track from the queue.
     * @param trackOrPosition the Track or position from where to remove a track.
     */
    fun deleteTrack(trackOrPosition: Any) {
        when (trackOrPosition) {
            is Track -> synchronized(songQueue) { songQueue.remove(trackOrPosition) }
            is Int -> synchronized(songQueue) { songQueue.removeAt(trackOrPosition) }
        }
    }

    /**
     * Delete tracks from the queue.
     * @param tracksOrPositions list of Tracks or positions of tracks to be deleted
     */
    fun deleteTracks(tracksOrPositions: Any) {
        val tracks = ArrayList<Track>()
        val positions = ArrayList<Int>()
        if (tracksOrPositions is List<*>) {
            for (item in tracksOrPositions) {
                when (item) {
                    is Track -> tracks.add(item)
                    is Int -> positions.add(item)
                }
            }
        }
        if (tracks.isNotEmpty())
            synchronized(songQueue) { songQueue.removeAll(tracks) }
        if (positions.isNotEmpty()) {
            positions.sortDescending()
            for (position in positions) {
                synchronized(songQueue) { songQueue.removeAt(position) }
            }
        }
    }

    /**
     * Moves a track to a new position
     * @param trackPosition Position of the track to move
     * @param newPosition New position of the track
     */
    private fun moveTrack(trackPosition: Int, newPosition: Int) {
        synchronized(songQueue) {
            if (newPosition < songQueue.size && newPosition >= 0) {
                val newTrack = songQueue[trackPosition]
                songQueue.removeAt(trackPosition)
                if (trackPosition < newPosition)
                    songQueue.add(newPosition - 1, newTrack)
                else
                    songQueue.add(newPosition, newTrack)
            }
        }
    }

    /**
     * Moves given tracks to a new position.
     * Whenever these tracks are in a position that will be affected by the moving of the previous item,
     * offsets are applied accordingly.
     * @param positions an ArrayList<Int> containing the positions of the tracks to move.
     * @param newPosition new position where to move tracks.
     * @return returns true if newPosition has been offset due to moving a track
     */
    fun moveTracks(positions: ArrayList<Int>, newPosition: Int): Boolean {
        var newPosIsOffset = false
        var newOffset = 0
        val trackPositions: ArrayList<Int> = positions.clone().let {
            it as ArrayList<*>
            it.map { pos -> pos as Int }
        } as ArrayList<Int>
        //loop through positions and move them to newPosition
        for (index in trackPositions.indices) {
            moveTrack(trackPositions[index], newPosition + newOffset)
            if (trackPositions[index] >= newPosition)
                newOffset++
            else
                newPosIsOffset = true

            // if moving more than one track,
            // apply appropriate offsets for remaining tracks to move
            if (index != trackPositions.lastIndex) {
                for (i in trackPositions.indices) {
                    val pos = trackPositions[i]
                    if (pos < trackPositions[index] && pos > newPosition) {
                        trackPositions.removeAt(i)
                        trackPositions.add(i, pos + 1)
                    } else if (pos > trackPositions[index] && pos < newPosition) {
                        trackPositions.removeAt(i)
                        trackPositions.add(i, pos - 1)
                    }
                }
            }
        }
        return newPosIsOffset
    }

    fun getQueue(): ArrayList<Track> = synchronized(songQueue) { songQueue }.toMutableList() as ArrayList<Track>

    fun nowPlaying(): Track {
        return getCurrent()
    }

    fun shuffleQueue() {
        synchronized(songQueue) {
            songQueue.shuffle()
        }
    }

    fun skipSong() {
        when (getState()) {
            State.QUEUE_PLAYING, State.QUEUE_PAUSED, State.QUEUE_STOPPED -> {
                CoroutineScope(IO).launch {
                    if (getQueue().isNotEmpty()) {
                        println("Skipping current track.")
                        stopQueue()
                        delay(1000)
                        playNext()
                    } else println("Track cannot be skipped.")
                }
            }
        }
    }

    //starts playing song queue
    fun startQueue() {
        if (getState() == State.QUEUE_STOPPED) {
            println("Starting queue.")
            playNext()
        } else {
            println("Song queue already active!")
        }
    }

    fun resumePlayback() {
        synchronized(trackPlayer) { trackPlayer.resumeTrack() }
    }

    fun pausePlayback() {
        synchronized(trackPlayer) { trackPlayer.pauseTrack() }
    }

    fun stopQueue() {
        synchronized(trackPlayer) { trackPlayer.stopTrack() }
        setCurrent(Track())
    }


    /**
     * Plays the next track in the queue.
     **/
    private fun playNext() {
        fun playTrack(track: Track) {
            synchronized(trackPlayer) {
                setCurrent(track)
                trackPlayer.startTrack()
            }
        }
        synchronized(songQueue) {
            if (songQueue.isNotEmpty()) {
                var firstTrack = songQueue.first()
                when (firstTrack.serviceType) {
                    Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> {
                        //check if youtube-dl is able to download the track
                        while (songQueue.isNotEmpty() && !CommandRunner().runCommand(
                                "youtube-dl --extract-audio --audio-format best --audio-quality 0 " +
                                        "--cookies youtube-dl.cookies --force-ipv4 --age-limit 21 --geo-bypass -s \"${firstTrack.link}\"",
                                printOutput = false,
                                printErrors = false
                            ).first.outputText.lines().last()
                                .contains("\\[(info|youtube)] ${firstTrack.link.getId()}: Downloading ([0-9]+ format\\(s\\):|webpage|API JSON)".toRegex())
                        ) {
                            println(
                                "youtube-dl cannot download this track! Skipping...\n" +
                                        "Check if a newer version of youtube-dl is available and update it to the latest one if you already haven't."
                            )
                            songQueue.removeFirst()
                            if (songQueue.isNotEmpty())
                                firstTrack = songQueue.first()
                            else
                                break
                        }
                        if (songQueue.isNotEmpty()) {
                            playTrack(firstTrack)
                        } else {
                            println("Song queue is empty!")
                            stopQueue()
                        }
                    }

                    else -> playTrack(firstTrack)
                }
            } else {
                println("Song queue is empty!")
                stopQueue()
            }
        }
    }


    override fun onTrackEnded(player: String, track: Track) {
        playStateListener.onTrackEnded(player, track)
        println("Track ended.")
        when (queueState) {
            State.QUEUE_PLAYING, State.QUEUE_PAUSED -> playNext()
            else -> {}
        }
    }

    override fun onTrackPaused(player: String, track: Track) {
        setState(State.QUEUE_PAUSED)
        println("Queue paused.")
        playStateListener.onTrackPaused(player, track)
    }

    override fun onTrackResumed(player: String, track: Track) {
        setState(State.QUEUE_PLAYING)
        println("Queue resumed.")
        playStateListener.onTrackResumed(player, track)
    }

    override fun onTrackStarted(player: String, track: Track) {
        setState(State.QUEUE_PLAYING)
        setCurrent(track)
        deleteTrack(track)
        println("Track started.")
        playStateListener.onTrackStarted(player, track)
    }

    override fun onTrackStopped(player: String, track: Track) {
        setState(State.QUEUE_STOPPED)
        println("Queue stopped.")
        playStateListener.onTrackStopped(player, track)
    }

    override fun onAdPlaying() {}

    private class TrackPlayer(
        val botSettings: BotSettings,
        val teamSpeak: Any,
        val listener: PlayStateListener
    ) {
        var trackJob = Job()
        var trackPositionJob = Job()

        var trackPosition = 0

        var track = Track()
        private val commandRunner = CommandRunner()

        /**
         * get the player suitable for playing the current track
         */
        fun getPlayer() = when (track.serviceType) {
            Service.ServiceType.SPOTIFY -> botSettings.spotifyPlayer
            Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> "mpv"
            else -> ""
        }

        fun currentUrl(): String {
            val metadata = playerctl(getPlayer(), "metadata")
            return if (metadata.second.errorText.isEmpty()) {
                metadata.first.outputText.lines()
                    .first { it.contains("xesam:url") }.replace("(^.+\\s+\"?|\"?$)".toRegex(), "")
            } else {
                ""
            }
        }

        fun refreshPulseAudio() {
            //if using pulseaudio, refresh it using pasuspender
            if (
                commandRunner.runCommand("command -v pasuspender", printOutput = false, printErrors = false)
                    .first.outputText.isNotEmpty()
            )
                commandRunner.runCommand("pasuspender true", printOutput = false, printErrors = false)
        }

        fun checkTeamSpeakAudio(trackJob: Job) {
            //check if the teamspeak is outputting audio, if not, restart the client.
            if (teamSpeak is OfficialTSClient)
                if (!teamSpeak.audioIsWorking()) {
                    println("TeamSpeak audio is broken, restarting client.")
                    runBlocking(trackJob + IO) {
                        teamSpeak.restartClient()
                    }
                }
        }

        fun startTrack() {
            synchronized(this) {
                trackPositionJob.cancel()
                trackPosition = 0
                trackJob.cancel()
                trackJob = Job()
                refreshPulseAudio()
                checkTeamSpeakAudio(trackJob)
            }

            fun playerStatus() = playerctl(getPlayer(), "status")

            suspend fun startSpotifyPlayer(shouldSetPrefs: Boolean = true) {
                fun killCommand() = when (botSettings.spotifyPlayer) {
                    "spotify" -> commandRunner.runCommand("pkill -9 spotify", ignoreOutput = true)
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

                //set the spotify user's settings
                fun setPrefs() {
                    //autoplay needs to be disabled so the spotify client doesn't start playing stuff on its own.
                    //friend feed, notifications, release announcements etc are disabled just for performance.
                    //volume normalization is enabled to get more consistent volume between tracks.
                    if (botSettings.spotifyPlayer == "spotify") {
                        val usersPath = System.getProperty("user.home") + "/.config/spotify/Users"
                        val users = File(usersPath).listFiles()
                        if (users != null && users.isNotEmpty()) {
                            for (user in users) {
                                val prefsFile = File("${user.listFiles()?.first { it.name == "prefs" }}")
                                if (prefsFile.exists()) {
                                    val newPrefs = StringBuilder()
                                    val prefs = emptyMap<String, String>().toMutableMap()
                                    prefsFile.readLines().forEach {
                                        val split = it.split("=".toRegex())
                                        prefs[split.first()] = split.last()
                                    }

                                    prefs["ui.right_panel_content"] = "0"
                                    listOf(
                                        "ui.track_notifications_enabled",
                                        "ui.show_friend_feed",
                                        "app.player.autoplay"
                                    ).forEach { prefs[it] = "false" }

                                    listOf(
                                        "ui.hide_hpto",
                                        "audio.normalize_v2"
                                    ).forEach { prefs[it] = "true" }

                                    //set normalization volume level.
                                    //0 = quiet, 1 = normal, 2 = loud.
                                    prefs["audio.loudness.environment"] = "1"

                                    //set audio streaming quality. 4 is the highest, which is equivalent to 320kbps bitrate.
                                    //0 = auto (based on connection quality)
                                    //1 = low (24kbps bitrate)
                                    //2 = normal (96kbps bitrate)
                                    //3 = high (160kbps bitrate)
                                    //4 = very high (320kbps bitrate)
                                    listOf(
                                        "audio.play_bitrate_enumeration",
                                        "audio.play_bitrate_non_metered_enumeration"
                                    ).forEach { prefs[it] = "4" }

                                    prefs.forEach {
                                        newPrefs.appendLine("${it.key}=${it.value}")
                                    }
                                    prefsFile.delete()
                                    prefsFile.createNewFile()
                                    prefsFile.writeText(newPrefs.toString())
                                }
                            }
                        } else {
                            println("Please log in to your Spotify account.")
                        }
                    }
                }

                //(re)starts the player
                suspend fun restartPlayer() {
                    killCommand()
                    delay(100)
                    startCommand()
                    //sometimes the spotify player has problems starting, so ensure it actually starts.
                    while (checkProcess().first.outputText.isEmpty()) {
                        delay(7000)
                        if (checkProcess().first.outputText.isEmpty()) {
                            repeat(2) { killCommand() }
                            delay(1000)
                            startCommand()
                            delay(2000)
                        }
                    }
                    //wait for the spotify player to start.
                    while (trackJob.isActive && commandRunner.runCommand(
                            "ps aux | grep -E \"[0-9]+:[0-9]+ .*(\\s+)?${botSettings.spotifyPlayer}(\\s+.*)?$\" | grep -v \"grep\"",
                            printOutput = false
                        ).first.outputText.isEmpty()
                    ) {
                        //do nothing
                        println("Waiting for ${botSettings.spotifyPlayer} to start")
                        delay(150)
                    }
                }

                if (shouldSetPrefs) {
                    restartPlayer()
                    setPrefs()
                    startSpotifyPlayer(false)
                } else {
                    restartPlayer()
                }

                delay(5000)
            }

            //starts playing the track
            suspend fun openTrack() {
                /**
                 * Get track length in seconds
                 * @return track length in seconds
                 */
                fun getTrackLength(): Int {
                    val lengthMicroseconds: Long = try {
                        playerctl(getPlayer(), "metadata").first.outputText.lines()
                            .first { it.contains("mpris:length") }.replace("^.+\\s+".toRegex(), "").toLong()
                    } catch (e: Exception) {
                        //track hasn't started
                        0L
                    }
                    val minutes = lengthMicroseconds / 1000000 / 60
                    val seconds = lengthMicroseconds / 1000000 % 60
                    //convert to seconds
                    return (minutes * 60 + seconds).toInt()
                }

                var trackLength = getTrackLength()
                var wasPaused = false

                suspend fun startCountingTrackPosition(job: Job) {
                    trackLength = getTrackLength()
                    withContext(job + IO) {
                        while (job.isActive) {
                            delay(985)
                            trackPosition += 1
                            println("Track Position: $trackPosition/$trackLength seconds")
                            if (trackPosition > trackLength + 10) {
                                println("Wait.. what?")
                                skipTrack()
                            }
                        }
                    }
                }

                //wait for track to start
                when (val type = track.serviceType) {
                    Service.ServiceType.SPOTIFY -> {
                        //check active processes and wait for the spotify player to start
                        val player = getPlayer()
                        while (commandRunner.runCommand(
                                "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$player(.+)?\" | grep -v \"grep\"",
                                printOutput = false
                            ).first.outputText.isEmpty()
                        ) {
                            println("Waiting for $player to start.")
                            delay(500)
                        }
                        //Try to start playing song
                        var attempts = 0
                        while (!playerStatus().first.outputText.contains("Playing")) {
                            if (attempts < 5) {
                                playerctl(
                                    getPlayer(), "open", "spotify:" +
                                            if (track.link.link.contains("/track/")) {
                                                "track"
                                            } else {
                                                "episode"
                                            } + ":" +
                                            track.link.getId()
                                )
                                delay(5000)
                                attempts++
                            } else {
                                attempts = 0
                                startSpotifyPlayer()
                            }
                        }
                    }

                    Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> {
                        suspend fun startMPV(job: Job) {
                            val volume = if (type == Service.ServiceType.SOUNDCLOUD)
                                botSettings.scVolume
                            else
                                botSettings.ytVolume

                            val mpvRunnable = Runnable {
                                commandRunner.runCommand(
                                    "mpv --terminal=no --no-video" +
                                            " --ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0" +
                                            (if (track.serviceType == Service.ServiceType.YOUTUBE) ",cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" else "") +
                                            " --ytdl \"${track.link}\" --volume=$volume &",
                                    inheritIO = true,
                                    ignoreOutput = true, printCommand = true
                                )
                            }
                            withContext(IO + job) {
                                mpvRunnable.run()
                                var attempts = 0
                                while (job.isActive && commandRunner.runCommand(
                                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                        printOutput = false
                                    ).first.outputText.isEmpty()
                                ) {
                                    println("Waiting for ${getPlayer()} to start.")
                                    //if playback hasn't started after five seconds, try starting playback again.
                                    if (attempts < 5) {
                                        delay(1000)
                                        attempts++
                                    } else {
                                        attempts = 0
                                        commandRunner.runCommand("pkill -9 mpv", ignoreOutput = true)
                                        delay(1000)
                                        mpvRunnable.run()
                                    }
                                }
                            }
                        }

                        var currentJob = Job()
                        startMPV(currentJob)
                        delay(7000)
                        var attempts = 0
                        while (!playerStatus().first.outputText.contains("Playing")) {
                            println("Waiting for track to start playing")
                            delay(1000)
                            if (attempts < 5) {
                                delay(1000)
                                attempts++
                            } else {
                                attempts = 0
                                currentJob.cancel()
                                delay(1000)
                                currentJob = Job()
                                startMPV(currentJob)
                            }
                        }
                    }

                    else -> {
                        println("Error: ${track.link} is not a supported link type!")
                        synchronized(SongQueue::javaClass) {
                            println("Removing track from queue.")
                            songQueue.remove(track)
                        }
                        trackPositionJob.cancel()
                        trackJob.complete()
                        listener.onTrackStopped(getPlayer(), track)
                    }
                }

                loop@ while (true) {
                    val status = playerStatus()
                    if (currentUrl() == track.link.link || currentUrl().startsWith("https://open.spotify.com/ad/")) {
                        when (status.first.outputText) {
                            "Playing" -> {
                                while (currentUrl().startsWith("https://open.spotify.com/ad/")) {
                                    //wait for ad to finish
                                    if (!currentUrl().startsWith("https://open.spotify.com/ad/")) {
                                        //wait for a couple seconds in case another ad starts playing
                                        delay(2000)
                                        if (!currentUrl().startsWith("https://open.spotify.com/ad/")) {
                                            trackLength = getTrackLength()
                                        }
                                    }
                                }
                                if (wasPaused) {
                                    CoroutineScope(trackJob + IO).launch {
                                        trackPositionJob.cancel()
                                        trackPositionJob = Job()
                                        startCountingTrackPosition(trackPositionJob)
                                    }
                                    listener.onTrackResumed(getPlayer(), track)
                                    wasPaused = false
                                }
                                if (trackPosition == 0) {
                                    CoroutineScope(trackJob + IO).launch {
                                        trackPositionJob.cancel()
                                        trackPositionJob = Job()
                                        startCountingTrackPosition(trackPositionJob)
                                    }
                                    listener.onTrackStarted(getPlayer(), track)
                                    delay(2000)
                                }
                            }

                            "Paused" -> {
                                if (trackPosition >= trackLength - 10) {
                                    //Song ended
                                    //Spotify changes playback status to "Paused" right before the song actually ends,
                                    //so wait for a brief moment so the song has a chance to end properly.
                                    if (getPlayer() == "spotify")
                                        delay(1495)
                                    trackPositionJob.cancel()
                                    trackJob.complete()
                                    listener.onTrackEnded(getPlayer(), track)
                                    break@loop
                                } else if (!wasPaused) {
                                    trackPositionJob.cancel()
                                    wasPaused = true
                                    listener.onTrackPaused(getPlayer(), track)
                                }
                            }

                            "Stopped" -> {
                                if (trackPosition >= trackLength - 10) {
                                    trackPositionJob.cancel()
                                    trackJob.complete()
                                    listener.onTrackEnded(getPlayer(), track)
                                    break@loop
                                } else {
                                    //wait a bit to see if track is actually stopped
                                    delay(3000)
                                    if (playerStatus().first.outputText.contains("Stopped")) {
                                        trackPositionJob.cancel()
                                        trackJob.complete()
                                        listener.onTrackStopped(getPlayer(), track)
                                        break@loop
                                    }
                                }
                            }

                            else -> {
                                if (status.second.errorText != "Error org.freedesktop.DBus.Error.ServiceUnknown: The name org.mpris.MediaPlayer2.${getPlayer()} was not provided by any .service files") {
                                    println("Player has stopped")
                                    trackPositionJob.cancel()
                                    trackJob.complete()
                                    listener.onTrackEnded(getPlayer(), track)
                                } else {
                                    trackPositionJob.cancel()
                                    val msg = "Unhandled Player Status: ${playerStatus().second.errorText}"
                                    trackJob.completeExceptionally(Throwable(msg))
                                    println(msg)
                                }
                                break@loop
                            }
                        }
                    } else {
                        if (trackPosition >= trackLength - 10) {
                            trackPositionJob.cancel()
                            trackJob.complete()
                            listener.onTrackEnded(getPlayer(), track)
                            break@loop
                        } else {
                            println("Something went seriously wrong. Restarting the player and starting the song again.")
                            commandRunner.runCommand("pkill -9 ${getPlayer()}", ignoreOutput = true)
                            if (getPlayer() == botSettings.spotifyPlayer)
                                startSpotifyPlayer()
                            synchronized(songQueue) { songQueue.add(0, track) }
                            trackPositionJob.cancel()
                            trackJob.complete()
                            openTrack()
                            break@loop
                        }
                    }
                }
            }

            when (track.link.serviceType()) {
                Service.ServiceType.SPOTIFY -> {
                    CoroutineScope(IO + synchronized(trackJob) { trackJob }).launch {
                        if (playerStatus().second.errorText == "Error org.freedesktop.DBus.Error.ServiceUnknown: The name org.mpris.MediaPlayer2.${getPlayer()} was not provided by any .service files")
                            startSpotifyPlayer()

                        openTrack()
                    }
                }

                Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> {
                    CoroutineScope(IO + synchronized(trackJob) { trackJob }).launch {
                        openTrack()
                    }
                }

                else -> {
                    println("Link type not supported!\n${track.link}")
                }
            }
        }

        fun pauseTrack() {
            if (track.isNotEmpty())
                playerctl(getPlayer(), "pause")
        }

        fun resumeTrack() {
            if (track.isNotEmpty()) {
                refreshPulseAudio()
                checkTeamSpeakAudio(trackJob)
                playerctl(getPlayer(), "play")
            }
        }

        fun stopTrack() {
            trackPositionJob.cancel()
            trackJob.cancel()
            val player = getPlayer()
            //try stopping playback twice because sometimes once doesn't seem to be enough
            for (i in 1..2) {
                playerctl(player, if (player == "spotify") "pause" else "stop")
            }
            //if mpv is in use, kill the process
            if (player == "mpv")
                commandRunner.runCommand("pkill -9 mpv", ignoreOutput = true)
            listener.onTrackStopped(player, track)
        }

        fun skipTrack() {
            trackPositionJob.cancel()
            trackJob.cancel()
            val player = getPlayer()
            when (player) {
                "spotify" -> for (i in 1..2) playerctl(player, "pause")
                "mpv" -> commandRunner.runCommand("pkill -9 mpv", ignoreOutput = true)
                else -> playerctl(player, "stop")
            }
            listener.onTrackEnded(player, track)
        }
    }
}


interface PlayStateListener {
    fun onTrackEnded(player: String, track: Track)
    fun onTrackPaused(player: String, track: Track)
    fun onTrackResumed(player: String, track: Track)
    fun onTrackStarted(player: String, track: Track)
    fun onTrackStopped(player: String, track: Track)
    fun onAdPlaying()
}
