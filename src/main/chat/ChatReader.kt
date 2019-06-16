package src.main.chat

import src.main.util.Time
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class ChatReader(var chatFile: File, var onChatUpdateListener: ChatUpdateListener, val apikey: String = "") {

    private var chatListenerThread: Thread
    private var shouldRead = false
    private var ytLink = ""
    private var ytResults = emptyList<String>()

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

                    Thread.sleep(1000)
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

                "%help" -> {
                    val lines = ArrayList<String>()
                    lines.add("Spotify commands:")
                    lines.add("%sp-pause                   -Pauses the Spotify playback")
                    lines.add("%sp-resume                  -Resumes the Spotify playback")
                    lines.add("%sp-play                    -Resumes the Spotify playback")
                    lines.add("%sp-skip                    -Skips the currently playing track")
                    lines.add("%sp-next                    -Skips the currently playing track")
                    lines.add("%sp-prev                    -Plays the previous track")
                    lines.add("%sp-playsong <track>        -Plays a Spotify song. <track> should be your song link or Spotify URI")
                    lines.add("%sp-playlist <playlist>     -Plays a Spotify playlist. <playlist> should be your playlist's Spotify URI. Links aren't currently supported")
                    lines.add("%sp-nowplaying              -Shows information on currently playing track")

                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }
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

                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }
                }

                "%sp-pause" -> Runtime.getRuntime().exec("sp pause && sleep 1")
                "%sp-resume" -> Runtime.getRuntime().exec("sp play && sleep 1")
                "%sp-play" -> Runtime.getRuntime().exec("sp play && sleep 1")

                "%sp-skip" -> Runtime.getRuntime().exec("sp next && sleep 1")
                "%sp-next" -> Runtime.getRuntime().exec("sp next && sleep 1")
                "%sp-prev" -> {
                    Runtime.getRuntime().exec("sp prev").waitFor(100, TimeUnit.MILLISECONDS)
                    Runtime.getRuntime().exec("sp prev").waitFor(100, TimeUnit.MILLISECONDS)
                }

                //Play Spotify song based on link or URI
                "%sp-playsong" -> {
                    if (parseLink(message).startsWith("https://open.spotify.com/track")) {
                        Runtime.getRuntime().exec("sp open spotify:track:${parseLink(message).split("track/".toRegex())[1].split("\\?si=".toRegex())[0]}")
                    } else {
                        Runtime.getRuntime().exec("sp open spotify:track:${message.split(" ".toRegex())[1].split("track:".toRegex())[1]}")
                    }
                }

                //Play Spotify playlist based on URI
                "%sp-playlist" -> {
                    if (message.split(" ".toRegex())[1].startsWith("spotify:user:") && message.split(" ".toRegex())[1].contains(":playlist:"))
                        Runtime.getRuntime().exec("sp open spotify:user:${message.split(" ".toRegex())[1].split(":".toRegex())[2]}:playlist:${message.split(":".toRegex()).last()}")
                }

                "%sp-nowplaying" -> {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "sp current > /tmp/sp-current && sleep 0.5")).waitFor()
                    val lines = ArrayList<String>()
                    lines.add("Now playing on Spotify:")
                    for (line in Files.readAllLines(File("/tmp/sp-current").toPath().toAbsolutePath(), StandardCharsets.UTF_8)) lines.add(line)
                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }

                }


                "%yt-pause" -> Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo \"cycle pause\" | socat - /tmp/mpvsocket"))
                "%yt-resume" -> Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo \"cycle pause\" | socat - /tmp/mpvsocket"))
                "%yt-play" -> Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo \"cycle pause\" | socat - /tmp/mpvsocket"))
                "%yt-stop" -> Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo \"stop\" | socat - /tmp/mpvsocket"))


                "%yt-playsong" -> {
                    //val link = message.split(" ".toRegex())[2].split("href=\"".toRegex())[1].split("\">".toRegex())[0]
                    ytLink = parseLink(message)
                    //Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl -o - \"$link\" | mpv --no-video --input-ipc-server=/tmp/mpvsocket - &"))
                    if (ytLink.isNotEmpty())
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", "mpv --no-video --input-ipc-server=/tmp/mpvsocket --ytdl $ytLink &"))
                }


                "%yt-nowplaying" -> {
                    File("/tmp/yt-nowplaying_cmd").printWriter().use { out ->
                        out.println("#!/bin/sh")
                        out.println("youtube-dl --geo-bypass -s -e \"$ytLink\" > /tmp/yt-current")
                    }
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod +x /tmp/yt-nowplaying_cmd"))
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "bash /tmp/yt-nowplaying_cmd")).waitFor()
                    Thread.sleep(500)
                    val lines = ArrayList<String>()
                    lines.add("Now playing on YouTube:")
                    for (np in Files.readAllLines(File("/tmp/yt-current").toPath().toAbsolutePath(), StandardCharsets.UTF_8)) lines.add(np)
                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }
                }

                "%yt-search" -> {
                    var lines = ArrayList<String>()
                    lines.add("Searching, please wait...")
                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }

                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "youtube-dl --geo-bypass -s -e \"ytsearch10:${message.replace("\"", "\\\"").replace("'", "\'")}\" > /tmp/yt-search")).waitFor()
                    Thread.sleep(500)
                    lines = ArrayList<String>()
                    lines.add("YouTube Search Results:")

                    ytResults = Files.readAllLines(File("/tmp/yt-search").toPath().toAbsolutePath(), StandardCharsets.UTF_8)
                    for (i in ytResults.indices) {
                        lines.add("${i + 1}: ${ytResults[i]}")
                    }
                    lines.add("Use command \"%yt-sel\" followed by the search result number to play the result, for example:")
                    lines.add("%yt-sel 4")

                    if (userName == "__console__"){
                        lines.forEach { println(it) }
                    }else{
                        if (apikey.isNotEmpty()){
                            val stringBuffer = StringBuffer()
                            lines.forEach { stringBuffer.append(it+"\n") }
                            printToChat(stringBuffer.toString(), apikey)
                        }else{
                            printToChat(lines, true)
                        }
                    }
                }

                "%yt-sel" -> {
                    val selection = ytResults[message.split(" ".toRegex())[1].toInt() - 1]
                    File("/tmp/yt-sel_cmd").printWriter().use { out ->
                        out.println("#!/bin/sh")
                        out.println("youtube-dl -s --get-id \"ytsearch1:${selection.replace("'", "\'").replace("\"", "\\\"")}\" > /tmp/yt-sel")
                    }
                    Thread.sleep(1000)
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod +x /tmp/yt-sel_cmd"))
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "bash /tmp/yt-sel_cmd")).waitFor()
                    Thread.sleep(250)
                    val link = "https://youtu.be/${Files.readAllLines(File("/tmp/yt-sel").toPath().toAbsolutePath()).last()}"
                    ytLink = link
                    parseLine("", "%yt-playsong $link")
                }

                else -> {
                    if (userName == "__console__"){
                        when (message.split(" ".toRegex())[0]){
                            "%say" -> {
                                val lines = ArrayList<String>()
                                lines.addAll(message.substringAfterLast("%say ").split("\n"))
                                if (apikey.isNotEmpty()){
                                    val stringBuffer = StringBuffer()
                                    lines.forEach { stringBuffer.append(it+"\n") }
                                    printToChat(stringBuffer.toString(), apikey)
                                }else{
                                    printToChat(lines, true)
                                }
                            }
                        }
                    }else{
                        val lines = ArrayList<String>()
                        lines.add("Command not found! Try %help to see available commands.")
                        if (userName == "__console__"){
                            lines.forEach { println(it) }
                        }else{
                            if (apikey.isNotEmpty()){
                                val stringBuffer = StringBuffer()
                                lines.forEach { stringBuffer.append(it+"\n") }
                                printToChat(stringBuffer.toString(), apikey)
                            }else{
                                printToChat(lines, true)
                            }
                        }
                    }
                }

            }
        }
    }

    //focus window and use xdotool to type message
    private fun printToChat(message: List<String>, focusWindow: Boolean) {
        if (focusWindow) {
            //Runtime.getRuntime().exec(arrayOf("sh", "-c", "sleep 1 && xdotool windowraise \$(xdotool search --classname \"ts3client_linux_amd64\" | tail -n1)"))
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool windowactivate --sync \$(xdotool search --classname \"ts3client_linux_amd64\" | tail -n1) && sleep 0.5 && xdotool key ctrl+Return && sleep 0.5"))
        }
        for (line in message) {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool type --delay 25 \"${line.replace("\"", "\\\"")}\" && sleep 0.25 && xdotool key ctrl+Return")).waitFor()
        }
        Runtime.getRuntime().exec(arrayOf("sh", "-c", "xdotool sleep 0.5 key \"Return\""))
    }

    //use ClientQuery to send message (requires apikey)
    private fun printToChat(message: String, apikey: String){
        if (apikey.isNotEmpty()){
            File("/tmp/print_to_chat_cmd").printWriter().use { out ->
                out.println("#!/bin/sh")
                out.println("(echo auth apikey=$apikey; echo \"sendtextmessage targetmode=2 msg=${message.replace(" ", "\\s").replace("\n", "\\n").replace("/", "\\/").replace("|", "\\p").replace("'", "\\'").replace("\"", "\\\"")}\"; echo quit) | nc localhost 25639")
            }
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod +x /tmp/print_to_chat_cmd"))
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "bash /tmp/print_to_chat_cmd"))
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
}

class ChatUpdate(val userName: String, val time: Time, val message: String)

interface ChatUpdateListener {
    fun onChatUpdated(update: ChatUpdate)
}
