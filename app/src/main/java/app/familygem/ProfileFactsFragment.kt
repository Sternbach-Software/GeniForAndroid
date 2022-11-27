package app.familygem

import app.familygem.DetailActivity.Companion.writeAddress
import app.familygem.Memory.Companion.add
import app.familygem.constant.Gender.Companion.isFemale
import app.familygem.Memory.Companion.setInstanceAndAllSubsequentToNull
import android.os.Bundle
import app.familygem.R
import android.widget.LinearLayout
import app.familygem.TypeView
import app.familygem.U
import app.familygem.ProfileFactsFragment
import android.widget.TextView
import android.content.DialogInterface
import app.familygem.Memory
import android.content.Intent
import android.view.*
import app.familygem.detail.NameActivity
import app.familygem.detail.EventActivity
import app.familygem.detail.ExtensionActivity
import android.view.ContextMenu.ContextMenuInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import app.familygem.ProfileActivity
import app.familygem.GedcomDateConverter
import app.familygem.DetailActivity
import org.folg.gedcom.model.*
import java.util.ArrayList
import java.util.LinkedHashMap

class ProfileFactsFragment : Fragment() {
    var one: Person? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val eventsView = inflater.inflate(R.layout.individuo_scheda, container, false)
        if (Global.gc != null) {
            val layout = eventsView.findViewById<LinearLayout>(R.id.contenuto_scheda)
            one = Global.gc!!.getPerson(Global.indi)
            if (one != null) {
                for (name in one!!.names) {
                    var title = getString(R.string.name)
                    if (name.type != null && !name.type.isEmpty()) {
                        title += " (" + TypeView.getTranslatedType(
                            name.type,
                            TypeView.Combo.NAME
                        ) + ")"
                    }
                    placeEvent(layout, title, U.firstAndLastName(name, " "), name)
                }
                for (fact in one!!.eventsFacts) {
                    placeEvent(layout, writeEventTitle(fact), writeEventText(fact), fact)
                }
                for (est in U.findExtensions(one!!)) {
                    placeEvent(layout, est.name, est.text, est.gedcomTag)
                }
                U.placeNotes(layout, one!!, true)
                U.placeSourceCitations(layout, one!!)
                U.placeChangeDate(layout, one!!.change)
            }
        }
        return eventsView
    }

    /**
     * Find out if it's a name with name pieces or a suffix in the value
     */
    fun complexName(n: Name): Boolean {
        // Name pieces
        val hasAllFields /*TODO improve translation of ricco*/ =
            n.given != null || n.surname != null || n.prefix != null || n.surnamePrefix != null || n.suffix != null || n.fone != null || n.romn != null
        // Something after the surname
        var name = n.value
        var hasSuffix = false
        if (name != null) {
            name = name.trim { it <= ' ' }
            if (name.lastIndexOf('/') < name.length - 1) hasSuffix = true
        }
        return hasAllFields || hasSuffix
    }

    private var chosenSex = 0
    private fun placeEvent(layout: LinearLayout, title: String, text: String, `object`: Any) {
        val eventView = LayoutInflater.from(layout.context)
            .inflate(R.layout.individuo_eventi_pezzo, layout, false)
        layout.addView(eventView)
        (eventView.findViewById<View>(R.id.evento_titolo) as TextView).text = title
        val textView = eventView.findViewById<TextView>(R.id.evento_testo)
        if (text.isEmpty()) textView.visibility = View.GONE else textView.text = text
        if (Global.settings!!.expert && `object` is SourceCitationContainer) {
            val sourceCitations = `object`.sourceCitations
            val sourceView = eventView.findViewById<TextView>(R.id.evento_fonti)
            if (!sourceCitations.isEmpty()) {
                sourceView.text = sourceCitations.size.toString()
                sourceView.visibility = View.VISIBLE
            }
        }
        val otherLayout = eventView.findViewById<LinearLayout>(R.id.evento_altro)
        if (`object` is NoteContainer) U.placeNotes(otherLayout, `object`, false)
        eventView.setTag(R.id.tag_object, `object`)
        registerForContextMenu(eventView)
        if (`object` is Name) {
            U.placeMedia(otherLayout, `object`, false)
            eventView.setOnClickListener { v: View? ->
                // If it is a complex name, it proposes entering expert mode
                if (!Global.settings!!.expert && complexName(`object`)) {
                    AlertDialog.Builder(requireContext()).setMessage(R.string.complex_tree_advanced_tools)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, i: Int ->
                            Global.settings!!.expert = true
                            Global.settings!!.save()
                            add(`object`)
                            startActivity(Intent(context, NameActivity::class.java))
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, i: Int ->
                            add(`object`)
                            startActivity(Intent(context, NameActivity::class.java))
                        }.show()
                } else {
                    add(`object`)
                    startActivity(Intent(context, NameActivity::class.java))
                }
            }
        } else if (`object` is EventFact) {
            // Sex fact
            if (`object`.tag != null && `object`.tag == "SEX") {
                val sexes: MutableMap<String, String> = LinkedHashMap()
                sexes["M"] = getString(R.string.male)
                sexes["F"] = getString(R.string.female)
                sexes["U"] = getString(R.string.unknown)
                textView.text = text
                chosenSex = 0
                for ((key, value) in sexes) {
                    if (text == key) {
                        textView.text = value
                        break
                    }
                    chosenSex++
                }
                if (chosenSex > 2) chosenSex = -1
                eventView.setOnClickListener { view: View ->
                    AlertDialog.Builder(view.context)
                        .setSingleChoiceItems(
                            sexes.values.toTypedArray(),
                            chosenSex
                        ) { dialog: DialogInterface, item: Int ->
                            `object`.value = ArrayList(sexes.keys)[item]
                            updateMaritalRoles(one)
                            dialog.dismiss()
                            refresh()
                            U.save(true, one)
                        }.show()
                }
            } else { // All other events
                U.placeMedia(otherLayout, `object`, false)
                eventView.setOnClickListener { v: View? ->
                    add(`object`)
                    startActivity(Intent(context, EventActivity::class.java))
                }
            }
        } else if (`object` is GedcomTag) {
            eventView.setOnClickListener { v: View? ->
                add(`object`)
                startActivity(Intent(context, ExtensionActivity::class.java))
            }
        }
    }

    // Contextual menu
    var pieceView: View? = null
    var pieceObject: Any? = null
    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        // menuInfo as usual is null
        pieceView = view
        pieceObject = view.getTag(R.id.tag_object)
        if (pieceObject is Name) {
            menu.add(0, 200, 0, R.string.copy)
            if (one!!.names.indexOf(pieceObject) > 0) menu.add(0, 201, 0, R.string.move_up)
            if (one!!.names.indexOf(pieceObject) < one!!.names.size - 1) menu.add(
                0,
                202,
                0,
                R.string.move_down
            )
            menu.add(0, 203, 0, R.string.delete)
        } else if (pieceObject is EventFact) {
            if (view.findViewById<View>(R.id.evento_testo).visibility == View.VISIBLE) menu.add(
                0,
                210,
                0,
                R.string.copy
            )
            if (one!!.eventsFacts.indexOf(pieceObject) > 0) menu.add(0, 211, 0, R.string.move_up)
            if (one!!.eventsFacts.indexOf(pieceObject) < one!!.eventsFacts.size - 1) menu.add(
                0,
                212,
                0,
                R.string.move_down
            )
            menu.add(0, 213, 0, R.string.delete)
        } else if (pieceObject is GedcomTag) {
            menu.add(0, 220, 0, R.string.copy)
            menu.add(0, 221, 0, R.string.delete)
        } else if (pieceObject is Note) {
            if ((view.findViewById<View>(R.id.note_text) as TextView).text.length > 0) menu.add(
                0,
                225,
                0,
                R.string.copy
            )
            if ((pieceObject as Note).id != null) menu.add(0, 226, 0, R.string.unlink)
            menu.add(0, 227, 0, R.string.delete)
        } else if (pieceObject is SourceCitation) {
            menu.add(0, 230, 0, R.string.copy)
            menu.add(0, 231, 0, R.string.delete)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val names = one!!.names
        val facts = one!!.eventsFacts
        var toUpdateId = 0 // what to update after the change
        when (item.itemId) {
            200, 210, 220 -> {
                U.copyToClipboard(
                    (pieceView!!.findViewById<View>(R.id.evento_titolo) as TextView).text,
                    (pieceView!!.findViewById<View>(R.id.evento_testo) as TextView).text
                )
                return true
            }
            201 -> {
                names.add(names.indexOf(pieceObject) - 1, pieceObject as Name?)
                names.removeAt(names.lastIndexOf(pieceObject))
                toUpdateId = 2
            }
            202 -> {
                names.add(names.indexOf(pieceObject) + 2, pieceObject as Name?)
                names.removeAt(names.indexOf(pieceObject))
                toUpdateId = 2
            }
            203 -> {
                if (U.preserve(pieceObject)) return false
                one!!.names.remove(pieceObject)
                setInstanceAndAllSubsequentToNull(pieceObject!!)
                pieceView!!.visibility = View.GONE
                toUpdateId = 2
            }
            211 -> {
                facts.add(facts.indexOf(pieceObject) - 1, pieceObject as EventFact?)
                facts.removeAt(facts.lastIndexOf(pieceObject))
                toUpdateId = 1
            }
            212 -> {
                facts.add(facts.indexOf(pieceObject) + 2, pieceObject as EventFact?)
                facts.removeAt(facts.indexOf(pieceObject))
                toUpdateId = 1
            }
            213 -> {
                // todo Confirm delete
                one!!.eventsFacts.remove(pieceObject)
                setInstanceAndAllSubsequentToNull(pieceObject!!)
                pieceView!!.visibility = View.GONE
            }
            221 -> (pieceObject as GedcomTag?)?.let { U.deleteExtension(it, one, pieceView) }
            225 -> {
                U.copyToClipboard(
                    getText(R.string.note),
                    (pieceView!!.findViewById<View>(R.id.note_text) as TextView).text
                )
                return true
            }
            226 -> U.disconnectNote(pieceObject as Note, one!!, pieceView)
            227 -> {
                val heads = U.deleteNote(pieceObject as Note, pieceView)
                U.save(true, *heads)
                refresh()
                return true
            }
            230 -> {
                U.copyToClipboard(
                    getText(R.string.source_citation),
                    """
                        ${(pieceView!!.findViewById<View>(R.id.fonte_testo) as TextView).text}
                        ${(pieceView!!.findViewById<View>(R.id.citazione_testo) as TextView).text}
                        """.trimIndent()
                )
                return true
            }
            231 -> {
                // todo confirm : Do you want to delete this source citation? The source will continue to exist.
                one!!.sourceCitations.remove(pieceObject)
                setInstanceAndAllSubsequentToNull(pieceObject!!)
                pieceView!!.visibility = View.GONE
            }
            else -> return false
        }
        refresh()
        U.save(true, one)
        return true
    }

    /**
     * Update content
     */
    fun refresh() {
        (requireActivity() as ProfileActivity).refresh()
    }

    companion object {
        /**
         * Compose the title of an event of the person
         */
        fun writeEventTitle(event: EventFact): String {
            var str = 0
            when (event.tag) {
                "SEX" -> str = R.string.sex
                "BIRT" -> str = R.string.birth
                "BAPM" -> str = R.string.baptism
                "BURI" -> str = R.string.burial
                "DEAT" -> str = R.string.death
                "EVEN" -> str = R.string.event
                "OCCU" -> str = R.string.occupation
                "RESI" -> str = R.string.residence
            }
            var txt: String
            txt = if (str != 0) Global.context!!.getString(str) else event.displayType
            if (event.type != null) txt += " (" + event.type + ")"
            return txt
        }

        fun writeEventText(event: EventFact): String {
            var txt = ""
            if (event.value != null) {
                txt = if (event.value == "Y" && event.tag != null &&
                    (event.tag == "BIRT" || event.tag == "CHR" || event.tag == "DEAT")
                ) Global.context!!.getString(R.string.yes) else event.value
                txt += "\n"
            }
            //if( event.getType() != null ) txt += event.getType() + "\n"; // Included in event title
            if (event.date != null) txt += """
     ${GedcomDateConverter(event.date).writeDateLong()}
     
     """.trimIndent()
            if (event.place != null) txt += """
     ${event.place}
     
     """.trimIndent()
            val address = event.address
            if (address != null) txt += """
     ${writeAddress(address, true)}
     
     """.trimIndent()
            if (event.cause != null) txt += event.cause
            return txt.trim { it <= ' ' }
        }

        /**
         * In all marital families, remove the spouse refs of 'person' and add one corresponding to the gender
         * It is especially useful in case of Gedcom export to have the HUSB and WIFE aligned with the sex
         */
        fun updateMaritalRoles(person: Person?) {
            val spouseRef = SpouseRef()
            spouseRef.ref = person!!.id
            var removed = false
            for (fam in person.getSpouseFamilies(Global.gc)) {
                if (isFemale(person)) { // Female 'person' will become a wife
                    val husbandRefs = fam.husbandRefs.iterator()
                    while (husbandRefs.hasNext()) {
                        val hr = husbandRefs.next().ref
                        if (hr != null && hr == person.id) {
                            husbandRefs.remove()
                            removed = true
                        }
                    }
                    if (removed) {
                        fam.addWife(spouseRef)
                        removed = false
                    }
                } else { // For all other sexs 'person' will become husband
                    val wifeRefs = fam.wifeRefs.iterator()
                    while (wifeRefs.hasNext()) {
                        val wr = wifeRefs.next().ref
                        if (wr != null && wr == person.id) {
                            wifeRefs.remove()
                            removed = true
                        }
                    }
                    if (removed) {
                        fam.addHusband(spouseRef)
                        removed = false
                    }
                }
            }
        }
    }
}