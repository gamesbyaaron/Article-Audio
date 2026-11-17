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

@Composable
private fun App(onPlay: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("home") }
    val home = { screen = "home" }
    if (screen != "home") BackHandler(onBack = home)
    when (screen) {
        "log" -> LogScreen(onBack = home)
        "text" -> TextScreen(onBack = home, onPlay = onPlay)
        "changes" -> ChangesScreen(onBack = home)
        "settings" -> SettingsScreen(onBack = home)
        else -> HomeScreen(onOpen = { screen = it }, onPlay = onPlay)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (String) -> Unit, onPlay: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Audio") },
                actions = {
                    TextButton(onClick = { onOpen("settings") }) { Text("Settings") }
                    TextButton(onClick = { onOpen("log") }) { Text("Log") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = Reader.linkInput,
                onValueChange = { Reader.linkInput = it },
                label = { Text("Wikipedia link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { Reader.fetch(Reader.linkInput) },
                    enabled = !Reader.loading,
                ) { Text("Fetch") }
                OutlinedButton(
                    onClick = {
                        val pasted = clipboard.getText()?.text.orEmpty()
                        Reader.linkInput = pasted
                        Reader.fetch(pasted)
                    },
                    enabled = !Reader.loading,
                ) { Text("Paste and fetch") }
            }

            if (Reader.message.isNotEmpty()) {
                Text(
                    Reader.message,
                    color = if (Reader.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val article = Reader.article
            if (article != null) ArticlePlayer(article, onOpen, onPlay)
        }
    }
}

@Composable
private fun ArticlePlayer(article: Article, onOpen: (String) -> Unit, onPlay: () -> Unit) {
    val index = Reader.index.coerceIn(0, article.sentences.size - 1)
    val current = article.sentences[index]
    val speed = Reader.speed
    // There is no audio file behind live reading, so times are worked out from the text length.
    val charsPerSecond = Reader.CHARS_PER_SECOND * speed
    val total = maxOf(1, article.totalChars)
    val elapsedSeconds = (article.startChars[index] / charsPerSecond).toInt()
    val totalSeconds = (total / charsPerSecond).toInt()
    var dragging by remember { mutableStateOf<Float?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(article.title, style = MaterialTheme.typography.titleLarge)
            Text(
                current.text,
                style = if (current.isHeading) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = dragging ?: (article.startChars[index].toFloat() / total),
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
                Text("about ${clock(totalSeconds)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { Reader.skipSeconds(-15) }) { Text("−15s") }
                Button(onClick = { if (Reader.playing) Reader.pause() else onPlay() }) {
                    Text(if (Reader.playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { Reader.skipSeconds(15) }) { Text("+15s") }
                TextButton(onClick = { Reader.changeSpeed(nextSpeed(speed)) }) { Text("${trimSpeed(speed)}×") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen("text") }) { Text("Full text") }
                OutlinedButton(onClick = { onOpen("changes") }) { Text("Changes (${article.changes.size})") }
            }
            TextButton(onClick = { Reader.stop() }) { Text("Stop and go to start") }
            DownloadSection(article)
        }
    }
}

/** Makes an audio file of the article, with progress, and offers to share or open the result. */
@Composable
private fun DownloadSection(article: Article) {
    val context = LocalContext.current
    val mine = Exporter.title == article.title
    when {
        Exporter.running -> {
            Text(
                "Making audio of \"${Exporter.title}\": ${(Exporter.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(progress = { Exporter.progress }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { Exporter.cancel() }) { Text("Cancel download") }
        }
        else -> {
            OutlinedButton(onClick = { Exporter.start(article) }) { Text("Download audio") }
            if (mine && Exporter.error.isNotEmpty()) {
                Text(Exporter.error, color = MaterialTheme.colorScheme.error)
            }
            if (mine && Exporter.savedAs.isNotEmpty()) {
                Text("Saved to ${Exporter.savedAs}", style = MaterialTheme.typography.bodySmall)
                val uri = Exporter.savedUri
                if (uri != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sendAudio(context, uri, Intent.ACTION_SEND) }) { Text("Share") }
                        OutlinedButton(onClick = { sendAudio(context, uri, Intent.ACTION_VIEW) }) { Text("Open") }
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
                title = { Text(article?.title ?: "Full text", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
        style = if (chunk.isHeading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
