package com.hayaguard.app

import android.webkit.JavascriptInterface

class FeedScraperInterface(
    private val onPostAnalyzed: (String, Boolean, Boolean, Boolean, String) -> Boolean
) {

    @JavascriptInterface
    fun onPostFound(
        postText: String,
        hasFollowButton: Boolean,
        hasJoinButton: Boolean,
        isSponsored: Boolean,
        posterName: String
    ): Boolean {
        return onPostAnalyzed(postText, hasFollowButton, hasJoinButton, isSponsored, posterName)
    }
}

object FeedScraperJS {

    const val INTERFACE_NAME = "HayaGuardFeedScraper"

    val SCRAPER_SCRIPT = """
        (function() {
            if (window._hayaGuardFeedObserver) return;
            
            var processedPosts = new Set();
            var hiddenPosts = new Set();
            
            function extractPostData(postElement) {
                var postId = postElement.getAttribute('data-tracking-duration-id') || 
                             postElement.getAttribute('data-comp-id') ||
                             Math.random().toString(36);
                
                if (processedPosts.has(postId)) return null;
                
                var postText = '';
                var textAreas = postElement.querySelectorAll('[data-mcomponent="TextArea"]');
                textAreas.forEach(function(area) {
                    var text = area.innerText || area.textContent || '';
                    if (text.length > 10 && text.length < 5000) {
                        postText += text + ' ';
                    }
                });
                
                var hasFollowButton = false;
                var hasJoinButton = false;
                var spans = postElement.querySelectorAll('span');
                spans.forEach(function(span) {
                    var text = (span.innerText || '').trim();
                    if (text === 'Follow') hasFollowButton = true;
                    if (text === 'Join') hasJoinButton = true;
                });
                
                var isSponsored = false;
                var allText = postElement.innerText || '';
                if (allText.indexOf('Sponsored') !== -1 || 
                    allText.indexOf('paid partnership') !== -1 ||
                    allText.indexOf('স্পন্সরড') !== -1 ||
                    allText.indexOf('বিজ্ঞাপন') !== -1) {
                    isSponsored = true;
                }
                
                var posterName = '';
                var profileLink = postElement.querySelector('[aria-label="Tap to open profile page"]');
                if (profileLink) {
                    var nameSpan = profileLink.querySelector('span.f2');
                    if (nameSpan) {
                        posterName = nameSpan.innerText || '';
                    }
                }
                
                processedPosts.add(postId);
                
                return {
                    id: postId,
                    text: postText.trim().substring(0, 2000),
                    hasFollowButton: hasFollowButton,
                    hasJoinButton: hasJoinButton,
                    isSponsored: isSponsored,
                    posterName: posterName,
                    element: postElement
                };
            }
            
            function hidePost(postElement) {
                postElement.style.display = 'none';
                postElement.style.height = '0';
                postElement.style.overflow = 'hidden';
                postElement.style.margin = '0';
                postElement.style.padding = '0';
            }
            
            function analyzeVisiblePosts() {
                var posts = document.querySelectorAll('[data-tracking-duration-id]');
                posts.forEach(function(post) {
                    var rect = post.getBoundingClientRect();
                    if (rect.top >= -200 && rect.top <= window.innerHeight) {
                        var data = extractPostData(post);
                        if (data && window.HayaGuardFeedScraper) {
                            var shouldHide = window.HayaGuardFeedScraper.onPostFound(
                                data.text,
                                data.hasFollowButton,
                                data.hasJoinButton,
                                data.isSponsored,
                                data.posterName
                            );
                            if (shouldHide && !hiddenPosts.has(data.id)) {
                                hiddenPosts.add(data.id);
                                hidePost(data.element);
                            }
                        }
                    }
                });
            }
            
            var scrollTimeout = null;
            window.addEventListener('scroll', function() {
                if (scrollTimeout) clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(analyzeVisiblePosts, 300);
            }, { passive: true });
            
            window._hayaGuardFeedObserver = new MutationObserver(function(mutations) {
                var hasNewPosts = false;
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1 && node.getAttribute && 
                                node.getAttribute('data-tracking-duration-id')) {
                                hasNewPosts = true;
                            }
                        });
                    }
                });
                if (hasNewPosts) {
                    setTimeout(analyzeVisiblePosts, 300);
                }
            });
            
            window._hayaGuardFeedObserver.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            setTimeout(analyzeVisiblePosts, 1000);
        })();
    """.trimIndent()
}
