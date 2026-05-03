# 🎵 YuneMusic – Android App Konzept
**Für Claude Code Agent: Vollständige Bauanleitung**

> **Ansatz:** 100% anonym, kein API-Key, kein Google-Login.  
> Alle YouTube-Daten werden über **NewPipe Extractor** abgerufen – genau wie ein Browser.

---

## 1. Projektübersicht

**App-Name:** YuneMusic  
**Plattform:** Android (minSdkVersion 26 / Android 8.0+)  
**Sprache:** Kotlin  
**Build-Tool:** Gradle (Android Studio Projekt)  
**Ziel:** Werbefreier YouTube-Musikplayer, anonym, mit KI-Geschmacksanalyse, Hintergrundwiedergabe und autofreundlichem UI. Kein API-Key, kein Login erforderlich.

---

## 2. Kernfunktionen

### 2.1 YouTube-Musikwiedergabe (werbefrei, anonym)
- **NewPipe Extractor** ersetzt die YouTube API vollständig
- Liefert: Suchergebnisse, verwandte Videos, Streams, Thumbnails, Metadaten
- Nur Audio abspielen (kein Video) → spart Daten, ideal im Auto
- Keine Werbung, da direkter Stream-Zugriff ohne YouTube-App

### 2.2 Hintergrundwiedergabe
- Android **Foreground Service** mit `MediaSession` API
- Persistente Benachrichtigung mit Play/Pause/Skip-Steuerung
- Integration mit Android Auto (Media Browser Service)
- Bluetooth-Steuerung (Headset-Tasten, Lenkradtasten)
- Unterbrechungsbehandlung: Anrufe pausieren Musik automatisch (Audio Focus)

### 2.3 KI-Musikgeschmack-Analyse (lokal, anonym)
- Kein Cloud-Dienst – alles läuft auf dem Gerät
- **Datenpunkte die gesammelt werden:**
  - Wiedergabedauer pro Track (>70% gehört = starkes positives Signal)
  - Manuelle Likes/Dislikes
  - Tageszeit der Wiedergabe
  - Häufig gehörte Genres/Künstler
  - Skip-Zeitpunkt (früh übersprungen = negatives Signal)
- **Empfehlungs-Engine:**
  - Analysiert Muster nach je 20 Plays
  - Erstellt persönliches "Taste Profile" (lokal in Room gespeichert)
  - Nutzt NewPipe Extractor für ähnliche Tracks (Related Streams)
  - Gewichtet Ergebnisse anhand des Taste Profiles

### 2.4 Autofreundliches UI (Car Mode)
- Großer zentraler Play/Pause-Button (min. 80dp)
- Nur 3–4 sichtbare Elemente gleichzeitig
- Hoher Kontrast, dunkles OLED-Theme
- Wischgesten für Skip/Zurück
- Automatische Aktivierung wenn bekanntes Bluetooth-Gerät (Auto) verbunden wird

---

## 3. Technischer Stack

```
App
├── Kotlin + Coroutines + Flow
├── Jetpack Compose (UI, Material3, Dark Theme)
├── ExoPlayer (Media3) → Audio-Wiedergabe
├── NewPipe Extractor → YouTube-Daten & Streams (anonym, kein Key)
├── OkHttp → HTTP-Client für NewPipe Extractor
├── Room Database → lokaler Verlauf, Taste Profile, Likes
├── DataStore → App-Einstellungen
├── Hilt → Dependency Injection
├── Coil → Thumbnail-Laden
└── WorkManager → Hintergrundanalyse & Playlist-Vorbereitung
```

**Kein Retrofit, kein API-Key, kein Login nötig.**

---

## 4. NewPipe Extractor – Zentrale Bibliothek

### 4.1 Dependency (build.gradle.kts)
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0.24.0")
    // Rhino JS Engine (wird von NewPipe Extractor intern gebraucht)
    implementation("org.mozilla:rhino:1.7.15")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### 4.2 Initialisierung (einmalig beim App-Start)
```kotlin
// In Application.kt
class YuneMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl.getInstance())
    }
}

// DownloaderImpl – OkHttp-basierter HTTP-Client für NewPipe
class DownloaderImpl private constructor(
    private val client: OkHttpClient
) : Downloader() {
    override fun execute(request: Request): Response {
        // Standard OkHttp Request → NewPipe Response umwandeln
    }
    companion object {
        fun getInstance() = DownloaderImpl(OkHttpClient.Builder().build())
    }
}
```

### 4.3 Suche und Streams
```kotlin
class YouTubeRepository {

    // Musik suchen
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            null
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toTrack() }
    }

    // Verwandte Videos (für Empfehlungen)
    suspend fun getRelatedTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo("https://youtube.com/watch?v=$videoId")
        streamInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toTrack() }
    }

    // Audio-Stream-URL holen (für ExoPlayer)
    suspend fun getAudioStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo("https://youtube.com/watch?v=$videoId")
        streamInfo.audioStreams
            .sortedByDescending { it.averageBitrate }
            .first()
            .url
    }

    // Trending Musik (ohne Key!)
    suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        val kiosk = YouTube.getKioskList()
        val trending = kiosk.getExtractorById("Trending", null)
        trending.fetchPage()
        trending.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toTrack() }
    }
}

// Extension: NewPipe StreamInfoItem → eigenes Track-Model
fun StreamInfoItem.toTrack() = Track(
    id = url.substringAfter("v=").substringBefore("&"),
    title = name,
    channelName = uploaderName,
    thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
    durationSeconds = duration.toInt()
)
```

---

## 5. Architektur

```
MVVM + Clean Architecture

ui/
  ├── player/         PlayerScreen, PlayerViewModel
  ├── discover/       DiscoverScreen, DiscoverViewModel
  ├── library/        LibraryScreen, LibraryViewModel
  ├── carmode/        CarModeScreen, CarModeViewModel
  └── settings/       SettingsScreen

domain/
  ├── model/          Track, Playlist, TasteProfile, PlayEvent
  ├── usecase/        PlayTrackUseCase, GetRecommendationsUseCase,
  │                   SearchTracksUseCase, UpdateTasteProfileUseCase
  └── repository/     MusicRepository (Interface)

data/
  ├── youtube/        YouTubeRepository (NewPipe Extractor)
  │                   DownloaderImpl (OkHttp Bridge)
  ├── local/          AppDatabase, TrackDao, PlayEventDao,
  │                   TasteProfileDao, LikedTrackDao
  └── repository/     MusicRepositoryImpl

service/
  ├── MusicService.kt    (Foreground Service + MediaSession)
  └── TasteAnalyzer.kt   (WorkManager Background Job)
```

---

## 6. Screens / Navigation

### Screen 1: Home / Discover
- "Für dich" Sektion mit KI-Empfehlungen (Taste Profile)
- "Trending" – aktuell beliebte Musik via NewPipe Kiosk
- "Weiterhören" – zuletzt gehörte Tracks
- Suchfeld oben
- Kategorie-Chips: Rock, Electronic, Jazz, Hip-Hop, etc.

### Screen 2: Player
- Thumbnail groß
- Songname + Kanal
- Progress Bar mit Zeitanzeige
- Play/Pause, Vor/Zurück, Like-Button, Shuffle
- Mini-Queue (nächste 3 Songs)

### Screen 3: Car Mode
- Vollbild, tief dunkel
- Nur: Thumbnail, Titel, großer Play-Button, Skip
- Wischgeste rechts = Skip, links = Zurück

### Screen 4: Bibliothek
- Gelikte Songs
- Zuletzt gehört
- Auto-Playlists: "Dein Mix", "Neue Entdeckungen", "Energie-Boost"

### Screen 5: Einstellungen
- Audio-Qualität (niedrig / mittel / hoch)
- Datenmodus (WLAN only / immer)
- Car Mode: Bluetooth-Gerät auswählen
- Taste Profile zurücksetzen
- Cache leeren

---

## 7. Datenmodell (Room)

```kotlin
@Entity
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val genre: String? = null
)

@Entity
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val trackId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playedSeconds: Int,
    val totalSeconds: Int,
    val skipped: Boolean,
    val liked: Boolean
)

@Entity
data class TasteProfile(
    @PrimaryKey val id: Int = 1,
    val favoriteArtistsJson: String,   // JSON Map<String, Float>
    val favoriteGenresJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity
data class LikedTrack(
    @PrimaryKey val trackId: String,
    val likedAt: Long = System.currentTimeMillis()
)
```

---

## 8. KI-Empfehlungs-Logik

```kotlin
class TasteAnalyzer(private val db: AppDatabase, private val repo: YouTubeRepository) {

    suspend fun recordPlayEvent(event: PlayEvent) {
        db.playEventDao().insert(event)
        if (db.playEventDao().getCount() % 20 == 0) updateTasteProfile()
    }

    private suspend fun updateTasteProfile() {
        val events = db.playEventDao().getRecent(100)
        val completionByTrack = events.groupBy { it.trackId }
            .mapValues { (_, evs) -> evs.map { it.playedSeconds.toFloat() / it.totalSeconds }.average() }

        val artistScores = events
            .filter { (completionByTrack[it.trackId] ?: 0.0) > 0.7 }
            .groupBy { db.trackDao().getById(it.trackId)?.channelName ?: "Unknown" }
            .mapValues { it.value.size.toFloat() }
            .normalized()

        db.tasteProfileDao().upsert(
            TasteProfile(
                favoriteArtistsJson = artistScores.toJson(),
                favoriteGenresJson = "{}"
            )
        )
    }

    suspend fun getRecommendations(): List<Track> {
        val profile = db.tasteProfileDao().get() ?: return repo.getTrending().take(20)
        val topArtist = profile.favoriteArtists.maxByOrNull { it.value }?.key
        val lastTrack = db.playEventDao().getLatest()

        val related = lastTrack?.let { repo.getRelatedTracks(it.trackId) } ?: emptyList()
        val byArtist = if (topArtist != null) repo.search("$topArtist music") else emptyList()

        val played = db.playEventDao().getAllTrackIds().toSet()
        return (related + byArtist)
            .distinctBy { it.id }
            .filter { it.id !in played }
            .shuffled()
            .take(20)
    }
}
```

---

## 9. Foreground Service

```kotlin
@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSessionCompat(this, "YuneMusic").apply {
            setCallback(MediaSessionCallback())
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun playTrack(track: Track, streamUrl: String) {
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
        updateNotification(track)
    }

    // Audio Focus: Anruf → Pause, danach → Play
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        when (focus) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player.pause()
            AudioManager.AUDIOFOCUS_GAIN -> player.play()
        }
    }

    // Android Auto
    override fun onGetRoot(pkg: String, uid: Int, hints: Bundle?) =
        BrowserRoot(MEDIA_ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        result.detach()
        // Liked Songs + letzte Tracks für Android Auto laden
    }
}
```

---

## 10. Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>

<application>
    <service
        android:name=".service.MusicService"
        android:foregroundServiceType="mediaPlayback"
        android:exported="true">
        <intent-filter>
            <action android:name="android.media.browse.MediaBrowserService"/>
        </intent-filter>
    </service>
</application>
```

---

## 11. Alle Abhängigkeiten (build.gradle.kts)

```kotlin
dependencies {
    // UI
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Media
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media:media:1.7.0")

    // NewPipe Extractor (YouTube anonym, kein Key)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0.24.0")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Datenbank
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // Async
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Einstellungen & Bilder
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Background Work
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```

---

## 12. Projektstruktur

```
app/
├── src/main/
│   ├── java/com/yunemusic/
│   │   ├── YuneMusicApp.kt
│   │   ├── ui/
│   │   │   ├── player/
│   │   │   ├── discover/
│   │   │   ├── library/
│   │   │   ├── carmode/
│   │   │   └── settings/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   ├── usecase/
│   │   │   └── repository/
│   │   ├── data/
│   │   │   ├── youtube/
│   │   │   │   ├── YouTubeRepository.kt
│   │   │   │   └── DownloaderImpl.kt
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── TrackDao.kt
│   │   │   │   ├── PlayEventDao.kt
│   │   │   │   ├── TasteProfileDao.kt
│   │   │   │   └── LikedTrackDao.kt
│   │   │   └── repository/
│   │   │       └── MusicRepositoryImpl.kt
│   │   ├── service/
│   │   │   ├── MusicService.kt
│   │   │   └── TasteAnalyzer.kt
│   │   └── di/
│   │       └── AppModule.kt
│   └── res/
├── build.gradle.kts
├── proguard-rules.pro
└── AndroidManifest.xml
```

---

## 13. Bauplan (Phasen)

**Phase 1 – Grundgerüst**
1. Android Projekt anlegen (Kotlin, Compose, MVVM, Hilt)
2. NewPipe Extractor einbinden + DownloaderImpl implementieren
3. YouTubeRepository: search(), getRelatedTracks(), getAudioStreamUrl(), getTrending()
4. ExoPlayer + MusicService als Foreground Service mit MediaSession
5. Basis-Player-UI: Play/Pause, Skip, Titel, Thumbnail

**Phase 2 – Kernfeatures**
6. Room Datenbank + alle DAOs
7. PlayEvent-Tracking nach jedem Track
8. TasteAnalyzer mit Taste Profile Berechnung
9. Discover-Screen: Trending + KI-Empfehlungen
10. Car Mode Screen

**Phase 3 – Polish**
11. Bibliothek: Likes, Verlauf, Auto-Playlists
12. Einstellungen: Qualität, Datenmodus, Car Mode Bluetooth-Trigger
13. Bluetooth-Auto-Detection
14. Android Auto (MediaBrowserService)
15. Audio Focus (Anruf-Unterbrechung)

---

## 14. Wichtige Hinweise für Claude Code

- **Kein API-Key, kein Login** – App funktioniert sofort nach Installation
- **NewPipe Version:** Neueste von JitPack prüfen: https://jitpack.io/#TeamNewPipe/NewPipeExtractor
- **DownloaderImpl** sorgfältig implementieren – Brücke zwischen NewPipe und OkHttp
- **Rhino Engine** muss als Dependency mit rein (für JS-Parsing intern)
- **Streams nicht auf Disk cachen** – nur live streamen
- **Dark Theme** Pflicht (OLED, Blendschutz im Auto)
- **minSdkVersion = 26**, **targetSdkVersion = 34**
- **ProGuard-Regeln** in proguard-rules.pro:
  ```
  -keep class org.schabi.newpipe.extractor.** { *; }
  -dontwarn org.schabi.newpipe.extractor.**
  -keep class org.mozilla.javascript.** { *; }
  -dontwarn org.mozilla.javascript.**
  ```

---

## 15. Prompt für Claude Code (direkt verwendbar)

```
Baue eine Android-App namens "YuneMusic" gemäß dem Konzeptdokument YoutubeMusicApp_Konzept.md.

Die App nutzt NewPipe Extractor statt der YouTube Data API – kein API-Key, kein Login nötig.

Starte mit Phase 1:
1. Erstelle ein vollständiges Android-Kotlin-Projekt mit Jetpack Compose und MVVM-Architektur
2. Binde NewPipe Extractor (via JitPack) ein und implementiere DownloaderImpl mit OkHttp
3. Implementiere YouTubeRepository: search(), getRelatedTracks(), getAudioStreamUrl(), getTrending()
4. Implementiere ExoPlayer (Media3) + MusicService als Foreground Service mit MediaSession
5. Baue Player-Screen und Discover-Screen (Trending + Suche)

Verwende:
- Kotlin + Coroutines + Flow
- Jetpack Compose (Material3, Dark Theme)
- ExoPlayer / Media3
- NewPipe Extractor (JitPack, Version 0.24.0)
- Hilt für DI
- Room für lokale Daten (Track, PlayEvent, TasteProfile, LikedTrack)

ProGuard-Regeln für NewPipe und Rhino beachten (siehe Abschnitt 14).
Das Projekt soll direkt mit Android Studio baubar sein.
```
