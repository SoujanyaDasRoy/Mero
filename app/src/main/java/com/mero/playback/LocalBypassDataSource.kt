package com.mero.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends files on the phone straight to disk and everything else through the
 * cache, choosing when the stream is opened.
 *
 * ExoPlayer takes one [DataSource.Factory] for the whole player, and a data
 * source only learns what it is reading at [open] — so the choice has to be
 * made here rather than when the factory is built.
 */
@UnstableApi
class LocalBypassDataSource(
    private val direct: DataSource,
    private val cached: DataSource,
) : DataSource {

    private var active: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme in LOCAL_SCHEMES) direct else cached
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        direct.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
    }

    private companion object {
        // content:// only — what the file picker and "Open with" hand over.
        // Nothing Mero stores is file://, and the podcast parser refuses it,
        // so there is no legitimate reason to open one.
        val LOCAL_SCHEMES = setOf("content")
    }
}
