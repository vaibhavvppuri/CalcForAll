# Using CalcForAll

CalcForAll is an offline Android calculator. This guide describes **CalcForAll 2.1.0** for Android and its optional local API. Final release-check status is tracked in the linked verification record. Install the supplied APK by opening it on your phone and following Android's installation prompts. The compatibility target is Android 6.0 or later; the [verification record](VERIFICATION.md) lists what was actually tested. The APK is a development build, not a Play Store release.

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

A power written on a unit belongs to the unit: `5 m^2` is five square metres and `9.8 m/s^2` is an acceleration. To raise a whole quantity, bracket it: `(5 m)^2` is 25 m². Results with more than one unit below the line are written with brackets, for example `3 kg/(m·s)`, and can be typed back in exactly as shown.

Number words are different: `lakh`, `crore` and `dozen` are part of the number, so `5 lakh^2` is 500000 squared. The ampere is written `amp` (the letter `A` is a memory register), and `mol` and `cd` are units too, so a constant such as the elementary charge, shown in `s·amp`, can also be typed back.

## Read the answer

Fractions and supported roots/constants stay exact where possible. **S⇔D**, when available, switches between fraction and decimal presentation. **≈** identifies an approximation or a rounded decimal view; changing displayed digits does not create extra accuracy. Numerical tools may insert `approx(...)` to retain that marker when you reuse an estimate.

Tap the answer to copy it. Copied estimates retain `approx(...)`; exact fractions retain forms such as `1/3`. Clipboard text is calculator syntax and may need adapting for an app that expects only plain digits. **Steps** opens available working; not every operation supplies a full derivation. An error is a refusal to produce a value, not zero.

A long answer can run past the answer area. The area scrolls, and a small **▾ more below** (or **▴ more above**) appears while there is more of the answer to see.

### Estimates, whole numbers and true/false

A decimal estimate has either a known error bound or unknown accuracy. When a known error affects the displayed digits, the answer shows a **±** radius. That interval includes the rounding accumulated during supported calculations. Unknown accuracy is not treated as zero error, and agreement between two calculations is not used to guess missing digits.

- **Whole parts and remainders** (`floor`, `ceil`, `int`, `round`, `frac`, `mod`) are answered only when the available bounds establish the required integer. The argument is evaluated once at up to fifty digits; it is never snapped to a nearby integer. Supported written identities can give exact answers: `floor(ln(125)/ln(5))` is 3 and `mod(8*(1+sqrt(2)),1+sqrt(2))` is 0. `floor(tanh(50))` is 0, while `floor(tanh(60))` can be refused because the available interval reaches 1.
- **True or false.** Supported exact identities are decided directly: `sin(20)^2+cos(20)^2=1` and `sqrt(8)=2*sqrt(2)` are True. Disjoint error intervals establish a difference, so `ln(2)=0.693` is False. Overlapping or unknown intervals can leave a statement undecided; equal displayed digits do not prove equality. General algebraic identities outside the supported rules may also remain undecided.
- **Equations.** A written repeated factor such as `(x-e)^2=0` has the root e once. Expanding an irrational expression can lose the dependency needed to prove a repeated root: `x^2-2*sqrt(e)*x+e=0` is refused when its discriminant's interval includes zero. The same rule prevents declaring an uncertain determinant singular. Close distinct roots are kept apart when established; `(x-10^18)^2=sin(20)` has two, even if a short display makes them look alike. Higher-degree polynomial solves have a ten-second work allowance; a slow or difficult calculation can still stop with an explanation instead of an incomplete root list.

Exact fractions and supported symbolic forms remain exact within the work limits. Cancellation can still lose nominal digits after an expression becomes decimal: `10^49+sqrt(2)-10^49` has nominal value 1 **with an error bound**, not an exact answer of 1. Its floor is refused. Writing `10^49-10^49+sqrt(2)` preserves the exact square root. Some exact forms can be expanded further within the budget, so `floor(pi*10^50)` is available and `sqrt(10^100+1)-10^50` remains near 5×10⁻⁵¹ rather than becoming zero.

**Ans** and registers retain the original error metadata. Storing a result does not certify its digits, and reusing it cannot recover accuracy already lost. Statistics, probability, finance and other tools that require known point inputs may refuse uncertain observations or rates. See [numeric limits](NUMERIC-LIMITS.md) for the detailed supported cases.

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

Matrix operations include inverse, transpose, rank, add/subtract, multiply and scale. **Solve system** finds X in A·X = B — put the coefficients in A and the constants in B as a column, one row per equation, for up to six unknowns (`2,1;1,3` with `3;5` gives 0.8 and 1.4) — and **Eigenvalues** lists a square matrix's distinct eigenvalues, exact where they are rational or come from a 2 × 2 matrix and marked ≈ otherwise. Eigenvectors are not computed. Statistics distinguishes population and sample measures; an unavailable sample variance or correlation is shown as undefined.

For a length conversion, enter value `5`, From `km`, To `m` in **Units** to obtain 5000 m.

**Algebra & calculus** supports bounded simplify, expand, factor, differentiate, integrate and definite integration. Try differentiating `x^3` to obtain `3*x^2`, or integrate `x^2` from 0 to 1 to obtain approximately 1/3. Select x, y or z as the variable.

### Everyday and study tools

The same list contains everyday and study tools; Money, Dates, Everyday and Time come first. Each one opens with working example values, so pressing its button once shows what it does.

| Tool | What it does | Example |
| --- | --- | --- |
| Money | Loan EMI and its year-by-year repayment schedule, compound and simple interest, SIP, add or remove GST, discount, percent change, split a bill, the loan a payment can afford, a savings goal; inflation and an amount in words for a cheque | Loan 10,00,000 at 8.5% for 240 months → 8,678.23 a month; 123456.50 → Rupees one lakh twenty-three thousand four hundred and fifty-six and paise fifty only |
| Dates | Days between two dates, add or subtract days, age and next birthday | 2026-01-01 to 12/11/2026 → 315 days (45 weeks) |
| Everyday | Percent of an amount, unit-price comparison of two packs, fuel cost of a trip, proportion (rule of three), BMI; sharing an amount in a ratio and a number in words | 120 for 500 against 200 for 1000 → B is cheaper by 16.67% per unit; 1200 in the ratio 2:3:5 → 240, 360, 600 |
| Time | Add or subtract durations, time between two clock times, seconds as days, hours, minutes | 1:45:30 + 0:50:45 → 2:36:15 |
| Equation solver | **Solve**: supported polynomial equations in x up to degree 8. **Numeric on a range**: supported continuous real expressions between two values | `x^3-6x^2+11x-6=0` → x = 1, 2, 3; `cos(x)=x` in radians → x ≈ 0.7390851 |
| Table & series | Table of f(x), the sum Σ or the product Π over whole x | Σ `x^2` for x = 1…10 → 385 |
| Number theory | Prime factors, GCD and LCM, divisors, primality (up to 10^14) | 360 = 2^3 × 3^2 × 5 |
| Vectors | Dot and cross product, length, angle, projection | (1,2,3)·(4,5,6) = 32; length of (3,4) = 5 |
| Probability | Binomial (exact fractions), Poisson, normal | 3 heads in 10 fair flips → 15/128 |
| Curve fitting | Quadratic, exponential, power and logarithmic least-squares fits | Points on y = x² + x + 1 → that curve, r² = 1 |
| Series & limits | Taylor polynomial about a point; numerical limit at a point or at `inf` | `sin(x)` about 0, order 5 → `x - x^3/6 + x^5/120` |
| Programmer | AND, OR, XOR, NOT and shifts, shown in decimal, hexadecimal, binary and octal | `1100` AND `1010` in base 2 → 1000 |
| Geometry | Circle, rectangle, triangle from three sides, right triangle, sphere, cylinder, cone — exact in π and square roots, with the decimal beside it | Circle of radius 5 → area 25π (≈ 78.5398163397) |
| Sequences | Any term and the sum of an arithmetic or geometric progression; the sum to infinity when the ratio is between −1 and 1 | 3, 7, 11, … → term 10 is 39, the first 10 add to 210 |
| Business | Margin and markup, the price for a target margin, break-even units, yearly growth rate | Cost 80, price 100 → margin 20% of the price, markup 25% of the cost |
| Data | Quartiles, interquartile range and mode | 1 to 8 → Q1 2.5, median 4.5, Q3 6.5 |

Things worth knowing:

- **Money** works on plain numbers in any currency. Every rate is the one you type; the app looks nothing up. Results are rounded to two decimals, half up, and follow your digit-grouping and numeral settings. They are arithmetic, not financial advice.
- **Dates** can be written `2026-11-12` or day-first as `12/11/2026`. A two-digit year is refused rather than guessed. A 29 February birthday is counted on 1 March in other years, which is also the day the age goes up.
- Estimated answers are marked ≈ and may show a known ± error. Money displays fixed two-decimal amounts; those displayed cents need not be mathematically exact. Reusing a rounded amount preserves the available error information.
- Input size, number magnitude and tool-specific work/time budgets are bounded. Exhausting a budget produces an explanation rather than a partial answer presented as complete.
- You can type a supported polynomial equation into the calculator and press **=**. Degree is limited to 8, with exact rational coefficients or separately supported written factors. Rational roots and supported quadratic factors stay exact (`x^3=8` gives 2 and −1 ± √3 i); numerical roots are marked ≈. Unsupported coefficients or unresolved root structure produce an explanation, not a silently shortened list.
- **Numeric on a range** samples between the two values and checks candidate residuals against established bounds. It can miss solutions that lie very close together or only touch zero, and can stop at its time limit. Its supported continuity subset excludes some expressions that can still be plotted or traced, such as a variable-dependent denominator. Trigonometry follows the calculator's angle mode.
- A **Taylor polynomial** approximates a function near one point; it is not the function. A **limit** here is an estimate from values ever closer to the point — never a proof. If the two sides disagree you see both; a function that keeps oscillating is reported as not settling; and if the calculator cannot evaluate the function close enough to the point, it says exactly that — a limit of the calculator, not a statement about your function. Both use radians.
- **Is it prime?** is checked by exact division, so the answer is certain, which is why it stops at 10^14.
- **Quartiles** have more than one textbook definition. This app uses the median of halves: sort the data, split it into a lower and an upper half (with an odd count the middle value belongs to neither), and take each half's median. Spreadsheet quartile functions interpolate and can give slightly different numbers.
- **Margin** and **markup** are different things: margin is profit as a share of the selling price, markup is profit as a share of the cost.
- **Break-even** rounds up only when every value in the quotient's established interval implies the same unit count. An interval crossing an integer is refused instead of guessed; supported square roots can still be exact quantities.
- On the keypad, the power key after `5 lakh` raises the whole five lakh, the same as typing `5 lakh^2`.
- A fraction or a power made with the keypad counts as one value. Whatever you put after it applies to all of it: ½ followed by `lakh` is half a lakh, and ½ followed by `%` is half a percent.
- In a field labelled with a percent sign you can type `18` or `18%`. Both mean eighteen percent.
- **Numbers in words** are given both ways: 1234567 is "twelve lakh thirty-four thousand five hundred and sixty-seven" and "one million two hundred and thirty-four thousand five hundred and sixty-seven". An **amount in words** is written the way a cheque is, in rupees and paise; an amount with a third decimal place is refused, because a cheque amount cannot be rounded for you.
- **Inflation** uses the one rate you enter for every year. It is arithmetic on your assumption, not a forecast.
- Write a **ratio** with colons, as in `2:3:5`. Each part can be a number or an expression (`1.5 : 2.5`, `1/2 : 1/3`), but write numbers without digit separators: `1500`, not `1,500`.
- Your phone keyboard's autocorrect and automatic capitals are switched off in every field of the app, so what you type is what the calculator reads.
- **Use in calculator** puts the answer on the entry line. A fraction, a negative number or a multiple of π or e arrives in brackets, so whatever you type next applies to the whole answer: `(15/128)^2`, not `15/128^2`.
- A **geometric sequence** keeps exact arithmetic where supported and otherwise returns a bounded or unknown estimate. An infinite sum requires the entire established ratio interval to lie strictly between −1 and 1.
- **BMI** is weight in kilograms divided by the square of height in metres, shown with the World Health Organization's general adult bands. It is a screening number, not a diagnosis, and it is not adjusted for age, build or population.
- **Time** works in hours, minutes and seconds written `H:MM` or `H:MM:SS`. Between two clock times, a second time earlier than the first is taken as the next day, and the answer says so. It knows nothing about time zones or dates.

Calculus and its bound expressions use **radians**, including bounds such as `asin(1)`. Read the displayed domain conditions. Results that require those conditions stay in the tool: automatic insertion is withheld so the qualifications are not lost. Reusable symbolic expressions carry RAD mode; an antiderivative's insertion notice chooses C = 0. Definite integrals carry an approximate marker and estimated error. Unsupported forms, singular intervals and exhausted budgets return explanations. See [symbolic scope](SYMBOLIC.md) for the supported subset; this is not a general CAS.

## Graphs

Open **Graph**, enter up to three functions of x, one per line, then **Plot**. Try `x^2-4`: tracing x = 3 gives y = 5. Drag to pan, pinch or use **Zoom +/−**, and use **Range** for explicit bounds. **Roots (1)** analyzes the first curve; **Intersect (1, 2)** compares the first two. **Trace x** evaluates a chosen point.

For a surface, open **Tools → 3D surface**, enter an expression in x and y, then **Plot**. With `x^2+y^2`, tracing x = 1 and y = 2 gives z = 5. **Domain** edits the x/y bounds; press Plot again afterward. Drag or use rotate/tilt buttons, pinch to zoom, and use **Reset view** to restore the view. Axes scale independently to fit.

Both views show the current angle mode and use a snapshot of registers, Ans and resolved history links. Undefined points and unevaluated points are reported separately. Finite samples can miss narrow features; numerical root/intersection candidates and visual curves do not prove completeness. The 3D view supports real height fields z = f(x,y), not arbitrary implicit surfaces.

## Write and photograph a calculation

Open **Tools → Write & calculate**. Draw with a finger or stylus; **Stylus only** ignores finger strokes. The draft is calculated automatically after you lift the pen. Choose a mode:

- **One symbol:** draw one digit or operator, then choose a suggested match to build the expression one symbol at a time.
- **Spaced line:** write clearly separated symbols from left to right at roughly the same height. Use `^` for powers and `/` for fractions.
- **Math layout:** the default mode also interprets raised powers, stacked fraction bars, square roots and equations. Keep symbols separate and write one expression at a time.

Use **Undo stroke**, **Redo stroke**, **Clear pad** or **Eraser** to change the ink. Numbered symbol buttons offer alternative matches; choosing one rebuilds the draft, so make free-text corrections afterward. New ink replaces the draft in line/layout mode. The local templates cover digits, common operators, parentheses, square roots, π and x/y/z. Math layout accepts at most 64 strokes, 12,000 points and 64 tokens; each stroke has a 2,000-point limit. An oversized stroke is discarded as a whole, with an explanation. Automatic calculation accepts at most 2,048 characters. This is bounded template matching: unclear symbols, cursive words and complex layouts may require typing.

Open **Tools → Photo calculator** and choose **Take photo** or **Choose image**. The app reads it offline and calculates the proposed expression automatically. Use **Crop expression** to select a smaller region, **Rotate 90°** to correct orientation, or **Read again**. A crop is decoded from the original image, retaining detail that can be lost in the whole-image preview. **Restore original image** undoes image adjustments. The encoded image is limited to 20 MiB, 100 megapixels and 50,000 pixels on either edge; a processed crop is limited to a 2,048-pixel edge. Unsupported region-decoding formats give an explanation. **Cancel reading** stops waiting; the next read waits for any underlying native OCR task to finish safely.

Ink stays on a fixed-proportion writing surface when the screen changes size. Erasing follows the whole visible stroke segment, including fast strokes with few captured points. Percent signs (`%`) and factorial marks (`!`) have geometric candidates; separate the percent loops and slash, and put a factorial dot below its stem. These are reviewable candidates, not a guarantee of recognition accuracy.

Closing and reopening either tool preserves its draft while the app process remains alive, including manual transcription edits. Activity recreation also retains it. **Clear draft** or **Clear photo** discards it; a successful reviewed save clears the saved draft. Draft images and ink are memory-only and do not survive an app restart. During a save the draft controls are locked. A storage failure leaves the draft available and restores the prior calculator state; follow any recovery message before retrying. Save succeeds only after the new local snapshot has been written and read back.

The spatial reader proposes fractions, powers and root scope from the detected symbol positions. **Use raw recognized text** and **Choose a raw text line** offer the uncorrected OCR text when it is complete. Keep multiple equations on separate lines for a supported system. The editor can hold 8,192 characters, but automatic calculation asks you to reduce anything longer than 2,048. An oversized page is refused rather than calculating a truncated prefix.

Sometimes **Possible corrections** offers an alternative, such as a square-root sign read as V or a duplicated OCR fragment. The explanation states the proposed change. Select it only after checking the image; the original transcription is never silently rewritten. You can also edit the expression directly.

Both screens show the current angle mode, an editable transcription and its result or error. Check every symbol, especially minus signs, x/×, powers and fraction bars. **Save reviewed calculation** adds a new history row; previews alone leave Ans, registers and history unchanged. Recognition can be wrong even when the expression is valid. Word problems, general cursive handwriting and unrestricted page layouts are outside this release. Photos stay on the device and are not stored in calculation history; temporary camera files are deleted after reading or cancellation.

## Estimated answers and reuse

Exact fractions and supported symbolic values remain exact. Estimated results are marked `≈`; when a known error affects the displayed digits, a `±` radius is also shown. An unknown error is not treated as zero. Rounding or a domain decision may be refused when the available bounds cannot establish an answer.

**Use in calculator** and result copying preserve estimate metadata through `approx(value)` for unknown accuracy or `approx(value,error)` for a bounded value. `approx(value,0)` explicitly treats the supplied decimal as a known point; it does not remove uncertainty already present in an expression. A measured physical constant's published standard uncertainty is a statistical descriptor, not a guaranteed arithmetic bound. Numeric integration, sampled limits and numeric complex roots remain estimates with unknown rigorous accuracy.

## Settings and local data

**Settings** controls theme, angle mode, digit grouping, Western/Devanagari numerals, notation, exact-answer presentation, displayed digits and complex numbers. Enable complex numbers to evaluate examples such as `sqrt(-4)` → 2i.

History and the editor are saved locally. **Settings → Delete local data** permanently deletes the saved session/history after confirmation; it is different from the undoable History Clear action. **Privacy policy & open-source licenses** opens the bundled notices. The app has no Internet permission; see [privacy details](PRIVACY.md) and the [capability matrix](CAPABILITY-MATRIX.md) for the supported scope and remaining verification limits.
