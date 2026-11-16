package app.articleaudio

import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { AppTheme { App() } }
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
private fun App() {
    var screen by rememberSaveable { mutableStateOf("home") }
    val home = { screen = "home" }
    if (screen != "home") BackHandler(onBack = home)
    when (screen) {
        "log" -> LogScreen(onBack = home)
        "text" -> TextScreen(onBack = home)
        "changes" -> ChangesScreen(onBack = home)
        else -> HomeScreen(onOpen = { screen = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Audio") },
                actions = { TextButton(onClick = { onOpen("log") }) { Text("Log") } },
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
            if (article != null) ArticlePlayer(article, onOpen)
        }
    }
}

@Composable
private fun ArticlePlayer(article: Article, onOpen: (String) -> Unit) {
    val index = Reader.index.coerceIn(0, article.sentences.size - 1)
    val current = article.sentences[index]
    // Built-in voices speak roughly 900 characters a minute at normal speed.
    val minutesLeft = remember(article, index) {
        maxOf(1, article.sentences.drop(index).sumOf { it.text.length } / 900)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(article.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Sentence ${index + 1} of ${article.sentences.size} · about $minutesLeft min left",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                current.text,
                style = if (current.isHeading) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Reader.skip(-1) }) { Text("Back") }
                Button(onClick = { if (Reader.playing) Reader.pause() else Reader.play() }) {
                    Text(if (Reader.playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { Reader.skip(1) }) { Text("Next") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen("text") }) { Text("Full text") }
                OutlinedButton(onClick = { onOpen("changes") }) { Text("Changes (${article.changes.size})") }
            }
            TextButton(onClick = { Reader.stop() }) { Text("Stop and go to start") }
        }
    }
}

/** The whole cleaned article. The sentence being read is highlighted; tap any sentence to jump to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextScreen(onBack: () -> Unit) {
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
                        TextButton(onClick = { if (Reader.playing) Reader.pause() else Reader.play() }) {
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
