package com.aeriotv.android.core.pip

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Process-wide now-playing labels for the background media notification
 * ([com.aeriotv.android.core.playback.AerioMediaPlaybackService]). Written by
 * the live player and Radio Mode. (The PiP state that used to live here went
 * with the phone builds: Android TV never enters Picture-in-Picture.)
 */
object PipState {
    @Volatile var nowPlayingTitle: String = "AerioTV"
    @Volatile var nowPlayingSubtitle: String = ""

    /** Now-playing channel logo URL, shown as the notification's large icon. */
    @Volatile var nowPlayingLogo: String? = null
}

/** Walk a Compose context chain to the host Activity. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
