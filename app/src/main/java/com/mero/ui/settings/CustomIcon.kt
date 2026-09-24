package com.mero.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * An app icon made from the user's own photo.
 *
 * Android has no way for an app to swap its launcher icon for an image chosen
 * at run time — the built-in choices in [AppIcon] only work because each is
 * compiled in. What it does allow is a pinned home-screen shortcut with any
 * image, which opens Mero like the icon does, and whose image Mero can update
 * in place afterwards. So that is what "your photo" is: the shortcut on the
 * home screen, and the logo inside the app. The app drawer keeps a built-in one.
 */
object CustomIcon {

    /** Adaptive icons are 108dp with the middle 72dp visible: 432px and 288px at xxxhdpi. */
    private const val OUT_PX = 432
    private const val VISIBLE_PX = 288
    private const val SOURCE_MAX_PX = 1024
    private const val SHORTCUT_ID = "custom-icon"

    private val current = MutableStateFlow<Bitmap?>(null)
    @Volatile private var loaded = false

    /** The finished icon, or null. Loaded from disk on first ask. */
    fun icon(context: Context): StateFlow<Bitmap?> {
        if (!loaded) {
            loaded = true
            current.value = iconFile(context).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.path) }
        }
        return current
    }

    /** The photo it was cut from, kept so it can be re-framed later. */
    fun source(context: Context): Bitmap? =
        sourceFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** How the photo was last framed: zoom, and offset as a fraction of the circle. */
    data class Framing(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

    fun framing(context: Context): Framing = prefs(context).let {
        Framing(it.getFloat("scale", 1f), it.getFloat("x", 0f), it.getFloat("y", 0f))
    }

    private fun prefs(context: Context) = context.getSharedPreferences("custom_icon", Context.MODE_PRIVATE)

    /** Decodes a picked photo, shrunk to a size worth editing. Software, so a Canvas can draw it. */
    fun decode(context: Context, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > SOURCE_MAX_PX) {
                val k = SOURCE_MAX_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
        }

    /**
     * Draws the framed photo as a full adaptive icon. What the editor's circle
     * showed becomes the visible middle; the margin the launcher may reveal at
     * the corners is the photo's average colour rather than transparent.
     */
    fun render(source: Bitmap, userScale: Float, offset: Offset, viewportPx: Float): Bitmap {
        val out = Bitmap.createBitmap(OUT_PX, OUT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Bitmap.createScaledBitmap(source, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt())
        val k = VISIBLE_PX / viewportPx
        val base = max(viewportPx / source.width, viewportPx / source.height)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postScale(base * userScale * k, base * userScale * k)
            postTranslate(OUT_PX / 2f + offset.x * k, OUT_PX / 2f + offset.y * k)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** What happened to the home-screen icon when a photo was saved. */
    enum class PinResult {
        /** It was already there; its picture was changed in place. */
        Updated,
        /** The launcher was asked to add it. Whether it did is known only later. */
        Requested,
        /** This launcher cannot take icons from apps at all. */
        Unsupported,
    }

    /** Whether the photo icon is on the home screen right now. */
    fun isPinned(context: Context): Boolean = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == SHORTCUT_ID && it.isEnabled }
    }.getOrDefault(false)

    /**
     * Saves the icon, makes it Mero's logo, and puts it on the home screen: in
     * place if it is already there, otherwise by asking the launcher to add it.
     */
    fun apply(context: Context, source: Bitmap, icon: Bitmap, framing: Framing): PinResult {
        prefs(context).edit()
            .putFloat("scale", framing.scale).putFloat("x", framing.x).putFloat("y", framing.y)
            .apply()
        sourceFile(context).outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        iconFile(context).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        current.value = icon

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("Mero")
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(context, "com.mero.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            .build()
        // A photo removed earlier left its shortcut disabled, and Android
        // ignores updates to a disabled shortcut — a new photo would not show.
        runCatching {
            context.getSystemService(android.content.pm.ShortcutManager::class.java)
                .enableShortcuts(listOf(SHORTCUT_ID))
        }
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == SHORTCUT_ID }
        if (pinned && ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))) return PinResult.Updated
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return PinResult.Unsupported
        return if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
            PinResult.Requested
        } else {
            PinResult.Unsupported
        }
    }

    /**
     * Forgets the photo. A pinned shortcut cannot be taken off someone's home
     * screen by the app, so it is disabled — greyed out, with a message — and
     * they remove it themselves.
     */
    fun remove(context: Context) {
        sourceFile(context).delete()
        iconFile(context).delete()
        prefs(context).edit().clear().apply()
        current.value = null
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(SHORTCUT_ID),
                "This photo icon was removed in Mero. Long-press to take it off.",
            )
        }
    }

    private fun iconFile(context: Context) = File(context.filesDir, "custom_icon.png")
    private fun sourceFile(context: Context) = File(context.filesDir, "custom_icon_source.png")
}

/** How far the photo can move each way before the circle would show past its edge. */
internal fun maxOffset(imageW: Int, imageH: Int, viewport: Float, userScale: Float): Pair<Float, Float> {
    val base = max(viewport / imageW, viewport / imageH)
    return ((imageW * base * userScale - viewport) / 2f).coerceAtLeast(0f) to
        ((imageH * base * userScale - viewport) / 2f).coerceAtLeast(0f)
}

/**
 * Framing a photo the way a profile picture is framed: pinch to zoom, drag to
 * move, and the circle is what the icon will show.
 */
@Composable
internal fun CustomIconEditor(
    source: Bitmap,
    /** Where to start: the saved framing when re-editing, centred for a new photo. */
    initial: CustomIcon.Framing,
    hasIcon: Boolean,
    onChangePhoto: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Bitmap, CustomIcon.Framing) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val image = remember(source) { source.asImageBitmap() }
    var viewport by remember { mutableFloatStateOf(1f) }
    var scale by remember(source) { mutableFloatStateOf(initial.scale) }
    // Kept as a fraction of the circle, so it survives the circle's pixel size.
    var norm by remember(source) { mutableStateOf(Offset(initial.x, initial.y)) }
    val offset = norm * viewport

    fun clamped(o: Offset, s: Float): Offset {
        val (mx, my) = maxOffset(source.width, source.height, viewport, s)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your icon", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Pinch to zoom, drag to move.",
                Modifier.padding(top = 4.dp, bottom = 16.dp),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .border(2.dp, scheme.primary, CircleShape)
                    .onSizeChanged { viewport = it.width.toFloat() }
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            norm = clamped(norm * viewport + pan, scale) / viewport
                        }
                    },
            ) {
                // Drawn by hand with the same maths as [CustomIcon.render]. An
                // Image with ContentScale.Crop clips to its own box before any
                // graphicsLayer move, so dragging revealed blank space where
                // the photo really continued — the preview lied about the icon.
                Canvas(Modifier.fillMaxSize()) {
                    val base = max(size.width / source.width, size.height / source.height) * scale
                    val w = source.width * base
                    val h = source.height * base
                    drawImage(
                        image,
                        dstOffset = IntOffset(
                            ((size.width - w) / 2f + offset.x).roundToInt(),
                            ((size.height - h) / 2f + offset.y).roundToInt(),
                        ),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
            Text(
                "Mero adds it to your home screen. The app drawer keeps a built-in icon; Android doesn't let apps change that one to a photo.",
                Modifier.padding(top = 16.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onChangePhoto, Modifier.weight(1f)) { Text("Change photo") }
                Button(
                    onClick = {
                        onSave(
                            CustomIcon.render(source, scale, offset, viewport),
                            CustomIcon.Framing(scale, norm.x, norm.y),
                        )
                    },
                    Modifier.weight(1f),
                ) { Text("Use as icon") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (hasIcon) {
                    TextButton(onClick = onRemove) { Text("Remove", color = scheme.error) }
                } else {
                    Box(Modifier)
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
