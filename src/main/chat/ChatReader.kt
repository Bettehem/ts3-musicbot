package main.chat

import main.util.Time
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

class ChatReader(chatFile: File, var onChatUpdateListener: ChatUpdateListener) {

    private var chatListenerThread: Thread
    private var shouldRead = false

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

                    Thread.sleep(500)
                }

            }.run()
        }

    }

    fun startReading() {
        shouldRead = true
        chatListenerThread.start()
    }

    fun stopReading() {
        shouldRead = false
        chatListenerThread.interrupt()
    }

    fun parseLine(userName: String, message: String) {
        //check if message is a command
        if (message.startsWith("%") && message.length > 1) {
            when (message.split(" ".toRegex())[0]) {
                "%sp-pause" -> Runtime.getRuntime().exec("sp pause")
                "%sp-resume" -> Runtime.getRuntime().exec("sp play")
                "%sp-play" -> Runtime.getRuntime().exec("sp play")

                "%sp-skip" -> Runtime.getRuntime().exec("sp next")
                "%sp-next" -> Runtime.getRuntime().exec("sp next")

                //Play Spotify song based on link or URI
                "%sp-playsong" -> {
                    if (message.split(" ".toRegex())[2].startsWith("href=\"https://open.spotify.com/track")) {
                        Runtime.getRuntime().exec("sp open spotify:track:${message.split(" ".toRegex())[2].split("track/".toRegex())[1].split("\\?si=".toRegex())[0]}")
                    } else {
                        Runtime.getRuntime().exec("sp open spotify:track:${message.split(" ".toRegex())[1].split("track:".toRegex())[1]}")
                    }
                }

                //Play Spotify playlist based on URI
                "%sp-playlist" -> {
                    if (message.split(" ".toRegex())[1].startsWith("spotify:user:") && message.split(" ".toRegex())[1].contains(":playlist:"))
                        Runtime.getRuntime().exec("sp open spotify:user:${message.split(" ".toRegex())[1].split(":".toRegex())[2]}:playlist:${message.split(":".toRegex()).last()}")
                }




                "%yt-pause" -> Runtime.getRuntime().exec("vlc --key-play-pause Pause")
                "%yt-resume" -> Runtime.getRuntime().exec("vlc --key-play-pause Play")
                "%yt-play" -> Runtime.getRuntime().exec("vlc --key-play-pause Play")


                "%yt-playsong" -> Runtime.getRuntime().exec("youtube-dl -o - \"${message.split(" ".toRegex())[2].split("href=\"".toRegex())[1].split("\">".toRegex())[0]}\" | mplayer -")

            }
        }
    }

    private fun chatUpdated(line: String) {
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
}

class ChatUpdate(val userName: String, val time: Time, val message: String)

interface ChatUpdateListener {
        fun onChatUpdated(update: ChatUpdate)
    }
