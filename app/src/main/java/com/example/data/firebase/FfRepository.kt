package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.data.model.AdminDashboardStats
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.CommentItem
import com.example.data.model.ReportItem
import com.example.data.model.TrendingHashtag
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRequest
import com.example.data.model.VideoPost
import com.example.data.model.VipRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.messaging
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

class FfRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول أولاً")

    private fun sanitizeId(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9_\\-]"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "tag_${System.currentTimeMillis()}" }

    // -------------------------------------------------------------------------
    // User Profile & Auth Recovery
    // -------------------------------------------------------------------------

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val userRef = db.collection("users").document(user.uid)
            val snap = userRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ensurePrivateInfo(user)
                syncFcmToken(user.uid)
                return Result.success(existing ?: UserProfile(userId = user.uid))
            }

            val baseName = (user.email?.substringBefore("@") ?: "ff_player")
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .take(16)
                .ifBlank { "ff_player" }
                .let { if (it.length < 3) "${it}_ff" else it }

            var candidate = baseName
            var attempt = 0
            while (attempt < 5) {
                val unameDoc = db.collection("usernames").document(candidate).get().await()
                if (!unameDoc.exists()) break
                attempt++
                candidate = "${baseName.take(14)}_${(100..999).random()}"
            }

            val displayName = (user.displayName?.take(40)?.ifBlank { null }) ?: "بطل Free Fire"
            val avatar = user.photoUrl?.toString()?.take(500)

            val batch = db.batch()
            batch.set(
                db.collection("usernames").document(candidate),
                mapOf(
                    "userId" to user.uid,
                    "username" to candidate,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val profilePayload = buildMap<String, Any> {
                put("userId", user.uid)
                put("username", candidate)
                put("displayName", displayName)
                put("bio", "صانع محتوى في مجتمع Free Fire")
                if (avatar != null) put("avatarUrl", avatar)
                put("verified", false)
                put("vip", false)
                put("vipFrame", "none")
                put("banned", false)
                put("followersCount", 0L)
                put("followingCount", 0L)
                put("likesCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(userRef, profilePayload)
            batch.set(
                userRef.collection("private").document("info"),
                mapOf(
                    "userId" to user.uid,
                    "email" to (user.email ?: ""),
                    "blockedUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            syncFcmToken(user.uid)

            val created = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(userId = user.uid, username = candidate, displayName = displayName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun ensurePrivateInfo(user: FirebaseUser) {
        val infoRef = db.collection("users").document(user.uid).collection("private").document("info")
        val snap = infoRef.get().await()
        if (!snap.exists()) {
            infoRef.set(
                mapOf(
                    "userId" to user.uid,
                    "email" to (user.email ?: ""),
                    "blockedUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun syncFcmToken(uid: String) {
        try {
            val token = withTimeoutOrNull(2500L) {
                Firebase.messaging.token.await()
            } ?: return
            db.collection("users").document(uid)
                .collection("private").document("info")
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            Log.d("FfRepository", "FCM token sync skipped: ${e.message}")
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.GET, path, userId)
                    throw e
                }
        )
    }

    fun observePrivateInfo(userId: String): Flow<UserPrivateInfo?> = flow {
        val path = "users/$userId/private/info"
        emitAll(
            db.collection("users").document(userId).collection("private").document("info")
                .snapshots()
                .map { it.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.GET, path, userId)
                    throw e
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users")
                .orderBy("followersCount", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "users", uid)
                    throw e
                }
        )
    }

    suspend fun updateProfile(
        currentProfile: UserProfile,
        newUsernameRaw: String,
        displayNameRaw: String,
        bioRaw: String,
        avatarDataUri: String?,
        selectedVipFrame: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "users/$uid"
        return try {
            val cleanUsername = newUsernameRaw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            require(cleanUsername.length in 3..24) { "اسم المستخدم يجب أن يكون بين 3 و 24 حرفاً بالإنجليزية والأرقام" }
            val cleanDisplay = displayNameRaw.trim()
            require(cleanDisplay.length in 1..40) { "الاسم المعروض مطلوب (حتى 40 حرفاً)" }
            val cleanBio = bioRaw.trim().take(200)

            val oldUsername = currentProfile.username.lowercase()
            if (cleanUsername != oldUsername) {
                val existingName = db.collection("usernames").document(cleanUsername).get().await()
                if (existingName.exists() && existingName.getString("userId") != uid) {
                    return Result.failure(IllegalStateException("اسم المستخدم @$cleanUsername محجوز بالفعل"))
                }
                db.collection("usernames").document(cleanUsername).set(
                    mapOf(
                        "userId" to uid,
                        "username" to cleanUsername,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (oldUsername.isNotBlank()) {
                    try {
                        db.collection("usernames").document(oldUsername).delete().await()
                    } catch (_: Exception) {
                    }
                }
            }

            val updates = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "displayName" to cleanDisplay,
                "bio" to cleanBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarDataUri != null) {
                updates["avatarUrl"] = avatarDataUri
            }
            if (selectedVipFrame != null && currentProfile.vip) {
                updates["vipFrame"] = selectedVipFrame
            }

            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Videos Feed, Publish, Delete & Interactions
    // -------------------------------------------------------------------------

    fun observeVideosFeed(): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("videos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "videos", uid)
                    throw e
                }
        )
    }

    fun observeUserVideos(targetUserId: String): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("videos")
                .whereEqualTo("userId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "videos", uid)
                    throw e
                }
        )
    }

    fun observeLikedVideoIds(userId: String): Flow<Set<String>> = flow {
        // Observe user's favorites and per-video likes cleanly; we also provide direct checks
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toSet() }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "users/$userId/favorites", userId)
                    throw e
                }
        )
    }

    suspend fun isVideoLikedByMe(videoId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("videos").document(videoId)
                .collection("likes").document(uid)
                .get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    fun observeFollowingIds(userId: String): Flow<Set<String>> = flow {
        emitAll(
            db.collection("users").document(userId).collection("following")
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toSet() }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "users/$userId/following", userId)
                    throw e
                }
        )
    }

    suspend fun publishVideo(
        profile: UserProfile,
        description: String,
        hashtagsRaw: String,
        compressedFile: File?,
        directMp4Url: String?,
        coverDataUri: String?,
        durationSec: Long
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val videoId = "vid_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "videos/$videoId"

        return try {
            val cleanDesc = description.trim()
            require(cleanDesc.length in 1..300) { "الوصف يجب أن يكون بين 1 و 300 حرف" }

            val tags = hashtagsRaw.split(Regex("[\\s,،]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { if (it.startsWith("#")) it else "#$it" }
                .distinct()
                .take(10)

            // Try uploading compressed MP4 to Firebase Storage; if bucket is not enabled yet,
            // fall back to local compressed file URI so playback works immediately on-device.
            var resolvedVideoUrl = directMp4Url?.trim()?.takeIf { it.isNotBlank() } ?: ""
            if (resolvedVideoUrl.isBlank() && compressedFile != null && compressedFile.exists()) {
                resolvedVideoUrl = tryUploadToStorage(uid, videoId, compressedFile)
                    ?: Uri.fromFile(compressedFile).toString()
            }
            require(resolvedVideoUrl.isNotBlank()) { "يرجى اختيار فيديو MP4 أو إدخال رابط فيديو مباشر" }

            val freshProfile = db.collection("users").document(uid).get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: profile

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("authorName", freshProfile.displayName.ifBlank { "بطل Free Fire" })
                put("authorUsername", freshProfile.username.ifBlank { "ff_player" })
                freshProfile.avatarUrl?.takeIf { it.isNotBlank() }?.let { put("authorAvatarUrl", it) }
                put("authorVerified", freshProfile.verified)
                put("authorVip", freshProfile.vip)
                put("authorVipFrame", freshProfile.vipFrame.ifBlank { "none" })
                put("description", cleanDesc)
                put("hashtags", tags)
                put("videoUrl", resolvedVideoUrl.take(1000))
                coverDataUri?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
                put("durationSec", durationSec.coerceIn(0L, 600L))
                put("likesCount", 0L)
                put("commentsCount", 0L)
                put("viewsCount", 0L)
                put("favoritesCount", 0L)
                put("sharesCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            db.collection("videos").document(videoId).set(payload).await()

            // Register/increment trending hashtags
            tags.forEach { tag ->
                val tagId = sanitizeId(tag.removePrefix("#"))
                val tagRef = db.collection("hashtags").document(tagId)
                try {
                    val tagSnap = tagRef.get().await()
                    if (tagSnap.exists()) {
                        tagRef.update(
                            mapOf(
                                "usageCount" to FieldValue.increment(1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        tagRef.set(
                            mapOf(
                                "tag" to tag.take(40),
                                "usageCount" to 1L,
                                "featured" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                } catch (_: Exception) {
                }
            }

            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun tryUploadToStorage(uid: String, videoId: String, file: File): String? {
        return try {
            withTimeoutOrNull(18_000L) {
                val storageRef = Firebase.storage.reference.child("videos/$uid/$videoId.mp4")
                storageRef.putFile(Uri.fromFile(file)).await()
                storageRef.downloadUrl.await().toString()
            }
        } catch (e: Exception) {
            Log.w("FfRepository", "Firebase Storage upload unavailable, using compressed local file: ${e.message}")
            null
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordVideoView(video: VideoPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "videos/${video.id}/views/$uid"
        return try {
            val viewRef = db.collection("videos").document(video.id).collection("views").document(uid)
            if (viewRef.get().await().exists()) return Result.success(Unit)
            val batch = db.batch()
            batch.set(
                viewRef,
                mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("videos").document(video.id),
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleVideoLike(video: VideoPost, myProfile: UserProfile): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "videos/${video.id}/likes/$uid"
        return try {
            val likeRef = db.collection("videos").document(video.id).collection("likes").document(uid)
            val alreadyLiked = likeRef.get().await().exists()
            val batch = db.batch()
            val delta = if (alreadyLiked) -1L else 1L

            if (alreadyLiked) {
                batch.delete(likeRef)
            } else {
                batch.set(
                    likeRef,
                    mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp())
                )
            }
            batch.update(
                db.collection("videos").document(video.id),
                mapOf(
                    "likesCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(video.userId),
                mapOf(
                    "likesCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            if (!alreadyLiked && video.userId != uid) {
                sendNotification(
                    recipientUid = video.userId,
                    senderUid = uid,
                    senderName = myProfile.displayName.ifBlank { myProfile.username },
                    type = "like",
                    targetId = video.id,
                    title = "إعجاب جديد",
                    message = "أعجب ${myProfile.displayName} بفيديوك: ${video.description.take(40)}"
                )
            }
            Result.success(!alreadyLiked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleVideoFavorite(video: VideoPost): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "users/$uid/favorites/${video.id}"
        return try {
            val favRef = db.collection("users").document(uid).collection("favorites").document(video.id)
            val exists = favRef.get().await().exists()
            val batch = db.batch()
            val delta = if (exists) -1L else 1L
            if (exists) {
                batch.delete(favRef)
            } else {
                batch.set(
                    favRef,
                    mapOf("videoId" to video.id, "createdAt" to FieldValue.serverTimestamp())
                )
            }
            batch.update(
                db.collection("videos").document(video.id),
                mapOf(
                    "favoritesCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(!exists)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementVideoShare(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).update(
                mapOf(
                    "sharesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Comments, Replies, Pinning & Comment Likes
    // -------------------------------------------------------------------------

    fun observeComments(videoId: String): Flow<List<CommentItem>> = flow {
        val uid = requireUserId()
        val path = "videos/$videoId/comments"
        emitAll(
            db.collection("videos").document(videoId).collection("comments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommentItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<CommentItem> { it.pinned }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    suspend fun addComment(
        video: VideoPost,
        textRaw: String,
        myProfile: UserProfile,
        parentComment: CommentItem? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "videos/${video.id}/comments/$commentId"
        return try {
            val cleanText = textRaw.trim()
            require(cleanText.length in 1..300) { "التعليق يجب أن يكون بين 1 و 300 حرف" }

            val batch = db.batch()
            val payload = buildMap<String, Any> {
                put("videoId", video.id)
                put("userId", uid)
                put("authorName", myProfile.displayName.ifBlank { "بطل Free Fire" })
                put("authorUsername", myProfile.username.ifBlank { "ff_player" })
                myProfile.avatarUrl?.takeIf { it.isNotBlank() }?.let { put("authorAvatarUrl", it) }
                put("authorVerified", myProfile.verified)
                put("authorVip", myProfile.vip)
                put("text", cleanText)
                parentComment?.id?.takeIf { it.isNotBlank() }?.let { put("parentCommentId", it) }
                parentComment?.authorUsername?.takeIf { it.isNotBlank() }?.let { put("replyToUsername", it) }
                put("likesCount", 0L)
                put("pinned", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(
                db.collection("videos").document(video.id).collection("comments").document(commentId),
                payload
            )
            batch.update(
                db.collection("videos").document(video.id),
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            // Notify video owner
            if (video.userId != uid) {
                sendNotification(
                    recipientUid = video.userId,
                    senderUid = uid,
                    senderName = myProfile.displayName,
                    type = "comment",
                    targetId = video.id,
                    title = "تعليق جديد",
                    message = "${myProfile.displayName}: ${cleanText.take(60)}"
                )
            }
            // Notify parent comment author on reply
            if (parentComment != null && parentComment.userId != uid) {
                sendNotification(
                    recipientUid = parentComment.userId,
                    senderUid = uid,
                    senderName = myProfile.displayName,
                    type = "reply",
                    targetId = video.id,
                    title = "رد على تعليقك",
                    message = "رد ${myProfile.displayName}: ${cleanText.take(60)}"
                )
            }
            // Detect @mentions and notify mentioned creators
            val mentions = Regex("@([a-zA-Z0-9_]{3,24})").findAll(cleanText)
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(3)
            for (mentionedUsername in mentions) {
                try {
                    val unameSnap = db.collection("usernames").document(mentionedUsername).get().await()
                    val mentionedUid = unameSnap.getString("userId")
                    if (!mentionedUid.isNullOrBlank() && mentionedUid != uid) {
                        sendNotification(
                            recipientUid = mentionedUid,
                            senderUid = uid,
                            senderName = myProfile.displayName,
                            type = "mention",
                            targetId = video.id,
                            title = "إشارة إليك",
                            message = "أشار إليك ${myProfile.displayName} في تعليق: ${cleanText.take(50)}"
                        )
                    }
                } catch (_: Exception) {
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleCommentLike(videoId: String, commentId: String): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "videos/$videoId/comments/$commentId/likes/$uid"
        return try {
            val likeRef = db.collection("videos").document(videoId)
                .collection("comments").document(commentId)
                .collection("likes").document(uid)
            val liked = likeRef.get().await().exists()
            val batch = db.batch()
            if (liked) {
                batch.delete(likeRef)
            } else {
                batch.set(likeRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
            }
            batch.update(
                db.collection("videos").document(videoId).collection("comments").document(commentId),
                mapOf(
                    "likesCount" to FieldValue.increment(if (liked) -1L else 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(!liked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinComment(videoId: String, comment: CommentItem): Result<Unit> {
        val path = "videos/$videoId/comments/${comment.id}"
        return try {
            requireUserId()
            db.collection("videos").document(videoId)
                .collection("comments").document(comment.id)
                .update(
                    mapOf(
                        "pinned" to !comment.pinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Follow / Unfollow & Blocking
    // -------------------------------------------------------------------------

    suspend fun toggleFollow(targetUserId: String, myProfile: UserProfile): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        if (targetUserId == uid) return Result.success(false)
        val path = "users/$targetUserId/followers/$uid"
        return try {
            val followerRef = db.collection("users").document(targetUserId).collection("followers").document(uid)
            val followingRef = db.collection("users").document(uid).collection("following").document(targetUserId)
            val alreadyFollowing = followerRef.get().await().exists()
            val delta = if (alreadyFollowing) -1L else 1L
            val batch = db.batch()

            if (alreadyFollowing) {
                batch.delete(followerRef)
                batch.delete(followingRef)
            } else {
                batch.set(followerRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                batch.set(followingRef, mapOf("userId" to targetUserId, "createdAt" to FieldValue.serverTimestamp()))
            }
            batch.update(
                db.collection("users").document(targetUserId),
                mapOf(
                    "followersCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "followingCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            if (!alreadyFollowing) {
                sendNotification(
                    recipientUid = targetUserId,
                    senderUid = uid,
                    senderName = myProfile.displayName,
                    type = "follow",
                    targetId = uid,
                    title = "متابع جديد",
                    message = "بدأ ${myProfile.displayName} (@${myProfile.username}) بمتابعتك"
                )
            }
            Result.success(!alreadyFollowing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "users/$uid/private/info"
        return try {
            val op = if (currentlyBlocked) FieldValue.arrayRemove(targetUserId) else FieldValue.arrayUnion(targetUserId)
            db.collection("users").document(uid).collection("private").document("info")
                .update(
                    mapOf(
                        "blockedUserIds" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = flow {
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, userId)
                    throw e
                }
        )
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "users/$uid/notifications/$notifId"
        return try {
            db.collection("users").document(uid).collection("notifications").document(notifId)
                .update(
                    mapOf(
                        "read" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun sendNotification(
        recipientUid: String,
        senderUid: String,
        senderName: String,
        type: String,
        targetId: String?,
        title: String,
        message: String
    ) {
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$recipientUid/notifications/$notifId"
        try {
            val payload = buildMap<String, Any> {
                put("userId", recipientUid)
                put("senderId", senderUid)
                put("senderName", senderName.take(50).ifBlank { "FF Creator" })
                put("type", type)
                targetId?.takeIf { it.isNotBlank() }?.let { put("targetId", it.take(128)) }
                put("title", title.take(100))
                put("message", message.take(300))
                put("read", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(recipientUid)
                .collection("notifications").document(notifId)
                .set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    // -------------------------------------------------------------------------
    // Private Messages & Chats
    // -------------------------------------------------------------------------

    fun chatIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        emitAll(
            db.collection("chats")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "chats", userId)
                    throw e
                }
        )
    }

    fun observeChatMessages(chatId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, userId)
                    throw e
                }
        )
    }

    suspend fun sendMessage(
        myProfile: UserProfile,
        otherUser: UserProfile,
        textRaw: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val cleanText = textRaw.trim()
        require(cleanText.length in 1..1000) { "الرسالة يجب أن تكون بين 1 و 1000 حرف" }
        val chatId = chatIdFor(uid, otherUser.userId)
        val path = "chats/$chatId"

        return try {
            val chatRef = db.collection("chats").document(chatId)
            val existingChat = chatRef.get().await()
            val participants = listOf(uid, otherUser.userId).sorted()

            val namesMap = mapOf(
                uid to myProfile.displayName,
                otherUser.userId to otherUser.displayName
            )
            val usernamesMap = mapOf(
                uid to myProfile.username,
                otherUser.userId to otherUser.username
            )
            val avatarsMap = buildMap<String, String> {
                myProfile.avatarUrl?.let { put(uid, it) }
                otherUser.avatarUrl?.let { put(otherUser.userId, it) }
            }

            if (!existingChat.exists()) {
                chatRef.set(
                    mapOf(
                        "participantIds" to participants,
                        "participantNames" to namesMap,
                        "participantUsernames" to usernamesMap,
                        "participantAvatars" to avatarsMap,
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                chatRef.update(
                    mapOf(
                        "participantNames" to namesMap,
                        "participantUsernames" to usernamesMap,
                        "participantAvatars" to avatarsMap,
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            chatRef.collection("messages").document(msgId).set(
                mapOf(
                    "chatId" to chatId,
                    "senderId" to uid,
                    "participantIds" to participants,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            sendNotification(
                recipientUid = otherUser.userId,
                senderUid = uid,
                senderName = myProfile.displayName,
                type = "message",
                targetId = uid,
                title = "رسالة جديدة من ${myProfile.displayName}",
                message = cleanText.take(100)
            )

            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Verification, VIP Requests, Reports & Hashtags
    // -------------------------------------------------------------------------

    fun observeMyVerificationRequests(userId: String): Flow<List<VerificationRequest>> = flow {
        emitAll(
            db.collection("verification_requests")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VerificationRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "verification_requests", userId)
                    throw e
                }
        )
    }

    fun observeMyVipRequests(userId: String): Flow<List<VipRequest>> = flow {
        emitAll(
            db.collection("vip_requests")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VipRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "vip_requests", userId)
                    throw e
                }
        )
    }

    fun observeTrendingHashtags(): Flow<List<TrendingHashtag>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("hashtags")
                .orderBy("usageCount", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TrendingHashtag::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<TrendingHashtag> { it.featured }
                                .thenByDescending { it.usageCount }
                        )
                }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "hashtags", uid)
                    throw e
                }
        )
    }

    suspend fun submitVerificationRequest(
        profile: UserProfile,
        ffPlayerId: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val reqId = "ver_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "verification_requests/$reqId"
        return try {
            val cleanId = ffPlayerId.trim()
            val cleanReason = reason.trim()
            require(cleanId.length in 4..20) { "معرف اللاعب في Free Fire يجب أن يكون 4 إلى 20 خانة" }
            require(cleanReason.length in 5..400) { "يرجى كتابة سبب طلب التوثيق (5 أحرف على الأقل)" }

            db.collection("verification_requests").document(reqId).set(
                mapOf(
                    "userId" to uid,
                    "username" to profile.username,
                    "displayName" to profile.displayName,
                    "ffPlayerId" to cleanId,
                    "reason" to cleanReason,
                    "followersCount" to profile.followersCount,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitVipRequest(
        profile: UserProfile,
        planTier: String,
        selectedFrame: String,
        note: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val reqId = "vip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "vip_requests/$reqId"
        return try {
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("username", profile.username)
                put("displayName", profile.displayName)
                put("planTier", planTier)
                put("selectedFrame", selectedFrame)
                note?.trim()?.takeIf { it.isNotBlank() }?.let { put("note", it.take(300)) }
                put("status", "pending")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("vip_requests").document(reqId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetLabel: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val repId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "reports/$repId"
        return try {
            val cleanReason = reason.trim()
            require(cleanReason.length in 3..400) { "يرجى توضيح سبب البلاغ (3 أحرف على الأقل)" }

            db.collection("reports").document(repId).set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "targetLabel" to targetLabel.take(100).ifBlank { targetId },
                    "reason" to cleanReason,
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // OWNER ADMIN PANEL (Protected by Verified Email y27985332@gmail.com)
    // -------------------------------------------------------------------------

    fun observeAllVerificationRequests(): Flow<List<VerificationRequest>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("verification_requests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(VerificationRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "verification_requests", uid)
                    throw e
                }
        )
    }

    fun observeAllVipRequests(): Flow<List<VipRequest>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("vip_requests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(VipRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "vip_requests", uid)
                    throw e
                }
        )
    }

    fun observeAllReports(): Flow<List<ReportItem>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(ReportItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "reports", uid)
                    throw e
                }
        )
    }

    suspend fun adminReviewVerification(
        request: VerificationRequest,
        approved: Boolean,
        rejectionReason: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "verification_requests/${request.id}"
        return try {
            val batch = db.batch()
            val status = if (approved) "approved" else "rejected"
            val reqUpdates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!approved && !rejectionReason.isNullOrBlank()) {
                reqUpdates["rejectionReason"] = rejectionReason.trim().take(300)
            }
            batch.update(db.collection("verification_requests").document(request.id), reqUpdates)
            if (approved) {
                batch.update(
                    db.collection("users").document(request.userId),
                    mapOf(
                        "verified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            if (approved) {
                // Propagate blue verified badge to existing user videos
                val userVideos = db.collection("videos").whereEqualTo("userId", request.userId).get().await()
                if (!userVideos.isEmpty) {
                    val vBatch = db.batch()
                    userVideos.documents.forEach { doc ->
                        vBatch.update(doc.reference, mapOf("authorVerified" to true, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                    vBatch.commit().await()
                }
            }

            sendNotification(
                recipientUid = request.userId,
                senderUid = uid,
                senderName = "إدارة FF Creator",
                type = "verification",
                targetId = request.userId,
                title = if (approved) "تم توثيق حسابك!" else "تحديث طلب التوثيق",
                message = if (approved) {
                    "مبروك! تمت الموافقة على طلب التوثيق الخاص بك وظهرت العلامة الزرقاء بجانب اسمك."
                } else {
                    "تم رفض طلب التوثيق. السبب: ${rejectionReason?.ifBlank { "لم يذكر سبب" } ?: "لم يذكر سبب"}"
                }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminReviewVip(
        request: VipRequest,
        approved: Boolean,
        rejectionReason: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "vip_requests/${request.id}"
        return try {
            val batch = db.batch()
            val status = if (approved) "approved" else "rejected"
            val reqUpdates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!approved && !rejectionReason.isNullOrBlank()) {
                reqUpdates["rejectionReason"] = rejectionReason.trim().take(300)
            }
            batch.update(db.collection("vip_requests").document(request.id), reqUpdates)
            if (approved) {
                batch.update(
                    db.collection("users").document(request.userId),
                    mapOf(
                        "vip" to true,
                        "vipFrame" to request.selectedFrame,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            if (approved) {
                val userVideos = db.collection("videos").whereEqualTo("userId", request.userId).get().await()
                if (!userVideos.isEmpty) {
                    val vBatch = db.batch()
                    userVideos.documents.forEach { doc ->
                        vBatch.update(
                            doc.reference,
                            mapOf(
                                "authorVip" to true,
                                "authorVipFrame" to request.selectedFrame,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    vBatch.commit().await()
                }
            }

            sendNotification(
                recipientUid = request.userId,
                senderUid = uid,
                senderName = "إدارة FF Creator",
                type = "vip",
                targetId = request.userId,
                title = if (approved) "تم تفعيل اشتراك VIP!" else "تحديث طلب VIP",
                message = if (approved) {
                    "أهلاً بك في نخبة VIP! تم تفعيل شارة VIP وإطارك الحصري الآن."
                } else {
                    "تم رفض طلب اشتراك VIP. السبب: ${rejectionReason?.ifBlank { "لم يذكر سبب" } ?: "لم يذكر سبب"}"
                }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminToggleBanUser(user: UserProfile): Result<Unit> {
        val path = "users/${user.userId}"
        return try {
            requireUserId()
            db.collection("users").document(user.userId).update(
                mapOf(
                    "banned" to !user.banned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminResolveReport(reportId: String, status: String, adminNote: String?): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!adminNote.isNullOrBlank()) {
                updates["adminNote"] = adminNote.trim().take(300)
            }
            db.collection("reports").document(reportId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminBroadcastNotification(title: String, body: String, allUsers: List<UserProfile>): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        require(cleanTitle.isNotBlank() && cleanBody.isNotBlank()) { "العنوان والرسالة مطلوبان" }
        val annId = "ann_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            db.collection("announcements").document(annId).set(
                mapOf(
                    "title" to cleanTitle.take(100),
                    "body" to cleanBody.take(500),
                    "senderId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            allUsers.forEach { target ->
                sendNotification(
                    recipientUid = target.userId,
                    senderUid = uid,
                    senderName = "إدارة FF Creator",
                    type = "broadcast",
                    targetId = annId,
                    title = cleanTitle,
                    message = cleanBody
                )
            }
            Result.success(allUsers.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "announcements/$annId")
            Result.failure(e)
        }
    }

    suspend fun adminSaveHashtag(tagRaw: String, featured: Boolean): Result<Unit> {
        val cleanTag = tagRaw.trim().let { if (it.startsWith("#")) it else "#$it" }.take(40)
        require(cleanTag.length >= 2) { "الهاشتاج قصير جداً" }
        val tagId = sanitizeId(cleanTag.removePrefix("#"))
        val path = "hashtags/$tagId"
        return try {
            requireUserId()
            val ref = db.collection("hashtags").document(tagId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "tag" to cleanTag,
                        "featured" to featured,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "tag" to cleanTag,
                        "usageCount" to 1L,
                        "featured" to featured,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteHashtag(tagId: String): Result<Unit> {
        val path = "hashtags/$tagId"
        return try {
            requireUserId()
            db.collection("hashtags").document(tagId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(firestoreError ?: error, op, path)
        }
    }
}
