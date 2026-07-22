package com.yunemusic.domain.model

/**
 * Repräsentiert den Zustand eines laufenden Downloads.
 * Wird als Flow emittiert für Echtzeit-Fortschrittsanzeige.
 */
sealed class DownloadState {
    /** Stream-URL wird von YouTube ermittelt */
    data object GettingUrl : DownloadState()

    /** Download läuft – progress: 0.0 bis 1.0 */
    data class Downloading(val progress: Float, val mbDownloaded: Float, val mbTotal: Float) : DownloadState()

    /** Download erfolgreich abgeschlossen */
    data class Completed(val filePath: String) : DownloadState()

    /** Download fehlgeschlagen */
    data class Failed(val error: String) : DownloadState()
}