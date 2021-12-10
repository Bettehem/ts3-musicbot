package ts3_musicbot.util

import kotlin.system.exitProcess

class Console(private val commandList: CommandList, private val consoleUpdateListener: ConsoleUpdateListener) {
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
                                "<command>\t<explanation>\n" +
                                "help\t\tShows this help message.\n" +
                                "${commandList.commandList["help"]}\t\tShows commands for controlling the actual bot.\n" +
                                "say\t\tSend a message to the chat.\n" +
                                "save-settings\tSaves current settings in to a config file.\n" +
                                "clear\t\tClears the screen.\n" +
                                "exit\t\tExits the program.\n" +
                                "quit\t\tSame as exit\n" +
                                ""
                    )
                }
                "say" -> consoleUpdateListener.onCommandIssued(commandList.commandPrefix + userCommand)
                "save-settings" -> consoleUpdateListener.onCommandIssued(command)
                "clear" -> print("\u001b[H\u001b[2J")
                "exit" -> exit(command)
                "quit" -> exit(command)
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
                commandRunner.runCommand("wmctrl -c TeamSpeak", ignoreOutput = true)
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
