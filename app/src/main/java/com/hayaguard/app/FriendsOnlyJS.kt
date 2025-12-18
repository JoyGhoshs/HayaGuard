package com.hayaguard.app

object FriendsOnlyJS {

    val FRIENDS_ONLY_SCRIPT = """
        (function() {
            if (window._hayaGuardFriendsOnly) return;
            window._hayaGuardFriendsOnly = true;
            
            var style = document.createElement('style');
            style.textContent = '[data-hayaguard-hide]{display:none!important;height:0!important;overflow:hidden!important;margin:0!important;padding:0!important}';
            document.head.appendChild(style);
            
            var processedPosts = new Set();
            
            function isNonFriendPost(postElement) {
                if (!postElement) return false;
                var html = postElement.innerHTML || '';
                var text = postElement.innerText || '';
                if (text.indexOf('Suggested for you') !== -1) return true;
                if (html.indexOf('>Follow<') !== -1) return true;
                if (html.indexOf('>Join<') !== -1) return true;
                if (text.indexOf(' Follow ') !== -1 && text.indexOf('Following') === -1) return true;
                var followButtons = postElement.querySelectorAll('[role="button"]');
                for (var i = 0; i < followButtons.length; i++) {
                    var btnText = followButtons[i].innerText || '';
                    if (btnText.trim() === 'Follow' || btnText.trim() === 'Join') {
                        return true;
                    }
                }
                var allElements = postElement.querySelectorAll('*');
                for (var i = 0; i < allElements.length; i++) {
                    var el = allElements[i];
                    var elText = el.innerText || '';
                    if (elText.trim() === 'Follow' || elText.trim() === 'Join') {
                        if (el.children.length === 0) {
                            return true;
                        }
                    }
                }
                return false;
            }
            
            function filterNonFriendPosts() {
                var posts = document.querySelectorAll('[data-tracking-duration-id]');
                posts.forEach(function(post) {
                    if (post.hasAttribute('data-hayaguard-processed')) return;
                    post.setAttribute('data-hayaguard-processed', 'true');
                    var trackingId = post.getAttribute('data-tracking-duration-id');
                    if (processedPosts.has(trackingId)) return;
                    processedPosts.add(trackingId);
                    var innerText = post.innerText || '';
                    if (innerText.indexOf('Reels') !== -1) return;
                    if (innerText.indexOf('Sponsored') !== -1) return;
                    if (isNonFriendPost(post)) {
                        post.setAttribute('data-hayaguard-hide', 'true');
                    }
                });
            }
            
            filterNonFriendPosts();
            setInterval(filterNonFriendPosts, 500);
            
            window.addEventListener('scroll', function() {
                setTimeout(filterNonFriendPosts, 100);
            }, { passive: true });
            
            var observer = new MutationObserver(function() {
                setTimeout(filterNonFriendPosts, 50);
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        })();
    """.trimIndent()
}
