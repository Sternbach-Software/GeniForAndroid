package app.familygem

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import app.familygem.constant.intdefs.*
import org.folg.gedcom.model.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for importing news in an existing tree
 */
class CompareActivity : BaseActivity() {

    private lateinit var sharingDate: Date
    private lateinit var changeDateFormat: SimpleDateFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.compara)
        val idTree1 = intent.getIntExtra(TREE_ID_KEY, 1) // Old tree
        val idTree2 = intent.getIntExtra(TREE_2_ID_KEY, 1) // New tree received in sharing
        Global.treeId2 = idTree2 // it will be used for the Comparator and Confirmation images
        Global.gc = TreesActivity.openGedcomTemporarily(idTree1, true)
        Global.gc2 = TreesActivity.openGedcomTemporarily(idTree2, false)
        if (Global.gc == null || Global.gc2 == null) {
            Toast.makeText(this, R.string.no_useful_data, Toast.LENGTH_LONG).show()
            onBackPressed()
            return
        }
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome")) // brings all dates back to the Aruba time zone //riconduce tutte le date al fuso orario di Aruba
        try {
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            sharingDate = dateFormat.parse(intent.getStringExtra(DATA_ID_KEY)!!) as Date
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        changeDateFormat = SimpleDateFormat("d MMM yyyyHH:mm:ss", Locale.ENGLISH)
        Comparison.reset() // Necessary to empty it, for example after a configuration change //Necessario svuotarlo, ad esempio dopo un cambio di configurazione

        // Compare all the records of the two Gedcoms
        for (o2 in Global.gc2!!.families) compare(Global.gc!!.getFamily(o2.id), o2, 7)
        for (o in Global.gc!!.families) reconcile(o, Global.gc2!!.getFamily(o.id), 7)
        for (o2 in Global.gc2!!.people) compare(Global.gc!!.getPerson(o2.id), o2, 6)
        for (o in Global.gc!!.people) reconcile(o, Global.gc2!!.getPerson(o.id), 6)
        for (o2 in Global.gc2!!.sources) compare(Global.gc!!.getSource(o2.id), o2, 5)
        for (o in Global.gc!!.sources) reconcile(o, Global.gc2!!.getSource(o.id), 5)
        for (o2 in Global.gc2!!.media) compare(Global.gc!!.getMedia(o2.id), o2, 4)
        for (o in Global.gc!!.media) reconcile(o, Global.gc2!!.getMedia(o.id), 4)
        for (o2 in Global.gc2!!.repositories) compare(Global.gc!!.getRepository(o2.id), o2, 3)
        for (o in Global.gc!!.repositories) reconcile(o, Global.gc2!!.getRepository(o.id), 3)
        for (o2 in Global.gc2!!.submitters) compare(Global.gc!!.getSubmitter(o2.id), o2, 2)
        for (o in Global.gc!!.submitters) reconcile(o, Global.gc2!!.getSubmitter(o.id), 2)
        for (o2 in Global.gc2!!.notes) compare(Global.gc!!.getNote(o2.id), o2, 1)
        for (o in Global.gc!!.notes) reconcile(o, Global.gc2!!.getNote(o.id), 1)
        val tree2 = Global.settings.getTree(idTree2)
        if (Comparison.list.isEmpty()) {
            setTitle(R.string.tree_without_news)
            if (tree2?.grade != NO_NOVELTIES) {
                tree2?.grade = NO_NOVELTIES
                Global.settings.save()
            }
        } else if (tree2?.grade != RETURNED_TO_ITALY_IS_DERIVATIVE) {
            tree2?.grade = RETURNED_TO_ITALY_IS_DERIVATIVE
            Global.settings.save()
        }
        populateCard(Global.gc!!, idTree1, R.id.compara_vecchio)
        populateCard(Global.gc2!!, idTree2, R.id.compara_nuovo)
        (findViewById<View>(R.id.compara_testo) as TextView).text =
            getString(R.string.tree_news_imported, Comparison.list.size)
        val button1 = findViewById<Button>(R.id.compara_bottone1)
        val button2 = findViewById<Button>(R.id.compara_bottone2)
        if (Comparison.list.size > 0) {
            // Review individually //Rivedi singolarmente
            button1.setOnClickListener { v: View? ->
                startActivity(
                    Intent(
                        this@CompareActivity,
                        TreeComparatorActivity::class.java
                    ).putExtra(POSITION_KEY, 1)
                )
            }
            // Accept everything //Accetta tutto
            button2.setOnClickListener { v: View ->
                v.isEnabled = false
                Comparison.numChoices = 0
                for (front in Comparison.list) {
                    if (front.canBothAddAndReplace) Comparison.numChoices++
                }
                val intent = Intent(this@CompareActivity, TreeComparatorActivity::class.java)
                intent.putExtra(POSITION_KEY, 1)
                if (Comparison.numChoices > 0) { // Revision request dialog //Dialogo di richiesta revisione
                    AlertDialog.Builder(this)
                        .setTitle(
                            if (Comparison.numChoices == 1) getString(R.string.one_update_choice) else getString(
                                R.string.many_updates_choice,
                                Comparison.numChoices
                            )
                        )
                        .setMessage(R.string.updates_replace_add)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, id: Int ->
                            Comparison.autoContinue = true
                            Comparison.choicesMade = 1
                            startActivity(intent)
                        }
                        .setNeutralButton(android.R.string.cancel) { dialog: DialogInterface?, id: Int ->
                            button2.isEnabled = true
                        }
                        .setOnCancelListener { dialog: DialogInterface? ->
                            button2.isEnabled = true
                        }.show()
                } else { // Start automatically //Avvio in automatico
                    Comparison.autoContinue = true
                    startActivity(intent)
                }
            }
        } else {
            button1.setText(R.string.delete_imported_tree)
            button1.setOnClickListener { v: View? ->
                TreesActivity.deleteTree(this@CompareActivity, idTree2)
                onBackPressed()
            }
            button2.visibility = View.GONE
        }
    }

    override fun onRestart() {
        super.onRestart()
        findViewById<View>(R.id.compara_bottone2).isEnabled =
            true // if possibly(?) //se eventualmente
        Comparison.autoContinue =
            false // It resets it if the automatism(?) was eventually chosen //Lo resetta se eventualmente era stato scelto l'automatismo
    }

    /**
     * See whether to add the two objects to the list of those to be evaluated
     * Vede se aggiungere i due oggetti alla lista di quelli da valutare
     */
    private fun compare(o: Any?, o2: Any, type: Int) {
        val c = o?.change
        val c2 = o2.change
        var isModified = false
        var canAddAndReplace = false
        val c2IsRecent = c2.isRecent
        if (o == null && c2IsRecent || // o2 has been added in the new tree -> ADD
            c == null && c2 != null //ditto?
        )
            isModified = true
        else if (c != null && c2 != null &&
            !(c.dateTime.value == c2.dateTime.value && c.dateTime.time == c2.dateTime.time)
        ) {
            if (c.isRecent && c2IsRecent) { // both changed after sharing -> ADD / REPLACE //entrambi modificati dopo la condivisione --> AGGIUNGI/SOSTITUISCI
                canAddAndReplace = true
            } else if (c2IsRecent) // only o2 has been changed -> REPLACE
                isModified = true
        }
        if (isModified) {
            val front = Comparison.addFront(o, o2, type)
            if (canAddAndReplace/*TODO android studio says this is always false??*/) front.canBothAddAndReplace =
                true
        }
    }

    /**
     * Ditto for the remaining objects deleted in the old tree
     * Idem per i rimanenti oggetti eliminati nell'albero vecchio
     */
    private fun reconcile(o: Any, o2: Any?, type: Int) {
        if (o2 == null && !o.change.isRecent) Comparison.addFront(o, null, type)
    }

    /**
     * Find if a top-level record has been modified after the date of sharing
     *
     * @param this Actual change date of the top-level record
     * @return true if the record is more recent than the date of sharing
     */
    private val Change?.isRecent: Boolean
        get() = if (this != null && dateTime != null) {
            try { // TODO with null time(?) //con time null
                changeDateFormat.timeZone = TimeZone.getTimeZone(
                    U.castJsonString(
                        getExtension(
                            ZONE_EXTENSION_KEY
                        )
                    ) ?: "UTC"
                )
                changeDateFormat.parse(dateTime.value + dateTime.time)!!.after(sharingDate)
                //long oreSfaso = TimeUnit.MILLISECONDS.toMinutes( timeZone.getOffset(dataobject.getTime()) );
                //s.l( dataobject+"\t"+ ok +"\t"+ (oreSfaso>0?"+":"")+oreSfaso +"\t"+ timeZone.getID() );
            } catch (e: ParseException) {
                false
            }
        } else false

    val Any.change
        get() = when (this) { //these classes don't inherit a common ancestor that has the getChange() method, so we need to parse them out into distinct classes
            is Family -> change
            is Person -> change
            is Source -> change
            is Media -> change
            is Repository -> change
            is Submitter -> change
            is Note -> change
            else -> null
        }

    private fun populateCard(gc: Gedcom, treeId: Int, cardId: Int) {
        val card = findViewById<CardView>(cardId)
        val tree = Global.settings.getTree(treeId)
        val title = card.findViewById<TextView>(R.id.confronto_titolo)
        val data = card.findViewById<TextView>(R.id.confronto_testo)
        title.text = tree?.title
        data.text = tree?.let { TreesActivity.writeData(this, it) }
        if (cardId == R.id.compara_nuovo) {
            if (tree?.grade == NO_NOVELTIES) {
                card.setCardBackgroundColor(resources.getColor(R.color.consumed))
                title.setTextColor(resources.getColor(R.color.gray_text))
                data.setTextColor(resources.getColor(R.color.gray_text))
            } else card.setCardBackgroundColor(resources.getColor(R.color.accent_medium))
            val submitter = gc.getSubmitter(tree?.shares?.get(tree.shares!!.size - 1)?.submitter)
            val txt = StringBuilder()
            if (submitter != null) {
                var name = submitter.name
                if (name.isNullOrEmpty()) name = getString(android.R.string.unknownName)
                txt.appendLine(getString(R.string.sent_by, name))
            }
            //if( Confronto.getLista().size() > 0 )
            //	txt += "Updates:\t";
            for (i in SHARED_NOTE..FAMILY_TYPE) {
                txt.append(getDifferencesString(i))
            }
            card.findViewById<TextView>(R.id.confronto_sottotesto).apply {
                text = txt.removeSuffix("\n")
                visibility = View.VISIBLE
            }
        }
        card.findViewById<View>(R.id.confronto_data).visibility = View.GONE
    }

    private fun getDifferencesString(@Type type: Int): String {
        val singulars = mapOf(
            SHARED_NOTE to R.string.shared_note,
            SUBMITTER to R.string.submitter,
            REPOSITORY to R.string.repository,
            SHARED_MEDIA_TYPE to R.string.shared_media,
            SOURCE_TYPE to R.string.source,
            PERSON_TYPE to R.string.person,
            FAMILY_TYPE to R.string.family
        )
        val plurals = mapOf(
            SHARED_NOTE to R.string.shared_notes,
            SUBMITTER to R.string.submitters,
            REPOSITORY to R.string.repositories,
            SHARED_MEDIA_TYPE to R.string.shared_medias,
            SOURCE_TYPE to R.string.sources,
            PERSON_TYPE to R.string.persons,
            FAMILY_TYPE to R.string.families
        )
        val numChanges = Comparison.list.count { it.type == type }
        return if (numChanges > 0) {
            """		+$numChanges ${getString((if (numChanges == 1) singulars[type] else plurals[type])!!).lowercase(Locale.getDefault())}
"""
        } else ""
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Comparison.reset() // resets the Comparison singleton
    }
}