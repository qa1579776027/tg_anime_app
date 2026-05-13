@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cameron.tganime.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private enum class DragAxis { Undecided, Horizontal, VerticalLeft, VerticalRight }

@Stable
private class HintState {
    var text by mutableStateOf("")
    var visible by mutableStateOf(false)
}

@Composable
fun PlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Default to portrait; landscape only when the user taps fullscreen.
    var isFullscreen by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val win = activity?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            win?.attributes = win?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            // Make sure system bars come back if we disposed while fullscreen.
            val w = win ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(w, true)
            WindowInsetsControllerCompat(w, w.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Apply the fullscreen state: rotate the activity and toggle system bars.
    LaunchedEffect(isFullscreen) {
        val win = activity?.window ?: return@LaunchedEffect
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val ctrl = WindowInsetsControllerCompat(win, win.decorView)
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(win, true)
            WindowInsetsControllerCompat(win, win.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Back press exits fullscreen first; only then leaves the player.
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    // ExoPlayer instance.
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Reactive player state.
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pressSpeedSaved by remember { mutableFloatStateOf(1.0f) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                errorMsg = error.errorCodeName + ": " + (error.message ?: "")
            }
            override fun onPlaybackParametersChanged(params: PlaybackParameters) {
                speed = params.speed
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // Tick the position while playing (or buffering / paused — we still want UI in sync).
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration == 0L) duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun bumpControls() {
        controlsVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(Unit) { bumpControls() }

    // Volume + brightness sourced from system at first composition.
    val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableFloatStateOf(audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
        mutableFloatStateOf(initial)
    }
    LaunchedEffect(brightness) {
        val win = activity?.window ?: return@LaunchedEffect
        win.attributes = win.attributes.apply { screenBrightness = brightness }
    }

    // Transient hint (volume / brightness / +-Xs etc.).
    val hint = remember { HintState() }
    var hintJob by remember { mutableStateOf<Job?>(null) }
    fun showHint(text: String) {
        hint.text = text
        hint.visible = true
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(800)
            hint.visible = false
        }
    }

    // Drag-seek state.
    var dragAxis by remember { mutableStateOf(DragAxis.Undecided) }
    var seekStartPos by remember { mutableLongStateOf(-1L) }
    var seekTarget by remember { mutableLongStateOf(-1L) }

    // Player owns the whole available area in both orientations; the surface
    // itself letterboxes the video via AspectRatioFrameLayout.RESIZE_MODE_FIT,
    // so portrait shows a centered 16:9 video with black bars top/bottom and
    // landscape (fullscreen) fills naturally.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1) Video surface (Media3 PlayerView, no built-in controls).
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
        )

        // 2) Tap layer (separate pointerInput so it cooperates with the drag layer).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { bumpControls() },
                        onDoubleTap = { offset ->
                            val target = if (offset.x < size.width / 2f) {
                                (player.currentPosition - 10_000).coerceAtLeast(0L)
                            } else {
                                (player.currentPosition + 10_000).coerceAtMost(player.duration)
                            }
                            player.seekTo(target)
                            showHint(if (offset.x < size.width / 2f) "-10 秒" else "+10 秒")
                            bumpControls()
                        },
                        onLongPress = {
                            pressSpeedSaved = player.playbackParameters.speed
                            player.setPlaybackSpeed(2.0f)
                            showHint("长按 2.0× 倍速")
                        },
                        onPress = {
                            val released = tryAwaitRelease()
                            if (released && player.playbackParameters.speed != pressSpeedSaved
                                && pressSpeedSaved > 0
                            ) {
                                player.setPlaybackSpeed(pressSpeedSaved)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragAxis = DragAxis.Undecided
                            seekStartPos = -1L
                            seekTarget = -1L
                        },
                        onDragEnd = {
                            if (dragAxis == DragAxis.Horizontal && seekTarget >= 0) {
                                player.seekTo(seekTarget)
                            }
                            dragAxis = DragAxis.Undecided
                            seekStartPos = -1L
                            seekTarget = -1L
                            bumpControls()
                        },
                        onDragCancel = {
                            dragAxis = DragAxis.Undecided
                            seekStartPos = -1L
                            seekTarget = -1L
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()

                            if (dragAxis == DragAxis.Undecided) {
                                // Lock axis once the user has moved past the slop threshold.
                                val totalDx = change.position.x - change.previousPosition.x
                                val totalDy = change.position.y - change.previousPosition.y
                                dragAxis = if (abs(drag.x) > abs(drag.y)) {
                                    DragAxis.Horizontal
                                } else {
                                    if (change.position.x < w / 2f)
                                        DragAxis.VerticalLeft else DragAxis.VerticalRight
                                }
                                // No-op consumption of unused vars (keeps lint happy on Kotlin 2.0).
                                @Suppress("UNUSED_EXPRESSION") totalDx
                                @Suppress("UNUSED_EXPRESSION") totalDy
                            }

                            when (dragAxis) {
                                DragAxis.Horizontal -> {
                                    if (seekStartPos < 0) seekStartPos = player.currentPosition
                                    val dur = player.duration.coerceAtLeast(1L)
                                    val pxDelta = drag.x
                                    // Half-screen swipe ~ 50% of the duration. Tune the divisor for feel.
                                    val deltaMs = (pxDelta / w * dur / 1.5f).toLong()
                                    val base = if (seekTarget < 0) seekStartPos else seekTarget
                                    seekTarget = (base + deltaMs).coerceIn(0L, dur)
                                    val signed = seekTarget - seekStartPos
                                    showHint(
                                        "${formatTime(seekTarget)} / ${formatTime(dur)}  " +
                                            "(${if (signed >= 0) "+" else "-"}${formatTime(abs(signed))})"
                                    )
                                }
                                DragAxis.VerticalLeft -> {
                                    val deltaUnit = -drag.y / h
                                    brightness = (brightness + deltaUnit).coerceIn(0.01f, 1.0f)
                                    showHint("亮度 ${(brightness * 100).toInt()}%")
                                }
                                DragAxis.VerticalRight -> {
                                    val deltaUnit = -drag.y / h
                                    volume = (volume + deltaUnit).coerceIn(0.0f, 1.0f)
                                    audioMgr.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (volume * maxVolume).toInt(),
                                        0,
                                    )
                                    showHint("音量 ${(volume * 100).toInt()}%")
                                }
                                DragAxis.Undecided -> Unit
                            }
                        },
                    )
                },
        )

        // 3) Buffering spinner.
        if (isBuffering && errorMsg == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(56.dp),
                color = Color.White,
            )
        }

        // 4) Error overlay.
        errorMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Text("播放失败", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(6.dp))
                    Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 5) Transient gesture hint.
        if (hint.visible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(hint.text, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        // 6) Controls overlay.
        if (controlsVisible) {
            TopBar(
                title = title,
                onBack = onBack,
                speed = speed,
                onSpeedChange = {
                    player.setPlaybackSpeed(it)
                    showHint("${"%.2f".format(it)}×")
                    bumpControls()
                },
                isFullscreen = isFullscreen,
                onToggleFullscreen = {
                    isFullscreen = !isFullscreen
                    bumpControls()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
            BottomBar(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onToggle = {
                    if (isPlaying) player.pause() else player.play()
                    bumpControls()
                },
                onSeek = { ms ->
                    player.seekTo(ms.coerceIn(0L, player.duration))
                    bumpControls()
                },
                onRew = {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                    bumpControls()
                },
                onFwd = {
                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
                    bumpControls()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoxScope.TopBar(
    title: String,
    onBack: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Text(
            text = title,
            color = Color.White,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        SpeedPicker(speed = speed, onSpeedChange = onSpeedChange)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onToggleFullscreen) {
            Icon(
                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit
                else Icons.Outlined.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun SpeedPicker(speed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("${"%.2f".format(speed)}×", color = Color.White,
                style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SPEED_OPTIONS.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${"%.2f".format(v)}×") },
                    onClick = {
                        expanded = false
                        onSpeedChange(v)
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRew: () -> Unit,
    onFwd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(formatTime(position), color = Color.White,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { frac -> onSeek((frac * duration).toLong()) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(formatTime(duration), color = Color.White,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRew) {
                Icon(Icons.Outlined.FastRewind, contentDescription = "-10s", tint = Color.White)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onFwd) {
                Icon(Icons.Outlined.FastForward, contentDescription = "+10s", tint = Color.White)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val hh = s / 3600
    val mm = (s % 3600) / 60
    val ss = s % 60
    return if (hh > 0) "%02d:%02d:%02d".format(hh, mm, ss)
    else "%02d:%02d".format(mm, ss)
}
