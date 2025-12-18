package com.hayaguard.app

import android.webkit.JavascriptInterface

class QuickLensInterface(private val onImageLongPress: (String) -> Unit) {

    @JavascriptInterface
    fun onImageSelected(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            onImageLongPress(imageUrl)
        }
    }
}

object QuickLensJS {
    const val LONG_PRESS_SCRIPT = """
        (function() {
            if (window._quickLensInitialized) return;
            window._quickLensInitialized = true;
            
            var longPressTimer = null;
            var longPressDuration = 500;
            var startX = 0;
            var startY = 0;
            var moveThreshold = 10;
            
            function findImageUrl(element) {
                if (!element) return null;
                
                if (element.tagName === 'IMG' && element.src) {
                    return element.src;
                }
                
                var style = window.getComputedStyle(element);
                var bgImage = style.backgroundImage;
                if (bgImage && bgImage !== 'none') {
                    var match = bgImage.match(/url\(['"]?([^'"]+)['"]?\)/);
                    if (match && match[1]) {
                        return match[1];
                    }
                }
                
                var img = element.querySelector('img');
                if (img && img.src) {
                    return img.src;
                }
                
                if (element.parentElement && element.parentElement !== document.body) {
                    return findImageUrl(element.parentElement);
                }
                
                return null;
            }
            
            function handleTouchStart(e) {
                var touch = e.touches[0];
                startX = touch.clientX;
                startY = touch.clientY;
                
                var target = document.elementFromPoint(startX, startY);
                var imageUrl = findImageUrl(target);
                
                if (imageUrl) {
                    longPressTimer = setTimeout(function() {
                        e.preventDefault();
                        e.stopPropagation();
                        if (window.QuickLens) {
                            window.QuickLens.onImageSelected(imageUrl);
                        }
                    }, longPressDuration);
                }
            }
            
            function handleTouchMove(e) {
                if (longPressTimer) {
                    var touch = e.touches[0];
                    var deltaX = Math.abs(touch.clientX - startX);
                    var deltaY = Math.abs(touch.clientY - startY);
                    if (deltaX > moveThreshold || deltaY > moveThreshold) {
                        clearTimeout(longPressTimer);
                        longPressTimer = null;
                    }
                }
            }
            
            function handleTouchEnd(e) {
                if (longPressTimer) {
                    clearTimeout(longPressTimer);
                    longPressTimer = null;
                }
            }
            
            document.addEventListener('touchstart', handleTouchStart, { passive: false, capture: true });
            document.addEventListener('touchmove', handleTouchMove, { passive: true, capture: true });
            document.addEventListener('touchend', handleTouchEnd, { passive: true, capture: true });
            document.addEventListener('touchcancel', handleTouchEnd, { passive: true, capture: true });
            
            console.log('QuickLens: Long press detection initialized');
        })();
    """
}
