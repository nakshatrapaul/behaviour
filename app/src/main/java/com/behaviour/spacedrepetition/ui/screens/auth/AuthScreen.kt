package com.behaviour.spacedrepetition.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behaviour.spacedrepetition.ui.theme.*
import androidx.compose.ui.draw.clip

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onAuthSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkSurface,
                        DarkBackground
                    )
                )
            )
    ) {
        if (uiState.isCheckingAuth || uiState.isLoggedIn) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.behaviour.spacedrepetition.R.mipmap.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(96.dp)
                        .background(androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(20.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Behave",
                    style = MaterialTheme.typography.displayLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fibonacci Spaced Repetition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(
                    color = PrimaryDark,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp)
                    .safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // App Logo / Title
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.behaviour.spacedrepetition.R.mipmap.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(96.dp)
                        .background(androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(20.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Behave",
                    style = MaterialTheme.typography.displayLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fibonacci Spaced Repetition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Mode Toggle
                Text(
                    text = if (uiState.isLoginMode) "Welcome Back" else "Create Account",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Name field (register only)
                AnimatedVisibility(
                    visible = !uiState.isLoginMode,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChanged,
                        label = { Text("Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = PrimaryDark,
                            unfocusedBorderColor = TextGray.copy(alpha = 0.3f),
                            focusedLabelColor = PrimaryDark,
                            unfocusedLabelColor = TextGray,
                            focusedLeadingIconColor = PrimaryDark,
                            unfocusedLeadingIconColor = TextGray,
                            cursorColor = PrimaryDark
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true
                    )
                }

                // Email field
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text("Email") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PrimaryDark,
                        unfocusedBorderColor = TextGray.copy(alpha = 0.3f),
                        focusedLabelColor = PrimaryDark,
                        unfocusedLabelColor = TextGray,
                        focusedLeadingIconColor = PrimaryDark,
                        unfocusedLeadingIconColor = TextGray,
                        cursorColor = PrimaryDark
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PrimaryDark,
                        unfocusedBorderColor = TextGray.copy(alpha = 0.3f),
                        focusedLabelColor = PrimaryDark,
                        unfocusedLabelColor = TextGray,
                        focusedLeadingIconColor = PrimaryDark,
                        unfocusedLeadingIconColor = TextGray,
                        focusedTrailingIconColor = PrimaryDark,
                        unfocusedTrailingIconColor = TextGray,
                        cursorColor = PrimaryDark
                    ),
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.submit()
                        }
                    ),
                    singleLine = true
                )

                // Terms & Privacy Checkbox (register only)
                AnimatedVisibility(
                    visible = !uiState.isLoginMode,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.acceptedTerms,
                            onCheckedChange = viewModel::onAcceptedTermsChanged,
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryDark,
                                uncheckedColor = TextGray.copy(alpha = 0.5f),
                                checkmarkColor = TextWhite
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val uriHandler = LocalUriHandler.current
                        val annotatedText = buildAnnotatedString {
                            append("I accept the ")
                            pushStringAnnotation(tag = "TERMS", annotation = "https://nakshatrapaul.github.io/behaviour/terms.html")
                            withStyle(style = SpanStyle(color = PrimaryVariant, fontWeight = FontWeight.Bold)) {
                                append("Terms of Service")
                            }
                            pop()
                            append(" and ")
                            pushStringAnnotation(tag = "PRIVACY", annotation = "https://nakshatrapaul.github.io/behaviour/privacy.html")
                            withStyle(style = SpanStyle(color = PrimaryVariant, fontWeight = FontWeight.Bold)) {
                                append("Privacy Policy")
                            }
                            pop()
                        }

                        ClickableText(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextGray),
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                                annotatedText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                    }
                }

                // Error message
                AnimatedVisibility(visible = uiState.error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submit button
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryDark
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (uiState.isLoginMode) "Sign In" else "Create Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle mode
                TextButton(onClick = viewModel::toggleMode) {
                    Text(
                        text = if (uiState.isLoginMode) "Don't have an account? Sign Up"
                        else "Already have an account? Sign In",
                        color = PrimaryVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
