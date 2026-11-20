package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.UserAvatarWithFrame
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatTimeAr
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FfMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: FfMainViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val privateInfo by viewModel.privateInfo.collectAsStateWithLifecycle()
    val blocked = privateInfo?.blockedUserIds.orEmpty().toSet()

    val otherUsers = remember(allUsers, viewModel.currentUserId) {
        allUsers.filter { it.userId != viewModel.currentUserId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        TopAppBar(
            title = { Text("الرسائل الخاصة", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchBlack)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chats.isNotEmpty()) {
                item {
                    Text(
                        text = "المحادثات الأخيرة",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCrimson
                    )
                }
                items(chats, key = { it.id }) { thread ->
                    val otherUid = thread.participantIds.firstOrNull { it != viewModel.currentUserId } ?: ""
                    val otherProfile = allUsers.firstOrNull { it.userId == otherUid }
                    val name = otherProfile?.displayName ?: thread.participantNames[otherUid] ?: "لاعب"
                    val avatar = otherProfile?.avatarUrl ?: thread.participantAvatars[otherUid]
                    val isBlocked = otherUid in blocked

                    Surface(
                        color = CyberCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricPurple.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable(enabled = otherUid.isNotBlank()) { onOpenChat(otherUid) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            UserAvatarWithFrame(
                                avatarUrl = avatar,
                                displayName = name,
                                vip = otherProfile?.vip == true,
                                vipFrame = otherProfile?.vipFrame ?: "none",
                                size = 48.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isBlocked) "$name (محظور)" else name,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatTimeAr(thread.updatedAt),
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = thread.lastMessage,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (otherUsers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "بدء محادثة مع لاعب",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricPurple
                    )
                }
                items(otherUsers, key = { it.userId }) { user ->
                    Surface(
                        color = CyberCardElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(user.userId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UserAvatarWithFrame(
                                avatarUrl = user.avatarUrl,
                                displayName = user.displayName,
                                vip = user.vip,
                                vipFrame = user.vipFrame,
                                size = 40.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(user.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    if (user.verified) VerifiedBadge(size = 14.dp)
                                }
                                Text("@${user.username}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("مراسلة", color = NeonCrimson, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else if (chats.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "لا توجد محادثات بعد",
                        subtitle = "عند انضمام لاعبين آخرين يمكنك مراسلتهم مباشرة هنا."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: FfMainViewModel,
    otherUserId: String,
    onBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val privateInfo by viewModel.privateInfo.collectAsStateWithLifecycle()
    val otherUser = allUsers.firstOrNull { it.userId == otherUserId }
        ?: UserProfile(userId = otherUserId, username = "player", displayName = "لاعب Free Fire")

    val chatId = remember(otherUserId, viewModel.currentUserId) { viewModel.chatIdFor(otherUserId) }
    val messages by viewModel.observeChatMessages(chatId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val isBlocked = otherUserId in (privateInfo?.blockedUserIds.orEmpty())
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .imePadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserAvatarWithFrame(
                        avatarUrl = otherUser.avatarUrl,
                        displayName = otherUser.displayName,
                        vip = otherUser.vip,
                        vipFrame = otherUser.vipFrame,
                        size = 36.dp
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(otherUser.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            if (otherUser.verified) VerifiedBadge(size = 14.dp)
                        }
                        Text("@${otherUser.username}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.toggleBlockUser(otherUserId) },
                    modifier = Modifier.testTag("chat_block_button")
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                        contentDescription = if (isBlocked) "فك الحظر" else "حظر المستخدم",
                        tint = if (isBlocked) ElectricPurple else NeonCrimson
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberCard)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == viewModel.currentUserId
                MessageBubble(message = msg, isMine = isMine)
            }
        }

        if (isBlocked) {
            Surface(
                color = NeonCrimson.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "لقد قمت بحظر هذا المستخدم. اضغط على أيقونة فك الحظر بالأعلى لإرسال الرسائل.",
                    color = NeonCrimson,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it.take(1000) },
                    placeholder = { Text("اكتب رسالة...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    maxLines = 3
                )
                IconButton(
                    onClick = {
                        val trimmed = messageInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.sendMessage(otherUser, trimmed)
                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeonCrimson)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isMine) NeonCrimson else CyberCardElevated,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimeAr(message.createdAt),
                    color = TextPrimary.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
