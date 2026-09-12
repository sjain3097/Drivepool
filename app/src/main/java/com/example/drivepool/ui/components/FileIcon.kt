package com.example.drivepool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.drivepool.data.model.FileCategory

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
