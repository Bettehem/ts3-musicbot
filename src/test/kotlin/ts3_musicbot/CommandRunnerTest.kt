package ts3_musicbot

import ts3_musicbot.util.runCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRunnerTest {
    @Test
    fun testCommandRunner(){
        //try running command: echo "This is a test."
        val testCommand = "echo \"This is a test.\""
        assertEquals("This is a test.", runCommand(testCommand))
        assert(runCommand(testCommand, ignoreOutput = true).isEmpty())
    }
}