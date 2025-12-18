package com.hayaguard.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles file uploads for WebView, supporting both camera capture and gallery selection.
 * Supports Android 10 through Android 14+ with proper permission handling.
 */
class FileUploadHandler(private val activity: Activity) {

    // CRITICAL: Store the callback to return the result later
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    // Stores the URI for camera captures
    private var cameraPhotoUri: Uri? = null
    private var cameraVideoUri: Uri? = null
    
    // Track if we're waiting for permission result
    private var pendingFileChooserParams: FileChooserParams? = null

    companion object {
        private const val TAG = "FileUploadHandler"
        
        // MIME type categories
        private const val MIME_IMAGE = "image/*"
        private const val MIME_VIDEO = "video/*"
        private const val MIME_ALL = "*/*"
    }

    /**
     * Called from WebChromeClient.onShowFileChooser
     * Returns true if we're handling the file chooser, false otherwise
     */
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
        fileChooserLauncher: ActivityResultLauncher<Intent>,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        // Cancel any existing callback to prevent WebView from freezing
        cancelPendingCallback()
        
        // Store the new callback
        this.filePathCallback = filePathCallback
        
        // Check permissions before proceeding
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            // Store params and request permissions
            pendingFileChooserParams = fileChooserParams
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return true
        }
        
        // All permissions granted, launch the chooser
        launchFileChooser(fileChooserParams, fileChooserLauncher)
        return true
    }

    /**
     * Called after permissions are granted
     */
    fun onPermissionsResult(
        granted: Boolean,
        fileChooserLauncher: ActivityResultLauncher<Intent>
    ) {
        val params = pendingFileChooserParams
        pendingFileChooserParams = null
        
        if (granted && params != null) {
            launchFileChooser(params, fileChooserLauncher)
        } else {
            // Permission denied - cancel the callback
            cancelPendingCallback()
        }
    }

    /**
     * Launches the file chooser intent with camera and gallery options
     */
    private fun launchFileChooser(
        fileChooserParams: FileChooserParams,
        fileChooserLauncher: ActivityResultLauncher<Intent>
    ) {
        val acceptTypes = fileChooserParams.acceptTypes
        val mimeType = determineMimeType(acceptTypes)
        val allowCamera = shouldAllowCamera(acceptTypes)
        
        val chooserIntents = mutableListOf<Intent>()
        
        // Add camera intents if allowed
        if (allowCamera) {
            // Photo capture
            if (mimeType == MIME_IMAGE || mimeType == MIME_ALL) {
                createCameraPhotoIntent()?.let { chooserIntents.add(it) }
            }
            // Video capture
            if (mimeType == MIME_VIDEO || mimeType == MIME_ALL) {
                createCameraVideoIntent()?.let { chooserIntents.add(it) }
            }
        }
        
        // Create gallery/document picker intent
        val galleryIntent = createGalleryIntent(mimeType, fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
        
        // Create chooser
        val chooserIntent = Intent.createChooser(galleryIntent, "Select source")
        
        if (chooserIntents.isNotEmpty()) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, chooserIntents.toTypedArray())
        }
        
        try {
            fileChooserLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to launch file chooser: ${e.message}", e)
            cancelPendingCallback()
        }
    }

    /**
     * Handles the result from the file chooser
     */
    fun onActivityResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback
        filePathCallback = null
        
        if (callback == null) {
            android.util.Log.w(TAG, "No callback to handle result")
            return
        }
        
        if (resultCode != Activity.RESULT_OK) {
            // CRITICAL: User cancelled - must return null to unfreeze WebView
            callback.onReceiveValue(null)
            clearCameraUris()
            return
        }
        
        val results = mutableListOf<Uri>()
        
        // Check if result is from gallery/document picker
        data?.let { intent ->
            // Check for multiple selection
            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { results.add(it) }
                }
            }
            
            // Check for single selection
            if (results.isEmpty()) {
                intent.data?.let { results.add(it) }
            }
        }
        
        // If no gallery result, check camera captures
        if (results.isEmpty()) {
            // Check photo capture
            cameraPhotoUri?.let { uri ->
                if (isFileValid(uri)) {
                    results.add(uri)
                }
            }
            
            // Check video capture
            if (results.isEmpty()) {
                cameraVideoUri?.let { uri ->
                    if (isFileValid(uri)) {
                        results.add(uri)
                    }
                }
            }
        }
        
        // Return results or null if empty
        if (results.isNotEmpty()) {
            callback.onReceiveValue(results.toTypedArray())
        } else {
            callback.onReceiveValue(null)
        }
        
        clearCameraUris()
    }

    /**
     * CRITICAL: Cancel any pending callback to prevent WebView from freezing
     */
    fun cancelPendingCallback() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        clearCameraUris()
    }

    /**
     * Get required permissions based on Android version
     */
    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        // Camera permission (always needed for camera capture)
        permissions.add(Manifest.permission.CAMERA)
        
        // Storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 (API 29-32) - scoped storage, only need read
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 9 and below
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        return permissions
    }

    /**
     * Determine the MIME type from accept types
     */
    private fun determineMimeType(acceptTypes: Array<String>?): String {
        if (acceptTypes.isNullOrEmpty() || acceptTypes.all { it.isBlank() }) {
            return MIME_ALL
        }
        
        val hasImage = acceptTypes.any { it.startsWith("image") || it == ".jpg" || it == ".jpeg" || it == ".png" || it == ".gif" || it == ".webp" }
        val hasVideo = acceptTypes.any { it.startsWith("video") || it == ".mp4" || it == ".mov" || it == ".avi" || it == ".mkv" }
        
        return when {
            hasImage && hasVideo -> MIME_ALL
            hasImage -> MIME_IMAGE
            hasVideo -> MIME_VIDEO
            else -> MIME_ALL
        }
    }

    /**
     * Check if camera should be allowed based on accept types
     */
    private fun shouldAllowCamera(acceptTypes: Array<String>?): Boolean {
        if (acceptTypes.isNullOrEmpty() || acceptTypes.all { it.isBlank() }) {
            return true
        }
        
        // Allow camera if accepting images or videos
        return acceptTypes.any { 
            it.startsWith("image") || it.startsWith("video") || 
            it == "*/*" || it.isBlank() ||
            it == ".jpg" || it == ".jpeg" || it == ".png" || 
            it == ".mp4" || it == ".mov"
        }
    }

    /**
     * Create camera photo capture intent with FileProvider
     */
    private fun createCameraPhotoIntent(): Intent? {
        val photoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        
        // Check if there's an app to handle the intent
        if (photoIntent.resolveActivity(activity.packageManager) == null) {
            return null
        }
        
        // Create temp file for photo
        val photoFile = createTempFile("IMG_", ".jpg")
        if (photoFile == null) {
            android.util.Log.e(TAG, "Failed to create temp file for photo")
            return null
        }
        
        try {
            cameraPhotoUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile
            )
            photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
            photoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            photoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return photoIntent
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create FileProvider URI: ${e.message}", e)
            return null
        }
    }

    /**
     * Create camera video capture intent with FileProvider
     */
    private fun createCameraVideoIntent(): Intent? {
        val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        
        // Check if there's an app to handle the intent
        if (videoIntent.resolveActivity(activity.packageManager) == null) {
            return null
        }
        
        // Create temp file for video
        val videoFile = createTempFile("VID_", ".mp4")
        if (videoFile == null) {
            android.util.Log.e(TAG, "Failed to create temp file for video")
            return null
        }
        
        try {
            cameraVideoUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                videoFile
            )
            videoIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraVideoUri)
            videoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            videoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Limit video quality for faster uploads
            videoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            return videoIntent
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create FileProvider URI: ${e.message}", e)
            return null
        }
    }

    /**
     * Create gallery/document picker intent
     */
    private fun createGalleryIntent(mimeType: String, allowMultiple: Boolean): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            
            // For mixed content, set MIME types
            if (mimeType == MIME_ALL) {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_IMAGE, MIME_VIDEO))
            }
        }
        return intent
    }

    /**
     * Create a temporary file for camera captures
     */
    private fun createTempFile(prefix: String, suffix: String): File? {
        return try {
            // Use cache directory for better cleanup
            val cameraDir = File(activity.cacheDir, "camera")
            if (!cameraDir.exists()) {
                cameraDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${prefix}${timestamp}${suffix}"
            
            File(cameraDir, fileName).also {
                // Clean up old temp files (older than 1 hour)
                cleanupOldTempFiles(cameraDir)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create temp file: ${e.message}", e)
            null
        }
    }

    /**
     * Clean up old temporary files
     */
    private fun cleanupOldTempFiles(directory: File) {
        try {
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            directory.listFiles()?.forEach { file ->
                if (file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cleanup temp files: ${e.message}", e)
        }
    }

    /**
     * Check if a file URI is valid (file exists and has content)
     */
    private fun isFileValid(uri: Uri): Boolean {
        return try {
            activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available() > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear camera URIs after use
     */
    private fun clearCameraUris() {
        cameraPhotoUri = null
        cameraVideoUri = null
    }

    /**
     * Check if there's a pending upload
     */
    fun hasPendingUpload(): Boolean = filePathCallback != null
}
