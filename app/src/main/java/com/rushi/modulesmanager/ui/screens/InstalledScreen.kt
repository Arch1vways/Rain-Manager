package com.rushi.modulesmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.BatchItemState
import com.rushi.modulesmanager.BatchPhase
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.InstalledAvatar
import com.rushi.modulesmanager.ui.components.ModulesSearchBar
import com.rushi.modulesmanager.ui.components.ScrollToTopFab
import com.rushi.modulesmanager.ui.components.SileoInstallButton
import com.rushi.modulesmanager.ui.components.UninstallIconButton
import com.rushi.modulesmanager.ui.components.VerticalScrollbar
import com.rushi.modulesmanager.ui.components.clearFocusOnTapOutside
import com.rushi.modulesmanager.ui.components.shortVersion
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstalledScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
    onOpenDeveloper: (String) -> Unit,
) {
    val installed by viewModel.allInstalled.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val batch by viewModel.batchUpdate.collectAsStateWithLifecycle()
    val batchActive = batch?.let { it.running || it.interrupted } == true
    val download by viewModel.downloads.collectAsStateWithLifecycle()
    val ignoredUpdates by viewModel.ignoredUpdates.collectAsStateWithLifecycle()
    val preReleaseModules by viewModel.preReleaseModules.collectAsStateWithLifecycle()
    val skippedVersions by viewModel.skippedVersions.collectAsStateWithLifecycle()
    val moduleLinks by viewModel.moduleLinks.collectAsStateWithLifecycle()
    val manualModules by viewModel.manualModules.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var pendingUninstall by remember { mutableStateOf<InstalledModule?>(null) }
    var linkingModule by remember { mutableStateOf<InstalledModule?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val focusManager = LocalFocusManager.current
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    val modulesByPackage = remember(modules) { modules.associateBy { it.packageName } }
    val updatesByPackage = remember(updates) { updates.associateBy { it.installed.packageName } }
    fun repoModuleFor(module: InstalledModule): RepoModule? =
        modulesByPackage[module.packageName]
            ?: manualModules[module.packageName]
            ?: moduleLinks[module.packageName]?.let { modulesByPackage[it] }

    val searched = remember(installed, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            installed
        } else {
            installed.filter { module ->
                module.label.lowercase().contains(needle) ||
                    module.packageName.lowercase().contains(needle)
            }
        }
    }
    val visible = remember(searched, updatesByPackage) {
        searched.sortedWith(
            compareBy<InstalledModule> { if (updatesByPackage.containsKey(it.packageName)) 0 else 1 }
                .thenBy { it.label.lowercase() },
        )
    }

    // Skip the first run so returning from a module page keeps the scroll position.
    var filtersReady by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (filtersReady) listState.scrollToItem(0) else filtersReady = true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .onGloballyPositioned { rootCoords = it }
                .clearFocusOnTapOutside(
                    focusManager = focusManager,
                    rootCoordsProvider = { rootCoords },
                    targetBoundsProvider = { searchBounds },
                ),
        ) {
            ModulesSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.searchInstalled,
                onBoundsChanged = { searchBounds = it },
            )

            if (updates.isNotEmpty() && !batchActive) {
                FilledTonalButton(
                    onClick = viewModel::updateAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.updateAllCount(updates.size))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.rescan(showMessage = false) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.Outlined.Apps,
                                    title = if (installed.isEmpty()) {
                                        strings.noInstalledModules
                                    } else {
                                        strings.nothingHere
                                    },
                                    body = if (installed.isEmpty()) {
                                        strings.installModulesHint
                                    } else {
                                        strings.noModuleMatches(query)
                                    },
                                )
                            }
                        }
                    } else {
                        items(visible, key = { it.packageName }) { module ->
                            val repoModule = repoModuleFor(module)
                            val linked = moduleLinks[module.packageName] != null
                            InstalledCard(
                                module = module,
                                repoModule = repoModule,
                                update = updatesByPackage[module.packageName],
                                batchItem = batch?.items?.get(module.packageName),
                                onCancelItem = {
                                    viewModel.cancelBatchItem(module.packageName)
                                },
                                ignored = module.packageName in ignoredUpdates ||
                                    (repoModule != null && repoModule.packageName in ignoredUpdates),
                                skippedVersion = repoModule?.let { skippedVersions[it.packageName] },
                                preReleases = repoModule != null &&
                                    repoModule.packageName in preReleaseModules,
                                canTrackPreReleases = repoModule != null &&
                                    viewModel.canTrackPreReleases(repoModule),
                                onOpenRepository = {
                                    onOpenModule(repoModule?.packageName ?: module.packageName)
                                },
                                onOpenApp = { viewModel.openInstalledModule(module.packageName) },
                                onUpdate = { update -> viewModel.installLatest(update.module) },
                                onSkipVersion = { repoModule?.let(viewModel::skipVersion) },
                                onClearSkipped = { repoModule?.let(viewModel::clearSkippedVersion) },
                                onToggleIgnore = { repoModule?.let(viewModel::toggleIgnoreUpdates) },
                                onTogglePreRelease = { repoModule?.let(viewModel::togglePreReleases) },
                                onAppInfo = { viewModel.openAppInfo(module.packageName) },
                                onOpenDeveloper = onOpenDeveloper,
                                onLinkModule = { linkingModule = module },
                                onUnlinkModule = { viewModel.unlinkModule(module.packageName) },
                                linked = linked,
                                onUninstall = { pendingUninstall = module },
                            )
                        }
                    }
                }
                }

                VerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 4.dp, bottom = 80.dp),
                )

                val showScrollToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                val stackAlpha by animateFloatAsState(
                    targetValue = if (download == null && showScrollToTop) 1f else 0f,
                    label = "fabStack",
                )
                if (stackAlpha > 0.01f) {
                    ScrollToTopFab(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .alpha(stackAlpha)
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = 98.dp),
                    )
                }
            }
        }
    }

    pendingUninstall?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(strings.uninstallTitle(module.label)) },
            text = { Text(strings.uninstallBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUninstall = null
                        viewModel.uninstall(module.packageName)
                    },
                ) { Text(strings.uninstall) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text(strings.cancel) }
            },
        )
    }

    linkingModule?.let { module ->
        LinkModuleDialog(
            label = module.label,
            onDismiss = { linkingModule = null },
            onLink = { url ->
                viewModel.linkModule(module.packageName, url)
                linkingModule = null
            },
        )
    }
}

@Composable
private fun LinkModuleDialog(
    label: String,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit,
) {
    val strings = LocalStrings.current
    var repoUrl by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.linkManually) },
        text = {
            Column {
                Text(
                    text = strings.linkManuallyDesc(label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLink(repoUrl) },
                enabled = repoUrl.isNotBlank(),
            ) { Text(strings.linkButton) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstalledCard(
    module: InstalledModule,
    repoModule: RepoModule?,
    update: ModuleUpdate?,
    batchItem: BatchItemState?,
    onCancelItem: () -> Unit,
    ignored: Boolean,
    skippedVersion: String?,
    preReleases: Boolean,
    canTrackPreReleases: Boolean,
    onOpenRepository: () -> Unit,
    onOpenApp: () -> Unit,
    onUpdate: (ModuleUpdate) -> Unit,
    onSkipVersion: () -> Unit,
    onClearSkipped: () -> Unit,
    onToggleIgnore: () -> Unit,
    onTogglePreRelease: () -> Unit,
    onAppInfo: () -> Unit,
    onLinkModule: () -> Unit,
    onUnlinkModule: () -> Unit,
    onOpenDeveloper: (String) -> Unit,
    linked: Boolean,
    onUninstall: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val strings = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }
    val showsUpdate = update != null && !ignored && skippedVersion == null
    val batchActive = batchItem != null &&
        (batchItem.phase == BatchPhase.QUEUED ||
            batchItem.phase == BatchPhase.DOWNLOADING ||
            batchItem.phase == BatchPhase.WAITING_TO_INSTALL ||
            batchItem.phase == BatchPhase.INSTALLING)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = amoledCardContainerColor()),
        border = amoledCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (repoModule != null) onOpenRepository()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUninstall()
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstalledAvatar(module, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(initialDelayMillis = 1500),
                )
                val subtitle = repoModule?.summary?.takeIf { it.isNotBlank() } ?: module.packageName
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val verLine = buildString {
                    append("v${shortVersion(module.versionName ?: module.versionCode.toString())}")
                    if (showsUpdate && update != null) {
                        append(" \u2192 v${shortVersion(update.version.name)}")
                    }
                    when {
                        ignored -> append(" · ${strings.ignoredLabel}")
                        skippedVersion != null -> append(" · ${strings.skippedLabel(shortVersion(skippedVersion.substringAfter('-')))}")
                    }
                }
                Text(
                    text = verLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (update != null && !batchActive) {
                    SileoInstallButton(
                        text = strings.update,
                        isPrimary = true,
                        onClick = { onUpdate(update) },
                    )
                    Spacer(Modifier.width(4.dp))
                    UninstallIconButton(
                        uninstalling = false,
                        onClick = onUninstall,
                    )
                } else if (!batchActive) {
                    if (module.hasLauncher) {
                        SileoInstallButton(
                            text = strings.open,
                            isSecondary = true,
                            onClick = onOpenApp,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    UninstallIconButton(
                        uninstalling = false,
                        onClick = onUninstall,
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More actions",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                            if (update != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.skipThisVersion) },
                                    onClick = {
                                        menuOpen = false
                                        onSkipVersion()
                                    },
                                )
                            }
                            if (skippedVersion != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.clearSkippedVersion) },
                                    onClick = {
                                        menuOpen = false
                                        onClearSkipped()
                                    },
                                )
                            }
                            if (repoModule != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (ignored) strings.restoreUpdates else strings.ignoreUpdates)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onToggleIgnore()
                                    },
                                )
                            }
                            if (linked) {
                                DropdownMenuItem(
                                    text = { Text(strings.unlinkManualRepo) },
                                    onClick = {
                                        menuOpen = false
                                        onUnlinkModule()
                                    },
                                )
                            } else if (repoModule == null) {
                                DropdownMenuItem(
                                    text = { Text(strings.linkManually) },
                                    onClick = {
                                        menuOpen = false
                                        onLinkModule()
                                    },
                                )
                            }
                            if (canTrackPreReleases) {
                                DropdownMenuItem(
                                    text = { Text(strings.followPreReleases) },
                                    leadingIcon = if (preReleases) {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onTogglePreRelease()
                                    },
                                )
                            }
                            repoModule?.developerLogin()?.let { developer ->
                                DropdownMenuItem(
                                    text = { Text(strings.moreByDeveloper(developer)) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenDeveloper(developer)
                                    },
                                )
                            }
                            if (repoModule != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.viewModule) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenRepository()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(strings.appInfo) },
                                onClick = {
                                    menuOpen = false
                                    onAppInfo()
                                },
                            )
                        }
                    }
                }
            }
            batchItem?.let { item -> BatchItemProgress(item, onCancelItem) }
        }
    }
}

/** One update's own transfer/install state, with a cancel action while it can still stop. */
@Composable
private fun BatchItemProgress(item: BatchItemState, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    if (item.phase == BatchPhase.FAILED) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.error ?: strings.updateFailed,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val fraction = when {
        item.phase == BatchPhase.WAITING_TO_INSTALL -> 1f
        item.total > 0L -> (item.downloaded.toFloat() / item.total).coerceIn(0f, 1f)
        else -> null
    }
    val label = when (item.phase) {
        BatchPhase.QUEUED -> strings.phaseQueued
        BatchPhase.DOWNLOADING -> strings.phaseDownloading
        BatchPhase.WAITING_TO_INSTALL -> strings.phaseWaitingToInstall
        BatchPhase.INSTALLING -> strings.phaseInstalling
        BatchPhase.CANCELLED -> strings.phaseCancelled
        else -> return
    }
    val cancelable = item.phase == BatchPhase.QUEUED ||
        item.phase == BatchPhase.DOWNLOADING ||
        item.phase == BatchPhase.WAITING_TO_INSTALL
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (item.phase == BatchPhase.DOWNLOADING && fraction != null) {
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (cancelable) {
            IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel this update",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
