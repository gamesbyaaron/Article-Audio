package app.articleaudio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            DebugLog.i("Notification permission " + if (granted) "granted" else "denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { AppTheme { App(onPlay = ::play) } }
    }

    /** Starts reading. The first time, also asks to show the playback notification (Android 13+). */
    private fun play() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.askedForNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Prefs.askedForNotifications = true
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reader.play()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A link shared from another app: fill it in and fetch it straight away. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        DebugLog.i("Received shared text: ${text.take(200)}")
        Reader.linkInput = text
        Reader.fetch(text)
        Nav.screen = "player"
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Which screen is showing. Kept outside the screens so turning the phone keeps it. */
object Nav {
    var screen by mutableStateOf("library")
}

@Composable
private fun App(onPlay: () -> Unit) {
    val screen = Nav.screen
    val back = {
        Nav.screen = when (screen) {
            "text", "changes" -> "player"
            "log" -> "settings"
            else -> "library"
        }
    }
    if (screen != "library") BackHandler(onBack = back)
    when (screen) {
        "player" -> PlayerScreen(onBack = back, onPlay = onPlay)
        "text" -> TextScreen(onBack = back, onPlay = onPlay)
        "changes" -> ChangesScreen(onBack = back)
        "settings" -> SettingsScreen(onBack = back)
        "log" -> LogScreen(onBack = back)
        else -> LibraryScreen(onPlay = onPlay)
    }
}

@Composable
private fun AppIcon(id: Int, description: String?, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = description, modifier = modifier)
}

// ---- Library (home) ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(onPlay: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val entries = Library.entries
    val open = Reader.entry

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Audio") },
                actions = {
                    IconButton(onClick = { Nav.screen = "settings" }) { AppIcon(R.drawable.ic_settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { AppIcon(R.drawable.ic_add, null) },
                text = { Text("Add article") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (Reader.loading || (Reader.message.isNotEmpty() && Reader.messageIsError)) {
                item { StatusLine() }
            }
            if (open != null && Reader.article != null) {
                item { ContinueCard(open, onPlay) }
            }
            if (entries.isEmpty()) {
                item { EmptyLibrary() }
            } else {
                item {
                    Text(
                        "Library",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(entries.size, key = { entries[it].id }) { i -> LibraryCard(entries[i]) }
            }
        }
    }

    if (adding) AddArticleDialog(onDismiss = { adding = false })
}

/** Fetching progress, or the last error. */
@Composable
private fun StatusLine() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (Reader.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            Reader.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (Reader.messageIsError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Share an article to Article Audio from the Wikipedia app or your browser, " +
                "or tap Add article and paste a link. Everything you open is saved here and works without internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The article that is open, with a play button, at the top of the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueCard(entry: LibraryEntry, onPlay: () -> Unit) {
    val saved = Library.entries.firstOrNull { it.id == entry.id } ?: entry
    val article = Reader.article ?: return
    val fraction = if (Reader.audioMode) Reader.audioPositionMs.toFloat() / maxOf(1, Reader.audioDurationMs)
    else article.startChars[Reader.index.coerceIn(0, article.sentences.size - 1)].toFloat() / maxOf(1, article.totalChars)

    ElevatedCard(
        onClick = { Nav.screen = "player" },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (Reader.playing) "Now playing" else "Continue listening",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    saved.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            FilledIconButton(
                onClick = { if (Reader.playing) Reader.pause() else onPlay() },
                modifier = Modifier.size(56.dp),
            ) {
                AppIcon(
                    if (Reader.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (Reader.playing) "Pause" else "Play",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryCard(entry: LibraryEntry) {
    OutlinedCard(
        onClick = {
            Reader.open(entry.id)
            Nav.screen = "player"
        },
        enabled = !Reader.loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { entry.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val progress = when {
                    entry.position == 0 -> "Not started"
                    entry.percent >= 100 -> "Finished"
                    else -> "${entry.percent}%"
                }
                Text(
                    "$progress · ${maxOf(1, entry.chars / 900)} min · ${day(entry.fetchedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (entry.hasAudio) {
                    AppIcon(R.drawable.ic_download, "Audio downloaded", Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Paste a link and fetch it. */
@Composable
private fun AddArticleDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf("") }
    val fetch = { text: String ->
        Reader.linkInput = text
        Reader.fetch(text)
        onDismiss()
        Nav.screen = "player"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add article") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a link to an English Wikipedia article.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Wikipedia link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { fetch(clipboard.getText()?.text.orEmpty()) }) { Text("Paste from clipboard and add") }
            }
        },
        confirmButton = { TextButton(onClick = { fetch(link) }, enabled = link.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun day(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

// ---- Player -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(onBack: () -> Unit, onPlay: () -> Unit) {
    val article = Reader.article
    val entry = Reader.entry
    val saved = entry?.let { e -> Library.entries.firstOrNull { it.id == e.id } ?: e }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Back") } },
                actions = {
                    if (article != null && saved != null) {
                        IconButton(onClick = { Nav.screen = "text" }) { AppIcon(R.drawable.ic_text, "Full text") }
                        Box {
                            IconButton(onClick = { menu = true }) { AppIcon(R.drawable.ic_more, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Open on Wikipedia") },
                                    onClick = { menu = false; openOriginal(context, saved.title) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Cleanup changes (${article.changes.size})") },
                                    onClick = { menu = false; Nav.screen = "changes" },
                                )
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(
                                    text = { Text("Refresh from Wikipedia") },
                                    onClick = { menu = false; Reader.refresh() },
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (article == null || saved == null) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (Reader.loading) CircularProgressIndicator()
                Text(
                    Reader.message.ifEmpty { "No article open." },
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                    color = if (Reader.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PlayerBody(article, saved, onPlay, Modifier.padding(padding))
        }
    }

    if (renaming && saved != null) RenameDialog(saved, onDone = { renaming = false })
    if (deleting && saved != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${saved.name}\"?") },
            text = {
                Text(
                    "This removes the article, your place in it and its downloaded audio from the app. " +
                        "A copy of the audio in your Music folder is not touched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    Reader.close()
                    Library.delete(saved.id)
                    Nav.screen = "library"
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBody(article: Article, saved: LibraryEntry, onPlay: () -> Unit, modifier: Modifier) {
    val index = Reader.index.coerceIn(0, article.sentences.size - 1)
    val current = article.sentences[index]
    val speed = Reader.speed
    val audio = Reader.audioMode
    var dragging by remember { mutableStateOf<Float?>(null) }

    // Live reading has no audio file behind it, so its times are worked out from the text
    // length. A downloaded file has exact times.
    val charsPerSecond = Reader.CHARS_PER_SECOND * speed
    val totalChars = maxOf(1, article.totalChars)
    val totalSeconds = if (audio) Reader.audioDurationMs / 1000 else (totalChars / charsPerSecond).toInt()
    val elapsedSeconds = if (audio) Reader.audioPositionMs / 1000 else (article.startChars[index] / charsPerSecond).toInt()
    val fraction = if (audio) Reader.audioPositionMs.toFloat() / maxOf(1, Reader.audioDurationMs)
    else article.startChars[index].toFloat() / totalChars

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(saved.name, style = MaterialTheme.typography.headlineSmall)
        if (Reader.message.isNotEmpty()) StatusLine()

        if (saved.hasAudio) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !audio, onClick = { Reader.useAudio(false) }, label = { Text("Read live") })
                FilterChip(selected = audio, onClick = { Reader.useAudio(true) }, label = { Text("Downloaded audio") })
            }
        }

        // The sentence being read, large, like a caption.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).clickable { Nav.screen = "text" },
        ) {
            Text(
                if (audio) "Playing the downloaded audio file." else current.text,
                style = if (current.isHeading && !audio) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 28.sp),
                modifier = Modifier.padding(20.dp),
            )
        }

        Column {
            Slider(
                value = (dragging ?: fraction).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { Reader.seekTo(it) }
                    dragging = null
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    clock(dragging?.let { (it * totalSeconds).toInt() } ?: elapsedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (audio) clock(totalSeconds) else "about ${clock(totalSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { Reader.changeSpeed(nextSpeed(speed)) }) {
                Text("${trimSpeed(speed)}×", style = MaterialTheme.typography.titleMedium)
            }
            SkipButton(R.drawable.ic_replay, "Back 15 seconds") { Reader.skipSeconds(-15) }
            FilledIconButton(
                onClick = { if (Reader.playing) Reader.pause() else onPlay() },
                modifier = Modifier.size(80.dp),
            ) {
                AppIcon(
                    if (Reader.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (Reader.playing) "Pause" else "Play",
                    Modifier.size(40.dp),
                )
            }
            SkipButton(R.drawable.ic_forward, "Forward 15 seconds") { Reader.skipSeconds(15) }
            TextButton(onClick = { Reader.stop() }) { Text("Restart") }
        }

        HorizontalDivider()
        DownloadSection(article)

        Text(
            "Text from the Wikipedia article \"${saved.title}\", licensed CC BY-SA 4.0. " +
                "Adapted for listening. Fetched ${day(saved.fetchedAt)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/** A circular-arrow icon with "15" under it. */
@Composable
private fun SkipButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(icon, description, Modifier.size(28.dp))
            Text("15", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RenameDialog(entry: LibraryEntry, onDone: () -> Unit) {
    var newName by remember(entry.id) { mutableStateOf(entry.name) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Rename") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                Library.rename(entry.id, newName)
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

private fun openOriginal(context: Context, title: String) {
    val url = "https://en.wikipedia.org/wiki/" + Uri.encode(title.replace(' ', '_'))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        DebugLog.e("Could not open the original article", t)
    }
}

/** Makes an audio file of the article, with progress, and offers to share or open the result. */
@Composable
private fun DownloadSection(article: Article) {
    val context = LocalContext.current
    val mine = Exporter.title == article.title
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            Exporter.running -> {
                Text(
                    "Making audio of \"${Exporter.title}\": ${(Exporter.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { Exporter.progress }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { Exporter.cancel() }) { Text("Cancel download") }
            }
            else -> {
                OutlinedButton(onClick = { Exporter.start(article) }) {
                    AppIcon(R.drawable.ic_download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download audio")
                }
                if (mine && Exporter.error.isNotEmpty()) {
                    Text(Exporter.error, color = MaterialTheme.colorScheme.error)
                }
                if (mine && Exporter.savedAs.isNotEmpty()) {
                    Text("Saved to ${Exporter.savedAs}", style = MaterialTheme.typography.bodySmall)
                    val uri = Exporter.savedUri
                    if (uri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { sendAudio(context, uri, Intent.ACTION_SEND) }) { Text("Share") }
                            TextButton(onClick = { sendAudio(context, uri, Intent.ACTION_VIEW) }) { Text("Open in another app") }
                        }
                    }
                }
            }
        }
    }
}

/** Hands the finished audio file to another app, to share it or to play it. */
private fun sendAudio(context: Context, uri: Uri, action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (action == Intent.ACTION_SEND) {
        intent.setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri)
    } else {
        intent.setDataAndType(uri, "audio/mp4")
    }
    try {
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (t: Throwable) {
        DebugLog.e("Could not hand the audio file to another app", t)
    }
}

/** 75 -> "1:15", 3700 -> "1:01:40". */
private fun clock(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f)

/** The next faster speed, wrapping round to the slowest. */
private fun nextSpeed(current: Float): Float = SPEEDS.firstOrNull { it > current + 0.01f } ?: SPEEDS.first()

private fun trimSpeed(speed: Float): String = "%.2f".format(speed).trimEnd('0').trimEnd('.')

/** The whole cleaned article. The sentence being read is highlighted; tap any sentence to jump to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextScreen(onBack: () -> Unit, onPlay: () -> Unit) {
    val article = Reader.article
    val listState = rememberLazyListState()
    val currentChunk = article?.sentences?.getOrNull(Reader.index)?.chunk ?: 0

    LaunchedEffect(currentChunk) {
        if (article != null) listState.animateScrollToItem(currentChunk)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "Full text", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Back") } },
                actions = {
                    if (article != null) {
                        TextButton(onClick = { if (Reader.playing) Reader.pause() else onPlay() }) {
                            Text(if (Reader.playing) "Pause" else "Play")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (article == null) {
            Text("No article loaded.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(article.chunks.size) { i -> ChunkText(article, i) }
            }
        }
    }
}

@Composable
private fun ChunkText(article: Article, chunkIndex: Int) {
    val chunk = article.chunks[chunkIndex]
    val index = Reader.index
    val active = if (index >= chunk.firstSentence && index < chunk.firstSentence + chunk.sentenceCount) index else -1
    val background = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    val text = remember(chunk, active, background, foreground) {
        buildAnnotatedString {
            append(chunk.text)
            if (active >= 0) {
                val s = article.sentences[active]
                addStyle(SpanStyle(background = background, color = foreground), s.start, s.end)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = if (chunk.isHeading) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        else MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 30.sp),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(article, chunkIndex) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    Reader.jumpTo(article.sentenceAt(chunkIndex, offset))
                }
            },
    )
}

/** Every rewrite the cleanup rules made in this article, to spot bad ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangesScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val changes = Reader.article?.changes.orEmpty()
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleanup changes (${changes.size})") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Back") } },
                actions = {
                    TextButton(onClick = {
                        val all = changes.joinToString("\n") { "${it.rule}: ${it.before}  ->  ${it.after}" }
                        clipboard.setText(AnnotatedString(all))
                        copied = true
                    }) { Text(if (copied) "Copied" else "Copy all") }
                },
            )
        },
    ) { padding ->
        if (changes.isEmpty()) {
            Text(
                "The cleanup rules changed nothing in this article.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(changes.size) { i ->
                    val change = changes[i]
                    Column {
                        Text(
                            change.rule,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("${change.before}  →  ${change.after}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Reading speed and voice. Both are remembered and apply to the next thing spoken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var slider by remember { mutableFloatStateOf(Reader.speed) }
    val voices = Reader.voices

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text("Speed: ${"%.2f".format(slider)}×", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { Reader.changeSpeed(slider) },
                    valueRange = 0.5f..2.5f,
                    steps = 7,
                )
                Text(
                    "Voice",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Voices installed on this phone that work without internet. Tap one to hear it. " +
                        "Male and female labels are a best guess and missing for voices the app does not recognise.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VoiceRow("Phone default", "", Reader.voiceName == null) { Reader.changeVoice(null) }
            }
            items(voices.size) { i ->
                val voice = voices[i]
                VoiceRow(voice.label, voice.name, Reader.voiceName == voice.name) { Reader.changeVoice(voice.name) }
            }
            if (Reader.message.isNotEmpty() && Reader.messageIsError) {
                item { Text(Reader.message, color = MaterialTheme.colorScheme.error) }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Developer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedButton(onClick = { Nav.screen = "log" }) { Text("Debug log") }
            }
        }
    }
}

@Composable
private fun VoiceRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(label)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var log by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(refreshKey) {
        log = DebugLog.readAll()
        scroll.scrollTo(scroll.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug log") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Back") } },
                actions = {
                    TextButton(onClick = {
                        DebugLog.i("Test crash pressed")
                        throw RuntimeException("Test crash from the Log screen")
                    }) { Text("Test crash") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(log))
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy all") }
                OutlinedButton(onClick = { refreshKey++; copied = false }) { Text("Refresh") }
                OutlinedButton(onClick = {
                    DebugLog.clear()
                    refreshKey++
                    copied = false
                }) { Text("Clear") }
            }
            SelectionContainer(modifier = Modifier.padding(top = 12.dp).verticalScroll(scroll)) {
                Text(
                    if (log.isEmpty()) "(log is empty)" else log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
