package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.data.model.VipFrameStyle
import com.example.data.model.VipPlanTier
import com.example.ui.components.ReportDialog
import com.example.ui.components.UserAvatarWithFrame
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VipBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedBlue
import com.example.ui.theme.VipGold
import com.example.ui.viewmodel.FfMainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: FfMainViewModel,
    targetUserId: String,
    isStandalonePage: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenVideo: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val isMe = targetUserId == viewModel.currentUserId
    val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val videosFeedState by viewModel.videosFeedState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteVideoIds.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val privateInfo by viewModel.privateInfo.collectAsStateWithLifecycle()
    val myVerRequests by viewModel.myVerificationRequests.collectAsStateWithLifecycle()
    val myVipRequests by viewModel.myVipRequests.collectAsStateWithLifecycle()

    val profile = if (isMe) {
        (myProfileState as? UiState.Success)?.data
            ?: UserProfile(userId = viewModel.currentUserId, displayName = "بطل Free Fire")
    } else {
        allUsers.firstOrNull { it.userId == targetUserId }
            ?: UserProfile(userId = targetUserId, displayName = "لاعب Free Fire")
    }

    val allVideos = (videosFeedState as? UiState.Success)?.data.orEmpty()
    val userVideos = remember(allVideos, targetUserId) {
        allVideos.filter { it.userId == targetUserId }
    }
    val favoriteVideos = remember(allVideos, favoriteIds) {
        allVideos.filter { it.id in favoriteIds }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var showVipDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .then(if (!isStandalonePage) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
    ) {
        if (isStandalonePage && onBack != null) {
            TopAppBar(
                title = { Text("@${profile.username}", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchBlack)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (profile.banned) {
                Surface(
                    color = NeonCrimson.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "تم تقييد هذا الحساب من قبل الإدارة",
                        color = NeonCrimson,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            UserAvatarWithFrame(
                avatarUrl = profile.avatarUrl,
                displayName = profile.displayName,
                vip = profile.vip,
                vipFrame = profile.vipFrame,
                size = 86.dp
            )
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                if (profile.verified) VerifiedBadge(size = 20.dp)
                if (profile.vip) VipBadge()
            }

            Text(
                text = "@${profile.username}",
                color = NeonViolet,
                style = MaterialTheme.typography.bodyMedium
            )
            if (profile.bio.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.bio,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(14.dp))

            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberCard)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell("متابِعون", profile.followersCount)
                StatCell("يتابع", profile.followingCount)
                StatCell("الإعجابات", profile.likesCount)
                StatCell("الفيديوهات", userVideos.size.toLong())
            }

            Spacer(Modifier.height(12.dp))

            if (isMe) {
                // Action Row 1: Edit Profile + Verification + VIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("edit_profile_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                        Spacer(Modifier.size(4.dp))
                        Text("تعديل", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { showVerificationDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedBlue.copy(alpha = 0.22f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_verification_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp), tint = VerifiedBlue)
                        Spacer(Modifier.size(4.dp))
                        Text("التوثيق", color = VerifiedBlue, style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { showVipDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_vip_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = VipGold)
                        Spacer(Modifier.size(4.dp))
                        Text("VIP", color = VipGold, style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Action Row 2: Account Recovery + Sign Out (+ Owner Admin Panel ONLY for y27985332@gmail.com)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.recoverAccount(Firebase.auth.currentUser) },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                        Spacer(Modifier.size(4.dp))
                        Text("استعادة الحساب", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = {
                            AuthManager.signOut(credentialManager, scope)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCrimson)
                        Spacer(Modifier.size(4.dp))
                        Text("تسجيل الخروج", color = NeonCrimson, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Strictly gated Owner Admin Panel button
                if (viewModel.isOwnerAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAdminPanel,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("owner_admin_panel_button")
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("لوحة تحكم المالك (Admin Panel)", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val isFollowing = targetUserId in followingIds
                val isBlocked = targetUserId in (privateInfo?.blockedUserIds.orEmpty())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleFollow(targetUserId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) CyberCardElevated else NeonCrimson
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isFollowing) "متابع ✓" else "متابعة", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onOpenChat(targetUserId) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("رسالة", fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { viewModel.toggleBlockUser(targetUserId) },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberCard)
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = if (isBlocked) "فك الحظر" else "حظر",
                            tint = if (isBlocked) NeonCrimson else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberCard)
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = "إبلاغ عن الحساب", tint = TextSecondary)
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PitchBlack,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = NeonCrimson
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الفيديوهات (${userVideos.size})") })
            if (isMe) {
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("المفضلة (${favoriteVideos.size})") })
            }
        }

        val displayedVideos = if (selectedTab == 1 && isMe) favoriteVideos else userVideos
        if (displayedVideos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedTab == 1) "لم تحفظ أي فيديو في المفضلة بعد" else "لا توجد فيديوهات منشورة بعد",
                    color = TextSecondary
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedVideos, key = { it.id }) { video ->
                    ProfileVideoTile(
                        video = video,
                        canDelete = isMe || viewModel.isOwnerAdmin,
                        onOpen = { onOpenVideo(video.id) },
                        onDelete = { viewModel.deleteVideo(video.id) }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { username, displayName, bio, avatarUri, frame ->
                viewModel.updateProfile(context, username, displayName, bio, avatarUri, frame) {
                    showEditDialog = false
                }
            }
        )
    }

    if (showVerificationDialog) {
        VerificationDialog(
            profile = profile,
            requests = myVerRequests,
            onDismiss = { showVerificationDialog = false },
            onSubmit = { ffId, reason ->
                viewModel.submitVerification(ffId, reason) {
                    showVerificationDialog = false
                }
            }
        )
    }

    if (showVipDialog) {
        VipSubscriptionDialog(
            profile = profile,
            requests = myVipRequests,
            onDismiss = { showVipDialog = false },
            onSubmit = { tier, frame, note ->
                viewModel.submitVip(tier, frame, note) {
                    showVipDialog = false
                }
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            title = "إبلاغ عن حساب @${profile.username}",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                viewModel.submitReport("user", profile.userId, "@${profile.username}", reason)
            }
        )
    }
}

@Composable
private fun StatCell(label: String, count: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCompactCount(count),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileVideoTile(
    video: VideoPost,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(10.dp))
            .background(CyberCard)
            .clickable(onClick = onOpen)
    ) {
        if (!video.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(32.dp))
            }
        }
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .background(PitchBlack.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف الفيديو", tint = NeonCrimson, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = "${formatCompactCount(video.viewsCount)} 👁",
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(PitchBlack.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Uri?, String?) -> Unit
) {
    var username by remember { mutableStateOf(profile.username) }
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFrame by remember { mutableStateOf(profile.vipFrame) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedAvatarUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = { Text("تعديل الملف الشخصي", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedAvatarUri != null) "تم اختيار صورة جديدة ✓" else "تغيير الصورة الشخصية", color = NeonViolet)
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(24) },
                    label = { Text("اسم المستخدم الفريد (بالإنجليزية)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(40) },
                    label = { Text("الاسم المعروض") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(200) },
                    label = { Text("النبذة الشخصية") },
                    minLines = 2
                )

                if (profile.vip) {
                    Text("إطار VIP الحصري:", color = VipGold, style = MaterialTheme.typography.labelLarge)
                    VipFrameStyle.entries.filter { it != VipFrameStyle.NONE }.forEach { frame ->
                        Surface(
                            color = if (selectedFrame == frame.id) ElectricPurple.copy(alpha = 0.25f) else CyberCardElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFrame = frame.id }
                        ) {
                            Text(
                                text = frame.labelAr,
                                color = if (selectedFrame == frame.id) VipGold else TextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, displayName, bio, selectedAvatarUri, if (profile.vip) selectedFrame else null) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson)
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) }
        }
    )
}

@Composable
private fun VerificationDialog(
    profile: UserProfile,
    requests: List<com.example.data.model.VerificationRequest>,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var ffPlayerId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val latest = requests.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerifiedBadge(size = 22.dp)
                Text("توثيق الحساب", color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (profile.verified) {
                    Text("حسابك موثق بالعلامة الزرقاء بالفعل!", color = VerifiedBlue, fontWeight = FontWeight.Bold)
                } else {
                    if (latest != null) {
                        val (statusLabel, statusColor) = when (latest.status) {
                            "approved" -> "مقبول ✓" to EmeraldSuccess
                            "rejected" -> "مرفوض" to NeonCrimson
                            else -> "قيد المراجعة..." to AmberWarning
                        }
                        Surface(color = CyberCardElevated, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("حالة آخر طلب: $statusLabel", color = statusColor, fontWeight = FontWeight.Bold)
                                if (!latest.rejectionReason.isNullOrBlank()) {
                                    Text("سبب الرفض: ${latest.rejectionReason}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (latest?.status != "pending") {
                        OutlinedTextField(
                            value = ffPlayerId,
                            onValueChange = { ffPlayerId = it.take(20) },
                            label = { Text("معرف اللاعب في Free Fire (UID)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it.take(400) },
                            label = { Text("لماذا تستحق علامة التوثيق الزرقاء؟") },
                            minLines = 2
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!profile.verified && latest?.status != "pending") {
                Button(
                    onClick = { onSubmit(ffPlayerId, reason) },
                    enabled = ffPlayerId.trim().length >= 4 && reason.trim().length >= 5,
                    colors = ButtonDefaults.buttonColors(containerColor = VerifiedBlue)
                ) {
                    Text("إرسال للإدارة", color = PitchBlack, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = TextSecondary) }
        }
    )
}

@Composable
private fun VipSubscriptionDialog(
    profile: UserProfile,
    requests: List<com.example.data.model.VipRequest>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var selectedTier by remember { mutableStateOf(VipPlanTier.SEASONAL) }
    var selectedFrame by remember { mutableStateOf(VipFrameStyle.CRIMSON_FLAME) }
    var note by remember { mutableStateOf("") }
    val latest = requests.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VipGold)
                Text("عضوية VIP الحصرية", color = VipGold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vip_banner),
                    contentDescription = "VIP Crest",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )

                if (profile.vip) {
                    Text("أنت عضو VIP نشط! يمكنك تغيير إطارك من تعديل الملف الشخصي.", color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                } else {
                    if (latest != null) {
                        val (label, col) = when (latest.status) {
                            "approved" -> "مفعل ✓" to EmeraldSuccess
                            "rejected" -> "مرفوض" to NeonCrimson
                            else -> "بانتظار موافقة المالك..." to AmberWarning
                        }
                        Surface(color = CyberCardElevated, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text("حالة الطلب: $label", color = col, fontWeight = FontWeight.Bold)
                                if (!latest.rejectionReason.isNullOrBlank()) {
                                    Text("السبب: ${latest.rejectionReason}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (latest?.status != "pending") {
                        Text("اختر الباقة:", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                        VipPlanTier.entries.forEach { tier ->
                            Surface(
                                color = if (selectedTier == tier) ElectricPurple.copy(alpha = 0.25f) else CyberCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (selectedTier == tier) VipGold else PitchBlack,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedTier = tier }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("${tier.titleAr} • ${tier.durationAr}", color = VipGold, fontWeight = FontWeight.Bold)
                                    tier.perksAr.forEach { perk ->
                                        Text("• $perk", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Text("اختر الإطار الحصري:", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                        VipFrameStyle.entries.filter { it != VipFrameStyle.NONE }.forEach { frame ->
                            Surface(
                                color = if (selectedFrame == frame) NeonCrimson.copy(alpha = 0.22f) else CyberCardElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFrame = frame }
                            ) {
                                Text(frame.labelAr, color = TextPrimary, modifier = Modifier.padding(10.dp))
                            }
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            label = { Text("ملاحظة للمالك (اختياري)") },
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!profile.vip && latest?.status != "pending") {
                Button(
                    onClick = { onSubmit(selectedTier.id, selectedFrame.id, note) },
                    colors = ButtonDefaults.buttonColors(containerColor = VipGold)
                ) {
                    Text("تقديم طلب VIP", color = PitchBlack, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = TextSecondary) }
        }
    )
}
