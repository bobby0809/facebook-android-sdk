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
package com.facebook.appevents

import android.app.Activity
import android.os.Bundle
import com.facebook.FacebookPowerMockTestCase
import com.facebook.FacebookSdk
import com.facebook.GraphRequest
import com.facebook.appevents.internal.ActivityLifecycleTracker
import com.facebook.internal.AttributionIdentifiers
import com.facebook.internal.FeatureManager
import com.facebook.internal.FetchedAppGateKeepersManager
import com.facebook.internal.FetchedAppSettingsManager
import com.facebook.internal.FetchedAppSettingsManager.parseAppSettingsFromJSON
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.reflect.Whitebox
import org.powermock.reflect.internal.WhiteboxImpl
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

@PrepareForTest(
    AppEventQueue::class,
    AppEventStore::class,
    AttributionIdentifiers::class,
    ActivityLifecycleTracker::class,
    FacebookSdk::class,
    FeatureManager::class,
    FetchedAppSettingsManager::class,
    FetchedAppGateKeepersManager::class,
    Executors::class,
    GraphRequest::class,
    AttributionIdentifiers.Companion::class)
class AutomaticAnalyticsTest : FacebookPowerMockTestCase() {

  @Before
  fun init() {
    PowerMockito.mockStatic(FacebookSdk::class.java)
    PowerMockito.`when`(FacebookSdk.isInitialized()).thenReturn(true)
    PowerMockito.`when`(FacebookSdk.getApplicationId()).thenReturn("1234")
    PowerMockito.`when`(FacebookSdk.getApplicationContext())
        .thenReturn(RuntimeEnvironment.application)

    PowerMockito.mockStatic(FetchedAppSettingsManager::class.java)
  }

  @Test
  fun testAutomaticLoggingEnabledServerConfiguration() {
    val settingsJSON = JSONObject()
    settingsJSON.put("app_events_feature_bitmask", "0")
    var settings = parseAppSettingsFromJSON("123", settingsJSON)
    Assert.assertFalse(settings.automaticLoggingEnabled)
    settingsJSON.put("app_events_feature_bitmask", "7")
    settings = parseAppSettingsFromJSON("123", settingsJSON)
    Assert.assertFalse(settings.automaticLoggingEnabled)
    settingsJSON.put("app_events_feature_bitmask", "23")
    settings = parseAppSettingsFromJSON("123", settingsJSON)
    Assert.assertFalse(settings.automaticLoggingEnabled)
    settingsJSON.put("app_events_feature_bitmask", "8")
    settings = parseAppSettingsFromJSON("123", settingsJSON)
    Assert.assertTrue(settings.automaticLoggingEnabled)
    settingsJSON.put("app_events_feature_bitmask", "9")
    settings = parseAppSettingsFromJSON("123", settingsJSON)
    Assert.assertTrue(settings.automaticLoggingEnabled)
    val noBitmaskFieldSettings = JSONObject()
    settings = parseAppSettingsFromJSON("123", noBitmaskFieldSettings)
    Assert.assertFalse(settings.automaticLoggingEnabled)
  }

  @Test
  fun testAutoTrackingWhenInitialized() {
    val mockExecutor: ScheduledExecutorService = FacebookSerialThreadPoolExecutor(1)
    Whitebox.setInternalState(
        ActivityLifecycleTracker::class.java, "singleThreadExecutor", mockExecutor)
    PowerMockito.mockStatic(ActivityLifecycleTracker::class.java)
    val activity =
        Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
    PowerMockito.doCallRealMethod()
        .`when`(ActivityLifecycleTracker::class.java, "onActivityResumed", activity)
  }

  @Test
  fun testLogAndSendAppEvent() {
    val mockExecutor: ScheduledExecutorService = FacebookSerialThreadPoolExecutor(1)
    Whitebox.setInternalState(AppEventQueue::class.java, "singleThreadExecutor", mockExecutor)
    // Mock App Settings to avoid App Setting request

    // Disable Gatekeeper
    PowerMockito.mockStatic(FetchedAppGateKeepersManager::class.java)
    PowerMockito.`when`(FetchedAppGateKeepersManager.getGateKeeperForKey(any(), any(), any()))
        .thenReturn(false)

    // Mock FeatureManger to avoid GK request
    PowerMockito.mockStatic(FeatureManager::class.java)

    // Stub mock IDs for AttributionIdentifiers
    val mockAdvertiserID = "fb_mock_advertiserID"
    val mockAttributionID = "fb_mock_attributionID"
    val mockIdentifiers: AttributionIdentifiers = mock()
    PowerMockito.`when`(mockIdentifiers.androidAdvertiserId).thenReturn(mockAdvertiserID)
    PowerMockito.`when`(mockIdentifiers.attributionId).thenReturn(mockAttributionID)
    val mockAttributionIdentifierCompanion =
        PowerMockito.mock(AttributionIdentifiers.Companion::class.java)
    WhiteboxImpl.setInternalState(
        AttributionIdentifiers::class.java, "Companion", mockAttributionIdentifierCompanion)
    PowerMockito.`when`(mockAttributionIdentifierCompanion.getAttributionIdentifiers(any()))
        .thenReturn(mockIdentifiers)

    // Mock App Event Store
    PowerMockito.mockStatic(AppEventStore::class.java)
    val accessTokenAppIdPair = AccessTokenAppIdPair("anothertoken1337", "yoloapplication")
    val appEvent = AppEvent("ctxName", "eventName2", 0.0, Bundle(), true, true, null)
    val map = hashMapOf(accessTokenAppIdPair to mutableListOf(appEvent))
    val persistedEvents: PersistedEvents = PersistedEvents(map)
    PowerMockito.`when`(AppEventStore.readAndClearStore()).thenReturn(persistedEvents)

    // Mock graph request
    val mockRequest: GraphRequest = mock()
    PowerMockito.whenNew(GraphRequest::class.java).withAnyArguments().thenReturn(mockRequest)
    PowerMockito.mockStatic(AppEventQueue::class.java)
    PowerMockito.`when`(AppEventQueue.buildRequestForSession(any(), any(), any(), any()))
        .thenReturn(mockRequest)
    PowerMockito.`when`(AppEventQueue.flush(any())).thenCallRealMethod()
    PowerMockito.`when`(AppEventQueue.flushAndWait(any())).thenCallRealMethod()
    PowerMockito.`when`(AppEventQueue.sendEventsToServer(any(), any())).thenCallRealMethod()
    PowerMockito.`when`(AppEventQueue.buildRequests(any(), any())).thenCallRealMethod()
    val loggerImpl = AppEventsLoggerImpl(RuntimeEnvironment.application, "1234", null)
    loggerImpl.logEvent("fb_mock_event", 1.0, Bundle(), true, null)
    loggerImpl.flush()
    Thread.sleep(200)
    verify(mockRequest).executeAndWait()
  }
}
