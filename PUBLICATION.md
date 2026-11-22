# Public Version 1.5 — UNACCEPTED CANDIDATE

**This is an UNACCEPTED CANDIDATE with a known numerical root-search timeout. It is not an accepted or release-ready build and is not final Version 2.0.**

The final APK passed 30 of 31 native checks on each of API 23 and API 34. The default DEG `cos(x)=x` numerical range operation reached its unchanged 5000 ms budget. Three API 23 runs and one API 34 run retained this failure; the historical candidate did not establish its cause. Passing host, photo and handwriting checks do not override the failed native acceptance gate.

This public milestone is labelled Version 1.5. Its unchanged APK, API and source retain internal version `2.3.0`, Android versionCode 8. Original source commit: `395f721b54415b728aad25b3263ef3ad10b24350`. The [Version 1.5 prerelease](https://github.com/vaibhavvppuri/CalcForAll/releases/tag/v1.5) provides the development-signed candidate APK, optional local API, source, original failed evidence and publication provenance.

The candidate adds explicit ink token corrections, support for separate root strokes and dotted division, and offered photo equation systems. Review the recognized expression and answer. Poor photographs, connected handwriting and unsupported mathematical layouts can still require correction or typed input. Unsaved drafts remain memory-only and do not survive process death. Physical phones, physical styluses, TalkBack and 16 KB-page runtime execution remain unverified.

The public source archive is byte-identical to the historical candidate source archive. Every tracked source file and build input retains the original Git identity. The APK and API remain byte-identical to their original candidate packages. Release assets include PUBLICATION.json mapping original/public archive members and hashes, and public-verification.json distinguishing historical failed acceptance from publication-integrity checks. Evidence-only privacy changes are documented there; no failed result has been converted to a pass.

This added notice identifies publication provenance. Publishing did not rebuild the app, fix the timeout or rerun application tests. The original CI workflow is preserved; the publication commit uses [skip ci] to avoid redundant builds. This prerelease is not marked as latest stable. Read the release notes for the exact historical evidence and limits.
