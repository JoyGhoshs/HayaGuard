package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Process
import android.util.Log
import android.util.LruCache
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NSFWWebViewClient(
    private val context: Context,
    private val contentFilterPipeline: ContentFilterPipeline,
    private val scope: CoroutineScope
) : WebViewClient() {

    interface NavigationListener {
        fun onPageChanged(url: String?)
    }

    var navigationListener: NavigationListener? = null

    private val placeholderCache = LruCache<String, ByteArray>(50)
    private val pendingOriginals = java.util.concurrent.ConcurrentHashMap<String, ImageData>()
    private val processingStartTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lowConfidenceThreshold = 0.75f
    private val processingTimeout = 15000L
    private val aiRaceTimeout = 300L
    private val refreshRetryDelay = 100L
    @Volatile private var currentWebView: WebView? = null
    private val hayaModeProcessor = HayaModeProcessor(context)

    companion object {
        private const val TAG = "NSFWWebViewClient"
        private const val MIN_IMAGE_SIZE = 5000
        private const val MIN_IMAGE_DIMENSION = 100
        private const val FACEBOOK_BLUE = 0xFF1877F2.toInt()
        
        private fun escapeJsString(input: String): String {
            val sb = StringBuilder()
            for (c in input) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '\'' -> sb.append("\\'")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '<' -> sb.append("\\u003c")
                    '>' -> sb.append("\\u003e")
                    '&' -> sb.append("\\u0026")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }
    }

    fun unblurImage(url: String, webView: WebView) {
        val normalizedUrl = ImageStateManager.normalizeUrl(url)
        ImageStateManager.markUnblurred(normalizedUrl)
        val escapedUrl = escapeJsString(normalizedUrl)
        webView.evaluateJavascript("""
            (function() {
                var imgs = document.querySelectorAll('img');
                imgs.forEach(function(img) {
                    if (img.src && img.src.indexOf('$escapedUrl') !== -1) {
                        img.src = img.src + '&_unblur=' + Date.now();
                    }
                });
            })();
        """.trimIndent(), null)
    }

    fun unblurAll(webView: WebView) {
        ImageStateManager.getLowConfidenceUrls().forEach { url ->
            ImageStateManager.markUnblurred(url)
        }
        webView.evaluateJavascript("""
            (function() {
                var imgs = document.querySelectorAll('img');
                imgs.forEach(function(img) {
                    if (img.src) {
                        img.src = img.src + (img.src.indexOf('?') > -1 ? '&' : '?') + '_unblur=' + Date.now();
                    }
                });
            })();
        """.trimIndent(), null)
    }

    fun isLowConfidence(url: String): Boolean {
        return ImageStateManager.isLowConfidence(ImageStateManager.normalizeUrl(url))
    }

    fun getLowConfidenceUrls(): Set<String> {
        return ImageStateManager.getLowConfidenceUrls()
    }

    private fun shouldPassthrough(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        if (lowercaseUrl.contains(".html") ||
            lowercaseUrl.contains(".php") ||
            lowercaseUrl.contains(".js") ||
            lowercaseUrl.contains(".css") ||
            lowercaseUrl.contains(".woff") ||
            lowercaseUrl.contains(".ttf") ||
            lowercaseUrl.contains(".eot") ||
            lowercaseUrl.contains(".svg") ||
            lowercaseUrl.contains(".mp4") ||
            lowercaseUrl.contains(".m3u8") ||
            lowercaseUrl.contains(".mpd") ||
            lowercaseUrl.contains("/api/") ||
            lowercaseUrl.contains("/ajax/") ||
            lowercaseUrl.contains("/graphql") ||
            lowercaseUrl.contains("rsrc.php") ||
            lowercaseUrl.contains("static") ||
            lowercaseUrl.contains("emoji") ||
            lowercaseUrl.contains("sprite") ||
            lowercaseUrl.contains("/icon") ||
            lowercaseUrl.contains("_icon")) {
            return true
        }
        if (!lowercaseUrl.contains("scontent") && !lowercaseUrl.contains("video")) {
            if (!lowercaseUrl.contains("fbcdn") || !isUserContentImage(lowercaseUrl)) {
                return true
            }
        }
        return false
    }

    private fun isUserContentImage(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        return (lowercaseUrl.endsWith(".jpg") ||
                lowercaseUrl.endsWith(".jpeg") ||
                lowercaseUrl.endsWith(".png") ||
                lowercaseUrl.endsWith(".webp") ||
                lowercaseUrl.contains(".jpg?") ||
                lowercaseUrl.contains(".jpeg?") ||
                lowercaseUrl.contains(".png?") ||
                lowercaseUrl.contains(".webp?") ||
                lowercaseUrl.contains("photo") ||
                lowercaseUrl.contains("thumbnail") ||
                lowercaseUrl.contains("poster"))
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val rawUrl = request?.url?.toString() ?: return null
        currentWebView = view

        if (TrackerBlocker.shouldBlock(rawUrl)) {
            return TrackerBlocker.createEmptyResponse()
        }

        if (shouldPassthrough(rawUrl)) {
            return null
        }
        
        val isMetered = NetworkOptimizer.isMeteredConnection(context)
        val optimizedRawUrl = if (isMetered) NetworkOptimizer.optimizeImageUrl(rawUrl, true) else rawUrl
        val url = ImageStateManager.normalizeUrl(optimizedRawUrl)
        
        if (!isImageUrl(url)) {
            return null
        }

        if (ImageStateManager.isUnblurred(url)) {
            val original = ImageStateManager.getOriginal(url)
            if (original != null) {
                return createImageResponse(original.data, original.mimeType)
            }
        }

        val state = ImageStateManager.getState(url)
        
        when (state) {
            ImageState.DONE -> {
                placeholderCache.remove(url)
                processingStartTime.remove(url)
                val cachedResult = ImageStateManager.getResultOrOriginal(url)
                if (cachedResult != null && cachedResult.data.isNotEmpty()) {
                    ImageStateManager.resetRetry(url)
                    pendingOriginals.remove(url)
                    return createImageResponse(cachedResult.data, cachedResult.mimeType)
                }
                val pendingOriginal = pendingOriginals.remove(url)
                if (pendingOriginal != null) {
                    Log.d(TAG, "Returning pending original for DONE state: $url")
                    return createImageResponse(pendingOriginal.data, pendingOriginal.mimeType)
                }
                if (ImageStateManager.canRetry(url)) {
                    Log.d(TAG, "Retrying DONE with empty result: $url")
                    ImageStateManager.forceComplete(url)
                    ImageStateManager.incrementRetry(url)
                }
            }
            ImageState.FAILED -> {
                placeholderCache.remove(url)
                processingStartTime.remove(url)
                val pendingOriginal = pendingOriginals.remove(url)
                if (pendingOriginal != null) {
                    Log.d(TAG, "Returning pending original for FAILED state: $url")
                    return createImageResponse(pendingOriginal.data, pendingOriginal.mimeType)
                }
                val cachedOriginal = ImageStateManager.getOriginal(url)
                if (cachedOriginal != null) {
                    Log.d(TAG, "Returning cached original for FAILED state: $url")
                    return createImageResponse(cachedOriginal.data, cachedOriginal.mimeType)
                }
                if (ImageStateManager.canRetry(url)) {
                    ImageStateManager.forceComplete(url)
                    ImageStateManager.incrementRetry(url)
                } else {
                    return null
                }
            }
            ImageState.PROCESSING -> {
                val startTime = processingStartTime[url]
                if (startTime != null && System.currentTimeMillis() - startTime > processingTimeout) {
                    Log.d(TAG, "Processing timeout for: $url")
                    placeholderCache.remove(url)
                    processingStartTime.remove(url)
                    val original = pendingOriginals.remove(url)
                    if (original != null) {
                        ImageStateManager.markDone(url, CachedResult(original.data, original.mimeType))
                        return createImageResponse(original.data, original.mimeType)
                    }
                    ImageStateManager.forceComplete(url)
                    return null
                }
                val placeholder = placeholderCache.get(url)
                if (placeholder != null) {
                    return createNoCachePlaceholderResponse(placeholder)
                }
                return createNoCachePlaceholderResponse(createLogoPlaceholder(100, 100))
            }
            null -> {}
        }

        if (!ImageStateManager.startProcessing(url)) {
            val startTime = processingStartTime[url]
            if (startTime != null && System.currentTimeMillis() - startTime > processingTimeout) {
                placeholderCache.remove(url)
                processingStartTime.remove(url)
                val original = pendingOriginals.remove(url)
                if (original != null) {
                    ImageStateManager.markDone(url, CachedResult(original.data, original.mimeType))
                    return createImageResponse(original.data, original.mimeType)
                }
                ImageStateManager.forceComplete(url)
                return null
            }
            val placeholder = placeholderCache.get(url)
            if (placeholder != null) {
                return createNoCachePlaceholderResponse(placeholder)
            }
            return createNoCachePlaceholderResponse(createLogoPlaceholder(100, 100))
        }

        processingStartTime[url] = System.currentTimeMillis()

        try {
            Log.d(TAG, "Intercepting: $url")
            val imageData = downloadImage(url, request)
            
            if (imageData == null) {
                ImageStateManager.markDone(url, CachedResult(ByteArray(0), "image/png"))
                return null
            }
            
            if (imageData.data.size < MIN_IMAGE_SIZE) {
                ImageStateManager.markDone(url, CachedResult(imageData.data, imageData.mimeType))
                return createImageResponse(imageData.data, imageData.mimeType)
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(imageData.data, 0, imageData.data.size, options)
            
            if (bitmap == null) {
                ImageStateManager.markDone(url, CachedResult(imageData.data, imageData.mimeType))
                return createImageResponse(imageData.data, imageData.mimeType)
            }

            if (bitmap.width < MIN_IMAGE_DIMENSION || bitmap.height < MIN_IMAGE_DIMENSION) {
                bitmap.recycle()
                ImageStateManager.markDone(url, CachedResult(imageData.data, imageData.mimeType))
                return createImageResponse(imageData.data, imageData.mimeType)
            }

            pendingOriginals[url] = imageData

            val placeholder = createLogoPlaceholder(bitmap.width, bitmap.height)
            placeholderCache.put(url, placeholder)

            scope.launch(AdaptivePerformanceEngine.getDispatcher()) {
                processImageAsync(url, bitmap, imageData, view)
            }

            return createNoCachePlaceholderResponse(placeholder)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing: ${e.message}")
            val pendingOriginal = pendingOriginals[url]
            if (pendingOriginal != null) {
                ImageStateManager.markDone(url, CachedResult(pendingOriginal.data, pendingOriginal.mimeType))
                pendingOriginals.remove(url)
                return createImageResponse(pendingOriginal.data, pendingOriginal.mimeType)
            }
            ImageStateManager.markFailed(url)
            return null
        }
    }

    private suspend fun processImageAsync(url: String, bitmap: Bitmap, imageData: ImageData, view: WebView?) {
        try {
            var isNSFW = false
            var maxConfidence = 0f
            
            try {
                val filterResult = contentFilterPipeline.processImage(bitmap)
                isNSFW = filterResult.isNSFW
                maxConfidence = filterResult.confidence
                Log.d(TAG, "Filter result: $isNSFW, stage: ${filterResult.stage}, confidence: $maxConfidence for $url")
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed: ${e.message}")
            }

            placeholderCache.remove(url)

            val resultData: ByteArray
            val mimeType: String

            if (isNSFW) {
                StatsTracker.incrementNsfwBlocked()
                ImageStateManager.storeOriginal(url, CachedResult(imageData.data, imageData.mimeType))
                if (maxConfidence < lowConfidenceThreshold) {
                    ImageStateManager.markLowConfidence(url)
                }
                val pixelatedBitmap = applyPixelation(bitmap)
                val outputStream = ByteArrayOutputStream()
                pixelatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                resultData = outputStream.toByteArray()
                mimeType = "image/jpeg"
                pixelatedBitmap.recycle()
                bitmap.recycle()
            } else {
                // Image passed NSFW check - now apply Haya Mode (face blur) separately
                val userGender = SettingsManager.getUserGender()
                val hayaResult = try {
                    hayaModeProcessor.processImage(bitmap, userGender)
                } catch (e: Exception) {
                    Log.e(TAG, "Haya Mode processing failed: ${e.message}")
                    null
                }
                
                if (hayaResult != null && hayaResult.facesBlurred > 0 && hayaResult.bitmap != null) {
                    // Faces were blurred by Haya Mode
                    val outputStream = ByteArrayOutputStream()
                    hayaResult.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    resultData = outputStream.toByteArray()
                    mimeType = "image/jpeg"
                    hayaResult.bitmap.recycle()
                    bitmap.recycle()
                    Log.d(TAG, "Haya Mode: Blurred ${hayaResult.facesBlurred} faces for $url")
                } else {
                    // No faces blurred, use original
                    hayaResult?.bitmap?.recycle()
                    bitmap.recycle()
                    resultData = imageData.data
                    mimeType = imageData.mimeType
                }
            }
            
            // Mark done with valid result before triggering refresh
            ImageStateManager.markDone(url, CachedResult(resultData, mimeType))
            ImageStateManager.resetRetry(url)
            placeholderCache.remove(url)
            processingStartTime.remove(url)
            pendingOriginals.remove(url)

            // Trigger image refresh with retry mechanism
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                refreshImageWithRetry(url, view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Async processing failed: ${e.message}")
            // On failure, mark done with original data so image still shows
            ImageStateManager.markDone(url, CachedResult(imageData.data, imageData.mimeType))
            placeholderCache.remove(url)
            processingStartTime.remove(url)
            pendingOriginals.remove(url)
            
            bitmap.recycle()
            
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                refreshImageWithRetry(url, view)
            }
        }
    }

    private fun refreshImageWithRetry(url: String, view: WebView?, attempt: Int = 0) {
        val webView = view ?: currentWebView ?: return
        val maxAttempts = 5
        val escapedUrl = escapeJsString(url)
        
        webView.evaluateJavascript("""
            (function() {
                var found = false;
                var imgs = document.querySelectorAll('img');
                var ts = Date.now();
                imgs.forEach(function(img) {
                    if (img.src && img.src.indexOf('$escapedUrl') !== -1) {
                        found = true;
                        var newSrc = img.src;
                        newSrc = newSrc.replace(/[&?]_processed=\d+/g, '');
                        newSrc = newSrc.replace(/[&?]_t=\d+/g, '');
                        newSrc = newSrc + (newSrc.indexOf('?') > -1 ? '&' : '?') + '_processed=' + ts + '&_t=' + ts;
                        img.src = '';
                        img.src = newSrc;
                    }
                });
                return found;
            })();
        """.trimIndent()) { result ->
            // If no image found and we haven't exceeded retries, try again
            if (result == "false" && attempt < maxAttempts) {
                webView.postDelayed({
                    refreshImageWithRetry(url, view, attempt + 1)
                }, refreshRetryDelay * (attempt + 1))
            }
        }
    }

    private fun isImageUrl(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        
        if (lowercaseUrl.contains(".js") || 
            lowercaseUrl.contains(".css") || 
            lowercaseUrl.contains(".html") ||
            lowercaseUrl.contains(".woff") ||
            lowercaseUrl.contains(".ttf") ||
            lowercaseUrl.contains(".mp4") ||
            lowercaseUrl.contains(".m3u8") ||
            lowercaseUrl.contains(".mpd") ||
            lowercaseUrl.contains("emoji") ||
            lowercaseUrl.contains("static") ||
            lowercaseUrl.contains("rsrc") ||
            lowercaseUrl.contains("sprite") ||
            lowercaseUrl.contains("/icon") ||
            lowercaseUrl.contains("_icon")) {
            return false
        }
        
        if (!lowercaseUrl.contains("scontent") && !lowercaseUrl.contains("fbcdn")) {
            return false
        }
        
        return lowercaseUrl.endsWith(".jpg") ||
                lowercaseUrl.endsWith(".jpeg") ||
                lowercaseUrl.endsWith(".png") ||
                lowercaseUrl.endsWith(".webp") ||
                lowercaseUrl.endsWith(".gif") ||
                lowercaseUrl.contains(".jpg?") ||
                lowercaseUrl.contains(".jpeg?") ||
                lowercaseUrl.contains(".png?") ||
                lowercaseUrl.contains(".webp?") ||
                lowercaseUrl.contains("video") ||
                lowercaseUrl.contains("thumbnail") ||
                lowercaseUrl.contains("poster") ||
                (lowercaseUrl.contains("photo") && !lowercaseUrl.contains("photoshop"))
    }

    private fun downloadImage(urlString: String, request: WebResourceRequest?): ImageData? {
        if (!isAllowedImageHost(urlString)) {
            Log.d(TAG, "Blocked download from untrusted host: $urlString")
            return null
        }
        
        val headers = mutableMapOf<String, String>()
        request?.requestHeaders?.forEach { (key, value) ->
            headers[key] = value
        }
        headers["Accept-Encoding"] = NetworkOptimizer.getOptimalAcceptEncoding()
        
        if (CronetHelper.isFacebookCdn(urlString)) {
            try {
                val cronetResponse = CronetHelper.fetchImage(urlString, headers)
                if (cronetResponse != null && cronetResponse.data.isNotEmpty()) {
                    return ImageData(cronetResponse.data, cronetResponse.mimeType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cronet failed, falling back: ${e.message}")
            }
        }
        
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
            connection.setRequestProperty("Accept-Encoding", NetworkOptimizer.getOptimalAcceptEncoding())
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            
            request?.requestHeaders?.forEach { (key, value) ->
                val lowerKey = key.lowercase()
                if (lowerKey != "user-agent" && lowerKey != "accept-encoding") {
                    connection.setRequestProperty(key, value)
                }
            }
            
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val mimeType = connection.contentType?.split(";")?.firstOrNull() ?: "image/jpeg"
                val data = connection.inputStream.readBytes()
                connection.disconnect()
                ImageData(data, mimeType)
            } else {
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            null
        }
    }

    private fun isAllowedImageHost(urlString: String): Boolean {
        return try {
            val uri = Uri.parse(urlString)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "https" && scheme != "http") {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            host.endsWith("fbcdn.net") ||
            host.endsWith("facebook.com") ||
            host.endsWith("fb.com") ||
            host.endsWith("cdninstagram.com") ||
            host.endsWith("instagram.com") ||
            host.contains("scontent")
        } catch (e: Exception) {
            false
        }
    }

    private fun applyPixelation(source: Bitmap): Bitmap {
        val pixelSize = 32
        val w = source.width
        val h = source.height
        val smallW = maxOf(w / pixelSize, 1)
        val smallH = maxOf(h / pixelSize, 1)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, false)
        val pixelated = Bitmap.createScaledBitmap(small, w, h, false)
        small.recycle()
        return pixelated
    }

    private fun applyStackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = maxOf(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }

    private fun createImageResponse(data: ByteArray, mimeType: String): WebResourceResponse {
        return WebResourceResponse(
            mimeType,
            "UTF-8",
            ByteArrayInputStream(data)
        )
    }

    private fun createNoCachePlaceholderResponse(data: ByteArray): WebResourceResponse {
        val headers = mapOf(
            "Cache-Control" to "no-store, no-cache, must-revalidate, max-age=0",
            "Pragma" to "no-cache",
            "Expires" to "0"
        )
        return WebResourceResponse(
            "image/png",
            "UTF-8",
            200,
            "OK",
            headers,
            ByteArrayInputStream(data)
        )
    }

    private fun createLogoPlaceholder(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.drawColor(Color.parseColor("#1A1A2E"))
        
        val minDimension = minOf(width, height)
        val logoSize = minOf(minDimension * 0.4f, 120f)
        val centerX = width / 2f
        val centerY = height / 2f
        
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A90D9")
            style = Paint.Style.FILL
        }
        
        val shieldPath = Path().apply {
            val shieldWidth = logoSize * 0.8f
            val shieldHeight = logoSize
            val left = centerX - shieldWidth / 2
            val top = centerY - shieldHeight / 2
            val right = centerX + shieldWidth / 2
            val bottom = centerY + shieldHeight / 2
            
            moveTo(centerX, top)
            lineTo(right, top + shieldHeight * 0.15f)
            lineTo(right, top + shieldHeight * 0.5f)
            quadTo(right, bottom - shieldHeight * 0.1f, centerX, bottom)
            quadTo(left, bottom - shieldHeight * 0.1f, left, top + shieldHeight * 0.5f)
            lineTo(left, top + shieldHeight * 0.15f)
            close()
        }
        canvas.drawPath(shieldPath, shieldPaint)
        
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = logoSize * 0.06f
            strokeCap = Paint.Cap.ROUND
        }
        
        val eyeWidth = logoSize * 0.45f
        val eyeHeight = logoSize * 0.25f
        val eyeRect = RectF(
            centerX - eyeWidth / 2,
            centerY - eyeHeight / 2,
            centerX + eyeWidth / 2,
            centerY + eyeHeight / 2
        )
        canvas.drawOval(eyeRect, eyePaint)
        
        val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, logoSize * 0.08f, pupilPaint)
        
        val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E74C3C")
            style = Paint.Style.STROKE
            strokeWidth = logoSize * 0.08f
            strokeCap = Paint.Cap.ROUND
        }
        val slashOffset = logoSize * 0.25f
        canvas.drawLine(
            centerX - slashOffset,
            centerY + slashOffset,
            centerX + slashOffset,
            centerY - slashOffset,
            slashPaint
        )
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        navigationListener?.onPageChanged(url)
        if (!isAuthPage(url)) {
            injectPerformanceCSS(view)
            injectCleanupScript(view)
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!isAuthPage(url)) {
            view?.evaluateJavascript("window._hayaGuardActive = false;", null)
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!isAuthPage(url)) {
            navigationListener?.onPageChanged(url)
            view?.evaluateJavascript("window._hayaGuardActive = false;", null)
        }
    }

    private fun isAuthPage(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("login") ||
                lowerUrl.contains("checkpoint") ||
                lowerUrl.contains("/auth") ||
                lowerUrl.contains("two_step") ||
                lowerUrl.contains("password") ||
                lowerUrl.contains("verify") ||
                lowerUrl.contains("confirmation") ||
                lowerUrl.contains("code_entry") ||
                lowerUrl.contains("approvals") ||
                lowerUrl.contains("recover") ||
                lowerUrl.contains("identify")
    }

    override fun onReceivedError(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            view?.loadDataWithBaseURL(null, getErrorPageHtml(), "text/html", "UTF-8", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        view?.loadDataWithBaseURL(null, getErrorPageHtml(), "text/html", "UTF-8", null)
    }

    private fun getErrorPageHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #3b5998 0%, #16223D 100%);
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        padding: 24px;
                        color: #fff;
                    }
                    .container {
                        text-align: center;
                        max-width: 320px;
                    }
                    .logo {
                        width: 120px;
                        height: 120px;
                        margin-bottom: 32px;
                    }
                    .logo svg {
                        width: 100%;
                        height: 100%;
                    }
                    h1 {
                        font-size: 24px;
                        font-weight: 600;
                        margin-bottom: 12px;
                        letter-spacing: 0.5px;
                    }
                    p {
                        font-size: 15px;
                        color: rgba(255,255,255,0.8);
                        line-height: 1.5;
                        margin-bottom: 32px;
                    }
                    .retry-btn {
                        background: rgba(255,255,255,0.15);
                        border: 1px solid rgba(255,255,255,0.3);
                        color: #fff;
                        padding: 14px 32px;
                        border-radius: 25px;
                        font-size: 16px;
                        font-weight: 500;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        backdrop-filter: blur(10px);
                    }
                    .retry-btn:active {
                        background: rgba(255,255,255,0.25);
                        transform: scale(0.98);
                    }
                    .icon-wifi {
                        width: 64px;
                        height: 64px;
                        margin-bottom: 24px;
                        opacity: 0.9;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <svg class="icon-wifi" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <path d="M1 9C4.5 5.5 8.5 4 12 4s7.5 1.5 11 5" stroke-linecap="round"/>
                        <path d="M5 13c2.5-2.5 5-3.5 7-3.5s4.5 1 7 3.5" stroke-linecap="round"/>
                        <path d="M9 17c1.5-1.5 2.5-2 3-2s1.5.5 3 2" stroke-linecap="round"/>
                        <circle cx="12" cy="20" r="1" fill="currentColor"/>
                        <line x1="4" y1="4" x2="20" y2="20" stroke-linecap="round" stroke="#ff6b6b" stroke-width="2"/>
                    </svg>
                    <h1>No Connection</h1>
                    <p>Unable to connect to the internet. Please check your network connection and try again.</p>
                    <button class="retry-btn" onclick="location.reload()">Try Again</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun injectPerformanceCSS(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                if (document.getElementById('hayaguard-perf-css')) return;
                var style = document.createElement('style');
                style.id = 'hayaguard-perf-css';
                style.textContent = '* { box-shadow: none !important; text-shadow: none !important; backdrop-filter: none !important; -webkit-backdrop-filter: none !important; } *::before, *::after { box-shadow: none !important; text-shadow: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent(), null)
    }

    private fun injectCleanupScript(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                if (window._hayaGuardActive) return;
                window._hayaGuardActive = true;
                
                var sponsoredPatterns = ['Sponsored', 'Gesponsert', 'Sponsorisé', 'Patrocinado', 'Bersponsor', 'প্রায়োজিত', 'স্পন্সরড', 'বিজ্ঞাপন', 'スポンサー', '赞助内容', '贊助', 'مُموَّستل', 'إعلان', 'Được tài trợ', 'ได้รับการสนับสนุน', 'Реклама', 'Publicidad', 'Reklam', 'Sponsorlu', 'Sponsorizzato', 'Gesponsord', 'Sponzorováno', 'Sponsorowane', 'Hirdetés', 'Sponset', 'Sponsrad', 'Sponsoroitu', 'Sponsoreret', 'Χορηγούμενο', 'ממומן', 'प्रायोजित', 'స్పాన్సర్డ్', 'ಪ್ರಾಯೋಜಿತ', 'സ്പോൺസർ ചെയ്തത്', 'ஸ்பான்சர்', 'ପ୍ରାୟୋଜିତ', 'સ્પોન્સર્ડ', 'ਸਪਾਂਸਰਡ', 'مالی تعاون یافتہ', 'Ditaja'];
                var greyColors = ['8a8d91', '65686c', '606770', '90949c'];
                
                function hideElement(el) {
                    if (!el) return;
                    el.style.cssText = 'display:none!important;visibility:hidden!important;height:0!important;max-height:0!important;overflow:hidden!important;opacity:0!important;pointer-events:none!important;margin:0!important;padding:0!important;';
                }
                
                function isGreyColor(el) {
                    if (!el) return false;
                    var style = el.getAttribute('style') || '';
                    for (var i = 0; i < greyColors.length; i++) {
                        if (style.indexOf(greyColors[i]) !== -1) return true;
                    }
                    if (el.classList && (el.classList.contains('f5') || el.classList.contains('f6'))) return true;
                    return false;
                }
                
                function findRootCard(el) {
                    var current = el;
                    for (var i = 0; i < 15; i++) {
                        if (!current || !current.parentElement) break;
                        current = current.parentElement;
                        if (current.dataset && current.dataset.compId) return current;
                        if (current.getAttribute && current.getAttribute('data-comp-id')) return current;
                        if (current.dataset && current.dataset.actualHeight && parseInt(current.dataset.actualHeight) > 200) return current;
                    }
                    return null;
                }
                
                function removeOpenApp() {
                    var banners = document.querySelectorAll('.fixed-container.bottom');
                    banners.forEach(function(banner) {
                        if (banner.dataset && banner.dataset.clOpenAppHidden) return;
                        var hasDeleteBtn = banner.querySelector('[aria-label*="Delete"]') || banner.querySelector('[aria-label*="Remove"]');
                        if (hasDeleteBtn) return;
                        var hasSearchHistory = banner.querySelector('[aria-label*="history"]') || banner.querySelector('[data-testid*="search"]');
                        if (hasSearchHistory) return;
                        var text = (banner.textContent || '').toLowerCase();
                        if (text.indexOf('delete') !== -1 || text.indexOf('remove this from') !== -1 || text.indexOf('search history') !== -1) return;
                        if (text.indexOf('open app') !== -1 || text.indexOf('open in app') !== -1 || text.indexOf('get the app') !== -1 || text.indexOf('use the app') !== -1 || text.indexOf('get facebook') !== -1 || text.indexOf('use app') !== -1) {
                            banner.dataset.clOpenAppHidden = '1';
                            hideElement(banner);
                        }
                    });
                    var appLinks = document.querySelectorAll('a[href*="play.google.com"], a[href*="apps.apple.com"], a[href*="itunes.apple.com"]');
                    appLinks.forEach(function(link) {
                        if (link.dataset && link.dataset.clOpenAppHidden) return;
                        link.dataset.clOpenAppHidden = '1';
                        hideElement(link);
                    });
                }
                
                function removeSponsored() {
                    document.querySelectorAll('span.f5, span.f6, span[style*="8a8d91"], span[style*="65686c"]').forEach(function(span) {
                        if (span.dataset && span.dataset.clSponsorChecked) return;
                        span.dataset.clSponsorChecked = '1';
                        var txt = (span.textContent || '').trim();
                        var isSponsored = false;
                        for (var j = 0; j < sponsoredPatterns.length; j++) {
                            if (txt.indexOf(sponsoredPatterns[j]) === 0 || txt.indexOf(sponsoredPatterns[j]) !== -1) {
                                isSponsored = true;
                                break;
                            }
                        }
                        if (!isSponsored) return;
                        var parent = span;
                        for (var k = 0; k < 30 && parent && parent !== document.body; k++) {
                            parent = parent.parentElement;
                            if (!parent) break;
                            var h = parent.getAttribute('data-actual-height');
                            var trackingId = parent.getAttribute('data-tracking-duration-id');
                            if (h && parseInt(h) > 150) {
                                if (!parent.dataset.clSponsorHidden) {
                                    parent.dataset.clSponsorHidden = '1';
                                    hideElement(parent);
                                }
                                return;
                            }
                            if (trackingId) {
                                if (!parent.dataset.clSponsorHidden) {
                                    parent.dataset.clSponsorHidden = '1';
                                    hideElement(parent);
                                }
                                return;
                            }
                        }
                    });
                    document.querySelectorAll('[data-testid="sponsored-story-photo"]').forEach(function(el) {
                        var parent = el;
                        for (var k = 0; k < 20 && parent && parent !== document.body; k++) {
                            parent = parent.parentElement;
                            if (!parent) break;
                            var h = parent.getAttribute('data-actual-height');
                            if (h && parseInt(h) > 200) {
                                if (!parent.dataset.clSponsorHidden) {
                                    parent.dataset.clSponsorHidden = '1';
                                    hideElement(parent);
                                }
                                return;
                            }
                        }
                    });
                }
                
                function removePromos() {
                    var promos = document.querySelectorAll('div[data-sigil="m-promo-jewel-container"]');
                    for (var i = 0; i < promos.length; i++) {
                        if (!promos[i].dataset.clPromoHidden) {
                            promos[i].dataset.clPromoHidden = '1';
                            hideElement(promos[i]);
                        }
                    }
                }
                
                function runCleanup() {
                    try {
                        removeOpenApp();
                        removeSponsored();
                        removePromos();
                    } catch(e) {}
                }
                
                runCleanup();
                setTimeout(runCleanup, 500);
                setTimeout(runCleanup, 1500);
                setTimeout(runCleanup, 3000);
                
                var observer = new MutationObserver(function() {
                    setTimeout(runCleanup, 100);
                });
                observer.observe(document.body || document.documentElement, {childList: true, subtree: true});
            })();
        """.trimIndent(), null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val uri = request.url
        val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        
        if (url.startsWith("fb://") || url.startsWith("intent://") || url.contains("market://")) {
            return true
        }
        
        if (host == "l.facebook.com" || host == "lm.facebook.com") {
            try {
                val targetUrl = uri.getQueryParameter("u")
                if (targetUrl != null && isValidExternalUrl(targetUrl)) {
                    val cleanUrl = removeFbclid(targetUrl)
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
            return true
        }
        
        if (host.endsWith("facebook.com") || host.endsWith("fb.com") || 
            host.endsWith("messenger.com") || host.endsWith("fbcdn.net") ||
            host.endsWith("fb.me") || host.endsWith("instagram.com")) {
            return false
        }
        
        try {
            if (isValidExternalUrl(url)) {
                val cleanUrl = removeFbclid(url)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (_: Exception) {}
        return true
    }

    private fun isValidExternalUrl(urlString: String): Boolean {
        return try {
            val uri = Uri.parse(urlString)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && uri.host?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }

    private fun removeFbclid(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val params = uri.queryParameterNames
            if (params.contains("fbclid")) {
                val builder = Uri.Builder()
                    .scheme(uri.scheme)
                    .encodedAuthority(uri.encodedAuthority)
                    .encodedPath(uri.encodedPath)
                
                for (param in params) {
                    if (param != "fbclid") {
                        // Use encoded value to preserve unicode
                        val encodedValue = uri.getQueryParameter(param)
                        if (encodedValue != null) {
                            builder.appendQueryParameter(param, encodedValue)
                        }
                    }
                }
                
                uri.encodedFragment?.let { builder.encodedFragment(it) }
                builder.build().toString()
            } else {
                url
            }
        } catch (_: Exception) {
            url
        }
    }
    
    fun close() {
        try {
            hayaModeProcessor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Haya Mode processor: ${e.message}")
        }
    }

    private data class ImageData(val data: ByteArray, val mimeType: String)
}

data class CachedResult(val data: ByteArray, val mimeType: String)
