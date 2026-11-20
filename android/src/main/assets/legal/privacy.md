# CalcForAll privacy policy

Updated 14 November 2026. This policy describes the CalcForAll 2.2 source configuration and its optional local server.

## Calculations and local storage

CalcForAll performs arithmetic, equation solving, graph sampling, statistics, matrices and supported algebra on the device. The app has no account system, advertising integration, paid calculation API, or developer-operated calculation server.

The app saves calculation sources, linked-history identifiers, editor state, settings and theme in its private Android app directory. Answers are recomputed from saved sources when restored. Saving uses a bounded versioned format and atomic file replacement; a damaged saved file is reported rather than silently replaced. Ordinary saved state is retained until you clear history, select **Settings → Delete local data**, clear the app's data through Android, or uninstall it. Android's automatic app backup is disabled in the manifest.

Copying an expression or result places it on Android's system clipboard. Android and other apps allowed to read the clipboard may then access it. CalcForAll does not control copies you place elsewhere.

## Handwriting and photos

The handwriting pad processes finger/stylus strokes and geometric symbol candidates in the app. Strokes and candidate shapes are not uploaded. Recognized expressions are calculated in a private preview context; they do not change history or memory registers until you choose **Save reviewed calculation**. The reviewed expression then becomes ordinary saved calculator input. The app does not persist the original ink strokes.

Photo recognition reads an image you select through Android's image picker or take using **Take photo**. Taking a photo opens your device's camera app and grants it temporary access to one file in CalcForAll's private capture cache through Android FileProvider. CalcForAll does not request broad photo-library access or the camera, microphone, contacts, or location permissions. A captured file is deleted after reading or cancellation; abandoned captures older than one day are cleaned up when another capture is started. Android may also clear this cache. A camera or document-provider app may itself save a copy or use a cloud service; those apps are separate from CalcForAll.

The selected image's encoded bytes, crop, orientation, image treatment, bounded ink undo/redo history and editable recognition drafts are held in application memory. They survive closing and reopening a recognition tool and Android activity recreation while the app process remains alive. **Clear photo**, **Clear draft**, a successful reviewed save, or ending the app process releases the corresponding draft. Drafts are not written to saved calculation history and do not survive an app restart. Contrast and orientation assistance run on the device and show the transformed image before review. A failed replacement import keeps the previous draft. Cropping decodes the selected region from the original image to retain detail; images over the documented size limits are refused. Clearing Android app data or uninstalling also removes private capture files.

A visible, editable transcription feeds an automatic calculation preview. **Save reviewed calculation** reports success only after the calculator snapshot is written and read back locally. A bounded rollback journal protects the previous snapshot during that operation; it contains ordinary saved calculator inputs and settings, never the image or ink. A failed save keeps the recognition draft available and reports the storage problem.

Photo recognition uses Google's **bundled ML Kit Latin text-recognition SDK and model**. The model is supplied with the application, so the feature does not need a first-use model download. Google's documentation states that image/text inputs and recognition outputs are processed on-device and are not sent to Google. See [ML Kit's terms and privacy information](https://developers.google.com/ml-kit/terms) and its [bundled-model documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

Google's general ML Kit terms also describe SDK performance/utilization metrics and possible update contacts. This app removes the Android `INTERNET` and `ACCESS_NETWORK_STATE` permissions and contains no application code that uploads an image or calls a cloud calculation service. That prevents direct internet access by the app under Android's permission model. This statement is not a claim that every behavior of Android, Google Play services, the device manufacturer, or a chosen document provider has been independently audited. Google's SDK remains subject to [Google's Privacy Policy](https://policies.google.com/privacy) and its own terms. If a future build changes network permissions, SDKs or data handling, this policy must be reviewed before distribution.

Developers can explicitly enable the local Android `CalcPhoto` debug log for recognition timing. It records image dimensions, rotation numbers, processing stages and elapsed times; it does not record image pixels, recognized text, paths, URIs or exception messages. This diagnostic is disabled by default and is not uploaded by the app.

## Optional local API

The JVM API is separate from the Android app and is not required for it to work. Its default listener is `127.0.0.1`, reachable from the same computer. Requests receive independent calculator instances; the server does not save formulas, registers, history or request bodies in a database. Its application error log omits submitted formulas and register values.

The legacy GET evaluation endpoint puts the formula in a URL. A browser or any proxy you configure may record that URL; use the versioned JSON POST endpoints for local integrations. A person who changes the server to listen on a network interface is responsible for authentication, transport security, logging and a deployment-specific privacy policy. No hosted service is included with this preview.

## Your controls and questions

You can use the calculator without photo recognition or the symbol pad. You can clear saved history or all app data from Settings. This does not erase clipboard copies, files you saved elsewhere, or records held by other apps or services you chose to use.

For questions about this source distribution, contact its distributor through the repository or the channel from which you obtained it. No public support address or hosted privacy-policy URL has been configured in this preview. A production distributor must supply its own current contact details and store disclosures.
