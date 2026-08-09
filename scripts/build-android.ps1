# ================================================================
# KONEKT Browser for Android — headless APK build.
# Pipeline: aapt2 compile/link -> javac -> d8 -> jar -> zipalign ->
# apksigner. No Gradle, no AGP, framework-only sources.
# Output: dist\KONEKT-Browser-android.apk
# ================================================================
$ErrorActionPreference = "Stop"

$root  = Split-Path -Parent $PSScriptRoot
$jdk   = "$env:LOCALAPPDATA\Programs\jdk17"
$sdk   = "$env:LOCALAPPDATA\Android\sdk"
$bt    = "$sdk\build-tools\34.0.0"
$ajar  = "$sdk\platforms\android-34\android.jar"
$adir  = "$root\android"
$build = "$adir\build"
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

foreach ($p in @("$jdk\bin\javac.exe", "$bt\aapt2.exe", "$bt\zipalign.exe", "$bt\apksigner.bat", "$bt\d8.bat", $ajar)) {
  if (-not (Test-Path $p)) { throw "missing tool: $p" }
}

Write-Output "clean build dir"
Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$build\gen", "$build\obj", "$build\dex" | Out-Null

Write-Output "aapt2 compile + link"
& "$bt\aapt2.exe" compile --dir "$adir\res" -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& "$bt\aapt2.exe" link -o "$build\base.apk" -I $ajar --manifest "$adir\AndroidManifest.xml" -A "$adir\assets" --java "$build\gen" --auto-add-overlay "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "javac"
$sources = @(Get-ChildItem "$adir\src" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$sources += @(Get-ChildItem "$build\gen" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$argfile = "$build\sources.txt"
$sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' } | Set-Content $argfile -Encoding ascii
cmd /c "`"$jdk\bin\javac.exe`" -encoding UTF-8 -Xlint:-options -nowarn -source 1.8 -target 1.8 -classpath `"$ajar`" -d `"$build\obj`" `"@$argfile`" 2>&1"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "d8"
# Call d8.jar directly (not d8.bat) so PowerShell's arg quoting handles the
# space in the repo path; run from obj/ with RELATIVE class paths so the
# argfile d8 reads has no spaces to split on.
$d8jar = if (Test-Path "$bt\lib\d8.jar") { "$bt\lib\d8.jar" } else { "$bt\d8.jar" }
Push-Location "$build\obj"
$classes = @(Get-ChildItem . -Recurse -Filter *.class | ForEach-Object { (Resolve-Path -Relative $_.FullName) -replace '^\.\\','' })
$clsfile = "$build\classes.txt"
$classes | Set-Content $clsfile -Encoding ascii
& "$jdk\bin\java.exe" -cp $d8jar com.android.tools.r8.D8 --release --min-api 24 --lib $ajar --output "$build\dex" "@$clsfile"
$d8rc = $LASTEXITCODE
Pop-Location
if ($d8rc -ne 0) { throw "d8 failed" }

Write-Output "assemble apk"
Copy-Item "$build\base.apk" "$build\unsigned.apk" -Force
Push-Location "$build\dex"
& "$jdk\bin\jar.exe" --update --file "$build\unsigned.apk" classes.dex
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "jar update failed" }

& "$bt\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---- signing key (generated once; keep it or installed updates break) ----
$ksdir = "$adir\keystore"
$ks    = "$ksdir\konekt.keystore"
$passf = "$ksdir\pass.txt"
if (-not (Test-Path $ks)) {
  New-Item -ItemType Directory -Force $ksdir | Out-Null
  $pass = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 28 | ForEach-Object { [char]$_ })
  [IO.File]::WriteAllText($passf, $pass)
  & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -alias konekt -keyalg RSA -keysize 2048 -validity 10950 `
    -storepass $pass -keypass $pass `
    -dname "CN=KONEKT Browser, O=NKO Intl. Foundation of Technological Research and Development"
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
  Write-Output "new signing key generated at android\keystore (gitignored - do not lose it)"
}

Write-Output "sign"
New-Item -ItemType Directory -Force "$root\dist" | Out-Null
$apk = "$root\dist\KONEKT-Browser-android.apk"
cmd /c "`"$bt\apksigner.bat`" sign --ks `"$ks`" --ks-key-alias konekt --ks-pass file:`"$passf`" --out `"$apk`" `"$build\aligned.apk`""
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

$verify = cmd /c "`"$bt\apksigner.bat`" verify --verbose `"$apk`" 2>&1"
$vrc = $LASTEXITCODE
$verify | Select-String -Pattern "^Verifies|scheme|Number of signers" | ForEach-Object { "$_" }
if ($vrc -ne 0) { throw "apksigner verify failed" }
$badge = & "$bt\aapt2.exe" dump badging $apk 2>&1
$badge | Select-String -Pattern "^package:|application-label:|launchable-activity:" | ForEach-Object { "$_" }
if (($badge -join "`n") -notmatch "network\.konekt\.browser") { throw "badging check failed" }

Write-Output ("APK OK: {0} ({1:N0} KB)" -f $apk, ((Get-Item $apk).Length / 1KB))
Write-Output ("SHA-256: " + (Get-FileHash $apk -Algorithm SHA256).Hash)
