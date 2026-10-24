package com.meshlit.core.mcp.control
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class TaskBoardTest {
    private class Store:TaskBoardStore{var saved=listOf<ManagedTask>();var fail=false
        override suspend fun load()=saved
        override suspend fun save(tasks:List<ManagedTask>){if(fail) error("disk unavailable");saved=tasks}}
    @Test fun bulkRevisionAndRestart()=runTest{
        val store=Store();val board=TaskBoard(store,this)
        val a=board.create(TaskMutation(title="Download model"));val b=board.create(TaskMutation(title="Pair phone",priority=TaskPriority.HIGH))
        board.batch(listOf(a.id,b.id),TaskPhase.DONE)
        assertTrue(board.tasks.value.all{it.phase==TaskPhase.DONE && it.revision==2L})
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{board.update(TaskMutation(id=a.id,expectedRevision=1,title="stale"))}}
        assertEquals(2,TaskBoard(store,this).also{it.ready.await()}.tasks.value.size)
    }
    @Test fun failedBatchAndStorageAreAtomic()=runTest{
        val store=Store();val board=TaskBoard(store,this);val a=board.create(TaskMutation(title="Task"))
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{board.batch(listOf(a.id,"missing"),TaskPhase.CANCELLED)}}
        assertEquals(TaskPhase.OPEN,board.tasks.value.single().phase)
        store.fail=true
        assertThrows(IllegalStateException::class.java){kotlinx.coroutines.runBlocking{board.update(TaskMutation(id=a.id,expectedRevision=1,phase=TaskPhase.DONE))}}
        assertEquals(TaskPhase.OPEN,board.tasks.value.single().phase)
    }
    @Test fun parentsAndValidation()=runTest{
        val board=TaskBoard(Store(),this);val parent=board.create(TaskMutation(title="Plan"));board.create(TaskMutation(title="Subtask",parentId=parent.id))
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{board.delete(parent.id,1)}}
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{board.create(TaskMutation(title=" "))}}
    }
}
