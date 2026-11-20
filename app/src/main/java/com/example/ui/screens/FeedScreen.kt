package com.example.ui.screens

import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.AsyncImage
import com.example.data.model.CommentItem
import com.example.data.model.UiState
import com.example.data.model.VideoPost
import com.example.media.FeedPlayerPool
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ReportDialog
import com.example.ui.components.UserAvatarWithFrame
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VipBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatTimeAr
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.DividerDark
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold
import com.example.ui.viewmodel.FfMainViewModel

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun FeedScreen(
    viewModel: FfMainViewModel,
    initialVideoId: String? = null,
    isScreenActive: Boolean = true,
    onOpenCreatorProfile: (String) -> Unit,
    onNavigateToPublish: () -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedState by viewModel.videosFeedState.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedVideoIds.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteVideoIds.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()

    val playerPool = remember(context) { FeedPlayerPool(context) }
    DisposableEffect(playerPool) {
        onDispose { playerPool.releaseAll() }
    }

    var isAppForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isAppForeground = false
                playerPool.pauseAll()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                isAppForeground = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var commentsVideo by remember { mutableStateOf<VideoPost?>(null) }
    var reportVideo by remember { mutableStateOf<VideoPost?>(null) }

    when (val state = feedState) {
        UiState.Loading -> {
            Box(Modifier.fillMaxSize().background(PitchBlack), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCrimson)
            }
        }
        is UiState.Error -> {
            EmptyStateView(
                title = "تعذر تحميل الفيديوهات",
                subtitle = state.message
            )
        }
        is UiState.Success -> {
            val videos = state.data
            if (videos.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد فيديوهات بعد",
                    subtitle = "كن أول صانع محتوى ينشر لقطة Free Fire أسطورية هنا!",
                    ctaLabel = "نشر أول فيديو الآن",
                    onCtaClick = onNavigateToPublish
                )
                return
            }

            val startIndex = remember(initialVideoId, videos) {
                if (initialVideoId != null) videos.indexOfFirst { it.id == initialVideoId }.coerceAtLeast(0)
                else 0
            }

            val pagerState = rememberPagerState(initialPage = startIndex) { videos.size }

            val videoPairs = remember(videos) { videos.map { it.id to it.videoUrl } }

            LaunchedEffect(pagerState.currentPage, videoPairs, isScreenActive, isAppForeground) {
                playerPool.syncActivePage(
                    videos = videoPairs,
                    activeIndex = pagerState.currentPage,
                    isScreenVisible = isScreenActive && isAppForeground
                )
                val current = videos.getOrNull(pagerState.currentPage)
                if (current != null && isScreenActive) {
                    viewModel.checkVideoLikeState(current.id)
                    viewModel.recordVideoView(current)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PitchBlack)
            ) {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val video = videos[page]
                    val player = remember(video.id, video.videoUrl) {
                        playerPool.getOrPreparePlayer(video.id, video.videoUrl)
                    }
                    var isPausedOverlayVisible by remember(video.id) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(video.id) {
                                detectTapGestures(
                                    onTap = {
                                        val nowPlaying = playerPool.togglePlayPause(video.id)
                                        isPausedOverlayVisible = !nowPlaying
                                    },
                                    onDoubleTap = {
                                        if (video.id !in likedIds) {
                                            viewModel.toggleLike(video)
                                        }
                                    }
                                )
                            }
                    ) {
                        ContentFrame(
                            player = player,
                            modifier = Modifier.fillMaxSize(),
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            contentScale = ContentScale.Crop,
                            shutter = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(PitchBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!video.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = video.coverUrl,
                                            contentDescription = "غلاف الفيديو",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    CircularProgressIndicator(color = NeonCrimson, modifier = Modifier.size(36.dp))
                                }
                            }
                        )

                        // Top & Bottom readability gradients
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(listOf(PitchBlack.copy(alpha = 0.7f), Color.Transparent))
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, PitchBlack.copy(alpha = 0.88f)))
                                )
                        )

                        // Play/Pause feedback badge
                        AnimatedVisibility(
                            visible = isPausedOverlayVisible,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PitchBlack.copy(alpha = 0.65f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "تشغيل الفيديو",
                                        tint = NeonCrimson,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                        }

                        // Top bar: FF CREATOR watermark + Mute/Unmute button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = PitchBlack.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(NeonCrimson, ElectricPurple)),
                                    RoundedCornerShape(12.dp)
                                )
                            ) {
                                Text(
                                    text = "FF CREATOR",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            IconButton(
                                onClick = { playerPool.toggleMute() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(PitchBlack.copy(alpha = 0.55f))
                                    .testTag("mute_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (playerPool.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (playerPool.isMuted) "فتح الصوت" else "كتم الصوت",
                                    tint = if (playerPool.isMuted) NeonCrimson else TextPrimary
                                )
                            }
                        }

                        // Bottom Overlay: Action Column + Creator Details
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 12.dp, bottom = 18.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Creator Info, Views & Description (Start side in RTL = Right)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "@${video.authorUsername}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onOpenCreatorProfile(video.userId) }
                                    )
                                    if (video.authorVerified) {
                                        VerifiedBadge(size = 17.dp)
                                    }
                                    if (video.authorVip) {
                                        VipBadge()
                                    }
                                    if (video.userId != viewModel.currentUserId) {
                                        val isFollowing = video.userId in followingIds
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isFollowing) CyberCard else NeonCrimson,
                                            modifier = Modifier
                                                .clickable { viewModel.toggleFollow(video.userId) }
                                                .testTag("feed_follow_button_${video.id}")
                                        ) {
                                            Text(
                                                text = if (isFollowing) "متابع ✓" else "متابعة",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = video.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (video.hashtags.isNotEmpty()) {
                                    @kotlin.OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        video.hashtags.forEach { tag ->
                                            Text(
                                                text = tag,
                                                color = NeonViolet,
                                                style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.clickable { onHashtagClick(tag) }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = "المشاهدات",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${formatCompactCount(video.viewsCount)} مشاهدة",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            // Side Vertical Interaction Rail (End side in RTL = Left)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                UserAvatarWithFrame(
                                    avatarUrl = video.authorAvatarUrl,
                                    displayName = video.authorName,
                                    vip = video.authorVip,
                                    vipFrame = video.authorVipFrame,
                                    size = 50.dp,
                                    onClick = { onOpenCreatorProfile(video.userId) }
                                )

                                val isLiked = video.id in likedIds
                                FeedActionItem(
                                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    label = formatCompactCount(video.likesCount),
                                    tint = if (isLiked) NeonCrimson else TextPrimary,
                                    testTag = "like_button_${video.id}",
                                    onClick = { viewModel.toggleLike(video) }
                                )

                                FeedActionItem(
                                    icon = Icons.AutoMirrored.Filled.Chat,
                                    label = formatCompactCount(video.commentsCount),
                                    tint = TextPrimary,
                                    testTag = "comments_button_${video.id}",
                                    onClick = { commentsVideo = video }
                                )

                                val isSaved = video.id in favoriteIds
                                FeedActionItem(
                                    icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    label = formatCompactCount(video.favoritesCount),
                                    tint = if (isSaved) VipGold else TextPrimary,
                                    testTag = "favorite_button_${video.id}",
                                    onClick = { viewModel.toggleFavorite(video) }
                                )

                                FeedActionItem(
                                    icon = Icons.Filled.Share,
                                    label = formatCompactCount(video.sharesCount),
                                    tint = TextPrimary,
                                    testTag = "share_button_${video.id}",
                                    onClick = {
                                        viewModel.recordShare(video.id)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "شاهد لقطة ${video.authorName} على تطبيق FF CREATOR:\n${video.description}\n${video.videoUrl}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفيديو"))
                                    }
                                )

                                IconButton(
                                    onClick = { reportVideo = video },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Flag,
                                        contentDescription = "إبلاغ عن الفيديو",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    commentsVideo?.let { targetVideo ->
        CommentsBottomSheet(
            video = targetVideo,
            viewModel = viewModel,
            onDismiss = { commentsVideo = null },
            onOpenProfile = { uid ->
                commentsVideo = null
                onOpenCreatorProfile(uid)
            }
        )
    }

    reportVideo?.let { target ->
        ReportDialog(
            title = "إبلاغ عن فيديو",
            onDismiss = { reportVideo = null },
            onSubmit = { reason ->
                viewModel.submitReport(
                    targetType = "video",
                    targetId = target.id,
                    targetLabel = "${target.authorUsername}: ${target.description.take(40)}",
                    reason = reason
                )
            }
        )
    }
}

@Composable
private fun FeedActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    video: VideoPost,
    viewModel: FfMainViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val comments by viewModel.observeComments(video.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommentItem?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "التعليقات (${comments.size})",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerDark)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد تعليقات بعد. اكتب أول تعليق!", color = TextSecondary)
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            canPin = video.userId == viewModel.currentUserId || viewModel.isOwnerAdmin,
                            onLike = { viewModel.toggleCommentLike(video.id, comment.id) },
                            onPin = { viewModel.togglePinComment(video.id, comment) },
                            onReply = {
                                replyingTo = comment
                                if (!commentText.contains("@${comment.authorUsername}")) {
                                    commentText = "@${comment.authorUsername} $commentText"
                                }
                            },
                            onAuthorClick = { onOpenProfile(comment.userId) }
                        )
                    }
                }
            }

            replyingTo?.let { parent ->
                Surface(
                    color = CyberCardElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الرد على @${parent.authorUsername}",
                            color = NeonViolet,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { replyingTo = null }) {
                            Text("إلغاء", color = NeonCrimson)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it.take(300) },
                    placeholder = { Text("اكتب تعليقاً أو أشر بـ @اسم_المستخدم...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input"),
                    maxLines = 2
                )
                IconButton(
                    onClick = {
                        val trimmed = commentText.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.addComment(video, trimmed, replyingTo)
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeonCrimson)
                        .testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال التعليق",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    canPin: Boolean,
    onLike: () -> Unit,
    onPin: () -> Unit,
    onReply: () -> Unit,
    onAuthorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (comment.parentCommentId != null) Modifier.padding(start = 24.dp) else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatarWithFrame(
            avatarUrl = comment.authorAvatarUrl,
            displayName = comment.authorName,
            vip = comment.authorVip,
            vipFrame = if (comment.authorVip) "violet_cyber" else "none",
            size = 36.dp,
            onClick = onAuthorClick
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.authorName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                )
                if (comment.authorVerified) VerifiedBadge(size = 14.dp)
                if (comment.authorVip) VipBadge()
                if (comment.pinned) {
                    Surface(
                        color = NeonCrimson.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "مثبت",
                            color = NeonCrimson,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (!comment.replyToUsername.isNullOrBlank()) {
                Text(
                    text = "رداً على @${comment.replyToUsername}",
                    color = NeonViolet,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = comment.text,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = formatTimeAr(comment.createdAt),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "رد",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable(onClick = onReply)
                )
                if (canPin) {
                    Row(
                        modifier = Modifier.clickable(onClick = onPin),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "تثبيت",
                            tint = if (comment.pinned) NeonCrimson else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (comment.pinned) "إلغاء التثبيت" else "تثبيت",
                            color = if (comment.pinned) NeonCrimson else TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onLike).padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = "إعجاب بالتعليق",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = comment.likesCount.toString(),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
