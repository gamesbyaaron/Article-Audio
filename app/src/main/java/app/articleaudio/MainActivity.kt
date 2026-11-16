package app.articleaudio

import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    var showLog by rememberSaveable { mutableStateOf(false) }
    if (showLog) {
        BackHandler { showLog = false }
        LogScreen(onBack = { showLog = false })
    } else {
        HomeScreen(onOpenLog = { showLog = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenLog: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Audio") },
                actions = { TextButton(onClick = onOpenLog) { Text("Log") } },
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

            val title = Reader.title
            val items = Reader.utterances
            if (title != null && items.isNotEmpty()) {
                ArticlePlayer(title, items)
            }
        }
    }
}

@Composable
private fun ArticlePlayer(title: String, items: List<Utterance>) {
    val index = Reader.index.coerceIn(0, items.size - 1)
    val remainingChars = items.drop(index).sumOf { it.text.length }
    // Built-in voices speak roughly 900 characters a minute at normal speed.
    val minutesLeft = maxOf(1, remainingChars / 900)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Sentence ${index + 1} of ${items.size} · about $minutesLeft min left",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                items[index].text,
                style = if (items[index].isHeading) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Reader.skip(-1) }) { Text("Back") }
                Button(onClick = { if (Reader.playing) Reader.pause() else Reader.play() }) {
                    Text(if (Reader.playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { Reader.skip(1) }) { Text("Next") }
            }
            TextButton(onClick = { Reader.stop() }) { Text("Stop and go to start") }
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
