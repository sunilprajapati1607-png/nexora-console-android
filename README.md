# Nexora Licence Console — Android

The licence console, as a native Android application. Not a web view around
the existing page: every screen is drawn in Kotlin with Jetpack Compose.

It does **not** copy the web console's layout. The console is a web page and
looks like one; this is an Android application and is built out of Android's
own parts — Material 3 components, a bottom navigation bar, a top app bar, a
floating + where things are added, Material dialogs, chips, switches and
text fields. What travels from the console is the brand: the same #4F7CFF,
the same N, the same green for good news and red for bad.

**Five sections along the bottom**, each with its own screen:

```
Dashboard      the figures, and what to do about them
Enquiries      every lead, searchable, filterable, one tap to move it on
Feedback       what the plants say from inside Nexora — feedback and problem
               reports with the picture of their screen; one tap to call back
Companies      the customers; tap one to open everything about it
More           the machines (every installation), circulars, Excel export,
               service settings, about, sign out
```

Forms open as screens of their own — New enquiry, Edit enquiry, New company —
with one obvious Save button. Back abandons, Save returns.

It talks to the same service the desktop application does
(`https://nexora-api-55jv.onrender.com`) over the same five endpoints, with the
same admin key.

## What it does

Everything the web console does, in the same order and the same words:

| | |
|---|---|
| **Gate** | admin key, and — new on a phone — the service address and whether the key is kept on the device |
| **Top bar** | brand, company/installation count, the four figure tiles, the light/dark switch, Refresh, Service settings, Sign out |
| **Service settings** | demo length, offline days, working window, what happens when a licence ends, registrations, anonymous demos |
| **Companies** | search, New company, and a card per customer with seats, clock, offline days, transactions, hours and people |
| **Manage** | Licence (make licensed, add days, +1 year) · Machines (seats, offline days, show installations) · People (set administrator, new company passcode, the full list with role, PIN and removal) · GST (verify online, mark by hand) · Usage (transaction limit, reset) · Stop (suspend/restore, delete) |
| **Installations** | every machine with its state, clock, version, transactions and hours, and Reset usage / Revoke / Delete |

And four things the web console does not have:

| | |
|---|---|
| **Dashboard** | customers, demos, machines running, and then the enquiries: how many are new, open, won, due a call today; how far each lead has got; and which software people actually ask about, as bars you can tap to filter |
| **Enquiries** | every lead in one table. The website's contact and demo forms post straight in; the ones that arrive by phone are typed in with New enquiry. Search, filter by state, one-tap state changes, and Call / WhatsApp / Email straight from the row |
| **Export Excel** | a real `.xlsx` — Companies and Installations, one sheet each — written without any spreadsheet library, saved wherever the phone's own save-as puts it |
| **Tell the customers** | one message to every company that should hear it (a new version, a new product). The app sends nothing itself: it fills your own mail app with every address in BCC and you press Send |
| **Notifications** | every fifteen minutes the phone asks whether a new enquiry has arrived or a plant has registered, and says so in the status bar under Nexora's own mark |

### What the service needs

The enquiries live on the service, so `D:\nexora-api-git` must be deployed for
that part of the app to do anything: a new `inquiries` table, `GET
/admin/api/inquiries`, `POST /admin/api/inquiry`, and the public `POST
/enquiry` the website posts to. Until it is deployed the app opens perfectly
well and simply shows no enquiries — the call fails quietly on purpose.

`prompt()` and `confirm()` have no equivalent on a phone, so they are rebuilt
as dialogs in the console's own surface — with the same warnings, including
typing the company name to confirm a delete.

## Building it

It builds. The toolchain lives outside the project in `D:\android-tools`
(JDK 17, the Android SDK, Gradle 8.7) — nothing was installed system-wide, and
deleting that one folder removes all of it.

From a shell:

```
set JAVA_HOME=D:\android-tools\jdk17
set ANDROID_HOME=D:\android-tools\sdk
cd D:\nexora-console-android
D:\android-tools\gradle-8.7\bin\gradle.bat assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it to
the phone and install it (allow installing from unknown sources once).

Android Studio works too: **Open** `D:\nexora-console-android` and let it sync —
it will write the `gradlew` wrapper files that are not in this folder, and point
itself at the SDK named in `local.properties`.

For a release APK, add a signing config in `app/build.gradle.kts` and run
`gradle assembleRelease` (or **Build → Generate Signed Bundle / APK**). The debug
APK is signed with the throwaway debug key, which is fine for installing on your
own phones but cannot be published.

Versions pinned: AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00,
compileSdk 34, minSdk 26 (Android 8.0).

## Where things are

```
app/src/main/java/org/nexoraofficial/console/
  MainActivity.kt          the window, the theme, the gate-or-console decision
  ConsoleViewModel.kt      every action, every message, what is open and filtered
  data/
    Api.kt                 the five endpoints, HttpURLConnection and org.json
    Models.kt              company, licence, settings, person, and the formatters
    Prefs.kt               the key, the service address, the chosen mode
  ui/
    GateScreen.kt          the admin key
    ConsoleScreen.kt       the page: top bar, settings, companies, legend, installations
    CompanyCard.kt         .co — the customer card and its manage panel
    InstallationsCard.kt   the console's table, read down instead of across
    SettingsCard.kt        service settings
    Components.kt          card, pill, figure tile, fact, bar, button, field, mode switch
    Dialogs.kt             what prompt() and confirm() become
    Layout.kt              the accent edge, and buttons that wrap
    theme/                 the console's tokens, light and dark
```

## Notes

- **The admin key** is kept in the app's private preferences, excluded from
  cloud backup and device transfer, and erased by Sign out. Turn off *Keep the
  key on this phone* at the gate to make it last only as long as the app is
  open, which is what the browser's `sessionStorage` did.
- **The service address** is a build field (`API_BASE`) and can also be changed
  at the gate under *Service…* — useful for pointing at a staging copy.
- **Timeouts are long on purpose** (30 s connect, 60 s read). The service sleeps
  on its free tier and the first call of the morning can take half a minute.
- **The table became rows.** A ten-column table cannot be read on a phone, so
  each installation is a row of its own with the same ten facts stacked. That is
  the only place where the layout departs from the web console; the colours,
  type, spacing, wording and behaviour are the same everywhere.
