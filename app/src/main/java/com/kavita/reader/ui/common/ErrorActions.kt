package com.kavita.reader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kavita.reader.R
import com.kavita.reader.core.network.KavitaError

/**
 * The action a full-screen error state should offer.
 *
 * A 401 must not offer "retry": retrying with the same dead token can only fail again, which is
 * precisely the dead end users hit after a couple of days. It offers "sign in again" instead,
 * taking them to the login form with the account already filled in.
 */
sealed interface ErrorAction {
    /** Same request, second attempt. */
    data class Retry(val label: String) : ErrorAction

    /** Re-authenticate for the active server. */
    data class SignInAgain(val label: String) : ErrorAction
}

@Composable
fun KavitaError.action(): ErrorAction = when (this) {
    is KavitaError.Unauthorized ->
        ErrorAction.SignInAgain(stringResource(R.string.session_expired_sign_in))

    else -> ErrorAction.Retry(stringResource(R.string.common_retry))
}

/** True when this failure needs a new credential rather than another attempt. */
val KavitaError.needsSignIn: Boolean
    get() = this is KavitaError.Unauthorized

/**
 * Navigation hook for "sign in again", provided once by the nav host.
 *
 * A screen must not have to know about the server-picker route just to render an error.
 */
val LocalSignInAgain = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The only full-screen error state screens should render.
 *
 * Picks the right action from the failure: retry for transient errors, "sign in again" for a dead
 * token. Using [com.kavita.reader.core.designsystem.ErrorState] directly is how every screen ended
 * up offering a retry that could only fail.
 */
@Composable
fun KavitaErrorState(
    error: KavitaError,
    onRetry: () -> Unit,
) {
    val signInAgain = LocalSignInAgain.current
    when (val action = error.action()) {
        is ErrorAction.SignInAgain -> com.kavita.reader.core.designsystem.ErrorState(
            message = error.message(),
            retryLabel = action.label,
            onRetry = signInAgain ?: onRetry,
            illustration = error.illustration,
        )

        is ErrorAction.Retry -> com.kavita.reader.core.designsystem.ErrorState(
            message = error.message(),
            retryLabel = action.label,
            onRetry = onRetry,
            illustration = error.illustration,
        )
    }
}
