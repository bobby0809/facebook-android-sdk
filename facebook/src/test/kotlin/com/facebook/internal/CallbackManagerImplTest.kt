/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.facebook.internal

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.facebook.FacebookPowerMockTestCase
import com.facebook.FacebookSdk
import com.facebook.bolts.Capture
import com.facebook.internal.CallbackManagerImpl.Companion.registerStaticCallback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.powermock.api.mockito.PowerMockito.mockStatic
import org.powermock.api.mockito.PowerMockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.reflect.Whitebox

@PrepareForTest(FacebookSdk::class, CallbackManagerImpl::class)
class CallbackManagerImplTest : FacebookPowerMockTestCase() {
  @Before
  fun before() {
    mockStatic(FacebookSdk::class.java)
    `when`(FacebookSdk.getApplicationId()).thenReturn("123456789")
    `when`(FacebookSdk.isInitialized()).thenReturn(true)
    `when`(FacebookSdk.getApplicationContext())
        .thenReturn(ApplicationProvider.getApplicationContext())
    // Reset the static state every time so tests don't interfere with each other.
    Whitebox.setInternalState(
        CallbackManagerImpl::class.java,
        "staticCallbacks",
        HashMap<Int, CallbackManagerImpl.Callback>())
  }

  @Test
  fun `test callback executed`() {
    val capture = Capture(false)
    val callbackManagerImpl = CallbackManagerImpl()
    callbackManagerImpl.registerCallback(
        CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(),
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            capture.set(true)
            return true
          }
        })
    callbackManagerImpl.onActivityResult(FacebookSdk.getCallbackRequestCodeOffset(), 1, Intent())
    Assert.assertTrue(capture.get())
  }

  @Test
  fun `test right callback executed`() {
    val capture = Capture(false)
    val callbackManagerImpl = CallbackManagerImpl()
    callbackManagerImpl.registerCallback(
        123,
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            capture.set(true)
            return true
          }
        })
    callbackManagerImpl.registerCallback(
        456,
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            return false
          }
        })
    callbackManagerImpl.onActivityResult(123, 1, Intent())
    Assert.assertTrue(capture.get())
  }

  @Test
  fun `test static callback executed`() {
    val capture = Capture(false)
    val callbackManagerImpl = CallbackManagerImpl()
    registerStaticCallback(
        CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(),
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            capture.set(true)
            return true
          }
        })
    callbackManagerImpl.onActivityResult(FacebookSdk.getCallbackRequestCodeOffset(), 1, Intent())
    Assert.assertTrue(capture.get())
  }

  @Test
  fun `test static callback skipped`() {
    val capture = Capture(false)
    val captureStatic = Capture(false)
    val callbackManagerImpl = CallbackManagerImpl()
    callbackManagerImpl.registerCallback(
        CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(),
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            capture.set(true)
            return true
          }
        })
    registerStaticCallback(
        CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode(),
        object : CallbackManagerImpl.Callback {
          override fun onActivityResult(resultCode: Int, data: Intent?): Boolean {
            captureStatic.set(true)
            return true
          }
        })
    callbackManagerImpl.onActivityResult(FacebookSdk.getCallbackRequestCodeOffset(), 1, Intent())
    Assert.assertTrue(capture.get())
    Assert.assertFalse(captureStatic.get())
  }
}
