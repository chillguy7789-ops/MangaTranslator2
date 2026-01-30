package com.mangatranslator

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*

/**
 * Service that displays a draggable floating button for triggering translation
 */
class FloatingButtonService : Service() {
    
    companion object {
        private const val TAG = "FloatingButtonService"
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "floating_button_channel"
        
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        
        var sourceLang = TranslationService.LANG_JA
        var targetLang = TranslationService.LANG_EN
        var isServiceRunning = false
    }
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var floatingButton: FloatingActionButton? = null
    
    private lateinit var ocrService: OCRService
    private val translationService = TranslationService()
    private val bubbleDetection = BubbleDetection()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isProcessing = false
    
    private var overlayService: OverlayService? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ocrService = OCRService(this)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                showFloatingButton()
                isServiceRunning = true
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showFloatingButton() {
        // Remove existing view if any
        removeFloatingButton()
        
        // Inflate the floating button layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        floatingButton = floatingView?.findViewById(R.id.floatingButton)
        
        // Set up window layout parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        
        // Add view to window
        windowManager.addView(floatingView, params)
        
        // Make button draggable and clickable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It's a tap, not a drag
                        onFloatingButtonClicked()
                    }
                    true
                }
                else -> false
            }
        }
        
        Log.d(TAG, "Floating button shown")
    }
    
    private fun removeFloatingButton() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
            floatingButton = null
        }
    }
    
    private fun onFloatingButtonClicked() {
        if (isProcessing) {
            Toast.makeText(this, R.string.processing, Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Floating button clicked")
        performTranslation()
    }
    
    private fun performTranslation() {
        isProcessing = true
        
        serviceScope.launch {
            try {
                // For demonstration, we'll capture a simulated screenshot
                // In a real implementation, you would need MediaProjection API
                val bitmap = createDemoBitmap()
                
                showToast("Processing image...")
                
                // Perform OCR
                val ocrResult = ocrService.recognizeText(bitmap, sourceLang == TranslationService.LANG_JA)
                
                if (ocrResult.isFailure) {
                    showToast(getString(R.string.error_ocr))
                    isProcessing = false
                    return@launch
                }
                
                val textBlocks = ocrResult.getOrNull() ?: emptyList()
                
                if (textBlocks.isEmpty()) {
                    showToast(getString(R.string.error_no_text))
                    isProcessing = false
                    return@launch
                }
                
                // Translate each text block
                val translations = mutableListOf<String>()
                for (block in textBlocks) {
                    val result = translationService.translate(block.text, sourceLang, targetLang)
                    if (result.isSuccess) {
                        translations.add(result.getOrNull() ?: "")
                    }
                }
                
                // Show results
                val resultText = translations.joinToString("\n\n")
                showTranslationResult(resultText)
                
            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                showToast(getString(R.string.error_translation))
            } finally {
                isProcessing = false
            }
        }
    }
    
    private fun createDemoBitmap(): Bitmap {
        // Create a simple demo bitmap with text
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // White background
        canvas.drawColor(Color.WHITE)
        
        // Draw some sample text regions
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 40f
            isAntiAlias = true
        }
        
        canvas.drawText("こんにちは", 50f, 100f, paint)
        canvas.drawText("世界", 50f, 200f, paint)
        
        return bitmap
    }
    
    private fun showTranslationResult(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            // Show as a dialog or overlay
            Toast.makeText(this@FloatingButtonService, "Translation:\n$text", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Translation result: $text")
        }
    }
    
    private fun showToast(message: String) {
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(this@FloatingButtonService, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingButtonService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_translate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_translate,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        removeFloatingButton()
        ocrService.cleanup()
        serviceScope.cancel()
        isServiceRunning = false
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
