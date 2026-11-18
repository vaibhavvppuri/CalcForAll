# Third-party notices

CalcForAll's original code is licensed under the root [MIT License](LICENSE). That license does not replace any third-party license. This document accompanies the 2.0 distribution, built from the pinned dependency coordinates in this repository. The runtime dependency versions and upstream notice contents are unchanged from the recorded inventory.

## Principal components

| Component | License / terms | Included evidence |
| --- | --- | --- |
| `ch.obermuhlner:big-math:2.3.2` | MIT; copyright 2017 Eric Obermühlner | [Complete license](docs/licenses/big-math-2.3.2-MIT.txt), verified against the [v2.3.2 source](https://github.com/eobermuhlner/big-math/blob/v2.3.2/LICENSE.txt). |
| Kotlin standard library and Kotlin extensions | Apache License 2.0; JetBrains and contributors | [Apache 2.0](docs/licenses/Apache-2.0.txt), upstream [Kotlin copyright](https://github.com/JetBrains/kotlin/blob/master/license/COPYRIGHT.txt), and runtime notices listed below. |
| AndroidX, Compose, Activity, Core and ExifInterface | Apache License 2.0; their respective copyright holders | [Apache 2.0](docs/licenses/Apache-2.0.txt) and [AndroidX license source](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt). |
| Ktor 3.0.3 | Apache License 2.0; JetBrains and contributors | [Ktor's versioned license](https://github.com/ktorio/ktor/blob/3.0.3/LICENSE) and [local Apache 2.0 copy](docs/licenses/Apache-2.0.txt). Ktor is part of the optional JVM API, not the Android calculator. |
| Android core-library desugaring `com.android.tools:desugar_jdk_libs:2.0.3` | GNU GPL version 2 with the Classpath Exception and component-specific notices | [Complete license and exception](docs/licenses/desugar_jdk_libs-2.0.3-LICENSE.txt), [Assembly Exception](docs/licenses/desugar_jdk_libs-2.0.3-ASSEMBLY_EXCEPTION.txt), [upstream third-party notices](docs/licenses/desugar_jdk_libs-2.0.3-THIRD_PARTY_README.txt), and [included corresponding source](docs/licenses/corresponding-source/README.md). The Classpath Exception permits the described independent-module combination; this dependency does not relicense CalcForAll's original code as GPL. |
| Desugaring configuration and adapters `com.android.tools:desugar_jdk_libs_configuration:2.0.3` | BSD 3-Clause; copyright 2016 the R8 project authors | [Complete R8 license](docs/licenses/R8-BSD-3-Clause.txt), matching the resolved artifact's declaration and [R8 upstream license](https://r8.googlesource.com/r8/+/refs/heads/main/LICENSE). This configuration/adapters artifact has separate terms from the OpenJDK-derived desugaring library. |
| Google ML Kit Latin text recognition 16.0.1 and its model / Google Play services dependencies | Google's applicable SDK terms, including [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) and incorporated [Google APIs Terms](https://developers.google.com/terms) | The resolved ML Kit POM declares ML Kit Terms of Service. This SDK/model is **not licensed by CalcForAll as MIT or Apache**. See the preserved upstream third-party notices below. |
| Google Play services base/basement/tasks and ODML image support | [Android Software Development Kit License](https://developer.android.com/studio/terms.html), as declared by their resolved POMs | Exact versions and declarations are recorded in the resolved inventory; these Google SDK components are not relicensed under the app's MIT license. |
| IBM Plex Sans and IBM Plex Sans Devanagari | SIL Open Font License 1.1; copyright 2017 IBM Corp.; Reserved Font Name “Plex” | Complete licenses are already bundled in [OFL-IBMPlexSans.txt](android/src/main/assets/fonts/OFL-IBMPlexSans.txt) and [OFL-IBMPlexSansDevanagari.txt](android/src/main/assets/fonts/OFL-IBMPlexSansDevanagari.txt). |
| JetBrains Mono 2.304 | SIL Open Font License 1.1; copyright 2020 The JetBrains Mono Project Authors | Complete [OFL-JetBrainsMono.txt](android/src/main/assets/fonts/OFL-JetBrainsMono.txt) is bundled in the APK. |

Font resource filenames and weights are documented in [android/FONTS.md](android/FONTS.md). The fonts are retained as supplied, with their license and reserved-name notices.

## Preserved Google SDK notices

The following files are copied byte-for-byte from the resolved `com.google.mlkit:text-recognition:16.0.1` AAR. They identify and preserve the SDK's own third-party notices; their presence does not relicense Google's SDK or model.

- [Upstream third-party license text](docs/licenses/mlkit-text-recognition-16.0.1-third_party_licenses.txt)
- [Upstream notice index](docs/licenses/mlkit-text-recognition-16.0.1-third_party_licenses.json)

The large upstream notice bundle covers the components Google listed in that artifact. Some entries may describe code or optional components not used by this app; preserving the supplied bundle avoids silently dropping upstream attribution.

## Distribution

Keep this notice file, the `docs/licenses` directory, and bundled font licenses with redistributed source and binaries. Preserve license/notice files inside JVM distribution JARs. Google's SDK terms continue to apply to bundled OCR. Changing dependencies requires regenerating the resolved-artifact inventory and reviewing any new notices.

Development-only tools and tests (Gradle, Android Gradle Plugin, Kotlin compiler, Kotest and JUnit) retain their upstream licenses; they are not all application runtime dependencies. Neither a dependency's presence in the local Gradle cache nor a POM's license label is treated as proof that it ships in the APK. The resolved runtime inventory below records the artifacts actually selected for this build.

## Resolved dependency inventory

The [readable inventory](docs/licenses/RESOLVED-DEPENDENCIES.md) and [machine-readable inventory with SHA-256 hashes](docs/licenses/RESOLVED-DEPENDENCIES.json) record the selected Android and JVM API runtime artifacts, plus Android core-library desugaring. License and notice entries found in those artifacts are copied without changing their contents. Shared notice contents are deduplicated; each artifact's entry names the retained files.

Keep the desugaring source archive beside the source/APK release. It is an unmodified archive of the upstream 2.0.3 release commit, with provenance and hashes in its README. The APK embeds the legal text and source location, while the source release contains the archive. The optional JVM server does not use Android desugaring and does not include that Android-only source archive.
