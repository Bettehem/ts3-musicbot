package ts3_musicbot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ts3_musicbot.client.Client
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.client.TeamSpeak
import kotlin.system.exitProcess

class Console(
    private val commandList: CommandList,
    private val consoleUpdateListener: ConsoleUpdateListener,
    private val client: Client
) {
    private val commandRunner = CommandRunner()

    fun startConsole() {
        //start console
        val console = System.console()
        println("Enter command \"help\" for all commands.")
        loop@ while (true) {
            val userCommand = console.readLine("Command: ")
            when (val command = userCommand.replace("\\s+.*$".toRegex(), "")) {
                "help" -> println(
                    "\n\nTS3 MusicBot help:\n\n" +
                            "<command>\t\t\t\t<explanation>\n" +
                            "help\t\t\t\t\tShows this help message.\n" +
                            "${commandList.commandList["help"]}\t\t\t\t\tShows commands for controlling the actual bot.\n" +
                            "say\t\t\t\t\tSend a message to the chat.\n" +
                            "save-settings\t\t\t\tSaves current settings in to a config file.\n" +
                            "clear\t\t\t\t\tClears the screen.\n" +
                            "exit\t\t\t\t\tExits the program.\n" +
                            "quit\t\t\t\t\tSame as exit.\n" +
                            "join-channel, jc <channel> -p <password>\tJoin a channel.\n" +
                            "restart <ts/teamspeak/ncspot>\t\tRestarts the teamspeak/ncspot client.\n" +
                            "playerctl -p <player> <args>\t\t\tRun playerctl-like commands.\n"
                )

                "say" -> client.sendMsgToChannel(userCommand.replace("^\\s+".toRegex(), ""))
                "save-settings" -> consoleUpdateListener.onCommandIssued(command)
                "clear" -> print("\u001b[H\u001b[2J")
                "exit" -> exit(command)
                "quit" -> exit(command)
                "join-channel", "jc" -> {
                    val channelName = userCommand.replace("(^\\S+\\s+|\\s+(-p).*$)".toRegex(), "")
                    val channelPassword =
                        if (userCommand.contains("^\\S+\\s+.*\\s+-p\\s+.*\\S+$".toRegex()))
                            userCommand.replace("^\\S+\\s+.*\\s+-p\\s+".toRegex(), "")
                        else ""
                    CoroutineScope(IO).launch {
                        client.joinChannel(channelName, channelPassword)
                    }
                }

                "restart" -> when (userCommand.replace("$command\\s+".toRegex(), "").replace("\\s+.*$", "").lowercase()) {
                    "ts", "teamspeak" -> CoroutineScope(IO).launch {
                        when (client) {
                            is OfficialTSClient -> launch { client.restartClient() }
                            is TeamSpeak -> launch { client.reconnect() }
                        }
                    }

                    "ncspot" -> CoroutineScope(IO).launch {
                        playerctl("ncspot", "stop")
                        commandRunner.runCommand(
                            "tmux kill-session -t ncspot",
                            ignoreOutput = true
                        )
                        delay(100)
                        commandRunner.runCommand(
                            "tmux new -s ncspot -n player -d; tmux send-keys -t ncspot \"ncspot\" Enter",
                            ignoreOutput = true,
                            printCommand = true
                        )
                    }

                    else -> println("Specify either ts,teamspeak or ncspot!")
                }

                "playerctl" -> {
                    val player = if (userCommand.contains("^\\w+\\s+-p\\s*\\S+\\s+.+".toRegex())) {
                        userCommand.replace("^\\w+\\s+-p\\s*".toRegex(), "")
                            .replace("\\s+.+$".toRegex(), "")
                    } else ""
                    val cmd = userCommand.replace("^\\w+\\s+(-p\\s*\\S+\\s+)?".toRegex(), "")
                    println(
                        if (player.isNotEmpty()) {
                            playerctl(player, cmd)
                        } else {
                            playerctl(command = cmd)
                        }
                    )
                }

                "" -> continue@loop
                else -> {
                    if (command.startsWith(commandList.commandPrefix) && !command.startsWith("${commandList.commandPrefix}say"))
                        consoleUpdateListener.onCommandIssued(userCommand)
                    else if (command.contains("^\\\\?!.+".toRegex())) {
                        commandRunner.runCommand(userCommand.substringAfter("!"), inheritIO = true)
                    } else
                        println("Command $command not found! Type \"help\" to see available commands.")
                }
            }
        }
    }

    private fun exit(command: String) {
        val console = System.console()
        var confirmed = false
        while (!confirmed) {
            val exitTeamSpeak = console.readLine("Close TeamSpeak? [Y/n]: ").lowercase()
            if (exitTeamSpeak.contentEquals("y") || exitTeamSpeak.contentEquals("yes") || exitTeamSpeak.contentEquals("")) {
                confirmed = true
                CoroutineScope(IO).launch {
                    when (client) {
                        is TeamSpeak -> launch { client.disconnect() }
                        is OfficialTSClient -> launch { client.stopTeamSpeak() }
                    }
                    delay(1000)
                }
            } else if (exitTeamSpeak.contentEquals("n") || exitTeamSpeak.contentEquals("no")) {
                break
            }
        }

        playerctl("ncspot", "stop")
        playerctl("spotifyd", "stop")
        commandRunner.runCommand("pkill mpv", ignoreOutput = true)
        commandRunner.runCommand("pkill ncspot", ignoreOutput = true)
        commandRunner.runCommand("pkill -9 spotify", ignoreOutput = true)
        commandRunner.runCommand("tmux kill-session -t ncspot", ignoreOutput = true)
        commandRunner.runCommand("pkill -9 ts3client_linux", ignoreOutput = true)
        consoleUpdateListener.onCommandIssued(command)
        exitProcess(0)
    }
}

interface ConsoleUpdateListener {
    fun onCommandIssued(command: String)
}
