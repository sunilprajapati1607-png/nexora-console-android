package org.nexoraofficial.console.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * WHAT THE CONSOLE KNOWS, AS A SPREADSHEET.
 *
 * Two sheets: one row per customer, one row per machine. The figures are the
 * same figures the page shows — seats used, days left, transactions against
 * the limit, hours — so a number in the file and a number on the screen can
 * never disagree.
 *
 * Counts and days go in as numbers, not as text that looks like numbers, so
 * the file can be sorted, filtered and summed the moment it opens.
 */
object Reports {

    fun fileName(prefix: String = "nexora-companies"): String =
        "$prefix-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}.xlsx"

    /** Every company, and every installation, in one workbook. */
    fun workbook(data: ConsoleData): ByteArray =
        Xlsx.build(listOf(companiesSheet(data.companies), installationsSheet(data.licences)))

    /** One customer on its own: the company, its machines, its people. */
    fun companyWorkbook(company: Company, data: ConsoleData, people: List<Person>): ByteArray =
        Xlsx.build(
            listOf(
                companiesSheet(listOf(company)),
                installationsSheet(data.licences.filter { it.companyId == company.id }),
                peopleSheet(people)
            )
        )

    private fun companiesSheet(companies: List<Company>) = Xlsx.Sheet(
        name = "Companies",
        header = listOf(
            "Company", "Licence key", "State", "Registered by", "Seats used", "Seats",
            "Seats available", "Computers", "Days left", "Expires", "Offline days",
            "Transactions", "Transaction limit", "Hours in use", "Minutes in use",
            "People", "Active people", "Administrator", "People's emails",
            "GSTIN", "GST status", "Email", "Phone", "Login id", "Registered on", "Registered from"
        ),
        rows = companies.map { c ->
            listOf(
                Xlsx.text(c.name),
                Xlsx.text(c.licenceKey),
                Xlsx.text(c.shownState.lowercase()),
                Xlsx.text(if (c.selfRegistered) "the plant itself" else "the owner"),
                Xlsx.num(c.seatsUsed),
                Xlsx.num(c.seats),
                Xlsx.num((c.seats - c.seatsUsed).coerceAtLeast(0)),
                Xlsx.num(c.machinesUsed),
                Xlsx.num(c.daysLeft),
                Xlsx.text(Fmt.day(c.expiresAt)),
                Xlsx.num(c.graceDays),
                Xlsx.num(c.txnUsed),
                Xlsx.num(c.txnLimit),
                Xlsx.text(Fmt.hours(c.usageMinutes)),
                Xlsx.num(c.usageMinutes),
                Xlsx.num(if (c.usersTotal > 0) c.usersTotal else c.usersCount),
                Xlsx.num(c.usersCount),
                Xlsx.text(c.adminNames ?: "none"),
                Xlsx.text(c.userEmails),
                Xlsx.text(c.gstin),
                Xlsx.text(c.gstStatus?.lowercase()),
                Xlsx.text(c.email),
                Xlsx.text(c.phone),
                Xlsx.text(c.loginId),
                Xlsx.text(Fmt.day(c.registeredAt)),
                Xlsx.text(c.registeredIp)
            )
        }
    )

    private fun installationsSheet(licences: List<Licence>) = Xlsx.Sheet(
        name = "Installations",
        header = listOf(
            "Company", "Seat", "Of seats", "Device id", "Machine", "State", "Email",
            "Days left", "Started", "Last seen", "Version", "Transactions", "Minutes in use"
        ),
        rows = licences.map { l ->
            listOf(
                Xlsx.text(l.coName ?: l.company),
                Xlsx.num(l.seatNo),
                Xlsx.num(l.coSeats),
                Xlsx.text(l.deviceId),
                Xlsx.text(l.deviceName),
                Xlsx.text(l.shownState.lowercase()),
                Xlsx.text(l.email),
                Xlsx.num(l.daysLeft),
                Xlsx.text(Fmt.day(l.trialStartedAt)),
                Xlsx.text(Fmt.day(l.lastSeenAt)),
                Xlsx.text(l.appVersion),
                Xlsx.num(l.txnCount),
                Xlsx.num(l.usageMinutes)
            )
        }
    )

    private fun peopleSheet(people: List<Person>) = Xlsx.Sheet(
        name = "People",
        header = listOf("Name", "Role", "Sees", "Switched on", "Last signed in", "Last active"),
        rows = people.map { u ->
            listOf(
                Xlsx.text(u.name),
                Xlsx.text(if (u.isAdmin) "administrator" else "user"),
                Xlsx.text(if (u.scope == "ALL") "everyone's work" else "own work"),
                Xlsx.text(if (u.active) "yes" else "no"),
                Xlsx.text(if (u.lastLoginAt != null) Fmt.dateTime(u.lastLoginAt) else "never"),
                Xlsx.text(if (u.lastSeenAt != null) Fmt.dateTime(u.lastSeenAt) else "-")
            )
        }
    )
}

/**
 * WHO A MESSAGE GOES TO.
 *
 * 4.42.0 — two kinds of address now. The company's own, which is the one it
 * registered with, and the people's, which are on the seats. A circular
 * normally wants both: the plant's office knows about the invoice, and the
 * people who actually run the software know about the new version.
 *
 * Blanks are skipped and duplicates removed, because a plant whose office
 * address is also its administrator's should not get the same notice twice.
 */
enum class Reach(val label: String, val why: String) {
    EVERYONE("Everyone", "the company's own address and all its people"),
    PEOPLE("The people", "only those who sign in and have an address"),
    OFFICES("The offices", "only the address each company registered with");
}
enum class Audience(val label: String, val why: String) {
    ACTIVE("Everyone running", "licensed and demo, minus suspended, revoked and expired"),
    LICENSED("Paying customers", "licensed companies only"),
    DEMOS("Demos", "companies still on a demo"),
    ALL("Every company", "including suspended and expired");

    fun matches(c: Company): Boolean = when (this) {
        ALL -> true
        LICENSED -> !c.isDemo && c.state != "SUSPENDED" && !c.expired
        DEMOS -> c.isDemo && c.state != "SUSPENDED" && !c.expired
        ACTIVE -> c.state != "SUSPENDED" && c.state != "REVOKED" && !c.expired
    }
}

data class Circular(
    val addresses: List<String>,
    val reached: Int,
    val withoutEmail: List<String>
)

fun circularFor(
    companies: List<Company>,
    audience: Audience,
    reach: Reach = Reach.EVERYONE
): Circular {
    val chosen = companies.filter { audience.matches(it) }
    val seen = LinkedHashSet<String>()
    val missing = mutableListOf<String>()

    chosen.forEach { c ->
        val here = LinkedHashSet<String>()

        if (reach != Reach.PEOPLE) {
            c.email?.trim()?.takeIf { it.contains("@") }?.let { here += it.lowercase() }
        }
        if (reach != Reach.OFFICES) {
            c.userEmails.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.contains("@") }
                .forEach { here += it.lowercase() }
        }

        if (here.isEmpty()) missing += c.name else seen += here
    }

    return Circular(
        addresses = seen.toList(),
        reached = chosen.size - missing.size,
        withoutEmail = missing
    )
}
