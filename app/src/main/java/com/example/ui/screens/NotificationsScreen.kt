package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppNotification
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatTimeAr
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedBlue
import com.example.ui.theme.VipGold
import com.example.ui.viewmodel.FfMainViewModel

@Composable
fun NotificationsScreen(
    viewModel: FfMainViewModel,
    onOpenChats: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenDirectChat: (String) -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الإشعارات",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            Button(
                onClick = onOpenChats,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("open_messages_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("الرسائل (${chats.size})", fontWeight = FontWeight.Bold)
            }
        }

        if (notifications.isEmpty()) {
            EmptyStateView(
                title = "لا توجد إشعارات حالياً",
                subtitle = "ستصلك هنا تنبيهات الإعجابات والتعليقات والمتابعين الجدد والرسائل وحالة التوثيق وVIP."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    NotificationCard(
                        notification = notif,
                        onClick = {
                            if (!notif.read) viewModel.markNotificationRead(notif.id)
                            when (notif.type) {
                                "like", "comment", "reply", "mention" -> {
                                    notif.targetId?.let(onOpenVideo)
                                }
                                "follow" -> {
                                    notif.targetId?.let(onOpenProfile)
                                }
                                "message" -> {
                                    notif.targetId?.let(onOpenDirectChat) ?: onOpenChats()
                                }
                                "verification", "vip" -> {
                                    onOpenProfile(viewModel.currentUserId)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val (icon, tint) = notificationVisuals(notification.type)

    Surface(
        color = if (notification.read) CyberCard else CyberCardElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (notification.read) Color.Transparent else tint.copy(alpha = 0.55f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("notification_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.25f), PitchBlack))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimeAr(notification.createdAt),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = notification.message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!notification.read) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NeonCrimson)
                )
            }
        }
    }
}

private fun notificationVisuals(type: String): Pair<ImageVector, Color> = when (type) {
    "like" -> Icons.Filled.Favorite to NeonCrimson
    "comment", "reply" -> Icons.AutoMirrored.Filled.Chat to ElectricPurple
    "follow" -> Icons.Filled.PersonAdd to VerifiedBlue
    "mention" -> Icons.Filled.AlternateEmail to ElectricPurple
    "message" -> Icons.AutoMirrored.Filled.Message to NeonCrimson
    "verification" -> Icons.Filled.Verified to VerifiedBlue
    "vip" -> Icons.Filled.AutoAwesome to VipGold
    "broadcast" -> Icons.Filled.Campaign to NeonCrimson
    else -> Icons.Filled.NotificationsActive to TextPrimary
}
