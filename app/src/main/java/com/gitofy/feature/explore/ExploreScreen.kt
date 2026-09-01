package com.gitofy.feature.explore

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.remote.dto.Release
import com.gitofy.data.remote.dto.ReleaseAsset
import com.gitofy.data.remote.dto.Repository
import com.gitofy.data.repository.DownloadedFile
import com.gitofy.data.repository.ExploreCacheStore
import com.gitofy.data.repository.ExploreDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val api: GitHubApiService,
    private val downloads: ExploreDownloadRepository,
    private val cacheStore: ExploreCacheStore
) : ViewModel() {
    private val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Repository>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val _details = MutableStateFlow<ExploreRepositoryDetails?>(null)
    val details = _details.asStateFlow()
    private val _detailsLoading = MutableStateFlow(false)
    val detailsLoading = _detailsLoading.asStateFlow()
    private val _detailsError = MutableStateFlow<String?>(null)
    val detailsError = _detailsError.asStateFlow()

    private val _downloadState = MutableStateFlow<ExploreDownloadState>(ExploreDownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    init {
        viewModelScope.launch {
            query.debounce(350).distinctUntilChanged().collectLatest { search(it) }
        }
        // PRD §13/§14: render any cached content immediately (no skeleton),
        // then silently refresh in the background. Only fall back to a
        // genuine loading skeleton when there's nothing cached yet.
        viewModelScope.launch {
            val cached = cacheStore.load()
            if (cached != null) {
                results.value = cached.results
            }
            search("stars:>10000", suppressLoadingIndicator = cached != null)
        }
    }

    fun setQuery(value: String) { query.value = value }

    private suspend fun search(value: String, suppressLoadingIndicator: Boolean = false) {
        if (!suppressLoadingIndicator) loading.value = true
        error.value = null
        val q = value.trim().ifEmpty { "stars:>10000" }
        safeApiCall {
            api.searchRepositories(q, sort = "stars", order = "desc")
        }.fold(
            onSuccess = {
                results.value = it.items
                // Only cache the default browse feed — typed searches are
                // transient and shouldn't overwrite the "last known good" feed.
                if (q == "stars:>10000") {
                    viewModelScope.launch { cacheStore.save(q, it.items) }
                }
            },
            onFailure = { error.value = it.message ?: "Couldn't load GitHub repositories" }
        )
        loading.value = false
    }

    fun openRepository(repo: Repository) {
        _detailsError.value = null
        _detailsLoading.value = true
        _details.value = ExploreRepositoryDetails(repo = repo, release = null)
        viewModelScope.launch {
            downloads.loadLatestRelease(repo.ownerLogin, repo.name).fold(
                onSuccess = { release ->
                    _details.value = ExploreRepositoryDetails(repo, release)
                    _detailsLoading.value = false
                },
                onFailure = { throwable ->
                    _detailsLoading.value = false
                    _detailsError.value = throwable.message ?: "Couldn't load recent release"
                }
            )
        }
    }

    fun closeRepository() {
        _details.value = null
        _detailsError.value = null
        _detailsLoading.value = false
        _downloadState.value = ExploreDownloadState.Idle
    }

    fun downloadApk(asset: ReleaseAsset) {
        val current = _details.value ?: return
        if (_downloadState.value is ExploreDownloadState.Downloading) return
        _downloadState.value = ExploreDownloadState.Downloading(asset.name, 0)
        viewModelScope.launch {
            downloads.downloadApk(current.repo.ownerLogin, current.repo.name, asset) { done, total ->
                // FIX: GitHub's zipball/asset responses don't always send a
                // Content-Length (the zip is generated/streamed on the fly),
                // so `total` can be <= 0. Forcing that into a percentage
                // used to freeze the fill at a stale value that never
                // matched how much had actually downloaded. Now an unknown
                // total is passed through as -1 so the UI can show a real
                // indeterminate spinner instead of a fake, non-matching %.
                val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                _downloadState.value = ExploreDownloadState.Downloading(asset.name, percent)
            }.fold(
                onSuccess = { file -> _downloadState.value = ExploreDownloadState.Completed(file) },
                onFailure = { error -> _downloadState.value = ExploreDownloadState.Failed(error.message ?: "APK download failed") }
            )
        }
    }

    fun downloadSource() {
        val current = _details.value ?: return
        if (_downloadState.value is ExploreDownloadState.Downloading) return
        _downloadState.value = ExploreDownloadState.Downloading("Source code", 0)
        viewModelScope.launch {
            downloads.downloadSourceZip(current.repo.ownerLogin, current.repo, current.release) { done, total ->
                val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                _downloadState.value = ExploreDownloadState.Downloading("Source code", percent)
            }.fold(
                onSuccess = { file -> _downloadState.value = ExploreDownloadState.Completed(file) },
                onFailure = { error -> _downloadState.value = ExploreDownloadState.Failed(error.message ?: "Source download failed") }
            )
        }
    }

    fun consumeDownloadState() {
        _downloadState.value = ExploreDownloadState.Idle
    }
}

data class ExploreRepositoryDetails(
    val repo: Repository,
    val release: Release?
) {
    val apkAsset: ReleaseAsset? get() = release?.assets?.firstOrNull { it.name.endsWith(".apk", true) }
}

sealed interface ExploreDownloadState {
    data object Idle : ExploreDownloadState
    data class Downloading(val name: String, val progress: Int) : ExploreDownloadState
    data class Completed(val file: DownloadedFile) : ExploreDownloadState
    data class Failed(val message: String) : ExploreDownloadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onRepoClick: (String, String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val details by viewModel.details.collectAsState()
    val detailsLoading by viewModel.detailsLoading.collectAsState()
    val detailsError by viewModel.detailsError.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    var pendingLegacyDownload by remember { mutableStateOf<(() -> Unit)?>(null) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingLegacyDownload?.invoke()
        pendingLegacyDownload = null
    }

    fun requestOrRunDownload(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingLegacyDownload = action
            legacyPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    LaunchedEffect(downloadState) {
        val completed = downloadState as? ExploreDownloadState.Completed ?: return@LaunchedEffect
        if (completed.file.mimeType == "application/vnd.android.package-archive") {
            openDownloadedApk(context, completed.file.uri, completed.file.mimeType)
        }
    }

    // FIX: text/icons on this screen that don't set an explicit color (the
    // search field's leading icon, back arrow, "Explore" title, etc.) were
    // resolving to Compose's hardcoded LocalContentColor default (black) —
    // because a bare Column, unlike Surface/Scaffold, never provides a
    // themed LocalContentColor. That looked fine in light mode (black text
    // on a light background) but was unreadable in dark mode, and stayed
    // black instead of adapting when dynamic color was on. Wrapping the
    // screen in a themed Surface makes every unset-color Text/Icon resolve
    // to the real onBackground for the active mode — white text/icons on
    // dark or dynamic-dark, black on light — exactly like the rest of the app.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    Column(
        Modifier.fillMaxSize()
    ) {
        ExploreHeader(onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.setQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("Search GitHub repositories") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp)
        )
        Text(
            if (query.isBlank()) "Trending repositories" else "Search results",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // FIX (no loading feedback on search / no skeleton loading): this
        // used to only ever show a bare spinner, and only on the very first
        // load — once results.isEmpty() became false, re-searching (typing
        // a new query) set loading=true but showed nothing at all while the
        // old results just sat there with no visible feedback. Now: a
        // genuine skeleton list appears for the first load, and a slim
        // top progress strip appears for subsequent searches so it's always
        // clear something is happening.
        if (loading && results.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (loading && results.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(6) { ExploreRepositoryCardSkeleton() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(results, key = { it.id }) { repo ->
                    ExploreRepositoryCard(repo) {
                        viewModel.openRepository(repo)
                    }
                }
            }
        }
    }
    }

    val repositoryDetails = details
    if (repositoryDetails != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeRepository() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            RepositoryDownloadSheet(
                details = repositoryDetails,
                loadingRelease = detailsLoading,
                error = detailsError,
                downloadState = downloadState,
                onClose = { viewModel.closeRepository() },
                onDownloadApk = { asset -> requestOrRunDownload { viewModel.downloadApk(asset) } },
                onDownloadSource = { requestOrRunDownload { viewModel.downloadSource() } },
                onRetryRelease = { viewModel.openRepository(repositoryDetails.repo) }
            )
        }
    }
}

@Composable
private fun ExploreHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text("Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExploreRepositoryCard(repo: Repository, onClick: () -> Unit) {
    val avatar = repo.owner?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: "https://github.com/${repo.ownerLogin}.png?size=200"
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepositoryAppIcon(
                    repo = repo,
                    fallback = avatar,
                    size = 48.dp,
                    contentDescription = "${repo.ownerLogin} repository icon"
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        repo.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        repo.private.let { if (it) "Private repository" else "Public repository" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            repo.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(Icons.Default.Star, compactCount(repo.stargazersCount))
                Stat(Icons.Default.ForkRight, compactCount(repo.forksCount))
                repo.language?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun ExploreRepositoryCardSkeleton() {
    // FIX (missing skeleton loading): reuses the app's existing shimmer
    // skeleton system (SkeletonRepository) instead of a bare spinner, wrapped
    // in the same Card styling as a real result so the first load reads as
    // "content is arriving" rather than empty space.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        com.gitofy.core.designsystem.components.SkeletonRepository()
    }
}

@Composable
private fun RepositoryAppIcon(
    repo: Repository,
    fallback: String,
    size: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    rounded: Boolean = false
) {
    val candidates = remember(repo.id, repo.ownerLogin, repo.name, repo.defaultBranch) {
        buildList {
            val base = "https://raw.githubusercontent.com/${repo.ownerLogin}/${repo.name}/${repo.defaultBranch}"
            add("$base/icon.png")
            add("$base/logo.png")
            add("$base/assets/icon.png")
            add("$base/assets/logo.png")
            add("$base/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
            add("$base/app/src/main/res/mipmap-xxhdpi/ic_launcher.png")
            add("$base/app/src/main/res/drawable/ic_launcher.png")
            add(fallback)
        }
    }
    var index by remember(candidates) { mutableStateOf(0) }
    AsyncImage(
        model = candidates[index.coerceAtMost(candidates.lastIndex)],
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onError = {
            if (index < candidates.lastIndex) index++
        },
        modifier = Modifier
            .size(size)
            .clip(if (rounded) RoundedCornerShape(22.dp) else CircleShape)
    )
}

/**
 * A download button that stays fully colorful (matching the app's normal
 * button styling) while idle, and — instead of just swapping in a spinner —
 * "drains" its solid color into a light outlined track the moment it's
 * tapped, then refills left-to-right as the download's real progress comes
 * in, landing back at fully colorful once the file is saved.
 *
 * Both color layers (fillColor / idleContentColor) are always drawn
 * explicitly here rather than left to Material3's containerColor →
 * contentColor inference, so the button can't end up washed-out or
 * low-contrast under any color scheme (dark, light, or dynamic/Material You).
 */
@Composable
private fun DownloadFillButton(
    label: String,
    completedLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fillColor: Color,
    idleContentColor: Color,
    isDownloading: Boolean,
    isEnabled: Boolean,
    completed: Boolean,
    progress: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val active = isDownloading || completed

    val fraction by animateFloatAsState(
        targetValue = when {
            completed -> 1f
            isDownloading && progress >= 0 -> progress.coerceIn(0, 100) / 100f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        label = "download-fill-fraction"
    )
    val trackColor by animateColorAsState(
        targetValue = if (active) fillColor.copy(alpha = 0.16f) else fillColor,
        animationSpec = tween(220),
        label = "download-track-color"
    )

    Box(
        modifier
            .clip(shape)
            .background(trackColor)
            .then(
                if (active) Modifier.border(1.dp, fillColor.copy(alpha = 0.45f), shape) else Modifier
            )
            .clickable(enabled = isEnabled && !isDownloading, onClick = onClick)
            .then(if (!isEnabled && !active) Modifier.background(Color.Black.copy(alpha = 0.06f)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // The colorful fill itself, sweeping in from the left as progress
        // advances (or snapping to full once the download completes).
        if (active) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(fillColor)
            )
        }

        // Base label, drawn in the track's own content color everywhere.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            DownloadButtonLabel(
                downloading = isDownloading,
                completed = completed,
                progress = progress,
                icon = icon,
                label = label,
                completedLabel = completedLabel,
                contentColor = if (active) fillColor else idleContentColor
            )
        }

        // The same label again, clipped to exactly the filled region and
        // drawn in the contrasting "on-fill" color — so the text/icon stay
        // readable both over the colorful fill and the light track behind
        // it, no matter where the progress edge currently sits.
        if (active) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        clipRect(right = size.width * fraction.coerceIn(0f, 1f)) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                DownloadButtonLabel(
                    downloading = isDownloading,
                    completed = completed,
                    progress = progress,
                    icon = icon,
                    label = label,
                    completedLabel = completedLabel,
                    contentColor = idleContentColor
                )
            }
        }
    }
}

@Composable
private fun DownloadButtonLabel(
    downloading: Boolean,
    completed: Boolean,
    progress: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    completedLabel: String,
    contentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            completed -> {
                Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                Text(completedLabel, color = contentColor, fontWeight = FontWeight.SemiBold)
            }
            downloading -> {
                if (progress >= 0) {
                    Text("$progress%", color = contentColor, fontWeight = FontWeight.SemiBold)
                } else {
                    // FIX: unknown total size (no Content-Length from the
                    // server) — show a real indeterminate spinner instead of
                    // a fake percentage that never matched the actual bytes
                    // downloaded.
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = contentColor)
                    Text("Downloading…", color = contentColor)
                }
            }
            else -> {
                Icon(icon, contentDescription = null, tint = contentColor)
                Text(label, color = contentColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryDownloadSheet(
    details: ExploreRepositoryDetails,
    loadingRelease: Boolean,
    error: String?,
    downloadState: ExploreDownloadState,
    onClose: () -> Unit,
    onDownloadApk: (ReleaseAsset) -> Unit,
    onDownloadSource: () -> Unit,
    onRetryRelease: () -> Unit
) {
    val repo = details.repo
    val avatar = repo.owner?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: "https://github.com/${repo.ownerLogin}.png?size=256"
    val apk = details.apkAsset
    val isDownloading = downloadState is ExploreDownloadState.Downloading
    val progress = (downloadState as? ExploreDownloadState.Downloading)?.progress ?: 0

    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(78.dp).clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                RepositoryAppIcon(
                    repo = repo,
                    fallback = avatar,
                    size = 78.dp,
                    contentDescription = "${repo.name} app icon",
                    rounded = true
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(repo.ownerLogin, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    details.release?.name ?: details.release?.tagName ?: "Recent release",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        repo.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat(Icons.Default.Star, compactCount(repo.stargazersCount))
            Stat(Icons.Default.ForkRight, compactCount(repo.forksCount))
            Text(if (repo.private) "Private" else "Public")
        }

        if (loadingRelease) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Loading latest release…")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetryRelease) { Text("Retry") }
        }

        if (!loadingRelease && error == null && details.release == null) {
            Text("No published release is available yet. Source code will use the default branch.")
        }

        apk?.let { asset ->
            // FIX (washed-out button under dark + dynamic color): this button
            // used to swap its *content* (icon/text vs. spinner) but always
            // kept relying on Button's own containerColor/contentColor
            // inference, which is what went pale/mismatched under a dynamic
            // Material You scheme. DownloadFillButton always paints its own
            // explicit brand color + a matching explicit content color for
            // every state, so it never inherits a broken scheme — it stays
            // colorful exactly like before until tapped, then the solid
            // color drains into a light track and refills left-to-right as
            // bytes actually arrive, instead of just showing a spinner.
            val apkDownloading = downloadState is ExploreDownloadState.Downloading &&
                downloadState.name == asset.name
            val apkCompleted = downloadState is ExploreDownloadState.Completed &&
                downloadState.file.mimeType == "application/vnd.android.package-archive"
            DownloadFillButton(
                label = "Download APK",
                completedLabel = "Saved",
                icon = Icons.Default.Download,
                fillColor = MaterialTheme.colorScheme.primary,
                idleContentColor = MaterialTheme.colorScheme.onPrimary,
                isDownloading = apkDownloading,
                isEnabled = !isDownloading,
                completed = apkCompleted,
                progress = progress,
                onClick = { onDownloadApk(asset) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
            Text(
                "Latest Android APK • ${formatBytes(asset.sizeInBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        run {
            val sourceDownloading = downloadState is ExploreDownloadState.Downloading &&
                downloadState.name == "Source code"
            val sourceCompleted = downloadState is ExploreDownloadState.Completed &&
                downloadState.file.mimeType == "application/zip"
            DownloadFillButton(
                label = "Download Source Code",
                completedLabel = "Saved",
                icon = Icons.Default.Code,
                fillColor = MaterialTheme.colorScheme.secondaryContainer,
                idleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                isDownloading = sourceDownloading,
                isEnabled = !isDownloading,
                completed = sourceCompleted,
                progress = progress,
                onClick = onDownloadSource,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
        // FIX: no size was ever shown for the source download at all. GitHub
        // doesn't report an exact zip size upfront (it's generated on the
        // fly), but the repository's own on-disk size (`size`, in KB) is a
        // real, honest estimate — labelled as an estimate rather than
        // presented as an exact figure.
        if (repo.sizeInKb > 0) {
            Text(
                "Estimated size • ~${formatBytes(repo.sizeInKb * 1024)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (downloadState) {
            is ExploreDownloadState.Completed -> {
                Text(
                    "Saved to Downloads/GITOFY/${downloadState.file.displayName}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is ExploreDownloadState.Failed -> Text(downloadState.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

private fun openDownloadedApk(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "Open APK with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
