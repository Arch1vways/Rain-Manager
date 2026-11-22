package com.rushi.modulesmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GitHubAuthorSource(
    val owner: String,
    val displayName: String = owner,
    val avatarUrl: String = "https://github.com/$owner.png?size=128",
    val moduleCount: Int = 0,
    val lastUpdated: Long = 0L,
    val error: String? = null,
) {
    val urlLabel: String get() = "github.com/$owner"
}

/**
 * Manages Sileo-style GitHub author repositories ("github.com/<author>") and fetches
 * all Xposed/Android APK modules published under that GitHub account or organization.
 */
class GitHubAuthorStore(
    context: Context,
    private val client: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("github_sources", Context.MODE_PRIVATE)
    private val cacheDir = File(context.cacheDir, "author_repos").apply { mkdirs() }

    private val _sources = MutableStateFlow<List<GitHubAuthorSource>>(emptyList())
    val sources: StateFlow<List<GitHubAuthorSource>> = _sources.asStateFlow()

    private val _modules = MutableStateFlow<List<RepoModule>>(emptyList())
    val modules: StateFlow<List<RepoModule>> = _modules.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        loadSavedSources()
    }

    private fun loadSavedSources() {
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!initialized) {
            val defaultSrc = GitHubAuthorSource(owner = DEFAULT_AUTHOR)
            _sources.value = listOf(defaultSrc)
            persistSources(_sources.value)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            _modules.value = emptyList()
            return
        }
        val raw = prefs.getString(KEY_AUTHORS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val list = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val owner = obj.optString("owner").trim()
                if (owner.isEmpty()) continue
                add(
                    GitHubAuthorSource(
                        owner = owner,
                        displayName = obj.optString("displayName", owner),
                        avatarUrl = obj.optString("avatarUrl", "https://github.com/$owner.png?size=128"),
                        moduleCount = obj.optInt("moduleCount", 0),
                        lastUpdated = obj.optLong("lastUpdated", 0L),
                    ),
                )
            }
        }
        _sources.value = list
        _modules.value = list.flatMap { readCachedModules(it.owner) }
    }

    private fun persistSources(list: List<GitHubAuthorSource>) {
        val arr = JSONArray()
        list.forEach { src ->
            arr.put(
                JSONObject()
                    .put("owner", src.owner)
                    .put("displayName", src.displayName)
                    .put("avatarUrl", src.avatarUrl)
                    .put("moduleCount", src.moduleCount)
                    .put("lastUpdated", src.lastUpdated),
            )
        }
        prefs.edit().putString(KEY_AUTHORS, arr.toString()).apply()
    }

    suspend fun addAuthor(rawInput: String): Result<GitHubAuthorSource> {
        val owner = parseGitHubAuthor(rawInput)
            ?: return Result.failure(IllegalArgumentException("Enter github.com/author or author username"))
        val existing = _sources.value.firstOrNull { it.owner.equals(owner, ignoreCase = true) }
        if (existing != null) {
            return refreshAuthor(existing.owner)
        }
        val placeholder = GitHubAuthorSource(owner = owner)
        val result = fetchAuthorModules(owner)
        return result.map { (source, mods) ->
            writeCachedModules(source.owner, mods)
            val updated = (_sources.value.filterNot { it.owner.equals(source.owner, ignoreCase = true) } + source)
            _sources.value = updated
            persistSources(updated)
            rebuildMergedModules()
            source
        }.onFailure {
            // If initial add failed, don't leave a broken ghost source unless user retries.
            placeholder.hashCode()
        }
    }

    suspend fun refreshAuthor(owner: String): Result<GitHubAuthorSource> {
        return fetchAuthorModules(owner).onSuccess { (source, mods) ->
            writeCachedModules(source.owner, mods)
            val updated = _sources.value.map {
                if (it.owner.equals(source.owner, ignoreCase = true)) source else it
            }.ifEmpty { listOf(source) }
            _sources.value = updated
            persistSources(updated)
            rebuildMergedModules()
        }.onFailure { err ->
            _sources.value = _sources.value.map {
                if (it.owner.equals(owner, ignoreCase = true)) it.copy(error = err.message) else it
            }
        }.map { it.first }
    }

    suspend fun refreshAll() {
        val current = _sources.value
        if (current.isEmpty()) return
        _refreshing.value = true
        try {
            coroutineScope {
                current.map { src -> async { refreshAuthor(src.owner) } }.awaitAll()
            }
        } finally {
            _refreshing.value = false
        }
    }

    fun removeAuthor(owner: String) {
        val updated = _sources.value.filterNot { it.owner.equals(owner, ignoreCase = true) }
        _sources.value = updated
        persistSources(updated)
        File(cacheDir, "${owner.lowercase()}.json").delete()
        rebuildMergedModules()
    }

    private fun rebuildMergedModules() {
        _modules.value = _sources.value.flatMap { readCachedModules(it.owner) }
    }

    private suspend fun fetchAuthorModules(owner: String): Result<Pair<GitHubAuthorSource, List<RepoModule>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reposJson = fetchReposArray(owner)
                val semaphore = Semaphore(4)
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until reposJson.length()) {
                    val repo = reposJson.optJSONObject(i) ?: continue
                    if (repo.optBoolean("archived", false)) continue
                    candidates.add(repo)
                }
                val modules = coroutineScope {
                    candidates.map { repoObj ->
                        async {
                            semaphore.withPermit {
                                buildModuleFromRepo(owner, repoObj)
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                val resolvedOwner = candidates.firstOrNull()
                    ?.optJSONObject("owner")
                    ?.optString("login")
                    ?.takeIf { it.isNotBlank() }
                    ?: owner
                val avatar = candidates.firstOrNull()
                    ?.optJSONObject("owner")
                    ?.optString("avatar_url")
                    ?.takeIf { it.isNotBlank() }
                    ?: "https://github.com/$resolvedOwner.png?size=128"
                val src = GitHubAuthorSource(
                    owner = resolvedOwner,
                    displayName = resolvedOwner,
                    avatarUrl = avatar,
                    moduleCount = modules.size,
                    lastUpdated = System.currentTimeMillis(),
                    error = null,
                )
                src to modules
            }
        }

    private fun fetchReposArray(owner: String): JSONArray {
        val userUrl = "https://api.github.com/users/$owner/repos?per_page=100&sort=updated"
        val userResp = getString(userUrl)
        if (userResp != null) return JSONArray(userResp)
        val orgUrl = "https://api.github.com/orgs/$owner/repos?per_page=100&sort=updated"
        val orgResp = getString(orgUrl)
        if (orgResp != null) return JSONArray(orgResp)
        val webRepos = fetchReposFromWeb(owner)
        if (webRepos.length() > 0) return webRepos
        throw IllegalStateException("GitHub user or organization \"$owner\" not found")
    }

    private fun fetchReposFromWeb(owner: String): JSONArray {
        val html = getHtml("https://github.com/$owner?tab=repositories")
            ?: getHtml("https://github.com/orgs/$owner/repositories")
            ?: return JSONArray()
        val pattern = Regex("""href="/${Regex.escape(owner)}/([A-Za-z0-9_.-]+)"\s+itemprop="name codeRepository"""", RegexOption.IGNORE_CASE)
        val orgPattern = Regex("""href="/${Regex.escape(owner)}/([A-Za-z0-9_.-]+)"[^>]*data-hovercard-type="repository"""", RegexOption.IGNORE_CASE)
        val names = LinkedHashSet<String>()
        pattern.findAll(html).forEach { names.add(it.groupValues[1]) }
        if (names.isEmpty()) orgPattern.findAll(html).forEach { names.add(it.groupValues[1]) }
        val arr = JSONArray()
        for (repo in names) {
            arr.put(
                JSONObject()
                    .put("name", repo)
                    .put("full_name", "$owner/$repo")
                    .put("html_url", "https://github.com/$owner/$repo")
                    .put("owner", JSONObject().put("login", owner)),
            )
        }
        return arr
    }

    private fun getHtml(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) LSPosed-Modules-Manager")
            .header("Accept", "text/html,application/xhtml+xml,application/atom+xml")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            res.body?.string()
        }
    }.getOrNull()

    private fun buildModuleFromRepo(owner: String, repoObj: JSONObject): RepoModule? {
        val repoName = repoObj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val fullName = repoObj.optString("full_name", "$owner/$repoName")
        val releasesRaw = getString("https://api.github.com/repos/$fullName/releases?per_page=5")
        val parsedReleases = if (releasesRaw != null) {
            parseApiReleases(releasesRaw)
        } else {
            fetchReleasesFromAtom(fullName)
        }
        if (parsedReleases.isEmpty()) return null

        val stable = parsedReleases.filterNot { it.isPrerelease || it.isSnapshot }
        val betas = parsedReleases.filter { it.isPrerelease && !it.isSnapshot }
        val snapshots = parsedReleases.filter { it.isSnapshot }
        val latestStable = stable.firstOrNull()
        val latestBeta = betas.firstOrNull()
        val latestSnap = snapshots.firstOrNull()

        val readme = fetchRawText(fullName, "README.md") ?: fetchRawText(fullName, "readme.md")
        val pkgName = detectPackageName(fullName) ?: "github.${owner.lowercase()}.${repoName.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
        val htmlUrl = repoObj.optString("html_url", "https://github.com/$fullName")
        val description = repoObj.optString("description").takeIf { it.isNotBlank() && it != "null" }
            ?: extractSummaryFromReadme(readme)
        val actualOwner = repoObj.optJSONObject("owner")?.optString("login")?.takeIf { it.isNotBlank() } ?: owner

        return RepoModule(
            packageName = pkgName,
            displayName = repoName.replace('-', ' '),
            summary = description,
            url = htmlUrl,
            homepageUrl = repoObj.optString("homepage").takeIf { it.isNotBlank() && it != "null" },
            sourceUrl = htmlUrl,
            latestRelease = latestStable?.tagName ?: latestStable?.name,
            latestReleaseTime = latestStable?.publishedAt,
            latestBetaRelease = latestBeta?.tagName ?: latestBeta?.name,
            latestBetaReleaseTime = latestBeta?.publishedAt,
            latestSnapshotRelease = latestSnap?.tagName ?: latestSnap?.name,
            latestSnapshotReleaseTime = latestSnap?.publishedAt,
            releases = stable,
            betaReleases = betas,
            snapshotReleases = snapshots,
            readme = readme,
            readmeHtml = null,
            collaborators = listOf(RepoCollaborator(login = actualOwner, name = null)),
            scope = emptyList(),
            hide = false,
            updatedAt = repoObj.optString("updated_at").takeIf { it.isNotBlank() },
            stargazerCount = repoObj.optInt("stargazers_count", 0),
            forkCount = repoObj.optInt("forks_count", 0),
            addedAt = repoObj.optString("created_at").takeIf { it.isNotBlank() },
            repoFullName = fullName,
            releaseHistory = parsedReleases,
            apiVersion = null,
        )
    }

    private fun parseApiReleases(releasesRaw: String): List<RepoRelease> {
        val releasesArr = runCatching { JSONArray(releasesRaw) }.getOrNull() ?: return emptyList()
        val parsedReleases = mutableListOf<RepoRelease>()
        for (i in 0 until releasesArr.length()) {
            val relObj = releasesArr.optJSONObject(i) ?: continue
            val assetsArr = relObj.optJSONArray("assets") ?: continue
            val assets = mutableListOf<RepoAsset>()
            for (j in 0 until assetsArr.length()) {
                val a = assetsArr.optJSONObject(j) ?: continue
                val assetName = a.optString("name")
                val dl = a.optString("browser_download_url")
                if (assetName.isBlank() || dl.isBlank()) continue
                assets.add(
                    RepoAsset(
                        name = assetName,
                        contentType = a.optString("content_type").takeIf { it.isNotBlank() },
                        downloadUrl = dl,
                        downloadCount = a.optInt("download_count", 0),
                        size = a.optLong("size", 0L),
                        originalDownloadUrl = dl,
                    ),
                )
            }
            if (assets.none { it.name.endsWith(".apk", ignoreCase = true) }) continue
            parsedReleases.add(
                RepoRelease(
                    name = relObj.optString("name").takeIf { it.isNotBlank() },
                    tagName = relObj.optString("tag_name").takeIf { it.isNotBlank() },
                    url = relObj.optString("html_url").takeIf { it.isNotBlank() },
                    descriptionHtml = relObj.optString("body").takeIf { it.isNotBlank() },
                    publishedAt = relObj.optString("published_at").takeIf { it.isNotBlank() },
                    isPrerelease = relObj.optBoolean("prerelease", false),
                    assets = assets,
                ),
            )
        }
        return parsedReleases
    }

    private fun fetchReleasesFromAtom(fullName: String): List<RepoRelease> {
        val atom = getHtml("https://github.com/$fullName/releases.atom") ?: return emptyList()
        val entryRegex = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
        val linkRegex = Regex("""href="https://github\.com/[^"]+/releases/tag/([^"]+)"""")
        val titleRegex = Regex("""<title>([^<]+)</title>""")
        val updatedRegex = Regex("""<updated>([^<]+)</updated>""")
        val apkRegex = Regex("""href="(/${Regex.escape(fullName)}/releases/download/[^"]+\.apk)"""", RegexOption.IGNORE_CASE)

        val out = mutableListOf<RepoRelease>()
        for (match in entryRegex.findAll(atom).take(3)) {
            val block = match.groupValues[1]
            val tag = linkRegex.find(block)?.groupValues?.getOrNull(1) ?: continue
            val title = titleRegex.find(block)?.groupValues?.getOrNull(1) ?: tag
            val updated = updatedRegex.find(block)?.groupValues?.getOrNull(1)
            val assetsHtml = getHtml("https://github.com/$fullName/releases/expanded_assets/$tag") ?: continue
            val assets = apkRegex.findAll(assetsHtml).map { m ->
                val relPath = m.groupValues[1]
                val name = relPath.substringAfterLast('/')
                val url = "https://github.com$relPath"
                RepoAsset(name = name, contentType = "application/vnd.android.package-archive", downloadUrl = url, downloadCount = 0, size = 0L, originalDownloadUrl = url)
            }.distinctBy { it.downloadUrl }.toList()
            if (assets.isEmpty()) continue
            out.add(
                RepoRelease(
                    name = title,
                    tagName = tag,
                    url = "https://github.com/$fullName/releases/tag/$tag",
                    descriptionHtml = null,
                    publishedAt = updated,
                    isPrerelease = tag.contains("beta", true) || tag.contains("alpha", true),
                    assets = assets,
                ),
            )
        }
        return out
    }

    private fun detectPackageName(fullName: String): String? {
        for (gradlePath in listOf("app/build.gradle.kts", "app/build.gradle")) {
            val text = fetchRawText(fullName, gradlePath) ?: continue
            APP_ID_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { return it }
            NAMESPACE_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        }
        val manifest = fetchRawText(fullName, "app/src/main/AndroidManifest.xml")
        if (manifest != null) {
            MANIFEST_PKG_REGEX.find(manifest)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun fetchRawText(fullName: String, path: String): String? =
        getString("https://raw.githubusercontent.com/$fullName/HEAD/$path")

    private fun extractSummaryFromReadme(readme: String?): String? {
        if (readme.isNullOrBlank()) return null
        return readme.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !line.startsWith("!") &&
                    !line.startsWith("<") &&
                    !line.startsWith("```") &&
                    !line.startsWith("---")
            }
            ?.take(180)
    }

    private fun getString(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            res.body?.string()
        }
    }.getOrNull()

    private fun writeCachedModules(owner: String, modules: List<RepoModule>) {
        val arr = JSONArray()
        modules.forEach { m -> arr.put(moduleToJson(m)) }
        File(cacheDir, "${owner.lowercase()}.json").writeText(arr.toString())
    }

    private fun readCachedModules(owner: String): List<RepoModule> {
        val file = File(cacheDir, "${owner.lowercase()}.json")
        if (!file.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(moduleFromJson(it)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun moduleToJson(m: RepoModule): JSONObject {
        fun relArray(list: List<RepoRelease>): JSONArray = JSONArray().apply {
            list.forEach { r ->
                put(
                    JSONObject()
                        .put("name", r.name)
                        .put("tagName", r.tagName)
                        .put("url", r.url)
                        .put("descriptionHtml", r.descriptionHtml)
                        .put("publishedAt", r.publishedAt)
                        .put("isPrerelease", r.isPrerelease)
                        .put(
                            "assets",
                            JSONArray().apply {
                                r.assets.forEach { a ->
                                    put(
                                        JSONObject()
                                            .put("name", a.name)
                                            .put("contentType", a.contentType)
                                            .put("downloadUrl", a.downloadUrl)
                                            .put("downloadCount", a.downloadCount)
                                            .put("size", a.size)
                                            .put("originalDownloadUrl", a.originalDownloadUrl),
                                    )
                                }
                            },
                        ),
                )
            }
        }
        return JSONObject()
            .put("packageName", m.packageName)
            .put("displayName", m.displayName)
            .put("summary", m.summary)
            .put("url", m.url)
            .put("homepageUrl", m.homepageUrl)
            .put("sourceUrl", m.sourceUrl)
            .put("latestRelease", m.latestRelease)
            .put("latestReleaseTime", m.latestReleaseTime)
            .put("latestBetaRelease", m.latestBetaRelease)
            .put("latestBetaReleaseTime", m.latestBetaReleaseTime)
            .put("latestSnapshotRelease", m.latestSnapshotRelease)
            .put("latestSnapshotReleaseTime", m.latestSnapshotReleaseTime)
            .put("releases", relArray(m.releases))
            .put("betaReleases", relArray(m.betaReleases))
            .put("snapshotReleases", relArray(m.snapshotReleases))
            .put("readme", m.readme)
            .put("updatedAt", m.updatedAt)
            .put("stargazerCount", m.stargazerCount)
            .put("forkCount", m.forkCount)
            .put("addedAt", m.addedAt)
            .put("repoFullName", m.repoFullName)
            .put("owner", m.collaborators.firstOrNull()?.login)
    }

    private fun moduleFromJson(obj: JSONObject): RepoModule {
        fun parseRels(arr: JSONArray?): List<RepoRelease> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val aArr = r.optJSONArray("assets")
                val assets = buildList {
                    if (aArr != null) {
                        for (j in 0 until aArr.length()) {
                            val a = aArr.optJSONObject(j) ?: continue
                            add(
                                RepoAsset(
                                    name = a.optString("name"),
                                    contentType = a.optString("contentType").takeIf { it.isNotBlank() && it != "null" },
                                    downloadUrl = a.optString("downloadUrl"),
                                    downloadCount = a.optInt("downloadCount", 0),
                                    size = a.optLong("size", 0L),
                                    originalDownloadUrl = a.optString("originalDownloadUrl").takeIf { it.isNotBlank() && it != "null" },
                                ),
                            )
                        }
                    }
                }
                add(
                    RepoRelease(
                        name = r.optString("name").takeIf { it.isNotBlank() && it != "null" },
                        tagName = r.optString("tagName").takeIf { it.isNotBlank() && it != "null" },
                        url = r.optString("url").takeIf { it.isNotBlank() && it != "null" },
                        descriptionHtml = r.optString("descriptionHtml").takeIf { it.isNotBlank() && it != "null" },
                        publishedAt = r.optString("publishedAt").takeIf { it.isNotBlank() && it != "null" },
                        isPrerelease = r.optBoolean("isPrerelease", false),
                        assets = assets,
                    ),
                )
            }
        }
        val stable = parseRels(obj.optJSONArray("releases"))
        val beta = parseRels(obj.optJSONArray("betaReleases"))
        val snap = parseRels(obj.optJSONArray("snapshotReleases"))
        val owner = obj.optString("owner").takeIf { it.isNotBlank() && it != "null" }
        return RepoModule(
            packageName = obj.optString("packageName"),
            displayName = obj.optString("displayName"),
            summary = obj.optString("summary").takeIf { it.isNotBlank() && it != "null" },
            url = obj.optString("url").takeIf { it.isNotBlank() && it != "null" },
            homepageUrl = obj.optString("homepageUrl").takeIf { it.isNotBlank() && it != "null" },
            sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" },
            latestRelease = obj.optString("latestRelease").takeIf { it.isNotBlank() && it != "null" },
            latestReleaseTime = obj.optString("latestReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            latestBetaRelease = obj.optString("latestBetaRelease").takeIf { it.isNotBlank() && it != "null" },
            latestBetaReleaseTime = obj.optString("latestBetaReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            latestSnapshotRelease = obj.optString("latestSnapshotRelease").takeIf { it.isNotBlank() && it != "null" },
            latestSnapshotReleaseTime = obj.optString("latestSnapshotReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            releases = stable,
            betaReleases = beta,
            snapshotReleases = snap,
            readme = obj.optString("readme").takeIf { it.isNotBlank() && it != "null" },
            readmeHtml = null,
            collaborators = if (owner != null) listOf(RepoCollaborator(owner, null)) else emptyList(),
            scope = emptyList(),
            hide = false,
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" },
            stargazerCount = obj.optInt("stargazerCount", 0),
            forkCount = obj.optInt("forkCount", 0),
            addedAt = obj.optString("addedAt").takeIf { it.isNotBlank() && it != "null" },
            repoFullName = obj.optString("repoFullName").takeIf { it.isNotBlank() && it != "null" },
            releaseHistory = stable + beta + snap,
            apiVersion = null,
        )
    }

    companion object {
        private const val KEY_AUTHORS = "github_authors"
        private const val KEY_INITIALIZED = "github_authors_initialized"
        private const val DEFAULT_AUTHOR = "Arch1vways"
        private const val USER_AGENT = "LSPosed-Modules-Manager"
        private val APP_ID_REGEX = Regex("""applicationId\s*=?\s*["']([^"']+)["']""")
        private val NAMESPACE_REGEX = Regex("""namespace\s*=?\s*["']([^"']+)["']""")
        private val MANIFEST_PKG_REGEX = Regex("""package\s*=\s*["']([^"']+)["']""")

        fun parseGitHubAuthor(raw: String): String? {
            var s = raw.trim()
            if (s.isEmpty()) return null
            if (s.startsWith("rainmanager://", ignoreCase = true)) {
                s = s.substring("rainmanager://".length)
            } else if (s.startsWith("rainmanager//:", ignoreCase = true)) {
                s = s.substring("rainmanager//:".length)
            } else if (s.startsWith("rainmanager:", ignoreCase = true)) {
                s = s.substring("rainmanager:".length).trimStart('/', ':')
            }
            if (s.startsWith("add/", ignoreCase = true)) {
                s = s.substring("add/".length)
            } else if (s.startsWith("repo/", ignoreCase = true)) {
                s = s.substring("repo/".length)
            } else if (s.startsWith("source/", ignoreCase = true)) {
                s = s.substring("source/".length)
            }
            s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            if (s.startsWith("github.com/", ignoreCase = true)) {
                s = s.substring("github.com/".length)
            }
            s = s.removePrefix("@").trim('/')
            val segment = s.substringBefore('/').substringBefore('?').substringBefore('#').trim()
            if (segment.isEmpty() || !segment.matches(Regex("[A-Za-z0-9-]+"))) return null
            return segment
        }
    }
}
