package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TapeLimitsSpec : StringSpec({
    fun src(text: String) = EditorState(Row(text.map(::Atom)))
    fun row(id: Int, text: String) = ArchivedEntry(EntryId(id), src(text), emptySet(), Settings())
    fun Tape.numAt(id: Int) = ((entry(EntryId(id))!!.value as EntryValue.Computed).value as RealValue).num

    "an 1800-term sum collects names and evaluates without recursive stack growth" {
        val tape = Tape()
        tape.append(src(List(1800) { "1" }.joinToString("+")))
        tape.numAt(1) shouldBe Num.of(1800)
        tape.append(src(List(1800) { "#1" }.joinToString("+")))
        tape.numAt(2) shouldBe Num.of(3_240_000)
    }

    "maximum-length linked history restores edits blocks and undoes as one transaction" {
        val size = Tape.MAX_ENTRIES
        val archive = TapeArchive(size + 1, (1..size).map { id -> row(id, if (id == 1) "1" else "#${id - 1}+1") })
        val tape = Tape()
        tape.restore(archive)
        tape.numAt(size) shouldBe Num.of(size)
        tape.edit(EntryId(1), src("2")).updatedCount shouldBe size
        tape.numAt(size) shouldBe Num.of(size + 1)
        tape.undo().shouldBeInstanceOf<UndoOutcome.Undone>()
        tape.numAt(size) shouldBe Num.of(size)
        tape.delete(EntryId(1))
        tape.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        tape.entry(EntryId(size))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(size - 1)
        tape.undo().shouldBeInstanceOf<UndoOutcome.Undone>()
        tape.numAt(size) shouldBe Num.of(size)
    }

    "SCC membership separates loops self loops and downstream bystanders" {
        val tape = Tape()
        tape.restore(TapeArchive(9, listOf(row(1,"#2"),row(2,"#1"),row(3,"#2+1"),
            row(4,"#5"),row(5,"#4"),row(6,"#5+1"),row(7,"#7"),row(8,"ans+1"))))
        for (id in listOf(1,2)) tape.entry(EntryId(id))!!.value.shouldBeInstanceOf<EntryValue.Cycle>().members shouldBe listOf(EntryId(1),EntryId(2))
        for (id in listOf(4,5)) tape.entry(EntryId(id))!!.value.shouldBeInstanceOf<EntryValue.Cycle>().members shouldBe listOf(EntryId(4),EntryId(5))
        tape.entry(EntryId(7))!!.value.shouldBeInstanceOf<EntryValue.Cycle>().members shouldBe listOf(EntryId(7))
        tape.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(2)
        tape.entry(EntryId(6))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(5)
        tape.entry(EntryId(8))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(7)
    }

    "one long cycle shares a single ordered membership list without recursion" {
        val size = 3000
        val tape = Tape()
        tape.restore(TapeArchive(size + 1, (1..size).map { row(it, "#${if(it == size) 1 else it + 1}") }))
        val first = tape.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        first.members shouldBe (1..size).map(::EntryId)
        val last = tape.entry(EntryId(size))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        (first.members === last.members) shouldBe true
        tape.edit(EntryId(1), src("#2+0")).updatedCount shouldBe 0
        tape.undo().shouldBeInstanceOf<UndoOutcome.Undone>().restored shouldBe 0
    }

    "cycle members stay in tape order rather than numeric identifier order" {
        val tape = Tape()
        tape.restore(TapeArchive(10, listOf(row(9,"#3"), row(3,"#7"), row(7,"#9"))))
        tape.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Cycle>().members shouldBe listOf(EntryId(9),EntryId(3),EntryId(7))
    }

    "SCC classification agrees with an independent reachability oracle on seeded graphs" {
        val random = kotlin.random.Random(20261110)
        repeat(60) {
            val size = 12
            val edges = (1..size).associateWith { (1..size).filter { random.nextInt(100) < 12 }.toSet() }
            val reachable = edges.mapValues { (_, first) ->
                val seen = mutableSetOf<Int>()
                val queue = ArrayDeque(first)
                while (queue.isNotEmpty()) {
                    val next = queue.removeFirst()
                    if (seen.add(next)) edges.getValue(next).forEach(queue::addLast)
                }
                seen
            }
            val tape = Tape()
            tape.restore(TapeArchive(size + 1, (1..size).map { id ->
                row(id, edges.getValue(id).takeIf { it.isNotEmpty() }?.joinToString("+") { "#$it" } ?: "1")
            }))
            for (id in 1..size) {
                val actual = tape.entry(EntryId(id))!!.value
                if (id in reachable.getValue(id)) {
                    val expected = (1..size).filter { it in reachable.getValue(id) && id in reachable.getValue(it) }.map(::EntryId)
                    actual.shouldBeInstanceOf<EntryValue.Cycle>().members shouldBe expected
                } else (actual is EntryValue.Cycle) shouldBe false
            }
        }
    }

    "undo keeps the most recent 100 whole transactions and IDs never rebound" {
        val tape = Tape()
        repeat(150) { tape.append(src("1")) }
        repeat(Tape.MAX_UNDO_TRANSACTIONS) { tape.undo().shouldBeInstanceOf<UndoOutcome.Undone>() }
        tape.entries().size shouldBe 50
        tape.undo() shouldBe UndoOutcome.NoMoreUndos
        repeat(Tape.MAX_UNDO_TRANSACTIONS) { tape.redo().shouldBeInstanceOf<UndoOutcome.Undone>() }
        tape.entries().size shouldBe 150
        tape.redo() shouldBe UndoOutcome.NoMoreUndos
        tape.undo()
        tape.append(src("2"))
        tape.entries().last().id shouldBe EntryId(151)
        tape.canRedo shouldBe false
    }

    "rejected source preserves visible state undo redo and next identifier" {
        val tape = Tape()
        tape.append(src("1")); tape.append(src("2")); tape.undo()
        val before = tape.archive()
        shouldThrow<IllegalArgumentException> { tape.append(src("1".repeat(Tape.MAX_SOURCE_NODES + 1))) }
        tape.archive() shouldBe before
        tape.canRedo shouldBe true
        tape.redo().shouldBeInstanceOf<UndoOutcome.Undone>()
        tape.numAt(2) shouldBe Num.TWO
        tape.append(src("3"))
        tape.entries().last().id shouldBe EntryId(3)
    }

    "full history refuses appends explicitly and permits deletion before another append" {
        val tape = Tape()
        tape.restore(TapeArchive(Tape.MAX_ENTRIES + 1, (1..Tape.MAX_ENTRIES).map { row(it, "1") }))
        shouldThrow<IllegalArgumentException> { tape.append(src("2")) }
        tape.entries().size shouldBe Tape.MAX_ENTRIES
        tape.canUndo shouldBe false
        tape.delete(EntryId(Tape.MAX_ENTRIES))
        tape.append(src("2"))
        tape.entries().size shouldBe Tape.MAX_ENTRIES
        tape.entries().last().id shouldBe EntryId(Tape.MAX_ENTRIES + 1)
    }

    "oversized or deeply nested archives are rejected atomically" {
        val tape = Tape()
        tape.append(src("7"))
        val before = tape.archive()
        val large = src("1".repeat(10_001))
        shouldThrow<IllegalArgumentException> {
            tape.restore(TapeArchive(11, (1..10).map { ArchivedEntry(EntryId(it),large,emptySet(),Settings()) }))
        }
        tape.archive() shouldBe before
        var nested = Row(listOf(Atom('1')))
        repeat(Tape.MAX_SOURCE_DEPTH + 1) { nested = Row(listOf(Paren(nested))) }
        shouldThrow<IllegalArgumentException> { tape.append(EditorState(nested)) }
        tape.archive() shouldBe before
        tape.canUndo shouldBe true
    }
})
