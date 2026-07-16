package com.neversoft.editor

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * App entry point. Configures Coil so the timeline can show a frame from a
 * video Uri (via [VideoFrameDecoder]) exactly the same way it shows a photo —
 * again without copying the source file.
 */
class NeverSoftApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
