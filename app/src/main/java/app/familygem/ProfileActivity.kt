package app.familygem

import app.familygem.constant.Gender.Companion.getGender
import app.familygem.F.displayMediaAppList
import app.familygem.Memory.Companion.add
import app.familygem.list.NotesFragment.Companion.newNote
import app.familygem.F.showMainImageForPerson
import app.familygem.F.mediaPath
import app.familygem.F.mediaUri
import app.familygem.F.proposeCropping
import app.familygem.GalleryFragment.Companion.newMedia
import app.familygem.F.endImageCropping
import app.familygem.IndividualEditorActivity.Companion.addParent
import app.familygem.Memory.Companion.clearStackAndRemove
import app.familygem.Diagram.Companion.getFamilyLabels
import app.familygem.ListOfPeopleFragment.Companion.deletePerson
import app.familygem.F.permissionsResult
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import android.os.Bundle
import app.familygem.U
import app.familygem.Memory
import app.familygem.R
import androidx.viewpager.widget.ViewPager
import app.familygem.ProfileActivity.SectionsPaginator
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.fragment.app.FragmentPagerAdapter
import app.familygem.ProfileMediaFragment
import app.familygem.ProfileFactsFragment
import app.familygem.ProfileRelativesFragment
import android.widget.TextView
import com.google.android.material.appbar.CollapsingToolbarLayout
import android.view.SubMenu
import app.familygem.F
import android.content.Intent
import app.familygem.detail.NameActivity
import android.content.DialogInterface
import app.familygem.detail.NoteActivity
import app.familygem.list.NotesFragment
import app.familygem.detail.SourceCitationActivity
import app.familygem.SourcesFragment
import app.familygem.NewRelativeDialog
import app.familygem.IndividualEditorActivity
import app.familygem.detail.EventActivity
import com.squareup.picasso.RequestCreator
import androidx.core.content.ContextCompat
import android.graphics.PorterDuff
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.BlurTransformation
import android.app.Activity
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import app.familygem.GalleryFragment
import com.theartofdev.edmodo.cropper.CropImage
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import app.familygem.ListOfPeopleFragment
import app.familygem.constant.Choice
import app.familygem.constant.Gender
import app.familygem.constant.intdefs.*
import org.folg.gedcom.model.*
import java.util.*

class ProfileActivity : AppCompatActivity() {
    var thisPerson: Person? = null
    lateinit var tabLayout: TabLayout
    var tabs = arrayOfNulls<Fragment>(3)
    var mainEventTags = arrayOf("BIRT", "BAPM", "RESI", "OCCU", "DEAT", "BURI")
    var otherEvents // List of tag + label
            : MutableList<Pair<String, String>>? = null

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        U.ensureGlobalGedcomNotNull(Global.gc)
        thisPerson = Memory.`object` as Person?
        // If the app goes into the background and is stopped, 'Memory' is reset and therefore 'thisPerson' will be null
        if (thisPerson == null && bundle != null) {
            thisPerson =
                Global.gc!!.getPerson(bundle.getString(THIS_PERSON_KEY)) // The individual's id is saved in the bundle
            Memory.setFirst(thisPerson) // Otherwise the memory is without a stack
        }
        if (thisPerson == null) return  // Rarely does the bundle not do its job
        Global.indi = thisPerson!!.id
        setContentView(R.layout.individuo)

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true) // brings up the back arrow and menu

        // Give the page view an adapter that manages the three tabs
        val viewPager = findViewById<ViewPager>(R.id.profile_pager)
        val sectionsPaginator = SectionsPaginator()
        viewPager.adapter = sectionsPaginator

        // "enriches"/populates the tablayout
        tabLayout = findViewById(R.id.profile_tabs)
        tabLayout.setupWithViewPager(viewPager) // otherwise the text in the TabItems disappears (?!)
        tabLayout.getTabAt(0)!!.setText(R.string.media)
        tabLayout.getTabAt(1)!!.setText(R.string.events)
        tabLayout.getTabAt(2)!!.setText(R.string.relatives)
        tabLayout.getTabAt(intent.getIntExtra(CARD_KEY, 1))!!.select()

        // to animate the FAB
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,  // 0 between first and second, 1 between second and third...
                offset: Float,  // 1->0 to the right, 0->1 to the left //delta? direction?
                positionOffsetPixels: Int
            ) {
                if (offset > 0) fab.hide() else fab.show()
            }

            override fun onPageSelected(position: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
        })

        // List of other events
        val otherEventTags = arrayOf(
            "CHR", "CREM", "ADOP", "BARM", "BATM", "BLES", "CONF", "FCOM", "ORDN",  //Events
            "NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
            "CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL",  // Attributes
            "_MILT"
        ) // User-defined
        /* Standard GEDCOM tags missing in the EventFact.DISPLAY_TYPE list:
				BASM (there is BATM instead) CHRA IDNO NMR FACT */otherEvents = ArrayList()
        for (tag in otherEventTags) {
            val event = EventFact()
            event.tag = tag
            var label = event.displayType
            if (Global.settings!!.expert) label += " — $tag"
            (otherEvents as ArrayList<Pair<String, String>>).add(Pair(tag, label))
        }
        // Alphabetically sorted by label
        Collections.sort(otherEvents) { item1: Pair<String, String>, item2: Pair<String, String> ->
            item1.second.compareTo(
                item2.second
            )
        }
    }

    internal inner class SectionsPaginator : FragmentPagerAdapter(supportFragmentManager) {
        // it doesn't actually select but CREATE the three tabs
        override fun getItem(position: Int): Fragment {
            if (position == 0) tabs[0] = ProfileMediaFragment() else if (position == 1) tabs[1] =
                ProfileFactsFragment() else if (position == 2) tabs[2] = ProfileRelativesFragment()
            return tabs[position]!!
        }

        override fun getCount(): Int {
            return 3
        }
    }

    override fun onStart() {
        super.onStart()
        if (thisPerson == null || Global.edited) thisPerson = Global.gc!!.getPerson(Global.indi)
        if (thisPerson == null) { // going back to the Record of an individual who has been deleted
            onBackPressed()
            return
        }


        // Person ID in the header
        val idView = findViewById<TextView>(R.id.profile_id)
        if (Global.settings!!.expert) {
            idView.text = "INDI " + thisPerson!!.id
            idView.setOnClickListener { v: View? -> U.editId(this, thisPerson!!) { refresh() } }
        } else idView.visibility = View.GONE
        // Person name in the header
        val toolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.profile_toolbar_layout)
        toolbarLayout.title = U.properName(thisPerson)
        toolbarLayout.setExpandedTitleTextAppearance(R.style.AppTheme_ExpandedAppBar)
        toolbarLayout.setCollapsedTitleTextAppearance(R.style.AppTheme_CollapsedAppBar)
        setImages()
        if (Global.edited) {
            // Reload the 3 tabs coming back to the profile
            for (tab in tabs) {
                if (tab != null) { // At the first activity creation they are null
                    supportFragmentManager.beginTransaction().detach(tab).commit()
                    supportFragmentManager.beginTransaction().attach(tab).commit()
                }
            }
            invalidateOptionsMenu()
        }

        // Menu FAB
        findViewById<View>(R.id.fab).setOnClickListener { vista: View? ->
            val popup = PopupMenu(this, vista!!)
            val menu = popup.menu
            when (tabLayout!!.selectedTabPosition) {
                0 -> {
                    menu.add(0, 10, 0, R.string.new_media)
                    menu.add(0, 11, 0, R.string.new_shared_media)
                    if (!Global.gc!!.media.isEmpty()) menu.add(0, 12, 0, R.string.link_shared_media)
                }
                1 -> {
                    menu.add(0, 20, 0, R.string.name)
                    // Gender
                    if (getGender(thisPerson!!) === Gender.NONE) menu.add(0, 21, 0, R.string.sex)
                    // Main events
                    val eventSubMenu = menu.addSubMenu(R.string.event)
                    val mainEventLabels = listOf<String>(
                        getText(R.string.birth).toString(),
                        getText(R.string.baptism).toString(),
                        getText(R.string.residence).toString(),
                        getText(R.string.occupation).toString(),
                        getText(R.string.death).toString(),
                        getText(R.string.burial).toString()
                    )
                    var i: Int
                    i = 0
                    while (i < mainEventLabels.size) {
                        var label = mainEventLabels[i]
                        if (Global.settings!!.expert) label += " — " + mainEventTags[i]
                        eventSubMenu.add(0, 40 + i, 0, label)
                        i++
                    }
                    // Other events
                    val otherSubMenu = eventSubMenu.addSubMenu(R.string.other)
                    i = 0
                    for (item in otherEvents!!) {
                        otherSubMenu.add(0, 50 + i, 0, item.second as String)
                        i++
                    }
                    val subNote = menu.addSubMenu(R.string.note)
                    subNote.add(0, 22, 0, R.string.new_note)
                    subNote.add(0, 23, 0, R.string.new_shared_note)
                    if (!Global.gc!!.notes.isEmpty()) subNote.add(
                        0,
                        24,
                        0,
                        R.string.link_shared_note
                    )
                    if (Global.settings!!.expert) {
                        val subSource = menu.addSubMenu(R.string.source)
                        subSource.add(0, 25, 0, R.string.new_source_note)
                        subSource.add(0, 26, 0, R.string.new_source)
                        if (!Global.gc!!.sources.isEmpty()) subSource.add(
                            0,
                            27,
                            0,
                            R.string.link_source
                        )
                    }
                }
                2 -> {
                    menu.add(0, 30, 0, R.string.new_relative)
                    if (U.containsConnectableIndividuals(thisPerson)) menu.add(
                        0,
                        31,
                        0,
                        R.string.link_person
                    )
                }
            }
            popup.show()
            popup.setOnMenuItemClickListener { item: MenuItem ->
                val members = arrayOf(
                    getText(R.string.parent),
                    getText(R.string.sibling),
                    getText(R.string.partner),
                    getText(R.string.child)
                )
                val builder = AlertDialog.Builder(this)
                when (item.itemId) {
                    0 -> {}
                    10 -> displayMediaAppList(this, null, 2173, thisPerson)
                    11 -> displayMediaAppList(this, null, 2174, thisPerson)
                    12 -> {
                        val principalIntent = Intent(this, Principal::class.java)
                        principalIntent.putExtra(Choice.MEDIA, true)
                        startActivityForResult(principalIntent, 43614)
                    }
                    20 -> {
                        val name = Name()
                        name.value = "//"
                        thisPerson!!.addName(name)
                        add(name)
                        startActivity(Intent(this, NameActivity::class.java))
                        U.save(true, thisPerson)
                    }
                    21 -> {
                        val sexNames = arrayOf(
                            getString(R.string.male),
                            getString(R.string.female),
                            getString(R.string.unknown)
                        )
                        AlertDialog.Builder(tabLayout!!.context)
                            .setSingleChoiceItems(sexNames, -1) { dialog: DialogInterface, i: Int ->
                                val gender = EventFact()
                                gender.tag = "SEX"
                                val sexValues = arrayOf("M", "F", "U")
                                gender.value = sexValues[i]
                                thisPerson!!.addEventFact(gender)
                                dialog.dismiss()
                                ProfileFactsFragment.updateMaritalRoles(thisPerson)
                                refresh()
                                U.save(true, thisPerson)
                            }.show()
                    }
                    22 -> {
                        val note = Note()
                        note.value = ""
                        thisPerson!!.addNote(note)
                        add(note)
                        startActivity(Intent(this, NoteActivity::class.java))
                        // todo? DetailActivity.edit(View viewValue);
                        U.save(true, thisPerson)
                    }
                    23 -> newNote(this, thisPerson)
                    24 -> {
                        val intent = Intent(this, Principal::class.java)
                        intent.putExtra(Choice.NOTE, true)
                        startActivityForResult(intent, 4074)
                    }
                    25 -> {
                        val citation = SourceCitation()
                        citation.value = ""
                        thisPerson!!.addSourceCitation(citation)
                        add(citation)
                        startActivity(Intent(this, SourceCitationActivity::class.java))
                        U.save(true, thisPerson)
                    }
                    26 -> SourcesFragment.createNewSource(this, thisPerson)
                    27 -> startActivityForResult(
                        Intent(
                            this,
                            Principal::class.java
                        ).putExtra(Choice.SOURCE, true), 50473
                    )
                    30 -> if (Global.settings!!.expert) {
                        val dialog: DialogFragment =
                            NewRelativeDialog(thisPerson, null, null, true, null)
                        dialog.show(supportFragmentManager, "scegli")
                    } else {
                        builder.setItems(members) { dialog: DialogInterface?, quale: Int ->
                            val intent1 = Intent(
                                applicationContext, IndividualEditorActivity::class.java
                            )
                            intent1.putExtra(PROFILE_ID_KEY, thisPerson!!.id)
                            intent1.putExtra(RELATIONSHIP_ID_KEY, quale + 1)
                            if (U.checkMultipleMarriages(intent1, this, null)) return@setItems
                            startActivity(intent1)
                        }.show()
                    }
                    31 -> if (Global.settings!!.expert) {
                        val dialog: DialogFragment =
                            NewRelativeDialog(thisPerson, null, null, false, null)
                        dialog.show(supportFragmentManager, "scegli")
                    } else {
                        builder.setItems(members) { dialog: DialogInterface?, which: Int ->
                            val intent2 = Intent(
                                application, Principal::class.java
                            )
                            intent2.putExtra(PROFILE_ID_KEY, thisPerson!!.id)
                            intent2.putExtra(Choice.PERSON, true)
                            intent2.putExtra(RELATIONSHIP_ID_KEY, which + 1)
                            if (U.checkMultipleMarriages(intent2, this, null)) return@setItems
                            startActivityForResult(intent2, 1401)
                        }.show()
                    }
                    else -> {
                        var keyTag: String? = null
                        if (item.itemId >= 50) {
                            keyTag = otherEvents!![item.itemId - 50].first
                        } else if (item.itemId >= 40) keyTag = mainEventTags[item.itemId - 40]
                        if (keyTag == null) return@setOnMenuItemClickListener false
                        val newEvent = EventFact()
                        newEvent.tag = keyTag
                        when (keyTag) {
                            "OCCU" -> newEvent.value = ""
                            "RESI" -> newEvent.place = ""
                            "BIRT", "DEAT", "CHR", "BAPM", "BURI" -> {
                                newEvent.place = ""
                                newEvent.date = ""
                            }
                        }
                        thisPerson!!.addEventFact(newEvent)
                        add(newEvent)
                        startActivity(Intent(this, EventActivity::class.java))
                        U.save(true, thisPerson)
                    }
                }
                true
            }
        }
    }

    /* Display an image in the profile header
	   The blurred background image is displayed in most cases (jpg, png, gif...)
	   ToDo but not in case of a video preview, or image downloaded from the web with ZuppaMedia */
    fun setImages() {
        val imageView = findViewById<ImageView>(R.id.profile_image)
        val media = showMainImageForPerson(Global.gc!!, thisPerson!!, imageView)
        // Same image blurred on background
        if (media != null) {
            val path = mediaPath(Global.settings!!.openTree, media)
            var uri: Uri? = null
            if (path == null) uri = mediaUri(Global.settings!!.openTree, media)
            if (path != null || uri != null) {
                val creator: RequestCreator
                val backImageView = findViewById<ImageView>(R.id.profile_background)
                backImageView.setColorFilter(
                    ContextCompat.getColor(
                        this, R.color.primary_grayed
                    ), PorterDuff.Mode.MULTIPLY
                )
                creator = if (path != null) Picasso.get().load("file://$path") else Picasso.get()
                    .load(uri)
                creator.resize(200, 200).centerCrop()
                    .transform(BlurTransformation(Global.context, 5, 1))
                    .into(backImageView)
            }
        }
    }

    // Refresh everyting without recreating the activity
    fun refresh() {
        // Name in the header
        val toolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.profile_toolbar_layout)
        toolbarLayout.title = U.properName(thisPerson)
        // Header images
        setImages()
        // ID in the header
        if (Global.settings!!.expert) {
            val idView = findViewById<TextView>(R.id.profile_id)
            idView.text = "INDI " + thisPerson!!.id
        }
        // 3 tabs
        for (tab in tabs) {
            if (tab != null) {
                val manager = supportFragmentManager
                manager.beginTransaction().detach(tab).commit()
                manager.beginTransaction().attach(tab).commit()
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(THIS_PERSON_KEY, thisPerson!!.id)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == 2173) { // File provided by an app becomes local media possibly cropped with Android Image Cropper
                val media = Media()
                media.fileTag = "FILE"
                thisPerson!!.addMedia(media)
                if (proposeCropping(
                        this,
                        null,
                        data,
                        media
                    )
                ) { // returns true if it is a clipable image
                    U.save(true, thisPerson)
                    return
                }
            } else if (requestCode == 2174) { // Files from apps in new Shared Media, with proposal to crop it
                val media = newMedia(thisPerson)
                if (proposeCropping(this, null, data, media)) {
                    U.save(true, media, thisPerson)
                    return
                }
            } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                // Get the image cropped by Android Image Cropper
                endImageCropping(data)
                U.save(true) // the switch date for Shared Media is already saved in the previous step
                // todo pass it Global.mediaCropped ?
                return
            } else if (requestCode == 43614) { // Media from GalleryFragment
                val mediaRef = MediaRef()
                mediaRef.ref = data!!.getStringExtra(MEDIA_ID_KEY)
                thisPerson!!.addMediaRef(mediaRef)
            } else if (requestCode == 4074) { // Note
                val noteRef = NoteRef()
                noteRef.ref = data!!.getStringExtra(NOTE_ID_KEY)
                thisPerson!!.addNoteRef(noteRef)
            } else if (requestCode == 50473) { // Source
                val citaz = SourceCitation()
                citaz.ref = data!!.getStringExtra(SOURCE_ID_KEY_ENGLISH)
                thisPerson!!.addSourceCitation(citaz)
            } else if (requestCode == 1401) { // Relative
                val modified = addParent(
                    data!!.getStringExtra(PROFILE_ID_KEY),  // corresponds to thisPerson.getId()
                    data.getStringExtra(RELATIVE_ID_KEY),
                    data.getStringExtra(FAMILY_ID_KEY),
                    data.getIntExtra(RELATIONSHIP_ID_KEY, 0),
                    data.getStringExtra(LOCATION_KEY)
                )
                U.save(true, *modified)
                return
            }
            U.save(true, thisPerson)
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) // if click back arrow in Crop Image
            Global.edited = true
    }

    override fun onBackPressed() {
        clearStackAndRemove()
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 0, 0, R.string.diagram)
        val familyLabels = getFamilyLabels(this, thisPerson, null)
        if (familyLabels[0] != null) menu.add(0, 1, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(0, 2, 0, familyLabels[1])
        if (Global.settings!!.currentTree!!.root == null || Global.settings!!.currentTree!!.root != thisPerson!!.id) menu.add(
            0,
            3,
            0,
            R.string.make_root
        )
        menu.add(0, 4, 0, R.string.modify)
        menu.add(0, 5, 0, R.string.delete)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> {
                U.askWhichParentsToShow(this, thisPerson, 1)
                return true
            }
            1 -> {
                U.askWhichParentsToShow(this, thisPerson, 2)
                return true
            }
            2 -> {
                U.askWhichSpouseToShow(this, thisPerson!!, null)
                return true
            }
            3 -> {
                Global.settings!!.currentTree!!.root = thisPerson!!.id
                Global.settings!!.save()
                Toast.makeText(
                    this,
                    getString(R.string.this_is_root, U.properName(thisPerson)),
                    Toast.LENGTH_LONG
                ).show()
                return true
            }
            4 -> {
                val intent1 = Intent(this, IndividualEditorActivity::class.java)
                intent1.putExtra(PROFILE_ID_KEY, thisPerson!!.id)
                startActivity(intent1)
                return true
            }
            5 -> {
                AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete) { dialog: DialogInterface?, i: Int ->
                        val families = deletePerson(this, thisPerson!!.id)
                        if (!U.checkFamilyItem(
                                this,
                                { onBackPressed() },
                                true,
                                *families
                            )
                        ) onBackPressed()
                    }.setNeutralButton(R.string.cancel, null).show()
                return true
            }
            else -> onBackPressed()
        }
        return false
    }

    override fun onRequestPermissionsResult(
        codice: Int,
        permessi: Array<String>,
        accordi: IntArray
    ) {
        super.onRequestPermissionsResult(codice, permessi, accordi)
        permissionsResult(this, null, codice, permessi, accordi, thisPerson!!)
    }
}