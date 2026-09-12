package com.example.drivepool.ui.screens.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.drivepool.data.model.DriveNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ViewerFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceDescription: String,
    val file: File? = null,
    val phoneFile: com.example.drivepool.data.model.LocalPhoneFile? = null,
    val poolFile: com.example.drivepool.data.model.PoolFile? = null
)

data class FileViewerSession(
    val initialIndex: Int = 0,
    val items: List<ViewerFileItem>
)

object BitmapMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(16 * 1024)
    private val cache = object : android.util.LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

data class ViewingFileTarget(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val file: File,
    val sourceDescription: String,
    val currentIndex: Int = 0,
    val totalCount: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false
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

fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.drivepool.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (mimeType.isNotBlank()) mimeType else "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share \"${file.name}\" via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FileViewerDialog(
    target: ViewingFileTarget,
    onDismiss: () -> Unit,
    onNavigateNext: (() -> Unit)? = null,
    onNavigatePrevious: (() -> Unit)? = null
) {
    val session = remember(target) {
        FileViewerSession(
            initialIndex = target.currentIndex.coerceAtLeast(0),
            items = listOf(
                ViewerFileItem(
                    id = target.name,
                    name = target.name,
                    mimeType = target.mimeType,
                    sizeBytes = target.sizeBytes,
                    sourceDescription = target.sourceDescription,
                    file = target.file
                )
            )
        )
    }
    FileViewerDialog(session = session, onDismiss = onDismiss)
}

@Composable
fun FileViewerDialog(
    session: FileViewerSession,
    onDismiss: () -> Unit,
    onPrepareFile: (suspend (ViewerFileItem) -> File?)? = null
) {
    if (session.items.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }

    val initialPage = session.initialIndex.coerceIn(0, session.items.size - 1)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { session.items.size }
    )

    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isCurrentPageZoomed = false
    }

    val currentItem = session.items[pagerState.currentPage.coerceIn(0, session.items.size - 1)]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. High Performance 120Hz Native Compose HorizontalPager
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = session.items[page]
                val isCurrent = page == pagerState.currentPage

                FileViewerPage(
                    item = item,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    onZoomChanged = { zoomed ->
                        if (isCurrent) {
                            isCurrentPageZoomed = zoomed
                        }
                    },
                    onPrepareFile = onPrepareFile
                )
            }

            // Animated Floating Top Header Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD0F172A),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentItem.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val countText = if (session.items.size > 1) {
                                "${pagerState.currentPage + 1} of ${session.items.size} • "
                            } else ""
                            Text(
                                text = "$countText${DriveNode.formatBytes(currentItem.sizeBytes)} • ${currentItem.sourceDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Fullscreen button (hide controls)
                        IconButton(onClick = { showControls = false }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen",
                                tint = Color.White
                            )
                        }

                        // Share via WhatsApp, Gmail, system share sheet
                        IconButton(
                            onClick = {
                                val file = currentItem.file ?: (currentItem.phoneFile?.let { File(it.path).takeIf { f -> f.exists() } })
                                if (file != null && file.exists()) {
                                    shareFile(context, file, currentItem.mimeType)
                                } else {
                                    Toast.makeText(context, "Preparing file to share...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share via WhatsApp / other apps",
                                tint = Color(0xFF4ADE80)
                            )
                        }

                        // External app button
                        IconButton(
                            onClick = {
                                val file = currentItem.file ?: (currentItem.phoneFile?.let { File(it.path).takeIf { f -> f.exists() } })
                                if (file != null && file.exists()) {
                                    launchExternalViewer(context, file, currentItem.mimeType)
                                } else {
                                    Toast.makeText(context, "Preparing file to open...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open with external app",
                                tint = Color(0xFF60A5FA)
                            )
                        }
                    }
                }
            }

            // 5. Floating Restore Controls Button when in pure Full Screen
            if (!showControls) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp),
                    shape = CircleShape,
                    color = Color(0x99000000),
                    shadowElevation = 4.dp
                ) {
                    IconButton(
                        onClick = { showControls = true },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Show Controls",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileViewerPage(
    item: ViewerFileItem,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onPrepareFile: (suspend (ViewerFileItem) -> File?)? = null
) {
    val context = LocalContext.current
    var resolvedFile by remember(item.id) {
        mutableStateOf(item.file ?: item.phoneFile?.let { File(it.path).takeIf { f -> f.exists() } })
    }
    var isLoading by remember(item.id) { mutableStateOf(resolvedFile == null) }

    LaunchedEffect(item.id) {
        if (resolvedFile == null && onPrepareFile != null) {
            val f = onPrepareFile(item)
            if (f != null && f.exists()) {
                resolvedFile = f
            }
            isLoading = false
        }
    }

    val isImage = item.mimeType.startsWith("image/") ||
            item.name.endsWith(".jpg", ignoreCase = true) ||
            item.name.endsWith(".jpeg", ignoreCase = true) ||
            item.name.endsWith(".png", ignoreCase = true) ||
            item.name.endsWith(".webp", ignoreCase = true) ||
            item.name.endsWith(".gif", ignoreCase = true)

    val isPdf = item.mimeType == "application/pdf" || item.name.endsWith(".pdf", ignoreCase = true)

    val isText = item.mimeType.startsWith("text/") ||
            item.name.endsWith(".txt", ignoreCase = true) ||
            item.name.endsWith(".json", ignoreCase = true) ||
            item.name.endsWith(".xml", ignoreCase = true) ||
            item.name.endsWith(".csv", ignoreCase = true) ||
            item.name.endsWith(".log", ignoreCase = true) ||
            item.name.endsWith(".md", ignoreCase = true)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading \"${item.name}\"...", color = Color.White)
            }
        } else if (resolvedFile != null) {
            when {
                isImage -> ImageViewer(
                    file = resolvedFile!!,
                    showControls = showControls,
                    onToggleControls = onToggleControls,
                    onZoomChanged = onZoomChanged,
                    onOpenExternal = { launchExternalViewer(context, resolvedFile!!, item.mimeType) }
                )
                isPdf -> PdfViewer(
                    file = resolvedFile!!,
                    showControls = showControls,
                    onToggleControls = onToggleControls,
                    onOpenExternal = { launchExternalViewer(context, resolvedFile!!, "application/pdf") }
                )
                isText -> TextViewer(
                    file = resolvedFile!!,
                    showControls = showControls,
                    onToggleControls = onToggleControls
                )
                else -> GenericDocumentViewer(
                    target = ViewingFileTarget(
                        name = item.name,
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes,
                        file = resolvedFile!!,
                        sourceDescription = item.sourceDescription
                    ),
                    onOpenExternal = { launchExternalViewer(context, resolvedFile!!, item.mimeType) }
                )
            }
        } else {
            GenericDocumentViewer(
                target = ViewingFileTarget(
                    name = item.name,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    file = File(""),
                    sourceDescription = item.sourceDescription
                ),
                onOpenExternal = {}
            )
        }
    }
}

@Composable
fun ZoomControlsBar(
    scale: Float,
    minScale: Float = 1f,
    maxScale: Float = 8f,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xDD0F172A),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = onZoomOut,
                enabled = scale > minScale + 0.05f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = if (scale > minScale + 0.05f) Color.White else Color.DarkGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onReset)
                    .background(Color(0xFF334155))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onZoomIn,
                enabled = scale < maxScale - 0.05f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = if (scale < maxScale - 0.05f) Color.White else Color.DarkGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (scale > minScale + 0.05f) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Zoom to Fit",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageViewer(
    file: File,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onOpenExternal: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
    onNavigateNext: (() -> Unit)? = null,
    onNavigatePrevious: (() -> Unit)? = null
) {
    val cached = remember(file.absolutePath) { BitmapMemoryCache.get(file.absolutePath) }
    var bitmap by remember(file.absolutePath) { mutableStateOf(cached) }
    var isLoading by remember(file.absolutePath) { mutableStateOf(bitmap == null) }
    var errorMessage by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    var scale by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var offset by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath) {
        if (bitmap == null) {
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

                    var sampleSize = 1
                    while (opts.outWidth / sampleSize > 2560 || opts.outHeight / sampleSize > 2560) {
                        sampleSize *= 2
                    }
                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sampleSize
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (decoded != null) {
                        BitmapMemoryCache.put(file.absolutePath, decoded)
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(file.absolutePath) {
                awaitEachGesture {
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var panAccumulator = Offset.Zero

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        val pressedCount = event.changes.count { it.pressed }
                        val isZoomed = scale > 1.05f

                        // When not zoomed and single finger: do NOT consume any gesture!
                        // This lets HorizontalPager handle 1-finger horizontal swipes smoothly.
                        if (!isZoomed && pressedCount < 2) {
                            continue
                        }

                        val panChange = event.calculatePan()
                        val zoomChange = event.calculateZoom()

                        if (!pastTouchSlop) {
                            panAccumulator += panChange
                            val panMotion = panAccumulator.getDistance()
                            val zoomMotion = kotlin.math.abs(1f - zoomChange)

                            if (pressedCount >= 2 || zoomMotion > 0.02f || (isZoomed && panMotion > touchSlop)) {
                                pastTouchSlop = true
                            }
                        }

                        if (pastTouchSlop) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 8f)
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + panChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + panChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                            scale = newScale
                            onZoomChanged(newScale > 1.05f)

                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
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

                // Floating Zoom Controls Bar
                AnimatedVisibility(
                    visible = showControls || scale > 1.05f,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    ZoomControlsBar(
                        scale = scale,
                        minScale = 1f,
                        maxScale = 8f,
                        onZoomIn = {
                            val nextScale = minOf(8f, scale + 0.5f)
                            scale = nextScale
                            onZoomChanged(nextScale > 1.05f)
                        },
                        onZoomOut = {
                            val nextScale = maxOf(1f, scale - 0.5f)
                            scale = nextScale
                            if (nextScale == 1f) {
                                offset = Offset.Zero
                                onZoomChanged(false)
                            }
                        },
                        onReset = {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        }
                    )
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
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun renderPage(index: Int) {
        val r = renderer ?: return
        if (index < 0 || index >= r.pageCount) return
        try {
            val page = r.openPage(index)
            // 2.5x supersampling for razor sharp clarity on zoom
            val superSample = 2.5f
            val bmp = Bitmap.createBitmap(
                (page.width * superSample).toInt(),
                (page.height * superSample).toInt(),
                Bitmap.Config.ARGB_8888
            )
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            currentPageBitmap = bmp
            currentPageIndex = index
            // Reset zoom to clean fit on page flip
            scale = 1f
            offset = Offset.Zero
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
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .pointerInput(file.absolutePath) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(file.absolutePath) {
                    awaitEachGesture {
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var panAccumulator = Offset.Zero

                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (canceled) break

                            val pressedCount = event.changes.count { it.pressed }
                            val isZoomed = scale > 1.05f

                            if (!isZoomed && pressedCount < 2) {
                                continue
                            }

                            val panChange = event.calculatePan()
                            val zoomChange = event.calculateZoom()

                            if (!pastTouchSlop) {
                                panAccumulator += panChange
                                val panMotion = panAccumulator.getDistance()
                                val zoomMotion = kotlin.math.abs(1f - zoomChange)

                                if (pressedCount >= 2 || zoomMotion > 0.02f || (isZoomed && panMotion > touchSlop)) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                scale = newScale

                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            currentPageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp, horizontal = 8.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } ?: CircularProgressIndicator(color = Color.White)

            // Bottom Controls Bar (Page Nav + Zoom)
            AnimatedVisibility(
                visible = showControls || scale > 1.05f,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xDD0F172A),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Page Nav
                        IconButton(
                            onClick = { if (currentPageIndex > 0) renderPage(currentPageIndex - 1) },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous Page",
                                tint = if (currentPageIndex > 0) Color.White else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "${currentPageIndex + 1} / $pageCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        IconButton(
                            onClick = { if (currentPageIndex < pageCount - 1) renderPage(currentPageIndex + 1) },
                            enabled = currentPageIndex < pageCount - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next Page",
                                tint = if (currentPageIndex < pageCount - 1) Color.White else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color(0xFF334155))
                        )

                        // Zoom Controls
                        IconButton(
                            onClick = {
                                val next = maxOf(1f, scale - 0.5f)
                                scale = next
                                if (next == 1f) offset = Offset.Zero
                            },
                            enabled = scale > 1.05f,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = if (scale > 1.05f) Color.White else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                .background(Color(0xFF334155))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                scale = minOf(6f, scale + 0.5f)
                            },
                            enabled = scale < 5.95f,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = if (scale < 5.95f) Color.White else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (scale > 1.05f) {
                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Zoom",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextViewer(
    file: File,
    showControls: Boolean,
    onToggleControls: () -> Unit
) {
    var textContent by remember { mutableStateOf<String?>(null) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
    ) {
        // Text Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 80.dp, start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
        ) {
            Text(
                text = textContent ?: "Loading text...",
                color = Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.5f).sp
            )
        }

        // Floating Bottom Text Action Bar (Zoom Font & Copy)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xDD0F172A),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { fontSizeSp = maxOf(10f, fontSizeSp - 2f) },
                        enabled = fontSizeSp > 10f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("A-", color = if (fontSizeSp > 10f) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { fontSizeSp = 13f }
                            .background(Color(0xFF334155))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${fontSizeSp.toInt()}sp",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { fontSizeSp = minOf(26f, fontSizeSp + 2f) },
                        enabled = fontSizeSp < 26f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("A+", color = if (fontSizeSp < 26f) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color(0xFF334155))
                    )

                    FilledTonalButton(
                        onClick = {
                            textContent?.let {
                                clipboard.setText(AnnotatedString(it))
                                Toast.makeText(context, "Copied text to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GenericDocumentViewer(
    target: ViewingFileTarget,
    onOpenExternal: () -> Unit,
    onNavigateNext: (() -> Unit)? = null,
    onNavigatePrevious: (() -> Unit)? = null
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
            .background(Color(0xFF0B1120))
            .pointerInput(onNavigateNext, onNavigatePrevious) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalPanX = 0f
                    var totalPanY = 0f
                    val touchSlop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val pan = change.position - change.previousPosition
                            totalPanX += pan.x
                            totalPanY += pan.y
                            if (kotlin.math.abs(totalPanX) > touchSlop) {
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val thresholdPx = 90f
                    val isHorizontal = kotlin.math.abs(totalPanX) > kotlin.math.abs(totalPanY) * 1.2f
                    if (isHorizontal && totalPanX < -thresholdPx) {
                        onNavigateNext?.invoke()
                    } else if (isHorizontal && totalPanX > thresholdPx) {
                        onNavigatePrevious?.invoke()
                    }
                }
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isOffice) Color(0xFF2B579A).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = if (isOffice) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = target.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOffice)
                "Office document ready. Open with Google Docs, Sheets, Microsoft 365, or WPS Office to view and edit."
            else
                "File format ready to open with compatible external app.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onOpenExternal,
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isOffice) "Open in Office App" else "Open in Compatible App")
        }
    }
}
