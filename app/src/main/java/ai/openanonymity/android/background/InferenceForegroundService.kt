package ai.openanonymity.android.background

import ai.openanonymity.android.MainActivity
import ai.openanonymity.android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class InferenceForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val activeJobs = NativeInferenceRegistry.activeJobCount()
        if (activeJobs <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(activeJobs))
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.inference_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.inference_notification_channel_description)
            }
        )
    }

    private fun buildNotification(activeJobs: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.inference_notification_title))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.inference_notification_body,
                    activeJobs,
                    activeJobs,
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "oa-inference-streams"
        private const val NOTIFICATION_ID = 1001

        fun sync(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, InferenceForegroundService::class.java)
            if (NativeInferenceRegistry.activeJobCount() > 0) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                runCatching {
                    appContext.startService(intent)
                }
            }
        }
    }
}
