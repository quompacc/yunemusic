# YuneMusic

Werbefreier YouTube-Musikplayer für Android. Kein API-Key, kein Google-Login — alle Daten werden anonym über [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) abgerufen.

## Features

- **Anonyme Wiedergabe** — NewPipe Extractor ersetzt die YouTube API vollständig
- **Hintergrundwiedergabe** — Foreground Service mit persistenter Benachrichtigung und Medientasten-Integration
- **Android Auto** — vollständige MediaBrowserService-Integration mit Browse-Kategorien (Geliked, Verlauf, Warteschlange)
- **KI-Geschmacksanalyse** — lokales Taste Profile auf Basis von Hörverlauf, Likes und Skip-Verhalten
- **Smart Radio** — kontinuierliche Wiedergabe über verwandte Tracks; bei leerem Queue automatisch nachgeladen
- **Shuffle / Repeat / Queue-Management** — vollständige Warteschlangenverwaltung inkl. Drag-to-reorder
- **Bibliothek** — Gelikte Songs, Hörverlauf
- **Car Mode** — vereinfachtes Vollbild-UI für die Nutzung im Auto

## Tech Stack

| Bereich | Bibliothek |
|---|---|
| UI | Jetpack Compose, Material3, Dark Theme (OLED) |
| Wiedergabe | ExoPlayer (Media3) |
| YouTube-Daten | NewPipe Extractor (dev-SNAPSHOT via JitPack) |
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
git clone https://gitea.hl.home.arpa/eduard/yunemusic-app.git
cd yunemusic-app
./gradlew assembleDebug
```

`local.properties` wird nicht versioniert — Android Studio legt die Datei beim ersten Öffnen automatisch an.

## Automatische NewPipe-Updates (CI)

YouTube ändert alle paar Wochen seine interne API — dann bricht der einkompilierte
NewPipe Extractor und die installierte APK spielt nichts mehr ab, bis mit dem
neuesten `dev-SNAPSHOT` neu gebaut wird. Das übernimmt
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

Das Workflow-Format ist GitHub-kompatibel: für GitHub Actions die Datei einfach
nach `.github/workflows/` kopieren.

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

## Lizenz

Privates Projekt.
