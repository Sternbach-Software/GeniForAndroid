package app.familygem

import android.content.Intent
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.familygem.constant.intdefs.*

/**
 * This BroadcastReceiver has a double function:
 * - Receive intent from Notifier to create notifications
 * - Receive ACTION_BOOT_COMPLETED after reboot to restore notifications saved in settings.json
 */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Set again alarms after reboot
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Notifier(context, null, 0, Notifier.What.REBOOT)
        } else { // Create notification
            val notifyIntent = Intent(context, TreesActivity::class.java)
                .putExtra(Notifier.TREE_ID_KEY, intent.getIntExtra(TREE_ID_KEY_ENGLISH, 0))
                .putExtra(Notifier.INDI_ID_KEY, intent.getStringExtra(INDI_ID_KEY))
                .putExtra(Notifier.NOTIFY_ID_KEY, intent.getIntExtra(ID_KEY, 1))
            val pendingIntent = PendingIntent.getActivity(
                context, intent.getIntExtra(ID_KEY, 1),
                notifyIntent, PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, Notifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.albero_cherokee)
                .setContentTitle(intent.getStringExtra(TITLE_KEY))
                .setContentText(intent.getStringExtra(TEXT_KEY))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(intent.getIntExtra(ID_KEY, 1), builder.build())
        }
    }
}