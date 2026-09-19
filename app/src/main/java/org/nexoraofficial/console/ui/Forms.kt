package org.nexoraofficial.console.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.InquiryForm

/* ======================================================================
   FORM ENTRY, AS ITS OWN SCREEN.

   A form inside a list is a form people get lost in. These open on top of
   the section they belong to, fill the screen, and end with one obvious
   button. Back abandons; Save saves and returns.
   ====================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnquiryFormScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    editingId: Int?,
    gutter: PaddingValues,
    page: Modifier,
    wide: Boolean
) {
    val f = vm.newInquiry

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(page) {
                ConsoleCard {
                    Text("Who asked", style = CardTitleStyle)
                    Spacer(Modifier.height(12.dp))
                    TwoUp(
                        wide,
                        { m -> ConsoleField("Name", f.name, { vm.newInquiry = f.copy(name = it) }, m) },
                        { m -> ConsoleField("Company / plant", f.company, { vm.newInquiry = f.copy(company = it) }, m) }
                    )
                    Spacer(Modifier.height(12.dp))
                    TwoUp(
                        wide,
                        { m -> ConsoleField("Mobile", f.phone, { vm.newInquiry = f.copy(phone = it) }, m) },
                        { m -> ConsoleField("Email", f.email, { vm.newInquiry = f.copy(email = it) }, m) }
                    )
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("Which software", style = CardTitleStyle)
                    Small("the same list the website offers")
                    Spacer(Modifier.height(10.dp))
                    WrapRow {
                        vm.inquiryData.products.forEach { p ->
                            Chip(p, f.product == p) { vm.newInquiry = f.copy(product = p) }
                        }
                    }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("How it came", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    WrapRow {
                        vm.inquiryData.sources.forEach { s ->
                            Chip(s.lowercase(), f.source == s) { vm.newInquiry = f.copy(source = s) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Where it has got to", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    WrapRow {
                        vm.inquiryData.states.forEach { s ->
                            Chip(s.lowercase(), f.state == s) { vm.newInquiry = f.copy(state = s) }
                        }
                    }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("What they said", style = CardTitleStyle)
                    Spacer(Modifier.height(12.dp))
                    ConsoleField(
                        "What they asked",
                        f.message,
                        { vm.newInquiry = f.copy(message = it) },
                        Modifier.heightIn(min = 110.dp),
                        singleLine = false,
                        imeAction = ImeAction.Default
                    )
                    Spacer(Modifier.height(12.dp))
                    ConsoleField(
                        "Your note",
                        f.notes,
                        { vm.newInquiry = f.copy(notes = it) },
                        Modifier.heightIn(min = 90.dp),
                        singleLine = false,
                        imeAction = ImeAction.Default
                    )
                    Spacer(Modifier.height(12.dp))
                    ConsoleField(
                        "Follow up on",
                        f.followUp,
                        { vm.newInquiry = f.copy(followUp = it) },
                        placeholder = "2026-09-30",
                        supporting = "yyyy-mm-dd. Leave it blank if there is nothing to chase.",
                        imeAction = ImeAction.Done
                    )
                }
            }
        }

        item {
            Box(page) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConsoleButton(
                        if (editingId == null) "Add the enquiry" else "Save",
                        {
                            vm.saveInquiry()
                            nav.back()
                        },
                        kind = ButtonKind.Primary,
                        enabled = !vm.busy && f.name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        center = true
                    )
                    ConsoleButton(
                        "Cancel",
                        {
                            vm.newInquiry = InquiryForm()
                            vm.showNewInquiry = false
                            nav.back()
                        },
                        modifier = Modifier.weight(1f),
                        center = true
                    )
                }
            }
        }
    }
}

@Composable
fun NewCompanyScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier,
    wide: Boolean
) {
    val f = vm.newCompany

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(page) {
                ConsoleCard {
                    Text("The customer", style = CardTitleStyle)
                    Spacer(Modifier.height(12.dp))
                    ConsoleField(
                        "Company name",
                        f.name,
                        { vm.newCompany = f.copy(name = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    TwoUp(
                        wide,
                        { m -> ConsoleField("GSTIN", f.gstin, { vm.newCompany = f.copy(gstin = it.uppercase()) }, m, placeholder = "15 characters") },
                        { m -> ConsoleField("Email", f.email, { vm.newCompany = f.copy(email = it) }, m) }
                    )
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("The licence", style = CardTitleStyle)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ConsoleField(
                            "Seats", f.seats, { vm.newCompany = f.copy(seats = it) },
                            Modifier.width(110.dp), numeric = true
                        )
                        ConsoleField(
                            "Licence days", f.days, { vm.newCompany = f.copy(days = it) },
                            Modifier.width(140.dp), numeric = true
                        )
                        ConsoleField(
                            "Offline days", f.graceDays, { vm.newCompany = f.copy(graceDays = it) },
                            Modifier.width(140.dp), numeric = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Help(
                        "A seat is a person who may sign in; computers are not rationed. A licence " +
                            "key is generated, and every machine they install types the same key."
                    )
                }
            }
        }

        item {
            Box(page) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConsoleButton(
                        "Create the company",
                        {
                            vm.createCompany()
                            nav.back()
                        },
                        kind = ButtonKind.Primary,
                        enabled = !vm.busy && f.name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        center = true
                    )
                    ConsoleButton(
                        "Cancel",
                        { nav.back() },
                        modifier = Modifier.weight(1f),
                        center = true
                    )
                }
            }
        }
    }
}

/** Material's filter chip: how Android says "one of these". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** Two fields side by side on a tablet, one above the other on a phone. */
@Composable
fun TwoUp(
    wide: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            first(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            second(Modifier.fillMaxWidth())
        }
    }
}
