# YuneMusic 1.3.1

VersionCode 7, Paket `com.yunemusic`.

„Session starten“ liest beim Antippen die gesamte gespeicherte Titelbasis, die aktuellen Likes und den Hörverlauf aus der lokalen Datenbank. Die besten bis zu 30 Titel starten absteigend nach Bewertung als Wiedergabeliste. Empfehlungen der Startseite und Trending-Titel werden dafür nicht verwendet. Ohne positive Hörsignale oder Likes erscheint ein Hinweis; es startet keine beliebige Ersatzliste.

Bewertung: aktueller Like +10; mindestens 90 Prozent gehört +2 pro Ereignis, mindestens 70 Prozent +1, mindestens 40 Prozent +0,25; früher Skip unter 20 Prozent −3. Hörereignisse haben eine Halbwertszeit von 30 Tagen; ihre Summe wird pro Titel auf −8 bis +8 begrenzt. Damit werden häufig gehörte Songs berücksichtigt, ohne dass alte Wiederholungen aktuelle Likes verdrängen. Historische Like-Flags ersetzen keine aktuellen Likes. Titel-IDs werden dedupliziert. Das ist eine nachvollziehbare Heuristik, keine objektive Garantie des Musikgeschmacks.

Die Session ist eine laufende Warteschlange, keine zusätzliche dauerhaft gespeicherte Bibliotheks-Playlist. Die bestehende Radio-Fortsetzung am Queue-Ende bleibt bestehen. Die bekannte Ungenauigkeit der bisherigen Hörzeiterfassung bei Pausen wurde in dieser Änderung nicht korrigiert und kann historische Bewertungen beeinflussen.

Keine Änderung an Datenbankschema, Datenbankname oder Migrationen. Direkt über die bestehende Release-App installieren; nicht deinstallieren.

Validierung: sechs Session-Tests für Ranking, Aktualität, leere Daten, Deduplizierung/Länge, entfernte Likes und frisches Lesen ohne Netzwerkempfehlungen; zusätzlich die sechs Datenbanktests. Release-Build und APK-Signatur werden vor Übergabe geprüft. Kein Gerätetest. NewPipe verwendet denselben zuvor getesteten Stand wie 1.3.0.
