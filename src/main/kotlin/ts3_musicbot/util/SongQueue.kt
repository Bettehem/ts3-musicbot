package ts3_musicbot.util

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.util.*
import kotlin.collections.ArrayList

private var songQueue = Collections.synchronizedList(ArrayList<Track>())

class SongQueue(
    spotifyPlayer: String = "spotify",
    mpvVolume: Int,
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

    private val trackPlayer = TrackPlayer(spotifyPlayer, mpvVolume, this)

    private fun setCurrent(track: Track) = synchronized(trackPlayer) { trackPlayer.track = track }
    private fun getCurrent() = synchronized(trackPlayer) { trackPlayer.track }

    /**
     * Adds track to queue
     * @param track song's link
     * @param position position in which the song should be added.
     */
    fun addToQueue(
        track: Track, position: Int = if (synchronized(songQueue) { songQueue }.isNotEmpty()) {
            -1
        } else {
            0
        }
    ) {
        synchronized(songQueue) {
            if (position >= 0)
                songQueue.add(position, track)
            else
                songQueue.add(track)
        }
    }

    /**
     * Adds a list of tracks to the queue
     * @param trackList list of tracks to add to the queue
     * @param position position in which the tracks should be added
     */
    fun addAllToQueue(
        trackList: TrackList, position: Int = if (synchronized(songQueue) { songQueue }.isNotEmpty()) {
            -1
        } else {
            0
        }
    ) {
        synchronized(songQueue) {
            if (position >= 0)
                songQueue.addAll(position, trackList.trackList)
            else {
                songQueue.addAll(trackList.trackList)
            }
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
                when (firstTrack.linkType) {
                    LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                        //check if youtube-dl is able to download the track
                        while (songQueue.isNotEmpty() && (firstTrack.linkType == LinkType.YOUTUBE || firstTrack.linkType == LinkType.SOUNDCLOUD)
                            && CommandRunner().runCommand(
                                "youtube-dl -s \"${firstTrack.link}\"",
                                printOutput = false,
                                printErrors = false
                            ).second.errorText.isNotEmpty()
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

    /**
     * Moves a desired track to a new position
     * @param track link to move
     * @param newPosition new position of the track
     */
    fun moveTrack(track: Track, newPosition: Int) {
        //TODO: make possible to choose which track to move if many exist in the queue
        synchronized(songQueue) {
            if (newPosition < songQueue.size && newPosition >= 0) {
                for (i in songQueue.indices) {
                    if (songQueue[i].link == track.link) {
                        val newTrack = songQueue[i]
                        songQueue.removeAt(i)
                        songQueue.add(newPosition, newTrack)
                        break
                    }
                }
            }
        }
    }

    private class TrackPlayer(val spotifyPlayer: String, val mpvVolume: Int, val listener: PlayStateListener) {
        var trackJob = Job()
        var trackPositionJob = Job()

        var trackPosition = 0

        var track = Track()
        private val commandRunner = CommandRunner()

        /**
         * get the player suitable for playing the current track
         */
        fun getPlayer() = when (track.linkType) {
            LinkType.SPOTIFY -> spotifyPlayer
            LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> "mpv"
            else -> ""
        }

        fun currentUrl() = commandRunner.runCommand(
            "playerctl -p ${getPlayer()} metadata --format '{{ xesam:url }}'",
            printOutput = false,
            printErrors = false
        ).first.outputText

        fun startTrack() {
            synchronized(this) {
                trackPositionJob.cancel()
                trackPosition = 0
                trackJob.cancel()
                trackJob = Job()
            }

            fun playerStatus() = commandRunner.runCommand(
                "playerctl -p ${getPlayer()} status", printOutput = false, printErrors = false
            )

            suspend fun startSpotifyPlayer() {
                fun killCommand() = when (spotifyPlayer) {
                    "spotify" -> commandRunner.runCommand("pkill -9 spotify", ignoreOutput = true)
                    "ncspot" -> commandRunner.runCommand(
                        "playerctl -p ncspot stop; tmux kill-session -t ncspot",
                        ignoreOutput = true
                    )
                    "spotifyd" -> commandRunner.runCommand("echo \"spotifyd isn't well supported yet, please kill it manually.\"")
                    else -> commandRunner.runCommand("echo \"$spotifyPlayer is not a supported player!\" > /dev/stderr; return 2")
                }

                fun startCommand() = when (spotifyPlayer) {
                    "spotify" -> commandRunner.runCommand(
                        "$spotifyPlayer &",
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
                    else -> commandRunner.runCommand("echo \"$spotifyPlayer is not a supported player!\" > /dev/stderr; return 2")
                }

                fun checkProcess() = commandRunner.runCommand(
                    "ps aux | grep $spotifyPlayer | grep -v grep",
                    printOutput = false
                )

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
                while (trackJob.isActive && commandRunner.runCommand(
                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$spotifyPlayer(\\s+\\S+)?$\" | grep -v \"grep\"",
                        printOutput = false
                    ).first.outputText.isEmpty()
                ) {
                    //do nothing
                    println("Waiting for $spotifyPlayer to start")
                    delay(10)
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
                    val lengthMicroseconds = try {
                        commandRunner.runCommand(
                            "playerctl -p ${getPlayer()} metadata --format '{{ mpris:length }}'",
                            printOutput = false
                        ).first.outputText.toInt()
                    } catch (e: Exception) {
                        //track hasn't started
                        0
                    }
                    val minutes = lengthMicroseconds / 1000000 / 60
                    val seconds = lengthMicroseconds / 1000000 % 60
                    //convert to seconds
                    return minutes * 60 + seconds
                }

                var trackLength = getTrackLength()
                var wasPaused = false

                suspend fun startCountingTrackPosition(job: Job) {
                    trackLength = getTrackLength()
                    withContext(job + IO) {
                        while (job.isActive) {
                            delay(985)
                            trackPosition += 1
                            println("Track Position: $trackPosition/$trackLength")
                        }
                    }
                }

                while (currentUrl() != track.link.link) {
                    //wait for track to start
                    when (track.linkType) {
                        LinkType.SPOTIFY -> {
                            //check active processes and wait for the spotify player to start
                            val player = getPlayer()
                            while (commandRunner.runCommand(
                                    "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$player(.+)?\" | grep -v \"grep\"",
                                    printOutput = false
                                ).first.outputText.isEmpty()
                            ) {
                                println("Waiting for $player to start.")
                                delay(100)
                            }
                            //Try to start playing song
                            var attempts = 0
                            while (playerStatus().first.outputText != "Playing") {
                                if (attempts < 5) {
                                    if (track.link.link.contains("/track/")) {
                                        commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer open spotify:track:${
                                                track.link.getId()
                                            }", printCommand = true
                                        )
                                    } else {
                                        commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer open spotify:episode:${
                                                track.link.getId()
                                            }", printCommand = true
                                        )
                                    }
                                    delay(2000)
                                    attempts++
                                } else {
                                    attempts = 0
                                    startSpotifyPlayer()
                                }
                            }
                        }
                        LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                            suspend fun startMPV(job: Job) {
                                val mpvRunnable = Runnable {
                                    commandRunner.runCommand(
                                        "mpv --terminal=no --no-video" +
                                                " --ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0" +
                                                (if (track.linkType == LinkType.YOUTUBE) ",cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" else "") +
                                                " --ytdl \"${track.link}\" --volume=$mpvVolume &",
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
                            while (playerStatus().first.outputText != "Playing") {
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
                            break
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
                            break
                        }
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
                                    if (status.first.outputText == "Stopped") {
                                        trackPositionJob.cancel()
                                        trackJob.complete()
                                        listener.onTrackStopped(getPlayer(), track)
                                        break@loop
                                    }
                                }
                            }
                            else -> {
                                if (status.second.errorText != "No players found") {
                                    //player has stopped, proceed to next song
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
                            //Something went seriously wrong. Restart the player and start the song again.
                            commandRunner.runCommand("pkill -9 ${getPlayer()}", ignoreOutput = true)
                            if (getPlayer() == spotifyPlayer)
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

            when (track.link.linkType()) {
                LinkType.SPOTIFY -> {
                    CoroutineScope(IO + synchronized(trackJob) { trackJob }).launch {
                        if (playerStatus().second.errorText == "No players found")
                            startSpotifyPlayer()
                        openTrack()
                    }
                }
                LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
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
                commandRunner.runCommand("playerctl -p ${getPlayer()} pause")
        }

        fun resumeTrack() {
            if (track.isNotEmpty())
                commandRunner.runCommand("playerctl -p ${getPlayer()} play", ignoreOutput = true)
        }

        fun stopTrack() {
            trackPositionJob.cancel()
            trackJob.cancel()
            //try stopping playback twice because sometimes once doesn't seem to be enough
            val player = getPlayer()
            for (i in 1..2) {
                commandRunner.runCommand(
                    "playerctl -p " +
                            when (player) {
                                "spotify" -> "spotify pause"
                                else -> "$player stop"
                            },
                    ignoreOutput = true
                )
            }
            //if mpv is in use, kill the process
            if (player == "mpv")
                commandRunner.runCommand("pkill -9 mpv", ignoreOutput = true)
            listener.onTrackStopped(player, track)
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
