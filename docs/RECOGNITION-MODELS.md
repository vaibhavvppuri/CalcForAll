# Offline recognition model research

**Follow-up, 15 November 2026:** [small CoMER model feasibility](RECOGNITION-MODELS-2026-11-15.md) records a promising smaller export, unresolved upstream weight permissions and API23 runtime requirements. No learned model has been integrated or ruled out as technically impossible. The review below remains the dated Paddle/ML Kit assessment.

Reviewed **14 November 2026** for the 2.2 development cycle. This is a feasibility review, not a model integration or accuracy claim. Only publisher documentation, model listings and source text were inspected. No model weights were downloaded or executed, and no runtime dependency was added for this research.

## Current decision

Keep the bundled photo OCR and bounded ink-template/layout reader for this increment. Improve review, image preparation, orientation choices and recoverable ink edits. These changes do not turn either recognizer into a general cursive-math or word-problem system.

The strongest candidate for a separate learned-model experiment is **PP-FormulaNet-S**. Its published license and image-to-formula purpose are promising; its Android conversion, actual device resource use and recognition quality remain unverified. It is not a drop-in replacement.

## What the primary sources establish

| Candidate | Evidence and consequence |
|---|---|
| PP-FormulaNet-S / plus-S | The publisher labels both model repositories Apache-2.0. The S file listing totals 238 MB, including 232 MB of parameters; plus-S totals 264 MB, including 257 MB of parameters. Those are publisher artifact sizes, not measured APK or RAM costs. [S model card](https://huggingface.co/PaddlePaddle/PP-FormulaNet-S), [S files](https://huggingface.co/PaddlePaddle/PP-FormulaNet-S/tree/main), [plus-S files](https://huggingface.co/PaddlePaddle/PP-FormulaNet_plus-S/tree/main). |
| Official Paddle deployment | Published formula timing uses an Intel Xeon/Tesla T4 environment, not an Android phone. Its current PP-StructureV3 ONNX Runtime example explicitly disables formula recognition while support is incomplete. Conversion and an Android decoder must therefore be demonstrated rather than assumed. [Formula module](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/module_usage/formula_recognition.en.md), [deployment example](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/pipeline_usage/PP-StructureV3.en.md). |
| ML Kit Digital Ink | Google's installation matrix lists Digital Ink as dynamically downloaded only, while Text Recognition supports bundling. Digital Ink consequently does not meet this app's requirement to work on the first launch without a model download. [Official installation matrix](https://developers.google.com/ml-kit/tips/installation-paths). |
| pix2tex / LaTeX-OCR | The author's original weight release explicitly uses CC BY-NC-SA. A permissive code license must not be substituted for that separate weight license. Do not bundle these weights under an assumption that everything is MIT. [Author's weight release](https://github.com/lukas-blecher/LaTeX-OCR/releases). |
| Google MathWriting | This is a dataset and evaluation-resource release, not a ready Android model. Its data is CC BY-NC-SA 4.0; accompanying code is Apache-2.0. It is not an unrestricted training-data shortcut. [Google Research README](https://github.com/google-research/google-research/blob/master/mathwriting/README.md). |

Model, code, tokenizer and dataset licenses must be tracked separately. Before redistribution, retain the exact pinned model's license and attribution requirements, record modifications such as conversion or quantization, and audit every additional asset. Publisher license metadata is useful evidence; it does not independently verify all upstream training-data rights.

## Android runtime and genuinely offline delivery

Google's current Android-specific LiteRT compatibility table lists **2.3.2 with minimum SDK 23**, including a CPU Interpreter path. This makes API23 execution plausible for a compatible converted model; it does not establish that PP-FormulaNet can be converted or that it will fit a small phone. Confirm the selected AAR manifest, native ABIs and actual device behavior before changing dependencies. [LiteRT Android support](https://developers.google.com/edge/litert/android).

ONNX Runtime also supports loading models on mobile, but requires a compatible ONNX model and enough device storage and memory. Its official guide recommends measuring model/runtime size and latency, then evaluating quantization or a reduced runtime. No current ONNX Runtime AAR size or API23 compatibility was verified in this review. [Official mobile deployment guide](https://onnxruntime.ai/docs/tutorials/mobile/).

To qualify as bundled, all weights, tokenizer tables, preprocessing configuration and required runtime libraries must be included in the distribution. Disable automatic asset resolution. Test a clean installation with network access unavailable, including the first recognition; a cached-model test is insufficient.

## Acceptance requirements for a future PP pilot

1. Pin publisher revisions and hashes; inspect licenses and conversion tools before downloading or executing artifacts. Keep the experiment separate from the default app until its evidence is complete.
2. Demonstrate a complete CPU path on API23 and API34, including preprocessing, bounded token decoding and supported formula conversion. Audit native page alignment and every shipped ABI. Measure peak memory, package/storage growth, cold/warm latency and cancellation behavior on physical devices as well as compatibility emulators.
3. Compare the existing reader and candidate on the same independent, locally held printed and handwritten expressions. Include fractions, powers, radicals, decimal points, negative signs, rotations, faint print, incomplete expressions and unsupported layouts. Report exact transcription correctness, failures and correction effort; model benchmark scores are not calculator accuracy.
4. Treat generated LaTeX as untrusted transcription. Convert only a bounded supported grammar; never execute TeX or silently discard unknown commands. Preserve the image and editable transcription, require review before saving, and keep stale/cancelled results out of the answer and history.
5. Verify first-use offline operation and zero service charges. Keep broad cursive recognition, arbitrary page understanding and word-problem solving explicitly unsupported unless separately demonstrated.
