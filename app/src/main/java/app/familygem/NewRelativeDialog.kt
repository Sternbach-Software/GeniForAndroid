package app.familygem

import android.app.Dialog
import android.content.Context
import org.folg.gedcom.model.Family
import android.widget.Spinner
import app.familygem.NewRelativeDialog.FamilyItem
import androidx.annotation.Keep
import android.os.Bundle
import app.familygem.R
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.CompoundButton
import android.content.DialogInterface
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import app.familygem.IndividualEditorActivity
import app.familygem.U
import app.familygem.constant.intdefs.*
import org.folg.gedcom.model.Person
import java.util.ArrayList

/**
 * DialogFragment which creates the dialog to connect a relative in expert mode
 */
class NewRelativeDialog : DialogFragment {
    private var pivot: Person? = null
    private var childFamPref // Family as a child to possibly show first in the spinner
            : Family? = null
    private var spouseFamPref // Family as a spouse to possibly show first in the spinner
            : Family? = null
    private var newRelative = false
    private var fragment: Fragment? = null
    private var dialog: AlertDialog? = null
    private lateinit var spinner: Spinner
    private val items: MutableList<FamilyItem> = ArrayList()
    private var relationship = 0

    constructor(
        pivot: Person?,
        favoriteChild: Family?,
        favoriteSpouse: Family?,
        newRelative: Boolean,
        fragment: Fragment?
    ) {
        this.pivot = pivot
        childFamPref = favoriteChild
        spouseFamPref = favoriteSpouse
        this.newRelative = newRelative
        this.fragment = fragment
    }

    // Zero-argument constructor: nececessary to re-instantiate this fragment (e.g. rotating the device screen)
    @Keep // Request to don't remove when minify
    constructor() {
    }

    override fun onCreateDialog(bundle: Bundle?): Dialog {
        // Recreate dialog
        if (bundle != null) {
            pivot = Global.gc!!.getPerson(bundle.getString(PIVOT_ID_KEY))
            childFamPref = Global.gc!!.getFamily(bundle.getString(CHILD_FAM_ID_KEY))
            spouseFamPref = Global.gc!!.getFamily(bundle.getString(SPOUSE_FAM_ID_KEY))
            newRelative = bundle.getBoolean(NEW_RELATIVE_KEY)
            fragment = requireActivity().supportFragmentManager.getFragment(bundle, FRAGMENT_KEY)
        }
        val builder = AlertDialog.Builder(
            requireContext()
        )
        //builder.setTitle( newRelative ? R.string.new_relative : R.string.link_person );
        val vista = requireActivity().layoutInflater.inflate(R.layout.nuovo_parente, null)
        // Spinner to choose the family
        spinner = vista.findViewById(R.id.nuovoparente_famiglie)
        val adapter = ArrayAdapter<FamilyItem>(requireContext(), android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.setAdapter(adapter)
        (spinner.getParent() as View).visibility = View.GONE //initially the spinner is hidden
        val radioButton1 = vista.findViewById<RadioButton>(R.id.nuovoparente_1)
        radioButton1.setOnCheckedChangeListener { r: CompoundButton?, selected: Boolean ->
            if (selected) populateSpinner(
                1
            )
        }
        val radioButton2 = vista.findViewById<RadioButton>(R.id.nuovoparente_2)
        radioButton2.setOnCheckedChangeListener { r: CompoundButton?, selected: Boolean ->
            if (selected) populateSpinner(
                2
            )
        }
        val radioButton3 = vista.findViewById<RadioButton>(R.id.nuovoparente_3)
        radioButton3.setOnCheckedChangeListener { r: CompoundButton?, selected: Boolean ->
            if (selected) populateSpinner(
                3
            )
        }
        val radioButton4 = vista.findViewById<RadioButton>(R.id.nuovoparente_4)
        radioButton4.setOnCheckedChangeListener { r: CompoundButton?, selected: Boolean ->
            if (selected) populateSpinner(
                4
            )
        }
        builder.setView(vista)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, id: Int ->
                // Set some values that will be passed to IndividualEditorActivity or to ListOfPeopleFragment and will arrive at addRelative()
                val intent = Intent()
                intent.putExtra(PROFILE_ID_KEY, pivot!!.id)
                intent.putExtra(RELATIONSHIP_ID_KEY, relationship)
                val familyItem = spinner.getSelectedItem() as FamilyItem
                if (familyItem.family != null) intent.putExtra(
                    FAMILY_ID_KEY,
                    familyItem.family!!.id
                ) else if (familyItem.parent != null) // Using 'location' to convey the id of the parent (the third actor in the scene)
                    intent.putExtra(
                        LOCATION_KEY,
                        NEW_FAMILY_OF_VALUE + familyItem.parent!!.id
                    ) else if (familyItem.existing) // conveys to the ListOfPeopleFragment the intention to join an existing family
                    intent.putExtra(LOCATION_KEY, "FAMIGLIA_ESISTENTE")
                if (newRelative) { // Collega persona nuova
                    intent.setClass(requireContext(), IndividualEditorActivity::class.java)
                    startActivity(intent)
                } else { // Connect existing person
                    intent.putExtra(PEOPLE_LIST_CHOOSE_RELATIVE_KEY, true)
                    intent.setClass(requireContext(), Principal::class.java)
                    if (fragment != null) fragment!!.startActivityForResult(
                        intent,
                        1401
                    ) else requireActivity().startActivityForResult(intent, 1401)
                }
            }.setNeutralButton(R.string.cancel, null)
        dialog = builder.create()
        return dialog!!
    }

    override fun onStart() {
        super.onStart()
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false // Initially disabled
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        bundle.putString(PIVOT_ID_KEY, pivot!!.id)
        if (childFamPref != null) bundle.putString(CHILD_FAM_ID_KEY, childFamPref!!.id)
        if (spouseFamPref != null) bundle.putString(SPOUSE_FAM_ID_KEY, spouseFamPref!!.id)
        bundle.putBoolean(NEW_RELATIVE_KEY, newRelative)
        //Save the fragment's instance
        if (fragment != null) requireActivity().supportFragmentManager.putFragment(
            bundle,
            FRAGMENT_KEY,
            fragment!!
        )
    }

    /**
     * Tells if there is empty space in a family to add one of the two parents
     */
    fun containsSpouseShortage(fam: Family): Boolean {
        return fam.husbandRefs.size + fam.wifeRefs.size < 2
    }

    private fun populateSpinner(relationship: Int) {
        this.relationship = relationship
        items.clear()
        var select =
            -1 // Index of the item to select in the spinner. If -1 remains select the first entry of the spinner
        when (relationship) {
            1 -> {
                for (fam in pivot!!.getParentFamilies(Global.gc)) {
                    items.add(FamilyItem(context, fam))
                    if ((fam == childFamPref || select < 0) // or the first one available
                        && containsSpouseShortage(fam)
                    ) // if they have empty parent space
                        select = items.size - 1
                }
                items.add(FamilyItem(context, false))
                if (select < 0) select = items.size - 1 // Select "New family"
            }
            2 -> {
                for (fam in pivot!!.getParentFamilies(Global.gc)) {
                    items.add(FamilyItem(context, fam))
                    for (padre in fam.getHusbands(Global.gc)) {
                        for (fam2 in padre.getSpouseFamilies(Global.gc)) if (fam2 != fam) items.add(
                            FamilyItem(
                                context, fam2
                            )
                        )
                        items.add(FamilyItem(context, padre))
                    }
                    for (madre in fam.getWives(Global.gc)) {
                        for (fam2 in madre.getSpouseFamilies(Global.gc)) if (fam2 != fam) items.add(
                            FamilyItem(
                                context, fam2
                            )
                        )
                        items.add(FamilyItem(context, madre))
                    }
                }
                items.add(FamilyItem(context, false))
                // Select the preferred family as a child
                select = 0
                for (voce in items) if (voce.family != null && voce.family == childFamPref) {
                    select = items.indexOf(voce)
                    break
                }
            }
            3, 4 -> {
                for (fam in pivot!!.getSpouseFamilies(Global.gc)) {
                    items.add(FamilyItem(context, fam))
                    if (items.size > 1 && fam == spouseFamPref // Select your favorite family as a spouse (except the first one)
                        || containsSpouseShortage(fam) && select < 0
                    ) // Select the first family where there are no spouses
                        select = items.size - 1
                }
                items.add(FamilyItem(context, pivot))
                if (select < 0) select = items.size - 1 // Select "New family of..."
                // For a child, select the preferred family (if any), otherwise the first
                if (relationship == 4) {
                    select = 0
                    for (voce in items) if (voce.family != null && voce.family == spouseFamPref) {
                        select = items.indexOf(voce)
                        break
                    }
                }
            }
        }
        if (!newRelative) {
            items.add(FamilyItem(context, true))
        }
        val adapter: ArrayAdapter<FamilyItem> = spinner!!.adapter as ArrayAdapter<FamilyItem>
        adapter.clear()
        adapter.addAll(items)
        (spinner!!.parent as View).visibility = View.VISIBLE
        spinner!!.setSelection(select)
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
    }

    /**
     * Class for family list entries in dialogs "Which family do you want to add...?"
     */
    internal class FamilyItem {
        var context: Context?
        var family: Family? = null
        var parent: Person? = null
        var existing // pivot try to fit into the already existing family
                = false

        /**
         * Existing family
         */
        constructor(context: Context?, family: Family?) {
            this.context = context
            this.family = family
        }

        /**
         * New family of a parent
         */
        constructor(context: Context?, parent: Person?) {
            this.context = context
            this.parent = parent
        }

        /**
         * Empty new family (false) OR recipient-acquired family (true)
         */
        constructor(context: Context?, existing: Boolean) {
            this.context = context
            this.existing = existing
        }

        override fun toString(): String {
            return if (family != null) U.familyText(
                context,
                Global.gc,
                family!!,
                true
            ) else if (parent != null) context!!.getString(
                R.string.new_family_of,
                U.properName(parent)
            ) else if (existing) context!!.getString(R.string.existing_family) else context!!.getString(
                R.string.new_family
            )
        }
    }
}