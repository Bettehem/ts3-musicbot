package ts3_musicbot

import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ts3_musicbot.chat.ChatReader
import ts3_musicbot.chat.ChatUpdate
import ts3_musicbot.chat.ChatUpdateListener
import ts3_musicbot.chat.CommandListener
import ts3_musicbot.services.SoundCloud
import ts3_musicbot.services.Spotify
import ts3_musicbot.services.YouTube
import ts3_musicbot.util.CommandList
import ts3_musicbot.util.Link
import ts3_musicbot.util.Track
import ts3_musicbot.util.TrackList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicBotCommandTester : ChatUpdateListener, CommandListener {
    private val commandList = CommandList()
    private val spotifyMarket = "FI"
    private val userName = "__console__"
    private val spotifyLink = Link("https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB")
    private val spotifyAlbumLink =
        Link("https://open.spotify.com/album/1syoohGc0fQAoJWy57XZUF?si=ATPp0MnORemSb2BPgar5EA")
    private val spotifyPlaylistLink = Link("https://open.spotify.com/user/bettehem/playlist/1V28nRUSl217OYNiXBJXHE")
    private val youTubeLink = Link("https://youtu.be/IKZnGWxJN3I")
    private val youTubePlaylistLink = Link("https://www.youtube.com/playlist?list=PLVzaRVhV8Ebb5m6IIEpOJeOIBMKk4AVwm")
    private val soundCloudLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")
    private val soundCloudPlaylistLink = Link("https://soundcloud.com/bettehem/sets/jeesjees")
    private val chatReader = ChatReader("", File(""), this, this, "", spotifyMarket, "", "", "", 60, commandList)

    private fun runCommand(
        chatReader: ChatReader, command: String, username: String = userName,
        commandListener: CommandListener = chatReader.commandListener
    ) {
        chatReader.commandListener = commandListener
        chatReader.latestMsgUsername = username
        chatReader.parseLine(command)
    }

    @Test
    fun testHelpCommand() {
        runBlocking(Default) {
            val chatReader = ChatReader("", File(""), this@MusicBotCommandTester, object : CommandListener {
                override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                    if (command.substringAfter("%help").isNotEmpty()) {
                        assertEquals(commandList.helpMessages[command.substringAfter(" ")], output)
                    } else {
                        assertEquals(commandList.helpMessages["%help"], output)
                    }
                }
            }, "", spotifyMarket, "", "", "", 60, commandList)
            val helpUser = "test"
            runCommand(chatReader, "%help", helpUser)
            delay(5)
            commandList.commandList.forEach {
                runCommand(chatReader, "%help ${it.key}", helpUser)
                delay(5)
            }
        }
    }

    @Test
    fun testAddingTrackToQueue() {
        runCommand(
            chatReader, "%queue-add $spotifyLink,$youTubeLink,$soundCloudLink",
            commandListener = object : CommandListener {
                override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                    if (extra is Track)
                        assert(listOf(spotifyLink, youTubeLink, soundCloudLink).contains(extra.link))

                }
            }).also { Thread.sleep(5000) }
        runCommand(chatReader, "%queue-clear")
        chatReader.commandListener = this
    }

    @Test
    fun testAddingSpAlbumToQueue() {
        lateinit var list: TrackList
        lateinit var extraList: TrackList
        runCommand(chatReader, "%queue-add $spotifyAlbumLink", commandListener = object : CommandListener {
            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                if (command == "%queue-add $spotifyAlbumLink")
                    runCommand(
                        chatReader, "%queue-list $spotifyAlbumLink",
                        commandListener = object : CommandListener {
                            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                                if (extra is TrackList) {
                                    extraList = extra
                                }
                            }
                        })
            }
        }).also { Thread.sleep(5000) }
        runBlocking(Default) {
            val spotify =
                Spotify(spotifyMarket).also { runBlocking(IO) { it.updateToken() } }
            list =
                TrackList(spotify.getAlbumTracks(spotifyAlbumLink).trackList.filter { it.playability.isPlayable })
        }
        assertEquals(list.toString(), extraList.toString())
        runCommand(chatReader, "%queue-clear")
        chatReader.commandListener = this
    }

    @Test
    fun testAddingSpPlaylistToQueue() {
        lateinit var list: List<Track>
        lateinit var extraList: TrackList
        runCommand(chatReader, "%queue-add $spotifyPlaylistLink", commandListener = object : CommandListener {
            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                if (command == "%queue-add $spotifyPlaylistLink")
                    runCommand(
                        chatReader, "%queue-list $spotifyPlaylistLink",
                        commandListener = object : CommandListener {
                            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                                if (extra is TrackList) {
                                    extraList = extra
                                }
                            }
                        })
            }
        }).also { Thread.sleep(5000) }
        runBlocking(Default) {
            val spotify =
                Spotify(spotifyMarket).also { runBlocking(IO) { it.updateToken() } }
            list =
                spotify.getPlaylistTracks(spotifyPlaylistLink).trackList.filter { it.playability.isPlayable }
        }
        assertEquals(list, extraList.trackList)
        runCommand(chatReader, "%queue-clear")
        chatReader.commandListener = this
    }

    @Test
    fun testAddingYtPlaylistToQueue() {
        lateinit var list: List<Track>
        lateinit var extraList: TrackList
        runCommand(chatReader, "%queue-add $youTubePlaylistLink", commandListener = object : CommandListener {
            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                if (command == "%queue-add $youTubePlaylistLink")
                    runCommand(
                        chatReader, "%queue-list $youTubePlaylistLink",
                        commandListener = object : CommandListener {
                            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                                if (extra is TrackList) {
                                    extraList = extra
                                }
                            }
                        })
            }
        }).also { Thread.sleep(5000) }
        runBlocking(Default) {
            val youTube = YouTube()
            list = youTube.getPlaylistTracks(youTubePlaylistLink).trackList.filter { it.playability.isPlayable }
        }
        assertEquals(list, extraList.trackList)
        runCommand(chatReader, "%queue-clear")
        chatReader.commandListener = this
    }

    @Test
    fun testAddingScPlaylistToQueue() {
        lateinit var list: List<Track>
        lateinit var extraList: TrackList
        runCommand(chatReader, "%queue-add $soundCloudPlaylistLink", commandListener = object : CommandListener {
            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                if (command == "%queue-add $soundCloudPlaylistLink")
                    runCommand(
                        chatReader, "%queue-list $soundCloudPlaylistLink",
                        commandListener = object : CommandListener {
                            override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                                if (extra is TrackList) {
                                    extraList = extra
                                }
                            }
                        })
            }
        }).also { Thread.sleep(5000) }
        runBlocking(Default) {
            val soundCloud = SoundCloud()
            list = soundCloud.getPlaylistTracks(soundCloudPlaylistLink).trackList.filter { it.playability.isPlayable }
        }
        assertEquals(list, extraList.trackList)
        runCommand(chatReader, "%queue-clear")
        chatReader.commandListener = this
    }

    override fun onChatUpdated(update: ChatUpdate) {}
    override fun onCommandExecuted(command: String, output: String, extra: Any?) {}
}

