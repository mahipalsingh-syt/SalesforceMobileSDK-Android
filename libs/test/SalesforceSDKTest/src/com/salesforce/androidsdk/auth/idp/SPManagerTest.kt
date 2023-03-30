/*
 * Copyright (c) 2023-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.*
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.SPLoginRequest
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.SPLoginResponse
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SPManagerTest {

    companion object {
        const val MAX_UPDATES_RECORDED = 16
        const val TIMEOUT: Long = 3
    }

    lateinit var context: Context
    lateinit var recordedUpdates: BlockingQueue<Status>
    lateinit var testStatusUpdateCallback: TestStatusUpdateCallback
    lateinit var testSDKMgr: TestSDKMgr

    inner class TestStatusUpdateCallback : StatusUpdateCallback {
        override fun onStatusUpdate(status: Status) {
            recordedUpdates.put(status)
        }
    }

    inner class TestSDKMgr : SPManager.SDKManager {
        override fun getCurrentUser(): UserAccount? {
            TODO("Not yet implemented")
        }

        override fun getUserFromOrgAndUserId(orgId: String, userId: String): UserAccount? {
            TODO("Not yet implemented")
        }

        override fun switchToUser(user: UserAccount) {
            TODO("Not yet implemented")
        }

        override fun getMainActivityClass(): Class<out Activity>? {
            TODO("Not yet implemented")
        }
    }

    @Before
    fun setup() {
        context = getInstrumentation().targetContext
        recordedUpdates = ArrayBlockingQueue(MAX_UPDATES_RECORDED)
        testStatusUpdateCallback = TestStatusUpdateCallback()
        testSDKMgr = TestSDKMgr()
    }

    @After
    fun teardown() {
    }

    @Test
    fun testKickOffSPInitiatedLoginFlow() {
        val spManager = SPManager("some-idp", testSDKMgr)
        spManager.kickOffSPInitiatedLoginFlow(context, testStatusUpdateCallback)

        // Waiting for status update
        waitForStatusUpdate(Status.LOGIN_REQUEST_SENT_TO_IDP)

        // Checking active flow
        val activeFlow = spManager.getActiveFlow()
        val request = activeFlow?.firstMessage
        Assert.assertNotNull(request)
        Assert.assertTrue(request is SPLoginRequest)

        // Faking a response from the idp
        val response = SPLoginResponse(request!!.uuid, error="Failure!")
        spManager.onReceive(context, response.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Waiting for status update
        waitForStatusUpdate(Status.ERROR_RESPONSE_RECEIVED_FROM_IDP)

        // Checking active flow
        Assert.assertEquals(2, activeFlow.messages.size)
        Assert.assertEquals(response.toString(), activeFlow.messages[1].toString())
    }

    fun waitForStatusUpdate(expectedStatusUpdate: Status) {
        val actualStatusUpdate = recordedUpdates.poll(TIMEOUT, TimeUnit.SECONDS)
        Assert.assertEquals(expectedStatusUpdate, actualStatusUpdate)
    }
}
