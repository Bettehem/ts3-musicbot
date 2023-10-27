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
    private val trackPlayer = TrackPlayer(botSettings, teamSpeak, this)
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
    fun getTrackPosition() = synchronized(trackPlayer) {
        trackPlayer.trackPosition
    }

    fun getTrackLength() = synchronized(trackPlayer) {
        trackPlayer.getTrackLength()
    }

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
                        var attempts = 0
                        while (songQueue.isNotEmpty() && !CommandRunner().runCommand(
                                "youtube-dl --extract-audio --audio-format best --audio-quality 0 " +
                                        "--cookies youtube-dl.cookies --force-ipv4 --age-limit 21 --geo-bypass -s \"${firstTrack.link}\"",
                                printOutput = false,
                                printErrors = false
                            ).outputText.lines().last()
                                .contains("\\[(info|youtube)] ${firstTrack.link.getId()}: Downloading ([0-9]+ format\\(s\\):|webpage|API JSON)".toRegex())
                        ) {
                            if (attempts < 5) {
                                println("Error downloading track! Trying again...")
                                attempts++
                            } else {
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

        var trackPosition = 0L

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

        fun playerStatus() = playerctl(getPlayer(), "status")

        fun currentUrl(): String {
            val metadata = playerctl(getPlayer(), "metadata")
            return if (metadata.errorText.isEmpty()) {
                metadata.outputText.lines()
                    .first { it.contains("xesam:url") }.replace("(^.+\\s+\"?|\"?$)".toRegex(), "")
            } else {
                ""
            }
        }

        /**
         * Get track length in seconds
         * @return track length in seconds
         */
        fun getTrackLength(): Long {
            val lengthMicroseconds: Long = try {
                playerctl(getPlayer(), "metadata").outputText.lines()
                    .first { it.contains("mpris:length") }.replace("^.+\\s+".toRegex(), "").toLong()
            } catch (e: Exception) {
                //track hasn't started
                0L
            }
            val minutes = lengthMicroseconds / 1000000 / 60
            val seconds = lengthMicroseconds / 1000000 % 60
            //convert to seconds
            return minutes * 60 + seconds
        }

        fun refreshPulseAudio() {
            //if using pulseaudio, refresh it using pasuspender
            if (
                commandRunner.runCommand("command -v pasuspender", printOutput = false, printErrors = false)
                    .outputText.isNotEmpty()
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

        fun killPlayer(player: String) = when (player) {
            "spotify", "mpv" -> commandRunner.runCommand("pkill -9 $player", ignoreOutput = true)
            "ncspot" -> {
                playerctl(player, "stop")
                commandRunner.runCommand("tmux kill-session -t ncspot", ignoreOutput = true)
            }

            "spotifyd" -> commandRunner.runCommand("echo \"$player isn't well supported yet, please kill it manually.\"")
            else -> commandRunner.runCommand("echo \"$player is not a supported player!\" > /dev/stderr; return 2")
        }


        /**
         * Check if the player's process for the current track is running
         * @return returns true if the process is running
         */
        fun processRunning(player: String = getPlayer()) = commandRunner.runCommand(
            "ps aux | grep $player | grep -v grep",
            printOutput = false
        ).outputText.isNotEmpty() ||
                if (player == "ncspot")
                    commandRunner.runCommand("tmux ls | grep player").outputText.isNotEmpty()
                else false

        fun startTrack() {
            synchronized(this) {
                trackPositionJob.cancel()
                trackPosition = 0
                trackJob.cancel()
                trackJob = Job()
                refreshPulseAudio()
                checkTeamSpeakAudio(trackJob)
            }

            suspend fun startSpotifyPlayer(shouldSetPrefs: Boolean = true) {
                fun startCommand() = when (botSettings.spotifyPlayer) {
                    "spotify" -> {
                        if (botSettings.spotifyUsername.isEmpty() || botSettings.spotifyPassword.isEmpty()) {
                            val msg = "Error! Missing Spotify username and/or password in the musicbot's config file!"
                            println(msg)
                            Output(errorText = msg)
                        } else {
                            commandRunner.runCommand(
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
                        }
                    }

                    "ncspot" -> commandRunner.runCommand(
                        "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                        ignoreOutput = true,
                        printCommand = true
                    )

                    "spotifyd" -> commandRunner.runCommand("echo \"spotifyd isn't well supported yet, please start it manually.\"")
                    else -> commandRunner.runCommand("echo \"${botSettings.spotifyPlayer} is not a supported player!\" > /dev/stderr; return 2")
                }

                //starts the player
                suspend fun startPlayer() {
                    startCommand()
                    delay(3000)

                    //sometimes the spotify player has problems starting, so ensure it actually starts.
                    println()
                    var attempts = 0
                    while (!processRunning()) {
                        if (attempts < 10) {
                            print("\rWaiting for ${getPlayer()} to start${".".repeat(attempts / 2)}")
                            delay(1000)
                            attempts++
                        } else {
                            println("\nTrying again.")
                            attempts = 0
                            killPlayer(getPlayer())
                            delay(1000)
                            startCommand()
                            delay(2000)
                        }
                    }
                }

                //restarts the player
                suspend fun restartPlayer() {
                    killPlayer(getPlayer())
                    delay(100)
                    startPlayer()
                }

                //set the spotify player's config options
                suspend fun setPrefs() {
                    when (botSettings.spotifyPlayer) {
                        "spotify" -> {
                            //autoplay needs to be disabled so the spotify client doesn't start playing stuff on its own.
                            //friend feed, notifications, release announcements etc. are disabled just for performance.
                            //volume normalization is enabled to get more consistent volume between tracks.
                            val usersPath = System.getProperty("user.home") + "/.config/spotify/Users"
                            if (!File(usersPath).exists()) {
                                println("Spotify config files not found, starting player once to create files.")
                                startPlayer()
                            }
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
                                println("Error! Not logged in to a Spotify account.")
                            }
                        }

                        "ncspot" -> {
                            val homePath = System.getProperty("user.home")
                            val credentialsFile = File("$homePath/.cache/ncspot/librespot/credentials.json")
                            val configPath = "$homePath/.config/ncspot"
                            if (!credentialsFile.exists()) {
                                println("Not logged in to ncspot! Creating config file...")
                                //If the credentials file doesn't exist, the user hasn't logged into ncspot.
                                //In this case, write a new config file in ~/.config/ncspot/config.toml
                                //This however, will require the user to have their spotify username & password defined
                                //in the musicbot's config file.

                                if (botSettings.spotifyUsername.isEmpty() || botSettings.spotifyPassword.isEmpty()) {
                                    println("Error! Missing Spotify username and/or password in the musicbot's config file!")
                                }

                                //create the config directory if it doesn't already exist
                                File(configPath).mkdirs()

                                val configFile = File("$configPath/config.toml")
                                val newConfig = StringBuilder()
                                newConfig.appendLine("# This config was auto-generated by ts3-musicbot.")
                                newConfig.appendLine("# You may adjust values such as audio_cache and audio_cache_size,")
                                newConfig.appendLine("# But the other options that have been added automatically should not be changed.")
                                newConfig.appendLine()
                                newConfig.appendLine("# You may check the ncspot github for a full list of configuration options:")
                                newConfig.appendLine("# https://github.com/hrkfdn/ncspot/blob/main/doc/users.md#configuration")
                                newConfig.appendLine()
                                newConfig.appendLine("audio_cache = true")
                                newConfig.appendLine("# Audio cache size in MiB")
                                newConfig.appendLine("audio_cache_size = 1024")
                                newConfig.appendLine()
                                newConfig.appendLine("# Although not used when playing the music bot's own song queue,")
                                newConfig.appendLine("# gapless playback improves the listening experience when controlling")
                                newConfig.appendLine("# the player manually.")
                                newConfig.appendLine("gapless = true")
                                newConfig.appendLine()
                                newConfig.appendLine("# Volume normalization")
                                newConfig.appendLine("volnorm = true")
                                newConfig.appendLine("# Amount of normalization to apply in dB")
                                newConfig.appendLine("volnorm_pregain = -1.8")
                                newConfig.appendLine()
                                newConfig.appendLine("# Default playback state")
                                newConfig.appendLine("playback_state = \"Stopped\"")
                                newConfig.appendLine("[saved_state]")
                                newConfig.appendLine("repeat = false")
                                newConfig.appendLine("shuffle = false")
                                newConfig.appendLine()
                                newConfig.appendLine("# Set the credentials to log in with")
                                newConfig.appendLine("[credentials]")
                                newConfig.appendLine("username_cmd = \"echo '${botSettings.spotifyUsername}'\"")
                                newConfig.appendLine("password_cmd = \"echo '${botSettings.spotifyPassword}'\"")

                                if (configFile.exists())
                                    configFile.delete()

                                configFile.createNewFile()
                                configFile.writeText(newConfig.toString())
                            }
                        }
                    }
                }

                if (shouldSetPrefs) {
                    setPrefs()
                    startSpotifyPlayer(false)
                } else {
                    restartPlayer()
                }
            }

            //starts playing the track
            suspend fun openTrack() {
                var trackLength = getTrackLength()
                var wasPaused = false

                suspend fun startCountingTrackPosition(job: Job) {
                    trackLength = getTrackLength()
                    /**
                     * Get the current track position in seconds
                     * @return returns current track position in seconds
                     */
                    fun getCurrentPosition(): Long {
                        val microseconds = playerctl(getPlayer(), "position").outputText.toLong()
                        return microseconds / 1000000
                    }
                    println()
                    withContext(job + IO) {
                        while (job.isActive) {
                            val currentPos = getCurrentPosition()
                            if (playerStatus().outputText == "Playing" && currentPos > trackPosition)
                                trackPosition = currentPos
                            print("\rTrack Position: $trackPosition/$trackLength seconds")
                            if (trackPosition > trackLength + 30) {
                                println("Wait.. what?")
                                if (trackLength == 0L) {
                                    println("Huh, why is trackLength 0?!\nTrying to get it again...")
                                    trackLength = getTrackLength()
                                } else {
                                    skipTrack()
                                }
                            }
                            delay(500)
                        }
                    }
                }

                //try to play the track
                when (val type = track.serviceType) {
                    Service.ServiceType.SPOTIFY -> {
                        //check active processes and wait for the spotify player to start
                        val player = getPlayer()

                        if (!processRunning())
                            startSpotifyPlayer()
                        var waitTime = 0
                        while (!processRunning()) {
                            if (waitTime < 10) {
                                delay(1000)
                                waitTime++
                            } else {
                                waitTime = 0
                                startSpotifyPlayer()
                                delay(1000)
                            }
                        }
                        println("Trying to play track \"${track.link}\" using $player as the player.")
                        var attempts = 0
                        while (!playerStatus().outputText.contains("Playing")) {
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
                                attempts++
                                delay(500)
                                if (playerStatus().outputText != "Playing")
                                    delay(3500)
                            } else {
                                println("The player may be stuck, trying to start it again.")
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
                                while (job.isActive && !processRunning()) {
                                    println("Waiting for ${getPlayer()} to start.")
                                    //if playback hasn't started after ten seconds, try starting playback again.
                                    if (attempts < 10) {
                                        delay(1000)
                                        attempts++
                                    } else {
                                        println("The player may be stuck, trying to start it again.")
                                        attempts = 0
                                        killPlayer("mpv")
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
                        while (!playerStatus().outputText.contains("Playing")) {
                            println("Waiting for track to start playing")
                            delay(1000)
                            if (attempts < 10) {
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
                        synchronized(songQueue) {
                            println("Removing track from queue.")
                            songQueue.remove(track)
                        }
                        trackPositionJob.cancel()
                        trackJob.complete()
                        listener.onTrackStopped(getPlayer(), track)
                    }
                }

                loop@ while (trackJob.isActive) {
                    delay(995)
                    val status = playerStatus()
                    if (currentUrl() == track.link.link || currentUrl().startsWith("https://open.spotify.com/ad/")) {
                        when (status.outputText) {
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
                                if (trackPosition == 0L) {
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
                                if (getPlayer() == "spotify") {
                                    if (trackPosition >= trackLength - 5) {
                                        //Song ended
                                        //Spotify changes playback status to "Paused" right before the song actually ends,
                                        //so wait for a brief moment so the song has a chance to end properly.
                                        delay(1495)
                                        skipTrack()
                                        break@loop
                                    } else if (!wasPaused) {
                                        trackPositionJob.cancel()
                                        wasPaused = true
                                        listener.onTrackPaused(getPlayer(), track)
                                    }
                                } else {
                                    if (!wasPaused) {
                                        trackPositionJob.cancel()
                                        wasPaused = true
                                        listener.onTrackPaused(getPlayer(), track)
                                    }
                                }
                            }

                            "Stopped" -> {
                                if (trackPosition >= trackLength - 5) {
                                    skipTrack()
                                    break@loop
                                } else {
                                    //wait a bit to see if track is actually stopped
                                    delay(3000)
                                    if (playerStatus().outputText.contains("Stopped")) {
                                        stopTrack()
                                        break@loop
                                    }
                                }
                            }

                            else -> {
                                if (status.errorText != "Error org.freedesktop.DBus.Error.ServiceUnknown: The name org.mpris.MediaPlayer2.${getPlayer()} was not provided by any .service files") {
                                    println("Player has stopped")
                                    skipTrack()
                                } else {
                                    trackPositionJob.cancel()
                                    val msg = "Unhandled Player Status: ${playerStatus().errorText}"
                                    trackJob.completeExceptionally(Throwable(msg))
                                    println(msg)
                                }
                                break@loop
                            }
                        }
                    } else {
                        if (trackPosition >= trackLength - 5) {
                            skipTrack()
                            break@loop
                        } else {
                            println("Something went seriously wrong. Restarting the player and starting the song again.")
                            killPlayer(getPlayer())
                            synchronized(songQueue) { songQueue.add(0, track) }
                            skipTrack()
                            break@loop
                        }
                    }
                }
            }

            when (track.link.serviceType()) {
                Service.ServiceType.SPOTIFY -> {
                    //first kill mpv in case its running
                    killPlayer("mpv")
                    CoroutineScope(IO + synchronized(trackJob) { trackJob }).launch {
                        openTrack()
                    }
                }

                Service.ServiceType.YOUTUBE, Service.ServiceType.SOUNDCLOUD -> {
                    //First kill the spotify player in case its running.
                    //Although this shouldn't be needed, at least in the case of the official spotify client,
                    //sometimes it won't respect the disabled autoplay setting, and will continue playing something else
                    //after the desired track has finished.
                    killPlayer(botSettings.spotifyPlayer)
                    CoroutineScope(IO + synchronized(trackJob) { trackJob }).launch {
                        openTrack()
                    }
                }

                else -> {
                    println("Link type not supported!\nLink: \"${track.link}\"")
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
            while (playerStatus().outputText == "Playing")
                playerctl(player, if (player == "spotify") "pause" else "stop")
            //if mpv is in use, kill the process
            if (player == "mpv")
                killPlayer(player)
            listener.onTrackStopped(player, track)
        }

        fun skipTrack() {
            trackJob.cancel()
            val player = getPlayer()
            while (playerStatus().outputText == "Playing")
                playerctl(player, if (player == "spotify") "pause" else "stop")
            if (player == "mpv")
                killPlayer(player)
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
