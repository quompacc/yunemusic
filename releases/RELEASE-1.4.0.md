# YuneMusic 1.4.0

Paket `com.yunemusic`, Versionscode 9. Originalsignierung, Update über die bestehende App.

## Cache und Funkloch-Vorsorge

- ExoPlayer puffert bis zu fünf Minuten voraus, statt bisher maximal 30 Sekunden. Die Startschwelle bleibt bei 1,5 Sekunden, damit der erste Song nicht erst vollständig geladen werden muss.
- Ein prozessweiter Media3-SimpleCache speichert gestreamte Audiodaten im privaten Cache-Verzeichnis. LRU-Verdrängung begrenzt die Audiodaten auf 256 MiB. Metadaten und Cache-Index liegen getrennt von der Nutzerdatenbank.
- Sobald der Player mindestens 30 Sekunden Reserve hat, werden bei normaler Reihenfolge die nächsten zwei verschiedenen Titel nacheinander vorgeladen. Bei Pufferproblemen, Pause, Queue-Wechsel oder deaktiviertem Schalter wird das spekulative Vorladen abgebrochen. Ein laufender Netzwerk-Read kann noch bis zum 8-Sekunden-Timeout brauchen.
- Bereits gecachte Bytes werden wiederverwendet. Gleichzeitiges Spielen und Schreiben desselben Bereichs kann vorübergehend einen zusätzlichen Netzwerkabruf verursachen; die laufende Wiedergabe wartet nicht auf einen Cache-Schreiblock.
- Automatisches Vorladen ist pro Titel auf 20 MiB begrenzt; sehr lange Konzerte/Mixe werden nicht vollständig auf Vorrat geladen. Der normale laufende Stream kann weiter gecacht werden.
- Bei Shuffle wird kein unzuverlässig vorhergesagter Folgetitel geladen. Der laufende Titel verwendet weiterhin den größeren Puffer und Cache.
- Der Schalter „Für Funklöcher vorladen“ ist standardmäßig aktiv und steuert das Vorladen kommender Titel. Der normale Wiedergabepuffer und die Wiederverwendung vorhandener Cache-Daten bleiben bei ausgeschaltetem Schalter bestehen.

## Offline und Netzwerkregeln

Vollständig vorhandene Titel der gewählten Qualitätsstufe starten ohne erneute YouTube-Extraktion aus dem Cache, selbst wenn die gespeicherte signierte Stream-URL abgelaufen ist. Unvollständige Dateien werden nicht als offline verfügbar behandelt. Unterschiedliche Stream-URLs bleiben getrennte Ressourcen, damit keine Bytes verschiedener Audioformate vermischt werden. Vollständigkeit wird anhand der gespeicherten Länge und aller Cache-Bereiche geprüft.

Die WLAN-Einstellung wird sowohl vor dem Auflösen einer neuen URL als auch bei Netzwerkzugriffen des Players und Cache-Writers berücksichtigt. Bereits vorhandenes Cache-Audio bleibt auch ohne WLAN lesbar. Automatisches Vorladen kann zusätzliche mobile Daten verbrauchen, wenn „Nur WLAN“ deaktiviert ist. Es garantiert keine bestimmte Funkloch-Dauer: Entscheidend ist, wie viele Daten vor dem Empfangsausfall tatsächlich geladen wurden. Für längere bekannte Funklöcher bleiben ausdrückliche Downloads sinnvoll.

## Datenerhalt und Prüfung

Keine Änderung an `yune_music.db`, Room-Schema, Migrationen oder vorhandenen Downloads. Die neue Einstellung ergänzt DataStore, ohne vorhandene Werte zurückzusetzen. Android darf Cache-Dateien bei Speicherknappheit löschen; sie sind kein Ersatz für dauerhafte Downloads.

Sieben neue Robolectric-Cachetests: komplett offline mit abgelaufener URL, unvollständige Bereiche und Fortsetzen ohne erneuten Abruf vorhandener Bytes, Wiederöffnen und Qualitätszuordnung, deaktiviertes Vorladen, LRU-Verdrängung, Netzwerkregelwechsel während eines Reads und unveränderte lokale Download-Wiedergabe. Zusätzlich werden die bisherigen 21 Tests für Datenbank, persönliche Sessions, Queue und Hintergrund-Lebenszyklus ausgeführt.

Die Cachetests verwenden kontrollierte Audiodaten und simulierte Netzwerkausfälle. Kein echter Fahrtest oder Funkzellenwechsel auf Hardware wurde durchgeführt. Die übrigen offenen Befunde aus dem Prüfbericht bleiben bestehen. NewPipe wurde für dieses Update nicht verändert.
