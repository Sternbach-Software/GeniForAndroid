package app.familygem

import app.familygem.BaseActivity.onCreate
import app.familygem.Settings.getTree
import app.familygem.NewTreeActivity.Companion.createHeader
import app.familygem.visitor.MediaList.list
import app.familygem.Settings.save
import app.familygem.BaseActivity
import android.os.Bundle
import app.familygem.R
import app.familygem.TreesActivity
import app.familygem.TreeInfoActivity
import app.familygem.U
import app.familygem.Settings.Share
import app.familygem.NewTreeActivity
import android.graphics.Typeface
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import app.familygem.visitor.MediaList
import org.folg.gedcom.model.*
import java.io.File
import java.lang.StringBuilder
import java.util.*

class TreeInfoActivity : BaseActivity() {
    var gc: Gedcom? = null
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.info_albero)
        val layout = findViewById<LinearLayout>(R.id.info_scatola)
        val treeId = intent.getIntExtra(TREE_ID_KEY, 1)
        val tree = Global.settings!!.getTree(treeId)
        val file = File(filesDir, "$treeId.json")
        val i = StringBuilder(getText(R.string.title).toString() + ": " + tree!!.title)
        if (!file.exists()) {
            i.append("\n\n").append(getText(R.string.item_exists_but_file)).append("\n")
                .append(file.absolutePath)
        } else {
            i.append("\n").append(getText(R.string.file)).append(": ").append(file.absolutePath)
            gc = TreesActivity.openGedcomTemporarily(treeId, false)
            if (gc == null) i.append("\n\n").append(getString(R.string.no_useful_data)) else {
                // Automatic or on-demand data update
                if (tree.persons < 100) {
                    refreshData(gc!!, tree)
                } else {
                    val updateButton = findViewById<Button>(R.id.info_aggiorna)
                    updateButton.visibility = View.VISIBLE
                    updateButton.setOnClickListener { v: View? ->
                        refreshData(gc!!, tree)
                        recreate()
                    }
                }
                i
                    .append("\n\n")
                    .append(getText(R.string.persons)).append(": ").append(tree.persons)
                    .append("\n")
                    .append(getText(R.string.families)).append(": ").append(gc!!.families.size)
                    .append("\n")
                    .append(getText(R.string.generations)).append(": ").append(tree.generations)
                    .append("\n")
                    .append(getText(R.string.media)).append(": ").append(tree.media)
                    .append("\n")
                    .append(getText(R.string.sources)).append(": ").append(gc!!.sources.size)
                    .append("\n")
                    .append(getText(R.string.repositories)).append(": ")
                    .append(gc!!.repositories.size)
                if (tree.root != null) {
                    i.append("\n").append(getText(R.string.root)).append(": ").append(
                        U.properName(
                            gc!!.getPerson(tree.root)
                        )
                    )
                }
                if (tree.shares != null && !tree.shares!!.isEmpty()) {
                    i.append("\n\n").append(getText(R.string.shares)).append(":")
                    for (share in tree.shares!!) {
                        i.append("\n").append(dataIdToDate(share.dateId))
                        if (gc!!.getSubmitter(share.submitter) != null) i.append(" - ").append(
                            submitterName(
                                gc!!.getSubmitter(share.submitter)
                            )
                        )
                    }
                }
            }
        }
        (findViewById<View>(R.id.info_statistiche) as TextView).text = i.toString()
        val headerButton = layout.findViewById<Button>(R.id.info_gestisci_testata)
        if (gc != null) {
            val h = gc!!.header
            if (h == null) {
                headerButton.setText(R.string.create_header)
                headerButton.setOnClickListener { view: View? ->
                    gc!!.header = createHeader(file.name)
                    U.saveJson(gc, treeId)
                    recreate()
                }
            } else {
                layout.findViewById<View>(R.id.info_testata).visibility = View.VISIBLE
                if (h.file != null) place(getText(R.string.file), h.file)
                if (h.characterSet != null) {
                    place(getText(R.string.characrter_set), h.characterSet.value)
                    place(getText(R.string.version), h.characterSet.version)
                }
                space() // a little space
                place(getText(R.string.language), h.language)
                space()
                place(getText(R.string.copyright), h.copyright)
                space()
                if (h.generator != null) {
                    place(
                        getText(R.string.software),
                        if (h.generator.name != null) h.generator.name else h.generator.value
                    )
                    place(getText(R.string.version), h.generator.version)
                    if (h.generator.generatorCorporation != null) {
                        place(getText(R.string.corporation), h.generator.generatorCorporation.value)
                        if (h.generator.generatorCorporation.address != null) place(
                            getText(R.string.address),
                            h.generator.generatorCorporation.address.displayValue
                        ) // non è male
                        place(getText(R.string.telephone), h.generator.generatorCorporation.phone)
                        place(getText(R.string.fax), h.generator.generatorCorporation.fax)
                    }
                    space()
                    if (h.generator.generatorData != null) {
                        place(getText(R.string.source), h.generator.generatorData.value)
                        place(getText(R.string.date), h.generator.generatorData.date)
                        place(getText(R.string.copyright), h.generator.generatorData.copyright)
                    }
                }
                space()
                if (h.getSubmitter(gc) != null) place(
                    getText(R.string.submitter),
                    submitterName(h.getSubmitter(gc))
                ) // todo: make it clickable?
                if (gc!!.submission != null) place(
                    getText(R.string.submission),
                    gc!!.submission.description
                ) // todo: clickable
                space()
                if (h.gedcomVersion != null) {
                    place(getText(R.string.gedcom), h.gedcomVersion.version)
                    place(getText(R.string.form), h.gedcomVersion.form)
                }
                place(getText(R.string.destination), h.destination)
                space()
                if (h.dateTime != null) {
                    place(getText(R.string.date), h.dateTime.value)
                    place(getText(R.string.time), h.dateTime.time)
                }
                space()
                for (est in U.findExtensions(h)) {    // each extension in its own line
                    place(est.name, est.text)
                }
                space()
                if (ruler != null) (findViewById<View>(R.id.info_tabella) as TableLayout).removeView(
                    ruler
                )

                // Button to update the GEDCOM header with the Family Gem parameters
                headerButton.setOnClickListener { view: View? ->
                    h.file = "$treeId.json"
                    var charSet = h.characterSet
                    if (charSet == null) {
                        charSet = CharacterSet()
                        h.characterSet = charSet
                    }
                    charSet.value = "UTF-8"
                    charSet.version = null
                    val loc = Locale(Locale.getDefault().language)
                    h.language = loc.getDisplayLanguage(Locale.ENGLISH)
                    var generator = h.generator
                    if (generator == null) {
                        generator = Generator()
                        h.generator = generator
                    }
                    generator.value = "FAMILY_GEM"
                    generator.name = getString(R.string.app_name)
                    //generator.setVersion( BuildConfig.VERSION_NAME ); // will saveJson()
                    generator.generatorCorporation = null
                    var gedcomVersion = h.gedcomVersion
                    if (gedcomVersion == null) {
                        gedcomVersion = GedcomVersion()
                        h.gedcomVersion = gedcomVersion
                    }
                    gedcomVersion.version = "5.5.1"
                    gedcomVersion.form = "LINEAGE-LINKED"
                    h.destination = null
                    U.saveJson(gc, treeId)
                    recreate()
                }
                U.placeNotes(layout, h, true)
            }
            // Extensions of Gedcom, i.e. non-standard level 0 zero tags
            for (est in U.findExtensions(gc)) {
                U.place(layout, est.name, est.text)
            }
        } else headerButton.visibility = View.GONE
    }

    fun dataIdToDate(id: String?): String {
        return if (id == null) "" else (id.substring(0, 4) + "-" + id.substring(
            4,
            6
        ) + "-" + id.substring(
            6,
            8
        ) + " "
                + id.substring(8, 10) + ":" + id.substring(10, 12) + ":" + id.substring(12))
    }

    var putText // prevents putting more than one consecutive space()
            = false

    fun place(title: CharSequence?, text: String?) {
        if (text != null) {
            val row = TableRow(this)
            val cell1 = TextView(this)
            cell1.textSize = 14f
            cell1.setTypeface(null, Typeface.BOLD)
            cell1.setPaddingRelative(0, 0, 10, 0)
            cell1.gravity = Gravity.END // Does not work on RTL layout
            cell1.text = title
            row.addView(cell1)
            val cell2 = TextView(this)
            cell2.textSize = 14f
            cell2.setPadding(0, 0, 0, 0)
            cell2.gravity = Gravity.START
            cell2.text = text
            row.addView(cell2)
            (findViewById<View>(R.id.info_tabella) as TableLayout).addView(row)
            putText = true
        }
    }

    var ruler: TableRow? = null
    fun space() {
        if (putText) {
            ruler = TableRow(applicationContext)
            val cell = View(applicationContext)
            cell.setBackgroundResource(R.color.primary)
            ruler!!.addView(cell)
            val param = cell.layoutParams as TableRow.LayoutParams
            param.weight = 1f
            param.span = 2
            param.height = 1
            param.topMargin = 5
            param.bottomMargin = 5
            cell.layoutParams = param
            (findViewById<View>(R.id.info_tabella) as TableLayout).addView(ruler)
            putText = false
        }
    }

    /**
     * back arrow in the toolbar like the hardware one
     */
    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        fun submitterName(submitter: Submitter): String {
            var name = submitter.name
            if (name == null) name =
                "[" + Global.context!!.getString(R.string.no_name) + "]" else if (name.isEmpty()) name =
                "[" + Global.context!!.getString(R.string.empty_name) + "]"
            return name
        }

        /**
         * Refresh the data displayed below the tree title in [TreesActivity] list
         */
        @JvmStatic
        fun refreshData(gedcom: Gedcom, treeItem: Settings.Tree?) {
            treeItem!!.persons = gedcom.people.size
            treeItem.generations = countGenerations(gedcom, U.getRootId(gedcom, treeItem))
            val mediaList = MediaList(gedcom, ALL_MEDIA)
            gedcom.accept(mediaList)
            treeItem.media = mediaList.list.size
            Global.settings!!.save()
        }

        var genMin = 0
        var genMax = 0
        fun countGenerations(gc: Gedcom, root: String?): Int {
            if (gc.people.isEmpty()) return 0
            genMin = 0
            genMax = 0
            goToUpEarliestGeneration(gc.getPerson(root), gc, 0)
            goDownToEarliestGeneration(gc.getPerson(root), gc, 0)
            // Removes the 'gen' extension from people to allow for later counting
            for (person in gc.people) {
                person.extensions.remove(GENERATION_KEY)
                if (person.extensions.isEmpty()) person.extensions = null
            }
            return 1 - genMin + genMax
        }

        /**
         * accepts a Person and finds the number of the earliest generation of ancestors
         */
        fun goToUpEarliestGeneration(person: Person, gc: Gedcom?, gen: Int) {
            if (gen < genMin) genMin = gen
            // adds the extension to indicate that it has passed from this Person
            person.putExtension(GENERATION_KEY, gen)
            // if he is a progenitor it counts the generations of descendants or goes back to any other marriages
            if (person.getParentFamilies(gc).isEmpty()) goDownToEarliestGeneration(person, gc, gen)
            for (family in person.getParentFamilies(gc)) {
                // intercept any siblings of the root
                for (sibling in family.getChildren(gc)) if (sibling.getExtension(GENERATION_KEY) == null) goDownToEarliestGeneration(
                    sibling,
                    gc,
                    gen
                )
                for (father in family.getHusbands(gc)) if (father.getExtension(GENERATION_KEY) == null) goToUpEarliestGeneration(
                    father,
                    gc,
                    gen - 1
                )
                for (mother in family.getWives(gc)) if (mother.getExtension(GENERATION_KEY) == null) goToUpEarliestGeneration(
                    mother,
                    gc,
                    gen - 1
                )
            }
        }

        /**
         * receives a Person and finds the number of the earliest generation of descendants
         */
        fun goDownToEarliestGeneration(person: Person, gc: Gedcom?, gen: Int) {
            if (gen > genMax) genMax = gen
            person.putExtension(GENERATION_KEY, gen)
            for (family in person.getSpouseFamilies(gc)) {
                // also identifies the spouses' family
                for (wife in family.getWives(gc)) if (wife.getExtension(GENERATION_KEY) == null) goToUpEarliestGeneration(
                    wife,
                    gc,
                    gen
                )
                for (husband in family.getHusbands(gc)) if (husband.getExtension(GENERATION_KEY) == null) goToUpEarliestGeneration(
                    husband,
                    gc,
                    gen
                )
                for (child in family.getChildren(gc)) if (child.getExtension(GENERATION_KEY) == null) goDownToEarliestGeneration(
                    child,
                    gc,
                    gen + 1
                )
            }
        }
    }
}