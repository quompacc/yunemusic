# YuneMusic 1.3.0

Lokale, mit dem Original-Keystore signierte Release-APK vom 19.09.2026.

- Paket: `com.yunemusic`, versionCode `6`; Vorgänger im Workspace: `1.2.1`, versionCode `5`.
- Neues Design und Icon sowie die im Prüfbericht beschriebenen Korrekturen.
- NewPipe: neu abgeglichener `dev-SNAPSHOT`, JitPack-Auflösung `dev-ab984a8e3b-1`, aktueller upstream dev-Commit `ab984a8e3bcd0bc6d4b5f90860815f7b10476541` vom 17.09.2026.
- SHA-256 der getesteten Extractor-JAR: `5c238c57328095ae03443075c462f44b910ff3b9d53ddfe1dfe23ade085d19b8`.
- Der finale Build erfolgte offline mit dem getesteten Abhängigkeitscache, ohne weiteren Snapshot-Wechsel.

## Datenerhalt beim Update

Die vorhandene App direkt mit dieser APK aktualisieren. **Nicht vorher deinstallieren und keine App-Daten löschen.** Falls Android das Update ablehnt, zuerst die Ursache prüfen; eine Deinstallation ist keine Lösung für ein Signatur- oder Versionsproblem.

App-ID, Signaturschlüssel, Datenbankname `yune_music.db`, Schema-Version 4 und das DataStore-Dateiformat bleiben erhalten. Die Datenbankdefinition und Entities wurden für dieses Release nicht verändert. Es gibt keinen destruktiven Migrations-Fallback. Die Backup-Ausschlüsse beeinflussen Backups und Geräteübertragungen, nicht ein normales Update auf demselben Gerät.

Sechs Robolectric-Tests bestanden: Wiederöffnen von Schema 4, Migrationen von 1/2/3, bekannter Schema-3-Sonderfall mit fehlenden Tabellen sowie nicht unterstützte Version ohne Datenlöschung. Geprüft werden Likes, Verlauf und vorhandene Playlist-/Download-Einträge. Historische Testdaten wurden aus den im Repository vorhandenen Migrationen rekonstruiert; die konkrete Datenbank auf dem Handy wurde nicht untersucht. Deshalb keine absolute Garantie für unbekannte Altversionen oder beschädigte Datenbanken.

## Prüfungen

- Release-Build einschließlich `lintVitalRelease`: erfolgreich.
- Original-Signatur mit vorheriger Release-APK verglichen und APK kryptografisch verifiziert.
- 6 Datenbanktests: erfolgreich, keine übersprungen.
- 3 echte YouTube-Smoke-Tests: Suche, Audio-URL einschließlich Datenabruf und verwandte Titel erfolgreich.
- Kein Installations-/Update-Test auf einem echten Gerät durchgeführt.
- Der vollständige Lint-Befund und die offenen Sicherheits-/Funktionsbefunde aus `docs/REVIEW-2026-09-19.md` bleiben bestehen. Der Release-Build behebt sie nicht automatisch.

APK und separate SHA-256-Datei liegen in diesem Ordner. Es wurde kein Repository-Release veröffentlicht.
