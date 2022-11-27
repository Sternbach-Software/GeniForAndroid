package app.familygem

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Diagram.Companion.getFamilyLabels
import app.familygem.F.showMainImageForPerson
import app.familygem.IndividualEditorActivity
import app.familygem.constant.Choice
import app.familygem.constant.Format
import app.familygem.constant.Gender
import app.familygem.constant.Gender.Companion.getGender
import app.familygem.constant.intdefs.*
import com.lb.fast_scroller_and_recycler_view_fixes_library.FastScrollerEx
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Person
import org.joda.time.Days
import org.joda.time.LocalDate
import org.joda.time.Years
import java.util.*

class ListOfPeopleFragment : Fragment() {
    var people: List<Person>? = null
    private val allPeople: MutableList<PersonWrapper> =
        ArrayList() // The immutable complete list of people
    private val selectedPeople: MutableList<PersonWrapper> =
        ArrayList() // Some persons selected by the search feature
    private val adapter = PeopleAdapter()
    private var order = Order.NONE
    private var searchView: SearchView? = null
    private var idsAreNumeric = false

    private enum class Order {
        NONE, ID_ASC, ID_DESC, SURNAME_ASC, SURNAME_DESC, DATE_ASC, DATE_DESC, AGE_ASC, AGE_DESC, KIN_ASC, KIN_DESC;

        operator fun next(): Order {
            return values()[ordinal + 1]
        }

        fun prev(): Order {
            return values()[ordinal - 1]
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bundle: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.recycler_view, container, false)
        if (Global.gc != null) {
            establishPeople()
            val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
            recyclerView.setPadding(12, 12, 12, recyclerView.paddingBottom)
            recyclerView.adapter = adapter
            idsAreNumeric = verifyIdsAreNumeric()
            view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
                val intent = Intent(context, IndividualEditorActivity::class.java)
                intent.putExtra(PROFILE_ID_KEY, "TIZIO_NUOVO")
                startActivity(intent)
            }

            // Fast scroller
            val thumbDrawable =
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.scroll_thumb
                ) as StateListDrawable?
            val lineDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.empty)
            FastScrollerEx(
                recyclerView, thumbDrawable, lineDrawable, thumbDrawable, lineDrawable,
                U.dpToPx(40f), U.dpToPx(100f), 0, true, U.dpToPx(80f)
            )
        }
        return view
    }

    /**
     * Puts all of the people inside the lists
     */
    private fun establishPeople() {
        allPeople.clear()
        for (person in Global.gc!!.people) {
            allPeople.add(PersonWrapper(person))
            // On version 0.9.2 all person's extensions was removed, replaced by PersonWrapper fields
            person.extensions = null // todo remove on a future release
        }
        selectedPeople.clear()
        selectedPeople.addAll(allPeople)
        // Display search results every second
        val timer = Timer()
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                if (activity != null && searchView != null) {
                    requireActivity().runOnUiThread {
                        adapter.filter.filter(
                            searchView!!.query
                        )
                    }
                }
            }
        }
        timer.scheduleAtFixedRate(task, 500, 1000)
        object : Thread() {
            override fun run() {
                for (wrapper in allPeople) {
                    wrapper.completeFields() // This task could take long time on a big tree
                }
                timer.cancel()
                // Display final rusults
                if (activity != null && searchView != null) {
                    requireActivity().runOnUiThread {
                        adapter.filter.filter(
                            searchView!!.query
                        )
                    }
                }
            }
        }.start()
        setupToolbar()
    }

    fun setupToolbar() {
        (activity as AppCompatActivity?)!!.supportActionBar!!.title =
            (people!!.size.toString() + " "
                    + getString(if (people!!.size == 1) R.string.person else R.string.persons).lowercase(
                Locale.getDefault()
            ))
        setHasOptionsMenu(people!!.size > 1)
    }

    private inner class PeopleAdapter : RecyclerView.Adapter<IndiHolder>(), Filterable {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IndiHolder {
            val indiView = LayoutInflater.from(parent.context)
                .inflate(R.layout.piece_person, parent, false)
            registerForContextMenu(indiView)
            return IndiHolder(indiView)
        }

        override fun onBindViewHolder(indiHolder: IndiHolder, position: Int) {
            val person = selectedPeople[position].person
            val indiView = indiHolder.view
            indiView.setTag(R.id.tag_id, person.id)
            indiView.setTag(R.id.tag_position, position)
            var label: String? = null
            if (order == Order.ID_ASC || order == Order.ID_DESC) label =
                person.id else if (order == Order.SURNAME_ASC || order == Order.SURNAME_DESC) label =
                U.surname(person) else if (order == Order.KIN_ASC || order == Order.KIN_DESC) label =
                selectedPeople[position].relatives.toString()
            val infoView = indiView.findViewById<TextView>(R.id.person_info)
            if (label == null) infoView.visibility = View.GONE else {
                infoView.isAllCaps = false
                infoView.text = label
                infoView.visibility = View.VISIBLE
            }
            val nameView = indiView.findViewById<TextView>(R.id.person_name)
            val name = U.properName(person)
            nameView.text = name
            nameView.visibility =
                if (name.isEmpty() && label != null) View.GONE else View.VISIBLE
            val titleView = indiView.findViewById<TextView>(R.id.person_title)
            val title = U.title(person)
            if (title.isEmpty()) titleView.visibility = View.GONE else {
                titleView.text = title
                titleView.visibility = View.VISIBLE
            }
            val border: Int
            border = when (getGender(person)) {
                Gender.MALE -> R.drawable.casella_bordo_maschio
                Gender.FEMALE -> R.drawable.casella_bordo_femmina
                else -> R.drawable.casella_bordo_neutro
            }
            indiView.findViewById<View>(R.id.person_border).setBackgroundResource(border)
            U.details(person, indiView.findViewById(R.id.person_details))
            showMainImageForPerson(Global.gc!!, person, indiView.findViewById(R.id.person_image))
            indiView.findViewById<View>(R.id.person_mourning).visibility =
                if (U.isDead(person)) View.VISIBLE else View.GONE
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(charSequence: CharSequence): FilterResults {
                    // Split query by spaces and search all the words
                    val query =
                        charSequence.toString().trim { it <= ' ' }.lowercase(Locale.getDefault())
                            .split("\\s+").toTypedArray()
                    selectedPeople.clear()
                    if (query.size == 0) {
                        selectedPeople.addAll(allPeople)
                    } else {
                        outer@ for (wrapper in allPeople) {
                            if (wrapper.text != null) {
                                for (word in query) {
                                    if (!wrapper.text!!.contains(word)) {
                                        continue@outer
                                    }
                                }
                                selectedPeople.add(wrapper)
                            }
                        }
                    }
                    if (order != Order.NONE) sortPeople()
                    val filterResults = FilterResults()
                    filterResults.values = selectedPeople
                    return filterResults
                }

                override fun publishResults(cs: CharSequence, fr: FilterResults) {
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount(): Int {
            return selectedPeople.size
        }
    }

    private inner class IndiHolder internal constructor(var view: View) :
        RecyclerView.ViewHolder(view), View.OnClickListener {
        init {
            view.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            // Choose the relative and return the values to Diagram, ProfileActivity, FamilyActivity or SharingActivity
            val relative = Global.gc!!.getPerson(view.getTag(R.id.tag_id) as String)
            val intent = requireActivity().intent
            if (intent.getBooleanExtra(Choice.PERSON, false)) {
                intent.putExtra(RELATIVE_ID_KEY, relative.id)
                // Look for any existing family that can host the pivot
                val placement = intent.getStringExtra(LOCATION_KEY)
                if (placement != null && placement == EXISTING_FAMILY_VALUE) {
                    var familyId: String? = null
                    when (intent.getIntExtra(RELATIONSHIP_ID_KEY, 0)) {
                        1 -> if (relative.spouseFamilyRefs.size > 0) familyId =
                            relative.spouseFamilyRefs[0].ref
                        2 -> if (relative.parentFamilyRefs.size > 0) familyId =
                            relative.parentFamilyRefs[0].ref
                        3 -> for (fam in relative.getSpouseFamilies(Global.gc)) {
                            if (fam.husbandRefs.isEmpty() || fam.wifeRefs.isEmpty()) {
                                familyId = fam.id
                                break
                            }
                        }
                        4 -> for (fam in relative.getParentFamilies(Global.gc)) {
                            if (fam.husbandRefs.isEmpty() || fam.wifeRefs.isEmpty()) {
                                familyId = fam.id
                                break
                            }
                        }
                    }
                    if (familyId != null) // addRelative() will use the found family
                        intent.putExtra(
                            FAMILY_ID_KEY,
                            familyId
                        ) else  // addRelative() will create a new family
                        intent.removeExtra(LOCATION_KEY)
                }
                requireActivity().setResult(AppCompatActivity.RESULT_OK, intent)
                requireActivity().finish()
            } else { // Normal link to the profile
                // todo Click on the photo opens the media tab..
                // intent.putExtra( CARD_KEY, 0 );
                Memory.setFirst(relative)
                startActivity(Intent(context, ProfileActivity::class.java))
            }
        }
    }

    /**
     * Update all the contents onBackPressed()
     */
    fun restart() {
        // Recreate the lists for some person added or removed
        establishPeople()
        // Update content of existing views
        adapter.notifyDataSetChanged()
    }

    /**
     * Reset the extra if leaving this fragment without choosing a person
     */
    override fun onPause() {
        super.onPause()
        requireActivity().intent.removeExtra(Choice.PERSON)
    }

    /**
     * Check if all people's ids contain numbers
     * As soon as an id contains only letters it returns false
     */
    private fun verifyIdsAreNumeric(): Boolean {
        out@ for (person in Global.gc!!.people) {
            for (character in person.id.toCharArray()) {
                if (Character.isDigit(character)) continue@out
            }
            return false
        }
        return true
    }

    private fun sortPeople() {
        selectedPeople.sortWith { wrapper1: PersonWrapper, wrapper2: PersonWrapper ->
            val p1 = wrapper1.person
            val p2 = wrapper2.person
            when (order) {
                Order.ID_ASC -> if (idsAreNumeric) U.extractNum(p1.id) - U.extractNum(p2.id) else p1.id.compareTo(
                    p2.id,
                    ignoreCase = true
                )
                Order.ID_DESC -> if (idsAreNumeric) U.extractNum(p2.id) - U.extractNum(
                    p1.id
                ) else p2.id.compareTo(p1.id, ignoreCase = true)
                Order.SURNAME_ASC -> {
                    when {
                        wrapper1.surname == null // Null surnames go to the end
                        -> if (wrapper2.surname == null) 0 else 1
                        wrapper2.surname == null -> -1
                        else -> wrapper1.surname!!.compareTo(wrapper2.surname!!)
                    }
                }
                Order.SURNAME_DESC -> {
                    when {
                        wrapper1.surname == null -> if (wrapper2.surname == null) 0 else 1
                        wrapper2.surname == null -> -1
                        else -> wrapper2.surname!!.compareTo(wrapper1.surname!!)
                    }
                }
                Order.DATE_ASC -> wrapper1.date - wrapper2.date
                Order.DATE_DESC -> {
                    when {
                        wrapper2.date == Int.MAX_VALUE -> -1 // Those without year go to the bottom
                        wrapper1.date == Int.MAX_VALUE -> 1
                        else -> wrapper2.date - wrapper1.date
                    }
                }
                Order.AGE_ASC -> wrapper1.age - wrapper2.age
                Order.AGE_DESC -> {
                    when {
                        wrapper2.age == Int.MAX_VALUE -> -1 // Those without age go to the bottom
                        wrapper1.age == Int.MAX_VALUE -> 1
                        else -> wrapper2.age - wrapper1.age
                    }
                }
                Order.KIN_ASC -> wrapper1.relatives - wrapper2.relatives
                Order.KIN_DESC -> wrapper2.relatives - wrapper1.relatives
                Order.NONE -> 0
            }
        }
    }

    /**
     * Returns a string with surname and firstname attached:
     * 'SalvadorMichele ' or 'ValleFrancesco Maria ' or ' Donatella '
     */
    private fun getSurnameFirstname(person: Person): String? {
        val names = person.names
        if (names.isNotEmpty()) {
            val name = names[0]
            val value = name.value
            if (value != null || name.given != null || name.surname != null) {
                var given = ""
                var surname = " " // There must be a space to sort names without surname
                if (value != null) {
                    if (value.replace('/', ' ').trim { it <= ' ' }.isEmpty()) // Empty value
                        return null
                    if (value.indexOf('/') > 0) given =
                        value.substring(0, value.indexOf('/')) // Take the given name before '/'
                    if (value.lastIndexOf('/') - value.indexOf('/') > 1) // If there is a surname between two '/'
                        surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf("/"))
                    // Only the given name coming from the value could have a prefix,
                    // from getGiven() no, because it is already only the given name.
                    val prefix = name.prefix
                    if (prefix != null && given.startsWith(prefix)) given =
                        given.substring(prefix.length).trim { it <= ' ' }
                } else {
                    if (name.given != null) given = name.given
                    if (name.surname != null) surname = name.surname
                }
                val surPrefix = name.surnamePrefix
                if (surPrefix != null && surname.startsWith(surPrefix)) surname =
                    surname.substring(surPrefix.length).trim { it <= ' ' }
                return surname + given.lowercase(Locale.getDefault())
            }
        }
        return null
    }

    var datator = GedcomDateConverter("") // Here outside to initialize only once

    /**
     * Class to wrap a person of the list and all their relevant fields
     */
    private inner class PersonWrapper internal constructor(val person: Person) {
        var text // Single string with all names and events for search
                : String? = null
        var surname // Surname and name of the person
                : String? = null
        var date // Date in the format YYYYMMDD
                = 0
        var age // Age in days
                = 0
        var relatives // Number of near relatives
                = 0

        fun completeFields() {
            // Write one string concatenating all names and personal events
            text = ""
            for (name in person.names) {
                text += U.firstAndLastName(name, " ") + " "
            }
            for (event in person.eventsFacts) {
                if (!("SEX" == event.tag || "Y" == event.value)) // Sex and 'Yes' excluded
                    text += ProfileFactsFragment.writeEventText(event) + " "
            }
            text = text!!.lowercase(Locale.getDefault())

            // Surname and first name concatenated
            surname = getSurnameFirstname(person)

            // Find the first date of a person's life or MAX_VALUE
            date = Int.MAX_VALUE
            for (event in person.eventsFacts) {
                if (event.date != null) {
                    datator.analyze(event.date)
                    date = datator.dateNumber
                }
            }

            // Calculate the age of a person in days or MAX_VALUE
            age = Int.MAX_VALUE
            var start: GedcomDateConverter? = null
            var end: GedcomDateConverter? = null
            for (event in person.eventsFacts) {
                if (event.tag != null && event.tag == "BIRT" && event.date != null) {
                    start = GedcomDateConverter(event.date)
                    break
                }
            }
            for (event in person.eventsFacts) {
                if (event.tag != null && event.tag == "DEAT" && event.date != null) {
                    end = GedcomDateConverter(event.date)
                    break
                }
            }
            if (start != null && start.isSingleKind && !start.data1.isFormat(Format.D_M)) {
                val startDate = LocalDate(start.data1.date)
                // If the person is still alive the end is now
                val now = LocalDate.now()
                if (end == null && startDate.isBefore(now)
                    && Years.yearsBetween(startDate, now).years <= 120 && !U.isDead(person)
                ) {
                    end = GedcomDateConverter(now.toDate())
                }
                if (end != null && end.isSingleKind && !end.data1.isFormat(Format.D_M)) {
                    val endDate = LocalDate(end.data1.date)
                    if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
                        age = Days.daysBetween(startDate, endDate).days
                    }
                }
            }

            // Relatives
            relatives = countRelatives(person)
        }
    }

    // option menu in the toolbar
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val subMenu = menu.addSubMenu(R.string.order_by)
        if (Global.settings!!.expert) subMenu.add(0, 1, 0, R.string.id)
        subMenu.add(0, 2, 0, R.string.surname)
        subMenu.add(0, 3, 0, R.string.date)
        subMenu.add(0, 4, 0, R.string.age)
        subMenu.add(0, 5, 0, R.string.number_relatives)

        //Search in the ListOfPeopleActivity
        inflater.inflate(
            R.menu.search,
            menu
        ) // this is already enough to bring up the lens with the search field
        searchView = menu.findItem(R.id.search_item).actionView as SearchView?
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                adapter.filter.filter(query)
                return true
            }

            override fun onQueryTextSubmit(q: String): Boolean {
                searchView!!.clearFocus()
                return false
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id > 0 && id <= 5) {
            // Clicking twice the same menu item switches sorting ASC and DESC
            order =
                if (order == Order.values()[id * 2 - 1]) order.next() else if (order == Order.values()[id * 2]) order.prev() else Order.values()[id * 2 - 1]
            sortPeople()
            adapter.notifyDataSetChanged()
            //U.saveJson( false ); // doubt whether to put it to immediately save the tidying up of people...
            return true
        }
        return false
    }

    // Context menu
    private var position = 0
    private var indiId: String? = null
    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        indiId = view.getTag(R.id.tag_id) as String
        position = view.getTag(R.id.tag_position) as Int
        position = view.getTag(R.id.tag_position) as Int
        menu.add(0, 0, 0, R.string.diagram)
        val familyLabels = getFamilyLabels(requireContext(), Global.gc!!.getPerson(indiId), null)
        if (familyLabels[0] != null) menu.add(0, 1, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(0, 2, 0, familyLabels[1])
        menu.add(0, 3, 0, R.string.modify)
        if (Global.settings!!.expert) menu.add(0, 4, 0, R.string.edit_id)
        menu.add(0, 5, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == 0) {    // Open Diagram
            U.askWhichParentsToShow(requireContext(), Global.gc!!.getPerson(indiId), 1)
        } else if (id == 1) { // Family as child
            U.askWhichParentsToShow(requireContext(), Global.gc!!.getPerson(indiId), 2)
        } else if (id == 2) { // Family as spouse
            U.askWhichSpouseToShow(requireContext(), Global.gc!!.getPerson(indiId), null)
        } else if (id == 3) { // Edit
            val intent = Intent(context, IndividualEditorActivity::class.java)
            intent.putExtra(PROFILE_ID_KEY, indiId)
            startActivity(intent)
        } else if (id == 4) { // Edit ID
            U.editId(requireContext(), Global.gc!!.getPerson(indiId)) { adapter.notifyDataSetChanged() }
        } else if (id == 5) { // Delete
            AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_person)
                .setPositiveButton(R.string.delete) { dialog: DialogInterface?, i: Int ->
                    val families = deletePerson(
                        context, indiId
                    )
                    selectedPeople.removeAt(position)
                    allPeople.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, selectedPeople.size - position)
                    setupToolbar()
                    U.checkFamilyItem(context, null, false, *families)
                }.setNeutralButton(R.string.cancel, null).show()
        } else {
            return false
        }
        return true
    }

    companion object {
        /** Count how many near relatives one person has: parents, siblings, step-siblings, spouses and children.
         * @param person The person to start from
         * @return Number of near relatives (person excluded)
         */
        @JvmStatic
        fun countRelatives(person: Person?): Int {
            var count = 0
            if (person != null) {
                // Families of origin: parents and siblings
                val families = person.getParentFamilies(Global.gc)
                for (family in families) {
                    count += family.husbandRefs.size
                    count += family.wifeRefs.size
                    for (sibling in family.getChildren(Global.gc))  // only children of the same two parents, not half-siblings
                        if (sibling != person) count++
                }
                // Stepbrothers and stepsisters
                for (family in person.getParentFamilies(Global.gc)) {
                    for (father in family.getHusbands(Global.gc)) {
                        val fatherFamily = father.getSpouseFamilies(Global.gc)
                        fatherFamily.removeAll(families)
                        for (fam in fatherFamily) count += fam.childRefs.size
                    }
                    for (mother in family.getWives(Global.gc)) {
                        val motherFamily = mother.getSpouseFamilies(Global.gc)
                        motherFamily.removeAll(families)
                        for (fam in motherFamily) count += fam.childRefs.size
                    }
                }
                // Spouses and children
                for (family in person.getSpouseFamilies(Global.gc)) {
                    count += family.wifeRefs.size
                    count += family.husbandRefs.size
                    count-- // Minus their self
                    count += family.childRefs.size
                }
            }
            return count
        }

        /**
         * Delete all refs in that person's families
         * @return the list of affected families
         */
        fun disconnect(personToDisconnect: Person): Array<Family> { //TODO rename to unlinkPerson
            val families: MutableSet<Family> = HashSet()
            for (f in personToDisconnect.getParentFamilies(Global.gc)) {    // unlink its refs in families
                f.childRefs.removeAt(f.getChildren(Global.gc).indexOf(personToDisconnect))
                families.add(f)
            }
            for (f in personToDisconnect.getSpouseFamilies(Global.gc)) {
                if (f.getHusbands(Global.gc).contains(personToDisconnect)) {
                    f.husbandRefs.removeAt(f.getHusbands(Global.gc).indexOf(personToDisconnect))
                    families.add(f)
                }
                if (f.getWives(Global.gc).contains(personToDisconnect)) {
                    f.wifeRefs.removeAt(f.getWives(Global.gc).indexOf(personToDisconnect))
                    families.add(f)
                }
            }
            personToDisconnect.parentFamilyRefs =
                null // in the indi it unlinks the refs of the families it belongs to
            personToDisconnect.spouseFamilyRefs = null
            return families.toTypedArray()
        }

        /**
         * Delete a person from the tree, possibly find the new root.
         * @param context
         * @param personId Id of the person to be deleted
         * @return Array of modified families
         */
        @JvmStatic
        fun deletePerson(context: Context?, personId: String?): Array<Family> {
            val person = Global.gc!!.getPerson(personId)
            val families = disconnect(person)
            Memory.setInstanceAndAllSubsequentToNull(person)
            Global.gc!!.people.remove(person)
            Global.gc!!.createIndexes() // Necessary
            val newRootId = U.findRoot(Global.gc!!) // Todo should read: findNearestRelative
            if (Global.settings!!.currentTree!!.root != null && Global.settings!!.currentTree!!.root == personId) {
                Global.settings!!.currentTree!!.root = newRootId
            }
            Global.settings!!.save()
            if (Global.indi != null && Global.indi == personId) Global.indi = newRootId!!
            Toast.makeText(context, R.string.person_deleted, Toast.LENGTH_SHORT).show()
            U.save(true, *families as Array<Any>)
            return families
        }
    }
}