package com.yunemusic.service

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore

internal const val EXTRA_CONTROL_TOKEN = "com.yunemusic.extra.CONTROL_TOKEN"

/** Only immutable PendingIntents minted by the live service carry its capability. */
internal fun isAuthorizedPlaybackCommand(intent: Intent?, token: String): Boolean =
    token.isNotEmpty() && runCatching {
        intent?.getStringExtra(EXTRA_CONTROL_TOKEN) == token
    }.getOrDefault(false)

internal fun voiceSearchQuery(intent: Intent): String? = runCatching {
    intent.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST),
            intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE))
            .joinToString(" ").trim().takeIf { it.isNotEmpty() }
}.getOrNull()
