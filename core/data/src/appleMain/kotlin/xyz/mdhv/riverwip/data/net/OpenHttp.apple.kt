package xyz.mdhv.riverwip.data.net

/**
 * Apple platforms have no HTTP transport yet: it arrives with the iOS port (NSURLSession).
 * Failing loudly here beats a build that appears to work and silently never fetches a feed.
 */
actual fun openHttp(request: RawRequest): RawResponse =
    throw UnsupportedOperationException("No HTTP transport on this platform yet (requested ${request.url})")
