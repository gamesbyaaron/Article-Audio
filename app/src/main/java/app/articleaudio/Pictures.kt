package app.articleaudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
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

/**
 * A square article picture. When there is no free picture, a generic icon for the kind of
 * article ([category], from [Category]) on a tinted square.
 */
@Composable
fun ArticlePicture(load: suspend () -> Bitmap?, key: Any, size: Dp, modifier: Modifier = Modifier, category: String = "article") {
    val bitmap by produceState<Bitmap?>(initialValue = null, key) { value = load() }
    val shaped = modifier.size(size).clip(MaterialTheme.shapes.medium)
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(categoryIcon(category)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize().padding(size * 0.25f),
            )
        }
    }
}

fun categoryIcon(category: String): Int = when (category) {
    "game" -> R.drawable.ic_cat_game
    "war" -> R.drawable.ic_cat_war
    "person" -> R.drawable.ic_cat_person
    "place" -> R.drawable.ic_cat_place
    "nature" -> R.drawable.ic_cat_nature
    "animal" -> R.drawable.ic_cat_animal
    "film" -> R.drawable.ic_cat_film
    "music" -> R.drawable.ic_cat_music
    "book" -> R.drawable.ic_cat_book
    "sport" -> R.drawable.ic_cat_sport
    "space" -> R.drawable.ic_cat_space
    "science" -> R.drawable.ic_cat_science
    "history" -> R.drawable.ic_cat_history
    "building" -> R.drawable.ic_cat_building
    else -> R.drawable.ic_cat_article
}

/** The picture for a library article, if it has one. */
@Composable
fun EntryPicture(entry: LibraryEntry, size: Dp, modifier: Modifier = Modifier) {
    val key = "${entry.id}:${entry.imageCredit}:${entry.imageChecked}"
    ArticlePicture({ Pictures.fromLibrary(entry.id, key) }, key, size, modifier, entry.category)
}
