package com.mero.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESETS = listOf(15, 30, 45, 60, 90)

@Composable
fun SleepTimerSheet(
    remainingSec: Int?,
    stopAfterTrack: Boolean,
    onPick: (Int) -> Unit,
    onStopAfterTrack: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val active = remainingSec != null || stopAfterTrack

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Text(
                "Sleep timer",
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(
                        if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    remainingSec != null -> formatRemaining(remainingSec)
                    stopAfterTrack -> "Stopping at end of track"
                    else -> "Music keeps playing"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (active) {
                Text(
                    "Playback pauses when this reaches zero.",
                    Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            "Set a timer",
            Modifier.padding(start = 20.dp, bottom = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
        )

        // Two rows of presets rather than a horizontal scroll — everything
        // should be reachable without hunting.
        PRESETS.chunked(3).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { minutes ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable { onPick(minutes) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$minutes min", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onStopAfterTrack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Stop at end of this track", fontSize = 15.sp)
        }

        if (active) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Cancel timer", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun formatRemaining(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d left".format(h, m, sec)
    } else {
        "%d:%02d left".format(m, sec)
    }
}
