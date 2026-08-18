package com.tvproxy.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * M0 test-rig smoke test: proves kotlinx-coroutines-test (runTest) + Turbine
 * are wired and working. From M1 onward this rig is used to test repositories
 * and use cases that expose Flow.
 */
class FlowRigTest {

    @Test
    fun flow_emitsExpectedValues() = runTest {
        flowOf(1, 2, 3).test {
            assertThat(awaitItem()).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(2)
            assertThat(awaitItem()).isEqualTo(3)
            awaitComplete()
        }
    }
}
