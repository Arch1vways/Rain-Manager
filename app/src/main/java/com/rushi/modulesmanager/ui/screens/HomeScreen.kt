package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.formatRelativeTime
import com.rushi.modulesmanager.data.parseInstant
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.LoadingState
import com.rushi.modulesmanager.ui.components.SileoInstallButton
import com.rushi.modulesmanager.ui.components.SileoModuleIcon
import com.rushi.modulesmanager.ui.components.UninstallIconButton
import com.rushi.modulesmanager.ui.components.VersionPill
import com.rushi.modulesmanager.ui.components.shortVersion
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val installedVersions by viewModel.installedVersions.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uninstallingPkg by viewModel.uninstallingPackage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = LocalStrings.current

    val updateByPkg = remember(updates) { updates.associateBy { it.installed.packageName } }
    val newestModules = remember(modules, channel) {
        modules
            .filter { it.hasReleases }
            .sortedByDescending { m ->
                parseInstant(m.addedAt) ?: parseInstant(m.latestTime(channel)) ?: parseInstant(m.updatedAt)
            }
            .take(40)
    }
    val featuredModules = remember(modules) {
        modules
            .filter { it.hasReleases }
            .sortedByDescending { it.stargazerCount }
            .take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.featuredTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = "Library")
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing || (loading && modules.isEmpty()),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (loading && modules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize())
            } else if (newestModules.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.NewReleases,
                    title = strings.noModulesYet,
                    body = strings.pullToRefreshOrAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "featured_carousel") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(featuredModules, key = { it.packageName }) { module ->
                                val release = module.releaseFor(channel)
                                val installed = installedVersions[module.packageName]
                                val update = updateByPkg[module.packageName]
                                val hasLauncher = installed != null && viewModel.canLaunch(module.packageName)
                                val busy = downloads?.packageName == module.packageName
                                val isUninstalling = uninstallingPkg == module.packageName
                                val showAction = update != null || installed == null || hasLauncher
                                val progress = if (busy) {
                                    downloads?.let { if (it.phase == com.rushi.modulesmanager.data.DownloadCenter.Phase.DOWNLOADING) it.fraction else 1f }
                                } else null
                                FeaturedModuleBannerCard(
                                    module = module,
                                    viewModel = viewModel,
                                    actionLabel = when {
                                        update != null -> strings.update
                                        installed != null && hasLauncher -> strings.open
                                        else -> strings.install
                                    },
                                    showAction = showAction,
                                    showUninstall = installed != null && !busy,
                                    uninstalling = isUninstalling,
                                    isPrimary = update != null,
                                    isSecondary = installed != null && update == null,
                                    busy = busy,
                                    progress = progress,
                                    onCancel = { viewModel.cancelDownload() },
                                    onOpen = { onOpenModule(module.packageName) },
                                    onUninstall = { viewModel.uninstall(module.packageName) },
                                    onAction = {
                                        when {
                                            update != null -> viewModel.installRelease(module, release)
                                            installed != null && hasLauncher -> viewModel.openInstalledModule(module.packageName)
                                            release != null -> viewModel.installRelease(module, release)
                                            else -> onOpenModule(module.packageName)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item(key = "new_header") {
                        Text(
                            text = strings.newModulesTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }

                    items(newestModules, key = { it.packageName }) { module ->
                        val release = module.releaseFor(channel)
                        val installed = installedVersions[module.packageName]
                        val update = updateByPkg[module.packageName]
                        val hasLauncher = installed != null && viewModel.canLaunch(module.packageName)
                        val busy = downloads?.packageName == module.packageName
                        val isUninstalling = uninstallingPkg == module.packageName
                        val showAction = update != null || installed == null || hasLauncher
                        val progress = if (busy) {
                            downloads?.let { if (it.phase == com.rushi.modulesmanager.data.DownloadCenter.Phase.DOWNLOADING) it.fraction else 1f }
                        } else null
                        val verRaw = module.latest(channel)?.name
                            ?: release?.let { it.name ?: it.tagName }
                        SileoNewModuleRow(
                            module = module,
                            viewModel = viewModel,
                            versionLabel = verRaw?.let(::shortVersion)?.takeIf { it.isNotBlank() }?.let { "v$it" },
                            timeLabel = (parseInstant(module.latestTime(channel)) ?: parseInstant(module.updatedAt))
                                ?.toEpochMilli()
                                ?.let(::formatRelativeTime),
                            actionLabel = when {
                                update != null -> strings.update
                                installed != null && hasLauncher -> strings.open
                                else -> strings.install
                            },
                            showAction = showAction,
                            showUninstall = installed != null && !busy,
                            uninstalling = isUninstalling,
                            isPrimary = update != null,
                            isSecondary = installed != null && update == null,
                            busy = busy,
                            progress = progress,
                            onCancel = { viewModel.cancelDownload() },
                            onClick = { onOpenModule(module.packageName) },
                            onUninstall = { viewModel.uninstall(module.packageName) },
                            onAction = {
                                when {
                                    update != null -> viewModel.installRelease(module, release)
                                    installed != null && hasLauncher -> viewModel.openInstalledModule(module.packageName)
                                    release != null -> viewModel.installRelease(module, release)
                                    else -> onOpenModule(module.packageName)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedModuleBannerCard(
    module: RepoModule,
    viewModel: AppViewModel,
    actionLabel: String,
    showAction: Boolean = true,
    showUninstall: Boolean = false,
    uninstalling: Boolean = false,
    isPrimary: Boolean,
    isSecondary: Boolean,
    busy: Boolean,
    progress: Float?,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit = {},
    onAction: () -> Unit,
) {
    var bannerUrl by remember(module.packageName) { mutableStateOf<String?>(null) }
    var bannerLoaded by remember(module.packageName) { mutableStateOf(false) }
    var accentColor by remember(module.packageName) {
        mutableStateOf(viewModel.moduleAccentColor(module.packageName)?.let(::Color))
    }

    LaunchedEffect(module.packageName) {
        bannerUrl = viewModel.resolveModuleBanner(module)
    }

    val fallbackColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer
    val gradient = Brush.linearGradient(
        colors = listOf(
            fallbackColor.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        ),
    )

    Card(
        modifier = Modifier
            .width(272.dp)
            .height(154.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        border = amoledCardBorder(),
        colors = CardDefaults.cardColors(containerColor = amoledCardContainerColor()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient),
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
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (bannerLoaded) 0.08f else 0.05f),
                                Color.Black.copy(alpha = if (bannerLoaded) 0.72f else 0.56f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SileoModuleIcon(
                    module = module,
                    viewModel = viewModel,
                    size = 42.dp,
                    cornerRadius = 11.dp,
                    onAccentColor = { accentColor = it },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(initialDelayMillis = 1500),
                    )
                    Text(
                        text = module.collaborators.firstOrNull()?.login ?: module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (uninstalling) {
                    Spacer(Modifier.width(8.dp))
                    UninstallIconButton(uninstalling = true, onClick = {})
                } else if (showAction) {
                    Spacer(Modifier.width(8.dp))
                    SileoInstallButton(
                        text = actionLabel,
                        isPrimary = isPrimary,
                        isSecondary = isSecondary,
                        busy = busy,
                        progress = progress,
                        onCancel = onCancel,
                        onClick = onAction,
                    )
                    if (showUninstall) {
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
internal fun SileoNewModuleRow(
    module: RepoModule,
    viewModel: AppViewModel,
    versionLabel: String?,
    timeLabel: String?,
    actionLabel: String,
    showAction: Boolean = true,
    showUninstall: Boolean = false,
    uninstalling: Boolean = false,
    isPrimary: Boolean,
    isSecondary: Boolean,
    busy: Boolean,
    progress: Float?,
    onCancel: () -> Unit,
    onClick: () -> Unit,
    onUninstall: () -> Unit = {},
    onAction: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = amoledCardContainerColor(),
        border = amoledCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SileoModuleIcon(
                module = module,
                viewModel = viewModel,
                size = 44.dp,
                cornerRadius = 11.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(initialDelayMillis = 1500),
                )
                val subtitle = module.summary?.takeIf { it.isNotBlank() }
                    ?: module.collaborators.firstOrNull()?.login
                    ?: module.packageName
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(versionLabel, timeLabel).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (uninstalling) {
                Spacer(Modifier.width(8.dp))
                UninstallIconButton(uninstalling = true, onClick = {})
            } else if (showAction) {
                Spacer(Modifier.width(8.dp))
                SileoInstallButton(
                    text = actionLabel,
                    isPrimary = isPrimary,
                    isSecondary = isSecondary,
                    busy = busy,
                    progress = progress,
                    onCancel = onCancel,
                    onClick = onAction,
                )
                if (showUninstall) {
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
