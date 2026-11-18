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
Owner confirmed builds 9 and 11 work well (background listening, player, voice names).

Step 4b (download as an audio file): built, waiting for the owner to test on the phone.
Done:
- "Download audio" button on the player card. Exporter.kt opens its own speech engine connection (so live reading is not disturbed), uses the saved voice and speed, has the engine write each paragraph to a WAV file in the cache (synthesizeToFile), streams it into AacWriter.kt (MediaCodec AAC-LC, 64 kbps per channel, MediaMuxer .m4a) and deletes the WAV. Nothing long is held in memory. 0.7 s of silence follows headings and 0.25 s follows paragraphs. A spoken credit ends the file: title, "from Wikipedia", and the licence.
- The finished file is kept in the app's own storage (files/downloads/<title>.m4a, for the library step to play) and, on Android 10+, copied to the shared folder Music/Article Audio through MediaStore, where the Files app and music players can see it. Share and Open buttons use that copy. On Android 9 and older there is no shared copy.
- ExportService.kt: foreground service (type dataSync) with a progress notification and Cancel, a wake lock, and a final "Audio ready" or failure notification.
- Logged: start, voice and speed, sound format, finish with file size, audio length and time taken, cancel, and any failure with its stack trace.
- Unit tests: WavTest (5) for the WAV header reader and file names. The encoder and service cannot be unit tested.
Owner should test on the phone:
- Download a short article first. Watch the progress on the card and in the notification, then Open the file and listen: voice, speed, pauses, the credit at the end.
- Find the file in the Files app or a music player under Music/Article Audio. Try Share.
- Download a long article with the phone locked. Try Cancel part-way through another.
- Listen live to one article while another downloads.
- Paste the Log either way: it records how long the download took.
Open:
- Never run on a phone: speech-to-file and the encoder are the riskiest parts of the project. Expect fixes.
- Only 16-bit PCM from the speech engine is handled; anything else fails with a clear message.
- Downloading the same article twice makes a second shared copy with a number added by Android.
- No list of past downloads yet; that is the library step.

Pinned for later, not in the development order (owner, 17 November 2026): the long-term goal is a public app for others as well as the owner. That means finding articles inside the app (search, featured, most-read, random) on top of the saved library, and a release (name and icon without "Wikipedia", private signing key, attribution and Wikimedia API terms, Google Play). Do not start any of this until the owner asks, but keep choices release-friendly: attribution on every article, no accounts or tracking, cope with phones that have poor or no voices.
Owner confirmed step 4b (build 13): the download works and plays in Files, Spotify and YT Music.

Step 4c (library, resume, playing downloads in the app, attribution): built, waiting for the owner to test on the phone.
Done:
- Library.kt: every fetched article is saved in app storage (files/library/<title>/text.txt holds the text as fetched; details.properties holds name, dates, size and position). Articles are rebuilt from the saved text with the current cleanup rules when opened, so they work offline and benefit from later rule changes. LibraryEntry.kt is the pure, tested part.
- Home screen: a Library list under the player, most recently opened first, showing percent read, approximate length, fetch date and whether audio is downloaded. Tap to open at the saved place.
- Resume: the place is saved to the sentence on pause, stop, jumps, switching article, and every few seconds while playing. Fetching an article again keeps the place; Refresh re-fetches and starts from the beginning. Reaching the end resets to the start.
- Playing downloads in the app: when an article has a downloaded file, the player card offers "Read live" or "Play download". The file is played with MediaPlayer through the same controls, lock-screen controls and audio-focus handling; times are exact and its position is remembered separately.
- Article options on the player card: Original (opens the Wikipedia page), Rename, Refresh, Delete (with confirmation; also removes the app's copy of the audio).
- Attribution on every open article: title, "licensed CC BY-SA 4.0", "Adapted for listening", fetch date, and the link to the original.
- Unit tests: LibraryEntryTest (4).
Owner should test on the phone:
- Fetch two or three articles; they appear in the Library. Close the app completely, reopen, and open one: it should be where you left it, with no internet needed (try aeroplane mode).
- Listen part-way, switch to another article, switch back: the place is kept and the percent in the list updates.
- On an article with a download: Play download, drag the bar, use the 15-second buttons, lock the phone, pause, close the app, reopen: it resumes in the file.
- Rename, Refresh and Delete.
Open:
- Not run on a phone yet.
- The speed button applies on top of the speed a file was downloaded at, so a file made at 1.5× and played at 1.5× is faster than live reading at 1.5×.
- In download mode the Full text screen does not follow along, and tapping a sentence there does nothing.
- Rename, Refresh and Delete are only on the open article, not on each row of the list.
- Articles fetched before this build are not in the library; fetch them again.

Owner confirmed step 4c (build 15) works.

Polish, build 1 of 2 ("make it pretty", generic; owner, 17 November 2026): built, waiting for the owner to test.
- Navigation: a Nav object holds the current screen (survives rotation). Library is the home screen; the player is its own screen; Full text and Changes open from the player; Log moved to Settings > Developer with the version number.
- Library screen: "Continue listening" / "Now playing" card for the open article with a play/pause button, then article cards with a progress bar, percent, length, date and a download icon; an empty-state hint; an "Add article" floating button opens a paste dialog. Sharing a link opens the player.
- Player screen: title, live/downloaded-audio chips, the current sentence in a large serif caption (tap for full text), progress bar with times, speed, back 15, a large play/pause button, forward 15, Restart; download section; attribution. Overflow menu: Open on Wikipedia, Cleanup changes, Rename, Refresh, Delete.
- Full text screen: book-style serif text, larger line height and margins.
- Icons are vector drawables in res/drawable (no icon library added).
Owner: polish build 1 (build 17) "looks prettier". Polish build 2 (icon, colours, transitions) dropped for now at the owner's request.

Search, article pictures, player on Full text (owner, 17 November 2026): built, merged on a passing build at the owner's request, waiting for the owner to test. In-app search is no longer pinned.
- Search: "Find article" on the library opens a search screen with suggestions as you type (300 ms pause), using Wikipedia's prefix search with each result's short description and a free-licensed thumbnail. Tapping a result fetches it (or opens it if already saved). "Paste link" stays in the top bar.
- Pictures: the article's lead picture is fetched only if free-licensed (pageimages with pilicense=free, then the file's NonFree flag is checked), stored with the article as image.jpg and shown on library cards, the continue card and the player. Credit (author and licence, from the file's metadata) is shown under the article's attribution. Articles with only non-free pictures stay blank. Existing library articles get their picture the next time they are opened. Pictures come from upload.wikimedia.org, Wikimedia's image server.
- Full text screen: a compact player bar along the bottom (progress, back 15, play/pause, forward 15; tap the title for the full player).
- Unit tests: ImageCreditTest (4).
Open:
- Not run on a phone yet.
- Search suggestions match the start of titles, as Wikipedia's own search box does; a full-text search is possible later.


After 4c the personal app is functionally complete; what follows is optional polish (queue, pronunciation list, more rules, icon and design) and then the pinned public version.

Owner confirmed build 19 (search, pictures, Full text player) works.

Feature plan (owner, 17 November 2026), in this order:
- A: section list, sleep timer, topic search, background sound (in Settings).
- B: Home page with bottom tabs (Home, Search, Library); Article of the Day hero and rows from Wikipedia's feed (featured, most read) plus a placeholder developer topic list, each row a search for now. Revisit "library" vs "saved" then.
- C: queue ("play next", "add to queue", reorder, remove, kept across restarts, auto-advance). Lock screen gets two pairs of buttons: back/forward 15 seconds and previous/next article.
- D: personal collections (many-to-many), playable as a queue.
Not now: storage management, download speed.

Build A: built, merged on a passing build at the owner's request, waiting for the owner to test.
- Sections: Article.sections lists "Beginning" plus every spoken heading. A Sections dialog on the player and the Full text screen jumps there (Reader.jumpToSection). New downloads save where each section starts (<title>.sections next to the .m4a, SectionTimes.kt); older downloads estimate from text position.
- Sleep timer on the player: 15/30/45/60 minutes, end of this section, end of this article, or off. The button shows a countdown. When time is up, downloaded audio fades over about six seconds; live reading finishes its sentence and stops (the speech engine cannot fade mid-sentence). The background sound fades with it.
- Topic search: as-you-type title suggestions as before; pressing the keyboard's search key shows up to 20 articles about the topic from Wikipedia's full-text search.
- Background sound (Settings): Off, Rain, Wind, Beach, generated from shaped noise in code (Noise.kt, no recordings or licences), with its own volume. Plays and fades with the reading; choosing one plays a six-second preview when nothing is playing. In-app only; not mixed into downloads.
- Unit tests: SectionsTest (5), NoiseTest (2).
Open:
- Not run on a phone. The generated sounds have never been listened to; they may need tuning by ear.

Owner confirmed build A (build 21): works; the generated sounds are "okay, work for now".

Build B (Home, tabs, article page): built, merged on a passing build at the owner's request, waiting for the owner to test.
- Navigation: Nav keeps a stack whose first item is the tab (home, search, library); other screens open on top and Back returns through them. Tabs along the bottom (NavigationBar) with the mini player above them whenever an article is open.
- Home (HomeFeed.kt), owner's sketch: "Article of the Day" as a large centred square card; a divider; a band with "Continue listening" (one square for the open article, or the latest unfinished one; tap continues playing; hidden when there is none) and, beside it behind a vertical divider, "Suggested topics" as square tiles in a row the user swipes; a divider; "Popular reads" (today's most read) as square cards in a row the user swipes. No automatic scrolling. Feed from en.wikipedia.org/api/rest_v1/feed/featured/YYYY/MM/DD, falling back to api.wikimedia.org's feed address and to yesterday; pictures and descriptions are then asked for separately with pilicense=free because the feed's own pictures can be non-free. Fetched once a day and saved for offline use (files/home.json). Main Page and special pages are left out of Popular reads.
- Topic tiles: a placeholder list in HomeFeed.TOPICS (name and search words), coloured squares; tapping opens a topic page with full-text search results. Replace with the owner's list later.
- Article page (Reader.Preview): opened from Home, topics and search. Picture, title, description, listening time at the current speed, section count, word count, progress and download status if in the library, Play (or Continue), Download, Open on Wikipedia, the opening paragraph and attribution. The text is fetched to show this but the article joins the library only on Play or Download.
- Library tab: filters All, In progress, Finished, Downloaded. A "finished" flag is now saved when an article is heard to the end.
- Player: a Sound button (background sound and volume, the same setting as in Settings) joins Speed, Sleep and Sections; Restart moved to the ⋮ menu.
- Sharing a link or pasting one still opens the player directly.
- Unit tests: FeedTitlesTest (2).
Open:
- Not run on a phone; the daily feed has never been fetched from here (no access to Wikipedia from the build machine).
- Library vs saved: the owner may change this later.

Owner confirmed build B (build 23) looks good, but Popular reads was empty.

Icons, topic pictures, Popular reads fix (owner, 17 November 2026): built, merged on a passing build at the owner's request, waiting for the owner to test.
- Pictures everywhere: Wikipedia's own picture if free-licensed; otherwise a generic icon for the kind of article (Category.kt) on a tinted square. The kind comes from the article's short description, using the head of the phrase (words before "by", "of", "in"...) and the last keyword in it ("American film director" is a person, "2019 song by American singer" is music), then the title if there is no description. Icons: game, war, person, place, nature, animal, film, music, book, sport, space, science, history, building, and a plain article icon. They are vector drawables in res/drawable/ic_cat_*.xml based on Material Design icon shapes.
- Descriptions are now fetched with the article text and stored in the library; articles saved earlier fall back to their title.
- Suggested topic tiles show a free picture from one of the topic's first articles (looked up once a day with the feed), with the name on a dark band; topics without one show the topic's own icon (HomeFeed.Topic has a category).
- Popular reads was empty because, early in the UTC day, today's feed has the featured article but no most-read list yet, and that half-filled day was saved as done. Now the most-read list falls back to yesterday's feed, a day is only marked done when both parts are present, and Home can be pulled down to refresh.
- Unit tests: CategoryTest (20).

Owner confirmed build 25: Popular reads filled in, topic pictures show, the Article of the Day icon is right.

Queue and collections (builds C and D together, owner, 18 November 2026): built, merged on a passing build at the owner's request, waiting for the owner to test.
- Queue (Queue.kt, pure part QueueState.kt): "up next" and a short history of library article ids, saved in files/queue.txt. Play next (goes first), Add to queue (goes last), reorder, remove, clear. When an article ends the next one opens and plays where it was left, unless a sleep timer was set for the end of the article. Previous goes back through the history, or to the start of the article if there is none. Deleting an article removes it from the queue.
- Collections (Collections.kt, pure part CollectionFormat.kt, files/collections.txt): named, ordered lists of library articles, like playlists; an article can be in any number. Play resumes each article where it was left and skips finished ones (owner's choice); "Play from the start" in the ⋮ menu resets them first. Playing a collection loads it into the queue, so the queue can then be changed without changing the collection. A collection's picture is its first article's.
- Library tab: a Collections row at the top (square tiles, plus a New collection tile), then the articles with their filters. Press and hold an article for: Play next, Add to queue, Add to collection, Rename, Mark as finished / not finished, Delete.
- Collection screen: picture, name, article count and total time, Play, and each article with up, down and remove buttons; tap an article to play the collection from there.
- Queue screen (queue icon on the player): Now playing and Up next, with up, down, remove and Clear; tap an article to jump to it.
- Article page: Play next, Add to queue and Collection… buttons (the article is saved to the library first).
- Player: previous and next article buttons either side of the 15-second skips.
- Lock screen and notification: previous article, back 15 s, play/pause, forward 15 s, next article. The media session's skip buttons and headphone next/previous move between articles; back/forward 15 s are custom actions, which newer Android shows beside them. The Stop button is gone; swiping the notification away while paused stops playback.
- Not done (owner undecided): a Save/bookmark button separate from playing, sorting, search within the library.
- Unit tests: QueueStateTest (6), CollectionFormatTest (3).

Owner confirmed build 27 (queue and collections) works well, but wanted the Library split into Saved and History.

Saved and History (owner, 18 November 2026): built, merged on a passing build at the owner's request, waiting for the owner to test.
- Library tab: Collections row at the top, then Saved and History tabs, each with the All / In progress / Finished / Downloaded filters.
- Saved: articles kept on purpose with the bookmark (article page, player top bar, press-and-hold Save/Unsave). Downloading an article saves it. Playing does not.
- History: articles listened to (and ones opened from a shared or pasted link), most recently opened first. "Remove from history" in the press-and-hold menu; "Clear history" empties it and deletes from the phone any article that is not saved, in a collection, queued or open.
- Existing articles (no saved/inHistory in their details) are put in History; downloaded ones and ones in collections are also marked Saved (Library.migrateSaved at start-up).
- Not done: saving from Home or search cards by press and hold.
- Unit tests: LibraryEntryTest gains 2.

Owner confirmed build 29 (Saved and History) looks good.

Suggested topics from History (owner, 18 November 2026; option 2 of two offered): built, merged on a passing build at the owner's request, waiting for the owner to test.
- The tiles look the same; what changes is how topics are chosen. The app takes the 10 most recently opened History articles, asks Wikipedia for their visible categories, and turns the categories into topics (TopicPicker.kt): first ones that several recent articles share, most shared first; then one per recent article, the shortest name. An article's own category ("Vietnam War") is never suggested, nor maintenance, date, birth or alumni categories, nor near-duplicate names. Up to 8; the general placeholder list fills any remaining places, and is used alone while History is empty.
- Tapping a suggested topic lists the articles in that Wikipedia category, leaving out ones already in the library. Each tile's picture is the first free picture among the category's articles; its icon comes from the topic name.
- Worked out again when Home opens and the recent History has changed; saved in files/suggested.txt for offline use.
- Unit tests: TopicPickerTest (7).
Things raised with the owner as possibly forgotten (not built): a library backup/export (needed before switching to a private signing key, which forces a reinstall), a contact address in the app's requests to Wikipedia, testing on other phones, shared downloads on Android 9 and older, storage management.

