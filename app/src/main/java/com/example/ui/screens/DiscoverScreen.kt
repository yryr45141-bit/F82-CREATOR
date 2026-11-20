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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.ui.components.EmptyStateView
import com.example.ui.components.UserAvatarWithFrame
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VipBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FfMainViewModel

@Composable
fun DiscoverScreen(
    viewModel: FfMainViewModel,
    initialHashtag: String? = null,
    onOpenVideo: (String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit
) {
    val videosState by viewModel.videosFeedState.collectAsStateWithLifecycle()
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val hashtags by viewModel.trendingHashtags.collectAsStateWithLifecycle()

    var query by remember(initialHashtag) { mutableStateOf(initialHashtag ?: "") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Trending, 1 = Most Viewed, 2 = Players

    val allVideos = (videosState as? UiState.Success)?.data.orEmpty()
    val filteredVideos = remember(allVideos, query, selectedTab) {
        val q = query.trim().lowercase()
        val matched = if (q.isBlank()) allVideos else {
            allVideos.filter { v ->
                v.description.lowercase().contains(q) ||
                    v.authorUsername.lowercase().contains(q) ||
                    v.authorName.lowercase().contains(q) ||
                    v.hashtags.any { it.lowercase().contains(q) }
            }
        }
        when (selectedTab) {
            1 -> matched.sortedByDescending { it.viewsCount }
            else -> matched.sortedByDescending {
                (it.likesCount * 3L) + (it.commentsCount * 2L) + it.viewsCount
            }
        }
    }

    val filteredUsers = remember(users, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) users else {
            users.filter { u ->
                u.username.lowercase().contains(q) ||
                    u.displayName.lowercase().contains(q) ||
                    u.bio.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "اكتشف مجتمع Free Fire",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث عن اللاعبين أو الفيديوهات أو #الهاشتاج...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NeonCrimson) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_search_input")
            )
        }

        // Trending Hashtags Row
        if (hashtags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hashtags, key = { it.id }) { tag ->
                    val isSelected = query.equals(tag.tag, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { query = if (isSelected) "" else tag.tag },
                        label = {
                            Text("${tag.tag} (${formatCompactCount(tag.usageCount)})")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (tag.featured) Icons.Filled.LocalFireDepartment else Icons.Filled.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (tag.featured) NeonCrimson else NeonViolet
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCrimson.copy(alpha = 0.25f),
                            containerColor = CyberCard
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الرائجة") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("الأكثر مشاهدة") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("اللاعبون (${filteredUsers.size})") })
        }

        if (selectedTab == 2) {
            if (filteredUsers.isEmpty()) {
                EmptyStateView(
                    title = "لا يوجد لاعبون مطابقون",
                    subtitle = "جرب البحث باسم مستخدم مختلف"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredUsers, key = { it.userId }) { player ->
                        PlayerSearchCard(
                            player = player,
                            onClick = { onOpenCreatorProfile(player.userId) }
                        )
                    }
                }
            }
        } else {
            if (filteredVideos.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد فيديوهات مطابقة",
                    subtitle = "انشر فيديو أو ابحث بكلمات مفتاحية أخرى"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredVideos, key = { it.id }) { video ->
                        DiscoverVideoCard(
                            video = video,
                            onClick = { onOpenVideo(video.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverVideoCard(video: VideoPost, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ElectricPurple.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("discover_video_${video.id}"),
        color = CyberCard
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!video.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = video.description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(CyberCardElevated, PitchBlack))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = NeonCrimson,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(PitchBlack.copy(alpha = 0.05f), PitchBlack.copy(alpha = 0.9f)))
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PitchBlack.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                Text(
                    text = formatCompactCount(video.viewsCount),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "@${video.authorUsername}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.authorVerified) VerifiedBadge(size = 13.dp)
                }
                Text(
                    text = video.description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlayerSearchCard(player: UserProfile, onClick: () -> Unit) {
    Surface(
        color = CyberCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricPurple.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("discover_player_${player.userId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatarWithFrame(
                avatarUrl = player.avatarUrl,
                displayName = player.displayName,
                vip = player.vip,
                vipFrame = player.vipFrame,
                size = 52.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = player.displayName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (player.verified) VerifiedBadge(size = 15.dp)
                    if (player.vip) VipBadge()
                }
                Text(
                    text = "@${player.username}",
                    color = NeonViolet,
                    style = MaterialTheme.typography.bodySmall
                )
                if (player.bio.isNotBlank()) {
                    Text(
                        text = player.bio,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCompactCount(player.followersCount),
                    color = NeonCrimson,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("متابع", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
