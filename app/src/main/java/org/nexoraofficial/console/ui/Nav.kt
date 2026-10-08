package org.nexoraofficial.console.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import org.nexoraofficial.console.data.Software

/**
 * WHERE YOU ARE IN THE APPLICATION.
 *
 * Places along the bottom and in the side menu, the way an Android
 * application is laid out, and everything else opens on top of one of them
 * and comes back with Back.
 *
 * Hand-rolled rather than navigation-compose: the graph is a few lists and
 * the windows they open, with no deep links and no arguments beyond an id,
 * and a list with push and pop says that in a few lines instead of a
 * dependency, a NavHost and a string route for every one of them.
 *
 * 2.0.0 — "console ne software jevu banavanu che row type details click and
 * open window" (owner, 2026-10-08): the lists are Records screens and a row
 * opens its record as a window (Customer, Payment, Plan) on top of them.
 */
sealed interface Screen {

    /** The places the bottom bar and the side menu switch between. */
    sealed interface Root : Screen {
        val title: String
        val icon: ImageVector
    }

    data object Dashboard : Root {
        override val title = "Dashboard"
        override val icon = Icons.Outlined.Insights
    }

    /** 2.0.0 — every customer once, with every software it uses (was Companies). */
    data object Customers : Root {
        override val title = "Customers"
        override val icon = Icons.Outlined.Business
    }

    /** 2.0.0 — every licence of every software, the one ending first at the top. */
    data object Validity : Root {
        override val title = "Validity"
        override val icon = Icons.Outlined.Event
    }

    /** 2.0.0 — what each customer paid, and the validity it bought. */
    data object Payments : Root {
        override val title = "Payments"
        override val icon = Icons.Outlined.Payments
    }

    /** 2.0.0 — every software's own plans. */
    data object Plans : Root {
        override val title = "Plans"
        override val icon = Icons.Outlined.WorkspacePremium
    }

    /** 2.0.0 — one software on its own: its customers, its plans, its payments. */
    data class BySoftware(val sw: String) : Root {
        override val title: String get() = Software.name(sw)
        override val icon: ImageVector get() = if (sw == Software.FABRIC) Icons.Outlined.Inventory2 else Icons.Outlined.Scale
    }

    data object Enquiries : Root {
        override val title = "Enquiries"
        override val icon = Icons.Outlined.QuestionAnswer
    }

    /** 1.4.0 — what the plants say from inside the application. */
    data object Feedback : Root {
        override val title = "Feedback"
        override val icon = Icons.Outlined.Feedback
    }

    data object Machines : Root {
        override val title = "Machines"
        override val icon = Icons.Outlined.Computer
    }

    data object More : Root {
        override val title = "More"
        override val icon = Icons.Outlined.MoreHoriz
    }

    /* ---- what opens on top of them ---- */

    /**
     * 2.0.0 — one customer's window: [key] "w12" (a Sales & Costing company, with its Fabric Stock) or
     * "f7" (Fabric Stock alone); opened on [sw]'s tab and its [sub] tab (licence, features, people,
     * computers, payments, more / company) when given.
     */
    data class CustomerWin(val key: String, val sw: String? = null, val sub: String? = null) : Screen

    /** 2.0.0 — one payment: DISPLAY, Edit, Delete. */
    data class PaymentWin(val id: Int) : Screen

    /** 2.0.0 — Record payment (the form is the view model's payForm). */
    data object RecordPayment : Screen

    /** 2.0.0 — one plan of one software; [code] null = a new plan, made from [copyFrom]'s ticks when given (Duplicate). */
    data class PlanWin(val sw: String, val code: String?, val copyFrom: String? = null) : Screen

    /** New when id is null, editing otherwise. */
    data class EnquiryForm(val id: Int?) : Screen

    /** One report, with its picture and what can be done about it. */
    data class FeedbackDetail(val id: Int) : Screen

    /** 2.0.0 — one customer, any software, each its own licence (was New company). */
    data object NewCustomer : Screen
    data object Announce : Screen
    data object Settings : Screen
    data object Broadcast : Screen
    data object About : Screen
}

/* 2.0.0 — the four used most along the bottom; everything else (Validity, each software on its own,
   Enquiries, Feedback, the machines, settings) in the side menu, whose ☰ wears a dot while an enquiry or a
   report is new. */
val ROOTS = listOf(Screen.Dashboard, Screen.Customers, Screen.Payments, Screen.Plans)

/**
 * The back stack. The bottom of it is always a root, so Back can never empty
 * it and leave a blank window; pressing Back on a root is what closes the
 * application, which is what the system expects.
 */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Dashboard)

    val current: Screen get() = stack.last()
    val root: Screen.Root get() = stack.first() as Screen.Root
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * 2.0.0 — what Back asks first, while a window is in Edit ("Close without saving the changes?").
     * Set by the window, cleared when it leaves Edit.
     */
    var guard by mutableStateOf<(() -> Unit)?>(null)

    /** Tapping a bottom-bar item: go to that section, and drop what was on top. */
    fun switchTo(dest: Screen.Root) {
        guard = null
        stack.clear()
        stack.add(dest)
    }

    fun open(screen: Screen) {
        stack.add(screen)
    }

    /** 2.0.0 — the window on top becomes another (Previous / Next, or a customer whose key changed). */
    fun replace(screen: Screen) {
        if (stack.size > 1) stack[stack.lastIndex] = screen else stack.add(screen)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        guard = null
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Back from the bar's arrow or the phone's: asks the window first when it is in Edit. */
    fun requestBack() {
        val g = guard
        if (g != null) g() else back()
    }

    /** After deleting the thing a screen was showing, there is nothing to go back to. */
    fun backToRoot() {
        guard = null
        while (canGoBack) stack.removeAt(stack.lastIndex)
    }
}
