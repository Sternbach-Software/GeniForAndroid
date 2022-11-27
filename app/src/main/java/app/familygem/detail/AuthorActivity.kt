package app.familygem.detail

import app.familygem.DetailActivity
import org.folg.gedcom.model.Submitter
import app.familygem.R
import app.familygem.U
import app.familygem.ListOfAuthorsFragment

class AuthorActivity : DetailActivity() {

    lateinit var a: Submitter

    override fun format() {
        setTitle(R.string.submitter)
        a = cast(Submitter::class.java) as Submitter
        placeSlug("SUBM", a.id)
        place(getString(R.string.value), "Value", false, true) // Value of what? //Value de che?
        place(getString(R.string.name), "Name")
        place(getString(R.string.address), a.address)
        place(getString(R.string.www), "Www")
        place(getString(R.string.email), "Email")
        place(getString(R.string.telephone), "Phone")
        place(getString(R.string.fax), "Fax")
        place(getString(R.string.language), "Language")
        place(getString(R.string.rin), "Rin", false, false)
        placeExtensions(a)
        U.placeChangeDate(box, a.change)
    }

    override fun delete() {
        // Remember that at least one author must be specified // Ricordiamo che almeno un autore deve essere specificato
        // don't update the date of any record // non aggiorna la data di nessun record
        ListOfAuthorsFragment.deleteAuthor(a)
    }
}