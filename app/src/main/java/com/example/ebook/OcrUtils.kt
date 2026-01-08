package com.example.ebook

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun extractTextFromImage(
    context: Context,
    imageUri: Uri
): String = suspendCancellableCoroutine { cont ->

    val image = InputImage.fromFilePath(context, imageUri)
    val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            cont.resume(visionText.text)
        }
        .addOnFailureListener { e ->
            cont.resumeWithException(e)
        }
}
