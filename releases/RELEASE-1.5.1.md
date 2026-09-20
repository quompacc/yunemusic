# YuneMusic 1.5.1 — Lint und sichere Wiedergabe-Einstiege

Paket `com.yunemusic`, Versionscode **12**. Signierte APK: **3.141.504 Bytes
(2,996 MiB)**. Originalschlüssel, Zertifikat-SHA-256 unverändert:
`5fc373cd56eaf5394d20a73ab11f9843101ce4c1e92e08dd7a792a7cde98649f`.
APK-Prüfsumme und R8-Mapping liegen neben der APK.

## Behobene Ursachen

- `MEDIA_PLAY_FROM_SEARCH` ist an der Activity deklariert und wird in `onCreate`
  sowie `onNewIntent` verarbeitet. Activity und Compose verwenden dasselbe
  PlayerViewModel. Der Suchtext bzw. Künstler/Titel-Metadaten werden an dieselbe
  Wiedergabelogik wie MediaSession-Sprachsuchen weitergegeben. Leere Anfragen
  setzen bestehende Wiedergabe fort oder beginnen mit vorhandenen Favoriten.
  Wiederherstellung der Activity löst den ursprünglichen Intent nicht erneut aus.
- Das PlayerViewModel hält den gebundenen Musikdienst nur noch schwach;
  Verbindung, Collector und ausstehende Befehle werden beim Freigeben bereinigt.
- Benachrichtigungsbefehle tragen eine private zufällige Capability des laufenden
  Service in unveränderlichen PendingIntents. Direkte Next-/Pause-/Play-/Stop-
  Intents ohne gültigen Token werden ignoriert. Der Token wird nicht geloggt oder
  persistiert; alte Tokens gelten nach einem Service-Neustart nicht mehr.
- Ungenutzte String-/Farbreste entfernt, Compose-Modifier-Parameter korrigiert,
  Zahlenformatierung mit expliziter Geräte-Locale versehen.
- Adaptive Icons in einem gemeinsamen Ressourcenordner konsolidiert und beide
  Icon-Varianten mit dem vorhandenen monochromen Symbol versehen.
- Rhino-Deklaration auf die bereits tatsächlich aufgelöste Version 1.8.1
  angeglichen; kein Wechsel der verwendeten Rhino-Binärversion.

## Eine begründete Lint-Ausnahme

`ExportedService` wird ausschließlich an `MusicService` im Manifest ausgenommen.
Ein MediaBrowserService muss für Android Auto und System-Medienbrowser erreichbar
sein; eine App-Signaturberechtigung würde fremde legitime Clients aussperren.
Stattdessen prüft `onGetRoot` Paket/UID über die Android-Mediencontroller-
Vertrauensprüfung. Direkte interne Benachrichtigungsbefehle sind jetzt zusätzlich
mit der Capability geschützt. Dies ist keine globale Unterdrückung und keine
Lint-Baseline. Die Ausnahme ist direkt am Service kommentiert.

Grundlage: [Android: MediaBrowserService und Client-Prüfung](https://developer.android.com/media/legacy/audio/mediabrowserservice),
[Assistant-Integration](https://developer.android.com/media/implement/assistant).

## Verifikation am 20.09.2026

- `:app:lint` und zusätzlich `:app:lintRelease`: **No issues found**.
- **52 Tests, 0 Fehler**, darunter sechs neue Tests zu Such-Extras und gültigen,
  fehlenden, falschen sowie veralteten Steuer-Tokens. Datenbank-, Cache-, Session-,
  Queue-, Rhino- und echte NewPipe-Netzwerktests bestehen weiterhin.
- Optimierter signierter Release-Build erfolgreich; Signatur und Version geprüft.
- Pixel 10 Pro XL: datenerhaltendes Update mit `adb install -r`, ursprüngliches
  Erstinstallationsdatum unverändert. Sprachsuch-Start nach Update erfolgreich;
  zweite Suchanfrage erreicht die bereits geöffnete Activity (`onNewIntent`) und
  wechselt zur gesuchten Beastie-Boys-Wiedergabe.
- Direkter externer Next-Aufruf mit gefälschtem Token und Pause ohne Token
  änderten weder Titel noch PLAYING-Zustand. Regulärer Mediencontroller konnte
  anschließend pausieren; PAUSED-Zustand bestätigt.

Keine Änderungen an Datenbankschema, Migrationen, Datenbank-/DataStore-Dateiname
oder Downloadverzeichnis. Keine Deinstallation oder Löschung von App-Daten.

Nicht getestet: reales Android-Auto-Fahrzeug, gesprochener Assistant-Befehl mit
Mikrofon, tatsächlicher Druck auf jeden Benachrichtigungsbutton, Langzeit-Leak-
Profiling und andere Android-Versionen. Die Voice-Tests auf Hardware senden die
offiziellen Intents direkt. Sie ersetzen keinen vollständigen Android-Auto-Test.

Lokale Berichte: `app/build/reports/lint-results-debug.html`,
`lint-results-release.html`, `tests/testDebugUnitTest/index.html`.
