package ts3_musicbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ts3_musicbot.chat.ChatReader
import ts3_musicbot.chat.ChatUpdate
import ts3_musicbot.chat.ChatUpdateListener
import ts3_musicbot.chat.CommandListener
import ts3_musicbot.services.Spotify
import ts3_musicbot.util.CommandList
import ts3_musicbot.util.Link
import ts3_musicbot.util.Track
import ts3_musicbot.util.TrackList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicBotCommandTester : ChatUpdateListener {
    private val spotifyMarket = "FI"
    private val userName = "__console__"
    private val spotifyLink = Link("https://open.spotify.com/track/19gtYiBXEhSyTCOe1GyKDB")
    private val spotifyPlaylistLink = Link("https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt")
    private val youTubeLink = Link("https://youtu.be/IKZnGWxJN3I")
    private val soundCloudLink = Link("https://soundcloud.com/iamleeya/something-worth-dreaming-of")

    private fun runCommand(chatReader: ChatReader, command: String, username: String = userName) =
        chatReader.parseLine(username, command)

    @Test
    fun testHelpCommand() {
        runBlocking(Default) {
            val chatReader = ChatReader(File(""), this@MusicBotCommandTester, object : CommandListener {
                override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                    if (command.substringAfter("%help").isNotEmpty()) {
                        assertEquals(CommandList.helpMessages.getValue(command.substringAfter("%help ")), output)
                    } else {
                        assertEquals(CommandList.helpMessages.getValue("%help"), output)
                    }
                }
            }, "", spotifyMarket, "", "", "")
            val helpUser = "test"
            runCommand(chatReader, "%help", helpUser)
            delay(5)
            CommandList.commandList.forEach {
                runCommand(chatReader, "%help $it", helpUser)
                delay(5)
            }
        }
    }

    @Test
    fun testAddingToQueue() {
        runBlocking(Default) {
            val spotify = Spotify(spotifyMarket).also { runBlocking(Dispatchers.IO) { it.updateToken() } }
            val chatReader = ChatReader(File(""), this@MusicBotCommandTester, object : CommandListener {
                override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                    when (extra){
                        is Track -> {
                            assert(listOf(spotifyLink, youTubeLink, soundCloudLink).contains(extra.link))
                        }

                        is TrackList -> {
                            runBlocking {
                                val list = spotify.getPlaylistTracks(spotifyPlaylistLink).trackList.filter { it.playability.isPlayable }
                                assert(list.containsAll(extra.trackList))
                            }
                        }
                    }
                }
            }, "", spotifyMarket, "", "", "")
            runCommand(chatReader, "%queue-add $spotifyLink,$youTubeLink,$soundCloudLink")
            for (i in 1..10)
                runCommand(chatReader, "%queue-add $spotifyPlaylistLink")
            runCommand(chatReader, "%queue-list --all")
        }
    }


    override fun onChatUpdated(update: ChatUpdate) {
        TODO("Not yet implemented")
    }
}