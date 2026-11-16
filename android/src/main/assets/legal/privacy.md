# CalcForAll privacy policy

Effective 10 November 2026. This policy describes the CalcForAll 1.0 preview source configuration and its optional local server.

## Calculations and local storage

CalcForAll performs arithmetic, equation solving, graph sampling, statistics, matrices and supported algebra on the device. The app has no account system, advertising integration, paid calculation API, or developer-operated calculation server.

The app saves calculation sources, linked-history identifiers, editor state, settings and theme in its private Android app directory. Answers are recomputed from saved sources when restored. Saving uses a bounded versioned format and atomic file replacement; a damaged saved file is reported rather than silently replaced. Ordinary saved state is retained until you clear history, select **Settings → Delete local data**, clear the app's data through Android, or uninstall it. Android's automatic app backup is disabled in the manifest.

Copying an expression or result places it on Android's system clipboard. Android and other apps allowed to read the clipboard may then access it. CalcForAll does not control copies you place elsewhere.

## Handwriting and photos

The experimental symbol pad processes finger/stylus strokes in the app. Strokes and candidate shapes are not uploaded; the reviewed expression becomes ordinary calculator input when you choose to use it.

Photo text recognition reads only the image you select through Android's document picker. It does not request broad photo-library, camera, microphone, contacts, or location access. The image is decoded for on-device recognition; the app does not add the image to saved calculation history. Only the reviewed expression is transferred to the calculator. A document-provider app may itself use a cloud service to supply a selected image; that provider is separate from CalcForAll.

Photo recognition uses Google's **bundled ML Kit Latin text-recognition SDK and model**. The model is supplied with the application, so the feature does not need a first-use model download. Google's documentation states that image/text inputs and recognition outputs are processed on-device and are not sent to Google. See [ML Kit's terms and privacy information](https://developers.google.com/ml-kit/terms) and its [bundled-model documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

Google's general ML Kit terms also describe SDK performance/utilization metrics and possible update contacts. This app removes the Android `INTERNET` and `ACCESS_NETWORK_STATE` permissions and contains no application code that uploads an image or calls a cloud calculation service. That prevents direct internet access by the app under Android's permission model. This statement is not a claim that every behavior of Android, Google Play services, the device manufacturer, or a chosen document provider has been independently audited. Google's SDK remains subject to [Google's Privacy Policy](https://policies.google.com/privacy) and its own terms. If a future build changes network permissions, SDKs or data handling, this policy must be reviewed before distribution.

## Optional local API

The JVM API is separate from the Android app and is not required for it to work. Its default listener is `127.0.0.1`, reachable from the same computer. Requests receive independent calculator instances; the server does not save formulas, registers, history or request bodies in a database. Its application error log omits submitted formulas and register values.

The legacy GET evaluation endpoint puts the formula in a URL. A browser or any proxy you configure may record that URL; use the versioned JSON POST endpoints for local integrations. A person who changes the server to listen on a network interface is responsible for authentication, transport security, logging and a deployment-specific privacy policy. No hosted service is included with this preview.

## Your controls and questions

You can use the calculator without photo recognition or the symbol pad. You can clear saved history or all app data from Settings. This does not erase clipboard copies, files you saved elsewhere, or records held by other apps or services you chose to use.

For questions about this source distribution, contact its distributor through the repository or the channel from which you obtained it. No public support address or hosted privacy-policy URL has been configured in this preview. A production distributor must supply its own current contact details and store disclosures.
