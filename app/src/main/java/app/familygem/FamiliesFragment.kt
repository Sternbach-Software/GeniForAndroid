package app.familygem

import android.widget.LinearLayout
import app.familygem.FamiliesFragment.FamilyWrapper
import android.os.Bundle
import app.familygem.R
import org.folg.gedcom.model.Family
import app.familygem.FamiliesFragment
import app.familygem.U
import app.familygem.Memory
import android.content.Intent
import app.familygem.detail.FamilyActivity
import android.widget.TextView
import android.view.ContextMenu.ContextMenuInfo
import android.content.DialogInterface
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.folg.gedcom.model.SpouseFamilyRef
import org.folg.gedcom.model.ParentFamilyRef
import org.folg.gedcom.model.Person
import java.lang.StringBuilder
import java.util.*

class FamiliesFragment : Fragment() {
    private var layout: LinearLayout? = null
    private var familyList: MutableList<FamilyWrapper>? = null
    private var order = 0
    private var idsAreNumeric = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bundle: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.scrollview, container, false)
        layout = view.findViewById(R.id.scrollview_layout)
        if (Global.gc != null) {
            familyList = ArrayList()
            refresh(What.RELOAD)
            if ((familyList as ArrayList<FamilyWrapper>).size > 1) setHasOptionsMenu(true)
            idsAreNumeric = verifyIdsAreNumeric()
            view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
                val newFamily = newFamily(true)
                U.save(true, newFamily)
                // If he(user?) goes straight back to the church he refreshes the list with the empty family //Se torna subito indietro in Chiesa rinfresca la lista con la famiglia vuota
                Memory.setFirst(newFamily)
                startActivity(Intent(context, FamilyActivity::class.java))
            }
        }
        return view
    }

    fun placeFamily(layout: LinearLayout?, wrapper: FamilyWrapper) {
        val familyView =
            LayoutInflater.from(layout!!.context).inflate(R.layout.piece_family, layout, false)
        layout.addView(familyView)
        val infoView = familyView.findViewById<TextView>(R.id.family_info)
        when (order) {
            1, 2 -> infoView.text = wrapper.id
            3, 4 -> if (wrapper.originalSurname != null) infoView.text =
                wrapper.originalSurname else infoView.visibility = View.GONE
            5, 6 -> infoView.text = wrapper.members.toString()
            else -> infoView.visibility = View.GONE
        }
        var parents = StringBuilder()
        for (husband in wrapper.family.getHusbands(Global.gc)) parents.append(U.properName(husband))
            .append("\n")
        for (wife in wrapper.family.getWives(Global.gc)) parents.append(U.properName(wife))
            .append("\n")
        if (parents.length > 0) parents = StringBuilder(parents.substring(0, parents.length - 1))
        (familyView.findViewById<View>(R.id.family_parents) as TextView).text = parents.toString()
        var children = StringBuilder()
        for (child in wrapper.family.getChildren(Global.gc)) children.append(U.properName(child))
            .append("\n")
        if (children.length > 0) children =
            StringBuilder(children.substring(0, children.length - 1))
        val childrenView = familyView.findViewById<TextView>(R.id.family_children)
        if (children.length == 0) {
            familyView.findViewById<View>(R.id.family_strut).visibility = View.GONE
            childrenView.visibility = View.GONE
        } else childrenView.text = children.toString()
        registerForContextMenu(familyView)
        familyView.setOnClickListener { v: View? ->
            Memory.setFirst(wrapper.family)
            layout.context.startActivity(Intent(layout.context, FamilyActivity::class.java))
        }
        familyView.tag =
            wrapper.id // only for the context menu Delete here in the Church //solo per il menu contestuale Elimina qui in Chiesa
    }

    private var selected: Family? = null
    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        selected = Global.gc.getFamily(view.tag as String)
        if (Global.settings.expert) menu.add(0, 0, 0, R.string.edit_id)
        menu.add(0, 1, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) { // Edit ID
            selected?.let { U.editId(requireContext(), it) { refresh(What.UPDATE) } }
        } else if (item.itemId == 1) { // Delete
            if (selected!!.husbandRefs.size + selected!!.wifeRefs.size + selected!!.childRefs.size > 0) {
                AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_family)
                    .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, i: Int ->
                        deleteFamily(selected)
                        refresh(What.RELOAD)
                    }.setNeutralButton(android.R.string.cancel, null).show()
            } else {
                deleteFamily(selected)
                refresh(What.RELOAD)
            }
        } else {
            return false
        }
        return true
    }

    /**
     * Check if all family ids contain numbers
     * As soon as an id contains only letters it returns false
     */
    fun verifyIdsAreNumeric(): Boolean {
        outer@ for (f in Global.gc!!.families) {
            for (c in f.id.toCharArray()) {
                if (Character.isDigit(c)) continue@outer
            }
            return false
        }
        return true
    }

    fun sortFamilies() {
        if (order > 0) {  // 0 keeps actual sorting
            Collections.sort(familyList) { f1: FamilyWrapper, f2: FamilyWrapper ->
                when (order) {
                    1 -> if (idsAreNumeric) return@sort U.extractNum(f1.id) - U.extractNum(f2.id) else return@sort f1.id.compareTo(
                        f2.id,
                        ignoreCase = true
                    )
                    2 -> if (idsAreNumeric) return@sort U.extractNum(f2.id) - U.extractNum(f1.id) else return@sort f2.id.compareTo(
                        f1.id,
                        ignoreCase = true
                    )
                    3 -> {
                        if (f1.lowerSurname == null) // null names go to the bottom
                            return@sort if (f2.lowerSurname == null) 0 else 1
                        if (f2.lowerSurname == null) return@sort -1
                        return@sort f1.lowerSurname!!.compareTo(f2.lowerSurname!!)
                    }
                    4 -> {
                        if (f1.lowerSurname == null) return@sort if (f2.lowerSurname == null) 0 else 1
                        if (f2.lowerSurname == null) return@sort -1
                        return@sort f2.lowerSurname!!.compareTo(f1.lowerSurname!!)
                    }
                    5 -> return@sort f1.members - f2.members
                    6 -> return@sort f2.members - f1.members
                }
                0
            }
        }
    }

    enum class What {
        RELOAD, UPDATE, BASIC
    }

    fun refresh(toDo: What) {
        if (toDo == What.RELOAD) { // Reload all families from Global.gc
            familyList!!.clear()
            for (family in Global.gc!!.families) familyList!!.add(FamilyWrapper(family))
            (activity as AppCompatActivity?)!!.supportActionBar!!.title =
                (familyList!!.size.toString() + " "
                        + getString(if (familyList!!.size == 1) R.string.family else R.string.families).lowercase(
                    Locale.getDefault()
                ))
            sortFamilies()
        } else if (toDo == What.UPDATE) { // Update the content of existing family wrappers
            for (wrapper in familyList!!) wrapper.id = wrapper.family.id
        }
        layout!!.removeAllViews()
        for (wrapper in familyList!!) placeFamily(layout, wrapper)
    }

    inner class FamilyWrapper(var family: Family) {
        var id: String
        var lowerSurname: String?
        var originalSurname: String?
        var members: Int

        init {
            id = family.id
            lowerSurname = familySurname(true)
            originalSurname = familySurname(false)
            members = countMembers()
        }

        /**
         * Main surname of the family
         */
        private fun familySurname(lowerCase: Boolean): String? {
            if (!family.getHusbands(Global.gc)
                    .isEmpty()
            ) return U.surname(family.getHusbands(Global.gc)[0], lowerCase)
            if (!family.getWives(Global.gc)
                    .isEmpty()
            ) return U.surname(family.getWives(Global.gc)[0], lowerCase)
            return if (!family.getChildren(Global.gc).isEmpty()) U.surname(
                family.getChildren(
                    Global.gc
                )[0], lowerCase
            ) else null
        }

        /**
         * Count how many family members
         */
        private fun countMembers(): Int {
            return family.husbandRefs.size + family.wifeRefs.size + family.childRefs.size
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val subMenu = menu.addSubMenu(R.string.order_by)
        if (Global.settings.expert) subMenu.add(0, 1, 0, R.string.id)
        subMenu.add(0, 2, 0, R.string.surname)
        subMenu.add(0, 3, 0, R.string.number_members)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id > 0 && id <= 3) {
            if (order == id * 2 - 1) order++ else if (order == id * 2) order-- else order =
                id * 2 - 1
            sortFamilies()
            refresh(What.BASIC)
            //U.salvaJson( false ); // doubt whether to put it to immediately save the reorganization of families //dubbio se metterlo per salvare subito il riordino delle famiglie
            return true
        }
        return false
    }

    companion object {
        // Delete a family, removing the refs from members
        @JvmStatic
        fun deleteFamily(family: Family?) {
            if (family == null) return
            val members: MutableSet<Person> = HashSet()
            // Remove references to the family from family members
            for (husband in family.getHusbands(Global.gc)) {
                val refs = husband.spouseFamilyRefs.iterator()
                while (refs.hasNext()) {
                    val sfr = refs.next()
                    if (sfr.ref == family.id) {
                        refs.remove()
                        members.add(husband)
                    }
                }
            }
            for (wife in family.getWives(Global.gc)) {
                val refs = wife.spouseFamilyRefs.iterator()
                while (refs.hasNext()) {
                    val sfr = refs.next()
                    if (sfr.ref == family.id) {
                        refs.remove()
                        members.add(wife)
                    }
                }
            }
            for (children in family.getChildren(Global.gc)) {
                val refi = children.parentFamilyRefs.iterator()
                while (refi.hasNext()) {
                    val pfr = refi.next()
                    if (pfr.ref == family.id) {
                        refi.remove()
                        members.add(children)
                    }
                }
            }
            // The family is deleted
            Global.gc!!.families.remove(family)
            Global.gc!!.createIndexes() // necessary to update individuals
            Memory.setInstanceAndAllSubsequentToNull(family)
            Global.familyNum =
                0 // In the unlikely event that this family was eliminated //Nel caso fortuito che sia stata eliminata proprio questa famiglia
            U.save(true, *members.toTypedArray<Any>())
        }

        @JvmStatic
        fun newFamily(add: Boolean): Family {
            val newFamily = Family()
            newFamily.id = U.newID(Global.gc!!, Family::class.java)
            if (add) Global.gc!!.addFamily(newFamily)
            return newFamily
        }
    }
}