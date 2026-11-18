package com.famitube.app.ui.screens.parental

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    onLogout: () -> Unit
) {
    val prefs = repository.getPreferences()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Requests, 1 = Kids, 2 = Controls
    var isLoading by remember { mutableStateOf(true) }
    var children by remember { mutableStateOf<List<ParentChildDto>>(emptyList()) }
    var videoRequests by remember { mutableStateOf<List<ParentVideoRequestDto>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showAddChildDialog by remember { mutableStateOf(false) }
    var childToDelete by remember { mutableStateOf<ParentChildDto?>(null) }
    var busyRequestId by remember { mutableStateOf<String?>(null) }

    fun loadDashboard() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val childrenDef = async { repository.getParentChildren() }
            val requestsDef = async { repository.getParentVideoRequests() }
            val cRes = childrenDef.await()
            val rRes = requestsDef.await()
            cRes.onSuccess { children = it }
                .onFailure { errorMessage = it.message }
            rRes.onSuccess { videoRequests = it }
                .onFailure { if (errorMessage == null) errorMessage = it.message }
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
                        onDeleteChild = { childToDelete = it }
                    )

                    2 -> ControlsTab(
                        prefs = prefs,
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
    prefs: com.famitube.app.data.local.ParentalPreferences,
    onOpenSettings: () -> Unit,
    onPreviewChildMode: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Device & Family Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Daily limit: ${prefs.dailyLimitMinutes} min • Age filter: ${prefs.selectedAgeFilter}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Parental Settings", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Child Feed Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Browse the curated channels and videos as your child sees them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
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
