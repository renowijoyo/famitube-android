package com.kidstube.app.ui.screens.parental

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
import com.kidstube.app.data.local.ParentalPreferences
import com.kidstube.app.data.remote.NetworkModule
import com.kidstube.app.data.repository.KidsTubeRepository
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark
import com.kidstube.app.ui.theme.SurfaceLightDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalSettingsScreen(
    repository: KidsTubeRepository,
    onBackClick: () -> Unit
) {
    val prefs = repository.getPreferences()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dailyLimit by remember { mutableIntStateOf(prefs.dailyLimitMinutes) }
    var selectedAge by remember { mutableStateOf(prefs.selectedAgeFilter) }
    var newPin by remember { mutableStateOf(prefs.parentPin) }
    var backendUrl by remember { mutableStateOf(prefs.backendUrl) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val ageOptions = listOf(
        "ALL" to "All Kids (Any Age)",
        "TODDLER" to "Toddlers (Ages 2-4)",
        "YOUNG_KIDS" to "Young Kids (Ages 5-8)",
        "OLDER_KIDS" to "Older Kids (Ages 9-12)"
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
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
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
            }

            // 2. Age Content Filter
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

            // 4. Backend Server Configuration
            SettingsSectionCard(title = "Backend Server Connection") {
                Text(
                    text = "Connect to your local or cloud KidsTube Curation Studio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("Backend API URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            prefs.backendUrl = backendUrl
                            NetworkModule.updateBaseUrl(backendUrl)
                            scope.launch {
                                isTestingConnection = true
                                val test = repository.getAppConfig()
                                isTestingConnection = false
                                if (test.isSuccess) {
                                    Toast.makeText(context, "Connected successfully to KidsTube Backend!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Connection failed: ${test.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text("Save & Test")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            backendUrl = NetworkModule.DEFAULT_BASE_URL
                            prefs.backendUrl = backendUrl
                            NetworkModule.updateBaseUrl(backendUrl)
                            Toast.makeText(context, "Reset to default emulator address", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ðŸ’¡ Tip: To curate videos from your computer, open http://localhost:4000/admin in your browser.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CC9F0)
                )
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
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
