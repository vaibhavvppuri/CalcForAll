package com.vaibhav.calc.core.tape

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.route.Candidate
import com.vaibhav.calc.core.route.Outcome

@JvmInline
value class EntryId(val n: Int) {
    override fun toString(): String = "#$n"
    val reference: String get() = "#$n"
}

/**
 * What a tape entry currently holds.
 *
 * **Seven states, none interchangeable** (Article 11). The distinctions that matter:
 *
 *  - [Failed] — *it ran and it broke.* `#3 = 1/0`.
 *  - [Blocked] — *it never ran; what it depends on gave it nothing to use.*
 *    `#5 = #3*2` when `#3` failed. **A blocked entry is not broken — it is
 *    waiting**, and it names which entry blocked it, so the UI can point at `#3`
 *    and grey `#5` rather than reddening both and hiding which one is actually
 *    wrong. Calling both "Failed" shows two errors and zero causes.
 *  - [Cycle] — *there is no computation to do.* Not a failure: no topological order
 *    exists, so the question has no answer rather than a broken one. It names its
 *    members, because "cycle" without "between which entries" is unactionable.
 *  - [Ambiguous] — the input has more than one reading. A state, not an error
 *    (D-016.5) — mirrors `RouteResult.Ambiguous` being a peer of Ok and Err.
 *  - [Stale] — a dependency changed and this has not recomputed yet. It keeps its
 *    [previous] value so the UI can show "was 5, updating" rather than blanking.
 *    (The [Tape] recomputes eagerly, so this is the async UI layer's state.)
 *  - [Empty] — there is nothing to compute. **Never** used to mean "something went
 *    wrong but I have no state for it" — that is the failure Article 11 names.
 */
sealed interface EntryValue {

    /**
     * It ran and produced [outcome] — which may be roots or a verdict, not only a
     * number. [value] is what *other entries can reference*: `#1*2` needs a value,
     * and an entry holding roots has none to give. Null there is a real answer to
     * a real question, not a swallowed error.
     */
    data class Computed(val outcome: Outcome) : EntryValue {
        val value: Value? get() = when (outcome) {
            is Outcome.Scalar -> outcome.value
            is Outcome.Stored -> outcome.value
            else -> null
        }
    }

    /** Nothing to compute. */
    object Empty : EntryValue

    /** A dependency changed; not yet recomputed. Retains what it last showed. */
    data class Stale(val previous: Value?) : EntryValue

    /** It ran and it broke. */
    data class Failed(val error: EvalError, val previous: Value? = null) : EntryValue

    /** It never ran: [by] gave it nothing to use. Waiting, not broken. */
    data class Blocked(val by: EntryId, val previous: Value? = null) : EntryValue {
        /**
         * The ONE user-facing wording for this state (D-036.8). "waiting on #n",
         * never the design mock's `why:'it failed'` — the measured blocking causes
         * include an Empty row and a successful solve with no referenceable value
         * (D-021), and "failed" would be a lie for both. Declared here so the UI
         * cannot grow a second, drifting copy of the sentence (Article 12).
         */
        val description: String get() = "waiting on ${by.reference}"
    }

    /** Part of a reference cycle — no computation exists. */
    data class Cycle(val members: List<EntryId>) : EntryValue

    /** More than one reading; the user must choose. */
    data class Ambiguous(val candidates: List<Candidate>) : EntryValue
}
