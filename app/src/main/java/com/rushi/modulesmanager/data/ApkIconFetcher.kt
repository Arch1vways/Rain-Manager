package com.rushi.modulesmanager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.PathParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

/**
 * Loads app icons directly into memory (without saving icon files to disk) from
 * installed packages or via HTTP Range requests against the remote APK's ZIP entry.
 */
class ApkIconFetcher(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val semaphore = Semaphore(10)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()
    private val memoryCache = object : LruCache<String, ImageBitmap>(96) {}
    private val noIconCache = ConcurrentHashMap.newKeySet<String>()
    private val accentCache = ConcurrentHashMap<String, Int>()

    private fun cacheKey(packageName: String, versionKey: String?): String =
        "$packageName|${versionKey.orEmpty()}"

    fun getCachedIconIfPresent(packageName: String, versionKey: String?): ImageBitmap? =
        memoryCache.get(cacheKey(packageName, versionKey))

    fun getAccentColor(packageName: String): Int? = accentCache[packageName]

    suspend fun loadIconBitmap(module: RepoModule, channel: UpdateChannel): ImageBitmap? {
        val release = module.releaseFor(channel) ?: module.releases.firstOrNull()
        val versionKey = release?.tagName ?: module.latestRawFor(channel == UpdateChannel.PRE_RELEASE)
        val key = cacheKey(module.packageName, versionKey)

        memoryCache.get(key)?.let { return it }
        if (noIconCache.contains(key)) return null

        // Fast path: if already installed on device, read directly from PackageManager in memory.
        installedIconBitmap(module.packageName)?.let { bmp ->
            memoryCache.put(key, bmp)
            return bmp
        }

        val asset = release?.preferredAsset(module.packageName)
        val repoFullName = module.githubRepoFullName()
        if (asset == null && repoFullName == null) return null
        val existing = inFlight[key]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<ImageBitmap?>()
        val prev = inFlight.putIfAbsent(key, deferred)
        if (prev != null) return prev.await()

        return try {
            val result = semaphore.withPermit {
                withContext(Dispatchers.IO) {
                    (if (asset != null) extractRemoteIcon(module.packageName, asset) else null)
                        ?: repoFullName?.let { fetchIconFromGitHubRepo(module.packageName, it) }
                }
            }
            if (result != null) {
                memoryCache.put(key, result)
            } else {
                noIconCache.add(key)
            }
            deferred.complete(result)
            result
        } catch (t: Throwable) {
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun installedIconBitmap(packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, ICON_SIZE_PX)
                extractAccentColor(bitmap)?.let { accentCache[packageName] = it }
                bitmap.asImageBitmap()
            }.getOrNull()
        }

    private fun extractRemoteIcon(packageName: String, asset: RepoAsset): ImageBitmap? {
        val urls = listOfNotNull(asset.downloadUrl, asset.originalDownloadUrl).distinct()
        for (url in urls) {
            extractIconViaRange(packageName, url)?.let { return it }
        }
        return null
    }

    private fun fetchIconFromGitHubRepo(packageName: String, repo: String): ImageBitmap? {
        val (folderType, iconName) = resolveManifestIconRef(repo) ?: return null

        // 1. Check adaptive-icon / vector XML directly for <name>.xml in mipmap-anydpi* or drawable/.
        val xmlCandidates = listOf(
            "app/src/main/res/$folderType-anydpi-v26/$iconName.xml",
            "app/src/main/res/$folderType-anydpi/$iconName.xml",
            "app/src/main/res/drawable/$iconName.xml",
        ).distinct()
        for (xmlPath in xmlCandidates) {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$xmlPath") ?: continue
            val xml = String(bytes, Charsets.UTF_8)
            if (xml.contains("<adaptive-icon")) {
                renderAdaptiveIconXml(packageName, repo, xml)?.let { return it }
            } else if (xml.contains("<vector")) {
                renderVectorXml(packageName, xml, 0xFF232730.toInt())?.let { return it }
            }
        }

        // 2. Raster icon matching the manifest icon name (only most common densities for speed).
        val manifestRaster = listOf(
            "app/src/main/res/$folderType-xxxhdpi/$iconName.png",
            "app/src/main/res/$folderType-xxhdpi/$iconName.png",
            "app/src/main/res/$folderType-xhdpi/$iconName.png",
            "app/src/main/res/$folderType-xxxhdpi/$iconName.webp",
            "app/src/main/res/$folderType-xxhdpi/$iconName.webp",
        )
        for (path in manifestRaster) {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$path") ?: continue
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val result = if (path.contains("foreground")) {
                compositeAdaptiveForeground(decoded, resolveAdaptiveBgColor(repo))
            } else {
                expandToFullSquare(decoded)
            }
            extractAccentColor(result)?.let { accentCache[packageName] = it }
            return result.asImageBitmap()
        }
        return null
    }

    private fun resolveManifestIconRef(repo: String): Pair<String, String>? {
        val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/app/src/main/AndroidManifest.xml")
            ?: return null
        val xml = String(bytes, Charsets.UTF_8)
        val match = MANIFEST_ICON_REGEX.find(xml) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun renderAdaptiveIconXml(packageName: String, repo: String, adaptiveXml: String): ImageBitmap? {
        val bgRef = ADAPTIVE_BG_REGEX.find(adaptiveXml)
        val fgRef = ADAPTIVE_FG_REGEX.find(adaptiveXml) ?: return null

        val bgColor = if (bgRef != null) {
            resolveColorOrDrawableBg(repo, bgRef.groupValues[1], bgRef.groupValues[2])
        } else {
            resolveAdaptiveBgColor(repo)
        }
        val fgType = fgRef.groupValues[1]
        val fgName = fgRef.groupValues[2]

        // Try vector foreground XML first (e.g. drawable/<fgName>.xml)
        for (fgXmlPath in listOf(
            "app/src/main/res/$fgType/$fgName.xml",
            "app/src/main/res/$fgType-anydpi/$fgName.xml",
            "app/src/main/res/$fgType-anydpi-v26/$fgName.xml",
        )) {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$fgXmlPath") ?: continue
            val xml = String(bytes, Charsets.UTF_8)
            if (xml.contains("<vector")) {
                renderVectorXml(packageName, xml, bgColor)?.let { return it }
            }
        }

        // Try raster foreground (e.g. drawable-nodpi/<fgName>.png, mipmap-xxxhdpi/<fgName>.webp)
        for (fgImgPath in listOf(
            "app/src/main/res/$fgType-nodpi/$fgName.png",
            "app/src/main/res/$fgType-xxxhdpi/$fgName.png",
            "app/src/main/res/$fgType-xxhdpi/$fgName.png",
            "app/src/main/res/$fgType-xhdpi/$fgName.png",
            "app/src/main/res/$fgType-xxxhdpi/$fgName.webp",
            "app/src/main/res/$fgType-xxhdpi/$fgName.webp",
            "app/src/main/res/$fgType/$fgName.png",
        )) {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$fgImgPath") ?: continue
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val result = compositeAdaptiveForeground(decoded, bgColor)
            extractAccentColor(result)?.let { accentCache[packageName] = it }
            return result.asImageBitmap()
        }
        return null
    }

    private fun resolveColorOrDrawableBg(repo: String, refType: String, refName: String): Int {
        if (refType == "color") {
            for (path in listOf(
                "app/src/main/res/values/$refName.xml",
                "app/src/main/res/values/colors.xml",
                "app/src/main/res/values/ic_launcher_background.xml",
            )) {
                val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$path") ?: continue
                val text = String(bytes, Charsets.UTF_8)
                val named = Regex("""name\s*=\s*"${Regex.escape(refName)}"[^>]*>\s*(#[0-9A-Fa-f]{6,8})""")
                    .find(text)?.groupValues?.getOrNull(1)
                parseHexColor(named)?.let { return it }
            }
        } else if (refType == "drawable") {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/app/src/main/res/drawable/$refName.xml")
            if (bytes != null) {
                val text = String(bytes, Charsets.UTF_8)
                val hex = HEX_COLOR.find(text)?.groupValues?.getOrNull(1)
                parseHexColor(hex)?.let { return it }
            }
        }
        return resolveAdaptiveBgColor(repo)
    }

    /** Crops transparent rounded-corner padding so icons fill the app's icon shape edge-to-edge. */
    private fun expandToFullSquare(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        var working = src
        if (w >= 24 && h >= 24) {
            val tl = (src.getPixel(0, 0) ushr 24) and 0xFF
            if (tl < 200) {
                var inset = 0
                val maxInset = minOf(w, h) / 3
                for (k in 1 until maxInset) {
                    val a1 = (src.getPixel(k, k) ushr 24) and 0xFF
                    val a2 = (src.getPixel(w - 1 - k, k) ushr 24) and 0xFF
                    val a3 = (src.getPixel(k, h - 1 - k) ushr 24) and 0xFF
                    val a4 = (src.getPixel(w - 1 - k, h - 1 - k) ushr 24) and 0xFF
                    if (a1 >= 240 && a2 >= 240 && a3 >= 240 && a4 >= 240) {
                        inset = k
                        break
                    }
                }
                if (inset in 1 until maxInset) {
                    working = Bitmap.createBitmap(src, inset, inset, w - inset * 2, h - inset * 2)
                    src.recycle()
                }
            }
        }
        return if (working.width != ICON_SIZE_PX || working.height != ICON_SIZE_PX) {
            Bitmap.createScaledBitmap(working, ICON_SIZE_PX, ICON_SIZE_PX, true).also {
                if (it !== working) working.recycle()
            }
        } else {
            working
        }
    }

    private fun compositeAdaptiveForeground(fg: Bitmap, bgColor: Int): Bitmap {
        val out = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bgColor)
        val pad = ICON_SIZE_PX * 0.25f
        val dst = android.graphics.RectF(-pad, -pad, ICON_SIZE_PX + pad, ICON_SIZE_PX + pad)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(fg, null, dst, paint)
        fg.recycle()
        return out
    }

    private fun resolveAdaptiveBgColor(repo: String): Int {
        for (bgPath in GITHUB_BG_PATHS) {
            val bytes = getBytes("https://raw.githubusercontent.com/$repo/HEAD/$bgPath") ?: continue
            val text = String(bytes, Charsets.UTF_8)
            val hex = HEX_COLOR.find(text)?.groupValues?.getOrNull(1)
            parseHexColor(hex)?.let { return it }
        }
        return 0xFF232730.toInt()
    }

    private fun renderVectorXml(packageName: String, xml: String, bgColor: Int): ImageBitmap? = runCatching {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())

        var vw = 108f
        var vh = 108f
        var foundVector = false
        val bmp = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        var drewAny = false

        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "vector" -> {
                            foundVector = true
                            vw = attrFloat(parser, "android:viewportWidth", 108f)
                            vh = attrFloat(parser, "android:viewportHeight", 108f)
                            canvas.save()
                            // Android adaptive icons place the 72dp visible icon inside a 108dp canvas
                            // ([18..90]), so scale 1.5x and crop 25% on each edge to match real launcher size.
                            val pad = ICON_SIZE_PX * 0.25f
                            val scale = (ICON_SIZE_PX * 1.5f) / vw
                            canvas.translate(-pad, -pad * (vh / vw))
                            canvas.scale(scale, scale)
                        }
                        "group" -> {
                            canvas.save()
                            val tx = attrFloat(parser, "android:translateX", 0f)
                            val ty = attrFloat(parser, "android:translateY", 0f)
                            val px = attrFloat(parser, "android:pivotX", 0f)
                            val py = attrFloat(parser, "android:pivotY", 0f)
                            val rot = attrFloat(parser, "android:rotation", 0f)
                            val sx = attrFloat(parser, "android:scaleX", 1f)
                            val sy = attrFloat(parser, "android:scaleY", 1f)
                            canvas.translate(tx + px, ty + py)
                            if (rot != 0f) canvas.rotate(rot)
                            if (sx != 1f || sy != 1f) canvas.scale(sx, sy)
                            canvas.translate(-px, -py)
                        }
                        "clip-path" -> {
                            val d = parser.getAttributeValue(null, "android:pathData")
                            if (!d.isNullOrBlank()) {
                                PathParser.createPathFromPathData(d)?.let { canvas.clipPath(it) }
                            }
                        }
                        "path" -> {
                            val d = parser.getAttributeValue(null, "android:pathData")
                            if (!d.isNullOrBlank()) {
                                val path = PathParser.createPathFromPathData(d)
                                if (path != null) {
                                    val fillHex = parser.getAttributeValue(null, "android:fillColor")
                                    val alpha = attrFloat(parser, "android:fillAlpha", 1f).coerceIn(0f, 1f)
                                    val baseColor = parseHexColor(fillHex) ?: 0xFFFFFFFF.toInt()
                                    paint.color = baseColor
                                    if (alpha < 1f) {
                                        paint.alpha = (android.graphics.Color.alpha(baseColor) * alpha).toInt()
                                    }
                                    canvas.drawPath(path, paint)
                                    drewAny = true
                                }
                            }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "group" || parser.name == "vector") {
                        if (canvas.saveCount > 1) canvas.restore()
                    }
                }
            }
            event = parser.next()
        }
        if (!foundVector || !drewAny) {
            bmp.recycle()
            return null
        }
        extractAccentColor(bmp)?.let { accentCache[packageName] = it }
        bmp.asImageBitmap()
    }.getOrNull()

    private fun attrFloat(parser: org.xmlpull.v1.XmlPullParser, name: String, default: Float): Float =
        parser.getAttributeValue(null, name)?.toFloatOrNull() ?: default

    private fun parseHexColor(raw: String?): Int? {
        if (raw.isNullOrBlank() || !raw.startsWith("#")) return null
        return runCatching { android.graphics.Color.parseColor(raw) }.getOrNull()
    }

    private fun getBytes(url: String): ByteArray? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            res.body?.bytes()
        }
    }.getOrNull()

    private data class ZipCdEntry(
        val name: String,
        val method: Int,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val localHeaderOffset: Long,
        val score: Int,
    )

    private fun extractIconViaRange(packageName: String, url: String): ImageBitmap? = runCatching {
        val tail = fetchRange(url, "bytes=-$TAIL_FETCH_BYTES") ?: return null
        val totalSize = tail.totalSize
        val tailBytes = tail.bytes
        val tailStart = (totalSize - tailBytes.size).coerceAtLeast(0L)

        var eocdPos = -1
        for (i in tailBytes.size - 22 downTo 0) {
            if (tailBytes[i] == 0x50.toByte() &&
                tailBytes[i + 1] == 0x4b.toByte() &&
                tailBytes[i + 2] == 0x05.toByte() &&
                tailBytes[i + 3] == 0x06.toByte()
            ) {
                eocdPos = i
                break
            }
        }
        if (eocdPos < 0) return null

        val eocdBuf = ByteBuffer.wrap(tailBytes, eocdPos, tailBytes.size - eocdPos).order(ByteOrder.LITTLE_ENDIAN)
        val cdSize = eocdBuf.getInt(12).toLong() and 0xFFFFFFFFL
        val cdOffset = eocdBuf.getInt(16).toLong() and 0xFFFFFFFFL
        if (cdSize <= 0L || cdSize > MAX_CD_BYTES) return null

        val cdBytes: ByteArray = if (cdOffset >= tailStart && (cdOffset - tailStart + cdSize) <= tailBytes.size) {
            val from = (cdOffset - tailStart).toInt()
            tailBytes.copyOfRange(from, from + cdSize.toInt())
        } else {
            fetchRange(url, "bytes=$cdOffset-${cdOffset + cdSize - 1}")?.bytes ?: return null
        }

        val best = findBestIconEntry(cdBytes) ?: return null
        val fetchEnd = best.localHeaderOffset + 30L + 512L + best.compressedSize.toLong()
        val entryRange = fetchRange(url, "bytes=${best.localHeaderOffset}-$fetchEnd")?.bytes ?: return null
        if (entryRange.size < 30) return null

        val lfh = ByteBuffer.wrap(entryRange).order(ByteOrder.LITTLE_ENDIAN)
        if (lfh.getInt(0) != 0x04034b50) return null
        val nameLen = lfh.getShort(26).toInt() and 0xFFFF
        val extraLen = lfh.getShort(28).toInt() and 0xFFFF
        val dataStart = 30 + nameLen + extraLen
        if (dataStart + best.compressedSize > entryRange.size) return null

        val rawIcon = when (best.method) {
            0 -> entryRange.copyOfRange(dataStart, dataStart + best.compressedSize)
            8 -> inflateRaw(entryRange, dataStart, best.compressedSize, best.uncompressedSize)
            else -> return null
        } ?: return null

        val decoded = BitmapFactory.decodeByteArray(rawIcon, 0, rawIcon.size) ?: return null
        val expanded = expandToFullSquare(decoded)
        extractAccentColor(expanded)?.let { accentCache[packageName] = it }
        expanded.asImageBitmap()
    }.getOrNull()

    private fun findBestIconEntry(cd: ByteArray): ZipCdEntry? {
        val buf = ByteBuffer.wrap(cd).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0
        var best: ZipCdEntry? = null
        while (pos + 46 <= cd.size) {
            if (buf.getInt(pos) != 0x02014b50) break
            val method = buf.getShort(pos + 10).toInt() and 0xFFFF
            val compSize = buf.getInt(pos + 20)
            val uncompSize = buf.getInt(pos + 24)
            val nameLen = buf.getShort(pos + 28).toInt() and 0xFFFF
            val extraLen = buf.getShort(pos + 30).toInt() and 0xFFFF
            val commentLen = buf.getShort(pos + 32).toInt() and 0xFFFF
            val localOffset = buf.getInt(pos + 42).toLong() and 0xFFFFFFFFL
            val nextPos = pos + 46 + nameLen + extraLen + commentLen
            if (nextPos > cd.size) break

            if (compSize in 1..MAX_ICON_ENTRY_BYTES && (method == 0 || method == 8)) {
                val name = String(cd, pos + 46, nameLen, Charsets.UTF_8)
                val score = scoreIconPath(name)
                if (score > 0 && (best == null || score > best.score)) {
                    best = ZipCdEntry(name, method, compSize, uncompSize, localOffset, score)
                }
            }
            pos = nextPos
        }
        return best
    }

    private fun scoreIconPath(name: String): Int {
        if (!name.startsWith("res/")) return 0
        val lower = name.lowercase()
        if (!lower.endsWith(".png") && !lower.endsWith(".webp")) return 0
        if (lower.contains(".9.png")) return 0
        if (lower.contains("background") || lower.contains("monochrome")) return 0
        val isLauncher = lower.contains("ic_launcher")
        val isMipmapCustom = lower.contains("mipmap") && (lower.contains("/ic_") || lower.contains("logo") || lower.contains("icon"))
        if (!isLauncher && !isMipmapCustom) return 0

        var score = if (isLauncher) 100 else 50
        if (lower.contains("mipmap")) score += 40
        if (lower.contains("xxxhdpi")) score += 50
        else if (lower.contains("xxhdpi")) score += 40
        else if (lower.contains("xhdpi")) score += 30
        else if (lower.contains("hdpi")) score += 20
        else if (lower.contains("mdpi")) score += 10
        if (lower.contains("ic_launcher.png") || lower.contains("ic_launcher.webp")) score += 25
        else if (lower.contains("ic_launcher_round")) score += 18
        else if (lower.contains("foreground")) score -= 30
        return score
    }

    private fun inflateRaw(src: ByteArray, offset: Int, length: Int, expectedSize: Int): ByteArray? = runCatching {
        val inflater = Inflater(true)
        try {
            inflater.setInput(src, offset, length)
            val out = ByteArrayOutputStream(expectedSize.coerceIn(1024, MAX_ICON_ENTRY_BYTES))
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }.getOrNull()

    private data class RangeResult(val bytes: ByteArray, val totalSize: Long)

    private fun fetchRange(url: String, rangeHeader: String): RangeResult? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", rangeHeader)
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code != 206) return@use null
            val contentRange = res.header("Content-Range") ?: return@use null
            val total = contentRange.substringAfter('/', "").toLongOrNull() ?: return@use null
            val bytes = res.body?.bytes() ?: return@use null
            RangeResult(bytes, total)
        }
    }.getOrNull()

    companion object {
        private const val ICON_SIZE_PX = 288
        private const val TAIL_FETCH_BYTES = 65536
        private const val MAX_CD_BYTES = 2 * 1024 * 1024L
        private const val MAX_ICON_ENTRY_BYTES = 512 * 1024
        private const val USER_AGENT = "LSPosed-Modules-Manager"
        private val GITHUB_PNG_ICON_PATHS = listOf(
            "design/icon.png",
            "icon.png",
            "app/src/main/ic_launcher-playstore.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "app/src/main/res/drawable-nodpi/ic_launcher_foreground.png",
        )
        private val GITHUB_VECTOR_ICON_PATHS = listOf(
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "app/src/main/res/drawable/ic_launcher_fg.xml",
            "app/src/main/res/drawable-anydpi/ic_launcher_foreground.xml",
        )
        private val GITHUB_BG_PATHS = listOf(
            "app/src/main/res/drawable/ic_launcher_bg.xml",
            "app/src/main/res/drawable/ic_launcher_background.xml",
            "app/src/main/res/values/ic_launcher_background.xml",
            "app/src/main/res/values/colors.xml",
        )
        private val HEX_COLOR = Regex("""(#[0-9A-Fa-f]{6,8})""")
        private val MANIFEST_ICON_REGEX = Regex("""android:icon\s*=\s*"@(mipmap|drawable)/([^"]+)"""")
        private val ADAPTIVE_BG_REGEX = Regex("""<background[^>]+android:drawable\s*=\s*"@(color|drawable)/([^"]+)"""")
        private val ADAPTIVE_FG_REGEX = Regex("""<foreground[^>]+android:drawable\s*=\s*"@(drawable|mipmap)/([^"]+)"""")

        fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val bmp = drawable.bitmap
                if (bmp.width == sizePx && bmp.height == sizePx) return bmp
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        fun extractAccentColor(bitmap: Bitmap): Int? {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return null
            val stepX = maxOf(1, w / 24)
            val stepY = maxOf(1, h / 24)
            val buckets = HashMap<Int, Float>()
            val hsv = FloatArray(3)
            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val px = bitmap.getPixel(x, y)
                    val a = (px ushr 24) and 0xFF
                    if (a < 180) continue
                    android.graphics.Color.colorToHSV(px, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    if (value < 0.14f || (sat < 0.12f && value > 0.9f)) continue
                    val r = ((px ushr 16) and 0xF0)
                    val g = ((px ushr 8) and 0xF0)
                    val b = (px and 0xF0)
                    val key = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val weight = 1f + sat * 3.5f
                    buckets[key] = (buckets[key] ?: 0f) + weight
                }
            }
            return buckets.maxByOrNull { it.value }?.key
        }
    }
}
