package org.nexoraofficial.console.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * WHERE YOU ARE IN THE APPLICATION.
 *
 * Five places along the bottom, the way an Android application is laid out,
 * and everything else opens on top of one of them and comes back with Back.
 *
 * Hand-rolled rather than navigation-compose: the whole graph is nine
 * screens with no deep links and no arguments beyond an id, and a list with
 * push and pop says that in thirty lines instead of a dependency, a NavHost
 * and a string route for every one of them.
 */
sealed interface Screen {

    /** The five the bottom bar switches between. */
    sealed interface Root : Screen {
        val title: String
        val icon: ImageVector
    }

    data object Dashboard : Root {
        override val title = "Dashboard"
        override val icon = Icons.Outlined.Insights
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

    data object Companies : Root {
        override val title = "Companies"
        override val icon = Icons.Outlined.Business
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

    /** One customer, with everything that can be done to them. */
    data class Company(val id: Int) : Screen

    /** New when id is null, editing otherwise. */
    data class EnquiryForm(val id: Int?) : Screen

    /** One report, with its picture and what can be done about it. */
    data class FeedbackDetail(val id: Int) : Screen

    data object NewCompany : Screen
    data object Announce : Screen
    data object Settings : Screen
    /* 1.5.0 — the plans, and a message into every room */
    data object Plans : Screen
    data object Broadcast : Screen
    data object About : Screen
}

/* 1.4.0 — Feedback took the fourth place. Machines is still a Root (it
   keeps its title and icon) but is reached from the Dashboard and More:
   a report from a plant is looked at every day, the list of every
   installation is not. */
val ROOTS = listOf(Screen.Dashboard, Screen.Enquiries, Screen.Feedback, Screen.Companies, Screen.More)

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

    /** Tapping a bottom-bar item: go to that section, and drop what was on top. */
    fun switchTo(dest: Screen.Root) {
        stack.clear()
        stack.add(dest)
    }

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** After deleting the thing a screen was showing, there is nothing to go back to. */
    fun backToRoot() {
        while (canGoBack) stack.removeAt(stack.lastIndex)
    }
}
