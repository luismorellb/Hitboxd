package com.hitboxd.app.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.hitboxd.app.R
import com.hitboxd.app.data.model.Notification
import com.hitboxd.app.ui.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object NotificationHelper {

    private const val CHANNEL_ID = "hitboxd_social"

    fun show(context: Context, notif: Notification) {
        CoroutineScope(Dispatchers.IO).launch {
            val text = when (notif.type) {
                "follow"      -> "started following you"
                "review_like" -> "liked your review"
                else          -> return@launch
            }

            val largeBitmap: Bitmap? = if (!notif.actorAvatar.isNullOrBlank()) {
                try {
                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(notif.actorAvatar)
                        .submit(128, 128)
                        .get(2, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val openIntent = Intent(context, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_notifications", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notif.idNotification,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(notif.actorUsername)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (largeBitmap != null) {
                builder.setLargeIcon(largeBitmap)
            }

            Handler(Looper.getMainLooper()).post {
                try {
                    NotificationManagerCompat.from(context).notify(notif.idNotification, builder.build())
                } catch (_: SecurityException) {}
            }
        }
    }
}
