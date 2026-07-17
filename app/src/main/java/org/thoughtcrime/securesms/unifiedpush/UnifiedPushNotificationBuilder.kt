/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.unifiedpush

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels

class UnifiedPushNotificationBuilder(val context: Context) {

  private val notificationManager = NotificationManagerCompat.from(context)

  private val builder: NotificationCompat.Builder =
    NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.unifiedpush))
      .setContentIntent(null)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)

  private fun getNotification(content: String): Notification {
    return builder.setContentText(content).setStyle(
      NotificationCompat.BigTextStyle()
        .bigText(content)
    ).build()
  }

  private fun notify(notificationId: Int, content: String) {
    val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    if (hasPermission) {
      notificationManager.notify(notificationId, getNotification(content))
    }
  }

  fun clearAlerts() {
    notificationManager.cancel(NOTIFICATION_ID)
  }

  fun setNotificationRegistrationFailed() {
    //notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__registration_failed))
  }

  companion object {
    private const val NOTIFICATION_ID = 51215
  }
}
