package ts3_musicbot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.client.TeamSpeak
import kotlin.system.exitProcess

class Console(
    private val commandList: CommandList,
    private val consoleUpdateListener: ConsoleUpdateListener,
    private val teamSpeak: Any
) {
    private val commandRunner = CommandRunner()

    fun startConsole() {
        //start console
        val console = System.console()
        println("Enter command \"help\" for all commands.")
        loop@ while (true) {
            val userCommand = console.readLine("Command: ")
            when (val command = userCommand.substringBefore(" ")) {
                "help" -> {
                    println(
                        "\n\nTS3 MusicBot help:\n\n" +
                                "<command>\t\t\t\t<explanation>\n" +
                                "help\t\t\t\t\tShows this help message.\n" +
                                "${commandList.commandList["help"]}\t\t\t\t\tShows commands for controlling the actual bot.\n" +
                                "say\t\t\t\t\tSend a message to the chat.\n" +
                                "save-settings\t\t\t\tSaves current settings in to a config file.\n" +
                                "clear\t\t\t\t\tClears the screen.\n" +
                                "exit\t\t\t\t\tExits the program.\n" +
                                "quit\t\t\t\t\tSame as exit\n" +
                                "join-channel, jc <channel> <password>\tJoin a channel\n" +
                                ""
                    )
                }
                "say" -> {
                    when (teamSpeak) {
                        is TeamSpeak -> teamSpeak.sendMsgToChannel(userCommand.replace("^say\\s+".toRegex(), ""))
                        is OfficialTSClient -> teamSpeak.sendMsgToChannel(userCommand.replace("^say\\s+".toRegex(), ""))
                    }
                }
                "save-settings" -> consoleUpdateListener.onCommandIssued(command)
                "clear" -> print("\u001b[H\u001b[2J")
                "exit" -> exit(command)
                "quit" -> exit(command)
                "join-channel", "jc" -> {
                    when (teamSpeak) {
                        is TeamSpeak -> {
                            teamSpeak.joinChannel(
                                userCommand.replace("(^\\S+\\s+|\\s+\\S+$)".toRegex(), ""),
                                if (userCommand.contains("^\\S+\\s+\\S+\\s+\\S+$".toRegex()))
                                    userCommand.replace("^\\S+\\s+\\S+\\s+".toRegex(), "")
                                else ""
                            )
                        }
                        is OfficialTSClient -> {
                            teamSpeak.joinChannel(
                                userCommand.replace("(^\\S+\\s+|\\s+\\S+$)".toRegex(), ""),
                                if (userCommand.contains("^\\S+\\s+\\S+\\s+\\S+$".toRegex()))
                                    userCommand.replace("^\\S+\\s+\\S+\\s+".toRegex(), "")
                                else ""
                            )
                        }
                    }
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
                when (teamSpeak) {
                    is TeamSpeak -> CoroutineScope(IO).launch {
                        launch {
                            teamSpeak.disconnect()
                        }
                        delay(1000)
                    }
                    is OfficialTSClient -> commandRunner.runCommand(
                        "wmctrl -c TeamSpeak; sleep 2; wmctrl -c TeamSpeak; sleep 2; pkill -9 ts3client_linux",
                        ignoreOutput = true
                    )
                }
            } else if (exitTeamSpeak.contentEquals("n") || exitTeamSpeak.contentEquals("no")) {
                break
            }
        }

        commandRunner.runCommand("killall mpv", ignoreOutput = true)
        commandRunner.runCommand("killall ncspot", ignoreOutput = true)
        commandRunner.runCommand("pkill -9 spotify", ignoreOutput = true)
        commandRunner.runCommand("tmux kill-session -t ncspot", ignoreOutput = true)
        commandRunner.runCommand("playerctl -p spotifyd stop", ignoreOutput = true)
        consoleUpdateListener.onCommandIssued(command)
        exitProcess(0)
    }
}

interface ConsoleUpdateListener {
    fun onCommandIssued(command: String)
}
