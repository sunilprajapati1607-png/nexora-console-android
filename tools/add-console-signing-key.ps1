# Nexora Console - add the console's own signing key (ONE TIME)
# =====================================================================
# 1.8.0 - the console is signed with Nexora's release key from now on.
# The key lives with Nexora Mobile's in D:\nexora-signing\nexora-release.jks,
# under an alias of its own, "nexora-console". This adds that alias and
# writes D:\nexora-signing\keystore-console.properties, which the build
# (app/build.gradle.kts) and release.ps1 read.
#
# The password is read from D:\nexora-signing\keystore.properties and passed
# to keytool through an environment variable, so it is never shown on the
# screen and never typed. The keystore is copied first and put back if
# anything goes wrong, so Nexora Mobile's key can never be damaged by this.
#
# Run it once:  double-click tools\add-console-signing-key.bat
# Afterwards back up the whole D:\nexora-signing folder again (pen drive and
# Google Drive), as its README says - it now holds the console's key too.

$ErrorActionPreference = 'Continue'   # keytool's own exit codes are checked below
$Dir     = 'D:\nexora-signing'
$Store   = Join-Path $Dir 'nexora-release.jks'
$Shared  = Join-Path $Dir 'keystore.properties'
$Out     = Join-Path $Dir 'keystore-console.properties'
$Backup  = Join-Path $Dir 'nexora-release.jks.before-console'
$Keytool = 'D:\android-tools\jdk17\bin\keytool.exe'
$Alias   = 'nexora-console'

function Say($text, $colour = 'Gray') { Write-Host $text -ForegroundColor $colour }
function Halt($text) { Say "  STOPPED: $text" 'Red'; $env:NX_SIGN_PW = $null; Read-Host '  Enter to close'; exit 1 }

Say ''
Say '  NEXORA CONSOLE - signing key (one time)' 'Cyan'
Say ''

if (Test-Path $Out)        { Say "  Already done: $Out exists. Nothing changed." 'Green'; Read-Host '  Enter to close'; exit 0 }
if (-not (Test-Path $Store))  { Halt "$Store is missing." }
if (-not (Test-Path $Shared)) { Halt "$Shared is missing." }

$line = Get-Content $Shared | Where-Object { $_ -match '^\s*storePassword\s*=' } | Select-Object -First 1
if (-not $line) { Halt "no storePassword in $Shared." }
$env:NX_SIGN_PW = ($line -replace '^\s*storePassword\s*=\s*', '').Trim()

# Already there (a run that stopped half-way)? Then only the properties file is missing.
& $Keytool -list -alias $Alias -keystore $Store -storepass:env NX_SIGN_PW *> $null
$exists = ($LASTEXITCODE -eq 0)

if (-not $exists) {
    Copy-Item $Store $Backup -Force
    # Same shape as Nexora Mobile's key: RSA 4096, SHA-384, thirty years.
    # The name CN=Nexora Console is what release.ps1 checks inside every APK.
    & $Keytool -genkeypair -alias $Alias -keyalg RSA -keysize 4096 -sigalg SHA384withRSA `
        -validity 10950 -dname 'CN=Nexora Console, OU=Nexora Android apps, O=Nexora, C=IN' `
        -storetype PKCS12 -keystore $Store -storepass:env NX_SIGN_PW -keypass:env NX_SIGN_PW
    $made = ($LASTEXITCODE -eq 0)
    & $Keytool -list -alias 'nexora-mobile' -keystore $Store -storepass:env NX_SIGN_PW *> $null
    $mobileSafe = ($LASTEXITCODE -eq 0)
    if (-not $made -or -not $mobileSafe) {
        Copy-Item $Backup $Store -Force
        Remove-Item $Backup -Force
        Halt 'keytool could not add the key. The keystore has been put back exactly as it was.'
    }
    Remove-Item $Backup -Force
}

# PKCS12: the key's password is the store's password (as in keystore.properties).
$text = @(
    '# Nexora Console release signing - read by D:\nexora-console-android\app\build.gradle.kts and release.ps1.',
    '# KEEP PRIVATE, keep with nexora-release.jks, never commit to git.',
    'storeFile=D:/nexora-signing/nexora-release.jks',
    "storePassword=$($env:NX_SIGN_PW)",
    "keyAlias=$Alias",
    '# PKCS12 keystore: the key password is the same as the store password',
    "keyPassword=$($env:NX_SIGN_PW)"
) -join "`n"
[System.IO.File]::WriteAllText($Out, $text + "`n", (New-Object System.Text.UTF8Encoding($false)))
$env:NX_SIGN_PW = $null

Say "  Done. The console's key is in $Store (alias $Alias)," 'Green'
Say "  and $Out is written." 'Green'
Say ''
Say '  NOW: copy the whole D:\nexora-signing folder to the pen drive and to Google Drive again.' 'Yellow'
Say ''
Read-Host '  Enter to close'
