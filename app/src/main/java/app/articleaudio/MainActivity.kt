package app.articleaudio

import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = Speaker(this)
        setContent { AppTheme { App(speaker) } }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
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
private fun App(speaker: Speaker) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    if (showLog) {
        BackHandler { showLog = false }
        LogScreen(onBack = { showLog = false })
    } else {
        HomeScreen(speaker, onOpenLog = { showLog = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(speaker: Speaker, onOpenLog: () -> Unit) {
    var text by rememberSaveable {
        mutableStateOf("Hello. This is Article Audio, reading with your phone's built-in voice.")
    }
    var message by remember { mutableStateOf("") }

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
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to speak") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { message = speaker.speak(text) ?: "" }) { Text("Speak") }
                OutlinedButton(onClick = {
                    speaker.stop()
                    DebugLog.i("Stop pressed")
                }) { Text("Stop") }
            }
            if (message.isNotEmpty()) {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = {
                DebugLog.i("Test crash pressed")
                throw RuntimeException("Test crash from the Home screen")
            }) { Text("Test crash") }
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
