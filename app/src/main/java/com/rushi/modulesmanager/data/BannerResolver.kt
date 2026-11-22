package com.rushi.modulesmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a module's Sileo-style header banner from its GitHub repository or README.
 * Returns null if the repository has no banner (in which case UI falls back to the icon accent color).
 */
class BannerResolver(
    context: Context,
    private val client: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("banner_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, String>()

    suspend fun resolveBannerUrl(module: RepoModule): String? {
        val repo = module.githubRepoFullName() ?: return null
        memory[repo]?.let { return it.takeIf { url -> url != NONE } }
        val cached = prefs.getString(repo, null)
        if (cached != null) {
            memory[repo] = cached
            return cached.takeIf { it != NONE }
        }

        val resolved = withContext(Dispatchers.IO) {
            findBannerInRepo(repo, module.readme, module.readmeHtml)
        }
        val stored = resolved ?: NONE
        memory[repo] = stored
        prefs.edit().putString(repo, stored).apply()
        return resolved
    }

    fun invalidate(repoFullName: String) {
        memory.remove(repoFullName)
        prefs.edit().remove(repoFullName).apply()
    }

    private fun findBannerInRepo(repo: String, readme: String?, readmeHtml: String?): String? {
        // 1. Check standard banner paths directly on raw.githubusercontent.com.
        for (path in CANDIDATE_PATHS) {
            val rawUrl = "https://raw.githubusercontent.com/$repo/HEAD/$path"
            if (headExists(rawUrl)) return rawUrl
        }
        // 2. Fallback: look for a top banner image in README markdown/html.
        extractReadmeBanner(repo, readme, readmeHtml)?.let { return it }
        return null
    }

    private fun headExists(url: String): Boolean = runCatching {
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use false
            val type = res.header("Content-Type").orEmpty()
            type.isEmpty() || type.startsWith("image/", ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun extractReadmeBanner(repo: String, readme: String?, readmeHtml: String?): String? {
        val candidates = mutableListOf<String>()
        if (!readme.isNullOrBlank()) {
            MD_IMG.findAll(readme).take(5).forEach { candidates.add(it.groupValues[1].trim()) }
            HTML_IMG.findAll(readme).take(5).forEach { candidates.add(it.groupValues[1].trim()) }
        }
        if (candidates.isEmpty() && !readmeHtml.isNullOrBlank()) {
            HTML_IMG.findAll(readmeHtml).take(5).forEach { candidates.add(it.groupValues[1].trim()) }
        }
        for (raw in candidates) {
            val url = normalizeImageUrl(repo, raw) ?: continue
            val lower = url.lowercase()
            if (IGNORED_HOSTS.any { lower.contains(it) }) continue
            if (lower.endsWith(".svg") || lower.contains("badge")) continue
            if (lower.contains("banner") || lower.contains("header") || lower.contains("cover") || lower.contains("hero")) {
                return url
            }
        }
        return null
    }

    private fun normalizeImageUrl(repo: String, raw: String): String? {
        val clean = raw.trim().trim('"', '\'', '<', '>')
        if (clean.isEmpty()) return null
        if (clean.startsWith("https://") || clean.startsWith("http://")) {
            return clean
                .replace("https://github.com/$repo/blob/", "https://raw.githubusercontent.com/$repo/")
                .replace("https://github.com/$repo/raw/", "https://raw.githubusercontent.com/$repo/")
        }
        val rel = clean.removePrefix("./").removePrefix("/")
        return "https://raw.githubusercontent.com/$repo/HEAD/$rel"
    }

    private companion object {
        const val NONE = "__NONE__"
        const val USER_AGENT = "LSPosed-Modules-Manager"
        val CANDIDATE_PATHS = listOf(
            "design/banner.png",
            "design/banner.jpg",
            "design/banner.webp",
            "banner.png",
            "banner.jpg",
            "banner.webp",
            ".github/banner.png",
            "art/banner.png",
            "assets/banner.png",
        )
        val IGNORED_HOSTS = listOf("shields.io", "badgen.net", "img.shields.io", "t.me/")
        val MD_IMG = Regex("""!\[[^]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
        val HTML_IMG = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
