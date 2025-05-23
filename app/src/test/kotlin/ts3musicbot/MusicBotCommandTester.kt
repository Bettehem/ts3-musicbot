package ts3musicbot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ts3musicbot.chat.ChatReader
import ts3musicbot.chat.ChatUpdate
import ts3musicbot.chat.ChatUpdateListener
import ts3musicbot.chat.CommandListener
import ts3musicbot.client.Client
import ts3musicbot.services.SoundCloud
import ts3musicbot.services.Spotify
import ts3musicbot.services.YouTube
import ts3musicbot.util.BotSettings
import ts3musicbot.util.CommandList
import ts3musicbot.util.Link
import ts3musicbot.util.Track
import ts3musicbot.util.TrackList
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicBotCommandTester : ChatUpdateListener, CommandListener {
    private val commandList = CommandList()
    private val spotifyMarket = "FI"
    private val userName = "__console__"
    private val spotifyLink = Link("https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB")
    private val spotifyAlbumLink = Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF")
    private val spotifyPlaylistLink = Link("https://open.spotify.com/user/bettehem/playlist/1V28nRUSl217OYNiXBJXHE")
    private val youTubeLink = Link("https://youtu.be/IKZnGWxJN3I")
    private val youTubePlaylistLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
    private val soundCloudLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
    private val soundCloudPlaylistLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")

    // TODO: figure out why trying to fetch the data for this track results in error 429 when running the tests in ci but not locally
    // private val bandcampLink = Link("https://visceraandvapor.bandcamp.com/track/side-b-3")
    private val ytKey = "gCBlkehNVeC9lRwpEVZZVT1FlMJ9FR4FWakhVVkdje0EVLTNWT2ZTW"
    private val botSettings =
        BotSettings(
            market = spotifyMarket,
            ytApiKey = String(Base64.getDecoder().decode("${"=".repeat(2)}$ytKey".reversed().trim())).reversed().trim(),
            spApiKey =
                String(
                    Base64.getEncoder().encode(
                        (
                            "KIDO0ETZkZjY0UTY4QGM4gjY2MDNkdTN2EWZzADNkBDZ" + ":" +
                                "KUGMwUWYyADZhdTYmRmMjFWOwgDN1IjMwUWN1M2YyQTZ"
                        ).split(":").reversed().joinToString(":") {
                            String(Base64.getDecoder().decode(it.reversed())).trim()
                        }.toByteArray(),
                    ),
                ),
        )
    private val chatReader = ChatReader(Client(botSettings), botSettings, this, this, commandList)
    private var commandCompleted = Pair("", false)

    private fun runCommand(
        chatReader: ChatReader,
        command: String,
        username: String = userName,
        commandListener: CommandListener = chatReader.commandListener,
    ) {
        chatReader.commandListener = commandListener
        chatReader.latestMsgUsername = username
        chatReader.parseLine(command)
    }

    @Test
    fun testHelpCommand() {
        runBlocking(IO) {
            commandCompleted = Pair("", false)
            val chatReader =
                ChatReader(
                    Client(botSettings),
                    BotSettings(),
                    this@MusicBotCommandTester,
                    object : CommandListener {
                        override fun onCommandExecuted(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {
                            if (command.substringAfter("%help").isNotEmpty()) {
                                assertEquals(commandList.helpMessages[command.substringAfter(" ")], output)
                            } else {
                                assertEquals(commandList.helpMessages["help"], output)
                            }
                            commandCompleted = Pair(command, true)
                        }

                        override fun onCommandProgress(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {}
                    },
                    commandList,
                )
            val helpUser = "test"
            runCommand(chatReader, "%help", helpUser)
            while (commandCompleted.first != "help" && !commandCompleted.second) {
                print("Waiting for command to complete\r")
                delay(10)
            }
            println()
            commandList.commandList.forEach {
                commandCompleted = Pair("", false)
                runCommand(chatReader, "%help ${it.key}", helpUser)
                while (commandCompleted.first != "%help ${it.key}" && !commandCompleted.second) {
                    print("Waiting for command to complete\r")
                    delay(10)
                }
                println()
            }
        }
    }

    @Test
    fun testAddingTrackToQueue() {
        runBlocking(IO) {
            val links = listOf(spotifyLink, youTubeLink, soundCloudLink)
            lateinit var track: Track
            for (link in links) {
                commandCompleted = Pair("", false)
                runCommand(
                    chatReader,
                    "%queue-clear; %queue-add $link",
                    commandListener =
                        object : CommandListener {
                            override fun onCommandExecuted(
                                command: String,
                                output: String,
                                extra: Any?,
                            ) {
                                if (command.startsWith("%queue-add")) {
                                    if (extra is Track) {
                                        track = extra
                                        commandCompleted = Pair(command.substringBefore(" "), true)
                                    }
                                }
                            }

                            override fun onCommandProgress(
                                command: String,
                                output: String,
                                extra: Any?,
                            ) {}
                        },
                )
                while (commandCompleted.first != "%queue-add" && !commandCompleted.second) {
                    print("Waiting for command to complete.\r")
                    delay(500)
                }
                println()
                assertEquals(link.link, track.link.link)
            }
        }
    }

    @Test
    fun testAddingSpAlbumToQueue() {
        runBlocking(IO) {
            lateinit var list: TrackList
            lateinit var extraList: TrackList
            commandCompleted = Pair("", false)
            runCommand(
                chatReader,
                "%queue-clear; %queue-add $spotifyAlbumLink",
                commandListener =
                    object : CommandListener {
                        override fun onCommandExecuted(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {
                            if (command == "%queue-add $spotifyAlbumLink") {
                                runCommand(
                                    chatReader,
                                    "%queue-list $spotifyAlbumLink",
                                    commandListener =
                                        object : CommandListener {
                                            override fun onCommandExecuted(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {
                                                if (extra is TrackList) {
                                                    extraList = extra
                                                }
                                                commandCompleted = Pair(command, true)
                                            }

                                            override fun onCommandProgress(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {}
                                        },
                                )
                            }
                        }

                        override fun onCommandProgress(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {}
                    },
            )
            val spotify = Spotify(botSettings)
            spotify.updateToken()
            list = TrackList(spotify.fetchAlbumTracks(spotifyAlbumLink).trackList.filter { it.playability.isPlayable })
            while (commandCompleted.first != "%queue-list $spotifyAlbumLink" && !commandCompleted.second) {
                print("Waiting for command to complete.\r")
                Thread.sleep(500)
            }
            println()
            assertEquals(list.toString(), extraList.toString())
        }
    }

    @Test
    fun testAddingSpPlaylistToQueue() {
        runBlocking(IO) {
            lateinit var list: List<Track>
            lateinit var extraList: TrackList
            commandCompleted = Pair("", false)
            runCommand(
                chatReader,
                "%queue-clear; %queue-add $spotifyPlaylistLink",
                commandListener =
                    object : CommandListener {
                        override fun onCommandExecuted(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {
                            if (command == "%queue-add $spotifyPlaylistLink") {
                                runCommand(
                                    chatReader,
                                    "%queue-list $spotifyPlaylistLink",
                                    commandListener =
                                        object : CommandListener {
                                            override fun onCommandExecuted(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {
                                                if (extra is TrackList) {
                                                    extraList = extra
                                                }
                                                commandCompleted = Pair(command, true)
                                            }

                                            override fun onCommandProgress(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {}
                                        },
                                )
                            }
                        }

                        override fun onCommandProgress(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {}
                    },
            )
            val spotify = Spotify(botSettings)
            spotify.updateToken()
            list = spotify.fetchPlaylistTracks(spotifyPlaylistLink).trackList.filter { it.playability.isPlayable }
            while (commandCompleted.first != "%queue-list $spotifyPlaylistLink" && !commandCompleted.second) {
                print("Waiting fo command to complete\r")
                Thread.sleep(500)
            }
            println()
            assertEquals(list, extraList.trackList)
        }
    }

    @Test
    fun testAddingYtPlaylistToQueue() {
        runBlocking(IO) {
            lateinit var list: List<Track>
            lateinit var extraList: TrackList
            commandCompleted = Pair("", false)
            runCommand(
                chatReader,
                "%queue-clear; %queue-add $youTubePlaylistLink",
                commandListener =
                    object : CommandListener {
                        override fun onCommandExecuted(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {
                            if (command == "%queue-add $youTubePlaylistLink") {
                                runCommand(
                                    chatReader,
                                    "%queue-list $youTubePlaylistLink",
                                    commandListener =
                                        object : CommandListener {
                                            override fun onCommandExecuted(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {
                                                if (extra is TrackList) {
                                                    extraList = extra
                                                }
                                                commandCompleted = Pair(command, true)
                                            }

                                            override fun onCommandProgress(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {}
                                        },
                                )
                            }
                        }

                        override fun onCommandProgress(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {}
                    },
            )
            val youTube = YouTube(botSettings.ytApiKey)
            list = youTube.fetchPlaylistTracks(youTubePlaylistLink).trackList.filter { it.playability.isPlayable }
            while (commandCompleted.first != "%queue-list $youTubePlaylistLink" && !commandCompleted.second) {
                print("Waiting for command to complete.\r")
                Thread.sleep(500)
            }
            println()
            assertEquals(list, extraList.trackList)
        }
    }

    @Test
    fun testAddingScPlaylistToQueue() {
        runBlocking(IO) {
            lateinit var list: List<Track>
            lateinit var extraList: TrackList
            commandCompleted = Pair("", false)
            runCommand(
                chatReader,
                "%queue-clear; %queue-add $soundCloudPlaylistLink",
                commandListener =
                    object : CommandListener {
                        override fun onCommandExecuted(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {
                            if (command == "%queue-add $soundCloudPlaylistLink") {
                                runCommand(
                                    chatReader,
                                    "%queue-list $soundCloudPlaylistLink",
                                    commandListener =
                                        object : CommandListener {
                                            override fun onCommandExecuted(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {
                                                if (extra is TrackList) {
                                                    extraList = extra
                                                }
                                                commandCompleted = Pair(command, true)
                                            }

                                            override fun onCommandProgress(
                                                command: String,
                                                output: String,
                                                extra: Any?,
                                            ) {}
                                        },
                                )
                            }
                        }

                        override fun onCommandProgress(
                            command: String,
                            output: String,
                            extra: Any?,
                        ) {}
                    },
            )
            val soundCloud = SoundCloud()
            list = soundCloud.fetchPlaylistTracks(soundCloudPlaylistLink).trackList.filter { it.playability.isPlayable }
            while (commandCompleted.first != "%queue-list $soundCloudPlaylistLink" && !commandCompleted.second) {
                print("Waiting for command to complete.\r")
                Thread.sleep(500)
            }
            println()
            assertEquals(list, extraList.trackList)
        }
    }

    override fun onChatUpdated(update: ChatUpdate) {}

    override fun onCommandExecuted(
        command: String,
        output: String,
        extra: Any?,
    ) {}

    override fun onCommandProgress(
        command: String,
        output: String,
        extra: Any?,
    ) {}
}
