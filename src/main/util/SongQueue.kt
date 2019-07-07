package src.main.util

class SongQueue : PlayStateListener {
    private val songQueue = ArrayList<String>()

    private val playStateListener: PlayStateListener = this
    private lateinit var playStateListener2: PlayStateListener

    private var currentSong = ""
    private var shouldMonitorSp = false
    private var shouldMonitorYt = false
    private var isPlaying = false




    fun addToQueue(songLink: String){
        songQueue.add(songLink)
    }

    fun addAllToQueue(songLinks: ArrayList<String>){
        songQueue.addAll(songLinks)
    }

    fun clearQueue(){
        songQueue.clear()
    }

    fun getQueue(): ArrayList<String> = songQueue

    fun shuffleQueue() {
        songQueue.shuffle()
    }

    fun skipSong(){
        if (songQueue.size >= 2){
            playNext()
        }else{
            val currentPlayers = runCommand("playerctl -l").split("\n".toRegex())
            if (currentPlayers.size > 1){
                for (player in currentPlayers){
                    when (player){
                        "mpv" -> runCommand("playerctl -p mpv stop", ignoreOutput = true)
                        "spotify" -> runCommand("playerctl -p spotify next", ignoreOutput = true)
                    }
                }
            }
        }
    }


    //starts playing song queue
    fun playQueue(queueListener: PlayStateListener){
        if (songQueue.size >= 1){
            //queue is not empty, so start playing the list.

            playStateListener2 = queueListener
            if (!isPlaying){
                //play first song in queue
                isPlaying = true
                playSong(songQueue[0])
            }
        }
    }

    private fun playSong(songLink: String){
        if (songLink.startsWith("https://open.spotify.com")){
            currentSong = songLink
            runCommand("playerctl -p spotify open spotify:track:${songLink.substringAfter("spotify.com/track/").substringBefore("?si=")}", inheritIO = true, ignoreOutput = true)
            Thread.sleep(5000)
            if (spotifyListenerThread.isAlive){
                shouldMonitorYt = false
                shouldMonitorSp = true
            }else{
                shouldMonitorYt = false
                shouldMonitorSp = true
                startSpotifyMonitor()
            }

        }else if (songLink.startsWith("https://youtu.be") || songLink.startsWith("https://youtube.com") || songLink.startsWith("https://www.youtube.com")){
            if (runCommand("playerctl -p spotify status") == "Playing")
                runCommand("playerctl -p spotify pause")
            currentSong = songLink
            val ytThread = Thread {
                Runnable {
                     runCommand("mpv --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $songLink", inheritIO = true, ignoreOutput = true)
                }.run()
            }
            ytThread.start()
            Thread.sleep(5000)
            if (youTubeListenerThread.isAlive){
                shouldMonitorSp = false
                shouldMonitorYt = true
            }else{
                shouldMonitorSp = false
                shouldMonitorYt = true
                startYouTubeMonitor()
            }
        }
    }

    private fun playNext(){
        songQueue.remove(currentSong)
        if (songQueue.size > 0)
            playSong(songQueue[0])
        else{
            shouldMonitorSp = false
            shouldMonitorYt = false
            isPlaying = false
        }
    }


    override fun onPlayStateChanged(player: String, track: String) {
        if (songQueue.size >= 2){
            if (songQueue[1].startsWith("https://open.spotify.com")){
                runCommand("playerctl -p mpv stop", ignoreOutput = true)
                Thread.sleep(100)
                playNext()
            }else{
                runCommand("playerctl -p spotify pause", ignoreOutput = true)
                Thread.sleep(100)
                playNext()
            }
        }else{
            //stop queue.
            shouldMonitorSp = false
            shouldMonitorYt = false
            isPlaying = false
            clearQueue() 
        }
    }

    override fun onNewSongPlaying(player: String, track: String){
        playStateListener2.onNewSongPlaying(player, track)
    }

    private val spotifyListenerThread = Thread {
        Runnable{
            while (shouldMonitorSp){
                if (runCommand("playerctl -p spotify status") == "Playing"){
                    val current = runCommand("playerctl -p spotify metadata --format '{{ xesam:url }}'", ignoreOutput = false)
                    if (current != currentSong && !current.startsWith("https://open.spotify.com/ad/")){
                        if (playStateListener != null){
                            Thread.sleep(500)
                            playStateListener.onPlayStateChanged("spotify", current)
                        }
                    }
                    Thread.sleep(1000)
                }
            }
        }.run()
    }

    private val youTubeListenerThread = Thread {
        Runnable{
            while (shouldMonitorYt){
                if (runCommand("playerctl -p mpv status") == "Playing"){
                        playStateListener.onNewSongPlaying("mpv", runCommand("playerctl -p mpv metadata --format '{{ title }}'"))
                }else if (runCommand("playerctl -p mpv status") == "No players found"){
                    val current = runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                    if (current != runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", ignoreOutput = false, printErrors = false) && !current.contains("v=$currentSong")){
                        Thread.sleep(500)
                        playStateListener.onPlayStateChanged("mpv", currentSong)
                    }
                }
            }
        }.run()
    }


    private fun startSpotifyMonitor(){
        spotifyListenerThread.start()
    }

    private fun startYouTubeMonitor(){
        youTubeListenerThread.start()
    }


}

public interface PlayStateListener{
    fun onPlayStateChanged(player: String, track: String)
    fun onNewSongPlaying(player: String, track: String)
}
