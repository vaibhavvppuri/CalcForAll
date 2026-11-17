# Verification record — Android 1.1 preview and API 1.0 preview

## Android 1.1 update — current evidence

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

## Verified 1.0 baseline

Snapshot: **11 November 2026, 02:27 India time**. The `r3` combined release gate and packaged API smoke test are green, as are the subsequent Android-only gates through `r6`. Fresh static/native/legal/signature receipts identify the exact `r6` APK. Final `r6` checks passed on both API 23 and API 34 emulators, including switch-label readback, exact-value clipboard round trip and linear intersections on API 34, plus same-APK API 23 installation and calculation. The earlier broader device observations remain separately documented; only two switch accessibility labels changed between `r5` and `r6`. The required preview handoff checks are complete within the scope recorded here. This record supersedes old milestone test counts in `PROGRESS.md`; those historical reports remain unchanged.

## Completed build and test gate

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

## Lint and packaging

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

## Packaged API smoke test

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

## Native-library 16 KB static audit

The APK contains four ML Kit OCR native libraries, one per ABI. Static inspection decoded ZIP local-header offsets and ELF load/RELRO program headers without executing or extracting the libraries. The refreshed `apk-native-alignment.json` receipt covers the exact `r6` APK hash above; the `r3` and `r5` receipts are preserved separately. All four native library hashes remain byte-identical to those earlier builds, and their current ZIP data offsets are 16 KB aligned and uncompressed. The APK hash was unchanged across the inspection.

| ABI | ELF load-segment assessment | Current APK ZIP alignment |
| --- | --- | --- |
| `arm64-v8a` | 16 KB alignment/congruence and RELRO checks passed | 16 KB aligned |
| `x86_64` | 16 KB alignment/congruence and RELRO checks passed | 16 KB aligned |
| `armeabi-v7a` | 4 KB ELF alignment recorded; not counted as a 16 KB ELF pass | 16 KB aligned |
| `x86` | 4 KB ELF alignment recorded; not counted as a 16 KB ELF pass | 16 KB aligned |

SDK Build Tools 34's legacy ZIP alignment check passed, but that tool lacks `-P 16`; the direct header inspection supplied the 16 KB ZIP assessment. No NDK `llvm-readelf` was available. **Execution on a 16 KB-page device has not been tested and remains future coverage.** Static headers do not prove correct execution, and this record does not claim that every ABI is 16 KB compatible. The assessment follows [Android's page-size guidance](https://developer.android.com/guide/practices/page-sizes), which also calls for runtime testing.

## Device QA observed so far

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

## Scope limits and future coverage

- The modal navigation, width and doubled-inset findings passed their `r5` API 23 retests described above; the API 34 number-base tool retained its fields across both rotations, with full modal width and content clear of the navigation bars. Wider modal variations remain untested. The Edit sheet's separate partial-collapse issue passed its `r3` device retest.
- Inline `5 km to m` is unsupported and correctly produces an unknown-name error. Main entry supports unit arithmetic such as `5 km+300 m`; conversion into a chosen unit uses **Tools → Units** or the API. The `r3` help text and current guide use the supported arithmetic example; no new grammar is implied.
- Explicit Android 12+ backup/transfer exclusions are compiled and inspected in `r3`; actual backup/transfer behavior remains untested. The tested `r3` rotation preserved its existing result; wider rotation/state combinations remain untested.
- The iterative equation/system preflight rejects more than 512 total expression nodes or depth 64 before recursive unknown/polynomial analysis. Plain scalar evaluation is unchanged. Its four new core regressions and four Android insertion regressions passed the `r3` release gate; the API structural-refusal/recovery and API 23 oversized-tool-result recovery checks are recorded above.
- Final build/static/native/legal/signature checks and both final-APK emulator sanity gates have passed. Other keyboard/tool/settings variations remain outside the exercised workflows and are future coverage, rather than outstanding required preview handoff checks.
- Expand offline OCR fixtures and symbol-pad cases, and test real stylus input. No population handwriting-accuracy claim is made.
- The effective 130% font-scale main-screen check passed as described above. Larger scales, all modal layouts, TalkBack, small-screen/tablet and 16 KB-page devices remain future test coverage. Successful JVM tests do not replace those checks.

To reproduce the automated gate with the documented JDK/SDK setup, run the engine boundary check and module tests, then build/lint the APK and package the API as described in [the README](../README.md). After source changes, record fresh results for the affected checks before treating a new APK as verified.
