package app.familygem

import org.folg.gedcom.model.Gedcom
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.WorkManager
import app.familygem.Settings.Birthday
import android.app.AlarmManager
import android.content.Intent
import android.app.PendingIntent
import android.content.Context
import app.familygem.constant.Format
import app.familygem.constant.intdefs.ID_KEY
import app.familygem.constant.intdefs.TEXT_KEY
import app.familygem.constant.intdefs.TITLE_KEY
import app.familygem.constant.intdefs.TREE_ID_KEY_ENGLISH
import org.folg.gedcom.model.Person
import java.lang.Exception
import java.util.*

/**
 * Manager of birthday notifications
 */
internal class Notifier(context: Context, gedcom: Gedcom?, treeId: Int, toDo: What?) {
    private val FACTOR = 100000
    private val now = Date()

    internal enum class What {
        REBOOT, CREATE, DELETE, DEFAULT
    }

    init {

        // Create the notification channel, necessary only on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getText(R.string.birthdays),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = context.getString(R.string.birthday_notified_midday)
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Delete previous Workish notifications
        WorkManager.getInstance(context).cancelAllWork() // Todo remove after version 0.9.1
        val tree = Global.settings!!.getTree(treeId)
        when (toDo) {
            What.REBOOT -> for (tree1 in Global.settings!!.trees!!) {
                createAlarms(context, tree1)
            }
            What.DELETE -> deleteAlarms(context, tree)
            What.CREATE -> {
                findBirthdays(gedcom, tree)
                createAlarms(context, tree)
            }
            else -> {
                deleteAlarms(context, tree)
                findBirthdays(gedcom, tree)
                createAlarms(context, tree)
            }
        }
    }

    /**
     * Select people who have to celebrate their birthday and add them to the settings
     * Eventually save settings
     */
    fun findBirthdays(gedcom: Gedcom, tree: Settings.Tree?) {
        if (tree!!.birthdays == null) tree.birthdays = HashSet() else tree.birthdays.clear()
        for (person in gedcom.people) {
            val birth = findBirth(person)
            if (birth != null) {
                val years = findAge(birth)
                if (years >= 0) {
                    tree.birthdays.add(
                        Birthday(
                            person.id, U.givenName(person),
                            U.properName(person), nextBirthday(birth), years
                        )
                    )
                }
            }
        }
        Global.settings!!.save()
    }

    /**
     * Possibly find the birth Date of a person
     */
    private fun findBirth(person: Person): Date? {
        if (!U.isDead(person)) {
            for (event in person.eventsFacts) {
                if (event.tag == "BIRT" && event.date != null) {
                    val dateConverter = GedcomDateConverter(event.date)
                    if (dateConverter.isSingleKind && dateConverter.data1.isFormat(Format.D_M_Y)) {
                        return dateConverter.data1.date
                    }
                }
            }
        }
        return null
    }

    /**
     * Count the number of years that will be turned on the next birthday
     */
    private fun findAge(birth: Date): Int {
        var years = now.year - birth.year
        if (birth.month < now.month || birth.month == now.month && birth.date < now.date
            || birth.month == now.month && birth.date == now.date && now.hours >= 12
        ) years++
        return if (years <= 120) years else -1
    }

    /**
     * From birth Date find next birthday as long timestamp
     */
    private fun nextBirthday(birth: Date): Long {
        birth.year = now.year
        birth.hours = 12
        //birth.setMinutes(0);
        if (now.after(birth)) birth.year = now.year + 1
        return birth.time
    }

    /**
     * Generate an alarm from each birthday of the provided tree
     */
    fun createAlarms(context: Context, tree: Settings.Tree?) {
        if (tree!!.birthdays == null) return
        var eventId = tree.id * FACTOR // Different for every tree
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (birthday in tree.birthdays) {
            if (birthday.date > now.time) { // Avoid setting alarm for a past birthday
                val intent = Intent(context, NotifyReceiver::class.java)
                    .putExtra(ID_KEY, eventId)
                    .putExtra(TITLE_KEY, birthday.name + " (" + tree.title + ")")
                    .putExtra(
                        TEXT_KEY,
                        context.getString(R.string.turns_years_old, birthday.given, birthday.age)
                    )
                    .putExtra(TREE_ID_KEY_ENGLISH, tree.id)
                    .putExtra(INDI_ID_KEY, birthday.id)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, eventId++, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT
                )
                try {
                    alarmManager.setExact(AlarmManager.RTC, birthday.date, pendingIntent)
                } catch (e: Exception) {
                    break // There is a limit of 500 alarms on some devices
                }
            }
        }
    }

    /**
     * Delete all alarms already set for a tree
     */
    fun deleteAlarms(context: Context, tree: Settings.Tree?) {
        if (tree!!.birthdays == null) return
        var eventId = tree.id * FACTOR
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (b in tree.birthdays) {
            val intent = Intent(context, NotifyReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, eventId++, intent,  // Flags also need to be identical to alarm creator
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        const val TREE_ID_KEY = "targetTreeId"
        const val INDI_ID_KEY = "targetIndiId"
        const val NOTIFY_ID_KEY = "notifyId"
        const val CHANNEL_ID = "birthdays"
    }
}