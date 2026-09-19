# Nexora Console — build a release and publish it
# =====================================================================
# Double-click release.bat and answer two questions. This then:
#
#   1. bumps the version in app/build.gradle.kts
#   2. builds the APK
#   3. copies it to releases/nexora-console-<name>.apk
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
    & $Gradle --no-daemon --console=plain assembleDebug | Tee-Object -Variable GradleOut | Select-String -Pattern '^e: |BUILD' | ForEach-Object { Say "  $_" }
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

$Apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $Apk)) { Say '  No APK was produced.' 'Red'; Read-Host '  Enter to close'; exit 1 }

# ---- stage it where the phones will fetch it ------------------------
$ReleaseDir = Join-Path $Root 'releases'
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$Out = Join-Path $ReleaseDir "nexora-console-$NewName.apk"
Copy-Item $Apk $Out -Force

$Sha  = (Get-FileHash $Out -Algorithm SHA256).Hash.ToLower()
$Size = (Get-Item $Out).Length

# The raw address of the file just written, worked out from the remote
# so that renaming the repository never means editing this script.
$Remote = (git -C $Root remote get-url origin)
$Slug   = [regex]::Match($Remote, 'github\.com[:/](.+?)(\.git)?$').Groups[1].Value
$Branch = (git -C $Root rev-parse --abbrev-ref HEAD)
$Url    = "https://raw.githubusercontent.com/$Slug/$Branch/releases/nexora-console-$NewName.apk"

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
