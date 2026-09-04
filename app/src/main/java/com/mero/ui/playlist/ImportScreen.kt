package com.mero.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.ui.components.MeroChip

/**
 * Paste a playlist link, get a playlist.
 *
 * The Spotify half is deliberately more work for the user than the YouTube
 * half, and the screen says why rather than hiding it: Spotify's API needs
 * credentials, and Mero ships none of its own.
 */
@Composable
fun ImportScreen(
    source: String,
    onSourceChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    clientId: String,
    onClientIdChange: (String) -> Unit,
    clientSecret: String,
    onClientSecretChange: (String) -> Unit,
    busy: Boolean,
    status: String?,
    onImport: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Import playlist", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("YouTube", "Spotify").forEach {
                MeroChip(label = it, selected = source == it, onClick = { onSourceChange(it) })
            }
        }

        Field(
            value = url,
            onChange = onUrlChange,
            placeholder = if (source == "YouTube") {
                "https://music.youtube.com/playlist?list=..."
            } else {
                "https://open.spotify.com/playlist/..."
            },
        )

        if (source == "Spotify") {
            Text(
                "Spotify never sends audio to Mero — only track names. Each one is " +
                    "looked up on YouTube, so a few may not match, and the import " +
                    "will tell you which.\n\n" +
                    "Spotify's API also needs credentials. Create a free app at " +
                    "developer.spotify.com/dashboard and paste its Client ID and " +
                    "Secret below. They stay on this phone.",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = scheme.onSurfaceVariant,
            )
            Field(clientId, onClientIdChange, "Spotify Client ID")
            Field(clientSecret, onClientSecretChange, "Spotify Client Secret")
        }

        Button(
            onClick = onImport,
            enabled = !busy && url.isNotBlank(),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Import")
            }
        }

        status?.let {
            Text(
                it,
                Modifier.padding(horizontal = 16.dp),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    val scheme = MaterialTheme.colorScheme
    // The placeholder overlays the field rather than sitting beside it. Laid
    // out as siblings in a Row it steals the width, and the tap target that
    // should focus the field lands on a Text instead.
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = scheme.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
