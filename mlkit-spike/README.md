# M0.4 Spike — does ML Kit Digital Ink handle math?

**Status: throwaway.** Not a Gradle module, not on the build path, not shipped.
It exists to answer one question that swings the v2 estimate by ~90 hours.

## Verdict (predicted): **FAIL** — text only, no math structure. Confidence: HIGH.

This verdict is **evidence-based, not an on-device run.** This environment has no
Android device/emulator, no touchscreen, and no ML Kit runtime, so the six
handwritten inputs could not be fed through the recognizer here. Rather than
fabricate "outputs I saw," the verdict is inferred from Google's own primary
documentation and the SDK's type system. The full evidence, citations, and
per-input predictions are in [`../DECISIONS.md`](../DECISIONS.md).

The two facts that decide it (both from primary Google sources):

1. **There is no math recognizer to request.** ML Kit Digital Ink ships text
   (300+ languages) plus emoji / autodraw / shapes / gesture models — and
   nothing else. `DigitalInkRecognitionModelIdentifier` has no `MATH`/`FORMULA`
   constant; you literally cannot name one.
2. **The output type cannot hold math.** A `RecognitionCandidate` exposes only
   `text: String` and `score`. There is no field for a superscript, a fraction
   bar, a radical span, or an integral bound. An on-device run can only reveal
   *which flat string* each input degrades to — it cannot produce structure that
   the data model has no room for.

Implication: v2 handwriting is **not** a ~30h SDK integration. It needs a
CROHME/MathWriting-trained stroke→LaTeX model ported to LiteRT — the **~120h**
path. (Per the Constitution's Article 8, that also moves v2 handwriting to
Fable 5.)

## Confirm it empirically (30 minutes on a device)

1. New Android app; add `implementation("com.google.mlkit:digital-ink-recognition:18.1.0")`
   and `<uses-permission android:name="android.permission.INTERNET"/>`.
2. Drop in [`InkMathSpikeActivity.kt`](InkMathSpikeActivity.kt) as the launcher activity.
3. Hand-write each input, tap **RECOGNIZE**, read Logcat (tag `InkSpike`):
   `2+2`, `x²`, `√9`, `∫x dx`, `1/2` **as a stacked fraction**, `sin(30)`.
4. Observe: each candidate is `text=String` + `score` only. Expect `x²`→`"x2"`,
   stacked `1/2`→`"12"` or two split strokes, `√`/`∫` mangled. There is nowhere
   for structure to appear — which is the finding.

If — against the evidence — a candidate ever came back with real 2D structure
(LaTeX/MathML/superscript semantics), that would flip the verdict to PASS.
Nothing in the shipped SDK can do that, which is why confidence is HIGH.
