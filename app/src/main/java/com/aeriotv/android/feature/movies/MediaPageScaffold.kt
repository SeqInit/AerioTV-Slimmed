package com.aeriotv.android.feature.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** A full-width row placed between the decks and the library header. */
class PageRow(val key: String, val content: @Composable () -> Unit)

