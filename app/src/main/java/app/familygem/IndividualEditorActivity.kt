package app.familygem

import app.familygem.constant.Gender.Companion.isMale
import app.familygem.constant.Gender.Companion.getGender
import app.familygem.detail.EventActivity.Companion.cleanUpTag
import app.familygem.Settings.currentTree
import app.familygem.Settings.save
import app.familygem.detail.FamilyActivity.Companion.connect
import app.familygem.FamiliesFragment.Companion.newFamily
import app.familygem.constant.Gender.Companion.isFemale
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioButton
import android.widget.EditText
import app.familygem.PublisherDateLinearLayout
import androidx.appcompat.widget.SwitchCompat
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import app.familygem.U
import app.familygem.R
import android.widget.RadioGroup
import android.widget.CompoundButton
import android.widget.TextView.OnEditorActionListener
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import app.familygem.ProfileFactsFragment
import app.familygem.detail.EventActivity
import app.familygem.detail.FamilyActivity
import app.familygem.IndividualEditorActivity
import app.familygem.FamiliesFragment
import app.familygem.constant.Gender
import org.folg.gedcom.model.*
import java.util.*

class IndividualEditorActivity : AppCompatActivity() {
    var p: Person? = null
    var idIndi: String? = null
    var familyId: String? = null
    var relationship = 0
    var sexMale: RadioButton? = null
    var sexFemale: RadioButton? = null
    var sexUnknown: RadioButton? = null
    var lastChecked = 0
    var dateOfBirth: EditText? = null
    var publisherDateLinearLayoutDOB //DOB = Date of Birth
            : PublisherDateLinearLayout? = null
    var birthplaceEditText: EditText? = null
    var isDeadSwitch: SwitchCompat? = null
    var dateOfDeath: EditText? = null
    var publisherDateLinearLayoutDOD //DOD = Date of Death
            : PublisherDateLinearLayout? = null
    var deathPlace: EditText? = null
    var nameFromPieces //If the name / surname comes from the Given and Surname pieces, they must return there // Se il nome/cognome vengono dai pieces Given e Surname, lì devono tornare
            = false

    override fun onCreate(bandolo: Bundle?) {
        super.onCreate(bandolo)
        U.ensureGlobalGedcomNotNull(Global.gc)
        setContentView(R.layout.edita_individuo)
        val bundle = intent.extras
        idIndi = bundle!!.getString(PROFILE_ID_KEY)
        familyId = bundle.getString(FAMILY_ID_KEY)
        relationship = bundle.getInt(RELATIONSHIP_ID_KEY, 0)
        sexMale = findViewById(R.id.sesso1)
        sexFemale = findViewById(R.id.sesso2)
        sexUnknown = findViewById(R.id.sesso3)
        dateOfBirth = findViewById(R.id.data_nascita)
        publisherDateLinearLayoutDOB = findViewById(R.id.editore_data_nascita)
        birthplaceEditText = findViewById(R.id.luogo_nascita)
        isDeadSwitch = findViewById(R.id.defunto)
        dateOfDeath = findViewById(R.id.data_morte)
        publisherDateLinearLayoutDOD = findViewById(R.id.editore_data_morte)
        deathPlace = findViewById(R.id.luogo_morte)

        // Toggle sex radio buttons
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val radioClick = View.OnClickListener { radioButton: View ->
            if (radioButton.id == lastChecked) {
                radioGroup.clearCheck()
            }
        }
        sexMale.setOnClickListener(radioClick)
        sexFemale.setOnClickListener(radioClick)
        sexUnknown.setOnClickListener(radioClick)
        radioGroup.setOnCheckedChangeListener { group: RadioGroup, checked: Int ->
            group.post {
                lastChecked = checked
            }
        }
        disableDeath()

        // New individual in kinship relationship
        if (relationship > 0) {
            p = Person()
            val pivot = Global.gc!!.getPerson(idIndi)
            var surname: String? = null
            // Brother's surname
            if (relationship == 2) { // = brother
                surname = U.surname(pivot)
                // Father's surname
            } else if (relationship == 4) { // = child from Diagram or Individual // = figlio da Diagramma o Individuo
                if (isMale(pivot)) surname = U.surname(pivot) else if (familyId != null) {
                    val fam = Global.gc!!.getFamily(familyId)
                    if (fam != null && !fam.getHusbands(Global.gc).isEmpty()) surname = U.surname(
                        fam.getHusbands(Global.gc)[0]
                    )
                }
            } else if (relationship == 6) { // = child of Family(Activity?) // = figlio da Famiglia
                val fam = Global.gc!!.getFamily(familyId)
                if (!fam.getHusbands(Global.gc).isEmpty()) surname = U.surname(
                    fam.getHusbands(Global.gc)[0]
                ) else if (!fam.getChildren(Global.gc).isEmpty()) surname = U.surname(
                    fam.getChildren(Global.gc)[0]
                )
            }
            (findViewById<View>(R.id.cognome) as EditText).setText(surname)
            // New disconnected individual
        } else if (idIndi == "TIZIO_NUOVO") {
            p = Person()
            // Upload the data of an existing individual to modify
        } else {
            p = Global.gc!!.getPerson(idIndi)
            // Name and surname
            if (!p.getNames().isEmpty()) {
                var name = ""
                var surname = ""
                val n = p.getNames()[0]
                val epithet = n.value
                if (epithet != null) {
                    name = epithet.replace("/.*?/".toRegex(), "")
                        .trim { it <= ' ' } //removes surname '/.../' // rimuove il cognome '/.../'
                    if (epithet.indexOf('/') < epithet.lastIndexOf('/')) surname =
                        epithet.substring(epithet.indexOf('/') + 1, epithet.lastIndexOf('/'))
                            .trim { it <= ' ' }
                } else {
                    if (n.given != null) {
                        name = n.given
                        nameFromPieces = true
                    }
                    if (n.surname != null) {
                        surname = n.surname
                        nameFromPieces = true
                    }
                }
                (findViewById<View>(R.id.nome) as EditText).setText(name)
                (findViewById<View>(R.id.cognome) as EditText).setText(surname)
            }
            when (getGender(p)) {
                Gender.MALE -> sexMale.setChecked(true)
                Gender.FEMALE -> sexFemale.setChecked(true)
                Gender.UNKNOWN -> sexUnknown.setChecked(true)
            }
            lastChecked = radioGroup.checkedRadioButtonId
            // Birth and death
            for (fact in p.getEventsFacts()) {
                if (fact.tag == "BIRT") {
                    if (fact.date != null) dateOfBirth.setText(fact.date.trim { it <= ' ' })
                    if (fact.place != null) birthplaceEditText.setText(fact.place.trim { it <= ' ' })
                }
                if (fact.tag == "DEAT") {
                    isDeadSwitch.setChecked(true)
                    activateDeathSwitch()
                    if (fact.date != null) dateOfDeath.setText(fact.date.trim { it <= ' ' })
                    if (fact.place != null) deathPlace.setText(fact.place.trim { it <= ' ' })
                }
            }
        }
        publisherDateLinearLayoutDOB.initialize(dateOfBirth)
        isDeadSwitch.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { button: CompoundButton?, checked: Boolean -> if (checked) activateDeathSwitch() else disableDeath() })
        publisherDateLinearLayoutDOD.initialize(dateOfDeath)
        deathPlace.setOnEditorActionListener(OnEditorActionListener { vista: TextView?, actionId: Int, keyEvent: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) save()
            false
        })

        // Toolbar
        val toolbar = supportActionBar
        val toolbarAction = layoutInflater.inflate(
            R.layout.barra_edita, LinearLayout(
                applicationContext
            ), false
        )
        toolbarAction.findViewById<View>(R.id.edita_annulla)
            .setOnClickListener { v: View? -> onBackPressed() }
        toolbarAction.findViewById<View>(R.id.edita_salva).setOnClickListener { v: View? -> save() }
        toolbar!!.customView = toolbarAction
        toolbar.setDisplayShowCustomEnabled(true)
    }

    fun disableDeath() {
        findViewById<View>(R.id.morte).visibility = View.GONE
        birthplaceEditText!!.imeOptions = EditorInfo.IME_ACTION_DONE
        birthplaceEditText!!.nextFocusForwardId = 0
        // Intercept the 'Done' on the keyboard
        birthplaceEditText!!.setOnEditorActionListener { view: TextView?, action: Int, event: KeyEvent? ->
            if (action == EditorInfo.IME_ACTION_DONE) save()
            false
        }
    }

    fun activateDeathSwitch() {
        birthplaceEditText!!.imeOptions = EditorInfo.IME_ACTION_NEXT
        birthplaceEditText!!.nextFocusForwardId = R.id.data_morte
        birthplaceEditText!!.setOnEditorActionListener(null)
        findViewById<View>(R.id.morte).visibility = View.VISIBLE
    }

    fun save() {
        U.ensureGlobalGedcomNotNull(Global.gc) //A crash occurred because gc was null here

        // Name
        val nameString =
            (findViewById<View>(R.id.nome) as EditText).text.toString().trim { it <= ' ' }
        val surname =
            (findViewById<View>(R.id.cognome) as EditText).text.toString().trim { it <= ' ' }
        val name: Name
        if (p!!.names.isEmpty()) {
            val names: MutableList<Name> = ArrayList()
            name = Name()
            names.add(name)
            p!!.names = names
        } else name = p!!.names[0]
        if (nameFromPieces) {
            name.given = nameString
            name.surname = surname
        } else {
            name.value = nameString + " /" + surname + "/".trim { it <= ' ' }
        }

        // Sex
        var chosenGender: String? = null
        if (sexMale!!.isChecked) chosenGender = "M" else if (sexFemale!!.isChecked) chosenGender =
            "F" else if (sexUnknown!!.isChecked) chosenGender = "U"
        if (chosenGender != null) {
            var missingSex = true
            for (fact in p!!.eventsFacts) {
                if (fact.tag == "SEX") {
                    fact.value = chosenGender
                    missingSex = false
                }
            }
            if (missingSex) {
                val sex = EventFact()
                sex.tag = "SEX"
                sex.value = chosenGender
                p!!.addEventFact(sex)
            }
            ProfileFactsFragment.updateMaritalRoles(p)
        } else { // Remove existing sex tag
            for (fact in p!!.eventsFacts) {
                if (fact.tag == "SEX") {
                    p!!.eventsFacts.remove(fact)
                    break
                }
            }
        }

        // Birth
        publisherDateLinearLayoutDOB!!.encloseInParentheses()
        var data = dateOfBirth!!.text.toString().trim { it <= ' ' }
        var location = birthplaceEditText!!.text.toString().trim { it <= ' ' }
        var found = false
        for (fact in p!!.eventsFacts) {
            if (fact.tag == "BIRT") {
                /* TODO:
					    if (data.isEmpty () && place.isEmpty () && tagAllEmpty (done))
							p.getEventsFacts (). remove (done);
						more generally, delete a tag when it is empty

					    if( data.isEmpty() && luogo.isEmpty() && tagTuttoVuoto(fatto) )
					    	p.getEventsFacts().remove(fatto);
					    più in generale, eliminare un tag quando è vuoto */
                fact.date = data
                fact.place = location
                cleanUpTag(fact)
                found = true
            }
        }
        // If there is any data to save, create the tag
        if (!found && (!data.isEmpty() || !location.isEmpty())) {
            val birth = EventFact()
            birth.tag = "BIRT"
            birth.date = data
            birth.place = location
            cleanUpTag(birth)
            p!!.addEventFact(birth)
        }

        // Death
        publisherDateLinearLayoutDOD!!.encloseInParentheses()
        data = dateOfDeath!!.text.toString().trim { it <= ' ' }
        location = deathPlace!!.text.toString().trim { it <= ' ' }
        found = false
        for (fact in p!!.eventsFacts) {
            if (fact.tag == "DEAT") {
                if (!isDeadSwitch!!.isChecked) {
                    p!!.eventsFacts.remove(fact)
                } else {
                    fact.date = data
                    fact.place = location
                    cleanUpTag(fact)
                }
                found = true
                break
            }
        }
        if (!found && isDeadSwitch!!.isChecked) {
            val morte = EventFact()
            morte.tag = "DEAT"
            morte.date = data
            morte.place = location
            cleanUpTag(morte)
            p!!.addEventFact(morte)
        }

        // Finalization of new individual
        var modifications =
            arrayOf<Any?>(p, null) // the null is used to accommodate a possible Family
        if (idIndi == "TIZIO_NUOVO" || relationship > 0) {
            val newId = U.newID(Global.gc, Person::class.java)
            p!!.id = newId
            Global.gc!!.addPerson(p)
            if (Global.settings!!.currentTree!!.root == null) Global.settings!!.currentTree!!.root =
                newId
            Global.settings!!.save()
            if (relationship >= 5) { // comes from Family(Activity)
                connect(p!!, Global.gc!!.getFamily(familyId), relationship)
                modifications[1] = Global.gc!!.getFamily(familyId)
            } else if (relationship > 0) // comes from Family Diagram or Individual
                modifications = addParent(
                    idIndi,
                    newId,
                    familyId,
                    relationship,
                    intent.getStringExtra(LOCATION_KEY)
                )
        } else Global.indi =
            p!!.id //to proudly (prominently?) show it in Diagram // per mostrarlo orgogliosi in Diagramma
        U.save(true, *modifications)
        onBackPressed()
    }

    companion object {
        /**
         * Aggiunge un nuovo individuo in relazione di parentela con 'perno', eventualmente all'interno della famiglia fornita.
         * @param familyId Id della famiglia di destinazione. Se è null si crea una nuova famiglia
         * @param collection Sintetizza come è stata individuata la famiglia e quindi cosa fare delle persone coinvolte
         *
         *
         * Adds a new kinship individual with 'pivot', possibly within the given family.
         * @param familyId Id of the target family. If it is null, a new family is created
         * @param collection Summarizes how the family was identified and therefore what to do with the people involved
         */
        @JvmStatic
        fun addParent(
            pivotId: String?,
            newId: String?,
            familyId: String?,
            relationship: Int,
            collection: String?
        ): Array<Any?> {
            var pivotId = pivotId
            var newId = newId
            var relationship = relationship
            Global.indi = pivotId
            var newPerson = Global.gc!!.getPerson(newId)
            // A new family is created in which both Pin and New end up
            if (collection != null && collection.startsWith("NUOVA_FAMIGLIA_DI")) { // Contains the id of the parent to create a new family for
                pivotId = collection.substring(17) // the parent effectively becomes the pivot
                relationship =
                    if (relationship == 2) 4 else relationship //instead of a pivotal sibling, it is as if we were putting a child to the parent // anziché un fratello a perno, è come se mettessimo un figlio al genitore
            } else if (collection != null && collection == "FAMIGLIA_ESISTENTE") {
                newId = null
                newPerson = null
            } else if (familyId != null) {
                pivotId =
                    null // pivot is already present in his family and should not be added again
            }
            val family = if (familyId != null) Global.gc!!.getFamily(familyId) else newFamily(true)
            val pivot = Global.gc!!.getPerson(pivotId)
            val refSpouse1 = SpouseRef()
            val refSposo2 = SpouseRef()
            val refChild1 = ChildRef()
            val refFiglio2 = ChildRef()
            val parentFamilyRef = ParentFamilyRef()
            val spouseFamilyRef = SpouseFamilyRef()
            parentFamilyRef.ref = family.id
            spouseFamilyRef.ref = family.id
            when (relationship) {
                1 -> {
                    refSpouse1.ref = newId
                    refChild1.ref = pivotId
                    newPerson?.addSpouseFamilyRef(spouseFamilyRef)
                    pivot?.addParentFamilyRef(parentFamilyRef)
                }
                2 -> {
                    refChild1.ref = pivotId
                    refFiglio2.ref = newId
                    pivot?.addParentFamilyRef(parentFamilyRef)
                    newPerson?.addParentFamilyRef(parentFamilyRef)
                }
                3 -> {
                    refSpouse1.ref = pivotId
                    refSposo2.ref = newId
                    pivot?.addSpouseFamilyRef(spouseFamilyRef)
                    newPerson?.addSpouseFamilyRef(spouseFamilyRef)
                }
                4 -> {
                    refSpouse1.ref = pivotId
                    refChild1.ref = newId
                    pivot?.addSpouseFamilyRef(spouseFamilyRef)
                    newPerson?.addParentFamilyRef(parentFamilyRef)
                }
            }
            if (refSpouse1.ref != null) addSpouse(family, refSpouse1)
            if (refSposo2.ref != null) addSpouse(family, refSposo2)
            if (refChild1.ref != null) family.addChild(refChild1)
            if (refFiglio2.ref != null) family.addChild(refFiglio2)
            if (relationship == 1 || relationship == 2) // It will bring up the selected family
                Global.familyNum = Global.gc!!.getPerson(Global.indi).getParentFamilies(Global.gc)
                    .indexOf(family) else Global.familyNum = 0 // eventually reset
            val transformed: Set<Any> = HashSet()
            if (pivot != null && newPerson != null) Collections.addAll(
                transformed,
                family,
                pivot,
                newPerson
            ) else if (pivot != null) Collections.addAll(
                transformed,
                family,
                pivot
            ) else if (newPerson != null) Collections.addAll(transformed, family, newPerson)
            return transformed.toTypedArray()
        }

        /**
         * Adds the spouse in a family: always and only on the basis of sex
         */
        fun addSpouse(family: Family, sr: SpouseRef) {
            val person = Global.gc!!.getPerson(sr.ref)
            if (isFemale(person)) family.addWife(sr) else family.addHusband(sr)
        }
    }
}