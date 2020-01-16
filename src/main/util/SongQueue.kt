package src.main.util

import src.main.services.SoundCloud
import src.main.services.Spotify
import src.main.services.Track
import src.main.services.YouTube
import java.util.concurrent.atomic.AtomicBoolean

class SongQueue(private val market: String = "") : PlayStateListener {
    @Volatile
    private var songQueue = ArrayList<String>()

    private val playStateListener: PlayStateListener = this
    private lateinit var playStateListener2: PlayStateListener

    @Volatile
    private var currentSong = ""
    @Volatile
    private var shouldMonitorSp: AtomicBoolean = AtomicBoolean(false)
    @Volatile
    private var shouldMonitorYt: AtomicBoolean = AtomicBoolean(false)
    @Volatile
    private var shouldMonitorSc: AtomicBoolean = AtomicBoolean(false)
    @Volatile
    private var songQueueActive: AtomicBoolean = AtomicBoolean(false)
    @Volatile
    private var songPosition = 0

    private val spotifyListenerThread = Thread {
        Runnable {
            var songLength = 0 //Song length in seconds
            var adNotified = false
            while (songQueueActive.get()) {
                if (shouldMonitorSp.get()) {
                    val lengthMicroseconds = try {
                        runCommand(
                            "playerctl -p spotify metadata --format '{{ mpris:length }}'",
                            printOutput = false
                        ).toInt()
                    } catch (e: Exception) {
                        //song hasn't started
                        0
                    }
                    val current =
                        runCommand("playerctl -p spotify metadata --format '{{ xesam:url }}'", printOutput = false)
                    val playerStatus = runCommand("playerctl -p spotify status", printOutput = false)
                    if (playerStatus == "Playing") {
                        //song is playing

                        if (current == currentSong.substringBefore("?")) {
                            val minutes = lengthMicroseconds / 1000000 / 60
                            val seconds = lengthMicroseconds / 1000000 % 60
                            songLength = minutes * 60 + seconds

                            //println("Position = $songPosition / $songLength")
                            if (songPosition < songLength) {
                                songPosition++
                                val delay: Long = 990
                                val delaySub: Float = songLength.toFloat() / 10
                                Thread.sleep(delay - delaySub.toLong())
                            }
                            adNotified = false
                        } else if (current.startsWith("https://open.spotify.com/ad/")) {
                            //ad playing, wait for it to finish.
                            if (!adNotified) {
                                adNotified = true
                                println("Ad playing.")
                                playStateListener.onAdPlaying()
                            }
                        } else {
                            //song has changed
                            songPosition = 0
                            //shouldMonitorSp = false
                            //playStateListener.onSongEnded("spotify", currentSong)
                        }
                    } else {
                        //Song is paused/stopped

                        if (current == currentSong.substringBefore("?")) {
                            adNotified = false
                            if (songPosition >= songLength - 10) {
                                //Song has ended
                                Thread.sleep(380)
                                songPosition = 0
                                //shouldMonitorSp = false
                                playStateListener.onSongEnded("spotify", currentSong)
                            } else {
                                //check if song position is 0
                                if (songPosition == 0) {
                                    //start playback

                                    //spotify broken?
                                    runCommand("playerctl -p spotify play")
                                    Thread.sleep(1000)
                                    if (runCommand(
                                            "playerctl -p spotify status",
                                            printOutput = false
                                        ) != "Playing"
                                    ) {
                                        runCommand("killall spotify && spotify &", printOutput = false)
                                        Thread.sleep(5000)

                                        runCommand(
                                            "playerctl -p spotify open spotify:track:${
                                            currentSong.substringAfter("spotify.com/track/")
                                                .substringBefore("?")}", ignoreOutput = false
                                        )
                                    }
                                } else {
                                    //Song is paused, wait for user to resume playback
                                }
                            }
                        } else {
                            //Song has changed

                            //reset songPosition and turn monitor off
                            songPosition = 0
                            //shouldMonitorSp = false
                            //break
                        }
                    }
                }
            }
        }.run()
    }

    /**
     * Adds track to queue
     * @param songLink song's link
     * @param position position in which the song should be added.
     */
    fun addToQueue(
        songLink: String, position: Int = if (songQueue.isNotEmpty()) {
            songQueue.size
        } else {
            0
        }
    ) {
        songQueue.add(position, songLink)
    }

    /**
     * Adds a list of links to the queue
     * @param songLinks list of links to add to the queue
     */
    fun addAllToQueue(songLinks: ArrayList<String>) {
        songQueue.addAll(songLinks)
    }

    /**
     * Clear the queue
     */
    fun clearQueue() {
        songQueue.clear()
    }

    /**
     * Get an ArrayList of links in the queue
     */
    fun getQueue(): ArrayList<String> = songQueue

    /**
     * Get currently playing track
     * @return returns a Track object based on the current song's link
     */
    fun nowPlaying(): Track {
        return when (currentSong.linkType) {
            "spotify" -> {
                Spotify(market).getTrack(currentSong)
            }
            "youtube" -> {
                Track("", "", YouTube().getTitle(currentSong), currentSong)
            }
            "soundcloud" -> {
                SoundCloud().getTrack(currentSong)
            }
            else -> {
                Track.Empty
            }
        }
    }

    /**
     * Adds a getTrack function to ArrayList<String>.
     * It can be used to get a Track object straight from the songQueue list, for example:
     * val track = songQueue.getTrack("https://open.spotify.com/track/1WtBDRmbb0q9hzDk2H4pyH")
     * @param songLink link to track
     * @return returns a Track object based on the link
     */
    private fun ArrayList<String>.getTrack(songLink: String): Track {
        return if (any { it == songLink }) {
            return when (songLink.linkType) {
                "spotify" -> {
                    Spotify(market).getTrack(songLink)
                }
                "youtube" -> {
                    Track("", "", YouTube().getTitle(songLink), songLink)
                }
                else -> {
                    Track.Empty
                }
            }
        } else {
            Track.Empty
        }
    }

    /**
     * Adds a linkType function to String.
     * Checks the given String if it contains a supported link type and returns it's type.
     */
    private val String.linkType: String
        get() = if (startsWith("https://open.spotify.com/") || (startsWith("spotify:") && contains(":track:"))) {
            "spotify"
        } else if (contains("youtube.com") || contains("youtu.be")) {
            "youtube"
        } else if (contains("soundcloud.com")){
            "soundcloud"
        }else{
            ""
        }

    /**
     * Shuffles the queue.
     */
    fun shuffleQueue() {
        songQueue.shuffle()
    }

    /**
     * Skips the current song and starts playing the next one in the queue, if it isn't empty.
     */
    fun skipSong() {
        if (songQueue.isNotEmpty()) {
            playNext()
        } else {
            val currentPlayers = runCommand("playerctl -l", printOutput = false).split("\n".toRegex())
            if (currentPlayers.size > 1) {
                for (player in currentPlayers) {
                    when (player) {
                        "mpv" -> runCommand("playerctl -p mpv stop", ignoreOutput = true)
                        "spotify" -> runCommand("playerctl -p spotify next", ignoreOutput = true)
                    }
                }
            }
        }
    }

    /**
     * Moves a desired track to a new position
     * @param link link to move
     * @param newPosition new position of the track
     */
    fun moveTrack(link: String, newPosition: Int) {
        if (newPosition < songQueue.size && newPosition >= 0) {
            for (i in songQueue.indices) {
                if (songQueue[i] == link) {
                    songQueue.removeAt(i)
                    songQueue.add(newPosition, link)
                    break
                }
            }
        }
    }

    /**
     * Resumes playback.
     */
    fun resume() {
        when (currentSong.linkType) {
            "spotify" -> runCommand("playerctl -p spotify play", printOutput = false, printErrors = false)
            "youtube", "soundcloud" -> runCommand("playerctl -p mpv play", printOutput = false, printErrors = false)
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        when (currentSong.linkType) {
            "spotify" -> runCommand("playerctl -p spotify pause", printOutput = false, printErrors = false)
            "youtube", "soundcloud" -> runCommand("playerctl -p mpv pause", printOutput = false, printErrors = false)
        }
    }

    /**
     * Stops the queue.
     */
    fun stopQueue() {
        songQueueActive.set(false)
        shouldMonitorSp.set(false)
        shouldMonitorYt.set(false)
        currentSong = ""
        runCommand("playerctl pause")
        runCommand("playerctl -p mpv stop")
        runCommand("killall playerctl")
    }

    /**
    @return returns true if the queue is active, false otherwise. Note that the queue can be active even though playback is paused.
     */
    fun queueActive(): Boolean {
        return songQueueActive.get()
    }


    //starts playing song queue
    fun playQueue(queueListener: PlayStateListener) {
        if (!queueActive()) {
            runCommand("killall playerctl", printOutput = false)
        }
        if (songQueue.size >= 1) {
            //queue is not empty, so start playing the list.
            playStateListener2 = queueListener
            playSong(songQueue[0])
        }
    }

    private fun playSong(songLink: String) {
        if (!songQueueActive.get()) {
            songQueueActive.set(true)
        }
        if (songLink.startsWith("https://open.spotify.com")) {
            if (runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) == "Playing")
                runCommand("playerctl -p mpv stop")
            currentSong = songLink
            songQueue.remove(currentSong)
            startSpotifyMonitor()
            val spThread = Thread {
                Runnable {
                    runCommand(
                        "playerctl -p spotify open spotify:track:${songLink.substringAfter("spotify.com/track/").substringBefore(
                            "?"
                        )}", ignoreOutput = false
                    )
                }.run()
            }
            spThread.start()

            //get current url from spotify player
            var currentUrl = runCommand(
                "playerctl -p spotify metadata --format '{{ xesam:url }}'",
                printOutput = false,
                printErrors = false
            )

            var adNotified = false

            while (currentUrl != currentSong.substringBefore("?")) {
                //get player status
                val playerStatus = runCommand("playerctl -p spotify status", printOutput = false, printErrors = false)
                //get current url from spotify player
                currentUrl = runCommand(
                    "playerctl -p spotify metadata --format '{{ xesam:url }}'",
                    printOutput = false,
                    printErrors = false
                )

                if (playerStatus == "No players found") {
                    //start spotify
                    val spotifyLauncherThread = Thread {
                        Runnable {
                            runCommand("spotify &", printOutput = false)
                        }.run()
                    }
                    spotifyLauncherThread.start()
                    while (!runCommand(
                            "ps aux | grep spotify",
                            printOutput = false
                        ).contains("--product-version=Spotify")
                    ) {
                        //do nothing
                    }
                    //wait a bit for spotify to start
                    Thread.sleep(5000)
                    val spThread2 = Thread {
                        Runnable {
                            runCommand(
                                "playerctl -p spotify open spotify:track:${songLink.substringAfter("spotify.com/track/").substringBefore(
                                    "?"
                                )}", ignoreOutput = false
                            )
                        }.run()
                    }
                    spThread2.start()
                }
                if (currentUrl.startsWith("https://open.spotify.com/ad/")) {
                    if (!adNotified) {
                        adNotified = true
                        onAdPlaying()
                    }
                }
            }
            if (currentUrl == currentSong.substringBefore("?")) {
                onNewSongPlaying("spotify", currentSong)
                songPosition = 0
                shouldMonitorYt.set(false)
                shouldMonitorSc.set(false)
                shouldMonitorSp.set(true)
            }

        } else if (songLink.startsWith("https://youtu.be") || songLink.startsWith("https://youtube.com") || songLink.startsWith(
                "https://www.youtube.com"
            )
        ) {
            if (runCommand("playerctl -p spotify status") == "Playing")
                runCommand("playerctl -p spotify pause")
            currentSong = songLink
            songQueue.remove(currentSong)
            startYouTubeMonitor()
            val ytThread = Thread {
                Runnable {
                    runCommand(
                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $songLink",
                        inheritIO = true,
                        ignoreOutput = true
                    )
                }.run()
            }
            ytThread.start()
            while (runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) != "Playing") {
                //wait for song to start
            }
            onNewSongPlaying("mpv", currentSong)
            shouldMonitorSp.set(false)
            shouldMonitorSc.set(false)
            shouldMonitorYt.set(true)
        } else if (songLink.startsWith("https://soundcloud.com")) {
            if (runCommand("playerctl -p spotify status") == "Playing")
                runCommand("playerctl -p spotify pause")
            currentSong = songLink
            songQueue.remove(currentSong)
            startSoundCloudMonitor()
            val scThread = Thread {
                Runnable {
                    runCommand(
                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $songLink",
                        inheritIO = true,
                        ignoreOutput = true
                    )
                }.run()
            }
            scThread.start()
            while (runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) != "Playing") {
                //wait for song to start
            }
            onNewSongPlaying("mpv", currentSong)
            shouldMonitorSp.set(false)
            shouldMonitorYt.set(false)
            shouldMonitorSc.set(true)
        } else {
            //song not supported so skip it
            songQueue.remove(currentSong)
            playSong(songQueue[0])
        }
    }

    private fun playNext() {
        //play next song in queue if not empty
        if (songQueue.isNotEmpty()) {
            shouldMonitorYt.set(false)
            shouldMonitorSp.set(false)
            shouldMonitorSc.set(false)
            runCommand("playerctl -p spotify pause", printOutput = false)
            runCommand("playerctl -p mpv stop", printOutput = false, printErrors = false)
            playSong(songQueue[0])
        } else {
            shouldMonitorSp.set(false)
            shouldMonitorYt.set(false)
            shouldMonitorSc.set(false)
            songQueueActive.set(false)
            currentSong = ""
        }
    }


    override fun onSongEnded(player: String, track: String) {
        //song ended, so play the next one in the queue
        playNext()
    }

    override fun onNewSongPlaying(player: String, track: String) {
        println("New song started.")
        playStateListener2.onNewSongPlaying(player, track)
    }

    override fun onAdPlaying() {}

    private fun startSpotifyMonitor() {
        if (!spotifyListenerThread.isAlive)
            spotifyListenerThread.start()
    }

    private fun startYouTubeMonitor() {
        val youTubeListenerThread = Thread {
            Runnable {
                while (songQueueActive.get()) {
                    if (shouldMonitorYt.get()) {
                        if (runCommand(
                                "playerctl -p mpv status",
                                printOutput = false,
                                printErrors = false
                            ) == "Playing"
                        ) {
                            //val current = runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                            //playStateListener.onNewSongPlaying("mpv", runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false))
                        } else {
                            val current = runCommand(
                                "playerctl -p mpv metadata --format '{{ title }}'",
                                printOutput = false,
                                printErrors = false
                            )
                            /*
                            if (current != runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false) && !current.contains("v=${currentSong.substringAfter("v=")}") && current == "No players found"){
                                playStateListener.onSongEnded("mpv", currentSong)
                            }
                            */
                            if (!current.contains("v=") && !current.contains(
                                    runCommand(
                                        "youtube-dl --geo-bypass -s -e \"$currentSong\"",
                                        printOutput = false, printErrors = false
                                    )
                                ) && current == "No players found"
                            ) {
                                playStateListener.onSongEnded("mpv", currentSong)
                                break
                            }
                        }
                    }
                    Thread.sleep(990)
                }
            }.run()
        }
        if (!youTubeListenerThread.isAlive) {
            youTubeListenerThread.start()
        }
    }

    private fun startSoundCloudMonitor() {
        val soundCloudListenerThread = Thread {
            Runnable {
                while (songQueueActive.get()) {
                    if (shouldMonitorSc.get()) {
                        if (runCommand(
                                "playerctl -p mpv status",
                                printOutput = false,
                                printErrors = false
                            ) == "Playing"
                        ) {
                        } else {
                            val current = runCommand(
                                "playerctl -p mpv metadata --format '{{ title }}'",
                                printOutput = false,
                                printErrors = false
                            )
                            if (!current.contains("soundcloud.com") && !current.contains(
                                    runCommand(
                                        "youtube-dl -s -e \"$currentSong\"",
                                        printOutput = false, printErrors = false
                                    )
                                ) && current == "No players found"
                            ) {
                                playStateListener.onSongEnded("mpv", currentSong)
                                break
                            }
                        }
                    }
                    Thread.sleep(990)
                }
            }.run()
        }
        if (!soundCloudListenerThread.isAlive) {
            soundCloudListenerThread.start()
        }
    }

}

interface PlayStateListener {
    fun onSongEnded(player: String, track: String)
    fun onNewSongPlaying(player: String, track: String)
    fun onAdPlaying()
}
