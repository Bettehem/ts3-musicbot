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

    fun moveTrack(link: String, newPosition: Int){
        if (newPosition < songQueue.size){
            for (i in songQueue.indices){
                if (songQueue[i] == link){
                    songQueue.removeAt(i)
                    songQueue.add(newPosition, link)
                    break
                }
            }
        }
    }


    fun stopQueue(){
        isPlaying = false
        shouldMonitorSp = false
        shouldMonitorYt = false
        runCommand("playerctl pause")
    }


    //starts playing song queue
    fun playQueue(queueListener: PlayStateListener){
        if (songQueue.size >= 1){
            //queue is not empty, so start playing the list.

            playStateListener2 = queueListener
            playSong(songQueue[0])
        }
    }

    private fun playSong(songLink: String){
        if (!isPlaying){
            isPlaying = true
            startYouTubeMonitor()
            startSpotifyMonitor()
        }
        if (songLink.startsWith("https://open.spotify.com")){
            if (runCommand("playerctl -p mpv status") == "Playing")
                runCommand("playerctl -p mpv stop")
            currentSong = songLink
            runCommand("playerctl -p spotify open spotify:track:${songLink.substringAfter("spotify.com/track/").substringBefore("?si=")}", inheritIO = true, ignoreOutput = true)
            Thread.sleep(3000)
            shouldMonitorSp = true
            shouldMonitorYt = false
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
            shouldMonitorYt = true
            shouldMonitorSp = false
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
            /*if (songQueue[1].startsWith("https://open.spotify.com")){
                runCommand("playerctl -p mpv pause", ignoreOutput = true)
                Thread.sleep(100)
                playNext()
            }else{
                runCommand("playerctl -p spotify pause", ignoreOutput = true)
                Thread.sleep(100)
                playNext()
            }*/
            runCommand("playerctl pause")
            playNext()
        }else{
            //stop queue.
            shouldMonitorSp = false
            shouldMonitorYt = false
            isPlaying = false
            currentSong = ""
            clearQueue() 
        }
    }

    override fun onNewSongPlaying(player: String, track: String){
        playStateListener2.onNewSongPlaying(player, track)
    }

    private val spotifyListenerThread = Thread {
        Runnable{
            while (isPlaying){
                if (shouldMonitorSp){
                    if (runCommand("playerctl -p spotify status") == "Playing"){
                        val current = runCommand("playerctl -p spotify metadata --format '{{ xesam:url }}'", ignoreOutput = false)
                        if (current != currentSong.substringBefore("?si=") && !current.startsWith("https://open.spotify.com/ad/") && currentSong.startsWith("https://open.spotify.com")){
                            if (playStateListener != null){
                                playStateListener.onPlayStateChanged("spotify", currentSong)
                            }
                        }
                    }
                }    
            }
        }.run()
    }

    private val youTubeListenerThread = Thread {
        Runnable{
            while (isPlaying){
                if (shouldMonitorYt){
                    if (runCommand("playerctl -p mpv status") == "Playing"){
                        playStateListener.onNewSongPlaying("mpv", runCommand("playerctl -p mpv metadata --format '{{ title }}'"))
                    }else{
                        val current = runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                        if (current != runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", ignoreOutput = false, printErrors = false) && !current.contains("v=${currentSong.substringAfter("v=")}") && current == "No players found"){
                            playStateListener.onPlayStateChanged("mpv", currentSong)
                        }
                    }
                }
            }
        }.run()
    }


    private fun startSpotifyMonitor(){
        if (!spotifyListenerThread.isAlive){
            spotifyListenerThread.start()
        }
    }

    private fun startYouTubeMonitor(){
        if (!youTubeListenerThread.isAlive){
            youTubeListenerThread.start()
        }
    }


}

public interface PlayStateListener{
    fun onPlayStateChanged(player: String, track: String)
    fun onNewSongPlaying(player: String, track: String)
}
