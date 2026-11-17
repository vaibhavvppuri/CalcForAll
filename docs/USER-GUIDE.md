# Using CalcForAll

CalcForAll is an offline Android calculator. This guide describes the **1.1.0-preview Android update**; the optional, unchanged local API retains version 1.0.0-preview. Install the supplied APK by opening it on your phone and following Android's installation prompts. The compatibility target is Android 6.0 or later; the [verification record](VERIFICATION.md) lists what was actually tested. The APK is a development build, not a Play Store release.

The installed app needs no account, subscription, API key or local server. Calculations and the bundled photo-text model run on the device. Building the app from source initially requires downloading development dependencies; using the installed calculator does not.

## Enter and calculate

Tap the keypad, then **=**. For longer expressions, tap **Edit**, type or paste, choose **Use expression**, then press **=**. **fn** opens searchable scientific functions. The arrow buttons move the cursor through structured expressions; **⌫** deletes, and holding it repeats deletion. A structure may be selected before a further press deletes it. **Undo entry** and **Redo** are in Edit.

Flick upward on these keys to insert a structure or constant:

| Key | Upward flick |
| --- | --- |
| ÷ | Square root |
| × | Power |
| − | Fraction |
| . | π |

The functions sheet also offers fractions, powers and roots without gestures. For plain text, use `sqrt(8)`, `x^2`, `1/3` and `pi`. Multiplication may be written as `*`; parentheses make grouping explicit.

| Try in Edit | Result |
| --- | --- |
| `0.1+0.2` | Exactly 0.3 |
| `1/3+1/6` | 1/2, or 0.5 in decimal form |
| `sqrt(8)` | 2√2 |
| `sin(30)` with **DEG** selected | 0.5 |
| `x^2-5x+6=0` | x = 2, 3 |
| `x+y=5;x-y=1` | x = 3; y = 2 |
| `5 km+300 m` | 5.3 km |

The angle chip cycles **DEG → RAD → GRAD** for new calculations. Saved history retains its original angle mode. Equations use x, y or z as unknowns; unsupported equation types produce an explanation.

Main entry supports unit arithmetic. To convert a quantity into a chosen unit, use **Tools → Units**; `to` is not an inline conversion operator.

## Read the answer

Fractions and supported roots/constants stay exact where possible. **S⇔D**, when available, switches between fraction and decimal presentation. **≈** identifies an approximation or a rounded decimal view; changing displayed digits does not create extra accuracy. Numerical tools may insert `approx(...)` to retain that marker when you reuse an estimate.

Tap the answer to copy it. Copied estimates retain `approx(...)`; exact fractions retain forms such as `1/3`. Clipboard text is calculator syntax and may need adapting for an app that expects only plain digits. **Steps** opens available working; not every operation supplies a full derivation. An error is a refusal to produce a value, not zero.

## History, links and memory

Open **History** to search calculations:

- **Reuse** copies an expression for a new calculation.
- **Link #n** inserts a live reference to that row's value. For example, link a row containing 5 and append `*2` to get 10.
- **Edit** changes an existing row. Choose **Use expression**, then **=** to save it and update dependent rows.
- **Delete** removes a row; dependent links can become unavailable. **Clear** clears history after confirmation. Use the **UNDO** notice promptly if needed.

**Ans** inserts the previous calculation's value when available. To store a register, enter `5→A`, then evaluate `A+2` to get 7. Registers are uppercase **A–F and M**. x/y/z are equation and graph unknowns, not storage registers. Reuse copies the source; any existing `#n` references remain links.

## Tools

Open **Tools**, choose a tool, fill its fields and press its operation button.

| Tool | How to enter data | Example |
| --- | --- | --- |
| Units | Value, From unit, To unit | 32, `degF`, `degC` → 0 °C |
| Matrices | Grid, or commas between cells and semicolons/newlines between rows; up to 6 × 6 | Determinant of `1,2;3,4` → −2 |
| Statistics | Values separated by commas, spaces or newlines; use ungrouped numbers | `1,2,3` → mean 2; sample variance 1 |
| Linear regression | Matching X and Y lists | X `0,1,2`, Y `1,3,5` → y = 2x + 1 |
| Number bases | Integer text, source base, destination base; bases 2–36 | `255`, 10, 16 → FF |
| Constants | Search a name, then choose its **Use** action | Physical constants retain their units; measured constants show uncertainty |

Matrix operations include inverse, transpose, rank, add/subtract, multiply and scale. Statistics distinguishes population and sample measures; an unavailable sample variance or correlation is shown as undefined.

For a length conversion, enter value `5`, From `km`, To `m` in **Units** to obtain 5000 m.

**Algebra & calculus** supports bounded simplify, expand, factor, differentiate, integrate and definite integration. Try differentiating `x^3` to obtain `3*x^2`, or integrate `x^2` from 0 to 1 to obtain approximately 1/3. Select x, y or z as the variable.

Calculus and its bound expressions use **radians**, including bounds such as `asin(1)`. Read the displayed domain conditions. Results that require those conditions stay in the tool: automatic insertion is withheld so the qualifications are not lost. Reusable symbolic expressions carry RAD mode; an antiderivative's insertion notice chooses C = 0. Definite integrals carry an approximate marker and estimated error. Unsupported forms, singular intervals and exhausted budgets return explanations. See [symbolic scope](SYMBOLIC.md) for the supported subset; this is not a general CAS.

## Graphs

Open **Graph**, enter up to three functions of x, one per line, then **Plot**. Try `x^2-4`: tracing x = 3 gives y = 5. Drag to pan, pinch or use **Zoom +/−**, and use **Range** for explicit bounds. **Roots (1)** analyzes the first curve; **Intersect (1, 2)** compares the first two. **Trace x** evaluates a chosen point.

For a surface, open **Tools → 3D surface**, enter an expression in x and y, then **Plot**. With `x^2+y^2`, tracing x = 1 and y = 2 gives z = 5. **Domain** edits the x/y bounds; press Plot again afterward. Drag or use rotate/tilt buttons, pinch to zoom, and use **Reset view** to restore the view. Axes scale independently to fit.

Both views show the current angle mode and use a snapshot of registers, Ans and resolved history links. Undefined points and unevaluated points are reported separately. Finite samples can miss narrow features; numerical root/intersection candidates and visual curves do not prove completeness. The 3D view supports real height fields z = f(x,y), not arbitrary implicit surfaces.

## Handwriting and photo text

**Tools → Handwriting** is experimental. A stylus is optional; **Stylus only** ignores finger strokes. Choose a mode:

- **One symbol:** draw one digit or operator, then choose a suggested match to build the expression one symbol at a time.
- **Spaced line:** write clearly separated symbols left to right at roughly the same height, then tap **Read line**. The local matcher proposes an **Unverified draft** in **Review expression**. Numbered glyph buttons let you inspect alternative matches; choosing an alternative rebuilds the draft, so make free-text corrections afterward. Review every symbol before **Use reviewed expression** and **=**.

Use **Undo stroke** or **Clear pad** to correct the ink. Line mode supports digits, decimal points, `+`, `−`, `×`, `/`, `=`, parentheses, `^`, `x` and `π`; ambiguous `x`/`×` readings remain review choices. Write powers inline using `^`, not raised superscripts, and fractions inline using `/`, not stacked bars. The draft is limited to 32 glyphs, 64 strokes and 12,000 points, with at most four strokes per glyph; the review field holds at most 2,048 characters. Detected stacked/multi-line layouts, unclear matches and unsupported symbols can be refused. This geometry matcher is not a trained whole-equation handwriting recognizer and cannot certify that an accepted draft is correct.

**Tools → Photo text** lets you **Choose an image**, rotate it and read it again. In 1.1, when multiple lines are detected, numbered buttons offer up to 24 nonblank lines; choose one to place that line in the editable review field. **Restore full recognized text** returns to the original OCR text after a selection or edit. Keep the full text when you intend a supported system of equations on separate lines. Review and correct it before **Use reviewed expression**.

Line selection preserves what OCR read, including ambiguous letters/digits, superscripts and fraction characters; it does not silently repair them or infer two-dimensional layout. The editable text is capped at 8,192 characters. This is bundled on-device text recognition, not a general photo-math solver; handwriting and word problems may be read incorrectly. The image is not saved in calculation history.

## Settings and local data

**Settings** controls theme, angle mode, digit grouping, Western/Devanagari numerals, notation, exact-answer presentation, displayed digits and complex numbers. Enable complex numbers to evaluate examples such as `sqrt(-4)` → 2i.

History and the editor are saved locally. **Settings → Delete local data** permanently deletes the saved session/history after confirmation; it is different from the undoable History Clear action. **Privacy policy & open-source licenses** opens the bundled notices. The app has no Internet permission; see [privacy details](PRIVACY.md) and the [capability matrix](CAPABILITY-MATRIX.md) for the preview's limits.
