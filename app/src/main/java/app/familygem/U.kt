package app.familygem

import app.familygem.TreesActivity.Companion.readJson
import app.familygem.Memory.Companion.setInstanceAndAllSubsequentToNull
import app.familygem.F.showMainImageForPerson
import app.familygem.constant.Gender.Companion.isMale
import app.familygem.constant.Gender.Companion.isFemale
import app.familygem.Memory.Companion.add
import app.familygem.Memory.Companion.secondToLastObject
import app.familygem.Memory.Companion.firstObject
import app.familygem.Memory.Companion.clearStackAndRemove
import app.familygem.SourcesFragment.Companion.sourceTitle
import app.familygem.MediaGalleryAdapter.Companion.setupMedia
import app.familygem.F.showImage
import app.familygem.detail.RepositoryRefActivity.Companion.putRepository
import app.familygem.ListOfPeopleFragment.Companion.countRelatives
import app.familygem.NewTreeActivity.Companion.createHeader
import app.familygem.Memory.Companion.replaceFirst
import app.familygem.FamiliesFragment.Companion.deleteFamily
import kotlin.jvm.JvmOverloads
import org.joda.time.Years
import org.joda.time.Months
import org.joda.time.Days
import org.folg.gedcom.parser.ModelParser
import android.widget.LinearLayout
import android.view.LayoutInflater
import android.widget.TextView
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import app.familygem.detail.NoteActivity
import app.familygem.visitor.NoteReferences
import app.familygem.visitor.FindStack
import app.familygem.MediaGalleryAdapter.MediaIconsRecyclerView
import app.familygem.visitor.MediaListContainer.MediaWithContainer
import app.familygem.detail.SourceCitationActivity
import app.familygem.detail.SourceActivity
import app.familygem.detail.FamilyActivity
import app.familygem.detail.ImageActivity
import app.familygem.detail.AuthorActivity
import androidx.appcompat.content.res.AppCompatResources
import app.familygem.detail.ChangesActivity
import com.google.android.material.navigation.NavigationView
import android.widget.Toast
import com.google.gson.JsonPrimitive
import app.familygem.Settings.Share
import android.app.Activity
import android.content.*
import android.widget.ArrayAdapter
import app.familygem.NewRelativeDialog.FamilyItem
import android.widget.EditText
import app.familygem.visitor.MediaContainers
import app.familygem.visitor.NoteContainers
import app.familygem.visitor.ListOfSourceCitations
import com.google.android.material.textfield.TextInputLayout
import android.text.TextWatcher
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.constant.Format
import app.familygem.constant.intdefs.*
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.*
import org.folg.gedcom.parser.JsonParser
import org.joda.time.LocalDate
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

/**
 * Static methods used all across the app
 */
object U {
    fun s(id: Int): String {
        return Global.context!!.getString(id)
    }

    /**
     * To use where it happens that 'Global.gc' could be null to reload it
     */
    fun ensureGlobalGedcomNotNull(gc: Gedcom?) {
        if (gc == null) Global.gc = readJson(Global.settings!!.openTree)
    }

    /**
     * Id of the main person of a GEDCOM or null
     */
    fun getRootId(gedcom: Gedcom, tree: Settings.Tree): String? {
        if (tree.root != null) {
            val root = gedcom.getPerson(tree.root)
            if (root != null) return root.id
        }
        return findRoot(gedcom)
    }

    /**
     * @return the id of the initial Person of a Gedcom
     * Todo Integrate into [.getRootId] ???
     */
    fun findRoot(gc: Gedcom): String? {
        if (gc.header != null) if (tagValue(gc.header.extensions, "_ROOT") != null) return tagValue(
            gc.header.extensions,
            "_ROOT"
        )
        return if (!gc.people.isEmpty()) gc.people[0].id else null
    }

    /**
     * receives a Person and returns string with primary first and last name
     * riceve una Person e restituisce stringa con nome e cognome principale
     */
    fun properName(person: Person?): String {
        return properName(person, false)
    }

    fun properName(person: Person?, twoLines: Boolean): String {
        return if (person != null && !person.names.isEmpty()) firstAndLastName(
            person.names[0],
            if (twoLines) "\n" else " "
        ) else "[" + s(R.string.no_name) + "]"
    }

    /**
     * The given name of a person or something
     */
    fun givenName(person: Person): String {
        return if (person.names.isEmpty()) {
            "[" + s(R.string.no_name) + "]"
        } else {
            var given = ""
            val name = person.names[0]
            if (name.value != null) {
                val value = name.value.trim { it <= ' ' }
                if (value.indexOf('/') == 0 && value.lastIndexOf('/') == 1 && value.length > 2) // Suffix only
                    given =
                        value.substring(2) else if (value.indexOf('/') == 0 && value.lastIndexOf('/') > 1) // Surname only
                    given = value.substring(
                        1,
                        value.lastIndexOf('/')
                    ) else if (value.indexOf('/') > 0) // Name and surname
                    given =
                        value.substring(
                            0,
                            value.indexOf('/')
                        ) else if (!value.isEmpty()) // Name only
                    given = value
            } else if (name.given != null) {
                given = name.given
            } else if (name.surname != null) {
                given = name.surname
            }
            given = given.trim { it <= ' ' }
            if (given.isEmpty()) "[" + s(R.string.empty_name) + "]" else given
        }
    }

    /**
     * receives a Person and returns the title of nobility
     */
    fun title(p: Person): String {
        // GEDCOM standard INDI.TITL
        for (ef in p.eventsFacts) if (ef.tag != null && ef.tag == "TITL" && ef.value != null) return ef.value
        // So instead it takes INDI.NAME._TYPE.TITL, old method of org.folg.gedcom
        for (n in p.names) if (n.type != null && n.type == "TITL" && n.value != null) return n.value
        return ""
    }

    /**
     * @param n The Name of a person
     * @param divider Can be a space " " or a new line "\n"
     * @return The full, decorated name
     */
    fun firstAndLastName(n: Name, divider: String): String {
        var fullName = ""
        if (n.value != null) {
            val raw = n.value.trim { it <= ' ' }
            val slashPos = raw.indexOf('/')
            val lastSlashPos = raw.lastIndexOf('/')
            fullName = if (slashPos > -1) // If there is a last name between '/'
                raw.substring(0, slashPos).trim { it <= ' ' } // first name
            else  // Or it's just a first name without a last name
                raw
            if (n.nickname != null) fullName += divider + "\"" + n.nickname + "\""
            if (slashPos < lastSlashPos) fullName += divider + raw.substring(
                slashPos + 1,
                lastSlashPos
            ).trim { it <= ' ' } // surname
            if (lastSlashPos > -1 && raw.length - 1 > lastSlashPos) fullName += " " + raw.substring(
                lastSlashPos + 1
            ).trim { it <= ' ' } // after the surname
        } else {
            if (n.prefix != null) fullName = n.prefix
            if (n.given != null) fullName += " " + n.given
            if (n.nickname != null) fullName += divider + "\"" + n.nickname + "\""
            if (n.surname != null) fullName += divider + n.surname
            if (n.suffix != null) fullName += " " + n.suffix
        }
        fullName = fullName.trim { it <= ' ' }
        return if (fullName.isEmpty()) "[" + s(R.string.empty_name) + "]" else fullName
    }

    /**
     * Return the surname of a person, optionally lowercase for comparison. Can return null.
     */
    @JvmOverloads
    fun surname(person: Person, lowerCase: Boolean = false): String? {
        var surname: String? = null
        if (!person.names.isEmpty()) {
            val name = person.names[0]
            val value = name.value
            if (value != null && value.lastIndexOf('/') - value.indexOf('/') > 1) //value.indexOf('/') < value.lastIndexOf('/')
                surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/'))
                    .trim { it <= ' ' } else if (name.surname != null) surname =
                name.surname.trim { it <= ' ' }
        }
        if (surname != null) {
            if (surname.isEmpty()) return null else if (lowerCase) surname =
                surname.lowercase(Locale.getDefault())
        }
        return surname
    }

    /**
     * Receives a person and finds out if he is dead or buried
     */
    fun isDead(person: Person): Boolean {
        for (eventFact in person.eventsFacts) {
            if (eventFact.tag == "DEAT" || eventFact.tag == "BURI") return true
        }
        return false
    }

    /**
     * Check whether a family has a marriage event of type 'marriage'
     */
    fun areMarried(family: Family?): Boolean {
        if (family != null) {
            for (eventFact in family.eventsFacts) {
                val tag = eventFact.tag
                if (tag == "MARR") {
                    val type = eventFact.type
                    if (type == null || type.isEmpty() || type == "marriage" || type == "civil" || type == "religious" || type == "common law") return true
                } else if (tag == "MARB" || tag == "MARC" || tag == "MARL" || tag == "MARS") return true
            }
        }
        return false
    }

    /** Write the basic dates of a person's life with the age.
     * @param person The dude to investigate
     * @param vertical Dates and age can be written on multiple lines
     * @return A string with date of birth an death
     */
    fun twoDates(person: Person, vertical: Boolean): String {
        var text = ""
        var endYear = ""
        var start: GedcomDateConverter? = null
        var end: GedcomDateConverter? = null
        var ageBelow = false
        val facts = person.eventsFacts
        // Birth date
        for (fact in facts) {
            if (fact?.tag == "BIRT" && fact.date != null) {
                start = GedcomDateConverter(fact.date)
                text = start.writeDate(false)
                break
            }
        }
        // Death date
        for (fact in facts) {
            if (fact?.tag == "DEAT" && fact.date != null) {
                end = GedcomDateConverter(fact.date)
                endYear = end.writeDate(false)
                if (text.isNotEmpty() && endYear.isNotEmpty()) {
                    if (vertical && (text.length > 7 || endYear.length > 7)) {
                        text += "\n"
                        ageBelow = true
                    } else {
                        text += " – "
                    }
                }
                text += endYear
                break
            }
        }
        // Otherwise find the first available date
        if (text.isEmpty()) {
            for (fact in facts) {
                if (fact.date != null) {
                    return GedcomDateConverter(fact.date).writeDate(false)
                }
            }
        }
        // Add the age between parentheses
        if (start?.isSingleKind == true && !start.data1.isFormat(Format.D_M)) {
            val startDate = LocalDate(start.data1.date) // Converted to joda time
            // If the person is still alive the end is now
            val now = LocalDate.now()
            if (end == null && (startDate.isBefore(now) || startDate.isEqual(now))
                && Years.yearsBetween(startDate, now).years <= 120 && !isDead(person)
            ) {
                end = GedcomDateConverter(now.toDate())
                endYear = end.writeDate(false)
            }
            if (end?.isSingleKind == true && !end.data1.isFormat(Format.D_M) && endYear.isNotEmpty()) { // Plausible dates
                val endDate = LocalDate(end.data1.date)
                if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
                    var units = ""
                    var age = Years.yearsBetween(startDate, endDate).years
                    if (age < 2) {
                        // Without day and/or month the years start at 1 January
                        age = Months.monthsBetween(startDate, endDate).months
                        units = " " + Global.context!!.getText(R.string.months)
                        if (age < 2) {
                            age = Days.daysBetween(startDate, endDate).days
                            units = " " + Global.context!!.getText(R.string.days)
                        }
                    }
                    text += if (ageBelow) "\n" else " "
                    text += "($age$units)"
                }
            }
        }
        return text
    }

    /**
     * Write the two main places of a person (initial – final) or null
     */
    fun twoPlaces(person: Person): String? {
        val facts = person.eventsFacts
        // One single event
        if (facts.size == 1) {
            val place = facts[0].place
            if (place != null) return stripCommas(place)
        } // Sex and another event
        else if (facts.size == 2 && ("SEX" == facts[0].tag || "SEX" == facts[1].tag)) {
            val place: String?
            place = if ("SEX" == facts[0].tag) facts[1].place else facts[0].place
            if (place != null) return stripCommas(place)
        } // Multiple events
        else if (facts.size >= 2) {
            val places = arrayOfNulls<String>(7)
            for (ef in facts) {
                val place = ef.place
                if (place != null) {
                    when (ef.tag) {
                        "BIRT" -> places[0] = place
                        "BAPM" -> places[1] = place
                        "DEAT" -> places[4] = place
                        "CREM" -> places[5] = place
                        "BURI" -> places[6] = place
                        else -> {
                            if (places[2] == null) // First of other events
                                places[2] = place
                            if (place != places[2]) places[3] = place // Last of other events
                        }
                    }
                }
            }
            var text: String? = null
            var i: Int
            // Write initial place
            i = 0
            while (i < places.size) {
                val place = places[i]
                if (place != null) {
                    text = stripCommas(place)
                    break
                }
                i++
            }
            // Priority to death event as final place
            if (text != null && i < 4 && places[4] != null) {
                val place = stripCommas(places[4])
                if (place != text) text += " – $place"
            } else {
                for (j in places.size - 1 downTo i + 1) {
                    var place = places[j]
                    if (place != null) {
                        place = stripCommas(place)
                        if (place != text) {
                            text += " – $place"
                            break
                        }
                    }
                }
            }
            return text
        }
        return null
    }

    /**
     * gets a Gedcom-style location and returns the first name between the commas
     */
    private fun stripCommas(place: String?): String {
        // skip leading commas for places type ',,,England'
        var place = place
        var start = 0
        for (c in place!!.toCharArray()) {
            if (c != ',' && c != ' ') break
            start++
        }
        place = place.substring(start)
        if (place.indexOf(",") > 0) place = place.substring(0, place.indexOf(","))
        return place
    }

    /**
     * Extracts only digits from a string that can also contain letters
     */
    fun extractNum(id: String): Int {
        //return Integer.parseInt( id.replaceAll("\\D+","") );	// synthetic but slow //sintetico ma lento
        var num = 0
        var x = 1
        for (i in id.length - 1 downTo 0) {
            val c = id[i].code
            if (c > 47 && c < 58) {
                num += (c - 48) * x
                x *= 10 //to convert positional notation into a base-10 representation
            }
        }
        return num
    }

    var max = 0

    /**
     * Generate the new id following the existing ones
     */
    fun newID(gc: Gedcom, clazz: Class<*>): String {
        max = 0
        var pre = ""
        if (clazz == Note::class.java) {
            pre = "N"
            for (n in gc.notes) calculateMax(n)
        } else if (clazz == Submitter::class.java) {
            pre = "U"
            for (a in gc.submitters) calculateMax(a)
        } else if (clazz == Repository::class.java) {
            pre = "R"
            for (r in gc.repositories) calculateMax(r)
        } else if (clazz == Media::class.java) {
            pre = "M"
            for (m in gc.media) calculateMax(m)
        } else if (clazz == Source::class.java) {
            pre = "S"
            for (f in gc.sources) calculateMax(f)
        } else if (clazz == Person::class.java) {
            pre = "I"
            for (p in gc.people) calculateMax(p)
        } else if (clazz == Family::class.java) {
            pre = "F"
            for (f in gc.families) calculateMax(f)
        }
        return pre + (max + 1)
    }

    private fun calculateMax(`object`: Any) {
        try {
            val idString = `object`.javaClass.getMethod("getId").invoke(`object`) as String
            val num = extractNum(idString)
            if (num > max) max = num
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copy text to clipboard
     */
    fun copyToClipboard(label: CharSequence?, text: CharSequence?) {
        val clipboard =
            Global.context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    /**
     * Returns the list of extensions
     */
    fun findExtensions(container: ExtensionContainer): List<Extension> {
        if (container.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) != null) {
            val list: MutableList<Extension> = ArrayList()
            for (est in container.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) as List<GedcomTag>) {
                var text = traverseExtension(est, 0)
                if (text.endsWith("\n")) text = text.substring(0, text.length - 1)
                list.add(Extension(est.tag, text, est))
            }
            return list
        }
        return emptyList()
    }

    /**
     * Constructs a text with the recursive content of the extension
     */
    fun traverseExtension(tag: GedcomTag, grade: Int): String {
        var grade = grade
        val text = StringBuilder()
        if (grade > 0) text.append(tag.tag).append(" ")
        if (tag.value != null) text.append(tag.value)
            .append("\n") else if (tag.id != null) text.append(tag.id)
            .append("\n") else if (tag.ref != null) text.append(tag.ref).append("\n")
        for (piece in tag.children) text.append(traverseExtension(piece, ++grade))
        return text.toString()
    }

    fun deleteExtension(extension: GedcomTag, container: Any?, view: View?) {
        if (container is ExtensionContainer) { // IndividualEventsFragment
            val exc = container
            val list =
                exc.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) as MutableList<GedcomTag>
            list.remove(extension)
            if (list.isEmpty()) exc.extensions.remove(ModelParser.MORE_TAGS_EXTENSION_KEY)
            if (exc.extensions.isEmpty()) exc.extensions = null
        } else if (container is GedcomTag) { // DetailActivity
            val gt = container
            gt.children.remove(extension)
            if (gt.children.isEmpty()) gt.children = null
        }
        setInstanceAndAllSubsequentToNull(extension)
        if (view != null) view.visibility = View.GONE
    }

    /**
     * Returns the value of a given tag in an extension ([GedcomTag])
     */
    fun tagValue(extensionMap: Map<String?, Any>, tagName: String): String? {
        for ((_, value) in extensionMap) {
            val tagList: List<GedcomTag> = value as ArrayList<GedcomTag>
            for (piece in tagList) {
                //l( piece.getTag() +" "+ piece.getValue() );
                if (piece.tag == tagName) {
                    return if (piece.id != null) piece.id else if (piece.ref != null) piece.ref else piece.value
                }
            }
        }
        return null
    }
    // Methods of creating list elements
    /**
     * Add a generic title-text entry to a Layout. Used seriously only by [ChangesActivity]
     */
    fun place(layout: LinearLayout, tit: String?, text: String?) {
        val pieceView =
            LayoutInflater.from(layout.context).inflate(R.layout.pezzo_fatto, layout, false)
        layout.addView(pieceView)
        (pieceView.findViewById<View>(R.id.fatto_titolo) as TextView).text = tit
        val textView = pieceView.findViewById<TextView>(R.id.fatto_testo)
        if (text == null) textView.visibility = View.GONE else {
            textView.text = text
            //((TextView)pieceView.findViewById( R.id.fatto_edita )).setText( text );
        }
        //((Activity)layout.getContext()).registerForContextMenu( pieceView );
    }

    /**
     * Composes text with details of an individual and places it in text view
     * also returns the same text for [TreeComparatorActivity]
     */
    fun details(person: Person, detailsView: TextView?): String {
        var dates = twoDates(person, false)
        val places = twoPlaces(person)
        if (dates.isEmpty() && places == null && detailsView != null) {
            detailsView.visibility = View.GONE
        } else {
            if (dates.isNotEmpty() && places != null && (dates.length >= 10 || places.length >= 20)) dates += """
     
     $places
     """.trimIndent() else if (places != null) dates += "   $places"
            if (detailsView != null) {
                detailsView.text = dates.trim { it <= ' ' }
                detailsView.visibility = View.VISIBLE
            }
        }
        return dates.trim { it <= ' ' }
    }

    fun placeIndividual(layout: LinearLayout, person: Person, role: String?): View {
        val indiView =
            LayoutInflater.from(layout.context).inflate(R.layout.piece_person, layout, false)
        layout.addView(indiView)
        val roleView = indiView.findViewById<TextView>(R.id.person_info)
        if (role == null) roleView.visibility = View.GONE else roleView.text = role
        val nameView = indiView.findViewById<TextView>(R.id.person_name)
        val name = properName(person)
        if (name.isEmpty() && role != null) nameView.visibility = View.GONE else nameView.text =
            name
        val titleView = indiView.findViewById<TextView>(R.id.person_title)
        val title = title(person)
        if (title.isEmpty()) titleView.visibility = View.GONE else titleView.text = title
        details(person, indiView.findViewById(R.id.person_details))
        showMainImageForPerson(Global.gc!!, person, indiView.findViewById(R.id.person_image))
        if (!isDead(person)) indiView.findViewById<View>(R.id.person_mourning).visibility =
            View.GONE
        if (isMale(person)) indiView.findViewById<View>(R.id.person_border)
            .setBackgroundResource(R.drawable.casella_bordo_maschio) else if (isFemale(person)) indiView.findViewById<View>(
            R.id.person_border
        ).setBackgroundResource(R.drawable.casella_bordo_femmina)
        indiView.tag = person.id
        return indiView
    }

    /**
     * Place all the notes of an object
     */
    fun placeNotes(layout: LinearLayout, container: Any, detailed: Boolean) {
        for (nota in (container as NoteContainer).getAllNotes(Global.gc)) {
            placeNote(layout, nota, detailed)
        }
    }

    /**
     * Place a single note on a layout, with details or not
     */
    fun placeNote(layout: LinearLayout, note: Note, detailed: Boolean) {
        val context = layout.context
        val noteView = LayoutInflater.from(context).inflate(R.layout.piece_note, layout, false)
        layout.addView(noteView)
        val textView = noteView.findViewById<TextView>(R.id.note_text)
        textView.text = note.value
        val sourceCiteNum = note.sourceCitations.size
        val sourceCiteView = noteView.findViewById<TextView>(R.id.note_sources)
        if (sourceCiteNum > 0 && detailed) sourceCiteView.text =
            sourceCiteNum.toString() else sourceCiteView.visibility = View.GONE
        textView.ellipsize = TextUtils.TruncateAt.END
        if (detailed) {
            textView.maxLines = 10
            noteView.setTag(R.id.tag_object, note)
            if (context is ProfileActivity) { // IndividualEventsFragment
                (context as AppCompatActivity).supportFragmentManager
                    .findFragmentByTag("android:switcher:" + R.id.profile_pager + ":1") // non garantito in futuro
                    ?.registerForContextMenu(noteView)
            } else if (layout.id != R.id.dispensa_scatola) // in AppCompatActivities except in the pantry (??)
                (context as AppCompatActivity).registerForContextMenu(noteView)
            noteView.setOnClickListener { v: View? ->
                if (note.id != null) Memory.setFirst(note) else add(note)
                context.startActivity(Intent(context, NoteActivity::class.java))
            }
        } else {
            textView.maxLines = 3
        }
    }

    fun disconnectNote(nota: Note, container: Any, view: View?) {
        val list = (container as NoteContainer).noteRefs
        for (ref in list) if (ref.getNote(Global.gc) == nota) {
            list.remove(ref)
            break
        }
        container.noteRefs = list
        if (view != null) view.visibility = View.GONE
    }

    /**
     * Delete an online or shared Note
     * @return an array of modified parents
     */
    fun deleteNote(note: Note, view: View?): Array<Any?> {
        val heads: MutableSet<Any?>
        if (note.id != null) { // OBJECT note
            // First remove the refs to the note with a nice Visitor
            val noteEliminator = NoteReferences(Global.gc!!, note.id, true)
            Global.gc!!.accept(noteEliminator)
            Global.gc!!.notes.remove(note) // ok removes it if it is an object note
            heads = noteEliminator.founders
            if (Global.gc!!.notes.isEmpty()) Global.gc!!.notes = null
        } else { // LOCAL note
            FindStack(Global.gc!!, note)
            val nc = secondToLastObject as NoteContainer?
            nc!!.notes.remove(note) //only removes if it is a local note, not if object note
            if (nc.notes.isEmpty()) nc.notes = null
            heads = HashSet()
            heads.add(firstObject())
            clearStackAndRemove()
        }
        setInstanceAndAllSubsequentToNull(note)
        if (view != null) view.visibility = View.GONE
        return heads.toTypedArray()
    }

    /**
     * List all media of a container object
     */
    fun placeMedia(layout: LinearLayout, container: Any, detailed: Boolean) {
        val recyclerView: RecyclerView = MediaIconsRecyclerView(layout.context, detailed)
        recyclerView.setHasFixedSize(true)
        val layoutManager: RecyclerView.LayoutManager =
            GridLayoutManager(layout.context, if (detailed) 2 else 3)
        recyclerView.layoutManager = layoutManager
        val listaMedia: MutableList<MediaWithContainer> = ArrayList()
        for (med in (container as MediaContainer).getAllMedia(Global.gc)) listaMedia.add(
            MediaWithContainer(
                med!!, container
            )
        )
        val adapter = MediaGalleryAdapter(listaMedia, detailed)
        recyclerView.adapter = adapter
        layout.addView(recyclerView)
    }

    /**
     * Of an object it inserts the citations to the sources //Di un object inserisce le citazioni alle fonti
     */
    fun placeSourceCitations(layout: LinearLayout, container: Any) {
        if (Global.settings!!.expert) {
            val listOfSourceCitations: List<SourceCitation>
            listOfSourceCitations =
                if (container is Note) // Notes does not extend SourceCitationContainer
                    container.sourceCitations else (container as SourceCitationContainer).sourceCitations
            for (citation in listOfSourceCitations) {
                val citationView = LayoutInflater.from(layout.context)
                    .inflate(R.layout.pezzo_citazione_fonte, layout, false)
                layout.addView(citationView)
                if (citation.getSource(Global.gc) != null) // source CITATION
                    (citationView.findViewById<View>(R.id.fonte_testo) as TextView).text =
                        sourceTitle(
                            citation.getSource(
                                Global.gc
                            )
                        ) else  // source NOTE, or Source citation that has been deleted
                    citationView.findViewById<View>(R.id.citazione_fonte).visibility = View.GONE
                var t = ""
                if (citation.value != null) t += """
     ${citation.value}
     
     """.trimIndent()
                if (citation.page != null) t += """
     ${citation.page}
     
     """.trimIndent()
                if (citation.date != null) t += """
     ${citation.date}
     
     """.trimIndent()
                if (citation.text != null) t += """
     ${citation.text}
     
     """.trimIndent() // applies to both sourceNote and sourceCitation
                val textView = citationView.findViewById<TextView>(R.id.citazione_testo)
                if (t.isEmpty()) textView.visibility = View.GONE else textView.text =
                    t.substring(0, t.length - 1)
                // All the rest
                val otherLayout = citationView.findViewById<LinearLayout>(R.id.citazione_note)
                placeNotes(otherLayout, citation, false)
                placeMedia(otherLayout, citation, false)
                citationView.setTag(R.id.tag_object, citation)
                if (layout.context is ProfileActivity) { // IndividualEventsFragment
                    (layout.context as AppCompatActivity).supportFragmentManager
                        .findFragmentByTag("android:switcher:" + R.id.profile_pager + ":1")
                        ?.registerForContextMenu(citationView)
                } else  // AppCompatActivity
                    (layout.context as AppCompatActivity).registerForContextMenu(citationView)
                citationView.setOnClickListener { v: View? ->
                    val intent = Intent(layout.context, SourceCitationActivity::class.java)
                    add(citation)
                    layout.context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Inserts the reference to a source, with details or essential, into the box
     */
    fun placeSource(layout: LinearLayout, source: Source, detailed: Boolean) {
        val sourceView =
            LayoutInflater.from(layout.context).inflate(R.layout.pezzo_fonte, layout, false)
        layout.addView(sourceView)
        val textView = sourceView.findViewById<TextView>(R.id.fonte_testo)
        var txt = ""
        if (detailed) {
            if (source.title != null) txt = """
     ${source.title}
     
     """.trimIndent() else if (source.abbreviation != null) txt = """
     ${source.abbreviation}
     
     """.trimIndent()
            if (source.type != null) txt += """
     ${source.type.replace("\n".toRegex(), " ")}
     
     """.trimIndent()
            if (source.publicationFacts != null) txt += """
     ${source.publicationFacts.replace("\n".toRegex(), " ")}
     
     """.trimIndent()
            if (source.text != null) txt += source.text.replace("\n".toRegex(), " ")
            if (txt.endsWith("\n")) txt = txt.substring(0, txt.length - 1)
            val otherLayout = sourceView.findViewById<LinearLayout>(R.id.fonte_scatola)
            placeNotes(otherLayout, source, false)
            placeMedia(otherLayout, source, false)
            sourceView.setTag(R.id.tag_object, source)
            (layout.context as AppCompatActivity).registerForContextMenu(sourceView)
        } else {
            textView.maxLines = 2
            txt = sourceTitle(source)
        }
        textView.text = txt
        sourceView.setOnClickListener { v: View? ->
            Memory.setFirst(source)
            layout.context.startActivity(Intent(layout.context, SourceActivity::class.java))
        }
    }

    /**
     * The returned view is used by [SharingActivity]
     */
    fun linkPerson(layout: LinearLayout, p: Person, card: Int): View {
        val personView = LayoutInflater.from(layout.context)
            .inflate(R.layout.pezzo_individuo_piccolo, layout, false)
        layout.addView(personView)
        showMainImageForPerson(Global.gc!!, p, personView.findViewById(R.id.collega_foto))
        (personView.findViewById<View>(R.id.collega_nome) as TextView).text = properName(p)
        val dates = twoDates(p, false)
        val detailsView = personView.findViewById<TextView>(R.id.collega_dati)
        if (dates.isEmpty()) detailsView.visibility = View.GONE else detailsView.text = dates
        if (!isDead(p)) personView.findViewById<View>(R.id.collega_lutto).visibility = View.GONE
        if (isMale(p)) personView.findViewById<View>(R.id.collega_bordo)
            .setBackgroundResource(R.drawable.casella_bordo_maschio) else if (isFemale(p)) personView.findViewById<View>(
            R.id.collega_bordo
        ).setBackgroundResource(R.drawable.casella_bordo_femmina)
        personView.setOnClickListener { v: View? ->
            Memory.setFirst(p)
            val intent = Intent(layout.context, ProfileActivity::class.java)
            intent.putExtra(CARD_KEY, card)
            layout.context.startActivity(intent)
        }
        return personView
    }

    fun familyText(context: Context?, gc: Gedcom?, fam: Family, oneLine: Boolean): String {
        var text = StringBuilder()
        for (husband in fam.getHusbands(gc)) text.append(properName(husband)).append("\n")
        for (wife in fam.getWives(gc)) text.append(properName(wife)).append("\n")
        if (fam.getChildren(gc).size == 1) {
            text.append(properName(fam.getChildren(gc)[0]))
        } else if (fam.getChildren(gc).size > 1) text.append(
            context!!.getString(
                R.string.num_children,
                fam.getChildren(gc).size
            )
        )
        if (text.toString().endsWith("\n")) text.deleteCharAt(text.length - 1)
        if (oneLine) text = StringBuilder(text.toString().replace("\n".toRegex(), ", "))
        if (text.length == 0) text =
            StringBuilder("[" + context!!.getString(R.string.empty_family) + "]")
        return text.toString()
    }

    /**
     * Used by pantry (??)
     */
    fun linkFamily(layout: LinearLayout, fam: Family) {
        val familyView =
            LayoutInflater.from(layout.context).inflate(R.layout.piece_family, layout, false)
        layout.addView(familyView)
        (familyView.findViewById<View>(R.id.family_text) as TextView).text =
            familyText(layout.context, Global.gc, fam, false)
        familyView.setOnClickListener { v: View? ->
            Memory.setFirst(fam)
            layout.context.startActivity(Intent(layout.context, FamilyActivity::class.java))
        }
    }

    /**
     * Used from pantry
     */
    fun linkMedia(layout: LinearLayout, media: Media?) {
        val imageView =
            LayoutInflater.from(layout.context).inflate(R.layout.pezzo_media, layout, false)
        layout.addView(imageView)
        setupMedia(
            media!!,
            imageView.findViewById(R.id.media_testo),
            imageView.findViewById(R.id.media_num)
        )
        val parami = imageView.layoutParams as LinearLayout.LayoutParams
        parami.height = dpToPx(80f)
        showImage(
            media,
            imageView.findViewById(R.id.media_img),
            imageView.findViewById(R.id.media_circolo)
        )
        imageView.setOnClickListener { v: View? ->
            Memory.setFirst(media)
            layout.context.startActivity(Intent(layout.context, ImageActivity::class.java))
        }
    }

    /**
     * Aggiunge un autore al layout
     */
    fun linkSubmitter(layout: LinearLayout, submitter: Submitter) {
        val context = layout.context
        val view = LayoutInflater.from(context).inflate(R.layout.piece_note, layout, false)
        layout.addView(view)
        val noteText = view.findViewById<TextView>(R.id.note_text)
        noteText.text = submitter.name
        view.findViewById<View>(R.id.note_sources).visibility = View.GONE
        view.setOnClickListener { v: View? ->
            Memory.setFirst(submitter)
            context.startActivity(Intent(context, AuthorActivity::class.java))
        }
    }

    /**
     * Adds a generic container with one or more links to parent records to the layout // Aggiunge al layout un contenitore generico con uno o più collegamenti a record capostipiti
     */
    fun putContainer(layout: LinearLayout, what: Any?, title: Int) {
        val view = LayoutInflater.from(layout.context).inflate(R.layout.dispensa, layout, false)
        val titleView = view.findViewById<TextView>(R.id.dispensa_titolo)
        titleView.setText(title)
        titleView.background =
            AppCompatResources.getDrawable(layout.context, R.drawable.sghembo) // per android 4
        layout.addView(view)
        val pantry = view.findViewById<LinearLayout>(R.id.dispensa_scatola)
        if (what is Array<*>) {
            for (o in what as Array<Any?>) putAny(pantry, o)
        } else putAny(pantry, what)
    }

    /**
     * It recognizes the record type and adds the appropriate link to the box
     */
    fun putAny(layout: LinearLayout, record: Any?) {
        if (record is Person) linkPerson(layout, record, 1) else if (record is Source) placeSource(
            layout,
            record,
            false
        ) else if (record is Family) linkFamily(
            layout,
            record
        ) else if (record is Repository) putRepository(
            layout,
            (record as Repository?)!!
        ) else if (record is Note) placeNote(
            layout,
            record,
            true
        ) else if (record is Media) linkMedia(
            layout,
            record as Media?
        ) else if (record is Submitter) linkSubmitter(layout, record)
    }

    /**
     * Adds the piece with the change date and time to the layout
     */
    fun placeChangeDate(layout: LinearLayout, change: Change?) {
        val changeView: View
        if (change != null && Global.settings!!.expert) {
            changeView = LayoutInflater.from(layout.context)
                .inflate(R.layout.pezzo_data_cambiamenti, layout, false)
            layout.addView(changeView)
            val textView = changeView.findViewById<TextView>(R.id.cambi_testo)
            if (change.dateTime != null) {
                var txt = ""
                if (change.dateTime.value != null) txt =
                    GedcomDateConverter(change.dateTime.value).writeDateLong()
                if (change.dateTime.time != null) txt += " - " + change.dateTime.time
                textView.text = txt
            }
            val noteLayout = changeView.findViewById<LinearLayout>(R.id.cambi_note)
            for (otherTag in findExtensions(change)) place(noteLayout, otherTag.name, otherTag.text)
            // Thanks to my contribution the change date can have notes
            placeNotes(noteLayout, change, false)
            changeView.setOnClickListener { v: View? ->
                add(change)
                layout.context.startActivity(Intent(layout.context, ChangesActivity::class.java))
            }
        }
    }

    /**
     * Asks for confirmation to delete an item
     */
    fun preserve(what: Any?): Boolean {
        // todo Confirm delete
        return false
    }

    /** Save the tree.
     * @param refresh Will refresh also other activities
     * @param objects Record(s) of which update the change date
     */
    fun save(refresh: Boolean, vararg objects: Any?) {
        if (refresh) Global.edited = true
        if (objects != null) updateChangeDate(*objects)

        // marks the authors on the first save
        if (Global.settings!!.currentTree!!.grade == 9) {
            for (author in Global.gc!!.submitters) author.putExtension(PASSED_EXTENSION_KEY, true)
            Global.settings!!.currentTree!!.grade = 10
            Global.settings!!.save()
        }
        if (Global.settings!!.autoSave) saveJson(
            Global.gc,
            Global.settings!!.openTree
        ) else { // shows the Save button
            Global.shouldSave = true
            if (Global.mainView != null) {
                val menu = Global.mainView!!.findViewById<NavigationView>(R.id.menu)
                menu.getHeaderView(0).findViewById<View>(R.id.menu_salva).visibility = View.VISIBLE
            }
        }
    }

    /**
     * Update the change date of record(s)
     */
    fun updateChangeDate(vararg objects: Any?) {
        for (`object` in objects) {
            try { // If updating doesn't have the get/setChange method, it passes silently
                var change = `object`?.javaClass?.getMethod("getChange")?.invoke(`object`) as Change?
                if (change == null) // the record does not yet have a CHAN
                    change = Change()
                change.dateTime = actualDateTime()
                `object`?.javaClass?.getMethod("setChange", Change::class.java)
                    ?.invoke(`object`, change)
                // Extension with zone id, a string type 'America/Sao_Paulo'
                change.putExtension(ZONE_EXTENSION_KEY, TimeZone.getDefault().id)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Return actual DateTime
     */
    fun actualDateTime(): DateTime {
        val dateTime = DateTime()
        val now = Date()
        dateTime.value = String.format(Locale.ENGLISH, "%te %<Tb %<tY", now)
        dateTime.time = String.format(Locale.ENGLISH, "%tT", now)
        return dateTime
    }

    /**
     * Save the Json
     */
    fun saveJson(gedcom: Gedcom?, treeId: Int) {
        val h = gedcom!!.header
        // Only if the header is from Family Gem
        if (h?.generator?.value == "FAMILY_GEM") {
            // Update the date and time
            h.dateTime = actualDateTime()
            // Eventually update the version of Family Gem
            if ((h.generator.version != null && h.generator.version != BuildConfig.VERSION_NAME)
                || h.generator.version == null
            ) h.generator.version = BuildConfig.VERSION_NAME
        }
        try {
            FileUtils.writeStringToFile(
                File(Global.context!!.filesDir, "$treeId.json"),
                JsonParser().toJson(gedcom), "UTF-8"
            )
        } catch (e: IOException) {
            Toast.makeText(Global.context, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
        Notifier(Global.context!!, gedcom, treeId, Notifier.What.DEFAULT)
    }

    fun castJsonInt(unknown: Any): Int {
        return if (unknown is Int) unknown else (unknown as JsonPrimitive).asInt
    }

    fun castJsonString(unknown: Any?): String? {
        return if (unknown == null) null else if (unknown is String) unknown else (unknown as JsonPrimitive).asString
    }

    fun pxToDp(pixels: Float): Float {
        return pixels / Global.context!!.resources.displayMetrics.density
    }

    fun dpToPx(dips: Float): Int {
        return (dips * Global.context!!.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * Evaluate whether there are individuals connectable with respect to an individual.
     * Used to decide whether to show 'Link Existing Person' in the menu
     */
    fun containsConnectableIndividuals(person: Person?): Boolean {
        val total = Global.gc!!.people.size
        if (total > 0 && (Global.settings!!.expert // the experts always can
                    || person == null)
        ) // in an empty family aRepresentativeOfTheFamily is null
            return true
        val kin = countRelatives(person)
        return total > kin + 1
    }

    /**
     * Asks whether to reference an author in the header
     */
    fun mainAuthor(context: Context?, authorId: String?) {
        val head = arrayOf(
            Global.gc!!.header
        )
        if (head[0] == null || head[0]!!.submitterRef == null) {
            AlertDialog.Builder(context!!).setMessage(R.string.make_main_submitter)
                .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, id: Int ->
                    if (head[0] == null) {
                        head[0] = createHeader(Global.settings!!.openTree.toString() + ".json")
                        Global.gc!!.header = head[0]
                    }
                    head[0]!!.submitterRef = authorId
                    save(true)
                }.setNegativeButton(R.string.no, null).show()
        }
    }

    /**
     * Returns the first non-passed author
     */
    fun newSubmitter(gc: Gedcom): Submitter? {
        for (author in gc.submitters) {
            if (author.getExtension(PASSED_EXTENSION_KEY) == null) return author
        }
        return null
    }

    /**
     * Check if an author has participated in the shares, so as not to have them deleted
     */
    fun submitterHasShared(autore: Submitter): Boolean {
        val shares: List<Share>? = Global.settings!!.currentTree!!.shares
        var inviatore = false
        if (shares != null) for (share in shares) if (autore.id == share.submitter) inviatore = true
        return inviatore
    }

    /**
     * String list of representative family members
     */
    fun listFamilies(familyList: List<Family>): Array<String> {
        val familyPivots: MutableList<String> = ArrayList()
        for (fam in familyList) {
            val label = familyText(Global.context, Global.gc, fam, true)
            familyPivots.add(label)
        }
        return familyPivots.toTypedArray()
    }

    /** For a stud? anchor? reference? ("perno") who is a child in more than one family, ask which family to show
     * @param thingToOpen what to open:
     *  * 0 diagram of the previous family, without asking which family (first click on Diagram)
     *  * 1 diagram possibly asking which family
     *  * 2 family possibly asking which family
     *
     */
    fun askWhichParentsToShow(context: Context, person: Person?, thingToOpen: Int) {
        if (person == null) finishParentSelection(context, null, 1, 0) else {
            val families = person.getParentFamilies(Global.gc)
            if (families.size > 1 && thingToOpen > 0) {
                AlertDialog.Builder(context).setTitle(R.string.which_family)
                    .setItems(listFamilies(families)) { dialog: DialogInterface?, quale: Int ->
                        finishParentSelection(
                            context,
                            person,
                            thingToOpen,
                            quale
                        )
                    }
                    .show()
            } else finishParentSelection(context, person, thingToOpen, 0)
        }
    }

    private fun finishParentSelection(
        context: Context,
        pivot: Person?,
        whatToOpen: Int,
        whichFamily: Int
    ) {
        if (pivot != null) Global.indi = pivot.id
        if (whatToOpen > 0) // The family to show is set
            Global.familyNum = whichFamily // it is usually 0
        if (whatToOpen < 2) { // Show the diagram
            if (context is Principal) { // Diagram, ListOfPeopleFragment or Principal itself
                val fm = (context as AppCompatActivity).supportFragmentManager
                // Name of the previous fragment in the backstack
                val previousName = fm.getBackStackEntryAt(fm.backStackEntryCount - 1).name
                if (previousName == "diagram") fm.popBackStack() // Clicking on Diagram removes the previous diagram fragment from the history
                fm.beginTransaction().replace(R.id.contenitore_fragment, Diagram())
                    .addToBackStack("diagram").commit()
            } else { // As an individual or as a family
                context.startActivity(Intent(context, Principal::class.java))
            }
        } else { // The family is shown
            val family = pivot!!.getParentFamilies(Global.gc)[whichFamily]
            if (context is FamilyActivity) { // Moving from Family to Family does not accumulate activities in the stack
                replaceFirst(family)
                (context as Activity).recreate()
            } else {
                Memory.setFirst(family)
                context.startActivity(Intent(context, FamilyActivity::class.java))
            }
        }
    }

    /**
     * For an anchor ("perno") who has multiple marriages it asks which one to show
     */
    fun askWhichSpouseToShow(context: Context, pivot: Person, family: Family?) {
        if (pivot.getSpouseFamilies(Global.gc).size > 1 && family == null) {
            AlertDialog.Builder(context).setTitle(R.string.which_family)
                .setItems(listFamilies(pivot.getSpouseFamilies(Global.gc))) { dialog: DialogInterface?, quale: Int ->
                    concludeSpouseChoice(
                        context,
                        pivot,
                        null,
                        quale
                    )
                }
                .show()
        } else {
            concludeSpouseChoice(context, pivot, family, 0)
        }
    }

    private fun concludeSpouseChoice(context: Context, pivot: Person, family: Family?, which: Int) {
        var family = family
        Global.indi = pivot.id
        family = family ?: pivot.getSpouseFamilies(Global.gc)[which]
        if (context is FamilyActivity) {
            replaceFirst(family)
            (context as Activity).recreate() // It does not accumulate activities on the stack
        } else {
            Memory.setFirst(family)
            context.startActivity(Intent(context, FamilyActivity::class.java))
        }
    }

    /**
     * Used to connect one person to another, in inexperienced mode only
     * Checks if the pivot could or has multiple marriages and asks which one to attach a spouse or child to
     * Also responsible for setting 'familyId' or 'location'
     */
    fun checkMultipleMarriages(intent: Intent, context: Context, fragment: Fragment?): Boolean {
        val pivotId = intent.getStringExtra(PROFILE_ID_KEY)
        val pivot = Global.gc!!.getPerson(pivotId)
        val parentsFamilies = pivot.getParentFamilies(Global.gc)
        val spouseFamilies = pivot.getSpouseFamilies(Global.gc)
        val relationship = intent.getIntExtra(RELATIONSHIP_ID_KEY, 0)
        val adapter = ArrayAdapter<FamilyItem>(context, android.R.layout.simple_list_item_1)

        // Parents: There is already a family that has at least one empty slot
        if (relationship == 1 && parentsFamilies.size == 1 && (parentsFamilies[0].husbandRefs.isEmpty() || parentsFamilies[0].wifeRefs.isEmpty())) intent.putExtra(
            FAMILY_ID_KEY,
            parentsFamilies[0].id
        ) // add 'familyId' to the existing intent
        // if this family is already full of parents, 'idFamily' remains null
        // then the recipient's existing family will be searched or a new family will be created

        // Parents: There are multiple families
        if (relationship == 1 && parentsFamilies.size > 1) {
            for (fam in parentsFamilies) if (fam.husbandRefs.isEmpty() || fam.wifeRefs.isEmpty()) adapter.add(
                FamilyItem(context, fam)
            )
            if (adapter.count == 1) intent.putExtra(
                FAMILY_ID_KEY,
                adapter.getItem(0)!!.family!!.id
            ) else if (adapter.count > 1) {
                AlertDialog.Builder(context).setTitle(R.string.which_family_add_parent)
                    .setAdapter(adapter) { dialog: DialogInterface?, quale: Int ->
                        intent.putExtra(FAMILY_ID_KEY, adapter.getItem(quale)!!.family!!.id)
                        finishCheckingMultipleMarriages(context, intent, fragment)
                    }.show()
                return true
            }
        } else if (relationship == 2 && parentsFamilies.size == 1) {
            intent.putExtra(FAMILY_ID_KEY, parentsFamilies[0].id)
        } else if (relationship == 2 && parentsFamilies.size > 1) {
            AlertDialog.Builder(context).setTitle(R.string.which_family_add_sibling)
                .setItems(listFamilies(parentsFamilies)) { dialog: DialogInterface?, quale: Int ->
                    intent.putExtra(FAMILY_ID_KEY, parentsFamilies[quale].id)
                    finishCheckingMultipleMarriages(context, intent, fragment)
                }.show()
            return true
        } else if (relationship == 3 && spouseFamilies.size == 1) {
            if (spouseFamilies[0].husbandRefs.isEmpty() || spouseFamilies[0].wifeRefs.isEmpty()) // Se c'è uno slot libero
                intent.putExtra(FAMILY_ID_KEY, spouseFamilies[0].id)
        } else if (relationship == 3 && spouseFamilies.size > 1) {
            for (fam in spouseFamilies) {
                if (fam.husbandRefs.isEmpty() || fam.wifeRefs.isEmpty()) adapter.add(
                    FamilyItem(
                        context,
                        fam
                    )
                )
            }
            // In the case of zero eligible families, familyId remains null
            if (adapter.count == 1) {
                intent.putExtra(FAMILY_ID_KEY, adapter.getItem(0)!!.family!!.id)
            } else if (adapter.count > 1) {
                //adapter.add(new NewRelativeDialog.FamilyItem(context, pivot) );
                AlertDialog.Builder(context).setTitle(R.string.which_family_add_spouse)
                    .setAdapter(adapter) { dialog: DialogInterface?, which: Int ->
                        intent.putExtra(FAMILY_ID_KEY, adapter.getItem(which)!!.family!!.id)
                        finishCheckingMultipleMarriages(context, intent, fragment)
                    }.show()
                return true
            }
        } else if (relationship == 4 && spouseFamilies.size == 1) {
            intent.putExtra(FAMILY_ID_KEY, spouseFamilies[0].id)
        } // Son: there are many conjugal families
        else if (relationship == 4 && spouseFamilies.size > 1) {
            AlertDialog.Builder(context).setTitle(R.string.which_family_add_child)
                .setItems(listFamilies(spouseFamilies)) { dialog: DialogInterface?, quale: Int ->
                    intent.putExtra(FAMILY_ID_KEY, spouseFamilies[quale].id)
                    finishCheckingMultipleMarriages(context, intent, fragment)
                }.show()
            return true
        }
        // Not having found a family of the pivot, he tells the ListOfPeopleActivity to try to place the pivot in the recipient's family
        if (intent.getStringExtra(FAMILY_ID_KEY) == null && intent.getBooleanExtra(
                PEOPLE_LIST_CHOOSE_RELATIVE_KEY,
                false
            )
        ) intent.putExtra(LOCATION_KEY, "FAMIGLIA_ESISTENTE")
        return false
    }

    /**
     * Conclusion of the previous function
     */
    fun finishCheckingMultipleMarriages(contesto: Context, intent: Intent, frammento: Fragment?) {
        if (intent.getBooleanExtra(PEOPLE_LIST_CHOOSE_RELATIVE_KEY, false)) {
            // open ListOfPeopleFragment
            if (frammento != null) frammento.startActivityForResult(
                intent,
                1401
            ) else (contesto as Activity).startActivityForResult(intent, 1401)
        } else  // open IndividualEditorActivity
            contesto.startActivity(intent)
    }

    /**
     * Check that one or more families are empty and propose to eliminate them
     * @param evenRunWhenDismissing tells to execute 'whatToDo' even when clicking Cancel or out of the dialog
     */
    fun checkFamilyItem(
        context: Context?,
        whatToDo: Runnable?,
        evenRunWhenDismissing: Boolean,
        vararg families: Family
    ): Boolean {
        val items: MutableList<Family> = ArrayList()
        for (fam in families) {
            val numMembers = fam.husbandRefs.size + fam.wifeRefs.size + fam.childRefs.size
            if (numMembers <= 1 && fam.eventsFacts.isEmpty() && fam.getAllMedia(Global.gc).isEmpty()
                && fam.getAllNotes(Global.gc).isEmpty() && fam.sourceCitations.isEmpty()
            ) {
                items.add(fam)
            }
        }
        if (items.size > 0) {
            AlertDialog.Builder(context!!).setMessage(R.string.empty_family_delete)
                .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, i: Int ->
                    for (fam in items) deleteFamily(fam) // So it happens to save several times together ... but oh well
                    whatToDo?.run()
                }
                .setNeutralButton(android.R.string.cancel) { dialog: DialogInterface?, i: Int -> if (evenRunWhenDismissing) whatToDo!!.run() }
                .setOnCancelListener { dialog: DialogInterface? -> if (evenRunWhenDismissing) whatToDo!!.run() }
                .show()
            return true
        }
        return false
    }

    /**
     * Display a dialog to edit the ID of any record
     */
    fun editId(context: Context, record: ExtensionContainer, refresh: Runnable) {
        val view = (context as Activity).layoutInflater.inflate(R.layout.id_editor, null)
        val inputField = view.findViewById<EditText>(R.id.edit_id_input_field)
        try {
            val oldId = record.javaClass.getMethod("getId").invoke(record) as String
            inputField.setText(oldId)
            val alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.edit_id).setView(view)
                .setPositiveButton(R.string.save) { dialog: DialogInterface?, i: Int ->
                    val newId = inputField.text.toString().trim { it <= ' ' }
                    if (newId == oldId) return@setPositiveButton
                    if (record is Person) {
                        val person = record
                        person.id = newId
                        val modified: MutableSet<PersonFamilyCommonContainer> = HashSet()
                        modified.add(person)
                        for (family in Global.gc!!.families) {
                            for (ref in family.husbandRefs) if (oldId == ref.ref) {
                                ref.ref = newId
                                modified.add(family)
                            }
                            for (ref in family.wifeRefs) if (oldId == ref.ref) {
                                ref.ref = newId
                                modified.add(family)
                            }
                            for (ref in family.childRefs) if (oldId == ref.ref) {
                                ref.ref = newId
                                modified.add(family)
                            }
                        }
                        save(true, *modified.toTypedArray())
                        val tree = Global.settings!!.currentTree
                        if (oldId == tree!!.root) {
                            tree.root = newId
                            Global.settings!!.save()
                        }
                        Global.indi = newId
                    } else if (record is Family) {
                        val family = record
                        family.id = newId
                        val modified: MutableSet<PersonFamilyCommonContainer> = HashSet()
                        modified.add(family)
                        for (person in Global.gc!!.people) {
                            for (ref in person.parentFamilyRefs) if (oldId == ref.ref) {
                                ref.ref = newId
                                modified.add(person)
                            }
                            for (ref in person.spouseFamilyRefs) if (oldId == ref.ref) {
                                ref.ref = newId
                                modified.add(person)
                            }
                        }
                        save(true, *modified.toTypedArray())
                    } else if (record is Media) {
                        val media = record
                        val mediaContainers = MediaContainers(Global.gc!!, media, newId)
                        media.id = newId
                        updateChangeDate(media)
                        save(true, *mediaContainers.containers.toTypedArray())
                    } else if (record is Note) {
                        val note = record
                        val noteContainers = NoteContainers(Global.gc!!, note, newId)
                        note.id = newId
                        updateChangeDate(note)
                        save(true, *noteContainers.containers.toTypedArray())
                    } else if (record is Source) {
                        val citations = ListOfSourceCitations(Global.gc!!, oldId)
                        for (triple in citations.list) triple.citation!!.ref = newId
                        val source = record
                        source.id = newId
                        updateChangeDate(source)
                        save(true, *citations.progenitors)
                    } else if (record is Repository) {
                        val modified: MutableSet<Source> = HashSet()
                        for (source in Global.gc!!.sources) {
                            val repoRef = source.repositoryRef
                            if (oldId == repoRef?.ref) {
                                repoRef.ref = newId
                                modified.add(source)
                            }
                        }
                        val repo = record
                        repo.id = newId
                        updateChangeDate(repo)
                        save(true, *modified.toTypedArray())
                    } else if (record is Submitter) {
                        for (share in Global.settings!!.currentTree!!.shares!!) if (oldId == share.submitter) share.submitter =
                            newId
                        Global.settings!!.save()
                        val header = Global.gc!!.header
                        if (oldId == header.submitterRef) header.submitterRef = newId
                        val submitter = record
                        submitter.id = newId
                        save(true, submitter)
                    }
                    Global.gc!!.createIndexes()
                    refresh.run()
                }.setNeutralButton(R.string.cancel, null).show()
            // Focus
            view.postDelayed({
                inputField.requestFocus()
                inputField.setSelection(inputField.text.length)
                val inputMethodManager =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
            }, 300)
            // All other IDs
            val allIds: MutableSet<String> = HashSet()
            for (person in Global.gc!!.people) allIds.add(person.id)
            for (family in Global.gc!!.families) allIds.add(family.id)
            for (media in Global.gc!!.media) allIds.add(media.id)
            for (note in Global.gc!!.notes) allIds.add(note.id)
            for (source in Global.gc!!.sources) allIds.add(source.id)
            for (repo in Global.gc!!.repositories) allIds.add(repo.id)
            for (submitter in Global.gc!!.submitters) allIds.add(submitter.id)
            allIds.remove(oldId)
            // Validation
            val inputLayout = view.findViewById<TextInputLayout>(R.id.edit_id_input_layout)
            val okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            inputField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    sequence: CharSequence,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    var error: String? = null
                    val proposal = sequence.toString().trim { it <= ' ' }
                    if (allIds.contains(proposal)) error =
                        context.getString(R.string.existing_id) else if (proposal.isEmpty() || proposal.matches(
                            "^[#].*|.*[@:!].*".toRegex()
                        )
                    ) error = context.getString(R.string.invalid_id)
                    inputLayout.error = error
                    okButton.isEnabled = error == null
                }

                override fun afterTextChanged(e: Editable) {}
            })
            inputField.setOnEditorActionListener { textView: TextView?, actionId: Int, keyEvent: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_DONE && okButton.isEnabled) {
                    okButton.performClick()
                    return@setOnEditorActionListener true
                }
                false
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Show a Toast message even from a side thread
     */
    fun toast(activity: Activity, message: Int) {
        toast(activity, activity.getString(message))
    }

    fun toast(activity: Activity, message: String?) {
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
    }
}