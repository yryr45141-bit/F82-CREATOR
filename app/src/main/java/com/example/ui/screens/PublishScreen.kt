package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.theme.CyberCard
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FfMainViewModel

@Composable
fun PublishScreen(
    viewModel: FfMainViewModel,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val progress by viewModel.publishProgress.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var directMp4Url by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("#FreeFire #Headshot #FFCreator") }

    // Zero-permission Android Photo Picker for Video & Cover Image
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedVideoUri = uri }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedCoverUri = uri }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "نشر فيديو Free Fire",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "يتم ضغط الفيديو تلقائياً (720p H.264 MP4) لتقليل استهلاك الإنترنت قبل النشر.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            // Video Picker Card
            Surface(
                color = CyberCard,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        Brush.linearGradient(listOf(NeonCrimson, ElectricPurple)),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(enabled = !isBusy) {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                    .testTag("pick_video_button")
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = if (selectedVideoUri != null) Icons.Filled.CheckCircle else Icons.Filled.VideoFile,
                        contentDescription = "اختيار فيديو MP4",
                        tint = if (selectedVideoUri != null) EmeraldSuccess else NeonCrimson,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedVideoUri != null) "تم اختيار الفيديو جاهز للضغط والنشر" else "اختيار فيديو MP4 من الهاتف",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedVideoUri?.lastPathSegment ?: "اضغط لفتح معرض الفيديوهات",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Cover Image Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pick_cover_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = ElectricPurple)
                    Spacer(Modifier.size(8.dp))
                    Text(if (selectedCoverUri != null) "تغيير صورة الغلاف" else "اختيار صورة غلاف", color = TextPrimary)
                }

                if (selectedCoverUri != null) {
                    AsyncImage(
                        model = selectedCoverUri,
                        contentDescription = "معاينة الغلاف",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, NeonCrimson, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Optional Direct MP4 HTTPS Link
            OutlinedTextField(
                value = directMp4Url,
                onValueChange = { directMp4Url = it.take(1000) },
                label = { Text("أو أدخل رابط فيديو MP4 مباشر (اختياري)") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("direct_mp4_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(300) },
                label = { Text("وصف الفيديو") },
                placeholder = { Text("مثال: هيدشوت One Tap في الرانكد...") },
                minLines = 3,
                maxLines = 5,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_description_input")
            )

            OutlinedTextField(
                value = hashtags,
                onValueChange = { hashtags = it.take(160) },
                label = { Text("الهاشتاجات") },
                placeholder = { Text("#FreeFire #Headshot #Booyah") },
                singleLine = true,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_hashtags_input")
            )

            if (progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pct = ((progress ?: 0f) * 100).toInt()
                    Text(
                        text = if (pct < 85) "جاري ضغط الفيديو لتقليل البيانات ($pct%)..." else "جاري الرفع والنشر ($pct%)...",
                        color = NeonCrimson,
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonCrimson,
                        trackColor = CyberCard
                    )
                }
            }

            val canPublish = !isBusy && description.trim().isNotEmpty() &&
                (selectedVideoUri != null || directMp4Url.trim().startsWith("http"))

            Button(
                onClick = {
                    viewModel.publishVideo(
                        context = context,
                        videoUri = selectedVideoUri,
                        coverUri = selectedCoverUri,
                        directMp4Url = directMp4Url,
                        description = description,
                        hashtagsRaw = hashtags,
                        onSuccess = {
                            selectedVideoUri = null
                            selectedCoverUri = null
                            directMp4Url = ""
                            description = ""
                            onPublished()
                        }
                    )
                },
                enabled = canPublish,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("publish_submit_button")
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (isBusy) "جاري النشر..." else "نشر الفيديو الآن",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
