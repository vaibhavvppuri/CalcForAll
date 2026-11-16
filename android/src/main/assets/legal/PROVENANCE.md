# Notice provenance

This preview's inventory was resolved on 10–11 November 2026 from Gradle's selected `:api:runtimeClasspath`, `:android:debugRuntimeClasspath`, and `:android:coreLibraryDesugaring` artifacts. The union contains 107 external artifacts. Internal CalcForAll project JARs are covered by the root MIT license.

`RESOLVED-DEPENDENCIES.json` records each artifact coordinate, selected modules, artifact SHA-256, declared licenses, metadata source, and retained notice filenames. POM metadata came from the resolved Gradle cache or the exact pinned artifact's POM on Maven Central (`repo.maven.apache.org`) or Google's Maven repository (`dl.google.com`). The Guava and SLF4J license declarations are inherited from their exact versioned parent POMs; this is identified in the JSON. A license name in metadata describes the upstream declaration and does not replace the full license text.

License, copyright, notice, and `third_party_licenses` entries were inspected in the actual resolved JARs/AARs, including AAR `classes.jar` contents. Nineteen distinct notice files were preserved byte-for-byte and deduplicated by SHA-256. Several Google notice indexes refer to the same text bundle; the inventory maps them to the retained file. Upstream empty notice files are also preserved. Some libraries do not embed a full license in their published binary; the principal component notices and the supplied Apache/MIT/OFL texts cover those separately.

The Android desugaring source and legal texts have additional release-specific provenance in [corresponding-source/README.md](corresponding-source/README.md). The ML Kit SDK/model remains subject to Google's SDK terms; preserving its open-source component notices does not classify the whole SDK as open-source software.

Re-resolve and review this inventory whenever runtime dependencies change. Retain upstream notices and applicable source obligations with redistributed binaries. The app bundles these legal texts under `assets/legal`; the Android-only desugaring source archive accompanies the source/APK release instead of occupying app storage.
