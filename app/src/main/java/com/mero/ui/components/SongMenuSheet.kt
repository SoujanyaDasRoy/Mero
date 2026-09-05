package com.mero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/**
 * What the ⋮ next to a track opens.
 *
 * Until now that button opened the add-to-playlist screen directly, which meant
 * every other per-track action had nowhere to live — queueing, liking, starting
 * a radio from a song. This is that missing layer.
 */
@Composable
fun SongMenuSheet(
    song: Song,
    liked: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    downloaded: Boolean,
    onStartRadio: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // Scrim plus bottom alignment: a sheet that opens from the top of the
    // screen reads as a screen, not a menu, and leaves the list it belongs to
    // invisible behind it.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(scheme.surfaceContainer)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            )
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHighest),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        MenuRow(Icons.Rounded.PlaylistPlay, "Play next") { onClose(); onPlayNext() }
        MenuRow(Icons.Rounded.LibraryAdd, "Add to queue") { onClose(); onAddToQueue() }
        MenuRow(Icons.Rounded.PlaylistAdd, "Add to playlist") { onAddToPlaylist() }
        MenuRow(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Remove from liked" else "Add to liked",
        ) { onClose(); onToggleLike() }
        MenuRow(
            if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            if (downloaded) "Remove from device" else "Download to device",
        ) { onClose(); onDownload() }
        MenuRow(Icons.Rounded.Radio, "Start radio") { onClose(); onStartRadio() }
        MenuRow(Icons.Rounded.Person, "Go to artist") { onClose(); onGoToArtist() }
        MenuRow(Icons.Rounded.Share, "Share") { onClose(); onShare() }
    }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 15.sp)
    }
}
