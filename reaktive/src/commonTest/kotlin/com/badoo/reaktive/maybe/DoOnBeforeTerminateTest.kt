package com.badoo.reaktive.maybe

import com.badoo.reaktive.test.maybe.DefaultMaybeObserver
import com.badoo.reaktive.test.maybe.TestMaybe
import com.badoo.reaktive.test.utils.SafeMutableList
import kotlin.test.Test
import kotlin.test.assertEquals

class DoOnBeforeTerminateTest
    : MaybeToMaybeTests by MaybeToMaybeTests<Unit>({ doOnBeforeTerminate {} }) {

    private val upstream = TestMaybe<Nothing>()
    private val callOrder = SafeMutableList<String>()

    @Test
    fun calls_action_before_completion() {

        upstream
            .doOnBeforeTerminate {
                callOrder += "action"
            }
            .subscribe(
                object : DefaultMaybeObserver<Nothing> {
                    override fun onComplete() {
                        callOrder += "onComplete"
                    }
                }
            )

        upstream.onComplete()

        assertEquals(listOf("action", "onComplete"), callOrder.items)
    }

    @Test
    fun calls_action_before_failing() {
        val callOrder = SafeMutableList<String>()
        val exception = Exception()

        upstream
            .doOnBeforeTerminate {
                callOrder += "action"
            }
            .subscribe(
                object : DefaultMaybeObserver<Nothing> {
                    override fun onError(error: Throwable) {
                        callOrder += "onError"
                    }
                }
            )

        upstream.onError(exception)

        assertEquals(listOf("action", "onError"), callOrder.items)
    }
}