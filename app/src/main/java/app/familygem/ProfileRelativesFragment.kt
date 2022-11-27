package app.familygem

import app.familygem.detail.FamilyActivity.Companion.getRole
import app.familygem.detail.FamilyActivity.Companion.writeLineage
import app.familygem.Memory.Companion.replaceFirst
import app.familygem.Diagram.Companion.getFamilyLabels
import app.familygem.detail.FamilyActivity.Companion.findParentFamilyRef
import app.familygem.detail.FamilyActivity.Companion.chooseLineage
import app.familygem.detail.FamilyActivity.Companion.disconnect
import app.familygem.ListOfPeopleFragment.Companion.deletePerson
import android.os.Bundle
import app.familygem.R
import org.folg.gedcom.model.Family
import android.widget.LinearLayout
import app.familygem.U
import app.familygem.detail.FamilyActivity
import app.familygem.Memory
import android.content.Intent
import app.familygem.ProfileActivity
import android.view.ContextMenu.ContextMenuInfo
import org.folg.gedcom.model.SpouseFamilyRef
import app.familygem.IndividualEditorActivity
import android.content.DialogInterface
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import app.familygem.ListOfPeopleFragment
import app.familygem.constant.Relation
import app.familygem.constant.intdefs.CARD_KEY
import app.familygem.constant.intdefs.PROFILE_ID_KEY
import org.folg.gedcom.model.Person
import java.util.*

class ProfileRelativesFragment : Fragment() {
    private var familyView: View? = null
    var person1: Person? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        familyView = inflater.inflate(R.layout.individuo_scheda, container, false)
        if (Global.gc != null) {
            person1 = Global.gc!!.getPerson(Global.indi)
            if (person1 != null) {
                /* TODO Show / be able to set the pedigree in the geniotrial families, in particular 'adopted' // Mostrare/poter settare nelle famiglie geniotriali il pedigree, in particolare 'adopted'
				LinearLayout container = vistaFamiglia.findViewById( R.id.contenuto_scheda );
				for( ParentFamilyRef pfr : person1.getParentFamilyRefs() ) {
					U.place( container, "Ref", pfr.getRef() );
					U.place( container, "Primary", pfr.getPrimary() ); // Custom tag _PRIM _PRIMARY
					U.place( container, "Relationship Type", pfr.getRelationshipType() ); // Tag PEDI (pedigree)
					for( Extension otherTag : U.findExtensions( pfr ) )
						U.place( container, otherTag.name, otherTag.text );
				} */
                // Families of origin: parents and siblings
                val listOfFamilies = person1!!.getParentFamilies(Global.gc)
                for (family in listOfFamilies) {
                    for (father in family.getHusbands(Global.gc)) createCard(
                        father,
                        Relation.PARENT,
                        family
                    )
                    for (mother in family.getWives(Global.gc)) createCard(
                        mother,
                        Relation.PARENT,
                        family
                    )
                    for (sibling in family.getChildren(Global.gc))  // only children of the same two parents, not half-siblings
                        if (sibling != person1) createCard(sibling, Relation.SIBLING, family)
                }
                // Step (half?) brothers and sisters
                for (family in person1!!.getParentFamilies(Global.gc)) {
                    for (husband in family.getHusbands(Global.gc)) {
                        val fatherFamilies = husband.getSpouseFamilies(Global.gc)
                        fatherFamilies.removeAll(listOfFamilies)
                        for (fam in fatherFamilies) for (stepSibling in fam.getChildren(Global.gc)) createCard(
                            stepSibling,
                            Relation.HALF_SIBLING,
                            fam
                        )
                    }
                    for (wife in family.getWives(Global.gc)) {
                        val wifeFamilies = wife.getSpouseFamilies(Global.gc)
                        wifeFamilies.removeAll(listOfFamilies)
                        for (fam in wifeFamilies) for (stepSibling in fam.getChildren(Global.gc)) createCard(
                            stepSibling,
                            Relation.HALF_SIBLING,
                            fam
                        )
                    }
                }
                // Spouses and children
                for (family in person1!!.getSpouseFamilies(Global.gc)) {
                    for (husband in family.getHusbands(Global.gc)) if (husband != person1) createCard(
                        husband,
                        Relation.PARTNER,
                        family
                    )
                    for (wife in family.getWives(Global.gc)) if (wife != person1) createCard(
                        wife,
                        Relation.PARTNER,
                        family
                    )
                    for (child in family.getChildren(Global.gc)) {
                        createCard(child, Relation.CHILD, family)
                    }
                }
            }
        }
        return familyView
    }

    fun createCard(person: Person, relation: Relation?, family: Family?) {
        val container = familyView!!.findViewById<LinearLayout>(R.id.contenuto_scheda)
        val personView = U.placeIndividual(
            container, person,
            getRole(person, family, relation!!, false) + writeLineage(
                person!!, family
            )
        )
        personView.setOnClickListener { v: View? ->
            requireActivity().finish() // Removes the current activity from the stack
            replaceFirst(person)
            val intent = Intent(context, ProfileActivity::class.java)
            intent.putExtra(CARD_KEY, 2) // apre la scheda famiglia
            startActivity(intent)
        }
        registerForContextMenu(personView)

        // The main purpose of this tag is to be able to disconnect the individual from the family
        // but it is also used below to move multiple marriages:
        personView.setTag(R.id.tag_famiglia, family)
    }

    private fun moveFamilyReference(direction: Int) {
        Collections.swap(person1!!.spouseFamilyRefs, familyPos, familyPos + direction)
        U.save(true, person1)
        refresh()
    }

    // context Menu
    private var indiId: String? = null
    private var person: Person? = null
    private var family: Family? = null
    private var familyPos // position of the marital family for those who have more than one
            = 0

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        indiId = view.tag as String
        person = Global.gc!!.getPerson(indiId)
        family = view.getTag(R.id.tag_famiglia) as Family
        familyPos = -1
        if (person1!!.spouseFamilyRefs.size > 1 && !family!!.getChildren(Global.gc)
                .contains(person)
        ) { // only spouses, not children
            val refs = person1!!.spouseFamilyRefs
            for (sfr in refs) if (sfr.ref == family!!.id) familyPos = refs.indexOf(sfr)
        }
        // Better to use numbers that do not conflict with the context menus of the other individual tabs
        menu.add(0, 300, 0, R.string.diagram)
        val familyLabels = getFamilyLabels(requireContext(), person, family)
        if (familyLabels[0] != null) menu.add(0, 301, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(0, 302, 0, familyLabels[1])
        if (familyPos > 0) menu.add(0, 303, 0, R.string.move_before)
        if (familyPos >= 0 && familyPos < person1!!.spouseFamilyRefs.size - 1) menu.add(
            0,
            304,
            0,
            R.string.move_after
        )
        menu.add(0, 305, 0, R.string.modify)
        if (findParentFamilyRef(person!!, family) != null) menu.add(0, 306, 0, R.string.lineage)
        menu.add(0, 307, 0, R.string.unlink)
        if (person != person1) // Here he cannot eliminate himself
            menu.add(0, 308, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == 300) { // Diagram
            U.askWhichParentsToShow(requireContext(), person, 1)
        } else if (id == 301) { // Family as a son
            U.askWhichParentsToShow(requireContext(), person, 2)
        } else if (id == 302) { // Family as a spouse
            U.askWhichSpouseToShow(requireContext(), person!!, family)
        } else if (id == 303) { // Move up
            moveFamilyReference(-1)
        } else if (id == 304) { // Move down
            moveFamilyReference(1)
        } else if (id == 305) { // Modify
            val intent = Intent(context, IndividualEditorActivity::class.java)
            intent.putExtra(PROFILE_ID_KEY, indiId)
            startActivity(intent)
        } else if (id == 306) { // Lineage
            chooseLineage(context, person!!, family)
        } else if (id == 307) { // Disconnect from this family
            disconnect(indiId!!, family!!)
            refresh()
            U.checkFamilyItem(context, { refresh() }, false, family!!)
            U.save(true, family, person)
        } else if (id == 308) { // Delete
            AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_person)
                .setPositiveButton(R.string.delete) { dialog: DialogInterface?, i: Int ->
                    deletePerson(
                        context, indiId
                    )
                    refresh()
                    U.checkFamilyItem(context, { refresh() }, false, family!!)
                }.setNeutralButton(R.string.cancel, null).show()
        } else {
            return false
        }
        return true
    }

    /**
     * Refresh the contents of the Family Fragment
     */
    fun refresh() {
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.beginTransaction().detach(this).commit()
        fragmentManager.beginTransaction().attach(this).commit()
        requireActivity().invalidateOptionsMenu()
        // todo update the change date in the Facts tab
    }
}