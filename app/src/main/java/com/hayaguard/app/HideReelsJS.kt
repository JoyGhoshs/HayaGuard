package com.hayaguard.app

object HideReelsJS {

    val HIDE_REELS_SCRIPT = """
        (function() {
            if (window._hayaGuardReelsHider) return;
            window._hayaGuardReelsHider = true;
            
            var privacyQuotes = [
                { quote: "Arguing that you don't care about the right to privacy because you have nothing to hide is no different than saying you don't care about free speech because you have nothing to say.", author: "Edward Snowden" },
                { quote: "The NSA has built an infrastructure that allows it to intercept almost everything. With this capability, the vast majority of human communications are automatically ingested without targeting.", author: "Edward Snowden" },
                { quote: "Big Brother is Watching You.", author: "George Orwell, 1984" },
                { quote: "If you want to keep a secret, you must also hide it from yourself.", author: "George Orwell, 1984" },
                { quote: "Power is in tearing human minds to pieces and putting them together again in new shapes of your own choosing.", author: "George Orwell, 1984" },
                { quote: "The best way to keep a prisoner from escaping is to make sure he never knows he's in prison.", author: "Fyodor Dostoevsky" },
                { quote: "Someone must have slandered Josef K., for one morning, without having done anything truly wrong, he was arrested.", author: "Franz Kafka, The Trial" },
                { quote: "It's only because of their stupidity that they're able to be so sure of themselves.", author: "Franz Kafka, The Trial" },
                { quote: "Under observation, we act less free, which means we effectively are less free.", author: "Edward Snowden" },
                { quote: "Privacy is not about something to hide. Privacy is about something to protect.", author: "Edward Snowden" },
                { quote: "In the end the Party would announce that two and two made five, and you would have to believe it.", author: "George Orwell, 1984" },
                { quote: "Freedom is the freedom to say that two plus two make four. If that is granted, all else follows.", author: "George Orwell, 1984" }
            ];
            
            var quoteIndex = 0;
            var processedReels = new Set();
            
            function getNextQuote() {
                var quote = privacyQuotes[quoteIndex];
                quoteIndex = (quoteIndex + 1) % privacyQuotes.length;
                return quote;
            }
            
            function isReelsContainer(element) {
                if (!element || !element.getAttribute) return false;
                var innerHTML = element.innerHTML || '';
                var innerText = element.innerText || '';
                if (innerText.indexOf('Reels') === -1 && innerHTML.indexOf('View reel') === -1) return false;
                var hasReelsHeader = element.querySelector('h2');
                if (hasReelsHeader) {
                    var headerText = hasReelsHeader.innerText || '';
                    if (headerText.trim() === 'Reels') return true;
                }
                var textAreas = element.querySelectorAll('[data-mcomponent="TextArea"]');
                for (var i = 0; i < textAreas.length; i++) {
                    var text = textAreas[i].innerText || '';
                    if (text.trim() === 'Reels') return true;
                }
                if (innerHTML.indexOf('View reel video') !== -1) return true;
                if (innerHTML.indexOf('aria-label="View reel') !== -1) return true;
                return false;
            }
            
            function replaceReels() {
                var containers = document.querySelectorAll('[data-tracking-duration-id]');
                containers.forEach(function(container) {
                    var trackingId = container.getAttribute('data-tracking-duration-id');
                    if (processedReels.has(trackingId)) return;
                    if (container.getAttribute('data-hayaguard-processed')) return;
                    
                    if (isReelsContainer(container)) {
                        processedReels.add(trackingId);
                        container.setAttribute('data-hayaguard-processed', 'true');
                        
                        var quoteData = getNextQuote();
                        
                        var reelButton = container.querySelector('[aria-label*="View reel"]');
                        if (reelButton) {
                            var w = reelButton.offsetWidth || 388;
                            var h = reelButton.offsetHeight || 438;
                            reelButton.removeAttribute('aria-label');
                            reelButton.removeAttribute('role');
                            reelButton.removeAttribute('tabindex');
                            reelButton.removeAttribute('data-focusable');
                            reelButton.removeAttribute('data-action-id');
                            reelButton.innerHTML = '<div style="width:' + w + 'px; height:' + h + 'px; background:#F0F2F5; border-radius:18px; display:flex; flex-direction:column; justify-content:center; align-items:center; padding:20px; box-sizing:border-box;"><div style="font-size:36px; color:#65686c; margin-bottom:12px; font-family:Georgia,serif;">"</div><div style="color:#1C1C1E; font-size:14px; line-height:1.5; text-align:center; font-family:-apple-system,BlinkMacSystemFont,sans-serif; font-style:italic; max-width:95%; margin-bottom:12px;">' + quoteData.quote + '</div><div style="color:#65686c; font-size:12px; font-family:-apple-system,BlinkMacSystemFont,sans-serif; font-weight:500;">— ' + quoteData.author + '</div><div style="margin-top:16px; background:rgba(0,0,0,0.05); padding:6px 12px; border-radius:12px; display:flex; align-items:center; gap:6px;"><span style="font-size:11px;">🛡️</span><span style="color:#65686c; font-size:10px; font-family:-apple-system,BlinkMacSystemFont,sans-serif;">Hidden by HayaGuard</span></div></div>';
                        } else {
                            var innerContent = container.querySelector('[data-tracking-duration-id="22"]');
                            if (!innerContent) {
                                var allInner = container.querySelectorAll('[data-mcomponent="MContainer"]');
                                for (var i = 0; i < allInner.length; i++) {
                                    if (allInner[i].offsetHeight > 400) {
                                        innerContent = allInner[i];
                                        break;
                                    }
                                }
                            }
                            if (innerContent) {
                                var w = innerContent.offsetWidth || 388;
                                var h = innerContent.offsetHeight || 438;
                                innerContent.innerHTML = '<div style="width:' + w + 'px; height:' + h + 'px; background:#F0F2F5; border-radius:18px; display:flex; flex-direction:column; justify-content:center; align-items:center; padding:20px; box-sizing:border-box;"><div style="font-size:36px; color:#65686c; margin-bottom:12px; font-family:Georgia,serif;">"</div><div style="color:#1C1C1E; font-size:14px; line-height:1.5; text-align:center; font-family:-apple-system,BlinkMacSystemFont,sans-serif; font-style:italic; max-width:95%; margin-bottom:12px;">' + quoteData.quote + '</div><div style="color:#65686c; font-size:12px; font-family:-apple-system,BlinkMacSystemFont,sans-serif; font-weight:500;">— ' + quoteData.author + '</div><div style="margin-top:16px; background:rgba(0,0,0,0.05); padding:6px 12px; border-radius:12px; display:flex; align-items:center; gap:6px;"><span style="font-size:11px;">🛡️</span><span style="color:#65686c; font-size:10px; font-family:-apple-system,BlinkMacSystemFont,sans-serif;">Hidden by HayaGuard</span></div></div>';
                            }
                        }
                        
                        var header = container.querySelector('h2');
                        if (header) {
                            var span = header.querySelector('span');
                            if (span && span.innerText.trim() === 'Reels') {
                                span.innerText = 'Privacy Thought';
                            }
                        }
                    }
                });
            }
            
            replaceReels();
            
            window.addEventListener('scroll', function() {
                replaceReels();
            }, { passive: true });
            
            var observer = new MutationObserver(function() {
                replaceReels();
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        })();
    """.trimIndent()
}
