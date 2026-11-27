package com.famitube.app.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.R
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthMode {
    CHILD_LOGIN,
    PARENT_LOGIN,
    PARENT_REGISTER
}

@Composable
fun LoginScreen(
    repository: FamiTubeRepository,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(AuthMode.CHILD_LOGIN) }

    // Form fields
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.oauthCallbackFlow.collect { uri ->
            val err = uri.getQueryParameter("error")
            val token = uri.getQueryParameter("token")
            if (!err.isNullOrBlank()) {
                isLoading = false
                errorMessage = err
            } else if (!token.isNullOrBlank()) {
                isLoading = true
                errorMessage = null
                val result = repository.completeGoogleOAuth(token)
                isLoading = false
                result.onSuccess {
                    onLoginSuccess()
                }.onFailure { e ->
                    errorMessage = e.message ?: "Google sign-in failed."
                }
            }
        }
    }

    // Dynamic color theme based on active auth mode
    val isParent = authMode == AuthMode.PARENT_LOGIN || authMode == AuthMode.PARENT_REGISTER
    val themeColor by animateColorAsState(
        targetValue = if (isParent) Color(0xFF4361EE) else PinkPrimary,
        label = "ThemeColor"
    )
    val themeContainerBg by animateColorAsState(
        targetValue = if (isParent) Color(0xFF4361EE).copy(alpha = 0.08f) else PinkPrimary.copy(alpha = 0.08f),
        label = "ContainerBg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Header Icon with badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isParent) listOf(Color(0xFF4361EE), Color(0xFF3A0CA3))
                                else listOf(PinkPrimary, Color(0xFFFF758C))
                            )
                        )
                        .border(
                            2.dp,
                            if (isParent) Color(0xFF4361EE).copy(alpha = 0.5f) else PinkPrimary.copy(alpha = 0.5f),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isParent) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Parent Security",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_famitube_logo),
                            contentDescription = "FamiTube Kids",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }

                // Small role pill badge
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = 6.dp)
                        .clip(CircleShape)
                        .background(if (isParent) Color(0xFF3A0CA3) else Color(0xFFFF2E93))
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isParent) "PARENT" else "KIDS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isParent) "FamiTube Parent Studio" else "FamiTube Kids",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (authMode) {
                    AuthMode.CHILD_LOGIN -> "Safe, algorithm-free video & learning space for kids"
                    AuthMode.PARENT_LOGIN -> "Manage child profiles, PIN safety & family settings"
                    AuthMode.PARENT_REGISTER -> "Create your new FamiTube parent account"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Auth Selector Tabs (Child vs Parent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                // Child Mode Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isParent) PinkPrimary else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.CHILD_LOGIN
                            errorMessage = null
                            successMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "👶 Kids Login",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (!isParent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Parent Mode Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isParent) Color(0xFF4361EE) else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.PARENT_LOGIN
                            errorMessage = null
                            successMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛡️ Parent / Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isParent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Container for Auth Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, if (isParent) Color(0xFF4361EE).copy(alpha = 0.35f) else PinkPrimary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Informational guidance banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeContainerBg)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = when (authMode) {
                                    AuthMode.CHILD_LOGIN -> Icons.Rounded.Face
                                    AuthMode.PARENT_LOGIN -> Icons.Rounded.VerifiedUser
                                    AuthMode.PARENT_REGISTER -> Icons.Rounded.PersonAdd
                                },
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (authMode) {
                                    AuthMode.CHILD_LOGIN ->
                                        "Child profiles use the email & password set up by their parent inside Parent Studio."
                                    AuthMode.PARENT_LOGIN ->
                                        "Sign in to your parent account to manage safety controls, daily screen time, and approve YouTube requests."
                                    AuthMode.PARENT_REGISTER ->
                                        "New to FamiTube? Create your parent account below to set up child profiles and access free curated channels."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = themeColor,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // If registering, show Name field
                    AnimatedVisibility(visible = authMode == AuthMode.PARENT_REGISTER) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; errorMessage = null },
                                label = { Text("Parent / Guardian Name") },
                                placeholder = { Text("e.g. Sarah or Mom") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = {
                            Text(
                                when (authMode) {
                                    AuthMode.CHILD_LOGIN -> "Child Email / Gmail"
                                    AuthMode.PARENT_LOGIN, AuthMode.PARENT_REGISTER -> "Parent Email Address"
                                }
                            )
                        },
                        placeholder = {
                            Text(if (isParent) "parent@example.com" else "kid@example.com")
                        },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        placeholder = { Text(if (authMode == AuthMode.PARENT_REGISTER) "At least 6 characters" else "••••••••") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // If registering, show optional Referral Code field
                    AnimatedVisibility(visible = authMode == AuthMode.PARENT_REGISTER) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = referralCode,
                                onValueChange = { referralCode = it.uppercase(); errorMessage = null },
                                label = { Text("Referral Code (Optional)") },
                                placeholder = { Text("e.g. FAM-8K21") },
                                leadingIcon = { Icon(Icons.Rounded.CardGiftcard, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🎁 Have a friend's code? Enter it to get a 3-month Family Pass voucher!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    // Error Message Banner
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Success Message Banner
                    if (successMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF06D6A0).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF06D6A0), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = successMessage!!,
                                color = Color(0xFF06D6A0),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Action Button (Sign In or Register)
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both email and password."
                                return@Button
                            }
                            if (authMode == AuthMode.PARENT_REGISTER && name.isBlank()) {
                                errorMessage = "Please enter your name."
                                return@Button
                            }
                            if (authMode == AuthMode.PARENT_REGISTER && password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null
                            successMessage = null

                            scope.launch {
                                when (authMode) {
                                    AuthMode.CHILD_LOGIN -> {
                                        val result = repository.loginChild(email, password)
                                        isLoading = false
                                        result.onSuccess { onLoginSuccess() }
                                            .onFailure { err -> errorMessage = err.message ?: "Unable to sign in as child." }
                                    }
                                    AuthMode.PARENT_LOGIN -> {
                                        val result = repository.loginParent(email, password)
                                        isLoading = false
                                        result.onSuccess { onLoginSuccess() }
                                            .onFailure { err -> errorMessage = err.message ?: "Invalid parent email or password." }
                                    }
                                    AuthMode.PARENT_REGISTER -> {
                                        val result = repository.registerParent(
                                            name = name,
                                            email = email,
                                            password = password,
                                            referralCode = referralCode
                                        )
                                        isLoading = false
                                        result.onSuccess { msg ->
                                            successMessage = msg
                                            authMode = AuthMode.PARENT_LOGIN
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Unable to register parent account."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = when (authMode) {
                                    AuthMode.CHILD_LOGIN -> "Sign In as Child ▶"
                                    AuthMode.PARENT_LOGIN -> "Sign In as Parent 🛡️"
                                    AuthMode.PARENT_REGISTER -> "Create Parent Account ✨"
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Quick Toggle between Parent Login and Parent Register
                    if (isParent) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (authMode == AuthMode.PARENT_REGISTER) "Already have an account?" else "Don't have a parent account?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    authMode = if (authMode == AuthMode.PARENT_REGISTER) AuthMode.PARENT_LOGIN else AuthMode.PARENT_REGISTER
                                    errorMessage = null
                                    successMessage = null
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (authMode == AuthMode.PARENT_REGISTER) "Sign In" else "Register Free",
                                    fontWeight = FontWeight.Bold,
                                    color = themeColor
                                )
                            }
                        }
                    }

                    // Divider and Google Sign-In for Parent mode
                    if (isParent) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = "  or  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                val oauthUrl = repository.getGoogleMobileOAuthStartUrl()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
                                context.startActivity(intent)
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = themeColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val appContext = LocalContext.current
            val versionName = remember(appContext) {
                try {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
                } catch (_: Exception) {
                    "1.0.0"
                }
            }
            Text(
                text = "FamiTube v$versionName • Safe Curated Video Platform",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
