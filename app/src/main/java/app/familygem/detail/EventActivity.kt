package app.familygem.detail

import app.familygem.DetailActivity
import org.folg.gedcom.model.EventFact
import app.familygem.Memory
import org.folg.gedcom.model.Family
import app.familygem.ProfileFactsFragment
import app.familygem.R
import app.familygem.U
import org.folg.gedcom.model.PersonFamilyCommonContainer
import java.util.*

class EventActivity : DetailActivity() {
    lateinit var e: EventFact

    /**
     * List of event tags useful to avoid putting the Value of the EventFact
     */
    private var eventTags = listOf(
        "BIRT",
        "CHR",
        "DEAT",
        "BURI",
        "CREM",
        "ADOP",
        "BAPM",
        "BARM",
        "BASM",
        "BLES",  // Individual events
        "CHRA",
        "CONF",
        "FCOM",
        "ORDN",
        "NATU",
        "EMIG",
        "IMMI",
        "CENS",
        "PROB",
        "WILL",
        "GRAD",
        "RETI",
        "ANUL",
        "DIV",
        "DIVF",
        "ENGA",
        "MARB",
        "MARC",
        "MARR",
        "MARL",
        "MARS"
    ) // Family events

    override fun format() {
        e = cast(EventFact::class.java) as EventFact
        title = if (Memory.firstObject() is Family) writeEventTitle(
            Memory.firstObject() as Family,
            e
        ) else ProfileFactsFragment.writeEventTitle(e) // It includes e.getDisplayType()
        placeSlug(e.tag)
        if (eventTags.contains(e.tag)) // is an event (without Value)
            place(
                getString(R.string.value),
                "Value",
                false,
                true
            ) else  // all other cases, usually attributes (with Value)
            place(getString(R.string.value), "Value", true, true)
        if (e.tag == "EVEN" || e.tag == "MARR") place(
            getString(R.string.type),
            "Type"
        ) // Type of event, relationship etc.
        else place(getString(R.string.type), "Type", false, false)
        place(getString(R.string.date), "Date")
        place(getString(R.string.place), "Place")
        place(getString(R.string.address), e.address)
        if (e.tag != null && e.tag == "DEAT") place(
            getString(R.string.cause),
            "Cause"
        ) else place(getString(R.string.cause), "Cause", false, false)
        place(getString(R.string.www), "Www", false, false)
        place(getString(R.string.email), "Email", false, false)
        place(getString(R.string.telephone), "Phone", false, false)
        place(getString(R.string.fax), "Fax", false, false)
        place(getString(R.string.rin), "Rin", false, false)
        place(getString(R.string.user_id), "Uid", false, false)
        //otherMethods = { "WwwTag", "EmailTag", "UidTag" };
        placeExtensions(e)
        U.placeNotes(box, e, true)
        U.placeMedia(box, e, true)
        U.placeSourceCitations(box, e)
    }

    override fun delete() {
        (Memory.getSecondToLastObject() as PersonFamilyCommonContainer).eventsFacts.remove(e)
        U.updateChangeDate(Memory.firstObject())
        Memory.setInstanceAndAllSubsequentToNull(e)
    }

    companion object {
        /**
         * Delete the main empty tags and eventually add the 'Y'
         * Elimina i principali tag vuoti e eventualmente aggiunge la 'Y'
         */
        @JvmStatic
        fun cleanUpTag(ef: EventFact) {
            if (ef.type?.isEmpty() == true) ef.type = null
            if (ef.date?.isEmpty() == true) ef.date = null
            if (ef.place?.isEmpty() == true) ef.place = null
            val tag = ef.tag
            if (tag != null && (tag == "BIRT" || tag == "CHR" || tag == "DEAT" || tag == "MARR" || tag == "DIV")) {
                if (ef.type == null && ef.date == null && ef.place == null && ef.address == null && ef.cause == null) ef.value =
                    "Y" else ef.value = null
            }
            if (ef.value?.isEmpty() == true) ef.value = null
        }
    }
}