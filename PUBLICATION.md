# Public Version 1.1

This snapshot is CalcForAll's second historical development preview, internally `1.1.0-preview` (Android version code 2). The original tested source commit is `6cb441efd5dcc50c3eade33e1be6d61d83831990`.

Download the Android APK, optional unchanged local API, source and historical Android test evidence from [Version 1.1](https://github.com/vaibhavvppuri/CalcForAll/releases/tag/v1.1).

Version 1.1 adds recognition of spaced handwriting lines, editable recognized drafts, explicit symbol alternatives and selection of individual recognized photo lines. Recognition remains experimental and requires review. This historical preview is not final Version 2.0.

The public source archive and this tree remove one internal local path from a design document. Application code, build inputs, dependencies and licenses retain the original source identity. `PUBLICATION.json` attached to the release maps original and public artifact hashes. This extra repository notice identifies publication provenance; the public commit differs from the original private development-history commit.

The tested APK and local API retain their original bytes. The local API is still `1.0.0-preview`. Historical Version 1.1 evidence contains 14 Android JUnit suites with 99 passing tests. The historical API23 emulator ink check covered `1+1` recognition and reviewed calculation. Photo-line selection had adapter tests; no fresh photo-device test or API34 run is claimed for this release. Physical stylus testing remains unverified.

The original CI workflow is preserved. The publication commit skips redundant automatic builds and makes no claim of a new GitHub CI pass. This publication did not rebuild or rerun the application tests.
