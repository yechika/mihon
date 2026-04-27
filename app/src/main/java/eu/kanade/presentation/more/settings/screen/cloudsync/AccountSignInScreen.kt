package eu.kanade.presentation.more.settings.screen.cloudsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.cloudsync.AccountManager
import eu.kanade.tachiyomi.data.cloudsync.SignInResult
import eu.kanade.tachiyomi.data.cloudsync.SignUpResult
import eu.kanade.tachiyomi.data.cloudsync.UsernameValidator
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AccountSignInScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val model = rememberScreenModel { AccountSignInScreenModel() }
        val state by model.state.collectAsState()
        var showNoEmailWarning by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_cloud_sync),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabRow(selectedTabIndex = state.tabIndex) {
                    Tab(
                        selected = state.tabIndex == 0,
                        onClick = { model.selectTab(0) },
                        text = { Text(stringResource(MR.strings.action_sign_in)) },
                    )
                    Tab(
                        selected = state.tabIndex == 1,
                        onClick = { model.selectTab(1) },
                        text = { Text(stringResource(MR.strings.action_sign_up)) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (state.tabIndex == 0) {
                    SignInForm(
                        state = state,
                        onUsername = model::onUsername,
                        onPassword = model::onPassword,
                        onSubmit = {
                            scope.launch {
                                val msg = when (val result = model.signIn()) {
                                    is SignInResult.Success -> null
                                    SignInResult.IncorrectCredentials -> context.contextStringResource(MR.strings.cloud_sync_signin_failed)
                                    SignInResult.Unavailable -> context.contextStringResource(MR.strings.pref_cloud_sync_unavailable)
                                    is SignInResult.NetworkError -> result.message
                                }
                                if (msg == null) {
                                    navigator.pop()
                                } else {
                                    context.toast(msg)
                                }
                            }
                        },
                    )
                } else {
                    SignUpForm(
                        state = state,
                        onUsername = model::onUsername,
                        onPassword = model::onPassword,
                        onConfirmPassword = model::onConfirmPassword,
                        onEmail = model::onEmail,
                        onSubmit = {
                            if (state.email.isBlank()) {
                                showNoEmailWarning = true
                            } else {
                                scope.launch { handleSignUpResult(model, navigator, context) }
                            }
                        },
                    )
                }
            }
        }

        if (showNoEmailWarning) {
            NoEmailWarningDialog(
                onConfirmContinue = {
                    showNoEmailWarning = false
                    scope.launch { handleSignUpResult(model, navigator, context) }
                },
                onAddEmail = {
                    showNoEmailWarning = false
                },
            )
        }
    }

    private suspend fun handleSignUpResult(
        model: AccountSignInScreenModel,
        navigator: cafe.adriel.voyager.navigator.Navigator,
        context: android.content.Context,
    ) {
        val msg = when (val result = model.signUp()) {
            is SignUpResult.Success -> null
            SignUpResult.UsernameTaken -> "Username already taken"
            SignUpResult.UsernameInvalid -> "Invalid username"
            SignUpResult.PasswordTooShort -> "Password must be at least 8 characters"
            SignUpResult.PasswordTooLong -> "Password must be 64 characters or fewer"
            SignUpResult.EmailInvalid -> "Email is not valid"
            SignUpResult.Unavailable -> "Cloud sync unavailable in this build"
            is SignUpResult.NetworkError -> result.message
        }
        if (msg == null) {
            navigator.pop()
        } else {
            context.toast(msg)
        }
    }
}

@Composable
private fun SignInForm(
    state: AccountFormState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = state.username,
        onValueChange = onUsername,
        label = { Text(stringResource(MR.strings.cloud_sync_field_username)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = onPassword,
        label = { Text(stringResource(MR.strings.cloud_sync_field_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSubmit,
        enabled = state.canSubmitSignIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(MR.strings.action_sign_in))
    }
}

@Composable
private fun SignUpForm(
    state: AccountFormState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = state.username,
        onValueChange = onUsername,
        label = { Text(stringResource(MR.strings.cloud_sync_field_username)) },
        supportingText = { Text(stringResource(MR.strings.cloud_sync_field_username_help)) },
        isError = state.usernameError,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = onPassword,
        label = { Text(stringResource(MR.strings.cloud_sync_field_password)) },
        supportingText = {
            Text("At least ${AccountFormState.MIN_PASSWORD} characters (${state.password.length} so far)")
        },
        isError = state.passwordTooShortVisible,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.confirmPassword,
        onValueChange = onConfirmPassword,
        label = { Text(stringResource(MR.strings.cloud_sync_field_confirm_password)) },
        supportingText = if (state.confirmPasswordError) {
            { Text("Passwords do not match") }
        } else null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = state.confirmPasswordError,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmail,
        label = { Text(stringResource(MR.strings.cloud_sync_field_email_optional)) },
        supportingText = { Text(stringResource(MR.strings.cloud_sync_field_email_help)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSubmit,
        enabled = state.canSubmitSignUp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(MR.strings.action_sign_up))
    }
}

@Composable
private fun NoEmailWarningDialog(
    onConfirmContinue: () -> Unit,
    onAddEmail: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* non-dismissible */ },
        title = { Text(stringResource(MR.strings.cloud_sync_no_email_warning_title)) },
        text = { Text(stringResource(MR.strings.cloud_sync_no_email_warning_body)) },
        confirmButton = {
            TextButton(onClick = onAddEmail) {
                Text(stringResource(MR.strings.cloud_sync_no_email_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmContinue) {
                Text(stringResource(MR.strings.cloud_sync_no_email_continue))
            }
        },
    )
}

data class AccountFormState(
    val tabIndex: Int = 0,
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val email: String = "",
    val isBusy: Boolean = false,
) {
    val usernameError: Boolean get() = username.isNotEmpty() && !UsernameValidator.isValid(username)
    val confirmPasswordError: Boolean get() = confirmPassword.isNotEmpty() && confirmPassword != password
    val passwordTooShortVisible: Boolean get() = password.isNotEmpty() && password.length < MIN_PASSWORD
    val canSubmitSignIn: Boolean
        get() = !isBusy && username.isNotBlank() && password.length >= MIN_PASSWORD
    val canSubmitSignUp: Boolean
        get() = !isBusy &&
            UsernameValidator.isValid(username) &&
            password.length in MIN_PASSWORD..MAX_PASSWORD &&
            confirmPassword == password

    companion object {
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 64
    }
}

class AccountSignInScreenModel(
    private val accountManager: AccountManager = Injekt.get(),
) : ScreenModel {

    private val mutableState = MutableStateFlow(AccountFormState())
    val state = mutableState.asStateFlow()

    fun selectTab(index: Int) = mutableState.update { it.copy(tabIndex = index) }
    fun onUsername(value: String) = mutableState.update { it.copy(username = value) }
    fun onPassword(value: String) = mutableState.update { it.copy(password = value) }
    fun onConfirmPassword(value: String) = mutableState.update { it.copy(confirmPassword = value) }
    fun onEmail(value: String) = mutableState.update { it.copy(email = value) }

    suspend fun signIn(): SignInResult {
        mutableState.update { it.copy(isBusy = true) }
        val s = mutableState.value
        val result = accountManager.signIn(s.username.trim(), s.password)
        mutableState.update { it.copy(isBusy = false) }
        return result
    }

    suspend fun signUp(): SignUpResult {
        mutableState.update { it.copy(isBusy = true) }
        val s = mutableState.value
        val email = s.email.takeIf { it.isNotBlank() }?.trim()
        val result = accountManager.signUp(s.username.trim(), s.password, email)
        mutableState.update { it.copy(isBusy = false) }
        return result
    }
}
