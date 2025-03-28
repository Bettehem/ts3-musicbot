package ts3musicbot.util

private fun listPlayers() =
    CommandRunner().runCommand(
        "dbus-send --print-reply --dest=org.freedesktop.DBus  /org/freedesktop/DBus " +
            "org.freedesktop.DBus.ListNames | grep 'org.mpris.MediaPlayer2.'",
        printOutput = false,
    )
        .outputText.lines().map { it.replace("^.*string\\s+\"org\\.mpris\\.MediaPlayer2\\.".toRegex(), "").replace("\".*$".toRegex(), "") }

fun playerctl(
    player: String = listPlayers().firstOrNull().orEmpty(),
    command: String,
    extra: String = "",
): Output {
    val mediaPlayer =
        if (player.isNotEmpty()) {
            listPlayers().firstOrNull { it.startsWith(player) }.orEmpty()
                .ifEmpty { player }
        } else {
            player
        }

    val commandRunner = CommandRunner()

    fun dbusGet(property: String) =
        commandRunner.runCommand(
            "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.$mediaPlayer /org/mpris/MediaPlayer2 " +
                "org.freedesktop.DBus.Properties.Get string:'org.mpris.MediaPlayer2.Player' string:'$property'",
            printOutput = false,
            printErrors = false,
        )

    /**
     * Runs a dbus-send command to the desired player with the specified method.
     * @param method specify a method to use. For example Stop, Play, Open
     * @param data any extra data to accompany the given method. Has to be properly formatted according to the MPRIS Specification.
     * For example, if your data is a string e.g. "hello", you need to prepend it accordingly with the string: tag, so the string you pass
     * in to data would be "string:'hello'".
     */
    fun dbusSend(
        method: String,
        data: String = "",
    ) = commandRunner.runCommand(
        "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.$mediaPlayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.$method" +
            if (data.isNotEmpty()) {
                " $data"
            } else {
                ""
            },
        printOutput = false,
        printErrors = false,
    )

    fun parseMetadata(metadata: String): Map<String, Any> {
        val metadataMap = emptyMap<String, Any>().toMutableMap()
        val subArray = ArrayList<String>()
        var inSubArray = false
        var inEntry = false
        var key = ""
        for (line in metadata.lines()) {
            if (line.startsWith("method return time")) continue
            if (line.contains("^\\s+dict entry\\(".toRegex())) {
                inEntry = true
                continue
            }
            if (line.contains("^\\s+string \"".toRegex()) && !inSubArray) {
                key = line.substringAfter('"').substringBeforeLast('"')
                continue
            }
            if (line.contains("^\\s+(variant\\s+)?\\S+\\s+\\S+".toRegex())) {
                when (val variant = line.replace("^\\s+(variant\\s+)?".toRegex(), "").substringBefore(' ')) {
                    "array" -> if (inEntry) inSubArray = true

                    "string" -> {
                        val str = line.substringAfter('"').substringBeforeLast('"')
                        if (inSubArray) {
                            subArray.add(str)
                        } else {
                            metadataMap[key] = str
                        }
                    }

                    "object" ->
                        if (line.substringAfter("$variant ").startsWith("path")) {
                            metadataMap[key] = line.replace("(^.+\\s+\"|\"$)".toRegex(), "'")
                        }

                    "uint64", "int64" -> metadataMap[key] = line.substringAfter("$variant ").toLong()
                    "double" -> metadataMap[key] = line.substringAfter("$variant ").toFloat()
                    "int32" -> metadataMap[key] = line.substringAfter("$variant ").toInt()

                    else ->
                        println(
                            "Encountered unknown variant \"$variant\" when parsing MPRIS metadata!\n" +
                                "Metadata:\n$metadata",
                        )
                }
                continue
            }
            if (line.contains("^\\s+]$".toRegex()) && inEntry) {
                inSubArray = false
                metadataMap[key] = subArray.toArray()
                subArray.clear()
                continue
            }
            if (line.contains("^\\s+\\)$".toRegex())) inEntry = false
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

            /**
             * Formats a line of given metadata
             * @param tag The tag to be used
             * @param tabs The amount of tabs that should be in between the tag and data
             */
            fun fmtMetadata(
                tag: String,
                tabs: Int = 2,
            ) {
                fun fmt(data: Any?) = formattedMetadata.appendLine("$mediaPlayer $tag" + "\t".repeat(tabs) + data)
                if (parsedOutput.contains(tag)) {
                    val data = parsedOutput[tag]
                    if (data is Array<*>) {
                        data.forEach { fmt(it) }
                    } else {
                        fmt(data)
                    }
                }
            }
            fmtMetadata(trackId)
            fmtMetadata(length)
            fmtMetadata(artUrl)
            fmtMetadata(album)
            fmtMetadata(albumArtist, 1)
            fmtMetadata(artist)
            fmtMetadata(rating)
            fmtMetadata(discNum)
            fmtMetadata(title)
            fmtMetadata(trackNum, 1)
            fmtMetadata(url)
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
            dbusSend("OpenUri", "string:'$extra'")
        }

        "next" -> {
            dbusSend("Next")
        }

        "list" -> {
            Output("${listPlayers()}".replace("(^\\[|\\]$)".toRegex(), ""))
        }

        "position" -> {
            if (extra.isEmpty()) {
                val positionData = dbusGet("Position").outputText.substringAfter("int64").trim().ifEmpty { "0" }
                Output(positionData)
            } else {
                val trackId =
                    playerctl(player, "metadata").outputText.lines().first { it.contains("mpris:trackid") }
                        .replace("\\S+\\s+mpris:trackid\\s+".toRegex(), "")
                dbusSend("SetPosition", "objpath:$trackId int64:" + extra.toLong() * 1000000)
            }
        }

        else -> Output()
    }
}
