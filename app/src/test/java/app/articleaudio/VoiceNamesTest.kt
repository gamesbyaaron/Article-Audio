package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNamesTest {
    @Test fun knownMaleVoice() = assertEquals(
        "English (United States) · Voice 3 · male",
        VoiceNames.label("English (United States)", 3, "en-us-x-iom-local"),
    )

    @Test fun knownFemaleVoice() = assertEquals(
        "English (United Kingdom) · Voice 1 · female",
        VoiceNames.label("English (United Kingdom)", 1, "en-gb-x-gba-local"),
    )

    @Test fun unknownVoiceGetsNoGuess() = assertEquals(
        "English (United States) · Voice 2",
        VoiceNames.label("English (United States)", 2, "en-US-language"),
    )
}
