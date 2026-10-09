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
 * The home-screen icons someone can pick from. Each is an `activity-alias` in
 * the manifest carrying the LAUNCHER filter; exactly one is enabled.
 *
 * [background] and [art] are the very resources that alias's adaptive icon is
 * built from, so the picker shows exactly what the launcher will. Classic is
 * the master logo; the other nine are single-colour versions of it.
 */
enum class AppIcon(
    val alias: String,
    val label: String,
    @ColorRes val background: Int,
    @DrawableRes val art: Int,
    /** Picking the icon moves the app's accent here too; the swatches can still override it. */
    val accent: MeroAccent,
) {
    Classic("IconClassic", "Classic", R.color.mero_lavender, R.mipmap.ic_launcher_foreground, MeroAccent.Violet),
    Honey("IconHoney", "Honey", R.color.icon_honey_bg, R.drawable.ic_launcher_fg_honey, MeroAccent.Peach),
    Blush("IconBlush", "Blush", R.color.icon_blush_bg, R.drawable.ic_launcher_fg_blush, MeroAccent.Rose),
    Forest("IconForest", "Forest", R.color.icon_forest_bg, R.drawable.ic_launcher_fg_forest, MeroAccent.Green),
    Sage("IconSage", "Sage", R.color.icon_sage_bg, R.drawable.ic_launcher_fg_sage, MeroAccent.Lime),
    Sky("IconSky", "Sky", R.color.icon_sky_bg, R.drawable.ic_launcher_fg_sky, MeroAccent.Sky),
    Lilac("IconLilac", "Lilac", R.color.icon_lilac_bg, R.drawable.ic_launcher_fg_lilac, MeroAccent.Orchid),
    Cocoa("IconCocoa", "Cocoa", R.color.icon_cocoa_bg, R.drawable.ic_launcher_fg_cocoa, MeroAccent.Sand),
    Gilded("IconGilded", "Gilded", R.color.icon_gilded_bg, R.drawable.ic_launcher_fg_gilded, MeroAccent.Amber),
    Ink("IconInk", "Ink", R.color.icon_ink_bg, R.drawable.ic_launcher_fg_ink, MeroAccent.Blue),
    ;

    fun component(context: Context) = ComponentName(context.packageName, "com.mero." + alias)

    companion object {
        private const val PREFS = "app_icon"
        private const val PENDING = "pending"

        /**
         * Icons that used to be offered and are not any more (an earlier set of
         * recolours). Their manifest aliases stay: whoever picked one has it
         * enabled and Classic disabled, and removing the alias would leave
         * Mero with no launcher entry at all.
         */
        private val RETIRED = listOf("IconMidnight", "IconPaper", "IconCoral", "IconOcean", "IconMint")

        private fun retiredComponent(context: Context, alias: String) =
            ComponentName(context.packageName, "com.mero." + alias)

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
            val icon = pending(context)
            if (icon != null) {
                prefs(context).edit().remove(PENDING).apply()
                if (icon != current(context)) apply(context, icon)
                return
            }
            // Someone whose icon has been retired: move them to Classic. Done
            // here, once Mero is in the background, for the same reason a pick
            // is: disabling the entry the running task came from closes it.
            val pm = context.packageManager
            val stranded = RETIRED.any {
                pm.getComponentEnabledSetting(retiredComponent(context, it)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            if (stranded) apply(context, Classic)
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
            (entries.filter { it != icon }.map { it.component(context) } +
                RETIRED.map { retiredComponent(context, it) }).forEach {
                pm.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/**
 * A preview of what is picked, then a grid of every icon drawn the way the
 * launcher will draw it. Picking one also moves the accent colour and the
 * splash screen to match.
 */
@Composable
internal fun AppIconPicker(onAccentChange: (MeroAccent) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var chosen by remember { mutableStateOf(AppIcon.chosen(context)) }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconArt(chosen, Modifier.size(72.dp), RoundedCornerShape(22.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(chosen.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(chosen.accent.swatch))
                    Text(
                        "Splash and " + chosen.accent.label + " accent follow it",
                        Modifier.padding(start = 8.dp),
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Rows of four, built by hand: FlowRow + weight + aspectRatio measured
        // every tile at full width.
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            (AppIcon.entries + null).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { icon ->
                        if (icon == null) {
                            CustomIconTile(Modifier.weight(1f))
                            return@forEach
                        }
                        val selected = icon == chosen
                        IconTile(
                            Modifier.weight(1f),
                            icon.label,
                            selected,
                            icon.label + " icon",
                            Role.RadioButton,
                            colorResource(icon.background),
                            onClick = {
                                if (!selected) {
                                    AppIcon.choose(context, icon)
                                    chosen = icon
                                    onAccentChange(icon.accent)
                                }
                            },
                        ) {
                            Image(painterResource(icon.art), null, Modifier.fillMaxSize().scale(1.5f))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The icon on its own background; the foreground's safe-zone margin is scaled away to match a launcher. */
@Composable
internal fun IconArt(icon: AppIcon, modifier: Modifier, shape: RoundedCornerShape) {
    Box(
        modifier
            .clip(shape)
            .background(colorResource(icon.background))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon.art), null, Modifier.fillMaxSize().scale(1.5f))
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
