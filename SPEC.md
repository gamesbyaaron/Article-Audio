# Article-Audio

Article Audio — project spec
Working name only. The final name and icon must not use "Wikipedia" or its logo.
Every Claude Code session should read this file first, do one milestone (or one clearly scoped piece of one), and update the Status section at the bottom before finishing.
What this is
A personal Android app that turns a Wikipedia article into an audio file for listening on the phone.
The user shares or pastes a Wikipedia link. The app fetches the article, removes everything that does not work when spoken, rewrites awkward written notation into natural speech, shows the cleaned text for review, then generates audio on the device and saves it in an in-app library with a player.
Hard constraints
Free with no conditions: no paid APIs, no accounts, no API keys, no usage caps.
Everything runs on the phone. The only network calls are to Wikipedia to fetch articles.
No analytics, ads, or tracking.
Faithful text: the cleanup rewrites notation, never meaning. No AI paraphrasing.
The whole project is built and tested from a phone. There is no PC, no emulator, and no adb logcat. Anything that fails must be visible inside the app (see the debug log in Milestone 0).
Tech choices
Kotlin, Jetpack Compose, single Gradle module, minSdk 26.
Speech: Android's built-in TextToSpeech engine, using synthesizeToFile.
Audio output: AAC in an .m4a file, encoded with Android's built-in MediaCodec and MediaMuxer. No MP3 encoder library.
HTML parsing: jsoup.
Playback: Media3 (ExoPlayer and MediaSession).
Keep dependencies to that list unless a milestone truly needs another.
Default decisions (owner can change these)
One audio file per article, not one per section.
Section headings are spoken ("Section: Background.") with a short pause after.
Tables, infoboxes, and image captions are skipped silently.
Block quotes are read.
English Wikipedia only for cleanup rules. Links from en.m.wikipedia.org are accepted and normalized.
Milestones
Do them in order. Each one ends with an installable build.
0. Build pipeline and debug tools
Nothing else can be tested until this works.
A "hello world" Compose app.
A GitHub Actions workflow that runs on every push to main: runs unit tests, builds the APK, and publishes it as a direct .apk download on a rolling GitHub Release (not only as a zipped workflow artifact, which is awkward to install from a phone).
A fixed signing key so each new build installs over the previous one without uninstalling. A debug keystore committed to this repo is acceptable for a personal project; say so in the README.
The version name shown on the main screen includes the commit short hash, so the owner can confirm which build is installed.
An in-app debug log: catch uncaught exceptions and key events, write them to a file, and show them on a Log screen with a "Copy all" button. This replaces logcat. Every later milestone logs its main steps and all errors here.
1. Voice proof
The riskiest on-device step, so it comes before any article work.
A text box and a Generate button.
Synthesize the text to a file with synthesizeToFile, encode it to .m4a, save it in app storage, and play it.
Voice picker listing the voices installed on the phone, plus a speed setting.
Log the engine name, voice, durations, and any failure.
2. Cleanup rules
Pure Kotlin with no Android dependencies, so the unit tests run in CI and this milestone can be verified without a phone.
A function that takes article text and returns speech-ready text.
One unit test per rule, using the examples in the table below, plus tests for the "leave unchanged" cases.
Guiding rule: when a pattern is ambiguous, leave the text unchanged.
3. Fetch and preview
Accept a Wikipedia URL by paste and from the Android share sheet.
Fetch the article HTML through Wikipedia's API. Check Wikimedia's current API documentation for the recommended endpoint and send a descriptive User-Agent.
Strip: infoboxes, tables, navigation boxes, reference markers and reference lists, edit links, figures and captions, hatnotes, coordinates, and these sections: References, Notes, Citations, See also, External links, Further reading, Bibliography.
Run the cleanup rules, then show the result in an editable preview, split by section, with a character count and a Generate button.
4. Long-form generation
Split text into chunks under the engine's maximum input length, breaking at sentence ends.
Generate in a foreground service with a progress notification and a Cancel action, so it survives the screen locking.
Encode incrementally, so an hour of audio is never held in memory as raw samples.
Append a short spoken credit at the end: article title, "from Wikipedia," and the license.
5. Library and player
List of generated articles with title, length, and date. Delete and rename.
Player with background playback, lock-screen controls, skip back and forward, speed, and remembered position per article.
Each item stores and shows its attribution: article title, URL, date fetched, and "Text from Wikipedia, CC BY-SA 4.0."
6. Queue and polish
Queue several articles for generation.
Export or share an audio file out of the app.
A personal pronunciation list: "when you see X, say Y."
Later, only if wanted
A bundled neural voice model running on-device, if the built-in voices are not good enough.
Chapter markers per section.
Preparing for public release: name, icon, store listing, and a review of attribution and Wikimedia API terms.
Cleanup rules and test examples
Written
Spoken
(April 1964 – May 1964)
from April 1964 to May 1964
1964–1968
1964 to 1968
12–15 March 1966
12 to 15 March 1966
John Smith (1920–1987) was
John Smith, 1920 to 1987, was
(born 1950) / (b. 1950)
born 1950
(d. 1987)
died 1987
(r. 1509–1547)
reigned 1509 to 1547
c. 1200 / ca. 1200
circa 1200
[12] / [a] / [citation needed]
(removed)
Alamo (/ˈæləmoʊ/ ...)
Alamo (pronunciation guide removed)
5 km (3.1 mi)
5 kilometers
10–15 km
10 to 15 kilometers
No. 3
number 3
e.g. / i.e.
for example / that is
vs.
versus
approx.
approximately
R&D, AT&T
(unchanged)
Smith & Jones
Smith and Jones
Leave unchanged, because they are ambiguous:
A dash between plain numbers that are not years or a shared-unit range (scores such as 3–1, page ranges).
"St." (Saint or Street).
Roman numerals (Henry VIII, World War II, III Corps). Let the speech engine handle them.
Any parenthetical that does not match a rule above.
Unit expansion applies only to a number followed by a known unit (km, m, cm, mm, mi, ft, in, kg, g, lb, mph, km/h, °C, °F). When a converted value follows in parentheses, drop the parenthetical.
Rules for every session
Read this file first. Do not start a later milestone before the earlier ones are marked done.
Keep the CI build passing. A change that breaks the build is not finished.
Log main steps and all errors to the in-app debug log.
Add or update unit tests for anything that can be tested without a device.
Finish by updating the Status section: what was done, what the owner should test on the phone, and anything left open.
Revised direction (agreed November 2026)
This overrides the milestones above where they conflict.
Read aloud live instead of generating audio files: store the cleaned article text and have the phone's built-in TextToSpeech speak it sentence by sentence inside a media service with lock-screen controls. No MediaCodec encoding, no generation service. Exporting an audio file becomes an optional later feature.
Fetch articles as plain text from Wikipedia's API (it already drops tables, infoboxes, and references), which likely removes the need for jsoup.
Revised order: 1) pipeline plus a Speak button, 2) paste or share a link and hear it read live, 3) cleanup rules with tests and a preview, 4) library, resume, lock-screen controls, speed and voice, attribution, 5) optional extras (audio export, pronunciation list, queue).

Status
Step 1 (pipeline plus Speak button): built, waiting for the owner to install and test.
Done:
- Kotlin + Compose app, package app.articleaudio, minSdk 26. Home screen: version with commit hash, text box, Speak, Stop, Test crash. Log screen: Copy all, Refresh, Clear.
- Debug log (DebugLog.kt): writes to a file in app storage, records app start with device info, text-to-speech engine and voice, each speak start/finish/error, and full stack traces of crashes.
- Fixed signing key keystore/debug.jks (password "android"), committed on purpose; every build is signed with it.
- GitHub Actions (.github/workflows/build.yml): on push to main runs unit tests, builds the APK, and replaces the release tagged "latest" with article-audio.apk. Version name is 0.1.<run number> (<commit hash>).
- Unit tests for log formatting (LogFormatTest.kt).
Owner should test on the phone:
- Install from the release; the version on screen matches the release title's hash.
- Speak reads the text aloud; Stop stops it.
- Log shows the start-up line and the TTS engine; Copy all copies it.
- Test crash closes the app; reopen, the Log shows the CRASH entry with a stack trace.
- Push any change, install the new build over the old one without uninstalling.
Open:
- The workflow also runs on the ccr-d3a27e4f-2qpss9 working branch so the first release could be checked before merging; remove that trigger once main builds.
- If the repo is private, the release download needs you signed in to GitHub in the phone's browser.
- No app icon yet (Android's default icon).
