package com.kavita.reader.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kavita.reader.R
import com.kavita.reader.core.network.KavitaError

/** Single place that turns a [KavitaError] into user-facing copy (four locales). */
@Composable
fun KavitaError.message(): String = when (this) {
    KavitaError.Network -> stringResource(R.string.error_network)
    KavitaError.Timeout -> stringResource(R.string.error_timeout)
    KavitaError.Dns -> stringResource(R.string.error_dns)
    KavitaError.NotKavita -> stringResource(R.string.error_not_kavita)
    KavitaError.CleartextBlocked -> stringResource(R.string.error_cleartext_blocked)
    KavitaError.Parse -> stringResource(R.string.error_parse)
    KavitaError.Unknown -> stringResource(R.string.error_unknown)
    is KavitaError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is KavitaError.Forbidden -> stringResource(R.string.error_forbidden)
    is KavitaError.NotFound -> stringResource(R.string.error_not_found)
    is KavitaError.BadRequest -> serverMessage ?: stringResource(R.string.error_unknown)
    is KavitaError.Server -> stringResource(R.string.error_server, status)
}

/**
 * Illustration that best explains this failure (docs/ASSETS.md §2.4).
 *
 * Connectivity failures get `empty_network` because the fix is "get back online"; everything else
 * is a genuine fault and gets the generic error art. Kept next to the copy so a new error variant
 * cannot silently ship without thinking about its artwork.
 */
@get:DrawableRes
val KavitaError.illustration: Int
    get() = when (this) {
        KavitaError.Network, KavitaError.Timeout, KavitaError.Dns, KavitaError.CleartextBlocked ->
            R.drawable.empty_network

        else -> R.drawable.error_generic
    }

@Composable
fun KavitaError.loginMessage(): String = when (this) {
    is KavitaError.Unauthorized -> when {
        serverMessage?.contains("disabled", ignoreCase = true) == true ->
            stringResource(R.string.login_error_disabled)

        serverMessage?.contains("lock", ignoreCase = true) == true ->
            stringResource(R.string.login_error_locked)

        serverMessage?.contains("password-authentication-disabled", ignoreCase = true) == true ->
            stringResource(R.string.login_error_password_disabled)

        else -> stringResource(R.string.login_error_credentials)
    }

    else -> message()
}
