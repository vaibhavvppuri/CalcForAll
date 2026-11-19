package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

class NativeRecognitionGateSpec : StringSpec({
    "cancelling active recognition returns promptly but retains the slot until native completion" {
        withTimeout(5000) { supervisorScope {
            val gate = NativeRecognitionGate()
            lateinit var finish: (Result<Int>) -> Unit
            val first = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { finish = it } }
            first.cancelAndJoin()
            var started = false
            val second = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { started = true; it(Result.success(2)) } }
            started shouldBe false
            finish(Result.success(1))
            second.await() shouldBe 2
        } }
    }

    "a cancelled waiter never starts native recognition or releases the active slot" {
        withTimeout(5000) { supervisorScope {
            val gate = NativeRecognitionGate()
            lateinit var finish: (Result<Int>) -> Unit
            val first = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { finish = it } }
            var cancelledStarted = false
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { cancelledStarted = true; it(Result.success(9)) } }
            cancelled.cancelAndJoin()
            var thirdStarted = false
            val third = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { thirdStarted = true; it(Result.success(3)) } }
            thirdStarted shouldBe false
            finish(Result.success(1))
            first.await() shouldBe 1
            third.await() shouldBe 3
            cancelledStarted shouldBe false
        } }
    }

    "a duplicate completion cannot release another recognition's slot" {
        withTimeout(5000) { supervisorScope {
            val gate = NativeRecognitionGate()
            lateinit var firstFinish: (Result<Int>) -> Unit
            val first = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { firstFinish = it } }
            firstFinish(Result.success(1)); first.await() shouldBe 1
            lateinit var secondFinish: (Result<Int>) -> Unit
            val second = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { secondFinish = it } }
            firstFinish(Result.success(99))
            var thirdStarted = false
            val third = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitCompletion<Int> { thirdStarted = true; it(Result.success(3)) } }
            thirdStarted shouldBe false
            secondFinish(Result.success(2)); second.await() shouldBe 2
            third.await() shouldBe 3
        } }
    }

    "synchronous startup and native task failures release the gate for a retry" {
        withTimeout(5000) {
            val gate = NativeRecognitionGate()
            runCatching { gate.awaitCompletion<Int> { throw IllegalStateException("start failed") } }
                .exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
            runCatching { gate.awaitCompletion<Int> { it(Result.failure(IllegalArgumentException("native failed"))) } }
                .exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            gate.awaitCompletion<Int> { it(Result.success(4)) } shouldBe 4
        }
    }
})
