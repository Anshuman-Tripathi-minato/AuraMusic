package com.auramusic.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.data.Artwork
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.MusicItem
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple
import com.auramusic.app.ui.theme.NeonGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ surfaces

@Composable
fun AuraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Aura.colors
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(c.bgTop.copy(alpha = if (c.isDark) 0.9f else 0.7f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 1400f
                )
            ),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    corner: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Aura.colors
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .clip(shape)
            .background(c.card.copy(alpha = if (c.isDark) 0.82f else 1f))
            .border(BorderStroke(1.dp, c.stroke), shape)
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier),
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    brush: Brush = Brush.horizontalGradient(listOf(AuraCyan, Color(0xFF7B6CFF), AuraPink))
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Aura.colors
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(c.card2)
            .border(1.dp, c.stroke, RoundedCornerShape(23.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            if (icon != null) {
                Icon(icon, null, tint = c.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun AuraChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Aura.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF3D5BFF), AuraPurple))) else Modifier.background(c.card2))
            .border(1.dp, if (selected) Color.Transparent else c.stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Color.White else c.muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun GradientIconTile(icon: ImageVector, brush: Brush, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 3.4f)).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

// ------------------------------------------------------------------ headers

@Composable
fun AuraTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = Aura.colors
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    val c = Aura.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = AuraCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val c = Aura.colors
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AuraCyan.copy(alpha = 0.18f), AuraPurple.copy(alpha = 0.25f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraCyan, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = c.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            GradientButton(actionLabel, onAction)
        }
    }
}

// ------------------------------------------------------------------ artwork

private val placeholderBrushes = listOf(
    listOf(Color(0xFF2B1B5A), Color(0xFF0E5C7A)),
    listOf(Color(0xFF4A1550), Color(0xFF1D2A6B)),
    listOf(Color(0xFF0F3D56), Color(0xFF3A1C6E)),
    listOf(Color(0xFF51203F), Color(0xFF1A3B5C))
)

@Composable
fun ArtworkImage(song: MusicItem?, modifier: Modifier = Modifier, corner: Dp = 10.dp, large: Boolean = false) {
    val context = LocalContext.current
    val size = if (large) Artwork.LARGE else Artwork.SMALL
    val bitmap by produceState<Bitmap?>(initialValue = song?.let { Artwork.cached(it.id, size) }, song?.id, large) {
        if (song == null || song.id < 0) { value = null; return@produceState }
        if (value == null && !Artwork.isKnownMissing(song.id)) value = withContext(Dispatchers.IO) { Artwork.load(context, song, size) }
    }
    val shape = RoundedCornerShape(corner)
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        val colors = placeholderBrushes[((song?.albumId ?: song?.id ?: 0L) % placeholderBrushes.size).toInt().let { if (it < 0) -it else it }]
        Box(modifier.clip(shape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = AuraCyan.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.42f))
        }
    }
}

// ------------------------------------------------------------------ song rows

data class MenuAction(val label: String, val icon: ImageVector, val action: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: MusicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    extraActions: List<MenuAction> = emptyList(),
    showFavorite: Boolean = false
) {
    val c = Aura.colors
    val controller = LocalAuraController.current
    val favorites by AuraPrefs.favorites.collectAsState()
    val isCurrent = PlayerManager.currentSong?.id == song.id
    val isFav = song.key in favorites
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text("$index", color = if (isCurrent) AuraCyan else c.muted, fontSize = 14.sp, modifier = Modifier.width(28.dp))
        }
        Box {
            ArtworkImage(song, Modifier.size(50.dp))
            if (isCurrent) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    PlayingBars(PlayerManager.isPlaying)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = if (isCurrent) AuraCyan else c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} • ${song.durationLabel}", color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showFavorite) {
            IconButton(onClick = { controller.toggleFavorite(song) }) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFav) "Unlike" else "Like", tint = if (isFav) AuraPink else c.muted)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = c.muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val base = listOf(
                    MenuAction("Play next", Icons.AutoMirrored.Filled.QueueMusic) { controller.playNext(song) },
                    MenuAction("Add to queue", Icons.AutoMirrored.Filled.PlaylistPlay) { controller.addToQueue(song) },
                    MenuAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { controller.addToPlaylist(listOf(song)) },
                    MenuAction(if (isFav) "Remove from Liked" else "Add to Liked", if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { controller.toggleFavorite(song) },
                    MenuAction("Go to album", Icons.Default.Album) { controller.navigate(Route.Album(song.album)) },
                    MenuAction("Go to artist", Icons.Default.Person) { controller.navigate(Route.Artist(song.artist)) },
                    MenuAction("Share", Icons.Default.Share) { controller.share(song) },
                    MenuAction("Song info", Icons.Default.Info) { controller.songInfoFor = song }
                )
                (extraActions + base).forEachIndexed { i, a ->
                    if (i == extraActions.size && extraActions.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(a.label) },
                        leadingIcon = { Icon(a.icon, null) },
                        onClick = { menu = false; a.action() }
                    )
                }
            }
        }
    }
}

/** Animated equalizer bars shown on the currently playing song. */
@Composable
fun PlayingBars(playing: Boolean, color: Color = AuraCyan) {
    val t = rememberInfiniteTransition(label = "bars")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "b")
    val d by t.animateFloat(0.5f, 0.9f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "d")
    Canvas(Modifier.size(18.dp)) {
        val heights = if (playing) listOf(a, b, d) else listOf(0.35f, 0.6f, 0.45f)
        val w = size.width / 5
        heights.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(color, topLeft = Offset(i * w * 2, size.height - bh), size = Size(w, bh), cornerRadius = CornerRadius(w / 2))
        }
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun NeonSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, onValueChangeFinished: (() -> Unit)? = null, enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = AuraCyan,
            inactiveTrackColor = Aura.colors.card2,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

@Composable
fun NeonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AuraCyan,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Aura.colors.muted,
            uncheckedTrackColor = Aura.colors.card2,
            uncheckedBorderColor = Aura.colors.stroke
        )
    )
}

/** Vertical neon fader used by the equalizer. [value] is in 0..1. */
@Composable
fun VerticalFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track = Aura.colors.card2
    Canvas(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos -> onValueChange((1f - pos.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
    ) {
        val cx = size.width / 2
        val trackW = 4.dp.toPx()
        val pad = 10.dp.toPx()
        val usable = size.height - pad * 2
        val y = pad + usable * (1f - value.coerceIn(0f, 1f))
        val mid = pad + usable / 2
        drawRoundRect(track, topLeft = Offset(cx - trackW / 2, pad), size = Size(trackW, usable), cornerRadius = CornerRadius(trackW))
        val top = minOf(y, mid)
        val bottom = maxOf(y, mid)
        drawRoundRect(
            Brush.verticalGradient(listOf(AuraCyan, AuraPurple), startY = pad, endY = size.height - pad),
            topLeft = Offset(cx - trackW / 2, top), size = Size(trackW, (bottom - top).coerceAtLeast(1f)), cornerRadius = CornerRadius(trackW)
        )
        drawCircle(AuraCyan.copy(alpha = 0.25f), radius = 13.dp.toPx(), center = Offset(cx, y))
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, y))
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = Aura.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = c.text, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun GradientText(text: String, modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 28.sp, brush: Brush = NeonGradient) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(brush = brush, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    )
}
