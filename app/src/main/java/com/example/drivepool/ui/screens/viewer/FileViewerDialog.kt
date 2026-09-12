package com.example.drivepool.ui.screens.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.drivepool.data.model.DriveNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ViewingFileTarget(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val file: File,
    val sourceDescription: String
)

fun launchExternalViewer(context: Context, file: File, mimeType: String) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.drivepool.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open \"${file.name}\" with...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No compatible app found to open this file.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun FileViewerDialog(
    target: ViewingFileTarget,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isImage = target.mimeType.startsWith("image/") ||
            target.name.endsWith(".jpg", ignoreCase = true) ||
            target.name.endsWith(".jpeg", ignoreCase = true) ||
            target.name.endsWith(".png", ignoreCase = true) ||
            target.name.endsWith(".webp", ignoreCase = true) ||
            target.name.endsWith(".gif", ignoreCase = true)

    val isPdf = target.mimeType == "application/pdf" || target.name.endsWith(".pdf", ignoreCase = true)

    val isText = target.mimeType.startsWith("text/") ||
            target.name.endsWith(".txt", ignoreCase = true) ||
            target.name.endsWith(".json", ignoreCase = true) ||
            target.name.endsWith(".xml", ignoreCase = true) ||
            target.name.endsWith(".csv", ignoreCase = true) ||
            target.name.endsWith(".log", ignoreCase = true) ||
            target.name.endsWith(".md", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${DriveNode.formatBytes(target.sizeBytes)} • ${target.sourceDescription}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Open With External App Button
                    IconButton(
                        onClick = { launchExternalViewer(context, target.file, target.mimeType) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open with external app",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // Content Viewers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isImage -> ImageViewer(
                            file = target.file,
                            onOpenExternal = { launchExternalViewer(context, target.file, target.mimeType) }
                        )
                        isPdf -> PdfViewer(file = target.file, onOpenExternal = { launchExternalViewer(context, target.file, "application/pdf") })
                        isText -> TextViewer(file = target.file)
                        else -> GenericDocumentViewer(
                            target = target,
                            onOpenExternal = { launchExternalViewer(context, target.file, target.mimeType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewer(
    file: File,
    onOpenExternal: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() == 0L) {
                    errorMessage = "Image file is empty or missing data."
                    isLoading = false
                    return@withContext
                }

                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)

                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    errorMessage = "Unable to decode image format."
                    isLoading = false
                    return@withContext
                }

                // Subsample large images to prevent OOM
                var sampleSize = 1
                while (opts.outWidth / sampleSize > 2048 || opts.outHeight / sampleSize > 2048) {
                    sampleSize *= 2
                }
                opts.inJustDecodeBounds = false
                opts.inSampleSize = sampleSize
                val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (decoded != null) {
                    bitmap = decoded
                } else {
                    errorMessage = "Could not decode image."
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load image: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                    val maxOffsetY = (size.height * (scale - 1f)) / 2f
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                        y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading image...", color = Color.White)
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )

                if (scale > 1.05f) {
                    FilledTonalButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ZoomOut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Zoom (${(scale * 100).toInt()}%)")
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: "Unable to render image preview",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenExternal) {
                        Text("Open with External Viewer")
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(
    file: File,
    onOpenExternal: () -> Unit
) {
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun renderPage(index: Int) {
        val r = renderer ?: return
        if (index < 0 || index >= r.pageCount) return
        try {
            val page = r.openPage(index)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            currentPageBitmap = bmp
            currentPageIndex = index
        } catch (e: Exception) {
            errorMessage = "Page render error: ${e.localizedMessage}"
        }
    }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd = descriptor
                val pdfRenderer = PdfRenderer(descriptor)
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
                renderPage(0)
            } catch (e: Exception) {
                errorMessage = "Could not open PDF: ${e.localizedMessage}"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) { }
        }
    }

    if (errorMessage != null) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpenExternal) {
                Text("Open in System PDF Viewer")
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PDF Page Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                currentPageBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } ?: Text("Rendering PDF page...", color = Color.DarkGray)
            }

            // PDF Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { if (currentPageIndex > 0) renderPage(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous Page")
                }

                Text(
                    text = "Page ${currentPageIndex + 1} of $pageCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = { if (currentPageIndex < pageCount - 1) renderPage(currentPageIndex + 1) },
                    enabled = currentPageIndex < pageCount - 1
                ) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next Page")
                }
            }
        }
    }
}

@Composable
fun TextViewer(file: File) {
    var textContent by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                // Read up to first 250 KB
                val text = file.bufferedReader().useLines { lines ->
                    lines.take(2000).joinToString("\n")
                }
                textContent = text
            } catch (e: Exception) {
                textContent = "Error reading text file: ${e.localizedMessage}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = {
                    textContent?.let {
                        clipboard.setText(AnnotatedString(it))
                        Toast.makeText(context, "Copied text to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Text", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = textContent ?: "Loading text...",
                color = Color(0xFFD4D4D4),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun GenericDocumentViewer(
    target: ViewingFileTarget,
    onOpenExternal: () -> Unit
) {
    val isOffice = target.name.endsWith(".doc", ignoreCase = true) ||
            target.name.endsWith(".docx", ignoreCase = true) ||
            target.name.endsWith(".xls", ignoreCase = true) ||
            target.name.endsWith(".xlsx", ignoreCase = true) ||
            target.name.endsWith(".ppt", ignoreCase = true) ||
            target.name.endsWith(".pptx", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isOffice) Color(0xFF2B579A).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = if (isOffice) Color(0xFF2B579A) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = target.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOffice)
                "Office document ready. Open with Google Docs, Microsoft Office, or WPS Office to view and edit."
            else
                "File format ready to open in external application.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenExternal,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isOffice) "Open in Office App" else "Open in Compatible App")
        }
    }
}
