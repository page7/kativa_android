package com.kativa.reader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kativa.reader.R
import com.kativa.reader.core.network.KativaError

/** Single place that turns a [KativaError] into user-facing copy (four locales). */
@Composable
fun KativaError.message(): String = when (this) {
    KativaError.Network -> stringResource(R.string.error_network)
    KativaError.Timeout -> stringResource(R.string.error_timeout)
    KativaError.Dns -> stringResource(R.string.error_dns)
    KativaError.NotKavita -> stringResource(R.string.error_not_kavita)
    KativaError.CleartextBlocked -> stringResource(R.string.error_cleartext_blocked)
    KativaError.Parse -> stringResource(R.string.error_parse)
    KativaError.Unknown -> stringResource(R.string.error_unknown)
    is KativaError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is KativaError.Forbidden -> stringResource(R.string.error_forbidden)
    is KativaError.NotFound -> stringResource(R.string.error_not_found)
    is KativaError.BadRequest -> serverMessage ?: stringResource(R.string.error_unknown)
    is KativaError.Server -> stringResource(R.string.error_server, status)
}

@Composable
fun KativaError.loginMessage(): String = when (this) {
    is KativaError.Unauthorized -> when {
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
