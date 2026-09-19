# YuneMusic 1.3.2

Paket `com.yunemusic`, Versionscode 8. Korrektur für automatische Titelwechsel im Hintergrund.

Der Mediendienst wurde bisher bei jedem `isPlaying=false` aus dem Vordergrund genommen. Das geschieht auch am Titelende und beim Puffern, ohne dass der Benutzer pausiert hat. Der Dienst bleibt jetzt während aktiver Wiedergabeabsicht beim Puffern und Auflösen des nächsten Titels im Vordergrund. Echte Pause, Stopp und Fehler geben den Schutz frei. Zustandswechsel werden über `onEvents` gemeinsam ausgewertet; Pause während des Pufferns wird ebenfalls erkannt.

ExoPlayer hält während Wiedergabe/Puffern die CPU wach (`WAKE_MODE_LOCAL`). Für die Lücke zwischen Titelende und Vorbereitung des Folgetitels gibt es einen separaten Partial Wake Lock, maximal 60 Sekunden. Er wird bei Verlassen des Übergangs, Pause, Fehler oder Dienstende freigegeben. Die bereits vorhandene WAKE_LOCK-Berechtigung wird genutzt; kein neuer Benutzerschalter ist nötig. Medienbenachrichtigungen melden Puffern statt fälschlich Pause und erlauben Pausieren während des Ladens.

Der Fehlerbefund passt zur Rückmeldung: Wechsel bei geöffneter App funktioniert, im Hintergrund bleibt die Wiedergabe stehen, und manuelles Weiter funktioniert. Das konkrete Handy wurde nicht untersucht; ein Bildschirm-aus-Test auf dem Gerät bleibt notwendig. Eine zusätzliche herstellerspezifische Einschränkung ist damit nicht ausgeschlossen.

Regressionstests: 5 für den Hintergrund-Lebenszyklus, 4 für Queue-Wechsel/Repeat, 6 für persönliche Sessions und 6 für Datenbank-Upgrades. Die Lebenszyklustests prüfen Zustandsentscheidungen, nicht das reale Energiemanagement eines Android-Geräts.

Keine Änderung an Datenbankschema, Migrationen oder App-ID. Originalsignierung; direkt über die bestehende App installieren. Nicht deinstallieren oder App-Daten löschen. NewPipe-Stand unverändert gegenüber der getesteten Version 1.3.1.

Die übrigen im Prüfbericht dokumentierten offenen Befunde werden durch diesen Patch nicht behoben.
