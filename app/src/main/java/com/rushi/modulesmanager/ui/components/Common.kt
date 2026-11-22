package com.rushi.modulesmanager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.BatchPhase
import com.rushi.modulesmanager.BatchUpdateState
import com.rushi.modulesmanager.ui.LocalStrings
import com.rushi.modulesmanager.data.ApkSaveCenter
import com.rushi.modulesmanager.data.DownloadCenter
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.formatBytes
import com.rushi.modulesmanager.ui.theme.LocalAmoled
import com.rushi.modulesmanager.ui.theme.amoledCardBorder
import com.rushi.modulesmanager.ui.theme.amoledCardContainerColor
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ModuleAvatar(module: RepoModule, size: Dp = 52.dp) {
    val shape = RoundedCornerShape(size * 0.32f)
    val avatarUrl = module.avatarUrl()
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape),
    ) {
        Identicon(seed = module.packageName, modifier = Modifier.matchParentSize())
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Sileo-style module icon that loads the real app icon directly into memory (without saving
 * files to disk), falling back to the repository avatar / identicon when unavailable.
 */
@Composable
fun SileoModuleIcon(
    module: RepoModule,
    viewModel: AppViewModel,
    size: Dp = 54.dp,
    cornerRadius: Dp = size * 0.26f,
    onAccentColor: ((Color) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    var iconBitmap by remember(module.packageName, module.latestRelease) {
        mutableStateOf<ImageBitmap?>(viewModel.cachedModuleIcon(module))
    }
    LaunchedEffect(module.packageName, module.latestRelease) {
        if (iconBitmap == null) {
            delay(30)
            iconBitmap = viewModel.loadModuleIcon(module)
        }
        viewModel.moduleAccentColor(module.packageName)?.let { argb ->
            onAccentColor?.invoke(Color(argb))
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape),
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = module.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            ModuleAvatar(module = module, size = size)
        }
    }
}

/**
 * Material You pill button ("Install" / "Update" / "Open") in the style of Sileo's GET button,
 * smoothly morphing into an animated filling circular progress ring during download/install.
 */
@Composable
fun SileoInstallButton(
    text: String,
    isPrimary: Boolean = false,
    isSecondary: Boolean = false,
    isDestructive: Boolean = false,
    busy: Boolean = false,
    progress: Float? = null,
    enabled: Boolean = true,
    onCancel: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        isDestructive -> MaterialTheme.colorScheme.errorContainer
        isPrimary -> MaterialTheme.colorScheme.primary
        isSecondary -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isDestructive -> MaterialTheme.colorScheme.onErrorContainer
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        isSecondary -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    AnimatedContent(
        targetState = busy,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.82f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(160)) + scaleOut(targetScale = 0.82f, animationSpec = tween(160)))
        },
        label = "sileo_install_btn",
        modifier = modifier,
    ) { isBusy ->
        if (isBusy) {
            val ringColor = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            val ringBg = if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ringBg)
                    .then(if (onCancel != null) Modifier.clickable(onClick = onCancel) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (progress != null) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        animationSpec = tween(220),
                        label = "install_ring_progress",
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.5.dp,
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.5.dp,
                        color = ringColor,
                    )
                }
                if (onCancel != null) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = LocalStrings.current.cancel,
                        tint = ringColor,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                modifier = Modifier.height(30.dp),
            ) {
                Text(
                    text = text.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Trash icon button that morphs into a red spinning circular indicator after the package is uninstalled.
 */
@Composable
fun UninstallIconButton(
    uninstalling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uninstalling,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.82f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(160)) + scaleOut(targetScale = 0.82f, animationSpec = tween(160)))
        },
        label = "uninstall_icon_btn",
        modifier = modifier,
    ) { isUninstalling ->
        if (isUninstalling) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            androidx.compose.material3.IconButton(
                onClick = onClick,
                modifier = Modifier.size(34.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = LocalStrings.current.uninstall,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun InstalledAvatar(module: InstalledModule, size: Dp = 52.dp) {
    val shape = RoundedCornerShape(size * 0.32f)
    val iconBitmap = remember(module.packageName) {
        module.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape),
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Identicon(seed = module.packageName, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
fun HeaderDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

private val VERSION_NUMBER = Regex("""\d+(?:\.\d+)+""")
private val ANY_NUMBER = Regex("""\d+""")
private val BUILD_PREFIX = Regex("""^\d+-(.+)$""")

/** Extracts a clean display version, preferring dotted semver (e.g. "1.0.4") over build prefixes. */
fun shortVersion(version: String?): String {
    var value = version?.trim().orEmpty()
    if (value.isEmpty()) return value
    BUILD_PREFIX.matchEntire(value)?.let { value = it.groupValues[1].trim() }
    value = value.removePrefix("v").removePrefix("V")
    return VERSION_NUMBER.find(value)?.value
        ?: ANY_NUMBER.find(value)?.value
        ?: value.take(12)
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/** The GitHub-style green outline used for a module's version. */
@Composable
fun VersionPill(text: String) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val border = if (dark) Color(0xFF238636) else Color(0xFF2DA44E)
    val content = if (dark) Color(0xFF3FB950) else Color(0xFF1A7F37)
    Surface(
        color = Color.Transparent,
        contentColor = content,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/** Target libxposed API level, or Legacy for modules without one. */
@Composable
fun XposedApiPill(apiVersion: Int?) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val modern = apiVersion != null
    val content = when {
        !modern -> MaterialTheme.colorScheme.onSurfaceVariant
        dark -> Color(0xFF58A6FF)
        else -> Color(0xFF0969DA)
    }
    val border = when {
        !modern -> MaterialTheme.colorScheme.outlineVariant
        dark -> Color(0xFF1F6FEB)
        else -> Color(0xFF0969DA)
    }
    Surface(
        color = Color.Transparent,
        contentColor = content,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = apiVersion?.let { "API $it" } ?: "Legacy",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun starAccentColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) Color(0xFFE3B341) else Color(0xFF9A6700)
}

@Composable
fun favouriteColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) Color(0xFFF85149) else Color(0xFFCF222E)
}

@Composable
fun StarBadge(count: Int, modifier: Modifier = Modifier) {
    val gold = starAccentColor()
    CountBadge(
        count = count,
        imageVector = Icons.Outlined.Star,
        contentColor = gold,
        borderColor = gold.copy(alpha = 0.55f),
        modifier = modifier,
    )
}

@Composable
fun ForkBadge(count: Int, modifier: Modifier = Modifier) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    CountBadge(
        count = count,
        imageVector = Icons.Outlined.ForkRight,
        contentColor = gray,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier,
    )
}

@Composable
private fun CountBadge(
    count: Int,
    imageVector: ImageVector,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = contentColor,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatStarCount(count),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 25129 becomes 25.1k, so the badge stays narrow on a card. */
fun formatStarCount(count: Int): String = when {
    count >= 1_000_000 -> compact(count / 1_000_000.0) + "M"
    count >= 1_000 -> compact(count / 1_000.0) + "k"
    else -> count.toString()
}

private fun compact(value: Double): String {
    val text = if (value >= 100) {
        "%.0f".format(Locale.US, value)
    } else {
        "%.1f".format(Locale.US, value)
    }
    return text.removeSuffix(".0")
}

@Composable
fun PackageLabel(packageName: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Text(
            text = packageName,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(LocalStrings.current.retry) }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DownloadBanner(
    state: DownloadCenter.State,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val amoled = LocalAmoled.current
    Surface(
        color = if (amoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (amoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        state.paused -> Icons.Outlined.Pause
                        state.phase == DownloadCenter.Phase.DOWNLOADING -> Icons.Outlined.Download
                        state.phase == DownloadCenter.Phase.VERIFYING -> Icons.Outlined.Verified
                        else -> Icons.Outlined.InstallMobile
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            state.paused -> {
                                val total = if (state.total > 0) formatBytes(state.total) else "unknown size"
                                "Paused - ${formatBytes(state.downloaded)} of $total"
                            }
                            state.phase == DownloadCenter.Phase.DOWNLOADING -> {
                                val total = if (state.total > 0) formatBytes(state.total) else "unknown size"
                                "${formatBytes(state.downloaded)} of $total"
                            }
                            state.phase == DownloadCenter.Phase.VERIFYING -> "Verifying package..."
                            else -> "Opening the installer..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.phase == DownloadCenter.Phase.DOWNLOADING) {
                    if (state.paused) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume download")
                        }
                    } else {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pause download")
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val fraction = state.fraction
            if (state.phase == DownloadCenter.Phase.DOWNLOADING && fraction == null && !state.paused) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            } else {
                LinearProgressIndicator(
                    progress = { if (state.phase == DownloadCenter.Phase.DOWNLOADING) fraction ?: 0f else 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
fun SaveBanner(
    state: ApkSaveCenter.State,
    onCancel: () -> Unit,
) {
    val amoled = LocalAmoled.current
    Surface(
        color = if (amoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (amoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.phase == ApkSaveCenter.State.Phase.SAVING) {
                        Icons.Outlined.Verified
                    } else {
                        Icons.Outlined.Download
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saving ${state.label}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (state.phase) {
                            ApkSaveCenter.State.Phase.PREPARING -> "Preparing download..."
                            ApkSaveCenter.State.Phase.DOWNLOADING -> {
                                val total = if (state.total > 0) {
                                    formatBytes(state.total)
                                } else {
                                    "unknown size"
                                }
                                "${formatBytes(state.downloaded)} of $total - Download/ModulesManager"
                            }
                            ApkSaveCenter.State.Phase.SAVING ->
                                "Copying to Download/ModulesManager..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.phase != ApkSaveCenter.State.Phase.SAVING) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel save")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val fraction = state.fraction
            if (state.phase == ApkSaveCenter.State.Phase.DOWNLOADING && fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** Batch update progress, styled like the download and message banners. */
@Composable
fun BatchUpdateBanner(
    state: BatchUpdateState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val amoled = LocalAmoled.current
    val fraction = if (state.total > 0) state.finished.toFloat() / state.total else 0f
    val failedItems = state.items.values.filter { it.phase == BatchPhase.FAILED }
    val firstFailure = failedItems.firstOrNull()
    Surface(
        color = if (amoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (amoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.running) {
                        Icons.Outlined.InstallMobile
                    } else {
                        Icons.Outlined.ErrorOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (state.running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !state.running -> "Updates interrupted"
                            state.current != null -> "Installing ${state.current}"
                            else -> "Preparing updates"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("${state.finished} of ${state.total} done")
                            if (failedItems.isNotEmpty()) append(" - ${failedItems.size} failed")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.running && firstFailure != null) {
                        Text(
                            text = "${firstFailure.title}: ${firstFailure.error.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = if (state.running) onCancel else onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = if (state.running) "Cancel update" else "Dismiss",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * Clears focus when the user taps anywhere outside [targetBounds] without consuming child clicks.
 */
fun Modifier.clearFocusOnTapOutside(
    focusManager: FocusManager,
    rootCoordsProvider: () -> LayoutCoordinates?,
    targetBoundsProvider: () -> Rect,
): Modifier = pointerInput(focusManager) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
            if (down != null) {
                val rootPos = rootCoordsProvider()?.localToRoot(down.position)
                if (rootPos != null && !targetBoundsProvider().contains(rootPos)) {
                    focusManager.clearFocus()
                }
            }
        }
    }
}

@Composable
fun ModulesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val amoled = LocalAmoled.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = amoledCardContainerColor(),
            focusedContainerColor = amoledCardContainerColor(),
            unfocusedBorderColor = if (amoled) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun SortGridCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        amoledCardContainerColor()
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = amoledCardBorder(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ScrollToTopFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowUp,
            contentDescription = "Scroll to top",
        )
    }
}

