package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * 1.8.0 — A QUESTION LEFT OPEN DOES NOT OUTLIVE THE LOCK.
 *
 * A question (Suspend, Set PIN, Delete …) is a dialog: a window of its own,
 * above the console and above the lock page drawn over it. Left open when the
 * phone was put down, it would still be there — its Yes still working — when
 * the console came back locked. AppScaffold shows no question while locked,
 * and the same question returns once the owner has unlocked.
 *
 * A made-up company; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LockHidesQuestionsTest {

    @get:Rule val rule = createComposeRule()

    private val sample = JSONObject("""{
      "settings": {"trialDays": 7, "demoGraceDays": 0, "sessionMinutes": 30, "expiredMode": "READONLY", "signupsOpen": true},
      "companies": [
        {"id": 3, "name": "Shree Demo Sacks", "licence_key": "NX-DEMO-0003", "state": "LICENSED", "seats": 5, "seats_used": 2,
         "machines_used": 3, "is_demo": false, "expires_at": "2027-06-01T18:29:59Z", "days_left": 245, "users_count": 2,
         "users_total": 2, "admin_names": "Priya", "plan": "PRO"}
      ],
      "licences": []
    }""")

    private val question = "Suspend this company?"

    private fun vm(): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        return ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            data = ConsoleData.from(sample)
        }
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun anOpenQuestionIsGoneWhileLockedAndBackAfter() {
        val vm = vm()
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()

        /* The owner opens a company and asks to suspend it … */
        tap("Companies")
        tap("Shree Demo Sacks")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Suspend"))
        tap("Suspend")
        rule.onAllNodes(isDialog()).assertCountEquals(1)
        rule.onAllNodesWithText(question).assertCountEquals(1)

        /* … puts the phone down with the question open, and it comes back
           after two minutes away: locked, and no question, no Yes, no company
           name anywhere on the screen. */
        rule.runOnIdle { vm.lock.wentAway() }
        ShadowSystemClock.advanceBy(Duration.ofMillis(AppLock.AWAY_MS))
        rule.runOnIdle { vm.lock.cameBack() }
        rule.waitForIdle()
        assertTrue("two minutes away must lock the console", vm.lock.locked)
        rule.onAllNodes(isDialog()).assertCountEquals(0)
        rule.onAllNodesWithText(question).assertCountEquals(0)

        /* Unlocked: the same question, as it was asked. */
        rule.runOnIdle { vm.lock.unlocked() }
        rule.waitForIdle()
        rule.onAllNodes(isDialog()).assertCountEquals(1)
        rule.onAllNodesWithText(question).assertCountEquals(1)
    }
}
