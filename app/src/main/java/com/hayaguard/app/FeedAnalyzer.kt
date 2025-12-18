package com.hayaguard.app

import android.content.Context
import android.content.SharedPreferences
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.tasks.await

enum class ContentCategory {
    FAMILY_FRIENDS,
    POLITICAL_TOXIC,
    VIRAL_JUNK,
    NEWS_INFO,
    SPONSORED
}

data class FeedAnalysis(
    val category: ContentCategory,
    val confidence: Float,
    val isFromFollowing: Boolean,
    val isSponsored: Boolean,
    val isGroupPost: Boolean,
    val detectedLanguage: String
)

object FeedAnalyzer {

    private const val PREFS_NAME = "feed_dna_stats"
    private const val KEY_FAMILY_FRIENDS = "family_friends"
    private const val KEY_POLITICAL_TOXIC = "political_toxic"
    private const val KEY_VIRAL_JUNK = "viral_junk"
    private const val KEY_NEWS_INFO = "news_info"
    private const val KEY_SPONSORED = "sponsored"
    private const val KEY_TOTAL_POSTS = "total_posts"

    private lateinit var prefs: SharedPreferences
    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.3f)
                .build()
        )
    }

    private val sessionFamilyFriends = AtomicInteger(0)
    private val sessionPoliticalToxic = AtomicInteger(0)
    private val sessionViralJunk = AtomicInteger(0)
    private val sessionNewsInfo = AtomicInteger(0)
    private val sessionSponsored = AtomicInteger(0)

    private val politicalKeywordsEn = arrayOf(
        "election", "vote", "voting", "politician", "government", "congress",
        "senate", "president", "democrat", "republican", "liberal", "conservative",
        "trump", "biden", "politics", "political", "party", "campaign", "protest",
        "activism", "activist", "rights", "immigration", "border", "policy",
        "corruption", "scandal", "impeach", "legislation", "law", "bill",
        "tax", "economy", "inflation", "debate", "opinion", "controversial"
    )

    private val politicalKeywordsBn = arrayOf(
        "নির্বাচন", "ভোট", "সরকার", "রাজনীতি", "রাজনৈতিক", "দল", "প্রার্থী",
        "মন্ত্রী", "প্রধানমন্ত্রী", "সংসদ", "আন্দোলন", "প্রতিবাদ", "বিরোধী",
        "আওয়ামী", "বিএনপি", "জামাত", "ছাত্রলীগ", "যুবলীগ", "শ্রমিক",
        "হরতাল", "অবরোধ", "মিছিল", "দুর্নীতি", "ষড়যন্ত্র", "বিচার"
    )

    private val toxicPatternsEn = arrayOf(
        "you won't believe", "this will shock", "they don't want you to know",
        "wake up", "sheeple", "mainstream media", "fake news", "hoax",
        "conspiracy", "truth bomb", "exposed", "cancelled", "boycott",
        "outraged", "furious", "disgusted", "unacceptable", "share before deleted",
        "they're hiding", "censored", "banned", "silenced", "fight back"
    )

    private val toxicPatternsBn = arrayOf(
        "জাগো", "সত্য প্রকাশ", "গোপন তথ্য", "ষড়যন্ত্র", "মিথ্যা সংবাদ",
        "লুকানো সত্য", "প্রতারণা", "ধোঁকা", "বিশ্বাসঘাতক", "দালাল",
        "রাজাকার", "মুক্তিযুদ্ধ", "শহীদ", "বিতর্ক"
    )

    private val viralJunkPatternsEn = arrayOf(
        "like and share", "tag someone", "comment below", "follow for more",
        "wait for it", "gone wrong", "challenge", "prank", "reaction",
        "try not to", "satisfying", "oddly satisfying", "asmr", "compilation",
        "best of", "top 10", "fails", "wins", "epic", "insane", "crazy",
        "unreal", "mind blown", "life hack", "diy", "tutorial", "easy way",
        "simple trick", "doctors hate", "one weird trick"
    )

    private val viralJunkPatternsBn = arrayOf(
        "লাইক দিন", "শেয়ার করুন", "কমেন্ট করুন", "ফলো করুন", "সাবস্ক্রাইব",
        "ভাইরাল", "ট্রেন্ডিং", "মজার ভিডিও", "ফানি", "হাসির", "চ্যালেঞ্জ",
        "প্র্যাংক", "রিয়েকশন", "টিপস", "ট্রিক্স", "হ্যাক"
    )

    private val familyFriendsPatternsEn = arrayOf(
        "family", "birthday", "anniversary", "wedding", "baby", "kids",
        "children", "mom", "dad", "brother", "sister", "grandma", "grandpa",
        "vacation", "trip", "holiday", "christmas", "thanksgiving", "easter",
        "dinner", "lunch", "breakfast", "cooking", "baking", "garden",
        "pet", "dog", "cat", "love you", "miss you", "proud of",
        "congratulations", "blessed", "grateful", "thankful", "boyfriend",
        "girlfriend", "husband", "wife", "partner", "friend", "best friend",
        "together", "memories", "throwback"
    )

    private val familyFriendsPatternsBn = arrayOf(
        "পরিবার", "জন্মদিন", "বিবাহ বার্ষিকী", "বিয়ে", "বাচ্চা", "সন্তান",
        "মা", "বাবা", "ভাই", "বোন", "দাদা", "দাদী", "নানা", "নানী",
        "ছুটি", "ভ্রমণ", "ঈদ", "পূজা", "বড়দিন", "নববর্ষ", "পহেলা বৈশাখ",
        "রান্না", "খাওয়া", "বাগান", "পোষা প্রাণী", "কুকুর", "বিড়াল",
        "ভালোবাসি", "মিস করছি", "গর্বিত", "অভিনন্দন", "ধন্যবাদ",
        "বন্ধু", "বান্ধবী", "স্বামী", "স্ত্রী", "প্রিয়", "স্মৃতি"
    )

    private val newsPatternsEn = arrayOf(
        "breaking news", "just in", "developing", "update", "report",
        "according to", "sources say", "officials", "statement", "announcement",
        "press release", "study shows", "research", "scientists", "experts",
        "analysis", "investigation", "exclusive", "interview", "coverage"
    )

    private val newsPatternsBn = arrayOf(
        "খবর", "সংবাদ", "ব্রেকিং", "আপডেট", "রিপোর্ট", "সূত্রে জানা গেছে",
        "কর্তৃপক্ষ", "বিবৃতি", "ঘোষণা", "প্রেস রিলিজ", "গবেষণা",
        "বিশেষজ্ঞ", "বিশ্লেষণ", "তদন্ত", "সাক্ষাৎকার"
    )

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun analyzePostAsync(
        postText: String,
        hasFollowButton: Boolean,
        hasJoinButton: Boolean,
        isSponsored: Boolean,
        posterName: String
    ): FeedAnalysis {
        val detectedLang = try {
            languageIdentifier.identifyLanguage(postText).await()
        } catch (e: Exception) {
            "und"
        }
        return analyzeWithLanguage(postText, hasFollowButton, hasJoinButton, isSponsored, posterName, detectedLang)
    }

    fun analyzePost(
        postText: String,
        hasFollowButton: Boolean,
        hasJoinButton: Boolean,
        isSponsored: Boolean,
        posterName: String
    ): FeedAnalysis {
        val isBangla = containsBangla(postText)
        val detectedLang = if (isBangla) "bn" else "en"
        return analyzeWithLanguage(postText, hasFollowButton, hasJoinButton, isSponsored, posterName, detectedLang)
    }

    private fun containsBangla(text: String): Boolean {
        for (char in text) {
            if (char.code in 0x0980..0x09FF) {
                return true
            }
        }
        return false
    }

    private fun analyzeWithLanguage(
        postText: String,
        hasFollowButton: Boolean,
        hasJoinButton: Boolean,
        isSponsored: Boolean,
        posterName: String,
        detectedLang: String
    ): FeedAnalysis {
        val lowerText = postText.lowercase()

        if (isSponsored) {
            sessionSponsored.incrementAndGet()
            StatsTracker.incrementSponsoredRemoved()
            return FeedAnalysis(
                category = ContentCategory.SPONSORED,
                confidence = 1.0f,
                isFromFollowing = false,
                isSponsored = true,
                isGroupPost = hasJoinButton,
                detectedLanguage = detectedLang
            )
        }

        val isBangla = detectedLang == "bn" || containsBangla(postText)

        val politicalScore = if (isBangla) {
            calculateScore(postText, politicalKeywordsBn) + calculateScore(lowerText, politicalKeywordsEn) +
            calculatePatternScore(postText, toxicPatternsBn) * 1.5f + calculatePatternScore(lowerText, toxicPatternsEn) * 1.5f
        } else {
            calculateScore(lowerText, politicalKeywordsEn) + calculatePatternScore(lowerText, toxicPatternsEn) * 1.5f
        }

        val viralScore = if (isBangla) {
            calculatePatternScore(postText, viralJunkPatternsBn) + calculatePatternScore(lowerText, viralJunkPatternsEn)
        } else {
            calculatePatternScore(lowerText, viralJunkPatternsEn)
        }

        val familyScore = if (isBangla) {
            calculatePatternScore(postText, familyFriendsPatternsBn) + calculatePatternScore(lowerText, familyFriendsPatternsEn)
        } else {
            calculatePatternScore(lowerText, familyFriendsPatternsEn)
        }

        val newsScore = if (isBangla) {
            calculatePatternScore(postText, newsPatternsBn) + calculatePatternScore(lowerText, newsPatternsEn)
        } else {
            calculatePatternScore(lowerText, newsPatternsEn)
        }

        val isFromFollowing = !hasFollowButton && !hasJoinButton

        var adjustedFamilyScore = familyScore
        var adjustedPoliticalScore = politicalScore
        var adjustedViralScore = viralScore

        if (isFromFollowing) {
            adjustedFamilyScore *= 1.5f
        } else {
            adjustedPoliticalScore *= 1.3f
            adjustedViralScore *= 1.3f
        }

        if (hasJoinButton) {
            adjustedViralScore *= 1.2f
        }

        val maxScore = maxOf(adjustedFamilyScore, adjustedPoliticalScore, adjustedViralScore, newsScore)
        val totalScore = adjustedFamilyScore + adjustedPoliticalScore + adjustedViralScore + newsScore

        val category: ContentCategory
        val confidence: Float

        when {
            maxScore == 0f -> {
                category = if (isFromFollowing) ContentCategory.FAMILY_FRIENDS else ContentCategory.VIRAL_JUNK
                confidence = 0.3f
            }
            maxScore == adjustedFamilyScore -> {
                category = ContentCategory.FAMILY_FRIENDS
                confidence = if (totalScore > 0) adjustedFamilyScore / totalScore else 0.5f
            }
            maxScore == adjustedPoliticalScore -> {
                category = ContentCategory.POLITICAL_TOXIC
                confidence = if (totalScore > 0) adjustedPoliticalScore / totalScore else 0.5f
            }
            maxScore == newsScore -> {
                category = ContentCategory.NEWS_INFO
                confidence = if (totalScore > 0) newsScore / totalScore else 0.5f
            }
            else -> {
                category = ContentCategory.VIRAL_JUNK
                confidence = if (totalScore > 0) adjustedViralScore / totalScore else 0.5f
            }
        }

        when (category) {
            ContentCategory.FAMILY_FRIENDS -> sessionFamilyFriends.incrementAndGet()
            ContentCategory.POLITICAL_TOXIC -> sessionPoliticalToxic.incrementAndGet()
            ContentCategory.VIRAL_JUNK -> sessionViralJunk.incrementAndGet()
            ContentCategory.NEWS_INFO -> sessionNewsInfo.incrementAndGet()
            ContentCategory.SPONSORED -> sessionSponsored.incrementAndGet()
        }

        return FeedAnalysis(
            category = category,
            confidence = confidence.coerceIn(0f, 1f),
            isFromFollowing = isFromFollowing,
            isSponsored = false,
            isGroupPost = hasJoinButton,
            detectedLanguage = detectedLang
        )
    }

    private fun calculateScore(text: String, keywords: Array<String>): Float {
        var score = 0f
        for (keyword in keywords) {
            if (text.contains(keyword)) {
                score += 1f
            }
        }
        return score
    }

    private fun calculatePatternScore(text: String, patterns: Array<String>): Float {
        var score = 0f
        for (pattern in patterns) {
            if (text.contains(pattern)) {
                score += 1.5f
            }
        }
        return score
    }

    fun saveSession() {
        prefs.edit()
            .putInt(KEY_FAMILY_FRIENDS, prefs.getInt(KEY_FAMILY_FRIENDS, 0) + sessionFamilyFriends.get())
            .putInt(KEY_POLITICAL_TOXIC, prefs.getInt(KEY_POLITICAL_TOXIC, 0) + sessionPoliticalToxic.get())
            .putInt(KEY_VIRAL_JUNK, prefs.getInt(KEY_VIRAL_JUNK, 0) + sessionViralJunk.get())
            .putInt(KEY_NEWS_INFO, prefs.getInt(KEY_NEWS_INFO, 0) + sessionNewsInfo.get())
            .putInt(KEY_SPONSORED, prefs.getInt(KEY_SPONSORED, 0) + sessionSponsored.get())
            .putInt(KEY_TOTAL_POSTS, getTotalAnalyzed())
            .apply()

        sessionFamilyFriends.set(0)
        sessionPoliticalToxic.set(0)
        sessionViralJunk.set(0)
        sessionNewsInfo.set(0)
        sessionSponsored.set(0)
    }

    fun getFamilyFriendsCount(): Int {
        return prefs.getInt(KEY_FAMILY_FRIENDS, 0) + sessionFamilyFriends.get()
    }

    fun getPoliticalToxicCount(): Int {
        return prefs.getInt(KEY_POLITICAL_TOXIC, 0) + sessionPoliticalToxic.get()
    }

    fun getViralJunkCount(): Int {
        return prefs.getInt(KEY_VIRAL_JUNK, 0) + sessionViralJunk.get()
    }

    fun getNewsInfoCount(): Int {
        return prefs.getInt(KEY_NEWS_INFO, 0) + sessionNewsInfo.get()
    }

    fun getSponsoredCount(): Int {
        return prefs.getInt(KEY_SPONSORED, 0) + sessionSponsored.get()
    }

    fun getTotalAnalyzed(): Int {
        val stored = prefs.getInt(KEY_TOTAL_POSTS, 0)
        val session = sessionFamilyFriends.get() + sessionPoliticalToxic.get() +
                sessionViralJunk.get() + sessionNewsInfo.get() + sessionSponsored.get()
        return stored + session
    }

    fun getFamilyFriendsPercentage(): Float {
        val total = getTotalAnalyzed()
        return if (total > 0) (getFamilyFriendsCount().toFloat() / total) * 100 else 0f
    }

    fun getPoliticalToxicPercentage(): Float {
        val total = getTotalAnalyzed()
        return if (total > 0) (getPoliticalToxicCount().toFloat() / total) * 100 else 0f
    }

    fun getViralJunkPercentage(): Float {
        val total = getTotalAnalyzed()
        return if (total > 0) (getViralJunkCount().toFloat() / total) * 100 else 0f
    }

    fun getNewsInfoPercentage(): Float {
        val total = getTotalAnalyzed()
        return if (total > 0) (getNewsInfoCount().toFloat() / total) * 100 else 0f
    }

    fun getSponsoredPercentage(): Float {
        val total = getTotalAnalyzed()
        return if (total > 0) (getSponsoredCount().toFloat() / total) * 100 else 0f
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        sessionFamilyFriends.set(0)
        sessionPoliticalToxic.set(0)
        sessionViralJunk.set(0)
        sessionNewsInfo.set(0)
        sessionSponsored.set(0)
    }
}
