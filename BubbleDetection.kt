package com.mangatranslator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple speech bubble detection using basic image processing
 * This is a simplified version - production would use more sophisticated CV algorithms
 */
class BubbleDetection {
    
    companion object {
        private const val TAG = "BubbleDetection"
        private const val MIN_BUBBLE_SIZE = 50
        private const val MAX_BUBBLE_SIZE = 1000
    }
    
    data class Bubble(
        val boundingBox: Rect,
        val confidence: Float
    )
    
    /**
     * Detect potential speech bubble regions in the image
     * This uses a simple approach based on finding rectangular text regions
     */
    suspend fun detectBubbles(bitmap: Bitmap): Result<List<Bubble>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Detecting bubbles in image: ${bitmap.width}x${bitmap.height}")
            
            // For simplicity, we'll divide the image into a grid and look for text-like regions
            val bubbles = detectGridBasedBubbles(bitmap)
            
            Log.d(TAG, "Found ${bubbles.size} potential bubbles")
            Result.success(bubbles)
        } catch (e: Exception) {
            Log.e(TAG, "Bubble detection error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Grid-based bubble detection
     * Divides image into regions and identifies likely text areas
     */
    private fun detectGridBasedBubbles(bitmap: Bitmap): List<Bubble> {
        val bubbles = mutableListOf<Bubble>()
        val gridSize = 100 // Size of each grid cell
        
        for (y in 0 until bitmap.height step gridSize) {
            for (x in 0 until bitmap.width step gridSize) {
                val width = minOf(gridSize, bitmap.width - x)
                val height = minOf(gridSize, bitmap.height - y)
                
                if (width < MIN_BUBBLE_SIZE || height < MIN_BUBBLE_SIZE) continue
                
                // Analyze this region
                val region = Rect(x, y, x + width, y + height)
                val hasText = analyzeRegion(bitmap, region)
                
                if (hasText) {
                    bubbles.add(Bubble(region, 0.7f))
                }
            }
        }
        
        return mergeBubbles(bubbles)
    }
    
    /**
     * Analyze a region to determine if it likely contains text
     */
    private fun analyzeRegion(bitmap: Bitmap, region: Rect): Boolean {
        var whitePixels = 0
        var totalPixels = 0
        val sampleRate = 5 // Sample every 5th pixel for performance
        
        for (y in region.top until region.bottom step sampleRate) {
            for (x in region.left until region.right step sampleRate) {
                if (x >= bitmap.width || y >= bitmap.height) continue
                
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                if (brightness > 200) { // Likely white/light background
                    whitePixels++
                }
                totalPixels++
            }
        }
        
        // If region has 30-70% white, it might be a speech bubble with text
        val whiteRatio = whitePixels.toFloat() / totalPixels
        return whiteRatio in 0.3f..0.7f
    }
    
    /**
     * Merge overlapping bubbles
     */
    private fun mergeBubbles(bubbles: List<Bubble>): List<Bubble> {
        if (bubbles.isEmpty()) return emptyList()
        
        val merged = mutableListOf<Bubble>()
        val used = BooleanArray(bubbles.size)
        
        for (i in bubbles.indices) {
            if (used[i]) continue
            
            var currentRect = bubbles[i].boundingBox
            var count = 1
            
            for (j in i + 1 until bubbles.size) {
                if (used[j]) continue
                
                if (Rect.intersects(currentRect, bubbles[j].boundingBox)) {
                    currentRect = mergeRects(currentRect, bubbles[j].boundingBox)
                    used[j] = true
                    count++
                }
            }
            
            merged.add(Bubble(currentRect, 0.7f))
        }
        
        return merged.filter { 
            it.boundingBox.width() >= MIN_BUBBLE_SIZE && 
            it.boundingBox.height() >= MIN_BUBBLE_SIZE &&
            it.boundingBox.width() <= MAX_BUBBLE_SIZE &&
            it.boundingBox.height() <= MAX_BUBBLE_SIZE
        }
    }
    
    /**
     * Merge two rectangles
     */
    private fun mergeRects(r1: Rect, r2: Rect): Rect {
        return Rect(
            minOf(r1.left, r2.left),
            minOf(r1.top, r2.top),
            maxOf(r1.right, r2.right),
            maxOf(r1.bottom, r2.bottom)
        )
    }
    
    /**
     * Get the largest bubble (likely the main text area)
     */
    fun getLargestBubble(bubbles: List<Bubble>): Bubble? {
        return bubbles.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    }
}
