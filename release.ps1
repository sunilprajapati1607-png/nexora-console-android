# Nexora Console — build a release and publish it
# =====================================================================
# Double-click release.bat and answer two questions. This then:
#
#   1. bumps the version in app/build.gradle.kts
#   2. builds the RELEASE APK, signed with Nexora's own key (1.8.0 on;
#      every build before was the debug build)
#   3. copies it to releases/nexora-console-<name>-<code>.apk
#   4. writes releases/latest.json — the file the service reads
#   5. commits, and pushes if you say so
#
# Within about five minutes of the push, every phone running the console
# is offered the new build.

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

$env:JAVA_HOME    = 'D:\android-tools\jdk17'
$env:ANDROID_HOME = 'D:\android-tools\sdk'
$Gradle           = 'D:\android-tools\gradle-8.7\bin\gradle.bat'

function Say($text, $colour = 'Gray') { Write-Host $text -ForegroundColor $colour }

Say ''
Say '  NEXORA CONSOLE — release' 'Cyan'
Say '  ------------------------' 'Cyan'

# ---- what it is now -------------------------------------------------
$BuildFile = Join-Path $Root 'app\build.gradle.kts'
if (-not (Test-Path $BuildFile)) { Say "Cannot find $BuildFile" 'Red'; Read-Host 'Enter to close'; exit 1 }
$Build = Get-Content $BuildFile -Raw

# 1.8.0 — the console is signed with Nexora's release key from now on, and a
# phone installs an update only over the same key. Without the key's file the
# build would quietly fall back to this computer's debug key, and every phone
# would refuse that update — so nothing is built or published without it.
$Signing = 'D:\nexora-signing\keystore-console.properties'
if (-not (Test-Path $Signing)) {
    Say "  STOPPED: $Signing is missing." 'Red'
    Say '  The console is signed with Nexora''s release key (alias nexora-console in' 'Red'
    Say '  D:\nexora-signing\nexora-release.jks). Copy the D:\nexora-signing folder back' 'Red'
    Say '  from its backup, then run this again. Nothing has been changed.' 'Red'
    Read-Host '  Enter to close'
    exit 1
}

$CurrentCode = [int]([regex]::Match($Build, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$CurrentName = [regex]::Match($Build, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
Say "  Installed builds are on $CurrentName (code $CurrentCode)."

# ---- what it is about to be -----------------------------------------
Say ''
$NewName = Read-Host "  New version name (Enter keeps $CurrentName)"
if ([string]::IsNullOrWhiteSpace($NewName)) { $NewName = $CurrentName }
$NewCode = $CurrentCode + 1

$Notes = Read-Host '  What changed (one line, shown on the phone)'
if ([string]::IsNullOrWhiteSpace($Notes)) { $Notes = "Version $NewName" }

Say ''
Say "  Building $NewName, code $NewCode." 'Yellow'

# The build file is the one place a version lives; edit it, then build.
$Build = [regex]::Replace($Build, 'versionCode\s*=\s*\d+',        "versionCode = $NewCode")
$Build = [regex]::Replace($Build, 'versionName\s*=\s*"[^"]+"',    "versionName = `"$NewName`"")
[System.IO.File]::WriteAllText($BuildFile, $Build, (New-Object System.Text.UTF8Encoding($false)))

# ---- build ----------------------------------------------------------
Push-Location $Root
try {
    & $Gradle --no-daemon --console=plain assembleRelease | Tee-Object -Variable GradleOut | Select-String -Pattern '^e: |BUILD' | ForEach-Object { Say "  $_" }
    if ($LASTEXITCODE -ne 0) { throw 'the build failed — nothing has been published' }
} catch {
    Say ''
    Say "  STOPPED: $_" 'Red'
    Say '  The version in build.gradle.kts was already changed; fix the error and run this again.' 'Red'
    Pop-Location
    Read-Host '  Enter to close'
    exit 1
}
Pop-Location

$Apk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $Apk)) { Say '  No APK was produced.' 'Red'; Read-Host '  Enter to close'; exit 1 }

# And the proof it is the right key: the certificate inside the APK must be
# the console's own (CN=Nexora Console), never "CN=Android Debug".
$ApkSigner = 'D:\android-tools\sdk\build-tools\34.0.0\apksigner.bat'
# stdout only: under 'Stop', PowerShell 5.1 would turn any warning apksigner
# writes to stderr into a terminating error if it were redirected here.
$Certs = (& $ApkSigner verify --print-certs $Apk | Out-String)
if ($LASTEXITCODE -ne 0 -or $Certs -notmatch 'CN=Nexora Console') {
    Say '  STOPPED: the APK is not signed with the console''s release key. Nothing has been published.' 'Red'
    Read-Host '  Enter to close'
    exit 1
}

# ---- stage it where the phones will fetch it ------------------------
$ReleaseDir = Join-Path $Root 'releases'
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
# The code goes in the name so every build has an address of its own.
# Reusing one address would mean a new manifest pointing at bytes GitHub's
# cache may still be serving from the last build — and the phone would
# refuse the download for failing its checksum, which is right but baffling.
$FileName = "nexora-console-$NewName-$NewCode.apk"
$Out = Join-Path $ReleaseDir $FileName
Copy-Item $Apk $Out -Force

$Sha  = (Get-FileHash $Out -Algorithm SHA256).Hash.ToLower()
$Size = (Get-Item $Out).Length

# The raw address of the file just written, worked out from the remote
# so that renaming the repository never means editing this script.
$Remote = (git -C $Root remote get-url origin)
$Slug   = [regex]::Match($Remote, 'github\.com[:/](.+?)(\.git)?$').Groups[1].Value
$Branch = (git -C $Root rev-parse --abbrev-ref HEAD)
$Url    = "https://raw.githubusercontent.com/$Slug/$Branch/releases/$FileName"

# ---- the file the service reads -------------------------------------
$Manifest = [ordered]@{
    versionCode = $NewCode
    versionName = $NewName
    url         = $Url
    notes       = $Notes
    sha256      = $Sha
    sizeBytes   = $Size
    publishedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
}
$Json = $Manifest | ConvertTo-Json -Depth 3
# No byte-order mark: the service parses this as JSON and a BOM would
# make the very first character unreadable.
[System.IO.File]::WriteAllText(
    (Join-Path $ReleaseDir 'latest.json'),
    $Json,
    (New-Object System.Text.UTF8Encoding($false)))

Say ''
Say ("  Built $NewName (code $NewCode), {0:N1} MB" -f ($Size / 1MB)) 'Green'
Say "  $Out"
Say "  $Url"

# ---- publish --------------------------------------------------------
Say ''
$Push = Read-Host '  Commit and push to GitHub now? (Y/N)'
if ($Push -match '^[Yy]') {
    Push-Location $Root
    git add -A
    git commit -m "Console $NewName - $Notes"
    git push
    $ok = ($LASTEXITCODE -eq 0)
    Pop-Location
    Say ''
    if ($ok) {
        Say '  Pushed. Every phone is offered this build within about five minutes.' 'Green'
    } else {
        Say '  The push failed. Run it again from the folder when the connection is back:' 'Red'
        Say '     git push' 'Red'
    }
} else {
    Say ''
    Say '  Not pushed. When you are ready, from this folder:' 'Yellow'
    Say "     git add -A && git commit -m `"Console $NewName`" && git push" 'Yellow'
}

Say ''
Read-Host '  Enter to close'
