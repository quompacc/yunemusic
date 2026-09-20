# YuneMusic 1.5.0 — Persönlicher Entdeckungsmix

Paket `com.yunemusic`, Versionscode **11**, Originalsignierung wie 1.4.0/1.4.1.
Signatur mit `apksigner` geprüft; APK und SHA-256 liegen in diesem Verzeichnis.

**3.161.492 Bytes / 3,0150 MiB**, gegenüber 1.4.0 weiterhin **91,78 % kleiner**.
Die Optimierungen aus [1.4.1](RELEASE-1.4.1.md) bleiben aktiv: keine Windows-EXE,
AGP 8.6.1 / R8 8.6.27, Code- und Ressourcenverkleinerung.
R8-Mapping: `YuneMusic-1.5.0.mapping.zip` (lokales Release-Artefakt).

## Session starten

Die vorherige Version sortierte ausschließlich vorhandene Titel nach Likes und
Hörhistorie. Das erzeugte weitgehend dieselbe Favoritenliste; es gab keine Suche
nach neuen Titeln. Jetzt dient die lokale Datenbank als Ausgangspunkt für eine
neue Suche, unabhängig von den sichtbaren Empfehlungen auf der Startseite.

- Aktuelle Likes und Hörereignisse werden bei jedem Klick erneut gelesen.
  Bis zu fünf verschiedene Ausgangskünstler werden gewichtet zufällig ausgewählt;
  starke positive Signale erhalten mehr Gewicht, ohne eine feste Reihenfolge.
- Verwandte Titel dieser Ausgangssongs und gezielte Suchen nach bis zu drei
  Ausgangskünstlern liefern Kandidaten. Keine allgemeinen Trending-Treffer.
  Maximal drei Discovery-Aufrufe gleichzeitig; einzelne Ausfälle lassen die
  übrigen Quellen weiterarbeiten. Zeitlimits sind kooperativ; ein bereits
  laufender blockierender HTTP-Aufruf kann bis zum Netzwerk-Timeout dauern.
- Ziel sind bis zu 30 Titel mit **70 % Neuentdeckungen / 30 % bekannten Songs**,
  beginnend mit einer Neuentdeckung. Die Quote gilt bei genügend passenden
  Kandidaten. Bei Ausfällen oder einer kleinen Bibliothek kann die Session
  kürzer sein oder mehr bekannte Titel enthalten.
- Keine doppelten Video-IDs; erkennbare gleiche Titel unter anderer Video-ID
  zählen nicht als Entdeckung. Künstler werden nicht direkt hintereinander
  gespielt, sofern mehr als ein Künstler vorhanden ist. Pro Künstler maximal
  drei Titel, bei sehr kleiner Künstlerauswahl maximal vier.
- In den letzten 24 Stunden gehörte Titel und die vorherige Session werden
  abgewertet. Die Erinnerung an die vorige Session gilt für den aktuellen
  App-Prozess. Der Hörverlauf bleibt dauerhaft in der bisherigen Datenbank.
- Zuletzt früh übersprungene Titel pausieren 14 Tage. Abgelehnte Künstler werden
  ausgelassen; aktuelle explizite Likes können den alten Künstler-Bann aufheben.
- Neue Kandidaten müssen 1–15 Minuten lang sein und plausible Musik-Metadaten
  besitzen. Vorhandene lange Favoriten werden dadurch nicht gelöscht oder aus
  der Bibliothek entfernt. Künstler-Suchtreffer müssen zum Kanal oder zum
  erkennbaren Interpreten im Titel passen; bloße Namensähnlichkeit reicht nicht.
- Ohne brauchbare neue Treffer wird die lokale Auswahl gewichtet neu gemischt.
  Das ist ein Fallback, keine Behauptung, offline neue Musik entdecken zu können.

Die Zuordnung basiert auf Kanalnamen, Titeln und YouTube-Verwandtschaft, nicht auf
einer Audioanalyse. Namensgleichheit, falsch beschriftete Videos und nicht erkannte
Neuauflagen können weiterhin durchkommen; persönliche Trefferqualität muss sich
auch beim Hören bewähren.

## Tests und Datenerhalt

**46 JVM-/Robolectric-Tests erfolgreich**, darunter 13 neue Discovery-Tests für
Quote, Quellenfehler, Abbruch, aktuelle DB-Signale, Künstlerwechsel, Wiederholungen,
bekannte Neu-Uploads, Namensverwechslungen, Skip- und Künstlerausschlüsse.
Die bisherigen Datenbank-, Cache-, Queue-, Lebenszyklus-, Rhino- und echten
NewPipe-Netzwerktests bestehen ebenfalls. Der signierte, optimierte Release-Build
inklusive Release-Lint ist erfolgreich.

Vollständiges Lint auf dem finalen Stand: **1 Fehler, 66 Warnungen**. Der Fehler
betrifft weiterhin die fehlende Android-Auto-Sprachsuche-Intent-Deklaration;
keine Baseline oder Unterdrückung.

Keine Änderungen an Room-Schema 4, Datenbankname, Migrationen, DataStore-Datei oder
Downloadverzeichnis. Installation auf dem Pixel als Update mit `adb install -r`;
keine Deinstallation oder Datenlöschung.

Geräteprüfung: Eine erste 1.5.0-Session wurde auf dem Pixel erstellt und abgespielt.
Dabei fiel ein Namenskonflikt F.R.K./FRK Music Pro auf; die Künstler-Suche wurde
anschließend verschärft und mit einem Regressionstest abgesichert.
Die endgültige APK wurde nach den 46 erfolgreichen Tests erneut installiert
und ebenfalls live getestet: „Session starten“ erzeugte **30 Songs**, beginnend
mit Beastie Boys, Wu-Tang Clan und Fatboy Slim, gefolgt von einem vertrauten
Draugr-Balled-Titel und weiteren Entdeckungen. Wiedergabe lief mit fortschreitender
Position und MediaSession-Status PLAYING. Die sichtbare Queue hatte wechselnde
Künstler; die komplette 70/30-Zuordnung aller privaten Datenbankeinträge wurde
auf dem Gerät nicht separat ausgelesen. Diese Quote ist durch kontrollierte
Unit-Testdaten geprüft.

Nicht abgedeckt: objektive musikalische Trefferqualität, Langzeitverhalten über
viele Sessions, Offline-Abspielen des Testdownloads, vollständiger Datei-/DB-Abgleich
nach dem letzten Update, echte Funklöcher/Doze, Android Auto und andere Geräte/Android-Versionen.
Weitere Details zur Hardwareprüfung von Wiedergabe, Hintergrundwechsel, Downloads
und Datenübernahme stehen im Prüfbericht zu 1.4.1.
