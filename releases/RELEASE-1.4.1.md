# YuneMusic 1.4.1 — APK-Optimierung

Paket `com.yunemusic`, Versionscode **10**, erstellt am 20.09.2026.
APK: `YuneMusic-1.4.1.apk`, SHA-256 in der benachbarten `.sha256`-Datei.
Unveränderter Signaturschlüssel, mit `apksigner verify --print-certs` geprüft:
`5fc373cd56eaf5394d20a73ab11f9843101ce4c1e92e08dd7a792a7cde98649f`.

## Größe (tatsächlich gebaute, signierte APKs)

| | 1.4.0 | 1.4.1 |
|---|---:|---:|
| APK | 38.478.220 Bytes / 36,6957 MiB | 3.156.056 Bytes / 3,0098 MiB |
| DEX, komprimierte ZIP-Einträge | 15.091.121 Bytes | 2.654.294 Bytes |
| resources.arsc | 745.400 Bytes | 307.052 Bytes |
| Windows-EXE, komprimiert | 22.296.914 Bytes | 0 |

**91,80 % kleiner**, 33,6859 MiB gespart. Keine App-Funktion entfernt.

Die getrackte `app/src/main/assets/yt-dlp.exe` (22.499.858 Bytes unkomprimiert)
hatte keine Aufrufstelle oder sonstige Referenz im Repository. Extraktion und
Downloads laufen über NewPipe/OkHttp. Die Datei ist entfernt; die finale APK
enthält keine `.exe`-Datei.

## R8 und Keep-Regeln

Mit AGP 8.5.2 und aktivierter Optimierung wurde der kommentierte
`java.util.ConcurrentModificationException` in `minifyReleaseWithR8` reproduziert.
AGP **8.6.1 / R8 8.6.27** behebt den Buildfehler. Gradle 8.7 bleibt unverändert.
Google dokumentiert den entsprechenden R8-Fix (#359385828) in den
[Release Notes](https://developer.android.com/build/releases/agp-8-6-0-release-notes).

`isMinifyEnabled` und `isShrinkResources` sind aktiv; `-dontoptimize` ist entfernt.
Keine pauschalen Keeps für NewPipe, Hilt, Room-Entities oder App-Modelle mehr.
Consumer-Regeln der Bibliotheken schützen unter anderem Room und Serialization.
Das Mapping bestätigt obfuskiertes `TasteProfile` und `StreamInfo`, aber erhaltene
Room-Datenbank-Implementierungen.

Rhinos reflektiv initialisierte Laufzeit bleibt gezielt als Bibliotheksgrenze
geschützt, entsprechend [NewPipes Android-Regeln](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro).
Optionale Java-SE-Pfade (`javax.script`, `jdk.dynalink`, JavaToJSONConverters)
erhalten begrenzte Warn-Ausnahmen. NewPipes tatsächlicher Bytecode benutzt
`Context.setInterpretedMode(true)`, keine JVM-Codegenerierung.
NewPipe-Locale-Patternklassen behalten ebenfalls ihre Namen.

NewPipe bleibt der bereits getestete Snapshot `ab984a8e3b-1`, JAR-SHA-256
`5c238c57328095ae03443075c462f44b910ff3b9d53ddfe1dfe23ade085d19b8`.
Aufgelöstes Rhino ist **1.8.1** (transitiv von NewPipe), unverändert gegenüber 1.4.0.

## Verifikation

- **33 JVM-/Robolectric-Tests erfolgreich**: 6 Datenbank-/Migrationstests,
  6 persönliche Sessions, 4 Queue-Wechsel, 5 Wiedergabe-Lebenszyklus,
  7 Cachetests, 2 Rhino-/Profil-Kompatibilitätstests, 3 echte NewPipe-Netzwerktests
  (Suche, abrufbare Audio-Bytes, Related Tracks).
- `assembleRelease` einschließlich R8, Resource Shrinker und Release-Lint erfolgreich.
- Vollständiges `:app:lint` ausgeführt: **1 Fehler, 66 Warnungen** verbleiben.
  Der bestehende Fehler ist `MissingIntentFilterForMediaSearch` (Android-Auto-
  Sprachsuche). Nicht unterdrückt und nicht durch eine unbehandelte Intent-Deklaration
  kaschiert. 22 Media3-Opt-in-Fehler an den Cache-DataSources behoben.
- Die JVM-Tests prüfen nicht den R8-transformierten Code. Deshalb zusätzlich
  die tatsächliche signierte APK auf einem **Pixel 10 Pro XL** getestet:
  Update mit `adb install -r` erfolgreich; Versionscode 10, ursprüngliches
  `firstInstallTime` unverändert. App-Start, Hilt/Room, Suche, NewPipe-Extraktion,
  Opus-Wiedergabe mit fortlaufender Positionsanzeige erfolgreich.
- Vorher/nachher **15 Likes und 28 Verlaufstitel**, einschließlich überprüfter
  Beispielzeilen, unverändert. Alle sichtbaren Audio-/Cache-/WLAN-Schalter und
  Qualitätsstufe erhalten. Keine vorhandenen Downloads oder Playlists auf dem Gerät.
- Testdownload vollständig abgeschlossen (3353 KB, Repository und UI-ViewModel
  melden Erfolg). Automatischer Übergang nach Titelende mit App im Hintergrund
  nachgewiesen; nächster Titel `PLAYING`, `MusicService.isForeground=true`.
  Testwiedergabe anschließend pausiert. Testtitel bleibt als Download gespeichert.

Keine Änderungen an Datenbankname, Schema 4, Migrationen, DataStore-Dateiname
oder Downloadverzeichnis. Keine Deinstallation, kein Löschen von App-Daten.

**Nicht getestet:** physische Audioqualität, Wiedergabe des Downloads ohne Netzwerk,
kompletter Abgleich jeder privaten Datenbankzeile, lange Doze-Phasen, echte Funklöcher,
Android Auto, Bluetooth-Wechsel, andere Android-Versionen. Bestehende weitere
Befunde aus `docs/REVIEW-2026-09-19.md` bleiben offen.

Build-/Lintprotokolle und R8-Mapping dieser APK liegen lokal unter
`app/build/reports/release-1.4.1/`. Das Mapping ist zusätzlich als
`releases/YuneMusic-1.4.1.mapping.zip` unabhängig vom Buildverzeichnis archiviert.
