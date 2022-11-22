package app.familygem.detail

import app.familygem.*
import org.folg.gedcom.model.Name

class NameActivity : DetailActivity() {

    lateinit var n: Name

    override fun format() {
        setTitle(R.string.name)
        placeSlug("NAME", null)
        n = cast(Name::class.java) as Name
        if (Global.settings.expert) place(getString(R.string.value), "Value") else {
            var firstName = ""
            var lastName = ""
            val epithet = n.value
            if (epithet != null) {
                firstName =
                    epithet.replace("/.*?/".toRegex(), "").trim { it <= ' ' } // Remove the lastName
                if (epithet.indexOf('/') < epithet.lastIndexOf('/')) lastName =
                    epithet.substring(epithet.indexOf('/') + 1, epithet.lastIndexOf('/'))
                        .trim { it <= ' ' }
            }
            placePiece(getString(R.string.given), firstName, 4043, false)
            placePiece(getString(R.string.surname), lastName, 6064, false)
        }
        place(getString(R.string.nickname), "Nickname")
        place(
            getString(R.string.type),
            "Type",
            true,
            false
        ) // _TYPE in GEDCOM 5.5, TYPE in GEDCOM 5.5.1
        place(getString(R.string.prefix), "Prefix", Global.settings.expert, false)
        place(getString(R.string.given), "Given", Global.settings.expert, false)
        place(getString(R.string.surname_prefix), "SurnamePrefix", Global.settings.expert, false)
        place(getString(R.string.surname), "Surname", Global.settings.expert, false)
        place(getString(R.string.suffix), "Suffix", Global.settings.expert, false)
        place(getString(R.string.married_name), "MarriedName", false, false) // _marrnm
        place(getString(R.string.aka), "Aka", false, false) // _aka
        place(getString(R.string.romanized), "Romn", Global.settings.expert, false)
        place(getString(R.string.phonetic), "Fone", Global.settings.expert, false)
        placeExtensions(n)
        U.placeNotes(box, n, true)
        U.placeMedia(
            box,
            n,
            true
        ) // It seems strange to me that a Name has Media .. anyway .. //Mi sembra strano che un Name abbia Media.. comunque..
        U.placeSourceCitations(box, n)
    }

    override fun delete() {
        val currentPerson = Global.gc.getPerson(Global.indi)
        currentPerson.names.remove(n)
        U.updateChangeDate(currentPerson)
        Memory.setInstanceAndAllSubsequentToNull(n)
    }
}