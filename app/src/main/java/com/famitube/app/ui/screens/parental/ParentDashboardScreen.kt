package com.famitube.app.ui.screens.parental

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.famitube.app.data.model.GlobalParentalControlsDto
import com.famitube.app.data.model.KidActivityHistoryData
import com.famitube.app.data.model.ParentChildDto
import com.famitube.app.data.model.ParentVideoRequestDto
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.YouTubeIFramePlayer
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

    var isAddingChild by remember { mutableStateOf(false) }
    var childToEdit by remember { mutableStateOf<ParentChildDto?>(null) }
    var childForActivityHistory by remember { mutableStateOf<ParentChildDto?>(null) }
    var childToDelete by remember { mutableStateOf<ParentChildDto?>(null) }
    var busyRequestId by remember { mutableStateOf<String?>(null) }
    var videoRequestToPreview by remember { mutableStateOf<ParentVideoRequestDto?>(null) }

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

    if (isAddingChild) {
        AddChildAccountView(
            onBack = { isAddingChild = false },
            onSubmit = { name, email, password, ageRating, birthday, religion, onDone ->
                scope.launch {
                    val res = repository.registerParentChild(email, name, password, ageRating, birthday, religion)
                    res.onSuccess { newChild ->
                        children = listOf(newChild) + children
                        isAddingChild = false
                        Toast.makeText(context, "Child account created!", Toast.LENGTH_SHORT).show()
                        onDone(null)
                    }.onFailure { err ->
                        onDone(err.message ?: "Failed to create child account")
                    }
                }
            }
        )
        return
    }

    if (childForActivityHistory != null) {
        ChildActivityHistoryView(
            child = childForActivityHistory!!,
            repository = repository,
            onPreviewVideo = { videoRequestToPreview = it },
            onBack = { childForActivityHistory = null }
        )
        if (videoRequestToPreview != null) {
            val req = videoRequestToPreview!!
            ParentVideoPreviewDialog(
                request = req,
                isPending = req.status == "PENDING",
                isBusy = busyRequestId == req.id,
                onDismiss = { videoRequestToPreview = null },
                onDecide = { id, dec ->
                    busyRequestId = id
                    scope.launch {
                        val res = repository.decideParentVideoRequest(id, dec)
                        busyRequestId = null
                        res.onSuccess { updated ->
                            videoRequests = videoRequests.map { if (it.id == id) updated else it }
                            videoRequestToPreview = null
                        }
                    }
                }
            )
        }
        return
    }

    if (childToEdit != null) {
        val target = childToEdit!!
        EditChildControlsView(
            child = target,
            globalControls = globalControls,
            onBack = { childToEdit = null },
            onWakeToddlerTv = { onDone ->
                scope.launch {
                    val res = repository.wakeChildToddlerTv(target.id)
                    res.onSuccess {
                        Toast.makeText(context, "Toddler TV woken up for ${target.name}!", Toast.LENGTH_SHORT).show()
                        onDone(null)
                    }.onFailure { err ->
                        onDone(err.message ?: "Failed to wake Toddler TV")
                    }
                }
            },
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
        return
    }

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
                    onClick = { isAddingChild = true },
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
                        onPreviewVideo = { videoRequestToPreview = it },
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
                        onAddChild = { isAddingChild = true },
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

    // Child creation, child activity history, and edit child controls are rendered as their own page views above

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

    if (videoRequestToPreview != null) {
        val req = videoRequestToPreview!!
        ParentVideoPreviewDialog(
            request = req,
            isPending = req.status == "PENDING",
            isBusy = busyRequestId == req.id,
            onDismiss = { videoRequestToPreview = null },
            onDecide = { id, dec ->
                busyRequestId = id
                scope.launch {
                    val res = repository.decideParentVideoRequest(id, dec)
                    busyRequestId = null
                    res.onSuccess { updated ->
                        videoRequests = videoRequests.map {
                            if (it.id == id) updated else it
                        }
                        videoRequestToPreview = null
                        Toast.makeText(
                            context,
                            if (dec == "APPROVED") "Video approved" else "Request declined",
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
    }
}

@Composable
private fun VideoRequestsTab(
    requests: List<ParentVideoRequestDto>,
    busyRequestId: String?,
    onPreviewVideo: (ParentVideoRequestDto) -> Unit,
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
                            Box(
                                modifier = Modifier
                                    .size(width = 104.dp, height = 62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPreviewVideo(req) }
                            ) {
                                AsyncImage(
                                    model = req.thumbnailUrl,
                                    contentDescription = req.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Preview Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
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

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreviewVideo(req) },
                            modifier = if (isPending) Modifier.weight(1f) else Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview")
                        }
                        if (isPending) {
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
private fun ParentVideoPreviewDialog(
    request: ParentVideoRequestDto,
    isPending: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onDecide: (String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = PinkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Preview Video Request",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val vidId = request.youtubeVideoId.ifBlank {
                    val regex = "vi/([^/]+)".toRegex()
                    regex.find(request.thumbnailUrl)?.groupValues?.getOrNull(1) ?: ""
                }

                if (vidId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        YouTubeIFramePlayer(
                            videoId = vidId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (request.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = request.thumbnailUrl,
                        contentDescription = request.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${request.channelTitle} • Requested by ${request.kidName.ifBlank { request.kidEmail }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isPending) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDecide(request.id, "REJECTED")
                                onDismiss()
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Decline")
                        }
                        Button(
                            onClick = {
                                onDecide(request.id, "APPROVED")
                                onDismiss()
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Preview")
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
    var contentLanguage by remember(globalControls) { mutableStateOf(globalControls.contentLanguage) }
    var cultureMode by remember(globalControls) { mutableStateOf(globalControls.cultureMode) }
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
                Text("Family Language", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("all" to "All", "en" to "English", "id" to "Indonesia", "bilingual" to "Bilingual").forEach { (code, label) ->
                        FilterChip(
                            selected = contentLanguage == code,
                            onClick = { contentLanguage = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("Culture Focus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("GLOBAL" to "Global Mix", "ID" to "Indonesia-First").forEach { (code, label) ->
                        FilterChip(
                            selected = cultureMode == code,
                            onClick = { cultureMode = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                                searchRequireApproval = ytRequireApproval,
                                contentLanguage = contentLanguage,
                                cultureMode = cultureMode
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChildAccountView(
    onBack: () -> Unit,
    onSubmit: (
        name: String,
        email: String,
        password: String,
        ageRating: String,
        birthday: String?,
        religion: String?,
        onDone: (String?) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var ageRating by remember { mutableStateOf("YOUNG_KIDS") }
    var religion by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val ageOptions = listOf(
        "TODDLER" to "Tier 1: Toddler (1.5-3)",
        "YOUNG_KIDS" to "Tier 2: Young (4-6)",
        "OLDER_KIDS" to "Tier 3: Older (7+)",
        "ALL" to "All Ages (12+)"
    )

    val religionOptions = listOf(
        "NONE" to "None (Secular Only)",
        "ISLAM" to "Islam",
        "CHRISTIANITY" to "Christianity",
        "CATHOLICISM" to "Catholicism",
        "HINDUISM" to "Hinduism",
        "BUDDHISM" to "Buddhism",
        "JUDAISM" to "Judaism",
        "CONFUCIANISM" to "Confucianism"
    )

    fun suggestTierFromBirthday(raw: String): String? {
        val parts = raw.trim().split("-")
        val year = parts.firstOrNull()?.toIntOrNull() ?: return null
        if (year !in 2005..2026) return null
        val approxAge = 2026 - year
        return when {
            approxAge <= 3 -> "TODDLER"
            approxAge <= 6 -> "YOUNG_KIDS"
            else -> "OLDER_KIDS"
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(birthday) {
            val parts = birthday.trim().split("-")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
                    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                        clear()
                        set(y, m - 1, d)
                    }
                    cal.timeInMillis
                } else System.currentTimeMillis()
            } else System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            val formatted = String.format(
                                "%04d-%02d-%02d",
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH) + 1,
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            )
                            birthday = formatted
                            errorMsg = null
                            suggestTierFromBirthday(formatted)?.let { ageRating = it }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = PinkPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    BackHandler(enabled = !isSaving) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Add Child Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create a safe profile for your kid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        tint = PinkPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Set up your child's profile and credentials. Picking their birthday automatically suggests the recommended age tier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = { Text("Child Name") },
                placeholder = { Text("e.g. Maya") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = null },
                label = { Text("Child Email / Login") },
                placeholder = { Text("e.g. maya@famitube.demo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = { Text("Password (min 6 chars)") },
                placeholder = { Text("••••••••") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = birthday,
                onValueChange = {
                    birthday = it
                    errorMsg = null
                    suggestTierFromBirthday(it)?.let { tier -> ageRating = tier }
                },
                label = { Text("Birthday (YYYY-MM-DD)") },
                placeholder = { Text("Tap calendar icon or type YYYY-MM-DD") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Birthday", tint = PinkPrimary)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Selecting or typing birthday automatically suggests the recommended age tier below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Age Tier (Parent decides; birthday suggests)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Faith & Religion (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "If unselected, religious content is completely hidden for this child.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    religionOptions.take(2).forEach { (code, label) ->
                        FilterChip(
                            selected = if (code == "NONE") religion == null else religion == code,
                            onClick = { religion = if (code == "NONE") null else code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    religionOptions.slice(2..4).forEach { (code, label) ->
                        FilterChip(
                            selected = religion == code,
                            onClick = { religion = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    religionOptions.drop(5).forEach { (code, label) ->
                        FilterChip(
                            selected = religion == code,
                            onClick = { religion = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isBlank() || email.isBlank() || password.length < 6) {
                            errorMsg = "Enter name, valid email, and a 6+ char password."
                            return@Button
                        }
                        isSaving = true
                        errorMsg = null
                        onSubmit(name, email, password, ageRating, birthday.trim().ifBlank { null }, religion) { err ->
                            isSaving = false
                            if (err != null) errorMsg = err
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(if (isSaving) "Creating Account..." else "Create Child Account")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditChildControlsView(
    child: ParentChildDto,
    globalControls: GlobalParentalControlsDto,
    onBack: () -> Unit,
    onWakeToddlerTv: ((String?) -> Unit) -> Unit = {},
    onSave: (Map<String, Any?>, (String?) -> Unit) -> Unit
) {
    var dailyLimit by remember { mutableStateOf(child.dailyLimitMinutes.toString()) }
    var pin by remember { mutableStateOf(child.parentPin) }
    var ageRating by remember { mutableStateOf(child.ageRating) }
    var attentionEnabled by remember { mutableStateOf(child.attentionTrackingEnabled) }
    var ytEnabled by remember { mutableStateOf(child.youtubeSearchEnabled) }
    var toddlerLoopMinutes by remember { mutableIntStateOf(child.toddlerLoopMinutes) }
    var toddlerCooldownMinutes by remember { mutableIntStateOf(child.toddlerCooldownMinutes) }
    var toddlerSubStage by remember { mutableStateOf(child.toddlerSubStage) }
    var contentLanguage by remember { mutableStateOf(child.contentLanguage) }
    var cultureMode by remember { mutableStateOf(child.cultureMode) }
    var religion by remember { mutableStateOf(child.religion) }
    var enabledTopics by remember {
        mutableStateOf(
            if (child.toddlerEnabledTopics.isNotEmpty()) child.toddlerEnabledTopics.toSet()
            else setOf("Music & Rhymes", "Animals & Nature", "Learning & ABCs", "Art & Crafts", "Kids Yoga & Active", "Stories & Reading")
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val allToddlerTopics = listOf(
        "Music & Rhymes",
        "Animals & Nature",
        "Learning & ABCs",
        "Art & Crafts",
        "Kids Yoga & Active",
        "Stories & Reading",
        "Cartoons & Fun"
    )

    fun doSave() {
        if (pin.length != 4) {
            errorMsg = "PIN must be 4 digits"
            return
        }
        isSaving = true
        onSave(
            mapOf(
                "dailyLimitMinutes" to (dailyLimit.toIntOrNull() ?: globalControls.dailyLimitMinutes),
                "parentPin" to pin,
                "ageRating" to ageRating,
                "attentionTrackingEnabled" to attentionEnabled,
                "youtubeSearchEnabled" to if (ageRating == "TODDLER") false else ytEnabled,
                "toddlerLoopMinutes" to toddlerLoopMinutes,
                "toddlerCooldownMinutes" to toddlerCooldownMinutes,
                "toddlerSubStage" to toddlerSubStage,
                "contentLanguage" to contentLanguage,
                "cultureMode" to cultureMode,
                "religion" to religion,
                "toddlerEnabledTopics" to allToddlerTopics.filter { it in enabledTopics }
            )
        ) { err ->
            isSaving = false
            if (err != null) errorMsg = err
        }
    }

    BackHandler(enabled = !isSaving) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${child.name} • Parental Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Individual settings & age tier overrides",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { doSave() },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isSaving) "Saving..." else "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = PinkPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Global defaults: ${globalControls.dailyLimitMinutes}m/day • ${globalControls.contentLanguage.uppercase()} • PIN ${globalControls.parentPin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Age Rating Tier", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("TODDLER" to "T1: 1.5-3", "YOUNG_KIDS" to "T2: 4-6", "OLDER_KIDS" to "T3: 7+", "ALL" to "All").forEach { (code, label) ->
                    FilterChip(
                        selected = ageRating == code,
                        onClick = { ageRating = code },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (ageRating == "TODDLER") {
                Text("Toddler TV Session Loop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 20).forEach { mins ->
                        FilterChip(
                            selected = toddlerLoopMinutes == mins,
                            onClick = { toddlerLoopMinutes = mins },
                            label = { Text("${mins}m", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Nap Cooldown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = toddlerCooldownMinutes == mins,
                            onClick = { toddlerCooldownMinutes = mins },
                            label = { Text("${mins}m", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Development Sub-stage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AUTO" to "Auto", "EARLY" to "1.5-2y", "OLDER" to "2-3y").forEach { (code, label) ->
                        FilterChip(
                            selected = toddlerSubStage == code,
                            onClick = { toddlerSubStage = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Language & Culture Mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All", "en" to "EN", "id" to "ID", "bilingual" to "EN+ID").forEach { (code, label) ->
                        FilterChip(
                            selected = contentLanguage == code,
                            onClick = { contentLanguage = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GLOBAL" to "Global", "ID" to "Indonesia").forEach { (code, label) ->
                        FilterChip(
                            selected = cultureMode == code,
                            onClick = { cultureMode = code },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Enabled TV Channels", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                allToddlerTopics.forEach { topic ->
                    val checked = topic in enabledTopics
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                enabledTopics = if (checked && enabledTopics.size > 1) {
                                    enabledTopics - topic
                                } else {
                                    enabledTopics + topic
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { next ->
                                enabledTopics = if (!next && enabledTopics.size > 1) {
                                    enabledTopics - topic
                                } else if (next) {
                                    enabledTopics + topic
                                } else enabledTopics
                            }
                        )
                        Text(topic, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedButton(
                    onClick = {
                        isSaving = true
                        onWakeToddlerTv { err ->
                            isSaving = false
                            if (err != null) errorMsg = err
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("☀️ Wake Up Toddler TV Now")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyLimit,
                        onValueChange = { if (it.all(Char::isDigit)) dailyLimit = it },
                        label = { Text("Daily Limit (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                        label = { Text("Parent PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Camera Attention Check", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Auto-pause video when child looks away", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = attentionEnabled, onCheckedChange = { attentionEnabled = it })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("YouTube Search Enabled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Allow searching allowed channels on YouTube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = ytEnabled, onCheckedChange = { ytEnabled = it })
                        }
                    }
                }
            }

            Text("Faith & Religious Content Filter", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "Optional faith filter. If None, videos and stories with religious themes are completely hidden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val relOptions = listOf(
                "NONE" to "None (Secular Only)",
                "ISLAM" to "Islam",
                "CHRISTIANITY" to "Christianity",
                "CATHOLICISM" to "Catholicism",
                "HINDUISM" to "Hinduism",
                "BUDDHISM" to "Buddhism",
                "JUDAISM" to "Judaism",
                "CONFUCIANISM" to "Confucianism"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                relOptions.take(2).forEach { (code, label) ->
                    FilterChip(
                        selected = if (code == "NONE") religion == null else religion == code,
                        onClick = { religion = if (code == "NONE") null else code },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                relOptions.slice(2..4).forEach { (code, label) ->
                    FilterChip(
                        selected = religion == code,
                        onClick = { religion = code },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                relOptions.drop(5).forEach { (code, label) ->
                    FilterChip(
                        selected = religion == code,
                        onClick = { religion = code },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    isSaving = true
                    onSave(
                        mapOf(
                            "parentPin" to null,
                            "dailyLimitMinutes" to null,
                            "ageRating" to null,
                            "religion" to null,
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

            Button(
                onClick = { doSave() },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(if (isSaving) "Saving Changes..." else "Save Changes", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildActivityHistoryView(
    child: ParentChildDto,
    repository: FamiTubeRepository,
    onPreviewVideo: (ParentVideoRequestDto) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${child.name} • Activity History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${child.ageRating.replace('_', ' ')} • Activity & Channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Parent Dashboard"
                        )
                    }
                },
                actions = {
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
                            Text("Clear Tab", color = PinkLight, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
                if (child.ageRating == "TODDLER") {
                    Text(
                        text = "Toddler TV Mode is linear & search-free (Watched history only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
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
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${req.channelTitle} • Status: ${req.status}",
                                                        fontSize = 11.sp,
                                                        color = when (req.status) {
                                                            "APPROVED" -> GreenSuccess
                                                            "REJECTED" -> PinkLight
                                                            else -> YellowWarning
                                                        }
                                                    )
                                                    OutlinedButton(
                                                        onClick = { onPreviewVideo(req) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text("Preview", fontSize = 10.sp)
                                                    }
                                                }
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
    }
}
