package ts3_musicbot.util

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.util.*
import kotlin.collections.ArrayList

private var songQueue = Collections.synchronizedList(ArrayList<Track>())

class SongQueue(
    spotifyPlayer: String = "spotify",
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
                    println("Skipping current track.")
                    synchronized(trackPlayer) { trackPlayer.stopTrack() }
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
        synchronized(trackPlayer) { trackPlayer.resumeTrack() }
    }

    fun pausePlayback() {
        synchronized(trackPlayer) { trackPlayer.pauseTrack() }
    }

    fun stopQueue() {
        synchronized(trackPlayer) { trackPlayer.stopTrack() }
        setCurrent(Track())
        setState(State.QUEUE_STOPPED)
    }


    private fun playNext() {
        fun playTrack(track: Track) {
            synchronized(trackPlayer) {
                trackPlayer.track = track
                trackPlayer.startTrack()
            }
        }
        synchronized(songQueue) {
            if (songQueue.isNotEmpty()) {
                when (songQueue[0].linkType) {
                    LinkType.YOUTUBE -> {
                        //check if youtube-dl is able to play the track
                        while (songQueue.isNotEmpty() && songQueue[0].linkType == LinkType.YOUTUBE
                                && CommandRunner().runCommand(
                                       "youtube-dl -s \"${songQueue[0].link}\"",
                                       printOutput = false,
                                       printErrors = false
                                   ).second.errorText.isNotEmpty()
                        ) {
                            println("youtube-dl cannot play this track! Skipping...")
                            songQueue.removeAt(0)
                        }
                        if (songQueue.isNotEmpty()) {
                            playTrack(songQueue[0])
                        } else {
                            println("Song queue is empty!")
                            stopQueue()
                        }
                    }
                    else -> playTrack(songQueue[0])
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
        synchronized(this) {
            songQueue.remove(track)
        }
        println("Track started.")
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

    private class TrackPlayer(val spotifyPlayer: String, val listener: PlayStateListener) {
        var trackJob = Job()

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
        ).first.outputText

        fun startTrack() {
            synchronized(trackPosition) { trackPosition = 0 }
            synchronized(this) { trackJob.cancel() }
            synchronized(trackJob) { trackJob = Job() }

            fun playerStatus() = commandRunner.runCommand(
                "playerctl -p ${getPlayer()} status", printOutput = false, printErrors = false
            )

            suspend fun startSpotifyPlayer() {
                when (spotifyPlayer) {
                    "spotify" -> commandRunner.runCommand("$spotifyPlayer &", printOutput = false, inheritIO = true)
                    "ncspot" -> {
                        commandRunner.runCommand("killall ncspot; sleep 2")
                        commandRunner.runCommand("\$TERMINAL -e ncspot", ignoreOutput = true)
                    }
                    else -> {
                    }
                }
                //wait for the spotify player to start.
                while (trackJob.isActive && commandRunner.runCommand(
                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$spotifyPlayer$\" | grep -v \"grep\"",
                        printOutput = false
                    ).first.outputText.isEmpty()
                ) {
                    //do nothing
                    println("Waiting for $spotifyPlayer to start")
                    delay(10)
                }
                delay(7000)
            }

            //starts playing the track
            suspend fun openTrack() {
                if (track.linkType == LinkType.SPOTIFY && currentUrl() == track.link.link) {
                    if (track.link.link.contains("/track/")) {
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer open spotify:track:${
                                track.link.getId()
                            } &"
                        )
                    } else {
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer open spotify:episode:${
                                track.link.getId()
                            } &"
                        )
                    }
                } else {
                    while (currentUrl() != track.link.link) {
                        //wait for track to start
                        when (track.linkType) {
                            LinkType.SPOTIFY -> {
                                //check active processes and wait for the spotify player to start
                                while (commandRunner.runCommand(
                                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                        printOutput = false
                                    ).first.outputText.isEmpty()
                                ) {
                                    //do nothing
                                    println("Waiting for ${getPlayer()} to start.")
                                    delay(10)
                                }
                                if (track.link.link.contains("/track/")) {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:track:${
                                            track.link.getId()
                                        } &"
                                    )
                                } else {
                                    commandRunner.runCommand(
                                        "playerctl -p $spotifyPlayer open spotify:episode:${
                                            track.link.getId()
                                        } &"
                                    )
                                }
                            }
                            LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                                Thread {
                                    Runnable {
                                        commandRunner.runCommand(
                                            "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket " +
                                                    "--ytdl-raw-options=extract-audio=,audio-format=best,audio-quality=0" +
                                                    (if (track.linkType == LinkType.YOUTUBE) ",cookies=youtube-dl.cookies,force-ipv4=,age-limit=21,geo-bypass=" else "") +
                                                    " --ytdl \"${track.link}\"",
                                            inheritIO = true,
                                            ignoreOutput = true
                                        )
                                    }.run()
                                }.start()
                                delay(5000)
                                while (commandRunner.runCommand(
                                        "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                        printOutput = false
                                    ).first.outputText.isEmpty()
                                ) {
                                    //do nothing
                                    println("Waiting for ${getPlayer()} to start.")
                                }

                            }
                            else -> {
                                println("Error: ${track.link} is not a supported link type!")
                                synchronized(SongQueue::javaClass) {
                                    println("Removing track from queue.")
                                    songQueue.remove(track)
                                }
                                listener.onTrackEnded(getPlayer(), track)
                                    .also { synchronized(trackJob) { trackJob }.complete() }
                            }
                        }
                    }
                }
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
                loop@ while (true) {
                    if (currentUrl() == track.link.link || currentUrl().startsWith("https://open.spotify.com/ad/")) {
                        when (playerStatus().first.outputText) {
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
                                if (synchronized(trackPosition) { trackPosition } == 0) {
                                    listener.onTrackStarted(getPlayer(), track)
                                }
                                delay(990 - trackLength / 10L)
                                synchronized(trackPosition) { trackPosition += 1 }
                            }
                            "Paused" -> {
                                if (synchronized(trackPosition) { trackPosition } >= trackLength - 10) {
                                    //songEnded
                                    listener.onTrackEnded(getPlayer(), track)
                                        .also { synchronized(trackJob) { trackJob.complete() } }
                                    break@loop
                                } else {
                                    if (!wasPaused) {
                                        listener.onTrackPaused(getPlayer(), track)
                                        wasPaused = true
                                    }
                                }
                            }
                            "Stopped" -> {
                                if (synchronized(trackPosition) { trackPosition } > 1) {
                                    listener.onTrackEnded(getPlayer(), track)
                                        .also { synchronized(trackJob) { trackJob.complete() } }
                                    break@loop
                                }
                            }
                            else -> {
                                synchronized(trackJob) {
                                    trackJob.completeExceptionally(Throwable("Unhandled Player Status"))
                                }
                                break@loop
                            }
                        }
                    } else {
                        if (synchronized(trackPosition) { trackPosition } >= trackLength - 20) {
                            listener.onTrackEnded(getPlayer(), track)
                                .also { synchronized(trackJob) { trackJob.complete() } }
                            break@loop
                        }
                    }
                }
                when (track.linkType) {
                    LinkType.YOUTUBE, LinkType.SOUNDCLOUD -> {
                        while (synchronized(trackJob) { trackJob.isActive }) {
                            if (commandRunner.runCommand(
                                    "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?${getPlayer()}(.+)?\" | grep -v \"grep\"",
                                    printOutput = false
                                ).first.outputText.isEmpty()
                            ) {
                                if (synchronized(trackPosition) { trackPosition } >= trackLength - 20) {
                                    listener.onTrackEnded(getPlayer(), track)
                                        .also { synchronized(trackJob) { trackJob.complete() } }
                                    break
                                }
                                delay(1000)
                            }
                            println("Waiting for ${getPlayer()} to close.")
                            delay(50)
                        }
                    }
                    else -> {
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
            for (i in 1..2) {
                commandRunner.runCommand(
                    "playerctl -p " +
                            when (getPlayer()) {
                                "spotify" -> "spotify pause"
                                else -> "${getPlayer()} stop"
                            },
                    printErrors = false
                )
            }
            synchronized(trackJob) { trackJob.cancel() }
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
