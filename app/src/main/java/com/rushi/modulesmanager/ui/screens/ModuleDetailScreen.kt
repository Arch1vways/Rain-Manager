package com.rushi.modulesmanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rushi.modulesmanager.ui.components.SileoInstallButton
import com.rushi.modulesmanager.ui.components.SileoModuleIcon
import com.rushi.modulesmanager.ui.components.UninstallIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.ReadmeTranslationState
import com.rushi.modulesmanager.data.SourceAlternative
import com.rushi.modulesmanager.data.RepoAsset
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.RepoRelease
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.data.preferredAsset
import com.rushi.modulesmanager.data.formatBytes
import com.rushi.modulesmanager.data.formatDate
import com.rushi.modulesmanager.data.stripHtml
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.ForkBadge
import com.rushi.modulesmanager.ui.components.LoadingState
import com.rushi.modulesmanager.ui.components.MarkdownBlocks
import com.rushi.modulesmanager.ui.components.ModuleAvatar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.StarBadge
import com.rushi.modulesmanager.ui.components.StatusPill
import com.rushi.modulesmanager.ui.components.VersionPill
import com.rushi.modulesmanager.ui.components.XposedApiPill
import com.rushi.modulesmanager.ui.components.favouriteColor
import com.rushi.modulesmanager.ui.components.markdownPlainText
import com.rushi.modulesmanager.ui.components.shortVersion
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor

private val tabs = listOf("Overview", "Releases")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    viewModel: AppViewModel,
    packageName: String,
    onBack: () -> Unit,
    onOpenDeveloper: (String) -> Unit,
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val loading by viewModel.detailLoading.collectAsStateWithLifecycle()
    val detailError by viewModel.detailError.collectAsStateWithLifecycle()
    val installedVersions by viewModel.installedVersions.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val readmeTranslation by viewModel.readmeTranslation.collectAsStateWithLifecycle()
    val releaseTranslation by viewModel.releaseTranslation.collectAsStateWithLifecycle()
    val autoTranslate by viewModel.autoTranslate.collectAsStateWithLifecycle()
    val translateLanguage by viewModel.translateLanguage.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showOriginalReadme by rememberSaveable(packageName) { mutableStateOf(false) }
    var showOriginalReleases by rememberSaveable(packageName) { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf(false) }
    var sourceInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val module = detail
    val installedVersion = installedVersions[packageName]?.versionName
    val installedHasLauncher = remember(packageName, installedVersion) {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    }
    val update = updates.firstOrNull { it.installed.packageName == packageName }
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val sourceAlternatives by viewModel.sourceAlternatives.collectAsStateWithLifecycle()
    val release = module?.releaseFor(channel)
    val primaryAsset = release?.preferredAsset(packageName)

    LaunchedEffect(packageName) { viewModel.openModule(packageName) }
    LaunchedEffect(Unit) {
        if (tab !in tabs.indices) tab = 0
    }
    LaunchedEffect(tab, module?.packageName, autoTranslate, translateLanguage) {
        if (tab == 1 && autoTranslate) viewModel.translateReleases(announce = false)
    }
    DisposableEffect(packageName) {
        onDispose { viewModel.closeModule() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (module == null) {
                    val strings = LocalStrings.current
                    Spacer(Modifier.statusBarsPadding().height(56.dp))
                    if (loading) {
                        LoadingState(strings.loadingModuleDetails)
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Info,
                            title = strings.moduleNotFound,
                            body = detailError ?: strings.moduleNotInRepo,
                        )
                    }
                    return@Column
                }

                SileoDetailHeader(
                    module = module,
                    viewModel = viewModel,
                    installedVersion = installedVersion,
                    installedHasLauncher = installedHasLauncher,
                    update = update,
                    channel = channel,
                    primaryAsset = primaryAsset,
                    onInstall = {
                        if (release != null) viewModel.installRelease(module, release)
                    },
                    onOpenModule = { viewModel.openInstalledModule(packageName) },
                    onOpenAppInfo = { viewModel.openAppInfo(packageName) },
                    onUninstall = { viewModel.uninstall(packageName) },
                    onOpenDeveloper = onOpenDeveloper,
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                ) {
                    val strings = LocalStrings.current
                    val tabTitles = listOf(strings.overview, strings.releases)
                    tabTitles.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = tab == index,
                            onClick = { tab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                            modifier = Modifier.weight(1f),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveContainerColor = amoledCardContainerColor(),
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {},
                            label = { Text(title) },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when (tab) {
                        0 -> OverviewTab(
                            module = module,
                            installing = loading,
                            translation = readmeTranslation,
                            showOriginal = showOriginalReadme,
                            onOpenLink = viewModel::openLink,
                        )
                        else -> ReleasesTab(
                            module = module,
                            loading = loading,
                            translations = if (showOriginalReleases) {
                                emptyMap()
                            } else {
                                releaseTranslation?.texts.orEmpty()
                            },
                            onInstall = { asset ->
                                viewModel.installChosen(module, asset)
                            },
                            onSave = { asset ->
                                viewModel.saveApk(module, asset)
                            },
                            onOpenRelease = viewModel::openLink,
                        )
                    }
                }
            }

            // Floating Sileo-style translucent top bar over the banner/accent header.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleFavourite(packageName) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (packageName in favourites) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = if (packageName in favourites) {
                                    "Remove from favourites"
                                } else {
                                    "Add to favourites"
                                },
                                tint = if (packageName in favourites) {
                                    favouriteColor()
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                        if ((tab == 0 || tab == 1) && module != null) {
                            val readme = tab == 0
                            val translating = if (readme) {
                                readmeTranslation?.loading == true
                            } else {
                                releaseTranslation?.loading == true
                            }
                            val translated = if (readme) {
                                readmeTranslation?.html != null
                            } else {
                                releaseTranslation?.translated == true
                            }
                            val progress = if (readme) {
                                readmeTranslation?.progress ?: 0f
                            } else {
                                releaseTranslation?.progress ?: 0f
                            }
                            val showOriginal = if (readme) showOriginalReadme else showOriginalReleases
                            IconButton(
                                enabled = !translating,
                                modifier = Modifier.size(40.dp),
                                onClick = {
                                    if (translated) {
                                        if (readme) showOriginalReadme = !showOriginalReadme
                                        else showOriginalReleases = !showOriginalReleases
                                    } else {
                                        if (readme) {
                                            showOriginalReadme = false
                                            viewModel.translateReadme()
                                        } else {
                                            showOriginalReleases = false
                                            viewModel.translateReleases()
                                        }
                                    }
                                },
                            ) {
                                if (translating) {
                                    if (progress > 0f) {
                                        CircularProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = "Translate",
                                        tint = if (translated && !showOriginal) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalContentColor.current
                                        },
                                    )
                                }
                            }
                        }
                        module?.releasesPageUrl()?.let { url ->
                            IconButton(
                                onClick = { viewModel.openLink(url) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = "Open releases page",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingSource && module != null) {
        AlertDialog(
            onDismissRequest = { editingSource = false },
            title = { Text("Set source repository") },
            text = {
                Column {
                    Text(
                        text = "Used for the owner, avatar, README and release links when the index points to a fork or the wrong repository.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val knownSources = remember(module, sourceAlternatives) {
                        val current = module.githubRepoFullName()
                        val listed = sourceAlternatives[module.packageName].orEmpty()
                        (if (current != null) {
                            listed + SourceAlternative(current, null, null)
                        } else {
                            listed
                        }).distinctBy { it.repo }
                            .sortedByDescending { it.releaseTime ?: "" }
                    }
                    if (knownSources.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Repositories in the index",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            knownSources.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { sourceInput = candidate.repo }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = sourceInput == candidate.repo,
                                        onClick = { sourceInput = candidate.repo },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.repo,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        candidate.releaseTime?.let { time ->
                                            Text(
                                                text = "Last release ${time.substringBefore('T')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (candidate.repo == module.githubRepoFullName()) {
                                        Text(
                                            text = "Current",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        StatusPill(text = "Fallback")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Or enter another repository",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(
                        value = sourceInput,
                        onValueChange = { sourceInput = it },
                        placeholder = { Text("owner/repo or https://github.com/owner/repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                val strings = LocalStrings.current
                TextButton(onClick = {
                    if (viewModel.setModuleSource(module, sourceInput)) editingSource = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                val strings = LocalStrings.current
                Row {
                    if (viewModel.hasSourceOverride(packageName)) {
                        TextButton(onClick = {
                            viewModel.clearModuleSource(packageName)
                            editingSource = false
                        }) { Text(strings.resetButton) }
                    }
                    TextButton(onClick = { editingSource = false }) { Text(strings.cancel) }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SileoDetailHeader(
    module: RepoModule,
    viewModel: AppViewModel,
    installedVersion: String?,
    installedHasLauncher: Boolean,
    update: ModuleUpdate?,
    channel: UpdateChannel,
    primaryAsset: RepoAsset?,
    onInstall: () -> Unit,
    onOpenModule: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onOpenDeveloper: (String) -> Unit,
) {
    val updateVersion = update?.version
    val hasUpdate = updateVersion != null
    val isInstalled = installedVersion != null
    val latestDate = formatDate(module.latestTime(channel))
    val latestVersion = module.releaseFor(channel)?.let { it.name ?: it.tagName }

    var bannerUrl by remember(module.packageName) { mutableStateOf<String?>(null) }
    var bannerLoaded by remember(module.packageName) { mutableStateOf(false) }
    var accentColor by remember(module.packageName) {
        mutableStateOf(viewModel.moduleAccentColor(module.packageName)?.let(::Color))
    }

    LaunchedEffect(module.packageName, module.repoFullName) {
        bannerUrl = viewModel.resolveModuleBanner(module)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val fallbackAccent = accentColor ?: MaterialTheme.colorScheme.primaryContainer

    Column(modifier = Modifier.fillMaxWidth()) {
        // Sileo-style edge-to-edge banner (or icon accent color gradient if no banner exists).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            fallbackAccent.copy(alpha = 0.88f),
                            fallbackAccent.copy(alpha = 0.35f),
                            surfaceColor,
                        ),
                    ),
                ),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { bannerLoaded = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (bannerLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        surfaceColor.copy(alpha = 0.28f),
                                        surfaceColor,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }

        // Sileo-style package icon + title + Material You pill Install/Uninstall button.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    SileoModuleIcon(
                        module = module,
                        viewModel = viewModel,
                        size = 58.dp,
                        cornerRadius = 14.dp,
                        onAccentColor = { accentColor = it },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    module.developerLogin()?.let { developer ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = developer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onOpenDeveloper(developer) },
                        )
                    }
                    val verSub = when {
                        hasUpdate -> "v${shortVersion(installedVersion)} \u2192 v${shortVersion(updateVersion.name)}"
                        isInstalled -> "v${shortVersion(installedVersion)}"
                        latestVersion != null -> "v${shortVersion(latestVersion)}"
                        else -> null
                    }
                    if (verSub != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = verSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                val strings = LocalStrings.current
                val activeDownload by viewModel.downloads.collectAsStateWithLifecycle()
                val uninstallingPkg by viewModel.uninstallingPackage.collectAsStateWithLifecycle()
                val isUninstalling = uninstallingPkg == module.packageName
                val busy = activeDownload?.packageName == module.packageName
                val progress = if (busy) {
                    activeDownload?.let {
                        if (it.phase == com.rushi.modulesmanager.data.DownloadCenter.Phase.DOWNLOADING) {
                            it.fraction
                        } else {
                            1f
                        }
                    }
                } else {
                    null
                }
                if (isUninstalling) {
                    UninstallIconButton(uninstalling = true, onClick = {})
                } else {
                    if (hasUpdate || !isInstalled || installedHasLauncher) {
                        val primaryLabel = when {
                            hasUpdate -> strings.update
                            isInstalled -> strings.open
                            else -> strings.install
                        }
                        val primaryEnabled = when {
                            hasUpdate -> primaryAsset != null
                            isInstalled -> true
                            else -> primaryAsset != null
                        }
                        SileoInstallButton(
                            text = primaryLabel,
                            isPrimary = hasUpdate || !isInstalled,
                            isSecondary = isInstalled && !hasUpdate,
                            busy = busy,
                            progress = progress,
                            enabled = primaryEnabled,
                            onCancel = { viewModel.cancelDownload() },
                            onClick = {
                                when {
                                    hasUpdate || !isInstalled -> onInstall()
                                    else -> onOpenModule()
                                }
                            },
                        )
                    }
                    if (isInstalled && !busy) {
                        Spacer(Modifier.width(4.dp))
                        UninstallIconButton(
                            uninstalling = false,
                            onClick = onUninstall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorPill(developer: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = Color.Transparent,
        contentColor = accent,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = developer,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DatePill(text: String) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (dark) Color(0xFFFFA657) else Color(0xFFB45309)
    Surface(
        color = Color.Transparent,
        contentColor = accent,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun OverviewTab(
    module: RepoModule,
    installing: Boolean,
    translation: ReadmeTranslationState?,
    showOriginal: Boolean,
    onOpenLink: (String) -> Unit,
) {
    val readme = module.readmeHtml
    if (readme.isNullOrBlank() && translation?.html == null) {
        val fallback = module.readme ?: module.summary
        if (fallback.isNullOrBlank()) {
            EmptyState(
                icon = Icons.Outlined.Extension,
                title = "No description",
                body = "This module has not published a README.",
            )
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            )
        }
        return
    }
    val html = translation?.html?.takeUnless { showOriginal } ?: readme
    if (html != null) {
        ReadmeWebView(
            html = html,
            repoFullName = module.githubRepoFullName(),
            onOpenLink = onOpenLink,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ReadmeWebView(
    html: String,
    repoFullName: String?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseUrl = repoFullName?.let { "https://github.com/$it/" } ?: "https://github.com"
    val rendered = remember(html, repoFullName) { rewriteRelativeImages(html, repoFullName) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewInstance = this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.textZoom = 100
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    settings.forceDark = if (dark) {
                        android.webkit.WebSettings.FORCE_DARK_ON
                    } else {
                        android.webkit.WebSettings.FORCE_DARK_OFF
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        onOpenLink(request.url.toString())
                        return true
                    }
                }
                loadDataWithBaseURL(baseUrl, wrapReadme(rendered, dark), "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(baseUrl, wrapReadme(rendered, dark), "text/html", "utf-8", null)
        },
    )
}

private val RELATIVE_IMG_SRC = Regex("""(<img\b[^>]*\bsrc=")(?!https?:|data:)([^"]+)""", RegexOption.IGNORE_CASE)

// GitHub's README HTML keeps relative asset paths. Load them from the raw repo
// instead of the GitHub site root, where they do not exist.
private fun rewriteRelativeImages(html: String, repoFullName: String?): String {
    if (repoFullName.isNullOrBlank()) return html
    val rawBase = "https://raw.githubusercontent.com/$repoFullName/HEAD/"
    return RELATIVE_IMG_SRC.replace(html) { match ->
        val path = match.groupValues[2].removePrefix("./").removePrefix("/")
        match.groupValues[1] + rawBase + path
    }
}

private fun wrapReadme(html: String, dark: Boolean): String {
    val colors = if (dark) {
        "body{color:#DDE4E4;background:transparent}a{color:#80D4DA}code,pre{background:rgba(255,255,255,.08);color:#DDE4E4}hr{border-color:rgba(255,255,255,.15)}td,th{border-color:rgba(255,255,255,.2)}blockquote{color:#BEC8C9;border-left:3px solid rgba(255,255,255,.2)}"
    } else {
        "body{color:#161D1E;background:transparent}a{color:#00696E}code,pre{background:rgba(0,0,0,.05);color:#161D1E}hr{border-color:rgba(0,0,0,.12)}td,th{border-color:rgba(0,0,0,.15)}blockquote{color:#3F4849;border-left:3px solid rgba(0,0,0,.15)}"
    }
    return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body{font-family:Roboto,sans-serif;font-size:15px;line-height:1.55;margin:16px;}
        img{max-width:100%;height:auto;}
        pre{overflow:auto;padding:10px;border-radius:8px;}
        code{font-family:monospace;font-size:13px;padding:1px 4px;border-radius:4px;}
        pre code{padding:0;}
        table{border-collapse:collapse;}
        td,th{border:1px solid;padding:5px 8px;}
        blockquote{margin:8px 0;padding-left:10px;}
        h1,h2,h3{line-height:1.25;}
        $colors
        </style></head>
        <body>$html</body></html>
    """.trimIndent()
}

@Composable
private fun ReleasesTab(
    module: RepoModule,
    loading: Boolean,
    translations: Map<String, String>,
    onInstall: (RepoAsset) -> Unit,
    onSave: (RepoAsset) -> Unit,
    onOpenRelease: (String?) -> Unit,
) {
    val releases = module.allReleases()
    if (releases.isEmpty()) {
        if (loading) {
            LoadingState("Loading releases...")
        } else {
            EmptyState(
                icon = Icons.Outlined.Info,
                title = "No releases",
                body = "This module has not published any releases on GitHub.",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(releases, key = { it.url ?: it.tagName ?: it.toString() }) { item ->
            ReleaseCard(
                release = item,
                translations = translations,
                canSave = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                onInstall = onInstall,
                onSave = onSave,
                onOpenRelease = { onOpenRelease(item.url) },
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    release: RepoRelease,
    translations: Map<String, String>,
    canSave: Boolean,
    onInstall: (RepoAsset) -> Unit,
    onSave: (RepoAsset) -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = amoledCardContainerColor()),
        border = amoledCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name ?: release.tagName ?: "Release",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDate(release.publishedAt) ?: "Unknown date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (release.isPrerelease) {
                    StatusPill(text = "Pre-release")
                    Spacer(Modifier.width(4.dp))
                }
                if (release.url != null) {
                    IconButton(onClick = onOpenRelease) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open release page")
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            val rawNotes = release.descriptionHtml
            val htmlNotes = remember(rawNotes) { rawNotes != null && looksLikeHtml(rawNotes) }
            val plainNotes = remember(rawNotes, htmlNotes) {
                rawNotes?.let { if (htmlNotes) stripHtml(it) else markdownPlainText(it) }
            }
            val markdownNotes = rawNotes.takeUnless { htmlNotes }
            val translatedPlain = plainNotes?.let { translations[it] } ?: plainNotes
            if (!translatedPlain.isNullOrBlank()) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                var canExpand by rememberSaveable { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                if (expanded && markdownNotes != null) {
                    MarkdownBlocks(
                        markdown = markdownNotes,
                        translations = translations,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = translatedPlain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout ->
                            if (!expanded && layout.hasVisualOverflow) canExpand = true
                        },
                    )
                }
                if (canExpand) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(if (expanded) "Show less" else "Read more")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            if (release.apkAssets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                release.apkAssets.forEach { asset ->
                    AssetRow(
                        asset = asset,
                        canSave = canSave,
                        onInstall = { onInstall(asset) },
                        onSave = { onSave(asset) },
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "No APK attached to this release",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val HTML_TAG = Regex(
    "<\\s*/?\\s*(p|h[1-6]|ul|ol|li|div|br|img|a|strong|em|pre|code|table|tr|td|th|blockquote|span)\\b",
    RegexOption.IGNORE_CASE,
)

private fun looksLikeHtml(value: String): Boolean = HTML_TAG.containsMatchIn(value)

@Composable
private fun AssetRow(
    asset: RepoAsset,
    canSave: Boolean,
    onInstall: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                if (asset.size > 0) add(formatBytes(asset.size))
                if (asset.downloadCount > 0) add("${asset.downloadCount} downloads")
            }.joinToString(" - ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (canSave) {
            TextButton(
                onClick = onSave,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Save")
            }
            Spacer(Modifier.width(2.dp))
        }
        FilledTonalButton(onClick = onInstall) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Install")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("package name", text))
    Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
}
