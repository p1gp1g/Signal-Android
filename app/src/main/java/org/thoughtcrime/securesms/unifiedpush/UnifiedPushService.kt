/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.unifiedpush

import androidx.core.os.bundleOf
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gcm.FcmReceiveService
import org.thoughtcrime.securesms.jobs.UnifiedPushRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork.account
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import org.whispersystems.signalservice.api.NetworkResultUtil.toBasicLegacy

class UnifiedPushService : PushService() {

  companion object {
    private val TAG = Log.tag(UnifiedPushService::class.java)
  }

  private val executor = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
    Log.i(TAG, "onNewEndpoint($instance)")
    refreshEndpoint(endpoint)
  }

  override fun onRegistrationFailed(reason: FailedReason, instance: String) {
    // called when the registration is not possible, eg. no network
    Log.w(TAG, "onRegistrationFailed($instance)")
    // TODO when `reason` is INTERNAL_ERROR, try to register again _one time_
    // TODO when `reason` is ACTION_REQUIRED, tell the distributor requires a user interaction
    // TODO when `reason` is NETWORK, tell to try again when network is back, or implement it.
    UnifiedPushNotificationBuilder(this).setNotificationRegistrationFailed()
  }

  override fun onUnregistered(instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    Log.i(TAG, "onUnregistered($instance)")
    refreshEndpoint(null)
  }

  override fun onMessage(message: PushMessage, instance: String) {
    Log.i(TAG, "onMessage($instance)")
    val msg = message.content.toString(Charsets.UTF_8)

    if (
      message.decrypted &&
      JSONObject(msg).has("activationToken")
    ) {
      Log.d(TAG, "Received activation token")
      try {
        val activationToken = JSONObject(msg).getString("activationToken")
        toBasicLegacy(account.activateWebPush(activationToken))
      } catch (e: Exception) {
        Log.e(TAG, "Got an exception while activating web push", e)
      }
    } else {
      updateLastReceivedTime(System.currentTimeMillis())
      syncOnMessage()
    }
  }

  private fun syncOnMessage() {
    if (SignalStore.account.isRegistered && SignalStore.unifiedpush.available) {
      Log.d(TAG, "New message")
      executor.enqueue {
        FcmReceiveService.handleReceivedNotification(this, RemoteMessage(bundleOf("google.delivered_priority" to "high")))
      }
    }
  }

  private fun updateLastReceivedTime(timestamp: Long) {
    SignalStore.unifiedpush.lastReceivedTime = timestamp
  }

  /**
   * Update UnifiedPush endpoint
   *
   * @return `true` if the endpoint has been updated, `false` if the endpoint is the same
   */
  private fun refreshEndpoint(endpoint: PushEndpoint?): Boolean {
    val storedEndpoint = SignalStore.unifiedpush.endpoint
    val storedPublicKey = SignalStore.unifiedpush.publicKey
    val storedAuth = SignalStore.unifiedpush.auth
    val currentStatus = SignalStore.unifiedpush.registrationStatus
    return if (endpoint?.url != storedEndpoint
      || endpoint?.pubKeySet?.pubKey != storedPublicKey
      || endpoint?.pubKeySet?.auth != storedAuth
      || currentStatus != RegistrationStatus.REGISTERED
    ) {
      Log.d(TAG, "Storing new endpoint")
      SignalStore.unifiedpush.endpoint = endpoint?.url
      SignalStore.unifiedpush.publicKey = endpoint?.pubKeySet?.pubKey
      SignalStore.unifiedpush.auth = endpoint?.pubKeySet?.auth
      AppDependencies.jobManager.add(UnifiedPushRefreshJob(fromNewEndpoint = true))
      true
    } else {
      Log.d(TAG, "Endpoint already known")
      false
    }
  }
}
