# Verification record

## CalcForAll 2.2.0 — host, offline device and packaged-runtime checks passed

**Status on 15 November 2026, India time:** Android and local API version `2.2.0`; Android version code **7**; baseline delivered 2.1 source `6e82146`. The final host gates passed **1,448 tests**, current artifact audits and **19/19 API smoke checks** passed, and the same APK passed **27/27 native tests on both API 23 and API 34**. Combined device QA passed with **eight API 23 and nineteen API 34 manual observations** tied to that APK and their evidence. Source/archive integrity is determined separately by the release manifest and package-audit receipt; the tests here do not assert their outcome. Earlier candidates and releases remain evidence for their own artifacts.

### Completed host gates and artifact identity

| Check | Completed result | Evidence |
| --- | ---: | --- |
| Core tests | **1,043 / 83 suites** | Current JUnit XML and `artifact-final-summary.json` |
| Android local unit tests | **327 / 41 suites** | Current JUnit XML and summary |
| API tests | **69 / 4 suites** | Current JUnit XML and summary |
| Battery harness tests | **9 / 4 suites** | Current JUnit XML and summary |
| Combined host tests | **1,448; 0 failures, errors or skips** | Final Gate4 summary |
| Gate3 | **BUILD SUCCESSFUL, 35m 49s** | `all-modules-3.log` |
| Gate4 | **BUILD SUCCESSFUL, 9m 31s** | `all-modules-4.log` |
| Battery case records | **1,910 PASS, 110 PROBE, 0 FAIL** | All B1/B2/B3 records; PROBE is exploratory |
| Core Android-import boundary | **159 files; no Android imports** | Both gate logs |
| Android lint | **0 errors, 4 warnings, 4 informational findings** | XML plus explicit Gate3 analysis proof |
| API 23 native tests | **27/27; 124.877 seconds** | Instrumentation log and same-installed-APK receipt |
| API 34 native tests | **27/27; 90.028 seconds** | Same-installed-APK receipt, 1,024 MiB allocation |
| Packaged API smoke | **19/19; owned JVM stopped** | `api-2.2-smoke-final.json`, bound to the archive below |

Gate3 freshly executed lint analysis; its unchanged XML report was cached. A nullable warning cleanup affected only an Android unit-test source after Gate3's unit-test task. Gate4 reran the host tests while production sources stayed frozen; its lint analysis was up-to-date. The release configuration therefore explicitly selects **Gate4 for final host acceptance and Gate3 for lint evidence**. Packaging retains and hashes both completed logs and still requires the lint analysis to have executed after current production/build inputs. No older-log fallback or relaxed lint rule is used.

| Final artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `android-debug.apk` | 56,618,774 | `890858d5f43d0601c2d814221954e0a323e9a9d80656e089d5667460364f473e` |
| `android-debug-androidTest.apk` | 505,066 | `99f14a9b6b5e77995d2a7bf90213edff7c177e84106c65e250e63fc55c7587f2` |
| `api-2.2.0.zip` | 10,722,560 | `600d77ecb9976b27c1157a2341ff5aee4fdaed7043a874d53cd547718dc335de` |

### Earlier candidates and the reason for another gate

Candidate1's Gate2 completed **1,430 host tests**: 1,040 core, 312 Android, 69 API and 9 battery tests, with no failures, errors or skips. Its battery records contained **1,910 PASS, 110 PROBE and 0 FAIL**; PROBE is exploratory, not a passing assertion. Lint reported **0 errors, 4 warnings and 4 informational findings**. Both API 23 and API 34 native runs passed **25/25** on that candidate. Those receipts are preserved under `candidate1/`.

Those successes did not establish release readiness: manual orientation search timed out three times, including a trial allowed to run without UI inspection. Ordinary **Read again** completed. The 45-second application deadline was not increased to turn those failures into passes. The earlier Gate1 lint failure also remains an earlier-attempt record, not an accepted gate.

Candidate2 passed **314 Android local unit tests** in its diagnostic build. Its API 34 native run passed **25 of 26**, with the default numeric root search exceeding its unchanged **5,000 ms** budget after **234 evaluations**; instrumentation ran for **396.488 seconds** overall. Native tracing observed OCR requests taking roughly **12–25 seconds**, compared with approximately three seconds in earlier observations. Cleanup/layout took much less time in that trace; it did not establish a main-thread handoff bottleneck. A SystemUI ANR prevented reliable manual UI acceptance. Candidate2's APK and diagnostic evidence are preserved under `candidate2/`, separate from final acceptance.

The final runs used **768 MiB for API 23** and **1,024 MiB for API 34**, as recorded in their receipts. API 34's smaller allocation followed the earlier host-pressure/ANR diagnosis; it is not proof of that diagnosis or a general speed claim.

### Corrections checked on the host and both API levels

The orientation batch now reuses one recognizer across three bounded geometric trials. Completion-owned leases retain it while a non-cancellable SDK task runs; owner cancellation defers disposal until that task completes. A later request remains gated through cleanup. Completed orientation choices are published incrementally and retained after a later interruption. The user can explicitly choose a completed option while other trials remain busy. The software does not select a numerical winner or silently change the expression.

The trigonometric kernel skips construction/subtraction of an enclosed zero multiple of `2*pi` when the center is already in `[-3,3]`. The same Taylor calculation and full input uncertainty radius remain. Independent review found the enclosure argument sound; all six reference bins in the three new tests were separately checked using exact rational Taylor arithmetic, including their omitted-term bounds. The final host suite and both native suites passed. No numerical precision, uncertainty, input or elapsed-time limit was relaxed.

The batch native fixture asserts **one recognizer client per three orientation trials**, with completion-owned cleanup. The six API 23 orientation reads observed in that testing took roughly **30 seconds in aggregate**. One final API 34 manual three-orientation batch recorded close at **10,334 ms**, within the unchanged 45-second limit. These observations verify bounded workflows on these emulators; they are not a controlled comparison or a general performance improvement claim.

Other 2.2 changes include whole-edit ink undo/redo for drawing, erasing and Clear pad; conservative detached/crossed digit variants; staged image replacement that preserves the old draft on load failure; explicit contrast/polarity treatment; orientation review; and withholding automatic expressions from rejected/incomplete OCR. Ink history is memory-only, with at most 32 snapshots and 96,000 points across snapshots plus current ink. Function shortcuts wrap reviewed text, such as `30` to `sin(30)`; they do not recognize handwritten function words.

The allocation-failure regression injects a synthetic `OutOfMemoryError`. It tests refusal, draft preservation and retry, **not recovery from actual physical-device memory exhaustion**.

### Both native gates and recorded manual observations

The Android 6 / API 23 emulator passed **27/27 native tests in 124.877 seconds**, using **768 MiB**. Android 14 / API 34 passed **27/27 in 90.028 seconds**, using **1,024 MiB**. These are instrumentation-reported durations, not total boot/run wall times. Both receipts match the installed APK to the artifact above, identify the same instrumentation APK and source/build inputs, and confirm no active default network with Wi-Fi/mobile data disabled before and after testing. Receipts: `android-device-api23-run.json`, `android-device-api34-run.json` and their corresponding final logs.

- **Manual partial-choice behavior: not yet claimed.** Host tests cover retained progressive choices and cancellation. An actual cancelled search and successful retry are recorded below; the later late Cancel tap is not a partial-result cancellation pass.
- **Combined `device-final-qa.json`: passed.** It records both current-APK native passes and the 8/19 manual observations, with hashes of their supporting evidence. `manual-network-final.txt` also records no active default network and disabled Wi-Fi/mobile data after the manual work.

Eight **new observations of the final APK** are recorded in `manual-qa-input.json` with screenshot/XML evidence:

1. Four ADB touch strokes produced handwritten `1+1`, editable transcription and automatic answer **2**, without typing the expression.
2. One **Undo ink** restored all four strokes after one eraser gesture.
3. The eraser sweep itself left the pad and transcription empty.
4. One **Redo ink** reapplied that complete erasure.
5. **Clear pad**, then one **Undo ink**, restored the complete drawing and `1+1` transcription.
6. The **sqrt** shortcut wrapped the reviewed expression as `sqrt(1+1)` while retaining the ink. The initial function-wrap screenshot clips the answer area and is not standalone evidence of √2.
7. Closing and reopening **Write & calculate** retained the wrapped correction without re-entry.
8. A later scrolled screenshot, `v22-api23-reopened-answer-visible.png`, shows `sqrt(1+1)`, exact **√2**, decimal `1.41421356237` and the review-before-save control together. This supplies the visible exact-answer evidence.

These are emulator touch observations, not physical finger/stylus validation. Candidate1's earlier manual records remain historical and were not relabelled as final checks.

The API 34 ledger records nineteen final photo observations:

1. The sideways fixture initially left the automatic expression empty with a layout warning. **Try other orientations** offered 90/180/270-degree choices without selecting one; the trace recorded batch close at 10,334 ms.
2. Selecting **270 degrees clockwise** produced editable `12+34` and answer **46**.
3. The displayed photo was visually confirmed upright and consistent with that transcription.
4. **Cancel reading** was tapped during an active search; a cancellation message appeared and the existing `12+34` draft stayed intact, without choosing an orientation.
5. Retrying after that confirmed cancellation completed all three choices and retained the draft. A later Cancel tap arrived after completion and is explicitly not counted as partial-result cancellation.
6. A new capture from the **emulator system camera**, using a synthetic scene, produced editable `12+34` and answer **46**. The 960 × 1,280 image's read returned at **33,761 ms** in the metadata trace; that is a read timing, not the duration of the entire camera interaction. This is not a physical-camera test.
7. The camera transcription was manually changed to `12+35`, giving **47**. Choosing the 50,001-pixel-wide oversized fixture was refused and retained both corrected text and answer. An earlier inspection swipe accidentally invoked raw text; the check was repeated from a freshly corrected draft before recording the outcome.
8. A separate screenshot confirms that the same refusal retained the previous camera preview alongside the dimension error, rather than replacing it with a blank image. Its XML retains `12+35`; the repeated check separately shows **47**.
9. **Boost contrast** changed the synthetic faint `12+34` preview from grey to black on white, visibly matching the selected treatment. The original already calculated **46**, so no recognition-accuracy improvement is claimed.
10. Reading that treated image displayed editable `12+34` and answer **46**.
11. Choosing **Original** restored the faint grey source pixels and retained the expression; the completed read again displayed **46**. This verifies reversal of the treatment, not a cumulative edit to the already processed pixels.
12. The original dark fixture visibly showed light `12+34` on a dark background. Its transcription was already correct, so it does not demonstrate an accuracy gain.
13. **Light text on dark** changed that preview to black text on white; the full selected treatment label and changed pixels were visually inspected.
14. The treated synthetic image produced editable `12+34` and answer **46**.
15. The two-line system image showed a layout warning with an empty editable expression and no automatic answer, while complete raw-text review remained available.
16. Selecting **Use raw recognized text** populated `X+y=5` followed by `X-y=1`; the visible result was **x = 3; y = 2**. No expected expression or manual text correction was supplied to recognition.
17. One **Save reviewed calculation** added that system as history row **6**, with six total calculations and the correct result.
18. Reopening Photo calculator after saving showed no selected image, no transcription and no answer, confirming that the saved draft cleared.
19. After force-stop/restart, history row **6** retained the two-line system and **x = 3; y = 2**. The earlier row **5**, `sqrt(16) = 4`, remained below it.

The explicitly selected logs `photo-timing-api34-ui-search.log` and `photo-timing-api34-camera.log` retain the raw 10,334 ms orientation and 33,761 ms camera-read observations. They contain stage/dimension/timing metadata without OCR text or image content. Their portable evidence inclusion and hashes are checked by the separate release-package audit.

These are the recorded workflows on this APK, not a claim that every possible gesture, image or lifecycle combination has been exercised.

Native coverage includes rotated-pixel/ownership checks, faint/dark printed-image treatment, explicit orientation alternatives, omitted one-sided fraction-rule refusal, real SDK cancellation/retry and deferred batch cleanup. These synthetic fixtures do not establish general recognition accuracy. Final manual statements must distinguish actual cancellation from a read that finished before the tap.

### Eight-image corpus: separate automatic, assisted and raw review

Both final corpus receipts independently record **four automatic matches, three assisted-only matches through explicit corrections, and one requiring raw-text review**, across the same eight synthetic images. Every entry remains PROBE with `accuracyBenchmark: false`. The automatic matches are addition, parentheses, quadratic and stacked fractions; root-integer, root-decimal and raised-power fixtures need explicit corrections. The two-line system is not an automatic or assisted match. Individual elapsed times remain in each corpus JSON; this is not eight automatic successes or a population accuracy benchmark.

The eighth image is the two-line system. Raw OCR retained `X+y=5` followed by `X-y=1`, but the spatial reader rejected the arrangement as unsupported multiline layout. In 2.1, automatically falling back to that raw text happened to give the correct result. Version 2.2 removes that fallback: rejected layouts leave the automatic expression empty even if the raw string could calculate. Complete raw text requires explicit review; incomplete prefixes are not offered as complete expressions. The final API 34 manual check confirmed that explicit raw review produced **x = 3; y = 2**, one reviewed save cleared the photo draft, and the row survived restart. This manual workflow does not change the corpus's automatic or assisted-match count.

### Artifact, runtime and distribution checks

The current API archive passed **19/19 smoke checks**, and its owned JVM stopped; the receipt matches the API SHA-256 above. APK security, static native alignment and packaged-notice audits also passed against the exact artifacts above. Committed source/archive integrity and the four-artifact checksum/evidence audit are recorded separately by the release manifest and package-audit receipt; no package-audit pass is inferred from these checks.

`apk-security-final.json` binds successful verifier execution and both raw reports to the current APK. Verification passed **v1/v2**, with one existing Android Debug signer. The only declared/requested permission is the app-specific `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; **INTERNET is absent**. Raw signature warnings remain visible. This is a development-signed APK, not a Play release signature.

The current native audit passed its defined static checks: four OCR libraries have 16 KB-aligned ZIP data offsets; the two 64-bit libraries pass ELF alignment/RELRO checks, while the two 32-bit libraries retain 4 KB ELF alignment. These checks are **not execution on a 16 KB-page runtime**.

The current notice audit matched **36 APK legal files, 38 API notice files and 21 external API JARs**, with two project API JARs and the desugaring corresponding-source archive also checked. These passed artifact-level checks remain separate from the source/evidence distribution audit.

Consult the release manifest and `release-package-v2.2-audit.json` for committed source inventory, archive/evidence correspondence, final checksums and the distribution audit outcome. This source record does not assert that outcome. Earlier 2.1 release outputs and verification remain unchanged.

### Retained limits

Recognition is an editable, bounded aid. A valid numerical answer does not establish a correct transcription. Unrestricted cursive, handwritten function words, arbitrary page/word-problem interpretation and unrestricted CAS remain unsupported. Orientation changes require explicit choice; new line/layout ink replaces the draft. Unsaved photos, ink and edit history are memory-only; reviewed saving writes calculation history.

Physical phones, physical styluses, TalkBack and a 16 KB-page Android runtime remain untested. Numerical/symbolic operations retain documented domain, precision and work limits. Processing remains offline and free, without image uploads, service charges or runtime model downloads. No recognition model or bundled runtime dependency was added in this increment.

## CalcForAll 2.1.0 — host, device and packaged-runtime checks passed

**Status on 14 November 2026:** Gate2 passed **1,384 host tests**, **21/21 native tests on each of API 23 and API 34**, and **19/19 packaged-API smoke checks**. The same APK passed the recorded offline manual workflows, signature verification, packaged-notice and static native-library checks. Android version is `2.1.0`, code **6**; the local API is `2.1.0`. This increment starts from delivered 2.0 source `f38a2ac`. Source/archive integrity is established separately by the release manifest and final package-audit receipt. The 2.0 records below remain historical evidence for their own artifacts.

### Host gate and artifact identity

`work/2026-11-14-v2.1/all-modules-2.log` ended **BUILD SUCCESSFUL in 10m 56s**. The gate completed core checks, tests, lint, app/test APK assembly and API distribution tasks; unchanged tasks could be up-to-date. JUnit XML and `artifact-final-summary.json` establish these results:

| Check | Completed result |
| --- | --- |
| Core | **1,040 tests / 82 suites** |
| Android local unit tests | **266 tests / 35 suites** |
| API | **69 tests / 4 suites** |
| Battery harness | **9 tests / 4 suites** |
| Combined host tests | **1,384; 0 failures, errors or skipped tests** |
| Core Android-import boundary | **158 files; no Android imports** |
| Battery case records | **1,910 PASS, 110 PROBE, 0 FAIL**; PROBE is exploratory, not a passing assertion |
| Android lint | **0 errors, 4 warnings, 4 informational findings** |

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `android-debug.apk` | 56,596,614 | `95b6d8f710aace26e823c683b68eabdd10f141fda3249505fd0d94145a3d088b` |
| `api-2.1.0.zip` | 10,722,246 | `716eecdfc8094f15360e95be28e875e91cfb15b44de7db411a8a46bde877ac78` |

The new regressions cover complete OCR token coverage, bounded input, crop/orientation geometry, serialized native recognition completion, retained workspaces, swept erasing and resize mapping, percent/factorial candidates, session checkpoints and durable-save rollback/recovery. Tests of synthetic glyphs establish those fixtures, not general recognition accuracy.

### Same-APK native and recorded UI checks

Both installed APK hashes matched the artifact above. **API 23 passed 21/21 tests in 104.125 seconds; API 34 passed 21/21 in 57.993 seconds**, as reported by instrumentation. Each run confirmed no active default network and disabled Wi-Fi/mobile data before and after testing. These are emulator observations, not physical-device benchmarks. Receipts: `android-device-api23-run.json`, `android-device-api34-run.json` and their corresponding final logs.

Native checks include original-image detail after preview/source-file disposal, all eight EXIF orientations with four user rotations, bounded odd-edge decoding, activity recreation retaining manual drafts and pending render/save state, and actual storage failure/recovery. The eight-image synthetic photo corpus on **each** API recorded **five automatic matches and three assisted-only matches** through explicit offered corrections. It remains `accuracyBenchmark: false`; no expected expression is fed to recognition as a correction.

The combined `device-final-qa.json` is **passed**, with six API 23 and seven API 34 manual observations tied to the current APK and evidence hashes. `manual-qa-input.json` retains the actual observations:

- **API 23:** four 220 ms finger strokes produced `1+1 → 2`; rapid Undo/Redo restored it. A manual correction to `3+4 → 7` survived closing/reopening. An injected save obstruction produced a visible refusal without a history write; removing it and retrying saved exactly one row, which survived force-stop/restart. A fast sparse stroke erased at its midpoint, and ink remained visible/proportionate through landscape and back.
- **API 34:** the system camera returned `12+34 → 46` offline. A manual edit to `12+35 → 47` survived reopening with its photo. A source crop that preserved the expression calculated 46 after immediate close/reopen. An earlier accidentally over-tight selection removed `34` and produced an input error; Restore original recovered 46 before the check was repeated with the full horizontal expression.
- **Rotation accuracy limit:** a 90-degree photo rotation remained visible after reopening, but sideways OCR proposed the inaccurate `12+3 → 15` with an unsupported-layout warning. This tested rotation retention and restoration, not correct rotated OCR. Restore original recovered `12+34 → 46`.
- **Explicit root correction and durable photo save:** the imported root fixture stayed `V16` with an unknown-name error until the offered `sqrt(16)` correction was selected; the preview then showed 4. One reviewed save added exactly one history row. Reopening Photo calculator showed that the saved draft had cleared; force-stop/restart retained row 5, `sqrt(16) = 4`.

A manual attempt to interrupt an active read finished before the cancellation tap, so **no manual in-flight cancellation pass is claimed**. The unit tests passed cancellation of an active recognizer, cancellation of a queued waiter, duplicate completion, and retry after startup/native failure; the completion slot remains occupied until the native operation actually ends. Pending crop/render requests and corrections are retained in memory across tool reopening/activity recreation; this does not promise recovery of unsaved drafts after process death.

### Artifact and packaged-runtime checks

The current-APK static native audit passed its defined checks: four OCR libraries have 16 KB-aligned ZIP offsets; the two 64-bit libraries pass the ELF alignment/RELRO checks. The two 32-bit libraries retain 4 KB ELF alignment. The legacy `zipalign` check passed; direct inspection supplies the 16 KB ZIP-offset check. This is **not execution on a 16 KB-page runtime**. Receipt: `apk-native-alignment-v2.1.json`.

The same-artifact notice audit passed: **36 APK legal files, 38 API notice files and 21 external API JARs** matched their inputs, with two project API JARs and the desugaring source archive checked. Receipt: `packaged-notice-audit-v2.1.json`.

APK signature verification passed **v1 and v2** with **one Android Debug signer**. v3/v3.1/v4 and SourceStamp were not present. The META-INF/JAR-entry warnings remain in `apk-signature-final.txt`; the result is not described as warning-free or production-signed. `apk-permissions-final.txt` lists only the app-specific `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, with **no INTERNET permission**.

The extracted `api-2.1.0.zip` passed **19/19 runtime smoke checks** against the archive hash above, using a 256 MiB owned JVM and the default loopback binding. Arithmetic, uncertainty, tools, schema/resource refusals and subsequent recovery passed. The owned JVM was stopped and temporary extraction removed. This was a local runtime check, not public deployment. Receipt: `api-2.1-smoke-final.json`.

The release manifest and separate `release-package-v2.1-audit.json` record the committed source inventory, archive/evidence correspondence and four final artifact checksums. Consult those receipts for the distribution result; the tests above do not themselves assert archive integrity.

### Limits retained

Photo and ink transcription can be wrong even when the resulting expression calculates. Supported neat symbols, selected spatial powers/fractions/roots and equations remain bounded; unrestricted cursive, arbitrary pages, diagrams, natural-language word problems and a full CAS are not implemented. Offered OCR changes require explicit selection, and only reviewed saving changes history. Offline processing uses no paid API or image upload.

No physical-phone, physical-stylus, TalkBack or 16 KB-page runtime test has been recorded. API 23 is the minimum target, not proof of every phone. Numerical sampling and symbolic operations retain their documented domain, precision and resource limits. The supplied APK is a development build; public API deployment and production signing remain outside this handoff.

## CalcForAll 2.0.0 — host and device acceptance passed

**Status on 14 November 2026: Gate10 passed all 1,326 host tests, the full build and 19 packaged-API smoke checks. The same APK passed 14/14 device tests on each of API 23 and API 34, plus the recorded offline handwriting/camera/correction/persistence checks. Signature, packaged-notice and static native-library audits passed. Distribution/archive checks are separate and supplied by the release manifest and package-audit receipts.** Android version name is `2.0.0`, version code 5; the local API distribution is `2.0.0`. This integration started from the completed `1.3.0-preview` source. Version numbers describe this supported release scope, not completion of every feature in the historical v2/v3/v4 roadmap.

### Completed host gate: Gate10

The complete Gate10 invocation ended **BUILD SUCCESSFUL at about 12:09 IST on 14 November 2026**, with **19m 51s elapsed** reported by Gradle. It ran core checks, Android/API/battery tests, lint, APK and instrumentation-APK assembly, and API distribution packaging. The counts below were read from JUnit XML after completion. The integration workspace retains `work/2026-11-13-resume/all-modules-10.log`, `artifact-final-summary.json` and the associated reports. The elapsed time describes this host invocation, not calculator response time.

| Check | Completed Gate10 result |
| --- | --- |
| Core tests | **1,037 tests in 81 suites**, 0 failures, 0 errors, 0 skipped |
| Core Android-import boundary | **157 files** scanned, no Android imports |
| Android local unit tests | **211 tests in 25 suites**, 0 failures, 0 errors, 0 skipped |
| Local API tests | **69 tests in 4 suites**, 0 failures, 0 errors, 0 skipped |
| Battery harness | **9 JUnit tests in 4 suites**, 0 failures, 0 errors, 0 skipped |
| B1/B2/B3 case records | **1,910 asserted PASS, 110 exploratory PROBE, 0 FAIL** across 2,020 cases. PROBE is an observation, not a passing assertion. |
| Android lint and build | **0 errors, 4 warnings, 4 informational findings**; `:android:lintDebug`, `:android:assembleDebug` and `:android:assembleDebugAndroidTest` completed successfully |
| Local API package | `:api:distZip` completed successfully |

These results cover the Gate10 source state, including directed point division, sign-aware interval multiplication, reuse of trigonometric series terms, and reuse of the certified sine/cosine enclosure for exact or known-point inputs. Six interval-kernel and four exact/known-point trigonometric regression tests passed, including independent numerical references and retained resource guards. Gate10 increases the polynomial solver's cooperative allowance from **3 to 10 seconds** for slower Android arithmetic; degree, size, iteration and root-acceptance checks remain unchanged. Graph range searches retain their separate five-second default. No numerical precision or acceptance criterion was relaxed. The final device/manual checks below exercised this same APK.

### Gate10 artifact and packaged-runtime checks

| Artifact | Recorded SHA-256 |
| --- | --- |
| `android-debug.apk` — 56,451,826 bytes | `597697cc5d8d7f47fa11db1071c2110dd0dfe916f7d478657394dd3b23996bf4` |
| `api-2.0.0.zip` — 10,720,293 bytes | `40e2d389174667200cf598e8dc6109f0a35ad814aef9357c1091ea67334f1c86` |

The packaged API passed **19/19 smoke checks** on 14 November using its extracted distribution, a 256 MiB heap and the default loopback binding. Checks included health/discovery, lossless arithmetic, uncertainty round-trip, finance, probability, dates, tables/series, eigenvalues, bitwise operations, tiny/discontinuous limits and rejected-request recovery. The owned server was stopped and temporary extraction removed. This was a local runtime check; no public deployment was made. Receipt: `api-2-smoke-final.json`.

APK verification passed **v1 (JAR) and v2 signature schemes**, with one signer; v3/v4 and SourceStamp were not present. This remains a development-signed APK. The verifier's META-INF/JAR-entry warnings are retained in `apk-signature-final.txt`, rather than represented as a warning-free result.

The static native-library audit found **four OCR libraries**. All four uncompressed ZIP data offsets are 16 KB aligned. The **two 64-bit libraries** (`arm64-v8a`, `x86_64`) have 16 KB-compatible ELF load segments and passed the recorded RELRO checks. The two 32-bit libraries (`armeabi-v7a`, `x86`) retain 4 KB ELF load alignment and are not counted as 16 KB ELF passes. The installed legacy `zipalign` check passed; it lacks `-P 16`, so 16 KB ZIP offsets were inspected directly. This is static inspection, **not execution on a 16 KB-page device**. Receipt: `apk-native-alignment-v2.json`.

The packaged-notice audit passed against these same artifact hashes: **36 APK legal files, 38 API notice files and 21 external API JARs** matched their recorded inputs; the API also contains two project JARs. The desugaring corresponding-source archive hash was checked. This verifies packaging and input correspondence, not a separate legal opinion. Receipt: `packaged-notice-audit-v2.0.json`.

### Completed device and manual checks

The **Gate10 APK passed 14/14 tests on Android 14 / API 34** in **42.395 seconds**, and **14/14 on Android 6 / API 23** in **101.511 seconds**, as reported by the instrumentation runners. Both installed APK SHA-256 values matched the Gate10 artifact above. Neither device had an active default network before or after its run. Both the default range-root search and the quintic polynomial test passed on each device. Receipts: `android-device-api34-run.json`, `android-device-api23-run.json` and their corresponding final logs. These are emulator results, not physical-device benchmarks.

Manual API 23 checks at **130% font scale** on the 720×1280 emulator verified finger handwriting: four 220 ms strokes formed `1+1`, and the preview automatically showed 2 after pen-up without Calculate or text editing. Undo changed the draft to `1+`, displayed an unexpected-end error and cleared the answer; Redo restored the expression and result 2. Reviewed saving increased history from 9 to 10 exactly once. Force-stop/restart retained the expression and history count; row 10 showed `1+1 = 2`. Offline state was checked again after this workflow. The ink-result screenshot and three API 34 screenshots were visually reviewed. Combined receipt: `device-final-qa.json`, with `passed: true` for both devices.

Manual checks on that same API 34 APK verified these workflows:

- **System camera → calculation → save:** native Shutter and Done returned `12+34`; the preview automatically showed 46 without a Calculate tap. Explicit reviewed saving increased history from 3 to 4 rows, and temporary captures were removed.
- **Rotation and persistence:** result 46 remained visible through portrait → landscape → portrait. After force-stop/restart, history still contained four rows, including saved row 4 `12+34 = 46`.
- **Offered correction without saving:** an imported root fixture produced raw `V16`, which was refused. Explicitly selecting the offered `sqrt(16)` correction automatically showed 4; closing without saving left history at four rows.
- **Offline and visual evidence:** no active network remained after manual checks; the app has no INTERNET permission. Three screenshots of the main result, camera answer and offered correction were visually reviewed.

Receipt: `device-final-qa.json`. The API 34 eight-image synthetic corpus recorded **five automatic matches and three assisted-only matches through offered corrections**. It is labelled PROBE and `accuracyBenchmark: false`, not a population recognition score. Receipt: `photo-corpus-api34-final.json`.

### Earlier 2.0 attempts and corrections

The earlier Gate8 APK passed **14 of 14 tests on the Android 6 / API 23 emulator**, with **70.601 seconds** reported for the complete instrumentation run. This includes the default catalogued operations, polynomial equations, known numerical answers, bundled OCR, image-size limits, radical correction/scope checks, private camera-URI decoding and capture cleanup. In that run the default 200-sample `cos(x)=x` root search completed within its unchanged five-second work budget. Preserved receipt: `android-device-api23-gate8-pass.log`, packaged under `historical/gate8-api23-passed-before-stroke-fix.log`. This does not assert that every search or phone meets that budget. The current final native/corpus receipts belong to Gate10 and are not cited as evidence for Gate8.

For comparison, Gate6 passed 13 of 14 API 34 tests but refused the default root search after its five-second budget. Gate7 passed 1,031 host core tests, yet the API 23 full run and a focused warm retry still hit that root-search limit. Gate8 adds the multiplication and duplicate-kernel optimizations described above and now passes that test on API 23. These were availability failures with explicit refusals, not wrong numerical answers. The Gate7 log's 15h 56m 3s elapsed included machine interruption/sleep and must not be read as active build time. Earlier logs remain retained; they do not replace the current artifact receipts.

Gate8's full host gate passed in 30m 41s with 1,037 core, 204 Android, 69 API and 9 battery tests. Gate9 passed in 18m 19s with the same counts except 211 Android tests. Gate9's API 34 instrumentation run then completed in **60.924 seconds with 13/14 tests passing**: the default range-root search passed, but `x^5-x-1=0` exceeded the polynomial solver's three-second budget. Two manual solves after the initial System UI ANR had recovered confirmed the same explicit refusal; the reason was not inferred solely from the test's `RouteResult.Err` cast failure. No partial or incorrect root list was accepted. Receipts: `all-modules-8.log`, `all-modules-9.log` and `android-device-api34-gate9-attempt1.log`, with manual UI evidence retained by the release owner. This led to the bounded ten-second polynomial allowance in Gate10, which now passes that test on API 34.

Earlier integration APK checks exercised handwritten `1+1` and imported `12+34` through recognition, automatic preview and reviewed saving on API 23. API 34 also exercised the actual system-camera callback, local decoding/OCR, `12+34 → 46`, one explicitly saved history row, and temporary-capture cleanup on success/cancellation. These are workflow observations, not a population recognition-accuracy score. Their APK revisions precede the final artifact receipt and must not be described as a completed final native gate.

### Distribution checks and remaining coverage

The release manifest supplies the source commit, archived-file inventory and artifact hashes. The separate `release-package-v2-audit.json` receipt records the distribution checks performed after the source commit: archive integrity, source correspondence, evidence correspondence and the four artifact checksums. Consult that receipt for the completed archive result. The source commit is recorded in the manifest rather than embedded in this document itself.

Host and emulator acceptance is complete for the documented scope. No physical-phone, physical-stylus or TalkBack test has been recorded. The small synthetic recognition corpus and manual examples do not establish general recognition accuracy.

### What the numerical checks establish

The new decimal contract distinguishes established absolute-error bounds from unknown accuracy. Tests cover directed elementary/inverse-function enclosures against independent references, uncertainty through arithmetic and stored values, domain/rounding refusals, close distinct roots, explicit repeated-factor proofs, tiny nonzero finance rates, scaled graph-root residual checks and small nonzero or discontinuous limits. The six identity/resource tests and all 200 nested-fraction expressions passed. No identity is proved merely because computations at different precisions agree.

Numerical integration, sampled limits, numerical complex roots and other legacy estimates may still have unknown rigorous accuracy. An unknown error is not zero. An ambiguous discriminant/determinant or an interval crossing a rounding boundary is refused; general symbolic identities outside the implemented rules can remain undecided. A finite numerical root scan can miss roots and may exhaust its work budget. See [NUMERIC-LIMITS.md](NUMERIC-LIMITS.md) and [SYMBOLIC.md](SYMBOLIC.md) for the supported subsets.

### Recognition and release limits

Photo and handwriting recognition remain bounded, editable transcription aids. Neat separated symbols, selected powers/fractions/root layouts and supported equations are in scope; general cursive, arbitrary pages, diagrams and natural-language word problems are not. OCR or a glyph candidate can be wrong even when its expression calculates successfully. **Save reviewed calculation** is the explicit history mutation; previews alone leave history, registers and Ans unchanged. Photos and processing remain local, with no paid API or runtime model download.

The supplied build is a debug APK, not a Play Store submission or a production signing setup. API 23 is the minimum compatibility target, not evidence for every Android phone. TalkBack, physical styluses, a representative recognition dataset and broader device/layout/performance coverage remain unverified. The local API is loopback-oriented; public deployment, authentication and hosting are outside this handoff.

---

## Historical Android 1.3 preview and API 1.0 preview

The following records describe earlier source and APK revisions. Their counts, hashes, timings and numerical policies are historical evidence; they do not verify 2.0.

### Android 1.3 update — evidence for that revision

Later on **12 November 2026** the owner asked for the next version. **1.3.0-preview** (version code 4) is the 1.2 code after the second review pass described below, plus four tools (geometry, sequences, business maths and quartiles) and, later the same evening, three smaller additions: numbers and cheque amounts in words, sharing an amount in a ratio, and inflation. The whole gate was run again on that state, one Gradle task per invocation, with `--rerun` on the test tasks and counts read from result XML written after each invocation started. Unlike 1.2, part of it ran on Android 6 and Android 14 emulators; what that covered, and what it did not, is set out under "On Android 6 and Android 14 emulators". Overnight into **13 November** four more review passes, the ninth to the twelfth, went through how the engine rounds, compares and subtracts, and how it treats approximate values. In the morning two more, the thirteenth and the fourteenth, reviewed the last of those rules and then the corrections to them. The figures below are from the gate run after the last of them.

| Check | Result |
| --- | --- |
| `:core:test` | **960 tests in 70 suites**, 0 failures, 0 errors, 0 skipped. XML written 06:41:02 on 13 November for a run started 06:40:24. The 1.2 gate had 893 tests in 65 suites; the sixty-seven added since are the second-pass regressions, the quartile tests, three third-pass regressions, the seven unit tests of the fourth and fifth passes, six for the extras (words, ratio shares, inflation), one for the power key after a number word, one for the whole part of an exact irrational, one for keyed fractions and powers staying one value, and forty from the ninth to fourteenth passes: thirteen for `round`, `frac` and `mod` of exact values, eight for values that agree to more digits than fifty, and nineteen for estimates. |
| `:core:checkNoAndroidImports` | Scanned **143 files**, no Android imports (136 at the 1.2 gate; the new files are `Quartiles.kt`, `UnitPowerSpec.kt`, `NumberWords.kt`, `NumberWordsAndSharesSpec.kt`, `RoundingFamilySpec.kt`, `NearlyEqualValuesSpec.kt` and `EstimatesSpec.kt`). Run at 06:46:32. |
| `:api:test` | **43 tests in 3 suites**, 0 failures, 0 errors, 0 skipped. XML 06:46:22, run started 06:45:24. The count is unchanged: the API is still 1.0.0-preview and exposes none of the new modules. It runs on the same engine, so the corrections below reach it. |
| `:battery:test` | BUILD SUCCESSFUL; **9 JUnit tests in 4 suites**, 0 failures. XML 06:42:34, run started 06:41:20. Case records identical to the 1.2 gate: B1 922 asserted passes and 78 probes, B2 479 and 21, B3 509 and 11, no failures and no critical cases. All forty probe observations printed in the log are the same, line for line, as in the tenth gate run. (That run was the first after keyed fractions and powers gained their outer brackets, which changed eight of the forty, in those brackets only.) |
| `:android:testDebugUnitTest` | **137 tests in 16 suites**, 0 failures, 0 errors, 0 skipped. XML written 06:45:12 for a run started 06:43:17. The 1.2 gate had 124 tests in 15 suites; the thirteen new ones are the four 1.3 tool adapters, five third-pass regressions, one from the fifth pass, one for the extras, one that reads the source to check that every text field switches the keyboard's autocorrect off, and one that reads it to check that the answer area says when an answer continues beyond what is shown. |
| `:android:lintDebug` | BUILD SUCCESSFUL; **0 errors, 27 warnings, 4 informational** findings. The warnings are the 21 of the 1.2 gate plus six "a newer version is available" notices for the two test-only libraries that the device gate needs (each notice is counted three times). None is in `MoreTools.kt`, `ToolsSheet.kt`, `ToolCalculator.kt`, `CalcApp.kt` or the device test. Run started 06:46:48 on the final sources: the analysis ran again and found the same issues, so Gradle kept the report written at 20:56:08 on 12 November as up to date. |
| `:android:assembleDebug` | BUILD SUCCESSFUL. `android/build/outputs/apk/debug/android-debug.apk`, version name `1.3.0-preview`, version code 4, **56,229,284 bytes**, SHA-256 `9b4d873b148d137c447ea9a39ab5bc338ab525156447a94a1a9d451b9dc3f9ff`, written 06:49:52. |
| `:android:connectedDebugAndroidTest` | On the emulators `CalcForAll_API23` (Android 6.0, API 23) and `CalcForAll_API34` (Android 14, API 34): **5 tests, 0 failures** on each, 0 errors, 0 skipped. XML written 06:51:45 (Android 6) and 06:51:01 (Android 14) for one run started 06:50:06, on the same sources and the same APK as the rows above. See "On Android 6 and Android 14 emulators" below. |

All eight invocations ran on the same source state, after every correction described below and with the three extras. Two engine runs earlier in the evening each failed a single new test whose expectation was wrong, and no engine code changed before the rerun: at 19:39:01 a test expected one particular refusal message for a triple complex root and the solver gave a different refusal, and at 20:01:57 a test expected a derived unit to be spelled `kg·m/s²` where the engine writes base units in SI order (`m·kg/s²`). Two runs after midnight failed for other reasons, and each led to a change. At 00:28 an older test failed that asserts a recorded gap, that `φ² = φ + 1` is answered False (D-026): the tenth-pass change had turned that False into a refusal, which is the better answer, and the test now asserts the refusal. At 02:15 a new test showed the measured allowance described under the twelfth pass answering 0 for a remainder whose quotient was known only to a tenth of a unit; a value is now read as a whole number only while its allowance is a billionth of a unit or less. Between 04:24 and 06:40 the engine suite was run seventeen times while the corrections of the thirteenth and fourteenth passes were made, and nine of those runs failed between one and three tests. One was an older test that pins ordinary sums of exact values to the digits they have always had: the ten digits of slack before such a sum is expanded further had been removed, and were put back. One was a round trip the suite already held: `floor(tan(atan(23)))` answered 22 when movements of up to ten units of the coarser precision were ignored, and the limit went back to two. The rest were new expectations. Some were wrong in themselves (a chain of `frac(3x + ln 2)` 120 deep was expected to give a number, and has no digits left to give). The others showed that the rule under test was not yet right, and the rule was changed: those are in the tables under the thirteenth and fourteenth passes.

This is the fourteenth gate run on 1.3. The first (18:59 to 19:09), the second (Android only, 19:15 to 19:20), the third (19:41 to 19:49), the fourth (20:04 to 20:14), the fifth (20:45 to 20:58), the sixth (21:07 to 21:18), the seventh (21:28 to 21:47), the eighth (22:08 to 22:18), the ninth (22:39 to 22:49) and the tenth (22:54 to 23:02) on 12 November, and the eleventh (00:29 to 00:51), the twelfth (01:34 to 01:49) and the thirteenth (02:19 to 02:28) on 13 November, passed on the code as it then stood; their figures were replaced by these.

### On Android 6 and Android 14 emulators

The 1.2 record had to say that nothing had been run on Android. For 1.3 two emulator images were started with hardware acceleration: `CalcForAll_API23` (Android 6.0, API 23, 720×1280, text size still at the 130% left by the 1.1 accessibility checks) and `CalcForAll_API34` (Android 14, API 34, 720×1280, normal text size). Three things were done on them: a test suite run on the device itself, a scripted walk through the tool screens, and typing by hand with the on-screen keyboard.

**The device gate.** `DeviceGateTest`, new in `android/src/androidTest`, runs on the Android runtime itself. It computes every catalogued tool operation with its own default values (70 operations in 16 tools), pins eight known answers, runs the older tools (matrices, units, statistics, number bases, algebra), checks seventeen identities with the engine's own equality, and solves four polynomial equations. On Android 6 it passed on every state it was run on: the code of commit `32f141d` (a sixteen-minute first build that finished at 20:45), of commits `6bdc7a7` (20:58), `df3d3a4` with the extras (21:18), `cb76961` (21:46), `2d5cf33` (22:18), `95b6267` (22:49) and `4dd2e81` (23:02). On Android 14 it passed on the code of commits `cb76961` (21:46), `2d5cf33` (22:18), `95b6267` (22:49) and `4dd2e81` (23:02). On 13 November it passed on both emulators on each of the three states gated that night, the last being the final code (the row above). Since the seventh gate it carries a negative control, a false claim that must be reported false, so that its identity check cannot pass by never saying no. This is what the JVM tests cannot show: that no path reaches a Java member missing from Android 6. It is the check that would have caught `BigInteger.TWO` in 1.2.

**A walk through the screens.** The APK was installed and driven through `adb` by a script that opens the Tools list, opens a tool, picks an operation, presses its button and reads the answer back from the screen. On Android 6 it did this for ten of the Money operations (on the build of commit `32f141d`), for the first operation of each of the other fifteen catalogued tools (on the build of commit `6bdc7a7`), and for the four operations added last (on the build of commit `df3d3a4`): twenty-nine operations, no crash, no empty answer, no text placed off the screen. Every answer read back was the one the unit tests pin (10258.27 a month for the default loan, 315 days between the default dates, x = 1, 2, 3 for the default cubic, 15/128 for three heads in ten, an area of 25π, quartiles 2.5, 4.5 and 6.5, and so on). Nine screenshots were examined by eye: the Tools list; the Money, Dates, Equation solver, Geometry, Business and Data forms with their answer cards; and the amount-in-words and number-in-words answers. At 130% text size nothing was clipped or overlapping; a long answer line wraps onto a second line.

On Android 14 the same script pressed the first operation of each of the sixteen catalogued tools, on the build of commit `cb76961`: sixteen operations, no crash, no empty answer, no text off the screen, and the same answers. Two of its screenshots were examined by eye, the Geometry and Probability forms at normal text size; both were laid out correctly. Just after it booted, that emulator showed a "System UI isn't responding" dialog, which was dismissed with Wait before the app was opened; it is an emulator start-up message about the system's own interface, not about the app.

**What the device found that nothing else could.** Typing on the Android 14 emulator's own keyboard showed a defect that no unit test, review pass or scripted answer check had seen. `5 lakh` was typed into the expression field; the keyboard's autocorrect changed it, and `5 leaky` arrived on the entry line. None of the app's fifteen text fields told the keyboard that it holds mathematics, not prose, so autocorrect and auto-capitalisation were on in all of them — the same mechanism turns `sqrt` into `sort`. This was older than 1.3. Every text field now passes one shared set of keyboard options with both switched off (`MathKeyboard`), and a test reads the source and fails if a field is added without it. On the build of commit `2d5cf33`, which has the fix, the same typing arrived as `5 lakh`.

With that fixed, the keypad correction of the sixth and seventh passes was pressed on the same emulator, on that build: `5 lakh` on the entry line, a flick of the power key (the entry became `(5 lakh)^()`), then 2, then equals. The screen drew 5 lakh with a raised 2 and answered 250000000000. A tool field was typed into the same way: `sqrt(2)` as the radius of a circle in Geometry stayed `sqrt(2)`, and the answer was an area of 2π and a diameter of 2√2, both exact. The `5 lakh`, power key, 2, equals sequence was then repeated on the Android 6 emulator with its own keyboard, with the same result. After the eighth pass, on the build of commit `95b6267`, `(2+3) lakh` was typed on the Android 14 emulator and the power key pressed: the entry became `((2+3) lakh)^()`, and with 2 and equals the answer was 250000000000. Last, on the final build, one half was keyed with the fraction key (1, a⁄b, 2), ` lakh` was added through the expression sheet, and equals was pressed: the answer was `(1/2) lakh`, shown beside it as ≈ 0.5 lakh — half a lakh, where the earlier text form would have read 1/(2 lakh).

**After the ninth to twelfth passes.** The corrections described under those passes were typed into the app on the Android 14 emulator through the expression sheet and read back from the screen, on each build as it was gated and finally on the build in the table above. On that build `floor(tan(atan(25)))` showed ≈ 25, `floor(ln(125)/ln(5))` ≈ 3 (1.2 answered 2), `mod(8*(1+sqrt(2)),1+sqrt(2))` ≈ 0, `sqrt(10^100+1)-10^50` ≈ 5e-51 (it had been 0), `floor(pi*10^50)` its full fifty-one digits, `x^2-2*sqrt(e)*x+e=0` the single root ≈ 1.6487212707, `sin(20)^2+cos(20)^2=1` the refusal that the two sides agree to thirty digits and cannot be called equal or unequal, and `floor(tan(89.99999999999999999999999999))` the refusal that its whole part is not settled. On the build before it `round(sqrt(10^50+10^25))` showed 10²⁵ exactly, `sqrt(10^700+1)=10^350` False (it had been True) and `(x-e)^2=0` x = e. Three of these were repeated on the Android 6 emulator on the final build, at 130% text size, with the same results.

**A second thing only the device showed.** The fifty-one digits of `floor(pi*10^50)` wrap onto four lines of the answer area, and the fourth was cut in half by the row of keys below it. The area scrolls, so nothing was lost, but nothing on the screen said that the answer, and the decimal reading under it, went on. An answer that looks finished and is not is what this app exists to avoid. The answer area now shows **▾ more below** and **▴ more above**, in rows of their own, while there is more to scroll to, and returns to the top for each new answer. A first version drew the cue over a corner of the answer, where it hid part of a digit once scrolled; it was moved before the final build. On the final build the cue was seen on both emulators for the long answer and was absent for a short one. On the Android 6 emulator at 130% text size it also appeared under the refusal for `sin(20)^2+cos(20)^2=1`, whose three lines do not fit there. A test that reads the source keeps the cue from being removed by accident; it cannot say that the cue is drawn.

**After the thirteenth and fourteenth passes.** The device gate ran on both emulators on the final build (the last row of the table). Seven of the corrected answers were then typed into the app on the Android 6 emulator at 130% text size, through the expression editor: `floor((sqrt(101)+10)*(sqrt(101)-10))` showed ≈ 1, `frac(e^108)` ≈ 0.23, `floor(frac(123.75*cos(10)/cos(10))*60)` ≈ 45, `floor(10^38+exp(ln(0.74))-10^38+0.2599999999)` 0, `(x-10^18)^2=sin(20)` two roots (both shown as 1e18 at twelve digits), `x^2-log(9)*x+log(3)^2=0` the one root 0.47712125472, and `cosh(13)^2-sinh(13)^2=1` the refusal that the two sides differ by no more than the rounding of the estimates in them. That message is longer than the answer area at this text size; the area showed its first two lines and "more below", and one screenshot of it was examined by eye. No crash. On the Android 14 emulator the system interface stopped responding after its gate run, on a machine that was also running the comparison scans, so nothing was typed there on this build.

**The rest of the operations.** After the final gate the same script pressed the forty-one operations it had not reached, on the Android 6 emulator at 130% text size and on the final build. With the twenty-nine pressed on earlier builds, all seventy catalogued operations have now been pressed on a screen: no crash, no empty answer, no text placed off the screen. Forty of the forty-one showed the answer the unit tests pin, among them 36π for the sphere, a 3-4-5 triangle's area of 6, 45 degrees between two vectors with cos θ = √2/2, 24 divisors of 360, and a break-even of 500 units. The forty-first, the equation solver's numerical search, showed a refusal on the first press: its search had exceeded its five-second budget. The cause was the script itself, which dumps the screen continuously while it waits and loads the emulator during a timed computation. Pressed again with the device left alone, it showed x ≈ 0.999847741531 for its default `cos(x)=x`, and the form with its answer was examined by eye. On the same emulator the device gate computes all seventy operations, this one included, in under a second. It is still worth knowing that the search works against a wall clock: on a phone that is busy with something else it can run out of time and say so.

What this does not cover: the forty-one operations pressed last were pressed on Android 6 only, and by a script that reads the text on the screen; apart from the one form above, their layout was not examined by eye. The break-even refusals were not pressed on a screen. The autocorrect defect was seen, and its fix confirmed, with the Android 14 emulator's keyboard; the Android 6 emulator's keyboard was only tried after the fix. A third-party keyboard could behave differently, though the options passed are the standard ones. Nothing was run on a physical phone, and TalkBack, rotation and touch behaviour were not examined. The scroll cue was looked at in portrait on the two emulators only: not in landscape, not in the compact layout, and not with TalkBack.

### A third pass, on the 1.3 additions

While the first 1.3 gate ran, three more readers went through the four new tools, the quartile code and the second-pass corrections, and reported six defects. The sceptics assigned to refute each report all failed to start (a service error), so none of the six was independently verified at the time. Each was then checked here against the code it names. All six were real, and all six are fixed and pinned by tests:

| Defect | Trigger | Fix |
| --- | --- | --- |
| A percent sign typed into a percent field was applied twice and still printed a plausible answer: the calculator reads `20%` as 0.2, and the field then treated 0.2 as the percentage. Every percent field was affected, including those of the 1.2 Money tool | Business → Price for a margin, cost 80, margin `20%`: 80/(1 − 0.002) ≈ 80.16 where 100 is right | One trailing `%` is dropped from any field labelled as a percentage |
| Break-even could ask for a whole unit too many when an input was irrational, because the ceiling was taken of an approximate quotient. The wrong figure was reported by the reader and not reproduced here before the fix; the right answers are now tests | Fixed costs 30, price `10+sqrt(3)`, variable cost `sqrt(3)` (3 units) | An approximate quotient is steadied to ten digits short of working precision before the ceiling is taken |
| A multiple of e could not be read back. The engine wrote 2·e as `2e` in input text, and to the tokenizer a lowercase e straight after digits is always an exponent marker (D-016.6). A Geometry or Sequences field holding `2*e` was refused as an "incomplete exponent", and an inserted answer `55e` followed by a typed `+3` read as 55000. The same text is what the main screen copies to the clipboard, so this was older than 1.3 | Geometry → Circle, radius `2*e`; Sequences → Arithmetic, first term e, difference e, 10 terms, then `+3` | `Formatter.input` writes the product sign (`2*e`). The display form `2e` is unchanged |
| A geometric sequence with an irrational ratio was refused once ratio^n was fifty orders of magnitude from 1, because the engine will not subtract a 1 that would vanish. Odd counts failed where the neighbouring even count worked | First term 1, ratio `sqrt(2)`, 333 terms; ratio `sqrt(2)/2`, 331 terms | When the engine declines and an input is irrational, the sum is worked out in plain decimals with guard digits and shown as approximate. Exact inputs keep the exact path and its refusals |
| A repeated complex root of an equation with irrational coefficients was listed twice, as two conjugate pairs about 10⁻²⁶ apart whose real parts were rounding noise. The closeness check compared real part with real part, which fails exactly when both should be zero | `(x^2+π)^2 = 0` | Complex roots are compared as points in the plane. A cluster closer than the coefficients can separate is reported once, at its mean, with a vanishing real part reported as zero; a wider cluster is refused |
| A curve fit could divide by a spread that was only rounding. X values that differ beyond the working precision have one logarithm, the rounded mean differs from it in the last digit, and the slope was read off that noise | Logarithmic fit, X = 2.5, 2.5 + 10⁻⁸⁰, 2.5 + 2·10⁻⁸⁰ | The spread of the transformed X values must stand clear of the twenty guard digits, or the fit is refused as "too close together" |

The first two rows were fixed and gated earlier in the evening; the last four were confirmed and fixed after the owner asked for the work to continue.

One more correction came out of the same reading, though no reader reported it: **an inserted answer is now bracketed**, unless it is a bare non-negative number or a single function call such as `approx(…)`. "Use in calculator" replaces the entry with the answer's text, and without brackets a typed `^2` after `-600` squared only the 600, after `15/128` only the 128, and after `25π` only the π. The determinant, base-conversion and unit-conversion answers of the 1.1 tools follow the same rule (the unit converter's since the fifth pass, below).

### A fourth pass, on the third-pass corrections

Three readers then went through the corrections above, and this time a second reader checked each report against the code. Three defects were reported and all three were confirmed. Two of them are older than 1.3: they were found because a reader followed `Formatter.input` to every kind of value it writes.

| Defect | Trigger | Fix |
| --- | --- | --- |
| A power written on a unit took the number in front with it. `5 m^2` evaluated as 25 m², and the engine's own rendering of a derived unit read back as a different value: `3 s * 2 s` displays `6 s²`, which evaluated as 36 s². Only `m²`, `km²` and `m³` were safe, because the tokenizer treats those three as unit names | `5 m^2`; or copy the result of `3 s * 2 s` and paste it back | The parser binds a power that follows a unit name to the unit, so `5 m^2` is five square metres, as `2x^2` is 2·(x²). `(5 m)^2` still squares the whole quantity |
| A derived unit with two units below the line read back with a different dimension: `6 kg / 2 m / 1 s` displayed `3 kg/m·s`, which evaluated as 3 kg·s/m | `6 kg / 2 m / 1 s`, then re-enter the result | A denominator of more than one unit is written in brackets: `kg/(m·s)` |
| Break-even still added a unit when the price and the variable cost were large and nearly equal, because the noise grows with the digits their subtraction cancels | Fixed costs 30, price `10+sqrt(7)*10^12`, variable cost `sqrt(7)*10^12` gave 4 | The quotient is steadied to the digits the subtraction left. When fewer than the displayed digits are left, the count is refused |

The first row changes what an input means, so it is recorded as a ruling in `DECISIONS.md`. No existing test and none of the batteries' cases depended on the old reading: the whole gate passed without any other change.

### A fifth pass, on the grammar change

Because the fourth pass changed what an input means, two readers went through that change alone, again with a second reader checking each report. Four defects were reported and all four were confirmed. The first two were caused by the fourth-pass fix itself.

| Defect | Trigger | Fix |
| --- | --- | --- |
| The unit rule reached number words. `5 lakh^2` became 5 × (1 lakh)² = 5×10¹⁰ where it had been (5 lakh)² = 2.5×10¹¹, and `2 dozen^2` became 288 instead of 576 | `5 lakh^2`, `2 dozen^2` | The rule applies to dimensioned units only. `lakh`, `crore` and `dozen` are part of the number, so a power after them raises the whole number, as it did before |
| The unit converter's inserted answer was no longer one value: under the new rule a `^2` typed after `(10000) m` squared the unit alone | Units: 10 km to m, "Use in calculator", then `^2` | The inserted quantity is bracketed as a whole: `((10000) m)` |
| Units containing amperes or moles could not be read back. The elementary charge rendered as `… s·A`, and `A` is memory register A: with 2 stored there, the same text evaluated to twice the number, in seconds. `mol` was an unknown name. Older than 1.3 | Store 2 in A, evaluate `e_charge`, re-enter the result; likewise `R` and `NA` | The ampere is written `amp`, and `amp`, `mol` and `cd` are registered units. A test requires every SI base symbol to be a unit the parser reads, and none to be a register |
| Break-even refused counts that were certain, and could still round away a genuine excess. Cancelled digits were charged even when the margin was exact, and the ten guard digits were far wider than the noise | At 32 displayed digits: fixed costs `5000+sqrt(2)`, price 1000, variable cost 991 was refused. Fixed costs `30.0000000000000000001` over the margin in the fourth-pass row gave 3 | Digits count as lost only when the margin is itself approximate, and the guard is three digits |

### A sixth pass, on the fifth-pass corrections

Two readers went through the fifth-pass corrections, again with a second reader on each report. Three defects were reported and all three were confirmed.

| Defect | Trigger | Fix |
| --- | --- | --- |
| The keypad's power key had not been told about number words. It captured only the word, so `5 lakh` drawn with a raised 2 evaluated as 5 × (1 lakh)², a fifth of what the typed `5 lakh^2` now gives. The key's behaviour was older than 1.3; the disagreement with typed text was new | Enter `5 lakh`, press xʸ, then 2 | The power key takes a number word together with the number in front of it: `(5 lakh)^(2)`. The battery's independent reference model of the editor was changed in step, from the rule and not from the code |
| Break-even's three-digit guard let rounding noise from inside one field add a unit. An approximate value carries no error bound, so no fixed guard can tell amplified noise from a genuine tiny excess | Fixed costs `30+(ln(21)-ln(3)-ln(7))*10^5`, price 20, variable cost 10 gave 4 | An approximate count is given only when it is the same at the displayed precision and at the finest precision the subtraction left. When it hinges on the digits in between, it is refused |
| Break-even gave a count rounded to twelve significant digits where it used to refuse | Fixed costs 30000000000040, price `10+sqrt(7)*10^26`, variable cost `sqrt(7)*10^26` gave 3000000000000 | Refused when the finer precision is no finer than the displayed one, or when the count has too many digits for the displayed precision to settle |

Exact amounts are unaffected by all of this: an exact quotient is simply rounded up. The rule for approximate amounts now errs towards refusing. One test that expected a fourth unit from an excess in the twenty-first digit of an approximate quotient was changed to expect the refusal.

### A seventh pass, on the extras and the sixth-pass corrections

Two readers went through the three extras, which had not been reviewed, and the sixth-pass corrections, with a second reader on each report. Four defects were reported and all four were confirmed.

| Defect | Trigger | Fix |
| --- | --- | --- |
| The sixth-pass keypad rule took only the digits in front of a number word. A root, a function call or a factorial in front of the number stayed outside the base, so `√4 lakh` with the power key evaluated √((4 lakh)²) = 400000 where the typed `√4 lakh^2` is (√4 lakh)² = 4×10¹⁰ | Enter `√4 lakh`, press xʸ, then 2 | The key no longer has capture rules of its own for this. It asks the parser for the unit application that ends the text and takes that: whatever the parser binds to the word is what the key raises. The battery's reference model does the same, written separately |
| Break-even answered 3 where the count is 4 when a genuine excess lay below the finest digits available: a quotient that is a whole number to every digit may be exactly that number or a hair above it | Fixed costs 30.00000000000000000000000001, price `10+sqrt(7)*10^12`, variable cost `sqrt(7)*10^12` | An approximate quotient that is a whole number to every available digit is refused. Three earlier expectations changed with this: 30 over a margin of `10+sqrt(3)` less `sqrt(3)`, its `sqrt(7)*10^12` twin, and `5*(sqrt(2)+2)` over `sqrt(2)+2` used to answer 3, 3 and 5 and are now refused, because nothing in a decimal approximation says the margin is exactly 10 |
| In "Share in a ratio", a comma inside a part became a separator: `1,500:2,500` was split four ways and answered without complaint | Amount 4000, ratio `1,500:2,500` | When a colon is present only colons separate, and each part goes whole to the engine, which refuses a grouping comma |
| In the same field a space inside a part split it: `1 +1 : 2` was read as 1 : 1 : 2 | Amount 100, ratio `1 +1 : 2` | The same change: the part `1 +1` is evaluated as 2 |

The break-even ruling in the second row is the one an owner might want to revisit. It trades three correct answers on contrived inputs (irrational amounts that cancel exactly) for never answering one unit too low. Exact amounts, which is what a shop types, are unaffected.

The autocorrect defect described under "On Android 6 and Android 14 emulators" was found during this pass's device check, not by a reader.

### An eighth pass, on the seventh-pass corrections

Two readers went through the seventh-pass corrections, with a second reader on each report. Four defects were reported and all four were confirmed. The first two were in the keypad rule that the seventh pass had just rewritten.

| Defect | Trigger | Fix |
| --- | --- | --- |
| The parser-based rule for the power key matched the operand's start only against the start of a node. For an operand in its own brackets the parser's span begins one character inside the bracket, nothing matched, and the key fell back to raising the word alone | `(2+3) lakh`, then xʸ, then 2: answered 5 × (1 lakh)² | The operand begins in the node whose text contains that position, so the whole bracket group, power or fraction goes into the base |
| The same rule parsed the row as a single expression, so a row that is an equation or a comparison did not parse and also fell back to the word alone | `250000000000 = 5 lakh`, then xʸ, then 2: answered "false" | The row is parsed with the full statement grammar, and the trailing expression of its last statement is used. When the text is not a complete entry at all, a structural reading stands in — the word, the spaces before it and the operand before those — and never the bare word |
| "Share in a ratio" still split a part at a comma when the parts were separated by semicolons | Amount 4000, ratio `1,500; 2,500`: four parts | One kind of separator at a time: a colon if there is one, else a semicolon, else a comma |
| The ceiling of an exact irrational with about fifty integer digits was taken from fifty rounded digits and came out one too small. Break-even showed it, but the cause was in the engine's `floor` and `ceil` for surds and multiples of π or e, which are older than 1.3 | Break-even with fixed costs `pi*10^50`, price 1; or `ceil(pi*10^50)` on the main screen | The whole part of an exact irrational is now taken from as many digits as it needs: the expansion is extended until the value stands clear of the nearest whole number, and refused past the working-precision limit. Ordinary values use the same fifty digits as before |

The reader of the keypad rule also pointed at an older behaviour, which was then fixed in its own step. A fraction built with the fraction key and followed by a unit, a number word, `%` or `!` was drawn as the fraction times what followed, but read as the numerator over (denominator × what followed): ½ followed by `lakh` evaluated as 1/(2 lakh), a ten-millionth of what was drawn, with no error, and ½ followed by `%` as 1/(2%) = 50. A power followed by `!` or `%` had the same flaw (2³ followed by `!` read as 2^(3!)). The cause was that a fraction's and a power's text carried no outer brackets.

| Change | What had to move with it |
| --- | --- |
| A keyed fraction is written `((n)/(d))` and a keyed power `((b)^(e))` in the text that goes to the engine | Eleven expectations in four older engine tests and eleven in the 1.3 keypad test that pin that text; one B2 battery case; and the B3 battery's independent linearizer, which is the design's `flatten` and is now amended with the reason. The batteries then passed with every asserted case unchanged |

The value of a fraction or power on its own is unchanged by the extra brackets. What changes is only what the parser does with the thing typed after it.

### A ninth pass, on the eighth-pass corrections

Two readers went through the eighth-pass corrections and the outer brackets, with a second reader on each report. The reader of the entry text reported nothing. The other reported two defects in the engine's rounding functions, both confirmed and both older than 1.3: the eighth pass had corrected `floor` and `ceil` and left their neighbours as they were.

| Defect | Trigger | Fix |
| --- | --- | --- |
| `round` of a surd or a multiple of π or e added one half in fifty-digit arithmetic and came out one too large | `round(sqrt(10^50+10^25))` gave 10²⁵ + 1; `round(pi*10^49)` ended …752 for a value of …751.058 | (⌊2·\|x\|⌋ + 1) div 2, with the floor settled from as many digits as it needs |
| `frac` and `mod` of such a value subtracted its exact whole part from a fifty-digit rounding of it | `frac(pi*10^50)` and `mod(pi*10^50, 1)` gave 0 | What is left over is taken from an expansion wide enough to keep fifty good digits. A result that would be written as 1, or as the divisor itself, is refused |

Before anything was changed, the present behaviour of the whole family was printed for some sixty inputs. That showed the same flaw in places no reader had named, and they were corrected with it:

| Defect | Trigger | Fix |
| --- | --- | --- |
| `mod` formed a − b·⌊a/b⌋ in fifty-digit arithmetic, which cancels as many digits as the quotient has | `mod(10^60, pi)` gave 10000000000, far larger than the divisor | The floor of the quotient is settled, and the remainder taken from widened expansions. A quotient that is an exact fraction leaves an exact remainder: `mod(3pi, 2pi)` is π |
| A difference of two exact values that agree to fifty digits was 0 | `sqrt(10^100+1) - 10^50`; π less its own fifty-digit decimal | When more than ten leading digits cancel, both values are expanded until the difference itself has fifty good digits |
| Two values of different kinds that agreed to 320 digits compared as equal | `sqrt(10^700+1) = 10^350` was True, and `min` of the two returned the larger | Fractions, finite decimals and surds are compared exactly, through their squares. A multiple of π or e is widened to 1,144 digits and then refused |

The reference values in the tests for these come from nowhere in the engine: π by Machin's formula and e by its own series in plain `BigDecimal`, and square roots from the JDK. Every fifty-digit answer is compared with them digit for digit.

### A tenth pass, on the ninth-pass corrections

Three readers went through those corrections and swept the engine for the same flaw elsewhere, with a second reader on each report. Six defects were reported and all six were confirmed. One reader also ran some thirty thousand randomized `round`, `frac` and `mod` cases over exact values against a 2,600-digit reference, outside the repository, and found no wrong number.

| Defect | Trigger | First remedy |
| --- | --- | --- |
| A value with more whole digits than any expansion can reach was expanded six times before it was refused. Under that, one fifty-digit square root of a radicand that long already took over half a minute (older than 1.3) | `round(sqrt(10^20001+2))` ran for six minutes before refusing | The refusal comes after the first expansion. A square root beyond 10^±300 is scaled by an even power of ten before the library is asked, and is found at once |
| The ninth-pass `mod` read a decimal estimate as if it were exact, so a multiple of an estimate left the divisor. New: 1.2 was right for these inputs and wrong for others | `mod(8*(1+sqrt(2)), 1+sqrt(2))` gave 2.41421356237 | See the eleventh and twelfth passes |
| The same against an irrational divisor | `mod(11*pi+1-1, pi)` gave π | The same |
| A True/False statement between estimates that agree to every digit computed was called False (older) | `sin(20)^2+cos(20)^2 = 1` | Agreement without equality is refused as undecidable; the allowance was settled in the twelfth pass |
| A quadratic with a repeated irrational root got a complex pair, from the sign of a discriminant that was rounding (older) | `(x-e)^2 = 0` gave e ± 5×10⁻²⁵ i | A discriminant of estimates that is zero to within their rounding is treated as zero |
| A sum kept one digit of an exact operand, and taking the other operand away again showed that digit as the answer (older) | `10^49+sqrt(2)-10^49` gave 1 | Refused at the sum; withdrawn in the eleventh pass |

For estimates, the remedies of this pass were refusals: a whole part or a remainder was to be given only when it was beyond doubt. The whole gate passed on that state (the eleventh gate run), and the eleventh pass then showed that two of the remedies were wrong in kind.

### An eleventh pass, on the tenth-pass corrections

Three readers, with a second reader on each report: nine reports, eight confirmed and one refuted.

| Defect | Trigger | Fix |
| --- | --- | --- |
| Refusing every estimate that lies next to a whole number turned away ordinary questions (new). Read digit for digit, as 1.2 read them, they are right or wrong by the luck of a last digit: 1.2 answered `floor(ln(125)/ln(5))` = 2 | `floor(ln(8)/ln(2))`, `floor(atan2(1,1))`, `ceil(e^(ln(10)))` | An estimate that reads as a whole number is taken as that whole number, and the answer is marked ≈ |
| An estimate was exactly whole at forty digits only because a function value had rounded onto it (older) | `floor(tanh(50))` gave 1; `floor(log(10^38-1))` gave 38 | The argument of a rounding function is recomputed at the engine's full fifty digits |
| The quadratic still split a repeated root when a coefficient came from a function, whose rounding is at the fortieth digit and not the fiftieth (older) | `x^2-2*sqrt(e)*x+e = 0` | The allowance follows the session's precision |
| Complex statements: an estimate against a real value was called False without any comparison (older), and a refusal on the real parts hid a plain difference in the imaginary ones (new) | `(cos(20)+i*sin(20))^9 = -1`; `e^(ln(5))+2i = 5+3i` | Compared as points in the plane, part by part against the size of the whole. One part that plainly differs is False |
| `A − B = 0` with units was still called False (older) | `sin(20)^2 m + cos(20)^2 m - 1 m = 0 m` | The sum is split whether or not its parts carry units |
| Graph roots failed for a quartic with an irrational coefficient (new) | `x^4 - pi*x` on −3 to 7 | The tenth-pass refusal of a sum that keeps few digits of an operand is withdrawn: it reached the engine's own arithmetic |
| The limit tool failed in the same way (new) | `(pi*x^4 + 1)/(x^4 + 1)` at infinity | The same |
| A quadratic with whole-number coefficients was refused (new) | `x^2 + x - 10^78 = 0` | The same |

The refuted report was that estimates above 5×10²⁹ were refused. That was the rule as recorded, and the line has since moved to 5×10⁴⁷, because the argument is now computed at fifty digits.

Withdrawing the sum refusal brings back the last row of the tenth-pass table: `10^49+sqrt(2)-10^49` answers 1 again. It is recorded as a known gap and asserted in a test, so that it cannot be forgotten.

### A twelfth pass, on the eleventh-pass corrections

Two readers, with a second reader on each report: five reports, all confirmed. The eleventh pass had computed True/False statements and the coefficients of equations at fifty digits as well, and that was a mistake.

| Defect | Trigger | Fix |
| --- | --- | --- |
| A value in `Ans` or a register was computed at the session's forty digits. Set against the same function recomputed at fifty, it differed at the forty-first (new) | `sin(20)`, then `Ans = sin(20)`, answered False. `ln(2) → A`, then `A*x + 1 = ln(2)*x + 3`, was solved to about 10⁴¹ where there is no solution | Statements and equations are computed at the session's own precision again |
| A singular system was no longer recognised: at fifty digits a doubled coefficient is rounded, and the determinant was the last digit of two products (new) | `x*sin(25) + y*cos(25) = 10` with twice that equation gave x = 0, y = 10⁴⁹ | The same. In addition, a determinant of estimates that is zero to within their rounding now means no unique solution; 1.2 recognised that only when the multiple happened to be exact |
| True identities that lose a few digits to cancellation were still called False (older) | `cosh(6)^2 - sinh(6)^2 = 1`; `(sqrt(2)-1)^4 = 17-12*sqrt(2)` | Two sides that agree to thirty digits without being equal are undecidable. The allowance is wide on purpose: an identity must not be called False, and a statement that is refused when it could have been called False has lost nothing |
| A fixed allowance of two digits in fifty is smaller than the rounding of an ill-conditioned step (new for these inputs) | `floor(tan(atan(25)))` gave 24; `floor(acos(cos(1)))` gave 0 | The allowance is measured. The argument is computed at fifty, forty-five and forty digits, and how far it moved says how far it can be off: rounding shrinks as the precision rises, and a true distance from a whole number does not move |
| The same for a half | `round(tan(atan(16.5)))` gave 16 | The same |

The reader of the last two rows measured them on 130 `tan(atan(N))` cases and 89 half cases. 1.2 was wrong on 61 and 43 of them, and the eleventh-pass code on 32 and 35. With the measured allowance all 219 are right, and they are now a test.

One more rule came out of testing that fix. A value is read as the whole number beside it only while its allowance is a billionth of a unit or less. With a wider allowance a value inside it could as well be a true fraction, so it is refused as unsettled, as is one whose allowance reaches half a unit. `floor(tan(89.99999999999999999999999999))` has twenty-eight whole digits, of which fifty-digit arithmetic gets about twenty; it used to print all twenty-eight.

**When this was first written, these last corrections had been through the gate and the checks on the emulators, but not through a review pass of their own.** The thirteenth pass, below, was that review, and it confirmed six defects in them. Every pass so far has found something, most of it in the previous pass's corrections, so that should be expected here too. The places to look first are the measured allowance for the rounding functions (`NumMath.doubt` and `nearLine`, and the three recomputations in `Evaluator.recomputed`), the thirty-digit allowance for statements, and the determinant test for systems. What the passes have settled is narrower: the exact-value corrections of the ninth pass were checked against an independent reference by two readers and by the tests, and no wrong number has been found in them.

In place of that review, one check that does not depend on a reader. A temporary probe evaluated 711 ordinary expressions on the build of commit `4dd2e81` (the state before these four passes) and on the final code, and the two sets of answers were compared line by line: the five rounding functions over ninety arguments each, eighteen compound uses of them, forty remainders, fifty-three sums, differences and comparisons, sixty-five True/False statements, fifty-two equations and systems, twenty steps of a session that stores values in `Ans` and registers, and thirteen cases in radians. Fifty-eight answers differ. Thirty-eight are whole parts, fractional parts and remainders that the old build had wrong or left as rounding (`floor(ln(125)/ln(5))` 2, now 3; `ceil(atan2(1,1))` 46, now 45; `frac(cbrt(2)^3)` 1, now 0), or that are the same number now marked as an estimate. Fourteen are true identities that the old build called False and the new one declines to decide. Three are equations with a repeated root that the old build split in two or gave as a complex pair. One is a singular system that the old build answered with x = 1.44, y = 0. The other two are the same value, one written with fewer trailing zeros and one now marked as an estimate. No answer that was right before is different now, and every statement still answered False is false. The probe was not kept; the cases that matter are in `EstimatesSpec`.

What remains wrong is known and recorded in `DECISIONS.md`: an estimate that is nothing but rounding next to zero; `10^49+sqrt(2)-10^49`; a value truly closer to a whole number than fifty digits show; functions evaluated within about 10⁻⁴⁰ of a special point; and an estimate kept in `Ans` or a register, which is rounded with the digits it was stored with. All five need an estimate to carry its own error bound.

### A thirteenth pass, on the twelfth-pass corrections

Two readers, with a second reader on each report: six reports, all confirmed. Two were answers that had been right before the ninth pass.

| Defect | Trigger | Fix |
| --- | --- | --- |
| A value built only from surds, π and e does not depend on the session's precision, so the three computations agreed, no doubt was measured, and it was read digit for digit (older) | `floor((sqrt(101)+10)*(sqrt(101)-10))` gave 0 for 1; `floor((1+sqrt(2))^65)` was one too low; `frac(e^108)` gave 0.398 for 0.230 | A measuring pass lowers the tower's own precision as well. A power of π or e above the 64th is taken from a wider expansion |
| A refusal raised while the argument was recomputed at fifty digits was dropped, and the session's forty-digit value used with no doubt (new for one input) | `floor(mod(floor(ln(125)/ln(5))*10^9,7)+ln(125)/ln(5))` gave 6 for 7 | The refusal is the answer. Each rounding function is decided once and kept, so an inner one is no longer left unrecomputed two levels down |
| Nested rounding functions cost the cube of the nesting (new) | 300 nested `frac` took 36 seconds; a graph of four nested `frac` no longer fitted its time budget | The same: decided once. 300 deep takes milliseconds |
| A discriminant or determinant that is small only beside exact terms was taken for rounding (new) | `(x-10^18)^2 = sin(20)` gave the single root 10^18, shown as exact; a solvable system of the same shape was refused | Rounding is measured, by working the coefficients out again in other sessions |
| With complex numbers on, `A − B = 0` was not split, so a true identity was False while the same one written `A = B` was refused (older) | `(cos(72)+i*sin(72))^5-1 = 0` | Before False, both sides are worked out again and their difference compared with how far it can be off |
| A true identity that loses more than ten digits to cancellation was False (older) | `cosh(13)^2-sinh(13)^2 = 1` | The same measure, and the thirty-digit allowance applied against the largest estimated term |

One more turned up while those corrections were being probed, not from a reader: at forty digits `tanh(50) − 1` is a decimal zero and a function of it came back exact, so `floor(e^(tanh(50)-1))` answered 1 with nothing recomputed. Whether an argument is exact is now asked at fifty digits.

### A fourteenth pass, on the thirteenth-pass corrections

Three readers (rounding and the tower; statements; equations), with a second reader on the two most severe reports of each: six reports, all confirmed, and nineteen further observations that were not second-read. The readers were given the build before the thirteenth pass and the corrected one as two engine files with a small runner, and compared them on about 35,000 inputs of their own. Three of the six were regressions the thirteenth-pass corrections had introduced.

| Defect | Trigger | Fix |
| --- | --- | --- |
| A value 10⁻¹⁰ short of a whole number was read as it, because the forty-digit tower pass has no room for digits the other two hold (new) | `floor(10^38 + exp(ln(0.74)) - 10^38 + 0.2599999999)` gave 1 | A value that both forty-five-digit computations reproduce exactly keeps its own digits: 0. A measured doubt wider than 10⁻²⁰ reads nothing as whole |
| The result of an inner `frac` or `mod` was kept as a finished value, so its argument's rounding never reached the outer function (older; 1.2 gave the same) | `floor(frac(123.75*cos(10)/cos(10))*60)` gave 44 for 45 | A measuring pass is given its own argument less the whole number already settled |
| The new statement measure called a true identity False when the coarser computations came out exactly equal (the measure fooled; the verdict is older) | `2*(ln(40)-ln(5)-ln(8)) = 0` | Sides that are equal with fewer digits are not called False, and a finer session is measured against |
| The measure missed rounding seen through a root or an inverse function, and a session in which the statement could not be worked out counted as no doubt (older) | `asin(sin(20)^2+cos(20)^2) = 90`; `sqrt(sin(50)^2+cos(50)^2-1) = 0` | The distance to a finer value is the error itself, whatever the rate; a session that fails counts when there is no finer one |
| The measured doubt of a discriminant or determinant was too small when a coarser computation landed exactly on zero (new) | `x+log(3)*y=1`, `2*x+log(9)*y=5` was solved to 10^40; `x^2-log(9)*x+log(3)^2=0` got two roots | The same reading of the two steps as for rounding, and the finer session |
| An estimate kept in a register does not move in any session, so its rounding was not measured (new) | `sqrt(e) → A`, then `x^2-2*A*x+e=0` got two roots | An equation that names a stored estimate has no measure and is judged by the session's digits, as before |

From the nineteen observations, each reproduced before it was acted on: with the tower lowered in both coarser passes, a true identity could land exactly on its whole number there and be read one too low at fifty digits (`floor(cosh(9)^2-sinh(9)^2)` gave 0), so the passes with only the functions lowered are made as well; a large estimate that is whole as held was refused because forty digits cannot hold it (`floor(approx(2)^140)`); exponent laws across the 32nd power of e had gone from True to refused, and the line was moved to the 64th; a remainder whose divisor vanishes more coarsely, and one whose quotient is exactly whole as held but unsettled, were answered; a value whose imaginary part is exactly zero in one session and rounding in another was compared as two kinds of value; sessions with fewer than ten digits had no measure at all. Three observations are recorded as limits and not changed: digits swallowed the same way at every precision, more than sixty-four terms in a statement, and plainly false statements that are refused at low precision.

Checked afterwards, on the final code. The readers' own input files were run again and compared with the outputs they had saved from the build before the thirteenth pass:

- The 711 ordinary expressions: no answer differs.
- Statements, 6,553 lines: 1,376 go from False to a refusal (the true identities among them include all 198 that the readers found called False by the measure) and two from True to a refusal (`e^128 = (e^64)^2` and `e^100 = e^64*e^32*e^4`, exponent laws across the 64th power). Nothing goes from False to True or from True to False, and nothing throws.
- Equations and systems, about 12,100 lines. In the second reader's scan of true repeated roots and singular systems over k (1,197 lines at forty and sixteen digits) every answer is the earlier build's: none is split or solved. Distinct roots that the earlier build merged are separate. At fifty-digit sessions eighteen lines go the other way: a pair of roots 10⁻²² apart is one root, where the earlier build kept or refused it.
- Rounding functions, about 16,500 lines: fifty answers become refusals (sums that swallow an operand, whole parts near the limit of fifty digits, chaotic nesting) and twenty-three exponent-law statements across the 64th power go from True to a refusal. One answer goes from right to wrong: a 38-digit decimal added to 10^8 and taken away again, constructed to fall 10⁻⁴⁰ short of 1, has its floor read as 1. Two refusals become answers. Nested `frac` and `mod` cases that were one too low are right (164 in one file); other changed values are last digits of large powers, now from the wider expansion.

**The corrections of the fourteenth pass have been through the gate above, the scans just described and the seven answers typed on the emulator, but not through a review pass of their own.** Every pass has found something in the one before it, the thirteenth's corrections included, so that should be expected here too. The places to look first are the reading of the two steps in `NumMath.movement`, the rule that a value reproduced at forty-five digits keeps its own digits (`readsAs`), `frac` and `mod` inside a measuring pass (`Evaluator.inThisPass`), and the three sessions a statement and a discriminant are measured in (`ExpressionRouter.differsOnlyByRounding`, `measuredDoubt`).

What remains wrong after these two passes is recorded in `DECISIONS.md` under "What these two passes do not reach".

What is new since the 1.2 gate, and what tests it: the corrections from the second review pass (each trigger in the table under "A second pass" is now a test); quartiles by the median-of-halves method for even and odd counts, two values, and tied modes; and the four tool adapters — circle, sphere and cone answers in exact multiples of π, a 3-4-5 triangle and a 5-12-13 triangle exactly, a diagonal of √2, arithmetic and geometric sums including the sum to infinity, margin against markup, break-even rounded up to whole units, a growth rate, and refusals for an impossible triangle, a non-positive length, a price at or below variable cost and a margin of 100%. The catalogue walk now computes the default values of sixteen tools.

The geometry, sequence and business tools hold no arithmetic of their own: each formula is a calculator expression evaluated by the engine, so their correctness rests on the engine's existing tests plus the adapter tests above. Their first operations were pressed on both emulators, as described under "On Android 6 and Android 14 emulators".

The extras added last are tested the same way. Numbers in words are pinned for twenty-two Indian and eight international cases, and a property test reads four hundred random numbers of up to twenty-one digits back from their words with a reader written separately from the code under test. A ratio's shares are checked to be exact and to add back to the amount, and inflation's two figures to mirror each other.

The engine corrections of the ninth to twelfth passes are tested in three files. `RoundingFamilySpec` (thirteen tests) covers `round`, `frac` and `mod` of exact values: six hundred multiples of surds, π and e rounded against the reference, fractional parts and remainders to fifty digits for values up to 10^1000, exact remainders, and the refusals at the edges. `NearlyEqualValuesSpec` (eight) covers differences and comparisons of values that agree to more digits than fifty, including a multiple of π against a 1,200-digit fraction, which is refused and not called equal. `EstimatesSpec` (nineteen) covers estimates: every trigger in the tenth to fourteenth pass tables, the 219 round-trip cases, sessions that compute with five, eight, twelve, sixteen, forty-five and fifty digits, rounding functions nested 300 deep finishing quickly, 180 repeated-root quadratics and 180 singular systems over three precisions, and the known gaps asserted as such.

## Historical Android 1.2 — gate at commit `9d8374e`

This section records the 1.2.0-preview gate as it was run. Its figures describe that commit, not the current one.

On **12 November 2026** (India time) the 1.2 gate ran on the development machine with JDK 21 and Gradle 8.10.2. Each Gradle task was a separate invocation forced with `--rerun`, and every count below was read from result XML written after that invocation started. The owner chose **automated tests, lint and APK assembly** as this increment's verification. **No emulator or device was used**, so none of the twelve new tool forms has been seen running, and no 1.2 claim is made about layout, touch, rotation, TalkBack or performance.

| Check | Result |
| --- | --- |
| `:core:test` | **893 tests in 65 suites**, 0 failures, 0 errors, 0 skipped. XML written 18:33:14 for a run started 18:30:51. The 1.1 baseline was 800 tests in 59 suites. |
| `:core:checkNoAndroidImports` | Scanned **136 files**, no Android imports (120 at the baseline). |
| `:api:test` | **43 tests in 3 suites**, 0 failures, 0 errors, 0 skipped. XML 18:36:05, run started 18:35:17. The same count as the baseline: the wider solver changed no API test. |
| `:battery:test` | BUILD SUCCESSFUL; **9 JUnit tests in 4 suites**, 0 failures. XML 18:37:28, run started 18:36:26. Case records unchanged from the baseline: B1 922 asserted passes and 78 probes, B2 479 and 21, B3 509 and 11, no failures. One probe now observes a computed result where it used to observe a refusal (the cubic `x^3-4x^2+x+6=0`); a probe is an observation, not an assertion. |
| `:android:testDebugUnitTest` | **124 tests in 15 suites**, 0 failures, 0 errors, 0 skipped. XML 18:34:51, run started 18:33:38. The 1.1 gate had 99 tests in 14 suites. |
| `:android:lintDebug` | BUILD SUCCESSFUL; **0 errors, 21 warnings, 4 informational** findings — the same counts as the 1.1 gate (Gradle and plugin version notices, version-catalog suggestions, state autoboxing, a monochrome launcher icon), none of them in the new code. Report written 18:39:34. An earlier lint run in this session failed on one error; see "Defects found and fixed" below. |
| `:android:assembleDebug` | BUILD SUCCESSFUL. `android/build/outputs/apk/debug/android-debug.apk`, version name `1.2.0-preview`, version code 3, **56,237,500 bytes**, SHA-256 `556606c54bef8609f548ee80d66dbea0b2506364c8b95e936015b5e68438988d`, written 18:41:29. |

All seven invocations ran on the same source state: the last engine change preceded the core run, and the last Android change preceded the Android run.

What the new tests establish:

- **Polynomial equations (degree 3–8).** Known cubics, quartics and a quintic against their published roots; exact rational roots, exact surds from a leftover quadratic factor and the exact complex pair of `x^3 = 8`; an all-complex quartic; repeated roots listed once; irrational coefficients marked approximate; degree 9 refused. Two property tests then generate random cases: 120 products of distinct integer factors must give back exactly those roots, and for 150 random integer polynomials every returned root must satisfy the equation, real roots must be ascending, and the list must be complete — checked with Vieta's sum of the roots, or, when a root repeats, by that root also being a root of the derivative.
- **Money.** Published EMI and SIP figures to the paisa; the EMI formula checked independently by simulating 240 monthly payments down to a zero balance; the affordability and savings-goal formulas checked as exact inverses of EMI and SIP; tax add/remove as exact inverses; refusals for impossible inputs.
- **Dates and time.** Day counts across leap years, weekday of known dates, a 29 February birthday, day-first and ISO parsing with two-digit years refused; duration parsing, overnight clock gaps and out-of-range times.
- **Number theory, vectors, probability, curve fitting.** Factorisations including 10^14 and a large prime; exact binomial fractions and a cumulative total of exactly 1; Poisson and normal values against tables, including Φ(−8) to ten significant digits; fits that recover the curve their data came from.
- **Taylor polynomials, limits, bitwise logic.** Exact series for sin, cos and ln; classic removable limits, a jump with two one-sided values, poles with signs, an oscillation that is reported as not settling, and limits at infinity.
- **The tool adapters.** One test walks the whole catalogue and computes every operation with its own default values, so a tool that is listed but not wired cannot ship. Others pin grouping and Devanagari numerals in money output, parser-ready insertion text, and the rule that a numerically found root is shown with `≈` unless its residual is exactly zero. That last rule was added because the first run of these tests printed a bisection midpoint as an exact fraction.

### Defects found and fixed before this record

Two checks beyond the unit tests found real defects in the 1.2 code. Each is fixed, and each has a regression test named for it.

**Lint** stopped the build on `BigInteger.TWO`, a constant that exists only from Android 13 (API 33); the app's floor is Android 6 (API 23), so that line would have crashed on older phones. The same constant was in the engine's `NumberTheory`, which lint does not scan and JVM tests cannot detect. Both now use `BigInteger.valueOf(2)`, and `AndroidApiFloorSpec` scans the engine sources for known too-new Java members. It is a denylist, not a proof of compatibility.

**An independent review** — three reviewers reading the 1.2 diff for wrong answers, crash paths and unbounded work, with a separate sceptic assigned to refute each reported defect by tracing its trigger through the code — reported fifteen findings. The sceptics examined nine and confirmed all nine; the other six were lower-ranked and either duplicates of those or confirmed by inspection here. None was refuted. They were:

| Defect | Trigger | Fix |
| --- | --- | --- |
| A repeated root of an equation with irrational coefficients was listed twice, or as a complex pair that does not exist | `(x-sqrt(2))^2*(x+1)=0`, `(x-sqrt(3))^3=0` | With approximated coefficients, a pair closer than the coefficients can separate is reported once; a wider cluster that could be one repeated root in pieces is refused (the first version of this fix was itself corrected — see the second pass) |
| Very small roots lost their digits (absolute bisection tolerance) | `x^3+x=1/10^25`, `x^3-3x/10^80+1/10^120=0` | Tolerance relative to the root; complex-root thresholds likewise |
| The steps said "No rational roots" when the rational-root test had been skipped; an exact whole-number root was shown as approximate | `x^3=10^15` | The claim is made only when the test ran; a refined root is checked for being a whole number |
| The polynomial solver had no work budget, and history re-solves an entry on every recompute | Degree 8 with 148-digit fractions | Size cap on the cleared-denominator polynomial and a 3-second budget, both typed refusals |
| Poisson and normal probabilities inside their documented ranges were refused with an unrelated "50 digits" message | Poisson average 4, k = 100; normal z = −15 | Sums accumulated in `BigDecimal`; upper tails computed directly |
| Exponential, power and logarithmic fits were refused when a data point sat exactly at the mean | X = 1, 2, 3 with Y = 2, 4, 8 | The transformed fit is computed in `BigDecimal` |
| The exact binomial sum could run for minutes | n = 2000, p = 0.123456789 | Whole-number arithmetic over one common denominator; a decimal answer when that denominator would be enormous |
| Sums and products had a term cap but no work bound | Σ 1/x⁵ for x = 1…5000 | An 8-second budget ending in an explanation |
| The limit tool said "does not settle" / "does not appear to exist" when the engine had only refused to evaluate | `exp(-x)` as x → ∞, `sin(1/x)` at 0 | Separate "could not be evaluated" and "no value on this side" results; "does not exist" only when both sides were determined |
| The tool form could show an answer under inputs it did not belong to | Start a long calculation, switch operation away and back | A generation counter bumped by every edit and operation change |
| Rounded percentages, unit prices and fuel quantities carried no ≈ mark | Percent change from 3 to 4 | Marked ≈ unless the digits shown are the value |
| "Birthday today" on 28 February for a 29 February birth date while the age was still one year short | Born 2000-02-29, as of 2023-02-28 | The birthday is observed on 1 March in common years, the day the age increases |

The review read code; it did not run the app. The 8-second series budget has no test of its own, because exercising it would add eight seconds to every run.

**A second pass then checked the fixes themselves**, with three readers looking only for regressions the fixes had introduced and a sceptic per claim. It was worth running: the fixes had flaws of their own. Its corrections were made after the 1.2 commit above, so they are part of 1.3.0-preview and are covered by the 1.3 gate at the top of this record, not by the 1.2 figures in this section.

| Flaw in a fix | Trigger | Resolution |
| --- | --- | --- |
| The binomial's decimal path rounded p before forming q = 1 − p, so a p just below 1 gave q = 0 and reported real probabilities as 0 | n = 2000, k = 1999, p = 1 − 10⁻⁷⁰ | q is rounded from the exact 1 − p; the exact path's budget was also raised to 120,000 bits |
| The limit classifier called lost precision "no value on this side" | `x^6/(1-cos(x^3))` at 0, whose limit is 2 | "No value" is said only when every sample on that side has none; values further out but none close in is "could not be evaluated near enough to tell" |
| A transformed fit divided zero by zero when two distinct X values had the same logarithm at working precision | X = 1 and 1 + 10⁻⁷⁰ | A typed refusal that says the X values are too close together |
| "Birthday today" beside "23 years 0 months 1 days" for a 29 February birth on 1 March (judged not a defect by the sceptic, fixed anyway) | Born 2000-02-29, as of 2023-03-01 | The age is counted from the observed birthday, so it reads 23 years 0 months 0 days |
| The first repeated-root fix merged by display precision, which flattened a genuine complex pair into a real root the equation does not have | `(x-π)*((x-1)^2+10^-26)=0`, roots π and 1 ± 10⁻¹³·i | The threshold now comes from the coefficients: only a pair within about 10⁻²⁴ is merged, a wider unresolvable cluster is refused, everything else is kept |
| The merge compared roots through the number tower, which refuses to subtract values more than 50 digits apart | `(x^2-π)*(x-10^60)=0` | Comparisons are done in `BigDecimal` |
| Clearing denominators left a common factor in a leftover quadratic, so an exact root came out as an unsimplified surd | `x^3+x^2/10007-2x-2/10007=0` gave ±(1/10007)·√200280098 for ±√2 | The quadratic factor is made monic before it is solved |

One confirmed low-severity item was left as it is and is recorded as a limit: a binomial probability smaller than about 10⁻³²⁷⁶⁸ cannot be represented in the engine's decimal range, and on the decimal path the calculation is refused rather than rounded to zero.

Limits of this evidence:

- Unit tests exercise the engine and the pure-Kotlin adapters. The Compose form that renders the catalogue compiles and passes lint but has not been run.
- The APK is the Gradle debug build, signed with the development key. Its signature schemes, native-library alignment, legal-asset inventory and install/launch behaviour were **not** re-audited for 1.2; the 1.1 receipts below describe the 1.1 APK only.
- The packaged local API ZIP is still the 1.0.0-preview build. API tests pass against the current source, which now solves polynomial equations to degree 8 through the shared router, but the ZIP was not rebuilt and the new tool modules are not API operations.
- Numerical methods remain numerical: a search on a range can miss roots, a Taylor polynomial is an approximation near one point, and a limit estimate is not a proof.

## Historical Android 1.1 — earlier evidence

At **11 November 2026, 04:32 India time**, the Android `1.1.0-preview` (version code 2) gate in `release-android-gates-r7.log` completed **BUILD SUCCESSFUL** in 7 minutes 52 seconds (54 tasks: 22 executed, 32 up-to-date). Its **99 unit tests in 14 suites** passed with zero failures, errors or skips; these counts come from XML beginning at 04:25:25 India time. APK assembly and lint completed, with **zero lint errors, 21 warnings and four informational findings**, the same categories recorded for the baseline below. New-APK signature verification and the API 23 installation/launch checks described below passed. The unchanged core, battery and local API retain their earlier verified receipts; the API remains **1.0.0-preview**.

The update adds **Spaced line → Read line** handwriting drafts with per-glyph alternatives and explicit editable review, plus photo OCR line selection and restoration of full recognized text. Seventeen new tests cover six photo-line cases, three ink-input bounds and eight line-grouping/recognition cases. These are bounded local review aids, not a trained general handwriting recognizer, mathematical-layout OCR, or completion of the historical v2–v4 roadmap.

| Current artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Android `1.1.0-preview` APK, `android/build/outputs/apk/debug/android-debug.apk` | 55,731,637 | `f3d5b1711839d7212fa4fa1768a8827ffcd78960bdcfecd421dcce91be23ce85` |
| Unchanged API `1.0.0-preview` ZIP | 10,464,736 | `dbd4fb0f9349761c47f36091882f48c6ca86626a6b6c620da59df3853c3d11ac` |

The `r7` static audit confirms the new version name/code, minSdk 23 and targetSdk 34, false backup flags and the data-extraction resource reference. All **36 APK legal assets**, **38 API notice/document files** and **21 external API JAR hashes** match their sources/inventory. All four native library hashes are unchanged from `r6`; fresh ZIP/ELF/RELRO checks pass for the 64-bit libraries, every native ZIP entry is stored and 16 KB aligned, and the 32-bit ELF files retain their previously documented 4 KB alignment. The actual legacy `zipalign` check passed. Receipts are `packaged-notice-audit-r7.json`, `apk-r7-manifest.txt` and the refreshed `apk-native-alignment.json`; earlier native receipts are preserved separately. No 16 KB-page runtime claim is made.

The new APK is smaller than the preserved 1.0 APK. A read-only archive comparison found exactly the same 592 entry names, all seven DEX files and byte-identical assets/native libraries; the app DEX grew by 62,228 uncompressed bytes. The size reduction is in archive/alignment/signing overhead, not omitted payload. The comparison is retained in `apk-r7-size-delta.json`.

The final `apk-signature-r7.txt` receipt reports **Verifies** with v1/v2 signatures present and other schemes absent. It retains the standard unsigned `META-INF` metadata warnings; this is a development-signed preview, and verification is not described as warning-free. The signing tool exited successfully.

The new 1.1 APK installed and launched successfully on API 23 with Wi-Fi and mobile data disabled. One launch measured **882 ms on the emulator**, not a physical-device benchmark. In **Tools → Handwriting → Spaced line**, an actual `1+1` drawing was read as three glyphs `1`, `+`, `1`, with editable draft `1+1`; **Use reviewed expression** then **=** returned 2. The UI hierarchy is retained as `api23-r7-ink-draft.xml`, and the emulator was stopped after the check. The new photo line-picker has six passing adapter tests but has not been device-tested in this 1.1 session. No new 1.1 API 34 or broad recognition-accuracy claim is made.

The completed record below describes the **Android 1.0.0-preview `r6` baseline** and its preceding iterations. Its API 23/34 device observations must not be treated as new-APK 1.1 device tests.

## Historical verified 1.0 baseline

Snapshot: **11 November 2026, 02:27 India time**. The `r3` combined release gate and packaged API smoke test are green, as are the subsequent Android-only gates through `r6`. Fresh static/native/legal/signature receipts identify the exact `r6` APK. Final `r6` checks passed on both API 23 and API 34 emulators, including switch-label readback, exact-value clipboard round trip and linear intersections on API 34, plus same-APK API 23 installation and calculation. The earlier broader device observations remain separately documented; only two switch accessibility labels changed between `r5` and `r6`. The required preview handoff checks are complete within the scope recorded here. This record supersedes old milestone test counts in `PROGRESS.md`; those historical reports remain unchanged.

## Historical 1.0 — Completed build and test gate

The local `release-all-gates-r3.log` ends **BUILD SUCCESSFUL**, taking 6 minutes 48 seconds and completing 66 Gradle tasks (39 executed, 27 up-to-date). The checked result includes current engine/API/Android tests, regression batteries, the core Android-import boundary check, debug APK assembly, Android lint, and the API distribution ZIP. Earlier failed attempts are not counted as successful runs.

| Test set | Suites | JUnit tests | Failures / errors / skipped |
| --- | ---: | ---: | --- |
| Core mathematics, editor, history and codec | 59 | 800 | 0 / 0 / 0 |
| API HTTP, strict JSON and configuration | 3 | 43 | 0 / 0 / 0 |
| Android adapters and session behavior, running on the JVM | 11 | 82 | 0 / 0 / 0 |
| Battery harness wrappers and safety tests | 4 | 9 | 0 / 0 / 0 |

These counts were read from completed XML, not inferred from a command starting. The unchanged core/API `r3` XML timestamps start at 01:37:32 and 01:38:15 India time respectively. Latest Android `r6` XML starts at 02:16:30 India time; the earlier `r3` Android XML began at 01:40:21. The XML stores UTC timestamps. The `r3` boundary check scanned **120 Kotlin files** and found no Android imports in `core`.

The preceding successful `r2` gate is preserved in `release-all-gates-r2.log`: 796 core tests in 58 suites, 43 API tests in 3 suites, 78 Android tests in 10 suites, and the same battery totals below. Its duration was 5 minutes 59 seconds. The `r3` counts include four new equation/system budget regressions and four Android insertion regressions.

Subsequent Android-only gates `release-android-gates-r4.log` and `release-android-gates-r5.log` completed successfully in 2 minutes 24 seconds and 2 minutes 4 seconds respectively. The final `release-android-gates-r6.log` completed **BUILD SUCCESSFUL** in 2 minutes 39 seconds (54 tasks: 14 executed, 40 up-to-date). Its XML confirms the same 82 Android tests in 11 suites, with no failures/errors/skips. Lint remains at zero errors, 21 warnings and four informational findings. Core, API and packaged API inputs did not change during these Android corrections; the `r3` receipts remain their applicable verification.

The nine battery JUnit tests comprise three complete battery runs and six harness-safety tests. Their individual records are a separate measure:

| Battery | Records | PASS | PROBE | FAIL |
| --- | ---: | ---: | ---: | ---: |
| B1 expressions | 1,000 | 922 | 78 | 0 |
| B2 history/tape | 500 | 479 | 21 | 0 |
| B3 editor/keypad | 520 | 509 | 11 | 0 |
| Total | **2,020** | **1,910** | **110** | **0** |

**PROBE is an exploratory or unsupported comparison, not a passing assertion.** The table does not convert the 110 probes into successes or add the battery records to the JUnit count. Asserted battery failures fail the build.

Generated evidence locations are `core/build/test-results/test`, `api/build/test-results/test`, `android/build/test-results/testDebugUnitTest`, and `battery/build/test-results/test`. Complete current battery records are `battery/build/battery/chunk-0.json`, `b2-chunk-all.json`, and `b3-chunk-all.json`; older chunk files are not part of this run.

## Historical 1.0 — Lint and packaging

Android lint completed with **0 errors**, **21 warnings**, and **4 informational findings**. The remaining findings are dependency/plugin update notices (17), a missing monochrome launcher icon (1), version-catalog suggestions (3), and Compose primitive-state suggestions (4 informational). Counts include repeated notices emitted for multiple declarations. No dependency upgrade was silently applied to obtain a green result.

The `r2` data-extraction warning was fixed in `r3`. Inspection of the compiled APK confirms `allowBackup=false`, `fullBackupContent=false`, the data-extraction resource reference, and 18 compiled exclusions: nine storage domains each for cloud backup and device transfer. The warning is absent from the new lint report. This verifies the packaged configuration; an actual cloud-backup/device-transfer exercise has not been performed.

The 1.0 baseline artifacts covered by this static/native/legal audit are the `r6` APK and unchanged `r3` API distribution. The Android build path may later contain the 1.1 update; the hash below identifies the earlier 1.0 artifact specifically:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `android/build/outputs/apk/debug/android-debug.apk` | 56,393,788 | `3c0679405cd430413d6f1bdcb0a5591479d05379b0010c463eeb1e3bd816be3d` |
| `api/build/distributions/api-1.0.0-preview.zip` | 10,464,736 | `dbd4fb0f9349761c47f36091882f48c6ca86626a6b6c620da59df3853c3d11ac` |

The APK is a development-signed preview. The final `apk-signature-r6.txt` receipt reports **Verifies**, with one signer, v1/v2 signatures present and v3/v3.1/v4 absent; no SourceStamp is present. It includes standard unsigned `META-INF` metadata warnings, so signature verification is not described as warning-free. Fresh `r6` permission/manifest inspection confirms no Internet or network-state permission; the only listed permission is the app's signature-protected dynamic-receiver permission. The binary manifest declares minSdk 23, targetSdk 34, both backup flags false and the data-extraction resource; the compiled resource retains all 18 exclusions.

All **36 APK legal assets** and **38 API notice/document files** match their source bytes. All 21 external API JAR hashes match the dependency inventory. The desugaring corresponding-source archive retains SHA-256 `5ac0164b7ceeb51b21f641f1a002baadf551fe8c00051820df71ad632148874f`. The fresh local receipt is `packaged-notice-audit-r6.json`; the `r3` and `r5` notice receipts are retained separately.

The `r2` APK used for the earlier device observations below had 56,060,117 bytes and SHA-256 `19bb9c4003e22ac1c88cc6da2466c189e9f0367ea0ea20d65247b7f38167c91f`; its API ZIP had 10,462,032 bytes and SHA-256 `454a2fc34809c5401c61a0744eb42c0231daca512275094e245d77ebe1092540`. The `r3` APK had 56,038,124 bytes and SHA-256 `61bda77280f8b450c93005eaa1a6eefe010e3aa38cfe06c93464741089e012b0`. The `r5` APK had 56,393,167 bytes and SHA-256 `804e1408ebd5e197833bd5b34d55d36e43f27b3ba0e0b15ca8293dc5098cb9f4`. Rebuilding creates a new artifact; its hash and applicable verification must be recorded again. These are local build receipts, not GitHub Actions runs, Play Store submissions or public deployments.

## Historical 1.0 — Packaged API smoke test

At 01:42:15 India time, the extracted `r3` API ZIP ran independently of Gradle in a hidden JVM capped at 256 MB. Seven checks passed:

1. The packaged server started and answered `/health`.
2. Its capabilities included surface sampling and definite integration.
3. An integer beyond the IEEE double range retained its digits; localized decimal presentation retained the exact `1/3` machine value and approximation metadata.
4. A 3 × 3 `x+2*y` surface returned the expected row-major plane samples.
5. The integral of `x` from 0 to 1 returned approximately one half with estimate metadata.
6. A wrong request-field type returned HTTP 400, and the server remained healthy afterward.
7. A long flat equation within the input-character limit returned HTTP 422 with `RESOURCE_LIMIT` for excessive AST depth; a subsequent normal calculation returned `2+3 = 5`.

The child JVM was stopped after the test and independently confirmed absent. All **21 external dependency JARs** matched the resolved inventory's hashes; both project JARs, Windows/Unix launchers and license/privacy/API notices were present. The Android-only desugaring source archive was correctly excluded from the server ZIP.

All smoke requests used `127.0.0.1`, and configuration tests verify the loopback default. Windows CIM did not expose the live endpoint in its socket table. The run explicitly records **socket-owner/public-binding inspection unavailable**; that unavailable check is not reported as a pass. The retained receipt is `api-packaged-smoke.json` in the local verification work folder. The earlier six-check `r2` success at 01:03:44 is preserved separately as `api-packaged-smoke-r2.json`; two preceding harness attempts had stopped after successful health checks while trying the unavailable socket inspection, with their child processes cleaned up.

## Historical 1.0 — Native-library 16 KB static audit

The APK contains four ML Kit OCR native libraries, one per ABI. Static inspection decoded ZIP local-header offsets and ELF load/RELRO program headers without executing or extracting the libraries. The refreshed `apk-native-alignment.json` receipt covers the exact `r6` APK hash above; the `r3` and `r5` receipts are preserved separately. All four native library hashes remain byte-identical to those earlier builds, and their current ZIP data offsets are 16 KB aligned and uncompressed. The APK hash was unchanged across the inspection.

| ABI | ELF load-segment assessment | Current APK ZIP alignment |
| --- | --- | --- |
| `arm64-v8a` | 16 KB alignment/congruence and RELRO checks passed | 16 KB aligned |
| `x86_64` | 16 KB alignment/congruence and RELRO checks passed | 16 KB aligned |
| `armeabi-v7a` | 4 KB ELF alignment recorded; not counted as a 16 KB ELF pass | 16 KB aligned |
| `x86` | 4 KB ELF alignment recorded; not counted as a 16 KB ELF pass | 16 KB aligned |

SDK Build Tools 34's legacy ZIP alignment check passed, but that tool lacks `-P 16`; the direct header inspection supplied the 16 KB ZIP assessment. No NDK `llvm-readelf` was available. **Execution on a 16 KB-page device has not been tested and remains future coverage.** Static headers do not prove correct execution, and this record does not claim that every ABI is 16 KB compatible. The assessment follows [Android's page-size guidance](https://developer.android.com/guide/practices/page-sizes), which also calls for runtime testing.

## Historical 1.0 — Device QA observed so far

The initial QA session booted an **Android 6 / API 23 emulator**, installed the `r2` APK and opened it offline. One measured first launch took **497 ms**. This is one emulator measurement, not a physical-device benchmark or a startup guarantee. The following table records that `r2` session.

| API 23 workflow | Observed status |
| --- | --- |
| `2+3` | Correct result, 5 |
| `0.1+0.2` | Correct exact result, 0.3 |
| `1/3` and S⇔D | Fraction/decimal presentation toggle exercised |
| `sin(30)` in DEG | Correct result, 0.5 |
| Quadratic equation and available Steps sheet | Solving and steps workflow exercised |
| Graph `x^2-4` | Exact roots −2 and 2, each with residual 0; tracing x = 3 returned y = 5 |
| Units tool, default 10 km → m | Correct result, 10,000 m |
| Matrix `[[1,2],[3,4]]` | Determinant −2; inserting the result and evaluating returned −2 |
| Statistics for 1, 2, 3, 4, 5 | Count 5, sum 15, mean/median 3, population variance 2, sample variance 2.5; exact square-root standard deviations shown |
| Differentiate `x^2+2x+1` | Returned `2*x+2` with the conditions shown |
| First-use photo recognition offline | Airplane mode enabled and Wi-Fi/mobile data disabled before launch; the `12+34` fixture was recognized, reviewed, inserted and evaluated to 46 |
| Surface `x^2-y^2` | 25 × 25 grid: 625 defined, 0 undefined, 0 unevaluated; Z range −25 to 25; trace at (3, 2) returned 5; wireframe visually inspected |
| Experimental symbol pad | One vertical finger stroke proposed `1`; selecting and inserting it evaluated to 1 |
| Home, force-stop and relaunch | Preserved 12 history rows and editor input `1`; the primary answer was cleared |
| Dependent history recomputation | Entry #13, `#1*2`, changed from 10 to 20 after editing #1 from `2+3` to `7+3` |
| Clear history and immediate Undo | Restored the one-row history and result 5 |
| Dark theme, Indian grouping and Devanagari numerals | `1234567` displayed as `१२,३४,५६७`; restart retained theme, editor and history |
| Landscape layout | All calculator keys remained visible and functional; the app name was ellipsized in the left panel |

These are observed workflows on one API 23 emulator, not broad recognition-accuracy or physical-device claims. The OCR check exercises first use with an actual fixture while offline; it is separate from the offline launch check. The symbol-pad result covers one finger stroke, not a stylus accuracy study. Retained screenshots in the local work folder include `api23-graph-roots.png`, `api23-unit-tool.png`, `api23-matrix.png`, `api23-statistics.png`, `api23-calculus.png`, `api23-ocr-offline.png`, `api23-ocr-recognized.png`, `api23-surface.png`, `api23-handwriting.png`, `api23-history-recompute.png`, and `api23-localization-dark.png`. Captured `api23-r2-logcat.txt` contains no `FATAL EXCEPTION`, fatal signal or app ANR in the observed session; this is not a general crash-free guarantee.

The `r3` APK was then installed and launched successfully on the same API 23 emulator. These additional checks were completed on that artifact:

| API 23 `r3` workflow | Observed status |
| --- | --- |
| Edit sheet and keyboard dismissal | **Use expression** was visible immediately and remained visible after pressing Back to close the keyboard; no partial collapse |
| Effective 130% font scale | `adb dumpsys window` reported `mCurConfiguration={1.3...}`; all main portrait calculator keys and buttons remained visible |
| Rotation with an existing result | The displayed result `१/३` survived portrait-to-landscape rotation |
| Delete local data | Reset settings and reduced history to zero entries |
| Corrupted saved session | With the app stopped, its test `session.calc` was deliberately replaced with `BROKEN`; relaunch showed an invalid-size/preserved-file warning |
| Preservation after corrupt-session warning | Calculating `2+2 = 4` did not overwrite the damaged file; an independent `run-as cat` still read `BROKEN` |
| Explicit recovery after corruption | Settings → Delete restored normal persistence; after calculating `9 = 9`, Home, force-stop and relaunch restored history entry #1 with value 9 |

Retained `r3` evidence includes `api23-final-edit-sheet.png`, `api23-final-edit-keyboard-dismissed.png`, `api23-final-landscape.png`, `api23-final-delete-reset.png`, `api23-final-corrupt-save.png`, and `api23-final-recovered-session.png`. The font-scale check covers the main portrait calculator and the observed rotated layout, not every sheet or all accessibility configurations.

After the modal-window corrections, the `r5` APK completed these API 23 checks at the confirmed 130% font scale:

| API 23 `r5` workflow | Observed status |
| --- | --- |
| Modal sizing across rotation | Full width on opening and after rotation, without a doubled right-side inset |
| Modal navigation bar | Black/readable in light and dark themes, portrait and landscape, with content clear of the bar |
| Editor retention and usable actions | `123+456` survived portrait → landscape → portrait; the landscape action was reachable by scrolling; **Use** then **=** returned 579 |
| Oversized tool result | A Simplify result with 65 nested `sin` calls was refused on **Use** with a visible **Could not use result** / maximum-64-level explanation; Tools stayed open, and dismissing the notice and returning left the main result 579 intact |

Screenshots are retained as `api23-r5-*.png`. The captured `api23-r5-logcat.txt` contains no `FATAL EXCEPTION`, fatal signal or app ANR during this session. The API 23 emulator was stopped after these checks. This does not establish behavior on every device or every font scale.

The same `r5` APK was installed on an **Android 14 / API 34 emulator**, then app data was cleared for a fresh session. Wi-Fi and mobile data were disabled before first launch. `dumpsys connectivity` reported **Active default network: none**, Wi-Fi was disabled, and `mobile_data` was zero. The protected airplane-mode broadcast was denied on API 34; its settings flag alone is not counted as proof of disconnection. These network-state observations establish the offline condition used for this session. One cold `am start` measured **2,097 ms** on this emulator, not a physical-device performance claim.

| API 34 `r5` workflow | Observed status |
| --- | --- |
| Basic and exact decimal arithmetic | `2+2 = 4` and `0.1+0.2 = 0.3` |
| Fraction/decimal presentation | `1/3` displayed exactly, then S⇔D displayed `0.333333333333`; the accessibility description included **Approximately** |
| Structured fraction gesture | An actual upward flick on the minus key after entering `1` created a two-dimensional fraction; entering `2` produced `1/2`, then S⇔D displayed `0.5` |
| Degree trigonometry | `sin(30)` in DEG returned 0.5 |
| Physical constant with units | `c*2s` returned 599,584,916 m |
| Number-base tool and rotation | 255 in base 10 converted to FF in base 16; fields survived portrait → landscape → portrait while the tool stayed open; **Use** then **=** returned 255 |
| Modal geometry and system bars | API 34 tool rotation retained full width with content clear of the navigation bars |
| Complex-number setting | `sqrt(-4)` with complex numbers disabled produced an explicit negative-root error; enabling complex numbers returned `2i` |
| Definite numerical integration and reuse | `sin(x)` from 0 to `pi` returned approximately 2 with estimated error about `4.51723400167e-21` and 62 evaluations, while global mode remained DEG; reusing the numeric result returned 2 with the accessibility description **Approximately 2** |
| Graph register binding | Stored `12→A`, opened Graph from editor input `A*x`, and traced x = 3; curve 1 returned (3, 36) |

The integration uses radians independently of the global numeric angle setting; its reused result is a numeric constant, so retaining DEG is correct. The estimate and its reported error are not exact-value or rigorous-error-bound claims. Screenshots include `api34-r5-home.png`, `api34-r5-structured-fraction.png`, `api34-r5-tool-rotation.png`, `api34-r5-bases.png`, `api34-r5-complex.png`, `api34-r5-definite-integral.png`, and `api34-r5-memory-trace.png`.

The captured `api34-r5-logcat.txt` contains **one fatal exception in the emulator's system keyboard**, process `com.android.inputmethod.latin` (PID 1209): a `SuggestionStripView.onScroll` null-pointer exception during a gesture. It is not a calculator crash, and the whole log is not described as fatal-error-free. No calculator fatal exception or ANR was observed in this session. The API 34 emulator was stopped after these checks.

UI automation found that the Complex switch lacked an accessibility label. Semantic labels **Complex numbers** and **Stylus only, ignore fingers** were added, passed the `r6` Android build/test/lint gate, and were confirmed on clickable switch nodes in the final API 34 UI hierarchy. This is not a completed TalkBack audit.

The final `r6` APK then completed these API 34 checks with no active default network:

| API 34 `r6` workflow | Observed status |
| --- | --- |
| Accessibility switch readback | Both clickable switches exposed **Complex numbers** and **Stylus only, ignore fingers**, retained in `api34-r6-*-label.xml` |
| Exact-value clipboard round trip | `sqrt(8)` returned `2√2`; tapping the answer copied it, Edit → Paste displayed `2√2`, and **Use** then **=** returned `2√2` again |
| Two-curve intersection | Plotted `x` and `-x`; **Intersect(1,2)** displayed x = 0, residual 0 and exact linear analysis |

The saved `api34-r6-logcat.txt` contains no `FATAL EXCEPTION`, fatal signal or ANR markers, and no calculator crash-process marker. This later clean session does not erase the separately documented `r5` system-keyboard crash. The API 34 emulator was stopped afterward.

The **same final `r6` APK** was then installed as an update on API 23 and launched successfully. `sqrt(8)` returned the exact value `2√2` (`api23-r6-main.png`). Both switch labels were read back again on clickable nodes in `api23-r6-complex-label.xml` and `api23-r6-stylus-label.xml`. `dumpsys window` confirmed effective font scale 1.3; Wi-Fi and mobile data remained disabled. One cold activity launch measured **560 ms** on this emulator, not a physical-device benchmark. The captured `api23-r6-logcat.txt` has no fatal-exception, fatal-signal, ANR or calculator crash-process markers. These checks close the final same-APK compatibility sanity gate; they do not establish behavior on every Android device.

## Historical 1.0 — Scope limits and future coverage

- The modal navigation, width and doubled-inset findings passed their `r5` API 23 retests described above; the API 34 number-base tool retained its fields across both rotations, with full modal width and content clear of the navigation bars. Wider modal variations remain untested. The Edit sheet's separate partial-collapse issue passed its `r3` device retest.
- Inline `5 km to m` is unsupported and correctly produces an unknown-name error. Main entry supports unit arithmetic such as `5 km+300 m`; conversion into a chosen unit uses **Tools → Units** or the API. The `r3` help text and current guide use the supported arithmetic example; no new grammar is implied.
- Explicit Android 12+ backup/transfer exclusions are compiled and inspected in `r3`; actual backup/transfer behavior remains untested. The tested `r3` rotation preserved its existing result; wider rotation/state combinations remain untested.
- The iterative equation/system preflight rejects more than 512 total expression nodes or depth 64 before recursive unknown/polynomial analysis. Plain scalar evaluation is unchanged. Its four new core regressions and four Android insertion regressions passed the `r3` release gate; the API structural-refusal/recovery and API 23 oversized-tool-result recovery checks are recorded above.
- Final build/static/native/legal/signature checks and both final-APK emulator sanity gates have passed. Other keyboard/tool/settings variations remain outside the exercised workflows and are future coverage, rather than outstanding required preview handoff checks.
- Expand offline OCR fixtures and symbol-pad cases, and test real stylus input. No population handwriting-accuracy claim is made.
- The effective 130% font-scale main-screen check passed as described above. Larger scales, all modal layouts, TalkBack, small-screen/tablet and 16 KB-page devices remain future test coverage. Successful JVM tests do not replace those checks.

To reproduce the automated gate with the documented JDK/SDK setup, run the engine boundary check and module tests, then build/lint the APK and package the API as described in [the README](../README.md). After source changes, record fresh results for the affected checks before treating a new APK as verified.
