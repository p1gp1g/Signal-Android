/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.unifiedpush.RegistrationStatus

class UnifiedPushValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(UnifiedPushValues::class)

    private const val UNIFIEDPUSH_STATUS = "up.status"
    private const val UNIFIEDPUSH_ENABLED = "up.enabled"
    private const val UNIFIEDPUSH_ENDPOINT = "up.endpoint"
    private const val UNIFIEDPUSH_PUBLIC_KEY = "up.publicKey"
    private const val UNIFIEDPUSH_AUTH = "up.auth"
    private const val UNIFIEDPUSH_LAST_RECEIVED_TIME = "up.lastRecvTime"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup() = emptyList<String>()

  @get:JvmName("available")
  var available: Boolean by booleanValue(UNIFIEDPUSH_ENABLED, false)

  var registrationStatus: RegistrationStatus
    get() = RegistrationStatus.fromValue(getInteger(UNIFIEDPUSH_STATUS, -1)) ?: RegistrationStatus.UNKNOWN
    set(status) {
      putInteger(UNIFIEDPUSH_STATUS, status.value)
    }

  var endpoint: String? by stringValue(UNIFIEDPUSH_ENDPOINT, null)
  var publicKey: String? by stringValue(UNIFIEDPUSH_PUBLIC_KEY, null)
  var auth: String? by stringValue(UNIFIEDPUSH_AUTH, null)

  var lastReceivedTime: Long by longValue(UNIFIEDPUSH_LAST_RECEIVED_TIME, 0)

  val registered: Boolean
    get() = available && registrationStatus.requested
}
