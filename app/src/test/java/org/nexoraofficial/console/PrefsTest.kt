package org.nexoraofficial.console

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Sealer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1.8.0 — the admin key is kept sealed, never plain; an earlier build's plain
 * copy is moved over once and deleted.
 *
 * The computer has no Android keystore, so a stand-in seals here (reversing
 * the text and marking it). What is checked is what Prefs keeps in the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file get() = app.getSharedPreferences("nexora.console", 0)

    private val fake = object : Sealer {
        override fun seal(plain: String) = "sealed:" + plain.reversed()
        override fun open(sealed: String) = sealed.removePrefix("sealed:").reversed()
    }

    private val broken = object : Sealer {
        override fun seal(plain: String): String = throw IllegalStateException("no keystore")
        override fun open(sealed: String): String = throw IllegalStateException("no keystore")
    }

    @Before
    fun clean() {
        file.edit().clear().commit()
    }

    @Test
    fun theKeyIsKeptSealed() {
        val p = Prefs(app, fake)
        p.adminKey = "NX-ADMIN-1234"
        assertNull("no plain copy", file.getString("adminKey", null))
        assertEquals("sealed:4321-NIMDA-XN", file.getString("adminKeySealed", null))
        assertEquals("NX-ADMIN-1234", Prefs(app, fake).adminKey)
    }

    @Test
    fun anEarlierBuildsPlainKeyIsMovedOnceAndDeleted() {
        file.edit().putString("adminKey", "NX-ADMIN-OLD").commit()
        assertEquals("NX-ADMIN-OLD", Prefs(app, fake).adminKey)
        assertNull("the plain copy is gone", file.getString("adminKey", null))
        assertEquals("sealed:DLO-NIMDA-XN", file.getString("adminKeySealed", null))
        assertEquals("NX-ADMIN-OLD", Prefs(app, fake).adminKey)
    }

    @Test
    fun aKeyThatCannotBeSealedIsNotKeptAtAll() {
        file.edit().putString("adminKey", "NX-ADMIN-OLD").commit()
        val p = Prefs(app, broken)
        assertEquals("this session still opens", "NX-ADMIN-OLD", p.adminKey)
        assertNull(file.getString("adminKey", null))
        assertNull(file.getString("adminKeySealed", null))
        p.adminKey = "NX-ADMIN-NEW"
        assertNull("never written in the clear", file.getString("adminKey", null))
        assertFalse(file.contains("adminKeySealed"))
    }

    @Test
    fun aSealThatWillNotOpenReadsAsNoKey() {
        file.edit().putString("adminKeySealed", "garbage").commit()
        assertEquals("", Prefs(app, broken).adminKey)
    }

    @Test
    fun signOutErasesBoth() {
        val p = Prefs(app, fake)
        p.adminKey = "NX-ADMIN-1234"
        file.edit().putString("adminKey", "left-over").commit()
        p.signOut()
        assertFalse(file.contains("adminKey"))
        assertFalse(file.contains("adminKeySealed"))
        assertEquals("", p.adminKey)
        assertTrue("other settings survive", file.getBoolean("rememberKey", true))
    }
}
