package src.main.util

import src.main.services.Spotify
import src.main.services.Track
import src.main.services.YouTube

class SongQueue : PlayStateListener {
    @Volatile
    private var songQueue = ArrayList<String>()

    private val playStateListener: PlayStateListener = this
    private lateinit var playStateListener2: PlayStateListener

    @Volatile
    private var currentSong = ""
    @Volatile
    private var shouldMonitorSp = false
    @Volatile
    private var shouldMonitorYt = false
    @Volatile
    private var isPlaying = false
    @Volatile
    private var songPosition = 0


    fun addToQueue(songLink: String) {
        songQueue.add(songLink)
    }

    fun addAllToQueue(songLinks: ArrayList<String>) {
        songQueue.addAll(songLinks)
    }

    fun clearQueue() {
        songQueue.clear()
    }

    fun getQueue(): ArrayList<String> = songQueue

    fun nowPlaying(): Track {
        return when (currentSong.linkType) {
            "spotify" -> {
                Spotify().getTrack(currentSong)
            }
            "youtube" -> {
                Track("", "", YouTube().getTitle(currentSong), currentSong)
            }
            else -> {
                Track.Empty
            }
        }
    }

    private fun ArrayList<String>.getTrack(songLink: String): Track {
        return if (any { it == songLink }) {
            return when (songLink.linkType) {
                "spotify" -> {
                    Spotify().getTrack(songLink)
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

    private val String.linkType: String
        get() = if (startsWith("https://open.spotify.com/") || (startsWith("spotify:") && contains(":track:"))) {
            "spotify"
        } else {
            "youtube"
        }

    fun shuffleQueue() {
        songQueue.shuffle()
    }

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

    fun moveTrack(link: String, newPosition: Int) {
        if (newPosition < songQueue.size) {
            for (i in songQueue.indices) {
                if (songQueue[i] == link) {
                    songQueue.removeAt(i)
                    songQueue.add(newPosition, link)
                    break
                }
            }
        }
    }


    fun stopQueue() {
        isPlaying = false
        shouldMonitorSp = false
        shouldMonitorYt = false
        currentSong = ""
        runCommand("playerctl pause")
        runCommand("playerctl -p mpv stop")
        runCommand("killall playerctl")
    }

    /**
    @return returns true if the queue is active, false otherwise. Note that the queue can be active even though playback is paused.
     */
    fun queueActive(): Boolean {
        return isPlaying
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
        if (!isPlaying) {
            isPlaying = true
            startYouTubeMonitor()
            startSpotifyMonitor()
        }
        if (songLink.startsWith("https://open.spotify.com")) {
            if (runCommand("playerctl -p mpv status") == "Playing")
                runCommand("playerctl -p mpv stop")
            //startSpotifyMonitor()
            currentSong = songLink
            songQueue.remove(currentSong)
            val spThread = Thread {
                Runnable {
                    runCommand(
                        "playerctl -p spotify open spotify:track:${songLink.substringAfter("spotify.com/track/").substringBefore(
                            "?si="
                        )}", ignoreOutput = false
                    )
                }.run()
            }
            spThread.start()
            onNewSongPlaying("spotify", currentSong)
            songPosition = 0
            shouldMonitorSp = true
            shouldMonitorYt = false
        } else if (songLink.startsWith("https://youtu.be") || songLink.startsWith("https://youtube.com") || songLink.startsWith(
                "https://www.youtube.com"
            )
        ) {
            if (runCommand("playerctl -p spotify status") == "Playing")
                runCommand("playerctl -p spotify pause")
            currentSong = songLink
            songQueue.remove(currentSong)
            val ytThread = Thread {
                Runnable {
                    runCommand(
                        "mpv --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $songLink",
                        inheritIO = true,
                        ignoreOutput = true
                    )
                }.run()
            }
            ytThread.start()
            Thread.sleep(7000)
            onNewSongPlaying("mpv", currentSong)
            shouldMonitorSp = false
            shouldMonitorYt = true
        }
    }

    private fun playNext() {
        //play next song in queue if not empty
        if (songQueue.isNotEmpty()) {
            shouldMonitorYt = false
            runCommand("playerctl -p spotify pause", printOutput = false)
            runCommand("playerctl -p mpv stop", printOutput = false)
            playSong(songQueue[0])
        } else {
            shouldMonitorSp = false
            shouldMonitorYt = false
            isPlaying = false
            currentSong = ""
        }
    }


    override fun onSongEnded(player: String, track: String) {
        //song ended, so play the next one in the queue
        playNext()
    }

    override fun onNewSongPlaying(player: String, track: String) {
        playStateListener2.onNewSongPlaying(player, track)
        println("New song started")
    }

    private fun startSpotifyMonitor() {
        val spotifyListenerThread = Thread {
            Runnable {
                var songLength = 0 //Song length in seconds
                while (isPlaying) {
                    val playerStatus = runCommand("playerctl -p spotify status", printOutput = false)
                    val current =
                        runCommand("playerctl -p spotify metadata --format '{{ xesam:url }}'", printOutput = false)

                    if (shouldMonitorSp) {
                        if (playerStatus == "Playing") {
                            //song is playing

                            if (current == currentSong) {
                                val lengthMicroseconds =
                                    runCommand("playerctl -p spotify metadata --format '{{ mpris:length }}'", printOutput = false).toInt()
                                val minutes = lengthMicroseconds / 1000000 / 60
                                val seconds = lengthMicroseconds / 1000000 % 60
                                songLength = minutes * 60 + seconds

                                //println("Position = $songPosition / $songLength")
                                if (songPosition < songLength) {
                                    songPosition++
                                    Thread.sleep(1000)
                                }
                            }else{
                                //Song changed
                                songPosition = 0
                                shouldMonitorSp = false
                                playStateListener.onSongEnded("spotify", currentSong)
                            }
                        } else {
                            //Song is paused/stopped

                            if (current == currentSong) {

                                if (songPosition >= songLength - 2) {
                                    //Song has ended
                                    songPosition = 0
                                    shouldMonitorSp = false
                                    playStateListener.onSongEnded("spotify", currentSong)
                                } else {
                                    //Song is paused
                                }
                            }else{
                                //Song has changed

                                //play next song
                                songPosition = 0
                                shouldMonitorSp = false
                                playStateListener.onSongEnded("spotify", currentSong)
                            }
                        }
                    }
                }
            }.run()
        }
        if (!spotifyListenerThread.isAlive) {
            spotifyListenerThread.start()
        }
    }

    private fun startYouTubeMonitor() {
        val youTubeListenerThread = Thread {
            Runnable {
                while (isPlaying) {
                    if (shouldMonitorYt) {
                        if (runCommand("playerctl -p mpv status", printOutput = false) == "Playing") {
                            //val current = runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                            //playStateListener.onNewSongPlaying("mpv", runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false))
                        } else {
                            val current = runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                            /*
                            if (current != runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false) && !current.contains("v=${currentSong.substringAfter("v=")}") && current == "No players found"){
                                playStateListener.onSongEnded("mpv", currentSong)
                            }
                            */
                            println("current = $current")
                            if (!current.contains("v=") && !current.contains(
                                    runCommand(
                                        "youtube-dl --geo-bypass -s -e \"$currentSong\"",
                                        printErrors = false
                                    )
                                ) && current == "No players found"
                            )
                                playStateListener.onSongEnded("mpv", currentSong)
                        }
                    }
                }
            }.run()
        }
        if (!youTubeListenerThread.isAlive) {
            youTubeListenerThread.start()
        }
    }


}

interface PlayStateListener {
    fun onSongEnded(player: String, track: String)
    fun onNewSongPlaying(player: String, track: String)
}
