package app.familygem.detail

import android.content.Context
import app.familygem.constant.Status.Companion.getStatus
import app.familygem.constant.Gender.Companion.isMale
import app.familygem.constant.Gender.Companion.isFemale
import app.familygem.detail.FamilyActivity
import android.content.Intent
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import app.familygem.*
import app.familygem.constant.Relation
import app.familygem.constant.Status
import org.folg.gedcom.model.*
import java.util.*

class FamilyActivity : DetailActivity() {
    lateinit var f: Family
    override fun format() {
        setTitle(R.string.family)
        f = cast(Family::class.java) as Family
        placeSlug("FAM", f.id)
        for (husbandRef in f.husbandRefs) addMember(husbandRef, Relation.PARTNER)
        for (wifeRef in f.wifeRefs) addMember(wifeRef, Relation.PARTNER)
        for (childRef in f.childRefs) addMember(childRef, Relation.CHILD)
        for (ef in f.eventsFacts) {
            place(writeEventTitle(f, ef), ef)
        }
        placeExtensions(f)
        U.placeNotes(box, f, true)
        U.placeMedia(box, f, true)
        U.placeSourceCitations(box, f)
        U.placeChangeDate(box, f.change)
    }

    /**
     * Add a member to the family
     */
    private fun addMember(sr: SpouseRef, relation: Relation) {
        val p = sr.getPerson(Global.gc) ?: return
        val personView =
            U.placeIndividual(box, p, getRole(p, f, relation, true) + writeLineage(p, f))
        personView.setTag(R.id.tag_object, p) // for the context menu in DetailActivity
        /*  Ref in the individual towards the family //Ref nell'individuo verso la famiglia
			If the same person is present several times with the same role (parent / child) in the same family // Se la stessa persona è presente più volte con lo stesso ruolo (parent/child) nella stessa famiglia
			the 2 following loops identify in the person the * first * FamilyRef (INDI.FAMS / INDI.FAMC) that refers to that family // i 2 loop seguenti individuano nella person il *primo* FamilyRef (INDI.FAMS / INDI.FAMC) che rimanda a quella famiglia
			They do not take the one with the same index as the corresponding Ref in the family (FAM.HUSB / FAM.WIFE) //Non prendono quello con lo stesso indice del corrispondente Ref nella famiglia  (FAM.HUSB / FAM.WIFE)
			It could be a problem in case of 'Unlink', but not anymore because all the Family content is reloaded //Poteva essere un problema in caso di 'Scollega', ma non più perché tutto il contenuto di Famiglia viene ricaricato
		 */if (relation === Relation.PARTNER) {
            for (sfr in p.spouseFamilyRefs) if (f.id == sfr.ref) {
                personView.setTag(R.id.tag_spouse_family_ref, sfr)
                break
            }
        } else if (relation === Relation.CHILD) {
            for (pfr in p.parentFamilyRefs) if (f.id == pfr.ref) {
                personView.setTag(R.id.tag_spouse_family_ref, pfr)
                break
            }
        }
        personView.setTag(R.id.tag_spouse_ref, sr)
        registerForContextMenu(personView)
        personView.setOnClickListener { v: View? ->
            val parentFam = p.getParentFamilies(Global.gc)
            val spouseFam = p.getSpouseFamilies(Global.gc)
            // a spouse with one or more families in which he is a child
            if (relation === Relation.PARTNER && parentFam.isNotEmpty()) {
                U.askWhichParentsToShow(this, p, 2)
            } // a child with one or more families in which he is a spouse
            else if (relation === Relation.CHILD && !p.getSpouseFamilies(Global.gc).isEmpty()) {
                U.askWhichSpouseToShow(this, p, null)
            } // an unmarried child who has multiple parental families
            else if (parentFam.size > 1) {
                if (parentFam.size == 2) { // Swap between the 2 parental families //Swappa tra le 2 famiglie genitoriali
                    Global.indi = p.id
                    Global.familyNum = if (parentFam.indexOf(f) == 0) 1 else 0
                    Memory.replaceFirst(parentFam[Global.familyNum])
                    recreate()
                } else  //More than two families
                    U.askWhichParentsToShow(this, p, 2)
            } // a spouse without parents but with multiple marital families //un coniuge senza genitori ma con più famiglie coniugali
            else if (spouseFam.size > 1) {
                if (spouseFam.size == 2) { // Swap between the 2 marital families //Swappa tra le 2 famiglie coniugali
                    Global.indi = p.id
                    val otherFamily = spouseFam[if (spouseFam.indexOf(f) == 0) 1 else 0]
                    Memory.replaceFirst(otherFamily)
                    recreate()
                } else U.askWhichSpouseToShow(this, p, null)
            } else {
                Memory.setFirst(p)
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
        if (aRepresentativeOfTheFamily == null) aRepresentativeOfTheFamily = p
    }

    companion object {
        var pediTexts = listOf(
            U.s(R.string.undefined) + " (" + U.s(R.string.birth).lowercase(
                Locale.getDefault()
            ) + ")",
            U.s(R.string.birth), U.s(R.string.adopted), U.s(R.string.foster)
        )
        var pediTypes = listOf(null, "birth", "adopted", "foster")

        /** Find the role of a person from their relation with a family
         * @param family Can be null
         * @param respectFamily The role to find is relative to the family (it becomes 'parent' with children)
         * @return A descriptor text of the person's role
         */
		@JvmStatic
		fun getRole(
            person: Person?,
            family: Family?,
            _relation: Relation,
            respectFamily: Boolean
        ): String {
            val role: Int
            var relation: Relation = _relation
            if (respectFamily && relation === Relation.PARTNER && family?.childRefs?.isNotEmpty() == true) relation =
                Relation.PARENT
            val status = getStatus(family)
            role = if (isMale(person!!)) {
                when (relation) {
                    Relation.PARENT -> R.string.father
                    Relation.SIBLING -> R.string.brother
                    Relation.HALF_SIBLING -> R.string.half_brother
                    Relation.PARTNER -> when (status) {
                        Status.MARRIED -> R.string.husband
                        Status.DIVORCED -> R.string.ex_husband
                        Status.SEPARATED -> R.string.ex_male_partner
                        else -> R.string.male_partner
                    }
                    Relation.CHILD -> R.string.son
                }
            } else if (isFemale(person)) {
                when (relation) {
                    Relation.PARENT -> R.string.mother
                    Relation.SIBLING -> R.string.sister
                    Relation.HALF_SIBLING -> R.string.half_sister
                    Relation.PARTNER -> when (status) {
                        Status.MARRIED -> R.string.wife
                        Status.DIVORCED -> R.string.ex_wife
                        Status.SEPARATED -> R.string.ex_female_partner
                        else -> R.string.female_partner
                    }
                    Relation.CHILD -> R.string.daughter
                }
            } else {
                when (relation) {
                    Relation.PARENT -> R.string.parent
                    Relation.SIBLING -> R.string.sibling
                    Relation.HALF_SIBLING -> R.string.half_sibling
                    Relation.PARTNER -> when (status) {
                        Status.MARRIED -> R.string.spouse
                        Status.DIVORCED -> R.string.ex_spouse
                        Status.SEPARATED -> R.string.ex_partner
                        else -> R.string.partner
                    }
                    Relation.CHILD -> R.string.child
                }
            }
            return Global.context.getString(role)
        }

        /**
         * Find the ParentFamilyRef of a child person in a family
         */
		@JvmStatic
		fun findParentFamilyRef(person: Person, family: Family?): ParentFamilyRef? {
            for (parentFamilyRef in person.parentFamilyRefs) {
                if (parentFamilyRef.ref == family!!.id) {
                    return parentFamilyRef
                }
            }
            return null
        }

        /**
         * Compose the lineage definition to be added to the role
         */
		@JvmStatic
		fun writeLineage(person: Person, family: Family?): String {
            val parentFamilyRef = findParentFamilyRef(person, family)
            if (parentFamilyRef != null) {
                val actual = pediTypes.indexOf(parentFamilyRef.relationshipType)
                if (actual > 0) return " – " + pediTexts[actual]
            }
            return ""
        }

        /**
         * Display the alert dialog to choose the lineage of one person
         */
		@JvmStatic
		fun chooseLineage(context: Context?, person: Person, family: Family?) {
            val parentFamilyRef = findParentFamilyRef(person, family)
            if (parentFamilyRef != null) {
                val actual = pediTypes.indexOf(parentFamilyRef.relationshipType)
                AlertDialog.Builder(context!!)
                    .setSingleChoiceItems(pediTexts.toTypedArray()                                                                                          , actual) { dialog: DialogInterface, i: Int ->
                        parentFamilyRef.relationshipType = pediTypes[i]
                        dialog.dismiss()
                        if (context is ProfileActivity) context.refresh() else if (context is FamilyActivity) context.refresh()
                        U.save(true, person)
                    }.show()
            }
        }

        /**
         * Connect a person to a family as a parent or child
         */
		@JvmStatic
		fun connect(person: Person, fam: Family, roleFlag: Int) {
            when (roleFlag) {
                5 -> {
                    // the ref of the indi in the family //il ref dell'indi nella famiglia
                    val sr = SpouseRef()
                    sr.ref = person.id
                    IndividualEditorActivity.addSpouse(fam, sr)

                    // the family ref in the indi //il ref della famiglia nell'indi
                    val sfr = SpouseFamilyRef()
                    sfr.ref = fam.id
                    //tizio.getSpouseFamilyRefs().add( sfr );	// no: with empty list UnsupportedOperationException //no: con lista vuota UnsupportedOperationException
                    //List<SpouseFamilyRef> listOfRefs = tizio.getSpouseFamilyRefs();	// That's no good://Non va bene:
                    // when the list is non-existent, instead of returning an ArrayList it returns a Collections$EmptyList which is IMMUTABLE i.e. it does not allow add ()
                    person.spouseFamilyRefs = person.spouseFamilyRefs + sfr
                }
                6 -> {
                    val cr = ChildRef()
                    cr.ref = person.id
                    fam.addChild(cr)
                    val pfr = ParentFamilyRef()
                    pfr.ref = fam.id
                    //tizio.getParentFamilyRefs().add( pfr );	// UnsupportedOperationException
                    person.parentFamilyRefs = person.parentFamilyRefs + pfr
                }
            }
        }

        /**
         * Removes the single SpouseFamilyRef from the individual and the corresponding SpouseRef from the family
         */
        fun disconnect(sfr: SpouseFamilyRef, sr: SpouseRef) {
            // From person to family //Dalla persona alla famiglia
            val person = sr.getPerson(Global.gc)
            person.spouseFamilyRefs.remove(sfr)
            if (person.spouseFamilyRefs.isEmpty()) person.spouseFamilyRefs =
                null // Any empty list is deleted //Eventuale lista vuota viene eliminata
            person.parentFamilyRefs.remove(sfr)
            if (person.parentFamilyRefs.isEmpty()) person.parentFamilyRefs = null
            // From family to person //Dalla famiglia alla persona
            val fam = sfr.getFamily(Global.gc)
            fam.husbandRefs.remove(sr)
            if (fam.husbandRefs.isEmpty()) fam.husbandRefs = null
            fam.wifeRefs.remove(sr)
            if (fam.wifeRefs.isEmpty()) fam.wifeRefs = null
            fam.childRefs.remove(sr)
            if (fam.childRefs.isEmpty()) fam.childRefs = null
        }

        /**
         * Removes ALL refs from an individual in a family
         */
		@JvmStatic
		fun disconnect(indiId: String, family: Family) {
            // Removes the refs of the indi in the family //Rimuove i ref dell'indi nella famiglia
            var spouseRefs = family.husbandRefs.iterator()
            while (spouseRefs.hasNext()) if (spouseRefs.next().ref == indiId) spouseRefs.remove()
            if (family.husbandRefs.isEmpty()) family.husbandRefs =
                null // Delete any empty list //Elimina eventuale lista vuota
            spouseRefs = family.wifeRefs.iterator()
            while (spouseRefs.hasNext()) if (spouseRefs.next().ref == indiId) spouseRefs.remove()
            if (family.wifeRefs.isEmpty()) family.wifeRefs = null
            val childRefs = family.childRefs.iterator()
            while (childRefs.hasNext()) if (childRefs.next().ref == indiId) childRefs.remove()
            if (family.childRefs.isEmpty()) family.childRefs = null

            // Removes family refs in the indi //Rimuove i ref della famiglia nell'indi
            val person = Global.gc.getPerson(indiId)
            val iterSfr = person.spouseFamilyRefs.iterator()
            while (iterSfr.hasNext()) if (iterSfr.next().ref == family.id) iterSfr.remove()
            if (person.spouseFamilyRefs.isEmpty()) person.spouseFamilyRefs = null
            val iterPfr = person.parentFamilyRefs.iterator()
            while (iterPfr.hasNext()) if (iterPfr.next().ref == family.id) iterPfr.remove()
            if (person.parentFamilyRefs.isEmpty()) person.parentFamilyRefs = null
        }
    }
}