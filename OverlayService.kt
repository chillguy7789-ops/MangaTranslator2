package com.mangatranslator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * Service for capturing screen and managing overlay
 */
class OverlayService : Service() {
    
    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Get screen metrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startMediaProjection(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            setupImageReader()
            createVirtualDisplay()
            
            Log.d(TAG, "Media projection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection", e)
            stopSelf()
        }
    }
    
    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )
    }
    
    private fun createVirtualDisplay() {
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }
    
    /**
     * Capture current screen
     */
    fun captureScreen(callback: (Bitmap?) -> Unit) {
        serviceScope.launch {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()
                    callback(bitmap)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture screen", e)
                callback(null)
            }
        }
    }
    
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
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
        val stopIntent = Intent(this, OverlayService::class.java).apply {
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
        
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
