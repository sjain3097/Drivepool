package com.example.drivepool.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import com.example.drivepool.data.model.PoolFile
import com.example.drivepool.util.ThumbnailLoader
import java.io.File

@Composable
fun FileThumbnail(
    file: PoolFile,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val cachedFile = remember(file.id, file.name) {
        val f = File(context.cacheDir, "cloud_view_cache/${file.id}_${file.name}")
        if (f.exists() && f.length() > 0) f else null
    }

    FileThumbnailBase(
        name = file.name,
        mimeType = file.mimeType,
        category = file.category,
        cacheKey = "pool_${file.id}_${cachedFile?.lastModified() ?: 0L}",
        pathOrUri = cachedFile?.absolutePath,
        modifier = modifier,
        size = size
    )
}

@Composable
fun FileThumbnail(
    file: LocalPhoneFile,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    FileThumbnailBase(
        name = file.name,
        mimeType = file.mimeType,
        category = file.category,
        cacheKey = "phone_${file.id}_${file.modifiedTime}",
        pathOrUri = file.path,
        modifier = modifier,
        size = size
    )
}

@Composable
fun FileThumbnailBase(
    name: String,
    mimeType: String,
    category: FileCategory,
    cacheKey: String,
    pathOrUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    var bitmap by remember(cacheKey) {
        mutableStateOf(ThumbnailLoader.getCached(cacheKey))
    }

    LaunchedEffect(cacheKey, pathOrUri) {
        if (bitmap == null && !pathOrUri.isNullOrBlank()) {
            bitmap = ThumbnailLoader.loadThumbnail(
                context = context,
                cacheKey = cacheKey,
                pathOrUri = pathOrUri,
                mimeType = mimeType,
                targetSizePx = (size.value * 3.5f).toInt().coerceIn(120, 240)
            )
        }
    }

    val isVideo = mimeType.startsWith("video/", ignoreCase = true) ||
            name.endsWith(".mp4", ignoreCase = true) ||
            name.endsWith(".mkv", ignoreCase = true)
    val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
            name.endsWith(".pdf", ignoreCase = true)
    val ext = name.substringAfterLast('.', "").uppercase().take(4)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .border(BorderStroke(0.75.dp, Color(0x33000000)), RoundedCornerShape(10.dp))
            )

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(size * 0.42f)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.28f)
                    )
                }
            }

            if (isPdf) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xDDE53935)
                ) {
                    Text(
                        text = "PDF",
                        color = Color.White,
                        fontSize = (size.value * 0.17f).coerceIn(7f, 10f).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                    )
                }
            }
        } else {
            // High quality category fallback with extension badge
            FileIcon(
                category = category,
                modifier = Modifier.fillMaxSize(),
                size = size
            )

            if (ext.isNotBlank() && ext != name.uppercase()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xDD374151)
                ) {
                    Text(
                        text = ext,
                        color = Color.White,
                        fontSize = (size.value * 0.17f).coerceIn(7f, 10f).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FileIcon(
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val (bgColor, iconColor, icon) = when (category) {
        FileCategory.DOCUMENTS -> Triple(
            Color(0xFFE8F0FE),
            Color(0xFF1A73E8),
            Icons.Default.Description
        )
        FileCategory.IMAGES -> Triple(
            Color(0xFFE6F4EA),
            Color(0xFF137333),
            Icons.Default.Image
        )
        FileCategory.VIDEOS -> Triple(
            Color(0xFFF3E8FD),
            Color(0xFF8430CE),
            Icons.Default.Movie
        )
        FileCategory.AUDIO -> Triple(
            Color(0xFFFEF7E0),
            Color(0xFFB06000),
            Icons.Default.AudioFile
        )
        FileCategory.ARCHIVES -> Triple(
            Color(0xFFFCE8E6),
            Color(0xFFC5221F),
            Icons.Default.FolderZip
        )
        FileCategory.ALL, FileCategory.OTHER -> Triple(
            Color(0xFFE8EAED),
            Color(0xFF5F6368),
            Icons.Default.InsertDriveFile
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = iconColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
