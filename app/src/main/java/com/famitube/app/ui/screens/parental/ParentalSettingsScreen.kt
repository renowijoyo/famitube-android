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
    var limitHours by remember { mutableIntStateOf((prefs.dailyLimitMinutes / 60).coerceIn(0, 12)) }
    var limitMinutes by remember { mutableIntStateOf((prefs.dailyLimitMinutes % 60).coerceIn(0, 59)) }
    var attentionTracking by remember { mutableStateOf(prefs.attentionTrackingEnabled) }
    var attentionPauseSeconds by remember { mutableIntStateOf(prefs.attentionPauseSeconds) }
    var attentionMinWatchPercent by remember { mutableIntStateOf(prefs.attentionMinWatchPercent) }
    var selectedAge by remember { mutableStateOf(prefs.selectedAgeFilter) }
    var contentLanguage by remember { mutableStateOf(prefs.contentLanguage) }
    var cultureMode by remember { mutableStateOf(prefs.cultureMode) }
    var toddlerLoopMinutes by remember { mutableIntStateOf(prefs.toddlerLoopMinutes) }
    var toddlerCooldownMinutes by remember { mutableIntStateOf(prefs.toddlerCooldownMinutes) }
    var toddlerEnabledTopics by remember { mutableStateOf(prefs.toddlerEnabledTopics.toSet()) }
    var newPin by remember { mutableStateOf(prefs.parentPin) }
    var customStarsInput by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }
    val starPointsManager = repository.getStarPointsManager()
    val starBalance by (starPointsManager?.starBalanceFlow?.collectAsState()
        ?: remember { mutableIntStateOf(0) })

    val ageOptions = listOf(
        "TODDLER" to "Tier 1: Toddler TV (Ages 1.5–3)",
        "YOUNG_KIDS" to "Tier 2: Young Kids (Ages 4–6)",
        "OLDER_KIDS" to "Tier 3: Older Kids (Ages 7+)",
        "ALL" to "All Ages / Pre-Teens (12+)"
    )

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
                    text = "Set hours and minutes for daily watch time (set 0h 0m for no limit).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                fun applyTimeLimit(h: Int, m: Int) {
                    limitHours = h.coerceIn(0, 12)
                    limitMinutes = m.coerceIn(0, 59)
                    val total = limitHours * 60 + limitMinutes
                    dailyLimit = total
                    prefs.dailyLimitMinutes = total
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeUnitPickerCard(
                        label = "Hours",
                        value = limitHours,
                        unitSuffix = "h",
                        onDecrement = { applyTimeLimit((limitHours - 1).coerceAtLeast(0), limitMinutes) },
                        onIncrement = { applyTimeLimit((limitHours + 1).coerceAtMost(12), limitMinutes) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeUnitPickerCard(
                        label = "Minutes",
                        value = limitMinutes,
                        unitSuffix = "m",
                        onDecrement = {
                            val nextM = if (limitMinutes - 5 < 0) 55 else limitMinutes - 5
                            applyTimeLimit(limitHours, nextM)
                        },
                        onIncrement = {
                            val nextM = if (limitMinutes + 5 >= 60) 0 else limitMinutes + 5
                            applyTimeLimit(limitHours, nextM)
                        },
                        onStepSmallDecrement = { applyTimeLimit(limitHours, (limitMinutes - 1).coerceAtLeast(0)) },
                        onStepSmallIncrement = { applyTimeLimit(limitHours, (limitMinutes + 1).coerceAtMost(59)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val summaryText = if (dailyLimit <= 0) {
                        "Current limit: No Limit"
                    } else {
                        "Current limit: ${limitHours}h ${limitMinutes}m ($dailyLimit mins)"
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PinkPrimary
                    )
                    OutlinedButton(
                        onClick = {
                            applyTimeLimit(0, 0)
                            Toast.makeText(context, "Daily watch limit set to No Limit", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("No Limit (0)")
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

                if (attentionTracking) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Auto-stop video if no viewer detected for:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30, 60).forEach { secs ->
                            val isSel = attentionPauseSeconds == secs
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) PinkPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        attentionPauseSeconds = secs
                                        prefs.attentionPauseSeconds = secs
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${secs}s",
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Minimum viewed % to earn badge & stars:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(50, 70, 80, 90).forEach { pct ->
                            val isSel = attentionMinWatchPercent == pct
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) PinkPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        attentionMinWatchPercent = pct
                                        prefs.attentionMinWatchPercent = pct
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${pct}%",
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
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

            // 3b. Global Language & Culture
            SettingsSectionCard(title = "Language & Culture") {
                Text(
                    text = "Parent-selected language and cultural mix for curated channels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("all" to "All", "en" to "English", "id" to "Indonesia", "bilingual" to "Bilingual").forEach { (code, label) ->
                        FilterChip(
                            selected = contentLanguage == code,
                            onClick = {
                                contentLanguage = code
                                prefs.contentLanguage = code
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("GLOBAL" to "Global Mix", "ID" to "Indonesia-First").forEach { (code, label) ->
                        FilterChip(
                            selected = cultureMode == code,
                            onClick = {
                                cultureMode = code
                                prefs.cultureMode = code
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3c. Age-Specific Toddler TV Mode Settings
            if (selectedAge == "TODDLER") {
                val allTopics = listOf(
                    "Music & Rhymes",
                    "Animals & Nature",
                    "Learning & ABCs",
                    "Art & Crafts",
                    "Kids Yoga & Active",
                    "Stories & Reading",
                    "Cartoons & Fun"
                )
                SettingsSectionCard(title = "Toddler TV Mode Settings") {
                    Text(
                        text = "1 loop = 1 calm session, followed by a gentle Nap Cooldown.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Session Loop Length", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 20).forEach { mins ->
                            FilterChip(
                                selected = toddlerLoopMinutes == mins,
                                onClick = {
                                    toddlerLoopMinutes = mins
                                    prefs.toddlerLoopMinutes = mins
                                },
                                label = { Text("${mins}m") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nap Cooldown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = toddlerCooldownMinutes == mins,
                                onClick = {
                                    toddlerCooldownMinutes = mins
                                    prefs.toddlerCooldownMinutes = mins
                                },
                                label = { Text("${mins}m") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enabled TV Channels", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    allTopics.forEach { topic ->
                        val checked = topic in toddlerEnabledTopics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = if (checked && toddlerEnabledTopics.size > 1) {
                                        toddlerEnabledTopics - topic
                                    } else {
                                        toddlerEnabledTopics + topic
                                    }
                                    toddlerEnabledTopics = next
                                    prefs.toddlerEnabledTopics = allTopics.filter { it in next }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val next = if (!isChecked && toddlerEnabledTopics.size > 1) {
                                        toddlerEnabledTopics - topic
                                    } else if (isChecked) {
                                        toddlerEnabledTopics + topic
                                    } else toddlerEnabledTopics
                                    toddlerEnabledTopics = next
                                    prefs.toddlerEnabledTopics = allTopics.filter { it in next }
                                }
                            )
                            Text(topic, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            prefs.wakeToddlerTv()
                            scope.launch { repository.wakeToddlerTvWithPin(prefs.parentPin) }
                            Toast.makeText(context, "Toddler TV woken up! ☀️", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("☀️ Wake Up TV Now", color = PinkPrimary, fontWeight = FontWeight.Bold)
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

@Composable
private fun TimeUnitPickerCard(
    label: String,
    value: Int,
    unitSuffix: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onStepSmallDecrement: (() -> Unit)? = null,
    onStepSmallIncrement: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                modifier = Modifier.size(34.dp)
            ) {
                Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Text(
                text = "${value}$unitSuffix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalIconButton(
                onClick = onIncrement,
                modifier = Modifier.size(34.dp)
            ) {
                Text("+", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
        if (onStepSmallDecrement != null && onStepSmallIncrement != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "-1m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PinkPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onStepSmallDecrement)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "+1m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PinkPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onStepSmallIncrement)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
