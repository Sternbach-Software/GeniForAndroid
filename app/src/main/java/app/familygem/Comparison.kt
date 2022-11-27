package app.familygem

import android.app.Activity
import app.familygem.constant.intdefs.Destiny
import app.familygem.constant.intdefs.POSITION_KEY
import app.familygem.constant.intdefs.Type

/**
 * Singleton that manages the objects of the 2 Gedcoms during the import of updates
 * Singleton che gestisce gli oggetti dei 2 Gedcom durante l'importazione degli aggiornamenti
 */
object Comparison {
    val list: MutableList<Front> = ArrayList()
    var autoContinue = false // determines whether to automatically accept all updates
    var numChoices = 0 // Total choices in case of autoContinue
    var choicesMade = 0// Position in case of autoContinue //Posizione in caso di autoProsegui

    data class Front(
        var object1: Any? = null,
        var object2: Any? = null,
        @Type var type: Int = 0
    ) {
        var canBothAddAndReplace = false // has the option to add + replace

        @Destiny
        var destiny = 0
    }

    fun addFront(object1: Any?, object2: Any?, type: Int): Front {
        return Front(object1, object2, type).apply {
            list.add(this)
        }
    }

    /**
     * Returns the currently active front
     */
    fun getFront(activity: Activity): Front {
        return list[activity.intent.getIntExtra(POSITION_KEY, 0) - 1]
    }

    /**
     * To call when exiting the comparison process
     */
    fun reset() {
        list.clear()
        autoContinue = false
    }
}