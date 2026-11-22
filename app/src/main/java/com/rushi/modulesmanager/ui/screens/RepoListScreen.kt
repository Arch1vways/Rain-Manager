package com.rushi.modulesmanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.TranslationState
import com.rushi.modulesmanager.data.InstalledPackageVersion
import com.rushi.modulesmanager.data.ModuleVersion
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.SortMode
import com.rushi.modulesmanager.data.TranslationLanguages
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.data.formatDate
import com.rushi.modulesmanager.data.formatRelativeTime
import com.rushi.modulesmanager.data.parseInstant
import com.rushi.modulesmanager.ui.components.CountedFilterChip
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.ErrorBanner
import com.rushi.modulesmanager.ui.components.ForkBadge
import com.rushi.modulesmanager.ui.components.LoadingState
import com.rushi.modulesmanager.ui.components.ModuleAvatar
import com.rushi.modulesmanager.ui.components.ModulesSearchBar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.ScrollToTopFab
import com.rushi.modulesmanager.ui.components.SileoInstallButton
import com.rushi.modulesmanager.ui.components.SileoModuleIcon
import com.rushi.modulesmanager.ui.components.SortGridCard
import com.rushi.modulesmanager.ui.components.StarBadge
import com.rushi.modulesmanager.ui.components.UninstallIconButton
import com.rushi.modulesmanager.ui.components.VersionPill
import com.rushi.modulesmanager.ui.components.XposedApiPill
import com.rushi.modulesmanager.ui.components.clearFocusOnTapOutside
import com.rushi.modulesmanager.ui.components.favouriteColor
import com.rushi.modulesmanager.ui.components.starAccentColor
import com.rushi.modulesmanager.ui.components.VerticalScrollbar
import com.rushi.modulesmanager.ui.components.shortVersion
import com.rushi.modulesmanager.ui.theme.LocalAmoled
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun RepoListScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
    onOpenDeveloper: (String) -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val updatesFirst by viewModel.updatesFirst.collectAsStateWithLifecycle()
    val installedVersions by viewModel.installedVersions.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val translations by viewModel.translations.collectAsStateWithLifecycle()
    val translateLanguage by viewModel.translateLanguage.collectAsStateWithLifecycle()
    val descriptionLanguages by viewModel.descriptionLanguages.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val dataUpdatedAt by viewModel.dataUpdatedAt.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val sourceAlternatives by viewModel.sourceAlternatives.collectAsStateWithLifecycle()
    val download by viewModel.downloads.collectAsStateWithLifecycle()

    val strings = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }

    val installedByPackage = installedVersions
    val updatesByPackage = remember(updates) { updates.associateBy { it.installed.packageName } }
    // Chip counts follow the search, so they describe the results the user is looking at.
    val searched = remember(modules, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            modules
        } else {
            modules.filter { module ->
                module.displayName.lowercase().contains(needle) ||
                    module.packageName.lowercase().contains(needle) ||
                    module.summary?.lowercase()?.contains(needle) == true
            }
        }
    }
    val installedInRepo = remember(searched, installedByPackage) {
        searched.count { installedByPackage.containsKey(it.packageName) }
    }
    val updatesInSearch = remember(searched, updatesByPackage) {
        searched.count { updatesByPackage.containsKey(it.packageName) }
    }
    val notInstalledInRepo = searched.size - installedInRepo
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Skip the first run so returning from a module page keeps the scroll position.
    var filtersReady by remember { mutableStateOf(false) }
    LaunchedEffect(query, sortMode, updatesFirst, channel) {
        if (filtersReady) listState.scrollToItem(0) else filtersReady = true
    }

    // A new index file means the restored position belongs to older data, and the
    // update time is worth announcing on launch and after a refresh.
    var lastDataStamp by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    LaunchedEffect(dataUpdatedAt, refreshing) {
        // The cached timestamp is published before the network refresh. Wait for it
        // to settle so a cold start announces only the current index time.
        if (refreshing) return@LaunchedEffect
        val stamp = dataUpdatedAt ?: return@LaunchedEffect
        val previous = lastDataStamp
        if (previous != null && stamp != previous) {
            listState.scrollToItem(0)
        }
        if (previous == null || stamp != previous) {
            Toast.makeText(
                context,
                strings.listUpdated(formatRelativeTime(stamp)),
                Toast.LENGTH_SHORT,
            ).show()
        }
        lastDataStamp = stamp
    }

    val filtered = remember(
        searched,
        sortMode,
        updatesFirst,
        updatesByPackage,
        sourceAlternatives,
        channel,
    ) {
        val baseComparator = when (sortMode) {
            SortMode.NAME -> compareBy<RepoModule> { it.displayName.lowercase() }
            SortMode.UPDATED -> compareByDescending<RepoModule> {
                parseInstant(it.latestTime(channel)) ?: parseInstant(it.updatedAt) ?: Instant.EPOCH
            }.thenBy { it.displayName.lowercase() }
            SortMode.ADDED -> compareByDescending<RepoModule> {
                parseInstant(it.addedAt) ?: parseInstant(it.latestTime(channel)) ?: parseInstant(it.updatedAt) ?: Instant.EPOCH
            }.thenBy { it.displayName.lowercase() }
            SortMode.STARS -> compareByDescending<RepoModule> { it.stargazerCount }
                .thenBy { it.displayName.lowercase() }
            SortMode.SOURCES -> compareByDescending<RepoModule> { sourceAlternatives[it.packageName]?.size ?: 1 }
                .thenBy { it.displayName.lowercase() }
        }
        val finalComparator = if (updatesFirst) {
            compareBy<RepoModule> { if (updatesByPackage.containsKey(it.packageName)) 0 else 1 }
                .then(baseComparator)
        } else {
            baseComparator
        }
        searched.sortedWith(finalComparator)
    }


    val focusManager = LocalFocusManager.current
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
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
                placeholder = strings.searchModules,
                onBoundsChanged = { searchBounds = it },
            )

            error?.let { message ->
                ErrorBanner(message = message, onRetry = viewModel::refresh)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        loading && modules.isEmpty() -> LoadingState(strings.loadingModules)
                        else -> LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "sort_header_grid") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = strings.sortByTitle,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = {
                                            scope.launch { listState.scrollToItem(0) }
                                            viewModel.refresh()
                                        }) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                                        }
                                        IconButton(onClick = onOpenLibrary) {
                                            Icon(Icons.Outlined.Bookmarks, contentDescription = "Library")
                                        }
                                        IconButton(onClick = onOpenSettings) {
                                            Icon(Icons.Outlined.Settings, contentDescription = strings.settingsTitle)
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        SortGridCard(
                                            icon = Icons.Outlined.SortByAlpha,
                                            label = strings.sortCardName,
                                            selected = sortMode == SortMode.NAME,
                                            onClick = { viewModel.setSortMode(SortMode.NAME) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        SortGridCard(
                                            icon = Icons.Outlined.Schedule,
                                            label = strings.sortCardAdded,
                                            selected = sortMode == SortMode.ADDED,
                                            onClick = { viewModel.setSortMode(SortMode.ADDED) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        SortGridCard(
                                            icon = Icons.Outlined.StarOutline,
                                            label = strings.sortCardStars,
                                            selected = sortMode == SortMode.STARS,
                                            onClick = { viewModel.setSortMode(SortMode.STARS) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        SortGridCard(
                                            icon = Icons.Outlined.History,
                                            label = strings.sortCardUpdated,
                                            selected = sortMode == SortMode.UPDATED,
                                            onClick = { viewModel.setSortMode(SortMode.UPDATED) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            if (filtered.isEmpty()) {
                                item(key = "empty_state") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (modules.isEmpty()) {
                                            EmptyState(
                                                icon = Icons.Outlined.ErrorOutline,
                                                title = strings.repoUnavailable,
                                                body = error ?: strings.noModulesFoundInRepo,
                                                actionLabel = strings.retry,
                                                onAction = viewModel::refresh,
                                            )
                                        } else {
                                            EmptyState(
                                                icon = Icons.Outlined.Extension,
                                                title = strings.nothingHere,
                                                body = strings.noModuleMatches(query),
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = filtered,
                                    key = { it.packageName },
                                    contentType = { "module" },
                                ) { module ->
                                    ModuleCard(
                                        module = module,
                                        latest = module.latest(channel),
                                        installed = installedByPackage[module.packageName],
                                        update = updatesByPackage[module.packageName],
                                        channel = channel,
                                        translation = translations[module.packageName],
                                        showTranslate = !TranslationLanguages.matches(
                                            descriptionLanguages[module.packageName],
                                            translateLanguage,
                                        ),
                                        onTranslate = { viewModel.translate(module) },
                                        onShowOriginal = { viewModel.showOriginal(module) },
                                        sourceCount = sourceAlternatives[module.packageName]?.size ?: 1,
                                        favourite = module.packageName in favourites,
                                        viewModel = viewModel,
                                        onInstall = if (module.hasReleases) {
                                            {
                                                val release = module.releaseFor(channel)
                                                val inst = installedByPackage[module.packageName]
                                                val upd = updatesByPackage[module.packageName]
                                                val canLaunch = inst != null && viewModel.canLaunch(module.packageName)
                                                when {
                                                    upd != null -> viewModel.installRelease(module, release)
                                                    inst != null && canLaunch -> viewModel.openInstalledModule(module.packageName)
                                                    inst != null -> viewModel.uninstall(module.packageName)
                                                    release != null -> viewModel.installRelease(module, release)
                                                    else -> onOpenModule(module.packageName)
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        onToggleFavourite = {
                                            viewModel.toggleFavourite(module.packageName)
                                        },
                                        onOpenDeveloper = onOpenDeveloper,
                                        onOpenRelease = viewModel::openLink,
                                        onClick = { onOpenModule(module.packageName) },
                                    )
                                }
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 8.dp, bottom = 80.dp),
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
}

/** A compact horizontal row of modules above the full list. */
@Composable
private fun ModuleShelf(
    title: String,
    modules: List<RepoModule>,
    channel: UpdateChannel,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    onOpen: (String) -> Unit,
    showStars: Boolean = false,
) {
    if (modules.isEmpty()) return
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(modules, key = { it.packageName }) { module ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = amoledCardContainerColor(),
                    ),
                    border = amoledCardBorder(),
                    modifier = Modifier
                        .width(170.dp)
                        .height(74.dp)
                        .clickable { onOpen(module.packageName) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ModuleAvatar(module, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            Text(
                                text = module.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (showStars) {
                                    val gold = starAccentColor()
                                    Icon(
                                        imageVector = Icons.Outlined.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = gold,
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = module.stargazerCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = gold,
                                    )
                                } else {
                                    val time = parseInstant(module.latestTime(channel))
                                    Text(
                                        text = time?.toEpochMilli()?.let(::formatRelativeTime)
                                            ?: "No release",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        val favourite = module.packageName in favourites
                        IconButton(
                            onClick = { onToggleFavourite(module.packageName) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (favourite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = if (favourite) {
                                    "Remove from favourites"
                                } else {
                                    "Add to favourites"
                                },
                                tint = if (favourite) {
                                    favouriteColor()
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The description line, swapped for its translation while one is showing. */
@Composable
private fun DescriptionBlock(
    summary: String,
    translation: TranslationState?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val translated = translation?.text
    val displayText = translated ?: summary
    val canExpand = displayText.length > 130
    Spacer(Modifier.height(6.dp))
    Text(
        text = displayText,
        style = MaterialTheme.typography.bodySmall,
        color = if (translated != null) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
    )
    if (canExpand || expanded) {
        val strings = LocalStrings.current
        Text(
            text = if (expanded) strings.showLess else strings.readMore,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable(onClick = onToggle),
        )
    }
}

/** The translate toggle shown beside the date. */
@Composable
private fun TranslateAction(
    translated: Boolean,
    loading: Boolean,
    onTranslate: () -> Unit,
    onShowOriginal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clickable(enabled = !loading) {
                if (translated) onShowOriginal() else onTranslate()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = if (translated) "Show original" else "Translate",
                tint = if (translated) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun ModuleCard(
    module: RepoModule,
    latest: ModuleVersion?,
    installed: InstalledPackageVersion?,
    update: ModuleUpdate?,
    channel: UpdateChannel,
    translation: TranslationState?,
    showTranslate: Boolean,
    onTranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    sourceCount: Int = 1,
    favourite: Boolean,
    showMoreMenu: Boolean = true,
    viewModel: AppViewModel? = null,
    onInstall: (() -> Unit)? = null,
    onToggleFavourite: () -> Unit,
    onOpenDeveloper: (String) -> Unit,
    onOpenRelease: (String) -> Unit,
    onClick: () -> Unit,
) {
    var descriptionExpanded by rememberSaveable(module.packageName) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = amoledCardContainerColor()),
        border = amoledCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel != null) {
                SileoModuleIcon(module, viewModel, size = 44.dp, cornerRadius = 11.dp)
            } else {
                ModuleAvatar(module, size = 44.dp)
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(initialDelayMillis = 1500),
                    )
                    if (onInstall != null) {
                        val canLaunch = installed != null &&
                            (viewModel?.canLaunch(module.packageName) == true)
                        val strings = LocalStrings.current
                        val activeDownload = viewModel?.downloads?.collectAsStateWithLifecycle()?.value
                        val uninstallingPkg = viewModel?.uninstallingPackage?.collectAsStateWithLifecycle()?.value
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
                            Spacer(Modifier.width(6.dp))
                            UninstallIconButton(uninstalling = true, onClick = {})
                        } else if (installed != null || update != null) {
                            if (update != null || canLaunch) {
                                val label = if (update != null) strings.update else strings.open
                                Spacer(Modifier.width(6.dp))
                                SileoInstallButton(
                                    text = label,
                                    isPrimary = update != null,
                                    isSecondary = installed != null && update == null,
                                    busy = busy,
                                    progress = progress,
                                    onCancel = { viewModel?.cancelDownload() },
                                    onClick = onInstall,
                                )
                            }
                            if (installed != null && !busy && viewModel != null) {
                                Spacer(Modifier.width(4.dp))
                                UninstallIconButton(
                                    uninstalling = false,
                                    onClick = { viewModel.uninstall(module.packageName) },
                                )
                            }
                        } else {
                            Spacer(Modifier.width(6.dp))
                            SileoInstallButton(
                                text = strings.install,
                                isPrimary = false,
                                isSecondary = false,
                                busy = busy,
                                progress = progress,
                                onCancel = { viewModel?.cancelDownload() },
                                onClick = onInstall,
                            )
                        }
                    }
                    if (showMoreMenu) {
                        val strings = LocalStrings.current
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
                                module.developerLogin()?.let { developer ->
                                    DropdownMenuItem(
                                        text = { Text(strings.moreByDeveloper(developer)) },
                                        onClick = {
                                            menuOpen = false
                                            onOpenDeveloper(developer)
                                        },
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(strings.copyPackageName) },
                                    onClick = {
                                        menuOpen = false
                                        val clipboard = context.getSystemService(
                                            ClipboardManager::class.java,
                                        )
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText("package name", module.packageName),
                                        )
                                        Toast.makeText(
                                            context,
                                            strings.packageNameCopied,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                                module.releasesPageUrl()?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text(strings.openReleasesPage) },
                                        onClick = {
                                            menuOpen = false
                                            onOpenRelease(url)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                module.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    DescriptionBlock(
                        summary = summary,
                        translation = translation,
                        expanded = descriptionExpanded,
                        onToggle = { descriptionExpanded = !descriptionExpanded },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fallbackVer = latest?.name
                        ?: module.releaseFor(channel)?.let { it.name ?: it.tagName }
                    val verText = when {
                        update != null -> "v${shortVersion(update.installed.versionName)} \u2192 v${shortVersion(update.version.name)}"
                        installed != null -> "v${shortVersion(installed.versionName)}".trim()
                        fallbackVer != null -> shortVersion(fallbackVer).takeIf { it.isNotBlank() }?.let { "v$it" }
                        else -> null
                    }
                    val rawDate = module.latestTime(channel) ?: module.updatedAt
                    val date = remember(module.packageName, rawDate) { formatDate(rawDate) }
                    val stars = if (module.stargazerCount > 0) "\u2605 ${module.stargazerCount}" else null
                    val metaText = listOfNotNull(verText, stars, date).joinToString(" · ")
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val translated = translation?.text != null
                    if (showTranslate || translated) {
                        Spacer(Modifier.width(8.dp))
                        TranslateAction(
                            translated = translated,
                            loading = translation?.loading == true,
                            onTranslate = onTranslate,
                            onShowOriginal = onShowOriginal,
                        )
                    }
                }
            }
        }
    }
}
