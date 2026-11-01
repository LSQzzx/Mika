package com.wangzan101.mika.ui

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.record.RunStore
import com.wangzan101.mika.record.SessionLog
import com.wangzan101.mika.record.sessionLogs
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.RadioRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MikaApp
    val store = remember { RunStore(context) }
    val scope = rememberCoroutineScope()
    val active by app.taskState.activeConversations.collectAsState()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val loaded by produceState<Result<List<SessionLog>>?>(null) {
        app.conversations.changes.onStart { emit("") }.collect {
            value = withContext(Dispatchers.IO) { runCatching { sessionLogs(app.conversations, store.list()) } }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val session = pending
        pending = null
        if (uri != null && session != null) scope.launch {
            exporting = true
            try {
                withContext(Dispatchers.IO) {
                    check(session !in app.taskState.activeConversations.value) { "Session is running" }
                    val logs = sessionLogs(app.conversations, store.list()).first { it.id == session }
                    val output = checkNotNull(context.contentResolver.openOutputStream(uri, "w"))
                    output.use { store.export(logs.runs.map { it.id }, it) }
                }
                Toast.makeText(context, R.string.logs_exported, Toast.LENGTH_LONG).show()
            } catch (failure: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                if (failure is CancellationException) throw failure
                Toast.makeText(context, R.string.logs_export_failed, Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
            }
        }
    }
    SessionLogsContent(
        sessions = loaded?.getOrNull(), selected = selected, busy = exporting || pending != null,
        failed = loaded?.isFailure == true, running = selected in active,
        onSelect = { selected = it }, onExport = {
            selected?.let { id ->
                pending = id
                launcher.launch("Mika-session-$id.zip")
            }
        }, onBack = onBack,
    )
}

@Composable
fun SessionLogsContent(
    sessions: List<SessionLog>?, selected: String?, busy: Boolean, failed: Boolean, running: Boolean,
    onSelect: (String) -> Unit, onExport: () -> Unit, onBack: () -> Unit,
) {
    PageScaffold(stringResource(R.string.logs_title), onBack) {
        FooterNote(stringResource(R.string.logs_content))
        when {
            failed -> EmptyNote(stringResource(R.string.logs_read_failed))
            sessions == null -> EmptyNote(stringResource(R.string.loading))
            sessions.isEmpty() -> EmptyNote(stringResource(R.string.logs_empty))
            else -> {
                GroupCard {
                    sessions.forEachIndexed { index, session ->
                        RadioRow(
                            session.title, session.id == selected, { onSelect(session.id) },
                            subtitle = stringResource(
                                R.string.logs_session_detail, session.runs.size,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(session.runs.last().startedAtMs)),
                                session.id,
                            ), first = index == 0, enabled = !busy,
                        )
                    }
                }
                GroupCard {
                    Column(Modifier.padding(16.dp)) {
                        MikaButton(
                            stringResource(if (busy) R.string.logs_exporting else R.string.logs_export), onExport,
                            Modifier.testTag("session-logs-export"),
                            enabled = !busy && !running && sessions.any { it.id == selected },
                        )
                    }
                }
                if (running) FooterNote(stringResource(R.string.logs_running))
            }
        }
        FooterNote(stringResource(R.string.logs_privacy))
        FooterNote(stringResource(R.string.logs_retention))
    }
}
