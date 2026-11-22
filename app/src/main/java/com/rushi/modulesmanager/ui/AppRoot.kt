package com.rushi.modulesmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.data.ApkVariants
import com.rushi.modulesmanager.data.formatBytes
import com.rushi.modulesmanager.ui.components.BatchUpdateBanner
import com.rushi.modulesmanager.ui.components.DownloadBanner
import com.rushi.modulesmanager.ui.components.SaveBanner
import com.rushi.modulesmanager.ui.screens.DeveloperScreen
import com.rushi.modulesmanager.ui.screens.DiagnosticsScreen
import com.rushi.modulesmanager.ui.screens.InstalledScreen
import com.rushi.modulesmanager.ui.screens.LibraryScreen
import com.rushi.modulesmanager.ui.screens.ModuleDetailScreen
import com.rushi.modulesmanager.ui.screens.RepoListScreen
import com.rushi.modulesmanager.ui.screens.SettingsScreen
import com.rushi.modulesmanager.ui.screens.SourcesScreen
import com.rushi.modulesmanager.ui.theme.LocalAmoled

private enum class Destination(val route: String, val icon: ImageVector) {
    MODULES("modules", Icons.Outlined.Extension),
    SOURCES("sources", Icons.Outlined.Source),
    INSTALLED("installed", Icons.Outlined.Apps),
}

@Composable
private fun Destination.localizedLabel(): String {
    val s = LocalStrings.current
    return when (this) {
        Destination.MODULES -> s.navModules
        Destination.SOURCES -> s.navSources
        Destination.INSTALLED -> s.navInstalled
    }
}

/**
 * The notification permission only gates the download progress notification, so ask when there
 * is an active download rather than on every cold start. The prompt is capped to one per
 * composition lifetime; Settings always offers the system notification controls afterwards.
 */
@Composable
private fun RequestNotificationPermissionOnDownload(downloading: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(downloading) {
        if (!downloading || asked) return@LaunchedEffect
        asked = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val download by viewModel.downloads.collectAsStateWithLifecycle()
    val save by viewModel.apkSave.collectAsStateWithLifecycle()
    val batch by viewModel.batchUpdate.collectAsStateWithLifecycle()
    val activeBatch = batch?.takeIf { it.running || it.interrupted }
    RequestNotificationPermissionOnDownload(
        downloading = download != null || save != null || batch?.running == true,
    )
    val installed by viewModel.allInstalled.collectAsStateWithLifecycle()
    val variantChoice by viewModel.variantChoice.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val strings = remember(appLanguage) { stringsFor(appLanguage) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pagerState = rememberPagerState(pageCount = { Destination.entries.size })
    val pagerScope = rememberCoroutineScope()
    val currentIsTab = currentRoute == "tabs"
    val showBottomBar = currentIsTab

    fun selectTab(destination: Destination) {
        if (currentRoute?.startsWith("module/") == true) {
            viewModel.closeModule()
        }
        if (!currentIsTab) {
            navController.popBackStack("tabs", inclusive = false)
        }
        pagerScope.launch {
            pagerState.animateScrollToPage(
                page = destination.ordinal,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 700f,
                ),
            )
        }
    }

    BackHandler(enabled = currentIsTab && pagerState.currentPage != 0) {
        pagerScope.launch {
            pagerState.animateScrollToPage(
                page = 0,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 700f,
                ),
            )
        }
    }

    var bottomBarVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(currentRoute, pagerState.currentPage) {
        bottomBarVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.deepLinkNav.collect { target ->
            if (!currentIsTab) {
                navController.popBackStack("tabs", inclusive = false)
            }
            when (target) {
                AppViewModel.DeepLinkNav.Sources -> {
                    pagerState.animateScrollToPage(Destination.SOURCES.ordinal)
                }
                is AppViewModel.DeepLinkNav.Module -> {
                    navController.navigate("module/${target.packageName}")
                }
            }
        }
    }
    val navScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    bottomBarVisible = false
                } else if (available.y > 10f) {
                    bottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
    BoxWithConstraints {
        // Tablets get the rail. Phones keep the same bottom navigation in portrait and
        // landscape; the smallest width tells a tablet apart from a large phone window.
        val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
            if (wide) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    activeBatch?.let { state ->
                        BatchUpdateBanner(
                            state = state,
                            onCancel = viewModel::cancelBatch,
                            onDismiss = viewModel::dismissBatch,
                        )
                    }
                    save?.let { state ->
                        SaveBanner(state = state, onCancel = viewModel::cancelSave)
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        // Keep the rail beside module and developer pages; only diagnostics
                        // takes the full window.
                        if (currentRoute != "diagnostics") {
                            NavigationRail {
                                Destination.entries.forEach { destination ->
                                    NavigationRailItem(
                                        selected = currentIsTab && pagerState.currentPage == destination.ordinal,
                                        onClick = { selectTab(destination) },
                                        icon = { DestinationIcon(destination, installed.size) },
                                        label = { Text(destination.localizedLabel()) },
                                    )
                                }
                            }
                        }
                        AppNavHost(
                            navController = navController,
                            pagerState = pagerState,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                AppNavHost(
                    navController = navController,
                    pagerState = pagerState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .padding(padding)
                        .nestedScroll(navScrollConnection),
                )
            }
            }
            if (!wide) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    if (!showBottomBar) {
                        activeBatch?.let { state ->
                            Box(modifier = Modifier.navigationBarsPadding()) {
                                BatchUpdateBanner(
                                    state = state,
                                    onCancel = viewModel::cancelBatch,
                                    onDismiss = viewModel::dismissBatch,
                                )
                            }
                        }
                        save?.let { state ->
                            Box(modifier = Modifier.navigationBarsPadding()) {
                                SaveBanner(state = state, onCancel = viewModel::cancelSave)
                            }
                        }
                    } else {
                        activeBatch?.let { state ->
                            BatchUpdateBanner(
                                state = state,
                                onCancel = viewModel::cancelBatch,
                                onDismiss = viewModel::dismissBatch,
                            )
                        }
                        save?.let { state ->
                            SaveBanner(state = state, onCancel = viewModel::cancelSave)
                        }
                        AnimatedVisibility(
                            visible = bottomBarVisible,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            FloatingNavBar(
                                pagerState = pagerState,
                                installedCount = installed.size,
                                onSelect = ::selectTab,
                            )
                        }
                    }
                }
            }
        }
    }

    variantChoice?.let { choice ->
        val s = LocalStrings.current
        AlertDialog(
            onDismissRequest = viewModel::cancelVariantChoice,
            title = { Text(s.chooseApkTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${choice.module.displayName}\n${s.chooseApkDesc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    choice.assets.forEach { asset ->
                        TextButton(
                            onClick = { viewModel.installVariant(asset) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asset.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val details = buildList {
                                    addAll(ApkVariants.hints(asset))
                                    if (asset.size > 0) add(formatBytes(asset.size))
                                    if (asset.downloadCount > 0) {
                                        add(s.downloadsCount(asset.downloadCount))
                                    }
                                }.joinToString(" - ")
                                if (details.isNotEmpty()) {
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelVariantChoice) { Text(s.cancel) }
            },
        )
    }
    }
}

@Composable
private fun FloatingNavBar(
    pagerState: PagerState,
    installedCount: Int,
    onSelect: (Destination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Drawn before the insets so the fade reaches the bottom of the screen, not the top
            // of the gesture area. The page under the pill fades into the background instead of
            // ending on a hard edge.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val amoled = LocalAmoled.current
        val pagePosProvider = remember(pagerState) {
            {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, (Destination.entries.size - 1).toFloat())
            }
        }
        val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
        val barContainerColor = if (amoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(50),
            color = barContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (amoled) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else {
                null
            },
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(4.dp)
                    .drawBehind {
                        val count = Destination.entries.size
                        val slotWidth = size.width / count
                        val minX = pagePosProvider() * slotWidth
                        val radius = size.height / 2f
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(minX, 0f),
                            size = Size(slotWidth, size.height),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { destination ->
                    FloatingNavItem(
                        destination = destination,
                        selected = pagerState.targetPage == destination.ordinal,
                        pagePosProvider = pagePosProvider,
                        installedCount = installedCount,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    destination: Destination,
    selected: Boolean,
    pagePosProvider: () -> Float,
    installedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "navItemContent",
    )
    val density = LocalDensity.current
    val iconShiftPx = with(density) { 8.dp.toPx() }
    val labelStartY = with(density) { 23.dp.toPx() }
    val labelEndY = with(density) { 12.5.dp.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val label = destination.localizedLabel()
    val index = destination.ordinal

    BoxWithConstraints(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(50))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val availWidthPx = with(density) { (maxWidth - 6.dp).toPx().coerceAtLeast(1f) }
        var textNaturalWidthPx by remember(label) { mutableFloatStateOf(0f) }
        val fitScale = if (textNaturalWidthPx > availWidthPx && textNaturalWidthPx > 0f) {
            (availWidthPx / textNaturalWidthPx).coerceIn(0.62f, 1f)
        } else {
            1f
        }

        Box(
            modifier = Modifier.graphicsLayer {
                val p = (1f - abs(index - pagePosProvider())).coerceIn(0f, 1f)
                translationY = lerp(0f, -iconShiftPx, p)
                val s = lerp(1f, 1.05f, p)
                scaleX = s
                scaleY = s
            },
        ) {
            DestinationIcon(
                destination = destination,
                installedCount = installedCount,
                tint = content,
            )
        }
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.15).sp,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            onTextLayout = { result ->
                textNaturalWidthPx = result.size.width.toFloat()
            },
            modifier = Modifier.graphicsLayer {
                val p = (1f - abs(index - pagePosProvider())).coerceIn(0f, 1f)
                alpha = ((p - 0.12f) / 0.88f).coerceIn(0f, 1f)
                translationY = lerp(labelStartY, labelEndY, p)
                val s = lerp(0.86f, 1f, p) * fitScale
                scaleX = s
                scaleY = s
            },
        )
    }
}

@Composable
private fun DestinationIcon(
    destination: Destination,
    installedCount: Int,
    tint: Color? = null,
) {
    val iconTint = tint ?: LocalContentColor.current
    if (destination == Destination.INSTALLED && installedCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(installedCount.toString())
                }
            },
        ) {
            Icon(destination.icon, contentDescription = null, tint = iconTint)
        }
    } else {
        Icon(destination.icon, contentDescription = null, tint = iconTint)
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    pagerState: PagerState,
    viewModel: AppViewModel,
    modifier: Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "tabs",
        modifier = modifier,
    ) {
        composable("tabs") {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 2,
                key = { Destination.entries[it].route },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (Destination.entries[page]) {
                    Destination.MODULES -> RepoListScreen(
                        viewModel = viewModel,
                        onOpenModule = { packageName -> navController.navigate("module/$packageName") },
                        onOpenDeveloper = { developer ->
                            navController.navigate("developer/${Uri.encode(developer)}")
                        },
                        onOpenLibrary = { navController.navigate("library") },
                        onOpenSettings = { navController.navigate("settings") },
                    )
                    Destination.SOURCES -> SourcesScreen(
                        viewModel = viewModel,
                        onOpenDeveloper = { developer ->
                            navController.navigate("developer/${Uri.encode(developer)}")
                        },
                    )
                    Destination.INSTALLED -> InstalledScreen(
                        viewModel = viewModel,
                        onOpenModule = { packageName -> navController.navigate("module/$packageName") },
                        onOpenDeveloper = { developer ->
                            navController.navigate("developer/${Uri.encode(developer)}")
                        },
                    )
                }
            }
        }
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onOpenModule = { packageName -> navController.navigate("module/$packageName") },
                onOpenDeveloper = { developer ->
                    navController.navigate("developer/${Uri.encode(developer)}")
                },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate("diagnostics") },
            )
        }
        composable("diagnostics") {
            DiagnosticsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("developer/{developer}") { entry ->
            val developer = entry.arguments?.getString("developer").orEmpty()
            DeveloperScreen(
                viewModel = viewModel,
                developer = developer,
                onBack = { navController.popBackStack() },
                onOpenModule = { packageName -> navController.navigate("module/$packageName") },
                onOpenDeveloper = { owner ->
                    navController.navigate("developer/${Uri.encode(owner)}")
                },
            )
        }
        composable("module/{packageName}") { entry ->
            val packageName = entry.arguments?.getString("packageName").orEmpty()
            ModuleDetailScreen(
                viewModel = viewModel,
                packageName = packageName,
                onBack = {
                    viewModel.closeModule()
                    navController.popBackStack()
                },
                onOpenDeveloper = { developer ->
                    navController.navigate("developer/${Uri.encode(developer)}")
                },
            )
        }
    }
}
