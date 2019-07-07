package src.main.chat

import src.main.services.Spotify
import src.main.util.SongQueue
import src.main.util.PlayStateListener
import src.main.util.Time
import src.main.util.runCommand
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList

class ChatReader(private var chatFile: File, private var onChatUpdateListener: ChatUpdateListener, private val apikey: String = ""): PlayStateListener {

    private var chatListenerThread: Thread
    private var shouldRead = false
    private var ytLink = ""
    private var ytResults = emptyList<String>()
    private var songQueue = SongQueue()

    init {
        //start listening to new messages in chat
        chatListenerThread = Thread {
            Runnable {
                var currentLine = ""
                while (shouldRead) {
                    val lines = Files.readAllLines(chatFile.toPath().toAbsolutePath(), StandardCharsets.UTF_8)

                    lines.last()
                    if (currentLine != lines.last()) {
                        currentLine = lines.last()
                        chatUpdated(currentLine)
                    }

                    Thread.sleep(100)
                }

            }.run()
        }

    }

    private fun parseLink(link: String): String {
        when (chatFile.extension) {
            "html" -> {
                return if (link.contains("href=\"")) {
                    link.split(" ".toRegex())[2].split("href=\"".toRegex())[1].split("\">".toRegex())[0]
                } else {
                    link
                }
            }

            "txt" -> {
                return link.split(" ".toRegex())[1].substringAfter("[URL]").substringBefore("[/URL]")
            }
        }

        return ""

    }

    fun startReading(): Boolean {
        shouldRead = true
        return if (chatFile.isFile){
            chatListenerThread.start()
            true
        }else{
            false
        }
    }

    fun stopReading() {
        shouldRead = false
    }

    fun parseLine(userName: String, message: String) {

        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {
            when (message.split(" ".toRegex())[0]) {

                "%help" -> {
                    val lines = ArrayList<String>()
                    lines.add("")
                    lines.add("General commands:")
                    lines.add("%help                       -Shows this help message")
                    lines.add("%queue-add <link>           -Add song to queue")
                    lines.add("%queue-play                 -Play the song queue")
                    lines.add("%queue-list                 -Lists current songs in queue")
                    lines.add("%queue-clear                -Clears the song queue")
                    lines.add("%queue-shuffle              -Shuffles the queue")
                    lines.add("%queue-skip                 -Skips current song")
                    lines.add("")
                    printToChat(userName, lines, apikey)
                    lines.clear()
                    lines.add("Player specific commands:")
                    lines.add("")
                    lines.add("Spotify commands:")
                    lines.add("%sp-pause                   -Pauses the Spotify playback")
                    lines.add("%sp-resume                  -Resumes the Spotify playback")
                    lines.add("%sp-play                    -Resumes the Spotify playback")
                    lines.add("%sp-skip                    -Skips the currently playing track")
                    lines.add("%sp-next                    -Skips the currently playing track")
                    lines.add("%sp-prev                    -Plays the previous track")
                    lines.add("%sp-playsong <track>        -Plays a Spotify song. <track> should be your song link or Spotify URI")
                    lines.add("%sp-playlist <playlist>     -Plays a Spotify playlist. <playlist> should be your playlist's link or Spotify URI")
                    lines.add("%sp-nowplaying              -Shows information on currently playing track")
                    lines.add("%sp-search <type> <text>    -Search on Spotify. <type> can be track or playlist")
                    printToChat(userName, lines, apikey)

                    Thread.sleep(200)
                    lines.clear()
                    lines.add("YouTube commands:")
                    lines.add("%yt-pause                   -Pauses the YouTube playback")
                    lines.add("%yt-resume                  -Resumes the YouTube playback")
                    lines.add("%yt-play                    -Resumes the YouTube playback")
                    lines.add("%yt-stop                    -Stops the YouTube playback")
                    lines.add("%yt-playsong <link>         -Plays a YouTube song based on link")
                    lines.add("%yt-nowplaying              -Shows information on currently playing track")
                    lines.add("%yt-search <text>           -Search on YouTube. Shows 10 first results.")
                    lines.add("%yt-sel <result>            -Used to select one of the search results %yt-search command gives")

                    printToChat(userName, lines, apikey)
                }

                "%queue-add" ->  {

                    if (message.substringAfter("%queue-add ").contains("spotify:") || message.substringAfter("%queue-add ").contains("https://open.spotify.com")){
                        when {
                            message.substringAfter("%queue-add ").contains("spotify:track:") -> songQueue.addToQueue("https://open.spotify.com/track/${message.substringAfter("spotify:track:")}")
                            message.substringAfter("%queue-add ").contains("https://open.spotify.com/") && message.substringAfter("https://open.spotify.com/").contains("playlist") -> {
                                val trackList = Spotify().getPlaylistTracks(parseLink(message))
                                for (track in trackList){
                                    songQueue.addToQueue(track.link)
                                }
                            }
                            message.substringAfter("%queue-add ").contains("spotify:") && message.substringAfter("spotify:").contains(":playlist:") -> {
                                val trackList = Spotify().getPlaylistTracks("https://open.spotify.com/playlist/${message.substringAfter(":playlist:")}")
                                for (track in trackList){
                                    songQueue.addToQueue(track.link)
                                }
                            }
                            else -> {
                                songQueue.addToQueue(parseLink(message))
                            }
                        }
                    }else{
                        songQueue.addToQueue(parseLink(message))
                    }
                }
                "%queue-play" -> songQueue.playQueue(this)
                "%queue-list" -> {
                    printToChat(userName, listOf("Song Queue:"), apikey)
                    val queueList = ArrayList<String>()
                    queueList.clear()
                    queueList.addAll(songQueue.getQueue())
                    when {
                        queueList.size in 1..15 -> {
                            queueList.add(0, "")
                            printToChat(userName, queueList, apikey)
                        }
                        queueList.isEmpty() -> printToChat(userName, listOf("Queue is empty!"), apikey)
                        else -> for (item in songQueue.getQueue()){
                            if (queueList.size < 15)
                                queueList.add(item)
                            else{
                                queueList.add(0, "")
                                printToChat(userName, queueList, apikey)
                                queueList.clear()
                            }
                        }
                    }

                }
                "%queue-clear" -> songQueue.clearQueue()
                "%queue-shuffle" -> songQueue.shuffleQueue()
                "%queue-skip" -> songQueue.skipSong()





                "%sp-pause" -> runCommand("playerctl -p spotify pause && sleep 1")
                "%sp-resume" -> runCommand("playerctl -p spotify play && sleep 1")
                "%sp-play" -> runCommand("playerctl -p spotify play && sleep 1")

                "%sp-skip" -> runCommand("playerctl -p spotify next && sleep 1")
                "%sp-next" -> runCommand("playerctl -p spotify next && sleep 1")
                "%sp-prev" -> runCommand("playerctl -p spotify previous && sleep 0.1 & playerctl -p spotify previous")

                //Play Spotify song based on link or URI
                "%sp-playsong" -> {
                    if (message.substringAfter("%sp-playsong").isNotEmpty()){
                        if (parseLink(message).startsWith("https://open.spotify.com/track")) {
                            runCommand("playerctl -p spotify open spotify:track:${parseLink(message).split("track/".toRegex())[1].split("\\?si=".toRegex())[0]}")
                        } else {
                            runCommand("playerctl -p spotify open spotify:track:${message.split(" ".toRegex())[1].split("track:".toRegex())[1]}")
                        }
                    }else{
                        printToChat(userName, listOf("Error! Please provide a song to play!"), apikey)
                    }
                }

                //Play Spotify playlist based on link or URI
                "%sp-playlist" -> {
                    if (message.split(" ".toRegex())[1].startsWith("spotify:user:") && message.split(" ".toRegex())[1].contains(":playlist:")){
                        runCommand("playerctl -p spotify open spotify:user:${message.split(" ".toRegex())[1].split(":".toRegex())[2]}:playlist:${message.split(":".toRegex()).last()}")
                    }else if (message.split(" ".toRegex())[1].startsWith("spotify:playlist")){
                        runCommand("playerctl -p spotify open spotify:playlist:${message.substringAfter("playlist:")}")
                    }else if (parseLink(message).startsWith("https://open.spotify.com/")){
                        runCommand("playerctl -p spotify open spotify:playlist:${parseLink(message).substringAfter("playlist/").substringBefore("?")}")
                    }
                }

                "%sp-nowplaying" -> {
                    val lines = ArrayList<String>()
                    lines.add("Now playing on Spotify:")
                    val nowPlaying = runCommand("playerctl -p spotify metadata").split("\n".toRegex())
                    for (line in nowPlaying){
                        when (line.substringAfter("xesam:").split("\\s+".toRegex())[0]){
                            "album"     -> lines.add("Album:\t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                            "artist"    -> lines.add("Artist:   \t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                            "title"     -> lines.add("Title:    \t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                            "url"       -> lines.add("Link:  \t${line.substringAfter(line.substringAfter("xesam:").split("\\s+".toRegex())[0])}")
                        }
                    }
                    printToChat(userName, lines, apikey)

                }

                "%sp-search" -> {

                    if (message.split(" ".toRegex())[1].toLowerCase() == "track" || message.split(" ".toRegex())[1].toLowerCase() == "playlist"){
                        val lines = ArrayList<String>()
                        lines.add("Searching, please wait...")
                        printToChat(userName, lines, apikey)
                        lines.clear()
                        val searchedLines = ArrayList<String>()
                        searchedLines.addAll(Spotify().searchSpotify(message.split(" ".toRegex())[1].toLowerCase(), message.substringAfter(message.split(" ".toRegex())[1]).replace("&owner=\\w+", "")).split("\n".toRegex()))
                        for (line in searchedLines.indices){
                            if (message.substringAfter(message.split(" ".toRegex())[1]).contains("&owner=") && searchedLines[line].replace(" ", "").toLowerCase().contains("Owner:${message.substringAfter(message.split(" ".toRegex())[1]).substringAfter("&owner=").substringBefore("&")}".toLowerCase())){
                                lines.addAll(listOf(searchedLines[line-1], searchedLines[line], searchedLines[line+1], searchedLines[line+2]))
                            }else if (!message.substringAfter(message.split(" ".toRegex())[1]).contains("&owner=")){
                                lines.add(searchedLines[line])
                            }
                        }

                        if(lines.size == 0){
                            lines.add("No search results with \"${message.substringAfter(message.split(" ".toRegex())[1])}\"!")
                        }

                        when (message.split(" ".toRegex())[1]){
                            "track" -> {
                                if (lines.size <= 15){
                                    lines.add(0, "Search results:")
                                    printToChat(userName, lines, apikey)
                                }else{
                                    val resultLines = ArrayList<String>()
                                    resultLines.add("Search results:")
                                    printToChat(userName, resultLines, apikey)
                                    resultLines.clear()
                                    for (line in lines){
                                        if (resultLines.size < 14){
                                            resultLines.add(line)
                                        }else{
                                            resultLines.add(0, "")
                                            printToChat(userName, resultLines, apikey)
                                            resultLines.clear()
                                        }

                                    }
                                }
                            }

                            "playlist" -> {
                                if (lines.size <= 12){
                                    lines.add(0, "Search results:")
                                    printToChat(userName, lines, apikey)
                                }else{
                                    val resultLines = ArrayList<String>()
                                    resultLines.add("Search results:")
                                    printToChat(userName, resultLines, apikey)
                                    resultLines.clear()
                                    for (line in lines){
                                        if (resultLines.size < 11){
                                            resultLines.add(line)
                                        }else{
                                            resultLines.add(0, "")
                                            printToChat(userName, resultLines, apikey)
                                            resultLines.clear()
                                        }

                                    }
                                }
                            }
                        }
                    }else{
                        val lines = ArrayList<String>()
                        lines.add("Error! \"${message.split(" ".toRegex())[1]}\" is not a valid search type! See %help for more info.")
                        printToChat(userName, lines, apikey)
                    }

                }


                "%yt-pause" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-resume" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-play" -> runCommand("echo \"cycle pause\" | socat - /tmp/mpvsocket")
                "%yt-stop" -> runCommand("echo \"stop\" | socat - /tmp/mpvsocket")


                "%yt-playsong" -> {
                    //val link = message.split(" ".toRegex())[2].split("href=\"".toRegex())[1].split("\">".toRegex())[0]
                    ytLink = parseLink(message)
                    //Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl -o - \"$link\" | mpv --no-video --input-ipc-server=/tmp/mpvsocket - &"))
                    if (ytLink.isNotEmpty()){
                        Thread{
                            Runnable {
                                run {
                                    runCommand("mpv --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $ytLink", inheritIO = true, ignoreOutput = true)
                                }
                            }.run()
                        }.start()
                    }
                }


                "%yt-nowplaying" -> {
                    val lines = ArrayList<String>()
                    lines.add("Now playing on YouTube:")
                    lines.addAll(runCommand("youtube-dl --geo-bypass -s -e \"$ytLink\"", printErrors = false).split("\n".toRegex()))
                    lines.add("Link: $ytLink")

                    printToChat(userName, lines, apikey)
                }

                "%yt-search" -> {
                    var lines = ArrayList<String>()
                    lines.add("Searching, please wait...")
                    printToChat(userName, lines, apikey)

                    lines = ArrayList()
                    lines.add("YouTube Search Results:")
                    ytResults = runCommand("youtube-dl --geo-bypass -s -e \"ytsearch10:${message.replace("\"", "\\\"").replace("'", "\'")}\"", printErrors = true).split("\n".toRegex())
                    for (i in ytResults.indices) {
                        lines.add("${i + 1}: ${ytResults[i]}")
                    }
                    lines.add("Use command \"%yt-sel\" followed by the search result number to play the result, for example:")
                    lines.add("%yt-sel 4")

                    printToChat(userName, lines, apikey)
                }

                "%yt-sel" -> {
                    if (ytResults.isNotEmpty()){
                        val selection = ytResults[message.split(" ".toRegex())[1].toInt() - 1]
                        val videoID = runCommand("youtube-dl -s --get-id \"ytsearch1:${selection.replace("'", "\'").replace("\"", "\\\"")}\"")
                        val link = "https://youtu.be/$videoID"
                        ytLink = link
                        parseLine("", "%yt-playsong $link")
                    }
                }

                else -> {
                    if (userName == "__console__"){
                        when (message.split(" ".toRegex())[0]){
                            "%say" -> {
                                val lines = ArrayList<String>()
                                lines.addAll(message.substringAfterLast("%say ").split("\n"))
                                printToChat("", lines, apikey)
                            }
                        }
                    }else{
                        val lines = ArrayList<String>()
                        lines.add("Command not found! Try %help to see available commands.")
                        printToChat(userName, lines, apikey)
                    }
                }

            }
        }
    }

    //focus window and use xdotool to type message
    private fun printToChat(message: List<String>, focusWindow: Boolean) {
        if (focusWindow) {
            //Runtime.getRuntime().exec(arrayOf("sh", "-c", "sleep 1 && xdotool windowraise \$(xdotool search --classname \"ts3client_linux_amd64\" | tail -n1)"))
            //Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool windowactivate --sync \$(xdotool search --classname \"ts3client_linux_amd64\" | tail -n1) && sleep 0.5 && xdotool key ctrl+Return && sleep 0.5"))
            runCommand("xdotool windowactivate --sync \$(xdotool search --classname \"ts3client_linux_amd64\" | tail -n1) && sleep 0.5 && xdotool key ctrl+Return && sleep 0.5")
        }
        for (line in message) {
            //Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool type --delay 25 \"${line.replace("\"", "\\\"")}\" && sleep 0.25 && xdotool key ctrl+Return")).waitFor()
            runCommand("xdotool type --delay 25 \"${line.replace("\"", "\\\"")}\" && sleep 0.25 && xdotool key ctrl+Return")
        }
        //Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool sleep 0.5 key \"Return\""))
        runCommand("xdotool sleep 0.5 key \"Return\"")
    }

    //use ClientQuery to send message (requires apikey)
    private fun printToChat(userName: String, messageLines: List<String>, apikey: String){
        if (userName == "__console__"){
            messageLines.forEach { println(it) }
        }else{
            if (apikey.isNotEmpty()){
                val stringBuffer = StringBuffer()
                messageLines.forEach { stringBuffer.append(it+"\n") }
                runCommand("(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${stringBuffer.toString().replace(" ","\\s").replace("\n", "\\n").replace("/", "\\/").replace("|", "\\p").replace("'", "\\'").replace("\"","\\\"").replace("$", "\\$")}\"; echo quit) | nc localhost 25639")
            }else{
                printToChat(messageLines, true)
            }
        }
    }

    private fun chatUpdated(line: String) {

        when (chatFile.extension) {
            "html" -> {
                //extract message
                val userName = line.split("client://".toRegex())[1].split("&quot;".toRegex())[1]
                val time = Time(Calendar.getInstance())
                val rawTime = line.split("TextMessage_Time\">&lt;".toRegex())[1]
                        .split("&gt;</span>".toRegex())[0]
                        .split(":".toRegex())
                time.hour = rawTime[0]
                time.minute = rawTime[1]
                time.second = rawTime[2]

                val userMessage = line.split("TextMessage_Text\">".toRegex())[1].split("</span>".toRegex())[0]
                parseLine(userName, userMessage)
                onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
            }

            "txt" -> {
                //extract message
                if (line.startsWith("<")) {
                    val userName = line.split(" ".toRegex())[1].substringBeforeLast(":")
                    val time = Time(Calendar.getInstance())
                    val rawTime = line.split(" ".toRegex())[0].substringAfter("<").substringBefore(">").split(":".toRegex())
                    time.hour = rawTime[0]
                    time.minute = rawTime[1]
                    time.second = rawTime[2]

                    val userMessage = line.substringAfter("$userName: ")
                    parseLine(userName, userMessage)
                    onChatUpdateListener.onChatUpdated(ChatUpdate(userName, time, userMessage))
                }
            }

            else -> {
                println("Error! file format \"${chatFile.extension}\" not supported!")
            }
        }


    }
    override fun onPlayStateChanged(player: String, track: String){}

    override fun onNewSongPlaying(player: String, track: String){
        if (player == "mpv"){
            if (track.startsWith("https://youtube.com") || track.startsWith("https://youtu.be") || track.startsWith("https://www.youtube.com")){
                ytLink = track
            }
        }
    }

}

class ChatUpdate(val userName: String, val time: Time, val message: String)

interface ChatUpdateListener {
    fun onChatUpdated(update: ChatUpdate)
}
