package app.articleaudio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NoiseTest {
    private fun loudness(kind: String): Double {
        val noise = Noise(kind)
        val buffer = ShortArray(22_050)
        var total = 0.0
        repeat(20) { // 20 seconds, long enough for a few waves and gusts
            noise.fill(buffer)
            total += buffer.sumOf { abs(it.toInt()).toDouble() } / buffer.size
        }
        return total / 20
    }

    @Test
    fun everySoundIsAudibleButNotClipping() {
        for (kind in listOf("rain", "wind", "beach")) {
            val level = loudness(kind)
            assertTrue("$kind is too quiet: $level", level > 300)
            assertTrue("$kind is too loud: $level", level < 15_000)
        }
    }

    @Test
    fun offIsSilent() = assertTrue(loudness("off") == 0.0)
}
