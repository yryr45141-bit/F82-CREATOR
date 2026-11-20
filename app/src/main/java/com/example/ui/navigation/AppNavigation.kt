package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.firebase.AuthManager.authStateFlow
import com.example.data.firebase.FfRepository
import com.example.notifications.LocalNotificationHelper
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.PublishScreen
import com.example.ui.theme.CyberCard
import com.example.ui.theme.DividerDark
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.FfMainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
data class HomeFeedRoute(val initialVideoId: String? = null)

@Serializable
data class DiscoverRoute(val hashtag: String? = null)

@Serializable
data object PublishRoute

@Serializable
data object NotificationsRoute

@Serializable
data object MyProfileRoute

@Serializable
data class CreatorProfileRoute(val userId: String)

@Serializable
data object ChatsListRoute

@Serializable
data class ChatDetailRoute(val otherUserId: String)

@Serializable
data object AdminPanelRoute

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppShell(user = user)
        }
    }
}

@Composable
private fun AuthenticatedAppShell(user: FirebaseUser) {
    val context = LocalContext.current
    val viewModel: FfMainViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val repository = FfRepository(app)
                FfMainViewModel(
                    repository = repository,
                    currentUserId = user.uid,
                    currentUserEmail = user.email,
                    firebaseUser = user
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount = remember(notifications) { notifications.count { !it.read } }
    var seenNotifIds by remember { mutableStateOf<Set<String>?>(null) }

    // Trigger local Android system notification for newly arrived unread alerts
    LaunchedEffect(notifications) {
        val currentIds = notifications.map { it.id }.toSet()
        val prev = seenNotifIds
        if (prev == null) {
            seenNotifIds = currentIds
        } else {
            val newUnread = notifications.filter { !it.read && it.id !in prev }
            newUnread.take(2).forEach { notif ->
                LocalNotificationHelper.showNotification(
                    context = context,
                    idSeed = notif.id,
                    title = notif.title,
                    body = notif.message
                )
            }
            seenNotifIds = currentIds
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isMainTab = currentDestination?.let { dest ->
        dest.hasRoute<HomeFeedRoute>() ||
            dest.hasRoute<DiscoverRoute>() ||
            dest.hasRoute<PublishRoute>() ||
            dest.hasRoute<NotificationsRoute>() ||
            dest.hasRoute<MyProfileRoute>()
    } ?: true

    val navigateToTopTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = PitchBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isMainTab && !useRail) {
                    FfBottomBar(
                        isHome = currentDestination?.hasRoute<HomeFeedRoute>() == true,
                        isDiscover = currentDestination?.hasRoute<DiscoverRoute>() == true,
                        isPublish = currentDestination?.hasRoute<PublishRoute>() == true,
                        isNotifications = currentDestination?.hasRoute<NotificationsRoute>() == true,
                        isProfile = currentDestination?.hasRoute<MyProfileRoute>() == true,
                        unreadNotifications = unreadCount,
                        onSelectHome = { navigateToTopTab(HomeFeedRoute()) },
                        onSelectDiscover = { navigateToTopTab(DiscoverRoute()) },
                        onSelectPublish = { navigateToTopTab(PublishRoute) },
                        onSelectNotifications = { navigateToTopTab(NotificationsRoute) },
                        onSelectProfile = { navigateToTopTab(MyProfileRoute) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isMainTab && useRail) {
                    FfNavigationRail(
                        isHome = currentDestination?.hasRoute<HomeFeedRoute>() == true,
                        isDiscover = currentDestination?.hasRoute<DiscoverRoute>() == true,
                        isPublish = currentDestination?.hasRoute<PublishRoute>() == true,
                        isNotifications = currentDestination?.hasRoute<NotificationsRoute>() == true,
                        isProfile = currentDestination?.hasRoute<MyProfileRoute>() == true,
                        unreadNotifications = unreadCount,
                        onSelectHome = { navigateToTopTab(HomeFeedRoute()) },
                        onSelectDiscover = { navigateToTopTab(DiscoverRoute()) },
                        onSelectPublish = { navigateToTopTab(PublishRoute) },
                        onSelectNotifications = { navigateToTopTab(NotificationsRoute) },
                        onSelectProfile = { navigateToTopTab(MyProfileRoute) }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeFeedRoute(),
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeFeedRoute> { entry ->
                        val args = entry.toRoute<HomeFeedRoute>()
                        val isHomeActive = currentDestination?.hasRoute<HomeFeedRoute>() == true
                        FeedScreen(
                            viewModel = viewModel,
                            initialVideoId = args.initialVideoId,
                            isScreenActive = isHomeActive,
                            onOpenCreatorProfile = { uid ->
                                if (uid == viewModel.currentUserId) navigateToTopTab(MyProfileRoute)
                                else navController.navigate(CreatorProfileRoute(uid))
                            },
                            onNavigateToPublish = { navigateToTopTab(PublishRoute) },
                            onHashtagClick = { tag ->
                                navController.navigate(DiscoverRoute(hashtag = tag))
                            }
                        )
                    }

                    composable<DiscoverRoute> { entry ->
                        val args = entry.toRoute<DiscoverRoute>()
                        DiscoverScreen(
                            viewModel = viewModel,
                            initialHashtag = args.hashtag,
                            onOpenVideo = { videoId ->
                                navController.navigate(HomeFeedRoute(initialVideoId = videoId))
                            },
                            onOpenCreatorProfile = { uid ->
                                if (uid == viewModel.currentUserId) navigateToTopTab(MyProfileRoute)
                                else navController.navigate(CreatorProfileRoute(uid))
                            }
                        )
                    }

                    composable<PublishRoute> {
                        PublishScreen(
                            viewModel = viewModel,
                            onPublished = {
                                navController.navigate(HomeFeedRoute()) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                }
                            }
                        )
                    }

                    composable<NotificationsRoute> {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onOpenChats = { navController.navigate(ChatsListRoute) },
                            onOpenVideo = { vid -> navController.navigate(HomeFeedRoute(initialVideoId = vid)) },
                            onOpenProfile = { uid ->
                                if (uid == viewModel.currentUserId) navigateToTopTab(MyProfileRoute)
                                else navController.navigate(CreatorProfileRoute(uid))
                            },
                            onOpenDirectChat = { otherUid -> navController.navigate(ChatDetailRoute(otherUid)) }
                        )
                    }

                    composable<MyProfileRoute> {
                        ProfileScreen(
                            viewModel = viewModel,
                            targetUserId = viewModel.currentUserId,
                            isStandalonePage = false,
                            onOpenVideo = { vid -> navController.navigate(HomeFeedRoute(initialVideoId = vid)) },
                            onOpenChat = { otherUid -> navController.navigate(ChatDetailRoute(otherUid)) },
                            onOpenAdminPanel = {
                                if (viewModel.isOwnerAdmin) navController.navigate(AdminPanelRoute)
                            }
                        )
                    }

                    composable<CreatorProfileRoute> { entry ->
                        val args = entry.toRoute<CreatorProfileRoute>()
                        ProfileScreen(
                            viewModel = viewModel,
                            targetUserId = args.userId,
                            isStandalonePage = true,
                            onBack = { navController.popBackStack() },
                            onOpenVideo = { vid -> navController.navigate(HomeFeedRoute(initialVideoId = vid)) },
                            onOpenChat = { otherUid -> navController.navigate(ChatDetailRoute(otherUid)) },
                            onOpenAdminPanel = {}
                        )
                    }

                    composable<ChatsListRoute> {
                        ChatsListScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenChat = { otherUid -> navController.navigate(ChatDetailRoute(otherUid)) }
                        )
                    }

                    composable<ChatDetailRoute> { entry ->
                        val args = entry.toRoute<ChatDetailRoute>()
                        ChatDetailScreen(
                            viewModel = viewModel,
                            otherUserId = args.otherUserId,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AdminPanelRoute> {
                        AdminPanelScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FfBottomBar(
    isHome: Boolean,
    isDiscover: Boolean,
    isPublish: Boolean,
    isNotifications: Boolean,
    isProfile: Boolean,
    unreadNotifications: Int,
    onSelectHome: () -> Unit,
    onSelectDiscover: () -> Unit,
    onSelectPublish: () -> Unit,
    onSelectNotifications: () -> Unit,
    onSelectProfile: () -> Unit
) {
    Surface(
        color = CyberCard,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerDark)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab(
                    label = "الرئيسية",
                    selected = isHome,
                    icon = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                    testTag = "nav_home",
                    onClick = onSelectHome
                )
                BottomNavTab(
                    label = "اكتشف",
                    selected = isDiscover,
                    icon = if (isDiscover) Icons.Filled.Explore else Icons.Outlined.Explore,
                    testTag = "nav_discover",
                    onClick = onSelectDiscover
                )

                // Center Publish Button
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(NeonCrimson, ElectricPurple)))
                        .border(
                            1.dp,
                            if (isPublish) Color.White else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(onClick = onSelectPublish)
                        .testTag("nav_publish"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "نشر فيديو",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                BottomNavTab(
                    label = "الإشعارات",
                    selected = isNotifications,
                    icon = if (isNotifications) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    badgeCount = unreadNotifications,
                    testTag = "nav_notifications",
                    onClick = onSelectNotifications
                )
                BottomNavTab(
                    label = "حسابي",
                    selected = isProfile,
                    icon = if (isProfile) Icons.Filled.Person else Icons.Outlined.Person,
                    testTag = "nav_profile",
                    onClick = onSelectProfile
                )
            }
        }
    }
}

@Composable
private fun BottomNavTab(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int = 0,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (selected) NeonCrimson else TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 62.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        if (badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(containerColor = NeonCrimson, contentColor = TextPrimary) {
                        Text(badgeCount.coerceAtMost(99).toString())
                    }
                }
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FfNavigationRail(
    isHome: Boolean,
    isDiscover: Boolean,
    isPublish: Boolean,
    isNotifications: Boolean,
    isProfile: Boolean,
    unreadNotifications: Int,
    onSelectHome: () -> Unit,
    onSelectDiscover: () -> Unit,
    onSelectPublish: () -> Unit,
    onSelectNotifications: () -> Unit,
    onSelectProfile: () -> Unit
) {
    NavigationRail(
        containerColor = CyberCard,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(
            selected = isHome,
            onClick = onSelectHome,
            icon = { Icon(if (isHome) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "الرئيسية") },
            label = { Text("الرئيسية") },
            colors = NavigationRailItemDefaults.colors(selectedIconColor = NeonCrimson, selectedTextColor = NeonCrimson)
        )
        NavigationRailItem(
            selected = isDiscover,
            onClick = onSelectDiscover,
            icon = { Icon(if (isDiscover) Icons.Filled.Explore else Icons.Outlined.Explore, contentDescription = "اكتشف") },
            label = { Text("اكتشف") },
            colors = NavigationRailItemDefaults.colors(selectedIconColor = NeonCrimson, selectedTextColor = NeonCrimson)
        )
        NavigationRailItem(
            selected = isPublish,
            onClick = onSelectPublish,
            icon = { Icon(Icons.Filled.Add, contentDescription = "نشر") },
            label = { Text("نشر") },
            colors = NavigationRailItemDefaults.colors(selectedIconColor = ElectricPurple, selectedTextColor = ElectricPurple)
        )
        NavigationRailItem(
            selected = isNotifications,
            onClick = onSelectNotifications,
            icon = {
                if (unreadNotifications > 0) {
                    BadgedBox(badge = { Badge { Text(unreadNotifications.toString()) } }) {
                        Icon(if (isNotifications) Icons.Filled.Notifications else Icons.Outlined.Notifications, contentDescription = "الإشعارات")
                    }
                } else {
                    Icon(if (isNotifications) Icons.Filled.Notifications else Icons.Outlined.Notifications, contentDescription = "الإشعارات")
                }
            },
            label = { Text("الإشعارات") },
            colors = NavigationRailItemDefaults.colors(selectedIconColor = NeonCrimson, selectedTextColor = NeonCrimson)
        )
        NavigationRailItem(
            selected = isProfile,
            onClick = onSelectProfile,
            icon = { Icon(if (isProfile) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = "حسابي") },
            label = { Text("حسابي") },
            colors = NavigationRailItemDefaults.colors(selectedIconColor = NeonCrimson, selectedTextColor = NeonCrimson)
        )
    }
}
