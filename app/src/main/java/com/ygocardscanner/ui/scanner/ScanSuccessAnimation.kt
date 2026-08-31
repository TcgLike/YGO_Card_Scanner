package com.ygocardscanner.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.ui.localization.appText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A local-only, non-blocking scan acknowledgement. It always finishes in under two seconds. */
@Composable
fun ScanSuccessAnimation(success: LiveScanSuccess, onFinished: () -> Unit) {
    var movingToDeck by remember(success.id) { mutableStateOf(false) }
    val horizontalOffset by animateDpAsState(
        targetValue = if (movingToDeck) 112.dp else 0.dp,
        animationSpec = tween(durationMillis = 650),
        label = "scan-card-horizontal",
    )
    val verticalOffset by animateDpAsState(
        targetValue = if (movingToDeck) 235.dp else 0.dp,
        animationSpec = tween(durationMillis = 650),
        label = "scan-card-vertical",
    )

    LaunchedEffect(success.id) {
        delay(400)
        movingToDeck = true
        delay(850)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DeckTarget(
            acceptedCount = success.acceptedCount,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
        SuccessCard(
            success = success,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = horizontalOffset, y = verticalOffset),
        )
    }
}

@Composable
private fun DeckTarget(acceptedCount: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier.width(92.dp).height(122.dp)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
            Text(
                appText("Deck\\n$acceptedCount", "Deck\\n$acceptedCount"),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SuccessCard(success: LiveScanSuccess, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val image = remember(success.localArtworkFileName) {
        fileStore.resolve(success.localArtworkFileName)
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, image) {
        value = withContext(Dispatchers.IO) { image?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }

    Card(modifier = modifier.width(164.dp).height(238.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = appText("Recently scanned ${success.cardName}", "Kürzlich gescannt: ${success.cardName}"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    appText("${success.cardName}\\n${success.setCode}", "${success.cardName}\\n${success.setCode}"),
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

