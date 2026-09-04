package com.mero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mero.domain.Song

/** Artwork with the design's rounded-square placeholder when there is no URL. */
@Composable
fun Artwork(
    url: String?,
    size: Int,
    radius: Int = 6,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.MusicNote,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp))
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
 * The design's chip: 32dp, 8dp corners, outlined when unselected and filled with
 * the primary container when selected. Not [androidx.compose.material3.FilterChip],
 * which is 32dp but uses M3's own 8dp-corner-plus-leading-icon layout — the design
 * has no leading icon and a tighter horizontal pad.
 */
@Composable
fun MeroChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else scheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        )
    }
}

/** 64dp browse/search row. */
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
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
