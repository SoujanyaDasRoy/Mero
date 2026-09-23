package com.mero.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.R

/**
 * The home-screen icons someone can pick from. Each is an `activity-alias` in
 * the manifest carrying the LAUNCHER filter; exactly one is enabled. Colours
 * here only draw the preview — the real icon is the alias's adaptive icon, and
 * the two are kept in step by `values/colors.xml`.
 */
enum class AppIcon(val alias: String, val label: String, val background: Color, val mark: Color) {
    Classic("IconClassic", "Classic", Color(0xFFFED703), Color(0xFF151515)),
    Midnight("IconMidnight", "Midnight", Color(0xFF151515), Color(0xFFFED703)),
    Paper("IconPaper", "Paper", Color(0xFFF4EFE6), Color(0xFF151515)),
    Coral("IconCoral", "Coral", Color(0xFFFF6B57), Color.White),
    Ocean("IconOcean", "Ocean", Color(0xFF1F4FD6), Color.White),
    Mint("IconMint", "Mint", Color(0xFF8FE3BD), Color(0xFF151515)),
    ;

    fun component(context: Context) = ComponentName(context.packageName, "com.mero." + alias)

    companion object {
        private const val PREFS = "app_icon"
        private const val PENDING = "pending"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun pending(context: Context): AppIcon? =
            prefs(context).getString(PENDING, null)?.let { name -> entries.firstOrNull { it.name == name } }

        /** What the picker shows as selected: the pick waiting to apply, else the live icon. */
        fun chosen(context: Context): AppIcon = pending(context) ?: current(context)

        /** Remembers the pick; [applyPending] switches it once Mero is in the background. */
        fun choose(context: Context, icon: AppIcon) {
            prefs(context).edit().putString(PENDING, icon.name).apply()
        }

        fun applyPending(context: Context) {
            val icon = pending(context) ?: return
            prefs(context).edit().remove(PENDING).apply()
            if (icon != current(context)) apply(context, icon)
        }

        /** Whichever alias is enabled now, falling back to the manifest default. */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                when (pm.getComponentEnabledSetting(it.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it == Classic
                    else -> false
                }
            } ?: Classic
        }

        /**
         * Enable the new alias before disabling the rest: in the other order
         * there is a moment with no launcher entry at all, and a crash in that
         * moment leaves Mero with no icon to open it from.
         *
         * DONT_KILL_APP keeps the process, and so playback, alive; the launcher
         * picks the change up on its own a few seconds later. Pinned shortcuts
         * to the old icon are removed by the launcher — unavoidable, and why
         * the picker says so.
         */
        private fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/** A tile per icon, drawn the way the launcher will draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconPicker() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var chosen by remember { mutableStateOf(AppIcon.chosen(context)) }

    FlowRow(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            val selected = icon == chosen
            Column(
                Modifier
                    .width(60.dp)
                    .semantics {
                        this.selected = selected
                        contentDescription = icon.label + " icon"
                    }
                    .clickable(role = Role.RadioButton) {
                        if (!selected) {
                            AppIcon.choose(context, icon)
                            chosen = icon
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                        .padding(4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(icon.background)
                        // Paper is nearly the colour of the card behind it.
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The foreground carries the adaptive-icon safe zone as
                    // padding; scaled up it matches what launchers show.
                    Image(
                        painterResource(R.mipmap.ic_launcher_foreground),
                        null,
                        Modifier.fillMaxSize().scale(1.5f),
                        colorFilter = ColorFilter.tint(icon.mark),
                    )
                }
                Text(
                    icon.label,
                    Modifier.padding(top = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        CustomIconTile()
    }
}

/**
 * The seventh tile: a photo of your own. Empty, it opens the photo picker;
 * set, it opens the editor to re-frame, change or remove it.
 */
@Composable
private fun CustomIconTile() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val icon by CustomIcon.icon(context).collectAsState()
    var editing by remember { mutableStateOf<Bitmap?>(null) }
    var framing by remember { mutableStateOf(CustomIcon.Framing()) }
    var message by remember { mutableStateOf<String?>(null) }

    // The system photo picker: no storage permission, and only the one photo
    // chosen is ever visible to Mero.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { CustomIcon.decode(context, uri) }
                .onSuccess {
                    framing = CustomIcon.Framing()
                    editing = it
                }
                .onFailure { message = "That photo couldn't be opened." }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(
        Modifier
            .width(60.dp)
            .semantics { contentDescription = if (icon == null) "Use your own photo as the icon" else "Edit your photo icon" }
            .clickable {
                val source = if (icon != null) CustomIcon.source(context) else null
                if (source != null) {
                    framing = CustomIcon.framing(context)
                    editing = source
                } else {
                    pick()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .border(2.dp, if (icon != null) scheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainerHighest)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val current = icon
            if (current != null) {
                // The saved icon has the adaptive margin around it; scaled up,
                // the tile shows what the launcher shows.
                Image(current.asImageBitmap(), null, Modifier.fillMaxSize().scale(1.5f))
            } else {
                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = scheme.onSurfaceVariant)
            }
        }
        Text(
            "Photo",
            Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (icon != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (icon != null) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }

    editing?.let { source ->
        CustomIconEditor(
            source = source,
            initial = framing,
            hasIcon = icon != null,
            onChangePhoto = { pick() },
            onRemove = {
                CustomIcon.remove(context)
                editing = null
                message = "Removed. If its icon is still on your home screen, long-press it to take it off."
            },
            onCancel = { editing = null },
            onSave = { rendered, framed ->
                val shown = CustomIcon.apply(context, source, rendered, framed)
                editing = null
                message = if (shown) {
                    null
                } else {
                    "Saved as Mero's logo, but this home screen doesn't let apps add icons to it."
                }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
