package com.wangzan101.mika.record

import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.record.RunSummary

data class SessionLog(val id: String, val title: String, val runs: List<RunSummary>)

/** 对话里的运行编号仍是唯一关联；旧版本保存的记录也能按会话导出。 */
fun sessionLogs(conversations: ConversationStore, runs: List<RunSummary>): List<SessionLog> {
    val byId = runs.associateBy { it.id }
    return conversations.list().mapNotNull { conversation ->
        val kept = conversations.turns(conversation.id).mapNotNull { byId[it.runId] }.distinctBy { it.id }
            .sortedBy { it.startedAtMs }
        if (kept.isEmpty()) null else SessionLog(conversation.id, conversation.title, kept)
    }
}
