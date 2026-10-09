# YuneMusic

Werbefreier YouTube-Musikplayer für Android. Kein API-Key, kein Google-Login — alle Daten werden anonym über [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) abgerufen.

## Features

- **Anonyme Wiedergabe** — NewPipe Extractor ersetzt die YouTube API vollständig
- **Hintergrundwiedergabe** — Foreground Service mit persistenter Benachrichtigung und Medientasten-Integration
- **Funkloch-Puffer** — bis zu fünf Minuten vorausladen, 256 MB automatisch verwalteter Audiocache und Vorbereitung der nächsten zwei Titel bei normaler Wiedergabereihenfolge; „Nur WLAN“ wird berücksichtigt
- **Android Auto** — vollständige MediaBrowserService-Integration mit Browse-Kategorien (Geliked, Verlauf, Warteschlange)
- **KI-Geschmacksanalyse** — lokales Taste Profile auf Basis von Hörverlauf, Likes und Skip-Verhalten
- **Persönliche Session** — sucht ausgehend von Likes und Hörverlauf neue Musik; Zielmix 70 % Entdeckungen / 30 % vertraute Songs, mit Künstlerwechseln und weniger Wiederholungen. Ohne passende Netzwerktreffer wird die lokale Auswahl neu gemischt.
- **Smart Radio** — kontinuierliche Wiedergabe über verwandte Tracks; bei leerem Queue automatisch nachgeladen
- **Shuffle / Repeat / Queue-Management** — vollständige Warteschlangenverwaltung inkl. Drag-to-reorder
- **Bibliothek** — Gelikte Songs, Hörverlauf
- **Car Mode** — vereinfachtes Vollbild-UI für die Nutzung im Auto
- **Sprachen** — Englisch (Standard) und Deutsch; ab Android 13 pro App umschaltbar

## Installation über F-Droid

YuneMusic hat ein eigenes F-Droid-Repo, das bei jedem Release automatisch aktualisiert wird.

1. In der F-Droid-App: **Einstellungen → Paketquellen → +**
2. Adresse: `https://raw.githubusercontent.com/quompacc/yunemusic/fdroid/repo`
3. Fingerprint: `5FC373CD56EAF5394D20A73AB11F9843101CE4C1E92E08DD7A792A7CDE98649F`

Oder auf dem Handy direkt öffnen:
[Repo hinzufügen](fdroidrepos://raw.githubusercontent.com/quompacc/yunemusic/fdroid/repo?fingerprint=5FC373CD56EAF5394D20A73AB11F9843101CE4C1E92E08DD7A792A7CDE98649F)

Alternativ: APK aus den [Releases](https://github.com/quompacc/yunemusic/releases) oder über
[Obtainium](https://github.com/ImranR98/Obtainium) mit der GitHub-URL des Repos.

## Tech Stack

| Bereich | Bibliothek |
|---|---|
| UI | Jetpack Compose, Material3, Dark Theme (OLED) |
| Wiedergabe | ExoPlayer (Media3) |
| YouTube-Daten | NewPipe Extractor (fest gepinnte Release via JitPack) |
| HTTP | OkHttp 4 |
| Datenbank | Room |
| Einstellungen | DataStore |
| DI | Hilt |
| Bilder | Coil |
| Background Jobs | WorkManager |
| Architektur | MVVM + Clean Architecture |

## Architektur

```
app/src/main/java/com/yunemusic/
├── ui/
│   ├── discover/       DiscoverScreen + ViewModel (Empfehlungen, Suche, Smart Mixes)
│   ├── player/         PlayerScreen + ViewModel (Queue, Radio, Playback State)
│   ├── library/        LibraryScreen + ViewModel (Likes, Verlauf)
│   ├── carmode/        CarModeScreen
│   ├── settings/       SettingsScreen + ViewModel
│   ├── components/     TrackOptionsSheet, MiniPlayer
│   └── navigation/     Navigation.kt
├── domain/
│   ├── model/          Track, TasteProfile, PlayEvent
│   ├── usecase/        PlayTrackUseCase, GetRecommendationsUseCase, SearchTracksUseCase
│   └── repository/     MusicRepository (Interface)
├── data/
│   ├── youtube/        YouTubeRepository, StreamExtractor, DownloaderImpl
│   ├── local/          Room DB, DAOs, Entities
│   ├── preferences/    UserPreferences (DataStore)
│   └── repository/     MusicRepositoryImpl
├── service/
│   ├── MusicService.kt    Foreground Service + MediaSession + Android Auto
│   └── TasteAnalyzer.kt   Lokale Geschmacksanalyse
└── di/
    └── AppModule.kt
```

## Voraussetzungen

- Android Studio Hedgehog oder neuer
- Android SDK 34
- Gerät / Emulator mit Android 8.0+ (minSdk 26)

## Build

```bash
git clone https://github.com/quompacc/yunemusic.git
cd yunemusic
./gradlew assembleDebug
```

`local.properties` wird nicht versioniert — Android Studio legt die Datei beim ersten Öffnen automatisch an.

Ohne Keystore (`yunemusic.jks` bzw. `KEYSTORE_FILE`) baut `./gradlew assembleRelease` eine
unsignierte Release-APK — so baut auch F-Droid.

### NewPipe-Version

Der Extractor ist in [`app/build.gradle.kts`](app/build.gradle.kts) auf ein Release-Tag gepinnt
(reproduzierbare Builds, Voraussetzung für F-Droid). Gegen den neuesten Entwicklungsstand bauen:

```bash
./gradlew -PnewpipeVersion=dev-SNAPSHOT --refresh-dependencies assembleRelease
```

Sobald TeamNewPipe ein neues Release veröffentlicht, die Standardversion in
`app/build.gradle.kts` anheben, `versionCode`/`versionName` erhöhen und ein Tag `vX.Y.Z` setzen.

## Automatische NewPipe-Updates (CI)

YouTube ändert alle paar Wochen seine interne API — dann bricht der einkompilierte
NewPipe Extractor und die installierte APK spielt nichts mehr ab, bis mit dem
neuesten `dev-SNAPSHOT` neu gebaut wird. Das übernimmt (mit `-PnewpipeVersion=dev-SNAPSHOT`)
[`.gitea/workflows/newpipe-healthcheck.yml`](.gitea/workflows/newpipe-healthcheck.yml):

1. **Täglich um 04:17 UTC**: Smoke-Test ([`NewPipeSmokeTest`](app/src/test/java/com/yunemusic/smoke/NewPipeSmokeTest.kt))
   gegen die echte YouTube-API mit dem jeweils neuesten NewPipe-SNAPSHOT
   (`--refresh-dependencies`) — geprüft werden Suche, Stream-Extraktion
   (inkl. tatsächlichem HTTP-Abruf der Audio-URL) und Related Tracks.
2. **Bei Erfolg**: signierte Release-APK bauen und als rollendes
   **`nightly`-Release** in Gitea veröffentlichen.
3. **Bei Fehlschlag**: automatisch ein Issue anlegen — das heißt: YouTube hat etwas
   geändert und TeamNewPipe hat den Fix noch nicht veröffentlicht. Sobald der Fix
   im SNAPSHOT ist, baut der nächste Lauf automatisch wieder eine funktionierende APK.

### Einrichtung (einmalig)

1. In Gitea muss ein Actions-Runner aktiv sein (Label `ubuntu-latest`).
2. Repo → Einstellungen → Actions → Secrets anlegen:
   - `KEYSTORE_B64` — `base64 -w0 yunemusic.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. Aufs Handy: [Obtainium](https://github.com/ImranR98/Obtainium) installieren und
   als Quelle die Gitea-Release-URL des Repos eintragen — dann meldet sich das
   Handy von selbst, sobald eine neue Nightly-APK bereitliegt.

Auf GitHub übernehmen das [`.github/workflows/newpipe-healthcheck.yml`](.github/workflows/newpipe-healthcheck.yml)
(Nightly) und [`.github/workflows/release.yml`](.github/workflows/release.yml): Jedes Tag `vX.Y.Z`
erzeugt ein signiertes GitHub-Release mit dem Fastlane-Changelog als Release-Text.
Die Secrets sind dieselben (Settings → Secrets and variables → Actions).

## Android Auto

YuneMusic erscheint automatisch als Media-App in Android Auto. Die Browse-Hierarchie zeigt:

- **Zuletzt gehört** — die letzten 30 gespielten Tracks
- **Geliked** — alle gelikten Tracks
- **Warteschlange** — aktuelle Wiedergabeliste; wird automatisch vom Radio-Algorithmus mit verwandten Tracks befüllt und wächst endlos weiter

Tracks können direkt aus Auto heraus abgespielt werden. Sprachbefehle ("Hey Google, spiel … auf YuneMusic") werden über `onPlayFromSearch` weitergeleitet.

## Datenschutz

- Suche, Streaming und Cover-Abrufe übertragen Anfragen und die IP-Adresse an YouTube beziehungsweise dessen Auslieferungsdienste. Ohne Google-Login bedeutet nicht anonym gegenüber diesen Diensten.
- Kein Google-Konto, kein API-Key erforderlich
- Taste Profile, Likes und Verlauf werden ausschließlich lokal in Room gespeichert
- Downloads werden auf Wunsch im privaten App-Speicher abgelegt; Profildaten und Downloads sind von Android-Backups und Gerätetransfers ausgeschlossen.
- Gestreamtes Audio wird zusätzlich im privaten Cache gespeichert (maximal 256 MB Audiodaten, ältere Einträge werden verdrängt). Unter „Für Funklöcher vorladen“ kann das automatische Vorladen kommender Titel deaktiviert werden. Cache-Dateien sind keine dauerhaften Downloads und können vom Betriebssystem entfernt werden.

## Lizenz

YuneMusic ist freie Software unter der [GNU General Public License v3.0 oder später](LICENSE)
(`GPL-3.0-or-later`). Der verwendete [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)
steht ebenfalls unter GPLv3.

YuneMusic ist kein offizielles Produkt von YouTube oder Google und steht in keiner Verbindung zu ihnen.
