/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PushServiceEvent
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork.account
import org.thoughtcrime.securesms.unifiedpush.RegistrationStatus
import org.thoughtcrime.securesms.unifiedpush.UnifiedPushDistributor
import org.thoughtcrime.securesms.unifiedpush.UnifiedPushNotificationBuilder
import org.whispersystems.signalservice.api.NetworkResultUtil.toBasicLegacy
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles UnifiedPush registration and ensures the MollySocket status is up-to-date.
 * Unregisters if the account is not registered or UnifiedPush is disabled.
 */
class UnifiedPushRefreshJob private constructor(
  private val fromNewEndpoint: Boolean,
  parameters: Parameters,
) : BaseJob(parameters) {

  constructor() : this(fromNewEndpoint = false)

  constructor(fromNewEndpoint: Boolean) : this(
    fromNewEndpoint = fromNewEndpoint,
    parameters = Parameters.Builder()
      .setQueue(FcmRefreshJob.KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(3)
      .setLifespan(TimeUnit.HOURS.toMillis(6))
      .setMaxInstancesForFactory(2)
      .build()
  )

  @Throws(Exception::class)
  public override fun onRun() {
    val hasAccount = SignalStore.account.isRegistered
    val currStatus = SignalStore.unifiedpush.registrationStatus

    Log.d(TAG, "Current registration status: $currStatus")

    if (!hasAccount) {
      Log.d(TAG, "No account registered: aborting")
      return
    }

    try {
      if (currStatus == RegistrationStatus.NOT_REGISTERED) {
        disableUnifiedPush()
      } else {
        val newStatus = checkRegistrationStatusWithWebPush(currStatus)

        if (currStatus == newStatus) {
          Log.d(TAG, "Registration status unchanged.")
        } else {
          Log.d(TAG, "Updated registration status: $newStatus")
          SignalStore.unifiedpush.registrationStatus = newStatus
        }

        if (newStatus == RegistrationStatus.REGISTERED) {
          UnifiedPushNotificationBuilder(context).clearAlerts()
        }
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Error checking registration status", t)
      // Re-throw the exception as IOException for retry
      when (t) {
        is IOException -> throw t
        else -> throw IOException(t)
      }
    } finally {
      AppDependencies.resetNetwork()
      EventBus.getDefault().post(PushServiceEvent)
    }
  }

  @Throws(IOException::class)
  private fun checkRegistrationStatusWithWebPush(prevStatus: RegistrationStatus): RegistrationStatus {
    if (!UnifiedPushDistributor.isAvailable()) {
      disableUnifiedPush()
      return RegistrationStatus.NOT_REGISTERED
    }

    val endpoint = SignalStore.unifiedpush.endpoint
    val publicKey = SignalStore.unifiedpush.publicKey
    val auth = SignalStore.unifiedpush.auth
    val lastReceivedTime = SignalStore.unifiedpush.lastReceivedTime

    Log.d(TAG, "Last notification received at: $lastReceivedTime")

    if (!fromNewEndpoint) {
      UnifiedPushDistributor.register()
    }

    if (!UnifiedPushDistributor.checkIfActive() || endpoint == null || publicKey == null || auth == null) {
      Log.e(TAG, "Distributor is not active or endpoint is missing.")
      return RegistrationStatus.PENDING
    }

    return try {
      toBasicLegacy(account.setWebPush(endpoint, publicKey, auth))
      Log.d(TAG, "Successfully registered web push")
      RegistrationStatus.REGISTERED
    } catch (e: IOException) {
      Log.e(TAG, "A error occured while registering web push", e)
      return prevStatus
    }
  }

  private fun disableUnifiedPush() {
    Log.d(TAG, "UnifiedPush is disabled.")
    if (
      SignalStore.unifiedpush.registrationStatus != RegistrationStatus.NOT_REGISTERED
      || SignalStore.unifiedpush.endpoint != null
      || SignalStore.unifiedpush.publicKey != null
      || SignalStore.unifiedpush.auth != null
      || SignalStore.unifiedpush.lastReceivedTime != 0L
    ) {
      try {
        toBasicLegacy(account.clearWebPush())
        SignalStore.unifiedpush.registrationStatus = RegistrationStatus.NOT_REGISTERED
        SignalStore.unifiedpush.endpoint = null
        SignalStore.unifiedpush.publicKey = null
        SignalStore.unifiedpush.auth = null
        SignalStore.unifiedpush.lastReceivedTime = 0
      } catch (e: Exception) {
        Log.e(TAG, "An error occurred while clearing web push token", e)
      }
    }
  }

  override fun onFailure() = Unit

  public override fun onShouldRetry(throwable: Exception): Boolean {
    return throwable !is NonSuccessfulResponseCodeException
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBoolean(KEY_FROM_NEW_ENDPOINT, fromNewEndpoint)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  class Factory : Job.Factory<UnifiedPushRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UnifiedPushRefreshJob {
      val data = JsonJobData.deserialize(serializedData)
      return UnifiedPushRefreshJob(
        fromNewEndpoint = data.getBoolean(KEY_FROM_NEW_ENDPOINT),
        parameters = parameters,
      )
    }
  }

  companion object {
    private val TAG = Log.tag(UnifiedPushRefreshJob::class.java)

    const val KEY = "UnifiedPushRefreshJob"
    private const val KEY_FROM_NEW_ENDPOINT = "fromNewEndpoint"
  }
}

