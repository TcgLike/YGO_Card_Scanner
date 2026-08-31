package com.ygocardscanner.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.ui.localization.appText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen, local-only viewer for already cached English card artwork. */
@Composable
fun LocalArtworkViewer(localFileName: String, cardName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val image = remember(localFileName) { fileStore.resolve(localFileName) }
    val bitmap by produceState<Bitmap?>(initialValue = null, image) {
        value = withContext(Dispatchers.IO) { image?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    if (bitmap == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += offsetChange.x
        offsetY += offsetChange.y
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = appText("Full English artwork for $cardName", "Vollständiges englisches Kartenbild für $cardName"),
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text(appText("Close", "Schließen"))
            }
        }
    }
}

