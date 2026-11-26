# Learned offline math recognition: CoMER feasibility

Reviewed 15 November 2026 using publisher documentation and remote source text only. No model, runtime, archive or dataset was downloaded or executed; no dependency was changed and no publisher was contacted. This follows the earlier [Paddle/ML Kit model review](RECOGNITION-MODELS.md).

The small ink-on export is worth a bounded technical pilot. Its published model footprint is substantially smaller than the previously considered Paddle formula models. It is not yet a redistributable, tested API23 solution: upstream weight permissions, model/operator compatibility and actual device performance remain unresolved. This is an engineering opportunity, not evidence that learned recognition is exhausted or impossible.

## Identity, pinned artifacts and permissions

CoMER is the ECCV 2022 work by **Wenqi Zhao and Liangcai Gao**, with the official implementation at **Green-Wood/CoMER**. TongkunGuan is not the author/repository identified by these primary sources. The paper reports expression recognition rates of 59.33%, 59.81% and 62.97% on CROHME 2014, 2016 and 2019. These are research benchmark results, not phone-camera or calculator correctness. [Paper](https://arxiv.org/abs/2207.04410), [official implementation](https://github.com/Green-Wood/CoMER).

ink-on release `v0.1.0` points to commit `aaf79ca3722458cd8bf8f38de26a32d0e0b44d68`. Additional current implementation inspection used immutable Git commit prefix `2585994`; this is newer than that release and must not be silently treated as the same source snapshot. [Release commit](https://github.com/kimseungdae/ink-on/commit/aaf79ca3722458cd8bf8f38de26a32d0e0b44d68), [inspected newer commit](https://github.com/kimseungdae/ink-on/commit/2585994).

GitHub's release metadata displays the following sizes and SHA256 digests. Sizes are rounded publisher displays, not exact byte counts measured here; the metadata API was unavailable through the remote reader. The binaries themselves were not inspected or independently hashed. [Published asset metadata](https://github.com/kimseungdae/ink-on/releases/expanded_assets/v0.1.0).

| Asset | Displayed size | Published SHA256 |
|---|---:|---|
| `encoder_int8.onnx` | 3.3 MB | `272f17c7b120db61db6cd8d77e695d0139144044abd57d9b23284435a5d916fa` |
| `decoder_int8.onnx` | 3.93 MB | `658901d81ca3aa4b62b0da262e8fe67ebcfdac19cfb52ca2cfd076bebd481088` |
| `vocab.json` | 3.84 KB | `6ef75b588049981d2b5b2ceedcc7066dddcbb0c7d04ee2cbfb0ec861f192a850` |

ink-on declares Apache-2.0 for its code. The upstream CoMER tree has no visible LICENSE, and its explicit licence question remains open without an answer; the repository is archived. Consequently, this review did **not** establish permission to redistribute the original checkpoint or a conversion of it. ink-on's licence cannot alone resolve that missing upstream grant. Nor did this review establish training-data rights or an exact checkpoint-to-export provenance chain. [ink-on licence](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/LICENSE), [upstream licence issue](https://github.com/Green-Wood/CoMER/issues/15), [upstream checkpoint instructions](https://raw.githubusercontent.com/Green-Wood/CoMER/master/README.md).

The related official BTTR code has an MIT licence, but its viewed README does not establish a ready, independently licensed converted checkpoint suitable for this app. It is an architecture alternative, not an immediate substitute for the unresolved CoMER weights. [BTTR repository](https://github.com/Green-Wood/BTTR), [BTTR licence](https://raw.githubusercontent.com/Green-Wood/BTTR/master/LICENSE).

## Runtime feasibility and costs still to measure

The native ONNX Runtime Android package provides Java/Kotlin integration. Pinned full-AAR build settings for **1.20.1** declare minSDK21 and four ABIs: armeabi-v7a, arm64-v8a, x86 and x86_64. **1.21.0 and 1.24.2 declare minSDK24**, so choosing the latest inspected prebuilt runtime would exclude Android6/API23. An older compatible runtime or carefully verified custom build is a plausible path; neither has been tested here. Final AAR manifest, merged minimum SDK, supported operators/ONNX IR, native alignment and security maintenance need inspection before selection. [1.20.1 settings](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.20.1/tools/ci_build/github/android/default_full_aar_build_settings.json), [1.21.0 settings](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.21.0/tools/ci_build/github/android/default_full_aar_build_settings.json), [1.24.2 settings](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.24.2/tools/ci_build/github/android/default_full_aar_build_settings.json).

The runtime is MIT-licensed. API23 needs a CPU path: the NNAPI execution provider requires API27 or newer. Published model sizes exclude runtime libraries, decompressed tensors, activations and decoder workspaces; neither final APK growth nor peak RAM is established. [Runtime licence](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.20.1/LICENSE), [official mobile guidance](https://onnxruntime.ai/docs/tutorials/mobile/), [NNAPI requirements](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider).

The inspected native-port contract is tractable: one image encoder, then an autoregressive decoder receiving features, mask and an int64 token prefix. The browser implementation recomputes the entire prefix, with default beam width3 and at most50 decoding steps. A native bounded implementation can reuse encoder outputs and enforce one inference owner, cancellation, tensor cleanup and explicit incomplete-output status. This is an inference from source, not measured speed. No Android-WebView compatibility is assumed. [Decoder source](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/src/core/inference.ts).

Preprocessing renders strokes on a black background, scales content to a maximum128-pixel height and creates a 256-high tensor with width128–1024 aligned to64. Maximum input floats alone occupy about1 MiB; intermediate model memory is unknown. A faithful Android rasterizer must be compared against reference inputs. This ink-oriented pipeline does not itself demonstrate robust photographed-paper handling. [Preprocessing source](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/src/core/preprocessing.ts).

## Accuracy and safe integration

ink-on reports **36.41% exact expression recognition**, 53.25% within one edit and65.82% within two edits on986 CROHME2014 expressions, using beam3. These self-reported results do not justify its separate claim of minimal accuracy loss. Its score is not a controlled quantization-only comparison with the CoMER paper. The inspected vocabulary contains113 tokens despite a stale245-symbol table. A locally independent corpus must determine whether it improves this app's correction effort and coverage. [Benchmark and model description](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/README.md), [actual vocabulary](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/public/models/comer/vocab.json).

Do not import its repair policy unchanged: the repair code discards extra brace groups after fraction/root arguments. For example, a subsequent grouped factor can disappear. Rendering-valid LaTeX is not evidence of faithful transcription. Preserve raw candidates, accept only a bounded calculator grammar and require explicit correction/review; never execute TeX. [Repair implementation](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/src/core/latex-repair.ts).

Decode-length/repetition termination must be distinguished from a genuine end token. Returning a syntactically valid prefix after exhausting the decoder is unsafe for calculation. Cancellation must not free a tensor still in use by native inference. These are integration requirements inferred from the inspected decoder, not an accusation that all its outputs are incorrect.

The browser caches a fetched model after first use. That does not meet this app's first-install offline requirement. An Android build would need all verified weights, vocabulary, preprocessing configuration and runtime libraries bundled in its APK, with all automatic downloads disabled. [Model cache implementation](https://raw.githubusercontent.com/kimseungdae/ink-on/2585994/src/core/model-cache.ts).

## A feasible next experiment and final-milestone criteria

1. Resolve the exact exported weight licence/provenance first. A documented permissive alternative is acceptable; absence of a reply is not permission. Meanwhile, bounded token parsing, candidate review and runtime-adapter interfaces can be prepared without shipping these weights.
2. Once acquisition is authorized and provenance is adequate, inspect model metadata before execution, pin exact bytes, and prove a complete CPU encoder/decoder on API23 and API34. Compare cold/warm latency, peak memory and package growth against the current reader. Keep it optional until measured.
3. Run a held-out corpus of supported ink and separately photographed math, including difficult fractions, powers, radicals, stray marks, negatives, incomplete input and unsupported forms. Report exact transcription, refusal rate and user correction effort separately. Do not turn plausible model confidence into certainty.
4. Verify a clean first launch without network, cancellation/retry, rotation and draft retention, explicit review, and durable single save. Include a physical low-end phone and stylus when available; emulator results cannot claim those experiences were tested.
5. Reserve final Version2.0 for a documented stable supported scope: critical correctness and lifecycle gates pass, highest-value remaining improvements have been implemented or measured and rejected for concrete licence/resource/quality reasons, and the remaining backlog has transparent cost-benefit decisions. No claim of 100% recognition, all handwriting, all Android devices or solved word problems is necessary or credible.

These criteria allow further meaningful work without an impossible promise to exhaust every future idea. A model experiment that materially reduces correction effort is valuable even if it remains an alternative to the existing reader.
