# Android desugaring corresponding source

The Android app uses `com.android.tools:desugar_jdk_libs:2.0.3` to support Java library APIs on Android 6 and later. This library includes OpenJDK-derived code under GNU GPL version 2 with the Classpath Exception, alongside its upstream component notices.

The accompanying `desugar_jdk_libs-2.0.3-source.zip` is the complete, unmodified upstream repository archive for release commit `4b6383042618306de03e20fe25f3f68081a2677b`. The archive's `VERSION_JDK11.txt` identifies version 2.0.3. CalcForAll makes no modifications to this library's source; the Android build applies the standard Android Gradle Plugin desugaring transformation.

- Upstream repository: https://github.com/google/desugar_jdk_libs
- Exact source: https://github.com/google/desugar_jdk_libs/tree/4b6383042618306de03e20fe25f3f68081a2677b
- Archive source: https://api.github.com/repos/google/desugar_jdk_libs/zipball/4b6383042618306de03e20fe25f3f68081a2677b
- Archive length: 30,592,734 bytes
- Archive SHA-256: `5ac0164b7ceeb51b21f641f1a002baadf551fe8c00051820df71ad632148874f`
- Retrieved: 11 November 2026 (India time)

The archive includes the upstream source, build instructions and build scripts. It also includes `LICENSE`, `ASSEMBLY_EXCEPTION` and `THIRD_PARTY_README`; copies of these legal texts are supplied one directory above and inside the Android app. The source is supplied with the source/APK release, rather than as a time-limited promise to provide it later. Retain this directory when redistributing that release, and provide the corresponding source alongside APK downloads. This is provenance of the supplied upstream source, not a claim of bit-for-bit reproducibility of Google's published Maven artifact.
