package com.famitube.app.ui.screens.parental

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.remote.NetworkModule
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import com.famitube.app.ui.theme.SurfaceLightDark
import com.famitube.app.ui.theme.YellowWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalSettingsScreen(
    repository: FamiTubeRepository,
    onBackClick: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val prefs = repository.getPreferences()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dailyLimit by remember { mutableIntStateOf(prefs.dailyLimitMinutes) }
    var attentionTracking by remember { mutableStateOf(prefs.attentionTrackingEnabled) }
    var selectedAge by remember { mutableStateOf(prefs.selectedAgeFilter) }
    var newPin by remember { mutableStateOf(prefs.parentPin) }
    var customStarsInput by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }
    val starPointsManager = repository.getStarPointsManager()
    val starBalance by (starPointsManager?.starBalanceFlow?.collectAsState()
        ?: remember { mutableIntStateOf(0) })

    val ageOptions = listOf(
        "ALL" to "Pre-Teens & Up (Ages 12+)",
        "TODDLER" to "Toddlers (Ages 0-4)",
        "YOUNG_KIDS" to "Young Kids (Ages 5-7)",
        "OLDER_KIDS" to "Explorers / Tweens (Ages 8-11)"
    )

    val limitOptions = listOf(15, 30, 45, 60, 90, 120)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parental Controls", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Screen Time Section
            SettingsSectionCard(title = "Daily Screen Time Limit") {
                Text(
                    text = "Control how long your child can watch videos each day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    limitOptions.forEach { mins ->
                        val isSel = dailyLimit == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) PinkPrimary else SurfaceLightDark)
                                .clickable {
                                    dailyLimit = mins
                                    prefs.dailyLimitMinutes = mins
                                    Toast.makeText(context, "Limit set to $mins min", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Used today: ${prefs.getUsedSecondsToday() / 60} mins",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            prefs.addUsedWatchTime(-prefs.getUsedSecondsToday())
                            Toast.makeText(context, "Watch timer reset for today", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Reset Today's Time")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Camera Attention Check",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tracks on-device whether the video is being watched vs. playing unattended.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = attentionTracking,
                        onCheckedChange = { enabled ->
                            attentionTracking = enabled
                            prefs.attentionTrackingEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PinkPrimary)
                    )
                }
            }

            // 2. Star Points Rewards
            SettingsSectionCard(title = "Star Points Rewards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reward your child with Stars for video requests & perks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YellowWarning.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⭐ $starBalance",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = YellowWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 25, 50, 100).forEach { gift ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceLightDark)
                                .clickable {
                                    repository.awardStarsFromParent(gift, "Gift from Parent (+$gift ⭐)")
                                    Toast.makeText(context, "Gave +$gift ⭐ to child!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$gift ⭐",
                                color = YellowWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customStarsInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                customStarsInput = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text("Custom Stars") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val amount = customStarsInput.toIntOrNull() ?: 0
                            if (amount > 0) {
                                repository.awardStarsFromParent(amount, "Gift from Parent (+$amount ⭐)")
                                customStarsInput = ""
                                Toast.makeText(context, "Gave +$amount ⭐ to child!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enter a valid star amount", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Text("Give Stars")
                    }
                }
            }

            // 3. Age Content Filter
            SettingsSectionCard(title = "Age Filter") {
                Text(
                    text = "Filter videos shown in the app to match your child's age group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                ageOptions.forEach { (key, label) ->
                    val isSel = selectedAge == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedAge = key
                                prefs.selectedAgeFilter = key
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSel,
                            onClick = {
                                selectedAge = key
                                prefs.selectedAgeFilter = key
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = PinkPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 3. Security PIN
            SettingsSectionCard(title = "Parent Security PIN") {
                Text(
                    text = "This 4-digit PIN is required to open parental controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4) newPin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        label = { Text("4-Digit PIN") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (newPin.length == 4) {
                                prefs.parentPin = newPin
                                Toast.makeText(context, "PIN updated successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Text("Save PIN")
                    }
                }
            }

            // 4. Backend Server Connection Status
            SettingsSectionCard(title = "Backend Server Connection") {
                Text(
                    text = "Connected to FamiTube Cloud Studio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isTestingConnection = true
                            val test = repository.getAppConfig()
                            isTestingConnection = false
                            if (test.isSuccess) {
                                Toast.makeText(context, "Connected successfully to FamiTube Backend!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Connection failed: ${test.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Test Connection")
                    }
                }
            }

            // 5. Signed-In Child Session
            SettingsSectionCard(title = "Child Account Session") {
                val signedInEmail = prefs.loggedInEmail
                val signedInName = prefs.loggedInName
                Text(
                    text = if (signedInEmail.isNotBlank()) {
                        "Signed in as ${signedInName.ifBlank { signedInEmail }} ($signedInEmail)"
                    } else {
                        "Not signed in"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out Child Account", color = PinkPrimary)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
