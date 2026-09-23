package com.mero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mero.data.artworkStepFor
import com.mero.data.atArtworkSize
import com.mero.domain.Song
import kotlin.math.roundToInt

/** Artwork with the design's rounded-square placeholder when there is no URL. */
@Composable
fun Artwork(
    url: String?,
    size: Int,
    radius: Int = 10,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.MusicNote,
) {
    val shape = RoundedCornerShape(radius.dp)
    // Ask the CDN for roughly the pixels this will be drawn at, rounded up to
    // one of a few sizes so that screens showing the same cover at similar
    // sizes share a URL and therefore a cache entry.
    val density = LocalDensity.current
    val model = remember(url, size, density.density) {
        url?.atArtworkSize(artworkStepFor((size * density.density).roundToInt()))
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            // Cropped, not fitted: YouTube thumbnails are 16:9 and every one
            // of them was being letterboxed inside a square with grey bars.
            //
            // fillMaxSize, against a box whose own modifier chain always ends
            // in size(size.dp), so the constraints reaching the image are
            // bounded whatever the caller passed in. matchParentSize looks
            // equivalent and is not: it takes the size the box worked out from
            // its *other* children, and this box has none, so on some screens
            // the image was measured at zero and simply never appeared.
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size((size * 0.45f).dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Mero's chip: fully rounded, outlined when unselected and filled with the
 * primary container when selected. Not [androidx.compose.material3.FilterChip],
 * which reserves room for a leading icon this design does not have.
 *
 * The corner radius is half the height, so the ends are semicircles however
 * tall it is — a pill rather than a rounded rectangle.
 */
@Composable
fun MeroChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(pill)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else scheme.outlineVariant,
                shape = pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What a swipe on a song row does, provided once at the top of the app so
 * every list gets the same gestures without each screen wiring them up.
 */
class SongSwipeActions(
    val playNext: (Song) -> Unit,
    val toggleLike: (Song) -> Unit,
    val isLiked: (Song) -> Boolean,
)

val LocalSongSwipeActions = compositionLocalOf<SongSwipeActions?> { null }

/** How far a row is pulled before letting go does something. */
private val SWIPE_ACTION_DISTANCE = 96.dp

/**
 * The gestures every song row shares, around whatever the row draws.
 *
 * Long-press opens the song's menu; swipe right plays it next; swipe left
 * likes or unlikes it. The action shows under the finger as the row moves,
 * a haptic tick marks the point where letting go counts, and the row always
 * springs back — nothing is removed from a list by swiping it. Without a
 * menu ([onMore] null) there are no gestures: that is how an album or artist
 * reusing a song row is told apart from a song.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongGestures(
    song: Song,
    onClick: () -> Unit,
    onMore: (() -> Unit)?,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val swipe = if (onMore != null) LocalSongSwipeActions.current else null
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { SWIPE_ACTION_DISTANCE.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (swipe != null && offset.value != 0f) {
            val right = offset.value > 0
            val armed = abs(offset.value) >= threshold
            val liked = swipe.isLiked(song)
            Row(
                Modifier
                    .align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { abs(offset.value).toDp() })
                    .background(if (armed) scheme.primaryContainer else scheme.surfaceContainerHighest)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when {
                        right -> Icons.AutoMirrored.Rounded.PlaylistPlay
                        liked -> Icons.Rounded.HeartBroken
                        else -> Icons.Rounded.Favorite
                    },
                    contentDescription = null,
                    tint = if (armed) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .then(
                    if (swipe == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(song.id) {
                            var armedTick = false
                            detectHorizontalDragGestures(
                                onDragStart = { armedTick = false },
                                onDragEnd = {
                                    val travelled = offset.value
                                    if (abs(travelled) >= threshold) {
                                        if (travelled > 0) swipe.playNext(song) else swipe.toggleLike(song)
                                    }
                                    scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                            ) { change, dx ->
                                change.consume()
                                val next = (offset.value + dx).coerceIn(-threshold * 1.4f, threshold * 1.4f)
                                // One tick as it arms, so the hand knows without looking.
                                if (!armedTick && abs(next) >= threshold) {
                                    armedTick = true
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (abs(next) < threshold) armedTick = false
                                scope.launch { offset.snapTo(next) }
                            }
                        }
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMore?.let { more ->
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            more()
                        }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 64dp browse/search row, with [SongGestures] when it is a real song. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = song.artist,
    highlighted: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    SongGestures(song, onClick, onMore, height = 64.dp, modifier = modifier) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(song.thumbnailUrl, size = 48)
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (highlighted) scheme.primary else scheme.onSurface,
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (song.downloaded) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Row with a leading filled-container icon — Library shortcuts. */
@Composable
fun ShortcutRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    trailing: ImageVector?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** Settings / equalizer list row. Trailing content is supplied by the caller. */
@Composable
fun PreferenceRow(
    icon: ImageVector?,
    label: String,
    subtitle: String,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun GroupHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}
