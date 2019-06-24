package src.main.util

import kotlin.system.exitProcess

class Console(private val consoleUpdateListener: ConsoleUpdateListener){

    fun startConsole(){
        //start console
        val console = System.console()
        println("Enter command \"help\" for all commands.")
        while (true){
            val userCommand = console.readLine("Command: ")
            when (val command = userCommand.substringBefore(" ")){
                "help" -> {
                    println("\n\nTS3 MusicBot help:\n\n" +
                            "<command>\t<explanation>\n" +
                            "say\t\tSend a message to the chat.\n" +
                            "clear\t\tClears the screen.\n" +
                            "exit\t\tExits the program.\n" +
                            "")
                }
                "say" -> consoleUpdateListener.onCommandIssued("%$userCommand")
                "clear" -> print("\u001b[H\u001b[2J")
                "exit" -> {
                    var confirmed = false
                    while (!confirmed){
                        val exitTeamSpeak = console.readLine("Close TeamSpeak? [Y/n]: ").toLowerCase()
                        if (exitTeamSpeak.contentEquals("y") || exitTeamSpeak.contentEquals("yes") || exitTeamSpeak.contentEquals("")){
                            confirmed = true
                            runCommand("xdotool search \"ts3client_linux\" windowactivate --sync key --window 0 --clearmodifiers alt+F4", ignoreOutput = true)
                        }else if (exitTeamSpeak.contentEquals("n") || exitTeamSpeak.contentEquals("no")){
                            break
                        }
                    }

                    consoleUpdateListener.onCommandIssued(command)
                    exitProcess(0)
                }
                else -> {
                    if (command.startsWith("%") && !command.startsWith("%say"))
                        consoleUpdateListener.onCommandIssued(userCommand)
                    else
                        println("Command $command not found! Type \"help\" to see available commands.")
                }
            }
        }
    }
}

interface ConsoleUpdateListener{
    fun onCommandIssued(command: String)
}