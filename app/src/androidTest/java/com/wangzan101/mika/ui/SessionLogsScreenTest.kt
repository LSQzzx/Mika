package com.wangzan101.mika.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.ChineseUi
import com.wangzan101.mika.core.record.RunSummary
import com.wangzan101.mika.record.SessionLog
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionLogsScreenTest {
    @get:Rule(order = Int.MIN_VALUE) val chinese = ChineseUi()
    @get:Rule val compose = createComposeRule()
    private val sessions = listOf(
        SessionLog("first", "第一个会话", listOf(RunSummary("a", "one", startedAtMs = 1))),
        SessionLog("second", "第二个会话", listOf(RunSummary("b", "two", startedAtMs = 2))),
    )

    @Test fun exportRequiresASelectionAndUsesTheChosenConversation() {
        var selected by mutableStateOf<String?>(null)
        val exported = mutableListOf<String>()
        compose.setContent {
            MikaTheme {
                SessionLogsContent(sessions, selected, false, false, false, { selected = it }, { exported += selected!! }, {})
            }
        }
        compose.onNodeWithTag("session-logs-export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("第二个会话").performScrollTo().performClick()
        compose.onNodeWithTag("session-logs-export").performScrollTo().performClick()
        assertEquals(listOf("second"), exported)
    }

    @Test fun aRunningConversationCannotBeExported() {
        compose.setContent {
            MikaTheme { SessionLogsContent(sessions, "first", false, false, true, {}, {}, {}) }
        }
        compose.onNodeWithTag("session-logs-export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("此会话有任务正在执行，请结束后再导出。").assertExists()
    }
}
