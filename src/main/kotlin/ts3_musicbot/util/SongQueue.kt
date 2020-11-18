package ts3_musicbot.util

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.util.*
import kotlin.collections.ArrayList

private var songQueue = Collections.synchronizedList(ArrayList<Track>())
private var queueState = SongQueue.State.QUEUE_STOPPED

class SongQueue(
    spotifyPlayer: String = "spotify",
    private val playStateListener: PlayStateListener
) : PlayStateListener {
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

    private val trackPlayer = TrackPlayer(spotifyPlayer, this)

    private var currentTrack = Track(Album(), Artists(), Name(), Link(), Playability())
    private fun setCurrent(track: Track) = synchronized(currentTrack) { currentTrack = track }
    private fun getCurrent() = synchronized(currentTrack) { currentTrack }

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
                if (getQueue().isNotEmpty()) {
                    trackPlayer.stopTrack()
                    setCurrent(Track())
                    playNext()
                } else println("Track cannot be skipped.")
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
        trackPlayer.resumeTrack()
    }

    fun pausePlayback() {
        trackPlayer.pauseTrack()
    }

    fun stopQueue() {
        trackPlayer.stopTrack()
        setCurrent(Track())
        setState(State.QUEUE_STOPPED)
    }


    private fun playNext() {
        fun playTrack(track: Track) {
            trackPlayer.track = track
            trackPlayer.startTrack()
        }
        synchronized(songQueue) {
            if (songQueue.isNotEmpty()) {
                if (songQueue[0].linkType == LinkType.SPOTIFY && songQueue[0].link.link == trackPlayer.currentUrl()){
                    CommandRunner().runCommand("killall ${trackPlayer.spotifyPlayer}; sleep 2")
                }
                playTrack(songQueue[0])
            } else {
                println("Song queue is empty!")
                stopQueue()
            }
        }
    }


    override fun onTrackEnded(player: String, track: Track) {
        playStateListener.onTrackEnded(player, track)
        playNext()
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
        synchronized(songQueue) {
            songQueue.remove(track)
        }
        playStateListener.onTrackStarted(player, track)
    }

    override fun onAdPlaying() {}

    /**
     * Moves a desired track to a new position
     * @param track link to move
     * @param newPosition new position of the track
     */
    fun moveTrack(track: Track, newPosition: Int) {
        //TODO: make possible to choose which track to move if many exist in the queue
        synchronized(this) {
            if (newPosition < songQueue.size && newPosition >= 0) {
                for (i in songQueue.indices) {
                    if (songQueue[i].link == track.link) {
                        songQueue.removeAt(i)
                        songQueue.add(newPosition, track)
                        break
                    }
                }
            }
        }
    }

    private class TrackPlayer(val spotifyPlayer: String, val listener: PlayStateListener) {
        @Volatile
        lateinit var trackJob: CompletableJob

        @Volatile
        var trackPosition = 0

        var track = Track()
        private val commandRunner = CommandRunner()
        fun getPlayer() = when (track.linkType) {
            LinkType.SPOTIFY -> spotifyPlayer
            LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> "mpv"
            else -> ""
        }

        fun currentUrl() = commandRunner.runCommand(
            "playerctl -p ${getPlayer()} metadata --format '{{ xesam:url }}'",
            printOutput = false,
            printErrors = false
        )

        fun startTrack() {
            trackPosition = 0
            trackJob = Job()

            fun playerStatus() = commandRunner.runCommand(
                "playerctl -p ${getPlayer()} status", printOutput = false, printErrors = false
            )

            suspend fun startSpotifyPlayer() {
                withContext(IO + trackJob) {
                    when (spotifyPlayer) {
                        "spotify" -> commandRunner.runCommand("$spotifyPlayer &", printOutput = false)
                        "ncspot" -> commandRunner.runCommand(
                            "killall ncspot; \$TERMINAL -e \"ncspot\" &",
                            printOutput = false,
                            ignoreOutput = true
                        )
                        else -> {
                        }
                    }
                    //wait for the spotify player to start.
                    while (commandRunner.runCommand(
                            "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$spotifyPlayer$\" | grep -v \"grep\"",
                            printOutput = false
                        ).isEmpty()
                    ) {
                        //do nothing
                    }
                    delay(5000)
                    if (track.link.link.contains("/track/")) {
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer open spotify:track:${
                                track.link.link.substringAfter("spotify.com/track/")
                                    .substringBefore(
                                        "?"
                                    )
                            } &", ignoreOutput = true
                        )
                    } else {
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer open spotify:episode:${
                                track.link.link.substringAfter("spotify.com/episode/")
                                    .substringBefore(
                                        "?"
                                    )
                            } &", ignoreOutput = true
                        )
                    }
                }
            }

            //starts playing the track
            suspend fun openTrack() {
                withContext(IO + trackJob) {
                    while (currentUrl() != track.link.link) {
                        //wait for track to start
                        when (track.linkType) {
                            LinkType.SPOTIFY -> {
                                commandRunner.runCommand(
                                    "playerctl -p ${getPlayer()} open spotify:track:${
                                        track.link.link.substringAfter("spotify.com/track/")
                                            .substringBefore("?")
                                    }", ignoreOutput = true
                                )
                                while (commandRunner.runCommand(
                                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                        printOutput = false
                                    ).isEmpty()
                                ) {
                                    //do nothing
                                }
                            }
                            LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                                Thread {
                                    Runnable {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket " +
                                                    "--ytdl-raw-options=cookies=youtube-dl.cookies${if (track.linkType == LinkType.YOUTUBE) ",force-ipv4=" else ""}" +
                                                    " --ytdl ${track.link}",
                                            inheritIO = true,
                                            ignoreOutput = true
                                        )
                                    }.run()
                                }.start()
                                delay(5000)
                                while (commandRunner.runCommand(
                                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                        printOutput = false
                                    ).isEmpty()
                                ) {
                                    //do nothing
                                }

                            }
                            else -> {
                                println("Error: ${track.link} is not a supported link type!")
                                synchronized(songQueue) {
                                    songQueue.remove(track)
                                }
                                trackJob.complete().also { listener.onTrackEnded(getPlayer(), track) }
                            }
                        }
                    }
                    fun getTrackLength(): Int {
                        val lengthMicroseconds = try {
                            commandRunner.runCommand(
                                "playerctl -p ${getPlayer()} metadata --format '{{ mpris:length }}'",
                                printOutput = false
                            ).toInt()
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
                    loop@ while (currentUrl() == track.link.link || currentUrl().startsWith("https://open.spotify.com/ad/")) {
                        when (playerStatus()) {
                            "Playing" -> {
                                while (currentUrl().startsWith("https://open.spotify.com/ad/")) {
                                    //wait for ad to finish
                                    if (!currentUrl().startsWith("https://open.spotify.com/ad/")) {
                                        trackLength = getTrackLength()
                                    }
                                }
                                if (wasPaused) {
                                    listener.onTrackResumed(getPlayer(), track)
                                    wasPaused = false
                                }
                                if (trackPosition == 0) {
                                    listener.onTrackStarted(getPlayer(), track)
                                }
                                delay(990 - trackLength / 10L)
                                trackPosition += 1
                            }
                            "Paused" -> {
                                if (trackPosition >= trackLength - 10) {
                                    //songEnded
                                    listener.onTrackEnded(getPlayer(), track).also { trackJob.complete() }
                                    break@loop
                                } else {
                                    if (!wasPaused) {
                                        listener.onTrackPaused(getPlayer(), track)
                                        wasPaused = true
                                    }
                                }
                            }
                            "Stopped" -> {
                                if (trackPosition > 1) {
                                    listener.onTrackEnded(getPlayer(), track).also { trackJob.complete() }
                                    break@loop
                                }
                            }
                            "No players found" -> println("No players found")
                            else -> {
                                trackJob.completeExceptionally(Throwable("Unhandled Player Status"))
                                break@loop
                            }
                        }
                    }
                    when (track.linkType) {
                        LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                            if (commandRunner.runCommand(
                                    "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                    printOutput = false
                                ).isEmpty()
                            ) {
                                if (trackPosition >= trackLength - 20) {
                                    listener.onTrackEnded(getPlayer(), track).also { trackJob.complete() }
                                }
                            }
                        }
                        else -> {
                        }
                    }
                }
            }

            when (track.link.linkType()) {
                LinkType.SPOTIFY -> {
                    CoroutineScope(IO + trackJob).launch {
                        if (playerStatus() == "No players found")
                            startSpotifyPlayer()
                        openTrack()
                    }
                }
                LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                    CoroutineScope(IO + trackJob).launch {
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
            commandRunner.runCommand(
                "playerctl -p " +
                        when (getPlayer()) {
                            "spotify" -> "spotify pause"
                            else -> "${getPlayer()} stop"
                        },
                printErrors = false
            ).also { trackJob.cancel() }
        }
    }
}


interface PlayStateListener {
    fun onTrackEnded(player: String, track: Track)
    fun onTrackPaused(player: String, track: Track)
    fun onTrackResumed(player: String, track: Track)
    fun onTrackStarted(player: String, track: Track)
    fun onAdPlaying()
}
