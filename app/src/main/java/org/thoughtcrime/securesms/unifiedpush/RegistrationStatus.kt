/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.unifiedpush

enum class RegistrationStatus(val value: Int) {
  UNKNOWN(0),
  /** The user doesn't want to use UnifiedPush */
  NOT_REGISTERED(1),
  /**
   * The user wants to use UnifiedPush,
   * but we are waiting to receive an endpoint,
   * or an error occurred while sending the endpoint to the server
   */
  PENDING(2),
  /**
   * We are registered to the Distributor,
   * and to the server
   */
  REGISTERED(3);

  val requested = value > 1

  companion object {
    fun fromValue(value: Int): RegistrationStatus? {
      return entries.firstOrNull { it.value == value }
    }
  }
}