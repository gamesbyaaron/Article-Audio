package app.articleaudio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Makes endless background sound (rain, wind, waves on a beach) from shaped noise, so the app
 * needs no recordings and no licences. Pure Kotlin, unit tested.
 */
class Noise(private val kind: String, private val sampleRate: Int = 22_050, seed: Int = 1) {
    private val random = Random(seed)
    private var brown = 0f
    private var pink0 = 0f
    private var pink1 = 0f
    private var pink2 = 0f
    private var low = 0f
    private var high = 0f
    private var t = 0L

    // Slowly wandering loudness for wind gusts.
    private var gust = 0.6f
    private var gustTarget = 0.6f

    // Waves: one swell every 7 to 12 seconds.
    private var wavePosition = 0f
    private var waveLength = 9f

    // Rain drops: a short burst of brighter noise that dies away quickly.
    private var drop = 0f

    private fun white() = random.nextFloat() * 2f - 1f

    private fun pink(w: Float): Float {
        pink0 = 0.99765f * pink0 + w * 0.0990460f
        pink1 = 0.96300f * pink1 + w * 0.2965164f
        pink2 = 0.57000f * pink2 + w * 1.0526913f
        return (pink0 + pink1 + pink2 + w * 0.1848f) * 0.18f
    }

    private fun brown(w: Float): Float {
        brown = (brown + w * 0.02f).coerceIn(-1f, 1f) * 0.998f
        return brown * 3f
    }

    /** Fills [out] with the next stretch of sound, as 16-bit samples. */
    fun fill(out: ShortArray) {
        for (i in out.indices) {
            val w = white()
            val sample = when (kind) {
                "rain" -> rain(w)
                "wind" -> wind(w)
                "beach" -> beach(w)
                else -> 0f
            }
            out[i] = (sample.coerceIn(-1f, 1f) * 20_000).toInt().toShort()
            t++
        }
    }

    private fun rain(w: Float): Float {
        // A steady patter: pink noise with the deepest rumble taken out.
        val p = pink(w)
        low += (p - low) * 0.05f
        var s = (p - low) * 0.9f
        // Individual drops.
        if (random.nextFloat() < 40f / sampleRate) drop = 0.4f + random.nextFloat() * 0.6f
        if (drop > 0.001f) {
            s += white() * drop * 0.5f
            drop *= 0.995f
        }
        return s * 0.8f
    }

    private fun wind(w: Float): Float {
        if (t % (sampleRate / 4) == 0L) {
            // Every quarter second, drift the gust strength towards a new random level.
            if (random.nextFloat() < 0.15f) gustTarget = 0.25f + random.nextFloat() * 0.75f
        }
        gust += (gustTarget - gust) * 0.00004f
        val b = brown(w)
        // Stronger gusts let more of the hiss through.
        val cutoff = 0.01f + gust * 0.05f
        low += (b + pink(w) * 0.3f - low) * cutoff
        return low * gust * 1.6f
    }

    private fun beach(w: Float): Float {
        wavePosition += 1f / sampleRate
        if (wavePosition >= waveLength) {
            wavePosition = 0f
            waveLength = 7f + random.nextFloat() * 5f
        }
        val phase = wavePosition / waveLength
        // The wave builds, breaks, then washes out with a long fade.
        val envelope = if (phase < 0.35f) sin(phase / 0.35f * PI.toFloat() / 2f)
        else exp(-(phase - 0.35f) * 4f).toFloat()
        val level = 0.15f + envelope * 0.85f
        // Brighter (more hiss) as the wave breaks.
        val cutoff = 0.02f + envelope * 0.25f
        val source = brown(w) * 0.6f + pink(w) * 0.8f
        low += (source - low) * cutoff
        high += (low - high) * 0.002f
        return (low - high * 0.5f) * level * 1.2f
    }

    companion object {
        val KINDS = listOf("off" to "Off", "rain" to "Rain", "wind" to "Wind", "beach" to "Beach")
    }
}
