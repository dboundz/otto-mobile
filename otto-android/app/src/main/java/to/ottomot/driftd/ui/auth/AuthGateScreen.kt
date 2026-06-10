package to.ottomot.driftd.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.ottomot.driftd.PendingSquadInviteStore
import to.ottomot.driftd.R
import to.ottomot.driftd.core.network.InviteLinkParsing
import to.ottomot.driftd.core.auth.AuthFailure
import to.ottomot.driftd.core.auth.AuthRepository
import to.ottomot.driftd.core.auth.VerifyOtpOutcome

private const val PrivacyUrl = "https://driftd.com/privacy"
private const val TermsUrl = "https://driftd.com/tos"

/** Matches iOS `AuthScreen` — `Image("SignInLogo")` width 240, top padding 8, then 22pt to content. */
@Composable
private fun AuthBrandingHeader() {
    Image(
        painter = painterResource(R.drawable.sign_in_logo),
        contentDescription = stringResource(R.string.app_name),
        modifier =
            Modifier
                .width(240.dp)
                .padding(top = 8.dp),
        contentScale = ContentScale.Fit,
    )
    Spacer(Modifier.height(22.dp))
}

/**
 * Mirrors iOS [AuthScreen] routing: sign-in vs new-account display name (requiresOnboardingName).
 */
enum class AuthGateMode {
    SignIn,
    CompleteProfile,
}

private enum class SignInStep {
    PhoneAndCode,
    InviteCode,
    DisplayName,
}

@Composable
fun AuthGateScreen(
    repository: AuthRepository,
    mode: AuthGateMode,
    modifier: Modifier = Modifier,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    when (mode) {
        AuthGateMode.SignIn -> AuthSignInContent(repository, modifier, ioDispatcher)
        AuthGateMode.CompleteProfile -> AuthCompleteProfileContent(repository, modifier, ioDispatcher)
    }
}

@Composable
private fun AuthCompleteProfileContent(
    repository: AuthRepository,
    modifier: Modifier,
    ioDispatcher: CoroutineDispatcher,
) {
    val genericErrorFallback = stringResource(R.string.auth_error_unknown)
    var displayName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthBrandingHeader()
        Text(
            text = stringResource(R.string.auth_welcome_headline),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.auth_onboarding_name_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        Spacer(
            Modifier
                .height(5.dp)
                .width(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.height(22.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.auth_your_name_label)) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                        ),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        scope.launch {
                            busy = true
                            errorMessage = null
                            try {
                                withContext(ioDispatcher) {
                                    repository.completeOnboardingName(displayName)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AuthFailure) {
                                errorMessage = e.message ?: genericErrorFallback
                            } catch (t: Throwable) {
                                errorMessage = t.message ?: genericErrorFallback
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = displayName.isNotBlank() && !busy,
                ) {
                    Text(stringResource(R.string.auth_continue))
                }
            }
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                )
            }
        }

        if (busy) {
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        AuthLegalFooter(uriHandler)
    }
}

@Composable
private fun AuthSignInContent(
    repository: AuthRepository,
    modifier: Modifier,
    ioDispatcher: CoroutineDispatcher,
) {
    val genericErrorFallback = stringResource(R.string.auth_error_unknown)
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var awaitingCode by rememberSaveable { mutableStateOf(false) }
    var stepName by rememberSaveable { mutableStateOf(SignInStep.PhoneAndCode.name) }
    val step = SignInStep.values().find { it.name == stepName } ?: SignInStep.PhoneAndCode
    var signupChallengeToken by rememberSaveable { mutableStateOf("") }
    var signupNeedsInviteCode by rememberSaveable { mutableStateOf(false) }
    var inviteCodeText by rememberSaveable { mutableStateOf("") }
    var optionalSquadInviteText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        PendingSquadInviteStore.load(context)?.code?.let { saved ->
            if (inviteCodeText.isBlank()) {
                inviteCodeText = InviteLinkParsing.normalizeInviteToken(saved).uppercase()
            }
        }
    }

    LaunchedEffect(step, signupNeedsInviteCode) {
        if (step == SignInStep.DisplayName && !signupNeedsInviteCode) {
            PendingSquadInviteStore.load(context)?.code?.let { saved ->
                if (optionalSquadInviteText.isBlank()) {
                    optionalSquadInviteText = InviteLinkParsing.normalizeInviteToken(saved).uppercase()
                }
            }
        }
    }

    var signupDisplayName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    val bodyRes =
        when (step) {
            SignInStep.PhoneAndCode ->
                if (awaitingCode) {
                    R.string.auth_code_step_body
                } else {
                    R.string.auth_welcome_body
                }
            SignInStep.InviteCode -> R.string.auth_invite_step_body
            SignInStep.DisplayName -> R.string.auth_display_name_step_body
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthBrandingHeader()
        Text(
            text = stringResource(R.string.auth_welcome_headline),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        Spacer(
            Modifier
                .height(5.dp)
                .width(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.height(22.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                when (step) {
                    SignInStep.PhoneAndCode -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = phone,
                            onValueChange = {
                                phone = it
                                errorMessage = null
                            },
                            enabled = !busy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.auth_phone_label)) },
                            placeholder = { Text(stringResource(R.string.auth_phone_hint)) },
                            keyboardOptions =
                                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone),
                        )

                        if (!awaitingCode) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    scope.launch {
                                        busy = true
                                        errorMessage = null
                                        try {
                                            withContext(ioDispatcher) {
                                                repository.requestOtp(phone)
                                            }
                                            awaitingCode = true
                                            code = ""
                                            stepName = SignInStep.PhoneAndCode.name
                                            signupChallengeToken = ""
                                            signupNeedsInviteCode = false
                                            inviteCodeText = ""
                                            optionalSquadInviteText = ""
                                            signupDisplayName = ""
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: AuthFailure) {
                                            errorMessage = e.message ?: genericErrorFallback
                                        } catch (t: Throwable) {
                                            errorMessage = t.message ?: genericErrorFallback
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = phone.isNotBlank() && !busy,
                            ) {
                                Text(stringResource(R.string.auth_send_code))
                            }
                        } else {
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = code,
                                onValueChange = {
                                    code = it
                                    errorMessage = null
                                },
                                enabled = !busy,
                                singleLine = true,
                                label = { Text(stringResource(R.string.auth_code_label)) },
                                placeholder = { Text(stringResource(R.string.auth_code_hint)) },
                                keyboardOptions =
                                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                            )

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    scope.launch {
                                        busy = true
                                        errorMessage = null
                                        try {
                                            val outcome =
                                                withContext(ioDispatcher) {
                                                    repository.verifyOtp(phone, code)
                                                }
                                            when (outcome) {
                                                is VerifyOtpOutcome.SignedIn -> Unit
                                                is VerifyOtpOutcome.SignupChallenge -> {
                                                    signupChallengeToken = outcome.signupChallengeToken
                                                    signupNeedsInviteCode = outcome.needsInviteCode
                                                    stepName =
                                                        if (outcome.needsInviteCode) {
                                                            SignInStep.InviteCode.name
                                                        } else {
                                                            SignInStep.DisplayName.name
                                                        }
                                                    awaitingCode = false
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: AuthFailure) {
                                            errorMessage = e.message ?: genericErrorFallback
                                        } catch (t: Throwable) {
                                            errorMessage = t.message ?: genericErrorFallback
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = phone.isNotBlank() && code.isNotBlank() && !busy,
                            ) {
                                Text(stringResource(R.string.auth_verify_sign_in))
                            }

                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    awaitingCode = false
                                    code = ""
                                },
                                modifier = Modifier.align(Alignment.End),
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.auth_change_number))
                            }
                        }
                    }

                    SignInStep.InviteCode -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = inviteCodeText,
                            onValueChange = {
                                inviteCodeText = it.uppercase()
                                errorMessage = null
                            },
                            enabled = !busy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.auth_invite_label)) },
                            keyboardOptions =
                                KeyboardOptions.Default.copy(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.Characters,
                                ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (inviteCodeText.isBlank()) {
                                    errorMessage = context.getString(R.string.auth_invite_step_body)
                                    return@Button
                                }
                                scope.launch {
                                    busy = true
                                    errorMessage = null
                                    try {
                                        withContext(ioDispatcher) {
                                            repository.checkSignupInvite(
                                                signupChallengeToken = signupChallengeToken,
                                                inviteCode =
                                                    InviteLinkParsing.normalizeInviteToken(inviteCodeText),
                                            )
                                        }
                                        stepName = SignInStep.DisplayName.name
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: AuthFailure) {
                                        errorMessage = e.message ?: genericErrorFallback
                                    } catch (t: Throwable) {
                                        errorMessage = t.message ?: genericErrorFallback
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = inviteCodeText.isNotBlank() && !busy,
                        ) {
                            Text(stringResource(R.string.auth_continue))
                        }
                    }

                    SignInStep.DisplayName -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = signupDisplayName,
                            onValueChange = {
                                signupDisplayName = it
                                errorMessage = null
                            },
                            enabled = !busy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.auth_your_name_label)) },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Words,
                                ),
                        )
                        if (!signupNeedsInviteCode) {
                            Spacer(Modifier.height(20.dp))
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = optionalSquadInviteText,
                                onValueChange = {
                                    optionalSquadInviteText = it.uppercase()
                                    errorMessage = null
                                },
                                enabled = !busy,
                                singleLine = true,
                                label = { Text(stringResource(R.string.auth_squad_invite_optional_label)) },
                                placeholder = { Text(stringResource(R.string.auth_squad_invite_optional_hint)) },
                                keyboardOptions =
                                    KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Ascii,
                                        capitalization = KeyboardCapitalization.Characters,
                                    ),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                scope.launch {
                                    busy = true
                                    errorMessage = null
                                    try {
                                        if (signupNeedsInviteCode && inviteCodeText.isBlank()) {
                                            errorMessage =
                                                context.getString(R.string.auth_invite_step_body)
                                            stepName = SignInStep.InviteCode.name
                                            return@launch
                                        }
                                        val pendingInvite = PendingSquadInviteStore.load(context)
                                        val optionalNormalized =
                                            InviteLinkParsing.normalizeInviteToken(optionalSquadInviteText)
                                        val pendingNormalized =
                                            pendingInvite?.code
                                                ?.let { InviteLinkParsing.normalizeInviteToken(it) }
                                                .orEmpty()
                                        val inviteToSend =
                                            when {
                                                signupNeedsInviteCode ->
                                                    InviteLinkParsing.normalizeInviteToken(inviteCodeText)
                                                optionalNormalized.isNotEmpty() -> optionalNormalized
                                                pendingNormalized.isNotEmpty() -> pendingNormalized
                                                else -> null
                                            }
                                        if (!signupNeedsInviteCode && !inviteToSend.isNullOrBlank()) {
                                            PendingSquadInviteStore.persist(
                                                context,
                                                inviteToSend,
                                                pendingInvite?.squadId,
                                            )
                                        }
                                        withContext(ioDispatcher) {
                                            repository.completeSignup(
                                                signupChallengeToken = signupChallengeToken,
                                                displayName = signupDisplayName,
                                                inviteCode = inviteToSend,
                                            )
                                        }
                                        if (!inviteToSend.isNullOrBlank()) {
                                            PendingSquadInviteStore.clear(context)
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: AuthFailure) {
                                        errorMessage = e.message ?: genericErrorFallback
                                    } catch (t: Throwable) {
                                        errorMessage = t.message ?: genericErrorFallback
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled =
                                signupDisplayName.isNotBlank() &&
                                    signupChallengeToken.isNotBlank() &&
                                    !busy,
                        ) {
                            Text(stringResource(R.string.auth_join_otto))
                        }
                    }
                }
            }
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                )
            }
        }

        if (busy) {
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        AuthLegalFooter(uriHandler)
    }
}

@Composable
private fun AuthLegalFooter(uriHandler: UriHandler) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { uriHandler.openUri(PrivacyUrl) }) {
            Text(
                stringResource(R.string.auth_privacy_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.auth_legal_separator),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uriHandler.openUri(TermsUrl) }) {
            Text(
                stringResource(R.string.auth_terms_of_use),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
