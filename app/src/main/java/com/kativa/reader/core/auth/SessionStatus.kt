package com.kativa.reader.core.auth

/**
 * Whether a stored session can still be used.
 *
 * The distinction matters on the server picker: a *present* session that has expired must NOT be
 * treated as "signed in", otherwise tapping the server skips the login screen and lands on a home
 * screen that can only show "session expired" — a loop with no exit short of clearing app data.
 */
enum class SessionStatus {
    /** No token stored: the user has never signed in here, or explicitly signed out. */
    Missing,

    /** A token is stored but its `exp` is (about to be) in the past: re-login required. */
    Expired,

    /** A token is stored and still usable. */
    Valid,
}

/**
 * Classifies this session.
 *
 * [skewSeconds] treats a soon-to-expire token as expired so the picker never advertises a session
 * that would die in the middle of the next screen.
 */
fun Session?.status(
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    skewSeconds: Long = 0,
): SessionStatus = when {
    this == null -> SessionStatus.Missing
    JwtExpiry.isExpired(token, nowEpochSeconds, skewSeconds) -> SessionStatus.Expired
    else -> SessionStatus.Valid
}
