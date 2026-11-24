package com.famitube.app.ui.screens.parental

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.data.model.GlobalParentalControlsDto
import com.famitube.app.data.model.KidActivityHistoryData
import com.famitube.app.data.model.ParentChildDto
import com.famitube.app.data.model.ParentVideoRequestDto
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.BlueSecondary
import com.famitube.app.ui.theme.GreenSuccess
import com.famitube.app.ui.theme.PinkLight
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.YellowWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    repository: FamiTubeRepository,
    onOpenSettings: () -> Unit,
    onPreviewChildMode: () -> Unit,
    onLogout: () -> Unit,
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {}
) {
    val prefs = repository.getPreferences()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Requests, 1 = Kids, 2 = Controls
    var isLoading by remember { mutableStateOf(true) }
    var children by remember { mutableStateOf<List<ParentChildDto>>(emptyList()) }
    var videoRequests by remember { mutableStateOf<List<ParentVideoRequestDto>>(emptyList()) }
    var globalControls by remember { mutableStateOf(GlobalParentalControlsDto()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showAddChildDialog by remember { mutableStateOf(false) }
    var childToEdit by remember { mutableStateOf<ParentChildDto?>(null) }
    var childForActivityHistory by remember { mutableStateOf<ParentChildDto?>(null) }
    var childToDelete by remember { mutableStateOf<ParentChildDto?>(null) }
    var busyRequestId by remember { mutableStateOf<String?>(null) }

    fun loadDashboard() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val childrenDef = async { repository.getParentChildren() }
            val requestsDef = async { repository.getParentVideoRequests() }
            val globalDef = async { repository.getParentGlobalControls() }
            val cRes = childrenDef.await()
            val rRes = requestsDef.await()
            val gRes = globalDef.await()
            cRes.onSuccess { children = it }
                .onFailure { errorMessage = it.message }
            rRes.onSuccess { videoRequests = it }
                .onFailure { if (errorMessage == null) errorMessage = it.message }
            gRes.onSuccess { globalControls = it }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadDashboard()
    }

    val pendingCount = videoRequests.count { it.status == "PENDING" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PinkPrimary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PARENT MODE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = prefs.loggedInName.ifBlank { "Parent" },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = prefs.loggedInEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge {
                                        Text(if (unreadNotifications > 9) "9+" else unreadNotifications.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = { loadDashboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Parental Settings")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = PinkPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddChildDialog = true },
                    containerColor = PinkPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Child Account")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (pendingCount > 0) "Requests ($pendingCount)" else "Requests",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "My Kids (${children.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text("Controls", fontWeight = FontWeight.SemiBold)
                    }
                )
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PinkPrimary.copy(alpha = 0.12f))
                ) {
                    Text(
                        text = errorMessage!!,
                        color = PinkLight,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PinkPrimary)
                }
            } else {
                when (selectedTab) {
                    0 -> VideoRequestsTab(
                        requests = videoRequests,
                        busyRequestId = busyRequestId,
                        onDecide = { requestId, decision ->
                            busyRequestId = requestId
                            scope.launch {
                                val res = repository.decideParentVideoRequest(requestId, decision)
                                busyRequestId = null
                                res.onSuccess { updated ->
                                    videoRequests = videoRequests.map {
                                        if (it.id == requestId) updated else it
                                    }
                                    Toast.makeText(
                                        context,
                                        if (decision == "APPROVED") "Video approved" else "Request declined",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.message ?: "Failed to update request",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )

                    1 -> MyKidsTab(
                        children = children,
                        onAddChild = { showAddChildDialog = true },
                        onViewHistory = { childForActivityHistory = it },
                        onEditOverrides = { childToEdit = it },
                        onDeleteChild = { childToDelete = it }
                    )

                    2 -> ControlsTab(
                        globalControls = globalControls,
                        onSaveGlobal = { updated, onDone ->
                            scope.launch {
                                val res = repository.updateParentGlobalControls(updated)
                                res.onSuccess { saved ->
                                    globalControls = saved
                                    loadDashboard()
                                    Toast.makeText(context, "Global controls saved for all kids", Toast.LENGTH_SHORT).show()
                                    onDone()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Failed to save global controls", Toast.LENGTH_SHORT).show()
                                    onDone()
                                }
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        onPreviewChildMode = onPreviewChildMode,
                        onLogout = onLogout
                    )
                }
            }
        }
    }

    if (showAddChildDialog) {
        AddChildAccountDialog(
            onDismiss = { showAddChildDialog = false },
            onSubmit = { name, email, password, ageRating, onDone ->
                scope.launch {
                    val res = repository.registerParentChild(email, name, password, ageRating)
                    res.onSuccess { newChild ->
                        children = listOf(newChild) + children
                        showAddChildDialog = false
                        Toast.makeText(context, "Child account created!", Toast.LENGTH_SHORT).show()
                        onDone(null)
                    }.onFailure { err ->
                        onDone(err.message ?: "Failed to create child account")
                    }
                }
            }
        )
    }

    if (childForActivityHistory != null) {
        ChildActivityHistoryDialog(
            child = childForActivityHistory!!,
            repository = repository,
            onDismiss = { childForActivityHistory = null }
        )
    }

    if (childToEdit != null) {
        val target = childToEdit!!
        ChildOverrideDialog(
            child = target,
            globalControls = globalControls,
            onDismiss = { childToEdit = null },
            onSave = { overrides, onDone ->
                scope.launch {
                    val res = repository.updateParentChildOverrides(target.id, overrides)
                    res.onSuccess { updated ->
                        children = children.map { if (it.id == target.id) updated else it }
                        childToEdit = null
                        Toast.makeText(context, "Updated controls for ${target.name}", Toast.LENGTH_SHORT).show()
                        onDone(null)
                    }.onFailure { err ->
                        onDone(err.message ?: "Failed to update overrides")
                    }
                }
            }
        )
    }

    if (childToDelete != null) {
        val target = childToDelete!!
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            title = { Text("Remove Child Account?") },
            text = { Text("Remove ${target.name} (${target.email}) from your family?") },
            confirmButton = {
                Button(
                    onClick = {
                        childToDelete = null
                        scope.launch {
                            val res = repository.deleteParentChild(target.id)
                            res.onSuccess {
                                children = children.filterNot { it.id == target.id }
                                Toast.makeText(context, "Child account removed", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to remove", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { childToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VideoRequestsTab(
    requests: List<ParentVideoRequestDto>,
    busyRequestId: String?,
    onDecide: (String, String) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    tint = GreenSuccess,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Video Requests Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "When your child searches YouTube for a new video, their request will appear here for approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(requests, key = { it.id }) { req ->
            val isPending = req.status == "PENDING"
            val statusColor = when (req.status) {
                "APPROVED" -> GreenSuccess
                "REJECTED" -> PinkLight
                else -> YellowWarning
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (req.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = req.thumbnailUrl,
                                contentDescription = req.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 104.dp, height = 62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = req.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${req.channelTitle} • Requested by ${req.kidName.ifBlank { req.kidEmail }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = req.status,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isPending) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDecide(req.id, "REJECTED") },
                                enabled = busyRequestId != req.id,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Decline")
                            }
                            Button(
                                onClick = { onDecide(req.id, "APPROVED") },
                                enabled = busyRequestId != req.id,
                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyKidsTab(
    children: List<ParentChildDto>,
    onAddChild: () -> Unit,
    onViewHistory: (ParentChildDto) -> Unit,
    onEditOverrides: (ParentChildDto) -> Unit,
    onDeleteChild: (ParentChildDto) -> Unit
) {
    if (children.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FamilyRestroom,
                    contentDescription = null,
                    tint = BlueSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Child Accounts Registered",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Register a child email so they can sign in on the main Child Login screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddChild,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Child Account", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(children, key = { it.id }) { child ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BlueSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = child.name.firstOrNull()?.uppercaseChar()?.toString() ?: "K",
                            fontWeight = FontWeight.Bold,
                            color = BlueSecondary,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = child.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = child.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Age: ${child.ageRating} • Limit: ${child.dailyLimitMinutes}m/day",
                            style = MaterialTheme.typography.labelSmall,
                            color = BlueSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val hasOverrides = child.overrides.isNotEmpty()
                        Text(
                            text = if (hasOverrides) "Custom Override (${child.overrides.size})" else "Using Global Controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasOverrides) PinkPrimary else GreenSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { onViewHistory(child) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Activity & Search History",
                            tint = GreenSuccess
                        )
                    }
                    IconButton(onClick = { onEditOverrides(child) }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Kid Overrides",
                            tint = BlueSecondary
                        )
                    }
                    IconButton(onClick = { onDeleteChild(child) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove child",
                            tint = PinkLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsTab(
    globalControls: GlobalParentalControlsDto,
    onSaveGlobal: (GlobalParentalControlsDto, () -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onPreviewChildMode: () -> Unit,
    onLogout: () -> Unit
) {
    var pin by remember(globalControls) { mutableStateOf(globalControls.parentPin) }
    var dailyLimit by remember(globalControls) { mutableStateOf(globalControls.dailyLimitMinutes.toString()) }
    var ageRating by remember(globalControls) { mutableStateOf(globalControls.ageRating) }
    var attentionEnabled by remember(globalControls) { mutableStateOf(globalControls.attentionTrackingEnabled) }
    var ytSearchEnabled by remember(globalControls) { mutableStateOf(globalControls.youtubeSearchEnabled) }
    var ytRequireApproval by remember(globalControls) { mutableStateOf(globalControls.searchRequireApproval) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Global Parental Controls (All Kids)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Applies to all kids by default unless overridden on an individual child in My Kids.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                        label = { Text("Global PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dailyLimit,
                        onValueChange = { if (it.all(Char::isDigit)) dailyLimit = it },
                        label = { Text("Daily Limit (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All", "TODDLER" to "2-4", "YOUNG_KIDS" to "5-8", "OLDER_KIDS" to "9-12").forEach { (code, label) ->
                        FilterChip(
                            selected = ageRating == code,
                            onClick = { ageRating = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attention Check", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = attentionEnabled, onCheckedChange = { attentionEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("YouTube Search", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ytSearchEnabled, onCheckedChange = { ytSearchEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Require Parent Approval", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ytRequireApproval, onCheckedChange = { ytRequireApproval = it })
                }
                Button(
                    onClick = {
                        isSaving = true
                        onSaveGlobal(
                            globalControls.copy(
                                parentPin = pin.ifBlank { "1234" },
                                dailyLimitMinutes = dailyLimit.toIntOrNull() ?: 45,
                                ageRating = ageRating,
                                attentionTrackingEnabled = attentionEnabled,
                                youtubeSearchEnabled = ytSearchEnabled,
                                searchRequireApproval = ytRequireApproval
                            )
                        ) { isSaving = false }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Saving..." else "Save Global Controls", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Device Settings & Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Device Parental Settings")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPreviewChildMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preview Child Mode")
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = PinkPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out of Parent Mode", color = PinkPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddChildAccountDialog(
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        email: String,
        password: String,
        ageRating: String,
        onDone: (String?) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ageRating by remember { mutableStateOf("YOUNG_KIDS") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val ageOptions = listOf(
        "TODDLER" to "Toddlers (2-4)",
        "YOUNG_KIDS" to "Young Kids (5-8)",
        "OLDER_KIDS" to "Older Kids (9-12)",
        "ALL" to "All Ages"
    )

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Add Child Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Child Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = { Text("Child Email / Gmail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Age Tier",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ageOptions.take(2).forEach { (code, label) ->
                        FilterChip(
                            selected = ageRating == code,
                            onClick = { ageRating = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ageOptions.drop(2).forEach { (code, label) ->
                        FilterChip(
                            selected = ageRating == code,
                            onClick = { ageRating = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = PinkLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || password.length < 6) {
                        errorMsg = "Enter name, valid email, and a 6+ char password."
                        return@Button
                    }
                    isSaving = true
                    errorMsg = null
                    onSubmit(name, email, password, ageRating) { err ->
                        isSaving = false
                        if (err != null) errorMsg = err
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text(if (isSaving) "Saving..." else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChildOverrideDialog(
    child: ParentChildDto,
    globalControls: GlobalParentalControlsDto,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>, (String?) -> Unit) -> Unit
) {
    var dailyLimit by remember { mutableStateOf(child.dailyLimitMinutes.toString()) }
    var pin by remember { mutableStateOf(child.parentPin) }
    var ageRating by remember { mutableStateOf(child.ageRating) }
    var attentionEnabled by remember { mutableStateOf(child.attentionTrackingEnabled) }
    var ytEnabled by remember { mutableStateOf(child.youtubeSearchEnabled) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("${child.name} — Controls Override", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Global defaults: ${globalControls.dailyLimitMinutes}m/day • ${globalControls.ageRating} • PIN ${globalControls.parentPin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyLimit,
                        onValueChange = { if (it.all(Char::isDigit)) dailyLimit = it },
                        label = { Text("Limit (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All", "TODDLER" to "2-4", "YOUNG_KIDS" to "5-8", "OLDER_KIDS" to "9-12").forEach { (code, label) ->
                        FilterChip(
                            selected = ageRating == code,
                            onClick = { ageRating = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attention Check", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = attentionEnabled, onCheckedChange = { attentionEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("YouTube Search", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ytEnabled, onCheckedChange = { ytEnabled = it })
                }
                OutlinedButton(
                    onClick = {
                        isSaving = true
                        onSave(
                            mapOf(
                                "parentPin" to null,
                                "dailyLimitMinutes" to null,
                                "ageRating" to null,
                                "attentionTrackingEnabled" to null,
                                "youtubeSearchEnabled" to null
                            )
                        ) { err ->
                            isSaving = false
                            if (err != null) errorMsg = err
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Global Controls")
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = PinkLight, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length != 4) {
                        errorMsg = "PIN must be 4 digits"
                        return@Button
                    }
                    isSaving = true
                    onSave(
                        mapOf(
                            "dailyLimitMinutes" to (dailyLimit.toIntOrNull() ?: globalControls.dailyLimitMinutes),
                            "parentPin" to pin,
                            "ageRating" to ageRating,
                            "attentionTrackingEnabled" to attentionEnabled,
                            "youtubeSearchEnabled" to ytEnabled
                        )
                    ) { err ->
                        isSaving = false
                        if (err != null) errorMsg = err
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text(if (isSaving) "Saving..." else "Save Override")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChildActivityHistoryDialog(
    child: ParentChildDto,
    repository: FamiTubeRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Watch, 1 = Searches, 2 = Requests
    var isLoading by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf(KidActivityHistoryData()) }

    fun loadHistory() {
        scope.launch {
            isLoading = true
            repository.getChildActivityHistory(child.id).onSuccess {
                data = it
            }
            isLoading = false
        }
    }

    LaunchedEffect(child.id) {
        loadHistory()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${child.name} • Activity History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                TabRow(selectedTabIndex = activeTab) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Watched (${data.watchHistory.size})", fontSize = 10.sp) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Searches (${data.searchHistory.size})", fontSize = 10.sp) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Requests (${data.videoRequests.size})", fontSize = 10.sp) }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("@Channels (${data.youtubeSubscriptions.size})", fontSize = 10.sp) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PinkPrimary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (activeTab) {
                            0 -> {
                                if (data.watchHistory.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No videos watched yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    items(data.watchHistory, key = { it.id }) { item ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = item.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${item.channelTitle} • ${if (item.isApprovedSearch) "Approved Search" else "Curated"}",
                                                    fontSize = 11.sp,
                                                    color = BlueSecondary
                                                )
                                                if (item.watchedAt.isNotBlank()) {
                                                    Text(
                                                        text = item.watchedAt,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                if (data.searchHistory.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No YouTube search queries logged yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    items(data.searchHistory, key = { it.id }) { item ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "🔍 \"${item.query}\"",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "${item.resultsCount} results • ${item.searchedAt}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                if (data.videoRequests.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No YouTube video requests yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    items(data.videoRequests, key = { it.id }) { req ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = req.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${req.channelTitle} • Status: ${req.status}",
                                                    fontSize = 11.sp,
                                                    color = when (req.status) {
                                                        "APPROVED" -> GreenSuccess
                                                        "REJECTED" -> PinkLight
                                                        else -> YellowWarning
                                                    }
                                                )
                                                if (req.createdAt.isNotBlank()) {
                                                    Text(
                                                        text = req.createdAt,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                if (data.youtubeSubscriptions.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No @YouTube channels subscribed yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    items(data.youtubeSubscriptions, key = { "sub_${it.handle}" }) { sub ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = sub.handle,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 13.sp,
                                                            color = BlueSecondary
                                                        )
                                                        Text(
                                                            text = sub.title,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    val trustColor = when (sub.trustLevel) {
                                                        "TRUSTED" -> GreenSuccess
                                                        "BLOCKED" -> PinkLight
                                                        else -> YellowWarning
                                                    }
                                                    val trustLabel = when (sub.trustLevel) {
                                                        "TRUSTED" -> "✓ Trusted"
                                                        "BLOCKED" -> "Blocked"
                                                        else -> "Supervised"
                                                    }
                                                    Text(
                                                        text = trustLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = trustColor
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    listOf(
                                                        "TRUSTED" to "Trust (Auto)",
                                                        "SUPERVISED" to "Supervise",
                                                        "BLOCKED" to "Block"
                                                    ).forEach { (level, label) ->
                                                        val isCurrent = sub.trustLevel == level
                                                        OutlinedButton(
                                                            onClick = {
                                                                if (!isCurrent) {
                                                                    scope.launch {
                                                                        repository.updateChildYouTubeSubscriptionTrust(
                                                                            childId = child.id,
                                                                            handle = sub.handle,
                                                                            trustLevel = level
                                                                        )
                                                                        loadHistory()
                                                                    }
                                                                }
                                                            },
                                                            enabled = !isCurrent,
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text(label, fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (activeTab == 0 || activeTab == 1) {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (activeTab == 0) {
                                repository.clearChildWatchHistory(child.id)
                            } else {
                                repository.clearChildSearchHistory(child.id)
                            }
                            loadHistory()
                        }
                    }
                ) {
                    Text("Clear Tab", color = PinkLight)
                }
            }
        }
    )
}
