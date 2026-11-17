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
Download feature (owner, 17 November 2026): the owner wants to save an article as an audio file as well as hear it live. So audio export is a planned step, not an optional extra: synthesizeToFile, encode to .m4a with MediaCodec and MediaMuxer, in a foreground service with progress, as the original Milestone 4 describes.
Revised order: 1) pipeline plus a Speak button, 2) paste or share a link and hear it read live, 3) cleanup rules with tests and a preview, 4) library, resume, lock-screen controls, speed and voice, attribution, 5) optional extras (audio export, pronunciation list, queue).

Status
Step 1 (pipeline plus Speak button): done. Confirmed on the owner's phone: installs, speaks, the Log screen shows events and records crashes.
Done:
- Kotlin + Compose app, package app.articleaudio, minSdk 26. Home screen: version with commit hash, text box, Speak, Stop, Test crash. Log screen: Copy all, Refresh, Clear.
- Debug log (DebugLog.kt): writes to a file in app storage, records app start with device info, text-to-speech engine and voice, each speak start/finish/error, and full stack traces of crashes.
- Fixed signing key keystore/debug.jks (password "android"), committed on purpose; every build is signed with it. The repo is public, so the key is public too; acceptable for a personal sideloaded app.
- GitHub Actions (.github/workflows/build.yml):
  - Push to main: runs unit tests, builds and verifies the APK (apksigner, aapt2), then publishes it twice: a permanent release "Build N (hash)" tagged build-N, and the rolling release tagged "latest" behind the fixed link. Only the newest 20 build-N releases are kept.
  - Pull requests: test and build only, nothing published.
  - Version name is 0.1.<run number> (<commit hash>); versionCode is the run number.
- Unit tests for log formatting (LogFormatTest.kt).
Installing on the phone:
- Newest build: github.com/gamesbyaaron/Article-Audio/releases/latest/download/article-audio.apk
- Older builds: the Releases page, "Build N (hash)". Android will not install an older version over a newer one: uninstall first, which erases app data.
- If Android says "App not installed" although the APK is valid: free up storage and check the phone's security settings (Play Protect, the maker's app blocker). Both mattered on the first install.
Open:
- No app icon yet (Android's default icon).

Step 2 (hear a Wikipedia article): done. Confirmed on the owner's phone: sharing and pasting a link works, the fetch works, reading sounds good.
- WikiLink.kt accepts en.wikipedia.org and en.m.wikipedia.org links, including links inside shared text. WikipediaClient.kt fetches plain text from the Action API (prop=extracts, explaintext, follows redirects); this is the app's only network call.
- ArticleText.kt drops reference-type sections and their subsections, speaks headings as "Section: X." with a 0.7 s pause, and skips headings with no text.

Step 3 (continuous reading, cleanup rules, checking screens): built, waiting for the owner to test on the phone.
Done:
- Continuous reading. The speech engine now gets a whole paragraph at a time (ArticleText builds "chunks"), with three more queued ahead, so there is no gap between sentences. The engine's word-position reports (onRangeStart) say which sentence is being spoken, so the on-screen sentence, Pause, Back and Next still work by sentence. If an engine does not report positions, tracking falls back to paragraphs; the Log says which ("Speech engine reports word positions...").
- Cleanup rules (Cleanup.kt, pure Kotlin, run on every paragraph and heading before sentence splitting):
  - citation markers removed; pronunciation guides in brackets removed (IPA between slashes or square brackets), keeping anything else in the same brackets
  - leftover "(; ...)" punctuation and empty "()" from Wikipedia's plain text; "{\displaystyle ...}" maths markup removed
  - date ranges: 1964–1968, 1964–68, 12–15 March 1966, March 12–15, 1966, June–July 1954, full date to full date, "–present"
  - brackets holding only a date range become ", X to Y," (or "from X to Y" for month-and-year ranges); (born X), (b. 1950), (died X), (d. 1987), (r. 1509–1547)
  - c. / ca. before a number become "circa"; a real minus sign before a number becomes "minus"
  - units after a number (km, m, cm, mm, mi, ft, kg, g, lb, mph, km/h, °C, °F, km2, m2, sq mi, sq ft) are spelled out and a following conversion in brackets is dropped; ranges like 10–15 km work; "in" is only treated as inches when a conversion follows
  - No. 3, e.g., i.e., vs., approx.; " & " between words becomes "and"
  - an abbreviation in brackets after the words it stands for becomes ", abbreviated to X," (owner's request after testing step 3): "World Health Organization (WHO)", "Deoxyribonucleic acid (DNA)". It must be mostly capitals, not a Roman numeral, and its letters must appear in order in the few words before it, starting at the beginning of a word; "London (UK)" and "the film (PG)" are left alone.
  - left alone: scores and page ranges (3–1), St., Roman numerals, R&D, AT&T, $5 m, 5 m/s, ISO dates, any other brackets
- Full text screen: the whole cleaned article, current sentence highlighted, follows along while playing; tap a sentence to jump to it.
- Changes screen: every rewrite the rules made in this article as before → after, with Copy all. The Log also records the number of changes per rule.
- Unit tests: CleanupTest (47, one or more per rule plus the leave-unchanged cases), ArticleTextTest (15), WikiLinkTest (13), LogFormatTest (4).
Owner should test on the phone:
- Play an article: it should flow with no gap between sentences, and the sentence shown should keep up with the voice. Check the Log line about word positions.
- Pause and Play again (it restarts at the current sentence), Back, Next.
- Full text: highlight follows the voice; tap a sentence further down and it jumps there.
- Changes: skim for rewrites that are wrong or sound bad, and for things that should have been rewritten but were not. Copy all and paste them into the chat.
Open:
- The rules were written from the spec's examples and from memory of how Wikipedia's plain text looks; the build machine cannot reach Wikipedia. Real articles will show what is missing: maths formulas may leave stray symbols, and foreign-language name lists in brackets are read as written.
- Units used as adjectives come out plural ("a 35 millimeters film").
- No editing of the text before reading (the original spec had an editable preview).
- Reading with the screen off may be stopped by Android; a proper background service with lock-screen controls is step 4.
- The User-Agent has no contact address; Wikimedia asks for one. Add the repo URL if the owner wants.
Owner's feedback on step 3 (build 7): it works; the one rewrite missing was abbreviations in brackets (rule added, see above).

Step 4a (listening with the phone locked, voice and speed): built, waiting for the owner to test on the phone.
Done:
- PlaybackService.kt: a foreground service (type mediaPlayback) that runs from Play until Stop or the end of the article, including while paused. It holds a MediaSession, so play/pause, back and next work from the notification, the lock screen and headphone buttons, and a wake lock while reading. It uses the platform's own MediaSession and Notification.MediaStyle rather than Media3, so there is no new dependency. Reader.kt still does the reading; the service only reports its state to Android.
- AudioFocus.kt: music apps pause when reading starts; reading pauses for calls and other short interruptions and resumes afterwards; pauses for good when another player takes over, and when headphones are unplugged.
- Settings screen (top bar): speed from 0.5× to 2.5× in steps of 0.25, and a voice picker listing the phone's offline English voices plus "Phone default". Both are saved (Prefs.kt), apply immediately while playing, and otherwise speak a short sample.
- Android 13+: the first Play asks for permission to show notifications. Reading works either way.
- Logged: service start and end, voice and speed changes, the number of voices found, every focus interruption, permission result.
Owner should test on the phone:
- Press Play, lock the phone: reading should continue. Leave it for ten minutes or more.
- Lock screen and notification: title shown; Pause, Play, Back and Next work. Headphone button if available.
- Start music in another app, then press Play here: the music should pause. Play something else while an article reads: the article should pause.
- Settings: change speed and voice while playing and while stopped.
- Stop and go to start: the notification disappears.
- If anything stops by itself, copy the Log.
Open:
- Not run on a phone yet; only compiled and unit tested by CI.
- Voice names are the engine's own codes (for example en-us-x-iom-local); there is no friendlier name available.
- Some phone makers' battery savers stop background apps regardless; if reading stops with the screen off, the Log will show whether the service ended.
Owner installed step 4a (build 9); detailed feedback still to come. Owner's decision: bracketed date ranges stay as they are (", 1920 to 1987,") for now.

Player and voice names (owner's requests, 17 November 2026): built, waiting for the owner to test.
- Home screen player: a draggable progress bar over the whole article with elapsed and total time, −15s and +15s buttons (also used by the lock-screen back/next), Play/Pause, and a speed button that steps through 0.75× to 2.5×. Times are estimates from text length (Reader.CHARS_PER_SECOND = 15 at 1×), because live reading has no audio file; jumps land on the start of a sentence.
- Voices are listed as "English (United States) · Voice 3 · male" (VoiceNames.kt). Android does not report gender, so male/female comes from a table of Google voice codes written from memory; it may be wrong and is omitted for unknown voices.
Next:
- 4b: download an article as an .m4a audio file (uses the same foreground service as 4a).
- 4c: library (saved articles, resume position, rename, delete) and attribution.
