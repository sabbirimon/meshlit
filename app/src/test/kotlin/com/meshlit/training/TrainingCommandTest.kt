package com.meshlit.training
import org.junit.Assert.*
import org.junit.Test
class TrainingCommandTest {
    private val host=TrainingHostConfig("owned","/opt/python","/home/owner/a'b.py","/home/owner/work space")
    @Test fun quoteKeepsHostPathsAsLiteralArguments(){assertEquals("'a'\"'\"'b'",TrainingCommand.quote("a'b"));val command=TrainingCommand.build(host,"doctor");assertTrue(command.contains("'/home/owner/a'\"'\"'b.py'"));assertTrue(command.contains("'/home/owner/work space'"))}
    @Test fun cannotDispatchArbitraryCompanionActionOrTraversalId(){assertThrows(IllegalArgumentException::class.java){TrainingCommand.build(host,"worker")};assertThrows(IllegalArgumentException::class.java){TrainingCommand.build(host,"cancel","../../other")};assertThrows(IllegalArgumentException::class.java){TrainingCommand.build(host.copy(pythonPath="python"),"doctor")}}
}
