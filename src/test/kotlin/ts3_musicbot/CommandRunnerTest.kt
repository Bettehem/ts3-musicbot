package ts3_musicbot

import ts3_musicbot.util.CommandRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRunnerTest {
    private val commandRunner = CommandRunner()
    @Test
    fun testCommandRunner(){
        //try running command: echo "This is a test."
        val testCommand = "echo \"This is a test.\""
        assertEquals("This is a test.", commandRunner.runCommand(testCommand).outputText)
        assert(commandRunner.runCommand(testCommand, ignoreOutput = true).outputText.isEmpty())
    }
}

