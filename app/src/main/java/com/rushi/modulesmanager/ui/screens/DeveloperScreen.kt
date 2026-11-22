package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.data.DownloadCenter
import com.rushi.modulesmanager.data.formatRelativeTime
import com.rushi.modulesmanager.data.parseInstant
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.HeaderDivider
import com.rushi.modulesmanager.ui.components.shortVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: AppViewModel,
    developer: String,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenDeveloper: (String) -> Unit,
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val manualModules by viewModel.manualModules.collectAsStateWithLifecycle()
    val installedVersions by viewModel.installedVersions.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    val entries = remember(modules, manualModules, developer) {
        (modules + manualModules.values)
            .distinctBy { it.packageName }
            .filter { it.developerLogin().equals(developer, ignoreCase = true) }
            .sortedBy { it.displayName.lowercase() }
    }
    val updatesByPackage = remember(updates) { updates.associateBy { it.installed.packageName } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = developer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (entries.size == 1) "1 module" else "${entries.size} modules",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                HeaderDivider()
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = strings.noModulesTitle,
                    body = strings.noDeveloperModulesBody,
                )
            }
        } else {
            val uninstallingPkg by viewModel.uninstallingPackage.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.packageName }, contentType = { "module" }) { module ->
                    val packageName = module.packageName
                    val release = module.releaseFor(channel)
                    val installed = installedVersions[packageName]
                    val update = updatesByPackage[packageName]
                    val hasLauncher = installed != null && viewModel.canLaunch(packageName)
                    val busy = downloads?.packageName == packageName
                    val isUninstalling = uninstallingPkg == packageName
                    val showAction = update != null || installed == null || hasLauncher
                    val progress = if (busy) {
                        downloads?.let {
                            if (it.phase == DownloadCenter.Phase.DOWNLOADING) it.fraction else 1f
                        }
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
                        onClick = { onOpenModule(packageName) },
                        onUninstall = { viewModel.uninstall(packageName) },
                        onAction = {
                            when {
                                update != null -> viewModel.installRelease(module, release)
                                installed != null && hasLauncher -> viewModel.openInstalledModule(packageName)
                                release != null -> viewModel.installRelease(module, release)
                                else -> onOpenModule(packageName)
                            }
                        },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
