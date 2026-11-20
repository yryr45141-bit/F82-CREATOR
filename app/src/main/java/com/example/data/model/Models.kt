package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VipFrameStyle(val id: String, val labelAr: String) {
    NONE("none", "بدون إطار"),
    CRIMSON_FLAME("crimson_flame", "لهب الكريمسون النيون"),
    VIOLET_CYBER("violet_cyber", "سايبر البنفسجي"),
    ROYAL_GOLD("royal_gold", "التاج الذهبي الأسطوري");

    companion object {
        fun fromId(raw: String?): VipFrameStyle =
            entries.firstOrNull { it.id == raw } ?: NONE
    }
}

enum class VipPlanTier(val id: String, val titleAr: String, val durationAr: String, val perksAr: List<String>) {
    MONTHLY(
        "monthly",
        "VIP شهري",
        "30 يوم",
        listOf("شارة VIP مضيئة بجانب اسمك", "إطار سايبر حصري لصورة الحساب", "أولوية في قائمة الاكتشاف")
    ),
    SEASONAL(
        "seasonal",
        "VIP موسمي",
        "90 يوم",
        listOf("كل مزايا VIP الشهري", "إطارات لهب الكريمسون والسايبر", "إبراز الفيديوهات الرائجة")
    ),
    LEGEND(
        "legend",
        "VIP الأسطورة",
        "سنة كاملة",
        listOf("جميع الإطارات الحصرية بما فيها التاج الذهبي", "شارة أسطورة Free Fire", "أولوية قصوى في الدعم والتوصيات")
    );

    companion object {
        fun fromId(raw: String?): VipPlanTier =
            entries.firstOrNull { it.id == raw } ?: MONTHLY
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    val vip: Boolean = false,
    val vipFrame: String = "none",
    val banned: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val fcmToken: String? = null,
    val blockedUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val authorVerified: Boolean = false,
    val authorVip: Boolean = false,
    val authorVipFrame: String = "none",
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val videoUrl: String = "",
    val coverUrl: String? = null,
    val durationSec: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val viewsCount: Long = 0L,
    val favoritesCount: Long = 0L,
    val sharesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommentItem(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val authorVerified: Boolean = false,
    val authorVip: Boolean = false,
    val text: String = "",
    val parentCommentId: String? = null,
    val replyToUsername: String? = null,
    val likesCount: Long = 0L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "like",
    val targetId: String? = null,
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantUsernames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val ffPlayerId: String = "",
    val reason: String = "",
    val followersCount: Long = 0L,
    val status: String = "pending",
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VipRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val planTier: String = "monthly",
    val selectedFrame: String = "crimson_flame",
    val note: String? = null,
    val status: String = "pending",
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportItem(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "video",
    val targetId: String = "",
    val targetLabel: String = "",
    val reason: String = "",
    val status: String = "open",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TrendingHashtag(
    @DocumentId val id: String = "",
    val tag: String = "",
    val usageCount: Long = 0L,
    val featured: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminDashboardStats(
    val totalUsers: Int = 0,
    val verifiedUsers: Int = 0,
    val vipUsers: Int = 0,
    val bannedUsers: Int = 0,
    val totalVideos: Int = 0,
    val totalViews: Long = 0L,
    val pendingVerifications: Int = 0,
    val pendingVipRequests: Int = 0,
    val openReports: Int = 0
)
