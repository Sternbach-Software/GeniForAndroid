package app.familygem

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.util.Pair
import androidx.core.view.children
import app.familygem.F.cropImage
import app.familygem.F.displayMediaAppList
import app.familygem.F.endImageCropping
import app.familygem.F.permissionsResult
import app.familygem.F.proposeCropping
import app.familygem.constant.Choice
import app.familygem.constant.intdefs.*
import app.familygem.detail.*
import app.familygem.detail.EventActivity.Companion.cleanUpTag
import app.familygem.detail.FamilyActivity.Companion.chooseLineage
import app.familygem.detail.FamilyActivity.Companion.connect
import app.familygem.detail.FamilyActivity.Companion.disconnect
import app.familygem.detail.FamilyActivity.Companion.findParentFamilyRef
import app.familygem.list.NotesFragment.Companion.newNote
import app.familygem.list.RepositoriesFragment.Companion.newRepository
import app.familygem.visitor.FindStack
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.theartofdev.edmodo.cropper.CropImage
import org.folg.gedcom.model.*
import java.io.File
import java.util.*

open class DetailActivity : AppCompatActivity() {

    lateinit var box: LinearLayout
    lateinit var obj // Name Media SourceCitation ecc.
            : Any
    val eggs = mutableListOf<Egg>() // List of all the possible editable pieces
    val otherEvents // Events for the Family FAB
            = mutableListOf<Pair<String, String>>()
    var aRepresentativeOfTheFamily // a family Person to hide in the FAB 'Colleague person' TODO what? Original: una Persona della Famiglia per nascondere nel FAB 'Collega persona'
            : Person? = null
    lateinit var publisherDateLinearLayout: PublisherDateLinearLayout
    lateinit var fab: FloatingActionButton
    lateinit var actionBar: ActionBar

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.activity_detail)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        box = findViewById(R.id.detail_box)
        fab = findViewById(R.id.fab)
        actionBar = supportActionBar as ActionBar
        U.ensureGlobalGedcomNotNull(Global.gc)
        obj = Memory.`object`?.apply {
            format()
        } ?: onBackPressed() // skip all other details without object
        // List of other events
        val otherEventTags = listOf(
            "ANUL",
            "CENS",
            "DIVF",
            "ENGA",
            "MARB",
            "MARC",
            "MARL",
            "MARS",
            "RESI",
            "EVEN",
            "NCHI"
        )
        for (tag in otherEventTags) {
            val event = EventFact()
            event.tag = tag
            var label = event.displayType
            if (Global.settings.expert) label += " — $tag"
            otherEvents.add(Pair(tag, label))
        }
        otherEvents.sortBy { it.second }
        val fab = findViewById<FloatingActionButton>(R.id.fab)!!
        fab.setOnClickListener { view: View? ->
            val popup = fabMenu(view)
            popup.show()
            popup.setOnMenuItemClickListener { item: MenuItem ->
                // FAB + puts a new egg and makes it immediately editable
                val id = item.itemId
                var toBeSaved = false
                when {
                    id.isAddress -> {
                        val thing = eggs[id].yolk
                        if (thing is Address) { // thing is a new Address()
                            when (obj) {
                                is EventFact -> (obj as EventFact).address =
                                    thing
                                is Submitter -> (obj as Submitter).address =
                                    thing
                                is Repository -> (obj as Repository).address =
                                    thing
                            }
                        }
                        // Tags needed to then export to Gedcom
                        when (obj) {
                            is Name -> {
                                if (thing == "Type") (obj as Name).typeTag =
                                    "TYPE" //if can be removed, but it is more consistent to leave it
                            }
                            is Repository -> {
                                if (thing == "Www") (obj as Repository).wwwTag = "WWW"
                                if (thing == "Email") (obj as Repository).emailTag = "EMAIL"
                            }
                            is Submitter -> {
                                if (thing == "Www") (obj as Submitter).wwwTag = "WWW"
                                if (thing == "Email") (obj as Submitter).emailTag = "EMAIL"
                            }
                        }
                        val piece = placePiece(eggs[id].title, "", thing, eggs[id].multiLine)
                        if (thing is String) edit(piece)
                        // TODO open new Address ("to edit it"?)["for editing"?] //aprire Address nuovo per editarlo
                    }
                    id == NEW_REPOSITORY_OPTION -> {
                        newRepository(this, obj as Source?)
                    }
                    id == LINK_REPOSITORY_OPTION -> {
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.REPOSITORY, true)
                        startActivityForResult(intent, 4562)
                    }
                    id == NEW_NOTE_OPTION -> { // New note
                        val note = Note()
                        note.value = ""
                        (obj as NoteContainer?)!!.addNote(note)
                        Memory.add(note)
                        startActivity(Intent(this, NoteActivity::class.java))
                        toBeSaved = true
                    }
                    id == NEW_SHARED_NOTE_OPTION -> { // New shared note
                        newNote(this, obj)
                    }
                    id == LINK_SHARED_NOTE_OPTION -> { // Link shared note
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.NOTE, true)
                        startActivityForResult(intent, 7074)
                    }
                    id == NEW_MEDIA_OPTION -> { // Search for local media
                        displayMediaAppList(this, null, 4173, obj as MediaContainer?)
                    }
                    id == NEW_SHARED_MEDIA_OPTION -> { // Search for shared media
                        displayMediaAppList(this, null, 4174, obj as MediaContainer?)
                    }
                    id == LINK_SHARED_MEDIA_OPTION -> { // Link shared media
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.MEDIA, true)
                        startActivityForResult(intent, 43616)
                    }
                    id == NEW_SOURCE_CITATION_OPTION -> { // New source citation //Nuova fonte-nota
                        val citation = SourceCitation()
                        citation.value = ""
                        if (obj is Note) (obj as Note).addSourceCitation(citation) else (obj as SourceCitationContainer?)!!.addSourceCitation(
                            citation
                        )
                        Memory.add(citation)
                        startActivity(Intent(this, SourceCitationActivity::class.java))
                        toBeSaved = true
                    }
                    id == NEW_SOURCE_OPTION -> {  // New source
                        SourcesFragment.createNewSource(this, obj)
                    }
                    id == LINK_SOURCE_OPTION -> { // Link source
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.SOURCE, true)
                        startActivityForResult(intent, 5065)
                    }
                    id == LINK_NEW_PARENT_OR_PARTNER_OPTION || id == LINK_NEW_CHILD_OPTION -> { // Create new family member
                        val intent = Intent(this, IndividualEditorActivity::class.java)
                        intent.putExtra(PROFILE_ID_KEY, NEW_PERSON_VALUE)
                        intent.putExtra(FAMILY_ID_KEY, (obj as Family?)!!.id)
                        intent.putExtra(RELATIONSHIP_ID_KEY, id - 115)
                        startActivity(intent)
                    }
                    id == LINK_EXISTING_PARENT_OR_PARTNER_OPTION || id == LINK_EXISTING_CHILD_OPTION -> { // Link existing person
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.PERSON, true)
                        intent.putExtra(RELATIONSHIP_ID_KEY, id - 117)
                        startActivityForResult(intent, 34417)
                    }
                    id == MARRIAGE_OPTION -> { // Put marriage - Metti matrimonio
                        val marriage = EventFact()
                        marriage.tag = "MARR"
                        marriage.date = ""
                        marriage.place = ""
                        marriage.type = ""
                        (obj as Family?)!!.addEventFact(marriage)
                        Memory.add(marriage)
                        startActivity(Intent(this, EventActivity::class.java))
                        toBeSaved = true
                    }
                    id == DIVORCE_OPTION -> { // Put divorce - Metti divorzio
                        val divorce = EventFact()
                        divorce.tag = "DIV"
                        divorce.date = ""
                        (obj as Family?)!!.addEventFact(divorce)
                        Memory.add(divorce)
                        startActivity(Intent(this, EventActivity::class.java))
                        toBeSaved = true
                    }
                    id.isEventOption -> { // Put another event - Metti altro evento
                        val event = EventFact()
                        event.tag = otherEvents[id - EVENT_OPTION].first
                        (obj as Family?)!!.addEventFact(event)
                        refresh()
                        toBeSaved = true
                    }
                }
                if (toBeSaved) U.save(true, obj)
                true
            }
        }
        // Menu test: if it is empty it hides the fab
        // Todo If the FAB is hidden, deleting one piece the FAB should reappear
        if (!fabMenu(null).menu.hasVisibleItems()) fab.hide()
    }

    /**
     * FAB menu: only with methods that are not already present in the box
     */
    private fun fabMenu /*menuFAB*/(fabView: View?): PopupMenu {
        val popup = PopupMenu(this, fabView!!)
        val menu = popup.menu
        val withAddress = listOf(
            "Www",
            "Email",
            "Phone",
            "Fax"
        ) // these objects appear in the Event FAB if an Address exists
        for ((index, egg) in eggs.withIndex()) {
            var alreadyPut = false
            var addressPresent = false
            for (child in box.children) {
                val `object` = child.getTag(R.id.tag_object)
                if (`object` != null && `object` == egg.yolk) alreadyPut = true
                if (`object` is Address) addressPresent = true
            }
            if (!alreadyPut) {
                if (egg.common || addressPresent && withAddress.contains(egg.yolk))
                    menu.add(0, index, 0, egg.title)
            }
        }
        if (obj is Family) {
            val hasChildren = (obj as Family).childRefs.isNotEmpty()
            val newMemberMenu = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.new_relative)
            // Non-expert can add maximum two parents // todo: expert too??
            if (!(!Global.settings.expert && (obj as Family).husbandRefs.size + (obj as Family).wifeRefs.size >= 2)) newMemberMenu.add(
                0,
                LINK_NEW_PARENT_OR_PARTNER_OPTION,
                0,
                if (hasChildren) R.string.parent else R.string.partner
            )
            newMemberMenu.add(0, LINK_NEW_CHILD_OPTION, 0, R.string.child)
            if (U.containsConnectableIndividuals(aRepresentativeOfTheFamily)) {
                val linkMemberMenu = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.link_person)
                if (!(!Global.settings.expert && (obj as Family).husbandRefs.size + (obj as Family).wifeRefs.size >= 2)) linkMemberMenu.add(
                    0,
                    LINK_EXISTING_PARENT_OR_PARTNER_OPTION,
                    0,
                    if (hasChildren) R.string.parent else R.string.partner
                )
                linkMemberMenu.add(0, LINK_EXISTING_CHILD_OPTION, 0, R.string.child)
            }
            val eventSubMenu = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.event)
            var marriageLabel =
                getString(R.string.marriage) + " / " + getString(R.string.relationship)
            var divorceLabel = getString(R.string.divorce) + " / " + getString(R.string.separation)
            if (Global.settings.expert) {
                marriageLabel += " — MARR"
                divorceLabel += " — DIV"
            }
            eventSubMenu.add(0, MARRIAGE_OPTION, 0, marriageLabel)
            eventSubMenu.add(0, DIVORCE_OPTION, 0, divorceLabel)

            // The other events that can be entered
            val otherSubMenu = eventSubMenu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.other)

            for ((i, event) in otherEvents.withIndex())
                otherSubMenu.add(0, EVENT_OPTION + i, 0, event.second)
        }
        if (obj is Source && findViewById<View?>(R.id.citazione_fonte) == null) { // todo doubt: shouldn't it be citation_REPOSITORY?
            val subRepository = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.repository)
            subRepository.add(0, NEW_REPOSITORY_OPTION, 0, R.string.new_repository)
            if (Global.gc?.repositories?.isNotEmpty() == true) subRepository.add(
                0,
                LINK_REPOSITORY_OPTION,
                0,
                R.string.link_repository
            )
        }
        if (obj is NoteContainer) {
            val subNote = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.note)
            subNote.add(0, NEW_NOTE_OPTION, 0, R.string.new_note)
            subNote.add(0, NEW_SHARED_NOTE_OPTION, 0, R.string.new_shared_note)
            if (Global.gc?.notes?.isNotEmpty() == true) subNote.add(
                0,
                LINK_SHARED_NOTE_OPTION,
                0,
                R.string.link_shared_note
            )
        }
        if (obj is MediaContainer) {
            val subMedia = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.media)
            subMedia.add(0, NEW_MEDIA_OPTION, 0, R.string.new_media)
            subMedia.add(0, NEW_SHARED_MEDIA_OPTION, 0, R.string.new_shared_media)
            if (Global.gc?.media?.isNotEmpty() == true) subMedia.add(
                0,
                LINK_SHARED_MEDIA_OPTION,
                0,
                R.string.link_shared_media
            )
        }
        if ((obj is SourceCitationContainer || obj is Note) && Global.settings.expert) {
            val subSource = menu.addSubMenu(0, LINK_ENTITY_OPTION, 0, R.string.source)
            subSource.add(0, NEW_SOURCE_CITATION_OPTION, 0, R.string.new_source_note)
            subSource.add(0, NEW_SOURCE_OPTION, 0, R.string.new_source)
            if (Global.gc?.sources?.isNotEmpty() == true) subSource.add(
                0,
                LINK_SOURCE_OPTION,
                0,
                R.string.link_source
            )
        }
        return popup
    }

    /**
     * Set what has been chosen in the lists
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // From the 'Connect/Link ...' submenu in FAB
            when (requestCode) {
                RELATIVE_CHOSEN_FROM_LIST_OF_PEOPLE_FRAGMENT -> {
                    Global.gc?.getPerson(
                        data!!.getStringExtra(RELATIVE_ID_KEY)
                    )?.let {
                        connect(
                            it,
                            (obj as Family?)!!,
                            data.getIntExtra(RELATIONSHIP_ID_KEY, 0)
                        )
                    }
                    U.save(true, Memory.firstObject())
                    return
                }
                SOURCE_SELECTED_IN_SOURCE_FRAGMENT -> { // Source selected in SourcesFragment
                    val sourceCitation = SourceCitation()
                    sourceCitation.ref = data!!.getStringExtra(SOURCE_ID_KEY_ENGLISH)
                    if (obj is Note) (obj as Note).addSourceCitation(sourceCitation) else (obj as SourceCitationContainer?)!!.addSourceCitation(
                        sourceCitation
                    )
                }
                SHARED_NOTE_RESULT_CODE -> { // Shared note
                    val noteRef = NoteRef()
                    noteRef.ref = data!!.getStringExtra(NOTE_ID_KEY)
                    (obj as NoteContainer?)!!.addNoteRef(noteRef)
                }
                FILE_FROM_FILE_MANAGER_BECOMES_LOCAL_MEDIA -> { // File taken from file manager or other app becomes local media //File preso dal file manager o altra app diventa media locale
                    val media = Media()
                    media.fileTag = "FILE"
                    (obj as MediaContainer?)!!.addMedia(media)
                    if (proposeCropping(this, null, data, media)) {
                        U.save(false, Memory.firstObject())
                        return
                    }
                }
                FILE_FROM_FILE_MANAGER_BECOMES_SHARED_MEDIA -> { // File taken from the file manager becomes shared media
                    val media = GalleryFragment.newMedia(obj)
                    if (proposeCropping(this, null, data, media)) {
                        U.save(false, media, Memory.firstObject())
                        return
                    }
                }
                MEDIA_FROM_GALLERY_FRAGMENT -> { // Media from GalleryFragment
                    val mediaRef = MediaRef()
                    mediaRef.ref = data!!.getStringExtra(MEDIA_ID_KEY)
                    (obj as MediaContainer?)!!.addMediaRef(mediaRef)
                }
                REPOSITORY_SELECTED -> { // Repository selected in database (? lit. "Warehouse") from source //Archivio scelto in Magazzino da Fonte
                    val archRef = RepositoryRef()
                    archRef.ref = data!!.getStringExtra(REPO_ID_KEY)
                    (obj as Source?)!!.repositoryRef = archRef
                }
                SAVE_MEDIA -> { // Save in Media a file chosen with the apps from Image // Salva in Media un file scelto con le app da Immagine
                    if (proposeCropping(this, null, data, (obj as Media?)!!)) {
                        U.save(false, Memory.firstObject())
                        return
                    }
                }
                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                    endImageCropping(data)
                }
            }
            //  from the context menu 'Choose ...'
            if (requestCode == SET_ARCHIVE) { // Sets the archive that has been chosen in database (? lit. "Warehouse") from Repository Ref //Imposta l'archivio che è stato scelto in Magazzino da ArchivioRef
                (obj as RepositoryRef?)!!.ref = data!!.getStringExtra(REPO_ID_KEY)
            } else if (requestCode == SET_SOURCE) { // Set the source that has been chosen in the Library by SourceCitation
                (obj as SourceCitation?)!!.ref = data!!.getStringExtra(SOURCE_ID_KEY_ENGLISH)
            }
            U.save(true, Memory.firstObject())
            // 'true' indicates to reload both this Detail thanks to the following onRestart (), and Individual or Family //'true' indica di ricaricare sia questo Dettaglio grazie al seguente onRestart(), sia Individuo o Famiglia
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) Global.edited = true
    }

    /**
     * Update contents when back with backPressed()
     * Aggiorna i contenuti quando si torna indietro con backPressed()
     */
    public override fun onRestart() {
        super.onRestart()
        if (Global.edited) { // refresh the detail
            refresh()
        }
    }

    open fun format() {} //original: impagina()

    /**
     * Reload the contents of the detail, including the modification date
     */
    fun refresh() {
        box.removeAllViews()
        eggs.clear()
        format()
    }

    /**
     * Place the tags slug
     * */
    fun placeSlug(tag: String?, id: String? = null) {
        val slugLayout = findViewById<FlexboxLayout>(R.id.dettaglio_bava)
        if (Global.settings?.expert == true) {
            slugLayout.removeAllViews()
            for (step in Memory.stepStack) {
                val stepView = LayoutInflater.from(this).inflate(R.layout.pezzo_bava, box, false)
                val stepText = stepView.findViewById<TextView>(R.id.bava_goccia)
                if (Memory.stepStack.indexOf(step) < Memory.stepStack.size - 1) {
                    if (step?.obj is Visitable) // GedcomTag extensions are not Visitable and it is impossible to find the stack of them //le estensioni GedcomTag non sono Visitable ed è impossibile trovargli la pila
                        stepView.setOnClickListener { v: View? ->
                            FindStack(Global.gc!!, step.obj as Visitable)
                            startActivity(
                                Intent(
                                    this,
                                    Memory.classes[(step.obj as Visitable).javaClass]
                                )
                            )
                        }
                } else {
                    step?.tag = tag
                    stepText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                var label = step?.tag
                if (id != null) {
                    label += " $id" // Id for main records INDI, FAMI, REPO... e.g. 'SOUR S123'
                    stepView.setOnClickListener { v: View? ->
                        concludeOtherPiece()
                        U.editId(this, obj as ExtensionContainer) { refresh() }
                    }
                }
                stepText.text = label
                slugLayout.addView(stepView)
            }
        } else slugLayout.visibility = View.GONE
    }

    /**
     * Conclude the possible editing of another piece
     * */
    private fun concludeOtherPiece() {
        for (otherPiece in box.children) {
            val editText = otherPiece.findViewById<EditText>(R.id.fatto_edita)
            if (editText?.isShown == true) {
                val textView = otherPiece.findViewById<TextView>(R.id.fatto_testo)
                if (editText.text.toString() != textView.text.toString()) // if there has been editing
                    save(otherPiece) else restore(otherPiece)
            }
        }
    }

    /**
     * Return 'object' casted in the required class,
     * or a new instance of the class, but in this case it immediately goes back.
     * Note: [aClass] must have an empty constructor, which will be used to create a new instance
     * TODO code smell: no type safety and reflection creating new classes.
     */
    fun cast(aClass: Class<*>): Any? {

        var casted = if (aClass == GedcomTag::class.java) GedcomTag(
            null,
            null,
            null
        ) else {
            //@michaelsalvador: If it goes wrong will return a new instance of the class, just to not crash DetailActivity.
            //I think what he means is that aClass.cast(obj) can crash and the assignment will fail,
            // so assign casted to something other than the result of aClass.cast(obj) as a fallback.
            val hasEmptyConstructor = runCatching { aClass.getConstructor(*arrayOf()) }.isSuccess
            if (!hasEmptyConstructor) {
                Log.e(
                    "DetailActivity",
                    "$aClass does not have empty constructor, cannot instantiate new instance"
                )
                null
            } else aClass.newInstance()
        }
        try {
            casted = aClass.cast(obj)
        } catch (e: Exception) {
            onBackPressed()
        }
        return casted
    }

    /**
     * A wrapper for every possible widget that can be displayed on a 'Details...' activity
     */
    inner class Egg(
        val title: String, // Can be a method string ("Value", "Date", "Type"...) or an Address
        val yolk: Any, // indicates whether to make it appear in the FAB menu to insert the piece
        val common: Boolean,
        val multiLine: Boolean
    ) {
        init {
            eggs.add(this) //TODO stateful constructors are bad form: it relies on the side effect of creating an object, which should naturally be stateless, and makes it unclear from reading the code what should happen, besides for the compiler not necessarily knowing that anything was done with the object, which I would imagine makes garbage collection slower - besides for being bad form.
        }
    }

    /**
     * Attempt to put a basic editable text piece in the layout
     */
    fun place(title: String, method: String, common: Boolean = true, multiLine: Boolean = false) {
        Egg(title, method, common, multiLine)
        val text = try {
            obj.javaClass.getMethod("get$method")
                .invoke(obj) as String //TODO this reflection is bad performance
        } catch (e: Exception) {
            "ERROR: " + e.message
        }
        // Value 'Y' is hidden for non-experts
        if (!Global.settings?.expert!! && obj is EventFact && method == "Value" && text == "Y") {
            val tag = (obj as EventFact).tag
            if (tag == "BIRT" || tag == "CHR" || tag == "DEAT" || tag == "MARR" || tag == "DIV") return
        }
        placePiece(title, text, method, multiLine)
    }

    /**
     * Places this address in the layout. [place] is implemented with different signatures to
     * accommodate various types of objects being placed.
     */
    fun place(title: String, address: Address?) {
        val addressNotNull = address ?: Address()
        Egg(title, addressNotNull, true, false)
        placePiece(title, writeAddress(address, false), addressNotNull, false)
    }

    /**
     * @param event Events of [FamilyActivity]
     */
    fun place(title: String?, event: EventFact?) {
        val eventNotNull = event ?: EventFact()
        placePiece(title, writeEvent(event), eventNotNull, false)
    }

    fun placePiece(title: String?, text: String?, `object`: Any, multiLine: Boolean): View? {
        if (text == null) return null
        val pieceView = LayoutInflater.from(box.context).inflate(R.layout.pezzo_fatto, box, false)
        box.addView(pieceView)
        (pieceView.findViewById<View>(R.id.fatto_titolo) as TextView).text = title
        (pieceView.findViewById<View>(R.id.fatto_testo) as TextView).text = text
        val editText = pieceView.findViewById<EditText>(R.id.fatto_edita)
        if (multiLine) {
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            editText.isVerticalScrollBarEnabled = true
        }
        var click: View.OnClickListener? = null
        when (`object`) {
            is Int -> {    //Full name in inexperienced mode
                click = View.OnClickListener { pieceView: View? -> edit(pieceView) }
            }
            is String -> { // Method
                click = View.OnClickListener { pieceView: View? -> edit(pieceView) }
                // If it is a date
                if (`object` == "Date") {
                    publisherDateLinearLayout = pieceView.findViewById(R.id.fatto_data)
                    publisherDateLinearLayout.initialize(editText)
                }
            }
            is Address -> { // Address
                click = View.OnClickListener { v: View? ->
                    Memory.add(`object`)
                    startActivity(Intent(this, AddressActivity::class.java))
                }
            }
            is GedcomTag -> { // Extension
                click = View.OnClickListener { v: View? ->
                    Memory.add(`object`)
                    startActivity(Intent(this, ExtensionActivity::class.java))
                }
            } //TODO can be combined with the previous branch
            is EventFact -> { // Event
                click = View.OnClickListener { v: View? ->
                    Memory.add(`object`)
                    startActivity(Intent(this, EventActivity::class.java))
                }
                // Family EventFacts can have notes and media
                val scatolaNote = pieceView.findViewById<LinearLayout>(R.id.fatto_note)
                U.placeNotes(scatolaNote, `object`, false)
                U.placeMedia(scatolaNote, `object`, false)
            }
        }
        pieceView.setOnClickListener(click)
        registerForContextMenu(pieceView)
        pieceView.setTag(
            R.id.tag_object,
            `object`
        ) // It serves various processes to recognize the piece //Serve a vari processi per riconoscere il pezzo
        return pieceView
    }

    fun placeExtensions(container: ExtensionContainer) {
        for (ext in U.findExtensions(container)) {
            placePiece(ext.name, ext.text, ext.gedcomTag, false)
        }
    }

    /**
     * Delete an address from the 3 possible containers
     */
    fun deleteAddress(container: Any?) {
        when (container) {
            is EventFact -> container.address =
                null
            is Repository -> container.address =
                null
            is Submitter -> container.address = null
        }
    }

    /**
     * Compose the title of family events
     */
    fun getEventTitle(family: Family?, event: EventFact) = when (event.tag) {
        "MARR" -> if (U.areMarried(family)) getString(R.string.marriage) else getString(R.string.relationship)
        "DIV" -> if (U.areMarried(family)) getString(R.string.divorce) else getString(R.string.separation)
        "EVEN" -> getString(R.string.event)
        "RESI" -> getString(R.string.residence)
        else -> event.displayType
    } +
            if (event.type?.isNotEmpty() == true && event.type != "marriage")
                " (${TypeView.getTranslatedType(event.type, TypeView.Combo.RELATIONSHIP)}"
            else ""

    lateinit var editText: EditText
    fun edit(pieceView: View?) {
        concludeOtherPiece()
        // Then make this piece editable //Poi rende editabile questo pezzo
        val textView = pieceView!!.findViewById<TextView>(R.id.fatto_testo)
        textView.visibility = View.GONE
        fab.hide()
        val pieceObject = pieceView.getTag(R.id.tag_object)
        var showInput = false
        editText = pieceView.findViewById(R.id.fatto_edita)
        // Place
        if (pieceObject == "Place") {
            showInput = true
            // If it hasn't already done so, it replaces EditText with PlaceFinderTextView
            if (editText !is PlaceFinderTextView) {
                val parent =
                    pieceView as ViewGroup? // todo: you could use Partview (vistaPezzo) directly if it were a ViewGroup or LinearLayout instead of View
                val index = parent!!.indexOfChild(editText)
                parent.removeView(editText)
                editText = PlaceFinderTextView(editText.getContext(), null)
                editText.setId(R.id.fatto_edita)
                parent.addView(editText, index)
            } else editText.setVisibility(View.VISIBLE)
        } // Name type
        else if (obj is Name && pieceObject == "Type") {
            if (editText !is TypeView) {
                val parent = pieceView as ViewGroup?
                parent!!.removeView(editText)
                editText = TypeView(editText.getContext(), TypeView.Combo.NAME)
                parent.addView(editText, parent.indexOfChild(editText))
            } else editText.visibility = View.VISIBLE
        } // Marriage/relationship type
        else if (obj is EventFact && pieceObject == "Type" && (obj as EventFact).tag == "MARR") {
            if (editText !is TypeView) {
                val parent = pieceView as ViewGroup?
                parent!!.removeView(editText)
                editText = TypeView(editText.getContext(), TypeView.Combo.RELATIONSHIP)
                parent.addView(editText, parent.indexOfChild(editText))
            } else editText.setVisibility(View.VISIBLE)
        } // Data
        else if (pieceObject == "Date") {
            editText.visibility = View.VISIBLE
        } // All other normal editing cases
        else {
            showInput = true
            editText.visibility = View.VISIBLE
        }
        if (showInput) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(
                InputMethodManager.SHOW_FORCED,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )
        }
        val text = textView.text
        editText.setText(text)
        editText.requestFocus()
        editText.setSelection(text.length) // Cursor at the end

        // Intercept the 'Done' and 'Next' on the keyboard
        editText.setOnEditorActionListener { vista: TextView?, actionId: Int, keyEvent: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) save(pieceView) else if (actionId == EditorInfo.IME_ACTION_NEXT) {
                if (editText.text.toString() != textView.text.toString()) save(pieceView) else restore(
                    pieceView
                )
                val nextPiece = box.getChildAt(box.indexOfChild(pieceView) + 1)
                if (nextPiece?.getTag(R.id.tag_object) is String) edit(nextPiece)
            }
            false
        }

        // Custom ActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(false) // hides arrow <-
        whichMenu = 0
        invalidateOptionsMenu()
        val editBar = layoutInflater.inflate(
            R.layout.barra_edita, LinearLayout(
                box.context
            ), false
        )
        editBar.findViewById<View>(R.id.edita_annulla).setOnClickListener { v: View? ->
            editText.setText(textView.text)
            restore(pieceView)
        }
        editBar.findViewById<View>(R.id.edita_salva)
            .setOnClickListener { v: View? -> save(pieceView) }
        actionBar!!.customView = editBar
        actionBar!!.setDisplayShowCustomEnabled(true)
    }

    fun save(pieceView: View?) {
        if (publisherDateLinearLayout != null) publisherDateLinearLayout!!.encloseInParentheses() // Basically just to add parentheses to the given sentence
        val text = editText.text.toString().trim { it <= ' ' }
        val pieceObject = pieceView!!.getTag(R.id.tag_object)
        if (pieceObject is Int) { // Save first and last name for inexperienced (non-expert mode?)
            val firstName = (box.getChildAt(0)
                .findViewById<View>(R.id.fatto_edita) as EditText).text.toString()
            val lastName = (box.getChildAt(1)
                .findViewById<View>(R.id.fatto_edita) as EditText).text.toString()
            (obj as Name?)!!.value = "$firstName /$lastName/"
        } else try { // All other normal methods
            obj!!.javaClass.getMethod("set$pieceObject", String::class.java)
                .invoke(obj, text) //TODO reflection
        } catch (e: Exception) {
            Toast.makeText(box.context, e.localizedMessage, Toast.LENGTH_LONG).show()
            return  // in case of error it remains in editor mode
        }
        (pieceView.findViewById<View>(R.id.fatto_testo) as TextView).text = text
        restore(pieceView)
        U.save(true, Memory.firstObject())
        /*if( Memory.getStepStack().size() == 1 ) {
			ricrea(); // Todo The record change date should be updated, but perhaps without reloading everything.
		}*/
        // In modified image the path updates the image (?) //In immagine modificato il percorso aggiorna l'immagine
        if (this is ImageActivity && pieceObject == "File") this.updateImage() else if (obj is Submitter) U.mainAuthor(
            this,
            (obj as Submitter).id
        ) else (this as? EventActivity)?.refresh() // To update the title bar
    }

    /**
     * Operations common to Save and Cancel
     */
    fun restore(viewPiece: View?) {
        editText.visibility = View.GONE
        viewPiece!!.findViewById<View>(R.id.fatto_data).visibility = View.GONE
        viewPiece.findViewById<View>(R.id.fatto_testo).visibility =
            View.VISIBLE
        actionBar!!.setDisplayShowCustomEnabled(false) // hides custom bar// nasconde barra personalizzata
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        whichMenu = 1
        invalidateOptionsMenu()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(viewPiece.windowToken, 0)
        if (!(obj is Note && !Global.settings.expert)) // Notes in inexperienced mode have no fab //Le note in modalità inesperto non hanno fab
            fab!!.show()
    }

    /**
     * Options menu
     * serves to hide it when entering editor mode
     */
    var whichMenu = 1
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (whichMenu == 1) { // Standard bar menu//Menu standard della barra
            if (obj is Submitter && (Global.gc?.header == null || // Non-principal(/main?) author
                        Global.gc!!.header.getSubmitter(Global.gc) == null || Global.gc!!.header.getSubmitter(
                    Global.gc
                ) != obj)
            ) menu.add(0, 1, 0, R.string.make_default)
            if (obj is Media) {
                if (box.findViewById<View>(R.id.immagine_foto)
                        .getTag(R.id.tag_file_type) == 1
                ) menu.add(0, 2, 0, R.string.crop)
                menu.add(0, 3, 0, R.string.choose_file)
            }
            if (obj is Family) menu.add(
                0,
                4,
                0,
                R.string.delete
            ) else if (!(obj is Submitter && U.submitterHasShared(obj as Submitter))) // the author who shared cannot be deleted
                menu.add(0, 5, 0, R.string.delete)
        }
        return true
    }

    /**
     * is invoked when a menu item is chosen AND by clicking the back arrow
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == 1) { // Main author //TODO code smell : magic number
            ListOfAuthorsFragment.setMainSubmitter(obj as Submitter?)
        } else if (id == 2) { // Image: crop
            cropImage(box)
        } else if (id == 3) { // Image: choose
            displayMediaAppList(this, null, 5173, null)
        } else if (id == 4) { // Family
            val fam = obj as Family?
            if (fam!!.husbandRefs.size + fam.wifeRefs.size + fam.childRefs.size > 0) {
                AlertDialog.Builder(this).setMessage(R.string.really_delete_family)
                    .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, i: Int ->
                        FamiliesFragment.deleteFamily(fam)
                        onBackPressed()
                    }.setNeutralButton(android.R.string.cancel, null).show()
            } else {
                FamiliesFragment.deleteFamily(fam)
                onBackPressed()
            }
        } else if (id == 5) { // All the others
            // todo: confirm deletion of all objects..
            delete()
            U.save(true) // the update of the dates takes place in the Overrides of delete()
            onBackPressed()
        } else if (id == android.R.id.home) {
            onBackPressed()
        }
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (obj is EventFact) cleanUpTag((obj as EventFact?)!!)
        Memory.clearStackAndRemove()
    }

    open fun delete() {}

    // Contextual menu
    lateinit var pieceView // editable text, notes, quotes, media ...
            : View
    lateinit var pieceObject: Any
    lateinit var person // as it is used a lot, we make it a pieceObject in its own right
            : Person

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        info: ContextMenuInfo
    ) { // info is null
        if (whichMenu != 0) { // If we are in edit mode show the editor menus
            pieceView = view
            pieceObject = view.getTag(R.id.tag_object)
            if (pieceObject is Person) {
                person = pieceObject as Person
                val fam = obj as Family?
                // Generate labels for 'family' entries (such as child and spouse)
                val famLabels = arrayOf<String?>(null, null)
                if (person!!.getParentFamilies(Global.gc).size > 1 && person!!.getSpouseFamilies(
                        Global.gc
                    ).size > 1
                ) {
                    famLabels[0] = getString(R.string.family_as_child)
                    famLabels[1] = getString(R.string.family_as_spouse)
                }
                menu.add(0, 10, 0, R.string.diagram)
                menu.add(0, 11, 0, R.string.card)
                if (famLabels[0] != null) menu.add(0, 12, 0, famLabels[0])
                if (famLabels[1] != null) menu.add(0, 13, 0, famLabels[1])
                if (fam!!.getChildren(Global.gc).indexOf(person) > 0) menu.add(
                    0,
                    14,
                    0,
                    R.string.move_before
                )
                if (fam.getChildren(Global.gc)
                        .indexOf(person) < fam.getChildren(Global.gc).size - 1 && fam.getChildren(
                        Global.gc
                    ).contains(person)
                ) // thus excludes parents whose index is -1
                    menu.add(0, 15, 0, R.string.move_after)
                menu.add(0, 16, 0, R.string.modify)
                if (findParentFamilyRef(person!!, fam) != null) menu.add(0, 17, 0, R.string.lineage)
                menu.add(0, 18, 0, R.string.unlink)
                menu.add(0, 19, 0, R.string.delete)
            } else if (pieceObject is Note) {
                menu.add(0, 20, 0, R.string.copy)
                if ((pieceObject as Note).id != null) menu.add(0, 21, 0, R.string.unlink)
                menu.add(0, 22, 0, R.string.delete)
            } else if (pieceObject is SourceCitation) {
                menu.add(0, 30, 0, R.string.copy)
                menu.add(0, 31, 0, R.string.delete)
            } else if (pieceObject is Media) {
                if ((pieceObject as Media).id != null) menu.add(0, 40, 0, R.string.unlink)
                menu.add(0, 41, 0, R.string.delete)
            } else if (pieceObject is Address) {
                menu.add(0, 50, 0, R.string.copy)
                menu.add(0, 51, 0, R.string.delete)
            } else if (pieceObject is EventFact) {
                menu.add(0, 55, 0, R.string.copy)
                val fam = obj as Family?
                if (fam!!.eventsFacts.indexOf(pieceObject) > 0) menu.add(0, 56, 0, R.string.move_up)
                if (fam.eventsFacts.contains(pieceObject)
                    && fam.eventsFacts.indexOf(pieceObject) < fam.eventsFacts.size - 1
                ) menu.add(0, 57, 0, R.string.move_down)
                menu.add(0, 58, 0, R.string.delete)
            } else if (pieceObject is GedcomTag) {
                menu.add(0, 60, 0, R.string.copy)
                menu.add(0, 61, 0, R.string.delete)
            } else if (pieceObject is Source) {
                menu.add(0, 70, 0, R.string.copy)
                menu.add(0, 71, 0, R.string.choose_source)
            } else if (pieceObject is RepositoryRef) {
                menu.add(0, 80, 0, R.string.copy)
                menu.add(0, 81, 0, R.string.delete)
            } else if (pieceObject is Repository) {
                menu.add(0, 90, 0, R.string.copy)
                menu.add(0, 91, 0, R.string.choose_repository)
            } else if (pieceObject is Int) {
                if (pieceObject == 43614) { //Google translate: "Imagine it", probably Image// Immaginona
                    // it is a croppable image
                    if (pieceView!!.findViewById<View>(R.id.immagine_foto)
                            .getTag(R.id.tag_file_type) == 1
                    ) menu.add(0, 100, 0, R.string.crop)
                    menu.add(0, 101, 0, R.string.choose_file)
                } else if (pieceObject == 4043 || pieceObject == 6064) // Name and surname for inexperienced
                    menu.add(0, 0, 0, R.string.copy)
            } else if (pieceObject is String) {
                if ((view.findViewById<View>(R.id.fatto_testo) as TextView).text.length > 0) menu.add(
                    0,
                    0,
                    0,
                    R.string.copy
                )
                menu.add(0, 1, 0, R.string.delete)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0, 50, 55, 60 -> {
                U.copyToClipboard(
                    (pieceView!!.findViewById<View>(R.id.fatto_titolo) as TextView).text,
                    (pieceView!!.findViewById<View>(R.id.fatto_testo) as TextView).text
                )
                return true
            }
            1 -> try {
                obj!!.javaClass.getMethod("set$pieceObject", String::class.java)
                    .invoke(obj, null as Any?)
            } catch (e: Exception) {
                Toast.makeText(box.context, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
            10 -> {
                U.askWhichParentsToShow(this, person, 1)
                return true
            }
            11 -> {
                Memory.setFirst(person)
                startActivity(Intent(this, ProfileActivity::class.java))
                return true
            }
            12 -> {
                U.askWhichParentsToShow(this, person, 2)
                return true
            }
            13 -> {
                U.askWhichSpouseToShow(this, person, null)
                return true
            }
            14 -> {
                val fa = obj as Family?
                val childRef1 = fa!!.childRefs[fa.getChildren(Global.gc).indexOf(person)]
                fa.childRefs.add(fa.childRefs.indexOf(childRef1) - 1, childRef1)
                fa.childRefs.removeAt(fa.childRefs.lastIndexOf(childRef1))
            }
            15 -> {
                val f = obj as Family?
                val childRef = f!!.childRefs[f.getChildren(Global.gc).indexOf(person)]
                f.childRefs.add(f.childRefs.indexOf(childRef) + 2, childRef)
                f.childRefs.removeAt(f.childRefs.indexOf(childRef)) //TODO can this be optimizeed to use removal by object?
            }
            16 -> {
                val i = Intent(this, IndividualEditorActivity::class.java)
                i.putExtra(PROFILE_ID_KEY, person!!.id)
                startActivity(i)
                return true
            }
            17 -> chooseLineage(this, person!!, obj as Family?)
            18 -> {
                disconnect(
                    (pieceView!!.getTag(R.id.tag_spouse_family_ref) as SpouseFamilyRef),
                    (pieceView!!.getTag(R.id.tag_spouse_ref) as SpouseRef)
                )
                U.updateChangeDate(person)
                findAnotherRepresentativeOfTheFamily(person)
            }
            19 -> {
                AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete) { dialog: DialogInterface?, id: Int ->
                        ListOfPeopleFragment.deletePerson(this, person!!.id)
                        box.removeView(pieceView)
                        findAnotherRepresentativeOfTheFamily(person)
                    }.setNeutralButton(R.string.cancel, null).show()
                return true
            }
            20 -> {
                U.copyToClipboard(
                    getText(R.string.note),
                    (pieceView!!.findViewById<View>(R.id.note_text) as TextView).text
                )
                return true
            }
            21 -> U.disconnectNote(pieceObject as Note, obj, null)
            22 -> {
                val heads /*capi*/ = U.deleteNote(pieceObject as Note, pieceView)
                U.save(true, *heads)
                return true
            }
            30 -> {
                U.copyToClipboard(
                    getText(R.string.source_citation),
                    """
                        ${(pieceView!!.findViewById<View>(R.id.fonte_testo) as TextView).text}
                        ${(pieceView!!.findViewById<View>(R.id.citazione_testo) as TextView).text}
                        """.trimIndent()
                )
                return true
            }
            31 -> {
                if (obj is Note) // Notes does not extend SourceCitationContainer
                    (obj as Note).sourceCitations.remove(pieceObject) else (obj as SourceCitationContainer?)!!.sourceCitations.remove(
                    pieceObject
                )
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            40 -> GalleryFragment.disconnectMedia(
                (pieceObject as Media?)!!.id,
                obj as MediaContainer
            )
            41 -> {
                val mediaHeads = GalleryFragment.deleteMedia(pieceObject as Media?, null)
                U.save(
                    true,
                    *mediaHeads
                ) // a shared media may need to update the dates of multiple heads
                refresh()
                return true
            }
            51 -> deleteAddress(obj)
            56 -> {
                val index1 = (obj as Family?)!!.eventsFacts.indexOf(pieceObject)
                Collections.swap((obj as Family?)!!.eventsFacts, index1, index1 - 1)
            }
            57 -> {
                val index2 = (obj as Family?)!!.eventsFacts.indexOf(pieceObject)
                Collections.swap((obj as Family?)!!.eventsFacts, index2, index2 + 1)
            }
            58 -> {
                (obj as Family?)!!.eventsFacts.remove(pieceObject)
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            61 -> U.deleteExtension(pieceObject as GedcomTag, obj, null)
            70 -> {
                U.copyToClipboard(
                    getText(R.string.source),
                    (pieceView!!.findViewById<View>(R.id.fonte_testo) as TextView).text
                )
                return true
            }
            71 -> {
                val inte = Intent(this, Principal::class.java)
                inte.putExtra(Choice.SOURCE, true)
                startActivityForResult(inte, 7047)
                return true
            }
            80 -> {
                U.copyToClipboard(
                    getText(R.string.repository_citation),
                    """
                        ${(pieceView!!.findViewById<View>(R.id.fonte_testo) as TextView).text}
                        ${(pieceView!!.findViewById<View>(R.id.citazione_testo) as TextView).text}
                        """.trimIndent()
                )
                return true
            }
            81 -> {
                (obj as Source?)!!.repositoryRef = null
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            90 -> {
                U.copyToClipboard(
                    getText(R.string.repository),
                    (pieceView!!.findViewById<View>(R.id.fonte_testo) as TextView).text
                )
                return true
            }
            91 -> {
                val intn = Intent(this, Principal::class.java)
                intn.putExtra(Choice.REPOSITORY, true)
                startActivityForResult(intn, 5390)
                return true
            }
            100 -> {
                cropImage(pieceView)
                return true
            }
            101 -> {
                displayMediaAppList(this, null, 5173, null)
                return true
            }
            else -> return false
        }
        // First recreate the page and then save, which for large trees can take a few seconds
        // closeContextMenu(); // Useless. Closing the menu waits for the end of saving,
        // unless you put saveJson () inside a postDelayed () of at least 500 ms
        Memory.firstObject()?.let { U.updateChangeDate(it) }
        refresh()
        U.save(true, null as Array<Any?>?)
        return true
    }

    /**
     * Fix a Family Representative to correctly show "Link Existing Person" in the menu
     */
    private fun findAnotherRepresentativeOfTheFamily(p: Person?) {
        if (aRepresentativeOfTheFamily == p) {
            val fam = obj as Family?
            aRepresentativeOfTheFamily = when {
                fam!!.getHusbands(Global.gc).isNotEmpty() -> fam.getHusbands(Global.gc)[0]
                fam.getWives(Global.gc).isNotEmpty() -> fam.getWives(Global.gc)[0]
                fam.getChildren(Global.gc).isNotEmpty() -> fam.getChildren(Global.gc)[0]
                else -> null
            }
        }
    }

    /**
     * Receives a View in which there is the image to be cropped and starts cropping
     */
    private fun cropImage(view: View?) {
        val imageView = view!!.findViewById<ImageView>(R.id.immagine_foto)
        var mediaFile: File? = null
        val path = imageView.getTag(R.id.tag_path) as String
        if (path != null) mediaFile = File(path)
        val mediaUri = imageView.getTag(R.id.tag_uri) as Uri
        Global.croppedMedia = obj as Media
        cropImage(this, mediaFile, mediaUri, null)
    }

    /**
     * Closes the keyboard that may be visible
     */
    override fun onPause() {
        super.onPause()
        if (editText != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsResult(
            this, null, requestCode, permissions, grantResults,
            (if (obj is MediaContainer) obj as MediaContainer? else null)!!
        )
        // Image has Media 'object' instance, not MediaContainer
    }

    companion object {
        @JvmStatic
        fun writeAddress(adr: Address?, oneLine: Boolean): String? {
            if (adr == null) return null
            var txt = "" //TODO use StringBuilder
            val br = if (oneLine) ", " else "\n"
            if (adr.value != null) txt = adr.value + br
            if (adr.addressLine1 != null) txt += adr.addressLine1 + br
            if (adr.addressLine2 != null) txt += adr.addressLine2 + br
            if (adr.addressLine3 != null) txt += adr.addressLine3 + br
            if (adr.postalCode != null) txt += adr.postalCode + " "
            if (adr.city != null) txt += adr.city + " "
            if (adr.state != null) txt += adr.state
            if (adr.postalCode != null || adr.city != null || adr.state != null) txt += br
            if (adr.country != null) txt += adr.country
            if (txt.endsWith(br)) txt = txt.substring(0, txt.length - br.length).trim { it <= ' ' }
            return txt
        }

        /**
         * He composes the text of the event in FamilyActivity
         * // Compone il testo dell'evento in Famiglia
         */
        fun writeEvent(ef: EventFact?): String? {
            if (ef == null) return null
            var txt = "" //TODO use StringBuilder
            if (ef.value != null) {
                txt =
                    if (ef.value == "Y" && ef.tag != null && (ef.tag == "MARR" || ef.tag == "DIV")) Global.context.getString(
                        R.string.yes
                    ) else ef.value
                txt += "\n"
            }
            if (ef.date != null) txt += """
     ${GedcomDateConverter(ef.date).writeDateLong()}
     
     """.trimIndent()
            if (ef.place != null) txt += """
     ${ef.place}
     
     """.trimIndent()
            val indirizzo = ef.address
            if (indirizzo != null) txt += """
     ${writeAddress(indirizzo, true)}
     
     """.trimIndent()
            if (txt.endsWith("\n")) txt = txt.substring(0, txt.length - 1)
            return txt
        }
    }
}