package dev.droidtop.shared.net

import java.io.IOException

/**
 * GitHub (or the CDN behind it) answered with a status that is not a success.
 *
 * The rate-limit headers are the difference between "you have asked too often" and "this
 * repository will not answer you"; without them a screen can only say the request failed.
 */
class GitHubHttpException(
    val status: Int,
    val rateLimitRemaining: Int?,
    val rateLimitResetEpochSeconds: Long?,
    message: String,
) : IOException(message)
