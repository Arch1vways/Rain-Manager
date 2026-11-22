package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.rushi.modulesmanager.data.GitHubAuthorSource
import com.rushi.modulesmanager.ui.components.StatusPill
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor

@Composable
fun SourcesScreen(
    viewModel: AppViewModel,
    onOpenDeveloper: (String) -> Unit,
) {
    val authorSources by viewModel.authorSources.collectAsStateWithLifecycle()
    val officialModules by viewModel.modules.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val authorRefreshing by viewModel.authorRefreshing.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    var showAddDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    val officialCount = remember(officialModules, authorSources) {
        val customOwners = authorSources.map { it.owner.lowercase() }.toSet()
        officialModules.count { m ->
            val owner = m.collaborators.firstOrNull()?.login?.lowercase()
            owner == null || owner !in customOwners
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.repositoriesTitleCount(1 + authorSources.size),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh sources")
            }
            IconButton(onClick = {
                input = ""
                addError = null
                showAddDialog = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add GitHub source")
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing || authorRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "default_xposed_repo") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = amoledCardContainerColor(),
                        border = amoledCardBorder(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Xposed Modules",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Verified,
                                        contentDescription = "Default",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Text(
                                    text = if (lastUpdated != null) "${strings.modulesCount(officialCount)} · github.com/rushiranpise/xposed-modules" else "github.com/rushiranpise/xposed-modules",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (authorSources.isNotEmpty()) {
                    item(key = "custom_header") {
                        Text(
                            text = strings.githubReposHeader,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        )
                    }
                }

                items(authorSources, key = { it.owner }) { source ->
                    GitHubSourceCard(
                        source = source,
                        onOpen = { onOpenDeveloper(source.owner) },
                        onDelete = { viewModel.removeAuthorSource(source.owner) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!adding) showAddDialog = false },
            title = { Text(strings.addSourceTitle) },
            text = {
                Column {
                    Text(
                        text = strings.addSourceDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            addError = null
                        },
                        singleLine = true,
                        enabled = !adding,
                        label = { Text(strings.githubAuthorLabel) },
                        placeholder = { Text(strings.githubAuthorPlaceholder) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (addError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = addError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !adding && input.isNotBlank(),
                    onClick = {
                        adding = true
                        addError = null
                        viewModel.addAuthorSource(input) { result ->
                            adding = false
                            result.onSuccess {
                                showAddDialog = false
                            }.onFailure { err ->
                                addError = err.message ?: strings.failedToFetchRepos
                            }
                        }
                    },
                ) {
                    if (adding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(strings.addSourceButton)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !adding,
                    onClick = { showAddDialog = false },
                ) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun GitHubSourceCard(
    source: GitHubAuthorSource,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = amoledCardContainerColor(),
        border = amoledCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(source.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = source.owner,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source.error ?: "${source.moduleCount} · ${source.urlLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (source.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove source",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
