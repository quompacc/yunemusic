# Ein-Befehl-Update, wenn YouTube den Extractor gebrochen hat:
#   .\update-app.ps1          -> neuesten NewPipe-SNAPSHOT holen, testen, APK bauen
#   .\update-app.ps1 -Install -> zusätzlich per adb aufs angeschlossene Handy installieren
param([switch]$Install)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
}

Write-Host "1/3  Neuesten NewPipe dev-SNAPSHOT laden und gegen YouTube testen..." -ForegroundColor Cyan
./gradlew --refresh-dependencies :app:testDebugUnitTest --tests "com.yunemusic.smoke.NewPipeSmokeTest"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Smoke-Test ROT: YouTube hat etwas geändert und NewPipe hat noch keinen Fix veröffentlicht." -ForegroundColor Red
    Write-Host "-> In 1-2 Tagen erneut versuchen (TeamNewPipe fixt meist schnell)." -ForegroundColor Yellow
    exit 1
}

Write-Host "2/3  Signierte Release-APK bauen..." -ForegroundColor Cyan
./gradlew :app:assembleRelease
if ($LASTEXITCODE -ne 0) { exit 1 }

$apk = "app\build\outputs\apk\release\app-release.apk"
Write-Host "APK liegt unter: $apk" -ForegroundColor Green

if ($Install) {
    Write-Host "3/3  Auf Gerät installieren (USB-Debugging muss aktiv sein)..." -ForegroundColor Cyan
    adb install -r $apk
} else {
    Write-Host "3/3  Zum Installieren: .\update-app.ps1 -Install (Handy per USB) oder APK aufs Handy kopieren." -ForegroundColor Cyan
}
