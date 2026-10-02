package org.nexoraofficial.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.0 — the app lock's clock: locked on opening, open after the prompt,
 * locked again only after two minutes away.
 */
class AppLockTest {

    private var clock = 1_000_000L
    private fun lock() = AppLock { clock }

    @Test
    fun startsLocked() {
        val l = lock()
        assertTrue(l.locked)
        assertTrue(l.startAsking())
        assertFalse("a second prompt must not stack on the first", l.startAsking())
        l.unlocked()
        assertFalse(l.locked)
        assertFalse("nothing to ask once open", l.startAsking())
    }

    @Test
    fun aShortTripAwayStaysOpen() {
        val l = lock()
        l.unlocked()
        l.wentAway()
        clock += AppLock.AWAY_MS - 1
        l.cameBack()
        assertFalse(l.locked)
    }

    @Test
    fun twoMinutesAwayLocksAgain() {
        val l = lock()
        l.unlocked()
        val before = l.closings
        l.wentAway()
        clock += AppLock.AWAY_MS
        l.cameBack()
        assertTrue(l.locked)
        assertEquals("the prompt comes up by itself once more", before + 1, l.closings)
    }

    @Test
    fun theTimeOnThePromptItselfDoesNotCount() {
        /* While locked, leaving for the phone's PIN screen and coming back
           is not "away": nothing is recorded, so nothing re-locks after. */
        val l = lock()
        l.startAsking()
        l.wentAway()
        clock += 10 * AppLock.AWAY_MS
        l.cameBack()
        l.unlocked()
        l.cameBack()
        assertFalse(l.locked)
    }

    @Test
    fun aCancelSaysNothingAndWaits() {
        val l = lock()
        val before = l.closings
        l.startAsking()
        l.failed(null)
        assertTrue(l.locked)
        assertFalse(l.asking)
        assertEquals(null, l.problem)
        assertEquals("a Cancel must not bring the prompt straight back", before, l.closings)
        l.failed("Too many attempts")
        assertEquals("Too many attempts", l.problem)
    }
}
