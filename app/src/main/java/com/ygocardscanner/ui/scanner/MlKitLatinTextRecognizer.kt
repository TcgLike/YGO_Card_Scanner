package com.ygocardscanner.ui.scanner

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ygocardscanner.data.scanner.OcrTextBlock
import java.io.Closeable

/** Bundled, offline Latin OCR adapter. It intentionally exposes only transient text. */
class MlKitLatinTextRecognizer : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(
        image: Image,
        rotationDegrees: Int,
        onText: (String) -> Unit,
        onFailure: () -> Unit,
        onComplete: () -> Unit,
    ) {
        recognizer.process(InputImage.fromMediaImage(image, rotationDegrees))
            .addOnSuccessListener { onText(it.text) }
            .addOnFailureListener { onFailure() }
            .addOnCompleteListener { onComplete() }
    }

    fun recognizeBlocks(
        image: Image,
        rotationDegrees: Int,
        onBlocks: (List<OcrTextBlock>) -> Unit,
        onComplete: () -> Unit,
    ) {
        recognizer.process(InputImage.fromMediaImage(image, rotationDegrees))
            .addOnSuccessListener { result ->
                onBlocks(result.textBlocks.mapNotNull { block ->
                    block.boundingBox?.let { bounds ->
                        OcrTextBlock(block.text, bounds.left, bounds.top, bounds.right, bounds.bottom)
                    }
                })
            }
            .addOnCompleteListener { onComplete() }
    }
    override fun close() = recognizer.close()
}

