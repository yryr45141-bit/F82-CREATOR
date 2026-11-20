package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthManager
import com.example.data.firebase.FfRepository
import com.example.data.firebase.extractFriendlyFirestoreError
import com.example.data.model.AdminDashboardStats
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.CommentItem
import com.example.data.model.ReportItem
import com.example.data.model.TrendingHashtag
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRequest
import com.example.data.model.VideoPost
import com.example.data.model.VipRequest
import com.example.media.VideoCompressor
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FfMainViewModel(
    private val repository: FfRepository,
    val currentUserId: String,
    val currentUserEmail: String?,
    firebaseUser: FirebaseUser?
) : ViewModel() {

    val isOwnerAdmin: Boolean = AuthManager.isOwnerEmail(currentUserEmail)

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages = _toastMessages.asSharedFlow()

    private val _likedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likedVideoIds: StateFlow<Set<String>> = _likedVideoIds.asStateFlow()

    private val _publishProgress = MutableStateFlow<Float?>(null)
    val publishProgress: StateFlow<Float?> = _publishProgress.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        if (firebaseUser != null) {
            viewModelScope.launch {
                repository.ensureUserProfile(firebaseUser).onFailure {
                    emitToast(extractFriendlyFirestoreError(it, "تعذر تهيئة بيانات الحساب"))
                }
            }
        }
    }

    val myProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("تعذر تحميل ملفك الشخصي")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val privateInfo: StateFlow<UserPrivateInfo?> =
        repository.observePrivateInfo(currentUserId)
            .catch { error ->
                extractFriendlyFirestoreError(error, "خطأ في البيانات الخاصة")
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val videosFeedState: StateFlow<UiState<List<VideoPost>>> =
        combine(
            repository.observeVideosFeed(),
            repository.observePrivateInfo(currentUserId).catch { emit(null) }
        ) { videos, priv ->
            val blocked = priv?.blockedUserIds?.toSet().orEmpty()
            videos.filter { it.userId !in blocked }
        }
            .toUiState("تعذر تحميل قائمة الفيديوهات")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val allUsers: StateFlow<List<UserProfile>> =
        repository.observeAllUsers()
            .catch { error ->
                Log.w(TAG, "Error observing users", error)
                extractFriendlyFirestoreError(error, "تعذر تحميل اللاعبين")
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val followingIds: StateFlow<Set<String>> =
        repository.observeFollowingIds(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في المتابعات") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptySet())

    val favoriteVideoIds: StateFlow<Set<String>> =
        repository.observeLikedVideoIds(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في المفضلة") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptySet())

    val notifications: StateFlow<List<AppNotification>> =
        repository.observeNotifications(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "تعذر تحميل الإشعارات") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val chats: StateFlow<List<ChatThread>> =
        repository.observeChats(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "تعذر تحميل المحادثات") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val trendingHashtags: StateFlow<List<TrendingHashtag>> =
        repository.observeTrendingHashtags()
            .catch { error -> extractFriendlyFirestoreError(error, "تعذر تحميل الهاشتاجات") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val myVerificationRequests: StateFlow<List<VerificationRequest>> =
        repository.observeMyVerificationRequests(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في طلبات التوثيق") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val myVipRequests: StateFlow<List<VipRequest>> =
        repository.observeMyVipRequests(currentUserId)
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في طلبات VIP") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    // Admin-only streams: NEVER attached unless isOwnerAdmin == true
    val adminVerifications: StateFlow<List<VerificationRequest>> =
        (if (isOwnerAdmin) repository.observeAllVerificationRequests() else flowOf(emptyList()))
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في طلبات التوثيق") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val adminVipRequests: StateFlow<List<VipRequest>> =
        (if (isOwnerAdmin) repository.observeAllVipRequests() else flowOf(emptyList()))
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في طلبات VIP") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val adminReports: StateFlow<List<ReportItem>> =
        (if (isOwnerAdmin) repository.observeAllReports() else flowOf(emptyList()))
            .catch { error -> extractFriendlyFirestoreError(error, "خطأ في البلاغات") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val adminStats: StateFlow<AdminDashboardStats> =
        combine(
            allUsers,
            videosFeedState,
            adminVerifications,
            adminVipRequests,
            adminReports
        ) { users, videosUi, vers, vips, reps ->
            val videos = (videosUi as? UiState.Success)?.data.orEmpty()
            AdminDashboardStats(
                totalUsers = users.size,
                verifiedUsers = users.count { it.verified },
                vipUsers = users.count { it.vip },
                bannedUsers = users.count { it.banned },
                totalVideos = videos.size,
                totalViews = videos.sumOf { it.viewsCount },
                pendingVerifications = vers.count { it.status == "pending" },
                pendingVipRequests = vips.count { it.status == "pending" },
                openReports = reps.count { it.status == "open" }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), AdminDashboardStats())

    private fun currentProfileOrFallback(): UserProfile {
        val fromState = (myProfileState.value as? UiState.Success)?.data
        return fromState ?: UserProfile(
            userId = currentUserId,
            username = "ff_player",
            displayName = "بطل Free Fire"
        )
    }

    fun checkVideoLikeState(videoId: String) {
        viewModelScope.launch {
            val liked = repository.isVideoLikedByMe(videoId)
            _likedVideoIds.value = if (liked) _likedVideoIds.value + videoId else _likedVideoIds.value - videoId
        }
    }

    fun recordVideoView(video: VideoPost) {
        viewModelScope.launch {
            repository.recordVideoView(video)
        }
    }

    fun toggleLike(video: VideoPost) {
        viewModelScope.launch {
            val wasLiked = video.id in _likedVideoIds.value
            _likedVideoIds.value = if (wasLiked) _likedVideoIds.value - video.id else _likedVideoIds.value + video.id
            repository.toggleVideoLike(video, currentProfileOrFallback()).onFailure {
                _likedVideoIds.value = if (wasLiked) _likedVideoIds.value + video.id else _likedVideoIds.value - video.id
                emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث الإعجاب"))
            }
        }
    }

    fun toggleFavorite(video: VideoPost) {
        viewModelScope.launch {
            repository.toggleVideoFavorite(video).onSuccess { saved ->
                emitToast(if (saved) "تم حفظ الفيديو في المفضلة" else "تمت إزالة الفيديو من المفضلة")
            }.onFailure {
                emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث المفضلة"))
            }
        }
    }

    fun recordShare(videoId: String) {
        viewModelScope.launch {
            repository.incrementVideoShare(videoId)
        }
    }

    fun toggleFollow(targetUserId: String) {
        viewModelScope.launch {
            repository.toggleFollow(targetUserId, currentProfileOrFallback()).onFailure {
                emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث المتابعة"))
            }
        }
    }

    fun observeComments(videoId: String): Flow<List<CommentItem>> =
        repository.observeComments(videoId)
            .catch { error ->
                extractFriendlyFirestoreError(error, "تعذر تحميل التعليقات")
                emit(emptyList())
            }

    fun addComment(video: VideoPost, text: String, parentComment: CommentItem? = null) {
        viewModelScope.launch {
            repository.addComment(video, text, currentProfileOrFallback(), parentComment)
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال التعليق")) }
        }
    }

    fun toggleCommentLike(videoId: String, commentId: String) {
        viewModelScope.launch {
            repository.toggleCommentLike(videoId, commentId)
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث إعجاب التعليق")) }
        }
    }

    fun togglePinComment(videoId: String, comment: CommentItem) {
        viewModelScope.launch {
            repository.togglePinComment(videoId, comment)
                .onSuccess { emitToast(if (!comment.pinned) "تم تثبيت التعليق" else "تم إلغاء تثبيت التعليق") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تثبيت التعليق")) }
        }
    }

    fun publishVideo(
        context: Context,
        videoUri: Uri?,
        coverUri: Uri?,
        directMp4Url: String,
        description: String,
        hashtagsRaw: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _publishProgress.value = 0.05f
            try {
                val compressed = if (videoUri != null) {
                    val res = VideoCompressor.compressVideo(context, videoUri) { p ->
                        _publishProgress.value = (p * 0.8f).coerceIn(0.05f, 0.85f)
                    }
                    res.getOrElse { throw it }
                } else {
                    null
                }

                _publishProgress.value = 0.9f
                val customCover = coverUri?.let {
                    VideoCompressor.compressImageUriToDataUri(context, it, maxWidth = 360, quality = 68)
                } ?: compressed?.autoCoverBase64

                val result = repository.publishVideo(
                    profile = currentProfileOrFallback(),
                    description = description,
                    hashtagsRaw = hashtagsRaw,
                    compressedFile = compressed?.file,
                    directMp4Url = directMp4Url.takeIf { it.isNotBlank() },
                    coverDataUri = customCover,
                    durationSec = compressed?.durationSec ?: 15L
                )
                result.onSuccess {
                    _publishProgress.value = 1f
                    emitToast("تم نشر الفيديو بنجاح!")
                    onSuccess()
                }.onFailure {
                    emitToast(extractFriendlyFirestoreError(it, "فشل نشر الفيديو"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitToast(extractFriendlyFirestoreError(e, "تعذر معالجة الفيديو"))
            } finally {
                _publishProgress.value = null
                _isBusy.value = false
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
                .onSuccess { emitToast("تم حذف الفيديو") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر حذف الفيديو")) }
        }
    }

    fun updateProfile(
        context: Context,
        username: String,
        displayName: String,
        bio: String,
        avatarUri: Uri?,
        vipFrame: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val encodedAvatar = avatarUri?.let {
                    VideoCompressor.compressImageUriToDataUri(context, it, maxWidth = 280, quality = 70)
                }
                repository.updateProfile(
                    currentProfile = currentProfileOrFallback(),
                    newUsernameRaw = username,
                    displayNameRaw = displayName,
                    bioRaw = bio,
                    avatarDataUri = encodedAvatar,
                    selectedVipFrame = vipFrame
                ).onSuccess {
                    emitToast("تم تحديث الملف الشخصي")
                    onSuccess()
                }.onFailure {
                    emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث الملف الشخصي"))
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recoverAccount(firebaseUser: FirebaseUser?) {
        if (firebaseUser == null) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.ensureUserProfile(firebaseUser)
                .onSuccess { emitToast("تمت مزامنة واستعادة بيانات حسابك من السحابة بنجاح") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر استعادة الحساب")) }
            _isBusy.value = false
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun chatIdFor(otherUserId: String): String = repository.chatIdFor(currentUserId, otherUserId)

    fun observeChatMessages(chatId: String): Flow<List<ChatMessage>> =
        repository.observeChatMessages(chatId, currentUserId)
            .catch { error ->
                extractFriendlyFirestoreError(error, "تعذر تحميل الرسائل")
                emit(emptyList())
            }

    fun sendMessage(otherUser: UserProfile, text: String) {
        viewModelScope.launch {
            repository.sendMessage(currentProfileOrFallback(), otherUser, text)
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال الرسالة")) }
        }
    }

    fun toggleBlockUser(targetUserId: String) {
        val blocked = targetUserId in (privateInfo.value?.blockedUserIds.orEmpty())
        viewModelScope.launch {
            repository.toggleBlockUser(targetUserId, blocked)
                .onSuccess { emitToast(if (!blocked) "تم حظر المستخدم" else "تم إلغاء حظر المستخدم") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث الحظر")) }
        }
    }

    fun submitVerification(ffPlayerId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.submitVerificationRequest(currentProfileOrFallback(), ffPlayerId, reason)
                .onSuccess {
                    emitToast("تم إرسال طلب التوثيق للإدارة")
                    onSuccess()
                }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال الطلب")) }
            _isBusy.value = false
        }
    }

    fun submitVip(planTier: String, selectedFrame: String, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.submitVipRequest(currentProfileOrFallback(), planTier, selectedFrame, note)
                .onSuccess {
                    emitToast("تم إرسال طلب اشتراك VIP بانتظار موافقة المالك")
                    onSuccess()
                }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال طلب VIP")) }
            _isBusy.value = false
        }
    }

    fun submitReport(targetType: String, targetId: String, targetLabel: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, targetLabel, reason)
                .onSuccess { emitToast("تم إرسال البلاغ لمراجعة المالك") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال البلاغ")) }
        }
    }

    // Admin actions
    fun adminReviewVerification(request: VerificationRequest, approved: Boolean, rejectionReason: String?) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminReviewVerification(request, approved, rejectionReason)
                .onSuccess { emitToast(if (approved) "تم توثيق الحساب" else "تم رفض طلب التوثيق") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تنفيذ القرار")) }
        }
    }

    fun adminReviewVip(request: VipRequest, approved: Boolean, rejectionReason: String?) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminReviewVip(request, approved, rejectionReason)
                .onSuccess { emitToast(if (approved) "تم تفعيل VIP للمستخدم" else "تم رفض طلب VIP") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تنفيذ قرار VIP")) }
        }
    }

    fun adminToggleBanUser(user: UserProfile) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminToggleBanUser(user)
                .onSuccess { emitToast(if (!user.banned) "تم حظر الحساب" else "تم فك الحظر عن الحساب") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث حالة الحظر")) }
        }
    }

    fun adminResolveReport(reportId: String, status: String, note: String?) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminResolveReport(reportId, status, note)
                .onSuccess { emitToast("تم تحديث حالة البلاغ") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر تحديث البلاغ")) }
        }
    }

    fun adminBroadcast(title: String, body: String, onSuccess: () -> Unit) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminBroadcastNotification(title, body, allUsers.value)
                .onSuccess { count ->
                    emitToast("تم إرسال الإشعار إلى $count مستخدم")
                    onSuccess()
                }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر إرسال الإشعار الجماعي")) }
            _isBusy.value = false
        }
    }

    fun adminSaveHashtag(tag: String, featured: Boolean) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminSaveHashtag(tag, featured)
                .onSuccess { emitToast("تم حفظ الهاشتاج") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر حفظ الهاشتاج")) }
        }
    }

    fun adminDeleteHashtag(tagId: String) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.adminDeleteHashtag(tagId)
                .onSuccess { emitToast("تم حذف الهاشتاج") }
                .onFailure { emitToast(extractFriendlyFirestoreError(it, "تعذر حذف الهاشتاج")) }
        }
    }

    private fun emitToast(message: String) {
        _toastMessages.tryEmit(message)
    }

    private fun <T> Flow<T>.toUiState(errorFallback: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val msg = extractFriendlyFirestoreError(error, errorFallback)
                emit(UiState.Error(msg))
            }

    private companion object {
        const val TAG = "FfMainVM"
        const val STOP_TIMEOUT = 5000L
    }
}
