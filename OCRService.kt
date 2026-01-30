package com.mangatranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * OCR Service using Google ML Kit for text recognition
 * Supports multiple languages including Japanese, Chinese, Korean
 */
class OCRService(private val context: Context) {
    
    companion object {
        private const val TAG = "OCRService"
    }
    
    // Text recognizer
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    data class TextBlock(
        val text: String,
        val boundingBox: Rect,
        val confidence: Float
    )
    
    /**
     * Recognize text from bitmap image
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        useJapanese: Boolean = true
    ): Result<List<TextBlock>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting OCR on image: ${bitmap.width}x${bitmap.height}")
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val visionText = textRecognizer.process(image).await()
            
            val blocks = extractTextBlocks(visionText)
            Log.d(TAG, "Found ${blocks.size} text blocks")
            
            Result.success(blocks)
        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extract text blocks from ML Kit result
     */
    private fun extractTextBlocks(visionText: Text): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        
        for (block in visionText.textBlocks) {
            val rect = block.boundingBox ?: continue
            val text = block.text
            
            // Calculate average confidence
            val confidence = block.lines
                .flatMap { it.elements }
                .mapNotNull { it.confidence }
                .average()
                .toFloat()
            
            blocks.add(TextBlock(text, rect, confidence))
            
            Log.d(TAG, "Block: '$text' at $rect (confidence: $confidence)")
        }
        
        return blocks
    }
    
    /**
     * Recognize text from specific region of bitmap
     */
    suspend fun recognizeTextInRegion(
        bitmap: Bitmap,
        region: Rect,
        useJapanese: Boolean = true
    ): Result<List<TextBlock>> = withContext(Dispatchers.IO) {
        try {
            // Crop bitmap to region
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                region.left.coerceAtLeast(0),
                region.top.coerceAtLeast(0),
                region.width().coerceAtMost(bitmap.width - region.left),
                region.height().coerceAtMost(bitmap.height - region.top)
            )
            
            recognizeText(croppedBitmap, useJapanese)
        } catch (e: Exception) {
            Log.e(TAG, "Region OCR error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all text as a single string
     */
    suspend fun getAllText(bitmap: Bitmap, useJapanese: Boolean = true): Result<String> {
        val result = recognizeText(bitmap, useJapanese)
        return result.map { blocks ->
            blocks.joinToString("\n") { it.text }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        textRecognizer.close()
    }
}
