package xyz.mdhv.riverwip.data.net

/**
 * Kotlin/Native (Ubuntu Touch, iOS) has no HTTP transport yet: it arrives with those
 * ports (libcurl on Linux, NSURLSession on iOS). Failing loudly here beats a build that
 * appears to work and silently never fetches a feed.
 */
actual fun openHttp(request: RawRequest): RawResponse =
    throw UnsupportedOperationException("No HTTP transport on this platform yet (requested ${request.url})")
