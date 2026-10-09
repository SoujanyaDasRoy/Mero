package com.mero.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.colorResource
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
import com.mero.ui.theme.MeroAccent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Check

/**
 * The home-screen icon. Mero has one logo: the `IconClassic` launcher alias, enabled.
 *
 * Every other alias in the manifest is retired: earlier recolours and the ten-icon set. Whoever
 * picked one of those has it enabled and Classic explicitly disabled, so the aliases stay and
 * [applyPending] moves them to the logo the next time Mero is closed.
 */
object AppIcon {
    private val RETIRED = listOf(
        "IconMidnight", "IconPaper", "IconCoral", "IconOcean", "IconMint",
        "IconHoney", "IconBlush", "IconForest", "IconSage", "IconSky",
        "IconLilac", "IconCocoa", "IconGilded", "IconInk",
    )

    private fun component(context: Context, alias: String) = ComponentName(context.packageName, "com.mero." + alias)

    /**
     * Moves anyone still on a retired icon to the logo. Done once Mero is in the background:
     * disabling the entry the running task came from closes it.
     *
     * The logo is enabled before the retired ones are disabled; in the other order there is a
     * moment with no launcher entry at all, and a crash in that moment leaves no icon to open Mero.
     * DONT_KILL_APP keeps the process, and so playback, alive.
     */
    fun applyPending(context: Context) {
        val pm = context.packageManager
        val stranded = RETIRED.any {
            pm.getComponentEnabledSetting(component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (!stranded) return
        pm.setComponentEnabledSetting(
            component(context, "IconClassic"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        RETIRED.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

/** The Mero logo, and the choice to use your own photo instead. */
@Composable
internal fun AppIconPicker() {
    val context = LocalContext.current
    val photo by CustomIcon.icon(context).collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(
            Modifier.weight(1f),
            "Mero",
            selected = photo == null,
            description = "Mero logo",
            role = null,
            background = Color.Transparent,
            onClick = {},
        ) {
            Image(painterResource(R.drawable.mero_logo), null, Modifier.fillMaxSize())
        }
        CustomIconTile(Modifier.weight(1f))
        Spacer(Modifier.weight(2f))
    }
}

/** One cell of the grid: art, a selected ring with a tick, and a label. */
@Composable
private fun IconTile(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    description: String,
    role: Role?,
    background: Color,
    onClick: () -> Unit,
    art: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .semantics {
                this.selected = selected
                contentDescription = description
            }
            .clickable(role = role, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, scheme.outlineVariant, shape),
                contentAlignment = Alignment.Center,
                content = art,
            )
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(3.dp, scheme.primary, RoundedCornerShape(23.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = scheme.onPrimary)
                }
            }
        }
        Text(
            label,
            Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The last tile: a photo of your own. Empty, it opens the photo picker;
 * set, it opens the editor to re-frame, change or remove it.
 */
@Composable
private fun CustomIconTile(modifier: Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val icon by CustomIcon.icon(context).collectAsState()
    var editing by remember { mutableStateOf<Bitmap?>(null) }
    var framing by remember { mutableStateOf(CustomIcon.Framing()) }
    var message by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf(false) }

    // Did the icon actually reach the home screen? Android does not say. On a
    // Pixel the launcher shows an "Add to home screen" sheet, which pauses
    // Mero; on Xiaomi, Oppo, Vivo and Realme phones that have not been given
    // the "Home screen shortcuts" permission, nothing appears at all and the
    // request vanishes — which is how a photo ended up as Mero's logo and
    // nowhere else. So: if Mero is never paused within a few seconds and the
    // icon is not there, the phone blocked it; if it was paused, look again on
    // return.
    var waitingForPin by remember { mutableStateOf(false) }
    var sheetShown by remember { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (!waitingForPin) return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> sheetShown = true
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (sheetShown) {
                    waitingForPin = false
                    // Declined on the sheet: their choice, nothing to say.
                    if (CustomIcon.isPinned(context)) message = ADDED_MESSAGE
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(waitingForPin) {
        if (!waitingForPin) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000)
        if (waitingForPin && !sheetShown) {
            waitingForPin = false
            if (CustomIcon.isPinned(context)) message = ADDED_MESSAGE else blocked = true
        }
    }

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

    IconTile(
        modifier,
        "Photo",
        icon != null,
        if (icon == null) "Use your own photo as the icon" else "Edit your photo icon",
        null,
        scheme.surfaceContainerHighest,
        onClick = {
            val source = if (icon != null) CustomIcon.source(context) else null
            if (source != null) {
                framing = CustomIcon.framing(context)
                editing = source
            } else {
                pick()
            }
        },
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
                editing = null
                when (CustomIcon.apply(context, source, rendered, framed)) {
                    CustomIcon.PinResult.Updated -> message = "Updated. Your photo is now Mero's icon on the home screen and its logo inside the app."
                    CustomIcon.PinResult.Requested -> {
                        sheetShown = false
                        waitingForPin = true
                    }
                    CustomIcon.PinResult.Unsupported -> blocked = true
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

    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            title = { Text("Your phone didn't add the icon") },
            text = {
                Text(
                    "Your photo is Mero's logo inside the app, but the phone stopped it going on the home screen.\n\n" +
                        "Xiaomi, Redmi, POCO, Oppo, Vivo and Realme phones block this until you allow it: open " +
                        "Mero's settings, then Permissions (or Other permissions), and turn on " +
                        "\u201cHome screen shortcuts\u201d. Then come back, tap your photo and Use as icon.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    blocked = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { blocked = false }) { Text("Not now") } },
        )
    }
}

private const val ADDED_MESSAGE =
    "Done! Your photo is now Mero's icon on your home screen and its logo inside the app. " +
        "Move it wherever you like. The app drawer keeps a built-in icon; Android doesn't let any app change that one to a photo."
