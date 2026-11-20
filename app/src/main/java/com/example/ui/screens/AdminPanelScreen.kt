package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UiState
import com.example.data.model.VerificationRequest
import com.example.data.model.VipRequest
import com.example.ui.components.EmptyStateView
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VipBadge
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: FfMainViewModel,
    onBack: () -> Unit
) {
    // Hard UI-level guard in addition to Firestore Security Rules
    if (!viewModel.isOwnerAdmin) {
        EmptyStateView(
            title = "الوصول مرفوض",
            subtitle = "هذه اللوحة مخصصة لحساب المالك الأساسي فقط."
        )
        return
    }

    val stats by viewModel.adminStats.collectAsStateWithLifecycle()
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val verifications by viewModel.adminVerifications.collectAsStateWithLifecycle()
    val vipRequests by viewModel.adminVipRequests.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val videosState by viewModel.videosFeedState.collectAsStateWithLifecycle()
    val hashtags by viewModel.trendingHashtags.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الإحصائيات والبث", "التوثيق", "VIP", "المستخدمون", "البلاغات والفيديوهات", "الهاشتاجات")

    var rejectingVer by remember { mutableStateOf<VerificationRequest?>(null) }
    var rejectingVip by remember { mutableStateOf<VipRequest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = NeonCrimson)
                    Text("لوحة تحكم المالك", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberCard)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PitchBlack,
            contentColor = TextPrimary,
            edgePadding = 12.dp,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                    color = NeonCrimson
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> AdminOverviewAndBroadcastTab(
                stats = stats,
                onBroadcast = { title, body, onDone -> viewModel.adminBroadcast(title, body, onDone) }
            )
            1 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(verifications, key = { it.id }) { req ->
                    Surface(color = CyberCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${req.displayName} (@${req.username})", color = TextPrimary, fontWeight = FontWeight.Bold)
                                StatusPill(req.status)
                            }
                            Text("معرف Free Fire: ${req.ffPlayerId}", color = VerifiedBlue, style = MaterialTheme.typography.bodySmall)
                            Text("المتابعون: ${req.followersCount}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("السبب: ${req.reason}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            if (req.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.adminReviewVerification(req, true, null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = PitchBlack)
                                        Text("قبول وتوثيق", color = PitchBlack, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { rejectingVer = req },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = null, tint = NeonCrimson)
                                        Text("رفض", color = NeonCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vipRequests, key = { it.id }) { req ->
                    Surface(color = CyberCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${req.displayName} (@${req.username})", color = TextPrimary, fontWeight = FontWeight.Bold)
                                StatusPill(req.status)
                            }
                            Text("الباقة: ${req.planTier} • الإطار: ${req.selectedFrame}", color = VipGold, style = MaterialTheme.typography.bodySmall)
                            if (!req.note.isNullOrBlank()) {
                                Text("ملاحظة: ${req.note}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (req.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.adminReviewVip(req, true, null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = VipGold),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("موافقة وتفعيل VIP", color = PitchBlack, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { rejectingVip = req },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("رفض", color = NeonCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                var userQuery by remember { mutableStateOf("") }
                val filtered = users.filter {
                    userQuery.isBlank() || it.username.contains(userQuery, true) || it.displayName.contains(userQuery, true)
                }
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = userQuery,
                        onValueChange = { userQuery = it },
                        label = { Text("بحث في المستخدمين...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.userId }) { user ->
                            Surface(color = CyberCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(user.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            if (user.verified) VerifiedBadge(size = 14.dp)
                                            if (user.vip) VipBadge()
                                        }
                                        Text("@${user.username} • ${user.followersCount} متابع", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (user.userId != viewModel.currentUserId) {
                                        Button(
                                            onClick = { viewModel.adminToggleBanUser(user) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (user.banned) EmeraldSuccess else NeonCrimson
                                            )
                                        ) {
                                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(if (user.banned) "فك الحظر" else "حظر")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            4 -> {
                val videos = (videosState as? UiState.Success)?.data.orEmpty()
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("البلاغات (${reports.size})", color = NeonCrimson, style = MaterialTheme.typography.titleMedium) }
                    items(reports, key = { it.id }) { rep ->
                        Surface(color = CyberCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${rep.targetType}: ${rep.targetLabel}", color = TextPrimary, fontWeight = FontWeight.Bold)
                                    StatusPill(rep.status)
                                }
                                Text("السبب: ${rep.reason}", color = TextSecondary)
                                if (rep.status == "open") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (rep.targetType == "video") {
                                            Button(
                                                onClick = {
                                                    viewModel.deleteVideo(rep.targetId)
                                                    viewModel.adminResolveReport(rep.id, "resolved", "تم حذف الفيديو")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson)
                                            ) {
                                                Text("حذف الفيديو وإغلاق")
                                            }
                                        }
                                        OutlinedButton(onClick = { viewModel.adminResolveReport(rep.id, "dismissed", "تمت المراجعة") }) {
                                            Text("تجاهل البلاغ", color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("إدارة الفيديوهات (${videos.size})", color = ElectricPurple, style = MaterialTheme.typography.titleMedium)
                    }
                    items(videos, key = { it.id }) { vid ->
                        Surface(color = CyberCardElevated, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("@${vid.authorUsername}", color = NeonViolet, fontWeight = FontWeight.Bold)
                                    Text(vid.description, color = TextPrimary, maxLines = 2)
                                }
                                IconButton(onClick = { viewModel.deleteVideo(vid.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف الفيديو", tint = NeonCrimson)
                                }
                            }
                        }
                    }
                }
            }
            5 -> {
                var newTag by remember { mutableStateOf("") }
                var isFeatured by remember { mutableStateOf(true) }
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it.take(40) },
                            label = { Text("هاشتاج رائج جديد") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    viewModel.adminSaveHashtag(newTag, isFeatured)
                                    newTag = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson)
                        ) {
                            Text("حفظ")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFeatured, onCheckedChange = { isFeatured = it })
                        Text("تثبيت كهاشتاج رائج مميز", color = TextPrimary)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(hashtags, key = { it.id }) { tag ->
                            Surface(color = CyberCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (tag.featured) Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = NeonCrimson)
                                        Text("${tag.tag} (${tag.usageCount})", color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Row {
                                        TextButton(onClick = { viewModel.adminSaveHashtag(tag.tag, !tag.featured) }) {
                                            Text(if (tag.featured) "إلغاء التمييز" else "تمييز", color = ElectricPurple)
                                        }
                                        IconButton(onClick = { viewModel.adminDeleteHashtag(tag.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = NeonCrimson)
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

    rejectingVer?.let { req ->
        RejectReasonDialog(
            title = "رفض طلب توثيق @${req.username}",
            onDismiss = { rejectingVer = null },
            onConfirm = { reason ->
                viewModel.adminReviewVerification(req, false, reason)
                rejectingVer = null
            }
        )
    }

    rejectingVip?.let { req ->
        RejectReasonDialog(
            title = "رفض طلب VIP لـ @${req.username}",
            onDismiss = { rejectingVip = null },
            onConfirm = { reason ->
                viewModel.adminReviewVip(req, false, reason)
                rejectingVip = null
            }
        )
    }
}

@Composable
private fun AdminOverviewAndBroadcastTab(
    stats: com.example.data.model.AdminDashboardStats,
    onBroadcast: (String, String, () -> Unit) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("إحصائيات المنصة المباشرة", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("المستخدمون", stats.totalUsers.toString(), NeonCrimson, Modifier.weight(1f))
                AdminStatCard("الموثقون", stats.verifiedUsers.toString(), VerifiedBlue, Modifier.weight(1f))
                AdminStatCard("VIP", stats.vipUsers.toString(), VipGold, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("الفيديوهات", stats.totalVideos.toString(), ElectricPurple, Modifier.weight(1f))
                AdminStatCard("المشاهدات", stats.totalViews.toString(), EmeraldSuccess, Modifier.weight(1f))
                AdminStatCard("المحظورون", stats.bannedUsers.toString(), NeonCrimson, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("طلبات التوثيق", stats.pendingVerifications.toString(), AmberWarning, Modifier.weight(1f))
                AdminStatCard("طلبات VIP", stats.pendingVipRequests.toString(), VipGold, Modifier.weight(1f))
                AdminStatCard("البلاغات المفتوحة", stats.openReports.toString(), NeonCrimson, Modifier.weight(1f))
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = CyberCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeonCrimson.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = NeonCrimson)
                        Text("إرسال إشعار لجميع المستخدمين", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(100) },
                        label = { Text("عنوان الإشعار") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broadcast_title_input")
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(300) },
                        label = { Text("نص الإشعار") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("broadcast_body_input")
                    )
                    Button(
                        onClick = {
                            onBroadcast(title, body) {
                                title = ""
                                body = ""
                            }
                        },
                        enabled = title.isNotBlank() && body.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                        modifier = Modifier.fillMaxWidth().testTag("send_broadcast_button")
                    ) {
                        Text("إرسال للجميع الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = CyberCard,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, col) = when (status) {
        "approved", "resolved" -> "مقبول / محلول" to EmeraldSuccess
        "rejected", "dismissed" -> "مرفوض" to NeonCrimson
        else -> "قيد الانتظار" to AmberWarning
    }
    Surface(color = col.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            color = col,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun RejectReasonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(300) },
                label = { Text("سبب الرفض (يصل للمستخدم)") },
                minLines = 2
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.ifBlank { "لا يستوفي الشروط الحالية" }) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson)
            ) {
                Text("تأكيد الرفض")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) }
        }
    )
}
