package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.model.VipFrameStyle
import com.example.ui.theme.CyberCard
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedBlue
import com.example.ui.theme.VipGold
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun UserAvatarWithFrame(
    avatarUrl: String?,
    displayName: String,
    vip: Boolean,
    vipFrame: String,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null
) {
    val frameStyle = if (vip) VipFrameStyle.fromId(vipFrame) else VipFrameStyle.NONE
    val infiniteTransition = rememberInfiniteTransition(label = "vip_frame")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frame_rotation"
    )

    val frameBrush = when (frameStyle) {
        VipFrameStyle.CRIMSON_FLAME -> Brush.sweepGradient(listOf(NeonCrimson, Color(0xFFFF7A00), NeonCrimson))
        VipFrameStyle.VIOLET_CYBER -> Brush.sweepGradient(listOf(ElectricPurple, VerifiedBlue, NeonViolet, ElectricPurple))
        VipFrameStyle.ROYAL_GOLD -> Brush.sweepGradient(listOf(VipGold, NeonCrimson, VipGold, Color(0xFFFFF0A0), VipGold))
        VipFrameStyle.NONE -> Brush.linearGradient(listOf(NeonCrimson.copy(alpha = 0.55f), ElectricPurple.copy(alpha = 0.55f)))
    }

    val borderThickness = if (frameStyle != VipFrameStyle.NONE) 3.dp else 1.5.dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (frameStyle != VipFrameStyle.NONE) Modifier.rotate(angle) else Modifier)
                .border(borderThickness, frameBrush, CircleShape)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(borderThickness + 1.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(ElectricPurple.copy(alpha = 0.35f), CyberCard))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "صورة حساب $displayName",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = displayName.trim().firstOrNull()?.uppercase() ?: "F",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp
                )
            }
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "حساب موثق",
        tint = VerifiedBlue,
        modifier = modifier.size(size)
    )
}

@Composable
fun VipBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = ElectricPurple.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Brush.horizontalGradient(listOf(VipGold, ElectricPurple)), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "VIP",
                tint = VipGold,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = "VIP",
                color = VipGold,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GoogleSignInButton(
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isLoading = true
            AuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    onAuthError(msg)
                },
                scope = scope,
                onAuthCancelled = { isLoading = false }
            )
        },
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCrimson,
            contentColor = TextPrimary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = TextPrimary,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = stringResource(R.string.sign_in_google),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReportDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val presets = listOf("محتوى مخالف", "إساءة أو تنمر", "احتيال أو هاكات", "انتحال شخصية")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = NeonCrimson)
                Text(text = title, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر سبب البلاغ أو اكتب تفاصيل إضافية لمراجعة الإدارة:",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { reason = preset },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(preset, color = if (reason == preset) NeonCrimson else TextPrimary)
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(400) },
                    label = { Text("تفاصيل البلاغ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_reason_input"),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().length >= 3) {
                        onSubmit(reason.trim())
                        onDismiss()
                    }
                },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("إرسال البلاغ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_feed),
            contentDescription = title,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(NeonCrimson, ElectricPurple)),
                    RoundedCornerShape(24.dp)
                ),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (ctaLabel != null && onCtaClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(ctaLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun formatCompactCount(count: Long): String = when {
    count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000f)
    else -> count.toString()
}

fun formatTimeAr(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "الآن"
    val diffSec = ((System.currentTimeMillis() - date.time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "الآن"
        diffSec < 3600L -> "منذ ${diffSec / 60} د"
        diffSec < 86400L -> "منذ ${diffSec / 3600} س"
        else -> SimpleDateFormat("dd/MM HH:mm", Locale.US).format(date)
    }
}
