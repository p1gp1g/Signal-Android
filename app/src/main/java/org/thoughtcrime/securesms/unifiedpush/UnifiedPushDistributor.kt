/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.unifiedpush

import android.content.pm.PackageManager
import android.os.Build
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush

object UnifiedPushDistributor {
  private const val TAG = "UnifiedPushDistributor"

  @JvmStatic
  fun register() {
    UnifiedPush.register(AppDependencies.application, vapid = BuildConfig.SIGNAL_VAPID_KEY)
  }

  @JvmStatic
  fun unregister() {
    UnifiedPush.unregister(AppDependencies.application)
    // MessagingReceiver.onUnregistered won't be called after the unregistration request
    SignalStore.unifiedpush.endpoint = null
  }

  @JvmStatic
  fun isAvailable() = UnifiedPush.getDistributors(AppDependencies.application).isNotEmpty()

  @JvmStatic
  fun nDistribInstalled() = UnifiedPush.getDistributors(AppDependencies.application).size

  @JvmStatic
  @get:JvmName("selected")
  val selected
    get() = UnifiedPush.getSavedDistributor(AppDependencies.application)

  fun distributorName(): String? {
    val context = AppDependencies.application
    val packageId = selected ?: return null
    return try {
      val ai = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getApplicationInfo(
          packageId,
          PackageManager.ApplicationInfoFlags.of(
            PackageManager.GET_META_DATA.toLong()
          )
        )
      } else {
        context.packageManager.getApplicationInfo(packageId, 0)
      }
      context.packageManager.getApplicationLabel(ai).toString()
    } catch (e: PackageManager.NameNotFoundException) {
      Log.e(TAG, "Could not resolve app name", e)
      null
    }
  }

  fun checkIfActive(): Boolean {
    return UnifiedPush.getAckDistributor(AppDependencies.application) != null
  }
}
