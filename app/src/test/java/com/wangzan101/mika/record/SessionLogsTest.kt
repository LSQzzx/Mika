package com.wangzan101.mika.record

import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.agent.Turn
import com.wangzan101.mika.core.record.RunSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun groupsRetainedRunsByConversationIncludingOlderRecords() {
        val conversations = ConversationStore(tmp.newFolder("conversations"))
        val first = conversations.newConversation()
        val second = conversations.newConversation()
        conversations.append(first, Turn("first task", "done", "succeeded", runId = "run-1"))
        conversations.append(first, Turn("second task", "done", "succeeded", runId = "run-2"))
        conversations.append(first, Turn("repeat", "done", "succeeded", runId = "run-2"))
        conversations.append(first, Turn("expired", "done", "succeeded", runId = "pruned"))
        conversations.append(second, Turn("other session", "done", "succeeded", runId = "run-3"))
        val runs = listOf(RunSummary("run-3", "other", startedAtMs = 3), RunSummary("run-2", "two", startedAtMs = 2), RunSummary("run-1", "one", startedAtMs = 1))
        val sessions = sessionLogs(conversations, runs).associateBy { it.id }
        assertEquals(listOf("run-1", "run-2"), sessions.getValue(first).runs.map { it.id })
        assertEquals(listOf("run-3"), sessions.getValue(second).runs.map { it.id })
        assertEquals("first task", sessions.getValue(first).title)
        assertEquals(emptyList<SessionLog>(), sessionLogs(conversations, emptyList()))
    }
}
