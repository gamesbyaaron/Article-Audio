package app.articleaudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows article pictures from the library or from search results, with a small memory cache. */
object Pictures {
    private val cache = LruCache<String, Bitmap>(60)

    private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    /** A picture stored with a library article; [key] changes when the picture does. */
    suspend fun fromLibrary(id: String, key: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(key) ?: try {
            val file = Library.imageFile(id)
            if (file.exists()) decode(file.readBytes())?.also { cache.put(key, it) } else null
        } catch (t: Throwable) {
            null
        }
    }

    /** A search thumbnail, downloaded on first use. Failures just show no picture. */
    suspend fun fromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: try {
            decode(WikipediaClient.getBytes(url))?.also { cache.put(url, it) }
        } catch (t: Throwable) {
            null
        }
    }
}

/** A square article picture, or a plain tile when there is none. */
@Composable
fun ArticlePicture(load: suspend () -> Bitmap?, key: Any, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key) { value = load() }
    val shaped = modifier.size(size).clip(MaterialTheme.shapes.medium)
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** The picture for a library article, if it has one. */
@Composable
fun EntryPicture(entry: LibraryEntry, size: Dp, modifier: Modifier = Modifier) {
    val key = "${entry.id}:${entry.imageCredit}:${entry.imageChecked}"
    ArticlePicture({ Pictures.fromLibrary(entry.id, key) }, key, size, modifier)
}
