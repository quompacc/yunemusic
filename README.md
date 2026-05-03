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

## Android Auto

YuneMusic erscheint automatisch als Media-App in Android Auto. Die Browse-Hierarchie zeigt:

- **Zuletzt gehört** — die letzten 30 gespielten Tracks
- **Geliked** — alle gelikten Tracks
- **Warteschlange** — aktuelle Wiedergabeliste (nur sichtbar wenn befüllt)

Tracks können direkt aus Auto heraus abgespielt werden. Sprachbefehle ("Hey Google, spiel … auf YuneMusic") werden über `onPlayFromSearch` weitergeleitet.

## Datenschutz

- Keine Nutzerdaten verlassen das Gerät
- Kein Google-Konto, kein API-Key erforderlich
- Taste Profile, Likes und Verlauf werden ausschließlich lokal in Room gespeichert
- Streams werden nicht gecacht, nur live gestreamt

## Lizenz

Privates Projekt.
