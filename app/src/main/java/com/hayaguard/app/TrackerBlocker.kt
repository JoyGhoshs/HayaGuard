package com.hayaguard.app

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object TrackerBlocker {
    
    private val blockedPatterns = arrayOf(
        "connect.facebook.net/en_US/fbevents.js",
        "connect.facebook.net/en_US/sdk.js",
        "connect.facebook.net/signals",
        "connect.facebook.net/en_US/all.js",
        "b-graph.facebook.com",
        "pixel.facebook.com",
        "analytics.facebook.com",
        "an.facebook.com",
        "facebook.com/tr?",
        "facebook.com/tr/",
        "facebook.com/audience_network",
        "facebook.net/signals",
        "staticxx.facebook.com/connect",
        "fbsbx.com/paid_ads_pixel",
        "facebook.com/plugins/like.php",
        "google-analytics.com",
        "googleanalytics.com",
        "doubleclick.net",
        "googletagmanager.com",
        "googlesyndication.com",
        "googletagservices.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "google.com/ccm/collect",
        "google.com/pagead",
        "google.com/adsense",
        "google.com/ads",
        "pixel.wp.com",
        "stats.wp.com",
        "quantserve.com",
        "scorecardresearch.com",
        "amazon-adsystem.com",
        "crashlytics.com",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "segment.com",
        "hotjar.com",
        "fullstory.com",
        "mouseflow.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "adsrvr.org",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "adnxs.com",
        "bidswitch.net",
        "sharethrough.com",
        "smartadserver.com",
        "advertising.com",
        "moatads.com",
        "chartbeat.com",
        "comscore.com",
        "newrelic.com",
        "nr-data.net",
        "sentry.io"
    )
    
    private val blockedPrefixes = arrayOf(
        "https://www.google-analytics.com",
        "https://google-analytics.com",
        "https://stats.g.doubleclick.net",
        "https://ad.doubleclick.net",
        "https://googleads.g.doubleclick.net",
        "https://www.googletagmanager.com",
        "https://connect.facebook.net"
    )
    
    @Volatile
    private var blockedCount: Long = 0
    
    fun shouldBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        for (prefix in blockedPrefixes) {
            if (lowerUrl.startsWith(prefix)) {
                blockedCount++
                return true
            }
        }
        
        for (pattern in blockedPatterns) {
            if (lowerUrl.contains(pattern)) {
                blockedCount++
                return true
            }
        }
        
        return false
    }
    
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/javascript",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }
    
    fun getBlockedCount(): Long = blockedCount
    
    fun resetBlockedCount() {
        blockedCount = 0
    }
}
