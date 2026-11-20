package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class PhotoOrientationSearchSpec : StringSpec({
    fun draft(text: String) = PhotoMathDraft(text,
        MathLayoutResult.Draft(text, listOf("Review"), true, emptyList()))

    "orientation search runs exactly three turns in geometric order without choosing an answer" {
        val calls = mutableListOf<Int>()
        val options = PhotoOrientationSearch.collect { turn -> calls += turn; draft(listOf("6", "9", "1/0")[turn - 1]) }
        calls shouldBe listOf(1, 2, 3)
        options.map { it.turns } shouldBe listOf(1, 2, 3)
        options.map { it.draft.expressionForReview } shouldBe listOf("6", "9", "1/0")
    }
    "complete rejected orientations remain manual review options without an automatic expression" {
        val options = PhotoOrientationSearch.collect { PhotoMathDraft("12", MathLayoutResult.Rejected("Incomplete fraction")) }
        options.size shouldBe 3
        options.all { it.draft.expressionForReview.isEmpty() && it.draft.completeRawText == "12" } shouldBe true
    }
    "incomplete truncated and empty orientations are never offered" {
        PhotoOrientationSearch.collect { turn -> when (turn) {
            1 -> draft("12").copy(incomplete = true)
            2 -> draft("12").copy(truncated = true)
            else -> draft("  ")
        } } shouldBe emptyList()
    }
    "cancellation stops further trials and does not return a partial-success list" {
        val calls = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        shouldThrow<CancellationException> {
            PhotoOrientationSearch.collect({ progress += it }) { turn ->
                calls += turn
                if (turn == 2) throw CancellationException("cancel")
                draft("12")
            }
        }
        calls shouldBe listOf(1, 2)
        progress shouldBe listOf(1, 2)
    }
    "recognition failure remains an error rather than hidden partial success" {
        val calls = mutableListOf<Int>()
        shouldThrow<IllegalStateException> {
            PhotoOrientationSearch.collect { turn -> calls += turn; if (turn == 2) error("native failure") else draft("12") }
        }
        calls shouldBe listOf(1, 2)
    }

    "progress announces each geometric turn immediately before its recognition starts" {
        val events = mutableListOf<String>()
        PhotoOrientationSearch.collect({ events += "turn$it" }) { turn -> events += "read$turn"; draft("12") }
        events shouldBe listOf("turn1", "read1", "turn2", "read2", "turn3", "read3")
    }

    "cancellation from a progress callback prevents that trial from starting" {
        var reads = 0
        shouldThrow<CancellationException> {
            PhotoOrientationSearch.collect({ throw CancellationException("cancel before trial") }) { reads++; draft("12") }
        }
        reads shouldBe 0
    }

    "completed options survive a later interruption without returning successful completion" {
        val snapshots = mutableListOf<List<PhotoOrientationOption>>()
        val reads = mutableListOf<Int>()
        shouldThrow<CancellationException> {
            PhotoOrientationSearch.collect(onOptionsReady = { snapshots += it }) { turn ->
                reads += turn
                if (turn == 2) throw CancellationException("interrupted")
                draft("6")
            }
        }
        reads shouldBe listOf(1, 2)
        snapshots.single().map { it.turns } shouldBe listOf(1)
        snapshots.single().single().draft.expressionForReview shouldBe "6"
    }

    "partial notifications exclude incomplete text but retain complete rejected text for manual review" {
        val snapshots = mutableListOf<List<PhotoOrientationOption>>()
        val result = PhotoOrientationSearch.collect(onOptionsReady = { snapshots += it }) { turn -> when (turn) {
            1 -> draft("12").copy(incomplete = true)
            2 -> PhotoMathDraft("12", MathLayoutResult.Rejected("Incomplete fraction"))
            else -> draft(" ")
        } }
        snapshots.size shouldBe 1
        result.map { it.turns } shouldBe listOf(2)
        snapshots.single().single().draft.expressionForReview shouldBe ""
    }

    "delivered snapshots do not change when later orientations complete" {
        val snapshots = mutableListOf<List<PhotoOrientationOption>>()
        PhotoOrientationSearch.collect(onOptionsReady = { snapshots += it }) { turn -> draft(turn.toString()) }
        snapshots.map { it.map { option -> option.turns } } shouldBe listOf(listOf(1), listOf(1, 2), listOf(1, 2, 3))
        (snapshots[0] !== snapshots[1] && snapshots[1] !== snapshots[2]) shouldBe true
    }

    "completed options are delivered in restored caller context after worker recognition" {
        val local = ThreadLocal<String>()
        val contexts = mutableListOf<String>()
        withContext(local.asContextElement("caller")) {
            PhotoOrientationSearch.collect(onOptionsReady = { contexts.add(requireNotNull(local.get())) }) {
                withContext(Dispatchers.Default + local.asContextElement("worker")) {
                    local.get() shouldBe "worker"
                    draft("12")
                }
            }
        }
        contexts shouldBe listOf("caller", "caller", "caller")
    }

    "a cancelled read cannot publish a late option even when it returns a draft" {
        supervisorScope {
            var notifications = 0
            lateinit var owner: Deferred<List<PhotoOrientationOption>>
            owner = async(start = CoroutineStart.LAZY) {
                PhotoOrientationSearch.collect(onOptionsReady = { notifications++ }) {
                    owner.cancel()
                    draft("12")
                }
            }
            owner.start(); owner.join()
            owner.isCancelled shouldBe true
            notifications shouldBe 0
        }
    }
})
