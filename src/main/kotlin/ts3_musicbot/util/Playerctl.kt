package ts3_musicbot.util

private fun listPlayers() = CommandRunner().runCommand("dbus-send --print-reply --dest=org.freedesktop.DBus  /org/freedesktop/DBus org.freedesktop.DBus.ListNames | grep 'org.mpris.MediaPlayer2.'", printOutput = false)
    .outputText.lines().map { it.replace("^.*string\\s+\"org\\.mpris\\.MediaPlayer2\\.".toRegex(), "").replace("\".*$".toRegex(), "") }

fun playerctl(
    player: String = listPlayers().firstOrNull().orEmpty(),
    command: String, extra: String = ""
): Output {
    val mediaPlayer = if (player.isNotEmpty()) {
        listPlayers().firstOrNull { it.startsWith(player) }.orEmpty()
            .ifEmpty { player }
    } else player

    val commandRunner = CommandRunner()
    fun dbusGet(property: String) = commandRunner.runCommand(
        "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.$mediaPlayer /org/mpris/MediaPlayer2 org.freedesktop.DBus.Properties.Get string:'org.mpris.MediaPlayer2.Player' string:'$property'",
        printOutput = false, printErrors = false
    )

    fun dbusSend(method: String, data: String = "") = commandRunner.runCommand(
        "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.$mediaPlayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.$method" +
                if (data.isNotEmpty()) {
                    " string:'$data'"
                } else {
                    ""
                },
        printOutput = false, printErrors = false
    )

    fun parseMetadata(metadata: String): Map<String, Any> {
        val metadataMap = emptyMap<String, Any>().toMutableMap()
        val subArray = ArrayList<String>()
        var inSubArray = false
        var inEntry = false
        var key = ""
        for (line in metadata.lines()) {
            if (line.startsWith("method return time")) continue
            when {
                line.contains("^\\s+dict entry\\(".toRegex()) -> {
                    inEntry = true
                }

                line.contains("^\\s+string \"".toRegex()) -> {
                    key = line.substringAfter('"').substringBeforeLast('"')
                }

                line.contains("^\\s+variant\\s+\\S+".toRegex()) -> {
                    when (val variant = line.replace("^\\s+variant\\s+".toRegex(), "").substringBefore(' ')) {
                        "array" -> if (inEntry) inSubArray = true

                        "string" -> {
                            val str = line.substringAfter('"').substringBeforeLast('"')
                            if (inSubArray)
                                subArray.add(str)
                            else
                                metadataMap[key] = str
                        }

                        "object" -> if (line.substringAfter("$variant ").startsWith("path")) {
                            metadataMap[key] = line.replace("(^.+\\s+\"|\"$)".toRegex(), "'")
                        }

                        "uint64", "int64" -> metadataMap[key] = line.substringAfter("$variant ").toLong()
                        "double" -> metadataMap[key] = line.substringAfter("$variant ").toFloat()
                        "int32" -> metadataMap[key] = line.substringAfter("$variant ").toInt()

                        else -> println(
                            "Encountered unknown variant \"$variant\" when parsing MPRIS metadata!\n" +
                                    "Metadata:\n$metadata"
                        )
                    }
                }

                line.contains("^\\s+]$".toRegex()) -> {
                    if (inEntry) {
                        inSubArray = false
                        metadataMap[key] = subArray
                        subArray.clear()
                    }
                }

                line.contains("^\\s+\\)$".toRegex()) -> inEntry = false
            }
        }
        return metadataMap
    }
    return when (command.lowercase()) {
        "status" -> {
            val cmd = dbusGet("PlaybackStatus")
            Output(cmd.outputText.substringAfter('"').substringBefore('"'), cmd.errorText)
        }

        "metadata" -> {
            val metadata = dbusGet("Metadata")
            val formattedMetadata = StringBuilder()
            val parsedOutput = parseMetadata(metadata.outputText)
            val trackId = "mpris:trackid"
            val length = "mpris:length"
            val artUrl = "mpris:artUrl"
            val album = "xesam:album"
            val albumArtist = "xesam:albumArtist"
            val artist = "xesam:artist"
            val rating = "xesam:autoRating"
            val discNum = "xesam:discNumber"
            val title = "xesam:title"
            val trackNum = "xesam:trackNumber"
            val url = "xesam:url"
            if (parsedOutput.contains(trackId))
                formattedMetadata.appendLine("$mediaPlayer $trackId\t\t${parsedOutput[trackId]}")
            if (parsedOutput.contains(length))
                formattedMetadata.appendLine("$mediaPlayer $length\t\t\t${parsedOutput[length]}")
            if (parsedOutput.contains(artUrl))
                formattedMetadata.appendLine("$mediaPlayer $artUrl\t\t\t${parsedOutput[artUrl]}")
            if (parsedOutput.contains(album))
                formattedMetadata.appendLine("$mediaPlayer $album\t\t\t${parsedOutput[album]}")
            if (parsedOutput.contains(albumArtist))
                formattedMetadata.appendLine("$mediaPlayer $albumArtist\t${
                    parsedOutput[albumArtist].let { if (it is List<*>) it.joinToString() else "" }
                }")
            if (parsedOutput.contains(artist))
                formattedMetadata.appendLine("$mediaPlayer $artist\t\t${
                    parsedOutput[artist].let { if (it is List<*>) it.joinToString() else "" }
                }")
            if (parsedOutput.contains(rating))
                formattedMetadata.appendLine("$mediaPlayer $rating\t\t${parsedOutput[rating]}")
            if (parsedOutput.contains(discNum))
                formattedMetadata.appendLine("$mediaPlayer $discNum\t\t${parsedOutput[discNum]}")
            if (parsedOutput.contains(title))
                formattedMetadata.appendLine("$mediaPlayer $title\t\t\t${parsedOutput[title]}")
            if (parsedOutput.contains(trackNum))
                formattedMetadata.appendLine("$mediaPlayer $trackNum\t${parsedOutput[trackNum]}")
            if (parsedOutput.contains(url))
                formattedMetadata.appendLine("$mediaPlayer $url\t\t\t${parsedOutput[url]}")
            Output(formattedMetadata.toString(), metadata.errorText)
        }

        "stop" -> {
            dbusSend("Stop")
        }

        "pause" -> {
            dbusSend("Pause")
        }

        "play" -> {
            dbusSend("Play")
        }

        "open" -> {
            dbusSend("OpenUri", extra)
        }

        "next" -> {
            dbusSend("Next")
        }

        "list" -> {
            Output("${listPlayers()}")
        }

        "position" -> {
            val positionData = dbusGet("Position").outputText.substringAfter("int64").trim().ifEmpty { "0" }
            Output(positionData)
        }

        else -> Output()
    }
}
